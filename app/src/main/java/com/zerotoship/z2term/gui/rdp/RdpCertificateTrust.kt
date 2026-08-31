package com.zerotoship.z2term.gui.rdp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zerotoship.z2term.R
import com.zerotoship.z2term.channel.HostKeyVerifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.security.MessageDigest
import java.security.cert.X509Certificate

private val Context.rdpCertificatesDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "z2term_rdp_certificates")

/**
 * RDP サーバーの TLS 証明書を**相手ごとに 1 度だけ確かめて覚える** (SSH の known_hosts と同じ考え方)。
 *
 * RDP の証明書は自己署名が普通なので、システム CA では検証できない。かといって全部受け入れると
 * 中間者に気付けないので、**初回だけ指紋を見せて利用者に決めてもらい、次回からは一致を要求する**。
 * 覚えた指紋と違うものが出てきたら、もう一度確認を出す (相手が入れ替わった可能性がある)。
 *
 * ⚠ 覚える名前 ([endpoint]) は **SSH 転送を通す前の本来の宛先**にすること。転送中の
 * `127.0.0.1:<毎回変わるポート>` で覚えると、次の接続で必ず「初めての相手」になる。
 *
 * 確認のダイアログは SSH のホスト鍵と同じ [HostKeyVerifier] に相乗りする (利用者が覚える画面を
 * 増やさない)。接続スレッドは応答があるまで blocking する。
 */
object RdpCertificateTrust {
    private val KEY = stringPreferencesKey("fingerprints")

    /** 証明書の SHA-256 指紋 (`AB:CD:…`)。 */
    fun fingerprintOf(certificate: X509Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString(":") { "%02X".format(it) }

    /**
     * この証明書で接続してよいか。未知・変化していれば UI へ確認を出し、承認されたら覚える。
     *
     * IO スレッド (RDP の接続処理) から呼ぶこと。UI の応答を待って blocking する。
     */
    fun verify(context: Context, endpoint: String, certificate: X509Certificate): Boolean {
        val app = context.applicationContext
        val fingerprint = runCatching { fingerprintOf(certificate) }.getOrNull() ?: return false
        val known = runCatching { runBlocking { stored(app)[endpoint] } }.getOrNull()
        if (known == fingerprint) return true

        val accepted = HostKeyVerifier.requestVerify(
            HostKeyVerifier.Prompt(
                host = endpoint,
                keyType = app.getString(R.string.rdp_cert_keytype),
                fingerprint = fingerprint,
                message = app.getString(
                    if (known == null) R.string.rdp_cert_unknown else R.string.rdp_cert_changed,
                    endpoint,
                ),
                title = app.getString(R.string.rdp_cert_title),
            )
        )
        if (accepted) runCatching { runBlocking { remember(app, endpoint, fingerprint) } }
        return accepted
    }

    /** 覚えている指紋をすべて忘れる (接続先を消したときの後片付け用)。 */
    suspend fun forget(context: Context, endpoint: String) {
        context.applicationContext.rdpCertificatesDataStore.edit { p ->
            p[KEY] = encode(decode(p[KEY]) - endpoint)
        }
    }

    private suspend fun stored(context: Context): Map<String, String> =
        decode(context.rdpCertificatesDataStore.data.first()[KEY])

    private suspend fun remember(context: Context, endpoint: String, fingerprint: String) {
        context.rdpCertificatesDataStore.edit { p ->
            p[KEY] = encode(decode(p[KEY]) + (endpoint to fingerprint))
        }
    }

    private fun decode(raw: String?): Map<String, String> {
        if (raw.isNullOrEmpty()) return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            o.keys().asSequence().associateWith { o.optString(it) }
        }.getOrDefault(emptyMap())
    }

    private fun encode(map: Map<String, String>): String =
        JSONObject().apply { map.forEach { (k, v) -> put(k, v) } }.toString()
}
