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
    private fun run(lang: String, vararg args: String): Pair<Int, String> =
        run(lang, fakes = false, args = args)

    /**
     * [fakes] = true なら `z2-alarm` などの偽物を PATH の先頭に置いて、**登録まで走り切らせる**。
     * 一覧に出る表記 (PLAN) を見たいときに使う。false なら本物も偽物も無い = 予約で止まる。
     */
    private fun run(lang: String, fakes: Boolean, vararg args: String): Pair<Int, String> {
        val (code, out, _) = runTraced(lang, fakes, *args)
        return code to out
    }

    /**
     * [run] と同じだが、偽物に渡された引数も返す (3 つ目)。
     * ⚠ 繰り返しの予定は `z2-when` へ渡す **cron 式が正しいか**が要で、そこは出力に出ない。
     */
    private fun runTraced(lang: String, fakes: Boolean, vararg args: String): Triple<Int, String, String> {
        val f = File.createTempFile("remind", ".sh").apply { writeText(script(lang)) }
        val home = File.createTempFile("remind-home", "").apply { delete(); mkdirs() }
        val bin = File(home, "bin").apply { mkdirs() }
        val trace = File(home, "trace.log")
        try {
            if (fakes) {
                for (name in listOf("z2-alarm", "z2-toast", "z2-notify", "z2-ask")) {
                    File(bin, name).apply {
                        writeText("#!/bin/sh\necho \"$name \$*\" >> ${trace.absolutePath}\nexit 0\n")
                        setExecutable(true)
                    }
                }
                // z2-when だけはルール id を返す (cmd_add がその出力を wid として持つため)。
                File(bin, "z2-when").apply {
                    writeText("#!/bin/sh\necho \"z2-when \$*\" >> ${trace.absolutePath}\necho w1\n")
                    setExecutable(true)
                }
            }
            val pb = ProcessBuilder(listOf(sh!!, f.absolutePath) + args).redirectErrorStream(true)
            pb.environment()["HOME"] = home.absolutePath
            // ⚠ PATH を空にはしない (date / grep が要る)。z2-* が無いことが再現できればよい。
            if (fakes) pb.environment()["PATH"] = bin.absolutePath + ":" + System.getenv("PATH")
            val p = pb.start()
            val out = p.inputStream.bufferedReader().readText()
            val code = p.waitFor()
            return Triple(code, out, if (trace.exists()) trace.readText() else "")
        } finally {
            f.delete()
            home.deleteRecursively()
        }
    }

    /** 今から [days] 日後の日付を `MM/dd` で。 */
    private fun dateAfter(days: Int): String {
        val c = java.util.Calendar.getInstance()
        c.add(java.util.Calendar.DAY_OF_YEAR, days)
        return java.text.SimpleDateFormat("MM/dd", java.util.Locale.US).format(c.time)
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
    fun `日付の言い方も読み取って予約まで進む`() {
        assumeTrue(sh != null)
        // ⚠ 「明日」系は z2-alarm の at では書けない (日付を渡せない) ので in <秒>s へ寄せている。
        //   読めていれば予約の段まで進み、テスト環境では z2-alarm が無くてそこで止まる。
        for (spec in listOf(
            arrayOf("明日", "18:30"), arrayOf("明日18:30"), arrayOf("明日の18:30"),
            arrayOf("明後日"), arrayOf("あさって", "09:00"), arrayOf("翌日", "07:00"),
            arrayOf("3日後", "07:00"), arrayOf("3日後の07:00"), arrayOf("3d", "09:00"), arrayOf("3d")
        )) {
            val (code, out) = run("ja", *spec, "本文")
            assertNotEquals("${spec.toList()}: 予約が無い環境で成功してしまった: $out", 0, code)
            assertTrue(
                "${spec.toList()}: 読み取りで断られている: $out",
                !out.contains("分かりません") && !out.contains("時刻") && !out.contains("過ぎ")
            )
        }
    }

    @Test
    fun `一覧の表記は「明日」ではなく実際の日付になる`() {
        assumeTrue(sh != null)
        // ⚠ 「明日」と覚えると日付が変わった後にズレて見える。登録時点で実日付に直して持つ。
        val (code, out) = run("ja", fakes = true, "明日", "18:30", "ゴミ出し")
        assertEquals("登録に失敗した: $out", 0, code)
        assertTrue("明日の日付になっていない: $out", out.contains("${dateAfter(1)} 18:30"))
        assertTrue("本文が落ちている: $out", out.contains("ゴミ出し"))
    }

    @Test
    fun `N日後は日数ぶん先の日付になる`() {
        assumeTrue(sh != null)
        val (code, out) = run("ja", fakes = true, "3日後", "07:00", "返却")
        assertEquals("登録に失敗した: $out", 0, code)
        assertTrue("3 日後の日付になっていない: $out", out.contains("${dateAfter(3)} 07:00"))
    }

    @Test
    fun `時刻を省くと今と同じ時刻になる`() {
        assumeTrue(sh != null)
        // 既定時刻を勝手に決めず、「明後日のこの時間」にする。
        val (code, out) = run("ja", fakes = true, "明後日", "電話する")
        assertEquals("登録に失敗した: $out", 0, code)
        val nowHm = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())
        assertTrue("明後日の同時刻になっていない (期待 ${dateAfter(2)} $nowHm): $out",
            out.contains("${dateAfter(2)} $nowHm"))
        assertTrue("本文が落ちている: $out", out.contains("電話する"))
    }

    @Test
    fun `時刻を省いた日付指定は本文を食べない`() {
        assumeTrue(sh != null)
        // 「明日 電話する」の "電話する" を時刻と誤解して弾いてはいけない (本文として残す)。
        val (code, out) = run("ja", "明日", "電話する")
        assertNotEquals(0, code)
        assertTrue("本文が時刻として読まれている: $out", !out.contains("時刻") && !out.contains("分かりません"))
    }

    @Test
    fun `過ぎた日時は断る`() {
        assumeTrue(sh != null)
        // 0 日後 = 今日。00:00 は必ず過ぎているので、予約せずに断る。
        val (code, out) = run("ja", "0日後の00:00", "本文")
        assertNotEquals("過去の日時が通ってしまった: $out", 0, code)
        assertTrue("過ぎたことが分からない出力: $out", out.contains("過ぎ"))
    }

    @Test
    fun `毎月と毎年は cron の日と月の欄を埋める`() {
        assumeTrue(sh != null)
        val (c1, out1, t1) = runTraced("ja", true, "毎月", "15", "09:00", "家賃")
        assertEquals("登録に失敗した: $out1", 0, c1)
        assertTrue("cron が毎月 15 日になっていない: $t1", t1.contains("time:cron=0 9 15 * *"))
        assertTrue("表記が毎月になっていない: $out1", out1.contains("毎月 15日 09:00"))

        val (c2, out2, t2) = runTraced("ja", true, "毎年", "07/30", "19:00", "誕生日")
        assertEquals("登録に失敗した: $out2", 0, c2)
        assertTrue("cron が毎年 7/30 になっていない: $t2", t2.contains("time:cron=0 19 30 7 *"))
        assertTrue("表記が毎年になっていない: $out2", out2.contains("毎年 07/30 19:00"))
    }

    @Test
    fun `毎月は「毎月15日」のようにくっつけても読める`() {
        assumeTrue(sh != null)
        val (code, _, trace) = runTraced("ja", true, "毎月15日", "09:00", "家賃")
        assertEquals(0, code)
        assertTrue("cron が毎月 15 日になっていない: $trace", trace.contains("time:cron=0 9 15 * *"))
    }

    @Test
    fun `簡易「毎」は次の語で毎日 毎週 毎月 毎年を選ぶ`() {
        assumeTrue(sh != null)
        // 毎 19:00 = 毎日 (daily は cron ではなく time:daily= へ行く)
        val (_, _, tDaily) = runTraced("ja", true, "毎", "19:00", "本文")
        assertTrue("毎日になっていない: $tDaily", tDaily.contains("time:daily=19:00"))
        // 毎 水 19:00 = 毎週水曜 (cron の曜日 3)
        val (_, _, tWeekly) = runTraced("ja", true, "毎", "水", "19:00", "本文")
        assertTrue("毎週水曜になっていない: $tWeekly", tWeekly.contains("time:cron=0 19 * * 3"))
        // 毎 15 19:00 = 毎月 15 日
        val (_, _, tMonthly) = runTraced("ja", true, "毎", "15", "19:00", "本文")
        assertTrue("毎月になっていない: $tMonthly", tMonthly.contains("time:cron=0 19 15 * *"))
        // 毎 07/30 19:00 = 毎年
        val (_, _, tYearly) = runTraced("ja", true, "毎", "07/30", "19:00", "本文")
        assertTrue("毎年になっていない: $tYearly", tYearly.contains("time:cron=0 19 30 7 *"))
    }

    @Test
    fun `年月日で書いた単発を読む`() {
        assumeTrue(sh != null)
        // ⚠ 実行日に左右されないよう、確実に未来の年を使う。
        for (spec in listOf(
            arrayOf("2030", "07/30", "19:00"),   // 年 + 月日 + 時刻
            arrayOf("203007301900"),             // 数字だけ 12 桁
        )) {
            val (code, out) = run("ja", fakes = true, *spec, "本文")
            assertEquals("${spec.toList()}: 登録に失敗した: $out", 0, code)
            assertTrue("${spec.toList()}: 表記が 2030/07/30 19:00 でない: $out",
                out.contains("2030/07/30 19:00"))
        }
    }

    @Test
    fun `年を省いた月日は今年、過ぎていれば来年になる`() {
        assumeTrue(sh != null)
        val (code, out) = run("ja", fakes = true, "12/31", "23:59", "大晦日")
        assertEquals("登録に失敗した: $out", 0, code)
        // 今年の 12/31 23:59 が未来なら年なし表記、過ぎていれば来年なので年付き表記になる。
        assertTrue("12/31 23:59 が読めていない: $out",
            out.contains("12/31 23:59") || out.contains("/12/31 23:59"))
    }

    @Test
    fun `数字だけ 8 桁 (月日時分) も読む`() {
        assumeTrue(sh != null)
        val (code, out) = run("ja", fakes = true, "12312359", "大晦日")
        assertEquals("登録に失敗した: $out", 0, code)
        assertTrue("12/31 23:59 になっていない: $out", out.contains("12/31 23:59"))
    }

    @Test
    fun `存在しない日付を断る`() {
        assumeTrue(sh != null)
        for (spec in listOf("02/30", "13/01", "07/32")) {
            val (code, out) = run("ja", fakes = true, spec, "09:00", "本文")
            assertNotEquals("$spec が通ってしまった: $out", 0, code)
            assertTrue("$spec: 日付の話だと分からない出力: $out", out.contains("日付"))
        }
    }

    @Test
    fun `英語の monthly と yearly と every`() {
        assumeTrue(sh != null)
        val (_, _, t1) = runTraced("en", true, "monthly", "15", "09:00", "rent")
        assertTrue("monthly が cron になっていない: $t1", t1.contains("time:cron=0 9 15 * *"))
        val (_, _, t2) = runTraced("en", true, "yearly", "07/30", "19:00", "birthday")
        assertTrue("yearly が cron になっていない: $t2", t2.contains("time:cron=0 19 30 7 *"))
        val (_, _, t3) = runTraced("en", true, "every", "wed", "19:00", "bins")
        assertTrue("every wed が毎週になっていない: $t3", t3.contains("time:cron=0 19 * * 3"))
    }

    @Test
    fun `英語の tomorrow も読める`() {
        assumeTrue(sh != null)
        val (code, out) = run("en", "tomorrow", "18:30", "the bins")
        assertNotEquals(0, code)
        assertTrue("読み取りで断られている: $out", !out.contains("cannot read"))
    }

    @Test
    fun `setup は名前で打てないときに PATH の直し方を出す`() {
        assumeTrue(sh != null)
        // ⚠ 0.8.286 まではマクロ置き場が PATH に無く、help も docs も名前で打つ前提だったので
        //   `remind.sh: command not found` になっていた (実機で指摘)。黙って終わらせない。
        val (code, out) = run("ja", fakes = true, "setup")
        assertEquals("setup が失敗した: $out", 0, code)
        assertTrue("PATH の直し方が出ていない: $out", out.contains("export PATH="))
    }

    @Test
    fun `英語でも読めない語を断る`() {
        assumeTrue(sh != null)
        val (code, out) = run("en", "whenever", "take pills")
        assertNotEquals("読めない語なのに成功で終わった: $out", 0, code)
        assertTrue("何が駄目か分からない出力: $out", out.contains("cannot read the time"))
    }
}
