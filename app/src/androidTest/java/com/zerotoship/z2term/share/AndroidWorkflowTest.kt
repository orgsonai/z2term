package com.zerotoship.z2term.share

import android.content.Context
import android.content.Intent
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
class AndroidWorkflowTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Test fun textProcessingIsOptInAndSurvivesSnippetExport() {
        val old = Snippet.fromJson(JSONObject("""{"id":"old","command":"cat"}"""))
        assertFalse(old.processTextAction)
        val action = old.copy(processTextAction = true, inputForm = true)
        assertEquals(action, Snippet.fromJson(action.toJson()))
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain").setPackage(context.packageName)
        val resolved = context.packageManager.resolveActivity(intent, 0)
        assertTrue(requireNotNull(resolved).activityInfo.exported)
        assertEquals(ProcessTextActivity::class.java.name, resolved.activityInfo.name)
    }

    @Test fun pickedDocumentBecomesAnIndependentBinaryFileAndCancellationCleansPartialCopy() {
        val sourceDir = File(context.cacheDir, "shared-files/test-${UUID.randomUUID()}").apply { mkdirs() }
        val inbox = File(context.filesDir, "shared_home/${SharedIntake.INBOX_DIR}")
        var imported: File? = null
        try {
            val bytes = ByteArray(150001) { it.toByte() }
            val source = File(sourceDir, "binary data.bin").apply { writeBytes(bytes) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.sharedfiles", source)
            val before = inbox.list().orEmpty().toSet()
            var checks = 0
            assertThrows(IllegalArgumentException::class.java) {
                SharedIntake.importDocument(context, uri) { checks++ == 0 }
            }
            assertEquals(before, inbox.list().orEmpty().toSet())
            imported = File(context.filesDir, "shared_home/${SharedIntake.importDocument(context, uri)}")
            source.delete()
            assertArrayEquals(bytes, imported.readBytes())
        } finally { sourceDir.deleteRecursively(); imported?.parentFile?.deleteRecursively() }
    }
}
