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
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.FileInputStream
import java.io.BufferedInputStream
import java.util.zip.GZIPInputStream

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
        val errors = mutableListOf<String>()
        for (urlStr in rootfsMirrors) {
            try {
                downloadFile(URL(urlStr), tempFile)
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

    suspend fun installRootfs(tarballPath: String) = withContext(Dispatchers.IO) {
        _status.value = InstanceStatus.INSTALLING
        try {
            rootfsDir.mkdirs()
            _progress.emit(Progress(0, "Extracting rootfs (JVM), this may take a while..."))

            extractTarGz(File(tarballPath), rootfsDir) { entry, current, total ->
                val pct = if (total > 0) (current * 100 / total) else 0
                _progress.emit(Progress(pct, "Extracting ${entry.name}..."))
            }
            File(tarballPath).delete()

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

    private fun extractTarGz(
        tarball: File,
        destDir: File,
        onEntry: (TarArchiveEntry, Int, Int) -> Unit = { _, _, _ -> },
    ) {
        val totalEntries = countTarEntries(tarball)
        var current = 0

        BufferedInputStream(FileInputStream(tarball)).use { fileIn ->
            GZIPInputStream(fileIn).use { gzipIn ->
                TarArchiveInputStream(gzipIn).use { tarIn ->
                    var entry: TarArchiveEntry? = tarIn.nextTarEntry
                    while (entry != null) {
                        current++
                        onEntry(entry, current, totalEntries)

                        val outFile = File(destDir, entry.name)

                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else if (entry.isLink) {
                            val linkTarget = File(destDir, entry.linkName)
                            if (linkTarget.exists()) {
                                linkTarget.copyTo(outFile, overwrite = true)
                            }
                        } else if (entry.isSymbolicLink) {
                            if (!outFile.parentFile.exists()) {
                                outFile.parentFile.mkdirs()
                            }
                            val success = try {
                                val p = Runtime.getRuntime().exec(
                                    arrayOf("ln", "-sf", entry.linkName, outFile.absolutePath)
                                )
                                p.waitFor() == 0
                            } catch (_: Exception) { false }
                            if (!success) {
                                val resolved = File(destDir, entry.linkName)
                                if (resolved.exists()) {
                                    resolved.copyTo(outFile, overwrite = true)
                                }
                            }
                        } else {
                            if (!outFile.parentFile.exists()) {
                                outFile.parentFile.mkdirs()
                            }
                            outFile.outputStream().use { out ->
                                tarIn.copyTo(out)
                            }
                        }

                        outFile.setReadable(entry.permissions and 0b100_000_000 != 0, false)
                        outFile.setWritable(entry.permissions and 0b010_000_000 != 0, false)
                        outFile.setExecutable(entry.permissions and 0b001_000_000 != 0, false)

                        entry = tarIn.nextTarEntry
                    }
                }
            }
        }
    }

    private fun countTarEntries(tarball: File): Int {
        var count = 0
        BufferedInputStream(FileInputStream(tarball)).use { fileIn ->
            GZIPInputStream(fileIn).use { gzipIn ->
                TarArchiveInputStream(gzipIn).use { tarIn ->
                    while (tarIn.nextTarEntry != null) count++
                }
            }
        }
        return count
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
        return existingPaths.sumOf { path ->
            File(path).walkTopDown().filter { it.isFile }.sumOf { it.length() }
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
