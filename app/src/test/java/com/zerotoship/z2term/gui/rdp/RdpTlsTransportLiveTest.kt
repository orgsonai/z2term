package com.zerotoship.z2term.gui.rdp

import com.zerotoship.z2term.gui.GuiKeyMapper
import com.zerotoship.z2term.gui.RemoteDesktopClient
import javax.net.ssl.SSLSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** scripts/rdp-testbed.sh probe からだけ実行する、端末内 FreeRDP server との実 wire 検証。 */
class RdpTlsTransportLiveTest {
    @Test
    fun authenticatesAndConnectsMcsAgainstLocalNlaServer() {
        assumeTrue("set Z2TERM_RDP_TESTBED=1 to run", System.getenv("Z2TERM_RDP_TESTBED") == "1")

        RdpTlsTransport.connect(
            host = "127.0.0.1",
            port = 13389,
            timeoutMs = 5_000,
            certificateVerifier = { true },
        ).use { transport ->
            val credentials = CredSspNtlm.Credentials(user = "z2test", password = "z2pass")
            transport.authenticate(credentials)
            val session = transport.connectMcs()
            assertTrue(session.userChannelId >= 1001)
            assertEquals(1003, session.ioChannelId)
            val active = transport.activate(session, credentials)
            assertTrue(active.serverCapabilities.contains(RdpActivation.CAP_BITMAP))
            assertTrue(active.clientCapabilities.contains(RdpActivation.CAP_BITMAP))
            assertTrue(active.clientCapabilities.contains(RdpActivation.CAP_ORDER))
            assertTrue(active.clientCapabilities.none {
                it == RdpActivation.CAP_SURFACE_COMMANDS || it == RdpActivation.CAP_BITMAP_CODECS
            })

            transport.sslSocketForTest().soTimeout = 5_000
            transport.finalizeConnection(session, active)

            // Xvnc の root window へ実際に注入される。probe の外で xev を併用すれば
            // pointer 321,222 と Return の Press/Release まで wire 結合確認できる。
            val rdpInput = RdpInput()
            val events =
                rdpInput.pointerEvents(RemoteDesktopClient.BUTTON_LEFT, 321, 222) +
                    rdpInput.pointerEvents(0, 321, 222) +
                    rdpInput.keyEvents(GuiKeyMapper.XK_Return, down = true) +
                    rdpInput.keyEvents(GuiKeyMapper.XK_Return, down = false)
            transport.sendInputEvents(
                session,
                active,
                events,
            )
            Thread.sleep(100)
        }
    }

    private fun RdpTlsTransport.sslSocketForTest(): SSLSocket {
        val field = RdpTlsTransport::class.java.getDeclaredField("socket")
        field.isAccessible = true
        return field.get(this) as SSLSocket
    }
}
