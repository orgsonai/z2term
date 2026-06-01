package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * かな漢字変換 [KkcConverter] の長文変換品質を、固定の評価セット (`kkc_eval.tsv`) に対して
 * バッチ評価し、文正解率とカテゴリ別正解率をコンソール + `build/kkc-eval-report.txt` に出力する。
 *
 * 期待値は商用 IME (Google 日本語入力等) の標準出力を基準にしているため、Phase 0 時点では
 * 大半が「不一致 = ギャップ」になる。本テストは品質**測定**が目的で、正解率閾値での失敗
 * (回帰検出) は意図的に設けていない (Phase 0 はベースライン記録、回帰検出は Phase 0 後に
 * 別 @Test で導入予定)。
 *
 * 実行: `./gradlew :app:testFullDebugUnitTest --tests *KkcEvalTest`
 */
class KkcEvalTest {

    /** 1 ケース。tags は集計のためのカテゴリラベル群。 */
    private data class Case(val reading: String, val expected: String, val tags: List<String>)

    companion object {
        @BeforeClass
        @JvmStatic
        fun loadKkc() {
            if (KkcConverter.loaded) return
            // JVM ユニットテストの cwd は :app モジュールルート。
            val matrix = locate("src/main/assets/kkc_matrix.bin")
            val lex = locate("src/main/assets/kkc_lex.tsv")
            matrix.inputStream().use { ms ->
                lex.bufferedReader(Charsets.UTF_8).use { lr ->
                    KkcConverter.loadFromStreams(ms, lr)
                }
            }
        }

        private fun locate(rel: String): File {
            for (base in listOf(".", "app", "../app")) {
                val f = File(base, rel)
                if (f.exists()) return f
            }
            error("asset not found: $rel (cwd=${File(".").absolutePath})")
        }
    }

    @Test
    fun evaluateAndReport() {
        val cases = loadEval()
        assertTrue("eval cases must be non-empty", cases.isNotEmpty())

        val results = cases.map { c -> c to KkcConverter.convert(c.reading) }

        val sb = StringBuilder()
        sb.appendLine("=== KKC eval report (Phase 0 baseline) ===")
        sb.appendLine("cases: ${cases.size}")
        sb.appendLine()

        // ----- 全体 -----
        val passAll = results.count { (c, got) -> got == c.expected }
        sb.appendLine("OVERALL  : ${pct(passAll, cases.size)}  ($passAll / ${cases.size})")
        sb.appendLine()

        // ----- カテゴリ別 -----
        sb.appendLine("--- by tag ---")
        val tagBuckets = LinkedHashMap<String, MutableList<Pair<Case, String?>>>()
        for ((c, got) in results) {
            for (t in c.tags) tagBuckets.getOrPut(t) { mutableListOf() }.add(c to got)
        }
        val sortedTags = tagBuckets.keys.sortedBy { it }
        val tagWidth = (sortedTags.maxOfOrNull { it.length } ?: 0).coerceAtLeast(8)
        for (t in sortedTags) {
            val bucket = tagBuckets[t]!!
            val ok = bucket.count { (c, got) -> got == c.expected }
            sb.appendLine("  ${t.padEnd(tagWidth)} : ${pct(ok, bucket.size)}  ($ok / ${bucket.size})")
        }
        sb.appendLine()

        // ----- 不一致ケース一覧 (上位 40 件まで) -----
        sb.appendLine("--- failures (first 40) ---")
        val fails = results.filter { (c, got) -> got != c.expected }
        for ((c, got) in fails.take(40)) {
            sb.appendLine("  read=${c.reading}")
            sb.appendLine("    exp=${c.expected}")
            sb.appendLine("    got=${got ?: "<null>"}")
            sb.appendLine("    tags=${c.tags.joinToString(",")}")
        }
        if (fails.size > 40) sb.appendLine("  ... (${fails.size - 40} more)")

        val text = sb.toString()
        println(text)

        // build/ 下にレポートを残す (CI から拾えるように)。
        val report = File("build/kkc-eval-report.txt")
        report.parentFile?.mkdirs()
        report.writeText(text)
    }

    private fun loadEval(): List<Case> {
        val stream = javaClass.classLoader!!.getResourceAsStream("kkc_eval.tsv")
            ?: error("kkc_eval.tsv not found in test resources")
        val out = ArrayList<Case>()
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                val cols = line.split('\t')
                if (cols.size < 2) continue
                val tags = if (cols.size >= 3) cols[2].split(',').map { it.trim() }.filter { it.isNotEmpty() } else emptyList()
                out.add(Case(cols[0], cols[1], tags))
            }
        }
        return out
    }

    private fun pct(num: Int, denom: Int): String =
        if (denom == 0) "  -.--%" else "%6.2f%%".format(100.0 * num / denom)
}
