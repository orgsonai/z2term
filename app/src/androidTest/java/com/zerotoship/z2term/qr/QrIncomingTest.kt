package com.zerotoship.z2term.qr

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QrIncomingTest {
    @Test fun imageStreamsTakePriorityOverCaptionsAndDuplicateClipUris() {
        val uri = Uri.parse("content://photos.example/item/1")
        val intent = Intent(Intent.ACTION_SEND).setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, "https://caption.example")
        intent.clipData = ClipData.newRawUri("Image", uri)
        val input = QrIncoming.from(intent)
        assertEquals(listOf(uri), input.images)
        assertEquals("", input.text)
    }

    @Test fun clipOnlyImagesAndMultipleStreamsAreAccepted() {
        val first = Uri.parse("content://photos.example/1")
        val second = Uri.parse("content://photos.example/2")
        val clip = Intent(Intent.ACTION_SEND).setType("image/jpeg").apply {
            clipData = ClipData.newRawUri("Image", first)
        }
        assertEquals(listOf(first), QrIncoming.from(clip).images)
        val multiple = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
        assertEquals(listOf(first, second), QrIncoming.from(multiple).images)
    }

    @Test fun filesNetworkUrisAndTooManyImagesAreRejectedWithoutOpeningThem() {
        for (uri in listOf("file:///sdcard/qr.png", "https://images.example/qr.png", "content:///missing-authority")) {
            val intent = Intent(Intent.ACTION_SEND).setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
            assertEquals(QrIncoming(), QrIncoming.from(intent))
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/png")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM,
                ArrayList((1..9).map { Uri.parse("content://photos.example/" + it) }))
        assertEquals(QrIncoming(), QrIncoming.from(intent))
    }

    @Test fun sharedTextIsRetainedForReviewWithoutTreatingPrivateExtrasAsActions() {
        val command = "echo safe\nnext command"
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, command).putExtra("text", "unexpected").putExtra("show", true)
        assertEquals(command, QrIncoming.from(intent).text)
        val clip = Intent(Intent.ACTION_SEND).setType("text/uri-list").apply {
            clipData = ClipData.newPlainText("URL", "https://example.org")
        }
        assertEquals("https://example.org", QrIncoming.from(clip).text)
        assertEquals(QrIncoming(), QrIncoming.from(Intent().putExtra("text", "unexpected")))
    }

    @Test fun oversizedTextAndIncorrectParcelableTypesDoNotBecomePartialCommands() {
        val huge = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "x".repeat(QrContent.MAX_TEXT + 1))
        assertEquals(QrIncoming(), QrIncoming.from(huge))
        val wrong = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, "not a URI")
        assertEquals(QrIncoming(), QrIncoming.from(wrong))
    }

    @Test fun viewActionsAcceptOnlySupportedQrPayloads() {
        val command = QrContent.command("Example", "echo reviewed")
        val endpoint = "ssh://demo@host.example:2222"
        for (value in listOf(command, endpoint)) {
            assertEquals(value, QrIncoming.from(Intent(Intent.ACTION_VIEW, Uri.parse(value))).text)
        }
        for (value in listOf("https://example.org", "javascript:alert(1)", "ssh://user:pass@host.example",
            "z2term://command?text=echo%0Areboot")) {
            assertEquals(value, QrIncoming(), QrIncoming.from(Intent(Intent.ACTION_VIEW, Uri.parse(value))))
        }
    }
}
