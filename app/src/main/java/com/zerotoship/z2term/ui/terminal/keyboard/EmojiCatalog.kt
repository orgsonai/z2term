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

    private val FACES = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃",
        "😉", "😊", "😇", "🥰", "😍", "😘", "😗", "😚", "😋", "😛",
        "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥳", "😏", "😒", "😞",
        "😔", "😟", "😕", "🙁", "😣", "😖", "😫", "😩", "🥺", "😢",
        "😭", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱",
        "😨", "😰", "😥", "😓", "🤗", "🤔", "🤭", "🤫", "😶", "😐",
        "😑", "😬", "🙄", "😯", "😦", "😧", "😮", "😲", "🥱", "😴",
        "🤤", "😪", "😵", "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒",
        "🤕", "🤑", "🤠", "😈", "👿", "💀", "👻", "👽", "🤖", "💩"
    )

    private val PEOPLE = listOf(
        "👍", "👎", "👌", "🤌", "🤏", "✌️", "🤞", "🤟", "🤘", "🤙",
        "👈", "👉", "👆", "👇", "☝️", "✋", "🤚", "🖐️", "🖖", "👋",
        "🤝", "🙏", "✍️", "💪", "🦵", "🦶", "👂", "👃", "👀", "👁️",
        "👄", "🧠", "🫀", "👶", "🧒", "👦", "👧", "🧑", "👨", "👩",
        "🧓", "👴", "👵", "🙋", "🙆", "🙅", "🤷", "🤦", "💁", "🙇",
        "🧑‍💻", "👨‍💻", "👩‍💻", "🕺", "💃", "🚶", "🏃", "🧗", "🧘", "👪"
    )

    private val NATURE = listOf(
        "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯",
        "🦁", "🐮", "🐷", "🐸", "🐵", "🙈", "🙉", "🙊", "🐔", "🐧",
        "🐦", "🐤", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴", "🦄",
        "🐝", "🐛", "🦋", "🐌", "🐞", "🐜", "🕷️", "🦂", "🐢", "🐍",
        "🦎", "🐙", "🦑", "🦐", "🦀", "🐡", "🐠", "🐟", "🐬", "🐳",
        "🐋", "🦈", "🐊", "🐅", "🐆", "🦓", "🦍", "🐘", "🐪", "🐄",
        "🌵", "🎄", "🌲", "🌳", "🌴", "🌱", "🌿", "☘️", "🍀", "🎋",
        "🍃", "🍂", "🍁", "🌾", "🌺", "🌻", "🌹", "🌷", "🌸", "💐",
        "🌞", "🌝", "🌚", "🌙", "⭐", "🌟", "✨", "⚡", "🔥", "🌈",
        "☀️", "⛅", "☁️", "🌧️", "⛈️", "❄️", "⛄", "💧", "🌊", "🌍"
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
        "🥂", "🍷", "🥃", "🍸", "🍶", "🧊"
    )

    private val ACTIVITY = listOf(
        "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🎱", "🏓",
        "🏸", "🥅", "🏒", "🏑", "🥍", "🏏", "⛳", "🏹", "🎣", "🤿",
        "🥊", "🥋", "🎽", "🛹", "🛷", "⛸️", "🥌", "🎿", "⛷️", "🏂",
        "🏋️", "🤼", "🤸", "⛹️", "🤺", "🤾", "🏌️", "🏇", "🧗", "🚴",
        "🏆", "🥇", "🥈", "🥉", "🏅", "🎖️", "🎗️", "🎫", "🎪", "🎭",
        "🎨", "🎬", "🎤", "🎧", "🎼", "🎹", "🥁", "🎷", "🎺", "🎸",
        "🎻", "🎲", "♟️", "🎯", "🎳", "🎮", "🕹️", "🎰", "🧩", "🎉",
        "🎊", "🎈", "🎁", "🎀", "🎃", "🎇", "🎆", "🧨"
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
        "🌉", "🎑", "🗾"
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
        "📦", "📫", "📮", "🛒"
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
        "♦️", "♣️", "🃏", "🀄", "🕐", "🈵", "©️", "®️", "™️", "〽️"
    )

    /** タブの並び (最近使った順は [KeyboardPad] 側が先頭に足す)。 */
    private val ALL = listOf(
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
