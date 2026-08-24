package com.zerotoship.z2term.emulator

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * alternate scroll (DECSET 1007) の状態遷移と、読み替えて送るカーソルキーのバイト列を固定する。
 *
 * alt screen には scrollback が無いため、マウスレポートを有効化しない全画面 TUI では
 * この読み替えがスワイプでスクロールする唯一の手段になる。UI 側 (`TerminalInputView`) は
 * 「alt screen + マウスレポート OFF + [TerminalEmulator.alternateScrollMode]」の 3 条件で
 * 読み替えを行うので、ここでは 3 条件目の状態管理と [TerminalEmulator.cursorKeyBytes] を見る。
 */
class AlternateScrollModeTest {

    private val ESC = "\u001B"

    private fun newEmulator() =
        TerminalEmulator(output = {}, initialRows = 24, initialColumns = 80)

    private fun TerminalEmulator.feed(s: String) =
        processBytes(s.toByteArray(Charsets.US_ASCII))

    @Test
    fun defaultsToEnabled() {
        // xterm の alternateScroll リソースを true にした状態に相当。1007 を送らない
        // 全画面 TUI (pager / エディタ) でもスワイプを効かせるための既定。
        assertTrue(newEmulator().alternateScrollMode)
    }

    @Test
    fun decrst1007DisablesAndDecset1007Enables() {
        val e = newEmulator()
        e.feed("$ESC[?1007l")
        assertFalse(e.alternateScrollMode)
        e.feed("$ESC[?1007h")
        assertTrue(e.alternateScrollMode)
    }

    @Test
    fun altScreenEntryKeepsExplicitSetting() {
        // 全文表示のような overlay は 1049h と 1007h をまとめて送る。両方を通しても
        // ON のままであること (どちらかの処理がもう一方を巻き戻していないか)。
        val e = newEmulator()
        e.feed("$ESC[?1049h$ESC[?1007h")
        assertTrue(e.alternateScrollMode)
        assertFalse(e.buffer.primaryActive)
    }

    @Test
    fun returningToPrimaryRestoresDefault() {
        // DECRST 1049 だけ送って DECRST 1007 を送り忘れる TUI が居ても、次に alt screen を
        // 使う TUI でスワイプが死なないよう既定へ戻す (mouseProtocol と同じ考え方)。
        val e = newEmulator()
        e.feed("$ESC[?1049h$ESC[?1007l")
        assertFalse(e.alternateScrollMode)
        e.feed("$ESC[?1049l")
        assertTrue(e.alternateScrollMode)
    }

    @Test
    fun risRestoresDefault() {
        val e = newEmulator()
        e.feed("$ESC[?1007l")
        assertFalse(e.alternateScrollMode)
        e.feed("${ESC}c")
        assertTrue(e.alternateScrollMode)
    }

    @Test
    fun cursorKeyBytesFollowDecckm() {
        val e = newEmulator()
        // DECCKM OFF (既定): CSI A / CSI B
        assertArrayEquals(
            byteArrayOf(0x1B, '['.code.toByte(), 'A'.code.toByte()),
            e.cursorKeyBytes(TerminalEmulator.CursorKey.UP)
        )
        assertArrayEquals(
            byteArrayOf(0x1B, '['.code.toByte(), 'B'.code.toByte()),
            e.cursorKeyBytes(TerminalEmulator.CursorKey.DOWN)
        )
        // DECCKM ON: SS3 O A / O B。全画面 TUI は application cursor keys を使うものが
        // 多いので、読み替えたキーもこちらへ追従しないと矢印として認識されない。
        e.feed("$ESC[?1h")
        assertArrayEquals(
            byteArrayOf(0x1B, 'O'.code.toByte(), 'A'.code.toByte()),
            e.cursorKeyBytes(TerminalEmulator.CursorKey.UP)
        )
        assertArrayEquals(
            byteArrayOf(0x1B, 'O'.code.toByte(), 'B'.code.toByte()),
            e.cursorKeyBytes(TerminalEmulator.CursorKey.DOWN)
        )
    }
}
