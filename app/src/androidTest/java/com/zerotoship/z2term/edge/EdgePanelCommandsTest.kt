package com.zerotoship.z2term.edge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zerotoship.z2term.proot.ProotLauncher
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class EdgePanelCommandsTest {
    @Test fun exportedCommandsRecreatePanelsThroughAndroidShellAndRealApi() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = EdgeRuntime.store(context)
        val root = "backup_test_" + UUID.randomUUID().toString().replace("-", "")
        val first = root + "_a"
        val second = root + "_b"
        val enabled = store.enabled()
        fun execute(script: String) {
            val process = ProotLauncher(context).launchAndroidSh(extraArgs = listOf("-c", script))
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
                    } catch (_: IOException) { /* PTY EOF */ }
                    bytes.toString("UTF-8")
                }
                val output = future.get(30, TimeUnit.SECONDS)
                assertEquals(output, 0, process.waitFor())
            } finally { process.close(); worker.shutdownNow() }
        }
        try {
            // Unique IDs and hidden handles keep this test separate from the user's panels.
            store.setPanel(root, mapOf("label" to "保存 ' \" \$HOME", "handle" to "off", "width" to "80%"))
            store.addTab(root, first, "First")
            store.addTab(root, second, "Second")
            store.setItem("$first:button", mapOf("label" to "引用符 ' \" =",
                "run" to "printf '%s\\n' \"\$HOME\"; printf '%s' '\$(printf inert)'", "off" to ""))
            val original = store.panels().filter { it.id in listOf(root, first, second) }
            val groupScript = EdgePanelCommands.generate(original, root)
            val tabScript = EdgePanelCommands.generate(original, first)
            listOf(first, second, root).forEach { store.removePanel(it) }
            execute(groupScript)
            assertEquals(original, store.panels().filter { it.id in listOf(root, first, second) })
            store.removePanel(first)
            store.setPanel(root, mapOf("width" to "60%"))
            execute(tabScript)
            assertEquals(original.first { it.id == first }, store.panel(first))
            assertEquals("60%", store.panel(root).fields["width"])
            assertEquals(listOf(second, first), store.panel(root).tabs)
            assertEquals(enabled, store.enabled())
        } finally {
            listOf(first, second, root).forEach { if (store.directory(it).isDirectory) store.removePanel(it) }
            EdgeRuntime.reload(context)
        }
    }
}
