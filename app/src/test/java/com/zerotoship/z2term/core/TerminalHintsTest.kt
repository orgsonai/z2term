package com.zerotoship.z2term.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * つまずきの言い換え ([TerminalHints.detect]) の検証。
 *
 * この機能は**誤爆すると一気にうっとうしい**（自分でスクリプトを書いていて `ping` の文字が
 * 出ただけで反応する、など）。当たるべきものと、**当たってはいけないもの**の両方を固定する。
 */
class TerminalHintsTest {

    @Test
    fun ping_isDetected() {
        assertEquals(
            TerminalHints.Hint.PING,
            TerminalHints.detect("ping: socket: Operation not permitted")
        )
    }

    @Test
    fun sshdAbsolutePath_isDetected() {
        // 「コマンドが無い」の案内は 0.8.304 で撤去したが、その判定 (looksMissing) は
        // ここが使い続ける。消すと sshd の直叩きに気付けなくなる。
        assertEquals(
            TerminalHints.Hint.SSHD_PATH,
            TerminalHints.detect("sh: /usr/sbin/sshd: not found")
        )
    }

    @Test
    fun lowPort_isDetected() {
        assertEquals(
            TerminalHints.Hint.LOW_PORT,
            TerminalHints.detect("bind: Permission denied")
        )
    }

    @Test
    fun sdcard_isDetected() {
        assertEquals(
            TerminalHints.Hint.STORAGE,
            TerminalHints.detect("ls: /sdcard: Permission denied")
        )
    }

    @Test
    fun commandNotFound_neverFires() {
        // 0.8.304 で撤去。`command not found` は端末で最も普通に起きる出来事で、
        // 案内も 3 ディストロを並べるしかなく役に立たなかった。うっかり復活させたらここで落ちる。
        assertNull(TerminalHints.detect("bash: git: command not found"))
        assertNull(TerminalHints.detect("sh: vim: not found"))
    }

    @Test
    fun ordinaryOutputNeverFires() {
        // ここが本番。普通の出力で出てしまうと、この機能は消される。
        assertNull(TerminalHints.detect("PING 8.8.8.8 (8.8.8.8): 56 data bytes"))
        assertNull(TerminalHints.detect("total 24\ndrwxr-xr-x  5 root root 4096 Jul 25 21:00 ."))
        assertNull(TerminalHints.detect("Successfully installed 12 packages"))
        assertNull(TerminalHints.detect(""))
    }

    @Test
    fun writingAboutPingDoesNotFire() {
        // 自分でスクリプトを書いていて `ping` の語が画面に出ただけでは反応しない。
        assertNull(TerminalHints.detect("# ping is not available on Android"))
        assertNull(TerminalHints.detect("echo \"use ping instead\""))
    }
}
