package com.zerotoship.z2term.automation

import android.content.Context

/**
 * MCP の `docs` 道具が引く、AI 向けの文書 (1.0.176)。
 *
 * 中身はビルド時に `docs/en/` から写した英語の使い方の文書 (assets/ai-docs/。app/build.gradle.kts の
 * copyAiDocs)。人が読む画面には出さない。内部設計書 (DESIGN-SPEC) と変更履歴は入れない
 * (使い方に要らず、容量の大半を占めるため)。
 *
 * ⭐ 見出しごとの節に分け、問い合わせの語を含む節だけを返す。AI が文書を丸ごと読むと会話の
 * 量 (トークン) を大きく使うので、要る所だけを渡すのがこの道具の目的。
 */
internal object AiDocs {
    private const val DIR = "ai-docs"
    /** 1 回の返事に入れる本文の上限 (文字)。超える節は part で続きを読む。 */
    private const val BUDGET = 8_000
    private const val MAX_HITS = 8
    /** これより長い節は、検索では語を含む行だけを抜き出す (大きな表の頭だけ返して肝心の行を落とさないため)。 */
    private const val WHOLE = 2_500

    class Section(val id: String, val path: String, val text: String)

    @Volatile private var cache: List<Section>? = null

    fun sections(context: Context): List<Section> = cache ?: synchronized(this) {
        cache ?: load(context).also { cache = it }
    }

    private fun load(context: Context): List<Section> {
        val assets = context.applicationContext.assets
        val files = assets.list(DIR).orEmpty().filter { it.endsWith(".md") }.sorted()
        return files.flatMap { name ->
            val text = assets.open("$DIR/$name").bufferedReader().use { it.readText() }
            split(name, text)
        }
    }

    /** 見出し (`#`〜`####`) で区切る。コードブロックの中の `#` は見出しにしない。 */
    internal fun split(file: String, text: String): List<Section> {
        val out = mutableListOf<Section>()
        val trail = arrayOfNulls<String>(5)
        var title = file.removeSuffix(".md")
        val body = StringBuilder()
        var fence = false
        fun flush() {
            val t = body.toString().trim()
            if (t.isNotEmpty()) out += Section("${file.removeSuffix(".md")}#${out.size + 1}", title, t)
            body.setLength(0)
        }
        text.lineSequence().forEach { line ->
            if (line.trimStart().startsWith("```")) fence = !fence
            val level = if (fence) 0 else line.takeWhile { it == '#' }.length
            if (level in 1..4 && line.getOrNull(level) == ' ') {
                flush()
                trail[level] = line.drop(level + 1).trim()
                for (i in level + 1..4) trail[i] = null
                title = (listOf(file.removeSuffix(".md")) + (1..level).mapNotNull { trail[it] }).joinToString(" > ")
            }
            body.append(line).append('\n')
        }
        flush()
        return out
    }

    private fun terms(query: String): List<String> =
        query.lowercase().split(Regex("[^a-z0-9_.-]+")).map { it.trim('.', '-') }.filter { it.length >= 2 }.distinct()

    /**
     * 語を多く含む節から順に返す。見出しに出る語は本文の 5 倍に数える。
     * [WHOLE] より長い節は語を含む行 (表なら行ごと) だけにする。本文は [BUDGET] まで詰め、
     * 入りきらない節は id と見出しだけ並べる (全文は section で読む)。
     */
    fun search(context: Context, query: String): String {
        val words = terms(query)
        if (words.isEmpty()) return "Give English keywords, e.g. \"broadcast group\" or \"z2-when battery\"."
        val ranked = sections(context).mapNotNull { s ->
            val head = s.path.lowercase()
            val body = s.text.lowercase()
            val counts = words.map { w -> head.occurrences(w) * 5 + body.occurrences(w) }
            val matched = counts.count { it > 0 }
            if (matched == 0) null else Triple(s, matched, counts.sum())
        }.sortedWith(compareByDescending<Triple<Section, Int, Int>> { it.second }.thenByDescending { it.third })
            .take(MAX_HITS)
        if (ranked.isEmpty()) return "No section matches: ${words.joinToString(" ")}. Try other English words."
        val sb = StringBuilder()
        val rest = mutableListOf<Section>()
        ranked.forEach { (s, _, _) ->
            val text = if (s.text.length <= WHOLE) s.text else excerpt(s.text, words)
            if (sb.length + text.length <= BUDGET || sb.isEmpty()) {
                sb.append("## ").append(s.path).append("  [").append(s.id).append("]\n")
                sb.append(text.take(BUDGET))
                if (text !== s.text) sb.append("\n(matching lines only; whole section: section=").append(s.id).append(')')
                sb.append("\n\n")
            } else rest += s
        }
        if (rest.isNotEmpty()) {
            sb.append("More matches (read with section):\n")
            rest.forEach { sb.append("- ").append(it.id).append("  ").append(it.path).append('\n') }
        }
        return sb.toString().trimEnd()
    }

    /** 語を含む行だけ。表の見出し行 (`|` で始まり次が `|---`) は意味が分かるように残す。 */
    private fun excerpt(text: String, words: List<String>): String {
        val lines = text.lines()
        return lines.filterIndexed { i, line ->
            val l = line.lowercase()
            i == 0 || words.any { it in l } || line.startsWith("|--") ||
                (line.startsWith("|") && lines.getOrNull(i + 1)?.startsWith("|--") == true)
        }.joinToString("\n")
    }

    /** 1 つの節を読む。長い節は [BUDGET] ごとに part 1, 2… と分ける。 */
    fun read(context: Context, id: String, part: Int): String {
        val s = sections(context).firstOrNull { it.id == id } ?: return "No section $id. Search first to get ids."
        return "## ${s.path}  [${s.id}]\n" + page(s.text, part)
    }

    private fun page(text: String, part: Int): String {
        val parts = (text.length + BUDGET - 1) / BUDGET
        val p = part.coerceIn(1, maxOf(parts, 1))
        val chunk = text.substring((p - 1) * BUDGET, minOf(text.length, p * BUDGET))
        return if (parts <= 1) chunk else "$chunk\n(part $p of $parts; read the next with part ${p + 1})".takeIf { p < parts }
            ?: "$chunk\n(part $p of $parts)"
    }

    private fun String.occurrences(w: String): Int {
        var n = 0
        var i = indexOf(w)
        while (i >= 0) { n++; i = indexOf(w, i + w.length) }
        return n
    }
}
