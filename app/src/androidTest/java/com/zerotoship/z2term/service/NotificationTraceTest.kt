package com.zerotoship.z2term.service

import android.app.Notification
import android.os.Bundle
import android.os.Process
import android.service.notification.StatusBarNotification
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Bundles/JSON, without posting notifications or rebuilding platform Messages. */
@RunWith(AndroidJUnit4::class)
class NotificationTraceTest {
    @Suppress("DEPRECATION")
    private fun posted(pkg: String = "sample.app", text: String = "private body"): StatusBarNotification {
        val n = Notification().apply {
            extras = Bundle().apply {
                putCharSequence(Notification.EXTRA_TITLE, "private title")
                putCharSequence(Notification.EXTRA_TEXT, text)
            }
        }
        return StatusBarNotification(pkg, pkg, 1, "private tag", 12345, 0,
            n, Process.myUserHandle(), null, 123L)
    }

    @Test fun receivedLengthsSurviveWithoutKeepingTextValues() {
        val full = "private message ".repeat(800) + "😀"
        val extras = Bundle().apply {
            putCharSequence(Notification.EXTRA_TEXT, full.take(1024))
            putParcelableArray(Notification.EXTRA_MESSAGES, arrayOf(Bundle().apply {
                putCharSequence("text", full)
                putCharSequence("sender", "private sender")
            }))
        }
        val shaped = NotificationTrace.shape(extras) as JSONObject
        val fields = shaped.getJSONObject("fields")
        assertEquals(1024, fields.getJSONObject(Notification.EXTRA_TEXT).getInt("utf16"))
        val message = fields.getJSONObject(Notification.EXTRA_MESSAGES).getJSONArray("items")
            .getJSONObject(0).getJSONObject("fields")
        assertEquals(full.length, message.getJSONObject("text").getInt("utf16"))
        assertFalse(shaped.toString().contains("private"))
        assertFalse(shaped.toString().contains("😀"))
    }

    @Test fun captureIsOptInFilteredBoundedAndExpiresWithoutDiscardingResults() {
        var now = 100L
        val trace = NotificationTrace(clock = { now }, capacity = 2, durationMs = 1000L)
        val n = posted()
        val body = NotificationText.Body("private body")
        trace.record(n, body)
        assertEquals(0, JSONObject(trace.command("dump")).getJSONArray("events").length())
        trace.command("start", "sample.app")
        trace.record(posted("different.app"), body)
        // Repeated callbacks must remain visible even if the normal history deduplicates them.
        repeat(3) { now++; trace.record(n, body) }
        val active = JSONObject(trace.command("dump"))
        assertEquals(2, active.getJSONArray("events").length())
        assertEquals(1, active.getInt("dropped"))
        assertFalse(active.toString().contains("private"))
        now = 1100L
        trace.record(n, body)
        val expired = JSONObject(trace.command("dump"))
        assertFalse(expired.getBoolean("active"))
        assertEquals(2, expired.getJSONArray("events").length())
        trace.command("start")
        assertEquals(0, JSONObject(trace.command("stop")).getJSONArray("events").length())
        trace.record(n, body)
        assertEquals(0, JSONObject(trace.command("dump")).getJSONArray("events").length())
    }

    @Test fun failedExtractionStillRecordsTheAvailableFields() {
        val trace = NotificationTrace(clock = { 100L })
        trace.command("start")
        trace.record(posted(), null)
        val event = JSONObject(trace.command("stop")).getJSONArray("events").getJSONObject(0)
        assertTrue(event.getBoolean("extractionFailed"))
        assertTrue(event.isNull("selectedUtf16"))
        assertEquals(12, event.getJSONObject("extras").getJSONObject("fields")
            .getJSONObject(Notification.EXTRA_TEXT).getInt("utf16"))
    }

    @Test fun nestedMetadataCannotGrowWithoutBound() {
        val text = "private message"
        val leaf = Bundle().apply { repeat(48) { putString("field$it", text) } }
        val branch = Bundle().apply { repeat(48) { putBundle("child$it", leaf) } }
        val root = Bundle().apply { repeat(48) { putBundle("branch$it", branch) } }
        val result = NotificationTrace.shape(root).toString()
        assertTrue(result.length < 30000)
        assertFalse(result.contains(text))
    }
}
