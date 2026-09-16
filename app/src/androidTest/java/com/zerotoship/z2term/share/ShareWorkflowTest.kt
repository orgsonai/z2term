package com.zerotoship.z2term.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zerotoship.z2term.snippets.Snippet
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ShareWorkflowTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun receivesCaptionAndAttachmentsOnceAndWritesACompleteReceipt() {
        val root = File(context.cacheDir, "shared-files/test-${UUID.randomUUID()}").apply { mkdirs() }
        var receipt: File? = null
        try {
            val input = File(root, "manifest.json").apply { writeText("attachment, not a receipt") }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.sharedfiles", input)
            val intent = Intent(Intent.ACTION_SEND).setType("application/json")
                .putExtra(Intent.EXTRA_TEXT, "caption ' and https://example.test/source")
                .putExtra(Intent.EXTRA_SUBJECT, "shared title")
                .putExtra(Intent.EXTRA_STREAM, uri).apply { clipData = ClipData.newRawUri("", uri) }
            val intake = requireNotNull(SharedIntake.intakeFrom(context, intent))
            receipt = File(context.filesDir, "shared_home/${intake.manifest}")
            val json = JSONObject(receipt.readText())
            assertEquals("mixed", intake.kind)
            assertEquals(1, intake.files.size)
            assertEquals("caption ' and https://example.test/source", intake.body)
            assertEquals(intake.body, intake.text)
            assertEquals(intake.body, json.getString("text"))
            assertEquals("shared title", json.getString("subject"))
            assertEquals(1, json.getJSONArray("files").length())
            assertNotEquals("manifest.json", intake.fileNames.single())
            input.delete()
            assertEquals("attachment, not a receipt", File(context.filesDir, "shared_home/${intake.files.single()}").readText())
        } finally { root.deleteRecursively(); receipt?.parentFile?.deleteRecursively() }
    }

    @Test fun failedAttachmentDoesNotLeaveAPartialReceipt() {
        val root = File(context.cacheDir, "shared-files/test-${UUID.randomUUID()}").apply { mkdirs() }
        val inbox = File(context.filesDir, "shared_home/${SharedIntake.INBOX_DIR}")
        val before = inbox.list().orEmpty().toSet()
        try {
            val good = File(root, "ok.txt").apply { writeText("ok") }
            val bad = File(root, "missing.txt")
            val uris = arrayListOf(good, bad).map {
                FileProvider.getUriForFile(context, "${context.packageName}.sharedfiles", it)
            }
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).putExtra(Intent.EXTRA_TEXT, "caption")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            assertTrue(runCatching { SharedIntake.intakeFrom(context, intent) }.isFailure)
            assertEquals(before, inbox.list().orEmpty().toSet())
        } finally { root.deleteRecursively() }
    }

    @Test fun fileShareProvidesReadableUrisAndReadOnlyGrantsForEveryFile() {
        val root = File(context.cacheDir, "shared-files/test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val files = listOf(File(root, "写真.png").apply { writeText("image") }, File(root, "report.pdf").apply { writeText("pdf") })
            val intent = OutgoingShare.intent(context, files)
            assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
            assertEquals("*/*", intent.type)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val clip = requireNotNull(intent.clipData)
            assertEquals(2, clip.itemCount)
            files.forEachIndexed { index, file ->
                val uri = clip.getItemAt(index).uri
                assertEquals("content", uri.scheme)
                assertEquals(file.readText(), context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)!!.use {
                    assertTrue(it.moveToFirst()); assertEquals(file.name, it.getString(0))
                }
            }
            val single = OutgoingShare.intent(context, listOf(files.first()))
            assertEquals(Intent.ACTION_SEND, single.action)
            assertEquals("image/png", single.type)
            assertTrue(runCatching { FileProvider.getUriForFile(context, "${context.packageName}.sharedfiles", File(context.filesDir, "private.txt")) }.isFailure)
        } finally { root.deleteRecursively() }
    }

    @Test fun snippetSettingsRoundTripAndOldEntriesStayLiteral() {
        val snippet = Snippet("test", "label", "echo {{text}}", inputForm = true, shareAction = true)
        assertEquals(snippet, Snippet.fromJson(snippet.toJson()))
        val old = Snippet.fromJson(JSONObject().put("id", "old").put("command", "echo {{text}}"))
        assertFalse(old.inputForm)
        assertFalse(old.shareAction)
    }
}
