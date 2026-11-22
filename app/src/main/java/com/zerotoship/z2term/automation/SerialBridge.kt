package com.zerotoship.z2term.automation

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.zerotoship.z2term.channel.SerialTranscript
import com.zerotoship.z2term.core.SessionManager
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.service.Z2ApiBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI がシリアルのタブを使う道具 (MCP の `serial_list` / `serial_read` / `serial_send`、1.0.208)。
 *
 * **AI は自分で口を開かない。** 利用者が開いたシリアルのタブを借りる。送った文字も返ってきた文字も
 * そのタブに出るので、AI が何をしているかを人がそのまま見られる。
 *
 * 返事は画面ではなく [SerialTranscript] から読む。送る直前の通算の受信バイト数を控えておき、
 * それより後に届いた分だけを返す (画面は折り返しや上書きで形が変わり、「今の返事」を切り出せない)。
 *
 * 許可は [UiBridgeSettings.serialAccess]。道具の一覧に出すかどうかとは別に、呼ばれるたびにも確かめる
 * (一覧を受け取った後で利用者が設定を戻すことがあるため)。
 */
internal object SerialBridge {
    private const val TAG = "SerialBridge"
    const val READ_DEFAULT = 4_000
    const val READ_MAX = SerialTranscript.DEFAULT_CAPACITY
    const val WAIT_DEFAULT_MS = 3_000L
    const val WAIT_MAX_MS = 30_000L
    const val QUIET_DEFAULT_MS = 500L
    const val TEXT_MAX = 4_096
    /** AI へ返す文字の上限。これを超えたら後ろを残す ([trimmed])。 */
    const val REPLY_MAX = 16_000

    fun list(app: Context): JSONObject {
        val access = access(app, SerialAccess.READ)
        val tabs = JSONArray()
        SessionManager.sessions.value.forEachIndexed { i, s ->
            val ch = (s as? TerminalSession)?.serialChannel ?: return@forEachIndexed
            tabs.put(JSONObject().put("tab", i + 1).put("name", s.label.value).put("connected", ch.connected)
                .put("device", ch.deviceLabel ?: JSONObject.NULL))
        }
        return UiBridge.success().put("access", access.key).put("tabs", tabs)
    }

    fun read(app: Context, tab: String?, bytes: Int?): JSONObject {
        access(app, SerialAccess.READ)
        val (index, session) = serialTab(tab)
        val ch = session.serialChannel!!
        val n = (bytes ?: READ_DEFAULT).coerceIn(1, READ_MAX)
        val text = clean(ch.transcript.tail(n).toString(Charsets.UTF_8))
        val (out, cut) = trimmed(text)
        return UiBridge.success().put("tab", index).put("connected", ch.connected).put("text", out).put("truncated", cut)
    }

    fun send(app: Context, tab: String?, text: String, enter: Boolean, raw: Boolean, until: String?,
        waitMs: Long?, quietMs: Long?): JSONObject {
        access(app, SerialAccess.SEND)
        if (text.length > TEXT_MAX) throw Refusal("bad_args", "text is longer than $TEXT_MAX characters")
        val pattern = until?.takeIf { it.isNotEmpty() }?.let {
            runCatching { Regex(it) }.getOrElse { e -> throw Refusal("bad_args", "until is not a valid regular expression: ${e.message}") }
        }
        val payload = (if (raw) decodeEscapes(text) else text.toByteArray(Charsets.UTF_8)) +
            (if (enter) byteArrayOf('\r'.code.toByte()) else ByteArray(0))
        if (payload.isEmpty()) throw Refusal("bad_args", "Nothing to send (empty text and enter: false)")
        val (index, session) = serialTab(tab)
        val ch = session.serialChannel!!
        if (!ch.connected) throw Refusal("not_connected", "The serial tab $index is open but its cable is not connected (or not allowed yet)")
        val started = SystemClock.elapsedRealtime()
        // ⚠ 控えを取ってから送る。逆にすると、速い相手の返事の頭が「送る前」に数えられて欠ける。
        val mark = ch.transcript.total
        runBlocking(Dispatchers.Main) { session.writeBytes(payload) }
        val reply = waitReply(ch.transcript, mark, pattern, (waitMs ?: WAIT_DEFAULT_MS).coerceIn(1, WAIT_MAX_MS),
            (quietMs ?: QUIET_DEFAULT_MS).coerceIn(50, 5_000))
        val (out, cut) = trimmed(clean(reply.bytes.toString(Charsets.UTF_8)))
        val result = UiBridge.success().put("tab", index).put("text", out).put("timed_out", reply.timedOut)
            .put("truncated", cut || reply.lost)
        if (pattern != null) result.put("matched", reply.matched)
        log(app, session.label.value, payload.size, result, SystemClock.elapsedRealtime() - started)
        return result
    }

    /** 送ったあとの返事の待ち方。 */
    class Reply(val bytes: ByteArray, val matched: Boolean, val timedOut: Boolean, val lost: Boolean)

    /**
     * [until] があれば、届いた分 (色の指示を除いた文字) がそれに当たるまで待つ。無ければ、何か届いてから
     * [quietMs] 何も来なくなったら返す。どちらも最大 [waitMs]。時刻と眠り方は試験のために差し替えられる。
     */
    fun waitReply(transcript: SerialTranscript, mark: Long, until: Regex?, waitMs: Long, quietMs: Long,
        now: () -> Long = SystemClock::elapsedRealtime, sleep: (Long) -> Unit = Thread::sleep): Reply {
        val start = now()
        var lastTotal = mark
        var lastChange = start
        while (true) {
            val t = now()
            val total = transcript.total
            if (total != lastTotal) { lastTotal = total; lastChange = t }
            if (until != null) {
                if (total > mark) {
                    val slice = transcript.since(mark)
                    if (until.containsMatchIn(clean(slice.bytes.toString(Charsets.UTF_8)))) {
                        return Reply(slice.bytes, matched = true, timedOut = false, lost = slice.lost)
                    }
                }
            } else if (total > mark && t - lastChange >= quietMs) {
                val slice = transcript.since(mark)
                return Reply(slice.bytes, matched = false, timedOut = false, lost = slice.lost)
            }
            if (t - start >= waitMs) {
                val slice = transcript.since(mark)
                return Reply(slice.bytes, matched = false, timedOut = true, lost = slice.lost)
            }
            sleep(POLL_MS)
        }
    }

    private const val POLL_MS = 30L

    private fun access(app: Context, need: SerialAccess): SerialAccess {
        val access = UiBridgeSettings(app).serialAccess
        if (access == SerialAccess.OFF) throw Refusal("serial_off",
            "AI access to serial tabs is off. The user can turn it on in z2term: 📜 › Automation › AI › AI and serial")
        if (need == SerialAccess.SEND && access != SerialAccess.SEND) throw Refusal("read_only",
            "The user allows reading serial tabs only. Ask them to choose \"Read and send\" under 📜 › Automation › AI › AI and serial")
        return access
    }

    /**
     * タブの指定 (`z2-session list` の番号かタブ名) を解決する。省略したときはシリアルのタブが 1 つだけなら
     * それを使う (2 つ以上なら選ばせる — 違う機器へ送る事故を作らない)。
     */
    private fun serialTab(tab: String?): Pair<Int, TerminalSession> {
        val sessions = SessionManager.sessions.value
        val session = if (tab.isNullOrBlank()) {
            val serial = sessions.filterIsInstance<TerminalSession>().filter { it.serialChannel != null }
            when (serial.size) {
                0 -> throw Refusal("no_serial_tab", "No serial tab is open. Ask the user to open one from the connection list")
                1 -> serial[0]
                else -> throw Refusal("ambiguous_tab", "${serial.size} serial tabs are open; pass tab (see serial_list)")
            }
        } else {
            Z2ApiBridge.resolveSession(tab) as? TerminalSession
                ?: throw Refusal("no_serial_tab", "No such tab: $tab")
        }
        if (session.serialChannel == null) throw Refusal("no_serial_tab", "Tab $tab is not a serial tab")
        return (sessions.indexOfFirst { it.id == session.id } + 1) to session
    }

    private val ANSI = Regex("\u001B\\[[0-?]*[ -/]*[@-~]|\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)|\u001B[@-Z\\\\-_]")

    /** AI が読む形にする: 色やカーソルの指示を除き、行末を `\n` にそろえる。 */
    fun clean(text: String): String = ANSI.replace(text, "").replace("\r\n", "\n").replace("\r", "")

    /** 長すぎる返事は後ろ (新しい方) を残す。 */
    fun trimmed(text: String): Pair<String, Boolean> =
        if (text.length <= REPLY_MAX) text to false else text.takeLast(REPLY_MAX) to true

    /** `raw: true` の文字: `\xHH` `\e` `\r` `\n` `\t` `\0` `\\` を実バイトへ。それ以外は UTF-8 のまま。 */
    fun decodeEscapes(text: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c != '\\' || i + 1 >= text.length) {
                out.write(c.toString().toByteArray(Charsets.UTF_8)); i++; continue
            }
            when (val e = text[i + 1]) {
                'x' -> {
                    val hex = text.substring(i + 2, minOf(i + 4, text.length))
                    val v = hex.takeIf { it.length == 2 }?.toIntOrNull(16)
                        ?: throw Refusal("bad_args", "\\x needs two hex digits")
                    out.write(v); i += 4; continue
                }
                'e' -> out.write(0x1B)
                'r' -> out.write('\r'.code)
                'n' -> out.write('\n'.code)
                't' -> out.write('\t'.code)
                '0' -> out.write(0)
                '\\' -> out.write('\\'.code)
                else -> throw Refusal("bad_args", "Unknown escape \\$e (use \\xHH \\e \\r \\n \\t \\0 \\\\)")
            }
            i += 2
        }
        return out.toByteArray()
    }

    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

    /**
     * AI 操作と同じ `~/.z2term/ui/log.jsonl` に 1 行残す。送った文字は残さず、バイト数だけ
     * (ログインのパスワードを送ることがあるため。`type` と同じ扱い)。
     */
    private fun log(app: Context, tab: String, bytes: Int, result: JSONObject, ms: Long) = runCatching {
        val line = JSONObject().put("time", synchronized(iso) { iso.format(Date()) }).put("verb", "serial_send")
            .put("tab", tab).put("bytes", bytes).put("ok", result.optBoolean("ok")).put("ms", ms)
        val dir = File(app.filesDir, "shared_home/.z2term/ui").apply { mkdirs() }
        val file = File(dir, "log.jsonl")
        synchronized(UiBridge) {
            if (file.length() > 1_000_000) file.renameTo(File(dir, "log.1.jsonl"))
            file.appendText(line.toString() + "\n")
        }
    }.onFailure { Log.w(TAG, "log failed", it) }
}
