package com.zerotoship.z2term.tile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import com.zerotoship.z2term.R
import com.zerotoship.z2term.icon.IconStore
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
 * 枠は manifest に書いた数だけで**実行中に増やせない** (Android の仕様) ため、[TileStore.COUNT]
 * 固定。並べる場所は利用者がクイック設定の「編集」から決める (アプリが勝手に置くことは OS が禁止)。
 *
 * アイコンは枠ごとに差し替えられる (`z2-icon set <枠> …` / [IconStore])。⚠ 差し替わるのは
 * **並べた後のタイル**だけで、「タイル編集」の一覧に出るアイコンは manifest 決め打ちのまま。
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
        unlockAndRun {
            collapsePanel()
            toggle(assigned)
        }
    }

    /**
     * クイック設定パネルを畳んでから走らせる。
     *
     * ⚠ **押した結果が見えるようにするため**。パネルが開いている間、Android は
     * ヘッドアップ通知 (画面上部のバナー) を出さずシェードに積むだけなので、走らせた
     * マクロが `z2-ask` で聞き返しても**パネルの下に隠れて答えられない**
     * (`remind.sh ask` をタイルから押すとまさにそうなっていた)。トーストも同様に埋もれる。
     *
     * ⚠ パネルを畳むには **Activity を起こすしかない** (OS の口がそれしか無い)。
     * 起こす [TileCollapseActivity] は画面を持たず、開いた瞬間に自分を閉じる踏み台。
     * Android 14 からは `PendingIntent` を渡す形だけが残っているので版で分ける。
     */
    private fun collapsePanel() {
        val intent = Intent(this, TileCollapseActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this, slot, intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            } else {
                @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
                startActivityAndCollapse(intent)
            }
        }.onFailure { Log.w(TAG, "collapse failed: ${it.message}") }
    }

    /** 走っていれば止め、走っていなければ実行する (D1 ウィジェットのボタンと同じ約束)。 */
    private fun toggle(assigned: TileStore.Slot) {
        val app = applicationContext
        // 入 / 切の 2 コマンドを持つ枠 (`--off`)。押すたびに反対側を走らせる。
        // ⚠ こちらは**止めない** — 利用者が「切るときはこれ」と書いた以上、走っているものを
        // 殺すのではなくそのコマンドを走らせるのが約束 (`z2-torch off` で消えるのであって、
        // `z2-torch on` のプロセスを殺しても消えない)。
        if (assigned.isPair) {
            val on = TileStore.isOn(app, slot)
            val next = if (on) assigned.offCommand.orEmpty() else assigned.command
            Thread {
                runCatching {
                    HeadlessRun.launch(
                        context = app,
                        script = TileStore.scriptFor(app, assigned, next),
                        logFile = File(File(app.filesDir, "shared_home"), TileStore.LOG_REL),
                        // 入と切で実行キーを分ける。同じキーだと、切るコマンドを走らせた瞬間に
                        // 入のほうを「実行中」と数えてしまう。
                        name = TileStore.runKey(slot) + if (on) "-off" else "-on",
                        header = HeadlessRun.logHeader("tile $slot $next"),
                    )
                    // 起動できたときだけ覚えを裏返す。失敗しても裏返すと、次の 1 回が
                    // 「切るつもりが切れていないのに切ったことになる」ですれ違う。
                    TileStore.setOn(app, slot, !on)
                }.onFailure { Log.w(TAG, "tile $slot pair failed", it) }
                render()
            }.apply { isDaemon = true; name = "tile-$slot-pair"; start() }
            return
        }
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
            // 端末から差し替えたドット絵があればそれを出す (`z2-icon set <枠> …`)。
            // ⚠ 色は乗らない — OS が入 / 切の色で塗り直すので、決まるのは形だけ ([IconStore])。
            tile.icon = IconStore.tileIcon(app, slot)
                ?: Icon.createWithResource(app, R.drawable.ic_notification)
            if (assigned == null) {
                tile.state = Tile.STATE_INACTIVE
                tile.label = getString(R.string.tile_label_empty, slot)
                tile.subtitle = getString(R.string.tile_subtitle_empty)
            } else if (assigned.isPair) {
                // 入 / 切の枠。緑 = アプリが「入にした」と覚えている状態 (実態を見に行く方法は
                // 無い。詳しくは TileStore.isOn)。
                val on = TileStore.isOn(app, slot)
                tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.label = assigned.label
                tile.subtitle = getString(
                    if (on) R.string.tile_subtitle_pair_on else R.string.tile_subtitle_pair_off
                )
            } else if (TileStore.isScreenKeepOn(assigned.command)) {
                // この枠の緑は「掛かっている間」。残りはいま読んだ値で、シェードを開いている間は
                // 進まない (OS がタイルを描き直さない) ので、分より細かくは出さない。
                val until = ScreenTimeout.keepOnUntil(app)
                tile.state = if (until != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                if (until == null) {
                    tile.label = assigned.label
                    tile.subtitle = getString(R.string.tile_subtitle_screen_off)
                } else {
                    val left = TileStore.remaining((until - System.currentTimeMillis()) / 1000)
                    // ⚠ 残りは**名前のほう**に足す。副題を一切表示しない機種があり (実機の
                    // Android 15 はアイコンと名前だけ)、副題に置くと誰にも読めない。
                    tile.label = TileStore.labelWithSuffix(
                        assigned.label,
                        getString(
                            when (left.unit) {
                                TileStore.RemainUnit.HOURS -> R.string.tile_remain_hours
                                TileStore.RemainUnit.MINUTES -> R.string.tile_remain_minutes
                                TileStore.RemainUnit.SECONDS -> R.string.tile_remain_seconds
                            },
                            left.value
                        )
                    )
                    // 副題が出る機種では、そちらに「押すと解除」まで書く。
                    tile.subtitle = getString(
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

        /**
         * 枠番号 → `TileService` の実装クラス。**manifest の並びと 1 対 1**。
         * 数は [TileStore.COUNT] と必ず揃えること (ずれた枠は消せなくなる)。
         */
        private val CLASSES: Array<Class<out Z2TileService>> = arrayOf(
            Z2Tile1::class.java, Z2Tile2::class.java, Z2Tile3::class.java,
            Z2Tile4::class.java, Z2Tile5::class.java, Z2Tile6::class.java,
            Z2Tile7::class.java, Z2Tile8::class.java, Z2Tile9::class.java,
            Z2Tile10::class.java, Z2Tile11::class.java, Z2Tile12::class.java,
        )

        fun classFor(slot: Int): Class<out Z2TileService>? = CLASSES.getOrNull(slot - 1)
    }
}

// 枠ごとに 1 クラス。中身は枠番号だけが違う — manifest に `TileService` を 1 個ずつ書く必要があり、
// 実行中に増やせないという Android の仕様に合わせた最小の形。
class Z2Tile1 : Z2TileService(1)
class Z2Tile2 : Z2TileService(2)
class Z2Tile3 : Z2TileService(3)
class Z2Tile4 : Z2TileService(4)
class Z2Tile5 : Z2TileService(5)
class Z2Tile6 : Z2TileService(6)
class Z2Tile7 : Z2TileService(7)
class Z2Tile8 : Z2TileService(8)
class Z2Tile9 : Z2TileService(9)
class Z2Tile10 : Z2TileService(10)
class Z2Tile11 : Z2TileService(11)
class Z2Tile12 : Z2TileService(12)
