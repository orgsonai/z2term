package com.zerotoship.z2term.gui.rfb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * VNC 認証 (RFB security type 2) の**手で確かめられない部分**を固定する。
 *
 * 期待値は z2term の実装とは無関係に `openssl enc -des-ecb` で作った
 * ({@code printf '000102…0f' | xxd -r -p | openssl enc -provider legacy -des-ecb -K <鍵> -nopad})。
 * 実装を書き換えても、この値が動かなければ「相手のサーバから見て同じ答え」であり続ける。
 */
class VncAuthTest {

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    private fun unhex(s: String) = ByteArray(s.length / 2) {
        s.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }

    @Test
    fun `des key reverses the bits of each byte`() {
        // "z2term!" = 7a 32 74 65 72 6d 21 → 8 バイトに 0 詰め → 各バイトのビット順を反転。
        assertEquals("5e4c2ea64eb68400", hex(VncAuth.desKey("z2term!")))
    }

    @Test
    fun `des key pads short passwords with zero`() {
        // 空パスワードでも鍵は 8 バイト (全 0)。長さで落ちないことの確認。
        assertEquals("0000000000000000", hex(VncAuth.desKey("")))
        assertEquals(VncAuth.KEY_BYTES, VncAuth.desKey("a").size)
    }

    @Test
    fun `des key ignores the ninth character onwards`() {
        // RFB のパスワードは 8 文字まで。9 文字目以降を無視するのは仕様どおり。
        assertArrayEquals(VncAuth.desKey("12345678"), VncAuth.desKey("123456789"))
    }

    @Test
    fun `challenge response matches openssl`() {
        val challenge = unhex("000102030405060708090a0b0c0d0e0f")
        assertEquals(
            "a492fca0494fc4fd0674e50ee34068d3",
            hex(VncAuth.challengeResponse("z2term!", challenge))
        )
    }

    @Test
    fun `challenge must be 16 bytes`() {
        assertThrows(IllegalArgumentException::class.java) {
            VncAuth.challengeResponse("z2term!", ByteArray(8))
        }
    }

    @Test
    fun `none wins when the server offers it`() {
        // ローカルの Xvnc は None のみ。パスワードを持っていても送らない。
        assertEquals(VncAuth.SEC_NONE, VncAuth.pickSecurityType(listOf(1), hasPassword = false))
        assertEquals(VncAuth.SEC_NONE, VncAuth.pickSecurityType(listOf(1, 2), hasPassword = true))
    }

    @Test
    fun `vnc auth is used when a password is available`() {
        assertEquals(VncAuth.SEC_VNC_AUTH, VncAuth.pickSecurityType(listOf(2), hasPassword = true))
    }

    @Test
    fun `missing password is reported as its own type`() {
        // UI が「パスワードを入れて」と案内できるよう、ただの IOException にしない。
        assertThrows(RfbPasswordRequiredException::class.java) {
            VncAuth.pickSecurityType(listOf(2), hasPassword = false)
        }
    }

    @Test
    fun `vencrypt and unknown types are unsupported`() {
        assertThrows(RfbSecurityUnsupportedException::class.java) {
            VncAuth.pickSecurityType(listOf(19), hasPassword = true)
        }
        assertThrows(RfbSecurityUnsupportedException::class.java) {
            VncAuth.pickSecurityType(listOf(16, 30), hasPassword = true)
        }
        assertThrows(RfbSecurityUnsupportedException::class.java) {
            VncAuth.pickSecurityType(emptyList(), hasPassword = true)
        }
    }
}
