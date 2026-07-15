package com.linuxhost

import android.content.Context
import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class StorageBreakdown(
    val rootfsBytes: Long = 0,
    val aptCacheBytes: Long = 0,
    val logsBytes: Long = 0,
    val tempBytes: Long = 0,
    val pythonPackagesBytes: Long = 0,
    val nodeModulesBytes: Long = 0,
    val gitProjectsBytes: Long = 0,
    val totalBytes: Long = 0,
)

data class Progress(
    val percent: Int = 0,
    val message: String = "",
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
)

class ProotEngine(private val context: Context) {

    val arch: String by lazy {
        when (Build.CPU_ABI) {
            "aarch64", "arm64", "arm64-v8a" -> "aarch64"
            "armv7l", "armeabi-v7a" -> "armv7l"
            "x86_64", "x86" -> "x86_64"
            else -> "aarch64"
        }
    }

    private val rootfsMirrors: List<String> get() {
        val a = archStringMap[arch] ?: "arm64"
        return listOf(
            "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.4-base-$a.tar.gz",
            "https://cdimage.ubuntu.com/ubuntu-base/releases/noble/release/ubuntu-base-24.04.4-base-$a.tar.gz",
            "https://ftpmirror.your.org/pub/ubuntu/cdimage/ubuntu-base/releases/24.04/release/ubuntu-base-24.04-base-$a.tar.gz",
            "http://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04.4-base-$a.tar.gz",
        )
    }

    private val _status = MutableStateFlow(InstanceStatus.NOT_INSTALLED)
    val status: StateFlow<InstanceStatus> = _status.asStateFlow()

    private val _progress = MutableSharedFlow<Progress>(replay = 1, extraBufferCapacity = 64)
    val progress: SharedFlow<Progress> = _progress.asSharedFlow()

    private var process: Process? = null
    private val filesDir: File = context.filesDir

    val prootBin: File get() = ProotInstaller.prootBin(context)
    val rootfsDir: File get() = ProotInstaller.rootfsDir(context)
    val prootLoader: File get() = ProotInstaller.prootLoader(context)
    val tmpDir: File get() = ProotInstaller.tmpDir(context)

    private val processLock = Any()

    companion object {
        private val archStringMap = mapOf(
            "aarch64" to "arm64",
            "armv7l" to "armhf",
            "x86_64" to "amd64",
        )
    }

    suspend fun checkStatus() = withContext(Dispatchers.IO) {
        try {
            if (process?.isAlive == true) {
                _status.value = InstanceStatus.RUNNING
                return@withContext
            }

            val dbRecord = LinuxHostDatabase.get(context).instanceDao().get()
            if (dbRecord?.status == InstanceStatus.INSTALLED) {
                _status.value = if (rootfsDir.exists() && prootBin.exists() && prootLoader.exists()) {
                    InstanceStatus.INSTALLED
                } else {
                    InstanceStatus.ERROR
                }
                return@withContext
            }

            if (rootfsDir.exists()) {
                val essentialBins = listOf("bin/sh", "usr/bin/env", "bin/bash")
                val hasAllBins = essentialBins.all { File(rootfsDir, it).exists() }
                _status.value = if (hasAllBins && prootBin.exists() && prootLoader.exists()) {
                    InstanceStatus.INSTALLED
                } else {
                    InstanceStatus.INTERRUPTED
                }
            } else {
                _status.value = InstanceStatus.NOT_INSTALLED
            }
        } catch (e: Exception) {
            _status.value = InstanceStatus.ERROR
        }
    }

    suspend fun installRootfs(tarballPath: String) = withContext(Dispatchers.IO) {
        _status.value = InstanceStatus.INSTALLING
        try {
            ProotInstaller.ensureInstalled(context)

            rootfsDir.mkdirs()
            _progress.emit(Progress(0, "Extracting rootfs, this may take a while..."))

            RootfsExtractor.extract(File(tarballPath), rootfsDir) { name, current, total ->
                val pct = if (total > 0) (current * 100 / total).coerceIn(0, 100) else 0
                _progress.emit(Progress(pct, "Extracting $name..."))
            }
            File(tarballPath).delete()

            _progress.emit(Progress(95, "Applying post-extraction fixups..."))
            RootfsExtractor.postExtractionFixups(rootfsDir)

            val essentialBins = listOf("bin/sh", "usr/bin/env", "bin/bash")
            val missingBins = essentialBins.filter { !File(rootfsDir, it).exists() }
            if (missingBins.isNotEmpty()) {
                rootfsDir.deleteRecursively()
                throw RuntimeException("Rootfs extraction incomplete — missing: ${missingBins.joinToString()}")
            }

            val instance = UbuntuInstance(
                id = "default",
                name = "Ubuntu 24.04",
                status = InstanceStatus.INSTALLED,
                rootfsPath = rootfsDir.absolutePath,
                sizeBytes = rootfsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() },
                version = "24.04",
                installedAt = System.currentTimeMillis(),
            )
            LinuxHostDatabase.get(context).instanceDao().upsert(instance)

            _progress.emit(Progress(100, "Rootfs extracted successfully"))
            _status.value = InstanceStatus.INSTALLED
        } catch (e: Exception) {
            _status.value = InstanceStatus.ERROR
            _progress.emit(Progress(0, "Failed to extract rootfs: ${e.message}"))
            throw e
        }
    }

    suspend fun downloadRootfs(): String = withContext(Dispatchers.IO) {
        _status.value = InstanceStatus.INSTALLING
        val tempFile = File(filesDir, "ubuntu-rootfs.tar.gz")
        _progress.emit(Progress(0, "Downloading Ubuntu rootfs..."))
        val errors = mutableListOf<String>()
        for (urlStr in rootfsMirrors) {
            try {
                RootfsExtractor.downloadFile(URL(urlStr), tempFile) { downloaded, total ->
                    val pct = if (total > 0) (downloaded * 100 / total).coerceIn(0, 100).toInt() else 0
                    runCatching {
                        _progress.tryEmit(Progress(pct, "Downloading Ubuntu rootfs...", downloaded, total))
                    }
                }
                _progress.emit(Progress(100, "Ubuntu rootfs downloaded"))
                return@withContext tempFile.absolutePath
            } catch (e: Exception) {
                errors.add("${e.message}")
                tempFile.delete()
            }
        }
        _status.value = InstanceStatus.ERROR
        val msg = "All mirrors failed:\n${errors.joinToString("\n")}"
        _progress.emit(Progress(0, msg))
        throw RuntimeException(msg)
    }

    suspend fun launch() = withContext(Dispatchers.IO) {
        try {
            val paths = ProotInstaller.ensureInstalled(context)

            if (!rootfsDir.exists()) {
                throw RuntimeException("Rootfs directory not found at ${rootfsDir.absolutePath}")
            }
            val requiredPaths = listOf("bin/sh", "usr/bin/env", "bin/bash")
            val missing = requiredPaths.filter { !File(rootfsDir, it).exists() }
            if (missing.isNotEmpty()) {
                throw RuntimeException("Rootfs is incomplete — missing: ${missing.joinToString()}")
            }

            val cmd = ProotCommandBuilder.buildLoginCommand(
                prootBin = paths.prootBin,
                rootfsDir = rootfsDir.absolutePath,
                tmpDir = paths.tmpDir,
            )

            val pb = ProcessBuilder(cmd)
            pb.environment()["PROOT_TMP_DIR"] = paths.tmpDir
            pb.environment()["PROOT_LOADER"] = paths.prootLoader
            pb.redirectErrorStream(true)
            val p = pb.start()
            synchronized(processLock) { process = p }
            _status.value = InstanceStatus.RUNNING
        } catch (e: Exception) {
            _status.value = InstanceStatus.ERROR
            throw e
        }
    }

    fun stop() {
        synchronized(processLock) {
            process?.let {
                it.destroyForcibly()
                it.waitFor()
            }
            process = null
        }
        _status.value = InstanceStatus.STOPPED
    }

    suspend fun cleanupInterrupted() = withContext(Dispatchers.IO) {
        try {
            val containersDir = ProotInstaller.containersDir(context)
            if (containersDir.exists()) {
                containersDir.deleteRecursively()
            }
            LinuxHostDatabase.get(context).instanceDao().deleteAll()
            _status.value = InstanceStatus.NOT_INSTALLED
        } catch (e: Exception) {
            _status.value = InstanceStatus.ERROR
            throw e
        }
    }

    suspend fun remove() = withContext(Dispatchers.IO) {
        try {
            stop()
            val containersDir = ProotInstaller.containersDir(context)
            if (containersDir.exists()) {
                containersDir.deleteRecursively()
            }
            val db = LinuxHostDatabase.get(context)
            db.instanceDao().deleteAll()
            _status.value = InstanceStatus.NOT_INSTALLED
        } catch (e: Exception) {
            _status.value = InstanceStatus.ERROR
            throw e
        }
    }

    suspend fun updatePackages() = withContext(Dispatchers.IO) {
        if (!canRun()) {
            _progress.emit(Progress(0, "Ubuntu is not installed"))
            return@withContext
        }
        _progress.emit(Progress(0, "Running apt update && apt upgrade -y..."))
        try {
            val output = runCommandWithOutput(prootCommand("apt update && apt upgrade -y"))
            output.forEach { line -> _progress.emit(Progress(0, line)) }
            _progress.emit(Progress(100, "Package update complete"))
        } catch (e: Exception) {
            _progress.emit(Progress(0, "Package update failed: ${e.message}"))
            throw e
        }
    }

    suspend fun repair() = withContext(Dispatchers.IO) {
        if (!canRun()) {
            _progress.emit(Progress(0, "Ubuntu is not installed"))
            return@withContext
        }
        _progress.emit(Progress(0, "Starting system repair..."))
        try {
            val cmds = listOf(
                "dpkg --configure -a",
                "apt --fix-broken install -y",
                "apt update",
            )
            cmds.forEach { cmd ->
                val output = runCommandWithOutput(prootCommand(cmd))
                output.forEach { line -> _progress.emit(Progress(0, line)) }
            }
            _progress.emit(Progress(100, "Repair complete"))
        } catch (e: Exception) {
            _progress.emit(Progress(0, "Repair failed: ${e.message}"))
            throw e
        }
    }

    suspend fun getStorageBreakdown(): StorageBreakdown = withContext(Dispatchers.IO) {
        val rootfsPath = rootfsDir.absolutePath
        val rootfsBytes = du(rootfsPath)
        val aptCacheBytes = du("$rootfsPath/var/cache/apt", "$rootfsPath/var/lib/apt")
        val logsBytes = du("$rootfsPath/var/log")
        val tempBytes = du("$rootfsPath/tmp")
        val pythonPackagesBytes = du(
            "$rootfsPath/usr/lib/python3",
            "$rootfsPath/usr/local/lib/python3",
        )
        val nodeModulesBytes = du(
            "$rootfsPath/usr/lib/node_modules",
            "$rootfsPath/usr/local/lib/node_modules",
        )
        val gitProjectsBytes = du("$rootfsPath/root/git")

        val totalBytes = rootfsBytes + aptCacheBytes + logsBytes + tempBytes +
            pythonPackagesBytes + nodeModulesBytes + gitProjectsBytes

        StorageBreakdown(
            rootfsBytes = rootfsBytes,
            aptCacheBytes = aptCacheBytes,
            logsBytes = logsBytes,
            tempBytes = tempBytes,
            pythonPackagesBytes = pythonPackagesBytes,
            nodeModulesBytes = nodeModulesBytes,
            gitProjectsBytes = gitProjectsBytes,
            totalBytes = totalBytes,
        )
    }

    private fun canRun(): Boolean = prootBin.exists() && rootfsDir.exists() && prootLoader.exists()

    suspend fun executeCommand(command: String): String = withContext(Dispatchers.IO) {
        runCommand(prootCommand(command))
    }

    suspend fun getPackageCount(): Int = withContext(Dispatchers.IO) {
        if (!canRun()) return@withContext 0
        try {
            val output = runCommand(prootCommand("dpkg --list 2>/dev/null | wc -l"))
            (output.toIntOrNull()?.minus(5))?.coerceAtLeast(0) ?: 0
        } catch (_: Exception) { 0 }
    }

    suspend fun getPendingUpdates(): Int = withContext(Dispatchers.IO) {
        if (!canRun()) return@withContext 0
        try {
            val output = runCommand(prootCommand("apt list --upgradable 2>/dev/null | wc -l"))
            (output.toIntOrNull()?.minus(1))?.coerceAtLeast(0) ?: 0
        } catch (_: Exception) { 0 }
    }

    private fun prootCommand(command: String): List<String> {
        val paths = ProotInstaller.ensureInstalled(context)
        return ProotCommandBuilder.buildCommand(
            prootBin = paths.prootBin,
            rootfsDir = rootfsDir.absolutePath,
            tmpDir = paths.tmpDir,
            command = command,
        )
    }

    private fun runCommand(cmd: List<String>, workingDir: File? = null): String {
        val pb = ProcessBuilder(cmd)
        pb.directory(workingDir)
        pb.environment()["PROOT_TMP_DIR"] = ProotInstaller.tmpDir(context).absolutePath
        pb.environment()["PROOT_LOADER"] = ProotInstaller.prootLoader(context).absolutePath
        pb.redirectErrorStream(true)
        val p = pb.start()
        val output = p.inputStream.bufferedReader().readText().trim()
        val exit = p.waitFor()
        if (exit != 0) {
            throw RuntimeException("Command exited with code $exit:\n$output")
        }
        return output
    }

    private fun runCommandWithOutput(cmd: List<String>): List<String> {
        val pb = ProcessBuilder(cmd)
        pb.environment()["PROOT_TMP_DIR"] = ProotInstaller.tmpDir(context).absolutePath
        pb.environment()["PROOT_LOADER"] = ProotInstaller.prootLoader(context).absolutePath
        pb.redirectErrorStream(true)
        val p = pb.start()
        val reader = p.inputStream.bufferedReader()
        val lines = reader.readLines()
        p.waitFor()
        return lines
    }

    private fun du(vararg paths: String): Long {
        val existingPaths = paths.filter { File(it).exists() }
        if (existingPaths.isEmpty()) return 0L
        return existingPaths.sumOf { path ->
            File(path).walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
    }
}
