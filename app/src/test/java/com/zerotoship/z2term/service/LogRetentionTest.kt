package com.zerotoship.z2term.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** 通知・SMS の記録を何日分だけ残す (1.0.50)。書式に関係なく、控えのバイト数で切り落とす。 */
class LogRetentionTest {
    @get:Rule val tmp = TemporaryFolder()

    private val day = 86_400_000L
    private val t0 = 1_800_000_000_000L

    private fun log() = tmp.newFolder().resolve("sms.jsonl")

    @Test fun appendModeDropsOldEntriesFromTheHead() {
        val f = log()
        LogWriter.write(f, "old\nsecond line", prepend = false, keepDays = 30, now = t0)
        LogWriter.write(f, "mid", prepend = false, keepDays = 30, now = t0 + 10 * day)
        LogWriter.write(f, "new", prepend = false, keepDays = 30, now = t0 + 35 * day)
        assertEquals("mid\nnew\n", f.readText())
        assertTrue(LogRetention.consistent(LogRetention.read(LogRetention.indexFile(f))!!, f.length()))
    }

    @Test fun prependModeDropsOldEntriesFromTheTail() {
        val f = log()
        LogWriter.write(f, "old", prepend = true, keepDays = 7, now = t0)
        LogWriter.write(f, "mid", prepend = true, keepDays = 7, now = t0 + 3 * day)
        LogWriter.write(f, "new", prepend = true, keepDays = 7, now = t0 + 8 * day)
        assertEquals("new\nmid\n", f.readText())
    }

    @Test fun unlimitedKeepsEverything() {
        val f = log()
        LogWriter.write(f, "old", prepend = false, keepDays = 0, now = t0)
        LogWriter.write(f, "new", prepend = false, keepDays = 0, now = t0 + 999 * day)
        assertEquals("old\nnew\n", f.readText())
    }

    @Test fun multibyteTextIsCountedInBytes() {
        val f = log()
        LogWriter.write(f, "認証コード 123456", prepend = false, keepDays = 1, now = t0)
        LogWriter.write(f, "新着", prepend = false, keepDays = 1, now = t0 + 2 * day)
        assertEquals("新着\n", f.readText())
    }

    @Test fun entriesFromBeforeTheIndexAreCountedFromToday() {
        val f = log()
        f.writeText("written by an older version\n")
        LogWriter.write(f, "a", prepend = false, keepDays = 30, now = t0)
        LogWriter.write(f, "b", prepend = false, keepDays = 30, now = t0 + 29 * day)
        assertEquals("written by an older version\na\nb\n", f.readText())
        LogWriter.write(f, "c", prepend = false, keepDays = 30, now = t0 + 31 * day)
        assertEquals("b\nc\n", f.readText())
    }

    @Test fun handEditedFileIsNeverCutByStaleIndex() {
        val f = log()
        LogWriter.write(f, "old", prepend = false, keepDays = 30, now = t0)
        f.writeText("kept by hand, longer than before\n")
        LogWriter.write(f, "new", prepend = false, keepDays = 30, now = t0 + 40 * day)
        assertEquals("kept by hand, longer than before\nnew\n", f.readText())
    }

    @Test fun switchingPrependMidwayKeepsEverythingUntilRecounted() {
        val f = log()
        LogWriter.write(f, "a", prepend = false, keepDays = 5, now = t0)
        LogWriter.write(f, "b", prepend = true, keepDays = 5, now = t0 + 6 * day)
        assertEquals("b\na\n", f.readText())
    }

    @Test fun consistencyRules() {
        val a = LogRetention.Entry(t0, 3, LogRetention.APPEND)
        val p = LogRetention.Entry(t0, 3, LogRetention.PREPEND)
        val u = LogRetention.Entry(t0, 3, LogRetention.UNKNOWN)
        assertTrue(LogRetention.consistent(listOf(u, a, a), 9))
        assertTrue(LogRetention.consistent(listOf(u, p), 6))
        assertFalse(LogRetention.consistent(listOf(a, p), 6))
        assertFalse(LogRetention.consistent(listOf(a, u), 6))
        assertFalse(LogRetention.consistent(listOf(a), 4))
    }

    @Test fun compactMergesTheSameHour() {
        val e = LogRetention.compact(listOf(
            LogRetention.Entry(t0, 1, LogRetention.APPEND),
            LogRetention.Entry(t0 + 60_000, 2, LogRetention.APPEND),
            LogRetention.Entry(t0 + 2 * 3_600_000, 4, LogRetention.APPEND),
        ))
        assertEquals(listOf(LogRetention.Entry(t0 + 60_000, 3, LogRetention.APPEND),
            LogRetention.Entry(t0 + 2 * 3_600_000, 4, LogRetention.APPEND)), e)
    }
}
