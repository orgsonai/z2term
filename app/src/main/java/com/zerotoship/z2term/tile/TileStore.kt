package com.zerotoship.z2term.tile

import android.content.ComponentName
import android.content.Context
import com.zerotoship.z2term.settings.SharedPrefsPortable
import android.content.pm.PackageManager
import androidx.core.content.edit
import com.zerotoship.z2term.service.HeadlessRun
import com.zerotoship.z2term.widget.WidgetStore

/**
 * クイック設定タイル (`z2-tile`) の割り当て。
 *
 * ホーム画面ウィジェット (D1) と同じく**アプリのプロセスが生きていない状態**で読まれるので、
 * DataStore (非同期) ではなく SharedPreferences を使う ([WidgetStore] と同じ理由)。
 *
 * **枠数は manifest 決め打ち**。クイック設定タイルは manifest に 1 個ずつ `TileService` を書く
 * 必要があり、実行中に増やせない (Android の仕様)。使わない枠は [syncEnabledTiles] が一覧から
 * 消すので、多めに用意しておくことの実害が無い。
 */
object TileStore {

    /**
     * タイルの枠数。manifest の `Z2Tile1`〜`Z2Tile12` と**必ず一致させること**。
     *
     * 0.8.294 で 4 → 12。マクロが増えると 4 枠では足りない、という実機からの指摘による。
     * 割り当ての無い枠は編集画面の一覧に出ない ([syncEnabledTiles]) ので、増やしても
     * 使わない人の目には触れない。
     *
     * ⚠ **減らしてはいけない**。減らした先の枠に残った割り当ては、`z2-tile clear` の
     * 範囲検査から外れて**消すことも押すこともできなくなる**。増やす方向だけが安全。
     */
    const val COUNT = 12

    /**
     * `Z2Tile1`〜`Z2Tile4` の完全修飾名の前半。⚠ manifest の `android:name` と
     * [Z2TileService] のサブクラス名の**両方**と一致させること (どれか 1 つでもずれると、
     * その枠は一覧から消せないか、逆に消えたまま戻せなくなる)。
     */
    private const val TILE_CLASS_PREFIX = "com.zerotoship.z2term.tile.Z2Tile"

    private const val PREFS = "z2term_tile"
    private const val KEY_CMD_PREFIX = "cmd_"
    private const val KEY_LABEL_PREFIX = "label_"
    private const val KEY_OFF_PREFIX = "off_"
    private const val KEY_ON_PREFIX = "on_"

    /** タイルに出す表示名の上限。クイック設定は狭いので、長い名前は切り詰めて出す。 */
    const val MAX_LABEL_CHARS = 12

    /**
     * 1 枠の割り当て。[command] は**マクロのファイル名** (`backup.sh`) か
     * **そのまま走らせるコマンド** (`z2-screen keepon 1h`) のどちらか ([scriptFor] 参照)。
     *
     * [offCommand] があれば**入 / 切の 2 コマンド**として扱う (`--off`)。`z2-torch on` のように
     * 「掛けるコマンドと外すコマンドが別」のものを 1 枚のタイルで往復させるための形。
     */
    data class Slot(
        val n: Int,
        val command: String,
        val label: String,
        val offCommand: String? = null
    ) {
        /** 入 / 切の 2 コマンドを持つ枠か。緑の意味がこれで変わる ([Z2TileService])。 */
        val isPair: Boolean get() = offCommand != null
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** [n] (1〜[COUNT]) の割り当て。未設定なら null。 */
    fun get(context: Context, n: Int): Slot? {
        if (n !in 1..COUNT) return null
        val cmd = prefs(context).getString(KEY_CMD_PREFIX + n, null)?.takeIf { it.isNotBlank() }
            ?: return null
        val label = prefs(context).getString(KEY_LABEL_PREFIX + n, null).orEmpty()
        val off = prefs(context).getString(KEY_OFF_PREFIX + n, null)?.takeIf { it.isNotBlank() }
        return Slot(n, cmd, labelFor(cmd, label), off)
    }

    /**
     * 入 / 切の枠 ([Slot.isPair]) が「いま入っているか」。
     *
     * ⚠ **これはアプリが覚えているだけ**で、実際に点いているかを見に行っているのではない
     * (`z2-torch` の光を Android から読む方法は無い)。端末から直接 `z2-torch off` を打つと
     * タイルの表示だけ入のまま残る。`z2-screen` を特別扱いしているのは、あちらだけは
     * **アプリが実態を持っている**ため ([isScreenKeepOn])。
     */
    fun isOn(context: Context, n: Int): Boolean =
        prefs(context).getBoolean(KEY_ON_PREFIX + n, false)

    /** [isOn] を書き換える。押したコマンドが**起動できたときだけ**呼ぶこと。 */
    fun setOn(context: Context, n: Int, on: Boolean) {
        prefs(context).edit { putBoolean(KEY_ON_PREFIX + n, on) }
    }

    /** 全枠 (未設定の枠は含まない)。 */
    fun all(context: Context): List<Slot> = (1..COUNT).mapNotNull { get(context, it) }

    /**
     * [n] へ割り当てる。[label] が空なら [labelFor] が [command] から作る。
     * @throws IllegalArgumentException 枠番号が範囲外・コマンドが空
     */
    fun set(context: Context, n: Int, command: String, label: String = "", offCommand: String = "") {
        require(n in 1..COUNT) { "z2-tile: 枠は 1〜$COUNT です" }
        require(command.isNotBlank()) { "z2-tile: 割り当てるコマンドがありません" }
        prefs(context).edit {
            putString(KEY_CMD_PREFIX + n, command)
            putString(KEY_LABEL_PREFIX + n, label)
            putString(KEY_OFF_PREFIX + n, offCommand)
            // 割り当て直したら「入」の記憶は捨てる。前の割り当ての入 / 切をそのまま持ち越すと、
            // 別のものを載せた 1 回目が切るほうから始まる。
            remove(KEY_ON_PREFIX + n)
        }
        syncEnabledTiles(context)
    }

    /** [n] の割り当てを消す。 */
    fun clear(context: Context, n: Int) {
        require(n in 1..COUNT) { "z2-tile: 枠は 1〜$COUNT です" }
        prefs(context).edit {
            remove(KEY_CMD_PREFIX + n)
            remove(KEY_LABEL_PREFIX + n)
            remove(KEY_OFF_PREFIX + n)
            remove(KEY_ON_PREFIX + n)
        }
        syncEnabledTiles(context)
    }

    /**
     * 枠 [n] をクイック設定の**一覧**に出すか (Android 非依存・テスト用)。
     *
     * 割り当ての無い枠まで [COUNT] 個並ぶと、タイルを使わない人のクイック設定の編集画面が
     * z2term で埋まる。**使うと決めた枠 (= [set] した枠) だけ**を出す。
     * 枠を 12 に増やせるのはこの仕組みがあるからで、ここを外すと増枠がそのまま迷惑になる。
     *
     * ⚠ **1 つも割り当てが無ければ 1 枚も出さない (0.8.271)**。0.8.260〜0.8.270 は
     * 「枠 1 だけは割り当てが無くても出す」としていた — 機能があることに気付ける場所を
     * アプリの外に 1 つ残す意図だったが、**使わない人には空の枠が消せずに残り続ける**
     * だけだった (実機で指摘)。⚠ 気付いてもらうための枠を、使わない人に押し付けない。
     * 割り当てれば出るので、入口は `z2-tile set` と設定画面の説明で足りる。
     */
    internal fun shouldEnable(n: Int, assigned: Boolean): Boolean = assigned

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
    /** 持ち出し用: 枠の割り当てをまるごと JSON へ ([SharedPrefsPortable])。 */
    fun exportRaw(context: Context): String = SharedPrefsPortable.toJson(prefs(context))

    /**
     * 持ち出しから戻す。**追加・更新**なので、バックアップに無い枠はそのまま残る。
     *
     * ⚠ 戻したら [syncEnabledTiles] まで通すこと (ここで通している)。割り当てだけ戻して
     * 一覧の同期を忘れると、**中身はあるのにクイック設定の編集画面に出てこない枠**ができる。
     */
    fun importRaw(context: Context, json: String) {
        SharedPrefsPortable.applyTo(prefs(context), json)
        syncEnabledTiles(context)
    }

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
     * 明示的な名前があればそれ。無ければ**先頭の語**で、マクロなら拡張子を落とす
     * (`backup.sh` → `backup` / `z2-screen keepon 1h` → `z2-screen`)。**タイルは名前が機能そのもの**
     * なので、コマンド全文を出して切れるより短い手掛かりを出す方が使える。
     *
     * ⚠ 引数付きのマクロ (`remind.sh ask` / `remind.sh peek`) は**どちらも同じ名前になる**ので、
     * 使い分けるなら `-l` を付ける (`z2-tile set 2 'remind.sh ask' -l リマインド`)。引数まで
     * 名前に畳み込まないのは、長い名前が機種によって黙って切れるため。⚠ 枠が増えて同じマクロを
     * 引数違いで並べる使い方が現実的になったので、`-l` を付ける場面はむしろ増えている。
     */
    internal fun labelFor(command: String, explicit: String): String {
        val raw = explicit.ifBlank {
            command.trim().substringBefore(' ').removeSuffix(".sh")
        }
        return if (raw.length > MAX_LABEL_CHARS) raw.take(MAX_LABEL_CHARS - 1) + "…" else raw
    }

    /**
     * 名前の後ろに残り時間などを足す (Android 非依存・テスト用)。
     *
     * ⚠ **タイルの副題 (`Tile.subtitle`) は機種によっては一切表示されない**。実機 (Android 15) では
     * アイコンと名前しか出ず、「残り 60 分」を副題に置いても**誰にも読めなかった**。
     * 出せる場所が名前しか無いので、状態はここへ畳み込む。
     *
     * 名前を削ってでも [suffix] は必ず残す — 押す前に知りたいのは「これは何か」ではなく
     * (自分で並べたのだから分かっている)「**あとどれだけか**」のほう。
     */
    internal fun labelWithSuffix(name: String, suffix: String): String {
        if (suffix.isBlank()) return name
        val room = MAX_LABEL_CHARS - suffix.length - 1
        if (room <= 0) return suffix
        val head = if (name.length > room) name.take(room - 1) + "…" else name
        return "$head $suffix"
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
     *
     * ⚠ **`--off` を明示した枠には効かせない** ([Slot.isPair] が優先)。書いたものが素直に
     * 効くほうを上に置く — 打った `--off` が黙って無視される作りは追いかけようがない。
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
     *
     * [command] は既定で [Slot.command]。入 / 切の枠 ([Slot.isPair]) では切るときに
     * [Slot.offCommand] を渡す。**切るほうもマクロ名で書ける** (判定は同じ道を通る)。
     */
    fun scriptFor(context: Context, slot: Slot, command: String = slot.command): String =
        scriptOf(slot.n, command, WidgetStore.availableMacros(context))

    /**
     * [scriptFor] の中身 (Android 非依存・テスト用)。[macros] は導入済みマクロのファイル名。
     *
     * ⚠ **先頭の語だけを見てマクロか判定する** (0.8.275)。それまでは割り当て全体との完全一致で、
     * `remind.sh ask` のように**引数を 1 つ付けた瞬間にコマンド扱いへ落ちていた**。マクロ置き場は
     * PATH に入っていないので `sh: remind.sh: not found` で終わり、**タイルは押しても無反応**
     * (失敗は `~/.z2term/tile/run.log` にしか出ない) という、外から原因の見えない壊れ方をした。
     * 1 本のマクロをサブコマンドで使い分ける書き方は自然に出てくるので、そちらを通す。
     *
     * 引数は**そのままシェルへ渡す** (`$HOME` や `$(…)` が効く)。マクロ名の方だけ単一引用符で
     * 囲むのは従来どおり — こちらは実在ファイル名しか来ないので、展開させる理由がない。
     */
    internal fun scriptOf(n: Int, command: String, macros: Collection<String>): String {
        val prefix = "export Z2_TILE=${HeadlessRun.shSingleQuote(n.toString())}; cd \"\$HOME\" 2>/dev/null; "
        val cmd = command.trim()
        val head = cmd.substringBefore(' ')
        val args = cmd.substringAfter(' ', "").trim()
        return if (head in macros) {
            val q = HeadlessRun.shSingleQuote(head)
            prefix + "export Z2_TILE_MACRO=$q; sh \"\$HOME/.z2term/macros/\"$q" +
                if (args.isEmpty()) "" else " $args"
        } else {
            prefix + cmd
        }
    }

    /** [HeadlessRun] の実行キー (ウィジェットの `widget-<名前>` と衝突させない)。 */
    fun runKey(n: Int): String = "tile-$n"

    /** 実行ログの置き場 (`~/.z2term/tile/run.log`)。ウィジェットと分けて混ざらないようにする。 */
    const val LOG_REL = ".z2term/tile/run.log"
}
