package com.linuxhost

import android.content.Context
import java.io.File

object ProotInstaller {

    fun prootBin(context: Context): File = File(context.filesDir, "proot")
    fun prootLoader(context: Context): File = File(context.filesDir, "libexec/proot/loader")
    fun containersDir(context: Context): File = File(context.filesDir, "containers")
    fun rootfsDir(context: Context, name: String = "ubuntu"): File =
        File(containersDir(context), "$name/rootfs")
    fun manifestFile(context: Context, name: String = "ubuntu"): File =
        File(containersDir(context), "$name/manifest.json")
    fun tmpDir(context: Context): File = File(context.filesDir, "tmp")

    fun ensureInstalled(context: Context): ProotPaths {
        val proot = prootBin(context)
        val loader = prootLoader(context)

        if (!proot.exists() || !proot.canExecute()) {
            proot.parentFile?.mkdirs()
            context.assets.open("bin/proot").use { input ->
                proot.outputStream().use { out -> input.copyTo(out) }
            }
            proot.setExecutable(true, false)
        }

        if (!loader.exists() || !loader.canExecute()) {
            loader.parentFile?.mkdirs()
            context.assets.open("libexec/proot/loader").use { input ->
                loader.outputStream().use { out -> input.copyTo(out) }
            }
            loader.setExecutable(true, false)
        }

        tmpDir(context).mkdirs()

        return ProotPaths(
            prootBin = proot.absolutePath,
            prootLoader = loader.absolutePath,
            tmpDir = tmpDir(context).absolutePath,
        )
    }

    fun isInstalled(context: Context): Boolean {
        val proot = prootBin(context)
        val loader = prootLoader(context)
        return proot.exists() && proot.canExecute() && loader.exists() && loader.canExecute()
    }

    data class ProotPaths(
        val prootBin: String,
        val prootLoader: String,
        val tmpDir: String,
    )
}
