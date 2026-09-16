package com.zerotoship.z2term.snippets

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class SnippetTemplateTest {
    @Test fun fieldsAndDefaults() {
        val fields = SnippetTemplate.fields("convert {{画像:file}} {{幅:number=1080}} {{形式:choice=png|jpg}} {{文章}}")
        assertEquals(listOf("画像", "幅", "形式", "文章"), fields.map { it.name })
        assertEquals("1080", fields[1].initial)
        assertEquals(listOf("png", "jpg"), fields[2].choices)
    }

    @Test fun valuesRemainDataIncludingQuotesAndShellSyntax() {
        val dir = Files.createTempDirectory("snippet-input").toFile()
        try {
            val marker = java.io.File(dir, "executed")
            val value = "a' b\n\$(touch ${marker.absolutePath}) `touch ${marker.absolutePath}`; *"
            val command = SnippetTemplate.render("printf '%s' {{文章}}", mapOf("文章" to value))
            val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
            assertEquals(value, process.inputStream.bufferedReader().readText())
            assertEquals(0, process.waitFor())
            assertFalse(marker.exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun repeatAndEmptyValues() {
        val command = SnippetTemplate.render("printf '[%s][%s]' {{text}} {{text}}", mapOf("text" to ""))
        val process = ProcessBuilder("sh", "-c", command).start()
        assertEquals("[][]", process.inputStream.bufferedReader().readText())
        assertEquals(0, process.waitFor())
    }

    @Test fun fileHomeIsExpandedWithoutEvaluatingTheFilename() {
        val command = SnippetTemplate.render("printf '%s' {{file:file}}", mapOf("file" to "~/a ' \$(printf bad).png"))
        val process = ProcessBuilder("sh", "-c", command).apply { environment()["HOME"] = "/tmp/test home" }.start()
        assertEquals("/tmp/test home/a ' \$(printf bad).png", process.inputStream.bufferedReader().readText())
        assertEquals(0, process.waitFor())
    }

    @Test fun invalidDefinitionsAndValuesAreRejected() {
        for (command in listOf("echo '{{text}}'", "echo \" {{text}} \"", "echo x{{text}}", "echo {{broken", "echo {{x:unknown}}", "echo {{x:choice=a}}", "echo {{x}} {{x:number}}")) {
            assertTrue(command, runCatching { SnippetTemplate.fields(command) }.isFailure)
        }
        assertTrue(runCatching { SnippetTemplate.render("echo {{width:number}}", mapOf("width" to "1;exit")) }.isFailure)
        assertTrue(runCatching { SnippetTemplate.render("echo {{type:choice=a|b}}", mapOf("type" to "c")) }.isFailure)
        assertTrue(runCatching { SnippetTemplate.render("echo {{file:file}}", emptyMap()) }.isFailure)
        assertEquals("echo plain", SnippetTemplate.render("echo plain", emptyMap()))
    }
}
