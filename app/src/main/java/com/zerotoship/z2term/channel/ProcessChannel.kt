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

    /**
     * 前景に子プロセス (= シェル以外) が居るか。
     *
     * ローカル PTY なら `tcgetpgrp(master_fd) != shellPid` で判定する。リモート SSH や
     * 判定機構を持たない実装は **常に true** を返す ("子プロセスが居ると仮定" のほうが
     * マウス wheel リーク回避より TUI スクロール維持の方が確実なため。SSH 側のリーク
     * 問題は別途別レイヤで対応する)。
     *
     * 用途: 行儀の悪い TUI が exit 時にマウスレポートを切り忘れて `mouseEnabled` が
     * stale で残ったとき、シェル前景なら primary 画面のスワイプを PTY wheel ではなく
     * scrollback に倒すための判定。
     */
    val hasForegroundChild: Boolean get() = true
}
