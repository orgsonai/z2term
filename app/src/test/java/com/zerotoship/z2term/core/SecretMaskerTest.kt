package com.zerotoship.z2term.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SecretMasker] の検証。
 *
 * **誤爆しないこと (notMasked) の方が本命**。伏せ損ないは「完全ではない」と明記して受け入れる
 * 設計だが、ふつうのビルド出力やファイル一覧が穴だらけになると、ユーザーはログ機能ごと切る。
 */
class SecretMaskerTest {

    private fun mask(vararg lines: String): List<String> {
        val m = SecretMasker()
        return lines.map { m.maskLine(it) }
    }

    private fun masked(line: String) = mask(line).first()

    /** 伏せ字が入った (= 値が消えた) こと。 */
    private fun assertMasked(line: String, leak: String) {
        val out = masked(line)
        assertTrue("伏せ字が入っていない: $out", out.contains(SecretMasker.MASK))
        assertFalse("値が残っている: $out", out.contains(leak))
    }

    /** 1 文字も変えていないこと。 */
    private fun assertUntouched(line: String) {
        assertEquals(line, masked(line))
    }

    // ---------------------------------------------------------------- 伏せる

    @Test
    fun `名前=値 を伏せる`() {
        assertMasked("export TOKEN=ghp_abcdefghijklmnop", "ghp_abcdefghijklmnop")
        assertMasked("MYSQL_ROOT_PASSWORD=hunter2", "hunter2")
        assertMasked("api_key: 0123456789abcdef", "0123456789abcdef")
        assertMasked("curl --api-key=0123456789abcdef https://x", "0123456789abcdef")
    }

    @Test
    fun `空白区切りの長いフラグを伏せる`() {
        assertMasked("app --password hunter2 --host db", "hunter2")
    }

    @Test
    fun `クォートで括った値は中身ごと伏せる`() {
        assertMasked("""PASSWORD="two words here"""", "two words")
    }

    @Test
    fun `Authorization ヘッダを伏せる`() {
        assertMasked(
            "curl -H 'Authorization: Bearer abcdefghijklmnop' https://x",
            "abcdefghijklmnop"
        )
    }

    @Test
    fun `発行元プレフィックスのトークンは頭だけ残して伏せる`() {
        val out = masked("found ghp_abcdefghijklmnopqrstuvwxyz in file")
        assertTrue(out.contains("ghp_"))          // どのサービスの鍵かは読める
        assertFalse(out.contains("abcdefghijkl")) // 値は復元できない
        assertMasked("aws key AKIAIOSFODNN7EXAMPLE", "IOSFODNN7EXAMPLE")
    }

    @Test
    fun `秘密鍵は本文だけ伏せて BEGIN と END は残す`() {
        val out = mask(
            "-----BEGIN OPENSSH PRIVATE KEY-----",
            "b3BlbnNzaC1rZXktdjEAAAAABG5vbmU",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "-----END OPENSSH PRIVATE KEY-----",
            "next line is normal"
        )
        assertEquals("-----BEGIN OPENSSH PRIVATE KEY-----", out[0])
        assertFalse(out[1].contains("b3Blbn"))
        assertFalse(out[2].contains("AAAAA"))
        assertEquals("-----END OPENSSH PRIVATE KEY-----", out[3])
        // END を過ぎたら元に戻る (ブロックの状態が残り続けない)。
        assertEquals("next line is normal", out[4])
    }

    // ------------------------------------------------------------ 誤爆しない

    @Test
    fun `ふつうのビルド・テスト出力は触らない`() {
        assertUntouched("Passed 12 tests in 3.4s")
        assertUntouched("pass 1 of 3 complete")
        assertUntouched("tar -pxvf archive.tar.gz")
        assertUntouched("BUILD SUCCESSFUL in 7s")
        assertUntouched("total 48")
        assertUntouched("-rw-r--r-- 1 root root 1024 Jul 26 04:00 notes.txt")
    }

    @Test
    fun `値が無い プロンプトだけの行は触らない`() {
        assertUntouched("[sudo] password for u:")
        assertUntouched("Enter passphrase:")
    }

    @Test
    fun `値の後ろにある秘密でない部分は残す`() {
        // 行末まで潰すと後半の意味が消える。潰すのは値 1 つ分だけ。
        val out = masked("TOKEN=abcdefgh && echo done")
        assertTrue(out, out.endsWith("&& echo done"))
        assertFalse(out.contains("abcdefgh"))
    }

    @Test
    fun `長い base64 や 6 桁の数字は秘密扱いしない`() {
        assertUntouched("checksum 9f8e7d6c5b4a39281706f5e4d3c2b1a09f8e7d6c")
        assertUntouched("build 123456 finished")
    }
}
