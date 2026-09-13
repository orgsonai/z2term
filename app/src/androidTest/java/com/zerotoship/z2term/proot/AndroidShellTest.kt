package com.zerotoship.z2term.proot

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Requires a device: host tests cannot validate Android's executable-file policy. */
@RunWith(AndroidJUnit4::class)
class AndroidShellTest {
    @Test fun packagedCommandsWorkFromNestedSystemShellWithoutLinux() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val process = ProotLauncher(context).launchAndroidSh(extraArgs = listOf("-c",
            "printf 'HOME=%s\\n' \"\$HOME\"; sh -c 'z2help && z2-battery'"))
        val worker = Executors.newSingleThreadExecutor()
        try {
            val future = worker.submit<String> {
                val bytes = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                try {
                    while (true) {
                        val n = process.reader.read(buffer)
                        if (n < 0) break
                        bytes.write(buffer, 0, n)
                    }
                } catch (_: IOException) { /* PTY EOF may be reported as EIO. */ }
                bytes.toString("UTF-8")
            }
            val out = future.get(20, TimeUnit.SECONDS)
            assertEquals(out, 0, process.waitFor())
            assertTrue(out, out.contains("HOME=${File(context.filesDir, "shared_home").absolutePath}"))
            assertTrue(out, out.contains("z2-edge"))
            assertTrue(out, out.contains("level"))
        } finally { process.close(); worker.shutdownNow() }
    }
}
