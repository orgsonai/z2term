package com.zerotoship.z2term.share

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DirectShareGrantTest {
    private class RecordingContext : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        val starts = ArrayList<Intent>()
        override fun startForegroundService(service: Intent): ComponentName? {
            starts.add(Intent(service))
            return service.component
        }
        override fun startService(service: Intent): ComponentName? {
            starts.add(Intent(service))
            return service.component
        }
        override fun stopService(name: Intent) = false
    }

    @Test fun folderGrantIncludesOnlyTheSelectionAndNonSecretRelaySettings() {
        val context = RecordingContext()
        val tree = Uri.parse("content://documents.example/tree/chosen")
        try {
            DirectShareManager.start(context, tree,
                ShareRelayConfig.parse("saved-profile", "https://share.example", 8080, 15), folder = true)
            assertEquals(1, context.starts.size)
            val selection = context.starts.single()
            assertEquals(tree, selection.data)
            assertTrue(selection.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION != 0)
            assertTrue(selection.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(1, selection.clipData!!.itemCount)
            assertEquals(tree, selection.clipData!!.getItemAt(0).uri)
            assertEquals(0, selection.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            assertEquals("saved-profile", selection.getStringExtra("profile"))
            assertEquals("https://share.example", selection.getStringExtra("origin"))
            assertEquals(8080, selection.getIntExtra("remotePort", 0))
            assertEquals(setOf("id", "file", "profile", "origin", "remotePort", "minutes", "folder"),
                selection.extras!!.keySet())
        } finally { DirectShareManager.stop(context) }
        assertFalse(DirectShareManager.state.value.active)
        assertFalse(DirectShareManager.state.value.stopping)
    }

    @Test fun singleFileGrantDoesNotIncludeSiblings() {
        val context = RecordingContext()
        val file = Uri.parse("content://documents.example/document/chosen")
        try {
            DirectShareManager.start(context, file,
                ShareRelayConfig.parse("saved-profile", "https://share.example", 8080, 5))
            assertEquals(0, context.starts.single().flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        } finally { DirectShareManager.stop(context) }
    }
}
