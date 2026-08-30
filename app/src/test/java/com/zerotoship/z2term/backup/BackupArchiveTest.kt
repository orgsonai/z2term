package com.zerotoship.z2term.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class BackupArchiveTest {

    private val manifestV2 = """{"format":2,"encrypted":true}""".toByteArray()
    private val payload = linkedMapOf(
        "settings.json" to "token=secret-setting".toByteArray(),
        "snippets.json" to "deploy --password secret-snippet".toByteArray(),
        "macros/deploy.sh" to "export API_TOKEN=secret-macro".toByteArray(),
        "ime_history.json" to "secret-learned-text".toByteArray(),
        "ssh.json" to "privateKey=secret-key".toByteArray(),
    )

    @Test
    fun format2LeavesOnlyManifestAndEncryptedPayloadOutside() {
        val bytes = write(manifestV2, payload, "correct horse")
        val outer = BackupArchive.readOuter(bytes)!!

        assertEquals(setOf(BackupArchive.MANIFEST, BackupArchive.PAYLOAD_ENC), outer.keys)
        assertArrayEquals(manifestV2, outer.getValue(BackupArchive.MANIFEST))

        // SSH だけでなく、設定・スニペット・マクロ・IME 学習履歴も外側には平文で残らない。
        val raw = String(bytes, Charsets.ISO_8859_1)
        payload.values.forEach { value ->
            assertFalse(raw.contains(String(value)))
        }
    }

    @Test
    fun format2RestoresEveryPayloadEntryAfterDecrypting() {
        val bytes = write(manifestV2, payload, "correct horse")
        val outer = BackupArchive.readOuter(bytes)!!
        val restored = BackupArchive.payloadForImport(
            outer,
            BackupArchive.FORMAT_ENCRYPTED_PAYLOAD,
            "correct horse",
        )!!

        assertEquals(payload.keys, restored.keys)
        payload.forEach { (name, value) -> assertArrayEquals(value, restored.getValue(name)) }
    }

    @Test
    fun format2RejectsWrongOrEmptyPassphraseBeforeReturningEntries() {
        val outer = BackupArchive.readOuter(write(manifestV2, payload, "right"))!!

        assertNull(
            BackupArchive.payloadForImport(
                outer,
                BackupArchive.FORMAT_ENCRYPTED_PAYLOAD,
                "wrong",
            ),
        )
        assertNull(
            BackupArchive.payloadForImport(
                outer,
                BackupArchive.FORMAT_ENCRYPTED_PAYLOAD,
                "",
            ),
        )
    }

    @Test
    fun format1FlatBackupRemainsReadable() {
        val manifest = """{"format":1,"encrypted":false}""".toByteArray()
        val bytes = write(manifest, payload, null)
        val outer = BackupArchive.readOuter(bytes)!!
        val restored = BackupArchive.payloadForImport(
            outer,
            BackupArchive.FORMAT_FLAT,
            "",
        )!!

        assertTrue(BackupArchive.PAYLOAD_ENC !in outer)
        assertEquals(payload.keys, restored.keys)
        payload.forEach { (name, value) -> assertArrayEquals(value, restored.getValue(name)) }
    }

    @Test
    fun unknownFormatAndExtraOuterEntryAreRejected() {
        val bytes = write(manifestV2, payload, "pass")
        val outer = BackupArchive.readOuter(bytes)!!

        assertNull(BackupArchive.payloadForImport(outer, 99, "pass"))
        assertNull(
            BackupArchive.payloadForImport(
                outer + ("settings.json" to "visible".toByteArray()),
                BackupArchive.FORMAT_ENCRYPTED_PAYLOAD,
                "pass",
            ),
        )
    }

    private fun write(
        manifest: ByteArray,
        payload: Map<String, ByteArray>,
        passphrase: String?,
    ): ByteArray = ByteArrayOutputStream().also {
        BackupArchive.write(it, manifest, payload, passphrase)
    }.toByteArray()
}
