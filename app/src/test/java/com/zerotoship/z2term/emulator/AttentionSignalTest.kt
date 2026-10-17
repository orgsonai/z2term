package com.zerotoship.z2term.emulator

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「呼んでいる」合図 (BEL / OSC 9 / OSC 777 notify) をハンドラへ渡すことの確認。
 * 裏のタブで入力待ちになった CLI を通知で知らせる機能 (1.0.41) の入口。
 */
class AttentionSignalTest {

    private val esc = "\u001b"
    private val bel = "\u0007"

    private class Seen {
        var bells = 0
        val notes = mutableListOf<Pair<String?, String>>()
    }

    private fun emu(seen: Seen) = TerminalEmulator(output = {}, initialRows = 3, initialColumns = 40,
        bellHandler = { seen.bells++ }, notifyHandler = { title, body -> seen.notes += title to body })

    private fun feed(e: TerminalEmulator, s: String) = e.processBytes(s.toByteArray(Charsets.UTF_8))

    @Test
    fun bareBellIsReported_butOscTerminatorIsNot() {
        val seen = Seen()
        val e = emu(seen)
        feed(e, "a${bel}b")
        // OSC の終端の BEL はベルではない。
        feed(e, "$esc]0;title$bel")
        assertEquals(1, seen.bells)
    }

    @Test
    fun osc9IsANotification_exceptProgress() {
        val seen = Seen()
        val e = emu(seen)
        feed(e, "$esc]9;入力を待っています$bel")
        // 9;4;… は進み具合の表示。通知にすると処理中ずっと鳴る。
        feed(e, "$esc]9;4;1;50$bel")
        feed(e, "$esc]9;4;0$esc\\")
        assertEquals(listOf<Pair<String?, String>>(null to "入力を待っています"), seen.notes)
    }

    @Test
    fun osc777NotifyCarriesTitleAndBody() {
        val seen = Seen()
        val e = emu(seen)
        feed(e, "$esc]777;notify;Agent;Waiting for input$esc\\")
        feed(e, "$esc]777;notify;Only body$bel")
        feed(e, "$esc]777;other;x;y$bel")
        assertEquals(listOf("Agent" to "Waiting for input", null to "Only body"), seen.notes)
    }
}
