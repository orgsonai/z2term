package com.zerotoship.z2term.gui.rdp

import java.net.SocketTimeoutException
import javax.net.ssl.SSLSocket
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Test

/** scripts/rdp-testbed.sh probe からだけ実行する、端末内 FreeRDP server との実 wire 検証。 */
class RdpTlsTransportLiveTest {
    @Test
    fun authenticatesAgainstLocalNlaServer() {
        assumeTrue("set Z2TERM_RDP_TESTBED=1 to run", System.getenv("Z2TERM_RDP_TESTBED") == "1")

        RdpTlsTransport.connect(
            host = "127.0.0.1",
            port = 13389,
            timeoutMs = 5_000,
            certificateVerifier = { true },
        ).use { transport ->
            transport.authenticate(
                CredSspNtlm.Credentials(
                    user = "z2test",
                    password = "z2pass",
                ),
            )

            // authInfo 後、成功時は server が MCS Connect-Initial を待つ。拒否なら
            // TSRequest(errorCode) または EOF が返るため、無通信 timeout だけを成功とする。
            transport.sslSocketForTest().soTimeout = 1_000
            assertThrows(SocketTimeoutException::class.java) {
                transport.input.read()
            }
        }
    }

    private fun RdpTlsTransport.sslSocketForTest(): SSLSocket {
        val field = RdpTlsTransport::class.java.getDeclaredField("socket")
        field.isAccessible = true
        return field.get(this) as SSLSocket
    }
}
