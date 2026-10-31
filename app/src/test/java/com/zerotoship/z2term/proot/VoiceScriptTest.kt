package com.zerotoship.z2term.proot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 声で操作のサンプル (`voice.sh`, 1.0.65) を実際の `sh` で動かし、**聞いた言葉がどの操作になるか**を固定する。
 *
 * `z2-*` と `remind.sh` は偽物 (呼ばれた引数を書き残すだけ) を置く。`voice.sh try <言葉>` は
 * 話さずに 1 回ぶんを流す入口で、voice イベントで呼ばれたときと同じ道を通る。
 */
class VoiceScriptTest {

    private val sh = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { File(it).canExecute() }

    private val fakes = listOf(
        "z2-say", "z2-notify", "z2-toast", "z2-media", "z2-volume", "z2-alarm",
        "z2-torch", "z2-intent", "z2-vibrate", "z2-key", "z2-when",
    )

    /**
     * voice.sh を引数付きで 1 回走らせ、(出力, 偽物が受けた呼び出し) を返す。
     * [files] は HOME からの相対パス → 中身 (言葉の表などを先に置く)。
     */
    private fun run(lang: String, vararg args: String, files: Map<String, String> = emptyMap()): Pair<String, String> {
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        val home = File.createTempFile("voice-home", "").apply { delete(); mkdirs() }
        val bin = File(home, "bin").apply { mkdirs() }
        val trace = File(home, "trace.log")
        try {
            val script = File(home, ".z2term/macros/voice.sh").apply {
                parentFile!!.mkdirs(); writeText(z2MacroSamples(lang).getValue("voice.sh"))
            }
            File(home, ".z2term/macros/remind.sh").writeText(
                "#!/bin/sh\necho \"remind \$*\" >> ${trace.absolutePath}\necho ok\n"
            )
            for (name in fakes) {
                File(bin, name).apply {
                    writeText("#!/bin/sh\necho \"$name \$*\" >> ${trace.absolutePath}\nexit 0\n")
                    setExecutable(true)
                }
            }
            // z2-app list は決まったアプリの一覧を返す (英字の名前・何語かの名前・日本語の名前)。
            File(bin, "z2-app").apply {
                writeText("#!/bin/sh\necho \"z2-app \$*\" >> ${trace.absolutePath}\n" +
                    "echo '[{\"package\":\"p.browser\",\"label\":\"Vivaldi\"},{\"package\":\"p.photo\",\"label\":\"Adobe Photoshop Express\"}," +
                    "{\"package\":\"p.clock\",\"label\":\"時計\"},{\"package\":\"p.video\",\"label\":\"YouTube\"}]'\n")
                setExecutable(true)
            }
            files.forEach { (path, body) -> File(home, path).apply { parentFile!!.mkdirs(); writeText(body) } }
            val pb = ProcessBuilder(listOf(sh!!, script.absolutePath) + args).redirectErrorStream(true)
            pb.environment()["HOME"] = home.absolutePath
            // ⚠ PATH は組み直す (z2term の中で回すと本物の z2-* が居るため)。偽物を先頭に。
            val base = listOf("/usr/bin", "/bin").filter { File(it).isDirectory }.joinToString(":")
            pb.environment()["PATH"] = bin.absolutePath + ":" + base
            val proc = pb.start()
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            return out to (if (trace.exists()) trace.readText() else "")
        } finally {
            home.deleteRecursively()
        }
    }

    private fun assertHeard(lang: String, phrase: String, expected: String) {
        val (_, calls) = run(lang, "try", phrase)
        assertTrue("$lang「$phrase」→ $expected のはずが:\n$calls", calls.lines().any { it == expected })
    }

    /** AI は既定でオフ (利用者の決定)。合図の言葉で始めても AI は起こさず、オンにする案内だけを出す。 */
    @Test
    fun aiIsOffByDefault() {
        val (out, _) = run("ja", "ai")
        assertTrue(out, out.lines().contains("ai=off"))
        val (_, calls) = run("ja", "try", "AIで明日の天気は")
        assertTrue(calls, calls.lines().any { it.startsWith("z2-notify -n voice-ai") && it.contains("voice.sh ai") })
        assertTrue(calls, calls.lines().none { it.startsWith("z2-toast") })
    }

    @Test
    fun japanesePhrases() {
        assertHeard("ja", "次の曲", "z2-media next")
        assertHeard("ja", "3分のタイマー", "z2-alarm in 3m voice-timer:3分")
        assertHeard("ja", "7時半に起こして", "z2-alarm at 07:30 voice-timer:起きる時間")
        assertHeard("ja", "明日の朝7時にゴミ出しをリマインド", "remind tomorrow 07:00 ゴミ出し")
        assertHeard("ja", "毎週月曜日の九時にゴミ出しをリマインド", "remind weekly mon 09:00 ゴミ出し")
        assertHeard("ja", "30分後に薬を飲むってリマインド", "remind 30m 薬を飲む")
        val (_, calls) = run("ja", "try", "音量3段階下げて")
        assertEquals(calls, 3, calls.lines().count { it == "z2-volume down" })
    }

    @Test
    fun englishPhrases() {
        assertHeard("en", "Next song", "z2-media next")
        assertHeard("en", "set a timer for 5 minutes", "z2-alarm in 5m voice-timer:5 minutes")
        assertHeard("en", "remind me to take out the bins tomorrow at 7 am", "remind tomorrow 07:00 take out the bins")
        assertHeard("en", "remind me tonight at 9 to call mom", "remind 21:00 call mom")
    }

    /**
     * 「◯◯を開いて」(1.0.69)。英字の名前をカタカナで言っても読みで当たる。種類の言葉は端末の既定のアプリ
     * (MAIN + APP_* の種類)。言葉の表の呼び名 (app_names) が名前より先。どれでもなければ開かない。
     */
    @Test
    fun openAppsByNameReadingKindAndNickname() {
        assertHeard("ja", "時計を開いて", "z2-intent -p p.clock")
        assertHeard("ja", "ヴィヴァルディ開いて", "z2-intent -p p.browser")
        assertHeard("ja", "フォトショップを開いて", "z2-intent -p p.photo")
        assertHeard("ja", "ユーチューブを開いて", "z2-intent -p p.video")
        assertHeard("ja", "ブラウザを開いて", "z2-intent -a android.intent.action.MAIN -c android.intent.category.APP_BROWSER")
        assertHeard("ja", "カメラを開いて", "z2-intent -a android.media.action.STILL_IMAGE_CAMERA")
        assertHeard("en", "open the browser", "z2-intent -a android.intent.action.MAIN -c android.intent.category.APP_BROWSER")
        val table = mapOf(".z2term/voice/words-ja.txt" to "app_names = うごが:YouTube\n")
        val nick = run("ja", "try", "うごが開いて", files = table).second
        assertTrue(nick, nick.lines().contains("z2-intent -p p.video"))
        val none = run("ja", "try", "ラジオを開いて").second
        assertTrue(none, none.lines().none { it.startsWith("z2-intent") })
    }

    /**
     * ナビと施設の電話番号 (1.0.70)。どちらも端末の地図アプリで場所を開く (geo: の検索)。
     * 「ナビ」だけなら聞き取りの画面を開き直し、1 分のうちに言ったことを行き先にする。
     */
    @Test
    fun naviAndPlacePhoneOpenTheMap() {
        val tokyo = "z2-intent -a android.intent.action.VIEW -d geo:0,0?q=%E6%9D%B1%E4%BA%AC%E9%A7%85"
        assertHeard("ja", "東京駅までナビ", tokyo)
        assertHeard("ja", "東京駅に行きたい", tokyo)
        assertHeard("ja", "東京駅の電話番号", tokyo)
        assertHeard("en", "navigate to Tokyo Station", "z2-intent -a android.intent.action.VIEW -d geo:0,0?q=tokyo%20station")
        assertHeard("en", "phone number of Tokyo Station", "z2-intent -a android.intent.action.VIEW -d geo:0,0?q=tokyo%20station")
        val ask = run("ja", "try", "ナビ").second
        assertTrue(ask, ask.lines().contains("z2-intent -a android.intent.action.ASSIST -p com.zerotoship.z2term"))
        val next = run("ja", "try", "東京駅", files = mapOf(".z2term/voice/navi-wait" to "")).second
        assertTrue(next, next.lines().contains(tokyo))
    }

    /** 6 言語とも表を持ち、アプリの言語がその表の既定になる。表の無い言語は英語。 */
    @Test
    fun everyLanguageHasItsTable() {
        mapOf(
            "ja" to "次の曲", "en" to "next song", "zh-CN" to "下一首", "zh-TW" to "下一首",
            "es" to "siguiente canción", "ko" to "다음 곡", "fr" to "next song",
        ).forEach { (lang, phrase) -> assertHeard(lang, phrase, "z2-media next") }
        assertHeard("zh-CN", "每周一上午9点提醒我浇花", "remind weekly mon 09:00 浇花")
        assertHeard("es", "recuérdame mañana a las 7 de la mañana sacar la basura", "remind tomorrow 07:00 sacar la basura")
        assertHeard("ko", "내일 아침 7시에 쓰레기 버리라고 알려 줘", "remind tomorrow 07:00 쓰레기 버리기")
    }

    /** 利用者が書き換えた言葉の表が勝つ。書いた行だけが置き換わり、書いていないキーは同梱の値のまま。 */
    @Test
    fun editedTableWins() {
        val table = mapOf(".z2term/voice/words-ja.txt" to "media_next = 飛ばして\nmedia_pause =\n")
        val skip = run("ja", "try", "飛ばして", files = table).second
        assertTrue(skip, skip.lines().contains("z2-media next"))
        val old = run("ja", "try", "次の曲", files = table).second
        assertTrue(old, old.lines().none { it.startsWith("z2-media") })
        val off = run("ja", "try", "止めて", files = table).second
        assertTrue(off, off.lines().none { it.startsWith("z2-media") })
        val other = run("ja", "try", "前の曲", files = table).second
        assertTrue(other, other.lines().contains("z2-media previous"))
    }

    /** setup は voice・alarm・steps の 3 本を登録する (どれも voice.sh を呼ぶので片付けの 1 行で消える)。 */
    @Test
    fun setupRegistersThreeRules() {
        val (_, calls) = run("ja", "setup")
        val adds = calls.lines().filter { it.startsWith("z2-when ") && !it.startsWith("z2-when list") }
        assertEquals(calls, 3, adds.size)
        assertTrue(calls, adds.all { it.contains("macros/voice.sh") })
        assertTrue(calls, adds.any { it.startsWith("z2-when event:voice ") })
        assertTrue(calls, adds.any { it.startsWith("z2-when event:alarm ") })
        assertTrue(calls, adds.any { it.startsWith("z2-when steps:reach=") })
    }
}
