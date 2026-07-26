package com.zerotoship.z2term.pty

import android.util.Log
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * PTY (擬似端末) プロセスのラッパー。
 *
 * forkpty() でネイティブに PTY を生成し、master FD を Java 側で扱う。
 *
 * 使い方:
 * ```
 * val pty = PtyProcess.create(
 *     command = "/system/bin/sh",
 *     args = arrayOf("sh"),
 *     env = arrayOf("TERM=xterm-256color", "HOME=/data/data/com.zerotoship.z2term/files"),
 *     cwd = "/data/data/com.zerotoship.z2term/files",
 *     rows = 24,
 *     cols = 80
 * )
 * pty.writer.write("ls\n".toByteArray())
 * val output = pty.reader.read(buf)
 * ```
 */
class PtyProcess private constructor(
    private val fd: Int,
    private val pid: Int
) {
    private val fileDescriptor: FileDescriptor = createFileDescriptor(fd)

    /** PTY からの読み込みストリーム（プロセス出力） */
    val reader: FileInputStream = FileInputStream(fileDescriptor)

    /** PTY への書き込みストリーム（プロセス入力） */
    val writer: FileOutputStream = FileOutputStream(fileDescriptor)

    /** プロセスが生存しているか */
    val isAlive: Boolean
        get() = nativeIsAlive(pid)

    /** forkpty() で受け取ったシェル側 PID (== セッションリーダ pgid)。 */
    val shellPid: Int
        get() = pid

    /**
     * PTY master fd の前景プロセスグループ ID。
     * シェルがプロンプトで待機中はシェル自身の pgid、TUI 実行中はその TUI の pgid を返す。
     * 取得失敗 (fd 無効 / 端末でない) は -1。
     */
    fun foregroundPgid(): Int = nativeForegroundPgid(fd)

    /** 終了コード（プロセスがまだ生きている場合は null） */
    val exitCode: Int?
        get() = if (isAlive) null else nativeGetExitCode(pid)

    /**
     * 端末サイズ変更を PTY に伝える。
     * @param rows 行数
     * @param cols 列数
     */
    fun resize(rows: Int, cols: Int) {
        if (rows <= 0 || cols <= 0) return
        nativeResize(fd, rows, cols)
    }

    /**
     * プロセスにシグナルを送信。
     * @param signal POSIX シグナル番号 (例: SIGHUP=1, SIGINT=2, SIGTERM=15, SIGKILL=9)
     */
    fun sendSignal(signal: Int) {
        nativeSendSignal(pid, signal)
    }

    /**
     * PTY とプロセスを終了させる。
     * - PTY ファイル記述子をクローズ
     * - プロセスに SIGHUP を送信
     * - waitpid で回収（ゾンビ防止）
     */
    fun close() {
        try {
            writer.close()
        } catch (e: IOException) {
            // ignore
        }
        try {
            reader.close()
        } catch (e: IOException) {
            // ignore
        }
        nativeClose(fd, pid)
    }

    /**
     * PTY を**閉じるだけ**にする（シグナルは一切送らない）。
     *
     * [close] との違いは「殺さない」こと。proot/z2root は `--kill-on-exit`
     * (`PTRACE_O_EXITKILL`) 付きで起動しているので、**ルートプロセスを kill すると配下の
     * プロセスがカーネルに道連れで kill される**。スクリプトが背景へ逃がしたデーモン
     * (`sshd --lan` の dropbear 等) を生かしたままにしたいときは [close] を使ってはいけない。
     *
     * ⚠ 呼ぶのは**ルートプロセスが既に終わったあと**だけにすること。生きているうちに
     * マスタ fd を閉じると、カーネルが端末のフォアグラウンドプロセスグループへ SIGHUP を
     * 送り、結局ルートごと落ちて同じ道連れが起きる（[waitFor] で待ってから呼ぶ）。
     */
    fun detach() {
        try {
            writer.close()
        } catch (e: IOException) {
            // ignore
        }
        try {
            reader.close()
        } catch (e: IOException) {
            // ignore
        }
    }

    /** プロセスの終了を待つ（ブロッキング） */
    fun waitFor(): Int {
        return nativeWaitFor(pid)
    }

    companion object {
        private const val TAG = "PtyProcess"

        init {
            try {
                System.loadLibrary("z2term")
                Log.i(TAG, "Native library 'libz2term.so' loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed to load native library", e)
                throw e
            }
        }

        /**
         * 新しい PTY プロセスを生成。
         *
         * @param command 実行するプログラムへのフルパス (例: "/system/bin/sh")
         * @param args 引数配列（argv[0] には通常コマンド名）
         * @param env 環境変数配列 ("KEY=VALUE" 形式)
         * @param cwd 作業ディレクトリ
         * @param rows 初期行数
         * @param cols 初期列数
         */
        fun create(
            command: String,
            args: Array<String>,
            env: Array<String>,
            cwd: String,
            rows: Int = 24,
            cols: Int = 80
        ): PtyProcess {
            val result = nativeCreate(command, args, env, cwd, rows, cols)
            val fd = (result ushr 32).toInt()
            val pid = (result and 0xFFFFFFFF).toInt()
            if (fd < 0 || pid <= 0) {
                throw IOException("forkpty() failed: fd=$fd, pid=$pid")
            }
            Log.i(TAG, "PTY created: pid=$pid, fd=$fd, cmd=$command")
            return PtyProcess(fd, pid)
        }

        // ───────── JNI 関数 ─────────

        /**
         * forkpty() で PTY を作成。
         * 戻り値の上位 32bit が fd、下位 32bit が pid。
         */
        @JvmStatic
        private external fun nativeCreate(
            command: String,
            args: Array<String>,
            env: Array<String>,
            cwd: String,
            rows: Int,
            cols: Int
        ): Long

        @JvmStatic
        private external fun nativeResize(fd: Int, rows: Int, cols: Int)

        @JvmStatic
        private external fun nativeSendSignal(pid: Int, signal: Int)

        @JvmStatic
        private external fun nativeIsAlive(pid: Int): Boolean

        @JvmStatic
        private external fun nativeGetExitCode(pid: Int): Int

        @JvmStatic
        private external fun nativeWaitFor(pid: Int): Int

        @JvmStatic
        private external fun nativeClose(fd: Int, pid: Int)

        @JvmStatic
        private external fun nativeForegroundPgid(fd: Int): Int

        @JvmStatic
        private external fun createFileDescriptor(fd: Int): FileDescriptor
    }
}
