package com.zerotoship.z2term.widget

/**
 * QR コードのエンコーダ (ウィジェット D3「SSH 接続 QR」用)。
 *
 * **外部ライブラリを足さず自前で書いている** (ユーザー方針 2026-07-24)。用途は
 * 「`ssh://user@host:port` を隣の PC のカメラに渡す」だけなので、必要十分に絞ってある:
 *
 *  - **8bit バイトモードのみ** (UTF-8。数字/英数モードの詰め込みはやらない)
 *  - **型番 1〜10** (バイトモード・誤り訂正 M で最大 122 バイト。ssh URI には十分)
 *  - 誤り訂正は **M 固定** (約 15% 復元。画面越しの読み取りで詰まらない実績値)
 *  - マスクは 0〜7 を全部試し、JIS X 0510 のペナルティ評価で最良を選ぶ
 *
 * 型番 7 以上で要る「型番情報」も入れてあるので 10 まで正しく出る。
 *
 * 検算しにくい部分 (Reed-Solomon・形式情報) は規格の既知値で
 * [com.zerotoship.z2term.widget.QrEncoderTest] が押さえている。
 */
object QrEncoder {

    /** 誤り訂正レベル M の形式情報ビット (規格の表より)。 */
    private const val ECC_M_BITS = 0b00

    /** 対応する最大型番。これを超える長さは [encode] が null を返す。 */
    private const val MAX_VERSION = 10

    /**
     * 型番ごとの [総コードワード数, 誤り訂正コードワード数/ブロック, ブロック数(グループ1), ブロック数(グループ2)]。
     * 誤り訂正レベル M のみ (JIS X 0510 表 9)。型番 1..10 の順。
     */
    private val M_SPEC = arrayOf(
        //      総CW,  EC/ブロック, G1ブロック数, G2ブロック数
        intArrayOf(26, 10, 1, 0),   // 1
        intArrayOf(44, 16, 1, 0),   // 2
        intArrayOf(70, 26, 1, 0),   // 3
        intArrayOf(100, 18, 2, 0),  // 4
        intArrayOf(134, 24, 2, 0),  // 5
        intArrayOf(172, 16, 4, 0),  // 6
        intArrayOf(196, 18, 4, 0),  // 7
        intArrayOf(242, 22, 2, 2),  // 8
        intArrayOf(292, 22, 3, 2),  // 9
        intArrayOf(346, 26, 4, 1),  // 10
    )

    /** 型番ごとの位置合わせパターンの中心座標 (型番 1 は無し)。 */
    private val ALIGNMENT = arrayOf(
        intArrayOf(),                 // 1
        intArrayOf(6, 18),            // 2
        intArrayOf(6, 22),            // 3
        intArrayOf(6, 26),            // 4
        intArrayOf(6, 30),            // 5
        intArrayOf(6, 34),            // 6
        intArrayOf(6, 22, 38),        // 7
        intArrayOf(6, 24, 42),        // 8
        intArrayOf(6, 26, 46),        // 9
        intArrayOf(6, 28, 50),        // 10
    )

    /** 出来上がった QR。[size] × [size] のマス目で、[dark] が true のマスが黒。 */
    class Matrix(val size: Int, private val dark: BooleanArray) {
        fun isDark(x: Int, y: Int): Boolean = dark[y * size + x]
    }

    /**
     * [text] を QR にする。型番 10 (M) に収まらなければ null。
     *
     * 収まらないケースは呼び元で「QR にできない」と出す (ssh の宛先は長くても 60 文字程度なので、
     * 実際にはまず起きない)。
     */
    fun encode(text: String): Matrix? = encode(text, forcedMask = null)

    /**
     * [encode] と同じだが、[forcedMask] を渡すとマスクを固定できる。
     *
     * **既存の QR 実装と 1 マスずつ突き合わせて検証するため**にある (マスクの選び方は実装ごとに
     * 変わり得るので、揃えないと比較できない)。ふだんは null＝ペナルティ最小のものを自動で選ぶ。
     */
    internal fun encode(text: String, forcedMask: Int?): Matrix? {
        val data = text.toByteArray(Charsets.UTF_8)
        val version = pickVersion(data.size) ?: return null
        val spec = M_SPEC[version - 1]
        val totalCw = spec[0]
        val ecPerBlock = spec[1]
        val blocks = spec[2] + spec[3]
        val dataCw = totalCw - ecPerBlock * blocks

        val bits = buildBitStream(data, version, dataCw)
        val interleaved = interleave(bits, spec, dataCw, ecPerBlock)
        return buildMatrix(version, interleaved, totalCw, forcedMask)
    }

    /** [byteCount] バイトが収まる最小の型番 (無ければ null)。 */
    private fun pickVersion(byteCount: Int): Int? {
        for (v in 1..MAX_VERSION) {
            val spec = M_SPEC[v - 1]
            val blocks = spec[2] + spec[3]
            val dataCw = spec[0] - spec[1] * blocks
            // モード指示子 4bit + 文字数指示子 + 本体。文字数指示子は型番 10 以下で 8bit。
            val needBits = 4 + 8 + byteCount * 8
            if (needBits <= dataCw * 8) return v
        }
        return null
    }

    // --- データ側 ---

    /** モード + 文字数 + 本体 + 終端 + パディングを [dataCw] バイトぶん組む。 */
    private fun buildBitStream(data: ByteArray, version: Int, dataCw: Int): IntArray {
        val bits = BitBuffer()
        bits.append(0b0100, 4)                      // 8bit バイトモード
        bits.append(data.size, if (version <= 9) 8 else 16)
        data.forEach { bits.append(it.toInt() and 0xFF, 8) }

        val capacity = dataCw * 8
        // 終端パターンは最大 4bit。残りが足りなければその分だけ。
        bits.append(0, minOf(4, capacity - bits.length))
        // バイト境界まで 0 埋め。
        if (bits.length % 8 != 0) bits.append(0, 8 - bits.length % 8)
        // 余りは 0xEC / 0x11 の繰り返しで埋める (規格が定める埋め草)。
        var pad = 0xEC
        while (bits.length < capacity) {
            bits.append(pad, 8)
            pad = if (pad == 0xEC) 0x11 else 0xEC
        }
        return bits.toBytes()
    }

    /**
     * ブロックに割ってから誤り訂正を付け、規格どおりに交互配置する。
     *
     * グループ 1 と 2 でブロックあたりのデータ長が 1 バイト違う型番があるので、
     * 短いブロックの終端を超えた位置は飛ばしながら読む。
     */
    private fun interleave(dataBytes: IntArray, spec: IntArray, dataCw: Int, ecPerBlock: Int): IntArray {
        val g1 = spec[2]
        val g2 = spec[3]
        val blocks = g1 + g2
        val shortLen = dataCw / blocks
        val dataBlocks = ArrayList<IntArray>(blocks)
        val ecBlocks = ArrayList<IntArray>(blocks)

        var pos = 0
        repeat(blocks) { i ->
            val len = if (i < g1) shortLen else shortLen + 1
            val block = IntArray(len) { dataBytes[pos + it] }
            pos += len
            dataBlocks.add(block)
            ecBlocks.add(ReedSolomon.encode(block, ecPerBlock))
        }

        val out = ArrayList<Int>(dataCw + ecPerBlock * blocks)
        val maxData = dataBlocks.maxOf { it.size }
        for (i in 0 until maxData) {
            dataBlocks.forEach { b -> if (i < b.size) out.add(b[i]) }
        }
        for (i in 0 until ecPerBlock) {
            ecBlocks.forEach { b -> out.add(b[i]) }
        }
        return out.toIntArray()
    }

    // --- 図形side ---

    private fun buildMatrix(version: Int, codewords: IntArray, totalCw: Int, forcedMask: Int?): Matrix {
        val size = version * 4 + 17
        val module = BooleanArray(size * size)
        val reserved = BooleanArray(size * size)

        fun set(x: Int, y: Int, dark: Boolean, reserve: Boolean = true) {
            module[y * size + x] = dark
            if (reserve) reserved[y * size + x] = true
        }

        // 位置検出パターン (3 隅) と分離パターン。
        listOf(0 to 0, size - 7 to 0, 0 to size - 7).forEach { (ox, oy) ->
            for (dy in -1..7) for (dx in -1..7) {
                val x = ox + dx
                val y = oy + dy
                if (x !in 0 until size || y !in 0 until size) continue
                val inner = dx in 0..6 && dy in 0..6
                val dark = inner && (dx == 0 || dx == 6 || dy == 0 || dy == 6 ||
                    (dx in 2..4 && dy in 2..4))
                set(x, y, dark)
            }
        }

        // タイミングパターン。
        for (i in 8 until size - 8) {
            val dark = i % 2 == 0
            if (!reserved[6 * size + i]) set(i, 6, dark)
            if (!reserved[i * size + 6]) set(6, i, dark)
        }

        // 位置合わせパターン。**3 隅 (位置検出パターンと重なる組み合わせ) だけ置かない。**
        // タイミングパターンと交差する位置には置く (規格どおり。ここを「予約済みだから飛ばす」と
        // 書くと型番 7 以上で必要なパターンが消える)。
        val centers = ALIGNMENT[version - 1]
        val n = centers.size
        for (i in 0 until n) for (j in 0 until n) {
            val corner = (i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)
            if (corner) continue
            val cx = centers[i]
            val cy = centers[j]
            for (dy in -2..2) for (dx in -2..2) {
                val dark = dx == -2 || dx == 2 || dy == -2 || dy == 2 || (dx == 0 && dy == 0)
                set(cx + dx, cy + dy, dark)
            }
        }

        // 形式情報の場所と、常に黒の 1 マスを予約しておく (値は [writeFormat] で入れる)。
        // 座標は writeFormat と 1 対 1 に対応させること (ずれるとデータが上書きされる)。
        // i == 6 は形式情報ではなく**タイミングパターン**の位置 ((8,6) と (6,8))。
        // ここを予約に含めると白で塗り潰してしまい、読み取り機がタイミングを追えなくなる。
        for (i in 0..8) {
            if (i == 6) continue
            set(8, i, false)
            set(i, 8, false)
        }
        for (i in 0..7) set(size - 1 - i, 8, false)
        for (i in 0..6) set(8, size - 1 - i, false)
        set(8, size - 8, true)  // 常に黒

        // 型番情報 (型番 7 以上)。
        if (version >= 7) {
            val info = versionInfo(version)
            for (i in 0 until 18) {
                val dark = (info shr i) and 1 == 1
                val a = i / 3
                val b = i % 3
                set(a, size - 11 + b, dark)
                set(size - 11 + b, a, dark)
            }
        }

        // データを右下から 2 列ずつジグザグに置く。
        var bitIndex = 0
        val totalBits = totalCw * 8
        var col = size - 1
        var upward = true
        while (col > 0) {
            if (col == 6) col--  // 縦のタイミングパターン列は飛ばす
            for (i in 0 until size) {
                val y = if (upward) size - 1 - i else i
                for (c in 0..1) {
                    val x = col - c
                    if (reserved[y * size + x]) continue
                    val dark = if (bitIndex < totalBits) {
                        val b = codewords[bitIndex / 8]
                        (b shr (7 - bitIndex % 8)) and 1 == 1
                    } else {
                        false
                    }
                    bitIndex++
                    module[y * size + x] = dark
                }
            }
            upward = !upward
            col -= 2
        }

        // マスクを 8 通り試して、ペナルティが最小のものを採用する (固定指定があればそれだけ)。
        var best = 0
        var bestScore = Int.MAX_VALUE
        var bestModules = module
        for (mask in if (forcedMask != null) forcedMask..forcedMask else 0..7) {
            val candidate = module.copyOf()
            applyMask(candidate, reserved, size, mask)
            writeFormat(candidate, size, mask)
            val score = penalty(candidate, size)
            if (score < bestScore) {
                bestScore = score
                best = mask
                bestModules = candidate
            }
        }
        writeFormat(bestModules, size, best)
        return Matrix(size, bestModules)
    }

    /** データ領域だけにマスクをかける (機能パターンには触れない)。 */
    private fun applyMask(m: BooleanArray, reserved: BooleanArray, size: Int, mask: Int) {
        for (y in 0 until size) for (x in 0 until size) {
            if (reserved[y * size + x]) continue
            val flip = when (mask) {
                0 -> (x + y) % 2 == 0
                1 -> y % 2 == 0
                2 -> x % 3 == 0
                3 -> (x + y) % 3 == 0
                4 -> (y / 2 + x / 3) % 2 == 0
                5 -> (x * y) % 2 + (x * y) % 3 == 0
                6 -> ((x * y) % 2 + (x * y) % 3) % 2 == 0
                else -> ((x + y) % 2 + (x * y) % 3) % 2 == 0
            }
            if (flip) m[y * size + x] = !m[y * size + x]
        }
    }

    /**
     * 形式情報を 2 か所に書く。**座標は (x, y) で、配列添字は `y * size + x`。**
     * ここを転置すると読み取り機がまったく認識しなくなるので、予約側と必ず一致させること。
     */
    private fun writeFormat(m: BooleanArray, size: Int, mask: Int) {
        val bits = formatInfo(mask)
        fun bit(i: Int) = (bits shr i) and 1 == 1
        fun put(x: Int, y: Int, v: Boolean) { m[y * size + x] = v }

        // 1 つめ (左上の位置検出パターンを囲む形)。x=6 / y=6 のタイミング列は飛ばす。
        for (i in 0..5) put(8, i, bit(i))
        put(8, 7, bit(6))
        put(8, 8, bit(7))
        put(7, 8, bit(8))
        for (i in 9..14) put(14 - i, 8, bit(i))

        // 2 つめ (右上の行と左下の列に分けて置く)。
        for (i in 0..7) put(size - 1 - i, 8, bit(i))
        for (i in 8..14) put(8, size - 15 + i, bit(i))
        put(8, size - 8, true)  // 常に黒
    }

    /**
     * 形式情報 15bit = 誤り訂正レベル 2bit + マスク 3bit + BCH(15,5) 10bit、最後に 0x5412 と XOR。
     * 生成多項式は 0x537。
     */
    internal fun formatInfo(mask: Int): Int {
        val data = (ECC_M_BITS shl 3) or mask
        var rem = data
        repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
        return ((data shl 10) or rem) xor 0x5412
    }

    /** 型番情報 18bit = 型番 6bit + BCH(18,6) 12bit (型番 7 以上で使う)。生成多項式は 0x1F25。 */
    internal fun versionInfo(version: Int): Int {
        var rem = version
        repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
        return (version shl 12) or rem
    }

    /** JIS X 0510 のマスク評価 (小さいほど良い)。 */
    private fun penalty(m: BooleanArray, size: Int): Int {
        var score = 0
        fun at(x: Int, y: Int) = m[y * size + x]

        // 規則 1: 同色が 5 個以上並ぶ。
        for (y in 0 until size) {
            var runColor = at(0, y)
            var run = 1
            for (x in 1 until size) {
                if (at(x, y) == runColor) run++ else { if (run >= 5) score += run - 2; runColor = at(x, y); run = 1 }
            }
            if (run >= 5) score += run - 2
        }
        for (x in 0 until size) {
            var runColor = at(x, 0)
            var run = 1
            for (y in 1 until size) {
                if (at(x, y) == runColor) run++ else { if (run >= 5) score += run - 2; runColor = at(x, y); run = 1 }
            }
            if (run >= 5) score += run - 2
        }
        // 規則 2: 2x2 の同色ブロック。
        for (y in 0 until size - 1) for (x in 0 until size - 1) {
            val c = at(x, y)
            if (c == at(x + 1, y) && c == at(x, y + 1) && c == at(x + 1, y + 1)) score += 3
        }
        // 規則 3: 1:1:3:1:1 の並び (位置検出パターンと紛らわしい)。
        val p1 = booleanArrayOf(true, false, true, true, true, false, true, false, false, false, false)
        val p2 = booleanArrayOf(false, false, false, false, true, false, true, true, true, false, true)
        for (y in 0 until size) for (x in 0..size - 11) {
            var m1 = true
            var m2 = true
            for (i in 0 until 11) {
                if (at(x + i, y) != p1[i]) m1 = false
                if (at(x + i, y) != p2[i]) m2 = false
            }
            if (m1 || m2) score += 40
        }
        for (x in 0 until size) for (y in 0..size - 11) {
            var m1 = true
            var m2 = true
            for (i in 0 until 11) {
                if (at(x, y + i) != p1[i]) m1 = false
                if (at(x, y + i) != p2[i]) m2 = false
            }
            if (m1 || m2) score += 40
        }
        // 規則 4: 黒の割合が 50% から離れているほど減点。
        val dark = m.count { it }
        val percent = dark * 100 / (size * size)
        val k = (kotlin.math.abs(percent - 50) + 4) / 5
        score += k * 10
        return score
    }

    /** ビットを並べる小さなバッファ。 */
    private class BitBuffer {
        private val bytes = ArrayList<Int>()
        var length = 0
            private set

        fun append(value: Int, bitCount: Int) {
            for (i in bitCount - 1 downTo 0) {
                if (length % 8 == 0) bytes.add(0)
                if ((value shr i) and 1 == 1) {
                    val idx = length / 8
                    bytes[idx] = bytes[idx] or (1 shl (7 - length % 8))
                }
                length++
            }
        }

        fun toBytes(): IntArray = bytes.toIntArray()
    }
}
