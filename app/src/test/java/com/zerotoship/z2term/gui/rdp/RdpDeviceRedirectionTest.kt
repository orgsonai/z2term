package com.zerotoship.z2term.gui.rdp

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RdpDeviceRedirectionTest {
    private val sent = mutableListOf<ByteArray>()

    private fun rdpdr() = RdpDeviceRedirection(sendMessage = sent::add, clientName = "Z2")

    private fun sharingRdpdr(name: String = "z2term"): RdpDeviceRedirection {
        val root: File = Files.createTempDirectory("rdpdr-share").toFile()
        root.deleteOnExit()
        return RdpDeviceRedirection(
            sendMessage = sent::add,
            clientName = "Z2",
            drive = RdpDrive(root = root, shareName = name),
        )
    }

    /** 相手の版と id をそのまま返し、続けて名前を出す (名前が来るまで相手は次へ進まない)。 */
    @Test
    fun theAnnounceIsAnsweredWithTheSameIdAndThenTheName() {
        val rdpdr = rdpdr()

        rdpdr.acceptChannelChunk(chunk(serverAnnounce(major = 1, minor = 13, clientId = 0x2A)))

        assertEquals(2, sent.size)
        val reply = sent[0]
        assertEquals(0x4472, le16(reply, 0)) // RDPDR_CTYP_CORE
        assertEquals(0x4343, le16(reply, 2)) // PAKID_CORE_CLIENTID_CONFIRM
        assertEquals(0x2A, le32(reply, 8)) // 相手がくれた id をそのまま返す

        val name = sent[1]
        assertEquals(0x434E, le16(name, 2)) // PAKID_CORE_CLIENT_NAME
        assertEquals(1, le32(name, 4)) // UnicodeFlag
    }

    /** ⚠ 相手より新しい版を名乗らない。 */
    @Test
    fun anOlderPeerIsAnsweredWithItsOwnVersion() {
        val rdpdr = rdpdr()

        rdpdr.acceptChannelChunk(chunk(serverAnnounce(major = 1, minor = 5, clientId = 1)))

        assertEquals(5, le16(sent[0], 6)) // VersionMinor = 相手に合わせる
    }

    /**
     * ⛔⛔ **公開するデバイスは 0 件。** ここを開くのは音を鳴らすためだけで、
     * ドライブもプリンタもスマートカードも渡さない。
     */
    @Test
    fun nothingOfOursIsEverShared() {
        val rdpdr = rdpdr()
        rdpdr.acceptChannelChunk(chunk(serverAnnounce(major = 1, minor = 13, clientId = 1)))
        sent.clear()

        rdpdr.acceptChannelChunk(chunk(serverCapability()))

        val caps = sent[0]
        assertEquals(0x4350, le16(caps, 2)) // PAKID_CORE_CLIENT_CAPABILITY
        assertEquals(1, le16(caps, 4)) // 一般 (GENERAL) だけ
        assertEquals(0x0001, le16(caps, 8)) // CAP_GENERAL_TYPE
        assertEquals(0x000C0001, le32(caps, 24)) // protocolMajor 1 / protocolMinor 12
        assertEquals(0, le32(caps, 28)) // ioCode1: I/O は 1 つも受け付けない
        assertEquals(0, le32(caps, 32)) // ioCode2
        assertEquals(0, le32(caps, 48)) // SpecialTypeDeviceCap

        val list = sent[1]
        assertEquals(0x4441, le16(list, 2)) // PAKID_CORE_DEVICELIST_ANNOUNCE
        assertEquals(0, le32(list, 4)) // DeviceCount = 0
    }

    /** ⭐ 一覧は 1 度だけ。Client ID Confirm が後から来ても二重に出さない。 */
    @Test
    fun theEmptyDeviceListIsSentOnlyOnce() {
        val rdpdr = rdpdr()
        rdpdr.acceptChannelChunk(chunk(serverAnnounce(major = 1, minor = 13, clientId = 1)))
        rdpdr.acceptChannelChunk(chunk(serverCapability()))
        sent.clear()

        rdpdr.acceptChannelChunk(
            chunk(header(0x4343) + le16Bytes(1) + le16Bytes(13) + le32Bytes(1)),
        )

        assertTrue("一覧を二度出さない", sent.isEmpty())
    }

    /** ⚠ 任意チャネル。壊れた 1 通でデスクトップ接続まで落とさない。 */
    @Test
    fun anInvalidChunkDoesNotBringDownTheSession() {
        val rdpdr = rdpdr()

        assertEquals(false, rdpdr.acceptChannelChunkSafely(byteArrayOf(1, 2, 3)))
        rdpdr.acceptChannelChunkSafely(chunk(serverAnnounce(major = 1, minor = 13, clientId = 1)))
        assertTrue("reset 後も名乗り直せる", sent.isNotEmpty())
    }

    /**
     * ⭐ フォルダを共有するときは **ドライブを名乗ってから**一覧に出す。
     * ここで CAP_DRIVE_TYPE と ioCode1 を出し忘れると、一覧に出しても相手は
     * I/O を 1 つも送ってこない (画面は繋がるのにフォルダだけ空に見える)。
     */
    @Test
    fun aSharedFolderIsAnnouncedAsADrive() {
        val rdpdr = sharingRdpdr()
        rdpdr.acceptChannelChunk(chunk(serverAnnounce(major = 1, minor = 13, clientId = 1)))
        sent.clear()

        rdpdr.acceptChannelChunk(chunk(serverCapability()))

        val caps = sent[0]
        assertEquals(2, le16(caps, 4)) // GENERAL + DRIVE
        assertEquals(0x0000FFFF, le32(caps, 28)) // ioCode1
        assertEquals(0x0004, le16(caps, 52)) // CAP_DRIVE_TYPE
        assertEquals(8, le16(caps, 54)) // capability header だけ
        assertEquals(2, le32(caps, 56)) // DRIVE_CAPABILITY_VERSION_02

        val list = sent[1]
        assertEquals(0x4441, le16(list, 2)) // PAKID_CORE_DEVICELIST_ANNOUNCE
        assertEquals(1, le32(list, 4)) // DeviceCount
        assertEquals(0x00000014, le32(list, 8)) // RDPDR_DTYP_FILESYSTEM
        assertEquals(1, le32(list, 12)) // DeviceId
        assertEquals("z2term", String(list, 16, 6, Charsets.US_ASCII))
        assertEquals(7, le32(list, 24)) // DeviceDataLength ("z2term" + NUL)
        assertEquals("z2term", String(list, 28, 6, Charsets.US_ASCII))

        rdpdr.close()
    }

    /**
     * ⚠ 共有名は相手の一覧に ASCII で載る。日本語のフォルダ名をそのまま出すと名前が
     * 壊れるので、置ける文字だけに倒す (中身のファイル名は UTF-16 なので影響しない)。
     */
    @Test
    fun aShareNameIsReducedToWhatThePeerCanShow() {
        assertEquals("_____", RdpDeviceRedirection.asciiShareName("共有フォルダ".take(5)))
        assertEquals("my_share", RdpDeviceRedirection.asciiShareName("my share"))
        assertEquals("a_b", RdpDeviceRedirection.asciiShareName("a\\b"))
        assertEquals("z2term", RdpDeviceRedirection.asciiShareName("   "))
    }

    /** ⚠ 名前が 8 バイトを超えても PreferredDosName は 7 文字 + NUL に収める。 */
    @Test
    fun aLongShareNameIsTruncatedInThePreferredDosName() {
        val rdpdr = sharingRdpdr(name = "verylongsharename")
        rdpdr.acceptChannelChunk(chunk(serverAnnounce(major = 1, minor = 13, clientId = 1)))
        sent.clear()

        rdpdr.acceptChannelChunk(chunk(serverCapability()))

        val list = sent[1]
        assertEquals("verylon", String(list, 16, 7, Charsets.US_ASCII))
        assertEquals(0, list[23].toInt()) // NUL 終端
        assertEquals(18, le32(list, 24)) // 名前ぜんぶ + NUL
        rdpdr.close()
    }

    private fun header(packetId: Int) = le16Bytes(0x4472) + le16Bytes(packetId)

    private fun serverAnnounce(major: Int, minor: Int, clientId: Int): ByteArray =
        header(0x496E) + le16Bytes(major) + le16Bytes(minor) + le32Bytes(clientId)

    private fun serverCapability(): ByteArray = ByteArrayOutputStream().apply {
        write(header(0x5350))
        write(le16Bytes(1)) // numCapabilities
        write(le16Bytes(0)) // padding
        write(le16Bytes(0x0001)) // CAP_GENERAL_TYPE
        write(le16Bytes(44))
        write(le32Bytes(2))
        write(ByteArray(36))
    }.toByteArray()

    private fun chunk(body: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write(le32Bytes(body.size))
        write(le32Bytes(3)) // FIRST | LAST
        write(body)
    }.toByteArray()

    private fun le16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun le32(data: ByteArray, offset: Int): Int =
        le16(data, offset) or (le16(data, offset + 2) shl 16)

    private fun le16Bytes(value: Int) = byteArrayOf(value.toByte(), (value ushr 8).toByte())

    private fun le32Bytes(value: Int) = ByteArray(4) { (value ushr (it * 8)).toByte() }
}
