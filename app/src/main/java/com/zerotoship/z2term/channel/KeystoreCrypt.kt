package com.zerotoship.z2term.channel

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore (hardware-backed AES-256) を使った文字列の暗号化ユーティリティ。
 *
 * - 鍵は alias "z2term_ssh_v1" でデバイス内に永続生成 (初回呼出し時に作成)
 * - 暗号化: AES/GCM/NoPadding、IV はランダム生成し ciphertext と連結して Base64
 * - 復号失敗時は IllegalStateException を投げる (ファクトリリセットや
 *   StrongBox 抜き取りで鍵が消えた場合)
 *
 * セキュリティ注意:
 * - ハードウェアキーストアが無い端末では soft fallback されるが、それでも
 *   アプリのプロセスごとに分離されるので素の DataStore よりは強い。
 * - 本クラスは「アプリのプロセスから読める範囲で平文化を防ぐ」ことが目的で、
 *   端末を root されたら破られる。
 */
object KeystoreCrypt {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "z2term_ssh_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12  // GCM 推奨

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }

        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        kg.init(spec)
        return kg.generateKey()
    }

    /** 平文 → "ENC:" + base64(iv || ciphertext) */
    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val joined = ByteArray(iv.size + ct.size).apply {
            System.arraycopy(iv, 0, this, 0, iv.size)
            System.arraycopy(ct, 0, this, iv.size, ct.size)
        }
        return PREFIX + Base64.encodeToString(joined, Base64.NO_WRAP)
    }

    /**
     * encrypt() の出力を復号。"ENC:" プレフィックスが無い場合は「平文」として
     * そのまま返す (旧バージョン互換)。鍵紛失時は IllegalStateException。
     */
    fun decrypt(value: String): String {
        if (value.isEmpty()) return ""
        if (!value.startsWith(PREFIX)) return value  // legacy plaintext
        val raw = try {
            Base64.decode(value.substring(PREFIX.length), Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Invalid encrypted payload", e)
        }
        if (raw.size < IV_BYTES + 1) throw IllegalStateException("Truncated payload")
        val iv = raw.copyOfRange(0, IV_BYTES)
        val ct = raw.copyOfRange(IV_BYTES, raw.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, loadOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val plain = cipher.doFinal(ct)
        return String(plain, Charsets.UTF_8)
    }

    /** デバッグ用: 暗号化済み文字列かどうか判定 */
    fun isEncrypted(value: String): Boolean = value.startsWith(PREFIX)

    private const val PREFIX = "ENC:"
}
