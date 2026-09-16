package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class EdgeTerminalTest {
    @Test fun framesAndAnsiCanBeSplitAtEveryCharacterWithoutLeakingIntoResults() {
        val events = mutableListOf<String>()
        val parser = EdgeTerminalProtocol("abc", events::add)
        "\u001eabc:ready:123\u001f\u001b[31m日本語\u001b[0m\r\n\u001b]0;title\u0007ok\u001eabc:done:0\u001f"
            .forEach { parser.append(it.toString()) }
        assertEquals(listOf("ready:123", "done:0"), events)
        assertEquals("日本語\nok", parser.text)
    }

    @Test fun clearsPreviousResultAndBoundsUnendingOutput() {
        val parser = EdgeTerminalProtocol("abc") {}
        repeat(100) { parser.append("x".repeat(4096)) }
        assertEquals(EdgeTerminalProtocol.LIMIT, parser.text.length)
        assertTrue(parser.truncated)
        parser.clear(); parser.append("failed\n")
        assertEquals("failed\n", parser.text)
        assertFalse(parser.truncated)
    }

    @Test fun unknownFramesAndIncompleteEscapesDoNotFabricateCompletion() {
        val events = mutableListOf<String>()
        val parser = EdgeTerminalProtocol("abc", events::add)
        parser.append("\u001eother:done:0\u001fhi\u001b]title\u001b")
        parser.append("\\there")
        assertTrue(events.isEmpty())
        assertEquals("hithere", parser.text)
    }

    @Test fun parsesProcNamesWithSpacesAndParenthesesAndHupMask() {
        val fields = MutableList(20) { "0" }
        fields[0] = "S"; fields[1] = "12"; fields[3] = "100"; fields[19] = "23456"
        val parsed = EdgeTerminalProcesses.parse(456, "456 (worker (long) name) ${fields.joinToString(" ")}",
            "TracerPid:\t100\nSigIgn:\t0000000000000001\n")!!
        assertEquals(100, parsed.session)
        assertEquals(23456L, parsed.started)
        assertTrue(parsed.hupIgnored)
        assertEquals(100, parsed.tracer)
    }

    @Test fun cleanupExcludesTracersNohupAndDetachedMultiplexersButIncludesDaemonizedServers() {
        fun proc(pid: Int, session: Int = 100, hup: Boolean = false, tracer: Int = 100) =
            EdgeTerminalProcesses.Process(pid, 100, session, pid.toLong(), hup, tracer, false)
        val all = listOf(proc(100, tracer = 0), proc(101), proc(102), proc(103, hup = true),
            proc(104, session = 104).copy(name = "tmux: server"),
            proc(105, session = 105).copy(parent = 104), proc(106, session = 106).copy(name = "server"))
        assertEquals(listOf(101, 102, 106), EdgeTerminalProcesses.targets(all, 100, 100).map { it.pid })
    }

    @Test fun reusedPidOrZombieCannotReceiveDelayedCleanup() {
        val old = EdgeTerminalProcesses.Process(100, 1, 100, 1234, false, 0, false)
        assertTrue(EdgeTerminalProcesses.sameProcess(old, old))
        assertFalse(EdgeTerminalProcesses.sameProcess(old, old.copy(started = 1235)))
        assertFalse(EdgeTerminalProcesses.sameProcess(old, old.copy(session = 200)))
        assertFalse(EdgeTerminalProcesses.sameProcess(old, old.copy(zombie = true)))
        assertFalse(EdgeTerminalProcesses.sameProcess(old, null))
    }

    @Test fun terminalDefinitionRoundTripsWithoutAConfiguredCommand() {
        val dir = Files.createTempDirectory("edge-terminal-test").toFile()
        try {
            val store = EdgeStore(dir)
            store.setPanel("main", emptyMap())
            store.setItem("main:term", mapOf("type" to "terminal", "label" to "Terminal"))
            val item = store.item("main:term")
            assertEquals("terminal", item.type)
            assertEquals("", item.command)
            assertTrue(EdgePanelCommands.generate(store.panels(), "main").contains("'type=terminal'"))
        } finally { dir.deleteRecursively() }
    }
}
