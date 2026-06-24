package com.zerotoship.z2term.emulator

/**
 * ターミナルの 1 行を表す。
 * 内部的にはセル配列で管理。
 */
class TerminalRow(initialColumns: Int) {
    private var cells: Array<TerminalCell> = Array(initialColumns) { TerminalCell() }

    /** ダーティフラグ (再描画必要) */
    var dirty: Boolean = true

    /** 折り返し行か (この行の末尾が次行に継続している) */
    var wrapped: Boolean = false

    /**
     * この行を anchor (top-left) とする画像 placement のリスト (Kitty graphics 等)。
     * 空のあいだは画像なし。 画像は `widthCells × heightCells` の矩形を占有し、
     * Renderer は anchor 行を描く回で配下のセルへ Bitmap を一括描画する。 同じ anchor 行
     * 上に複数 placement (異なる image id / 異なる placement id) が乗ることがある
     * (例: 1 行に小サムネを横並びで複数置く TUI) ため、`null` ではなくリストで保持する。
     * 文字書込み (`setChar`) や `clear` が画像占有領域に被ったときは、そのセルに被さる
     * placement だけを除去する (他の placement は残す)。
     */
    val images: MutableList<TerminalImage> = mutableListOf()

    val columns: Int get() = cells.size

    /**
     * 列幅変更時の再構成。
     *
     * 縮小時は「行末の実内容より右側の余白」だけを捨て、文字の入ったセルは保持する。
     * これにより、ピンチで一度幅を狭めて元に戻したときに、右端へはみ出した文字が
     * 消えずに復活する (描画側は表示幅でクリップするだけ)。内容が新幅に収まる行は
     * 通常どおり新幅まで縮める。
     */
    fun resize(newColumns: Int) {
        if (newColumns == cells.size) return
        // 画像 anchor + 幅が新列幅で範囲外になる placement を破棄。 残せる placement は維持。
        // Kitty 仕様は resize 後の画像保証はしないが、本実装は範囲内のものは保つ
        // (シンプル端末で「リサイズしても入る画像は残る」のと等価)。
        if (images.isNotEmpty()) {
            images.removeAll { it.col + it.widthCells > newColumns }
        }
        if (newColumns < cells.size) {
            val keep = contentWidth().coerceAtLeast(newColumns)
            if (keep == cells.size) return  // 捨てられる余白なし → そのまま保持
            cells = Array(keep) { cells[it] }
            dirty = true
            return
        }
        cells = Array(newColumns) { i ->
            if (i < cells.size) cells[i] else TerminalCell()
        }
        dirty = true
    }

    /** 末尾の空白を除いた実内容の列数 (wide-cont セルは内容として扱う) */
    private fun contentWidth(): Int {
        var end = cells.size - 1
        while (end >= 0 && cells[end].char == ' ' && !cells[end].wideCont) end--
        return end + 1
    }

    fun getCell(col: Int): TerminalCell {
        require(col in cells.indices) { "col=$col out of range [0,${cells.size})" }
        return cells[col]
    }

    fun setChar(col: Int, ch: Char, fg: Int, bg: Int, wideCont: Boolean = false, link: String? = null) {
        if (col !in cells.indices) return
        cells[col].char = ch
        cells[col].fgAttr = fg
        cells[col].bgAttr = bg
        cells[col].wideCont = wideCont
        cells[col].link = link
        // 画像領域に文字が書かれたら被さる placement だけ無効化 (TUI が画像の上に文字を
        // 書いた = "消した" と解釈)。 [TerminalEmulator.commitKittyImage] では空白埋め →
        // image 追加の順に呼ぶので、commit 直後の画像本体が自分自身を消すことはない。
        invalidateImagesHittingCol(col)
        dirty = true
    }

    /** [col] が画像の占有セル範囲に入っている placement を破棄する。 */
    private fun invalidateImagesHittingCol(col: Int) {
        if (images.isEmpty()) return
        images.removeAll { img -> col >= img.col && col < img.col + img.widthCells }
    }

    /** [from, to) を消去 */
    fun clear(from: Int = 0, to: Int = cells.size, fg: Int = SgrAttribute.DEFAULT, bg: Int = SgrAttribute.DEFAULT) {
        val start = from.coerceAtLeast(0)
        val end = to.coerceAtMost(cells.size)
        for (i in start until end) {
            cells[i].setClearedWith(fg, bg)
        }
        // clear 範囲に anchor col が入っている placement を破棄 (他の placement は残す)。
        if (images.isNotEmpty()) {
            images.removeAll { it.col in start until end }
        }
        dirty = true
    }

    /** col 位置に文字を挿入 (右側を右にシフト) */
    fun insertChars(col: Int, count: Int, fg: Int, bg: Int) {
        if (col !in cells.indices || count <= 0) return
        val shiftCount = (cells.size - col - count).coerceAtLeast(0)
        // 右にシフト
        for (i in cells.size - 1 downTo col + count) {
            if (i - count >= col) {
                cells[i].copyFrom(cells[i - count])
            }
        }
        // 空白で埋める
        for (i in col until (col + count).coerceAtMost(cells.size)) {
            cells[i].setClearedWith(fg, bg)
        }
        dirty = true
    }

    /** col 位置から count 文字削除 (左側にシフト) */
    fun deleteChars(col: Int, count: Int, fg: Int, bg: Int) {
        if (col !in cells.indices || count <= 0) return
        // 左にシフト
        for (i in col until cells.size - count) {
            cells[i].copyFrom(cells[i + count])
        }
        // 末尾を空白で
        for (i in (cells.size - count).coerceAtLeast(col) until cells.size) {
            cells[i].setClearedWith(fg, bg)
        }
        dirty = true
    }

    /**
     * 行の表示用文字列を返す (デバッグ・コピー用)。
     * 末尾の空白は除去し、wide-cont セルは出力しない (左セルが本体の文字を持つ)。
     */
    fun toText(): String {
        var end = cells.size - 1
        while (end >= 0 && cells[end].char == ' ' && !cells[end].wideCont) end--
        if (end < 0) return ""
        return buildString(end + 1) {
            for (i in 0..end) {
                val c = cells[i]
                // wide-cont でも低サロゲート (絵文字/CJK 拡張の右半分) は出力する。
                if (!c.wideCont || c.char.isLowSurrogate()) append(c.char)
            }
        }
    }

    fun copyFrom(other: TerminalRow) {
        if (cells.size != other.cells.size) {
            cells = Array(other.cells.size) { TerminalCell() }
        }
        for (i in cells.indices) {
            cells[i].copyFrom(other.cells[i])
        }
        wrapped = other.wrapped
        images.clear()
        images.addAll(other.images)
        dirty = true
    }
}
