package com.zerotoship.z2term.channel

import com.zerotoship.z2term.pty.PtyProcess
import java.io.InputStream
import java.io.OutputStream

/**
 * forkpty() ベースのローカル PTY を [ProcessChannel] で包む。
 */
class LocalPtyChannel(private val pty: PtyProcess) : ProcessChannel {
    override val reader: InputStream get() = pty.reader
    override val writer: OutputStream get() = pty.writer
    override val isAlive: Boolean get() = pty.isAlive
    override val exitCode: Int? get() = pty.exitCode

    override fun resize(rows: Int, cols: Int) = pty.resize(rows, cols)
    override fun close() = pty.close()
}
