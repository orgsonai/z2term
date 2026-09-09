package com.zerotoship.z2term.edge

import org.junit.Assert.assertEquals
import org.junit.Test

class EdgeMacroCommandTest {
    @Test fun macroNameResolvesWithArgumentsIntact() {
        assertEquals("sh \"\$HOME/.z2term/macros/\"'sample.sh' \"two words\" | cat",
            EdgeMacroCommand.resolve("sample.sh \"two words\" | cat", listOf("sample.sh")))
    }

    @Test fun selectedFileNameIsQuotedIncludingShellCharacters() {
        assertEquals("sh \"\$HOME/.z2term/macros/\"'a'\"'\"'\$(id).sh'",
            EdgeMacroCommand.resolve("a'\$(id).sh", listOf("a'\$(id).sh")))
        assertEquals("sh \"\$HOME/.z2term/macros/\"'two words.sh'",
            EdgeMacroCommand.resolve("two words.sh", listOf("two words.sh")))
    }

    @Test fun ordinaryCommandsRemainUnchanged() {
        assertEquals(" echo hello ", EdgeMacroCommand.resolve(" echo hello ", listOf("sample.sh")))
        assertEquals("sample.sh", EdgeMacroCommand.resolve("sample.sh", emptyList()))
    }
}
