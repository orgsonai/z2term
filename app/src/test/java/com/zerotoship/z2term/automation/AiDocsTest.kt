package com.zerotoship.z2term.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDocsTest {
    @Test
    fun splitsByHeadingsAndKeepsTheTrail() {
        val text = """
            |# Guide
            |intro
            |## Tabs
            |about tabs
            |### Broadcast
            |about broadcast
            |## Keys
            |about keys
        """.trimMargin()
        val s = AiDocs.split("HANDBOOK.md", text)
        assertEquals(listOf("HANDBOOK > Guide", "HANDBOOK > Guide > Tabs", "HANDBOOK > Guide > Tabs > Broadcast", "HANDBOOK > Guide > Keys"),
            s.map { it.path })
        assertEquals(listOf("HANDBOOK#1", "HANDBOOK#2", "HANDBOOK#3", "HANDBOOK#4"), s.map { it.id })
        assertTrue(s[2].text.contains("about broadcast"))
    }

    @Test
    fun hashInsideCodeIsNotAHeading() {
        val text = """
            |## Shell
            |```sh
            |# a comment, not a heading
            |echo hi
            |```
            |after
        """.trimMargin()
        val s = AiDocs.split("X.md", text)
        assertEquals(1, s.size)
        assertTrue(s[0].text.contains("# a comment") && s[0].text.contains("after"))
    }
}
