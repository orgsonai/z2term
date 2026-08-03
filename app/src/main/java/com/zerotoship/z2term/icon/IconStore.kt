package com.zerotoship.z2term.icon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.core.content.edit
import androidx.core.graphics.drawable.IconCompat
import com.zerotoship.z2term.tile.TileStore
import java.util.concurrent.ConcurrentHashMap

/**
 * ステータスバーの通知アイコンとクイック設定タイルのアイコンを、端末から差し替える (`z2-icon`)。
 *
 * **なぜドット絵なのか**: ステータスバーのアイコンもタイルのアイコンも、**OS が単色で塗り直す**
 * (タイルは入 / 切で色が変わる)。色を持ち込む余地は最初から無く、実表示も 24px 前後しかない。
 * つまり指定できるのは**形 (どの点を塗るか) だけ**なので、24x24 の白黒グリッドがそのまま
 * 表現力の上限になる。PNG を受け取って縮小するより、書いたものと出るものが一致する。
 *
 * **保存は SharedPreferences**。タイルは[アプリのプロセスが生きていない状態]で読まれるので、
 * DataStore (非同期) は使えない ([TileStore] と同じ理由)。1 枚 600 バイト弱のテキストなので、
 * ファイルに逃がす必要も無い。
 *
 * ⚠ **差し替えられない場所がある**。クイック設定の「タイル編集」一覧に出るアイコンと、
 * ファイル選択 (SAF) のルートアイコンは manifest / リソース ID で決まり、実行中に変えられない
 * (Android の仕様)。並べた後のタイルと、実際に出る通知は差し替わる。
 */
object IconStore {

    /** ドット絵の一辺。ステータスバーの実表示 (24dp) と同じ。これより細かく描いても潰れる。 */
    const val GRID = 24

    /**
     * Bitmap を作るときの拡大率。24px のままだと OS 側の拡大で**にじむ**ので、
     * こちらで整数倍に引き伸ばしてから渡す (点の角を保つ)。
     */
    private const val SCALE = 4

    /** 通知アイコン (常駐通知・`z2-notify`・`z2-ask` すべて共通の 1 枚)。 */
    const val TARGET_NOTIFY = "notify"

    private const val TILE_PREFIX = "tile"
    private const val PREFS = "z2term_icon"

    /** その枠の絵を [autoAssign] が入れたことの印。手で入れた絵と区別するためだけに持つ。 */
    private const val KEY_AUTO_PREFIX = "auto_"

    /** 自分で描いて名前を付けた絵 (`z2-icon save`)。値は正規形テキスト。 */
    private const val KEY_USER_PREFIX = "sample_"

    /**
     * 自分の絵の並び順 (改行区切り・登録順)。
     *
     * ⚠ **名前順に並べ替えない**。一覧は番号で選ぶので、名前順にすると新しく保存した絵が
     * 途中へ割り込み、**前に覚えた番号が別の絵を指す**ようになる。末尾へ足せば番号は動かない
     * (同梱サンプルの並びを固定してあるのと同じ理由)。
     */
    private const val KEY_USER_ORDER = "sample_order"

    /** 自分の絵に付けられる名前の長さ。一覧で名前の列が流れない範囲。 */
    private const val NAME_MAX = 24

    /** 塗らない点として読む文字。これ以外の**空白でない文字はすべて塗る**。 */
    private const val BLANK_CHARS = ". 0-_\t"

    /** 正規形で「塗る」を表す文字 ([toText] の出力)。 */
    private const val INK = '#'

    /** 正規形で「塗らない」を表す文字 ([toText] の出力)。 */
    private const val BLANK = '.'

    /**
     * 作った Bitmap の使い回し。通知は 1 回出すたびに読まれるので、そのたびに
     * 96x96 を敷き直さない。[set] / [clear] で捨てる。
     */
    private val cache = ConcurrentHashMap<String, Bitmap>()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** タイル枠 [n] のアイコンの置き場を指す名前。 */
    fun tileTarget(n: Int): String = "$TILE_PREFIX$n"

    /**
     * 端末から来た指定を置き場の名前へ直す (Android 非依存・テスト用)。
     *
     * 受けるのは `notify` と**枠番号そのもの** (`3`)。`z2-tile` が枠を番号で呼ぶので、
     * こちらだけ `tile3` と書かせない。`tile3` と書かれても通す (打ち間違いにしない)。
     *
     * @return 置き場の名前。指定が不正なら null
     */
    fun normalizeTarget(raw: String): String? {
        val t = raw.trim().lowercase()
        if (t == TARGET_NOTIFY) return TARGET_NOTIFY
        val num = t.removePrefix(TILE_PREFIX).toIntOrNull() ?: return null
        return if (num in 1..TileStore.COUNT) tileTarget(num) else null
    }

    /** [normalizeTarget] 済みの名前がタイル枠なら枠番号、通知なら null。 */
    fun slotOf(target: String): Int? =
        if (target == TARGET_NOTIFY) null else target.removePrefix(TILE_PREFIX).toIntOrNull()

    /** 置き場の名前を全部 (通知 → 枠 1..[TileStore.COUNT] の順)。`z2-icon list` の並び。 */
    fun targets(): List<String> =
        listOf(TARGET_NOTIFY) + (1..TileStore.COUNT).map { tileTarget(it) }

    // --- 読み書き ---

    /** [target] のドット絵 (正規形テキスト)。未設定なら null。 */
    fun text(context: Context, target: String): String? =
        prefs(context).getString(target, null)?.takeIf { it.isNotBlank() }

    /**
     * [target] の絵が [autoAssign] の入れたものか。`z2-icon list` が「自動」と「自分で入れた」を
     * 出し分けるのに使う — どちらなのかが見えないと、`z2-icon auto` で戻せることに気付けない。
     */
    fun isAuto(context: Context, target: String): Boolean =
        prefs(context).getBoolean(KEY_AUTO_PREFIX + target, false)

    /** [target] のドット絵。未設定なら null。 */
    fun mask(context: Context, target: String): BooleanArray? =
        text(context, target)?.let { runCatching { parse(it) }.getOrNull() }

    /**
     * [target] へドット絵を割り当て、**入った絵を返す**。
     *
     * 返すのは、呼び出し側が保存し直したものを読み戻さずにそのままプレビューへ回せるようにするため
     * (読み戻す形にすると「保存できていない」場合に空の絵をプレビューしようとして落ちる)。
     *
     * @throws IllegalArgumentException 絵が読めない・大きすぎる・1 点も塗られていない
     */
    fun set(context: Context, target: String, art: String): BooleanArray {
        val parsed = parse(art)
        prefs(context).edit {
            putString(target, toText(parsed))
            // 手で入れた絵には印を残さない = 以後 [autoAssign] が触らない。
            remove(KEY_AUTO_PREFIX + target)
        }
        cache.remove(target)
        return parsed
    }

    /**
     * タイル枠 [slot] へ、割り当てた [command] に合う絵を**自動で**入れる
     * ([IconSamples.guess] が当てる)。
     *
     * ⚠ **自分で決めた絵は絶対に上書きしない**。触ってよいのは「まだ絵が無い枠」と
     * 「前にここが自動で入れた枠」だけで、そのために自動で入れた印を別に持つ。
     * 印を持たずに「絵の有無」だけで判断すると、`z2-tile set` を打ち直すたびに
     * **自分で描いた絵が消える**か、逆に前のマクロの絵が残り続けるかのどちらかになる。
     *
     * ⚠ 合う絵が無ければ、**前に自動で入れた絵は消す**。枠の中身を別のマクロへ替えたのに
     * 前の絵だけ残ると、タイルの見た目が中身と食い違う。
     *
     * [force] は利用者が明示的に頼んだとき (`z2-icon auto`) にだけ true。**自分で入れた絵も
     * 上書きする**。自動を断る手段 (`z2-icon` で好きな絵を入れる) と、自動へ戻す手段の
     * 両方が要る — 片道だけだと、一度手で入れたら二度と自動に戻せない。
     *
     * @return 入れた絵の名前。何も入れなかったら null
     */
    fun autoAssign(context: Context, slot: Int, command: String, force: Boolean = false): String? {
        val target = tileTarget(slot)
        val wasAuto = prefs(context).getBoolean(KEY_AUTO_PREFIX + target, false)
        if (!force && text(context, target) != null && !wasAuto) return null
        val name = IconSamples.guess(command)
        if (name == null) {
            if (wasAuto || force) clear(context, target)
            return null
        }
        val parsed = runCatching { parse(IconSamples.get(name).orEmpty()) }.getOrNull() ?: return null
        prefs(context).edit {
            putString(target, toText(parsed))
            putBoolean(KEY_AUTO_PREFIX + target, true)
        }
        cache.remove(target)
        return name
    }

    /**
     * 枠 [slot] の絵が [autoAssign] の入れたものなら片付ける (`z2-tile clear` から呼ぶ)。
     *
     * ⚠ **手で入れた絵は残す**。割り当てを消しただけで自分の描いた絵まで消えると、置き直す
     * たびに描き直しになる。
     */
    fun clearAuto(context: Context, slot: Int) {
        val target = tileTarget(slot)
        if (prefs(context).getBoolean(KEY_AUTO_PREFIX + target, false)) clear(context, target)
    }

    /** [target] を既定のアイコンへ戻す。 */
    fun clear(context: Context, target: String) {
        prefs(context).edit {
            remove(target)
            remove(KEY_AUTO_PREFIX + target)
        }
        cache.remove(target)
    }

    // --- 自分の絵 (名前を付けて一覧へ足す) ---

    /**
     * 自分で保存した絵の名前を**登録順**に。同梱サンプルの後ろに続く並び。
     *
     * **なぜ要るか**: `z2-icon edit` で描いた絵はその対象にしか残らず、別の枠へ同じ絵を入れたく
     * なったら描き直すしかなかった。名前を付けて残せれば、同梱サンプルとまったく同じように
     * `pick` の一覧から選べる — **自分の絵と同梱の絵の間に段差を作らない**。
     */
    fun userSampleNames(context: Context): List<String> =
        prefs(context).getString(KEY_USER_ORDER, null)
            ?.split("\n")
            ?.filter { it.isNotBlank() && prefs(context).contains(KEY_USER_PREFIX + it) }
            ?: emptyList()

    /** 自分の絵 [name] の正規形テキスト。無ければ null。 */
    fun userSample(context: Context, name: String): String? =
        prefs(context).getString(KEY_USER_PREFIX + name, null)

    /**
     * 付けてよい名前か見て、正規化した名前を返す (Android 非依存・テスト用)。
     *
     * ⚠ **数字だけの名前を許さない**。一覧は番号でも名前でも選べるので、`3` という名前の絵を
     * 作れてしまうと「3 番」と「3 という名前」のどちらを指すのか決められなくなる。
     * ⚠ 空白とタブも許さない — 一覧は TSV で、名前に入ると列がずれる。
     *
     * @return 正規化した名前。使えない名前なら null
     */
    fun normalizeSampleName(raw: String): String? {
        val name = raw.trim()
        if (name.isEmpty() || name.length > NAME_MAX) return null
        if (name.toIntOrNull() != null) return null
        if (name.any { it.isWhitespace() || it.code < 0x20 }) return null
        return name
    }

    /**
     * いま [target] に入っている絵に [name] を付けて一覧へ足す。
     *
     * ⚠ **同梱サンプルと同じ名前は断る**。同名を許すと `z2-icon sample <名前>` がどちらを指すか
     * 決められない。同じ名前で保存し直したときは**上書き** (並び順はそのまま) — 描き直して
     * 保存し直すのはごく普通の流れで、そこで別名を強いる理由が無い。
     *
     * @return 保存した絵の正規形テキスト
     * @throws IllegalArgumentException 名前が使えない・[target] が既定のまま
     */
    fun saveUserSample(context: Context, target: String, name: String): String {
        val art = text(context, target)
            ?: throw IllegalArgumentException("z2-icon save: $target は既定のアイコンのままです")
        val key = normalizeSampleName(name)
            ?: throw IllegalArgumentException(
                "z2-icon save: その名前は使えません (空白と数字だけの名前は不可・$NAME_MAX 文字まで)"
            )
        require(IconSamples.get(key) == null) { "z2-icon save: $key は同梱の絵の名前です。別の名前を付けてください" }
        val order = userSampleNames(context)
        prefs(context).edit {
            putString(KEY_USER_PREFIX + key, art)
            if (key !in order) putString(KEY_USER_ORDER, (order + key).joinToString("\n"))
        }
        return art
    }

    /**
     * 自分の絵 [name] を一覧から消す。
     *
     * ⚠ **入れてある対象の絵は消さない**。一覧から下げるのと、いま出ているアイコンを既定へ戻すのは
     * 別の操作 (戻すのは `z2-icon clear`)。ここで両方やると、一覧の整理をしただけで
     * タイルの見た目が変わってしまう。
     */
    fun deleteUserSample(context: Context, name: String): Boolean {
        if (userSample(context, name) == null) return false
        prefs(context).edit {
            remove(KEY_USER_PREFIX + name)
            putString(KEY_USER_ORDER, userSampleNames(context).filter { it != name }.joinToString("\n"))
        }
        return true
    }

    /**
     * 同梱と自分の絵を合わせた一覧 (この順が `pick` の番号)。
     *
     * @return 名前 → 自分の絵なら true
     */
    fun allSampleNames(context: Context): List<Pair<String, Boolean>> =
        IconSamples.names().map { it to false } + userSampleNames(context).map { it to true }

    /** 番号 (1 始まり) か名前で絵を引く。無ければ null。 */
    fun findSample(context: Context, key: String): String? {
        val k = key.trim()
        val byIndex = k.toIntOrNull()?.let { allSampleNames(context).getOrNull(it - 1)?.first }
        val name = byIndex ?: k
        return IconSamples.get(name) ?: userSample(context, name)
    }

    /**
     * いま [target] に入っている絵の**名前**。同梱にも自分の絵にも無いものは null。
     *
     * **なぜ要るか**: `z2-icon list` が「変えてある」ことしか出さないと、枠を何枚も使ったときに
     * **どの枠に何の絵が入っているのか分からない** (利用者の指摘)。名前まで出せば一覧で見分けが
     * 付き、名前の無い絵は `z2-icon save` で名前を付ければよい、と次の一手も見える。
     */
    fun nameOf(context: Context, target: String): String? {
        val art = text(context, target) ?: return null
        builtinByArt[art]?.let { return it }
        return userSampleNames(context).firstOrNull { userSample(context, it) == art }
    }

    /**
     * 同梱サンプルの「絵 → 名前」。⚠ 同梱の絵は書かれたまま (余白や字がまちまち) なので、
     * [parse] を通した**正規形**で引き当てる — 保存されている絵も正規形なので、これで一致する。
     */
    private val builtinByArt: Map<String, String> by lazy {
        IconSamples.names().mapNotNull { name ->
            IconSamples.get(name)
                ?.let { runCatching { toText(parse(it)) }.getOrNull() }
                ?.let { it to name }
        }.toMap()
    }

    // --- Android へ渡す形 ---

    /** [target] の Bitmap。未設定なら null (= 既定のリソースを使えの意味)。 */
    fun bitmap(context: Context, target: String): Bitmap? {
        cache[target]?.let { return it }
        val m = mask(context, target) ?: return null
        val bmp = render(m)
        cache[target] = bmp
        return bmp
    }

    /**
     * 通知の小アイコン用。未設定なら null。
     *
     * ⚠ 呼び出し側は `?: setSmallIcon(R.drawable.ic_notification)` で**必ず既定へ落ちること**。
     * アイコンの無い通知は Android が丸ごと捨てる。
     */
    fun notificationIcon(context: Context): IconCompat? =
        bitmap(context, TARGET_NOTIFY)?.let { IconCompat.createWithBitmap(it) }

    /** タイル枠 [n] のアイコン。未設定なら null。 */
    fun tileIcon(context: Context, n: Int): Icon? =
        bitmap(context, tileTarget(n))?.let { Icon.createWithBitmap(it) }

    /**
     * ドット絵を Bitmap にする (点を [SCALE] 倍の正方形で敷く)。
     *
     * 塗る点は**不透明な白**。OS がここへ状態の色を被せるので、こちらで色を決めても意味が無い
     * (被せない機種では白のまま出る — ステータスバーもタイルも暗い背景なので白で成り立つ)。
     */
    private fun render(m: BooleanArray): Bitmap {
        val size = GRID * SCALE
        val px = IntArray(size * size)
        for (y in 0 until GRID) {
            for (x in 0 until GRID) {
                if (!m[y * GRID + x]) continue
                for (dy in 0 until SCALE) {
                    val row = (y * SCALE + dy) * size + x * SCALE
                    for (dx in 0 until SCALE) px[row + dx] = 0xFFFFFFFF.toInt()
                }
            }
        }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
    }

    // --- テキスト <-> ドット絵 (Android 非依存・テスト用) ---

    /**
     * 書かれたドット絵を [GRID] x [GRID] の点へ読む。
     *
     * - 塗らない点は `.` ` ` `0` `-` `_`。**それ以外の文字はすべて塗る**ので、`#` でも `*` でも
     *   `X` でも好きな字で描ける (描いている本人が見分けやすい字を選べばよい)。
     * - **余白は無視して中央へ置き直す**。行や桁を [GRID] にきっちり合わせなくてよく、
     *   `$(cat)` が末尾の空行を落とすことも気にしなくて済む。
     * - 塗った範囲が [GRID] を超えたら弾く。⚠ 黙って切り詰めると、描いた本人にだけ
     *   「なぜか端が欠けたアイコン」が届く。
     *
     * @throws IllegalArgumentException 1 点も塗られていない・[GRID] に収まらない
     */
    internal fun parse(art: String): BooleanArray {
        val lines = art.replace("\r\n", "\n").replace('\r', '\n').split("\n")
        // 塗られた点だけを拾い、そのあと外接する四角を見る。
        val points = ArrayList<IntArray>()
        lines.forEachIndexed { y, line ->
            line.forEachIndexed { x, ch ->
                if (ch.code >= 0x20 && !BLANK_CHARS.contains(ch)) points.add(intArrayOf(x, y))
            }
        }
        require(points.isNotEmpty()) { "z2-icon: 1 点も塗られていません" }
        val minX = points.minOf { it[0] }
        val maxX = points.maxOf { it[0] }
        val minY = points.minOf { it[1] }
        val maxY = points.maxOf { it[1] }
        val w = maxX - minX + 1
        val h = maxY - minY + 1
        require(w <= GRID && h <= GRID) { "z2-icon: 絵が大きすぎます (${w}x${h})。${GRID}x${GRID} 以内で描いてください" }
        // 幅や高さが奇数だと 1 点ぶんは必ずどちらかへ寄る。余りを上と左へ多く回す
        // (= 絵は気持ち下と右へ寄る) と決め打ちにして、絵ごとに寄る向きが変わらないようにする。
        val offX = (GRID - w + 1) / 2 - minX
        val offY = (GRID - h + 1) / 2 - minY
        val mask = BooleanArray(GRID * GRID)
        points.forEach { (x, y) -> mask[(y + offY) * GRID + (x + offX)] = true }
        return mask
    }

    /** 点を正規形のテキストへ ([GRID] 行 x [GRID] 桁の `#` と `.`)。保存にも `z2-icon get` にも使う。 */
    internal fun toText(m: BooleanArray): String = (0 until GRID).joinToString("\n") { y ->
        (0 until GRID).map { x -> if (m[y * GRID + x]) INK else BLANK }.joinToString("")
    }

    /** 何も無いドット絵のテキスト (`z2-icon edit` が新規に開くひな形)。 */
    internal fun emptyText(): String = toText(BooleanArray(GRID * GRID))

    /**
     * 端末で見るためのプレビュー。
     *
     * ⚠ **上下 2 行を 1 文字に畳む** (`▀` `▄` `█`)。端末の文字は縦長なので、1 点 1 文字で出すと
     * 縦に間延びして**元の形に見えない**。半分に畳むとほぼ正方形になり、描いたものと同じ形が出る。
     */
    internal fun preview(m: BooleanArray): String = (0 until GRID step 2).joinToString("\n") { y ->
        (0 until GRID).map { x ->
            val top = m[y * GRID + x]
            val bottom = m[(y + 1) * GRID + x]
            when {
                top && bottom -> '█'
                top -> '▀'
                bottom -> '▄'
                else -> ' '
            }
        }.joinToString("")
    }
}
