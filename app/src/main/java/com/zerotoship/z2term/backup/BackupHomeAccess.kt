package com.zerotoship.z2term.backup

import android.content.Context
import android.os.Process
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Keep the app's backup directories usable after a real-root shell writes to shared HOME. */
internal object BackupHomeAccess {
    fun prepare(context: Context) {
        val home = File(context.filesDir.canonicalFile, "shared_home")
        val state = File(home, ".z2term")
        val dirs = listOf(File(state, "when"), File(state, "macros"), File(state, "edge"))
        val parents = listOf(home, state) + dirs
        // These paths belong to the app. Never repair a symlink into some other directory.
        parents.forEach { rejectSymlink(it) }
        parents.forEach { file ->
            runCatching {
                val stat = Os.lstat(file.path)
                if (stat.st_uid == Process.myUid()) Os.chmod(file.path, stat.st_mode or 0x1c0)
            }
        }
        if (ready(home, state, dirs)) return

        val uid = Process.myUid()
        val gid = Os.getgid()
        val paths = parents.joinToString(" ") { quote(it.path) }
        val script = """
            set -e
            [ "${'$'}(id -u)" = 0 ]
            for p in $paths; do [ ! -L "${'$'}p" ]; done
            mkdir -p ${dirs.joinToString(" ") { quote(it.path) }}
            chgrp $gid ${quote(home.path)}
            chmod g+rx ${quote(home.path)}
            chown $uid:$gid ${quote(state.path)}
            chmod u+rwx ${quote(state.path)}
            for p in ${dirs.joinToString(" ") { quote(it.path) }}; do
                find "${'$'}p" -xdev -type d -exec chown $uid:$gid {} \; -exec chmod u+rwx {} \;
                find "${'$'}p" -xdev -type f -exec chown $uid:$gid {} \; -exec chmod u+rw {} \;
            done
        """.trimIndent()
        // Use the existing root authorization only when normal app access is actually blocked.
        val process = runCatching { ProcessBuilder("su", "-c", script).redirectErrorStream(true).start() }
            .getOrNull() ?: throw IOException("Backup directories are not accessible")
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                throw IOException("Timed out preparing backup directories")
            }
            if (process.exitValue() != 0 || !ready(home, state, dirs)) {
                throw IOException("Backup directories are not accessible")
            }
        } finally {
            process.destroy()
        }
    }

    private fun ready(home: File, state: File, dirs: List<File>): Boolean {
        if (!(home.isDirectory || home.mkdirs()) || !home.canRead() || !home.canExecute()) return false
        if (!state.exists() && !state.mkdirs()) return false
        if (!state.canRead() || !state.canWrite() || !state.canExecute()) return false
        return dirs.all { dir ->
            (dir.isDirectory || dir.mkdirs()) && dir.canRead() && dir.canWrite() && dir.canExecute() &&
                readableAndWritable(dir)
        }
    }

    private fun readableAndWritable(dir: File): Boolean {
        val children = dir.listFiles() ?: return false
        return children.all { file ->
            val stat = runCatching { Os.lstat(file.path) }.getOrNull() ?: return@all false
            when {
                OsConstants.S_ISLNK(stat.st_mode) -> true
                file.isDirectory -> file.canRead() && file.canWrite() && file.canExecute() &&
                    readableAndWritable(file)
                else -> file.canRead() && file.canWrite()
            }
        }
    }

    private fun rejectSymlink(file: File) {
        val stat = runCatching { Os.lstat(file.path) }.getOrNull() ?: return
        if (OsConstants.S_ISLNK(stat.st_mode)) throw IOException("Backup directory is a symbolic link")
    }

    private fun quote(text: String): String = "'" + text.replace("'", "'\\''") + "'"
}
