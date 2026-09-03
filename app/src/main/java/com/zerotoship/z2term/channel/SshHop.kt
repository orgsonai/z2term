package com.zerotoship.z2term.channel

import com.zerotoship.z2term.net.HostAddress
import org.json.JSONObject

/**
 * 踏み台 1 段ぶん (`ssh -J` の 1 要素)。
 *
 * ⭐ **参照ではなく実体を持つ。** 「登録済みの接続先を指す」形にすると、その接続先を消した
 * 瞬間に踏み台が壊れ、A が B を経由し B が A を経由する輪も作れてしまう。⇒ 各段は自分の
 * 宛先と認証をそのまま持ち、画面側の「取り込み」で登録済みの内容を**コピーして**埋める。
 *
 * ⚠ [SshProfile.jumpHosts] の**並び順が経路そのもの**。先頭が端末から最初に繋ぐ相手で、
 * 最後の段の中から本来の接続先 ([SshProfile.host]) へ出る。
 */
data class SshHop(
    val host: String,
    val port: Int = 22,
    val user: String = "",
    val authType: SshProfile.AuthType = SshProfile.AuthType.PASSWORD,
    /** 平文。永続化時は [KeystoreCrypt] で暗号化する。 */
    val password: String = "",
    /** 平文。永続化時は [KeystoreCrypt] で暗号化する。 */
    val privateKey: String = "",
    /** 平文。永続化時は [KeystoreCrypt] で暗号化する。 */
    val keyPassphrase: String = "",
) {
    /** 一覧・ログに出す 1 行 (`ubuntu@gate.example.com:22`)。⚠ 秘密は出さない。 */
    fun describe(): String = buildString {
        if (user.isNotBlank()) append(user).append('@')
        append(HostAddress.hostPort(host, port))
    }

    fun credentials(): SshCredentials = SshCredentials(
        authType = authType,
        password = password,
        privateKey = privateKey,
        keyPassphrase = keyPassphrase,
    )

    fun toJson(encryptSecrets: Boolean): JSONObject = JSONObject().apply {
        put("host", host)
        put("port", port)
        put("user", user)
        put("authType", authType.name)
        put("password", if (encryptSecrets) KeystoreCrypt.encrypt(password) else password)
        put("privateKey", if (encryptSecrets) KeystoreCrypt.encrypt(privateKey) else privateKey)
        put(
            "keyPassphrase",
            if (encryptSecrets) KeystoreCrypt.encrypt(keyPassphrase) else keyPassphrase,
        )
    }

    companion object {
        fun fromJson(o: JSONObject, encryptedSecrets: Boolean): SshHop {
            fun secret(name: String): String {
                val raw = o.optString(name)
                if (!encryptedSecrets) return raw
                return runCatching { KeystoreCrypt.decrypt(raw) }.getOrDefault("")
            }
            return SshHop(
                host = o.optString("host"),
                port = o.optInt("port", 22),
                user = o.optString("user"),
                authType = runCatching {
                    SshProfile.AuthType.valueOf(
                        o.optString("authType", SshProfile.AuthType.PASSWORD.name)
                    )
                }.getOrDefault(SshProfile.AuthType.PASSWORD),
                password = secret("password"),
                privateKey = secret("privateKey"),
                keyPassphrase = secret("keyPassphrase"),
            )
        }
    }
}

/**
 * 1 本の SSH セッションを開くのに要る認証情報だけを取り出したもの。
 *
 * 接続先 ([SshProfile]) と踏み台 ([SshHop]) は持ち物が違うが、**繋ぐときの手順は同じ**。
 * [SshSessionFactory] がどちらも同じ 1 本の道で扱えるようにするための入れ物。
 */
data class SshCredentials(
    val authType: SshProfile.AuthType,
    val password: String,
    val privateKey: String,
    val keyPassphrase: String,
)
