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

/**
 * Kitty graphics の virtual placement (`a=p,U=1` または `a=T,U=1`) 1 件分の登録情報。
 *
 *  - [widthCells] × [heightCells]: タイル分割数。 本文中の Unicode placeholder セル
 *    (`U+10EEEE` + diacritic で row/col 指定) 1 個が、 元画像をこのグリッドで割った
 *    1 タイルを指す。
 *  - [bitmap]: 元画像 (タイルは描画時に矩形で切り出す)。 削除コマンドで本 spec が
 *    破棄されると Renderer は placeholder セルを空きとして扱う。
 *  - [placementId]: 同一 imageId 内で別の virtual placement を区別する 8bit (将来用途)。
 *  - [zIndex]: 通常 placement と同じ Z-index 規約 (`<0` テキスト下、 `>=0` テキスト上)。
 */
class VirtualPlacementSpec(
    val widthCells: Int,
    val heightCells: Int,
    val zIndex: Int,
    val placementId: Int,
    val bitmap: Bitmap
)

/**
 * Kitty graphics の animation 1 frame ぶんのメタ。
 *
 *  - [bitmap]: そのフレームの bitmap (PNG/RGB(A)/RGBA から組み立て済み)。
 *  - [delayMs]: 次のフレームへ遷移するまでの delay (ms)。 Kitty 仕様の `z=N`、 既定 40ms。
 *  - [composeMode]: Kitty `c=N` の合成モード。 0 = 既存フレームを置き換え (replace)、
 *    1 = α 合成で重ねる (over)。 本実装は 0/1 をそのまま保持するのみで、 実描画での
 *    合成は段階 8 (animation 再生) で対応する。
 *  - [xOffset] / [yOffset]: Kitty `X=N` / `Y=N` (px)。 frame の左上を image canvas 内の
 *    どこに置くかのオフセット。 本実装は保持のみ。
 *
 * 注意: Kitty 仕様の `a=f` (frame transmit) は `z=N` を **Z-index ではなく delay (ms)**
 * として解釈する。 本実装でも parser 側で action `f` のときだけ `z=N` を delay として読む。
 */
class AnimationFrame(
    val bitmap: Bitmap,
    val delayMs: Int,
    val composeMode: Int = 0,
    val xOffset: Int = 0,
    val yOffset: Int = 0
)
