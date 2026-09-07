package com.zerotoship.z2term.channel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ポート転送の種類 ([ForwardKind]) の読み替えと、一覧に出す 1 行 ([PortForward.describe])。
 *
 * ⚠ **一番怖いのは、保存済みの設定が別の意味で読まれること。** `-R` が `-L` として張られると
 * 向きが黙って逆になり、繋がらない理由が画面のどこにも出ない (0.8.288 で 1 度やっている)。
 */
class PortForwardTest {

    @Test
    fun theNewFieldWins() {
        assertEquals(ForwardKind.SOCKS, PortForward.kindOf("SOCKS", reverse = false))
        assertEquals(ForwardKind.LOCAL, PortForward.kindOf("LOCAL", reverse = true))
    }

    @Test
    fun settingsWrittenBeforeTheFieldExistedStillRead() {
        // 0.8.523 までに保存されたものには kind が無い。そこでは reverse が正本。
        assertEquals(ForwardKind.REMOTE, PortForward.kindOf(null, reverse = true))
        assertEquals(ForwardKind.LOCAL, PortForward.kindOf(null, reverse = false))
        assertEquals(ForwardKind.REMOTE, PortForward.kindOf("", reverse = true))
    }

    @Test
    fun anUnreadableNameFallsBackToTheDirection() {
        // ⚠ 読めない名前を -L 扱いにすると、-R の設定が逆向きに張られる。
        assertEquals(ForwardKind.REMOTE, PortForward.kindOf("DYNAMIC", reverse = true))
    }

    @Test
    fun describeShowsTheDirection() {
        val forward = PortForward(
            bindAddress = "127.0.0.1", localPort = 8080, remoteHost = "localhost", remotePort = 80
        )
        assertEquals("-L 127.0.0.1:8080 → localhost:80", forward.describe())
        assertEquals(
            "-R 127.0.0.1:80 → localhost:8080",
            forward.copy(kind = ForwardKind.REMOTE).describe()
        )
    }

    @Test
    fun socksHasNoDestination() {
        // -D は宛先を持たない (使う側が 1 接続ごとに決める) ので、「→」を出さない。
        val socks = PortForward(
            bindAddress = "127.0.0.1", localPort = 1080, remoteHost = "", remotePort = 0,
            kind = ForwardKind.SOCKS
        )
        assertEquals("-D 127.0.0.1:1080", socks.describe())
    }
}
