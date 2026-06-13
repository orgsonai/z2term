package com.zerotoship.z2term

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 間欠的な起動クラッシュ ("たまにアプリ立ち上げた瞬間落ちる") を adb 無しで捕まえるための
 * クラッシュロガー。`Application.onCreate` の冒頭で [install] して、捕捉漏れのない位置に張る。
 *
 * - 直前のデフォルトハンドラに必ずチェーンする (OS のクラッシュダイアログ / プロセス終了は従来どおり)。
 * - 落ちる直前にスタックトレースを `filesDir/crash/crash-<時刻>.txt` へ同期書き込みする
 *   (最新は `crash-last.txt` にも複製)。古いものは [MAX_FILES] 件までで間引く。
 * - 端末タブから `cat <filesDir>/crash/crash-last.txt` で読める。filesDir の絶対パスは
 *   起動時に logcat へも出すので、再現後にパスが分かる。
 */
object CrashLogger {
    private const val TAG = "Z2Crash"
    private const val DIR_NAME = "crash"
    private const val LAST_NAME = "crash-last.txt"
    private const val MAX_FILES = 20

    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val dir = File(app.filesDir, DIR_NAME)
        Log.i(TAG, "crash logs dir = ${dir.absolutePath}")

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(dir, thread, throwable) }
                .onFailure { Log.w(TAG, "failed to persist crash: ${it.message}") }
            // OS / 既存ハンドラへ必ず委譲してプロセス終了の挙動を変えない。
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrash(dir: File, thread: Thread, throwable: Throwable) {
        if (!dir.exists()) dir.mkdirs()
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val sw = StringWriter()
        PrintWriter(sw).use { pw ->
            pw.println("time:    $ts")
            pw.println("version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            pw.println("flavor:  ${BuildConfig.FLAVOR} / ${BuildConfig.BUILD_TYPE}")
            pw.println("device:  ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            pw.println("thread:  ${thread.name} (id=${thread.id})")
            pw.println("--- stacktrace ---")
            throwable.printStackTrace(pw)
        }
        val text = sw.toString()
        File(dir, "crash-$ts.txt").writeText(text)
        File(dir, LAST_NAME).writeText(text)
        prune(dir)
    }

    /** crash-*.txt を新しい順に [MAX_FILES] 件残して古いものを削除する (crash-last.txt は対象外)。 */
    private fun prune(dir: File) {
        val files = dir.listFiles { f -> f.name.startsWith("crash-") && f.name != LAST_NAME }
            ?: return
        if (files.size <= MAX_FILES) return
        files.sortedByDescending { it.lastModified() }
            .drop(MAX_FILES)
            .forEach { runCatching { it.delete() } }
    }
}
