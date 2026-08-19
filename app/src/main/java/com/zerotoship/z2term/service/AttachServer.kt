package com.zerotoship.z2term.service

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.emulator.SgrAttribute
import com.zerotoship.z2term.proot.Z2ApiMsg
import com.zerotoship.z2term.settings.LocaleHelper
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * **タブに繋ぎっぱなしにする受付** (`z2-session attach`)。
 *
 * 他の `z2-*` が使う `z2api` は「ファイルを 1 つ置いて 0.1 秒ごとに返事を見に行く」作りで、
 * 1 往復のお使いには十分だが **打った文字が即座に返る用途には使えない**。そこで繋ぎっぱなしの
 * 管を 1 本だけ別に開ける。
 *
 * ## 通り道
 *
 * ホスト側の実体は `filesDir/shared_home/.z2term/attach.sock`、rootfs からは
 * `/root/.z2term/attach.sock` に見える (`/root` = 共有ホームの bind)。z2root が AF_UNIX の
 * `bind`/`connect` のパスを翻訳するので、ゲストからはゲストのパスのまま繋げる
 * (実績は 0.8.327 の pacman/gpg-agent)。
 *
 * ⚠⚠ **受付は 1 本だけにする。タブごとに切らない。** AF_UNIX のパスは 107 バイトまでで、
 * タブごとに id (UUID 36 文字) を挟むと 109 バイトになり **実機でだけ繋がらない**。
 * 受付 1 本なら 72 バイトで収まり、どのタブに繋ぐかは最初のフレームで受け取れば足りる。
 *
 * ## フレーム
 *
 * `[種類 1 byte][長さ 2 byte BE][中身]`。生バイトの素通しにすると **広さの変更を伝える隙間が
 * 無い** ので、全部を同じ封筒に入れる。定義は `app/src/main/cpp/z2attach/z2attach.c` と対。
 */
object AttachServer {

    private const val TAG = "AttachServer"

    private const val F_DATA = 0
    private const val F_SIZE = 1
    private const val F_NOTICE = 2
    private const val F_TARGET = 3

    /** 読み取りの塊 ([TerminalSession] の 8192) と同じ。長さ 2 byte に必ず収まる。 */
    private const val MAX_PAYLOAD = 8192

    /** ESC (0x1B)。⚠ ソースに生の制御文字を書かない — 経路の途中で消える。 */
    private const val ESC = "\u001B"

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var server: LocalServerSocket? = null

    /** ホスト側の実体。ゲストからは `/root/.z2term/attach.sock`。 */
    fun socketFile(context: Context): File =
        File(context.filesDir, "shared_home/.z2term/attach.sock")

    fun start(context: Context) {
        if (server != null) return
        val appCtx = context.applicationContext
        val sock = socketFile(appCtx)
        runCatching { sock.parentFile?.mkdirs() }
        // ⚠ 前回の残骸を消してから bind する。AF_UNIX はファイルが残っていると EADDRINUSE で
        //   落ち、以後どのタブにも繋げなくなる (プロセスが死んだ後もファイルだけ残る)。
        runCatching { if (sock.exists()) sock.delete() }

        val srv = runCatching {
            val ls = LocalSocket(LocalSocket.SOCKET_STREAM)
            ls.bind(LocalSocketAddress(sock.absolutePath, LocalSocketAddress.Namespace.FILESYSTEM))
            LocalServerSocket(ls.fileDescriptor)
        }.getOrElse { e ->
            Log.w(TAG, "cannot bind ${sock.absolutePath}: ${e.message}")
            return
        }
        server = srv
        thread(name = "z2term-attach-accept", isDaemon = true) {
            while (server === srv) {
                val client = runCatching { srv.accept() }.getOrNull() ?: break
                thread(name = "z2term-attach", isDaemon = true) { serve(appCtx, client) }
            }
        }
        Log.i(TAG, "listening on ${sock.absolutePath}")
    }

    fun stop() {
        val srv = server ?: return
        server = null
        runCatching { srv.close() }
    }

    // --- 1 本の接続の面倒を見る ---------------------------------------------

    private fun serve(context: Context, client: LocalSocket) {
        val input = client.inputStream
        val output = client.outputStream
        val writeLock = Any()
        var detach: (() -> Unit)? = null
        var held = false
        try {
            // 最初のフレームは必ず繋ぎ先。
            val first = readFrame(input) ?: return
            if (first.type != F_TARGET) {
                sendFrame(output, writeLock, F_NOTICE, "ERR protocol".toByteArray())
                return
            }
            val target = String(first.payload, Charsets.UTF_8)
            val m = Z2ApiMsg(en = LocaleHelper.language(context) != LocaleHelper.LANG_JA, d = "$")

            val session = onMain { Z2ApiBridge.resolveSession(target) }
            val refusal = refusalFor(session, target, m)
            if (refusal != null) {
                sendFrame(output, writeLock, F_NOTICE, "ERR $refusal".toByteArray(Charsets.UTF_8))
                return
            }
            val term = session as TerminalSession
            sendFrame(
                output, writeLock, F_NOTICE,
                "OK ${term.label.value}".toByteArray(Charsets.UTF_8)
            )

            // PTY の出力をこの接続へ。⚠ 1 本の接続が詰まっても PTY 側は止めない
            // (書けなくなったら、その接続だけ畳む)。
            detach = onMain {
                term.addAttachSink { chunk ->
                    runCatching { sendFrame(output, writeLock, F_DATA, chunk) }
                        .onFailure { runCatching { client.close() } }
                }
            }

            // 繋いでいる間は常駐枠に入れて、作業中に落とされないようにする。
            held = true
            AttachHold.acquire(context)

            // 繋いだ瞬間に真っ黒から始めない。今の画面を色ごと組み直して送る。
            val screen = onMain { renderScreen(term) }
            if (screen.isNotEmpty()) sendFrame(output, writeLock, F_DATA, screen)

            while (true) {
                val f = readFrame(input) ?: break
                when (f.type) {
                    F_DATA -> onMain { term.writeBytes(f.payload) }
                    F_SIZE -> {
                        val parts = String(f.payload, Charsets.UTF_8).trim().split(" ")
                        val rows = parts.getOrNull(0)?.toIntOrNull() ?: continue
                        val cols = parts.getOrNull(1)?.toIntOrNull() ?: continue
                        onMain { term.setAttachSize(rows, cols) }
                    }
                    else -> Unit
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "attach ended: ${e.message}")
        } finally {
            // ⚠ 外し忘れると PTY の出力が閉じた接続へ流れ続け、広さも戻らない。
            runCatching { onMain { detach?.invoke() } }
            if (held) runCatching { AttachHold.release(context) }
            runCatching { client.close() }
        }
    }

    /**
     * 繋げない理由 (無ければ null)。
     *
     * ⛔ **黙って失敗しない** — `z2-session key` で決めた「送らずに、なぜ送れないかを出す」と
     * 同じ約束。特に「まだ起動していない」は **こちらから勝手に起こさない**
     * (繋いだつもりが OS の初回ダウンロードを始める、を作らない)。
     */
    private fun refusalFor(session: Any?, target: String, m: Z2ApiMsg): String? = when {
        session == null -> m.attachNoSuchTab(target)
        session !is TerminalSession -> m.attachNotTerminal
        session.uiState.value.state == TerminalSession.TerminalState.IDLE -> m.attachNotStarted
        session.uiState.value.state == TerminalSession.TerminalState.EXITED -> m.attachExited
        else -> null
    }

    // --- 画面を色ごと組み直す -----------------------------------------------

    /**
     * 今の画面を SGR 込みで作る。
     *
     * ⚠ `getAllText()` を使わない — あれは平文なので **色と装飾が全部落ちる**。
     * ⚠ 遡れる分 (スクロールバック) は送らない。今見えている画面だけ
     * (遡りたいなら `z2-session capture --all`)。
     */
    private fun renderScreen(term: TerminalSession): ByteArray {
        val buf = term.emulator.buffer
        val sb = StringBuilder()
        sb.append(ESC).append("[0m").append(ESC).append("[2J").append(ESC).append("[H")
        var curFg = SgrAttribute.DEFAULT
        var curBg = SgrAttribute.DEFAULT
        val rows = buf.rows
        for (r in 0 until rows) {
            val row = runCatching { buf.getScreenRow(r) }.getOrNull() ?: continue
            for (c in 0 until row.columns) {
                val cell = row.getCell(c)
                if (cell.wideCont) continue
                if (cell.fgAttr != curFg || cell.bgAttr != curBg) {
                    sb.append(sgrFor(cell.fgAttr, cell.bgAttr))
                    curFg = cell.fgAttr
                    curBg = cell.bgAttr
                }
                sb.append(cell.char)
            }
            sb.append(ESC).append("[0m").append(ESC).append("[K")
            curFg = SgrAttribute.DEFAULT
            curBg = SgrAttribute.DEFAULT
            if (r < rows - 1) sb.append("\r\n")
        }
        // カーソルは最後に置く (途中で動かすと組み立て中の位置が残る)。
        sb.append(ESC).append("[")
            .append(term.emulator.cursorRow + 1).append(";")
            .append(term.emulator.cursorCol + 1).append("H")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    /**
     * 属性 2 つぶんの SGR。⚠ **必ず `0` (全部戻す) から組む** — 差分だけ出すと、
     * 消し忘れた装飾が以降の行へ延々と尾を引く。
     */
    private fun sgrFor(fg: Int, bg: Int): String {
        val p = ArrayList<String>(8)
        p.add("0")
        if (SgrAttribute.hasFlag(fg, SgrAttribute.FLAG_BOLD)) p.add("1")
        if (SgrAttribute.hasFlag(fg, SgrAttribute.FLAG_ITALIC)) p.add("3")
        if (SgrAttribute.hasFlag(fg, SgrAttribute.FLAG_UNDERLINE)) p.add("4")
        if (SgrAttribute.hasFlag(fg, SgrAttribute.FLAG_BLINK)) p.add("5")
        if (SgrAttribute.hasFlag(fg, SgrAttribute.FLAG_INVERSE)) p.add("7")
        if (SgrAttribute.hasFlag(fg, SgrAttribute.FLAG_STRIKE)) p.add("9")
        when {
            SgrAttribute.isIndexed(fg) -> {
                val i = SgrAttribute.getIndex(fg)
                when {
                    i < 8 -> p.add("${30 + i}")
                    i < 16 -> p.add("${90 + i - 8}")
                    else -> { p.add("38"); p.add("5"); p.add("$i") }
                }
            }
            SgrAttribute.isRgb(fg) -> {
                p.add("38"); p.add("2")
                p.add("${SgrAttribute.getR(fg)}")
                p.add("${SgrAttribute.getG(fg)}")
                p.add("${SgrAttribute.getB(fg)}")
            }
        }
        when {
            SgrAttribute.isIndexed(bg) -> {
                val i = SgrAttribute.getIndex(bg)
                when {
                    i < 8 -> p.add("${40 + i}")
                    i < 16 -> p.add("${100 + i - 8}")
                    else -> { p.add("48"); p.add("5"); p.add("$i") }
                }
            }
            SgrAttribute.isRgb(bg) -> {
                p.add("48"); p.add("2")
                p.add("${SgrAttribute.getR(bg)}")
                p.add("${SgrAttribute.getG(bg)}")
                p.add("${SgrAttribute.getB(bg)}")
            }
        }
        return ESC + "[" + p.joinToString(";") + "m"
    }

    // --- フレームの読み書き --------------------------------------------------

    private class Frame(val type: Int, val payload: ByteArray)

    private fun readFrame(input: InputStream): Frame? {
        val head = ByteArray(3)
        if (!readExact(input, head, 3)) return null
        val len = ((head[1].toInt() and 0xFF) shl 8) or (head[2].toInt() and 0xFF)
        if (len > MAX_PAYLOAD) return null
        val payload = ByteArray(len)
        if (len > 0 && !readExact(input, payload, len)) return null
        return Frame(head[0].toInt() and 0xFF, payload)
    }

    private fun readExact(input: InputStream, buf: ByteArray, len: Int): Boolean {
        var off = 0
        while (off < len) {
            val n = input.read(buf, off, len - off)
            if (n < 0) return false
            off += n
        }
        return true
    }

    /**
     * ⚠ **1 フレームを分割して書かない。** 出力側は PTY 読み取りスレッドと、繋いだ側への
     * 返事とで **同時に書きうる**。混ざると封筒の境目が壊れて相手が同期を失う。
     * 中身が [MAX_PAYLOAD] を超えるときは複数のフレームに割る (割れ目は封筒の外)。
     */
    private fun sendFrame(out: OutputStream, lock: Any, type: Int, payload: ByteArray) {
        var off = 0
        do {
            val n = minOf(MAX_PAYLOAD, payload.size - off)
            val head = byteArrayOf(
                type.toByte(),
                ((n shr 8) and 0xFF).toByte(),
                (n and 0xFF).toByte()
            )
            synchronized(lock) {
                out.write(head)
                if (n > 0) out.write(payload, off, n)
                out.flush()
            }
            off += n
        } while (off < payload.size)
    }

    /** 画面側と同じ前提に乗せるため Main で読む (`z2-session capture` と同じ扱い)。 */
    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val latch = CountDownLatch(1)
        var result: T? = null
        var error: Throwable? = null
        mainHandler.post {
            try { result = block() } catch (e: Throwable) { error = e } finally { latch.countDown() }
        }
        latch.await(5, TimeUnit.SECONDS)
        error?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
