package com.zerotoship.z2term.emulator

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * [KittyHostTransferSource] の単体テスト。 ゲスト→ホストパス変換と range 読込、
 * `TempFile` の自動 unlink、 path traversal 防御を確認する。
 */
class KittyHostTransferSourceTest {

    private lateinit var rootfs: File

    @Before
    fun setUp() {
        rootfs = File(System.getProperty("java.io.tmpdir"), "z2term_kitty_xfer_test_${System.nanoTime()}")
        rootfs.mkdirs()
    }

    @After
    fun tearDown() {
        rootfs.deleteRecursively()
    }

    private fun writeGuestFile(relative: String, body: ByteArray): File {
        val f = File(rootfs, relative)
        f.parentFile!!.mkdirs()
        f.writeBytes(body)
        return f
    }

    @Test
    fun fileTransferReadsWholePayload() {
        val body = "hello world".toByteArray()
        writeGuestFile("tmp/img.png", body)
        val src = KittyHostTransferSource(rootfs)
        val got = src.read(KittyGraphicsParser.TransferKind.File, "/tmp/img.png", 0L, -1L)
        assertArrayEquals(body, got)
    }

    @Test
    fun fileTransferRespectsOffsetAndSize() {
        val body = "0123456789".toByteArray()
        writeGuestFile("tmp/r.bin", body)
        val src = KittyHostTransferSource(rootfs)
        val got = src.read(KittyGraphicsParser.TransferKind.File, "/tmp/r.bin", 3L, 4L)
        assertArrayEquals("3456".toByteArray(), got)
    }

    @Test
    fun fileTransferTreatsNegativeSizeAsTail() {
        val body = "abcdef".toByteArray()
        writeGuestFile("tmp/tail.bin", body)
        val src = KittyHostTransferSource(rootfs)
        val got = src.read(KittyGraphicsParser.TransferKind.File, "/tmp/tail.bin", 2L, -1L)
        assertArrayEquals("cdef".toByteArray(), got)
    }

    @Test
    fun tempFileTransferUnlinksAfterRead() {
        val body = "scratch".toByteArray()
        val host = writeGuestFile("tmp/scratch", body)
        val src = KittyHostTransferSource(rootfs)
        val got = src.read(KittyGraphicsParser.TransferKind.TempFile, "/tmp/scratch", 0L, -1L)
        assertArrayEquals(body, got)
        assertFalse("temp file should be deleted after read", host.exists())
    }

    @Test
    fun sharedMemoryMapsToDevShmUnderRootfs() {
        val body = "shm-payload".toByteArray()
        writeGuestFile("dev/shm/k-img-1", body)
        val src = KittyHostTransferSource(rootfs)
        val got = src.read(KittyGraphicsParser.TransferKind.SharedMemory, "/k-img-1", 0L, -1L)
        assertArrayEquals(body, got)
    }

    @Test
    fun pathTraversalIsRejected() {
        // `../` で rootfs の外を指そうとするケース。 normalize 結果が rootfs 配下に
        // 留まらないなら null。
        File(rootfs.parentFile, "outside.bin").writeBytes("nope".toByteArray())
        val src = KittyHostTransferSource(rootfs)
        val got = src.read(KittyGraphicsParser.TransferKind.File, "/../outside.bin", 0L, -1L)
        assertNull(got)
    }

    @Test
    fun emptyNameIsRejected() {
        val src = KittyHostTransferSource(rootfs)
        assertNull(src.read(KittyGraphicsParser.TransferKind.File, "", 0L, -1L))
        assertNull(src.read(KittyGraphicsParser.TransferKind.SharedMemory, "/", 0L, -1L))
    }

    @Test
    fun nonAbsoluteFilePathIsRejected() {
        writeGuestFile("rel/x.bin", "x".toByteArray())
        val src = KittyHostTransferSource(rootfs)
        // file/temp は絶対パス (`/...`) を要求する。 相対は拒否。
        assertNull(src.read(KittyGraphicsParser.TransferKind.File, "rel/x.bin", 0L, -1L))
    }

    @Test
    fun missingFileReturnsNull() {
        val src = KittyHostTransferSource(rootfs)
        assertNull(src.read(KittyGraphicsParser.TransferKind.File, "/no/such.bin", 0L, -1L))
    }

    @Test
    fun offsetBeyondFileLengthReturnsNull() {
        writeGuestFile("tmp/small", "ab".toByteArray())
        val src = KittyHostTransferSource(rootfs)
        assertNull(src.read(KittyGraphicsParser.TransferKind.File, "/tmp/small", 99L, 1L))
    }

    @Test
    fun zeroSliceReturnsEmptyArray() {
        writeGuestFile("tmp/z", "abc".toByteArray())
        val src = KittyHostTransferSource(rootfs)
        val got = src.read(KittyGraphicsParser.TransferKind.File, "/tmp/z", 0L, 0L)
        assertNotNull(got)
        assertEquals(0, got!!.size)
    }

    @Test
    fun oversizedReadIsRejected() {
        // 上限超え (16 MiB) の slice 指定は zip-bomb / DoS 対策で拒否。
        // ファイル自体は小さくして OK。 size パラメータが上限を越えた時点で null。
        writeGuestFile("tmp/big", ByteArray(8))
        val src = KittyHostTransferSource(rootfs)
        val tooBig = KittyHostTransferSource.MAX_BYTES + 1
        assertNull(src.read(KittyGraphicsParser.TransferKind.File, "/tmp/big", 0L, tooBig))
    }
}
