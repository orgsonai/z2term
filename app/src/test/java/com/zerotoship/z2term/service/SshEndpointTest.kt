package com.zerotoship.z2term.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 常駐サーバーのコマンドから、動いている sshd と同じポートを読めるか。 */
class SshEndpointTest {

    private fun port(command: String): Int? =
        SshEndpoint.sshdArgs(command)?.second?.let { SshEndpoint.portFromArgs(it) }

    @Test
    fun readsPortOption() {
        assertEquals(65152, port("sshd --lan -p 65152"))
        assertEquals(65152, port("sshd -p65152 --lan"))
        assertEquals(65152, port("/usr/sbin/sshd -D -p 65152"))
    }

    @Test
    fun readsPortFromDashO() {
        assertEquals(65152, port("sshd -o Port=65152"))
        assertEquals(65152, port("sshd -o \"Port 65152\""))
    }

    @Test
    fun laterOptionWins() {
        assertEquals(3333, port("sshd -p 2222 -p 3333"))
    }

    @Test
    fun noPortOptionFallsBackToConfig() {
        assertNull(port("sshd --lan"))
        assertNull(SshEndpoint.sshdArgs("python3 -m http.server 8080"))
    }

    @Test
    fun readsConfigOption() {
        val args = SshEndpoint.sshdArgs("sshd -f /etc/ssh/my.conf --lan")!!.second
        assertEquals("/etc/ssh/my.conf", SshEndpoint.configFromArgs(args))
    }
}
