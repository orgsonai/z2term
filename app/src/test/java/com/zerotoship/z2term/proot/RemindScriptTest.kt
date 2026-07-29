package com.zerotoship.z2term.proot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * リマインドのサンプル (`remind.sh`) の**入力の受け止め方**を実際の `sh` で確かめる。
 *
 * ⚠ ここが要 — 「いつ？」は人が手で書く欄なので、**読めない書き方が必ず来る**。読めないまま
 * 予約へ渡すと、鳴らない予定が「登録できた」顔で一覧に並ぶ (0.8.282 までがそうだった)。
 * 断るときは**理由が出ること**、正しい書き方は**予約の段まで進むこと**を固定する。
 *
 * テスト環境には `z2-alarm` などが無いので、正しい入力は「予約に失敗した」で止まる。
 * それは**読み取りには成功した**ことの裏返しなので、そこまでを見る。
 */
class RemindScriptTest {

    private val sh = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { File(it).canExecute() }

    private fun script(lang: String): String = z2MacroSamples(lang)["remind.sh"]!!

    /** remind.sh を引数付きで走らせ、(終了コード, 出力) を返す。 */
    private fun run(lang: String, vararg args: String): Pair<Int, String> {
        val f = File.createTempFile("remind", ".sh").apply { writeText(script(lang)) }
        val home = File.createTempFile("remind-home", "").apply { delete(); mkdirs() }
        try {
            val pb = ProcessBuilder(listOf(sh!!, f.absolutePath) + args).redirectErrorStream(true)
            pb.environment()["HOME"] = home.absolutePath
            // ⚠ PATH を空にはしない (date / grep が要る)。z2-* が無いことが再現できればよい。
            val p = pb.start()
            val out = p.inputStream.bufferedReader().readText()
            return p.waitFor() to out
        } finally {
            f.delete()
            home.deleteRecursively()
        }
    }

    @Test
    fun `両言語とも POSIX sh として構文が通る`() {
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        for (lang in listOf("ja", "en")) {
            val f = File.createTempFile("remind", ".sh").apply { writeText(script(lang)) }
            try {
                val p = ProcessBuilder(sh!!, "-n", f.absolutePath).redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                assertEquals("remind.sh ($lang): sh -n が構文エラーを報告した:\n$out", 0, p.waitFor())
            } finally {
                f.delete()
            }
        }
    }

    @Test
    fun `読めない語は理由と例を出して断る`() {
        assumeTrue(sh != null)
        val (code, out) = run("ja", "ぬるぽ", "薬を飲む")
        assertNotEquals("読めない語なのに成功で終わった: $out", 0, code)
        assertTrue("何が駄目か分からない出力: $out", out.contains("分かりません"))
        assertTrue("直し方の例が無い: $out", out.contains("30m") && out.contains("18:30"))
    }

    @Test
    fun `範囲外の時刻を断る`() {
        assumeTrue(sh != null)
        val (code, out) = run("ja", "18:70", "ゴミ出し")
        assertNotEquals("18:70 が通ってしまった: $out", 0, code)
        assertTrue("範囲の説明が無い: $out", out.contains("23:59"))
    }

    @Test
    fun `時刻を書き忘れた繰り返しを断る`() {
        assumeTrue(sh != null)
        val (code, out) = run("ja", "毎日", "体重を計る")
        assertNotEquals("時刻なしの毎日が通ってしまった: $out", 0, code)
        assertTrue("時刻が要ると分からない出力: $out", out.contains("時刻"))
    }

    @Test
    fun `数でない待ち時間を断る`() {
        assumeTrue(sh != null)
        // "1.5h" は書式こそ似ているが $((num*3600)) が壊れる。予約前に断る。
        val (code, out) = run("ja", "1.5h", "休憩")
        assertNotEquals("1.5h が通ってしまった: $out", 0, code)
        assertTrue("理由が出ていない: $out", out.contains("分かりません"))
    }

    @Test
    fun `曜日が読めない毎週を断る`() {
        assumeTrue(sh != null)
        val (code, out) = run("ja", "毎週", "げつ", "09:00", "資源ごみ")
        assertNotEquals("読めない曜日が通ってしまった: $out", 0, code)
        assertTrue("曜日の話だと分からない出力: $out", out.contains("曜日"))
    }

    @Test
    fun `正しい書き方は読み取りを抜けて予約まで進む`() {
        assumeTrue(sh != null)
        // z2-alarm / z2-when がテスト環境に無いので、ここでは必ず「予約できませんでした」で
        // 止まる。⚠ それは**読み取りに成功した**ことの裏返し (読めていなければ手前で断られる)。
        for (spec in listOf(arrayOf("30m"), arrayOf("18:30"), arrayOf("毎日", "07:00"),
                            arrayOf("平日", "09:00"), arrayOf("毎週", "月", "09:00"))) {
            val (code, out) = run("ja", *spec, "本文")
            assertNotEquals("$spec: 予約が無い環境で成功してしまった: $out", 0, code)
            assertTrue(
                "$spec: 読み取りで断られている (予約まで進んでいない): $out",
                !out.contains("分かりません") && !out.contains("書いてください")
            )
        }
    }

    @Test
    fun `英語でも読めない語を断る`() {
        assumeTrue(sh != null)
        val (code, out) = run("en", "whenever", "take pills")
        assertNotEquals("読めない語なのに成功で終わった: $out", 0, code)
        assertTrue("何が駄目か分からない出力: $out", out.contains("cannot read the time"))
    }
}
