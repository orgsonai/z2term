package com.zerotoship.z2term.backup

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 持ち出したファイルを合言葉で包む (0.8.239、全 payload は 0.8.449)。
 *
 * **なぜ Keystore を使わないか**: Android Keystore の鍵は**その端末から出せない**。
 * 端末内で守るには最適だが、持ち出しには使えない (移した先で復号できない)。だから
 * ここだけは「合言葉から鍵を作る」方式にする。
 *
 * 形式は自前だが中身は標準的なもの: `PBKDF2WithHmacSHA256` (salt 16B / [ITERATIONS] 回) で
 * 256bit 鍵を作り、`AES/GCM/NoPadding` (iv 12B) で包む。format 1 では SSH の秘密だけ、
 * format 2 では manifest を除くバックアップ全体を対象にする。出力は
 * `"Z2BK1" | salt(16) | iv(12) | ciphertext+tag` の連結。
 *
 * 合言葉が違えば GCM の認証が失敗して復号できない = **「合言葉が違う」と「壊れている」を
 * 区別せずに弾ける**。取り込み側はどちらでも同じく「取り込めません」と言えばよい。
 */
object BackupCrypt {

    /** 形式の目印。将来変えるときはここを見て分岐する。 */
    private val MAGIC = "Z2BK1".toByteArray(Charsets.US_ASCII)

    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256

    /**
     * 反復回数。端末で 1 回だけ走る処理なので、体感を損なわない範囲で多くする
     * (合言葉は人が覚えられる長さ = 総当たりが現実的なので、ここを削るとその分弱くなる)。
     */
    private const val ITERATIONS = 210_000

    fun encrypt(plain: ByteArray, passphrase: String): ByteArray {
        require(passphrase.isNotEmpty()) { "empty passphrase" }
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        }
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    /** 復号。合言葉違い・改竄・形式違いはすべて例外になる (呼び元は同じ扱いでよい)。 */
    fun decrypt(blob: ByteArray, passphrase: String): ByteArray {
        require(passphrase.isNotEmpty()) { "empty passphrase" }
        val head = MAGIC.size
        require(blob.size > head + SALT_LEN + IV_LEN) { "too short" }
        require(blob.copyOfRange(0, head).contentEquals(MAGIC)) { "not a z2term backup blob" }
        val salt = blob.copyOfRange(head, head + SALT_LEN)
        val iv = blob.copyOfRange(head + SALT_LEN, head + SALT_LEN + IV_LEN)
        val body = blob.copyOfRange(head + SALT_LEN + IV_LEN, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        }
        return cipher.doFinal(body)
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val kf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(kf.generateSecret(spec).encoded, "AES")
    }
}
