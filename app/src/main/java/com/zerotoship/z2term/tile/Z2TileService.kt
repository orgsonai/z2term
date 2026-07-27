package com.zerotoship.z2term.tile

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import com.zerotoship.z2term.R
import com.zerotoship.z2term.service.HeadlessRun
import com.zerotoship.z2term.service.ScreenTimeout
import java.io.File

/**
 * クイック設定タイル (`z2-tile`)。通知シェードを下ろした先からマクロ / コマンドを 1 タップで走らせる。
 *
 * **なぜウィジェットと別に要るか**: ホーム画面ウィジェットは「ホーム画面へ戻る」必要がある。
 * クイック設定は**どのアプリを開いていても・ロック画面からでも** 2 スワイプで出るので、
 * 別のことをしている最中に届く唯一の入口になる。
 *
 * **常駐は増やさない**。`TileService` はシェードを開いている間しか OS にバインドされないので、
 * 置いただけでは何も動かない。実行は D1 ウィジェットと同じ [HeadlessRun] を通る。
 *
 * **約束は D1 ウィジェットのボタンと同じ**: タップで実行、実行中は緑 ([Tile.STATE_ACTIVE])、
 * もう一度タップで停止。入口ごとに違う操作感を作らない。
 *
 * ⚠ **ロック画面から素通しで走らせない**。[unlockAndRun] を通すので、ロックされていれば OS が
 * 解除を求め、解除できたときだけ走る。拾った人がシェードからコマンドを 1 発撃てる状態を作らない
 * (設定で切り替えるのではなく、常にこうする — 誤爆の実害がアプリの外に出る類の話なので、
 * 選べるようにしても選ぶ理由が無い)。
 *
 * 枠は manifest に書いた数だけで**実行中に増やせない** (Android の仕様) ため、[TileStore.COUNT] = 4
 * の固定。並べる場所は利用者がクイック設定の「編集」から決める (アプリが勝手に置くことは OS が禁止)。
 */
abstract class Z2TileService(private val slot: Int) : TileService() {

    private val main = Handler(Looper.getMainLooper())

    override fun onTileAdded() = render()

    override fun onStartListening() = render()

    override fun onClick() {
        val assigned = TileStore.get(this, slot)
        if (assigned == null) {
            // 未割り当ての枠。何も起きないと故障に見えるので、割り当て方を出す。
            Toast.makeText(
                applicationContext,
                getString(R.string.tile_unassigned_toast, slot),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        // ロック中なら OS が解除を求め、解除できたときだけ block が走る。
        unlockAndRun { toggle(assigned) }
    }

    /** 走っていれば止め、走っていなければ実行する (D1 ウィジェットのボタンと同じ約束)。 */
    private fun toggle(assigned: TileStore.Slot) {
        val app = applicationContext
        // z2-screen の枠は「掛かっているなら外す」。外すのはアプリ側で完結する操作なので、
        // わざわざ端末を起こして `z2-screen keepon off` を走らせない (proot の起動を待たずに済む)。
        // ⚠ 掛けるほうは今までどおりコマンドを走らせる — `1h` のような時間の読み方を
        // ここへ書き写すと、端末側の z2-screen と 2 か所で解釈がずれる。
        if (TileStore.isScreenKeepOn(assigned.command) && ScreenTimeout.keepOnUntil(app) != null) {
            Thread {
                runCatching { ScreenTimeout.cancel(app) }
                    .onFailure { Log.w(TAG, "tile $slot screen cancel failed", it) }
                render()
            }.apply { isDaemon = true; name = "tile-$slot-screen"; start() }
            return
        }
        val key = TileStore.runKey(slot)
        // stop() は最大 1 秒ブロックし、launch() は設定の読み出しで待つ。どちらも main では走らせない。
        Thread {
            runCatching {
                if (HeadlessRun.isRunning(key)) {
                    HeadlessRun.stop(key)
                } else {
                    HeadlessRun.launch(
                        context = app,
                        script = TileStore.scriptFor(app, assigned),
                        logFile = File(File(app.filesDir, "shared_home"), TileStore.LOG_REL),
                        name = key,
                        header = HeadlessRun.logHeader("tile $slot ${assigned.command}"),
                        // 終わったら緑を消す。onExit は drain スレッドから呼ばれる。
                        // ⚠ render() も呼ぶ — requestUpdate は「次にシェードが開かれたら」なので、
                        // シェードを下ろしたまま終わったコマンドの緑が消えずに残る。
                        onExit = { requestUpdate(app, slot); render() },
                    )
                }
            }.onFailure { Log.w(TAG, "tile $slot failed", it) }
            render()
        }.apply { isDaemon = true; name = "tile-$slot"; start() }
        // ⚠ ここで render() を足さないこと。起動/停止が済む前に描くと、まだ変わっていない状態
        // (押したのに灰色のまま・止めたのに緑のまま) が一瞬出て、押せていないように見える。
    }

    /** いまの割り当てと実行状態をタイルへ反映する。どのスレッドから呼んでもよい。 */
    private fun render() {
        val app = applicationContext
        main.post {
            val tile = qsTile ?: return@post
            val assigned = TileStore.get(app, slot)
            tile.icon = Icon.createWithResource(app, R.drawable.ic_notification)
            if (assigned == null) {
                tile.state = Tile.STATE_INACTIVE
                tile.label = getString(R.string.tile_label_empty, slot)
                tile.subtitle = getString(R.string.tile_subtitle_empty)
            } else if (TileStore.isScreenKeepOn(assigned.command)) {
                // この枠の緑は「掛かっている間」。残りはいま読んだ値で、シェードを開いている間は
                // 進まない (OS がタイルを描き直さない) ので、分より細かくは出さない。
                val until = ScreenTimeout.keepOnUntil(app)
                tile.state = if (until != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.label = assigned.label
                tile.subtitle = if (until == null) getString(R.string.tile_subtitle_screen_off)
                else {
                    val left = TileStore.remaining((until - System.currentTimeMillis()) / 1000)
                    getString(
                        when (left.unit) {
                            TileStore.RemainUnit.HOURS -> R.string.tile_subtitle_screen_hours
                            TileStore.RemainUnit.MINUTES -> R.string.tile_subtitle_screen_minutes
                            TileStore.RemainUnit.SECONDS -> R.string.tile_subtitle_screen_seconds
                        },
                        left.value
                    )
                }
            } else {
                val busy = HeadlessRun.isRunning(TileStore.runKey(slot))
                tile.state = if (busy) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.label = assigned.label
                tile.subtitle = getString(
                    if (busy) R.string.tile_subtitle_running else R.string.tile_subtitle_idle
                )
            }
            runCatching { tile.updateTile() }
        }
    }

    companion object {
        private const val TAG = "Z2Tile"

        /**
         * [slot] のタイルに描き直しを求める。実行が終わったときに緑を消すために使う。
         *
         * `requestListeningState` は「次にシェードが開かれたら `onStartListening` を呼べ」を
         * OS に頼むもの。タイルが置かれていなければ何も起きない (置く前提の API ではない)。
         */
        fun requestUpdate(context: Context, slot: Int) {
            val cls = classFor(slot) ?: return
            runCatching {
                TileService.requestListeningState(context, ComponentName(context, cls))
            }.onFailure { Log.w(TAG, "requestListeningState failed for $slot", it) }
        }

        /** 枠番号 → `TileService` の実装クラス。manifest の並びと 1 対 1。 */
        fun classFor(slot: Int): Class<out Z2TileService>? = when (slot) {
            1 -> Z2Tile1::class.java
            2 -> Z2Tile2::class.java
            3 -> Z2Tile3::class.java
            4 -> Z2Tile4::class.java
            else -> null
        }
    }
}

// 枠ごとに 1 クラス。中身は枠番号だけが違う — manifest に `TileService` を 1 個ずつ書く必要があり、
// 実行中に増やせないという Android の仕様に合わせた最小の形。
class Z2Tile1 : Z2TileService(1)
class Z2Tile2 : Z2TileService(2)
class Z2Tile3 : Z2TileService(3)
class Z2Tile4 : Z2TileService(4)
