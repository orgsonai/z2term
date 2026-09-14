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

    @Test fun folderHttpsDoesNotBroadenTheCertificatesExactGrant() {
        val context = RecordingContext()
        val tree = Uri.parse("content://documents.example/tree/chosen")
        val certificate = Uri.parse("content://documents.example/document/certificate")
        try {
            DirectShareManager.start(context, tree, "chosen",
                DirectShareConfig.parse("https://share.example:8443", 8443, 15),
                certificate, "test", false, folder = true)
            assertEquals(2, context.starts.size)
            val selection = context.starts[0]
            val certGrant = context.starts[1]
            assertEquals(tree, selection.data)
            assertTrue(selection.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION != 0)
            assertEquals(1, selection.clipData!!.itemCount)
            assertEquals(tree, selection.clipData!!.getItemAt(0).uri)
            assertEquals(certificate, certGrant.data)
            assertEquals(0, certGrant.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            assertTrue(certGrant.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(selection.getStringExtra("id"), certGrant.getStringExtra("id"))
            assertEquals(0, selection.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            assertEquals(0, certGrant.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        } finally { DirectShareManager.stop(context) }
        assertFalse(DirectShareManager.state.value.active)
        assertFalse(DirectShareManager.state.value.stopping)
    }
}
