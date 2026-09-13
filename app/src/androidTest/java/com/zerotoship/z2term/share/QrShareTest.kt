package com.zerotoship.z2term.share

import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class QrShareTest {
    @Test fun cliUsesTheActualDistroHomeOverlayAndRejectsUnrelatedPrivatePaths() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val unique = "qr-test-${UUID.randomUUID()}.txt"
        val home = File(context.filesDir, "shared_home").apply { mkdirs() }
        val shared = File(home, ".config/$unique").apply { parentFile!!.mkdirs(); writeText("shared") }
        val overlay = File(context.filesDir, "home_overlay/alpine/.config/$unique").apply { parentFile!!.mkdirs(); writeText("overlay") }
        try {
            assertEquals(overlay.canonicalFile, QrShareManager.cliFile(context, "/root/.config/$unique", "/root", "alpine"))
            assertEquals(shared.canonicalFile, QrShareManager.cliFile(context, shared.absolutePath, home.absolutePath, ""))
            assertTrue(runCatching { QrShareManager.cliFile(context, "/root/../secret", "/root", "alpine") }.isFailure)
            assertTrue(runCatching { QrShareManager.cliFile(context, "/root/.config/$unique", "/root", "../outside") }.isFailure)
            assertTrue(runCatching { QrShareManager.cliFile(context, File(context.filesDir, "secret").path, home.path, "") }.isFailure)
        } finally { shared.delete(); overlay.delete() }
    }

    /** Opt in explicitly: publishes generated test bytes through a real external relay. */
    @Test fun bundledAndroidTunnelTransfersAnImmutableSnapshotAndStops() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("qrPublicNetwork") == "true")
        assumeTrue(!QrShareManager.state.value.active)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent(context, QrShareActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val file = File(File(context.filesDir, "shared_home").apply { mkdirs() }, "QR test 日本語 '${UUID.randomUUID()}.bin")
        val bytes = ByteArray(1024 * 1024) { (it % 251).toByte() }
        file.writeBytes(bytes)
        val client = OkHttpClient.Builder().dns(QrTunnelDns(context)).callTimeout(30, TimeUnit.SECONDS).build()
        fun publicRequest(request: Request): okhttp3.Response {
            // A new public DNS record can reach resolvers at different times. Keep actual HTTP/assertion failures fatal.
            val deadline = android.os.SystemClock.elapsedRealtime() + 30_000
            var retries = 0
            while (true) {
                try {
                    return client.newCall(request).execute().also {
                        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "Public request DNS/connection retries=$retries\n") })
                    }
                } catch (e: IOException) {
                    if (android.os.SystemClock.elapsedRealtime() >= deadline) throw e
                    retries++
                    Thread.sleep(1000)
                }
            }
        }
        try {
            QrShareCommands.command(context, listOf("start", file.path, file.parent!!, ""))
            val deadline = android.os.SystemClock.elapsedRealtime() + 150_000
            while (QrShareManager.state.value.phase != QrShareManager.Phase.READY) {
                val state = QrShareManager.state.value
                assertTrue(state.error, state.active)
                assertTrue("Public connection timed out", android.os.SystemClock.elapsedRealtime() < deadline)
                Thread.sleep(250)
            }
            val state = QrShareManager.state.value
            val url = state.url
            assertTrue(url.startsWith("https://"))
            assertTrue(state.expires > System.currentTimeMillis())
            assertTrue(runCatching { QrShareCommands.command(context, listOf("start", file.path, file.parent!!, "")) }.isFailure)
            file.writeText("Changed after preparing the snapshot")
            publicRequest(Request.Builder().url(url + "file").build()).use {
                assertEquals(200, it.code)
                assertEquals("no-store, private, max-age=0", it.header("Cache-Control"))
                assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(bytes),
                    MessageDigest.getInstance("SHA-256").digest(it.body.bytes()))
            }
            publicRequest(Request.Builder().url(url + "file").header("Range", "bytes=100-199").build()).use {
                assertEquals(206, it.code)
                assertArrayEquals(bytes.copyOfRange(100, 200), it.body.bytes())
            }
            instrumentation.sendStatus(0, Bundle().apply { putString("stream", "QR_TEST_READY=$url\n") })
            Thread.sleep(15_000) // Allows a second receiver to check the same synthetic file.
            QrShareCommands.command(context, listOf("stop"))
            val stopDeadline = android.os.SystemClock.elapsedRealtime() + 10_000
            while (QrShareManager.state.value.active && android.os.SystemClock.elapsedRealtime() < stopDeadline) Thread.sleep(100)
            assertFalse(QrShareManager.state.value.active)
            assertTrue(QrShareManager.state.value.url.isEmpty())
            assertFalse(File(context.cacheDir, "qr-share/${state.id}").exists())
            val status = runCatching {
                client.newCall(Request.Builder().url(url + "file").build()).execute().use { it.code }
            }.getOrDefault(599)
            assertTrue("Stopped download returned $status", status >= 400)
        } finally {
            QrShareManager.stop(context)
            file.delete()
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
