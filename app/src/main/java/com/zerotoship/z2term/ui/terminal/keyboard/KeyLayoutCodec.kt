package com.zerotoship.z2term.ui.terminal.keyboard

/**
 * [KeyLayout] と**保存できる形**の相互変換（0.8.408・段階 2）。
 *
 * ## なぜ org.json を使わないのか
 *
 * ⚠ **JVM のユニットテストでは `org.json` が動かない**。Android の `android.jar` はスタブで、
 * このプロジェクトは `unitTests.isReturnDefaultValues = true`（`app/build.gradle.kts`）にして
 * いるので、`JSONObject.put()` は例外も投げず**黙って何もしない**。そこにコーデックを直接
 * 書くと、**往復テストが書けない**（書いても嘘の結果で緑になる）。
 *
 * そこで 2 段にした:
 *
 *  1. **ここ** … [KeyLayout] ⇄ `Map` / `List` / String / Number / Boolean だけの木。
 *     Android に触らないので **JVM のテストで往復を固定できる**（[KeyLayoutCodecTest]）。
 *  2. [KeyLayoutJson] … その木と JSON 文字列の橋。`org.json` を使うのはこの薄い層だけ。
 *
 * ## 書き方の方針
 *
 * ⭐ **既定値は書かない。** レイアウトは「AI に書いてもらった JSON を貼る」を正規の入口に
 * する（設計 §3.9）ので、**人が読める短さ**が機能のうち。`repeat` も `press` も既定のキーは
 * `{"label":"a","bind":{"tap":[{"t":"text","s":"a"}]}}` だけで済む。
 *
 * ⚠ **知らない値は落として読み進める。** 未知のジェスチャ名・アクション種別・列挙の id が
 * 来ても、その 1 つを捨てるだけで残りは読む。新しい版で書いた JSON を古い版が開いたときに
 * **レイアウトごと消える**のが一番困る。
 *
 * ## 形（例）
 *
 * ```json
 * {
 *   "v": 1, "id": "my_ascii", "name": "じぶんの英字",
 *   "rows": [
 *     { "slots": [
 *         { "w": 1.4, "key": { "label": "ESC", "bind": { "tap": [ {"t":"named","k":"esc"} ] } } },
 *         { "key": { "label": "a", "bind": { "tap": [ {"t":"text","s":"a"} ] } } },
 *         { "split": "v", "parts": [ { "key": { … } }, { "key": { … } } ] }
 *     ] }
 *   ]
 * }
 * ```
 *
 * - `w` が無い枠 = [KeyWidth.Auto]（段の残りを均等に分ける）
 * - `split` がある枠 = [SlotContent.Split]。無ければ `key` を見る
 * - `parts` の `r` が無ければ取り分 1.0
 */
object KeyLayoutCodec {

    /** 書き出す形の版。⚠ 読むときは**版が違っても捨てない**（知らない項目を無視するだけ）。 */
    const val VERSION = 2

    // ---- 書き出す ----------------------------------------------------------------------

    fun encode(layout: KeyLayout): Map<String, Any?> = linkedMapOf<String, Any?>(
        "v" to VERSION,
        "id" to layout.id,
        "name" to layout.name,
    ).apply {
        if (layout.faceId != KeyboardFace.ASCII.id) put("face", layout.faceId)
        if (layout.styleId != KeyboardStyle.SPACIOUS.id) put("style", layout.styleId)
        put("rows", layout.rows.map { encodeRow(it) })
        layout.symbolRows?.let { put("symbols", it.map(::encodeRow)) }
        layout.defaultRows?.let { defaults ->
            put("default", linkedMapOf<String, Any?>(
                "name" to (layout.defaultName ?: layout.name),
                "rows" to defaults.map(::encodeRow),
            ).apply {
                layout.defaultSymbolRows?.let { put("symbols", it.map(::encodeRow)) }
            })
        }
    }

    fun encodeAll(layouts: List<KeyLayout>): List<Map<String, Any?>> = layouts.map { encode(it) }

    private fun encodeRow(row: KeyRow): Map<String, Any?> =
        linkedMapOf("slots" to row.slots.map { encodeSlot(it) })

    private fun encodeSlot(slot: KeySlot): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        (slot.width as? KeyWidth.Fixed)?.let { m["w"] = it.ratio.toDouble() }
        encodeContentInto(m, slot.content)
        return m
    }

    /**
     * 中身を**枠の中へ直に**書く（`{"key":…}` か `{"split":…,"parts":[…]}`）。
     * ⚠ もう 1 段 `"content"` で包まない — 深さ 2 まで割れるので、包むと読む側の目が滑る。
     */
    private fun encodeContentInto(into: MutableMap<String, Any?>, content: SlotContent) {
        when (content) {
            is SlotContent.Single -> into["key"] = encodeKey(content.key)
            is SlotContent.Split -> {
                into["split"] = content.dir.id
                into["parts"] = content.parts.map { part ->
                    val m = LinkedHashMap<String, Any?>()
                    if (part.ratio != 1f) m["r"] = part.ratio.toDouble()
                    encodeContentInto(m, part.content)
                    m
                }
            }
        }
    }

    private fun encodeKey(key: KeyDef): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        if (key.label.isNotEmpty()) m["label"] = key.label
        if (key.bindings.isNotEmpty()) {
            val binds = LinkedHashMap<String, Any?>()
            // ⚠ 書き出す順は [KeyGesture] の宣言順に固定する。Map の並びに任せると、
            //   中身が同じでも文字列が変わって「変更あり」に見える。
            KeyGesture.entries.forEach { g ->
                key.bindings[g]?.let { actions -> binds[g.id] = actions.map { encodeAction(it) } }
            }
            m["bind"] = binds
        }
        if (key.hintGestures.isNotEmpty()) {
            m["hint"] = KeyGesture.entries.filter { it in key.hintGestures }.map { it.id }
        }
        if (key.repeatable) m["repeat"] = true
        if (key.repeatInitialMs != KeyDef.DEFAULT_REPEAT_INITIAL_MS) {
            m["repeatInitialMs"] = key.repeatInitialMs
        }
        if (key.repeatIntervalMs != KeyDef.DEFAULT_REPEAT_INTERVAL_MS) {
            m["repeatIntervalMs"] = key.repeatIntervalMs
        }
        if (!key.pressFeedback) m["press"] = false
        if (!key.flickOnRelease) m["flickOnRelease"] = false
        if (key.highlighted) m["highlight"] = true
        if (key.labelTone != LabelTone.PRIMARY) m["tone"] = key.labelTone.id
        if (key.fontRole != KeyFontRole.NORMAL) m["font"] = key.fontRole.id
        if (key.layers.isNotEmpty()) {
            val layers = LinkedHashMap<String, Any?>()
            key.layers.forEach { (name, def) -> layers[name] = encodeKey(def) }
            m["layers"] = layers
        }
        return m
    }

    private fun encodeAction(action: KeyAction): Map<String, Any?> = when (action) {
        is KeyAction.Text -> linkedMapOf("t" to "text", "s" to action.text)
        is KeyAction.Named -> linkedMapOf("t" to "named", "k" to action.key.id)
        is KeyAction.Chord -> {
            val m = linkedMapOf<String, Any?>(
                "t" to "chord",
                "mods" to ModKey.entries.filter { it in action.mods }.map { it.id },
            )
            action.text?.let { m["s"] = it }
            action.key?.let { m["k"] = it.id }
            m
        }
        // ⚠ 生バイトは 16 進で持つ。JSON の文字列に生の制御文字を入れると、エディタや
        //   クリップボードを 1 度通っただけで壊れる（`` が消える・改行が混ざる）。
        is KeyAction.Raw -> linkedMapOf("t" to "raw", "hex" to action.bytes.toHexString())
        is KeyAction.Modifier -> linkedMapOf("t" to "mod", "k" to action.mod.id)
        is KeyAction.Layer -> {
            val m = linkedMapOf<String, Any?>("t" to "layer", "name" to action.layer)
            if (action.sticky) m["sticky"] = true
            m
        }
        is KeyAction.App -> linkedMapOf("t" to "app", "a" to action.action.id)
        is KeyAction.Snippet -> linkedMapOf("t" to "snippet", "id" to action.id)
        is KeyAction.Macro -> linkedMapOf("t" to "macro", "name" to action.name)
    }

    // ---- 読む --------------------------------------------------------------------------

    /**
     * 木からレイアウトを起こす。**読めなければ null**（呼出し側が「1 件飛ばす」を選べる）。
     *
     * ⚠ [KeyLayout.validate] は**ここでは掛けない**。壊れているかどうかの判断（弾く / 直す /
     * 警告する）は保存する側の仕事で、コーデックは「書いてあるとおりに起こす」に徹する。
     */
    fun decode(node: Map<*, *>): KeyLayout? {
        val id = node.str("id")?.takeIf { it.isNotBlank() } ?: return null
        val name = node.str("name") ?: id
        val rows = decodeRows(node.list("rows")) ?: return null
        if (rows.isEmpty()) return null
        val faceId = node.str("face")?.let { KeyboardFace.byId(it).id } ?: KeyboardFace.ASCII.id
        // v1 には style が無い。6 段ならシンプル、5 段以下なら4方向として復元する。
        val styleId = node.str("style")?.takeIf { raw -> KeyboardStyle.ALL.any { it.id == raw } }
            ?: if (rows.size >= 6) KeyboardStyle.COMPACT.id else KeyboardStyle.SPACIOUS.id
        val symbols = decodeRows(node.list("symbols"))
        val defaults = node.map("default")
        val defaultRows = defaults?.let { decodeRows(it.list("rows")) }
        return KeyLayout(
            id = id,
            name = name,
            rows = rows,
            faceId = faceId,
            styleId = styleId,
            symbolRows = symbols,
            defaultName = defaults?.str("name"),
            defaultRows = defaultRows,
            defaultSymbolRows = defaults?.let { decodeRows(it.list("symbols")) },
        )
    }

    /** 束をまとめて読む。⚠ **読めない 1 件のために他を落とさない**。 */
    fun decodeAll(nodes: List<*>): List<KeyLayout> = nodes.mapNotNull { n ->
        (n as? Map<*, *>)?.let { decode(it) }
    }

    private fun decodeRows(raw: List<*>?): List<KeyRow>? = raw?.mapNotNull { row ->
        (row as? Map<*, *>)?.let(::decodeRow)
    }?.takeIf { it.isNotEmpty() }

    private fun decodeRow(node: Map<*, *>): KeyRow? {
        val slots = (node.list("slots") ?: return null).mapNotNull { s ->
            (s as? Map<*, *>)?.let { decodeSlot(it) }
        }
        return if (slots.isEmpty()) null else KeyRow(slots)
    }

    private fun decodeSlot(node: Map<*, *>): KeySlot? {
        val content = decodeContent(node) ?: return null
        val fixed = node.num("w")?.toFloat()
        val width = if (fixed != null && fixed.isFinite() && fixed > 0f) {
            KeyWidth.Fixed(fixed)
        } else {
            KeyWidth.Auto
        }
        return KeySlot(content, width)
    }

    private fun decodeContent(node: Map<*, *>): SlotContent? {
        val splitId = node.str("split")
        if (splitId != null) {
            val dir = SplitDir.byId(splitId) ?: return null
            val parts = (node.list("parts") ?: return null).mapNotNull { p ->
                val pm = p as? Map<*, *> ?: return@mapNotNull null
                val c = decodeContent(pm) ?: return@mapNotNull null
                val ratio = pm.num("r")?.toFloat()?.takeIf { it.isFinite() && it > 0f } ?: 1f
                SlotPart(c, ratio)
            }
            // ⚠ 割った先が 1 つしか読めなかったら、割らないより悪い（片側が消えた枠になる）。
            return if (parts.size < KeyLayout.MIN_SPLIT_PARTS) null else SlotContent.Split(dir, parts)
        }
        val key = node.map("key") ?: return null
        return SlotContent.Single(decodeKey(key))
    }

    private fun decodeKey(node: Map<*, *>): KeyDef {
        val bindings = LinkedHashMap<KeyGesture, List<KeyAction>>()
        node.map("bind")?.forEach { (rawGesture, rawActions) ->
            val gesture = KeyGesture.byId(rawGesture as? String ?: return@forEach) ?: return@forEach
            val actions = (rawActions as? List<*>)?.mapNotNull { a ->
                (a as? Map<*, *>)?.let { decodeAction(it) }
            }.orEmpty()
            if (actions.isNotEmpty()) bindings[gesture] = actions
        }
        val hints = node.list("hint").orEmpty()
            .mapNotNull { KeyGesture.byId(it as? String ?: return@mapNotNull null) }
            .toSet()
        val layers = LinkedHashMap<String, KeyDef>()
        node.map("layers")?.forEach { (rawName, rawDef) ->
            val name = rawName as? String ?: return@forEach
            val def = rawDef as? Map<*, *> ?: return@forEach
            layers[name] = decodeKey(def)
        }
        return KeyDef(
            label = node.str("label").orEmpty(),
            bindings = bindings,
            hintGestures = hints,
            repeatable = node.bool("repeat") ?: false,
            repeatInitialMs = node.num("repeatInitialMs")?.toLong() ?: KeyDef.DEFAULT_REPEAT_INITIAL_MS,
            repeatIntervalMs = node.num("repeatIntervalMs")?.toLong() ?: KeyDef.DEFAULT_REPEAT_INTERVAL_MS,
            pressFeedback = node.bool("press") ?: true,
            flickOnRelease = node.bool("flickOnRelease") ?: true,
            highlighted = node.bool("highlight") ?: false,
            labelTone = node.str("tone")?.let { LabelTone.byId(it) } ?: LabelTone.PRIMARY,
            fontRole = node.str("font")?.let { KeyFontRole.byId(it) } ?: KeyFontRole.NORMAL,
            layers = layers,
        )
    }

    private fun decodeAction(node: Map<*, *>): KeyAction? = when (node.str("t")) {
        "text" -> node.str("s")?.let { KeyAction.Text(it) }
        "named" -> node.str("k")?.let { NamedKey.byId(it) }?.let { KeyAction.Named(it) }
        "chord" -> {
            val mods = node.list("mods").orEmpty()
                .mapNotNull { ModKey.byId(it as? String ?: return@mapNotNull null) }
                .toSet()
            val text = node.str("s")
            val key = node.str("k")?.let { NamedKey.byId(it) }
            // ⚠ 送るものが無い Chord は捨てる（押しても何も起きないキーになる）。
            if (text == null && key == null) null else KeyAction.Chord(mods, text, key)
        }
        "raw" -> node.str("hex")?.hexToBytesOrNull()?.let { KeyAction.Raw(it) }
        "mod" -> node.str("k")?.let { ModKey.byId(it) }?.let { KeyAction.Modifier(it) }
        "layer" -> node.str("name")?.let { KeyAction.Layer(it, node.bool("sticky") ?: false) }
        "app" -> node.str("a")?.let { AppAction.byId(it) }?.let { KeyAction.App(it) }
        "snippet" -> node.str("id")?.let { KeyAction.Snippet(it) }
        "macro" -> node.str("name")?.let { KeyAction.Macro(it) }
        else -> null
    }

    // ---- 木を読むための小道具 ------------------------------------------------------------
    //
    // ⚠ 数は Int で来たり Double で来たりする（JSON を通すと `1` は Integer、`1.4` は Double）。
    //   [Number] で受けて必要な型へ落とす。
    //
    // ⚠ **`map[key]` を使わない。** 受けているのは `Map<*, *>`（何が入っているか分からない木）で、
    //   スター射影の Map は添字で引けない。中身を舐めて取り出す。マップは 1 つあたり数個なので
    //   速さは問題にならない。

    private fun Map<*, *>.value(key: String): Any? = entries.firstOrNull { it.key == key }?.value
    private fun Map<*, *>.str(key: String): String? = value(key) as? String
    private fun Map<*, *>.num(key: String): Double? = (value(key) as? Number)?.toDouble()
    private fun Map<*, *>.bool(key: String): Boolean? = value(key) as? Boolean
    private fun Map<*, *>.list(key: String): List<*>? = value(key) as? List<*>
    private fun Map<*, *>.map(key: String): Map<*, *>? = value(key) as? Map<*, *>

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    private fun String.hexToBytesOrNull(): ByteArray? {
        val s = trim()
        if (s.isEmpty() || s.length % 2 != 0) return null
        val out = ByteArray(s.length / 2)
        for (i in out.indices) {
            val v = s.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return null
            out[i] = v.toByte()
        }
        return out
    }
}
