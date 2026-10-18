package com.zerotoship.z2term.backup

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zerotoship.z2term.edge.EdgeRuntime
import com.zerotoship.z2term.channel.SshProfile
import com.zerotoship.z2term.channel.SshProfileStore
import com.zerotoship.z2term.service.WhenManager
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.snippets.Snippet
import com.zerotoship.z2term.snippets.SnippetStore
import com.zerotoship.z2term.widget.WidgetStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class BackupRestoreTest {
    @Test fun restoresPlainBackup() = roundTrip("")

    @Test fun restoresEncryptedBackupAndRejectsWrongPassphrase() = roundTrip("backup test 合言葉")

    @Test fun restoresWhenAppOwnedHomeLostItsAccessBits() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        check(app.packageName.endsWith(".debug2"))
        BackupHomeAccess.prepare(app)
        val id = "backup-test-${UUID.randomUUID()}"
        val macro = File(WidgetStore.macroDir(app), "$id.sh")
        val archive = File(app.cacheDir, "$id.zip")
        val home = File(app.filesDir, "shared_home")
        try {
            archive.outputStream().use { out ->
                BackupArchive.write(out, "{\"format\":1}".toByteArray(),
                    mapOf("macros/$id.sh" to "restored".toByteArray()), null)
            }
            assertTrue(home.setReadable(false, false))
            assertTrue(home.setExecutable(false, false))
            assertNotNull(BackupManager.import(app, Uri.fromFile(archive), ""))
            assertEquals("restored", macro.readText())
        } finally {
            home.setReadable(true, true); home.setExecutable(true, true)
            macro.delete(); archive.delete()
        }
    }

    /** 既定の取り込みは、自動で動く設定を止めて入れる (1.0.49)。 */
    @Test fun importStopsAutomationByDefault() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        check(app.packageName.endsWith(".debug2"))
        BackupHomeAccess.prepare(app)
        val id = "backup-test-${UUID.randomUUID()}"
        val rule = File(WhenManager.whenDir(app), "$id.rule")
        val archive = File(app.cacheDir, "$id.zip")
        val settings = AppSettings(app)
        val before = settings.flow.first()
        val servers = "[{\"id\":\"$id\",\"name\":\"x\",\"command\":\"echo x\",\"enabled\":true}]"
        val settingsJson = "{\"server_entries\":{\"t\":\"s\",\"v\":${org.json.JSONObject.quote(servers)}}," +
            "\"servers_autostart_on_boot\":{\"t\":\"b\",\"v\":true}," +
            "\"init_command\":{\"t\":\"s\",\"v\":\"echo injected\"}}"
        try {
            archive.outputStream().use { out ->
                BackupArchive.write(out, "{\"format\":1}".toByteArray(), mapOf(
                    "when/$id.rule" to "trigger=boot\nrun=echo hi\nenabled=1\n".toByteArray(),
                    "settings.json" to settingsJson.toByteArray(),
                ), null)
            }
            val result = checkNotNull(BackupManager.import(app, Uri.fromFile(archive), ""))
            assertEquals(4, result.stopped)
            assertTrue(rule.readText().endsWith("enabled=0\n"))
            val after = settings.flow.first()
            assertEquals(before.initCommand, after.initCommand)
            assertEquals(before.serversAutostartOnBoot, after.serversAutostartOnBoot)
            assertTrue(com.zerotoship.z2term.settings.ServerEntry.decode(after.serverEntries).none { it.id == id && it.enabled })
        } finally {
            rule.delete(); archive.delete()
            settings.setInitCommand(before.initCommand)
            settings.setServersAutostartOnBoot(before.serversAutostartOnBoot)
            settings.setServerEntries(before.serverEntries)
        }
    }

    private fun roundTrip(passphrase: String) = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        // Run on the separate debug package, never on the user's live release settings.
        check(app.packageName.endsWith(".debug2"))
        BackupHomeAccess.prepare(app)
        val id = "backup-test-${UUID.randomUUID()}"
        val macros = WidgetStore.macroDir(app)
        val macro = File(macros, "$id.sh")
        val rule = File(WhenManager.whenDir(app), "$id.rule")
        val archive = File(app.cacheDir, "$id.zip")
        val snippets = SnippetStore(app)
        val profiles = SshProfileStore(app)
        val settings = AppSettings(app)
        val previousFontSize = settings.flow.first().fontSizeSp
        val edge = EdgeRuntime.store(app)
        val previousEnabled = edge.enabled()
        val externalNote = File(app.cacheDir, "$id-note.txt")
        val includeSecrets = passphrase.isNotEmpty()
        val original = "#!/bin/sh\nprintf 'backup test 日本語\\n'\n"
        try {
            settings.setFontSize(17f)
            edge.setPanel(id, mapOf("handle" to "bar", "side" to "left", "scroll-how" to "node"))
            edge.setItem("$id:memo", mapOf("type" to "note", "file" to externalNote.path))
            externalNote.writeText("backup note 日本語")
            edge.enable(false)
            macro.writeText(original)
            rule.writeText("enabled=false\n")
            snippets.upsert(Snippet(id = id, label = "Backup test", command = "printf original"))
            profiles.upsert(SshProfile(id = id, name = "Backup test", host = "127.0.0.1",
                user = "root",
                password = if (includeSecrets) "test-only-secret" else ""))
            archive.outputStream().use {
                BackupManager.export(app, it, BackupManager.Options(includeSecrets, passphrase))
            }
            val uri = Uri.fromFile(archive)
            val summary = checkNotNull(BackupManager.peek(app, uri))
            assertEquals(includeSecrets, summary.encrypted)
            assertTrue(summary.macroCount > 0)
            assertTrue(summary.edgePanelCount > 0)
            assertTrue(summary.edgeNoteCount > 0)
            settings.setFontSize(21f)
            edge.setPanel(id, mapOf("scroll-how" to "swipe"))
            externalNote.writeText("changed note")
            macro.writeText("changed")
            rule.delete()
            snippets.upsert(Snippet(id = id, label = "Changed", command = "printf changed"))
            profiles.delete(id)
            if (includeSecrets) {
                assertNull(BackupManager.import(app, uri, "wrong", keepAutomation = true))
                assertEquals(21f, settings.flow.first().fontSizeSp, 0f)
                assertEquals("changed", macro.readText())
                assertFalse(rule.exists())
                assertEquals("swipe", edge.panel(id).fields["scroll-how"])
                assertEquals("changed note", externalNote.readText())
            }
            // 自分のバックアップを元どおりに戻す (自動で動く設定も止めない)。
            assertNotNull(BackupManager.import(app, uri, passphrase, keepAutomation = true))
            assertEquals(17f, settings.flow.first().fontSizeSp, 0f)
            assertEquals(original, macro.readText())
            assertEquals("node", edge.panel(id).fields["scroll-how"])
            assertEquals("backup note 日本語", edge.noteFile(id, edge.item("$id:memo")).readText())
            assertEquals("changed note", externalNote.readText())
            assertFalse(edge.enabled())
            assertTrue(macro.canExecute())
            assertEquals("enabled=false\n", rule.readText())
            assertEquals("printf original", snippets.snippets.first().single { it.id == id }.command)
            val restored = profiles.profiles.first().single { it.id == id }
            assertEquals(if (includeSecrets) "test-only-secret" else "", restored.password)
        } finally {
            settings.setFontSize(previousFontSize)
            edge.directory(id).deleteRecursively(); edge.enable(previousEnabled); externalNote.delete()
            macro.delete(); rule.delete(); archive.delete()
            snippets.delete(id); profiles.delete(id)
        }
    }
}
