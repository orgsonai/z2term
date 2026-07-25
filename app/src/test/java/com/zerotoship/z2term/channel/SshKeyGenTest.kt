package com.zerotoship.z2term.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * アプリ内で作る SSH クライアント鍵 ([SshKeyGen]) の検証。
 *
 * 鍵は**作った直後にしか渡す機会が無い**（公開鍵は保存しない）ので、形が崩れていると
 * 「登録したのに繋がらない」という一番切り分けにくい失敗になる。形式だけは固定しておく。
 */
class SshKeyGenTest {

    @Test
    fun generatesOpenSshPrivateKeyAndEd25519PublicLine() {
        val g = SshKeyGen.generate(comment = "z2term-test")
        assertTrue(
            "秘密鍵が OpenSSH 形式で始まっていない:\n${g.privatePem.take(60)}",
            g.privatePem.startsWith("-----BEGIN OPENSSH PRIVATE KEY-----")
        )
        assertTrue(
            "公開鍵が ssh-ed25519 で始まっていない: ${g.publicLine.take(40)}",
            g.publicLine.startsWith("ssh-ed25519 ")
        )
        // 公開鍵は「種別 本体 コメント」の 1 行。改行が混じると authorized_keys が壊れる。
        assertEquals(1, g.publicLine.lines().size)
        assertEquals(3, g.publicLine.split(" ").size)
        assertTrue(g.publicLine.endsWith("z2term-test"))
    }

    @Test
    fun generatedKeyIsLoadableByTheClientWeActuallyUse() {
        // ⚠ これがこのテストの本命。JSch は ed25519 を**読めるが書けない**ので、
        // 生成は BouncyCastle で行っている。両者の形式が食い違うと「作れたのに繋がらない」
        // という一番切り分けにくい失敗になるので、実際に JSch に読ませて確かめる。
        val g = SshKeyGen.generate("z2term-test")
        val kp = com.jcraft.jsch.KeyPair.load(
            com.jcraft.jsch.JSch(), g.privatePem.toByteArray(Charsets.UTF_8), null
        )
        assertEquals(com.jcraft.jsch.KeyPair.ED25519, kp.keyType)
        kp.dispose()
    }

    @Test
    fun everyKeyIsDifferent() {
        // 同じ鍵が出ると、端末を変えても同じ秘密鍵を使い回すことになる。
        assertTrue(SshKeyGen.generate().publicLine != SshKeyGen.generate().publicLine)
    }
}
