package com.zerotoship.z2term.service

import android.content.Context
import android.util.Base64
import android.util.Log
import com.zerotoship.z2term.channel.KeystoreCrypt
import java.io.File

/**
 * 自動化ルールの**隠す返事** (パスワード) の置き場 (1.0.12)。
 *
 * ルールファイル (`~/.z2term/when/<id>.rule`) は端末から読める平文で、同期やバックアップにも
 * 乗る。そこへパスワードを書かないよう、中身だけをアプリの中 (`filesDir/when_secrets/<id>`) に
 * Android Keystore の鍵で暗号化して置く。端末 (Linux 側) からは見えない。
 *
 * 1 行 = `base64(聞かれる文言)<TAB>暗号文`。鍵は端末ごとなので、別の端末へ写しても読めない
 * (入れ直してもらう)。
 */
object WhenSecrets {

    private const val TAG = "WhenSecrets"

    private fun file(context: Context, ruleId: String): File =
        File(File(context.filesDir, "when_secrets"), ruleId)

    /** [ruleId] の 聞かれる文言 → 返事。読めない行 (鍵が変わった等) は無いものとして扱う。 */
    fun read(context: Context, ruleId: String): Map<String, String> {
        val f = file(context, ruleId)
        if (ruleId.isEmpty() || !f.isFile) return emptyMap()
        val out = LinkedHashMap<String, String>()
        runCatching {
            f.readLines().forEach { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) return@forEach
                runCatching {
                    val prompt = String(Base64.decode(line.substring(0, tab), Base64.NO_WRAP), Charsets.UTF_8)
                    out[prompt] = KeystoreCrypt.decrypt(line.substring(tab + 1))
                }.onFailure { Log.w(TAG, "unreadable secret in $ruleId: ${it.message}") }
            }
        }
        return out
    }

    /** [ruleId] に保存済みの文言。編集画面が「保存済み」と出すために使う (中身は返さない)。 */
    fun storedPrompts(context: Context, ruleId: String): Set<String> {
        val f = file(context, ruleId)
        if (ruleId.isEmpty() || !f.isFile) return emptySet()
        // 復号しない (画面を開くたびに鍵を使わない)。文言の側だけ読む。
        return runCatching {
            f.readLines().mapNotNull { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) null
                else runCatching {
                    String(Base64.decode(line.substring(0, tab), Base64.NO_WRAP), Charsets.UTF_8)
                }.getOrNull()
            }.toSet()
        }.getOrDefault(emptySet())
    }

    /**
     * [keep] にある文言だけを残し、[updates] (文言 → 新しい返事) を上書きして書く。
     * 残すものが無ければファイルごと消す。
     */
    fun save(context: Context, ruleId: String, keep: Collection<String>, updates: Map<String, String>): Boolean {
        if (ruleId.isEmpty()) return false
        val merged = LinkedHashMap<String, String>()
        val old = read(context, ruleId)
        keep.forEach { prompt -> (updates[prompt] ?: old[prompt])?.let { merged[prompt] = it } }
        val f = file(context, ruleId)
        if (merged.isEmpty()) {
            f.delete()
            return true
        }
        return runCatching {
            f.parentFile?.mkdirs()
            f.writeText(merged.entries.joinToString("\n", postfix = "\n") { (prompt, text) ->
                Base64.encodeToString(prompt.toByteArray(Charsets.UTF_8), Base64.NO_WRAP) + "\t" +
                    KeystoreCrypt.encrypt(text)
            })
        }.onFailure { Log.w(TAG, "save failed ($ruleId): ${it.message}") }.isSuccess
    }

    /** ルールを消したときに呼ぶ。 */
    fun delete(context: Context, ruleId: String) {
        if (ruleId.isNotEmpty()) runCatching { file(context, ruleId).delete() }
    }
}
