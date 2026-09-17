package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class EdgeMacroFormTest {
    private val input = EdgeStore.Item("input", mapOf("type" to "argument", "required" to "on"))
    private val other = EdgeStore.Item("other", mapOf("type" to "argument"))
    private val result = EdgeStore.Item("output", mapOf("type" to "result"))
    private val action = EdgeStore.Item("run", mapOf("type" to "macro", "args" to "other,input", "result" to "output"))
    private val items = listOf(result, input, action, other)

    @Test fun bindingOrderDoesNotDependOnLayoutAndPreservesEmptyArguments() {
        assertEquals(listOf("", " text\n "), EdgeMacroForm.resolve(action, items) { if (it == "input") " text\n " else "" })
    }

    @Test fun missingOrRetypedBindingsAndRequiredInputsFailBeforeLaunch() {
        for (list in listOf(items.filter { it != result }, items.filter { it != input },
            items.map { if (it == input) it.copy(fields = mapOf("type" to "run")) else it })) {
            assertThrows(IllegalArgumentException::class.java) { EdgeMacroForm.resolve(action, list) { "x" } }
        }
        assertThrows(IllegalArgumentException::class.java) { EdgeMacroForm.resolve(action, items) { " " } }
        assertThrows(IllegalArgumentException::class.java) { EdgeMacroForm.resolve(action, items) { null } }
    }

    @Test fun inputLimitsAreUtf8AndNulIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { EdgeMacroForm.resolve(action, items) { "\u0000" } }
        assertThrows(IllegalArgumentException::class.java) { EdgeMacroForm.resolve(action, items) { "日".repeat(12000) } }
    }

    @Test fun malformedDefinitionsAreRejected() {
        for (fields in listOf(mapOf("args" to "a,,b"), mapOf("args" to "../x"), mapOf("result" to "p:r"),
            mapOf("args" to (1..17).joinToString(",") { "a$it" }), mapOf("rows" to "0"), mapOf("required" to "yes"),
            mapOf("type" to "argument", "argument-kind" to "choice", "choices" to "a|a"),
            mapOf("type" to "argument", "argument-kind" to "choice", "choices" to "a|b", "default" to "c"))) {
            assertThrows(fields.toString(), IllegalArgumentException::class.java) { EdgeStore.validateItem(fields) }
        }
    }

    @Test fun stdinAndArgumentsCanReadFilesAndPreviousResultsWithoutChangingTheirContents() {
        val note = EdgeStore.Item("note", mapOf("type" to "note"))
        val run = EdgeStore.Item("button", mapOf("type" to "run", "args" to "output", "stdin" to "note", "result" to "output"))
        val values = mapOf("note" to " leading\n'quoted'\n", "output" to "previous output")
        val all = listOf(run, result, note)
        assertEquals(listOf("previous output"), EdgeMacroForm.resolve(run, all) { values[it] })
        assertEquals(values["note"], EdgeMacroForm.stdin(run, all) { values[it] })
        assertThrows(IllegalArgumentException::class.java) { EdgeMacroForm.stdin(run, all) { null } }
        assertThrows(IllegalArgumentException::class.java) { EdgeMacroForm.stdin(run, all) { "日".repeat(22000) } }
        assertThrows(IllegalArgumentException::class.java) { EdgeStore.validateItem(mapOf("stdin" to "other:note")) }
    }

    @Test fun literalArgvCannotExecuteShellAndPreservesQuotesNewlinesAndOptionPrefixes() {
        val root = Files.createTempDirectory("edge-form-argv").toFile()
        try {
            val dir = File(root, ".z2term/edge/.runtime").apply { mkdirs() }
            val sentinel = File(root, "injected")
            val args = listOf("", " 日本語\n'\" ", "\$(touch ${sentinel.path}); `touch ${sentinel.path}`", "--help", "*")
            val script = EdgeCommandScript.create("args", "printf '<%s>\\n'", null, null, args)
            val process = ProcessBuilder("sh", "-c", script).apply { environment()["HOME"] = root.path }.start()
            assertEquals(0, process.waitFor())
            assertEquals(args.joinToString("") { "<$it>\n" }, File(dir, "args.out").readText())
            assertEquals("0", File(dir, "args.status").readText())
            assertFalse(sentinel.exists())
        } finally { root.deleteRecursively() }
    }
}
