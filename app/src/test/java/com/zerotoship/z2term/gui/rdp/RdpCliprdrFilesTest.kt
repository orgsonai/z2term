package com.zerotoship.z2term.gui.rdp

import com.zerotoship.z2term.gui.ClipboardFiles
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RdpCliprdrFilesTest {
    private val sent = mutableListOf<ByteArray>()
    private val sink = RecordingSink()

    private class RecordingSink : ClipboardFiles.Sink {
        val started = mutableListOf<ClipboardFiles.Entry>()
        val body = ByteArrayOutputStream()
        val finished = mutableListOf<Boolean>()
        var accept = true

        override fun begin(entry: ClipboardFiles.Entry): Boolean {
            if (!accept) return false
            started += entry
            return true
        }

        override fun write(data: ByteArray) = body.write(data)
        override fun finish(complete: Boolean) { finished += complete }
    }

    private val offered = mutableListOf<List<ClipboardFiles.Entry>>()
    private var receivedCount = 0

    private fun cliprdr(withSink: Boolean = true) = RdpCliprdr(
        sendMessage = sent::add,
        onRemoteText = { },
        fileSink = if (withSink) sink else null,
        onFilesOffered = offered::add,
        onFilesReceived = { receivedCount++ },
    )

    /** ⚠ 受け取れないものを宣言しない: sink が無いならファイルの対応も宣言しない。 */
    @Test
    fun fileCapabilityIsOnlyAdvertisedWithASink() {
        cliprdr(withSink = true).start()
        val withFiles = le32(sent.first(), 8 + 12)
        sent.clear()
        cliprdr(withSink = false).start()
        val withoutFiles = le32(sent.first(), 8 + 12)

        // LONG_NAMES | STREAM_FILECLIP | NO_FILE_PATHS | CAN_LOCK_CLIPDATA
        assertEquals(0x02 or 0x04 or 0x08 or 0x10, withFiles)
        assertEquals(0x02, withoutFiles)
    }

    /**
     * ⭐ ファイルがあればファイルを優先する。Windows はファイルをコピーすると置き場の文字列も
     * 一緒に announce するので、テキストを先に取ると**中身の代わりにパスが届く**。
     */
    @Test
    fun filesWinOverTextInTheFormatList() {
        val clip = cliprdr()
        clip.start()
        sent.clear()

        clip.acceptChannelChunk(chunk(formatList(
            13 to "", // CF_UNICODETEXT
            0xC104 to "FileGroupDescriptorW",
        )))

        val request = sent.last()
        assertEquals(0x0004, le16(request, 0)) // CB_FORMAT_DATA_REQUEST
        assertEquals(0xC104, le32(request, 8))
    }

    /**
     * ⛔⛔ **コピーしただけでは中身を 1 バイトも取り寄せない。** 相手のコピーは相手の中だけで
     * 完結することも多く、そのたびに端末へ落としていたら通信も置き場も浪費する。
     */
    @Test
    fun copyingOnThePeerOnlyOffersTheList() {
        val clip = cliprdr()
        clip.start()
        clip.acceptChannelChunk(chunk(formatList(0xC104 to "FileGroupDescriptorW")))
        sent.clear()

        clip.acceptChannelChunk(chunk(message(0x0005, 0x0001, descriptors(descriptor("note.txt", 6)))))

        assertEquals(listOf(listOf(ClipboardFiles.Entry("note.txt", 6))), offered)
        assertTrue("要求は 1 つも出さない", sent.isEmpty())
        assertTrue("受け皿にも触らない", sink.started.isEmpty())
    }

    /** 相手がコピーし直したら、出していた一覧は取り下げる。 */
    @Test
    fun aNewFormatListWithdrawsTheOffer() {
        val clip = cliprdr()
        clip.start()
        clip.acceptChannelChunk(chunk(formatList(0xC104 to "FileGroupDescriptorW")))
        clip.acceptChannelChunk(chunk(message(0x0005, 0x0001, descriptors(descriptor("note.txt", 6)))))

        clip.acceptChannelChunk(chunk(formatList(13 to "")))

        assertEquals(emptyList<ClipboardFiles.Entry>(), offered.last())
    }

    @Test
    fun descriptorsAreFetchedChunkByChunkAndWrittenToTheSink() {
        val clip = cliprdr()
        clip.start()
        clip.acceptChannelChunk(chunk(formatList(0xC104 to "FileGroupDescriptorW")))
        clip.acceptChannelChunk(chunk(message(0x0005, 0x0001, descriptors(
            descriptor("note.txt", 6),
            descriptor("sub\\deep.bin", 2),
        ))))
        sent.clear()

        // ⭐ ここで初めて中身が流れる。
        clip.receiveOfferedFiles()

        val first = sent.single()
        assertEquals(0x0008, le16(first, 0)) // CB_FILECONTENTS_REQUEST
        val streamId = le32(first, 8)
        assertEquals(0, le32(first, 12)) // lindex
        assertEquals(0x02, le32(first, 16)) // FILECONTENTS_RANGE
        assertEquals(0, le32(first, 20)) // 位置は先頭から
        assertEquals(6, le32(first, 28)) // 残り 6 バイトだけ要求する
        assertEquals(listOf(ClipboardFiles.Entry("note.txt", 6)), sink.started)
        sent.clear()

        clip.acceptChannelChunk(chunk(message(0x0009, 0x0001, le32Bytes(streamId) + "abc".toByteArray())))
        assertEquals("続きがあるので、まだ次のファイルへ行かない", 0, sink.finished.size)
        assertEquals(3, le32(sent.single(), 20)) // 続きの位置
        assertEquals(3, le32(sent.single(), 28)) // 残り 3 バイト
        val secondStream = le32(sent.single(), 8)
        sent.clear()

        clip.acceptChannelChunk(chunk(message(0x0009, 0x0001, le32Bytes(secondStream) + "def".toByteArray())))

        assertArrayEquals("abcdef".toByteArray(), sink.body.toByteArray())
        assertEquals(listOf(true), sink.finished)
        // ⚠ 2 件目の名前は葉だけにする (フォルダごとコピーされると `sub\deep.bin` で届く)。
        assertEquals("deep.bin", sink.started[1].name)
    }

    /** フォルダは飛ばす (中身の一覧は別に来ないので、開いても空になる)。 */
    @Test
    fun directoriesAreSkipped() {
        val clip = cliprdr()
        clip.start()
        clip.acceptChannelChunk(chunk(formatList(0xC104 to "FileGroupDescriptorW")))
        sent.clear()

        clip.acceptChannelChunk(chunk(message(0x0005, 0x0001, descriptors(
            descriptor("folder", 0, directory = true),
            descriptor("real.txt", 1),
        ))))
        clip.receiveOfferedFiles()

        assertEquals(listOf("real.txt"), sink.started.map { it.name })
        assertEquals(listOf(listOf(ClipboardFiles.Entry("real.txt", 1))), offered)
    }

    /** ⚠ 空の応答は「もう出せない」。足りていなければ未完了として畳む。 */
    @Test
    fun aTruncatedTransferIsFinishedAsIncomplete() {
        val clip = cliprdr()
        clip.start()
        clip.acceptChannelChunk(chunk(formatList(0xC104 to "FileGroupDescriptorW")))
        clip.acceptChannelChunk(chunk(message(0x0005, 0x0001, descriptors(descriptor("big.bin", 10)))))
        clip.receiveOfferedFiles()
        val streamId = le32(sent.last(), 8)

        clip.acceptChannelChunk(chunk(message(0x0009, 0x0001, le32Bytes(streamId))))

        assertEquals(listOf(false), sink.finished)
        assertEquals("終わったことは伝える (帯を消すため)", 1, receivedCount)
    }

    @Test
    fun aRefusedTransferIsFinishedAsIncomplete() {
        val clip = cliprdr()
        clip.start()
        clip.acceptChannelChunk(chunk(formatList(0xC104 to "FileGroupDescriptorW")))
        clip.acceptChannelChunk(chunk(message(0x0005, 0x0001, descriptors(descriptor("big.bin", 10)))))
        clip.receiveOfferedFiles()
        val streamId = le32(sent.last(), 8)

        clip.acceptChannelChunk(chunk(message(0x0009, 0x0002, le32Bytes(streamId))))

        assertEquals(listOf(false), sink.finished)
    }

    @Test
    fun aSinkThatRefusesAFileMovesOnToTheNext() {
        val clip = cliprdr()
        sink.accept = false
        clip.start()
        clip.acceptChannelChunk(chunk(formatList(0xC104 to "FileGroupDescriptorW")))
        sent.clear()

        clip.acceptChannelChunk(chunk(message(0x0005, 0x0001, descriptors(descriptor("skip.bin", 4)))))
        clip.receiveOfferedFiles()

        assertTrue("取り寄せを始めない", sent.isEmpty())
        assertTrue(sink.finished.isEmpty())
    }

    /** こちらのファイルを渡す側: 一覧を返し、要求された範囲だけ読んで返す。 */
    @Test
    fun localFilesAreOfferedAndServedByRange() {
        val clip = cliprdr()
        clip.start()
        val source = object : ClipboardFiles.Source {
            override val entries = listOf(ClipboardFiles.Entry("photo.png", 5))
            override fun read(index: Int, position: Long, length: Int): ByteArray =
                "12345".toByteArray().copyOfRange(position.toInt(), position.toInt() + length)
        }
        clip.announceLocalFiles(source)

        val list = sent.last()
        assertEquals(0x0002, le16(list, 0)) // CB_FORMAT_LIST
        // ⭐ 一覧 (FileGroupDescriptorW) と中身 (FileContents) が**並んで**初めて相手は貼り付けられる。
        val names = list.copyOfRange(8, list.size).toString(Charsets.UTF_16LE)
        assertTrue(names.contains("FileGroupDescriptorW"))
        assertTrue(names.contains("FileContents"))
        val formatId = le32(list, 8)
        sent.clear()

        clip.acceptChannelChunk(chunk(message(0x0004, 0, le32Bytes(formatId))))
        val response = sent.single()
        assertEquals(0x0005, le16(response, 0))
        assertEquals(1, le32(response, 8)) // cItems
        assertEquals(5, le32(response, 8 + 4 + 68)) // fileSizeLow
        sent.clear()

        clip.acceptChannelChunk(chunk(message(0x0008, 0, ByteArrayOutputStream().apply {
            write(le32Bytes(77)) // streamId
            write(le32Bytes(0)) // lindex
            write(le32Bytes(0x02)) // RANGE
            write(le32Bytes(1)) // 位置
            write(le32Bytes(0))
            write(le32Bytes(3)) // 3 バイト要求
        }.toByteArray())))

        val contents = sent.single()
        assertEquals(0x0009, le16(contents, 0))
        assertEquals(0x0001, le16(contents, 2)) // CB_RESPONSE_OK
        assertEquals(77, le32(contents, 8))
        assertArrayEquals("234".toByteArray(), contents.copyOfRange(12, contents.size))
    }

    /** Windows の read は 64 KiB より大きい。短く丸めると Windows は EOF と見なす。 */
    @Test
    fun aLargeRequestedRangeIsReturnedAtTheRequestedLength() {
        val requested = 128 * 1024
        val clip = cliprdr()
        clip.start()
        clip.announceLocalFiles(object : ClipboardFiles.Source {
            override val entries = listOf(ClipboardFiles.Entry("large.bin", requested.toLong()))
            override fun read(index: Int, position: Long, length: Int): ByteArray =
                ByteArray(length) { ((position + it) and 0xFF).toByte() }
        })
        sent.clear()

        clip.acceptChannelChunk(chunk(message(0x0008, 0, ByteArrayOutputStream().apply {
            write(le32Bytes(91))
            write(le32Bytes(0))
            write(le32Bytes(0x02)) // RANGE
            write(le32Bytes(0))
            write(le32Bytes(0))
            write(le32Bytes(requested))
        }.toByteArray())))

        val response = sent.single()
        assertEquals(0x0001, le16(response, 2))
        assertEquals(requested + 4, le32(response, 4)) // streamId + requested bytes
        assertEquals(requested + 12, response.size)
    }

    /**
     * ⭐ ［受け取る］を押すまで取りに行かない以上、それまで相手に持っていてもらう必要がある。
     * 押されて受け取り切ったら手放させる ([MS-RDPECLIP] Lock / Unlock Clipboard Data)。
     */
    @Test
    fun theOfferIsLockedUntilItHasBeenReceived() {
        val clip = cliprdr()
        clip.start()
        clip.acceptChannelChunk(chunk(peerCaps(canLock = true)))
        clip.acceptChannelChunk(chunk(formatList(0xC104 to "FileGroupDescriptorW")))
        sent.clear()

        clip.acceptChannelChunk(chunk(message(0x0005, 0x0001, descriptors(descriptor("note.txt", 3)))))

        val lock = sent.single()
        assertEquals(0x000A, le16(lock, 0)) // CB_LOCK_CLIPDATA
        val clipDataId = le32(lock, 8)
        sent.clear()

        clip.receiveOfferedFiles()
        val request = sent.single()
        assertEquals(0x0008, le16(request, 0))
        assertEquals(28, le32(request, 4)) // 24 + clipDataId
        assertEquals(clipDataId, le32(request, 8 + 24))
        sent.clear()

        clip.acceptChannelChunk(
            chunk(message(0x0009, 0x0001, le32Bytes(le32(request, 8)) + "abc".toByteArray())),
        )

        val unlock = sent.single()
        assertEquals(0x000B, le16(unlock, 0)) // CB_UNLOCK_CLIPDATA
        assertEquals(clipDataId, le32(unlock, 8))
    }

    /** ⚠ 宣言していない相手へ Lock を送らない (知らない PDU で切られる)。 */
    @Test
    fun aPeerThatCannotLockIsNeverSentOne() {
        val clip = cliprdr()
        clip.start()
        clip.acceptChannelChunk(chunk(peerCaps(canLock = false)))
        clip.acceptChannelChunk(chunk(formatList(0xC104 to "FileGroupDescriptorW")))
        sent.clear()

        clip.acceptChannelChunk(chunk(message(0x0005, 0x0001, descriptors(descriptor("note.txt", 3)))))
        assertTrue("Lock は出さない", sent.isEmpty())

        clip.receiveOfferedFiles()
        assertEquals(24, le32(sent.single(), 4)) // clipDataId を付けない 24 バイトのまま
    }

    /** ✕ で閉じたら、相手に抱えさせていたものも手放させる。 */
    @Test
    fun dismissingTheOfferReleasesTheLock() {
        val clip = cliprdr()
        clip.start()
        clip.acceptChannelChunk(chunk(peerCaps(canLock = true)))
        clip.acceptChannelChunk(chunk(formatList(0xC104 to "FileGroupDescriptorW")))
        clip.acceptChannelChunk(chunk(message(0x0005, 0x0001, descriptors(descriptor("note.txt", 3)))))
        val clipDataId = le32(sent.single { le16(it, 0) == 0x000A }, 8)
        sent.clear()

        clip.dismissOfferedFiles()

        val unlock = sent.single()
        assertEquals(0x000B, le16(unlock, 0))
        assertEquals(clipDataId, le32(unlock, 8))
    }

    /**
     * ⭐ **末尾は要求より短く返すのが EOF の伝え方。** 相手は最後まで同じ大きさで要求してくるので、
     * 残りが足りないことを理由に FAIL を返すと、最後の端数だけ落ちて転送全体が失敗する。
     */
    @Test
    fun theLastPartialChunkIsServedInsteadOfRefused() {
        val size = 100
        val clip = cliprdr()
        clip.start()
        clip.announceLocalFiles(object : ClipboardFiles.Source {
            override val entries = listOf(ClipboardFiles.Entry("tail.bin", size.toLong()))
            override fun read(index: Int, position: Long, length: Int) = ByteArray(length)
        })
        sent.clear()

        // 残り 40 バイトしか無いところへ、相手はいつもどおり 256 バイトを要求してくる。
        clip.acceptChannelChunk(chunk(fileContentsRequest(streamId = 7, position = 60, requested = 256)))

        val response = sent.single()
        assertEquals(0x0009, le16(response, 0))
        assertEquals(0x0001, le16(response, 2)) // CB_RESPONSE_OK — FAIL ではない
        assertEquals(40 + 4, le32(response, 4)) // streamId + 残り 40 バイト
    }

    /** ⚠ 位置がファイルの外なら、そこで初めて断る。 */
    @Test
    fun aRequestPastTheEndOfTheFileIsRefused() {
        val clip = cliprdr()
        clip.start()
        clip.announceLocalFiles(object : ClipboardFiles.Source {
            override val entries = listOf(ClipboardFiles.Entry("tail.bin", 100))
            override fun read(index: Int, position: Long, length: Int) = ByteArray(length)
        })
        sent.clear()

        clip.acceptChannelChunk(chunk(fileContentsRequest(streamId = 7, position = 101, requested = 8)))

        assertEquals(0x0002, le16(sent.single(), 2)) // CB_RESPONSE_FAIL
    }

    /**
     * ⚠ **1 回のチャネル書き込みに PDU が 2 つ入っていることがある。**
     * 先頭だけ見て残りを捨てると、応答を取りこぼしたうえ通ごと落とす。
     */
    @Test
    fun twoMessagesInOneChunkAreBothHandled() {
        val clip = cliprdr()
        clip.start()
        sent.clear()

        clip.acceptChannelChunk(
            chunk(
                formatList(0xC104 to "FileGroupDescriptorW") +
                    message(0x0005, 0x0001, descriptors(descriptor("note.txt", 6))),
            ),
        )

        // 1 つ目で Format List Response と Format Data Request、2 つ目で一覧が出る。
        assertEquals(listOf(listOf(ClipboardFiles.Entry("note.txt", 6))), offered)
        assertEquals(0x0003, le16(sent.first(), 0)) // CB_FORMAT_LIST_RESPONSE
    }

    /** Clipboard の破損 1 通でデスクトップ接続まで落とさない。 */
    @Test
    fun anInvalidClipboardChunkOnlyResetsClipboard() {
        val clip = cliprdr()

        assertFalse(clip.acceptChannelChunkSafely(byteArrayOf(1, 2, 3)))

        // reset 後の正しい通は再び処理できる。
        assertTrue(clip.acceptChannelChunkSafely(chunk(message(0x0001, 0, byteArrayOf()))))
        assertTrue(sent.isNotEmpty())
    }

    @Test
    fun aRequestForAnUnknownFileFails() {
        val clip = cliprdr()
        clip.start()
        sent.clear()

        clip.acceptChannelChunk(chunk(message(0x0008, 0, ByteArray(24))))

        assertEquals(0x0009, le16(sent.single(), 0))
        assertEquals(0x0002, le16(sent.single(), 2)) // CB_RESPONSE_FAIL
        assertFalse(sent.single().size > 12)
    }

    private fun formatList(vararg formats: Pair<Int, String>): ByteArray {
        val body = ByteArrayOutputStream().apply {
            formats.forEach { (id, name) ->
                write(le32Bytes(id))
                write((name + "\u0000").toByteArray(Charsets.UTF_16LE))
            }
        }.toByteArray()
        return message(0x0002, 0, body)
    }

    /** 相手からの CLIPRDR_FILECONTENTS_REQUEST (RANGE)。 */
    private fun fileContentsRequest(streamId: Int, position: Long, requested: Int): ByteArray = message(
        0x0008,
        0,
        ByteArrayOutputStream().apply {
            write(le32Bytes(streamId))
            write(le32Bytes(0)) // lindex
            write(le32Bytes(0x02)) // RANGE
            write(le32Bytes(position.toInt()))
            write(le32Bytes((position ushr 32).toInt()))
            write(le32Bytes(requested))
        }.toByteArray(),
    )

    /** 相手の Clipboard Capabilities PDU。ロックを名乗るかどうかだけを変える。 */
    private fun peerCaps(canLock: Boolean): ByteArray = message(
        0x0007,
        0,
        ByteArrayOutputStream().apply {
            write(le16Bytes(1)) // cCapabilitiesSets
            write(le16Bytes(0)) // pad
            write(le16Bytes(0x0001)) // CB_CAPSTYPE_GENERAL
            write(le16Bytes(12))
            write(le32Bytes(2)) // CB_CAPS_VERSION_2
            write(le32Bytes(0x02 or if (canLock) 0x10 else 0))
        }.toByteArray(),
    )

    private fun descriptors(vararg items: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write(le32Bytes(items.size))
        items.forEach { write(it) }
    }.toByteArray()

    private fun descriptor(name: String, size: Long, directory: Boolean = false): ByteArray =
        ByteArrayOutputStream().apply {
            write(le32Bytes(0x40 or 0x04))
            write(ByteArray(32))
            write(le32Bytes(if (directory) 0x10 else 0x80))
            write(ByteArray(16))
            write(ByteArray(8))
            write(le32Bytes((size ushr 32).toInt()))
            write(le32Bytes(size.toInt()))
            val field = ByteArray(520)
            name.toByteArray(Charsets.UTF_16LE).copyInto(field)
            write(field)
        }.toByteArray()

    private fun descriptor(name: String, size: Int, directory: Boolean = false) =
        descriptor(name, size.toLong(), directory)

    private fun message(type: Int, flags: Int, body: ByteArray): ByteArray =
        le16Bytes(type) + le16Bytes(flags) + le32Bytes(body.size) + body

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
