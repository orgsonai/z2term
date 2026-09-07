package com.zerotoship.z2term.ime

/**
 * ローマ字 → かな。**物理キーボードから日本語を打つためだけ**に使う。
 *
 * ⚠ 内蔵キーボードはフリック入力なので、ここまで「かなは直接入る」ものだった。外付け
 * キーボードを繋ぐと打鍵はアルファベットで届くので、その間だけこの変換が要る。
 *
 * ⭐ **確定したかなを返すだけ**で、変換 (かな漢字) や候補は
 * [com.zerotoship.z2term.ui.terminal.keyboard.ComposingState] が今までどおり受け持つ。
 * ここは「打鍵の途中」を持つ小さな状態機械に徹する。
 */
object RomajiKana {

    /**
     * 1 打鍵ぶん進める。
     *
     * @param pending まだかなにならずに溜まっているローマ字 (例: "k", "ky")
     * @param typed 打たれた 1 文字 (英小文字・`-` などの記号も来る)
     * @return [Result]。確定したかな (無ければ空) と、次に持ち越すローマ字。
     */
    fun feed(pending: String, typed: Char): Result {
        val ch = typed.lowercaseChar()
        val next = pending + ch

        // 1. そのまま 1 つのかなになる (「ん」を除く。下の 4 を見よ)
        TABLE[next]?.let { kana ->
            // ⚠ "n" は "na" にも育つので、ここでは確定させない (4 で扱う)。
            if (next != "n") return Result(kana, "")
        }

        // 2. まだ育つ途中 ("k" → "ka" / "ky" → "kya")
        if (PREFIXES.contains(next)) return Result("", next)

        // 3. 促音。同じ子音が 2 つ続いたら「っ」を出して、2 つ目から打ち直す
        //    (kka → っ + ka)。⚠ "nn" は「ん」なので 4 で先に捕まえる。
        if (pending.length == 1 && pending[0] == ch && ch !in "aiueon" && ch.isLetter()) {
            return Result("っ", ch.toString())
        }

        // 4. 「ん」。"n" のあとに母音でも y でも来なければ「ん」で確定し、その打鍵から再開する。
        //    "nn" と "n'" は「ん」を出してローマ字を空にする (次の打鍵は素で始まる)。
        if (pending == "n") {
            if (ch == 'n' || ch == '\'') return Result("ん", "")
            val restart = feed("", ch)
            return Result("ん" + restart.kana, restart.pending)
        }

        // 5. どれでもない打鍵。溜まっていたローマ字は**そのまま文字として出す** — 黙って
        //    捨てると、打ったはずの字が消えて「打鍵が飛んだ」ようにしか見えない。
        if (pending.isNotEmpty()) {
            val restart = feed("", ch)
            return Result(pending + restart.kana, restart.pending)
        }

        // 6. 単独で意味のある記号・数字はそのまま。育つ見込みのない英字もそのまま出す。
        SYMBOLS[ch]?.let { return Result(it, "") }
        return Result(ch.toString(), "")
    }

    /** [feed] の結果。 */
    data class Result(
        /** 確定して composing へ積むぶん (空のこともある)。 */
        val kana: String,
        /** 次の打鍵へ持ち越すローマ字。 */
        val pending: String,
    )

    /**
     * 打ち終わりで溜まったままのローマ字を、かなに落とせるだけ落とす。
     * ⚠ 変換キーや確定キーの直前に呼ぶ ("kyo" まで打って変換したいのに "ky" が宙に浮く、を防ぐ)。
     */
    fun flush(pending: String): String = when {
        pending.isEmpty() -> ""
        pending == "n" -> "ん"
        else -> TABLE[pending] ?: pending
    }

    private val SYMBOLS: Map<Char, String> = mapOf(
        '-' to "ー", ',' to "、", '.' to "。", '/' to "・", '[' to "「", ']' to "」",
    )

    /**
     * ローマ字 → かなの表。⚠ **ヘボン式と訓令式を両方受ける** (si/shi, tu/tsu, hu/fu, zi/ji …)。
     * どちらで打つかは人によるので、片方しか受けないと「打てない」と受け取られる。
     */
    private val TABLE: Map<String, String> = buildMap {
        // 母音
        putAll(mapOf("a" to "あ", "i" to "い", "u" to "う", "e" to "え", "o" to "お"))
        // 清音・濁音・半濁音 (子音 + 母音)
        val rows = listOf(
            "k" to "かきくけこ", "g" to "がぎぐげご",
            "s" to "さしすせそ", "z" to "ざじずぜぞ",
            "t" to "たちつてと", "d" to "だぢづでど",
            "n" to "なにぬねの",
            "h" to "はひふへほ", "b" to "ばびぶべぼ", "p" to "ぱぴぷぺぽ",
            "m" to "まみむめも",
            "y" to "や_ゆ_よ",
            "r" to "らりるれろ",
            "w" to "わ___を",
            "v" to "ゔゔゔゔゔ",
        )
        val vowels = "aiueo"
        rows.forEach { (consonant, kana) ->
            kana.forEachIndexed { index, k ->
                if (k != '_') put("$consonant${vowels[index]}", k.toString())
            }
        }
        // 訓令式・ヘボン式の揺れ
        putAll(mapOf(
            "shi" to "し", "chi" to "ち", "tsu" to "つ", "fu" to "ふ", "ji" to "じ",
            "sya" to "しゃ", "syu" to "しゅ", "syo" to "しょ",
            "tya" to "ちゃ", "tyu" to "ちゅ", "tyo" to "ちょ",
            "jya" to "じゃ", "jyu" to "じゅ", "jyo" to "じょ",
            "dya" to "ぢゃ", "dyu" to "ぢゅ", "dyo" to "ぢょ",
            "wo" to "を", "wi" to "うぃ", "we" to "うぇ",
            "fa" to "ふぁ", "fi" to "ふぃ", "fe" to "ふぇ", "fo" to "ふぉ",
            "she" to "しぇ", "che" to "ちぇ", "je" to "じぇ",
            "tsa" to "つぁ", "tsi" to "つぃ", "tse" to "つぇ", "tso" to "つぉ",
            "di" to "ぢ", "du" to "づ",
        ))
        // 拗音 (子音 + y + 母音)
        val palatal = mapOf(
            "k" to "き", "g" to "ぎ", "s" to "し", "z" to "じ", "t" to "ち", "d" to "ぢ",
            "n" to "に", "h" to "ひ", "b" to "び", "p" to "ぴ", "m" to "み", "r" to "り",
        )
        val small = mapOf('a' to "ゃ", 'u' to "ゅ", 'o' to "ょ", 'i' to "ぃ", 'e' to "ぇ")
        palatal.forEach { (consonant, base) ->
            small.forEach { (vowel, tail) -> put("${consonant}y$vowel", base + tail) }
        }
        // ヘボン式の拗音 (sha/shu/sho, cha/chu/cho, ja/ju/jo)
        putAll(mapOf(
            "sha" to "しゃ", "shu" to "しゅ", "sho" to "しょ",
            "cha" to "ちゃ", "chu" to "ちゅ", "cho" to "ちょ",
            "ja" to "じゃ", "ju" to "じゅ", "jo" to "じょ",
        ))
        // 小書き (x / l で始める)
        listOf("x", "l").forEach { prefix ->
            putAll(mapOf(
                "${prefix}a" to "ぁ", "${prefix}i" to "ぃ", "${prefix}u" to "ぅ",
                "${prefix}e" to "ぇ", "${prefix}o" to "ぉ",
                "${prefix}ya" to "ゃ", "${prefix}yu" to "ゅ", "${prefix}yo" to "ょ",
                "${prefix}tu" to "っ", "${prefix}tsu" to "っ", "${prefix}wa" to "ゎ",
            ))
        }
        put("nn", "ん")
    }

    /** 途中まで打った状態が「まだ育つ」かの判定用。表のすべての接頭辞。 */
    private val PREFIXES: Set<String> = buildSet {
        TABLE.keys.forEach { key ->
            for (length in 1 until key.length) add(key.substring(0, length))
        }
    }
}
