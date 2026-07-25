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
     * プロンプト待機中 (子プロセスが前景に居ない) の前景プロセスグループ ID を基準値として保持する。
     *
     * `pty.shellPid` は forkpty の子 = **エンジン (proot/z2root) プロセス**の pid であり、実際に端末を
     * 持つゲストのログインシェルは別 pid・別 pgid になる。そのため `tcgetpgrp` の値を `shellPid` と
     * 比べると**アイドル時でも常に不一致**になり「常に動作中」と誤判定してしまう。
     * そこで「シェルがプロンプトで待機している時の前景 pgid」を実測して基準とし、そこから外れた
     * ときだけ子プロセスが前景に居ると判定する。
     */
    @Volatile private var idlePgid: Int = -1

    init {
        // 生成直後はシェルがプロンプトで待機しているはず。少し待って前景 pgid を1度だけ実測し、
        // アイドル基準として確定する (ここで確定できれば以後の判定が安定する)。
        Thread {
            runCatching { Thread.sleep(1200) }
            if (idlePgid < 0) {
                val fg = runCatching { pty.foregroundPgid() }.getOrDefault(-1)
                if (fg > 0) idlePgid = fg
            }
        }.apply { isDaemon = true; name = "pty-idle-pgid-seed"; start() }
    }

    /**
     * `tcgetpgrp(master_fd)` でいま端末を持っているプロセスグループを取り、**アイドル基準 pgid** と
     * 違えば子プロセスが前景に居ると判定する。基準未確定なら初回の有効値を基準として採用する。
     * `tcgetpgrp` が失敗 (-1) した場合は安全側に倒して `true` (= 子プロセス前景扱い)。これは TUI の
     * スクロールを止めない方を優先するため (リーク側は別の救済が効かないだけで悪化しない)。
     */
    /** `tcgetpgrp` で実際に見ているので、表示にも使える。 */
    override val supportsForegroundChild: Boolean get() = true

    override val hasForegroundChild: Boolean
        get() {
            val fg = pty.foregroundPgid()
            if (fg < 0) return true
            // 基準が未確定なら、この観測時点をアイドルとみなして基準に採る (= 動作中ではない)。
            if (idlePgid < 0) { idlePgid = fg; return false }
            return fg != idlePgid
        }
}
