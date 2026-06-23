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

    /**
     * `tcgetpgrp(master_fd)` でいま端末を持っているプロセスグループを取り、シェル PID と
     * 違えば子プロセスが前景に居ると判定する。`tcgetpgrp` が失敗 (-1) した場合は安全側に
     * 倒して `true` (= 子プロセス前景扱い)。これは TUI のスクロールを止めない方を優先
     * するため (リーク側は別の救済が効かないだけで悪化しない)。
     */
    override val hasForegroundChild: Boolean
        get() {
            val fg = pty.foregroundPgid()
            if (fg < 0) return true
            return fg != pty.shellPid
        }
}
