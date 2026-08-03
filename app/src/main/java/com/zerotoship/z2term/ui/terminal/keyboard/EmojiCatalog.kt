package com.zerotoship.z2term.ui.terminal.keyboard

import android.content.Context
import android.graphics.Paint
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 絵文字パッド ([KeyboardPad]) が並べる絵文字の一覧。
 *
 * ⚠ **端末のフォントが持っていない絵文字は出さない。** 持っていない字は □ (豆腐) で描かれ、
 * 押しても相手のアプリでも □ のままになる。`Paint.hasGlyph` で 1 度だけふるいに掛け、
 * 通ったものだけを並べる ([categories])。これで Android のバージョンが上がって
 * 絵文字が増えても、こちらの表を触らずに新しい字が出るようになる。
 *
 * 一覧は「よく打つもの」を選んだ手書きの表で、全収録は狙わない (数千字を並べても探せない)。
 * 先頭カテゴリの **最近使った順** ([RecentEmojiStore]) が実際の入口になる。
 */
internal object EmojiCatalog {

    /** 1 カテゴリ。[label] はタブに出す絵文字 1 字 (文字ラベルにすると翻訳が要るため)。 */
    data class Category(val label: String, val items: List<String>)

    /**
     * 連番で埋まっている絵文字ブロックをそのまま並べる (0.8.301)。
     *
     * ⚠ **1 字ずつ書き写した表は必ず抜ける**。実際 U+1F600–U+1F64F の 80 字のうち
     * 😌 U+1F60C・😝 U+1F61D・😸〜🙀 U+1F638–U+1F640 など **13 字が抜けていた**
     * (利用者の報告。打てないことに気付けるのは打とうとした本人だけで、こちらからは見えない)。
     * ブロックごと足しておけば、同じ抜け方は二度と起きない。
     *
     * **豆腐は出ない**: 端末のフォントが持っていない字は [categories] の `hasGlyph` が落とす。
     * 表を広く持って困るのは「探しにくくなる」ことだけなので、⚠ **手で選んだ表を先に、
     * ブロックを後ろに**置いて、よく打つ字が上に来る並びは崩さない。
     */
    private fun block(from: Int, to: Int): List<String> =
        (from..to).map { String(Character.toChars(it)) }

    private val FACES_PICKED = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃",
        "😉", "😊", "😇", "🥰", "😍", "😘", "😗", "😚", "😋", "😛",
        "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥳", "😏", "😒", "😞",
        "😔", "😟", "😕", "🙁", "😣", "😖", "😫", "😩", "🥺", "😢",
        "😭", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱",
        "😨", "😰", "😥", "😓", "🤗", "🤔", "🤭", "🤫", "😶", "😐",
        "😑", "😬", "🙄", "😯", "😦", "😧", "😮", "😲", "🥱", "😴",
        "🤤", "😪", "😵", "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒",
        "🤕", "🤑", "🤠", "😈", "👿", "💀", "👻", "👽", "🤖", "💩",
        // 新しめの追加 (Emoji 12〜15.1)。端末フォントが持たない字は hasGlyph で自動的に外れる。
        "🥲", "🥹", "🫠", "🫡", "🫢", "🫣", "🫥", "🫤", "🫨", "🙂‍↕️",
        "🙂‍↔️", "😶‍🌫️", "😮‍💨", "😵‍💫", "🤥", "🥸", "😙", "🤩"
    )

    private val PEOPLE_PICKED = listOf(
        "👍", "👎", "👌", "🤌", "🤏", "✌️", "🤞", "🤟", "🤘", "🤙",
        "👈", "👉", "👆", "👇", "☝️", "✋", "🤚", "🖐️", "🖖", "👋",
        "🤝", "🙏", "✍️", "💪", "🦵", "🦶", "👂", "👃", "👀", "👁️",
        "👄", "🧠", "🫀", "👶", "🧒", "👦", "👧", "🧑", "👨", "👩",
        "🧓", "👴", "👵", "🙋", "🙆", "🙅", "🤷", "🤦", "💁", "🙇",
        "🧑‍💻", "👨‍💻", "👩‍💻", "🕺", "💃", "🚶", "🏃", "🧗", "🧘", "👪",
        // 手のジェスチャー・人 (Emoji 14〜15.1)。
        "🫰", "🫶", "🫱", "🫲", "🫳", "🫴", "🫸", "🫷", "🫂", "🙌",
        "👏", "🤲", "👐", "🤛", "🤜", "👊", "✊", "🫅"
    )

    private val NATURE_PICKED = listOf(
        "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯",
        "🦁", "🐮", "🐷", "🐸", "🐵", "🙈", "🙉", "🙊", "🐔", "🐧",
        "🐦", "🐤", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴", "🦄",
        "🐝", "🐛", "🦋", "🐌", "🐞", "🐜", "🕷️", "🦂", "🐢", "🐍",
        "🦎", "🐙", "🦑", "🦐", "🦀", "🐡", "🐠", "🐟", "🐬", "🐳",
        "🐋", "🦈", "🐊", "🐅", "🐆", "🦓", "🦍", "🐘", "🐪", "🐄",
        "🌵", "🎄", "🌲", "🌳", "🌴", "🌱", "🌿", "☘️", "🍀", "🎋",
        "🍃", "🍂", "🍁", "🌾", "🌺", "🌻", "🌹", "🌷", "🌸", "💐",
        "🌞", "🌝", "🌚", "🌙", "⭐", "🌟", "✨", "⚡", "🔥", "🌈",
        "☀️", "⛅", "☁️", "🌧️", "⛈️", "❄️", "⛄", "💧", "🌊", "🌍",
        // 動物・植物の追加 (Emoji 13〜15.1)。
        "🫎", "🫏", "🦭", "🦬", "🦣", "🦫", "🦥", "🦦", "🦧", "🐦‍⬛",
        "🪶", "🪸", "🪼", "🦤", "🪿", "🐈‍⬛", "🦔", "🦩", "🦚", "🦜",
        "🪷", "🪻", "🌼", "🍄"
    )

    private val FOOD = listOf(
        "🍎", "🍏", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🫐",
        "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🍆", "🥑",
        "🥦", "🥬", "🥒", "🌶️", "🌽", "🥕", "🧄", "🧅", "🥔", "🍠",
        "🥐", "🍞", "🥖", "🥨", "🧀", "🥚", "🍳", "🧈", "🥞", "🧇",
        "🥓", "🍔", "🍟", "🍕", "🌭", "🥪", "🌮", "🌯", "🥙", "🍜",
        "🍝", "🍛", "🍣", "🍱", "🍚", "🍙", "🍘", "🍥", "🥟", "🍢",
        "🍡", "🍧", "🍨", "🍦", "🥧", "🍰", "🎂", "🍮", "🍭", "🍬",
        "🍫", "🍿", "🍩", "🍪", "☕", "🍵", "🧃", "🥤", "🍺", "🍻",
        "🥂", "🍷", "🥃", "🍸", "🍶", "🧊",
        // 食べ物・飲み物の追加 (Emoji 13〜15)。
        "🫛", "🫚", "🫘", "🧋", "🫗", "🧉", "🫙", "🧆", "🫓", "🥯",
        "🥣", "🥡", "🥠", "🍾"
    )

    private val ACTIVITY = listOf(
        "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🎱", "🏓",
        "🏸", "🥅", "🏒", "🏑", "🥍", "🏏", "⛳", "🏹", "🎣", "🤿",
        "🥊", "🥋", "🎽", "🛹", "🛷", "⛸️", "🥌", "🎿", "⛷️", "🏂",
        "🏋️", "🤼", "🤸", "⛹️", "🤺", "🤾", "🏌️", "🏇", "🧗", "🚴",
        "🏆", "🥇", "🥈", "🥉", "🏅", "🎖️", "🎗️", "🎫", "🎪", "🎭",
        "🎨", "🎬", "🎤", "🎧", "🎼", "🎹", "🥁", "🎷", "🎺", "🎸",
        "🎻", "🎲", "♟️", "🎯", "🎳", "🎮", "🕹️", "🎰", "🧩", "🎉",
        "🎊", "🎈", "🎁", "🎀", "🎃", "🎇", "🎆", "🧨",
        // 遊び・スポーツの追加 (Emoji 13〜14)。
        "🥏", "🪁", "🛼", "🪀", "🪇", "🪈", "🪭", "🪩", "🩰", "🛝"
    )

    private val PLACES = listOf(
        "🚗", "🚕", "🚙", "🚌", "🚎", "🏎️", "🚓", "🚑", "🚒", "🚐",
        "🚚", "🚛", "🚜", "🛴", "🚲", "🛵", "🏍️", "🚨", "🚔", "🚍",
        "🚝", "🚄", "🚅", "🚈", "🚂", "🚇", "🚊", "🚉", "✈️", "🛫",
        "🛬", "🛩️", "💺", "🚀", "🛸", "🚁", "🛶", "⛵", "🚤", "🛳️",
        "⛴️", "🚢", "⚓", "🗺️", "🗿", "🗽", "🗼", "🏰", "🏯", "🎡",
        "🎢", "🎠", "⛲", "⛱️", "🏖️", "🏝️", "🏜️", "🌋", "⛰️", "🏔️",
        "🗻", "🏕️", "⛺", "🏠", "🏡", "🏢", "🏣", "🏥", "🏦", "🏨",
        "🏪", "🏫", "🏬", "⛩️", "🕍", "🕌", "⛪", "🌃", "🌆", "🌇",
        "🌉", "🎑", "🗾",
        // 乗り物・場所の追加 (Emoji 13〜15)。
        "🛞", "🛟", "🚏", "🛰️", "🪐", "🌌", "🏙️", "🛖", "🏞️", "🚟", "🚠"
    )

    private val OBJECTS = listOf(
        "💡", "🔦", "🕯️", "🧯", "🛢️", "💸", "💵", "💴", "💶", "💷",
        "💰", "💳", "🧾", "💎", "⚖️", "🧰", "🔧", "🔨", "⚒️", "🛠️",
        "⛏️", "🔩", "⚙️", "🧱", "⛓️", "🧲", "🔫", "💣", "🧨", "🔪",
        "🗡️", "⚔️", "🛡️", "🚬", "⚰️", "🏺", "🔮", "📿", "💈", "⚗️",
        "🔭", "🔬", "🕳️", "💊", "💉", "🩹", "🩺", "🌡️", "🧬", "🦠",
        "🧫", "🧪", "🧹", "🧺", "🧻", "🚽", "🚿", "🛁", "🧼", "🪥",
        "🔑", "🗝️", "🚪", "🛋️", "🛏️", "🧸", "🖼️", "🛍️", "🎒", "👑",
        "👓", "🕶️", "👔", "👕", "👖", "🧣", "🧤", "🧥", "👗", "👞",
        "👟", "🥾", "🧢", "⌚", "📱", "💻", "⌨️", "🖥️", "🖨️", "🖱️",
        "💽", "💾", "💿", "📀", "🧮", "🎥", "📷", "📹", "📼", "🔍",
        "📔", "📕", "📖", "📚", "📝", "✏️", "🖊️", "📌", "📎", "📐",
        "📏", "✂️", "🗂️", "📁", "📅", "📈", "📉", "📊", "📋", "🗒️",
        "🗓️", "📇", "🗃️", "🗄️", "🗑️", "🔒", "🔓", "🔔", "🔕", "📢",
        "📣", "📯", "🔊", "📞", "☎️", "📠", "📺", "📻", "⏰", "⏱️",
        "⌛", "⏳", "🔋", "🔌", "🧭", "🗳️", "✉️", "📧", "📨", "📩",
        "📦", "📫", "📮", "🛒",
        // 道具・身の回りの追加 (Emoji 13〜15)。
        "🪪", "🩻", "🪫", "🪬", "🩼", "🪮", "🪄", "🪅", "🪆", "🪝",
        "🪛", "🪚", "🪙", "🪗", "🧴", "🧷", "🧶", "🧵", "🪡", "🩲",
        "🩳", "🩱", "🥻", "🪖", "🧳"
    )

    private val SYMBOLS = listOf(
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
        "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💯", "💢",
        "💬", "💭", "🗯️", "💤", "👌", "✅", "☑️", "✔️", "❌", "❎",
        "⭕", "🚫", "⛔", "❗", "❓", "❕", "❔", "‼️", "⁉️", "⚠️",
        "🔰", "♻️", "🈶", "🈚", "🈲", "🉑", "㊗️", "㊙️", "🆗", "🆖",
        "🆕", "🆒", "🆓", "🆙", "🔝", "🔙", "🔚", "🔜", "🔛", "🔄",
        "🔃", "▶️", "⏸️", "⏹️", "⏭️", "⏮️", "⏫", "⏬", "➡️", "⬅️",
        "⬆️", "⬇️", "↗️", "↘️", "↙️", "↖️", "🔺", "🔻", "🔴", "🟠",
        "🟡", "🟢", "🔵", "🟣", "⚫", "⚪", "🟥", "🟧", "🟨", "🟩",
        "🟦", "🟪", "⬛", "⬜", "🔶", "🔷", "🔸", "🔹", "♠️", "♥️",
        "♦️", "♣️", "🃏", "🀄", "🕐", "🈵", "©️", "®️", "™️", "〽️",
        // ハート・記号の追加 (Emoji 13.1〜15.1)。
        "🩷", "🩵", "🩶", "❤️‍🔥", "❤️‍🩹", "💟", "☮️", "♾️", "🟰", "🔟",
        "🔢", "🔤", "🔡", "🆎", "🅰️", "🅱️", "🆑", "🆘"
    )

    // --- 手で選んだ表 + 連番ブロック ([block] 参照) ---
    // ⚠ **足す順を変えない**。手で選んだ表が先、ブロックが後ろ。逆にすると、よく打つ字が
    // ブロックの数十字の後ろへ押し出されて探せなくなる。`distinct()` が重なりを落とす。

    /** 顔 (U+1F600–U+1F644)。⚠ U+1F645 から先は「人のしぐさ」なので [PEOPLE] へ入れる。 */
    private val FACES = (FACES_PICKED + block(0x1F600, 0x1F644)).distinct()

    /** 手と人。連番の手 (U+1F446–U+1F450) と、人のしぐさ (U+1F645–U+1F64F) を足す。 */
    private val PEOPLE =
        (PEOPLE_PICKED + block(0x1F446, 0x1F450) + block(0x1F645, 0x1F64F)).distinct()

    /**
     * 動物と自然 (U+1F400–U+1F43E)。
     *
     * ⚠ **U+1F43E で止め、U+1F43F 🐿 を入れない**。この字は**異体字セレクタ (U+FE0F) を
     * 付けないと絵で出ない**ので、素のまま並べると白黒の記号が 1 つ混じる。範囲で足してよいのは
     * 絵が既定の字だけ。
     */
    private val NATURE = (NATURE_PICKED + block(0x1F400, 0x1F43E)).distinct()

    /**
     * タブの並び (最近使った順は [KeyboardPad] 側が先頭に足す)。
     *
     * `internal` なのは**テストから中身を見るため**。[categories] は `Paint` を要るので
     * ふつうのユニットテストからは呼べず、抜けを押さえる側がここを直接読む
     * (`EmojiCatalogTest`)。
     */
    internal val ALL = listOf(
        Category("😀", FACES),
        Category("👍", PEOPLE),
        Category("🐶", NATURE),
        Category("🍎", FOOD),
        Category("⚽", ACTIVITY),
        Category("🚗", PLACES),
        Category("💡", OBJECTS),
        Category("🔣", SYMBOLS)
    )

    @Volatile private var filtered: List<Category>? = null

    /**
     * 端末フォントが持っている絵文字だけに絞ったカテゴリ一覧 (1 度だけ計算して使い回す)。
     *
     * ⚠ `hasGlyph` は 1 字ずつ描画可否を問い合わせるので、数百字ぶんまとめて呼ぶと数十 ms
     * かかる。パッドを初めて開くときに 1 回だけ通し、以降は結果を使い回す。
     */
    fun categories(): List<Category> {
        filtered?.let { return it }
        val paint = Paint()
        val out = ALL.map { c -> Category(c.label, c.items.filter { runCatching { paint.hasGlyph(it) }.getOrDefault(true) }) }
            .filter { it.items.isNotEmpty() }
        filtered = out
        return out
    }
}

/**
 * 絵文字パッドの「最近使った順」。保存場所: `filesDir/emoji_recent.json`。
 *
 * 絵文字は探すのに時間が掛かる一方、実際に使うのは 20 字ほどに偏る。⚠ カテゴリを
 * 最初のタブにすると毎回そこから探すことになるので、**最近使った順を先頭タブ**にする。
 */
// 保持するのは applicationContext のみ ([ImeHistoryStore] と同じ方針)。
@Suppress("StaticFieldLeak")
internal object RecentEmojiStore {
    private const val TAG = "RecentEmoji"
    private const val FILE_NAME = "emoji_recent.json"
    private const val MAX_ENTRIES = 48
    private const val SAVE_DEBOUNCE_MS = 500L

    private val _items = MutableStateFlow<List<String>>(emptyList())
    val items: StateFlow<List<String>> = _items.asStateFlow()

    @Volatile private var loaded = false
    private var saveJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var contextRef: Context? = null

    suspend fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        contextRef = context.applicationContext
        withContext(Dispatchers.IO) {
            runCatching {
                val f = File(context.applicationContext.filesDir, FILE_NAME)
                if (!f.exists()) return@runCatching
                val arr = JSONObject(f.readText(Charsets.UTF_8)).optJSONArray("items") ?: return@runCatching
                _items.value = (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotEmpty() } }
            }.onFailure { Log.w(TAG, "load failed: ${it.message}") }
        }
    }

    /** 使った絵文字を先頭へ (同じ字が既にあれば引き上げるだけ)。 */
    fun record(emoji: String) {
        if (emoji.isEmpty()) return
        _items.value = (listOf(emoji) + _items.value.filterNot { it == emoji }).take(MAX_ENTRIES)
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            val ctx = contextRef ?: return@launch
            runCatching {
                val arr = JSONArray()
                _items.value.forEach { arr.put(it) }
                val obj = JSONObject().put("items", arr)
                val tmp = File(ctx.filesDir, "$FILE_NAME.tmp")
                tmp.writeText(obj.toString(), Charsets.UTF_8)
                val dst = File(ctx.filesDir, FILE_NAME)
                if (!tmp.renameTo(dst)) { dst.writeText(obj.toString(), Charsets.UTF_8); tmp.delete() }
            }.onFailure { Log.w(TAG, "save failed: ${it.message}") }
        }
    }
}
