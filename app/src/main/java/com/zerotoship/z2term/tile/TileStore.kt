package com.zerotoship.z2term.tile

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.zerotoship.z2term.service.HeadlessRun
import com.zerotoship.z2term.widget.WidgetStore

/**
 * クイック設定タイル (`z2-tile`) の割り当て。
 *
 * ホーム画面ウィジェット (D1) と同じく**アプリのプロセスが生きていない状態**で読まれるので、
 * DataStore (非同期) ではなく SharedPreferences を使う ([WidgetStore] と同じ理由)。
 *
 * **枠は 4 つ固定**。クイック設定タイルは manifest に 1 個ずつ `TileService` を書く必要があり、
 * 実行中に増やせない (Android の仕様)。D1 ウィジェットのボタンと同じ数に揃えて、
 * 「どこに置いても 4 つ」で覚え方を 1 つにする。
 */
object TileStore {

    /** タイルの枠数。manifest の `Z2Tile1`〜`Z2Tile4` と**必ず一致させること**。 */
    const val COUNT = 4

    /**
     * `Z2Tile1`〜`Z2Tile4` の完全修飾名の前半。⚠ manifest の `android:name` と
     * [Z2TileService] のサブクラス名の**両方**と一致させること (どれか 1 つでもずれると、
     * その枠は一覧から消せないか、逆に消えたまま戻せなくなる)。
     */
    private const val TILE_CLASS_PREFIX = "com.zerotoship.z2term.tile.Z2Tile"

    private const val PREFS = "z2term_tile"
    private const val KEY_CMD_PREFIX = "cmd_"
    private const val KEY_LABEL_PREFIX = "label_"

    /** タイルに出す表示名の上限。クイック設定は狭いので、長い名前は切り詰めて出す。 */
    const val MAX_LABEL_CHARS = 12

    /**
     * 1 枠の割り当て。[command] は**マクロのファイル名** (`backup.sh`) か
     * **そのまま走らせるコマンド** (`z2-screen keepon 1h`) のどちらか ([scriptFor] 参照)。
     */
    data class Slot(val n: Int, val command: String, val label: String)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** [n] (1〜[COUNT]) の割り当て。未設定なら null。 */
    fun get(context: Context, n: Int): Slot? {
        if (n !in 1..COUNT) return null
        val cmd = prefs(context).getString(KEY_CMD_PREFIX + n, null)?.takeIf { it.isNotBlank() }
            ?: return null
        val label = prefs(context).getString(KEY_LABEL_PREFIX + n, null).orEmpty()
        return Slot(n, cmd, labelFor(cmd, label))
    }

    /** 全枠 (未設定の枠は含まない)。 */
    fun all(context: Context): List<Slot> = (1..COUNT).mapNotNull { get(context, it) }

    /**
     * [n] へ割り当てる。[label] が空なら [labelFor] が [command] から作る。
     * @throws IllegalArgumentException 枠番号が範囲外・コマンドが空
     */
    fun set(context: Context, n: Int, command: String, label: String = "") {
        require(n in 1..COUNT) { "z2-tile: 枠は 1〜$COUNT です" }
        require(command.isNotBlank()) { "z2-tile: 割り当てるコマンドがありません" }
        prefs(context).edit()
            .putString(KEY_CMD_PREFIX + n, command)
            .putString(KEY_LABEL_PREFIX + n, label)
            .apply()
        syncEnabledTiles(context)
    }

    /** [n] の割り当てを消す。 */
    fun clear(context: Context, n: Int) {
        require(n in 1..COUNT) { "z2-tile: 枠は 1〜$COUNT です" }
        prefs(context).edit()
            .remove(KEY_CMD_PREFIX + n)
            .remove(KEY_LABEL_PREFIX + n)
            .apply()
        syncEnabledTiles(context)
    }

    /**
     * 枠 [n] をクイック設定の**一覧**に出すか (Android 非依存・テスト用)。
     *
     * 割り当ての無い枠まで 4 つ並ぶと、タイルを使わない人のクイック設定の編集画面が
     * z2term で埋まる。使うと決めた枠 (= [set] した枠) だけを出す。
     * ⚠ **枠 1 だけは割り当てが無くても出す** — ここまで消すと「クイック設定に置ける」こと
     * 自体に気付ける場所がアプリの外に無くなり、機能があることを知っている人しか辿り着けない。
     */
    internal fun shouldEnable(n: Int, assigned: Boolean): Boolean = n == 1 || assigned

    /**
     * 割り当ての有無に合わせて `TileService` の有効 / 無効を揃える ([shouldEnable])。
     *
     * 枠は manifest 決め打ちで**増やせない**が、`PackageManager` で個別に**無効化はできる**ので、
     * 減らす方向だけは実行中に効かせられる。無効にした枠は編集画面の一覧からも消える。
     *
     * ⚠ **無効にすると、その枠が既にクイック設定に並んでいたらパネルからも外れる** (OS が外す)。
     * もう一度割り当てても自動では戻らず並べ直しになるので、`z2-tile clear` は
     * 「割り当てを消す」だけでなく「タイルを 1 枚片付ける」操作でもある。
     */
    fun syncEnabledTiles(context: Context) {
        val app = context.applicationContext
        val pm = app.packageManager
        for (n in 1..COUNT) {
            val want = shouldEnable(n, get(app, n) != null)
            val component = ComponentName(app, "$TILE_CLASS_PREFIX$n")
            val current = runCatching { pm.getComponentEnabledSetting(component) }.getOrNull()
                ?: continue
            // DEFAULT は manifest の android:enabled (= 既定 true) を指す。ENABLED と同一視しないと、
            // まだ一度も触っていない枠へ毎回書き込むことになる。
            val now = current != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            if (now == want) continue
            runCatching {
                pm.setComponentEnabledSetting(
                    component,
                    if (want) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    // ⚠ DONT_KILL_APP は必須。付けないと、端末から z2-tile を打った瞬間に
                    // 自分のプロセスごと落ちる (= 打ったコマンドの応答が返らない)。
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }

    /**
     * タイルに出す名前を決める (Android 非依存・テスト用)。
     *
     * 明示的な名前があればそれ。無ければ、マクロなら拡張子を落とした名前 (`backup.sh` → `backup`)、
     * コマンドなら**先頭の語**(`z2-screen keepon 1h` → `z2-screen`)。**タイルは名前が機能そのもの**
     * なので、コマンド全文を出して切れるより短い手掛かりを出す方が使える。
     */
    internal fun labelFor(command: String, explicit: String): String {
        val raw = explicit.ifBlank {
            if (command.endsWith(".sh")) command.removeSuffix(".sh")
            else command.trim().substringBefore(' ')
        }
        return if (raw.length > MAX_LABEL_CHARS) raw.take(MAX_LABEL_CHARS - 1) + "…" else raw
    }

    /**
     * 割り当てが **`z2-screen keepon <時間>`** か (Android 非依存・テスト用)。
     *
     * このタイルだけは緑が「走っている間」ではなく**「消灯しないが掛かっている間」**を指す。
     * `z2-screen keepon 1h` は設定を書いて**すぐ終わる**ので、実行中で緑を決めると
     * 押した瞬間しか光らず、シェードを開き直すと切れているのか掛かっているのか分からない
     * (実機で指摘された)。掛かっているかどうかはアプリ側 (`ScreenTimeout`) が持っているので、
     * **そちらを正本にする** — 端末から `z2-screen keepon off` を打ってもタイルが揃うのはこのため。
     *
     * ⚠ `keepon off` と `status` は対象外。どちらも状態を持たない一度きりの操作で、
     * これらを緑にすると「押すと消える緑」というちぐはぐな見え方になる。
     */
    internal fun isScreenKeepOn(command: String): Boolean {
        val parts = command.trim().split(Regex("\\s+"))
        return parts.size >= 3 && parts[0] == "z2-screen" && parts[1] == "keepon" && parts[2] != "off"
    }

    /** [remaining] の単位。タイルに出す文言 (`残り %d 分`) を選ぶためだけのもの。 */
    enum class RemainUnit { HOURS, MINUTES, SECONDS }

    /** 残り時間の丸めた表し方。 */
    data class Remaining(val unit: RemainUnit, val value: Long)

    /**
     * 残り [seconds] 秒を、タイルに出す 1 つの単位へ丸める (Android 非依存・テスト用)。
     *
     * ⚠ **切り上げる**。切り捨てると `keepon 1h` を掛けた直後に「残り 59 分」と出て、
     * 頼んだ時間より短く見える。⚠ 秒まで落ちるのは 1 分未満のときだけ — クイック設定は
     * シェードを下ろした瞬間の値で止まるので、秒を出しても読んでいる間に古くなる。
     */
    internal fun remaining(seconds: Long): Remaining {
        val s = seconds.coerceAtLeast(0)
        return when {
            s >= 3600 -> Remaining(RemainUnit.HOURS, (s + 3599) / 3600)
            s >= 60 -> Remaining(RemainUnit.MINUTES, (s + 59) / 60)
            else -> Remaining(RemainUnit.SECONDS, s)
        }
    }

    /**
     * [command] を実行するスクリプトを組む。
     *
     * **導入済みマクロのファイル名と一致すればマクロとして**、そうでなければ**コマンドとして**
     * 走らせる。⚠ ここで種別を選ばせない (`--macro` のようなフラグを作らない) のは、割り当てる側が
     * 「これはマクロか、コマンドか」を意識せずに済ませるため。`~/.z2term/macros/` にある名前を
     * 打てばマクロが走り、それ以外は端末に打ったのと同じになる。
     *
     * `Z2_TILE` に枠番号が入るので、同じマクロを複数の枠に置いて中で分岐することもできる。
     */
    fun scriptFor(context: Context, slot: Slot): String {
        val n = HeadlessRun.shSingleQuote(slot.n.toString())
        val prefix = "export Z2_TILE=$n; cd \"\$HOME\" 2>/dev/null; "
        return if (slot.command in WidgetStore.availableMacros(context)) {
            val q = HeadlessRun.shSingleQuote(slot.command)
            prefix + "export Z2_TILE_MACRO=$q; sh \"\$HOME/.z2term/macros/\"$q"
        } else {
            prefix + slot.command
        }
    }

    /** [HeadlessRun] の実行キー (ウィジェットの `widget-<名前>` と衝突させない)。 */
    fun runKey(n: Int): String = "tile-$n"

    /** 実行ログの置き場 (`~/.z2term/tile/run.log`)。ウィジェットと分けて混ざらないようにする。 */
    const val LOG_REL = ".z2term/tile/run.log"
}
