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

    /** A progress line redrawn with CR must replace itself, not pile up into one endless line. */
    @Test fun carriageReturnStartsTheLineOverButCrLfIsAPlainNewline() {
        val parser = EdgeTerminalProtocol("abc") {}
        "one\r\n 10%\r 50%\r\u001b[K100%\r\ndone\r".forEach { parser.append(it.toString()) }
        assertEquals("one\n100%\ndone", parser.text)
    }

    @Test fun foregroundJobIsReportedOnlyWhenItIsNotTheShell() {
        fun stat(group: Int, foreground: Int) = "456 (sh) S 1 $group 456 34816 $foreground 0 0"
        assertEquals(789, EdgeTerminalProcesses.foregroundJob(stat(456, 789)))
        assertNull(EdgeTerminalProcesses.foregroundJob(stat(456, 456)))
        assertNull(EdgeTerminalProcesses.foregroundJob(stat(456, -1)))
    }

    /**
     * Runs the real shell loop on a pseudo-terminal: a running command can be answered, and an answer
     * that arrives after its command ended is dropped instead of being run as the next command.
     */
    @Test fun runningCommandReadsAnswersAndLateAnswersAreNeverRun() {
        val script = listOf("/usr/bin/script", "/bin/script").firstOrNull { java.io.File(it).canExecute() }
        org.junit.Assume.assumeTrue("no script(1) to provide a pseudo-terminal", script != null)
        val events = java.util.concurrent.CopyOnWriteArrayList<String>()
        val parser = EdgeTerminalProtocol("abc") { events.add(it) }
        val builder = ProcessBuilder(script!!, "-q", "-c", "exec sh -i -c \"\$Z2_TEST_SCRIPT\"", "/dev/null").redirectErrorStream(true)
        builder.environment()["Z2_TEST_SCRIPT"] = EdgeTerminalProtocol.script("abc")
        val process = builder.start()
        val reader = Thread {
            runCatching {
                val input = java.io.InputStreamReader(process.inputStream, Charsets.UTF_8)
                val chars = CharArray(1024)
                while (true) { val n = input.read(chars); if (n < 0) break; synchronized(parser) { parser.append(String(chars, 0, n)) } }
            }
        }.apply { isDaemon = true; start() }
        fun await(what: String, test: () -> Boolean) {
            val end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10)
            while (!test()) { assertTrue("timed out waiting for $what: ${synchronized(parser) { parser.text }}", System.nanoTime() < end); Thread.sleep(20) }
        }
        fun write(text: String) { process.outputStream.write(text.toByteArray()); process.outputStream.flush() }
        fun text() = synchronized(parser) { parser.text }
        try {
            await("ready") { events.any { it.startsWith("ready:") } }
            write(EdgeTerminalProtocol.commandLine("abc", "printf 'ok? '; read answer; echo got=\$answer"))
            await("the prompt") { text().contains("ok? ") }
            write("y\n")
            await("the first command") { events.count { it.startsWith("done:") } == 1 }
            assertTrue(text(), text().contains("got=y"))
            write("echo LATE-ANSWER-WAS-RUN\n")
            write(EdgeTerminalProtocol.commandLine("abc", "echo next"))
            await("the second command") { events.count { it.startsWith("done:") } == 2 }
            assertTrue(text(), text().contains("next"))
            assertFalse(text(), text().contains("LATE-ANSWER-WAS-RUN"))
        } finally { process.destroyForcibly(); reader.join(1000) }
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
            // The pinched text size is saved on the item and survives into the recreation commands.
            store.setItem("main:term", mapOf("terminal-size" to "20"))
            assertEquals("20", store.item("main:term").fields["terminal-size"])
            assertTrue(EdgePanelCommands.generate(store.panels(), "main").contains("terminal-size=20"))
            assertTrue(runCatching { store.setItem("main:term", mapOf("terminal-size" to "40")) }.isFailure)
        } finally { dir.deleteRecursively() }
    }
}
