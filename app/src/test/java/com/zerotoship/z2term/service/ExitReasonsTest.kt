package com.zerotoship.z2term.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 前回までの死因 ([ExitReasons]) の読み方を固定する。
 *
 * ここが崩れると「落ちた理由」の欄が黙って嘘をつく。**落ちた後にしか見ないもの**なので、
 * 嘘に気付くのは次に困ったときになる = 一番たちが悪い。
 */
class ExitReasonsTest {

    /** OS の理由コードは訳さずそのまま出す (検索できる語であることに意味がある)。 */
    @Test
    fun labelsReasonCodes() {
        assertEquals("LOW_MEMORY", ExitReasons.reasonLabel(3))
        assertEquals("CRASH", ExitReasons.reasonLabel(4))
        assertEquals("CRASH_NATIVE", ExitReasons.reasonLabel(5))
        assertEquals("ANR", ExitReasons.reasonLabel(6))
        assertEquals("SIGNALED", ExitReasons.reasonLabel(2))
        assertEquals("PACKAGE_UPDATED", ExitReasons.reasonLabel(16))
    }

    /** 知らないコードでも**番号は残す**。新しい Android で理由が増えても診断が空にならない。 */
    @Test
    fun keepsUnknownReasonNumbers() {
        assertTrue(ExitReasons.reasonLabel(99).contains("99"))
    }

    /**
     * 入れ替え・ユーザー操作・自分から終了は**異常ではない**。
     * ⚠ ここを異常に数えると、開発中の入れ替えだけで欄が埋まって本当の事故が埋もれる。
     */
    @Test
    fun updatesAndUserActionsAreNotFailures() {
        assertFalse("自分から終了", ExitReasons.isAbnormal(1))
        assertFalse("ユーザーが要求", ExitReasons.isAbnormal(10))
        assertFalse("ユーザーが停止", ExitReasons.isAbnormal(11))
        assertFalse("入れ替え", ExitReasons.isAbnormal(16))
        assertFalse("凍結の後片付け", ExitReasons.isAbnormal(14))
    }

    /** 殺された・落ちた・応答しなかったは異常。ここを取りこぼすと診断の意味が無い。 */
    @Test
    fun killsAndCrashesAreFailures() {
        assertTrue("シグナルで終了", ExitReasons.isAbnormal(2))
        assertTrue("メモリ不足", ExitReasons.isAbnormal(3))
        assertTrue("クラッシュ", ExitReasons.isAbnormal(4))
        assertTrue("ネイティブのクラッシュ", ExitReasons.isAbnormal(5))
        assertTrue("ANR", ExitReasons.isAbnormal(6))
        assertTrue("資源の使い過ぎ", ExitReasons.isAbnormal(9))
    }

    /** 9 = SIGKILL (外から強制終了された) が一番よく出るので、番号のままにしない。 */
    @Test
    fun namesTheCommonSignals() {
        assertEquals("SIGKILL", ExitReasons.signalLabel(9))
        assertEquals("SIGSEGV", ExitReasons.signalLabel(11))
        assertTrue(ExitReasons.signalLabel(42).contains("42"))
    }

    /**
     * 1 行に**理由・死んだ時の RSS・そのときの扱われ方**が揃うこと。
     * ⚠ FOREGROUND_SERVICE のまま殺されていたかどうかは「常駐が守れているか」の話なので、
     * ここが落ちると症状の切り分けができなくなる。
     */
    @Test
    fun buildsOneReadableLine() {
        val line = ExitReasons.Record(
            ts = 1_787_314_454_000L,
            reason = 3,               // LOW_MEMORY
            status = 0,
            importance = 125,         // IMPORTANCE_FOREGROUND_SERVICE
            rssKb = 409_600L,
            pid = 5486,
            processName = "com.zerotoship.z2term",
            description = "isolated not needed",
        ).line()
        assertTrue(line, line.contains("LOW_MEMORY"))
        assertTrue(line, line.contains("rss=400MB"))
        assertTrue(line, line.contains("FOREGROUND_SERVICE"))
        assertTrue(line, line.contains("isolated not needed"))
    }

    /** シグナルで死んだ行は**どのシグナルか**まで出す (SIGKILL かどうかで話が変わる)。 */
    @Test
    fun showsTheSignalForSignaledExits() {
        val line = ExitReasons.Record(
            ts = 1_787_314_454_000L, reason = 2, status = 9, importance = 400,
            rssKb = 0L, pid = 1, processName = "p", description = "",
        ).line()
        assertTrue(line, line.contains("SIGNALED(SIGKILL)"))
        assertFalse("RSS が 0 のときは出さない", line.contains("rss="))
    }

    /**
     * 重複を弾く時刻の拾い出しは**壊れた行で落ちない**こと。
     * 診断のためのログで例外を投げたら本末転倒 (書けなくなるだけでなく、起動の邪魔になる)。
     */
    @Test
    fun ignoresBrokenLinesWhenDeduping() {
        val ts = ExitReasons.knownTimestamps(
            listOf("""{"ts":100,"reason":"CRASH"}""", "壊れた行", "", """{"ts":200}""", "{}")
        )
        assertEquals(setOf(100L, 200L), ts)
    }
}
