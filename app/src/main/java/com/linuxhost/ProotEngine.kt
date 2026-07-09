package com.linuxhost

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
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

    private val prootDownloadUrl: String get() =
        "https://skirsten.github.io/proot-portable-android-binaries/$arch/proot"
    private val rootfsDownloadUrl: String get() =
        "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04-base-${archStringMap[arch]}.tar.gz"

    private val _status = MutableStateFlow(InstanceStatus.NOT_INSTALLED)
    val status: StateFlow<InstanceStatus> = _status.asStateFlow()

    private val _progress = MutableSharedFlow<Progress>(replay = 1, extraBufferCapacity = 64)
    val progress: SharedFlow<Progress> = _progress.asSharedFlow()

    private var process: Process? = null
    private val filesDir: File = context.filesDir
    val rootfsDir: File get() = File(filesDir, "ubuntu")
    val prootBin: File get() = File(filesDir, "proot")

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
            when {
                process?.isAlive == true -> _status.value = InstanceStatus.RUNNING
                rootfsDir.exists() && prootBin.exists() -> _status.value = InstanceStatus.INSTALLED
                rootfsDir.exists() -> _status.value = InstanceStatus.INSTALLING
                else -> _status.value = InstanceStatus.NOT_INSTALLED
            }
        } catch (e: Exception) {
            _status.value = InstanceStatus.ERROR
        }
    }

    suspend fun downloadProotBinary() = withContext(Dispatchers.IO) {
        _status.value = InstanceStatus.INSTALLING
        _progress.emit(Progress(0, "Downloading PRoot binary..."))
        try {
            downloadFile(URL(prootDownloadUrl), prootBin)
            prootBin.setExecutable(true)
            _progress.emit(Progress(100, "PRoot binary downloaded and made executable"))
        } catch (e: Exception) {
            _status.value = InstanceStatus.ERROR
            _progress.emit(Progress(0, "Failed to download PRoot: ${e.message}"))
            throw e
        }
    }

    suspend fun downloadRootfs(): String = withContext(Dispatchers.IO) {
        _status.value = InstanceStatus.INSTALLING
        val tempFile = File(filesDir, "ubuntu-rootfs.tar.gz")
        _progress.emit(Progress(0, "Downloading Ubuntu rootfs..."))
        try {
            downloadFile(URL(rootfsDownloadUrl), tempFile)
            _progress.emit(Progress(100, "Ubuntu rootfs downloaded"))
            tempFile.absolutePath
        } catch (e: Exception) {
            _status.value = InstanceStatus.ERROR
            _progress.emit(Progress(0, "Failed to download rootfs: ${e.message}"))
            throw e
        }
    }

    suspend fun installRootfs(tarballPath: String) = withContext(Dispatchers.IO) {
        _status.value = InstanceStatus.INSTALLING
        try {
            File(filesDir, "tmp").mkdirs()
            val hasTar = try {
                Runtime.getRuntime().exec("which tar").also { it.waitFor() }.exitValue() == 0
            } catch (_: Exception) {
                false
            }
            if (!hasTar) {
                val msg = "tar not found. Install it with: pkg install tar"
                _progress.emit(Progress(0, msg))
                throw RuntimeException(msg)
            }
            rootfsDir.mkdirs()
            _progress.emit(Progress(0, "Extracting rootfs, this may take a while..."))
            runCommand(listOf("tar", "-xf", tarballPath, "-C", rootfsDir.absolutePath))
            File(tarballPath).delete()
            _progress.emit(Progress(100, "Rootfs extracted successfully"))
            _status.value = InstanceStatus.INSTALLED
        } catch (e: Exception) {
            _status.value = InstanceStatus.ERROR
            _progress.emit(Progress(0, "Failed to extract rootfs: ${e.message}"))
            throw e
        }
    }

    suspend fun launch() = withContext(Dispatchers.IO) {
        try {
            File(filesDir, "tmp").mkdirs()
            val cmd = buildList {
                add(prootBin.absolutePath)
                add("-0")
                add("--link2symlink")
                add("-b")
                add("/proc:/proc")
                add("-b")
                add("/sys:/sys")
                add("-b")
                add("/dev:/dev")
                add("-b")
                add("/sdcard:/sdcard")
                add("-r")
                add(rootfsDir.absolutePath)
                add("/usr/bin/env")
                add("-i")
                add("HOME=/root")
                add("USER=root")
                add("TERM=xterm-256color")
                add("/bin/bash")
                add("--login")
            }
            val pb = ProcessBuilder(cmd)
            pb.environment()["PROOT_TMP_DIR"] = "${filesDir.absolutePath}/tmp"
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

    suspend fun remove() = withContext(Dispatchers.IO) {
        try {
            stop()
            if (rootfsDir.exists()) {
                rootfsDir.deleteRecursively()
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
            val output = runCommandWithOutput(prootBashCommand("apt update && apt upgrade -y"))
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
                val output = runCommandWithOutput(prootBashCommand(cmd))
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

    private fun canRun(): Boolean = prootBin.exists() && rootfsDir.exists()

    suspend fun executeCommand(command: String): String = withContext(Dispatchers.IO) {
        runCommand(prootBashCommand(command))
    }

    suspend fun getPackageCount(): Int = withContext(Dispatchers.IO) {
        if (!canRun()) return@withContext 0
        try {
            val output = runCommand(prootBashCommand("dpkg --list 2>/dev/null | wc -l"))
            (output.toIntOrNull()?.minus(5))?.coerceAtLeast(0) ?: 0
        } catch (_: Exception) { 0 }
    }

    suspend fun getPendingUpdates(): Int = withContext(Dispatchers.IO) {
        if (!canRun()) return@withContext 0
        try {
            val output = runCommand(prootBashCommand("apt list --upgradable 2>/dev/null | wc -l"))
            (output.toIntOrNull()?.minus(1))?.coerceAtLeast(0) ?: 0
        } catch (_: Exception) { 0 }
    }

    private fun prootBashCommand(command: String): List<String> = buildList {
        add(prootBin.absolutePath)
        add("-0")
        add("--link2symlink")
        add("-r")
        add(rootfsDir.absolutePath)
        add("/bin/bash")
        add("-c")
        add(command)
    }

    private fun runCommand(cmd: List<String>, workingDir: File? = null): String {
        val pb = ProcessBuilder(cmd)
        pb.directory(workingDir)
        pb.environment()["PROOT_TMP_DIR"] = "${filesDir.absolutePath}/tmp"
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
        pb.environment()["PROOT_TMP_DIR"] = "${filesDir.absolutePath}/tmp"
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
        val cmd = buildList {
            add("du")
            add("-sb")
            add("--total")
            addAll(existingPaths)
        }
        return try {
            val output = runCommand(cmd)
            val lastLine = output.lines().lastOrNull() ?: return 0L
            lastLine.split("\\s+".toRegex()).firstOrNull()?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private suspend fun downloadFile(url: URL, destination: File) {
        try {
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.connect()

            val totalBytes = connection.contentLengthLong
            var downloaded = 0L

            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (totalBytes > 0) {
                            val pct = ((downloaded.toDouble() / totalBytes) * 100).toInt()
                            _progress.emit(Progress(pct, "Downloading...", downloaded, totalBytes))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            _progress.emit(Progress(0, "Network error: ${e.message}"))
            throw e
        }
    }
}
