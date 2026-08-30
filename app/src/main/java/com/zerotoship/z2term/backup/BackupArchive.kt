package com.zerotoship.z2term.backup

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * バックアップ ZIP の外枠。
 *
 * format 1 は [MANIFEST] と各データを同じ ZIP に並べる旧形式。format 2 は [MANIFEST] だけを
 * プレビュー用に平文で残し、データ一式を内側の ZIP にまとめて [PAYLOAD_ENC] として暗号化する。
 *
 * Android の状態へ適用する前にこの層で完全に復号・展開する。合言葉違い・破損・重複エントリは
 * すべて null にし、「設定だけ戻ったが SSH は戻らなかった」という部分適用を作らない。
 */
internal object BackupArchive {

    const val FORMAT_FLAT = 1
    const val FORMAT_ENCRYPTED_PAYLOAD = 2
    const val MANIFEST = "manifest.json"
    const val PAYLOAD_ENC = "payload.enc"

    /**
     * [payload] を書き出す。[passphrase] があると format 2、無ければ旧来の平文 format 1。
     */
    fun write(
        out: OutputStream,
        manifest: ByteArray,
        payload: Map<String, ByteArray>,
        passphrase: String?,
    ) {
        require(MANIFEST !in payload && PAYLOAD_ENC !in payload) { "reserved backup entry" }
        ZipOutputStream(out).use { zip ->
            zip.putBytes(MANIFEST, manifest)
            if (passphrase != null) {
                require(passphrase.isNotEmpty()) { "empty passphrase" }
                val innerZip = zipBytes(payload)
                zip.putBytes(PAYLOAD_ENC, BackupCrypt.encrypt(innerZip, passphrase))
            } else {
                payload.forEach { (name, data) -> zip.putBytes(name, data) }
            }
        }
    }

    /** 外側の ZIP を読む。壊れた ZIP と同名エントリを持つ ZIP は拒否する。 */
    fun readOuter(bytes: ByteArray): Map<String, ByteArray>? = unzip(bytes)

    /**
     * manifest の format に従って、実際に適用するエントリを得る。
     * format 2 は payload 全体の復号と ZIP 展開が終わるまで何も返さない。
     */
    fun payloadForImport(
        outer: Map<String, ByteArray>,
        format: Int,
        passphrase: String,
    ): Map<String, ByteArray>? {
        return when (format) {
            FORMAT_FLAT -> outer.filterKeys { it != MANIFEST }
                .takeIf { it.isNotEmpty() }

            FORMAT_ENCRYPTED_PAYLOAD -> {
                if (passphrase.isEmpty() || outer.keys != setOf(MANIFEST, PAYLOAD_ENC)) return null
                val plain = runCatching {
                    BackupCrypt.decrypt(outer.getValue(PAYLOAD_ENC), passphrase)
                }.getOrNull() ?: return null
                unzip(plain)?.takeIf { it.isNotEmpty() && MANIFEST !in it && PAYLOAD_ENC !in it }
            }

            else -> null
        }
    }

    private fun zipBytes(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, data) -> zip.putBytes(name, data) }
        }
        return out.toByteArray()
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray>? = runCatching {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zin ->
            while (true) {
                val entry: ZipEntry = zin.nextEntry ?: break
                if (!entry.isDirectory) {
                    require(out.put(entry.name, zin.readBytes()) == null) {
                        "duplicate backup entry: ${entry.name}"
                    }
                }
                zin.closeEntry()
            }
        }
        out
    }.getOrNull()

    private fun ZipOutputStream.putBytes(name: String, data: ByteArray) {
        require(name.isNotEmpty() && !name.startsWith('/') && !name.contains("../")) {
            "unsafe backup entry: $name"
        }
        putNextEntry(ZipEntry(name))
        write(data)
        closeEntry()
    }
}
