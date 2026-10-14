package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class EdgeSamplePanelsTest {
    @Test fun oneHandleContainsThreeTabsWithWorkingTranslationBindings() {
        val dir = Files.createTempDirectory("sample-panel").toFile()
        try {
            val store = EdgeStore(dir)
            val kinds = listOf(EdgeSamplePanels.Kind.TRANSLATION, EdgeSamplePanels.Kind.TERMINAL, EdgeSamplePanels.Kind.NOTE)
            for (kind in kinds) {
                val panel = EdgeSamplePanels.panel(kind, { "Label" }, "en")
                store.setPanel(panel.id, panel.fields)
                panel.items.forEach { store.setItem("${panel.id}:${it.id}", it.fields) }
            }
            val panels = EdgeStore(dir).panels()
            assertEquals(3, panels.size)
            assertTrue(panels.all { it.fields["alpha"] == "0.3" })
            assertEquals(1, panels.count { it.handle != "off" })
            val root = panels.single { it.handle != "off" }
            assertEquals(listOf("sample-tools-translation", "sample-tools-terminal"), root.tabs)
            assertEquals("note", root.items.single().type)
            assertTrue(EdgePanelLayout.bounded(root, panels))
            val translation = panels.single { it.id == root.tabs.first() }
            val action = translation.items.single { it.type == "run" }
            val values = mapOf("source" to "Hello '世界'", "language" to "en")
            assertEquals(listOf("Hello '世界'", "en"), EdgeMacroForm.resolve(action, translation.items) { values[it] })
            assertEquals("result", action.fields["result"])
            assertEquals("off", translation.items.single { it.type == "result" }.fields["result-controls"])
            assertEquals("terminal", panels.single { it.id == root.tabs.last() }.items.single().type)
        } finally { dir.deleteRecursively() }
    }

    @Test fun guideCommandPreservesExistingDefinitionsAndQuotesLabels() {
        val dir = Files.createTempDirectory("sample-command").toFile()
        try {
            // A tiny CLI double records definitions and reports which targets already exist.
            val cli = dir.resolve("z2-edge")
            cli.writeText("#!/bin/sh\ncase \"\$1\" in\nget) test -f \"\$2\" ;;\npanel|set) target=\$2; shift 2; printf '%s\\n' \"\$@\" > \"\$target\" ;;\n" +
                "tab) printf '%s\\n' \"\$4\" > \"\$3\"; printf '%s\\n' \"\$3\" >> \"\$2.tabs\" ;;\n*) exit 99 ;;\nesac\n")
            cli.setExecutable(true)
            val label = "User's \$(touch injected) label"
            val command = EdgeSamplePanels.command({ label })
            fun run() {
                val process = ProcessBuilder("/bin/sh", "-c", command).directory(dir).apply {
                    environment()["PATH"] = dir.path + ":" + environment()["PATH"]
                    // The optional tabs look for installed macros under HOME, never on the machine running the test.
                    environment()["HOME"] = dir.path
                }.redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText()
                assertEquals(output, 0, process.waitFor())
            }
            run()
            val parent = dir.resolve("sample-tools")
            assertTrue(parent.readText().contains("label=$label\n"))
            assertFalse(dir.resolve("injected").exists())
            parent.writeText("custom settings")
            val item = dir.resolve("sample-tools:note").apply { writeText("custom note") }
            run()
            assertEquals("custom settings", parent.readText())
            assertEquals("custom note", item.readText())
            // No macro installed: no extra tab. Installed later: its tab joins the existing panel, once.
            assertFalse(dir.resolve("sample-tools.tabs").exists())
            dir.resolve(".z2term/macros").mkdirs()
            dir.resolve(".z2term/macros/rss.sh").writeText("#!/bin/sh\n")
            run()
            assertEquals("sample-tools-rss\n", dir.resolve("sample-tools.tabs").readText())
            assertEquals("$label\n", dir.resolve("sample-tools-rss").readText())
            assertTrue(dir.resolve("sample-tools-rss:page").readText().contains("run=rss.sh view\n"))
            dir.resolve(".z2term/macros/remind.sh").writeText("#!/bin/sh\n")
            val page = dir.resolve("sample-tools-rss:page").apply { writeText("custom page") }
            run()
            assertEquals("sample-tools-rss\nsample-tools-remind\n", dir.resolve("sample-tools.tabs").readText())
            assertEquals("custom page", page.readText())
            assertTrue(dir.resolve("sample-tools-remind:page").readText().contains("run=remind.sh view\n"))
            assertEquals("custom settings", parent.readText())
        } finally { dir.deleteRecursively() }
    }
}
