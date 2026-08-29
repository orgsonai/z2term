package com.zerotoship.z2term.channel

import android.content.Context
import android.util.Log
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

/** SFTP のディレクトリエントリ 1 件 (UI 表示用) */
data class SftpEntry(
    val name: String,
    val isDir: Boolean,
    val isLink: Boolean,
    val size: Long,
    /** 最終更新 (epoch 秒) */
    val mtimeSec: Long,
    /** "drwxr-xr-x" のような表示用パーミッション文字列 */
    val permissions: String
)

/**
 * SSH 上の SFTP サブシステム ([ChannelSftp]) のラッパー。
 *
 * 認証 / known_hosts 検証は [SshSessionFactory] でシェル接続と共有する
 * (別 [Session] を張る — シェルとは独立に開閉できる)。
 *
 * すべての I/O は [Dispatchers.IO] で実行する suspend 関数。失敗は例外を送出する。
 * 使い終わったら必ず [close] すること。
 */
class SftpClient private constructor(
    private val session: Session,
    private val channel: ChannelSftp
) : RemoteFs {
    /** リモートのホームディレクトリ (取得不能なら "/") */
    override val home: String = runCatching { channel.home }.getOrNull()?.takeIf { it.isNotBlank() } ?: "/"

    override val isAlive: Boolean get() = channel.isConnected && session.isConnected

    /** path 配下のエントリ一覧 (`.` 除外、`..` は最上位以外で先頭、フォルダ→ファイル名順) */
    override suspend fun list(path: String): List<SftpEntry> = withContext(Dispatchers.IO) {
        val out = ArrayList<SftpEntry>()
        @Suppress("UNCHECKED_CAST")
        val entries = channel.ls(path) as java.util.Vector<ChannelSftp.LsEntry>
        for (e in entries) {
            val name = e.filename
            if (name == ".") continue
            val a = e.attrs
            out.add(
                SftpEntry(
                    name = name,
                    isDir = a.isDir,
                    isLink = a.isLink,
                    size = a.size,
                    mtimeSec = a.mTime.toLong(),
                    permissions = runCatching { a.permissionsString }.getOrDefault("")
                )
            )
        }
        out.sortWith(
            compareByDescending<SftpEntry> { it.name == ".." }
                .thenByDescending { it.isDir }
                .thenBy { it.name.lowercase() }
        )
        out
    }

    /** remotePath の内容を sink へ書き出す (ダウンロード)。sink は呼び出し側で close。 */
    override suspend fun download(remotePath: String, sink: OutputStream) = withContext(Dispatchers.IO) {
        channel.get(remotePath, sink)
    }

    /** source の内容を remotePath へ書き込む (アップロード、上書き)。source は呼び出し側で close。 */
    override suspend fun upload(source: InputStream, remotePath: String) = withContext(Dispatchers.IO) {
        channel.put(source, remotePath, ChannelSftp.OVERWRITE)
    }

    override suspend fun mkdir(path: String) = withContext(Dispatchers.IO) { channel.mkdir(path) }

    override suspend fun rename(from: String, to: String) = withContext(Dispatchers.IO) { channel.rename(from, to) }

    /** ファイル削除。ディレクトリは [rmdir] を使う。 */
    override suspend fun rm(path: String) = withContext(Dispatchers.IO) { channel.rm(path) }

    /** 空ディレクトリ削除。 */
    override suspend fun rmdir(path: String) = withContext(Dispatchers.IO) { channel.rmdir(path) }

    override fun close() {
        runCatching { channel.disconnect() }
        runCatching { session.disconnect() }
    }

    companion object {
        private const val TAG = "SftpClient"

        /** プロファイルに従い SFTP 接続を確立する。IO Dispatcher 上で実行される。 */
        suspend fun connect(profile: SshProfile, context: Context): SftpClient =
            withContext(Dispatchers.IO) {
                val session = SshSessionFactory.create(profile, context)
                session.connect(SshSessionFactory.CONNECT_TIMEOUT_MS)
                try {
                    val channel = session.openChannel("sftp") as ChannelSftp
                    channel.connect(SshSessionFactory.CONNECT_TIMEOUT_MS)
                    Log.i(TAG, "SFTP connected to ${profile.user}@${profile.host}:${profile.port}")
                    SftpClient(session, channel)
                } catch (e: Throwable) {
                    runCatching { session.disconnect() }
                    throw e
                }
            }

        /** パス結合 (".." は 1 階層上がる)。常に絶対パスを保つ。 */
        fun resolve(base: String, name: String): String {
            if (name == "..") {
                val trimmed = base.trimEnd('/')
                if (trimmed.isEmpty()) return "/"
                val parent = trimmed.substringBeforeLast('/', "")
                return if (parent.isEmpty()) "/" else parent
            }
            val b = base.trimEnd('/')
            return "$b/$name"
        }
    }
}
