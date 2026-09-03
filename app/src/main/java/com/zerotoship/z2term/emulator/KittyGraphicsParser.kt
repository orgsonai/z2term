package com.zerotoship.z2term.emulator

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Kitty graphics protocol (APC `ESC _ G <key=value,…> ; <base64 payload> ESC \`) パーサ。
 *
 * 仕様: https://sw.kovidgoyal.net/kitty/graphics-protocol/
 *
 * 扱うアクション・フォーマット・転送:
 *  - アクション: `a=T` (transmit and display) / `a=t` (transmit only) / `a=p` (put existing
 *    image at cursor) / `a=d` (delete; `d=A`/`d=I`/`d=i`/`d=p` のサブを区別) / `a=q`
 *    (query) / `a=f` (frame transmit, animation 用)。
 *  - フォーマット: `f=100` (PNG, [BitmapFactory] でデコード) / `f=24` (生 RGB, 3 bytes/px,
 *    `s=N v=N` 必須) / `f=32` (生 RGBA, 4 bytes/px, `s=N v=N` 必須)。
 *  - 転送: `t=d` (direct base64) は常時。 `t=f`/`t=t`/`t=s` (file/temp/shm) は
 *    [externalTransferSource] が **明示的に注入されたとき** のみ。 注入されない (= 既定 OFF)
 *    間はすべて破棄され、`a=q` も ENOTSUPPORTED を返す (opt-in セキュリティ既定)。
 *  - チャンク連結: `m=1` 連続 + `m=0`/省略 終端で payload を結合する。 連結中はヘッダを
 *    最初の APC のものだけ使う (Kitty 仕様)。
 *  - `c=N` `r=N`: 表示セル数。 省略時は Bitmap のピクセル数を Renderer から渡された
 *    `cellWidthPx` / `lineHeightPx` で割って自動算出。
 *  - `i=N` (image id) / `p=N` (placement id): キャッシュ・配置・削除の照合。
 *  - `s=N` `v=N`: 生フォーマットの幅高 (px)。 PNG では BitmapFactory が自動取得する。
 *  - `O=N` (offset bytes) / `S=N` (size bytes): `t=f`/`t=t`/`t=s` でファイルの一部だけ読む
 *    レンジ指定。 省略時は 0 / 末尾まで。
 *  - `U=1` + `a=T`/`a=p`: virtual placement (Unicode placeholder と組合せる遅延配置)。
 *    通常の `a=T`/`a=p` がカーソル位置に「実 placement」を作るのに対し、`U=1` は image を
 *    grid (cellsWidth × cellsHeight) の「仮想 placement」として登録するだけで、 実際の
 *    描画位置はあとから本文に書かれる Unicode placeholder セル (U+10EEEE) と
 *    combining diacritic (row/col/placementId エンコード) で決まる。
 *
 * 範囲外 (静かに `Discard`):
 *  - composition (`a=c`)、画像変形系の追加オプション。
 */
class KittyGraphicsParser {

    /**
     * `t=f` (regular file) / `t=t` (temporary file, 読了後 unlink) / `t=s` (shared memory) の
     * 種別。 [ExternalTransferSource.read] に渡してホスト側で出し分ける。
     */
    enum class TransferKind { File, TempFile, SharedMemory }

    /**
     * `t=f`/`t=t`/`t=s` 経路の payload 取得を担う interface。 **既定では未設定で
     * file/temp/shm はすべて破棄**。 セッション側で opt-in 設定が ON のとき、
     * ホスト/ゲストのパス変換 + 実ファイル/SHM 読込を実装した実体を注入する。
     *
     *  - `name` は TUI 側から base64 デコードして得たパス文字列 (`t=f`/`t=t` は絶対
     *    パス相当、 `t=s` は POSIX shm 名 `/<name>` 形式)。
     *  - `offset` / `size` は Kitty 仕様の `O=N` / `S=N`。 `size < 0` のときは末尾まで。
     *  - 戻り値はファイル/SHM から読んだ生バイト列。 PNG なら BitmapFactory 用、生 RGB(A)
     *    ならそのまま組立に使う。 失敗 (権限 / 不在 / 読込打ち切り) は null。
     *  - `kind == TempFile` のときは読了後の unlink まで実装側で行う (parser からは
     *    再度呼ばれない前提)。
     */
    fun interface ExternalTransferSource {
        fun read(kind: TransferKind, name: String, offset: Long, size: Long): ByteArray?
    }

    /**
     * `t=f`/`t=t`/`t=s` 経路の payload 取得元。 null (既定) のときはすべて Discard。
     * セッション側で AppSettings の opt-in が ON のときに非 null を注入する。
     */
    var externalTransferSource: ExternalTransferSource? = null

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
        // Kitty 仕様: `a=f` のときだけ `z=N` は **frame delay (ms)** を意味する。
        // それ以外のアクションでは Z-index として読む (既定 0)。
        val zIndex = if (action == "f") 0 else (header["z"]?.toIntOrNull() ?: 0)

        val unicodePlaceholder = (header["U"] ?: "0") == "1"

        return when (action) {
            "d" -> classifyDelete(header)
            "f" -> handleFrame(header, payloadStr, imageId, quietLevel)
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
        val compression = header["o"] ?: ""
        val externalAllowed = externalTransferSource != null
        val (ok, reason) = when {
            transmission != "d" && !isExternalKind(transmission) ->
                false to "ENOTSUPPORTED:t=$transmission"
            transmission != "d" && !externalAllowed ->
                false to "ENOTSUPPORTED:t=$transmission"
            format != 100 && format != 24 && format != 32 -> false to "ENOTSUPPORTED:f=$format"
            compression.isNotEmpty() && compression != "z" -> false to "ENOTSUPPORTED:o=$compression"
            else -> true to "OK"
        }
        return Result.Query(imageId = imageId, ok = ok, message = reason, quietLevel = quietLevel)
    }

    private fun isExternalKind(t: String): Boolean = t == "f" || t == "t" || t == "s"

    private fun transferKindOf(t: String): TransferKind? = when (t) {
        "f" -> TransferKind.File
        "t" -> TransferKind.TempFile
        "s" -> TransferKind.SharedMemory
        else -> null
    }

    /**
     * `t=d` または `t=f`/`t=t`/`t=s` から payload バイト列を取り出す。
     *  - `t=d`: payload は base64 ペイロード。 デコード後、 `o=z` 指定なら inflate。
     *  - `t=f`/`t=t`/`t=s`: payload を base64 デコードして UTF-8 パス/SHM 名に直し、
     *    [externalTransferSource] に `(kind, name, offset, size)` で読み取り依頼。
     *    取得結果はそのまま (透過) で扱う。 圧縮指定 (`o=z`) は file/temp/shm 経路でも
     *    inflate に通す (Kitty 仕様で禁止されていない)。
     *  - source 未注入 (= 既定) のときの file/temp/shm は null (= Discard) で返す。
     */
    private fun obtainPayloadBytes(header: Map<String, String>, payloadStr: String): ByteArray? {
        val transmission = header["t"] ?: "d"
        if (transmission == "d") {
            val raw = decodeBase64(payloadStr) ?: return null
            return maybeInflate(header, raw)
        }
        val kind = transferKindOf(transmission) ?: return null
        val source = externalTransferSource ?: return null
        val pathBytes = decodeBase64(payloadStr) ?: return null
        val name = runCatching { String(pathBytes, Charsets.UTF_8) }.getOrNull()
            ?.takeIf { it.isNotEmpty() } ?: return null
        val offset = header["O"]?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        val size = header["S"]?.toLongOrNull() ?: -1L
        val raw = source.read(kind, name, offset, size) ?: return null
        return maybeInflate(header, raw)
    }

    /**
     * `a=f`: Kitty animation の frame transmit。 既存 image (`imageCache` の原画像 = frame 0)
     * に対し、 frame 1 以降の bitmap を追加する。
     *
     * 必須: `i=N` (image id; 0 は無効) と payload (Bitmap が組み立てられること)。
     * 任意: `r=N` (1-based frame index; 段階 7 では「追加順 = appendix」のみで replace 未対応)、
     * `z=N` (delay ms; 既定 40)、 `c=N` (compose; 0 = replace / 1 = α over)、
     * `X=N` / `Y=N` (image canvas 内オフセット px)、
     * `s=N` / `v=N` / `f=N` (生 RGB(A) のときの幅高・フォーマット)。
     *
     * 段階 7 (0.8.133) では受領・蓄積のみで実際の再生は段階 8 で行う。
     */
    private fun handleFrame(
        header: Map<String, String>,
        payloadStr: String,
        imageId: Int,
        quietLevel: Int
    ): Result {
        if (imageId == 0) return Result.Discard
        val rawBytes = obtainPayloadBytes(header, payloadStr) ?: return Result.Discard
        val format = header["f"]?.toIntOrNull() ?: 32
        val bitmap = when (format) {
            100 -> decodePng(rawBytes)
            24 -> buildRawBitmap(rawBytes, header, hasAlpha = false)
            32 -> buildRawBitmap(rawBytes, header, hasAlpha = true)
            else -> null
        } ?: return Result.Discard
        val delayMs = (header["z"]?.toIntOrNull() ?: 40).coerceAtLeast(0)
        val composeMode = header["c"]?.toIntOrNull() ?: 0
        val xOffset = header["X"]?.toIntOrNull() ?: 0
        val yOffset = header["Y"]?.toIntOrNull() ?: 0
        val frameIndex = header["r"]?.toIntOrNull() ?: 0
        return Result.Frame(
            imageId = imageId,
            bitmap = bitmap,
            delayMs = delayMs,
            composeMode = composeMode,
            xOffset = xOffset,
            yOffset = yOffset,
            frameIndex = frameIndex,
            quietLevel = quietLevel
        )
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
        val rawBytes = obtainPayloadBytes(header, payloadStr) ?: return Result.Discard
        val decoded = when (format) {
            100 -> decodeImage(rawBytes)
            // 生 RGB(A) は s=/v= で画素数を宣言済み = 間引く余地がないので、そのまま包む。
            24 -> buildRawBitmap(rawBytes, header, hasAlpha = false)?.let { Decoded(it, it.width, it.height) }
            32 -> buildRawBitmap(rawBytes, header, hasAlpha = true)?.let { Decoded(it, it.width, it.height) }
            else -> null
        } ?: return Result.Discard

        // ⚠ 間引く前の画素数で数える。間引き後で数えると絵が勝手に小さくなる。
        val cellsW = header["c"]?.toIntOrNull()
            ?: estimateCells(decoded.srcWidth.toFloat(), cellWidthPx)
        val cellsH = header["r"]?.toIntOrNull()
            ?: estimateCells(decoded.srcHeight.toFloat(), lineHeightPx)

        return Result.Transmit(
            bitmap = decoded.bitmap,
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

    /**
     * 復号した Bitmap と、**間引く前の**画素数 ([srcWidth] / [srcHeight])。
     *
     * セル数を出すのは常に間引く前の値。間引きは**記憶量の話**で、絵の大きさの話ではない。
     */
    private class Decoded(val bitmap: Bitmap, val srcWidth: Int, val srcHeight: Int)

    /**
     * PNG / JPEG / WebP / GIF / BMP を復号する ([BitmapFactory] が読める形式すべて)。
     *
     * ⚠ **大きすぎるものは間引いて読む** (0.8.495)。スマホのカメラで撮った 12MP の写真は
     * ARGB_8888 で約 50MB あり、`imageCache` は原画像を持ち続けるので、数枚出しただけで
     * アプリが落ちる。端末に出るのはたかだか数十セル (数百 px) なので、[MAX_DECODED_PIXELS]
     * まで落としても見た目は変わらない。`inSampleSize` は 2 の冪でしか効かないため、
     * 上限を「越えなくなるまで倍々で間引く」形にしてある。
     *
     * ⚠ セル数は間引き後の Bitmap ではなく [Decoded.srcWidth] / [Decoded.srcHeight] から出す。
     * 間引いた値で出すと、`c=`/`r=` を省いた送り手の絵が**勝手に小さくなる**。
     */
    private fun decodeImage(bytes: ByteArray): Decoded? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        var sample = 1
        if (srcW > 0 && srcH > 0) {
            while ((srcW.toLong() / sample) * (srcH.toLong() / sample) > MAX_DECODED_PIXELS) {
                sample *= 2
            }
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            ?: return@runCatching null
        // 寸法だけの読み取りに失敗する形式でも、復号できたなら Bitmap 自身の値を使う。
        Decoded(bmp, if (srcW > 0) srcW else bmp.width, if (srcH > 0) srcH else bmp.height)
    }.getOrNull()

    private fun decodePng(bytes: ByteArray): Bitmap? = decodeImage(bytes)?.bitmap

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
        // JVM 標準 `java.util.Base64` を使う (minSdk 29 = Java 8 同等で利用可)。 これにより
        // unit test (Robolectric なし) でも実装が走り、 file/temp/shm 経路の path 解決を
        // 含む parser 側ロジックを直接テストできる。 Kitty 仕様は標準 base64 なので互換。
        return runCatching { java.util.Base64.getDecoder().decode(s) }.getOrNull()
    }

    /**
     * Kitty graphics の `o=z` (zlib 圧縮) を展開する。
     *  - 入力 [compressed]: base64 デコード済みの zlib stream
     *  - 出力: 解凍後のバイト列 (生 RGB(A) または PNG)
     *  - 解凍失敗 / メモリ枯渇は null を返す (呼び元で `Discard`)
     *  - 安全のため、 展開結果が `MAX_INFLATED_BYTES` を超えると途中で打ち切って null
     *    (悪意ある zip-bomb 対策)。
     */
    private fun inflateZlib(compressed: ByteArray): ByteArray? = runCatching {
        val inflater = java.util.zip.Inflater()
        try {
            inflater.setInput(compressed)
            val out = java.io.ByteArrayOutputStream(compressed.size.coerceAtLeast(64))
            val buf = ByteArray(16 * 1024)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) return@runCatching null
                    break
                }
                out.write(buf, 0, n)
                if (out.size() > MAX_INFLATED_BYTES) return@runCatching null
            }
            out.toByteArray()
        } finally {
            inflater.end()
        }
    }.getOrNull()

    /**
     * `o=z` (zlib) 指定があれば base64 デコード後のバイト列を inflate して返す。
     * `o` 未指定 / `o=` 空 はそのまま透過。 それ以外の `o=` 値は **未対応** として null を
     * 返す (呼び元で `Discard`)。 仕様: Kitty graphics protocol の transmission options。
     */
    private fun maybeInflate(header: Map<String, String>, raw: ByteArray): ByteArray? {
        val opt = header["o"]?.takeIf { it.isNotEmpty() } ?: return raw
        return when (opt) {
            "z" -> inflateZlib(raw)
            else -> null
        }
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
        /**
         * `a=f` (frame transmit): 既存 image の animation frame 追加。
         * 呼び出し側は `TerminalBuffer.addAnimationFrame` で蓄積する。
         * 段階 7 (0.8.133) では蓄積のみで実描画は frame 0 (`imageCache` の原画像) を継続表示。
         * frame 切替 / delay 駆動の再生は段階 8 で対応。
         *
         * @property frameIndex Kitty 仕様 `r=N` (1-based)。 0 = 末尾追加 (段階 7 はこれだけ)。
         */
        class Frame(
            val imageId: Int,
            val bitmap: Bitmap,
            val delayMs: Int,
            val composeMode: Int,
            val xOffset: Int,
            val yOffset: Int,
            val frameIndex: Int,
            val quietLevel: Int
        ) : Result()
    }

    companion object {
        private const val MAX_BUFFER_BYTES = 8 * 1024 * 1024
        /** zlib 展開後の上限 (zip-bomb 対策)。 16 MiB を越えるなら拒否。 */
        private const val MAX_INFLATED_BYTES = 16 * 1024 * 1024

        /**
         * 復号後の Bitmap の画素数の上限 (0.8.495)。 越えるものは `inSampleSize` で
         * 間引いて読む。 400 万画素 = ARGB_8888 で約 16MB。
         *
         * ⚠ **拒否ではなく間引き**にする。 端末に出るのは数十セル (数百 px) なので、
         * ここまで落としても見た目は変わらない。 一方で拒否すると「写真は出せない」
         * という別の欠落になる。
         */
        private const val MAX_DECODED_PIXELS = 4_000_000L
    }
}
