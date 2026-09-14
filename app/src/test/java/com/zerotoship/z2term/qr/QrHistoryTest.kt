package com.zerotoship.z2term.qr

import org.junit.Assert.*
import org.junit.Test

class QrHistoryTest {
    @Test fun rereadingMovesToTheTopAndKeepsThePin() {
        var list = QrHistory.add(emptyList(), "a", 1)
        list = QrHistory.add(list, "b", 2)
        list = QrHistory.setPinned(list, "a", true)
        list = QrHistory.add(list, "a", 3)
        assertEquals(listOf("a", "b"), list.map { it.text })
        assertTrue(list.first().pinned)
        assertEquals(3L, list.first().time)
    }

    @Test fun unpinnedEntriesAreCappedButPinnedOnesStay() {
        var list = QrHistory.setPinned(QrHistory.add(emptyList(), "pinned", 0), "pinned", true)
        for (i in 1..QrHistory.MAX_UNPINNED + 5) list = QrHistory.add(list, "t$i", i.toLong())
        assertEquals(QrHistory.MAX_UNPINNED, list.count { !it.pinned })
        assertTrue(list.any { it.text == "pinned" && it.pinned })
        assertFalse(list.any { it.text == "t5" })
        assertTrue(list.any { it.text == "t6" })
    }

    @Test fun clearingKeepsPinnedEntriesAndPinnedOnesAreShownFirst() {
        var list = QrHistory.add(QrHistory.add(QrHistory.add(emptyList(), "old", 1), "mid", 2), "new", 3)
        list = QrHistory.setPinned(list, "old", true)
        assertEquals(listOf("old", "new", "mid"), QrHistory.ordered(list).map { it.text })
        assertEquals(listOf("new", "old"), QrHistory.remove(list, "mid").map { it.text })
        assertEquals(listOf("old"), QrHistory.clearUnpinned(list).map { it.text })
    }
}
