package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class EdgePanelCommandsTest {
    @get:Rule val temp = TemporaryFolder()
    private fun store() = EdgeStore(temp.newFolder())

    /** Run the actual exported shell text, then validate and replay its argument vectors in EdgeStore. */
    private fun execute(script: String, target: EdgeStore, failTarget: String = ""): Int {
        val cli = temp.newFolder()
        val state = File(cli, "state").apply { mkdirs() }
        target.panels().forEach { File(state, it.id).writeText(EdgeStore.encode(it.fields)) }
        val calls = File(cli, "calls").apply { writeText("") }
        File(cli, "z2-edge").apply {
            writeText("""
                #!/bin/sh
                verb=${'$'}1
                target=${'$'}2
                shift 2
                case ${'$'}verb in
                  get) [ -f "${'$'}STATE_DIR/${'$'}target" ] || exit 1
                       cat "${'$'}STATE_DIR/${'$'}target" ;;
                  panel) [ "${'$'}target" != "${'$'}FAIL_TARGET" ] || exit 7
                         printf '%s\n' "${'$'}@" > "${'$'}STATE_DIR/${'$'}target" ;;
                  set) : ;;
                  *) exit 9 ;;
                esac
                printf '%s\0' "${'$'}verb" "${'$'}target" "${'$'}@" >> "${'$'}CALLS_FILE"
                printf '\0' >> "${'$'}CALLS_FILE"
            """.trimIndent() + "\n")
            check(setExecutable(true))
        }
        val file = File(cli, "restore.sh").apply { writeText(script) }
        val process = ProcessBuilder("sh", file.path).redirectErrorStream(true).apply {
            environment()["PATH"] = cli.path + ":" + System.getenv("PATH")
            environment()["STATE_DIR"] = state.path
            environment()["CALLS_FILE"] = calls.path
            environment()["FAIL_TARGET"] = failTarget
        }.start()
        try {
            assertTrue("Exported commands timed out", process.waitFor(10, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(output, "", output)
            calls.readText().split("\u0000\u0000").filter { it.isNotEmpty() }.forEach { call ->
                val args = call.split('\u0000')
                val fields = EdgeStore.parse(args.drop(2).joinToString("\n"))
                when (args[0]) {
                    "panel" -> target.setPanel(args[1], fields)
                    "set" -> target.setItem(args[1], fields)
                }
            }
            return process.exitValue()
        } finally { process.destroyForcibly() }
    }

    @Test fun quotesRoundTripWithoutRunningStoredCommands() {
        val original = store()
        val marker = File(temp.root, "must-not-exist")
        val tricky = "  名 前 ' \" \\ \$HOME \$(touch ${marker.path}) `touch ${marker.path}` ; & | = \tend  "
        original.setPanel("main", linkedMapOf("label" to tricky, "handle" to "bar", "width" to "80%",
            "run" to tricky, "actions-double-tap" to "wait:500|back"))
        original.setItem("main:button", mapOf("run" to tricky, "label" to tricky, "off" to "", "button-state" to "on"))
        val restored = store()
        assertEquals(0, execute(EdgePanelCommands.generate(original.panels(), "main"), restored))
        assertEquals(original.panels(), restored.panels())
        assertFalse("Backup generation/restoration must not execute item actions", marker.exists())
    }

    @Test fun parentIncludesAllTabsInSavedOrderAndEveryItemType() {
        val original = store()
        original.setPanel("main", mapOf("handle" to "button", "x" to "80", "y" to "40"))
        original.addTab("main", "second", "Second")
        original.addTab("main", "first", "First")
        listOf("run", "text", "toggle", "list", "input", "note").forEachIndexed { i, type ->
            original.setItem("first:$type", mapOf("type" to type, "order" to (10 - i).toString(), "label" to type))
        }
        original.setItem("second:memo", mapOf("type" to "note", "file" to "~/notes/a b.txt",
            "note-background" to "#FFF4BD", "note-color" to "#000000", "note-lines" to "on"))
        val restored = store()
        val script = EdgePanelCommands.generate(original.panels(), "main")
        assertEquals(0, execute(script, restored))
        assertEquals(original.panels(), restored.panels())
        assertEquals(0, execute(script, restored))
        assertEquals(original.panels(), restored.panels())
        assertFalse(restored.enabled())
    }

    @Test fun selectedTabRecreatesMissingParentWithoutExportingSiblings() {
        val original = store()
        original.setPanel("main", mapOf("handle" to "bar", "width" to "90%"))
        original.addTab("main", "work", "Work")
        original.addTab("main", "private", "Unselected")
        original.setItem("work:clock", mapOf("type" to "text", "run" to "date", "every" to "30"))
        original.setItem("private:hidden", mapOf("run" to "unselected-command"))
        val script = EdgePanelCommands.generate(original.panels(), "work")
        val restored = store()
        assertEquals(0, execute(script, restored))
        assertEquals(original.panel("work"), restored.panel("work"))
        assertEquals(listOf("work"), restored.panel("main").tabs)
        assertEquals("90%", restored.panel("main").fields["width"])
        assertEquals(setOf("main", "work"), restored.panels().map { it.id }.toSet())
        assertFalse(script.contains("unselected-command"))
    }

    @Test fun selectedTabPreservesExistingParentAndDoesNotDuplicateTabReferences() {
        val original = store()
        original.setPanel("main", mapOf("label" to "Original", "width" to "90%"))
        original.addTab("main", "work", "Work")
        val target = store()
        target.setPanel("main", mapOf("label" to "Keep", "width" to "70%"))
        target.addTab("main", "workshop", "Keep sibling")
        target.setItem("workshop:keep", mapOf("run" to "date"))
        val sibling = target.panel("workshop")
        val script = EdgePanelCommands.generate(original.panels(), "work")
        assertEquals(0, execute(script, target))
        assertEquals(listOf("workshop", "work"), target.panel("main").tabs)
        assertEquals("70%", target.panel("main").fields["width"])
        assertEquals("Keep", target.panel("main").fields["label"])
        assertEquals(sibling, target.panel("workshop"))
        target.setPanel("main", mapOf("tabs" to "work,workshop"))
        assertEquals(0, execute(script, target))
        assertEquals(listOf("work", "workshop"), target.panel("main").tabs)
    }

    @Test fun emptyDefinitionsRemainUsableAndExternalFilesAreNotIncluded() {
        val original = store()
        original.setPanel("empty", emptyMap())
        original.setItem("empty:run", emptyMap())
        original.setItem("empty:memo", mapOf("type" to "note"))
        File(original.directory("empty"), "memo.txt").writeText("note body not part of a definition")
        val script = EdgePanelCommands.generate(original.panels(), "empty")
        val target = store()
        assertEquals(0, execute(script, target))
        assertEquals("off", target.panel("empty").handle)
        assertEquals("run", target.item("empty:run").type)
        assertFalse(script.contains("note body"))
    }

    @Test fun stopsAfterCliFailureAndRejectsMissingSelection() {
        val original = store()
        original.setPanel("main", mapOf("handle" to "bar"))
        original.setItem("main:item", mapOf("run" to "date"))
        val target = store()
        assertEquals(7, execute(EdgePanelCommands.generate(original.panels(), "main"), target, "main"))
        assertTrue(target.panels().isEmpty())
        assertThrows(IllegalArgumentException::class.java) { EdgePanelCommands.generate(original.panels(), "missing") }
    }
}
