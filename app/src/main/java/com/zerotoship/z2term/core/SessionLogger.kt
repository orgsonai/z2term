package com.zerotoship.z2term.core

import android.util.Log
import com.zerotoship.z2term.emulator.Utf8Decoder
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 端末に出た内容をテキストファイルへ記録する (ツールバー ⚪ = C1)。
 *
 * **書き込み経路**: `TerminalSession.startReadLoop` が PTY から読んだ塊を、エミュレータに
 * 食わせた**直後**に [append] へ渡す。alt screen かどうかはエミュレータに食わせた後でないと
 * 正しく判定できないため、この順にしている (タブに出るものは必ずここを通る)。
 *
 * **スレッド**: [append] は呼び出し元 (エミュレータのシリアルスレッド) をブロックしない。
 * バイト列を単一スレッドの executor へ積むだけで、変換とファイル書き込みはそちらで行う。
 * 端末描画の 60fps コアレッシングに I/O が割り込まないようにするため。
 * flush は [FLUSH_INTERVAL_MS] 周期。アプリが OS に殺されても失うのは末尾のこの分だけ。
 *
 * **ローテーションしない** ([com.zerotoship.z2term.service.LogWriter] と同じユーザー方針)。
 * 代わりに現在のサイズを [bytesWritten] で出し、青天井なのを黙って進めない。
 *
 * **伏せ字** ([mask]、0.8.243): 書く直前に [SecretMasker] を通す。行の途中で秘密が半分だけ
 * 通ることが無いよう**完成した行の単位**で当てるので、ON のときは改行が来るまで最後の 1 行を
 * 保持する (ファイルは後から読むものなので実害は無く、[close] で必ず吐き出す)。
 *
 * @param file    書き込み先。親ディレクトリは呼び出し側で作っておくこと。
 * @param append  true なら既存ファイルの末尾に足す。false なら新規 (呼び出し側で名前を決めておく)。
 * @param raw     true ならバイト列をそのまま書く (不具合報告用の生ログ)。
 *                false は色や画面制御の指示を落として人が読めるテキストにする。
 * @param mask    true なら鍵・トークンらしき部分を伏せ字にする。**生ログ (raw) にも効かせる** —
 *                外に出す機会がいちばん多いのが不具合報告用のログなので、そこに穴を空けない。
 */
class SessionLogger(
    val file: File,
    append: Boolean,
    private val raw: Boolean,
    mask: Boolean,
    timestamp: Boolean = false
) {
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "z2term-log").apply { isDaemon = true }
    }
    private val out = BufferedOutputStream(FileOutputStream(file, append), BUFFER_BYTES)
    private val plain = if (raw) null else PlainTextFilter()
    private val masker = if (mask) SecretMasker() else null

    /** 伏せ字の当て先を作るための、まだ改行が来ていない行。 */
    private val maskBuf = StringBuilder()

    /**
     * 伏せ字のときにバイト列と文字列を行き来する文字集合。
     *
     * プレーンは変換後の UTF-8。**生ログは ISO-8859-1** — 1 バイト = 1 文字で往復するので、
     * 伏せ字に当たらなかった部分のバイト列が 1 ビットも変わらない (UTF-8 で読もうとすると
     * 不正なバイトが `?` に化けて生ログでなくなる)。
     */
    private val maskCharset = if (raw) Charsets.ISO_8859_1 else Charsets.UTF_8

    /**
     * 行頭に付ける時刻の書式。**生ログ (raw) では絶対に付けない** — バイト列がそのまま
     * 残ることが生ログの存在意義で、1 バイトでも足したら不具合報告の材料として使えない。
     *
     * **固定長**にしてある（`[2026-07-27 08:42:13] ` = 常に 22 文字）。桁が揺れると
     * 本文の開始位置がズレて、せっかく行頭に付けても読みづらくなる。年から秒まで
     * 完全な日付を入れるのは、日をまたぐ記録で「その 08:42 はいつのものか」を
     * 後から読めなくしないため。
     */
    private val stampFormat =
        if (timestamp && !raw) SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) else null

    /** 次に書くバイトが行頭か（チャンクをまたいで持ち越す）。 */
    private var atLineStart = true

    @Volatile
    private var closed = false

    /** これまでに書いたバイト数。バッファ待ちの分も含む (UI の「今のサイズ」表示用)。 */
    @Volatile
    var bytesWritten: Long = 0L
        private set

    init {
        bytesWritten = if (append) file.length() else 0L
        executor.scheduleWithFixedDelay(
            { runCatching { out.flush() } },
            FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS
        )
    }

    /**
     * 端末に出た塊を記録する。呼び出し元はブロックされない。
     *
     * @param chunk PTY から読んだ生バイト (呼び出し側で複製済みのものを渡すこと)。
     */
    fun append(chunk: ByteArray) {
        if (closed) return
        runCatching {
            executor.execute {
                if (closed) return@execute
                runCatching {
                    val bytes = stampIfNeeded(maskIfNeeded(plain?.filter(chunk) ?: chunk))
                    if (bytes.isNotEmpty()) {
                        out.write(bytes)
                        bytesWritten += bytes.size
                    }
                }.onFailure { Log.w(TAG, "write failed: ${it.message}") }
            }
        }
    }

    /** 既に画面に出ていた分 (スクロールバック) を先頭に書く。記録開始直後に 1 回だけ呼ぶ。 */
    fun appendText(text: String) {
        if (closed || text.isEmpty()) return
        val raw = text.toByteArray(maskCharset)
        runCatching {
            executor.execute {
                if (closed) return@execute
                runCatching {
                    val bytes = stampIfNeeded(maskIfNeeded(raw))
                    if (bytes.isNotEmpty()) {
                        out.write(bytes)
                        bytesWritten += bytes.size
                    }
                }.onFailure { Log.w(TAG, "write failed: ${it.message}") }
            }
        }
    }

    /**
     * 行頭ごとに時刻を差し込む。時刻はチャンク単位で 1 回だけ求める（同じ塊は同じ瞬間に届く）。
     *
     * 改行の直後を「次の行頭」として持ち越すので、1 行がチャンクの境目で割れても
     * 二重に付いたり付き損ねたりしない。挿入するのは ASCII だけなので、UTF-8 の
     * 途中に割り込んで文字を壊すことはない。
     */
    private fun stampIfNeeded(bytes: ByteArray): ByteArray {
        val fmt = stampFormat ?: return bytes
        if (bytes.isEmpty()) return bytes
        val prefix = "[${fmt.format(Date())}] ".toByteArray(Charsets.US_ASCII)
        val out = java.io.ByteArrayOutputStream(bytes.size + prefix.size * 4)
        var from = 0
        for (i in bytes.indices) {
            if (bytes[i] == NEWLINE) {
                if (atLineStart) out.write(prefix)
                out.write(bytes, from, i - from + 1)
                atLineStart = true
                from = i + 1
            }
        }
        if (from < bytes.size) {
            if (atLineStart) out.write(prefix)
            out.write(bytes, from, bytes.size - from)
            atLineStart = false
        }
        return out.toByteArray()
    }

    /**
     * 伏せ字を当てる。**完成した行だけ**を通し、改行が来ていない末尾は次回へ持ち越す
     * (行の途中で切ると秘密の後半が素通りする)。伏せ字が OFF なら何もしない。
     *
     * 改行を出さないまま流れ続ける出力で持ち越しが無限に伸びないよう、
     * [MASK_LINE_LIMIT] を超えたらそこまでを 1 行として扱って吐き出す。
     */
    private fun maskIfNeeded(bytes: ByteArray): ByteArray {
        val m = masker ?: return bytes
        if (bytes.isNotEmpty()) maskBuf.append(String(bytes, maskCharset))
        val sb = StringBuilder()
        while (true) {
            val nl = maskBuf.indexOf("\n")
            if (nl < 0) break
            sb.append(m.maskLine(maskBuf.substring(0, nl))).append('\n')
            maskBuf.delete(0, nl + 1)
        }
        if (maskBuf.length >= MASK_LINE_LIMIT) {
            sb.append(m.maskLine(maskBuf.toString()))
            maskBuf.setLength(0)
        }
        return if (sb.isEmpty()) EMPTY else sb.toString().toByteArray(maskCharset)
    }

    /** 伏せ字の持ち越し (改行で終わっていない最後の行) を吐き出す。 */
    private fun drainMask(): ByteArray {
        val m = masker ?: return EMPTY
        if (maskBuf.isEmpty()) return EMPTY
        val text = m.maskLine(maskBuf.toString())
        maskBuf.setLength(0)
        return text.toByteArray(maskCharset)
    }

    /** 記録を止めてファイルを閉じる。書き残しは必ず吐き出す (タブを閉じるときも呼ぶこと)。 */
    fun close() {
        if (closed) return
        closed = true
        runCatching {
            executor.execute {
                runCatching {
                    // 改行で終わっていない最後の 1 行も落とさない
                    // (整形 → 伏せ字 の順。伏せ字の持ち越しは整形の残りも含めて最後に吐く)。
                    plain?.drain()?.let { if (it.isNotEmpty()) out.write(maskIfNeeded(it)) }
                    drainMask().let { if (it.isNotEmpty()) out.write(it) }
                    out.flush()
                    out.close()
                }.onFailure { Log.w(TAG, "close failed: ${it.message}") }
            }
            executor.shutdown()
        }
    }

    companion object {
        /** 行頭判定に使う改行 (LF)。 */
        private const val NEWLINE: Byte = 10
        private const val TAG = "SessionLogger"
        private const val BUFFER_BYTES = 16 * 1024
        private const val FLUSH_INTERVAL_MS = 500L
        private val EMPTY = ByteArray(0)
        /** 改行が来ないまま伏せ字の持ち越しがこれ以上伸びたら、そこまでを 1 行として扱う。 */
        private const val MASK_LINE_LIMIT = 8192
    }
}

/**
 * 端末の生バイト列を「人が読めるプレーンテキスト」に直す変換器。
 *
 * 単に `ESC[...m` を捨てるだけでは読めるものにならないので、**画面の 1 行を組み立て直してから**
 * 出力する:
 *
 *  - **`\r` は行頭に戻るだけ**で、行の内容は消さない。以後の文字はその行を**上書き**する。
 *    ダウンロードの進捗表示 (`50%\r75%\r100%\n`) が全部別の行になって数千行に膨れるのを防ぎ、
 *    最後の状態だけを 1 行として残す。`\r\n` (ふつうの改行) もこの規則で正しく処理される
 *    (行頭に戻ってから改行 = 行の内容はそのまま出る)。
 *  - **`\b` は 1 文字ぶん戻る**。上書きされなければ内容は残る (端末と同じ)。
 *  - エスケープシーケンス (CSI / OSC / DCS 等) と、意味を持たない C0 制御文字は捨てる。
 *  - タブは残す。
 *
 * バイト位置ではなく**コードポイント単位**で組み立てる ([Utf8Decoder] 経由)。日本語が混ざった行で
 * `\r` 上書きが起きてもマルチバイト文字が割れない。塊の途中で UTF-8 が切れても次回に持ち越す。
 *
 * 1 行が [MAX_LINE_CHARS] を超えたら改行を待たずに吐き出す (改行を出さないまま流れ続ける出力で
 * 行バッファが無限に伸びるのを防ぐ)。
 */
internal class PlainTextFilter {
    private val decoder = Utf8Decoder()
    /** 組み立て中の 1 行 (コードポイント)。 */
    private val line = StringBuilder()
    /** 行内の書き込み位置 (文字数)。`\r` で 0 に戻り、以後は上書きになる。 */
    private var pos = 0
    private var state = State.NORMAL
    /** 文字列系シーケンス (OSC/DCS/…) の中で ESC を見たか (ESC \ = 終端の検出用)。 */
    private var escInString = false

    private enum class State { NORMAL, ESC, CSI, OSC, STRING, CHARSET }

    fun filter(chunk: ByteArray): ByteArray {
        val sb = StringBuilder()
        for (b in chunk) {
            val v = b.toInt() and 0xFF
            when (state) {
                State.NORMAL -> {
                    // ESC 以降はエスケープとして読み捨てるので、デコーダには通さない。
                    if (v == 0x1B) { state = State.ESC; continue }
                    val cp = decoder.feed(v) ?: continue
                    onCodePoint(cp, sb)
                }
                State.ESC -> {
                    state = when {
                        v == '['.code -> State.CSI
                        v == ']'.code -> { escInString = false; State.OSC }
                        // DCS / SOS / PM / APC: ST (ESC \) まで読み捨てる。
                        v == 'P'.code || v == 'X'.code || v == '^'.code || v == '_'.code -> {
                            escInString = false; State.STRING
                        }
                        // 文字集合の指定 (ESC ( B 等) は次の 1 バイトまでが本体。
                        v in 0x28..0x2F -> State.CHARSET
                        else -> State.NORMAL   // ESC c / ESC = 等の 2 バイト系はここで終わり
                    }
                }
                State.CSI -> {
                    // パラメータ (0x30-0x3F) と中間バイト (0x20-0x2F) が続き、0x40-0x7E で終わる。
                    if (v in 0x40..0x7E) state = State.NORMAL
                }
                State.OSC -> {
                    // BEL 終端と ST (ESC \) 終端の両方がある。
                    when {
                        v == 0x07 -> state = State.NORMAL
                        escInString && v == '\\'.code -> state = State.NORMAL
                        else -> escInString = (v == 0x1B)
                    }
                }
                State.STRING -> {
                    when {
                        escInString && v == '\\'.code -> state = State.NORMAL
                        else -> escInString = (v == 0x1B)
                    }
                }
                State.CHARSET -> state = State.NORMAL
            }
        }
        return if (sb.isEmpty()) EMPTY else sb.toString().toByteArray(Charsets.UTF_8)
    }

    /** 改行で終わっていない組み立て中の行を吐き出す (記録の停止時)。 */
    fun drain(): ByteArray {
        if (line.isEmpty()) return EMPTY
        val text = line.toString() + "\n"
        line.setLength(0)
        pos = 0
        return text.toByteArray(Charsets.UTF_8)
    }

    private fun onCodePoint(cp: Int, sb: StringBuilder) {
        when (cp) {
            0x0A -> {                      // LF: 行を確定
                sb.append(line).append('\n')
                line.setLength(0)
                pos = 0
            }
            0x0D -> pos = 0                // CR: 行頭へ戻るだけ (内容は残す)
            0x08 -> if (pos > 0) pos--     // BS: 1 文字戻る
            0x09 -> put('\t')              // TAB は残す
            else -> {
                // 残りの C0 と DEL は意味を持たないので捨てる。
                if (cp < 0x20 || cp == 0x7F) return
                if (Character.isSupplementaryCodePoint(cp)) {
                    // サロゲートペアは 2 char。上書き位置の管理が崩れないよう 1 文字として扱わず、
                    // 上位・下位をそれぞれ put する (絵文字の途中に上書きが入る事故は実害が無い)。
                    for (c in Character.toChars(cp)) put(c)
                } else {
                    put(cp.toChar())
                }
            }
        }
        if (line.length >= MAX_LINE_CHARS) {
            sb.append(line).append('\n')
            line.setLength(0)
            pos = 0
        }
    }

    /** 現在位置に 1 文字置く。行末なら足し、途中なら上書きする。 */
    private fun put(c: Char) {
        if (pos < line.length) line.setCharAt(pos, c) else line.append(c)
        pos++
    }

    companion object {
        private val EMPTY = ByteArray(0)
        private const val MAX_LINE_CHARS = 8192
    }
}
