package com.zerotoship.z2term.proot

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zerotoship.z2term.core.DistroDeletion
import com.zerotoship.z2term.distro.DistroDataDeletion
import com.zerotoship.z2term.pty.PtyProcess
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class DistroDeletionTest {
    @Test fun deletesSelectedSyntheticDistroAndKeepsSharedHome() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        // Never use an installed OS for a destructive test. Every removed path is newly created.
        val id = "deletion-test-${UUID.randomUUID()}"
        val root = File(app.filesDir, "distros/$id").apply { check(mkdirs()) }
        val overlay = File(app.filesDir, "home_overlay/$id").apply { check(mkdirs()) }
        val archive = File(app.cacheDir, "distros/$id-arm64-v8a.tgz")
        val shared = File(app.filesDir, "shared_home/$id.txt")
        val settings = AppSettings(app)
        val previous = settings.flow.first().distroId
        try {
            archive.parentFile!!.mkdirs(); archive.writeText("synthetic")
            shared.parentFile!!.mkdirs(); shared.writeText("preserve")
            File(root, "package.txt").writeText("synthetic")
            File(overlay, "config.txt").writeText("synthetic")
            Files.createSymbolicLink(File(root, "root").toPath(), shared.parentFile!!.toPath())
            settings.setDistro(id)
            assertEquals(id, settings.flow.first().distroId)
            val done = CompletableDeferred<Throwable?>()
            withContext(Dispatchers.Main) { DistroDeletion.delete(app, id) { done.complete(it) } }
            assertNull(withTimeout(30_000) { done.await() })
            assertFalse(root.exists()); assertFalse(overlay.exists()); assertFalse(archive.exists())
            assertEquals("preserve", shared.readText())
            assertNull(DistroDeletion.deleting.value)
        } finally {
            settings.setDistro(previous)
            DistroDataDeletion(app.filesDir, app.cacheDir, id).delete()
            Files.deleteIfExists(shared.toPath())
        }
    }

    @Test fun stoppingOneDistroAndClosingItTwiceDoesNotCloseAnotherTerminal() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val launcher = ProotLauncher(app)
        val id = "process-test-${UUID.randomUUID()}"
        val first = DistroProcesses.register(id, launcher.launchAndroidSh(extraArgs = listOf("-c",
            "trap '' HUP; printf 'READY\\n'; while :; do sleep 1; done")))
        val other = DistroProcesses.register("$id-other", launcher.launchAndroidSh(extraArgs = listOf("-c", "read value; printf 'SURVIVED=%s\\n' \"\$value\"")))
        val worker = Executors.newSingleThreadExecutor()
        val checking = AtomicBoolean(true)
        var reused: PtyProcess? = null
        try {
            assertEquals("READY", worker.submit<String> { first.reader.bufferedReader().readLine() }
                .get(10, TimeUnit.SECONDS))
            val poll = worker.submit {
                while (checking.get()) { first.exitCode; Thread.sleep(1) }
            }
            try { DistroProcesses.stop(id) } finally { checking.set(false) }
            poll.get(5, TimeUnit.SECONDS)
            assertFalse(first.isAlive)
            assertFalse("Engine must exit before deletion", File("/proc/${first.shellPid}").exists())
            assertTrue(other.isAlive)
            // A new process may now receive the old fd. A repeated close must be harmless.
            reused = launcher.launchAndroidSh(extraArgs = listOf("-c", "read value"))
            first.close(); DistroProcesses.stop(id)
            assertTrue(reused.isAlive)
            val output = worker.submit<String> {
                val bytes = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                try {
                    while (true) {
                        val n = other.reader.read(buffer)
                        if (n < 0) break
                        bytes.write(buffer, 0, n)
                    }
                } catch (_: IOException) { /* PTY EOF */ }
                bytes.toString("UTF-8")
            }
            other.writer.write("yes\n".toByteArray())
            val text = output.get(15, TimeUnit.SECONDS)
            assertTrue(text, text.contains("SURVIVED=yes"))
            assertEquals(text, 0, other.waitFor())
        } finally {
            checking.set(false)
            first.close(); other.close(); reused?.close(); worker.shutdownNow()
            DistroProcesses.stop(id); DistroProcesses.stop("$id-other")
        }
    }
}
