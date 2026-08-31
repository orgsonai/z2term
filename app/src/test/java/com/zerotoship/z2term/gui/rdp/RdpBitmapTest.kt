package com.zerotoship.z2term.gui.rdp

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class RdpBitmapTest {
    @Test
    fun uncompressed24IsBottomUpAndRowsAreFourByteAligned() {
        val raw = pixel24(BLUE) + pixel24(WHITE) + byteArrayOf(0, 0) +
            pixel24(RED) + pixel24(GREEN) + byteArrayOf(0, 0)
        val framebuffer = IntArray(4)

        val dirty = RdpBitmap.applyUpdate(
            update(rect(0, 0, 1, 1, 2, 2, 24, 0, raw)),
            framebuffer,
            2,
            2,
        )

        assertEquals(RdpBitmap.DirtyRect(0, 0, 2, 2), dirty)
        assertArrayEquals(intArrayOf(RED, GREEN, BLUE, WHITE), framebuffer)
    }

    @Test
    fun uncompressed15And16ExpandToArgb() {
        val rgb555 = byteArrayOf(0x00, 0x7C, 0, 0) // red in 5:5:5
        val rgb565 = byteArrayOf(0xE0.toByte(), 0x07, 0, 0) // green in 5:6:5
        val framebuffer = IntArray(2)
        val data = update(
            rect(0, 0, 0, 0, 1, 1, 15, 0, rgb555),
            rect(1, 0, 1, 0, 1, 1, 16, 0, rgb565),
        )

        RdpBitmap.applyUpdate(data, framebuffer, 2, 1)

        assertArrayEquals(intArrayOf(RED, GREEN), framebuffer)
    }

    @Test
    fun multipleRectanglesAreAppliedAndDirtyBoundsAreUnited() {
        val framebuffer = IntArray(8)
        val data = update(
            rect(0, 0, 0, 0, 1, 1, 24, 0, pixel24(RED) + byteArrayOf(0)),
            rect(3, 1, 3, 1, 1, 1, 24, 0, pixel24(BLUE) + byteArrayOf(0)),
        )

        val dirty = RdpBitmap.applyUpdate(data, framebuffer, 4, 2)

        assertEquals(RdpBitmap.DirtyRect(0, 0, 4, 2), dirty)
        assertEquals(RED, framebuffer[0])
        assertEquals(BLUE, framebuffer[7])
    }

    @Test
    fun destinationOutsideFramebufferIsClippedWithoutChangingSourceStride() {
        val raw = row24(CYAN, MAGENTA, YELLOW) + row24(RED, GREEN, BLUE)
        val framebuffer = IntArray(4)

        val dirty = RdpBitmap.applyUpdate(
            update(rect(1, 1, 3, 2, 3, 2, 24, 0, raw)),
            framebuffer,
            2,
            2,
        )

        assertEquals(RdpBitmap.DirtyRect(1, 1, 2, 2), dirty)
        assertEquals(RED, framebuffer[3])
    }

    @Test
    fun compressedBitmapAcceptsHeaderAndHeaderlessForms() {
        val rleRed = byteArrayOf(0x61, 0, 0, 0xFF.toByte()) // regular color run, one red pixel
        val withHeader = compressionHeader(rleRed, width = 1, uncompressed = 3) + rleRed
        val framebuffer = IntArray(3)
        val data = update(
            rect(0, 0, 0, 0, 1, 1, 24, 0x0001, withHeader),
            rect(1, 0, 1, 0, 1, 1, 24, 0x0401, rleRed),
            rect(2, 0, 2, 0, 1, 1, 24, 0x0001, withHeader, declaredLength = rleRed.size),
        )

        RdpBitmap.applyUpdate(data, framebuffer, 3, 1)

        assertArrayEquals(intArrayOf(RED, RED, RED), framebuffer)
    }

    @Test
    fun rleColorDitherLiteralWhiteAndBlackOrdersDecode() {
        val stream = byteArrayOf(
            0x61, 0, 0, 0xFF.toByte(),                         // COLOR_RUN red
            0xE1.toByte(), 0, 0xFF.toByte(), 0, 0xFF.toByte(), 0, 0, // DITHER green/blue
            0x82.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0, 0, 0, // COLOR_IMAGE
            0xFD.toByte(), 0xFE.toByte(),
        )

        val decoded = RdpBitmap.decodeInterleaved(stream, 7, 1, 24)

        assertArrayEquals(intArrayOf(RED, GREEN, BLUE, WHITE, BLACK, WHITE, BLACK), decoded)
    }

    @Test
    fun rleForegroundBackgroundSetAndSpecialOrdersDecode() {
        val setForeground = byteArrayOf(0xC1.toByte(), 0, 0, 0xFF.toByte())
        assertArrayEquals(intArrayOf(RED), RdpBitmap.decodeInterleaved(setForeground, 1, 1, 24))

        val fgImage = byteArrayOf(0x41, 0x55)
        assertArrayEquals(
            intArrayOf(WHITE, BLACK, WHITE, BLACK, WHITE, BLACK, WHITE, BLACK),
            RdpBitmap.decodeInterleaved(fgImage, 8, 1, 24),
        )

        val setFgImage = byteArrayOf(0xD1.toByte(), 0, 0, 0xFF.toByte(), 0x03)
        assertArrayEquals(
            intArrayOf(RED, RED, BLACK, BLACK, BLACK, BLACK, BLACK, BLACK),
            RdpBitmap.decodeInterleaved(setFgImage, 8, 1, 24),
        )

        assertArrayEquals(
            intArrayOf(WHITE, WHITE, BLACK, BLACK, BLACK, BLACK, BLACK, BLACK),
            RdpBitmap.decodeInterleaved(byteArrayOf(0xF9.toByte()), 8, 1, 24),
        )
        assertArrayEquals(
            intArrayOf(WHITE, BLACK, WHITE, BLACK, BLACK, BLACK, BLACK, BLACK),
            RdpBitmap.decodeInterleaved(byteArrayOf(0xFA.toByte()), 8, 1, 24),
        )
    }

    @Test
    fun rleBackgroundAndForegroundUsePreviousScanline() {
        val bottom = byteArrayOf(0x84.toByte()) + pixel24(RED) + pixel24(GREEN) +
            pixel24(BLUE) + pixel24(WHITE)
        val copiedTop = byteArrayOf(0x04)

        val decoded = RdpBitmap.decodeInterleaved(bottom + copiedTop, 4, 2, 24)

        assertArrayEquals(
            intArrayOf(RED, GREEN, BLUE, WHITE, RED, GREEN, BLUE, WHITE),
            decoded,
        )
    }

    @Test
    fun rleMegaOrdersAndExtendedRunLengthsDecode() {
        val megaColor = byteArrayOf(0xF3.toByte(), 1, 0) + pixel24(RED)
        val megaLiteral = byteArrayOf(0xF4.toByte(), 1, 0) + pixel24(GREEN)
        val megaDither = byteArrayOf(0xF8.toByte(), 1, 0) + pixel24(BLUE) + pixel24(WHITE)
        val megaSetForeground = byteArrayOf(0xF6.toByte(), 1, 0) + pixel24(MAGENTA)
        val stream = megaColor + megaLiteral + megaDither + megaSetForeground

        assertArrayEquals(
            intArrayOf(RED, GREEN, BLUE, WHITE, MAGENTA),
            RdpBitmap.decodeInterleaved(stream, 5, 1, 24),
        )

        val extendedColorRun = byteArrayOf(0x60, 0) + pixel24(CYAN) // 0 + 32 pixels
        assertArrayEquals(IntArray(32) { CYAN }, RdpBitmap.decodeInterleaved(extendedColorRun, 32, 1, 24))

        assertArrayEquals(
            intArrayOf(BLACK),
            RdpBitmap.decodeInterleaved(byteArrayOf(0xF0.toByte(), 1, 0), 1, 1, 24),
        )
        assertArrayEquals(
            intArrayOf(WHITE),
            RdpBitmap.decodeInterleaved(byteArrayOf(0xF1.toByte(), 1, 0), 1, 1, 24),
        )
        assertArrayEquals(
            intArrayOf(WHITE) + IntArray(7) { BLACK },
            RdpBitmap.decodeInterleaved(byteArrayOf(0xF2.toByte(), 8, 0, 0x01), 8, 1, 24),
        )
        assertArrayEquals(
            intArrayOf(RED, RED) + IntArray(6) { BLACK },
            RdpBitmap.decodeInterleaved(
                byteArrayOf(0xF7.toByte(), 8, 0) + pixel24(RED) + byteArrayOf(0x03),
                8,
                1,
                24,
            ),
        )
    }

    /** FreeRDP 3 の interleaved_compress が生成したfixtureとのopt-in相互運用試験。 */
    @Test
    fun decodesFreeRdpInterleavedFixtureIntoDistinctPixels() {
        val fixturePath = System.getenv("Z2TERM_FREERDP_RLE_FIXTURE")
        assumeTrue("set Z2TERM_FREERDP_RLE_FIXTURE to run", !fixturePath.isNullOrBlank())
        val compressed = Files.readAllBytes(Paths.get(fixturePath))
        val framebuffer = IntArray(64 * 64)

        RdpBitmap.applyUpdate(
            update(rect(0, 0, 63, 63, 64, 64, 24, 0x0401, compressed)),
            framebuffer,
            64,
            64,
        )

        // Sample inside each deliberately distinct quadrant instead of at an RLE transition.
        assertEquals(RED, framebuffer[1])
        assertEquals(GREEN, framebuffer[63])
        assertEquals(BLUE, framebuffer[63 * 64 + 1])
        assertEquals(WHITE, framebuffer.last())
        assertTrue(framebuffer.toSet().containsAll(setOf(RED, GREEN, BLUE, WHITE)))
    }

    @Test
    fun malformedLengthsCoordinatesRunsAndOversizedImagesAreRejected() {
        assertThrows(IOException::class.java) {
            RdpBitmap.applyUpdate(
                update(rect(0, 0, 0, 0, 1, 1, 24, 0, byteArrayOf(0, 0))),
                IntArray(1), 1, 1,
            )
        }
        assertThrows(IOException::class.java) {
            RdpBitmap.applyUpdate(
                update(rect(2, 0, 1, 0, 1, 1, 24, 0, byteArrayOf(0, 0, 0, 0))),
                IntArray(1), 1, 1,
            )
        }
        assertThrows(IOException::class.java) {
            RdpBitmap.decodeInterleaved(byteArrayOf(0x05), 4, 1, 24)
        }
        assertThrows(IOException::class.java) {
            RdpBitmap.decodeInterleaved(byteArrayOf(0x61, 0), 1, 1, 24)
        }
        assertThrows(IOException::class.java) {
            RdpBitmap.decodeInterleaved(byteArrayOf(0x61, 0, 0, 0), 1, 1, 32)
        }
        assertThrows(IOException::class.java) {
            RdpBitmap.applyUpdate(
                update(rect(0, 0, 0, 0, 65535, 65535, 24, 0, ByteArray(0))),
                IntArray(1), 1, 1,
            )
        }

        val framebuffer = intArrayOf(MAGENTA, CYAN)
        assertThrows(IOException::class.java) {
            RdpBitmap.applyUpdate(
                update(
                    rect(0, 0, 0, 0, 1, 1, 24, 0, pixel24(RED) + byteArrayOf(0)),
                    rect(1, 0, 1, 0, 1, 1, 24, 0, byteArrayOf(0)),
                ),
                framebuffer,
                2,
                1,
            )
        }
        assertArrayEquals(intArrayOf(MAGENTA, CYAN), framebuffer)
    }

    private fun update(vararg rectangles: ByteArray): ByteArray =
        le16(1) + le16(rectangles.size) + rectangles.fold(ByteArray(0), ByteArray::plus)

    private fun rect(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        width: Int,
        height: Int,
        bitsPerPixel: Int,
        flags: Int,
        bitmap: ByteArray,
        declaredLength: Int = bitmap.size,
    ): ByteArray = le16(left) + le16(top) + le16(right) + le16(bottom) +
        le16(width) + le16(height) + le16(bitsPerPixel) + le16(flags) +
        le16(declaredLength) + bitmap

    private fun compressionHeader(data: ByteArray, width: Int, uncompressed: Int): ByteArray =
        le16(0) + le16(data.size) + le16(width) + le16(uncompressed)

    private fun row24(vararg colors: Int): ByteArray {
        val body = colors.fold(ByteArray(0)) { bytes, color -> bytes + pixel24(color) }
        return body + ByteArray((4 - body.size % 4) % 4)
    }

    private fun pixel24(argb: Int) = byteArrayOf(argb.toByte(), (argb ushr 8).toByte(), (argb ushr 16).toByte())
    private fun le16(value: Int) = byteArrayOf(value.toByte(), (value ushr 8).toByte())

    companion object {
        private const val BLACK = 0xFF000000.toInt()
        private const val RED = 0xFFFF0000.toInt()
        private const val GREEN = 0xFF00FF00.toInt()
        private const val BLUE = 0xFF0000FF.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val CYAN = 0xFF00FFFF.toInt()
        private const val MAGENTA = 0xFFFF00FF.toInt()
        private const val YELLOW = 0xFFFFFF00.toInt()
    }
}
