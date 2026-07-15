package com.linuxhost

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RootfsExtractor {

    private val ROOTFS_DIRS = setOf(
        "bin", "dev", "etc", "home", "lib", "lib32", "lib64", "libx32",
        "media", "mnt", "opt", "proc", "root", "run", "sbin", "srv",
        "sys", "tmp", "usr", "var",
    )

    data class TarEntry(
        val name: String,
        val typeFlag: Byte,
        val linkName: String,
        val mode: Long,
        val size: Long,
    )

    private object TarType {
        const val REGULAR: Byte = 0
        const val REGULAR_ALT: Byte = '0'.code.toByte()
        const val HARD_LINK: Byte = '1'.code.toByte()
        const val SYMLINK: Byte = '2'.code.toByte()
        const val DIRECTORY: Byte = '5'.code.toByte()
    }

    fun detectStripCount(memberNames: List<String>): Int {
        val sample = memberNames.take(500)
        var bestStrip = 0
        var bestScore = -1
        for (strip in 0..4) {
            var score = 0
            for (name in sample) {
                val parts = name.trim('/').split('/')
                if (parts.size > strip && parts[strip] in ROOTFS_DIRS) {
                    score++
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestStrip = strip
            }
        }
        return bestStrip
    }

    suspend fun extract(
        tarball: File,
        rootfsDir: File,
        onEntry: suspend (String, Int, Int) -> Unit = { _, _, _ -> },
    ) = withContext(Dispatchers.IO) {
        rootfsDir.mkdirs()

        val memberNames = mutableListOf<String>()
        scanAllMembers(tarball, memberNames)

        val strip = detectStripCount(memberNames)
        val total = memberNames.size
        var current = 0
        val pendingLinks = mutableListOf<Pair<TarEntry, File>>()

        openTarStream(tarball).use { stream ->
            val buf = ByteArray(512)
            while (readBlock(buf, stream)) {
                if (buf.all { it == 0.toByte() }) break
                val entry = parseHeader(buf) ?: continue
                current++
                val strippedName = stripLeading(entry.name, strip)
                val outFile = File(rootfsDir, strippedName)

                onEntry(strippedName.substringAfterLast('/'), current, total)

                when (entry.typeFlag) {
                    TarType.DIRECTORY -> outFile.mkdirs()
                    TarType.HARD_LINK, TarType.SYMLINK -> {
                        pendingLinks += entry to outFile
                    }
                    else -> {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { out ->
                            copyExact(stream, out, entry.size)
                        }
                        applyPermissions(outFile, entry.mode, entry.typeFlag)
                    }
                }
                skipPadding(stream, entry.size)
            }
        }

        for ((entry, outFile) in pendingLinks) {
            when (entry.typeFlag) {
                TarType.HARD_LINK -> {
                    val strippedTarget = stripLeading(entry.linkName, strip)
                    val linkTarget = File(rootfsDir, strippedTarget)
                    if (linkTarget.exists()) {
                        outFile.parentFile?.mkdirs()
                        linkTarget.copyTo(outFile, overwrite = true)
                        applyPermissions(outFile, entry.mode, entry.typeFlag)
                    }
                }
                TarType.SYMLINK -> {
                    outFile.parentFile?.mkdirs()
                    try { java.nio.file.Files.delete(outFile.toPath()) } catch (_: Exception) {}
                    android.system.Os.symlink(entry.linkName, outFile.absolutePath)
                }
            }
        }
    }

    private fun scanAllMembers(tarball: File, outNames: MutableList<String>) {
        openTarStream(tarball).use { stream ->
            val buf = ByteArray(512)
            while (readBlock(buf, stream)) {
                if (buf.all { it == 0.toByte() }) break
                val entry = parseHeader(buf) ?: continue
                outNames.add(entry.name)
                skipPadding(stream, entry.size)
            }
        }
    }

    private fun openTarStream(tarball: File): InputStream {
        val probe = FileInputStream(tarball)
        val magic = ByteArray(2)
        val isGzip = probe.read(magic) == 2 &&
            magic[0] == 0x1F.toByte() && magic[1] == 0x8B.toByte()
        probe.close()

        return if (isGzip) {
            GZIPInputStream(FileInputStream(tarball))
        } else {
            FileInputStream(tarball)
        }
    }

    private fun readBlock(buf: ByteArray, stream: InputStream): Boolean {
        var offset = 0
        while (offset < buf.size) {
            val read = stream.read(buf, offset, buf.size - offset)
            if (read == -1) return offset > 0
            offset += read
        }
        return true
    }

    private fun copyExact(stream: InputStream, output: FileOutputStream, size: Long) {
        var remaining = size
        val buf = ByteArray(8192)
        while (remaining > 0) {
            val toRead = minOf(buf.size.toLong(), remaining).toInt()
            val read = stream.read(buf, 0, toRead)
            if (read == -1) break
            output.write(buf, 0, read)
            remaining -= read
        }
    }

    private fun skipPadding(stream: InputStream, size: Long) {
        val remainder = size % 512
        if (remainder == 0L) return
        val padding = (512 - remainder).toInt()
        val skipBuf = ByteArray(padding)
        readBlock(skipBuf, stream)
    }

    private fun stripLeading(name: String, strip: Int): String {
        if (strip == 0) return name
        val parts = name.trim('/').split('/')
        return if (parts.size > strip) {
            parts.drop(strip).joinToString("/")
        } else {
            name
        }
    }

    private fun applyPermissions(file: File, mode: Long, typeFlag: Byte) {
        if (typeFlag == TarType.SYMLINK) return
        try {
            file.setReadable(mode and 0b100_000_000L != 0L, false)
            file.setWritable(mode and 0b010_000_000L != 0L, false)
            file.setExecutable(mode and 0b001_000_000L != 0L, false)
        } catch (_: Exception) {}
    }

    fun writeResolvConf(rootfsDir: File) {
        val file = File(rootfsDir, "etc/resolv.conf")
        file.parentFile?.mkdirs()
        file.writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
    }

    fun writeHosts(rootfsDir: File) {
        val file = File(rootfsDir, "etc/hosts")
        file.parentFile?.mkdirs()
        file.writeText(
            """# IPv4.
127.0.0.1   localhost.localdomain localhost

# IPv6.
::1         localhost.localdomain localhost ip6-localhost ip6-loopback
fe00::0     ip6-localnet
ff00::0     ip6-mcastprefix
ff02::1     ip6-allnodes
ff02::2     ip6-allrouters
ff02::3     ip6-allhosts
"""
        )
    }

    fun registerAndroidIds(rootfsDir: File) {
        val uid = android.os.Process.myUid()
        val username = "linuxhost"

        for (path in listOf("etc/passwd", "etc/shadow", "etc/group", "etc/gshadow")) {
            val file = File(rootfsDir, path)
            if (file.exists()) {
                try { file.setReadOnly() } catch (_: Exception) {}
            }
        }

        val passwdFile = File(rootfsDir, "etc/passwd")
        if (passwdFile.exists()) {
            try {
                passwdFile.appendText(
                    "$username:x:$uid:$uid:LinuxHost:/:/sbin/nologin\n"
                )
            } catch (_: Exception) {}
        }

        val shadowFile = File(rootfsDir, "etc/shadow")
        if (shadowFile.exists()) {
            try {
                shadowFile.appendText("$username:*:18446:0:99999:7:::\n")
            } catch (_: Exception) {}
        }
    }

    private fun writeFakeProcFiles(rootfsDir: File) {
        val procDir = File(rootfsDir, "proc")
        procDir.mkdirs()

        // /proc/stat — fake aggregate CPU line + enough for tools like htop/top
        File(procDir, "stat").writeText(
            "cpu  0 0 0 0 0 0 0 0 0 0\n" +
            "cpu0 0 0 0 0 0 0 0 0 0 0\n" +
            "intr 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0\n" +
            "ctxt 0\n" +
            "btime 0\n" +
            "processes 0\n" +
            "procs_running 1\n" +
            "procs_blocked 0\n" +
            "softirq 0 0 0 0 0 0 0 0 0 0 0\n"
        )

        // /proc/version — proot-distro style
        File(procDir, "version").writeText(
            "Linux version 6.17.0-PRoot-Distro (proot@localhost) " +
            "(Ubuntu clang version 14.0.0) #1 SMP PREEMPT_DYNAMIC Fri, 10 Oct 2025 00:00:00 +0000\n"
        )

        // /proc/uptime — seconds since boot
        File(procDir, "uptime").writeText("0.00 0.00\n")

        // /proc/loadavg — 1/5/15 min averages + running/total
        File(procDir, "loadavg").writeText("0.00 0.00 0.00 1/1 0\n")

        // /proc/vmstat — minimal, enough for tools that parse it
        File(procDir, "vmstat").writeText(
            "nr_free_pages 0\n" +
            "nr_inactive_anon 0\n" +
            "nr_active_anon 0\n" +
            "nr_inactive_file 0\n" +
            "nr_active_file 0\n" +
            "pswpin 0\n" +
            "pswpout 0\n" +
            "pgfault 0\n" +
            "pgmajfault 0\n"
        )
    }

    fun postExtractionFixups(rootfsDir: File) {
        writeResolvConf(rootfsDir)
        writeHosts(rootfsDir)
        registerAndroidIds(rootfsDir)
        writeFakeProcFiles(rootfsDir)

        val tmpDir = File(rootfsDir, "tmp")
        tmpDir.mkdirs()
        try { tmpDir.setExecutable(true, false) } catch (_: Exception) {}
    }

    suspend fun downloadFile(url: URL, destination: File, onProgress: (Long, Long) -> Unit = { _, _ -> }) =
        withContext(Dispatchers.IO) {
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
                        onProgress(downloaded, totalBytes)
                    }
                }
            }
        }

    private fun parseHeader(header: ByteArray): TarEntry? {
        if (header[257] != 'u'.code.toByte() || header[258] != 's'.code.toByte()) {
            if (header.all { it == 0.toByte() }) return null
        }

        val name = readString(header, 0, 100).trim('\u0000')
        if (name.isEmpty()) return null

        val mode = readOctal(header, 100, 8)
        val size = readOctal(header, 124, 12)
        val typeFlag = header[156]
        val linkName = readString(header, 157, 100).trim('\u0000')

        val prefix = readString(header, 345, 155).trim('\u0000')
        val fullName = if (prefix.isNotEmpty()) "$prefix/$name" else name

        return TarEntry(
            name = fullName,
            typeFlag = typeFlag,
            linkName = linkName,
            mode = mode,
            size = size,
        )
    }

    private fun readString(buf: ByteArray, offset: Int, len: Int): String {
        val end = (offset until offset + len).firstOrNull { buf[it] == 0.toByte() } ?: (offset + len)
        return String(buf, offset, end - offset, Charsets.US_ASCII)
    }

    private fun readOctal(buf: ByteArray, offset: Int, len: Int): Long {
        val str = readString(buf, offset, len).trim()
        if (str.isEmpty()) return 0L
        return try { str.toLong(8) } catch (_: Exception) { 0L }
    }
}
