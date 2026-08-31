package com.zerotoship.z2term.channel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteServiceProfileTest {
    @Test
    fun automaticLocalPortIsTheDefault() {
        val service = RemoteService(
            id = "svc-1",
            protocol = RemoteServiceProtocol.WEBDAV,
        )

        assertEquals(0, service.localPort)
        assertEquals(443, service.remotePort)
        assertTrue(service.useSshTunnel)
    }

    @Test
    fun everyServiceHasTheExpectedDefaultPort() {
        assertEquals(21, RemoteServiceProtocol.FTP.defaultPort)
        assertEquals(445, RemoteServiceProtocol.SMB.defaultPort)
        assertEquals(443, RemoteServiceProtocol.WEBDAV.defaultPort)
        assertEquals(5901, RemoteServiceProtocol.VNC.defaultPort)
        assertEquals(3389, RemoteServiceProtocol.RDP.defaultPort)
    }

    @Test
    fun onlyTheScreenProtocolsOpenAGuiTab() {
        // 呼び出し側はプロトコル名を並べず、この印だけで GUI タブ / ファイル画面を分ける。
        assertTrue(RemoteServiceProtocol.VNC.opensDesktopTab)
        assertTrue(RemoteServiceProtocol.RDP.opensDesktopTab)
        assertFalse(RemoteServiceProtocol.FTP.opensDesktopTab)
        assertFalse(RemoteServiceProtocol.SMB.opensDesktopTab)
        assertFalse(RemoteServiceProtocol.WEBDAV.opensDesktopTab)
    }

    @Test
    fun rdpDefaultsToTheTunneledStandardPort() {
        // 追加した直後のまま繋げること。既定は SSH 転送あり + 3389。
        val service = RemoteService(id = "rdp-1", protocol = RemoteServiceProtocol.RDP)

        assertTrue(service.useSshTunnel)
        assertEquals(3389, service.remotePort)
        assertEquals(0, service.localPort)
        assertEquals("localhost:3389", service.endpointDescription())
    }

    @Test
    fun directModeIsExplicitOptOut() {
        val tunneled = RemoteService(id = "vnc", protocol = RemoteServiceProtocol.VNC)
        val direct = tunneled.copy(useSshTunnel = false)

        assertTrue(tunneled.useSshTunnel)
        assertFalse(direct.useSshTunnel)
    }

    @Test
    fun directModeUsesTheParentSshHost() {
        val profile = SshProfile(
            id = "ssh-1",
            name = "server",
            host = "192.168.10.12",
            user = "user",
        )
        val service = RemoteService(
            id = "smb",
            protocol = RemoteServiceProtocol.SMB,
            useSshTunnel = false,
            host = "localhost",
        )

        assertEquals("192.168.10.12", service.connectionHost(profile))
    }

    @Test
    fun tunneledModeUsesTheServiceHost() {
        val profile = SshProfile(
            id = "ssh-1",
            name = "server",
            host = "192.168.10.12",
            user = "user",
        )
        val service = RemoteService(
            id = "smb",
            protocol = RemoteServiceProtocol.SMB,
            host = "nas.internal",
        )

        assertEquals("nas.internal", service.connectionHost(profile))
    }

    @Test
    fun ipv6EndpointsAreBracketedAndBracketedInputIsNormalized() {
        val profile = SshProfile(
            id = "ssh-v6",
            name = "server-v6",
            host = "[2001:db8::10]",
            port = 2222,
            user = "user",
        )
        val service = RemoteService(
            id = "webdav-v6",
            protocol = RemoteServiceProtocol.WEBDAV,
            host = "[fd00::20]",
            remotePort = 8443,
        )

        assertEquals("user@[2001:db8::10]:2222", profile.endpointDescription())
        assertEquals("2001:db8::10", profile.toVncTarget().host)
        assertEquals("fd00::20", service.connectionHost(profile))
        assertEquals("[fd00::20]:8443", service.endpointDescription())
    }

    @Test
    fun browserBackMovesToTheParentAndStopsAtRoot() {
        assertEquals("/share/folder", RemotePath.resolve("/share/folder/deep", ".."))
        assertEquals("/", RemotePath.resolve("/share", ".."))
        assertEquals("/", RemotePath.resolve("/", ".."))
    }

    @Test
    fun smbPathUsesBackslashesWithoutEncodingNames() {
        assertEquals("写真\\2026 年\\sample.png", SmbClient.relativePath("/写真/2026 年/sample.png"))
        assertEquals("", SmbClient.relativePath("/"))
    }
}
