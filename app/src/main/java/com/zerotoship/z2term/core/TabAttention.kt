package com.zerotoship.z2term.core

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zerotoship.z2term.MainActivity
import com.zerotoship.z2term.R
import com.zerotoship.z2term.icon.setZ2SmallIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 「出力が止まった」の判定 (Android 非依存・AttentionStateTest)。1 つの端末タブに 1 つ。
 *
 * 見ていないタブで出力が [armBytes] 以上続いたら構え、最後の出力から決めた秒数なにも出なければ
 * 1 度だけ知らせる。知らせたあとは、また出力が続くまで構えない (止まったままのタブで鳴り続けない)。
 *
 * ⚠ 構えるのに量を求めるのは、プロンプトの描き直しや時計の更新のような少しの出力で
 * 「止まった」を出さないため。AI の CLI が考えている間は表示が動き続けるので、すぐに量を超える。
 */
internal class AttentionState(private val armBytes: Int = ARM_BYTES) {
    private var lastOutputAt = 0L
    private var pending = 0
    private var armed = false

    /** 見ていないタブに出力が来た。 */
    @Synchronized fun output(now: Long, bytes: Int) {
        lastOutputAt = now
        pending += bytes
        if (pending >= armBytes) armed = true
    }

    /** タブを見た (または見ている間に出力が来た)。構えを解く。 */
    @Synchronized fun seen() {
        pending = 0
        armed = false
    }

    /** 次に確かめる時刻。構えていなければ null。 */
    @Synchronized fun dueAt(silenceMs: Long): Long? = if (armed) lastOutputAt + silenceMs else null

    /** [now] の時点で止まっているか。true を返したら構えを解く (1 度だけ知らせる)。 */
    @Synchronized fun silent(now: Long, silenceMs: Long): Boolean {
        if (!armed || now - lastOutputAt < silenceMs) return false
        seen()
        return true
    }

    companion object {
        const val ARM_BYTES = 256
    }
}

/**
 * 見ていない端末タブが「呼んでいる」ことを通知で知らせる (1.0.41)。
 *
 *  - **合図**: タブが BEL を鳴らした・OSC 9 / OSC 777 で通知を求めた。入力待ちになった CLI が出す。
 *  - **出力が止まった**: 出力が続いていたのに決めた秒数止まった ([AttentionState])。合図を出さない CLI 向け。
 *
 * 「見ている」= アプリが前面にいて、そのタブが選ばれている。見ているタブでは何もしない。
 * 通知を押すとそのタブを開き、タブを見たら通知は消える。通知はタブごとに 1 つで、出し直すと置き換わる。
 */
@SuppressLint("MissingPermission")
internal object TabAttention {
    private const val CHANNEL = "z2term_attention"
    /** 合図が続けて来ても、同じタブの通知はこの間隔より短くは出し直さない (ベルを連打する画面向け)。 */
    private const val COOLDOWN_MS = 10_000L
    const val EXTRA_SESSION = "com.zerotoship.z2term.OPEN_SESSION"

    enum class Reason { SIGNAL, SILENT }

    private val appVisible = MutableStateFlow(false)
    private val lastAlert = ConcurrentHashMap<String, Long>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var started = false

    /** MainActivity の onStart / onStop から。画面回転での作り直しは呼ばない。 */
    fun setAppVisible(visible: Boolean) { appVisible.value = visible }

    /** [id] のタブを人が見ているか。 */
    fun visible(id: String) = appVisible.value && SessionManager.activeId.value == id

    /** 見たタブの通知を消す。最初の端末タブができたときに 1 度だけ見張りを始める。 */
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        scope.launch {
            combine(appVisible, SessionManager.activeId) { shown, id -> id.takeIf { shown } }.collect { id ->
                id?.let { cancel(app, it) }
            }
        }
    }

    /**
     * 知らせる。見ているタブ・設定で切ったもの・続けて来すぎたものは出さない。
     * [title] / [body] は OSC 9 / 777 が付けた文 (無ければ決まった文)。
     */
    fun alert(context: Context, session: TerminalSession, reason: Reason, title: String? = null, body: String? = null,
        silenceSec: Int = 0) {
        val id = session.id
        if (visible(id)) return
        val now = SystemClock.elapsedRealtime()
        if (reason == Reason.SIGNAL && now - (lastAlert[id] ?: Long.MIN_VALUE / 2) < COOLDOWN_MS) return
        lastAlert[id] = now
        val app = context.applicationContext
        val label = session.label.value
        val heading = title?.take(80) ?: app.getString(
            if (reason == Reason.SIGNAL) R.string.attention_waiting_title else R.string.attention_silent_title, label)
        val text = body?.take(400) ?: if (reason == Reason.SIGNAL) app.getString(R.string.attention_waiting_text)
            else app.resources.getQuantityString(R.plurals.attention_silent_text, silenceSec, silenceSec)
        runCatching {
            channel(app)
            val open = Intent(app, MainActivity::class.java).putExtra(EXTRA_SESSION, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending = PendingIntent.getActivity(app, notifyId(id), open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(app, CHANNEL)
                .setZ2SmallIcon(app)
                .setContentTitle(heading)
                .setContentText(text)
                // OSC の題を使ったときも、どのタブからかは分かるようにする。
                .setSubText(if (title != null) label else null)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(app).notify(notifyId(id), notification)
        }
    }

    /** タブを見た・閉じたとき。 */
    fun cancel(context: Context, id: String) {
        runCatching { NotificationManagerCompat.from(context.applicationContext).cancel(notifyId(id)) }
    }

    fun forget(context: Context, id: String) {
        cancel(context, id)
        lastAlert.remove(id)
    }

    /** タブの id から通知の番号を作る。ほかの通知 (78xx 番台など) と被らない範囲へ寄せる。 */
    private fun notifyId(id: String) = 0x5A000000 or (id.hashCode() and 0x00FFFFFF)

    private fun channel(app: Context) {
        val manager = app.getSystemService(NotificationManager::class.java)
        // 画面の上に出したいので重要度は高。裏のタブが呼んでいるのに気付けなければ意味が無い。
        if (manager.getNotificationChannel(CHANNEL) == null) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, app.getString(R.string.attention_channel), NotificationManager.IMPORTANCE_HIGH))
    }
}
