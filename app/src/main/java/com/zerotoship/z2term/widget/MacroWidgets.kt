package com.zerotoship.z2term.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.zerotoship.z2term.MainActivity
import com.zerotoship.z2term.R
import com.zerotoship.z2term.service.HeadlessRun
import com.zerotoship.z2term.service.ServerDaemonManager
import com.zerotoship.z2term.service.SshEndpoint
import com.zerotoship.z2term.service.WhenManager
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.settings.ServerEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * マクロウィジェット 2 種 (マス目型 [StatusWidgetProvider] / 一覧型 [MacroListWidgetProvider]) で
 * 共通の処理: 状態の読み取り・上段の状態表示・マクロの実行と停止・1 つのマクロの見せ方。
 *
 * **見た目はここに書かない。** 色・背景・アイコン・文字の基準サイズは、すべて名前で
 * リソースを指すだけにしてある (`widget_state_*` / `widget_tile_*` / `widget_row_*` /
 * `ic_widget_*` / `widget_text_*`)。中身はレイアウト担当が決める — 名前の一覧は
 * 引き継ぎ書「ウィジェットの約束の表」が正本で、ここで名前を変えたら表も直すこと。
 */
internal object MacroWidgets {

    private const val TAG = "MacroWidgets"

    /** マクロ実行のまとめログ (`~/.z2term/widget/run.log`)。端末から `tail` して確かめられる。 */
    private const val LOG_REL = ".z2term/widget/run.log"

    /** マクロの終了コードを書き残す場所 (`~/.z2term/widget/rc/<ファイル名>`)。 */
    private const val RC_DIR_REL = ".z2term/widget/rc"

    /** [HeadlessRun] に渡す実行キー。ウィジェット発の実行だと分かる形にしておく。 */
    fun runKey(macro: String) = "widget-$macro"

    fun isRunning(macro: String): Boolean = HeadlessRun.isRunning(runKey(macro))

    /** 置かれているマクロウィジェットを 2 種とも描き直す (バックグラウンドスレッドから呼ぶこと)。 */
    fun renderEverything(context: Context) {
        runCatching { StatusWidgetProvider.renderAll(context) }
        runCatching { MacroListWidgetProvider.renderAll(context) }
    }

    // --- マクロ実行 ---

    private fun home(context: Context) = File(context.applicationContext.filesDir, "shared_home")

    private fun rcFile(context: Context, name: String) = File(File(home(context), RC_DIR_REL), name)

    /**
     * `~/.z2term/macros/<name>` を headless 実行する。マクロ名は実在するファイルからしか
     * 来ないが、シェルへは単一引用符で渡して展開させない (`z2-when` と同じ安全境界)。
     *
     * **終了コードの取り方**: マクロを子の `sh` で走らせ、終わったら `$?` を
     * `~/.z2term/widget/rc/<name>` へ書く。[HeadlessRun] の `onExit` は pty の EOF で呼ばれ、
     * プロセスの終了コードは渡ってこない (背景へ逃がしたデーモンを待つ作りなので、そこで
     * `waitFor` すると止まり得る) ため、スクリプトの側で残させる。止めたときは書かれないので、
     * ファイルが無い＝最後まで行かなかった、と読める。
     */
    fun run(context: Context, name: String) {
        if (name !in WidgetStore.availableMacros(context)) {
            Log.w(TAG, "macro not found: $name")
            return
        }
        val app = context.applicationContext
        val rc = rcFile(app, name)
        runCatching { rc.delete() }
        val q = HeadlessRun.shSingleQuote(name)
        val script = "export Z2_WIDGET_MACRO=$q; cd \"\$HOME\" 2>/dev/null; " +
            "sh \"\$HOME/.z2term/macros/\"$q; z2rc=\$?; " +
            "mkdir -p \"\$HOME/$RC_DIR_REL\" 2>/dev/null; " +
            "printf '%s\\n' \"\$z2rc\" > \"\$HOME/$RC_DIR_REL/\"$q"
        val ok = HeadlessRun.launch(
            context = app,
            script = script,
            logFile = File(home(app), LOG_REL),
            name = runKey(name),
            header = HeadlessRun.logHeader("widget $name"),
            // 終わったら実行中の表示を戻す (onExit は drain スレッドから呼ばれる)。
            onExit = {
                val code = runCatching { rc.readText().trim().toIntOrNull() }.getOrNull()
                runCatching { WidgetStore.setRunFinish(app, name, code) }
                renderEverything(app)
                // run.log を見ているライブ tail があれば、そちらも描き直す。
                runCatching { TailWidgetProvider.refresh(app) }
            },
        )
        if (ok) WidgetStore.setRunStart(app, name)
    }

    /** 実行中の [name] を止める。最大 1 秒ブロックするので必ず別スレッドから呼ぶ。 */
    fun stop(context: Context, name: String) {
        // 止める前に印を付ける (終了の通知が先に届いても「停止」と出せるように)。
        WidgetStore.markStopped(context, name)
        HeadlessRun.stop(runKey(name))
    }

    // --- 描画の共通部品 ---

    /**
     * レイアウトの基準文字サイズ (sp)。`res/values/widget_dimens.xml` の `widget_text_*` を読む。
     *
     * 0.8.255〜1.0.54 は基準値をコードに直書きしていたので、レイアウトで文字の大きさを変えると
     * 「文字の大きさ」設定を当てた瞬間に元へ戻っていた。基準値をリソースに置き、レイアウトと
     * コードが同じ値を見るようにした。⚠ `widget_text_*` は **sp で書くこと** (数値だけを読む)。
     */
    private fun baseSp(context: Context, @DimenRes res: Int): Float {
        val tv = TypedValue()
        context.resources.getValue(res, tv, true)
        return TypedValue.complexToFloat(tv.data)
    }

    /** [viewId] に「文字の大きさ」設定を当てる ([WidgetStore.scaled] と同じく差分で動かす)。 */
    fun textSize(context: Context, views: RemoteViews, appWidgetId: Int, viewId: Int, @DimenRes base: Int) {
        views.setTextViewTextSize(
            viewId, TypedValue.COMPLEX_UNIT_SP,
            WidgetStore.scaled(context, appWidgetId, baseSp(context, base))
        )
    }

    /**
     * 文字色を当てる。API 31+ は**色の名前**を渡してランチャー側で引かせる。
     *
     * 1.0.56 から配色は端末のライト / ダーク (と API 31+ は壁紙の配色) で変わる。こちらで色の値に
     * 直して渡すと、その時点の値で固まり、端末をダークへ切り替えても描き直すまで明るい地用の
     * 文字色のまま残る。名前で渡せば、ランチャーが切り替わった設定で引き直す。
     */
    fun textColor(context: Context, views: RemoteViews, viewId: Int, @ColorRes res: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColor(viewId, "setTextColor", res)
        } else {
            views.setTextColor(viewId, ContextCompat.getColor(context, res))
        }
    }

    fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * 上段 (`widget_status_header.xml`) を描く。マス目型・一覧型で同じ。
     *
     * ⚠ ⟳ のアイコンは**必ず書き戻す**。updateAppWidget は既存の View に操作を上乗せするだけ
     * なので、[showBusy] で「更新中」の絵にしたものは触らない限り残り続ける (「砂時計から
     * 戻らない」実機フィードバックの原因)。
     */
    fun bindHeader(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        s: Snapshot,
        refresh: PendingIntent,
        config: PendingIntent,
    ) {
        views.setImageViewResource(R.id.widget_refresh, R.drawable.ic_widget_refresh)
        views.setOnClickPendingIntent(R.id.widget_title, openAppIntent(context))
        views.setOnClickPendingIntent(R.id.widget_refresh, refresh)
        views.setOnClickPendingIntent(R.id.widget_config, config)

        listOf(
            R.id.widget_ssh_label, R.id.widget_servers_label,
            R.id.widget_rules_label, R.id.widget_battery_label,
        ).forEach { textSize(context, views, appWidgetId, it, R.dimen.widget_text_label) }
        listOf(
            R.id.widget_ssh, R.id.widget_servers, R.id.widget_rules, R.id.widget_battery,
        ).forEach { textSize(context, views, appWidgetId, it, R.dimen.widget_text_value) }
        textSize(context, views, appWidgetId, R.id.widget_footer, R.dimen.widget_text_footer)

        views.setTextViewText(R.id.widget_ssh, s.sshLine ?: context.getString(R.string.widget_ssh_none))
        // sshd が動いているときだけアクセント色にする (動いていない＝その宛先では入れない)。
        textColor(
            context, views, R.id.widget_ssh,
            if (s.sshdRunning) R.color.widget_accent else R.color.widget_text_secondary
        )
        // 「常駐 1/3」= 動いている本数 / 登録して有効にしてある本数。「自動化 2/5」も同じで、
        // 有効なルール数 / 全ルール数。分子だけだと **0 の理由** (登録が無いのか・常駐を
        // 止めているのか) が読めず、マクロの数とも混同された (実機フィードバック 2026-07-25)。
        views.setTextViewText(
            R.id.widget_servers,
            context.getString(R.string.widget_ratio, s.serversRunning, s.serversEnabled)
        )
        views.setTextViewText(
            R.id.widget_rules,
            context.getString(R.string.widget_ratio, s.rulesEnabled, s.rulesTotal)
        )
        views.setTextViewText(
            R.id.widget_battery,
            if (s.batteryLevel < 0) context.getString(R.string.widget_value_unknown)
            else context.getString(R.string.widget_battery_value, s.batteryLevel)
        )
    }

    /**
     * 下端の 1 行。読み直した時刻を必ず出すので、⟳ を押したときに**表示が変わったことで
     * 押せたと分かる**。あわせて直近に終わったマクロも出す (当日限り)。
     */
    fun bindFooter(context: Context, views: RemoteViews, s: Snapshot, hasMacros: Boolean, now: Long) {
        val hhmm = SimpleDateFormat("HH:mm", Locale.US)
        val hms = SimpleDateFormat("HH:mm:ss", Locale.US)
        val footer = if (!hasMacros) {
            context.getString(R.string.widget_no_macros)
        } else {
            val parts = ArrayList<String>(2)
            if (s.lastFinish != null && WidgetStore.isSameDay(s.lastFinish.second, now)) {
                parts.add(
                    context.getString(
                        R.string.widget_last_finish,
                        WidgetStore.label(s.lastFinish.first),
                        hhmm.format(Date(s.lastFinish.second))
                    )
                )
            }
            parts.add(context.getString(R.string.widget_updated_at, hms.format(Date(now))))
            parts.joinToString(" · ")
        }
        views.setViewVisibility(R.id.widget_footer, View.VISIBLE)
        views.setTextViewText(R.id.widget_footer, footer)
    }

    /** ⟳ を押した直後だけ「更新中」を見せる部分更新 (⟳ の絵とフッターだけを差し替える)。 */
    fun busyViews(context: Context, layout: Int): RemoteViews =
        RemoteViews(context.packageName, layout).apply {
            setImageViewResource(R.id.widget_refresh, R.drawable.ic_widget_refreshing)
            setTextViewText(R.id.widget_footer, context.getString(R.string.widget_refreshing))
            setViewVisibility(R.id.widget_footer, View.VISIBLE)
        }

    /**
     * マクロ 1 つぶんの表示 (マス目の 1 マス / 一覧の 1 行で共通)。
     *
     * View の id はマス目と一覧で違うので、[Ids] で受け取って同じ手順で埋める。
     */
    class Ids(
        val root: Int,
        val name: Int,
        val desc: Int,
        val state: Int,
        val timer: Int,
        val icon: Int,
    )

    /** 1 マス / 1 行の背景。マス目と一覧で別の絵を使えるよう 2 組ある。 */
    enum class Surface { TILE, ROW }

    fun bindMacro(
        context: Context,
        views: RemoteViews,
        appWidgetId: Int,
        ids: Ids,
        surface: Surface,
        name: String,
        status: MacroStatus,
        description: String,
    ) {
        val label = WidgetStore.label(name)
        textSize(context, views, appWidgetId, ids.name, R.dimen.widget_text_name)
        textSize(context, views, appWidgetId, ids.desc, R.dimen.widget_text_desc)
        textSize(context, views, appWidgetId, ids.state, R.dimen.widget_text_state)
        textSize(context, views, appWidgetId, ids.timer, R.dimen.widget_text_state)

        views.setTextViewText(ids.name, label)
        if (description.isBlank()) {
            views.setViewVisibility(ids.desc, View.GONE)
        } else {
            views.setViewVisibility(ids.desc, View.VISIBLE)
            views.setTextViewText(ids.desc, description)
        }
        views.setTextViewText(ids.state, stateText(context, status))
        textColor(context, views, ids.state, stateColor(status.kind))
        textColor(context, views, ids.timer, stateColor(status.kind))
        views.setImageViewResource(ids.icon, stateIcon(status.kind))
        views.setInt(ids.root, "setBackgroundResource", background(surface, status.kind))
        views.setContentDescription(
            ids.root,
            context.getString(
                if (status.kind == MacroStatus.Kind.RUNNING) R.string.widget_stop_desc
                else R.string.widget_run_desc,
                label
            )
        )

        // 実行中は経過時間を Chronometer に数えさせる (ランチャー側で進むので、こちらは
        // 再描画しなくてよい = 1 秒ごとに起こさない)。
        if (status.kind == MacroStatus.Kind.RUNNING && status.startAt > 0) {
            val base = SystemClock.elapsedRealtime() - (System.currentTimeMillis() - status.startAt)
            views.setChronometer(ids.timer, base, null, true)
            views.setViewVisibility(ids.timer, View.VISIBLE)
        } else {
            views.setChronometer(ids.timer, SystemClock.elapsedRealtime(), null, false)
            views.setViewVisibility(ids.timer, View.GONE)
        }
    }

    /**
     * 状態の 1 行 (「07:10–07:12 完了」など)。時刻は**今日の**開始〜終了。
     * 開始時刻は 0.8.216 からボタンに出していたもので、終了時刻を足した。
     */
    fun stateText(context: Context, s: MacroStatus): String {
        val hhmm = SimpleDateFormat("HH:mm", Locale.US)
        val start = if (s.startAt > 0) hhmm.format(Date(s.startAt)) else ""
        val fin = if (s.finishAt > 0) hhmm.format(Date(s.finishAt)) else ""
        val span = if (fin.isEmpty() || fin == start) start else "$start–$fin"
        return when (s.kind) {
            MacroStatus.Kind.IDLE -> context.getString(R.string.widget_state_idle)
            MacroStatus.Kind.RUNNING -> context.getString(R.string.widget_state_running)
            MacroStatus.Kind.DONE -> context.getString(R.string.widget_state_done, span)
            MacroStatus.Kind.FAILED -> context.getString(R.string.widget_state_failed, span, s.exitCode ?: -1)
            MacroStatus.Kind.STOPPED -> context.getString(R.string.widget_state_stopped, span)
        }
    }

    @ColorRes
    private fun stateColor(kind: MacroStatus.Kind): Int = when (kind) {
        MacroStatus.Kind.IDLE -> R.color.widget_state_idle
        MacroStatus.Kind.RUNNING -> R.color.widget_state_running
        MacroStatus.Kind.DONE -> R.color.widget_state_done
        MacroStatus.Kind.FAILED -> R.color.widget_state_failed
        MacroStatus.Kind.STOPPED -> R.color.widget_state_stopped
    }

    /** 状態のアイコン。待機中は「押せば走る」、実行中は「押せば止まる」を表す。 */
    @DrawableRes
    private fun stateIcon(kind: MacroStatus.Kind): Int = when (kind) {
        MacroStatus.Kind.IDLE -> R.drawable.ic_widget_run
        MacroStatus.Kind.RUNNING -> R.drawable.ic_widget_stop
        MacroStatus.Kind.DONE -> R.drawable.ic_widget_done
        MacroStatus.Kind.FAILED -> R.drawable.ic_widget_failed
        MacroStatus.Kind.STOPPED -> R.drawable.ic_widget_stopped
    }

    @DrawableRes
    private fun background(surface: Surface, kind: MacroStatus.Kind): Int = when (surface) {
        Surface.TILE -> when (kind) {
            MacroStatus.Kind.IDLE -> R.drawable.widget_tile_idle
            MacroStatus.Kind.RUNNING -> R.drawable.widget_tile_running
            MacroStatus.Kind.DONE -> R.drawable.widget_tile_done
            MacroStatus.Kind.FAILED -> R.drawable.widget_tile_failed
            MacroStatus.Kind.STOPPED -> R.drawable.widget_tile_stopped
        }
        Surface.ROW -> when (kind) {
            MacroStatus.Kind.IDLE -> R.drawable.widget_row_idle
            MacroStatus.Kind.RUNNING -> R.drawable.widget_row_running
            MacroStatus.Kind.DONE -> R.drawable.widget_row_done
            MacroStatus.Kind.FAILED -> R.drawable.widget_row_failed
            MacroStatus.Kind.STOPPED -> R.drawable.widget_row_stopped
        }
    }

    /**
     * ウィジェットに出す状態のひとまとめ。全ウィジェットで 1 回だけ読んで使い回す
     * (置いてある数だけファイルを読み直さない)。
     */
    data class Snapshot(
        val sshLine: String?,
        val sshdRunning: Boolean,
        val serversRunning: Int,
        /** 登録済みで有効な常駐サーバーの件数 (= 起動対象の数)。「常駐 1/3」の分母。 */
        val serversEnabled: Int,
        val rulesEnabled: Int,
        /** ルールファイルの総数。「自動化 2/5」の分母。 */
        val rulesTotal: Int,
        val batteryLevel: Int,
        /** 直近に**終わった**マクロと時刻。フッターで「止めたのではなく終わった」ことを示す。 */
        val lastFinish: Pair<String, Long>?,
    ) {
        companion object {
            fun read(context: Context): Snapshot {
                val settings = runCatching {
                    runBlocking { AppSettings(context).flow.first() }
                }.getOrNull()
                val distroId = settings?.distroId.orEmpty()
                // 「常駐 1/3」の分母。ServerDaemonManager.start が起動対象にする条件
                // (command が空でなく enabled) と揃えて数える。
                val enabledServers = runCatching {
                    ServerEntry.decode(settings?.serverEntries)
                        .count { it.enabled && it.command.isNotBlank() }
                }.getOrDefault(0)
                val rules = runCatching { WhenManager.loadRules(context) }.getOrDefault(emptyList())

                // status ファイルは supervisor が落ちても残るので、supervisor が生きているときだけ
                // 数える。常駐サーバーが動いていればフォアグラウンドサービスがこのプロセスを
                // 生かしているので、ここで見る isRunning は実態と一致する (プロセスごと死んでいれば
                // サーバーも死んでいる = 0 が正しい)。
                val running = if (ServerDaemonManager.isRunning) {
                    runCatching { ServerDaemonManager.readStatus(context) }
                        .getOrDefault(emptyList())
                        .filter { it.state == "running" }
                } else {
                    emptyList()
                }

                val batt = runCatching {
                    context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                }.getOrNull()
                val lvl = batt?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batt?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

                return Snapshot(
                    sshLine = if (distroId.isEmpty()) null
                    else SshEndpoint.sshCommand(context, distroId),
                    // コマンド文字列に sshd が現れる常駐サーバーが走っているか (`sshd --lan` 等)。
                    sshdRunning = running.any { it.command?.contains("sshd") == true },
                    serversRunning = running.size,
                    serversEnabled = enabledServers,
                    rulesEnabled = rules.count { it.enabled },
                    rulesTotal = rules.size,
                    batteryLevel = if (lvl >= 0 && scale > 0) lvl * 100 / scale else -1,
                    lastFinish = WidgetStore.lastFinish(context),
                )
            }
        }
    }
}
