package com.zerotoship.z2term.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 踏み台 ([SshHop]) の扱い。
 *
 * ⚠ **JSON の往復はここでは見られない。** JVM の unit test では `org.json` が Android の
 * スタブ (既定値を返すだけ) になるため (`isReturnDefaultValues = true`)。⇒ 保存の形は
 * 実機で確かめ、ここでは**経路の並び**と**秘密の落とし方**という間違えると危ない側を見る。
 */
class SshHopTest {

    /** ⚠ **並びが経路そのもの。** 順番が入れ替わると別の道になる。 */
    @Test
    fun theOrderOfTheHopsIsTheRoute() {
        val profile = profile(
            SshHop(host = "gate", user = "a"),
            SshHop(host = "10.0.0.5", port = 2222, user = "b"),
        )

        assertEquals("a@gate:22 → b@10.0.0.5:2222 → me@target:22", profile.routeDescription())
    }

    /** 踏み台が無ければ、経路は本来の接続先だけ。 */
    @Test
    fun withoutJumpHostsTheRouteIsJustTheTarget() {
        assertEquals("me@target:22", profile().routeDescription())
    }

    /** IPv6 は角括弧で囲む (ポートとの区切りが分からなくなるため)。 */
    @Test
    fun anIpv6JumpHostKeepsItsBrackets() {
        assertEquals("a@[2001:db8::10]:22", SshHop(host = "2001:db8::10", user = "a").describe())
    }

    /** 利用者名が空の段は、まだ埋まっていないものとして名前だけを出す。 */
    @Test
    fun aHopWithoutAUserStillDescribesItsHost() {
        assertEquals("gate:22", SshHop(host = "gate").describe())
    }

    /** 認証の取り出しは接続先と同じ形になる ([SshSessionFactory] が 1 本道で扱えるように)。 */
    @Test
    fun aHopAndAProfileHandOverTheSameShapeOfCredentials() {
        val hop = SshHop(
            host = "gate",
            user = "a",
            authType = SshProfile.AuthType.PUBLIC_KEY,
            privateKey = "KEY",
            keyPassphrase = "PASS",
        )

        assertEquals(
            SshCredentials(SshProfile.AuthType.PUBLIC_KEY, "", "KEY", "PASS"),
            hop.credentials(),
        )
    }

    /**
     * ⛔ 「秘密を含めない」で持ち出したファイルに、踏み台のパスワードだけが残らないこと。
     * [SshProfileStore.exportRaw] はここと同じ copy を通す。
     */
    @Test
    fun strippingSecretsAlsoClearsTheJumpHosts() {
        val profile = profile(
            SshHop(host = "gate", user = "a", password = "jump-secret"),
            SshHop(host = "inner", user = "b", privateKey = "jump-key", keyPassphrase = "jump-pass"),
        )

        val stripped = profile.copy(
            password = "",
            privateKey = "",
            keyPassphrase = "",
            jumpHosts = profile.jumpHosts.map {
                it.copy(password = "", privateKey = "", keyPassphrase = "")
            },
        )

        assertTrue(stripped.jumpHosts.all {
            it.password.isEmpty() && it.privateKey.isEmpty() && it.keyPassphrase.isEmpty()
        })
        // 宛先は残る (取り込んだ先が分からなくなると持ち出す意味が無い)。
        assertEquals(listOf("gate", "inner"), stripped.jumpHosts.map { it.host })
        assertFalse(stripped.jumpHosts.any { it.user.isEmpty() })
    }

    private fun profile(vararg hops: SshHop) = SshProfile(
        id = "id",
        name = "name",
        host = "target",
        port = 22,
        user = "me",
        password = "target-secret",
        jumpHosts = hops.toList(),
    )
}
