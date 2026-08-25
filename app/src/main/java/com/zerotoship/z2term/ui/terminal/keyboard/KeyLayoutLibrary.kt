package com.zerotoship.z2term.ui.terminal.keyboard

/**
 * 保存してあるレイアウトの束を触る（0.8.408・段階 2）。
 *
 * ⚠ ここは**純粋な Kotlin**（Android にも `org.json` にも触らない）。束の出し入れは
 * 「消したつもりが別の 1 件も消える」が一番怖いところなので、JVM のテストで固定できる
 * ようにしてある（[KeyLayoutLibraryTest]）。JSON との橋は [KeyLayoutJson]。
 */

/**
 * プリセットを**編集できるテンプレート**にする。
 *
 * ⭐ **`Fixed(1.0)` を [KeyWidth.Auto] へ読み替えるのが肝。** プリセット
 * （[asciiKeyLayout]）は見た目を 1 ドットも動かさないために幅を全部 [KeyWidth.Fixed] で
 * 書いてある（0.8.403）。そのまま複製すると、**1 つ広げても他が縮まない**テンプレートに
 * なり、要望の「変えたキー以外が均等に再分配される」が効かない。
 *
 * ⚠ **意図して広げた幅はそのまま残す。** ESC / ⌫ の 1.4、`?#` の 1.2、スペースの 4.0 は
 * 「均等 1 枠分の何倍か」であって、均等割りへ戻したいわけではない。読み替えるのは
 * 「均等 1 枠ぶんちょうど」= 1.0 だけ。
 *
 * ⚠ 複製すると**幅の比率がわずかに変わる**（例: Row 1 は `1.4 + 1.0×10 + 1.4` の合計 12.8 で
 * 描いていたのが、予算 12 枠を `1.4 + Auto×10 + 1.4` で分け直すので英字キーが 1.0 → 0.92 に
 * なる）。テンプレートとして正しい姿にする代償で、**これは仕様**。
 */
fun KeyLayout.asTemplate(id: String, name: String): KeyLayout = KeyLayout(
    id = id,
    name = name,
    rows = rows.map { row -> KeyRow(row.slots.map { it.copy(width = it.width.toTemplateWidth()) }) },
)

/** 均等 1 枠ぶんちょうどの固定幅だけを [KeyWidth.Auto] へ戻す。 */
private fun KeyWidth.toTemplateWidth(): KeyWidth = when (this) {
    is KeyWidth.Auto -> KeyWidth.Auto
    // ⚠ 浮動小数の等号は使わない。1.0f は書き出して読み直すと 0.9999999 になり得る。
    is KeyWidth.Fixed -> if (kotlin.math.abs(ratio - 1f) < 0.001f) KeyWidth.Auto else this
}

/**
 * まだ使われていない id を作る。⚠ **id は改名しない**（設定の参照が切れる）ので、
 * 名前とは別に、1 回だけ決めて動かさない値が要る。
 */
fun newKeyLayoutId(existing: List<KeyLayout>, base: String = "layout"): String {
    val used = existing.mapTo(HashSet()) { it.id }
    var n = 1
    while ("$base$n" in used) n++
    return "$base$n"
}

/**
 * 同じ名前が並ばないようにする（`じぶんの英字` → `じぶんの英字 2`）。
 *
 * ⚠ 名前が重なっても壊れはしない（参照は id）が、**一覧で見分けられない**。
 */
fun uniqueKeyLayoutName(existing: List<KeyLayout>, base: String): String {
    val used = existing.mapTo(HashSet()) { it.name }
    if (base !in used) return base
    var n = 2
    while ("$base $n" in used) n++
    return "$base $n"
}

/** 同じ id があれば差し替え、無ければ末尾へ足す。 */
fun List<KeyLayout>.upsertLayout(layout: KeyLayout): List<KeyLayout> {
    val at = indexOfFirst { it.id == layout.id }
    return if (at < 0) this + layout else toMutableList().also { it[at] = layout }
}

/** 名前だけ差し替える。⚠ 見つからなければ**何もしない**（束を壊さない）。 */
fun List<KeyLayout>.renameLayout(id: String, name: String): List<KeyLayout> =
    map { if (it.id == id) it.copy(name = name) else it }

/** 1 件だけ消す。 */
fun List<KeyLayout>.removeLayout(id: String): List<KeyLayout> = filterNot { it.id == id }

/**
 * 1 件消したあと、次に選ぶ id を返す。
 *
 * ⚠ **消した 1 件が選ばれていたときだけ**選び直す。関係ない 1 件を消したのに使っている
 * 配列が変わったら、何が起きたか分からない。戻り先は**既定のプリセット**（空文字）。
 */
fun nextActiveAfterRemove(activeId: String, removedId: String): String =
    if (activeId == removedId) "" else activeId
