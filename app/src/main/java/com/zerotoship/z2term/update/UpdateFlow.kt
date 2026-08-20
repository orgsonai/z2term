package com.zerotoship.z2term.update

import android.content.Context
import com.zerotoship.z2term.BuildConfig
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * 「確認 → ダウンロード → 入れ替えの確認画面」までを 1 本にまとめた手順 (0.8.371)。
 *
 * ⚠ **⚙設定のボタンと `z2-update` が同じここを通る**。片方だけに条件や順番を書くと、
 * 「端末からは入るのに設定からは入らない」のような、再現条件の分からない食い違いになる。
 * 文言だけは呼び出し側が持つ (GUI は strings.xml、CLI は [com.zerotoship.z2term.proot.Z2ApiMsg])。
 */
object UpdateFlow {

    /** 呼び出し側が文言を選ぶための結果。⚠ ここで文章を作らない。 */
    sealed interface Outcome {
        /** すでに最新。 */
        data class UpToDate(val current: String) : Outcome

        /** 新しい版がある (`checkOnly` のときはここで止まる)。 */
        data class Found(val current: String, val latest: String, val size: Long) : Outcome

        /** 落とし終えて、OS の確認画面を出した。⚠ **まだ入っていない**。 */
        data class Handed(val current: String, val latest: String, val size: Long) : Outcome

        /** 新版はあるが、そのリリースに APK が付いていない。 */
        data class NoApk(val latest: String, val pageUrl: String) : Outcome

        /** 「不明なアプリのインストール」が未許可。 */
        object NeedPermission : Outcome

        /** F-Droid / ストアから入った版なので、こちらでは入れ替えない。 */
        object ManagedByStore : Outcome

        /** 通信・保存・入れ替えのどこかで失敗。 */
        data class Failed(val reason: String) : Outcome
    }

    /**
     * @param checkOnly 確認だけで、落とさない
     * @param keepApk   null なら設定に従う (`z2-update --keep` が渡すときだけ true)
     * @param dirArg    落とし先。空なら設定に従う
     */
    suspend fun run(
        context: Context,
        checkOnly: Boolean,
        keepApk: Boolean? = null,
        dirArg: String? = null,
    ): Outcome {
        if (UpdateInstaller.isManagedByStore(context)) return Outcome.ManagedByStore

        val current = BuildConfig.VERSION_NAME
        when (val r = UpdateChecker.check()) {
            is UpdateResult.Failed -> return Outcome.Failed(r.reason)
            is UpdateResult.UpToDate -> return Outcome.UpToDate(r.current)
            is UpdateResult.Available -> {
                if (checkOnly) return Outcome.Found(current, r.latest, r.apkSize)
                val url = r.apkUrl ?: return Outcome.NoApk(r.latest, r.url)
                // ⚠ 落とす前に許可を見る。許可が無いまま進めると 20MB 落とした末に
                //    「確認画面が出ない」で終わり、何が足りないのか誰にも分からない。
                if (!UpdateInstaller.canInstall(context)) return Outcome.NeedPermission

                val settings = runCatching { AppSettings(context).flow.first() }.getOrNull()
                val keep = keepApk ?: (settings?.updateKeepApk ?: false)
                val dirPath = dirArg?.takeIf { it.isNotBlank() } ?: settings?.updateDownloadDir

                return withContext(Dispatchers.IO) {
                    runCatching {
                        val dir = UpdateInstaller.downloadDir(context, dirPath)
                        // ⚠ 名前はこちらで決める (リリース側の名前をそのまま使わない)。
                        //    掃除は `z2term-*.apk` だけを対象にするので、名前が揺れると消し残る。
                        val dest = File(dir, "z2term-${r.latest}.apk")
                        val apk = UpdateInstaller.download(url, dest, r.apkSize)
                        UpdateInstaller.install(context, apk, keep)
                        Outcome.Handed(current, r.latest, r.apkSize) as Outcome
                    }.getOrElse { e ->
                        Outcome.Failed(e.message ?: e.javaClass.simpleName)
                    }
                }
            }
        }
    }

    /** 「22.0 MB」のような人が読む大きさ。0 なら空文字 (大きさ不明を「0 B」と言わない)。 */
    fun humanSize(bytes: Long): String = when {
        bytes <= 0L -> ""
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        else -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    }
}
