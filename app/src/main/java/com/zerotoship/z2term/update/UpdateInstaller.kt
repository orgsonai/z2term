package com.zerotoship.z2term.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 新しい版の APK を**取ってきて入れ替える**ところまでを受け持つ (0.8.371)。
 *
 * 手で APK を落として開く手順しか無かったのを、⚙設定のボタンと `z2-update` の 1 行に畳む。
 * 新版があるかどうかを調べるのは [UpdateChecker] の役目で、ここはその答えを受け取ってから動く。
 *
 * ⚠ **無音では入らない。** Android は自分自身の入れ替えに**必ず OS の確認画面**を挟む
 * (端末オーナーか root でない限り例外はない)。ここでできるのは「確認画面が出るところまでを
 * 全部やる」ことで、最後の 1 タップは必ず人が押す。⛔ **これを「自動更新」と書かない** —
 * 押さないと入らないものを自動と呼ぶと、押し忘れた人が「入ったつもり」で古い版を使い続ける。
 *
 * ⚠ **入れ替えの瞬間に自分が落とされる。** そのため「入った後に APK を消す」は当てにできない
 * ([UpdateStatusReceiver] が受け取れるとは限らない)。掃除は**次の起動時**にもやる
 * ([cleanupDownloads] を [com.zerotoship.z2term.Z2TermApplication] から呼ぶ)。
 */
object UpdateInstaller {
    private const val TAG = "UpdateInstaller"

    /** [UpdateStatusReceiver] が受ける、PackageInstaller からの結果通知。 */
    const val ACTION_STATUS = "com.zerotoship.z2term.action.UPDATE_STATUS"

    /** 入れ替え用に落とした APK の置き場 (既定)。⚠ 端末の他アプリからは見えない場所。 */
    private const val DOWNLOAD_SUBDIR = "update"

    /** 掃除の対象にする名前。⚠ **これ以外は消さない** (保存先を共有フォルダにもできるため)。 */
    private val APK_NAME = Regex("""^z2term-.*\.apk$""", RegexOption.IGNORE_CASE)

    /**
     * 「不明なアプリのインストール」を z2term に許しているか。
     *
     * ⚠ 断られている状態で [install] を呼ぶと、確認画面ではなく**何も起きない**ように見える。
     * 呼ぶ前に必ずここを見て、[unknownSourcesIntent] で設定画面へ送る。
     */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** 「不明なアプリのインストール」の設定画面 (z2term のページを直接開く)。 */
    fun unknownSourcesIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * この版を**入れた相手**のパッケージ名 (分かれば)。
     *
     * F-Droid のように**自分で更新を配るところから入れた版**では、アプリ内の入れ替えは出しゃばりで、
     * 配布側の方針にも反する。⇒ [isManagedByStore] が true のときは断り、そちらから更新してもらう。
     */
    private fun installerPackage(context: Context): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
    }.getOrNull()

    /**
     * 更新を配る側から入った版か (F-Droid / Play)。
     *
     * ⚠ **GitHub の APK を直に入れた版では null か "com.android.packageinstaller" 等**になり、
     * そのときだけ入れ替えを引き受ける。「どこから入ったか分からない」を**引き受ける側に倒す**のは、
     * この機能の入口が GitHub Releases 決め打ちだから (配布元が違えば版の比較自体が噛み合わない)。
     */
    fun isManagedByStore(context: Context): Boolean {
        val installer = installerPackage(context) ?: return false
        return installer.startsWith("org.fdroid") ||
            installer == "com.android.vending" ||
            installer.startsWith("com.aurora.store")
    }

    /** 落とし先のフォルダ。[override] が空なら アプリ内の [DOWNLOAD_SUBDIR]。 */
    fun downloadDir(context: Context, override: String?): File {
        val dir = if (override.isNullOrBlank()) File(context.cacheDir, DOWNLOAD_SUBDIR)
        else File(override)
        dir.mkdirs()
        return dir
    }

    /**
     * APK を落とす。⚠ **落とし切ってから名前を付ける** (`.part` で書いて最後に rename)。
     * 途中で切れたファイルが正しい名前で残ると、次に「もうある」と誤認して壊れた APK を入れにいく。
     *
     * @param expectedSize リリースが申告しているバイト数。0 なら検査しない。
     * @return 落とした APK
     */
    fun download(url: String, dest: File, expectedSize: Long): File {
        val part = File(dest.parentFile, dest.name + ".part")
        runCatching { part.delete() }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "z2term-update")
        }
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) throw IllegalStateException("HTTP $code")
            conn.inputStream.use { input ->
                part.outputStream().use { out -> input.copyTo(out, 64 * 1024) }
            }
        } finally {
            conn.disconnect()
        }
        if (expectedSize > 0 && part.length() != expectedSize) {
            val got = part.length()
            runCatching { part.delete() }
            throw IllegalStateException("size mismatch: $got != $expectedSize")
        }
        runCatching { dest.delete() }
        if (!part.renameTo(dest)) throw IllegalStateException("cannot rename ${part.name}")
        return dest
    }

    /**
     * 落とした APK でこのアプリを入れ替える。**確認画面を出すところまで**が仕事。
     *
     * ⚠ `setAppPackageName` に自分を指定する。指定しないと、入れ替えのつもりが**別アプリの導入**
     * として扱われる余地が残る (署名が違えば OS が弾くが、断り方が「解析エラー」になって理由が見えない)。
     *
     * @param keepApk 入れ終わっても APK を残すか (残さないなら [UpdateStatusReceiver] が消す)
     * @return PackageInstaller のセッション番号 (ログ用)
     */
    fun install(context: Context, apk: File, keepApk: Boolean): Int {
        require(apk.isFile && apk.length() > 0) { "no apk: ${apk.absolutePath}" }
        val pi = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        val sessionId = pi.createSession(params)
        pi.openSession(sessionId).use { session ->
            session.openWrite(apk.name, 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out, 64 * 1024) }
                session.fsync(out)
            }
            // ⚠ PendingIntent は **可変**にする (API 31+)。OS はここへ確認画面の Intent を
            //    詰めて返すので、不変にすると STATUS_PENDING_USER_ACTION で何も取り出せない。
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val intent = Intent(ACTION_STATUS)
                .setPackage(context.packageName)
                .putExtra(UpdateStatusReceiver.EXTRA_APK_PATH, apk.absolutePath)
                .putExtra(UpdateStatusReceiver.EXTRA_KEEP, keepApk)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
            session.commit(pending.intentSender)
        }
        Log.i(TAG, "install session $sessionId committed (${apk.name})")
        return sessionId
    }

    /**
     * 落とした APK を片付ける。**入れ替えの後で自分が生き残っている保証が無い**ので、
     * 次の起動でも呼ぶ。⚠ 消すのは `z2term-*.apk` だけ (保存先には人のファイルが同居しうる)。
     */
    fun cleanupDownloads(context: Context, override: String? = null) {
        val dirs = buildList {
            add(File(context.cacheDir, DOWNLOAD_SUBDIR))
            if (!override.isNullOrBlank()) add(File(override))
        }
        for (dir in dirs) {
            val files = dir.listFiles() ?: continue
            for (f in files) {
                if (!f.isFile) continue
                if (!APK_NAME.matches(f.name) && !f.name.endsWith(".apk.part")) continue
                runCatching { f.delete() }
            }
        }
    }
}
