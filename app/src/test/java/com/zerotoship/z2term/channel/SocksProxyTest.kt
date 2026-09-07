package com.zerotoship.z2term.channel

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SOCKS5 (RFC 1928) のやりとり ([readGreeting] / [readConnect] / [socksReply])。
 *
 * ⚠ **ここを間違えても「繋がらない」としか見えない。** バイトの読み違いは 1 つずれるだけで
 * 宛先が別物になり、実機では相手側のログにも何も残らない。仕様を数値で固定しておく。
 */
class SocksProxyTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun greetingWithNoAuthIsAccepted() {
        assertTrue(readGreeting(ByteArrayInputStream(bytes(0x05, 0x01, 0x00))))
        // 認証方式を複数名乗ってきても、その中に「認証なし」があればよい。
        assertTrue(readGreeting(ByteArrayInputStream(bytes(0x05, 0x02, 0x02, 0x00))))
    }

    @Test
    fun greetingWithoutNoAuthIsRejected() {
        // ユーザー名/パスワード (0x02) しか名乗らない相手は受けない。
        assertFalse(readGreeting(ByteArrayInputStream(bytes(0x05, 0x01, 0x02))))
    }

    @Test
    fun socks4IsRejected() {
        // ⚠ 4 のつもりで送られたものを 5 として読み進めると、後ろのバイトが丸ごとずれる。
        assertFalse(readGreeting(ByteArrayInputStream(bytes(0x04, 0x01, 0x00, 0x50))))
    }

    @Test
    fun domainNamesAreNotResolvedHere() {
        // 名前は引かずにそのまま渡す (向こう側で解決させる)。
        val name = "example.com"
        val request = bytes(0x05, 0x01, 0x00, 0x03, name.length) +
            name.toByteArray(Charsets.US_ASCII) + bytes(0x01, 0xBB)
        assertEquals(SocksTarget("example.com", 443), readConnect(ByteArrayInputStream(request)))
    }

    @Test
    fun ipv4TargetIsRead() {
        val request = bytes(0x05, 0x01, 0x00, 0x01, 192, 168, 10, 20, 0x00, 0x50)
        assertEquals(SocksTarget("192.168.10.20", 80), readConnect(ByteArrayInputStream(request)))
    }

    @Test
    fun ipv6TargetIsRead() {
        val request = bytes(0x05, 0x01, 0x00, 0x04) +
            ByteArray(16).also { it[15] = 1 } + bytes(0x1F, 0x90)
        assertEquals(SocksTarget("0:0:0:0:0:0:0:1", 8080), readConnect(ByteArrayInputStream(request)))
    }

    @Test
    fun theHighestPortSurvives() {
        // ⚠ 上位バイトを符号付きで扱うと 65535 が -1 になる。
        val request = bytes(0x05, 0x01, 0x00, 0x01, 127, 0, 0, 1, 0xFF, 0xFF)
        assertEquals(65535, readConnect(ByteArrayInputStream(request)).port)
    }

    @Test
    fun bindIsRefusedAfterTheAddressIsConsumed() {
        // BIND (0x02) は受けないが、⚠ **宛先を読み飛ばしてから**断る。読み残すと次の読み手が
        // 要求の途中から読み始めることになる。
        val input = ByteArrayInputStream(bytes(0x05, 0x02, 0x00, 0x01, 127, 0, 0, 1, 0x00, 0x50))
        val failure = runCatching { readConnect(input) }.exceptionOrNull()
        assertTrue("BIND は断る: $failure", failure is SocksFailure)
        assertEquals(REP_CMD_NOT_SUPPORTED, (failure as SocksFailure).code)
        assertEquals("要求を読み切っていない", 0, input.available())
    }

    @Test
    fun unknownAddressTypeIsRefused() {
        val failure = runCatching {
            readConnect(ByteArrayInputStream(bytes(0x05, 0x01, 0x00, 0x09)))
        }.exceptionOrNull()
        assertEquals(REP_ATYP_NOT_SUPPORTED, (failure as SocksFailure).code)
    }

    @Test
    fun replyIsTenBytes() {
        // VER REP RSV ATYP + IPv4 4 バイト + ポート 2 バイト。BND は 0 で返す。
        assertArrayEquals(bytes(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0), socksReply(REP_OK))
        assertArrayEquals(bytes(0x05, 0x04, 0x00, 0x01, 0, 0, 0, 0, 0, 0), socksReply(REP_HOST_UNREACHABLE))
    }
}
