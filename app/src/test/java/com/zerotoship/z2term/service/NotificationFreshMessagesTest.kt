package com.zerotoship.z2term.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 会話 (MessagingStyle) の「前回の続きから」判定
 * ([NotificationLogService.freshMessageText]) の検証。
 *
 * 実機で、続けて届いた 4 通が 1 回の通知にまとめられ、`EXTRA_TEXT` からは
 * **最後の 1 通を短くしたものしか取れずに 3 通が記録から消えていた** (0.8.358 で修正)。
 * 会話の全文は `EXTRA_MESSAGES` にあるが、そちらは**毎回、直近の数通をまるごと**載せてくる
 * ので、前回の続きだけを切り出す必要がある。
 */
class NotificationFreshMessagesTest {

    private fun sig(m: Pair<Long, String>) = NotificationLogService.messageSig(m)
    private fun fresh(all: List<Pair<Long, String>>, prev: String?) =
        NotificationLogService.freshMessageText(all, prev)

    private val conversation = listOf(
        10L to "午後①",
        20L to "午後②",
        30L to "午後③",
        40L to "午後④",
    )

    /** 実機の症状そのもの: 4 通まとめて 1 回で届く。初回なので 4 通とも残る。 */
    @Test
    fun firstTimeKeepsEveryMessage() {
        assertEquals("午後①\n午後②\n午後③\n午後④", fresh(conversation, null))
    }

    /** ②まで記録済みなら、続きの ③④ だけ。 */
    @Test
    fun onlyWhatCameAfterTheMark() {
        assertEquals("午後③\n午後④", fresh(conversation, sig(20L to "午後②")))
    }

    /** 最後まで記録済み = 新着ゼロ。null を返す (空文字だと題名だけの行が残る)。 */
    @Test
    fun nothingNewReturnsNull() {
        assertNull(fresh(conversation, sig(40L to "午後④")))
    }

    /** 印が会話の中に無い (初回・LRU からあふれた・送り手が印を変えた) なら全部返す。 */
    @Test
    fun unknownMarkFallsBackToEverything() {
        assertEquals("午後①\n午後②\n午後③\n午後④", fresh(conversation, sig(99L to "知らない発言")))
    }

    @Test
    fun emptyConversationReturnsNull() {
        assertNull(fresh(emptyList(), null))
        assertNull(fresh(emptyList(), sig(10L to "午後①")))
    }

    /** 同じ本文が会話に 2 度あるときは、**新しい側**を続きの起点にする。 */
    @Test
    fun repeatedTextResumesFromTheLatestMatch() {
        val repeated = listOf(1L to "はい", 2L to "ありがとう", 3L to "はい", 4L to "また明日")
        assertEquals("また明日", fresh(repeated, sig(3L to "はい")))
    }

    /** 印は時刻と本文の両方を映す (同時刻に複数届く / 時刻を持たない送り手がいるため)。 */
    @Test
    fun markReflectsBothTimeAndText() {
        assertEquals(sig(1L to "a"), sig(1L to "a"))
        assertNotEquals(sig(1L to "a"), sig(2L to "a"))
        assertNotEquals(sig(1L to "a"), sig(1L to "b"))
    }

    /** 時刻を持たない送り手 (全部 0) でも、本文が違えば続きを切り出せる。 */
    @Test
    fun worksWhenSenderHasNoTimestamps() {
        val noTime = listOf(0L to "一通目", 0L to "二通目", 0L to "三通目")
        assertEquals("三通目", fresh(noTime, sig(0L to "二通目")))
    }
}
