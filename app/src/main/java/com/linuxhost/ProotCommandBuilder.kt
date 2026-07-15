package com.linuxhost

import android.os.Build
import java.io.File

object ProotCommandBuilder {

    fun buildLoginCommand(
        prootBin: String,
        rootfsDir: String,
        tmpDir: String,
        shell: String = "/bin/bash",
        workDir: String = "/root",
    ): List<String> = buildList {
        add(prootBin)
        add("--kill-on-exit")
        add("--link2symlink")
        add("--sysvipc")
        add("-L")
        add("--change-id=0:0")
        add("--rootfs=$rootfsDir")
        add("--cwd=$workDir")
        add("--env=LD_PRELOAD=")
        add("--bind=/dev")
        add("--bind=/proc")
        add("--bind=/sys")
        addNonMinimalBinds(rootfsDir)
        add("--kernel-release=${buildKernelRelease()}")
        add(shell)
        add("--login")
    }

    fun buildCommand(
        prootBin: String,
        rootfsDir: String,
        tmpDir: String,
        command: String,
    ): List<String> = buildList {
        add(prootBin)
        add("--kill-on-exit")
        add("--link2symlink")
        add("--sysvipc")
        add("-L")
        add("--change-id=0:0")
        add("--rootfs=$rootfsDir")
        add("--cwd=/root")
        add("--env=LD_PRELOAD=")
        add("--bind=/dev")
        add("--bind=/proc")
        add("--bind=/sys")
        addNonMinimalBinds(rootfsDir)
        add("--kernel-release=${buildKernelRelease()}")
        add("/usr/bin/env")
        add("-i")
        add("HOME=/root")
        add("USER=root")
        add("TERM=xterm-256color")
        add("/bin/bash")
        add("-c")
        add(command)
    }

    private fun MutableList<String>.addNonMinimalBinds(rootfsDir: String) {
        addTermuxDevBinds()
        addStorageBinds()
        addSystemBinds()
    }

    private fun MutableList<String>.addTermuxDevBinds() {
        add("--bind=/dev/urandom:/dev/random")
        if (!File("/dev/fd").exists()) {
            add("--bind=/proc/self/fd:/dev/fd")
        }
        for (i in 0..2) {
            val name = when (i) { 0 -> "stdin"; 1 -> "stdout"; 2 -> "stderr"; else -> continue }
            if (!File("/dev/$name").exists() && File("/proc/self/fd/$i").exists()) {
                add("--bind=/proc/self/fd/$i:/dev/$name")
            }
        }
    }

    private fun MutableList<String>.addStorageBinds() {
        for (path in listOf("/sdcard", "/storage/emulated/0")) {
            if (File(path).exists()) {
                add("--bind=$path")
            }
        }
    }

    private fun MutableList<String>.addSystemBinds() {
        for (path in listOf("/apex", "/system", "/vendor")) {
            if (File(path).exists()) {
                add("--bind=$path")
            }
        }
    }

    private fun buildKernelRelease(): String {
        val hostname = "localhost"
        val unameM = when (Build.CPU_ABI) {
            "aarch64", "arm64", "arm64-v8a" -> "aarch64"
            "armv7l", "armeabi-v7a" -> "armv7l"
            "x86_64" -> "x86_64"
            "x86" -> "i686"
            else -> "aarch64"
        }
        return "\\Linux\\$hostname\\6.17.0-PRoot-Distro\\#1 SMP PREEMPT_DYNAMIC Fri, 10 Oct 2025 00:00:00 +0000\\$unameM\\localdomain\\-1\\"
    }
}
