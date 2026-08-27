package com.zerotoship.z2term.gui.rfb

import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * サーバがパスワードを要求しているのに、こちらが持っていない (A1 リモート VNC)。
 * 呼び出し側 ([com.zerotoship.z2term.gui.GuiSession]) はこれを見て入力を促す。
 */
class RfbPasswordRequiredException(message: String) : IOException(message)

/** パスワードが違う / サーバに認証を断られた。 */
class RfbAuthFailedException(message: String) : IOException(message)

/** サーバが要求する認証方式にこちらが対応していない (VeNCrypt/TLS 等)。 */
class RfbSecurityUnsupportedException(message: String) : IOException(message)

/**
 * RFB の認証まわりのうち、**ソケットに触らない部分**だけを集めたもの (A1)。
 *
 * [RfbClient] は Socket と Bitmap を握るので JVM の単体テストから動かせない。
 * 間違えると「繋がらない」以外の症状が出ず、しかも間違えやすい 2 つ
 * (**方式の選び方**と **DES 鍵のビット反転**) をここに寄せてテストできるようにしてある。
 */
object VncAuth {

    /** security type。0 は「失敗」で、その後に理由の文字列が続く。 */
    const val SEC_INVALID = 0
    const val SEC_NONE = 1
    const val SEC_VNC_AUTH = 2
    const val SEC_VENCRYPT = 19

    /** VNC 認証のチャレンジ長 (RFB 仕様で固定)。 */
    const val CHALLENGE_BYTES = 16

    /** DES 鍵の長さ。パスワードは 8 バイトに詰め / 切り詰めされる (RFB 仕様)。 */
    const val KEY_BYTES = 8

    /**
     * サーバが並べた security type から使うものを 1 つ選ぶ。
     *
     * 規則:
     *  - **None があれば None** — 認証が要らない相手にわざわざパスワードを送らない
     *    (ローカルの Xvnc は常にこちら)。
     *  - None が無く VNC 認証があり、パスワードを持っていれば VNC 認証。
     *  - パスワードが無ければ [RfbPasswordRequiredException]。UI が入力を促せるよう、
     *    ただの「繋がらない」ではなく専用の型で返す。
     *  - どちらも無ければ [RfbSecurityUnsupportedException]。VeNCrypt (19) は TLS を
     *    伴うので、名指しで「未対応」と伝えて設定を直してもらう。
     */
    fun pickSecurityType(offered: List<Int>, hasPassword: Boolean): Int = when {
        offered.contains(SEC_NONE) -> SEC_NONE
        offered.contains(SEC_VNC_AUTH) && hasPassword -> SEC_VNC_AUTH
        offered.contains(SEC_VNC_AUTH) -> throw RfbPasswordRequiredException(
            "VNC server requires a password (security types: " + offered.joinToString() + ")"
        )
        offered.contains(SEC_VENCRYPT) -> throw RfbSecurityUnsupportedException(
            "VeNCrypt (TLS) is not supported (security types: " + offered.joinToString() + ")"
        )
        else -> throw RfbSecurityUnsupportedException(
            "no supported VNC security type (offered: " + offered.joinToString() + ")"
        )
    }

    /**
     * パスワードから VNC 認証の DES 鍵を作る。
     *
     * ⚠ **各バイトのビット順を逆にする**のが RFB 独自の癖 (元実装が DES 鍵を LSB 側から
     * 詰めていた名残)。ここを飛ばすと「パスワードは合っているのに毎回拒否される」になる。
     * 8 バイトに満たなければ 0 で埋め、超えた分は捨てる (9 文字目以降は効かない)。
     */
    fun desKey(password: String): ByteArray {
        val raw = password.toByteArray(Charsets.ISO_8859_1)
        val key = ByteArray(KEY_BYTES)
        for (i in 0 until KEY_BYTES) {
            val b = if (i < raw.size) raw[i].toInt() and 0xFF else 0
            key[i] = reverseBits(b).toByte()
        }
        return key
    }

    /**
     * サーバから来た 16 バイトのチャレンジを [desKey] で暗号化して返す (DES-ECB・2 ブロック)。
     * これをそのまま送り返すのが VNC 認証 (security type 2)。
     */
    fun challengeResponse(password: String, challenge: ByteArray): ByteArray {
        require(challenge.size == CHALLENGE_BYTES) {
            "challenge must be " + CHALLENGE_BYTES + " bytes (got " + challenge.size + ")"
        }
        val cipher = Cipher.getInstance("DES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(desKey(password), "DES"))
        return cipher.doFinal(challenge)
    }

    /** 1 バイトのビット順を反転する (0b0111_1010 → 0b0101_1110)。 */
    private fun reverseBits(b: Int): Int {
        var v = 0
        for (i in 0 until 8) if (b and (1 shl i) != 0) v = v or (1 shl (7 - i))
        return v
    }
}
