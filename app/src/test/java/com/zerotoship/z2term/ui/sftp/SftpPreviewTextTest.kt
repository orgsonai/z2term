package com.zerotoship.z2term.ui.sftp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SftpPreviewTextTest {

    @Test
    fun extensionlessUtf8Text_isDetectedFromContent() {
        val bytes = "#!/bin/sh\necho hello\n".toByteArray()

        assertEquals("#!/bin/sh\necho hello\n", decodePreviewText(bytes))
    }

    @Test
    fun utf16TextWithBom_isDetected() {
        val body = "日本語の設定".toByteArray(Charsets.UTF_16LE)
        val bytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + body

        assertEquals("日本語の設定", decodePreviewText(bytes))
    }

    @Test
    fun binaryContent_isRejected() {
        val bytes = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x7F)

        assertNull(decodePreviewText(bytes))
    }

    @Test
    fun malformedUtf8WithoutTextDeclaration_isRejected() {
        val bytes = byteArrayOf(0xC3.toByte(), 0x28)

        assertNull(decodePreviewText(bytes))
    }
}
