package com.zerotoship.z2term.ui.terminal

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint

/**
 * Compose KeyEvent → ターミナル送出バイト列。
 *
 * - KeyDown のみ対応
 * - Ctrl + 英字 → 0x01〜0x1A
 * - Alt + キー → ESC + その文字 (xterm "meta sends escape")
 * - ファンクションキー / カーソル / 編集キーはエスケープシーケンスに変換
 * - 通常の印字可能文字は utf16CodePoint を UTF-8 で送出
 *
 * 解釈不能なキーは null を返す (呼び出し側で OS デフォルト処理に委ねる)。
 */
object PhysicalKeyMapper {

    private val ESC = byteArrayOf(0x1B)
    private val CSI = byteArrayOf(0x1B, '['.code.toByte())
    private val SS3 = byteArrayOf(0x1B, 'O'.code.toByte())

    fun map(event: KeyEvent): ByteArray? {
        if (event.type != KeyEventType.KeyDown) return null

        // 特殊キーの解決を先に
        specialBytes(event)?.let { return it }

        // Ctrl + 英字 → 0x01〜0x1A
        if (event.isCtrlPressed) {
            ctrlBytes(event)?.let { return it }
        }

        // 通常文字
        val cp = event.utf16CodePoint
        if (cp == 0) return null
        val text = String(Character.toChars(cp))
        val bytes = text.toByteArray(Charsets.UTF_8)
        return if (event.isAltPressed) ESC + bytes else bytes
    }

    private fun ctrlBytes(event: KeyEvent): ByteArray? {
        // Compose の Key には a-z の定数があるが、KeyEvent.utf16CodePoint は
        // Ctrl 押下時でも英字コードを返すことが多い (端末次第)。
        val cp = event.utf16CodePoint
        if (cp in 'a'.code..'z'.code) {
            return byteArrayOf((cp - 'a'.code + 1).toByte())
        }
        if (cp in 'A'.code..'Z'.code) {
            return byteArrayOf((cp - 'A'.code + 1).toByte())
        }
        // Ctrl + 特定記号
        return when (cp) {
            ' '.code -> byteArrayOf(0x00)
            '['.code -> byteArrayOf(0x1B)
            '\\'.code -> byteArrayOf(0x1C)
            ']'.code -> byteArrayOf(0x1D)
            '^'.code -> byteArrayOf(0x1E)
            '_'.code, '/'.code -> byteArrayOf(0x1F)
            else -> null
        }
    }

    private fun specialBytes(event: KeyEvent): ByteArray? {
        val base: ByteArray = when (event.key) {
            Key.Enter, Key.NumPadEnter -> byteArrayOf(0x0D)
            Key.Tab -> if (event.isShiftPressed) CSI + 'Z'.code.toByte() else byteArrayOf(0x09)
            Key.Escape -> byteArrayOf(0x1B)
            Key.Backspace -> byteArrayOf(0x7F)
            Key.Delete -> CSI + "3~".toByteArray()
            Key.DirectionUp -> SS3 + 'A'.code.toByte()
            Key.DirectionDown -> SS3 + 'B'.code.toByte()
            Key.DirectionRight -> SS3 + 'C'.code.toByte()
            Key.DirectionLeft -> SS3 + 'D'.code.toByte()
            Key.MoveHome -> CSI + 'H'.code.toByte()
            Key.MoveEnd -> CSI + 'F'.code.toByte()
            Key.PageUp -> CSI + "5~".toByteArray()
            Key.PageDown -> CSI + "6~".toByteArray()
            Key.F1 -> SS3 + 'P'.code.toByte()
            Key.F2 -> SS3 + 'Q'.code.toByte()
            Key.F3 -> SS3 + 'R'.code.toByte()
            Key.F4 -> SS3 + 'S'.code.toByte()
            Key.F5 -> CSI + "15~".toByteArray()
            Key.F6 -> CSI + "17~".toByteArray()
            Key.F7 -> CSI + "18~".toByteArray()
            Key.F8 -> CSI + "19~".toByteArray()
            Key.F9 -> CSI + "20~".toByteArray()
            Key.F10 -> CSI + "21~".toByteArray()
            Key.F11 -> CSI + "23~".toByteArray()
            Key.F12 -> CSI + "24~".toByteArray()
            else -> return null
        }
        return if (event.isAltPressed) ESC + base else base
    }
}
