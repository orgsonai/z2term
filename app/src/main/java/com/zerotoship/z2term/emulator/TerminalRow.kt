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
     * この行を anchor (top-left) とする画像 placement (Kitty graphics 等)。
     * null の間は画像なし。 画像は `widthCells × heightCells` の矩形を占有し、
     * Renderer は anchor 行を描く回で配下のセルへ Bitmap を一括描画する。
     * 文字書込み・clear/insert/delete が image 占有領域に被ったときは
     * Emulator 側で `image = null` を入れて invalidate する。
     */
    var image: TerminalImage? = null

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
        // 画像 anchor が新列幅で範囲外になるなら破棄。 残せる場合は維持する
        // (Kitty 仕様は resize 後の画像保証はしないが、本実装は anchor が
        // 新範囲に収まる限り画像を保つ ≒ シンプル端末で「リサイズしても画像が
        // ちょっと右へはみ出してもそのまま残る」のと等価)。
        val img = image
        if (img != null && img.col + img.widthCells > newColumns) image = null
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
        // 画像領域に文字が書かれたら画像を無効化 (TUI が画像の上に文字を書いた = "消した"
        // と解釈する)。 [TerminalEmulator.commitKittyImage] では空白埋め→image セットの
        // 順に呼ぶので画像本体が消えることはない。
        invalidateImageIfHit(col)
        dirty = true
    }

    /** [col] が現在の anchor 画像の占有セル範囲に入っていたら画像を破棄する。 */
    private fun invalidateImageIfHit(col: Int) {
        val img = image ?: return
        if (col >= img.col && col < img.col + img.widthCells) image = null
    }

    /** [from, to) を消去 */
    fun clear(from: Int = 0, to: Int = cells.size, fg: Int = SgrAttribute.DEFAULT, bg: Int = SgrAttribute.DEFAULT) {
        val start = from.coerceAtLeast(0)
        val end = to.coerceAtMost(cells.size)
        for (i in start until end) {
            cells[i].setClearedWith(fg, bg)
        }
        // clear 範囲に image の anchor col が含まれていたら画像も破棄。
        val img = image
        if (img != null && img.col in start until end) image = null
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
        image = other.image
        dirty = true
    }
}
