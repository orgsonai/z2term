package com.zerotoship.z2term.ui.snippets

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 履歴パレット (B2) の履歴読み取り ([ShellHistory]) の検証。
 *
 * bash と zsh で**ファイル形式が違う**うえ、zsh は拡張形式・複数行継続まである。
 * ここが崩れると「履歴が出ない」「1 コマンドが途中で切れる」という形で効くので、
 * 実ファイルを使わずに済む純ロジックとして押さえる。
 */
class ShellHistoryTest {

    @Test fun bashIsOneCommandPerLine() {
        val got = ShellHistory.parseBash("ls -la\ncd /tmp\n\ngit status\n")
        assertEquals(listOf("ls -la", "cd /tmp", "git status"), got.map { it.command })
        // bash は時刻を持たない。
        assertEquals(listOf(0L, 0L, 0L), got.map { it.at })
    }

    @Test fun zshExtendedFormatIsParsed() {
        val got = ShellHistory.parseZsh(": 1784894575:0;ls -la\n: 1784894600:3;git status\n")
        assertEquals(listOf("ls -la", "git status"), got.map { it.command })
        assertEquals(listOf(1784894575L, 1784894600L), got.map { it.at })
    }

    @Test fun zshPlainLinesAlsoWork() {
        // 拡張形式が無効な設定のときは素の 1 行で書かれる。
        assertEquals(listOf("echo hi"), ShellHistory.parseZsh("echo hi\n").map { it.command })
    }

    @Test fun zshMultilineCommandIsJoined() {
        // 行末 `\` は次の行へ続く。1 コマンドとして拾えないと途中で切れる。
        val got = ShellHistory.parseZsh(": 1784894575:0;for i in 1 2 3; do\\\necho \$i\\\ndone\n")
        assertEquals(1, got.size)
        assertEquals("for i in 1 2 3; do\necho \$i\ndone", got[0].command)
    }

    @Test fun zshCommandContainingSemicolonKeepsEverything() {
        // 本文に `;` が入っていても、区切りは**最初の** `;` だけ。
        val got = ShellHistory.parseZsh(": 1784894575:0;cd /tmp; ls\n")
        assertEquals("cd /tmp; ls", got[0].command)
    }

    @Test fun mergeIsNewestFirstAndDeduplicated() {
        // ファイルは古い→新しいの順。マージ後は新しい順で、重複は 1 つに畳む。
        val bash = ShellHistory.parseBash("old-b\nls -la\n")
        val zsh = ShellHistory.parseZsh(": 10:0;old-z\n: 20:0;ls -la\n")
        val got = ShellHistory.merge(bash, zsh)
        assertEquals(listOf("ls -la", "old-z", "old-b"), got.map { it.command })
        // 時刻を持つ zsh 側が優先される。
        assertEquals(20L, got[0].at)
    }

    @Test fun filterNeedsEveryTerm() {
        val entries = listOf(
            ShellHistory.Entry("git --no-pager log", 0),
            ShellHistory.Entry("git status", 0),
            ShellHistory.Entry("ls -la", 0),
        )
        assertEquals(
            listOf("git --no-pager log"),
            ShellHistory.filter(entries, "git log").map { it.command }
        )
        // 大小文字は無視。
        assertEquals(2, ShellHistory.filter(entries, "GIT").size)
        // 空なら全部。
        assertEquals(3, ShellHistory.filter(entries, "  ").size)
    }
}
