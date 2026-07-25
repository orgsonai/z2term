package com.zerotoship.z2term.channel

import android.content.Context
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.util.OpenSSHPrivateKeyUtil
import org.bouncycastle.crypto.util.OpenSSHPublicKeyUtil
import java.io.File
import java.security.SecureRandom
import java.util.Base64

/**
 * SSH クライアント鍵をアプリの中で作る (0.8.238)。
 *
 * **なぜ要るか**: これまでクライアント鍵を使うには**秘密鍵の PEM をテキスト欄に貼る**しか
 * なかった。スマホで PEM を用意して貼るのは初心者にはほぼ無理で、SSH を使い始める前に
 * そこで止まる。「鍵を作る」ボタンがあるだけで、SSH がデモの最初の 5 分に入る。
 *
 * **既存の貼り付け欄は残す**。作る／貼る の 2 択で、モード分けはしない
 * (「初心者モード」を作らない、というこのアプリの方針)。
 *
 * 秘密鍵の置き場は従来どおり [SshProfile] の中で、永続化時に [KeystoreCrypt] が暗号化する。
 * ここが作るのは**文字列 2 本**だけで、保存の責任は増やさない。
 */
object SshKeyGen {

    /** 生成した鍵ペア。[privatePem] は保存用、[publicLine] は相手に渡す 1 行。 */
    data class Generated(val privatePem: String, val publicLine: String)

    /**
     * ed25519 の鍵ペアを作る。[comment] は公開鍵の末尾に付く目印 (どの端末の鍵か分かるように)。
     *
     * ed25519 を選ぶのは、短くて速く、いまの sshd がまず対応しているため。RSA を選ばせる
     * 設定は置かない — 選択肢を出しても、選べる人はそもそも自分で鍵を作れる。
     */
    fun generate(comment: String = "z2term"): Generated {
        // ⚠ **JSch では作れない。** `KeyPair.genKeyPair(…, ED25519)` は生成できるが、
        // `writePrivateKey` が `UnsupportedOperationException` を投げる (JSch は ed25519 の
        // **読み込みには対応、書き出しには未対応**)。SSH のために既に入っている BouncyCastle で
        // 作り、OpenSSH 形式で書き出す。作った鍵を JSch が読めることは [SshKeyGenTest] が確認する。
        val gen = Ed25519KeyPairGenerator()
        gen.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val kp = gen.generateKeyPair()

        // パスフレーズは付けない。付けると接続のたびに入力を求めることになり、「まず繋がる」
        // までの距離が伸びる。秘密鍵は Keystore 暗号化で端末内に留まる。
        val privBlob = OpenSSHPrivateKeyUtil.encodePrivateKey(kp.private)
        val pubBlob = OpenSSHPublicKeyUtil.encodePublicKey(kp.public)
        return Generated(
            privatePem = pem(privBlob),
            publicLine = "ssh-ed25519 ${Base64.getEncoder().encodeToString(pubBlob)} $comment"
        )
    }

    /** OpenSSH 秘密鍵の PEM 包み (64 文字改行)。この形なら JSch も OpenSSH も読める。 */
    private fun pem(blob: ByteArray): String {
        val b64 = Base64.getEncoder().encodeToString(blob)
        val body = b64.chunked(64).joinToString("\n")
        return "-----BEGIN OPENSSH PRIVATE KEY-----\n$body\n-----END OPENSSH PRIVATE KEY-----\n"
    }

    /**
     * 内蔵 sshd の `~/.ssh/authorized_keys` に [publicLine] を追記する (0.8.238)。
     *
     * これまでは端末で `cat … >> ~/.ssh/authorized_keys && chmod 600 …` を手で打たせていた。
     * **同じ鍵は二重に足さない**（重複行があっても害はないが、消すときに迷う）。
     *
     * `~` は共有ホーム (`filesDir/shared_home`) で、端末から見える `~` と同じ場所。
     * dropbear は権限に厳しいので、ディレクトリ 700 / ファイル 600 に寄せる。
     *
     * @return 追記したら true、既に入っていたら false。
     */
    fun addToAuthorizedKeys(context: Context, publicLine: String): Boolean {
        val line = publicLine.trim()
        require(line.isNotEmpty()) { "empty public key" }
        val home = File(context.applicationContext.filesDir, "shared_home")
        val dir = File(home, ".ssh").apply { mkdirs() }
        val f = File(dir, "authorized_keys")
        val existing = runCatching { f.readText() }.getOrDefault("")
        // 鍵本体 (2 列目) で比べる。コメントだけ違う同じ鍵を二重登録しない。
        val body = line.split(" ").getOrNull(1)
        if (body != null && existing.lineSequence().any { it.split(" ").getOrNull(1) == body }) {
            return false
        }
        val prefix = if (existing.isEmpty() || existing.endsWith("\n")) "" else "\n"
        f.appendText("$prefix$line\n")
        runCatching {
            @Suppress("SetWorldReadable")
            dir.setReadable(true, true)
            dir.setExecutable(true, true)
            f.setReadable(true, true)
            f.setWritable(true, true)
        }
        return true
    }
}
