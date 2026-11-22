package com.zerotoship.z2term.automation

import com.zerotoship.z2term.channel.SerialTranscript
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI のシリアルの道具 ([SerialBridge]) のうち、端末を使わずに決まる部分: 返事の待ち方・読む形・`raw` の読み替え。
 */
class SerialBridgeTest {

    /** 時計を進めながら、決めた時刻に受信を起こす。 */
    private class Fake(private val transcript: SerialTranscript, private val arrivals: Map<Long, String>) {
        var now = 0L
        fun sleep(ms: Long) {
            val from = now
            now += ms
            arrivals.filterKeys { it in (from + 1)..now }.toSortedMap().values
                .forEach { transcript.append(it.toByteArray(Charsets.UTF_8)) }
        }
    }

    private fun run(arrivals: Map<Long, String>, until: Regex?, waitMs: Long = 3_000, quietMs: Long = 500): SerialBridge.Reply {
        val t = SerialTranscript(1024)
        t.append("before".toByteArray())
        val mark = t.total
        val fake = Fake(t, arrivals)
        return SerialBridge.waitReply(t, mark, until, waitMs, quietMs, now = { fake.now }, sleep = fake::sleep)
    }

    @Test
    fun returnsOnceTheLineGoesQuiet() {
        val r = run(mapOf(100L to "ver\r\n", 200L to "1.2\r\n"), until = null)
        assertEquals("ver\r\n1.2\r\n", r.bytes.toString(Charsets.UTF_8))
        assertFalse(r.timedOut)
    }

    @Test
    fun waitsForThePattern() {
        // 途中で 500 ms 以上止まっても、until があれば決まった文字まで待つ (遅い機器のログイン)。
        val r = run(mapOf(100L to "Password: ", 1_500L to "\r\nRouter# "), until = Regex("# $"))
        assertTrue(r.matched)
        assertFalse(r.timedOut)
        assertEquals("Password: \r\nRouter# ", r.bytes.toString(Charsets.UTF_8))
    }

    @Test
    fun patternIsCheckedOnCleanedText() {
        val r = run(mapOf(100L to "\u001B[32m>>> \u001B[0m"), until = Regex(">>> $"))
        assertTrue(r.matched)
    }

    @Test
    fun timesOutWhenNothingComes() {
        val r = run(emptyMap(), until = null, waitMs = 1_000)
        assertTrue(r.timedOut)
        assertEquals(0, r.bytes.size)
    }

    @Test
    fun timesOutWhenThePatternNeverShows() {
        val r = run(mapOf(100L to "booting"), until = Regex("login:"), waitMs = 1_000)
        assertTrue(r.timedOut)
        assertFalse(r.matched)
        assertEquals("booting", r.bytes.toString(Charsets.UTF_8))
    }

    @Test
    fun cleanDropsColoursAndCarriageReturns() {
        assertEquals("ok\nnext\n", SerialBridge.clean("\u001B[1;32mok\u001B[0m\r\nnext\r\n"))
        assertEquals("title gone", SerialBridge.clean("\u001B]0;tab\u0007title gone"))
    }

    @Test
    fun trimmedKeepsTheNewestPart() {
        val long = "a".repeat(SerialBridge.REPLY_MAX) + "END"
        val (text, cut) = SerialBridge.trimmed(long)
        assertTrue(cut)
        assertTrue(text.endsWith("END"))
        assertEquals(SerialBridge.REPLY_MAX, text.length)
    }

    @Test
    fun rawEscapesBecomeBytes() {
        assertArrayEquals(byteArrayOf(3), SerialBridge.decodeEscapes("\\x03"))
        assertArrayEquals(byteArrayOf(0x1B, '['.code.toByte(), 'A'.code.toByte()), SerialBridge.decodeEscapes("\\e[A"))
        assertArrayEquals("a\r\n\\".toByteArray(), SerialBridge.decodeEscapes("a\\r\\n\\\\"))
    }

    @Test(expected = Refusal::class)
    fun badEscapeIsRefused() {
        SerialBridge.decodeEscapes("\\q")
    }
}
