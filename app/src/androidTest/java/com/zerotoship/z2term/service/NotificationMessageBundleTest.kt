package com.zerotoship.z2term.service

import android.app.Notification
import android.app.Person
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise delivered Bundles, without constructing platform Messages that truncate the fixture. */
@RunWith(AndroidJUnit4::class)
class NotificationMessageBundleTest {
    @Test fun currentAndHistoricBundlesKeepTextPast1024And5120() {
        val full = "先頭\n" + "長い本文😀\n".repeat(1200) + "結末まで保存"
        for (field in listOf(Notification.EXTRA_MESSAGES, Notification.EXTRA_HISTORIC_MESSAGES)) {
            val entry = Bundle().apply {
                putCharSequence("text", full)
                putLong("time", 123L)
                putParcelable("sender_person", Person.Builder().setKey("sender-id").setName("Sender").build())
            }
            val extras = Bundle().apply { putParcelableArray(field, arrayOf(entry)) }
            val messages = NotificationLogService.messages(extras, field)
            assertEquals(listOf(NotificationText.Message(123L, full, "sender-id")), messages)
            val body = NotificationText.body(messages, emptyList(), full.take(1024),
                full.take(128), emptyList(), emptyList())
            assertEquals(full, NotificationText.History().fresh("key", body, "title"))
            assertEquals(full, NotificationLogService.render("{text}", 1L, "", "", "", "", body.text, "", ""))
        }
    }

    @Test fun legacySenderAndBatchedMessagesSurviveDecoding() {
        fun entry(text: String, time: Long) = Bundle().apply {
            putCharSequence("text", text)
            putLong("time", time)
            putCharSequence("sender", "sender")
        }
        val full = "文字".repeat(1024) + "最後"
        val extras = Bundle().apply {
            putParcelableArray(Notification.EXTRA_MESSAGES, arrayOf(entry(full, 1L), entry("次の文", 2L)))
        }
        assertEquals(listOf(NotificationText.Message(1L, full, "sender"),
            NotificationText.Message(2L, "次の文", "sender")),
            NotificationLogService.messages(extras, Notification.EXTRA_MESSAGES))
    }
}
