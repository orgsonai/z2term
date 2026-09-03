package com.zerotoship.z2term.gui.rdp

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [RdpDrive] — RDP のフォルダ共有が相手 (Windows) の要求どおりに答えるか。
 *
 * ⭐ ここで一番大事なのは **[root] の外へ出られないこと**。開ける・読める・書けるは
 * 直せば済むが、共有フォルダの外が見えてしまうのは直す前に事故になる。
 */
class RdpDriveTest {

    private lateinit var root: File
    private lateinit var drive: RdpDrive

    @Before
    fun setUp() {
        root = Files.createTempDirectory("rdp-drive").toFile()
        drive = RdpDrive(root = root, shareName = "z2term")
    }

    @After
    fun tearDown() {
        drive.close()
        root.deleteRecursively()
    }

    // --- 外へ出さない ---

    /** ⛔⛔ `..` を混ぜた道は開かせない。 */
    @Test
    fun aPathThatClimbsOutOfTheShareIsRefused() {
        File(root.parentFile, "outside.txt").writeText("secret")

        val response = create("\\..\\outside.txt", disposition = FILE_OPEN)

        assertEquals(RdpDrive.STATUS_ACCESS_DENIED, response.status)
    }

    /** ⛔⛔ 共有フォルダの中に置かれた「外を指すリンク」も開かせない。 */
    @Test
    fun aSymlinkPointingOutOfTheShareIsRefused() {
        val outside = File(root.parentFile, "outside-target.txt")
        outside.writeText("secret")
        val link = File(root, "link.txt")
        runCatching { Files.createSymbolicLink(link.toPath(), outside.toPath()) }
            .onFailure { return } // symlink を作れない環境では検証をとばす

        val response = create("\\link.txt", disposition = FILE_OPEN)

        assertEquals(RdpDrive.STATUS_ACCESS_DENIED, response.status)
    }

    // --- 開く・読む・書く ---

    @Test
    fun anExistingFileIsOpenedAndReadBack() {
        File(root, "hello.txt").writeText("konnichiwa")

        val opened = create("\\hello.txt", disposition = FILE_OPEN)
        assertEquals(RdpDrive.STATUS_SUCCESS, opened.status)
        val fileId = le32(opened.payload, 0)
        assertEquals(FILE_OPENED, opened.payload[4].toInt())

        val read = drive.process(
            RdpDrive.Request(fileId, RdpDrive.IRP_MJ_READ, 0, readBody(length = 32, offset = 0))
        )!!
        assertEquals(RdpDrive.STATUS_SUCCESS, read.status)
        assertEquals("konnichiwa".length, le32(read.payload, 0))
        assertEquals("konnichiwa", String(read.payload, 4, le32(read.payload, 0)))
    }

    /** 相手が新しく作って書く経路 (Windows からのコピー) がそのまま端末のファイルになる。 */
    @Test
    fun theRemoteSideCanCreateAFileAndWriteIntoIt() {
        val created = create("\\new\\note.txt", disposition = FILE_OPEN_IF)
        assertEquals(RdpDrive.STATUS_SUCCESS, created.status)
        assertEquals(FILE_CREATED, created.payload[4].toInt())
        val fileId = le32(created.payload, 0)

        val payload = "written by the peer".toByteArray()
        val written = drive.process(
            RdpDrive.Request(fileId, RdpDrive.IRP_MJ_WRITE, 0, writeBody(offset = 0, data = payload))
        )!!
        assertEquals(RdpDrive.STATUS_SUCCESS, written.status)
        assertEquals(payload.size, le32(written.payload, 0))

        drive.process(RdpDrive.Request(fileId, RdpDrive.IRP_MJ_CLOSE, 0, ByteArray(32)))
        assertArrayEquals(payload, File(root, "new/note.txt").readBytes())
    }

    /** 途中から書き換えられる (相手はファイルの真ん中だけを直すことがある)。 */
    @Test
    fun aWriteAtAnOffsetOnlyReplacesThatPart() {
        File(root, "edit.txt").writeText("AAAAAAAA")
        val fileId = le32(create("\\edit.txt", disposition = FILE_OPEN).payload, 0)

        drive.process(
            RdpDrive.Request(fileId, RdpDrive.IRP_MJ_WRITE, 0, writeBody(offset = 4, data = "BBBB".toByteArray()))
        )
        drive.process(RdpDrive.Request(fileId, RdpDrive.IRP_MJ_CLOSE, 0, ByteArray(32)))

        assertEquals("AAAABBBB", File(root, "edit.txt").readText())
    }

    @Test
    fun openingAMissingFileFailsWithoutCreatingIt() {
        val response = create("\\nope.txt", disposition = FILE_OPEN)

        assertEquals(RdpDrive.STATUS_NO_SUCH_FILE, response.status)
        assertFalse(File(root, "nope.txt").exists())
    }

    // --- 一覧 ---

    /** 一覧は 1 通に 1 件。⚠ `.` と `..` も返す (Explorer はそれを前提に数える)。 */
    @Test
    fun theListingReturnsOneEntryPerRequestAndThenSaysThereAreNoMore() {
        File(root, "a.txt").writeText("a")
        File(root, "b.txt").writeText("b")
        val dirId = le32(create("", disposition = FILE_OPEN, directory = true).payload, 0)

        val names = mutableListOf<String>()
        var initial = true
        while (true) {
            val response = drive.process(
                RdpDrive.Request(dirId, RdpDrive.IRP_MJ_DIRECTORY_CONTROL, RdpDrive.IRP_MN_QUERY_DIRECTORY,
                    queryDirectoryBody(initialQuery = initial, path = "\\*"))
            )!!
            if (response.status != RdpDrive.STATUS_SUCCESS) {
                assertEquals(RdpDrive.STATUS_NO_MORE_FILES, response.status)
                break
            }
            initial = false
            names += entryName(response.payload)
        }

        assertEquals(listOf(".", "..", "a.txt", "b.txt"), names)
    }

    /** 検索パターンで絞られる (`*.txt` で開いた一覧に他の拡張子を混ぜない)。 */
    @Test
    fun theSearchPatternFiltersTheListing() {
        File(root, "keep.txt").writeText("x")
        File(root, "drop.bin").writeText("x")
        val dirId = le32(create("", disposition = FILE_OPEN, directory = true).payload, 0)

        val names = mutableListOf<String>()
        var initial = true
        while (true) {
            val response = drive.process(
                RdpDrive.Request(dirId, RdpDrive.IRP_MJ_DIRECTORY_CONTROL, RdpDrive.IRP_MN_QUERY_DIRECTORY,
                    queryDirectoryBody(initialQuery = initial, path = "\\*.txt"))
            )!!
            if (response.status != RdpDrive.STATUS_SUCCESS) break
            initial = false
            names += entryName(response.payload)
        }

        assertEquals(listOf("keep.txt"), names)
    }

    /** ⚠ フォルダの変更通知には答えない (本物と同じく保留する)。 */
    @Test
    fun theChangeNotificationIsHeldWithoutAnAnswer() {
        val dirId = le32(create("", disposition = FILE_OPEN, directory = true).payload, 0)

        val response = drive.process(
            RdpDrive.Request(dirId, RdpDrive.IRP_MJ_DIRECTORY_CONTROL,
                RdpDrive.IRP_MN_NOTIFY_CHANGE_DIRECTORY, ByteArray(32))
        )

        assertNull("変更通知に返事をしない", response)
    }

    // --- 名前を変える / 消す ---

    @Test
    fun theRemoteSideCanRenameAFile() {
        File(root, "before.txt").writeText("x")
        val fileId = le32(create("\\before.txt", disposition = FILE_OPEN).payload, 0)

        val response = drive.process(
            RdpDrive.Request(fileId, RdpDrive.IRP_MJ_SET_INFORMATION, 0, renameBody("\\after.txt"))
        )!!

        assertEquals(RdpDrive.STATUS_SUCCESS, response.status)
        assertTrue(File(root, "after.txt").exists())
        assertFalse(File(root, "before.txt").exists())
    }

    /** ⛔ 名前の変更でも外へは出せない。 */
    @Test
    fun aRenameOutOfTheShareIsRefused() {
        File(root, "before.txt").writeText("x")
        val fileId = le32(create("\\before.txt", disposition = FILE_OPEN).payload, 0)

        val response = drive.process(
            RdpDrive.Request(fileId, RdpDrive.IRP_MJ_SET_INFORMATION, 0, renameBody("\\..\\escaped.txt"))
        )!!

        assertEquals(RdpDrive.STATUS_ACCESS_DENIED, response.status)
        assertFalse(File(root.parentFile, "escaped.txt").exists())
    }

    /** 削除は「閉じるときに消す」印を付けて閉じる、という Windows のやり方で来る。 */
    @Test
    fun aFileMarkedForDeletionIsRemovedWhenItIsClosed() {
        File(root, "gone.txt").writeText("x")
        val fileId = le32(create("\\gone.txt", disposition = FILE_OPEN).payload, 0)

        drive.process(
            RdpDrive.Request(fileId, RdpDrive.IRP_MJ_SET_INFORMATION, 0, dispositionBody(delete = true))
        )
        assertTrue("閉じるまでは消さない", File(root, "gone.txt").exists())

        drive.process(RdpDrive.Request(fileId, RdpDrive.IRP_MJ_CLOSE, 0, ByteArray(32)))

        assertFalse(File(root, "gone.txt").exists())
    }

    // --- 情報 ---

    @Test
    fun theStandardInformationReportsTheSizeAndWhetherItIsAFolder() {
        File(root, "size.txt").writeText("0123456789")
        val fileId = le32(create("\\size.txt", disposition = FILE_OPEN).payload, 0)

        val response = drive.process(
            RdpDrive.Request(fileId, RdpDrive.IRP_MJ_QUERY_INFORMATION, 0, infoBody(FILE_STANDARD_INFORMATION))
        )!!

        assertEquals(RdpDrive.STATUS_SUCCESS, response.status)
        assertEquals(22, le32(response.payload, 0))
        assertEquals(10L, le64(response.payload, 12)) // EndOfFile
        assertEquals(0, response.payload[4 + 21].toInt()) // Directory = false
    }

    /** ⚠ 相手は空き容量を見てコピーを止める。0 を返し続けると「容量不足」で失敗する。 */
    @Test
    fun theVolumeSizeIsReported() {
        val response = drive.process(
            RdpDrive.Request(0, RdpDrive.IRP_MJ_QUERY_VOLUME_INFORMATION, 0, infoBody(FILE_FS_SIZE_INFORMATION))
        )!!

        assertEquals(RdpDrive.STATUS_SUCCESS, response.status)
        assertEquals(24, le32(response.payload, 0))
        assertTrue("総容量が 0 でない", le64(response.payload, 4) > 0)
    }

    @Test
    fun theVolumeIsNamedAfterTheShare() {
        val response = drive.process(
            RdpDrive.Request(0, RdpDrive.IRP_MJ_QUERY_VOLUME_INFORMATION, 0, infoBody(FILE_FS_VOLUME_INFORMATION))
        )!!

        assertEquals(RdpDrive.STATUS_SUCCESS, response.status)
        val labelBytes = le32(response.payload, 4 + 12)
        val label = String(response.payload, 4 + 17, labelBytes, Charsets.UTF_16LE)
        assertEquals("z2term", label)
    }

    /** ⚠ 知らない要求で例外を投げない (相手はエラーを受け取れる)。 */
    @Test
    fun anUnknownRequestIsAnsweredWithNotSupported() {
        val response = drive.process(RdpDrive.Request(0, 0x7F, 0, ByteArray(4)))

        assertNotNull(response)
        assertEquals(RdpDrive.STATUS_NOT_SUPPORTED, response!!.status)
    }

    // --- 要求の組み立て ---

    private fun create(
        path: String,
        disposition: Int,
        directory: Boolean = false,
    ): RdpDrive.Response {
        val name = path.toByteArray(Charsets.UTF_16LE) + byteArrayOf(0, 0)
        val body = ByteArrayOutputStream().apply {
            write(bytes32(0x0012_0089)) // DesiredAccess (読み書き相当)
            write(ByteArray(8)) // AllocationSize
            write(bytes32(0x80)) // FileAttributes
            write(bytes32(7)) // SharedAccess
            write(bytes32(disposition))
            write(bytes32(if (directory) 1 else 0x40)) // CreateOptions
            write(bytes32(name.size))
            write(name)
        }.toByteArray()
        return drive.process(RdpDrive.Request(0, RdpDrive.IRP_MJ_CREATE, 0, body))!!
    }

    private fun readBody(length: Int, offset: Long) = ByteArrayOutputStream().apply {
        write(bytes32(length))
        write(bytes64(offset))
        write(ByteArray(20))
    }.toByteArray()

    private fun writeBody(offset: Long, data: ByteArray) = ByteArrayOutputStream().apply {
        write(bytes32(data.size))
        write(bytes64(offset))
        write(ByteArray(20))
        write(data)
    }.toByteArray()

    private fun infoBody(infoClass: Int) = ByteArrayOutputStream().apply {
        write(bytes32(infoClass))
        write(bytes32(0))
        write(ByteArray(24))
    }.toByteArray()

    private fun renameBody(target: String) = ByteArrayOutputStream().apply {
        val name = target.toByteArray(Charsets.UTF_16LE)
        write(bytes32(FILE_RENAME_INFORMATION))
        write(bytes32(6 + name.size))
        write(ByteArray(24))
        write(byteArrayOf(0)) // ReplaceIfExists
        write(byteArrayOf(0)) // RootDirectory
        write(bytes32(name.size))
        write(name)
    }.toByteArray()

    private fun dispositionBody(delete: Boolean) = ByteArrayOutputStream().apply {
        write(bytes32(FILE_DISPOSITION_INFORMATION))
        write(bytes32(1))
        write(ByteArray(24))
        write(byteArrayOf(if (delete) 1 else 0))
    }.toByteArray()

    private fun queryDirectoryBody(initialQuery: Boolean, path: String) = ByteArrayOutputStream().apply {
        val name = path.toByteArray(Charsets.UTF_16LE)
        write(bytes32(FILE_BOTH_DIRECTORY_INFORMATION))
        write(byteArrayOf(if (initialQuery) 1 else 0))
        write(bytes32(name.size))
        write(ByteArray(23))
        write(name)
    }.toByteArray()

    /** FileBothDirectoryInformation の FileName を取り出す。 */
    private fun entryName(payload: ByteArray): String {
        val nameLength = le32(payload, 4 + 60)
        return String(payload, 4 + 93, nameLength, Charsets.UTF_16LE)
    }

    private fun le32(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)

    private fun le64(data: ByteArray, offset: Int): Long =
        (le32(data, offset).toLong() and 0xFFFFFFFFL) or
            ((le32(data, offset + 4).toLong() and 0xFFFFFFFFL) shl 32)

    private fun bytes32(value: Int) = ByteArray(4) { (value ushr (it * 8)).toByte() }

    private fun bytes64(value: Long) = ByteArray(8) { (value ushr (it * 8)).toByte() }

    private companion object {
        const val FILE_OPEN = 1
        const val FILE_OPEN_IF = 3
        const val FILE_OPENED = 1
        const val FILE_CREATED = 2
        const val FILE_BOTH_DIRECTORY_INFORMATION = 3
        const val FILE_STANDARD_INFORMATION = 5
        const val FILE_RENAME_INFORMATION = 10
        const val FILE_DISPOSITION_INFORMATION = 13
        const val FILE_FS_VOLUME_INFORMATION = 1
        const val FILE_FS_SIZE_INFORMATION = 3
    }
}
