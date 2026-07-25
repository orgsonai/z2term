package com.zerotoship.z2term.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 持ち出した秘密の包み方 ([BackupCrypt]) の検証。
 *
 * ここは**間違えても画面には何も出ない**（合言葉が違えば「取り込めません」と言うだけ）ので、
 * 「実は素通しだった」「合言葉が違っても通った」に気付けない。性質を明示的に固定する。
 */
class BackupCryptTest {

    private val secret = """[{"host":"example.com","privateKey":"-----BEGIN OPENSSH PRIVATE KEY-----"}]"""
        .toByteArray()

    @Test
    fun roundTripsWithTheSamePassphrase() {
        val blob = BackupCrypt.encrypt(secret, "correct horse battery staple")
        assertArrayEquals(secret, BackupCrypt.decrypt(blob, "correct horse battery staple"))
    }

    @Test
    fun wrongPassphraseFails() {
        val blob = BackupCrypt.encrypt(secret, "right")
        // GCM の認証が落ちるので例外。合言葉違いと改竄を区別せず弾ける。
        assertThrows(Exception::class.java) { BackupCrypt.decrypt(blob, "wrong") }
    }

    @Test
    fun plaintextIsNotVisibleInTheBlob() {
        // 「暗号化したつもりで実は素通し」が一番怖い。中身が生で残っていないことを見る。
        val blob = BackupCrypt.encrypt(secret, "pass")
        val asText = String(blob, Charsets.ISO_8859_1)
        assertFalse(asText.contains("example.com"))
        assertFalse(asText.contains("BEGIN OPENSSH"))
    }

    @Test
    fun sameInputGivesDifferentBlobs() {
        // salt と iv が毎回変わること。同じなら、2 つのバックアップを見比べただけで
        // 「中身が変わっていない」が分かってしまう。
        val a = BackupCrypt.encrypt(secret, "pass")
        val b = BackupCrypt.encrypt(secret, "pass")
        assertFalse(a.contentEquals(b))
        assertArrayEquals(secret, BackupCrypt.decrypt(a, "pass"))
        assertArrayEquals(secret, BackupCrypt.decrypt(b, "pass"))
    }

    @Test
    fun tamperedBlobFails() {
        val blob = BackupCrypt.encrypt(secret, "pass")
        blob[blob.size - 1] = (blob[blob.size - 1] + 1).toByte()
        assertThrows(Exception::class.java) { BackupCrypt.decrypt(blob, "pass") }
    }

    @Test
    fun emptyPassphraseIsRefusedOnBothSides() {
        // 「合言葉なしで秘密を扱う経路」を作らない、という設計をコードで固定する。
        assertThrows(IllegalArgumentException::class.java) { BackupCrypt.encrypt(secret, "") }
        val blob = BackupCrypt.encrypt(secret, "pass")
        assertThrows(IllegalArgumentException::class.java) { BackupCrypt.decrypt(blob, "") }
    }

    @Test
    fun foreignBlobIsRejected() {
        assertThrows(Exception::class.java) { BackupCrypt.decrypt("not a backup".toByteArray(), "pass") }
    }

    @Test
    fun blobStartsWithTheFormatMarker() {
        val blob = BackupCrypt.encrypt(secret, "pass")
        assertTrue(String(blob.copyOfRange(0, 5), Charsets.US_ASCII) == "Z2BK1")
    }
}
