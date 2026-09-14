package com.zerotoship.z2term.share

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.zerotoship.z2term.channel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.InetAddress
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

/** Explicit local fixture only: adb reverse connects to a temporary SSH/HTTPS server on the PC. */
@RunWith(AndroidJUnit4::class)
class ShareRelayLoopbackTest {
    @Test fun strictKnownHostsForwardingBrowserDownloadAndStop() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixtureName = InstrumentationRegistry.getArguments().getString("relayFixture").orEmpty()
        assumeTrue("Needs a temporary loopback SSH/HTTPS fixture", fixtureName.matches(Regex("relay-fixture-[a-f0-9-]+.json")))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixtureFile = File(context.getExternalFilesDir(null), fixtureName)
        val fixture = JSONObject(fixtureFile.readText())
        val profile = SshProfile(id = UUID.randomUUID().toString(), name = "Temporary relay test", host = "127.0.0.1",
            port = fixture.getInt("sshPort"), user = fixture.getString("user"),
            authType = SshProfile.AuthType.PUBLIC_KEY, privateKey = fixture.getString("privateKey"),
            // A relay must never execute the saved initial command or apply unrelated forwards.
            initCommand = "exit 99", forwards = listOf(PortForward(localPort = 1, remoteHost = "127.0.0.1", remotePort = 1)))
        val config = ShareRelayConfig.parse(profile.id, "https://localhost:" + fixture.getInt("httpsPort"),
            fixture.getInt("remotePort"), 5)
        val host = "[127.0.0.1]:${profile.port}"
        val keyParts = fixture.getString("hostKey").trim().split(' ')
        val keyBytes = Base64.decode(keyParts[1], Base64.NO_WRAP)
        val repository = KnownHostsHolder.repository(context).also { it.awaitLoaded() }
        assertEquals("Use a fresh temporary SSH port", HostKeyRepository.NOT_INCLUDED, repository.check(host, keyBytes))
        val store = KnownHostsHolder.store(context)
        val ca = CertificateFactory.getInstance("X.509").generateCertificate(fixture.getString("certificate").byteInputStream())
        val keys = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setCertificateEntry("fixture", ca) }
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(keys) }
            .trustManagers.filterIsInstance<X509TrustManager>().single()
        val tls = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
        // Trust only this fixture certificate; production uses the default trusted HTTPS client.
        fun client() = OkHttpClient.Builder().sslSocketFactory(tls.socketFactory, trust)
            .followRedirects(false).followSslRedirects(false).callTimeout(8, TimeUnit.SECONDS).build()
        val runId = UUID.randomUUID().toString()
        val file = File(context.cacheDir, "relay-$runId.bin")
        val completion = File(context.getExternalFilesDir(null), "relay-test-$runId.done")
        val payload = ByteArray(1024 * 1024) { (it % 251).toByte() }
        val hash = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        file.writeBytes(payload)
        fun server() = DirectFileServer(file, "payload.bin", null, 0, false, 300_000,
            bindAddress = InetAddress.getByName("127.0.0.1"))
        try {
            server().use { local ->
                ShareRelay(context, local, profile, config, client()).use { unknown ->
                    assertTrue("Unknown SSH key must fail without a dialog", runCatching { unknown.connect() }.isFailure)
                }
            }
            repository.add(HostKey(host, keyBytes), null)
            // This catches accidentally double-encoding JSch's already Base64 host key.
            withTimeout(5000) { store.entries.first { entries -> entries.any { it.host == host && it.keyBase64 == keyParts[1] } } }
            assertEquals(HostKeyRepository.OK, repository.check(host, keyBytes))
            server().use { local ->
                ShareRelay(context, local, profile, config, client()).use { relay ->
                    val url = withTimeout(120_000) { relay.connect() }
                    assertEquals(config.origin + local.path, url)
                    // A second session must fail rather than steal this relay's occupied port.
                    server().use { another ->
                        ShareRelay(context, another, profile, config, client()).use { conflict ->
                            assertTrue("An occupied remote port must fail", runCatching { conflict.connect() }.isFailure)
                        }
                    }
                    instrumentation.sendStatus(0, Bundle().apply {
                        putString("relay_test_url", url)
                        putString("relay_test_kind", "file")
                        putString("relay_test_sha256", hash)
                        putString("relay_test_completion", completion.absolutePath)
                    })
                    withTimeout(120_000) { while (!completion.exists()) delay(100) }
                    assertEquals(hash, completion.readText().trim())
                    relay.close()
                    instrumentation.sendStatus(0, Bundle().apply { putString("relay_test_stopped", url) })
                }
            }
            server().use { local ->
                ShareRelay(context, local, profile, config, client()).use { cancelled ->
                    cancelled.close()
                    assertTrue(runCatching { cancelled.connect() }.isFailure)
                }
            }
        } finally {
            repository.remove(host, keyParts[0], keyBytes)
            withTimeout(5000) { store.entries.first { entries -> entries.none { it.host == host } } }
            file.delete()
            completion.delete()
            fixtureFile.delete()
        }
    }
}
