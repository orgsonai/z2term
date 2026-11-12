package com.zerotoship.z2term.edge

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPickerOrderTest {
    private fun entry(label: String, profile: Long?) = AppPickerActivity.Entry("org.example.app", label, label, profile) { null }

    /** The same name in z2term's own profile and another one used to compare a Long with an Int and crash. */
    @Test fun theSameAppInTwoProfilesSortsOwnFirst() {
        val sorted = listOf(entry("B", 10L), entry("A", 11L), entry("B", null), entry("A", null)).sortedWith(AppPickerActivity.Entry.order)
        assertEquals(listOf("A" to null, "A" to 11L, "B" to null, "B" to 10L), sorted.map { it.sortLabel to it.profile })
    }
}
