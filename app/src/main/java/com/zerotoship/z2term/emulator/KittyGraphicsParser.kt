package com.zerotoship.z2term.emulator

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

/**
 * Kitty graphics protocol (APC `ESC _ G <key=value,…> ; <base64 payload> ESC \`) パーサ。
 *
 * 仕様: https://sw.kovidgoyal.net/kitty/graphics-protocol/
 *
 * 0.8.131 までで扱うアクション・フォーマット・転送:
 *  - アクション: `a=T` (transmit and display) / `a=t` (transmit only) / `a=p` (put existing
 *    image at cursor) / `a=d` (delete; `d=A`/`d=I`/`d=i`/`d=p` のサブを区別) / `a=q`
 *    (query)。
 *  - フォーマット: `f=100` (PNG, [BitmapFactory] でデコード) / `f=24` (生 RGB, 3 bytes/px,
 *    `s=N v=N` 必須) / `f=32` (生 RGBA, 4 bytes/px, `s=N v=N` 必須)。
 *  - 転送: `t=d` (direct base64) のみ。 `t=f`/`t=t`/`t=s` (file/temp/shm) は破棄。
 *  - チャンク連結: `m=1` 連続 + `m=0`/省略 終端で payload を結合する。 連結中はヘッダを
 *    最初の APC のものだけ使う (Kitty 仕様)。
 *  - `c=N` `r=N`: 表示セル数。 省略時は Bitmap のピクセル数を Renderer から渡された
 *    `cellWidthPx` / `lineHeightPx` で割って自動算出。
 *  - `i=N` (image id) / `p=N` (placement id): キャッシュ・配置・削除の照合。
 *  - `s=N` `v=N`: 生フォーマットの幅高 (px)。 PNG では BitmapFactory が自動取得する。
 *  - `U=1` + `a=T`/`a=p`: virtual placement (Unicode placeholder と組合せる遅延配置)。
 *    通常の `a=T`/`a=p` がカーソル位置に「実 placement」を作るのに対し、`U=1` は image を
 *    grid (cellsWidth × cellsHeight) の「仮想 placement」として登録するだけで、 実際の
 *    描画位置はあとから本文に書かれる Unicode placeholder セル (U+10EEEE) と
 *    combining diacritic (row/col/placementId エンコード) で決まる。
 *
 * 範囲外 (静かに `Discard`):
 *  - animation frames (`a=a`), file/temp/shm 転送, composition (`a=c`), Z-index への
 *    32bit 拡張など。
 */
class KittyGraphicsParser {

    private val current = StringBuilder(2048)
    private val payload = StringBuilder(2048)
    private var headerKeys: Map<String, String> = emptyMap()
    private var inMultiChunk = false

    fun feedByte(b: Int) {
        if (current.length > MAX_BUFFER_BYTES) return
        current.append(b.toChar())
    }

    fun finishSequence(cellWidthPx: Float, lineHeightPx: Float): Result {
        val raw = current.toString()
        current.clear()

        if (raw.isEmpty() || raw[0] != 'G') return Result.Discard

        val semi = raw.indexOf(';')
        val keysPart = if (semi >= 0) raw.substring(1, semi) else raw.substring(1)
        val bodyPart = if (semi >= 0) raw.substring(semi + 1) else ""

        val keys = parseKeys(keysPart)

        if (!inMultiChunk) headerKeys = keys
        payload.append(bodyPart.filter { it != '\n' && it != '\r' && it != ' ' })

        val moreChunks = (keys["m"] ?: "0") == "1"
        if (moreChunks) {
            inMultiChunk = true
            return Result.Continue
        }

        val header = headerKeys
        val payloadStr = payload.toString()
        headerKeys = emptyMap()
        payload.clear()
        inMultiChunk = false

        val action = header["a"] ?: "T"
        val imageId = header["i"]?.toIntOrNull() ?: 0
        val placementId = header["p"]?.toIntOrNull() ?: 0
        val quietLevel = (header["q"]?.toIntOrNull() ?: 0).coerceIn(0, 2)
        val zIndex = header["z"]?.toIntOrNull() ?: 0

        val unicodePlaceholder = (header["U"] ?: "0") == "1"

        return when (action) {
            "d" -> classifyDelete(header)
            "p" -> {
                val cellsW = header["c"]?.toIntOrNull()
                val cellsH = header["r"]?.toIntOrNull()
                if (unicodePlaceholder) {
                    Result.VirtualPut(
                        imageId = imageId,
                        placementId = placementId,
                        cellsWidth = cellsW,
                        cellsHeight = cellsH,
                        zIndex = zIndex
                    )
                } else {
                    Result.Put(
                        imageId = imageId,
                        placementId = placementId,
                        cellsWidth = cellsW,
                        cellsHeight = cellsH,
                        zIndex = zIndex
                    )
                }
            }
            "T", "t" -> handleTransmit(
                header,
                payloadStr,
                cellWidthPx,
                lineHeightPx,
                display = action == "T",
                imageId = imageId,
                placementId = placementId,
                quietLevel = quietLevel,
                zIndex = zIndex,
                unicodePlaceholder = unicodePlaceholder
            )
            "q" -> classifyQuery(header, payloadStr, imageId, quietLevel)
            else -> Result.Discard
        }
    }

    /**
     * `a=q` (query): TUI 側からのケイパビリティ確認。 Kitty 仕様では `t=d,f=N,s=1,v=1`
     * 等で 1px の payload を送って「parser が解釈できるか」を見る。 本実装は対応している
     * 組み合わせなら `OK`、未対応なら `ENOTSUPPORTED:<reason>` を返す。
     *
     * Quiet level の扱い (Kitty 仕様):
     *  - `q=0` (既定): success/error 両方返す
     *  - `q=1`: error のみ
     *  - `q=2`: 一切返さない
     */
    private fun classifyQuery(
        header: Map<String, String>,
        @Suppress("UNUSED_PARAMETER") payloadStr: String,
        imageId: Int,
        quietLevel: Int
    ): Result {
        // Kitty の query はヘッダで「parser が解釈できる組み合わせか」を聞くもの。
        // payload は本来 base64 でも、本実装は payload の中身まで踏み込まず、
        // format / transmission のサポート状況だけ返す (Kitty の reference 実装も同様)。
        val format = header["f"]?.toIntOrNull() ?: 32
        val transmission = header["t"] ?: "d"
        val (ok, reason) = when {
            transmission != "d" -> false to "ENOTSUPPORTED:t=$transmission"
            format != 100 && format != 24 && format != 32 -> false to "ENOTSUPPORTED:f=$format"
            else -> true to "OK"
        }
        return Result.Query(imageId = imageId, ok = ok, message = reason, quietLevel = quietLevel)
    }

    private fun handleTransmit(
        header: Map<String, String>,
        payloadStr: String,
        cellWidthPx: Float,
        lineHeightPx: Float,
        display: Boolean,
        imageId: Int,
        placementId: Int,
        quietLevel: Int,
        zIndex: Int,
        unicodePlaceholder: Boolean
    ): Result {
        val format = header["f"]?.toIntOrNull() ?: 100
        val transmission = header["t"] ?: "d"
        if (transmission != "d") return Result.Discard  // file/temp/shm 未対応

        val rawBytes = decodeBase64(payloadStr) ?: return Result.Discard
        val bitmap = when (format) {
            100 -> decodePng(rawBytes)
            24 -> buildRawBitmap(rawBytes, header, hasAlpha = false)
            32 -> buildRawBitmap(rawBytes, header, hasAlpha = true)
            else -> null
        } ?: return Result.Discard

        val cellsW = header["c"]?.toIntOrNull()
            ?: estimateCells(bitmap.width.toFloat(), cellWidthPx)
        val cellsH = header["r"]?.toIntOrNull()
            ?: estimateCells(bitmap.height.toFloat(), lineHeightPx)

        return Result.Transmit(
            bitmap = bitmap,
            widthCells = cellsW.coerceAtLeast(1),
            heightCells = cellsH.coerceAtLeast(1),
            imageId = imageId,
            placementId = placementId,
            display = display,
            quietLevel = quietLevel,
            zIndex = zIndex,
            unicodePlaceholder = unicodePlaceholder
        )
    }

    private fun classifyDelete(header: Map<String, String>): Result {
        // `d` サブパラメータ。 省略時は "A" 扱い (Kitty 仕様)。
        // image id は大文字 `I=N` (free / 削除対象が確実に存在する形) と小文字 `i=N`
        // (keep / 通常照合) の両方をサポート。 削除照合上はどちらも同じ id 値として扱う。
        val sub = header["d"] ?: "A"
        val imageId = header["I"]?.toIntOrNull() ?: header["i"]?.toIntOrNull() ?: 0
        val placementId = header["p"]?.toIntOrNull() ?: 0
        return when (sub.uppercase()) {
            "A" -> Result.DeleteAll
            "I" -> if (imageId != 0) Result.DeleteImage(imageId) else Result.DeleteAll
            // `d=p` は (image id, placement id) ペアでの個別削除。 両方無いと安全側で Discard。
            "P" -> if (imageId != 0 && placementId != 0) Result.DeletePlacement(imageId, placementId)
                   else Result.Discard
            else -> Result.DeleteAll
        }
    }

    private fun decodePng(bytes: ByteArray): Bitmap? =
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()

    /**
     * `f=24` (RGB, 3 bytes/px) または `f=32` (RGBA, 4 bytes/px) の生バイト列を
     * [Bitmap] に組み立てる。 `s=N` `v=N` で px 幅高が必要 (どちらか欠ければ Discard)。
     * 入力の総バイト数が `s * v * bytesPerPx` 未満なら不正と判定して null。
     *
     * Bitmap への変換は `ARGB_8888` に正規化する (Compose の Canvas が一番素直に扱える
     * フォーマット)。 入力 RGB は `0xFF<RGB>` に拡張、RGBA は `aRGB` 順に並べ替える。
     */
    private fun buildRawBitmap(bytes: ByteArray, header: Map<String, String>, hasAlpha: Boolean): Bitmap? {
        val w = header["s"]?.toIntOrNull() ?: return null
        val h = header["v"]?.toIntOrNull() ?: return null
        if (w <= 0 || h <= 0) return null
        val bpp = if (hasAlpha) 4 else 3
        val expected = w.toLong() * h.toLong() * bpp.toLong()
        if (expected > Int.MAX_VALUE || bytes.size < expected) return null
        val pixels = IntArray(w * h)
        var src = 0
        var dst = 0
        if (hasAlpha) {
            while (dst < pixels.size) {
                val r = bytes[src].toInt() and 0xFF
                val g = bytes[src + 1].toInt() and 0xFF
                val b = bytes[src + 2].toInt() and 0xFF
                val a = bytes[src + 3].toInt() and 0xFF
                pixels[dst] = (a shl 24) or (r shl 16) or (g shl 8) or b
                src += 4
                dst++
            }
        } else {
            while (dst < pixels.size) {
                val r = bytes[src].toInt() and 0xFF
                val g = bytes[src + 1].toInt() and 0xFF
                val b = bytes[src + 2].toInt() and 0xFF
                pixels[dst] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                src += 3
                dst++
            }
        }
        return runCatching {
            Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }

    fun reset() {
        current.clear()
        payload.clear()
        headerKeys = emptyMap()
        inMultiChunk = false
    }

    private fun parseKeys(s: String): Map<String, String> {
        if (s.isEmpty()) return emptyMap()
        val out = HashMap<String, String>(8)
        for (kv in s.split(',')) {
            val eq = kv.indexOf('=')
            if (eq <= 0) continue
            val k = kv.substring(0, eq)
            val v = kv.substring(eq + 1)
            out[k] = v
        }
        return out
    }

    private fun decodeBase64(s: String): ByteArray? {
        if (s.isEmpty()) return null
        return runCatching { Base64.decode(s, Base64.DEFAULT) }.getOrNull()
    }

    private fun estimateCells(pixels: Float, perCell: Float): Int {
        if (perCell <= 0f) return 1
        return (pixels / perCell + 0.5f).toInt().coerceAtLeast(1)
    }

    sealed class Result {
        /** チャンク継続。 */
        data object Continue : Result()
        /** 未対応 / 不正。 */
        data object Discard : Result()
        /** `a=d,d=A`: 全画像消去 + キャッシュ全消去。 */
        data object DeleteAll : Result()
        /** `a=d,d=I,I=N` / `d=i,i=N`: image id 単位で削除 + キャッシュも削除。 */
        class DeleteImage(val imageId: Int) : Result()
        /** `a=d,d=p,i=N,p=N`: 特定 placement のみ削除 (画像キャッシュは残る)。 */
        class DeletePlacement(val imageId: Int, val placementId: Int) : Result()
        /**
         * `a=T` (display = true) / `a=t` (display = false): 画像本体を返す。
         * 呼び出し側は [display] が true ならカーソル位置に placement、いずれにせよ
         * `imageId != 0` ならキャッシュに登録する。
         *
         * [unicodePlaceholder] が true (`U=1`) のときは「カーソル位置に置く」ではなく
         * 「virtual placement として登録だけして、後段の本文に書かれる U+10EEEE +
         * combining diacritic で位置決めする」モード。 呼び出し側は cache 登録 +
         * virtual placement 登録のみを行い、 cursor は動かさない。
         */
        class Transmit(
            val bitmap: Bitmap,
            val widthCells: Int,
            val heightCells: Int,
            val imageId: Int,
            val placementId: Int,
            val display: Boolean,
            val quietLevel: Int = 0,
            val zIndex: Int = 0,
            val unicodePlaceholder: Boolean = false
        ) : Result()
        /**
         * `a=p`: 既存画像 (`imageId`) をカーソル位置に配置するよう要求。
         * 呼び出し側はキャッシュから Bitmap を引き、現在位置に placement を追加する。
         * `cellsWidth` / `cellsHeight` が null ならキャッシュ画像の native px と
         * cell hint から自動算出。
         */
        class Put(
            val imageId: Int,
            val placementId: Int,
            val cellsWidth: Int?,
            val cellsHeight: Int?,
            val zIndex: Int = 0
        ) : Result()
        /**
         * `a=p,U=1`: virtual placement の登録要求 (Unicode placeholder 経由の遅延描画)。
         * 呼び出し側はキャッシュ画像を引き、(cellsWidth × cellsHeight) のタイル分割
         * グリッドで virtual placement を登録する。 cursor は動かさず、 描画は本文に
         * 書かれる U+10EEEE + diacritic セルから引かれる。
         *
         * `cellsWidth` / `cellsHeight` が null ならキャッシュ画像の native px と
         * cell hint から自動算出。
         */
        class VirtualPut(
            val imageId: Int,
            val placementId: Int,
            val cellsWidth: Int?,
            val cellsHeight: Int?,
            val zIndex: Int = 0
        ) : Result()
        /**
         * `a=q` (query): TUI 側のケイパビリティ確認。 呼び出し側は [quietLevel] に応じて
         * `\e_Gi=<id>;<message>\e\\` を `output` 経由で返す。 `ok=false` (エラー) なら
         * `quietLevel <= 1` で送信、`ok=true` (成功) なら `quietLevel == 0` のみ送信。
         */
        class Query(
            val imageId: Int,
            val ok: Boolean,
            val message: String,
            val quietLevel: Int
        ) : Result()
    }

    companion object {
        private const val MAX_BUFFER_BYTES = 8 * 1024 * 1024
    }
}
