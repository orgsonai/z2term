package com.zerotoship.z2term.service

import com.zerotoship.z2term.settings.PromptAnswer
import com.zerotoship.z2term.settings.WhenCommandLine
import com.zerotoship.z2term.settings.WhenRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptResponderTest {

    private class Terminal(answers: List<PromptAnswer>) {
        var echoOff = false
        val sent = ArrayList<String>()
        val notes = ArrayList<String>()
        var aborted = false
        val responder = PromptResponder(answers, { echoOff }, { sent += it }, { notes += it }, { aborted = true })
        fun out(text: String) = text.toByteArray().let { responder.onOutput(it, it.size) }
        fun ticks(n: Int) = repeat(n) { responder.tick() }
    }

    @Test
    fun `answers a visible question once the output goes quiet`() {
        val t = Terminal(listOf(PromptAnswer("[y/N]", "y")))
        t.out("Proceed? [y/N] ")
        t.ticks(1)
        assertTrue(t.sent.isEmpty())
        t.ticks(1)
        assertEquals(listOf("y\r"), t.sent)
        t.ticks(5)
        assertEquals(1, t.sent.size)
    }

    @Test
    fun `answers the same visible question each time it comes back`() {
        val t = Terminal(listOf(PromptAnswer("[y/N]", "y")))
        repeat(3) { t.out("y\r\nNext? [y/N] "); t.ticks(2) }
        assertEquals(3, t.sent.size)
    }

    @Test
    fun `does not answer text in the middle of the output`() {
        val t = Terminal(listOf(PromptAnswer("Password:", "x", secret = true)))
        t.echoOff = true
        t.out("Password: was asked\nworking")
        t.ticks(5)
        assertTrue(t.sent.isEmpty())
    }

    @Test
    fun `a hidden reply waits until the terminal hides input`() {
        val t = Terminal(listOf(PromptAnswer("Password:", "pw", secret = true)))
        t.out("\u001B[1mPassword:\u001B[0m ")
        t.ticks(10)
        assertTrue(t.sent.isEmpty())
        t.echoOff = true
        t.ticks(1)
        assertEquals(listOf("pw\r"), t.sent)
        assertTrue(t.notes.none { "pw" in it })
    }

    @Test
    fun `a hidden reply is never sent to a terminal that shows input`() {
        val t = Terminal(listOf(PromptAnswer("Password:", "pw", secret = true)))
        t.out("Password: ")
        t.ticks(PromptResponder.SECRET_WAIT_TICKS + 5)
        assertTrue(t.sent.isEmpty())
        assertEquals(1, t.notes.size)
    }

    @Test
    fun `a hidden reply asked twice stops the run instead of resending`() {
        val t = Terminal(listOf(PromptAnswer("Password:", "pw", secret = true)))
        t.echoOff = true
        t.out("Password: ")
        t.ticks(1)
        t.out("\r\nwrong\r\nPassword: ")
        t.ticks(5)
        assertEquals(1, t.sent.size)
        assertTrue(t.aborted)
    }

    @Test
    fun `the longer prompt wins and case is ignored`() {
        val answers = listOf(PromptAnswer("?", "a"), PromptAnswer("continue?", "b"))
        assertEquals("b", PromptTail.match("Do you want to Continue? ", answers)?.text)
        assertNull(PromptTail.match("   ", answers))
    }

    @Test
    fun `rule file keeps replies and never the hidden content`() {
        val rule = WhenRule(
            id = "w1", trigger = "boot", run = "sync",
            answers = listOf(PromptAnswer("a=>b? [y/N]", " y"), PromptAnswer("Password:", "", secret = true)),
        )
        val text = rule.serialize()
        assertTrue("answer=a=>b? [y/N]=> y\n" in text)
        assertTrue("secret=Password:\n" in text)
        assertEquals(rule, WhenRule.parse("w1", text))
        assertEquals(
            "z2-when boot answer='a=>b? [y/N]=> y' secret=Password: run sync",
            WhenCommandLine.of(rule),
        )
    }

    @Test
    fun `runs in the tab shell with its startup files`() {
        assertEquals("/bin/zsh", HeadlessRun.shellFor("/bin/zsh"))
        assertEquals("/bin/sh", HeadlessRun.shellFor("/usr/bin/fish"))
        assertEquals(listOf("-l", "-i", "-c", "x"), HeadlessRun.shellArgs("/bin/zsh", "x"))
        assertEquals(listOf("-l", "-i", "-c", "x"), HeadlessRun.shellArgs("/bin/sh", "x"))
        assertEquals(listOf("-i", "-c", "x"), HeadlessRun.shellArgs("/usr/bin/bash", "x"))
    }
}
