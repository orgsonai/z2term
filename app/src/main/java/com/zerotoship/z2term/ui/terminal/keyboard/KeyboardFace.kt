package com.zerotoship.z2term.ui.terminal.keyboard

/**
 * 内蔵キーボードの「面」(0.8.305)。
 *
 * 面は**キーの一式がまるごと差し替わる**もので、切替キー 1 つで巡回する。
 * ⚠ パッド ([PadMode] の 絵文字 / 貼り付け) とは**別物**。パッドは中央の列だけを一時的に
 * 差し替える表示で、閉じれば元の面に戻る。面は次にキーボードを開いたときも同じものが出る。
 *
 * ⛔ **キーを増やさない**のがこの機構の要点 (画面に見えるキーの数は面が増えても変わらない)。
 * 数字面を足すときも切替キーは新設せず、既存の「あ」/「ABC」が指す先に 3 つ目を挟んだ。
 */
enum class KeyboardFace(val id: String) {
    /** 日本語フリック (かな)。⚠ **アプリの言語が日本語のときだけ**出せる。 */
    KANA("kana"),

    /** 英字 (qwerty)。⚠ **どの巡回順にも必ず入っている** — 消えると逃げ場が無くなる。 */
    ASCII("ascii"),

    /**
     * 数字のみ (テンキー 3 列 × 4 段)。設定で出す / 出さないを選べる。
     *
     * フリック面には数字が 1 つも無く、ASCII 面の Row 1 (`ESC 1〜0 ⌫`) は横に 10 個並ぶので
     * 指が細かい。ポート番号・IP・`chmod 755` のように**数字だけを続けて打つ場面**が端末では
     * 多いので、そこだけ大きなキーで打てる面を用意する。
     */
    NUMBER("number");

    /**
     * 切替キーに出すラベル。
     *
     * ⚠ 表すのは**いま居る面ではなく、押すと行く面**。2 面のときは「もう片方」しか無いので
     * どちらの意味でも同じだったが、3 面あると「押したらどこへ行くのか」がラベル以外に
     * 分からない。
     */
    val switchLabel: String
        get() = when (this) {
            KANA -> "あ"
            ASCII -> "ABC"
            NUMBER -> "12"
        }

    companion object {
        /** 巡回順「あ → A → 12」の保存値。 */
        const val ORDER_ASCII_FIRST_ID = "kana_ascii_number"

        /** 巡回順「あ → 12 → A」の保存値。 */
        const val ORDER_NUMBER_FIRST_ID = "kana_number_ascii"

        /** 巡回順「あ → A → 12」(既定)。 */
        val ORDER_ASCII_FIRST = listOf(KANA, ASCII, NUMBER)

        /** 巡回順「あ → 12 → A」。 */
        val ORDER_NUMBER_FIRST = listOf(KANA, NUMBER, ASCII)

        /**
         * 設定に出す巡回順の全部。
         *
         * ⚠ **3 面の巡回順は回転を除いて 2 通りしかない**ので、この 2 つで尽きている
         * (`A → 12 → あ` は `あ → A → 12` を回しただけの同じ順)。並べ替え UI を作っても
         * 選べるものは増えないため、設定は 2 択のラジオにしてある。
         */
        val ORDERS = listOf(ORDER_ASCII_FIRST, ORDER_NUMBER_FIRST)

        fun byId(id: String?): KeyboardFace = entries.firstOrNull { it.id == id } ?: ASCII

        fun orderById(id: String?): List<KeyboardFace> =
            if (id == ORDER_NUMBER_FIRST_ID) ORDER_NUMBER_FIRST else ORDER_ASCII_FIRST

        fun orderIdOf(order: List<KeyboardFace>): String =
            if (order == ORDER_NUMBER_FIRST) ORDER_NUMBER_FIRST_ID else ORDER_ASCII_FIRST_ID

        /** 設定 (巡回順 + 数字面の有無) から、呼出し側が渡す巡回順を組む。 */
        fun orderFrom(orderId: String?, numberFace: Boolean): List<KeyboardFace> =
            orderById(orderId).filter { it != NUMBER || numberFace }

        /**
         * [order] のうち**いまこの画面で出せる面**だけを残す。
         *
         * ⚠ [ASCII] は必ず残る。日本語面はアプリの言語が英語なら出せず、数字面は設定で
         * 切れるので、素直に絞ると面が 1 つも無くなることがある。
         */
        fun available(order: List<KeyboardFace>, allowKana: Boolean): List<KeyboardFace> =
            order.filter { it != KANA || allowKana }.ifEmpty { listOf(ASCII) }

        /**
         * [current] の次の面。
         *
         * ⚠ [faces] に [current] が無ければ先頭へ戻す — 設定を変えた直後は「いま出ている面」が
         * 巡回から外れていることがあり、そこで詰まると切替キーが効かなくなる。
         */
        fun next(faces: List<KeyboardFace>, current: KeyboardFace): KeyboardFace {
            if (faces.isEmpty()) return ASCII
            val idx = faces.indexOf(current)
            return if (idx < 0) faces.first() else faces[(idx + 1) % faces.size]
        }
    }
}

/**
 * `next_face` が巡回する 1 枚。内蔵面と保存済みカスタム配列を同じ列で扱う。
 * [id] は並び順・ON/OFF保存用の安定IDで、カスタム配列は元の layout id を含む。
 */
data class KeyboardFaceEntry(
    val id: String,
    val face: KeyboardFace,
    val customLayout: KeyLayout? = null,
) {
    val switchLabel: String
        get() = customLayout?.name?.trim()?.takeIf { it.isNotEmpty() }?.take(4) ?: face.switchLabel

    companion object {
        const val BUILTIN_KANA_ID = "builtin:kana"
        const val BUILTIN_ASCII_ID = "builtin:ascii"
        const val BUILTIN_NUMBER_ID = "builtin:number"
        private const val CUSTOM_PREFIX = "custom:"

        fun builtin(face: KeyboardFace): KeyboardFaceEntry = KeyboardFaceEntry(
            id = when (face) {
                KeyboardFace.KANA -> BUILTIN_KANA_ID
                KeyboardFace.ASCII -> BUILTIN_ASCII_ID
                KeyboardFace.NUMBER -> BUILTIN_NUMBER_ID
            },
            face = face,
        )

        fun custom(layout: KeyLayout): KeyboardFaceEntry = KeyboardFaceEntry(
            id = customId(layout.id),
            face = KeyboardFace.byId(layout.faceId),
            customLayout = layout,
        )

        fun customId(layoutId: String): String = "$CUSTOM_PREFIX$layoutId"
    }
}

/** 面の全順序と有効集合を、旧3面設定との互換を保って解決する。 */
object KeyboardFaceConfig {
    private const val SEPARATOR = ","

    /** 内蔵3面 + 保存済みカスタム面を、保存順に並べる。未知・削除済みIDは落とす。 */
    fun allEntries(orderValue: String?, layouts: List<KeyLayout>): List<KeyboardFaceEntry> {
        val known = buildList {
            add(KeyboardFaceEntry.builtin(KeyboardFace.KANA))
            add(KeyboardFaceEntry.builtin(KeyboardFace.ASCII))
            add(KeyboardFaceEntry.builtin(KeyboardFace.NUMBER))
            layouts.forEach { add(KeyboardFaceEntry.custom(it)) }
        }
        val byId = known.associateBy { it.id }
        val requested = parseOrder(orderValue)
        return buildList {
            requested.mapNotNullTo(this) { byId[it] }
            known.filterTo(this) { entry -> none { it.id == entry.id } }
        }
    }

    /**
     * 有効ID。新設定が空なら旧設定から移行する。英語UIで旧設定を読む場合だけかなを外すが、
     * 一度ユーザーがONにすれば言語に関係なく日本語面を使える。
     */
    fun enabledIds(
        enabledValue: String?,
        entries: List<KeyboardFaceEntry>,
        legacyNumberEnabled: Boolean,
        legacyActiveLayoutId: String,
        legacyKanaAvailable: Boolean,
    ): LinkedHashSet<String> {
        val known = entries.mapTo(HashSet()) { it.id }
        val explicit = enabledValue.orEmpty().split(SEPARATOR)
            .map(String::trim)
            .filterTo(LinkedHashSet()) { it in known }
        if (enabledValue?.isNotBlank() == true) {
            return explicit.ifEmpty { linkedSetOf(entries.first().id) }
        }

        val migrated = linkedSetOf(KeyboardFaceEntry.BUILTIN_ASCII_ID)
        if (legacyKanaAvailable) migrated += KeyboardFaceEntry.BUILTIN_KANA_ID
        if (legacyNumberEnabled) migrated += KeyboardFaceEntry.BUILTIN_NUMBER_ID
        entries.firstOrNull { it.customLayout?.id == legacyActiveLayoutId }?.let { custom ->
            migrated -= KeyboardFaceEntry.builtin(custom.face).id
            migrated += custom.id
        }
        migrated.retainAll(known)
        if (migrated.isEmpty() && entries.isNotEmpty()) migrated += entries.first().id
        return migrated
    }

    fun enabledEntries(
        orderValue: String?,
        enabledValue: String?,
        layouts: List<KeyLayout>,
        legacyNumberEnabled: Boolean,
        legacyActiveLayoutId: String,
        legacyKanaAvailable: Boolean,
    ): List<KeyboardFaceEntry> {
        val entries = allEntries(orderValue, layouts)
        if (entries.isEmpty()) return listOf(KeyboardFaceEntry.builtin(KeyboardFace.ASCII))
        val enabled = enabledIds(
            enabledValue,
            entries,
            legacyNumberEnabled,
            legacyActiveLayoutId,
            legacyKanaAvailable,
        )
        return entries.filter { it.id in enabled }.ifEmpty { listOf(entries.first()) }
    }

    fun enabledEntriesFromJson(
        orderValue: String?,
        enabledValue: String?,
        layoutsJson: String,
        legacyNumberEnabled: Boolean,
        legacyActiveLayoutId: String,
        legacyKanaAvailable: Boolean,
    ): List<KeyboardFaceEntry> = enabledEntries(
        orderValue = orderValue,
        enabledValue = enabledValue,
        layouts = KeyLayoutJson.listFromJsonString(layoutsJson),
        legacyNumberEnabled = legacyNumberEnabled,
        legacyActiveLayoutId = legacyActiveLayoutId,
        legacyKanaAvailable = legacyKanaAvailable,
    )

    fun encodeOrder(entries: List<KeyboardFaceEntry>): String = entries.joinToString(SEPARATOR) { it.id }

    fun encodeEnabled(entries: List<KeyboardFaceEntry>, enabledIds: Set<String>): String =
        entries.filter { it.id in enabledIds }.joinToString(SEPARATOR) { it.id }

    /** 旧IME保存値 (`ascii` 等) も新しい面IDへ解決する。 */
    fun initialEntryId(saved: String?, entries: List<KeyboardFaceEntry>): String {
        entries.firstOrNull { it.id == saved }?.let { return it.id }
        val legacyFace = KeyboardFace.entries.firstOrNull { it.id == saved }
        entries.firstOrNull { it.customLayout == null && it.face == legacyFace }?.let { return it.id }
        return entries.firstOrNull { it.id == KeyboardFaceEntry.BUILTIN_ASCII_ID }?.id
            ?: entries.firstOrNull()?.id
            ?: KeyboardFaceEntry.BUILTIN_ASCII_ID
    }

    private fun parseOrder(value: String?): List<String> = when (value) {
        KeyboardFace.ORDER_NUMBER_FIRST_ID -> listOf(
            KeyboardFaceEntry.BUILTIN_KANA_ID,
            KeyboardFaceEntry.BUILTIN_NUMBER_ID,
            KeyboardFaceEntry.BUILTIN_ASCII_ID,
        )
        KeyboardFace.ORDER_ASCII_FIRST_ID, null, "" -> listOf(
            KeyboardFaceEntry.BUILTIN_KANA_ID,
            KeyboardFaceEntry.BUILTIN_ASCII_ID,
            KeyboardFaceEntry.BUILTIN_NUMBER_ID,
        )
        else -> value.split(SEPARATOR).map(String::trim).filter(String::isNotEmpty).distinct()
    }
}
