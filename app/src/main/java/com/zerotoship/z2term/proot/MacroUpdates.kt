package com.zerotoship.z2term.proot

import java.io.File
import java.security.MessageDigest

/**
 * 同梱サンプルの更新に気付けるようにする (1.0.198)。
 *
 * `z2-macro install` は既存のコピーを上書きしない (利用者の書き換えを守るため)。そのせいで
 * **同梱版が直っても、入れた人には届かず、気付く手段も無かった**。`list` の「差分あり」は
 * 「自分で書き換えた」と「同梱版が新しくなった」を見分けられない。
 *
 * そこで入れたときの同梱版を `~/.z2term/macros/.bundled/<名前>` に写しておく (= 元)。
 *  - コピー = 今の同梱版 → 同じ
 *  - コピー = 元、今の同梱版 ≠ 元 → **更新あり** (書き換えていないので入れ替えてよい)
 *  - コピー ≠ 元 → 書き換え済み (上書きしない)
 *  - 元が無い → 差分あり (見分けられない。安全側)
 *
 * ⚠ 同梱版のコメントや文言は**アプリの言語に合わせて変わる**。言語を変えただけで「更新あり」に
 * ならないよう、中身の名札 ([contentIds]) は英語で組んだ中身から作り、元と一緒に
 * `.bundled/<名前>.id` へ残す。名札が同じなら、違うのは言語だけ。
 *
 * ⚠ 除外 (`z2-macro hold`) は `~/.z2term/macros/.hold` に 1 行 1 名前。通知にも一括更新にも出さない。
 *
 * 判定の規則は `z2-macro` ([z2MacroScript]) のシェル側と同じ。片方だけ直さないこと。
 */
internal object MacroUpdates {
    /** 同梱版の置き場 (`/usr/local/share/z2term/macros/`) に書く名札の一覧。1 行「名前 名札」。 */
    const val IDS = ".ids"
    private const val BASE = ".bundled"
    private const val HOLD = ".hold"
    /** 最後に確かめたアプリの versionCode。版が変わったときだけ通知する。 */
    private const val CHECKED = ".checked"

    fun contentIds(samplesEn: Map<String, String>): Map<String, String> =
        samplesEn.mapValues { (_, body) ->
            MessageDigest.getInstance("SHA-1").digest(body.toByteArray())
                .joinToString("") { "%02x".format(it) }.take(12)
        }

    fun idsText(ids: Map<String, String>): String =
        ids.entries.sortedBy { it.key }.joinToString("") { "${it.key} ${it.value}\n" }

    fun parseIds(text: String?): Map<String, String> =
        text.orEmpty().lines().mapNotNull { line ->
            val p = line.trim().split(' ')
            if (p.size == 2 && p[0].isNotEmpty() && p[1].isNotEmpty()) p[0] to p[1] else null
        }.toMap()

    /** `.hold` の名前 (`voice` でも `voice.sh` でもよい。`#` から後ろは書き添え)。 */
    fun heldNames(text: String?): Set<String> =
        text.orEmpty().lines().map { it.substringBefore('#').trim().removeSuffix(".sh") }
            .filter { it.isNotEmpty() }.toSet()

    private fun same(f: File, body: ByteArray): Boolean =
        f.isFile && runCatching { f.readBytes().contentEquals(body) }.getOrDefault(false)

    /**
     * 1.0.197 までに入れたコピーには元が無い。書き出す**前**の同梱版 ([before] = 前回の起動の版) と
     * 同じ中身なら、それを元として残す (書き換えていないことが分かる)。今の同梱版と同じならそれを残す。
     * どちらでもなければ触らない (書き換えた可能性がある)。
     */
    fun adoptLegacy(
        macros: File,
        now: Map<String, String>, nowIds: Map<String, String>,
        before: Map<String, String>, beforeIds: Map<String, String>,
    ) {
        if (!macros.isDirectory) return
        val base = File(macros, BASE)
        for ((name, body) in now) {
            val dest = File(macros, name)
            if (!dest.isFile || File(base, name).isFile) continue
            val (src, id) = when {
                same(dest, body.toByteArray()) -> body to nowIds[name].orEmpty()
                before[name]?.let { same(dest, it.toByteArray()) } == true ->
                    before.getValue(name) to (beforeIds[name] ?: "unknown")
                else -> continue
            }
            runCatching {
                base.mkdirs()
                File(base, name).writeText(src)
                File(base, "$name.id").writeText(id + "\n")
            }
        }
    }

    /** 書き換えていないコピーのうち、同梱版の中身 (言語を除く) が新しくなったもの。除外したものは入れない。 */
    fun updatable(macros: File, now: Map<String, String>, nowIds: Map<String, String>): List<String> {
        if (!macros.isDirectory) return emptyList()
        val held = heldNames(File(macros, HOLD).takeIf { it.isFile }?.readText())
        val base = File(macros, BASE)
        return now.keys.sorted().filter { name ->
            val dest = File(macros, name)
            val b = File(base, name)
            name.removeSuffix(".sh") !in held && dest.isFile && b.isFile &&
                !same(dest, now.getValue(name).toByteArray()) &&
                runCatching { dest.readBytes().contentEquals(b.readBytes()) }.getOrDefault(false) &&
                File(base, "$name.id").takeIf { it.isFile }?.readText()?.trim() != nowIds[name]
        }
    }

    /**
     * アプリの版が変わって最初の起動なら、知らせる名前を返す (無ければ空)。同じ版の 2 回目からは
     * 空を返す (起動のたびに同じ通知を出さない)。マクロを 1 本も入れていない人には何もしない。
     */
    fun namesToAnnounce(
        macros: File, versionCode: Int, now: Map<String, String>, nowIds: Map<String, String>,
    ): List<String> {
        if (!macros.isDirectory) return emptyList()
        val stamp = File(macros, "$BASE/$CHECKED")
        if (stamp.takeIf { it.isFile }?.readText()?.trim() == versionCode.toString()) return emptyList()
        val names = updatable(macros, now, nowIds)
        runCatching { stamp.parentFile?.mkdirs(); stamp.writeText("$versionCode\n") }
        return names
    }
}
