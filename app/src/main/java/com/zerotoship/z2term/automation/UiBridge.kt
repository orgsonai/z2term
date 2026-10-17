package com.zerotoship.z2term.automation

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.zerotoship.z2term.R
import com.zerotoship.z2term.automation.UiBridgePolicy.Level
import com.zerotoship.z2term.automation.UiBridgePolicy.Target
import com.zerotoship.z2term.edge.AndroidActions
import com.zerotoship.z2term.edge.AndroidUiLook
import com.zerotoship.z2term.edge.AppLaunch
import com.zerotoship.z2term.icon.setZ2SmallIcon
import com.zerotoship.z2term.service.EventEmitter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * `z2-ui` の受付 (AI からのアプリ操作)。設計は `docs/ja/AI-BRIDGE.md`。
 *
 * **可否の判定はすべてここで行う。** 段階 ([UiBridgePolicy.Level])・承認・一時停止・指示・停止・
 * 記録を 1 か所に集め、[AndroidUiLook] には判定を済ませた操作だけを渡す。
 *
 * 返事は常に 1 行の JSON (`{"ok":true,…}` / `{"ok":false,"error":"<コード>","message":"…"}`)。
 * AI はコードで次の手を分ける。
 *
 * ⭐ **割り込みは「次の呼び出しへの返事」として届ける。** AI の操作中は対象アプリが前面にいて、
 * 人は AI のタブに文字を打てない。通知の「一時停止」「指示を送る」「停止」で状態を変えておき、
 * AI が次に `z2-ui` を呼んだときに `paused` / `interrupted` / `stopped` を返す。実行中の 1 手
 * (待ち・承認待ち) は [generation] が進んだ時点で止める。
 *
 * ⚠ **通知のボタンは [controlToken] を持つ。** AI は同じ uid で `cmd activity broadcast` を
 * 打てるので、印の無い放送を受け付けると「再開」「許可」を AI 自身が押せてしまう。印はこの
 * プロセスのメモリにしか無い。
 */
@SuppressLint("MissingPermission")
internal object UiBridge {
    private const val TAG = "UiBridge"
    private const val CHANNEL = "z2term_ui"
    private const val CHANNEL_ASK = "z2term_ui_ask"
    private const val NOTIFY_RUNNING = 7820
    private const val NOTIFY_ASK = 7821
    private const val IDLE_MS = 120_000L
    private const val ANSWER_MS = 60_000L
    private val CONTROL = setOf("status", "pause", "say", "stop")

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "z2-ui").apply { isDaemon = true } }
    // 一時停止・停止は、承認待ちや wait で塞がっている worker の後ろに並ばせない。
    private val control = Executors.newSingleThreadExecutor { Thread(it, "z2-ui-control").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    private val busyFlag = AtomicBoolean(false)
    private val generation = AtomicInteger()
    private val controlToken = UUID.randomUUID().toString()
    private val grants = ConcurrentHashMap<String, Long>()
    @Volatile private var paused = false
    @Volatile private var message: String? = null
    @Volatile private var lastPackage: String? = null
    @Volatile private var pending: Pending? = null
    private var protectedCache: Set<String>? = null

    /** 操作を送っている最中。z2-action はこの間始めない。 */
    val busy get() = busyFlag.get()
    val isPaused get() = paused

    private class Pending(val nonce: String, val minutes: Int) {
        val latch = CountDownLatch(1)
        @Volatile var answer: String? = null
    }

    fun submit(context: Context, args: List<String>, reply: (Boolean, String) -> Unit) {
        val app = context.applicationContext
        val verb = args.firstOrNull().orEmpty()
        (if (verb in CONTROL) control else worker).execute {
            val result = try {
                handle(app, verb, args.drop(1))
            } catch (e: Refusal) {
                failure(e.code, e.message.orEmpty())
            } catch (e: Exception) {
                Log.w(TAG, "z2-ui $verb failed", e)
                failure("error", e.message ?: e.javaClass.simpleName)
            }
            if (verb !in setOf("status", "")) log(app, verb, args.drop(1), result)
            reply(result.optBoolean("ok"), result.toString())
        }
    }

    private fun failure(code: String, text: String) = JSONObject().put("ok", false).put("error", code).put("message", text)
    private fun success() = JSONObject().put("ok", true)

    private fun handle(app: Context, verb: String, args: List<String>): JSONObject {
        val settings = UiBridgeSettings(app)
        return when (verb) {
            "status" -> { count(args, 0); status(app, settings) }
            "pause" -> { count(args, 0); pause(app); success() }
            "say" -> {
                count(args, 1)
                val text = args[0].trim().takeIf { it.isNotEmpty() && it.length <= 2000 }
                    ?: throw Refusal("bad_args", "z2-ui say TEXT (1..2000 characters)")
                say(app, text); success()
            }
            "stop" -> { count(args, 0); stop(app); success() }
            "allow" -> if (args.isEmpty()) status(app, settings) else exclusive { allow(app, settings, args) }
            "front" -> { count(args, 0); enter(settings, false); front(app, settings) }
            "look" -> {
                require(args.isEmpty() || args == listOf("--all")) { "z2-ui look [--all]" }
                enter(settings, false)
                device(app)
                val snapshot = runBlocking { AndroidUiLook.look(args.isNotEmpty()) { gateLook(app, settings, it) } }
                touch(app, snapshot.pkg)
                val screen = ActionRuntime.screen(app)
                success().put("screen", snapshot.screen).put("package", snapshot.pkg).put("app", label(app, snapshot.pkg))
                    .put("size", JSONArray(listOf(screen.width, screen.height))).put("rotation", screen.rotation)
                    .put("elements", JSONArray(snapshot.elements.mapIndexed { i, e -> e.json(i + 1) }))
            }
            "tap", "long", "enter" -> { count(args, 1); exclusive { operate(app, settings, verb, UiBridgePolicy.target(args[0]), null, false) } }
            "type" -> {
                val append = args.lastOrNull() == "--append"
                val rest = if (append) args.dropLast(1) else args
                count(rest, 2)
                exclusive { operate(app, settings, verb, UiBridgePolicy.target(rest[0]), rest[1], append) }
            }
            "key" -> { count(args, 1); exclusive { key(app, settings, args[0]) } }
            "open" -> { count(args, 1); exclusive { open(app, settings, args[0]) } }
            "wait" -> {
                require(args.size in 1..2) { "z2-ui wait SELECTOR [MS]" }
                val target = UiBridgePolicy.target(args[0]) as? Target.Find
                    ?: throw Refusal("bad_args", "wait takes text=, desc= or id=")
                val gen = enter(settings, false)
                val found = runBlocking {
                    AndroidUiLook.waitFor(target, UiBridgePolicy.waitMs(args.getOrNull(1)),
                        readable = { level(app, settings, it).rank >= Level.LOOK.rank }, alive = { alive(app, gen) })
                }
                if (found) success() else throw Refusal("not_found", "No element matched ${target.selector} in time")
            }
            "wait-change" -> {
                require(args.size <= 1) { "z2-ui wait-change [MS]" }
                val gen = enter(settings, false)
                val changed = runBlocking { AndroidUiLook.waitChange(UiBridgePolicy.waitMs(args.getOrNull(0))) { alive(app, gen) } }
                if (changed) success().put("changed", true) else throw Refusal("unchanged", "The screen did not change in time")
            }
            else -> throw Refusal("bad_args", "See z2-ui --help")
        }
    }

    /**
     * 操作を送る間 (承認待ちを含む) は z2-action を始めさせない。承認のバナーは前面に出るので、
     * 待っている間に AI が z2-action の座標操作で「許可」を押せてしまうため。
     */
    private fun <T> exclusive(block: () -> T): T {
        if (ActionRuntime.running) throw Refusal("busy", "An action macro is running; wait for z2-action to finish")
        if (!busyFlag.compareAndSet(false, true)) throw Refusal("busy", "Another z2-ui operation is running")
        try { return block() } finally { busyFlag.set(false) }
    }

    private fun count(args: List<String>, n: Int) { if (args.size != n) throw Refusal("bad_args", "Wrong arguments; see z2-ui --help") }

    private fun require(ok: Boolean, usage: () -> String) { if (!ok) throw Refusal("bad_args", usage()) }

    // --- 状態 ---

    /** 呼び出しの入口。溜まっている指示はここで 1 回だけ渡す。 */
    private fun enter(settings: UiBridgeSettings, operation: Boolean): Int {
        message?.let { message = null; throw Refusal("interrupted", it) }
        if (settings.stopped) throw Refusal("stopped", "Stopped from the notification; the user must turn AI operation on again")
        if (!settings.enabled) throw Refusal("disabled", "AI operation is off (commands → automation → AI)")
        if (!AndroidActions.connected()) throw Refusal("no_accessibility", "Enable z2term Android actions: z2-key permission")
        if (operation && paused) throw Refusal("paused", "Paused from the notification; wait until the user resumes")
        return generation.get()
    }

    /** 実行中の 1 手を続けてよいか。止める合図が来ていたら理由ごと投げる。 */
    private fun alive(app: Context, gen: Int) {
        if (generation.get() == gen) return
        val settings = UiBridgeSettings(app)
        message?.let { message = null; throw Refusal("interrupted", it) }
        if (settings.stopped) throw Refusal("stopped", "Stopped from the notification")
        if (paused) throw Refusal("paused", "Paused from the notification")
    }

    private fun device(app: Context) {
        val interactive = app.getSystemService(PowerManager::class.java).isInteractive
        val locked = app.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if (!interactive || locked) throw Refusal("locked", "The screen is off or locked")
    }

    fun pause(app: Context) {
        paused = true
        generation.incrementAndGet()
        showRunning(app)
    }

    /** 再開は通知と設定画面からだけ。AI が自分で一時停止を解けないよう、CLI には無い。 */
    fun resume(app: Context) {
        paused = false
        showRunning(app)
    }

    fun say(app: Context, text: String) {
        message = text
        generation.incrementAndGet()
        EventEmitter.emit(app, "ui_interrupt", name = "z2-ui", action = text)
        showRunning(app)
    }

    fun stop(app: Context) {
        val settings = UiBridgeSettings(app)
        settings.enabled = false
        settings.stopped = true
        paused = false
        message = null
        grants.clear()
        generation.incrementAndGet()
        pending?.let { it.answer = "deny"; it.latch.countDown() }
        AndroidUiLook.forget()
        main.post { NotificationManagerCompat.from(app).cancel(NOTIFY_RUNNING) }
    }

    /** 設定画面から OFF にしたときも、実行中の 1 手と承認待ちを止める。 */
    fun disabled(app: Context) {
        paused = false
        message = null
        grants.clear()
        generation.incrementAndGet()
        pending?.let { it.answer = "deny"; it.latch.countDown() }
        main.post { NotificationManagerCompat.from(app).cancel(NOTIFY_RUNNING) }
    }

    // --- 判定 ---

    /** 常に断る画面。機種ごとに名前の違う設定画面・インストーラーは Intent の解決先から足す。 */
    fun protectedPackages(context: Context): Set<String> = protectedCache ?: run {
        val pm = context.packageManager
        fun resolve(intent: Intent) = runCatching {
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.getOrNull()
        val resolved = listOf(
            Intent(Settings.ACTION_SETTINGS), Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
            Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://z2term/app.apk"), "application/vnd.android.package-archive"),
        ).mapNotNull(::resolve)
        (UiBridgePolicy.PROTECTED + context.packageName + resolved).also { protectedCache = it }
    }

    private fun level(app: Context, settings: UiBridgeSettings, pkg: String) =
        UiBridgePolicy.level(pkg, protectedPackages(app), settings.levels())

    private fun gateLook(app: Context, settings: UiBridgeSettings, pkg: String) {
        when (level(app, settings, pkg)) {
            Level.OFF -> refuseOff(app, pkg)
            else -> Unit
        }
    }

    private fun refuseOff(app: Context, pkg: String): Nothing =
        if (pkg in protectedPackages(app)) throw Refusal("protected", "$pkg is never available to AI operation")
        else throw Refusal("not_allowed", "$pkg is set to off")

    /** 操作してよいか。`ask` なら通知で人に聞く (まとめて許可の間は聞かない)。 */
    private fun gateOperate(app: Context, settings: UiBridgeSettings, pkg: String, what: String, gen: Int) {
        when (level(app, settings, pkg)) {
            Level.OFF -> refuseOff(app, pkg)
            Level.LOOK -> throw Refusal("read_only", "$pkg is read-only for AI operation")
            Level.ASK -> if ((grants[pkg] ?: 0L) <= SystemClock.elapsedRealtime()) {
                grants.remove(pkg)
                approve(app, app.getString(R.string.ui_bridge_ask_title), what, pkg, gen)
            }
            Level.AUTO -> Unit
        }
    }

    // --- 見る・操作する ---

    private fun status(app: Context, settings: UiBridgeSettings): JSONObject {
        val now = SystemClock.elapsedRealtime()
        val levels = settings.levels()
        return success().put("enabled", settings.enabled).put("stopped", settings.stopped).put("paused", paused)
            .put("accessibility", AndroidActions.connected())
            .put("default", (levels[UiBridgePolicy.DEFAULT_PATTERN] ?: UiBridgePolicy.DEFAULT_LEVEL).key)
            .put("apps", JSONObject(levels.filterKeys { it != UiBridgePolicy.DEFAULT_PATTERN }.mapValues { it.value.key }))
            .put("grant_minutes", settings.grantMinutes)
            .put("grants", JSONObject(grants.filterValues { it > now }.mapValues { (it.value - now) / 1000 }))
            .put("protected", JSONArray(protectedPackages(app).sorted()))
    }

    private fun front(app: Context, settings: UiBridgeSettings): JSONObject {
        val window = runBlocking { AndroidUiLook.window() }
        val screen = ActionRuntime.screen(app)
        val locked = app.getSystemService(KeyguardManager::class.java).isKeyguardLocked ||
            !app.getSystemService(PowerManager::class.java).isInteractive
        return success().put("package", window?.pkg ?: JSONObject.NULL).put("app", window?.pkg?.let { label(app, it) } ?: JSONObject.NULL)
            .put("level", window?.pkg?.let { level(app, settings, it).key } ?: JSONObject.NULL)
            .put("size", JSONArray(listOf(screen.width, screen.height))).put("rotation", screen.rotation).put("locked", locked)
    }

    private fun operate(app: Context, settings: UiBridgeSettings, op: String, target: Target, text: String?, append: Boolean): JSONObject {
        if (op == "type" && (text == null || text.length > 4096)) throw Refusal("bad_args", "Text must be at most 4096 characters")
        val gen = enter(settings, true)
        device(app)
        val window = runBlocking { AndroidUiLook.window() } ?: throw Refusal("no_window", "No foreground app window")
        gateOperate(app, settings, window.pkg, describe(app, op, window.pkg, target, text), gen)
        alive(app, gen)
        device(app)
        val changed = runBlocking {
            val before = AndroidUiLook.fingerprint()
            AndroidUiLook.act(op, target, text, append) { pkg ->
                if (pkg != window.pkg) throw Refusal("changed", "The foreground app changed; look again")
            }
            changedAfter(before)
        }
        touch(app, window.pkg)
        return success().put("changed", changed)
    }

    /** 操作の後 1 秒以内に画面が変わったか。遷移の完了までは保証しない。 */
    private suspend fun changedAfter(before: String?): Boolean {
        repeat(7) {
            delay(150)
            val now = AndroidUiLook.fingerprint()
            if (now != null && now != before) return true
        }
        return false
    }

    private fun key(app: Context, settings: UiBridgeSettings, name: String): JSONObject {
        if (name !in setOf("back", "home", "recents")) throw Refusal("bad_args", "z2-ui key back|home|recents")
        val gen = enter(settings, true)
        device(app)
        if (name == "back") {
            // 「戻る」は前面のアプリの中で効く (入力の破棄・ダイアログを閉じる等)。操作として扱う。
            val pkg = runBlocking { AndroidUiLook.window() }?.pkg ?: throw Refusal("no_window", "No foreground app window")
            gateOperate(app, settings, pkg, app.getString(R.string.ui_bridge_op_back, label(app, pkg)), gen)
            alive(app, gen)
        }
        val before = runBlocking { AndroidUiLook.fingerprint() }
        runBlocking { withContext(Dispatchers.Main) { AndroidActions.command(app, listOf(name)) } }
        return success().put("changed", runBlocking { changedAfter(before) })
    }

    private fun open(app: Context, settings: UiBridgeSettings, value: String): JSONObject {
        val gen = enter(settings, true)
        device(app)
        val pm = app.packageManager
        val uri = ':' in value
        val intent: Intent
        val pkg: String
        if (uri) {
            intent = Intent(Intent.ACTION_VIEW, Uri.parse(value))
            pkg = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
                ?: throw Refusal("not_found", "No app opens $value")
            // 解決先が "android" なら選択画面が出る (どのアプリが開くか決まらない)。
            if (pkg == "android") throw Refusal("ambiguous", "Several apps can open this URI; open the app by package")
            intent.setPackage(pkg)
        } else {
            pkg = try { ActionDefinition.packageName(value) } catch (_: IllegalArgumentException) {
                throw Refusal("bad_args", "z2-ui open PACKAGE|URI")
            }
            intent = pm.getLaunchIntentForPackage(pkg) ?: throw Refusal("not_found", "$pkg has no launchable screen")
        }
        when {
            // z2term へ戻るのは許す (開くだけで、z2term の画面は読めも押せもしない)。
            pkg == app.packageName && !uri -> Unit
            level(app, settings, pkg) == Level.OFF -> refuseOff(app, pkg)
            // URI は相手のアプリに何かをさせうる (作成画面・検索など)。操作として扱う。
            uri -> gateOperate(app, settings, pkg, app.getString(R.string.ui_bridge_op_open, label(app, pkg), value), gen)
        }
        alive(app, gen)
        runBlocking { withContext(Dispatchers.Main) { AppLaunch.launch(app, intent, "full") } }
        AndroidUiLook.forget()
        touch(app, pkg)
        return success().put("package", pkg)
    }

    private fun describe(app: Context, op: String, pkg: String, target: Target, text: String?): String {
        val what = when (target) {
            is Target.Ref -> AndroidUiLook.lastElement(target.n)?.let { it.text ?: it.desc ?: it.hint ?: it.id ?: it.role } ?: "#${target.n}"
            is Target.Find -> target.selector.value
            is Target.Point -> app.getString(R.string.ui_bridge_point, target.x, target.y)
        }
        val name = label(app, pkg)
        return when (op) {
            "tap" -> app.getString(R.string.ui_bridge_op_tap, name, what)
            "long" -> app.getString(R.string.ui_bridge_op_long, name, what)
            "type" -> app.getString(R.string.ui_bridge_op_type, name, what, text.orEmpty().let { if (it.length > 80) it.take(80) + "…" else it })
            else -> app.getString(R.string.ui_bridge_op_enter, name, what)
        }
    }

    fun label(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    // --- 許可の変更 ---

    private fun allow(app: Context, settings: UiBridgeSettings, args: List<String>): JSONObject {
        if (args.isEmpty()) return status(app, settings)
        val gen = enter(settings, false)
        if (args.size == 2 && args[0] == "--grant") {
            val minutes = args[1].toIntOrNull()?.takeIf { it in 1..UiBridgePolicy.GRANT_MAX_MINUTES }
                ?: throw Refusal("bad_args", "Minutes must be 1..${UiBridgePolicy.GRANT_MAX_MINUTES}")
            if (minutes > settings.grantMinutes) approve(app, app.getString(R.string.ui_bridge_change_title),
                app.getString(R.string.ui_bridge_change_grant, minutes), null, gen)
            settings.grantMinutes = minutes
            return status(app, settings)
        }
        count(args, 2)
        val pattern = args[0]
        if (pattern != UiBridgePolicy.DEFAULT_PATTERN) {
            try { ActionDefinition.packageName(pattern) } catch (_: IllegalArgumentException) {
                throw Refusal("bad_args", "z2-ui allow PACKAGE|* off|look|ask|auto|default")
            }
            if (pattern in protectedPackages(app)) throw Refusal("protected", "$pattern is never available to AI operation")
        }
        val levels = settings.levels()
        val fallback = levels[UiBridgePolicy.DEFAULT_PATTERN] ?: UiBridgePolicy.DEFAULT_LEVEL
        val old = levels[pattern] ?: fallback
        val new = when {
            args[1] != "default" -> Level.parse(args[1])
            pattern == UiBridgePolicy.DEFAULT_PATTERN -> UiBridgePolicy.DEFAULT_LEVEL
            else -> fallback
        }
        if (UiBridgePolicy.widens(old, new)) approve(app, app.getString(R.string.ui_bridge_change_title),
            app.getString(R.string.ui_bridge_change_level, if (pattern == UiBridgePolicy.DEFAULT_PATTERN)
                app.getString(R.string.ui_bridge_default_apps) else label(app, pattern), levelName(app, new)), null, gen)
        settings.setLevel(pattern, if (args[1] == "default") null else new)
        return status(app, settings)
    }

    fun levelName(context: Context, level: Level): String = context.getString(when (level) {
        Level.OFF -> R.string.ui_bridge_level_off
        Level.LOOK -> R.string.ui_bridge_level_look
        Level.ASK -> R.string.ui_bridge_level_ask
        Level.AUTO -> R.string.ui_bridge_level_auto
    })

    // --- 通知 ---

    private fun channels(app: Context) {
        val manager = app.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, app.getString(R.string.ui_bridge_channel), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) })
        // 承認は対象アプリの上にバナーで出したいので、高い重要度の別チャンネルにする。
        if (manager.getNotificationChannel(CHANNEL_ASK) == null) manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ASK, app.getString(R.string.ui_bridge_ask_channel), NotificationManager.IMPORTANCE_HIGH))
    }

    private fun broadcast(app: Context, action: String, code: Int, mutable: Boolean = false, extra: Intent.() -> Unit = {}) =
        PendingIntent.getBroadcast(app, code, Intent(app, UiBridgeReceiver::class.java).setAction(action)
            .putExtra(UiBridgeReceiver.EXTRA_TOKEN, controlToken).apply(extra),
            PendingIntent.FLAG_UPDATE_CURRENT or if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE)

    @Volatile private var appContext: Context? = null
    // 一時停止中は消さない (再開のボタンがここにしか無い)。
    private val idle = Runnable { if (!paused) appContext?.let { NotificationManagerCompat.from(it).cancel(NOTIFY_RUNNING) } }

    /** 呼ばれたアプリを覚え、常駐通知を出し直す。しばらく呼ばれなければ消す。 */
    private fun touch(app: Context, pkg: String) {
        lastPackage = pkg
        showRunning(app)
    }

    private fun showRunning(app: Context) = main.post {
        runCatching {
            channels(app)
            val pkg = lastPackage
            val left = pkg?.let { grants[it] }?.let { (it - SystemClock.elapsedRealtime() + 59_999) / 60_000 }?.takeIf { it > 0 }
            val text = listOfNotNull(pkg?.let { label(app, it) },
                left?.let { app.getString(R.string.ui_bridge_grant_left, it.toInt()) }).joinToString(" · ")
            val reply = RemoteInput.Builder(UiBridgeReceiver.EXTRA_TEXT).setLabel(app.getString(R.string.ui_bridge_say_hint)).build()
            val notification = NotificationCompat.Builder(app, CHANNEL)
                .setZ2SmallIcon(app)
                .setContentTitle(app.getString(if (paused) R.string.ui_bridge_paused_title else R.string.ui_bridge_running_title))
                .setContentText(text)
                .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
                .addAction(0, app.getString(if (paused) R.string.ui_bridge_resume else R.string.ui_bridge_pause),
                    broadcast(app, if (paused) UiBridgeReceiver.RESUME else UiBridgeReceiver.PAUSE, 7822))
                .addAction(NotificationCompat.Action.Builder(0, app.getString(R.string.ui_bridge_say),
                    broadcast(app, UiBridgeReceiver.SAY, 7823, mutable = true)).addRemoteInput(reply).build())
                .addAction(0, app.getString(R.string.ui_bridge_stop), broadcast(app, UiBridgeReceiver.STOP, 7824))
                .build()
            NotificationManagerCompat.from(app).notify(NOTIFY_RUNNING, notification)
            appContext = app
            main.removeCallbacks(idle)
            main.postDelayed(idle, IDLE_MS)
        }.onFailure { Log.w(TAG, "running notification failed", it) }
    }

    /**
     * 通知で人に聞き、答えが来るまで待つ。許可なら戻り、拒否・時間切れ・中断なら投げる。
     * [pkg] があるときだけ「このあと N 分は許可」を付ける (許可の変更には付けない)。
     */
    private fun approve(app: Context, title: String, text: String, pkg: String?, gen: Int) {
        val minutes = UiBridgeSettings(app).grantMinutes
        val request = Pending(UUID.randomUUID().toString(), minutes)
        pending = request
        fun answer(value: String, code: Int) = broadcast(app, UiBridgeReceiver.ANSWER, code) {
            putExtra(UiBridgeReceiver.EXTRA_NONCE, request.nonce).putExtra(UiBridgeReceiver.EXTRA_ANSWER, value)
            data = Uri.parse("z2term-ui:${request.nonce}/$value")
        }
        try {
            channels(app)
            val builder = NotificationCompat.Builder(app, CHANNEL_ASK)
                .setZ2SmallIcon(app)
                .setContentTitle(title).setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(false)
                .setDeleteIntent(answer("deny", 7825))
                .addAction(0, app.getString(R.string.ui_bridge_allow), answer("allow", 7826))
                .addAction(0, app.getString(R.string.ui_bridge_deny), answer("deny", 7827))
            if (pkg != null) builder.addAction(0, app.getString(R.string.ui_bridge_allow_for, minutes), answer("grant", 7828))
            try {
                NotificationManagerCompat.from(app).notify(NOTIFY_ASK, builder.build())
            } catch (e: SecurityException) {
                throw Refusal("denied", "Cannot ask for approval (notifications are blocked)")
            }
            val deadline = SystemClock.elapsedRealtime() + ANSWER_MS
            while (!request.latch.await(250, TimeUnit.MILLISECONDS)) {
                alive(app, gen)
                if (SystemClock.elapsedRealtime() > deadline) throw Refusal("denied", "No answer within 60 seconds")
            }
            alive(app, gen)
            when (request.answer) {
                "allow" -> Unit
                "grant" -> if (pkg != null) {
                    grants[pkg] = SystemClock.elapsedRealtime() + request.minutes * 60_000L
                    lastPackage = pkg
                    showRunning(app)
                }
                else -> throw Refusal("denied", "Denied from the notification")
            }
        } finally {
            pending = null
            NotificationManagerCompat.from(app).cancel(NOTIFY_ASK)
        }
    }

    /** [UiBridgeReceiver] から。印が違う放送 (AI が打ったもの・前のプロセスの通知) は捨てる。 */
    internal fun onBroadcast(app: Context, intent: Intent) {
        if (intent.getStringExtra(UiBridgeReceiver.EXTRA_TOKEN) != controlToken) {
            Log.w(TAG, "ignored z2-ui broadcast without the current token")
            return
        }
        when (intent.action) {
            UiBridgeReceiver.PAUSE -> pause(app)
            UiBridgeReceiver.RESUME -> resume(app)
            UiBridgeReceiver.STOP -> stop(app)
            UiBridgeReceiver.SAY -> RemoteInput.getResultsFromIntent(intent)?.getCharSequence(UiBridgeReceiver.EXTRA_TEXT)
                ?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { say(app, it.take(2000)) }
            UiBridgeReceiver.ANSWER -> {
                val request = pending ?: return
                if (intent.getStringExtra(UiBridgeReceiver.EXTRA_NONCE) != request.nonce) return
                request.answer = intent.getStringExtra(UiBridgeReceiver.EXTRA_ANSWER)
                request.latch.countDown()
            }
        }
    }

    // --- 記録 ---

    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)

    /** `~/.z2term/ui/log.jsonl`。入れた文字は記録せず文字数だけ、look の中身も残さない。 */
    private fun log(app: Context, verb: String, args: List<String>, result: JSONObject) = runCatching {
        val line = JSONObject().put("time", synchronized(iso) { iso.format(Date()) }).put("verb", verb)
            .put("package", lastPackage ?: JSONObject.NULL)
        when (verb) {
            "type" -> { line.put("target", args.firstOrNull()); line.put("chars", args.getOrNull(1)?.length ?: 0) }
            "say" -> line.put("chars", args.firstOrNull()?.length ?: 0)
            "look", "front", "pause", "stop", "status" -> Unit
            else -> line.put("args", JSONArray(args))
        }
        line.put("ok", result.optBoolean("ok"))
        result.optString("error").takeIf { it.isNotEmpty() }?.let { line.put("error", it) }
        val dir = File(app.filesDir, "shared_home/.z2term/ui").apply { mkdirs() }
        val file = File(dir, "log.jsonl")
        synchronized(this) {
            if (file.length() > 1_000_000) file.renameTo(File(dir, "log.1.jsonl"))
            file.appendText(line.toString() + "\n")
        }
    }.onFailure { Log.w(TAG, "log failed", it) }
}

/** 通知のボタン・返信欄の受け口。自アプリの PendingIntent からだけ届く (印は [UiBridge] が確かめる)。 */
class UiBridgeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        UiBridge.onBroadcast(context.applicationContext, intent)
    }

    companion object {
        const val PAUSE = "com.zerotoship.z2term.UI_PAUSE"
        const val RESUME = "com.zerotoship.z2term.UI_RESUME"
        const val SAY = "com.zerotoship.z2term.UI_SAY"
        const val STOP = "com.zerotoship.z2term.UI_STOP"
        const val ANSWER = "com.zerotoship.z2term.UI_ANSWER"
        const val EXTRA_TOKEN = "token"
        const val EXTRA_NONCE = "nonce"
        const val EXTRA_ANSWER = "answer"
        const val EXTRA_TEXT = "text"
    }
}
