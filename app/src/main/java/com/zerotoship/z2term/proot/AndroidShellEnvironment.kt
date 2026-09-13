package com.zerotoship.z2term.proot

import android.content.Context
import android.system.Os
import com.zerotoship.z2term.settings.LocaleHelper
import java.io.File

/** Private launch files stay outside shared HOME and every distro's /usr/local/bin. */
internal object AndroidShellEnvironment {
    data class Prepared(val home: File, val env: Array<String>)

    @Synchronized
    fun prepare(context: Context, sessionId: String): Prepared {
        val base = File(context.filesDir, "android-shell")
        val bin = File(base, "bin").apply { mkdirs() }
        val scripts = File(base, "scripts").apply { mkdirs() }
        val home = File(context.filesDir, "shared_home").apply { mkdirs() }
        File(home, ".z2term/macros").mkdirs()
        val external = checkNotNull(context.getExternalFilesDir(null)) { "Android API bridge storage unavailable" }
        val native = File(context.applicationInfo.nativeLibraryDir, "libz2android.so")
        check(native.isFile) { "Missing bundled Android command launcher" }
        AndroidShellScripts.create(LocaleHelper.language(context), bin.absolutePath, File(external, "z2api").absolutePath)
            .forEach { (name, body) ->
                writeIfChanged(File(scripts, name), body)
                link(File(bin, name), native)
            }
        val attach = File(context.applicationInfo.nativeLibraryDir, "libz2attach.so")
        if (attach.isFile) link(File(bin, "z2attach"), attach)
        val rc = File(base, "mkshrc")
        writeIfChanged(rc, "export PS1='$ '\nexport PS2='> '\nset +o multiline 2>/dev/null\n")
        return Prepared(home, buildList {
            add("HOME=${home.absolutePath}")
            add("TERM=xterm-256color")
            add("PATH=${bin.absolutePath}:/system/bin:/system/xbin:/vendor/bin")
            add("TMPDIR=${context.cacheDir.absolutePath}")
            add("Z2_ANDROID_SCRIPTS=${scripts.absolutePath}")
            add("Z2_ATTACH_SOCK=${File(home, ".z2term/attach.sock").absolutePath}")
            add("ENV=${rc.absolutePath}")
            add("PS1=$ ")
            add("PS2=> ")
            if (sessionId.isNotBlank()) add("Z2_SESSION_ID=$sessionId")
        }.toTypedArray())
    }

    private fun writeIfChanged(file: File, text: String) {
        if (file.isFile && file.readText() == text) return
        val temp = File.createTempFile(".update-", ".tmp", file.parentFile)
        try {
            temp.writeText(text)
            Os.rename(temp.absolutePath, file.absolutePath)
        } finally { temp.delete() }
    }

    private fun link(file: File, target: File) {
        if (runCatching { Os.readlink(file.absolutePath) }.getOrNull() == target.absolutePath) return
        val temp = File.createTempFile(".link-", ".tmp", file.parentFile)
        try {
            check(temp.delete())
            Os.symlink(target.absolutePath, temp.absolutePath)
            Os.rename(temp.absolutePath, file.absolutePath)
        } finally { temp.delete() }
    }
}
