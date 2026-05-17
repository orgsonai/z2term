package com.zerotoship.z2term.channel

import java.io.InputStream
import java.io.OutputStream

/**
 * ターミナルに繋がる入出力チャンネルの抽象。
 *
 * - ローカル PTY (PRoot 経由) は [LocalPtyChannel]
 * - リモート SSH は [SshChannel]
 *
 * TerminalSession から見て両者を同じ API で扱える。
 */
interface ProcessChannel {
    val reader: InputStream
    val writer: OutputStream
    val isAlive: Boolean
    val exitCode: Int?

    fun resize(rows: Int, cols: Int)
    fun close()
}
