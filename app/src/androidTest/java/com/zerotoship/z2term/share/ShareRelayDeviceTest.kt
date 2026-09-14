package com.zerotoship.z2term.share

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Opt-in integration test. Publishes generated fixtures only; a separate PC receives them. */
@RunWith(AndroidJUnit4::class)
class ShareRelayDeviceTest {
    @Test fun selfHostedFolderAndSingleFileReachAReceiverAndStopCleanly() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("Needs an explicit self-hosted relay configuration and receiver",
            InstrumentationRegistry.getArguments().getString("relaySelfHosted") == "true")
        val args = InstrumentationRegistry.getArguments()
        val config = ShareRelayConfig.parse(args.getString("relayProfile").orEmpty(),
            args.getString("relayOrigin").orEmpty(), args.getString("relayPort")!!.toInt(), 5)
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue("Do not interrupt an existing share", !DirectShareManager.state.value.active)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        assertTrue("Keep the sender on cellular for this regression test",
            connectivity.getNetworkCapabilities(connectivity.activeNetwork)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true)
        val runId = UUID.randomUUID().toString()
        val fixture = File(context.filesDir, "shared_home/relay-test-$runId").apply { mkdirs() }
        val completion = File(context.getExternalFilesDir(null), "relay-test-$runId.done")
        val payload = ByteArray(1024 * 1024) { (it % 251).toByte() }
        val hash = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
        val nested = File(fixture, "nested").apply { mkdir() }
        val file = File(nested, "payload.bin").apply { writeBytes(payload) }
        File(fixture, "empty").mkdir()
        File(fixture, "readme.txt").writeText("Synthetic relay integration fixture\n")
        val activity = instrumentation.startActivitySync(Intent(context, DirectShareActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            for (folder in listOf(true, false)) {
                completion.delete()
                val uri = if (folder) DocumentsContract.buildTreeDocumentUri(context.packageName + ".documents", fixture.absolutePath)
                    else DocumentsContract.buildDocumentUri(context.packageName + ".documents", file.absolutePath)
                instrumentation.runOnMainSync { DirectShareManager.start(context, uri, config, folder) }
                await(130_000) { DirectShareManager.state.value.let { it.url.isNotEmpty() || !it.active } }
                val ready = DirectShareManager.state.value
                assertTrue("Relay preparation failed: ${ready.problem}", ready.active && !ready.failed)
                assertTrue(ready.url.startsWith("https://"))
                assertFalse(ready.connecting)
                assertFalse("Health checks must not count as recipient visits", ready.visited)
                assertEquals(0L, ready.sent)
                assertEquals(if (folder) 2 else 1, ready.fileCount)
                instrumentation.sendStatus(0, Bundle().apply {
                    putString("relay_test_url", ready.url)
                    putString("relay_test_kind", if (folder) "folder" else "file")
                    putString("relay_test_sha256", hash)
                    putString("relay_test_completion", completion.absolutePath)
                })
                await(120_000) { completion.exists() }
                assertEquals("External receiver must verify all bytes", hash, completion.readText().trim())
                assertTrue(DirectShareManager.state.value.visited)
                assertTrue(DirectShareManager.state.value.sent >= payload.size)
                assertTrue(connectivity.getNetworkCapabilities(connectivity.activeNetwork)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true)
                instrumentation.runOnMainSync { DirectShareManager.stop(context) }
                await(10_000) { !DirectShareManager.state.value.stopping }
                assertFalse(DirectShareManager.state.value.active)
                assertEquals("", DirectShareManager.state.value.url)
                assertFalse("Remove this share's snapshot", File(context.cacheDir, "direct-share")
                    .walkTopDown().any { it.isDirectory && it.name == ready.id })
                instrumentation.sendStatus(0, Bundle().apply { putString("relay_test_stopped", ready.url) })
            }
            // Stop during connection setup; cancellation must not leave a later URL or snapshot.
            val uri = DocumentsContract.buildDocumentUri(context.packageName + ".documents", file.absolutePath)
            instrumentation.runOnMainSync { DirectShareManager.start(context, uri, config) }
            await(10_000) { DirectShareManager.state.value.let { it.connecting || !it.active } }
            val cancelledId = DirectShareManager.state.value.id
            instrumentation.runOnMainSync { DirectShareManager.stop(context) }
            await(20_000) { !DirectShareManager.state.value.stopping }
            assertEquals("", DirectShareManager.state.value.url)
            assertFalse(File(context.cacheDir, "direct-share").walkTopDown().any { it.name == cancelledId })
        } finally {
            instrumentation.runOnMainSync { DirectShareManager.stop(context); activity.finish() }
            fixture.deleteRecursively()
            completion.delete()
        }
    }

    private fun await(timeout: Long, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (!condition()) {
            if (SystemClock.elapsedRealtime() >= deadline) fail("Timed out waiting for sharing state / external receiver")
            Thread.sleep(100)
        }
    }
}
