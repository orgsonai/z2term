package com.zerotoship.z2term.ui.terminal.keyboard

/**
 * GUI エディタが指すキーの場所（0.8.410・段階 4）。
 *
 * [row] / [slot] は一番外の段と枠、[parts] は分割の中で辿る区画番号。たとえば田の字の
 * 右下は `[右側, 下側]` の 2 要素になる。モデルと同じく深さは最大 2。
 */
data class KeyCellPath(
    val row: Int,
    val slot: Int,
    val parts: List<Int> = emptyList(),
)

/** 指した場所にあるキー。壊れた / 分割そのものを指す path は null。 */
fun KeyLayout.keyAt(path: KeyCellPath): KeyDef? =
    contentAt(path)?.let { (it as? SlotContent.Single)?.key }

/** GUI で選べるすべての末端キーを、画面の順に返す。 */
fun KeyLayout.keyPaths(): List<KeyCellPath> = buildList {
    rows.forEachIndexed { rowIndex, row ->
        row.slots.forEachIndexed { slotIndex, slot ->
            collectPaths(slot.content, KeyCellPath(rowIndex, slotIndex), this)
        }
    }
}

private fun collectPaths(content: SlotContent, path: KeyCellPath, into: MutableList<KeyCellPath>) {
    when (content) {
        is SlotContent.Single -> into += path
        is SlotContent.Split -> content.parts.forEachIndexed { index, part ->
            collectPaths(part.content, path.copy(parts = path.parts + index), into)
        }
    }
}

/** 指したキーだけを置き換える。path が無効なら元をそのまま返す。 */
fun KeyLayout.updateKey(path: KeyCellPath, transform: (KeyDef) -> KeyDef): KeyLayout =
    updateContent(path) { content ->
        (content as? SlotContent.Single)?.let { SlotContent.Single(transform(it.key)) }
    }

/** 選択した複数キーへ同じ変更を適用する。無効な path は安全に読み飛ばす。 */
fun KeyLayout.updateKeys(paths: Collection<KeyCellPath>, transform: (KeyDef) -> KeyDef): KeyLayout =
    paths.fold(this) { current, path -> current.updateKey(path, transform) }

/** 外枠の幅を変更する。分割内の区画は外枠を共有するため [parts] は見ない。 */
fun KeyLayout.updateSlotWidth(path: KeyCellPath, width: KeyWidth): KeyLayout {
    val row = rows.getOrNull(path.row) ?: return this
    if (path.slot !in row.slots.indices) return this
    val changedRow = row.copy(
        slots = row.slots.mapIndexed { index, slot ->
            if (index == path.slot) slot.copy(width = width) else slot
        },
    )
    return copy(rows = rows.mapIndexed { index, old -> if (index == path.row) changedRow else old })
}

/**
 * 選択したキーが属する外枠すべてへ同じ幅を適用する。
 * 分割された兄弟キーは外枠を共有するため、同じ row/slot は 1 回だけ更新する。
 */
fun KeyLayout.updateSlotWidths(paths: Collection<KeyCellPath>, width: KeyWidth): KeyLayout =
    paths
        .distinctBy { it.row to it.slot }
        .fold(this) { current, path -> current.updateSlotWidth(path, width) }

/** 選択キーを 2 区画へ割る。新しい側は何も送らない空キー。 */
fun KeyLayout.splitKey(path: KeyCellPath, dir: SplitDir): KeyLayout {
    // top-level Single の parts.size=0、1 段割った中は 1。2 段割った中はもう割れない。
    if (path.parts.size >= KeyLayout.MAX_SPLIT_DEPTH) return this
    return updateContent(path) { content ->
        val single = content as? SlotContent.Single ?: return@updateContent null
        SlotContent.Split(
            dir = dir,
            parts = listOf(
                SlotPart(single),
                SlotPart(SlotContent.Single(KeyDef())),
            ),
        )
    }
}

/**
 * 選択キーが分割内なら、その親分割を**選択キーだけ**へ畳む。兄弟区画は消えるため UI 側で
 * 「分割を解除」と明示して呼ぶ。外枠キーなら何もしない。
 */
fun KeyLayout.collapseParentTo(path: KeyCellPath): KeyLayout {
    if (path.parts.isEmpty()) return this
    val selected = contentAt(path) ?: return this
    val parent = path.copy(parts = path.parts.dropLast(1))
    return updateContent(parent) { selected }
}

/**
 * 選択キーを消す。外枠なら枠を 1 つ削除、分割内なら区画を削除する。2 分割から 1 つを消した
 * ときは残った側へ自動で畳む。段の最後の 1 枠は消さない（段削除を使う）。
 */
fun KeyLayout.removeKeyCell(path: KeyCellPath): KeyLayout {
    val row = rows.getOrNull(path.row) ?: return this
    if (path.slot !in row.slots.indices) return this
    if (path.parts.isEmpty()) {
        if (row.slots.size <= 1) return this
        val changed = row.copy(slots = row.slots.filterIndexed { index, _ -> index != path.slot })
        return copy(rows = rows.mapIndexed { index, old -> if (index == path.row) changed else old })
    }

    val partIndex = path.parts.last()
    val parentPath = path.copy(parts = path.parts.dropLast(1))
    return updateContent(parentPath) { content ->
        val split = content as? SlotContent.Split ?: return@updateContent null
        if (partIndex !in split.parts.indices) return@updateContent null
        val left = split.parts.filterIndexed { index, _ -> index != partIndex }
        when (left.size) {
            0 -> null
            1 -> left.single().content
            else -> split.copy(parts = left)
        }
    }
}

/** 選択中の段の末尾へ空キーを 1 枠足す。 */
fun KeyLayout.appendKey(rowIndex: Int): KeyLayout {
    val row = rows.getOrNull(rowIndex) ?: return this
    val changed = row.copy(slots = row.slots + KeySlot.of(KeyDef()))
    return copy(rows = rows.mapIndexed { index, old -> if (index == rowIndex) changed else old })
}

/** 選択中の段の直後へ、空キー 1 つの段を足す。 */
fun KeyLayout.insertRowAfter(rowIndex: Int): KeyLayout {
    if (rowIndex !in rows.indices) return this
    val out = rows.toMutableList()
    out.add(rowIndex + 1, KeyRow(listOf(KeySlot.of(KeyDef()))))
    return copy(rows = out)
}

/** 段を削除する。最後の 1 段は消さない。 */
fun KeyLayout.removeRow(rowIndex: Int): KeyLayout =
    if (rows.size <= 1 || rowIndex !in rows.indices) this
    else copy(rows = rows.filterIndexed { index, _ -> index != rowIndex })

/** 外枠を左右へ 1 つ動かす。分割内を選んでいても、その外枠ごと動く。 */
fun KeyLayout.moveSlot(path: KeyCellPath, delta: Int): KeyLayout {
    val row = rows.getOrNull(path.row) ?: return this
    val to = path.slot + delta
    if (path.slot !in row.slots.indices || to !in row.slots.indices) return this
    val slots = row.slots.toMutableList()
    val moving = slots.removeAt(path.slot)
    slots.add(to, moving)
    val changed = row.copy(slots = slots)
    return copy(rows = rows.mapIndexed { index, old -> if (index == path.row) changed else old })
}

private fun KeyLayout.contentAt(path: KeyCellPath): SlotContent? {
    var content = rows.getOrNull(path.row)?.slots?.getOrNull(path.slot)?.content ?: return null
    path.parts.forEach { index ->
        val split = content as? SlotContent.Split ?: return null
        content = split.parts.getOrNull(index)?.content ?: return null
    }
    return content
}

/** path の場所にある content を差し替える。変換不能なら元のレイアウトを返す。 */
private fun KeyLayout.updateContent(
    path: KeyCellPath,
    transform: (SlotContent) -> SlotContent?,
): KeyLayout {
    val row = rows.getOrNull(path.row) ?: return this
    val slot = row.slots.getOrNull(path.slot) ?: return this
    val changedContent = updateNested(slot.content, path.parts, transform) ?: return this
    val changedRow = row.copy(
        slots = row.slots.mapIndexed { index, old ->
            if (index == path.slot) old.copy(content = changedContent) else old
        },
    )
    return copy(rows = rows.mapIndexed { index, old -> if (index == path.row) changedRow else old })
}

private fun updateNested(
    content: SlotContent,
    parts: List<Int>,
    transform: (SlotContent) -> SlotContent?,
): SlotContent? {
    if (parts.isEmpty()) return transform(content)
    val split = content as? SlotContent.Split ?: return null
    val index = parts.first()
    if (index !in split.parts.indices) return null
    val changed = updateNested(split.parts[index].content, parts.drop(1), transform) ?: return null
    return split.copy(
        parts = split.parts.mapIndexed { at, part ->
            if (at == index) part.copy(content = changed) else part
        },
    )
}
