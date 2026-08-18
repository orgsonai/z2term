package com.zerotoship.z2term.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * プロンプトを rc へ書き込む処理の回帰テスト。
 *
 * ⚠ 一番守りたいのは **利用者が自分で書いた設定を消さないこと**。rc を丸ごと書き換える作りに
 * すると、`alias` や `export` が黙って消えて「何かおかしい」だけが残る。目印で囲んだ部分しか
 * 触らないことと、**2 回目の適用が重複せず差し替わる**ことを固定する。
 */
class ShellPromptTest {

    private fun tmpRootfs(): File = Files.createTempDirectory("z2rootfs").toFile()

    private fun rc(root: File, shell: ShellPrompt.Shell) = File(root, shell.rcPath)

    @Test
    fun writesIntoAnEmptyRc() {
        val root = tmpRootfs()
        try {
            val body = ShellPrompt.body(ShellPrompt.Preset.USER_HOST, ShellPrompt.Shell.BASH)
            assertNotNull(ShellPrompt.apply(root, ShellPrompt.Shell.BASH, body))
            val text = rc(root, ShellPrompt.Shell.BASH).readText()
            assertTrue("目印が無い: $text", text.contains(ShellPrompt.MARKER_BEGIN))
            assertTrue("閉じる目印が無い: $text", text.contains(ShellPrompt.MARKER_END))
            assertTrue("PS1 が無い: $text", text.contains("PS1="))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun keepsWhatTheUserWroteThemselves() {
        val root = tmpRootfs()
        try {
            val mine = "alias ll='ls -la'\nexport EDITOR=vi\n"
            rc(root, ShellPrompt.Shell.ZSH).apply { parentFile?.mkdirs(); writeText(mine) }
            ShellPrompt.apply(
                root, ShellPrompt.Shell.ZSH,
                ShellPrompt.body(ShellPrompt.Preset.TWO_LINE, ShellPrompt.Shell.ZSH)
            )
            val text = rc(root, ShellPrompt.Shell.ZSH).readText()
            assertTrue("自分で書いた alias が消えた: $text", text.contains("alias ll='ls -la'"))
            assertTrue("自分で書いた export が消えた: $text", text.contains("export EDITOR=vi"))
            assertTrue("プロンプトが書かれていない: $text", text.contains("PROMPT="))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun applyingTwiceReplacesInsteadOfPilingUp() {
        val root = tmpRootfs()
        try {
            val shell = ShellPrompt.Shell.BASH
            ShellPrompt.apply(root, shell, ShellPrompt.body(ShellPrompt.Preset.USER_HOST, shell))
            ShellPrompt.apply(root, shell, ShellPrompt.body(ShellPrompt.Preset.PATH_ONLY, shell))
            val text = rc(root, shell).readText()
            assertEquals(
                "目印が増えている (差し替えでなく追記されている): $text",
                1, Regex(Regex.escape(ShellPrompt.MARKER_BEGIN)).findAll(text).count()
            )
            // 2 回目に選んだ方だけが残る。
            assertEquals(
                ShellPrompt.body(ShellPrompt.Preset.PATH_ONLY, shell),
                ShellPrompt.current(root, shell)
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun clearRemovesOnlyOurBlock() {
        val root = tmpRootfs()
        try {
            val shell = ShellPrompt.Shell.SH
            rc(root, shell).apply { parentFile?.mkdirs(); writeText("export TZ=Asia/Tokyo\n") }
            ShellPrompt.apply(root, shell, ShellPrompt.body(ShellPrompt.Preset.WITH_TIME, shell))
            assertTrue(ShellPrompt.clear(root, shell))
            val text = rc(root, shell).readText()
            assertTrue("自分で書いた export が消えた: $text", text.contains("export TZ=Asia/Tokyo"))
            assertFalse("目印が残っている: $text", text.contains(ShellPrompt.MARKER_BEGIN))
            assertNull("消したのに中身が読める", ShellPrompt.current(root, shell))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun clearOnAnUntouchedRcIsANoOp() {
        val root = tmpRootfs()
        try {
            assertFalse("書いていないのに消せたと言っている", ShellPrompt.clear(root, ShellPrompt.Shell.BASH))
        } finally {
            root.deleteRecursively()
        }
    }

    /**
     * ⚠ シェルごとの書き方を取り違えないこと。bash の `\[ \]` を zsh に出すとそのまま画面に出るし、
     * sh に出すと**行の折り返しが崩れる**。取り違えは動くように見えて見た目だけ壊れるので、
     * ここで固定しておく。
     */
    @Test
    fun eachShellUsesItsOwnSyntax() {
        for (preset in ShellPrompt.Preset.entries) {
            val bash = ShellPrompt.body(preset, ShellPrompt.Shell.BASH)
            val zsh = ShellPrompt.body(preset, ShellPrompt.Shell.ZSH)
            val sh = ShellPrompt.body(preset, ShellPrompt.Shell.SH)
            assertTrue("$preset: bash が PS1 を出していない", bash.contains("PS1="))
            assertTrue("$preset: zsh が PROMPT を出していない", zsh.contains("PROMPT="))
            assertTrue("$preset: sh が PS1 を出していない", sh.contains("PS1="))
            assertFalse("$preset: zsh に bash 専用の \\[ \\] が混ざっている", zsh.contains("\\["))
            assertFalse("$preset: sh に bash 専用の \\[ \\] が混ざっている", sh.contains("\\["))
            assertFalse("$preset: bash に zsh 専用の %F{} が混ざっている", bash.contains("%F{"))
            assertFalse("$preset: sh に zsh 専用の %F{} が混ざっている", sh.contains("%F{"))
        }
    }

    /**
     * 右端の時刻。⚠ **端末の幅を数えないこと**を固定する。
     *
     * `COLUMNS` から引き算する作りは、sh では変数が無いことがあり、あっても画面を回したときに
     * 更新されないので**必ずズレる**。右端まで動いてから戻る方式なら幅を知らなくてよい。
     */
    @Test
    fun theRightEdgeClockDoesNotCountColumns() {
        for (shell in ShellPrompt.Shell.entries) {
            for (preset in ShellPrompt.Preset.entries) {
                val on = ShellPrompt.body(preset, shell, rightClock = true)
                assertFalse("$shell/$preset: COLUMNS を数えている: $on", on.contains("COLUMNS"))
                if (shell == ShellPrompt.Shell.ZSH) {
                    // zsh は幅の管理を RPROMPT に任せるのが正道。
                    assertTrue("$shell/$preset: RPROMPT を使っていない: $on", on.contains("RPROMPT="))
                } else {
                    assertTrue("$shell/$preset: 右端へ寄せていない: $on", on.contains("[999C"))
                }
                // OFF のときは何も足さない。
                val off = ShellPrompt.body(preset, shell, rightClock = false)
                assertFalse("$shell/$preset: OFF なのに時刻が入る: $off", off.contains("[999C"))
                assertFalse("$shell/$preset: OFF なのに RPROMPT が入る: $off", off.contains("RPROMPT="))
            }
        }
    }

    /** 右端の時刻も、bash では幅を持たない印の中に入れる (囲み忘れると折り返しがずれる)。 */
    @Test
    fun theRightEdgeClockIsZeroWidthInBash() {
        for (preset in ShellPrompt.Preset.entries) {
            val on = ShellPrompt.body(preset, ShellPrompt.Shell.BASH, rightClock = true)
            assertTrue("$preset: 時刻が \\[ \\] で囲まれていない: $on", on.contains("\\[\\e[s"))
            assertTrue("$preset: 元の位置へ帰っていない: $on", on.contains("\\e[u\\]"))
        }
    }

    /** 色を使うサンプルは、bash では必ず幅を持たない印で囲む (囲み忘れると折り返しがずれる)。 */
    @Test
    fun bashWrapsColorsSoLineWrappingStaysCorrect() {
        for (preset in ShellPrompt.Preset.entries) {
            val bash = ShellPrompt.body(preset, ShellPrompt.Shell.BASH)
            if (!bash.contains("\\e[")) continue
            assertTrue("$preset: 色が \\[ \\] で囲まれていない: $bash", bash.contains("\\[\\e["))
        }
    }
}
