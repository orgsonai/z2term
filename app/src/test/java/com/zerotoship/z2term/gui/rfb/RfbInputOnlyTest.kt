package com.zerotoship.z2term.gui.rfb

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class RfbInputOnlyTest {
    @Test fun inputOnlyNeverRequestsPixelsButSendsKeysPointerAndClipboard() {
        ServerSocket(0).use { listener ->
            val received = CompletableFuture<List<Int>>()
            val server = Thread {
                try {
                    listener.accept().use { socket ->
                        socket.soTimeout = 3000
                        val input = DataInputStream(socket.getInputStream())
                        val out = DataOutputStream(socket.getOutputStream())
                        out.write("RFB 003.008\n".toByteArray()); out.flush()
                        input.readFully(ByteArray(12))
                        out.write(byteArrayOf(1, 1)); out.flush()
                        assertEquals(1, input.readUnsignedByte())
                        out.writeInt(0); out.flush()
                        assertEquals(1, input.readUnsignedByte())
                        out.writeShort(640); out.writeShort(480); out.write(ByteArray(16)); out.writeInt(0); out.flush()
                        assertEquals(0, input.readUnsignedByte()); input.readFully(ByteArray(19))
                        assertEquals(2, input.readUnsignedByte()); input.readByte()
                        repeat(input.readUnsignedShort()) { input.readInt() }
                        val types = mutableListOf<Int>()
                        repeat(4) {
                            val type = input.readUnsignedByte(); types += type
                            when (type) {
                                4 -> { input.readBoolean(); input.readShort(); assertEquals(65, input.readInt()) }
                                5 -> { assertEquals(1, input.readUnsignedByte()); assertEquals(100, input.readUnsignedShort()); assertEquals(200, input.readUnsignedShort()) }
                                6 -> { input.readFully(ByteArray(3)); val text = ByteArray(input.readInt()).also { input.readFully(it) }; assertEquals("example", String(text)) }
                                else -> fail("Unexpected message, including framebuffer request: $type")
                            }
                        }
                        received.complete(types)
                    }
                } catch (e: Throwable) { received.completeExceptionally(e) }
            }.apply { isDaemon = true; start() }
            val client = RfbClient(port = listener.localPort, inputOnly = true)
            try {
                client.connect(3000)
                assertNull(client.frame)
                assertEquals(640, client.width)
                client.sendKeyEvent(65, true); client.sendKeyEvent(65, false)
                client.sendPointerEvent(1, 100, 200); client.sendClipboardText("example")
                assertEquals(listOf(4, 4, 5, 6), received.get(5, TimeUnit.SECONDS))
            } finally { client.close(); server.join(1000) }
        }
    }
}
