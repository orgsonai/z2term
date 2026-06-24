package com.zerotoship.z2term.emulator

import android.graphics.Bitmap

/**
 * 端末バッファ内に配置された 1 つの画像 placement。
 *
 * 設計:
 *  - 画像は「anchor 行」 (top-left のセルがある [TerminalRow]) に紐付ける。
 *    anchor 行が scrollback に押し出されても TerminalRow オブジェクトは生きているため、
 *    画像の参照も自動で追従する (scroll で行が動いてもキャンバス相対の絶対 row を
 *    Renderer 側が再計算するだけで済む)。
 *  - anchor 行が scrollback 容量を超えて捨てられたら画像も自動で GC される。
 *  - 複数行にまたがる画像でも、anchor 行 1 つにだけ image を持たせ、Renderer は
 *    anchor 行を描く回で `widthCells × heightCells` の矩形を一括描画する。
 *  - 1 つの anchor 行に複数 placement (同じ画像の別 placement / 別画像) が乗ることが
 *    あるため、`TerminalRow` は [List]<TerminalImage> で保持する。
 *
 * @property col anchor 行内の開始列 (top-left)
 * @property widthCells 画像が占めるセル幅
 * @property heightCells 画像が占めるセル高
 * @property bitmap 元画像 (PNG/RGB(A) を BitmapFactory でデコード済み、または f=24/f=32 で
 *                  生 RGB(A) から組み立てた Bitmap)
 * @property imageId Kitty graphics の `i=N` (なければ 0)。 削除コマンドの照合用。
 * @property placementId Kitty graphics の `p=N` (なければ 0)。 同じ image id 配下の
 *                      placement を識別する。 `a=d,d=p,i=N,p=N` で個別削除に使う。
 */
class TerminalImage(
    val col: Int,
    val widthCells: Int,
    val heightCells: Int,
    val bitmap: Bitmap,
    val imageId: Int = 0,
    val placementId: Int = 0,
    /**
     * Kitty graphics の `z=N` (Z-index)。 既定 0。
     * Renderer は 2 層に分けて描画する: **`zIndex < 0` の placement はテキストの下層**、
     * **`zIndex >= 0` の placement はテキストの上層**。 同じ層内では追加順 (= 後勝ち)。
     * これにより TUI が「画像の上に文字を読みやすく重ねる」「アイコンを文字の前面に出す」
     * の両方を表現できる。
     */
    val zIndex: Int = 0
)
