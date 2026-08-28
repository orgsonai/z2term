package com.zerotoship.z2term.settings

/**
 * z2term が**文言を用意している言語の名簿**。ここが唯一の正本。
 *
 * ⭐ **言語を 1 つ増やすときに触る場所を 1 か所に集めるためにある。**
 * 0.8.421 までは言語の判定が `en: Boolean` / `lang != "en"` の 2 値で書かれていて、
 * 3 言語目を置く場所がコード上に存在しなかった。ここに 1 行足すと、
 * 設定画面の選択肢・端末に出る文言の選択・`Locale` の適用が**まとめて**その言語を知る。
 *
 * ## 言語を 1 つ増やす手順
 *
 * 1. [ALL] に `Entry("<コード>", "<その言語での言語名>")` を足す（並び順が設定画面の並び順）。
 * 2. `app/src/main/res/values-<コード>/strings.xml` を作る（アプリ画面の文言）。
 *    ⚠ 中国語のように地域を伴うものは Android の作法に従って `values-zh-rCN` のように書く。
 *    このコードは **`Locale.forLanguageTag` が解釈できる形** (`zh-CN` 等) にしておく。
 * 3. 端末に出る文言（`z2-*` CLI・同梱スクリプト・サンプルマクロ）に、その言語の
 *    変わり値を足す。呼び出し側は [com.zerotoship.z2term.proot.CliText] の
 *    `t(en = …, ja = …)` なので、`"<コード>" to "…"` を足すだけでよい。
 *    ⚠ **足さなくても壊れない** — 変わり値の無い文言は [FALLBACK]（英語）で出る。
 * 4. `bash scripts/i18n-status.sh` で埋まり具合を見る。
 *    未訳の一覧は `bash scripts/i18n-status.sh --missing <コード>`。
 * 5. 端末に出る文言が 100% になったら [Entry.cliComplete] に `true` を付ける。
 *    ⭐ **ここまでやって初めて「その言語は腐らない」状態になる**（下の注記）。
 *
 * ## ⚠ 端末に出る文言は、印を付けないと静かに腐る
 *
 * lint の `MissingTranslation` が守るのは **res だけ**。端末に出る文言は未訳でも英語が出て
 * **CI は緑のまま通る**ので、新しい機能を足すたび、訳した言語の `z2-*` の表示だけが
 * 少しずつ英語へ戻っていく（画面は中国語なのに `z2-notify --help` は英語、という形）。
 *
 * これを止めるのが [Entry.cliComplete]。印の付いた言語は
 * `bash scripts/i18n-status.sh --check` が 100% を要求し、**欠けていればテストが落ちる**
 * ([com.zerotoship.z2term.proot.CliTranslationCheckTest])。
 *
 * ⛔ **res だけは全部埋めきってから足すこと。** `values-<コード>/` を作った時点で lint の
 * `MissingTranslation` が全ての未訳を数え上げ、**CI が赤になる**（`app/build.gradle.kts` の
 * lint 設定でわざと error にしている — 訳し忘れを黙って出さないため）。
 * 端末に出る文言の方は途中でも通る（英語で出る）ので、res → CLI の順に進めるのが楽。
 *
 * ⚠ **[FALLBACK] は英語で固定する。** 「英語ではない = 日本語」という書き方をすると、
 * 3 言語目を選んだ利用者に**日本語**が出る（0.8.421 まで `z2-macro` が実際そうだった）。
 * 分からない言語は必ず英語へ倒すこと。
 */
object AppLanguages {

    /** 名簿に無い言語を選ばれたときに出す言語。⚠ 日本語にしないこと（上の注記）。 */
    const val FALLBACK = "en"

    /**
     * 1 つの言語。
     *
     * @param code `Locale.forLanguageTag` が解釈できる言語タグ (`ja` / `en` / `zh-CN` …)。
     * @param nativeName 設定画面に出す名前。⚠ **その言語で書く**（英語話者向けに "Japanese" と
     *   書くと、日本語しか読めない利用者が自分の言語を見つけられない）。翻訳対象ではないので
     *   `strings.xml` には置かない。
     * @param cliComplete **端末に出る文言（`z2-*` CLI）を訳しきった**という印。
     *   ⭐ 付けると `scripts/i18n-status.sh --check` がその言語に 100% を要求するようになり、
     *   訳を足さずに新しい文言を書いた時点でテストが落ちる。⚠ **付け忘れると腐る**
     *   （クラス説明の「静かに腐る」を参照）。res は lint が別途守るのでここには含めない。
     *   ⛔ **訳しきる前に付けないこと** — 付けた瞬間に落ちる。訳の途中は `false` のままでよい
     *   （未訳の文言は英語で出るので、アプリは壊れない）。
     */
    data class Entry(
        val code: String,
        val nativeName: String,
        val cliComplete: Boolean = false,
    )

    /** 対応言語（設定画面の並び順）。⛔ 増やすときはクラス説明の手順を最後まで行うこと。 */
    val ALL: List<Entry> = listOf(
        // en/ja は `t(en = …, ja = …)` の名前つき引数なので、構造上つねに 100%。
        Entry("en", "English", cliComplete = true),
        Entry("ja", "日本語", cliComplete = true),
        // ⚠ 素の `zh` はここに先に置いた方 (簡体字) へ行く。繁体字を足すときは後ろへ。
        Entry("zh-CN", "简体中文", cliComplete = true),
    )

    /** 対応言語のコードだけ。 */
    val CODES: List<String> = ALL.map { it.code }

    /** 名簿にある言語ならそのまま、無ければ [FALLBACK]。 */
    fun resolve(lang: String): String = if (lang in CODES) lang else FALLBACK

    /**
     * 中国語の**書き方**（script サブタグ）から、名簿で使う代表のコードへの対応表。
     *
     * ⚠ **簡体字と繁体字は別の言語として扱う。** 中身が違うので、片方しか無いときに
     * もう片方へ流すと読めない文字が並ぶ。Android は端末の設定によって
     * `zh-CN` とだけ言うことも `zh-Hans-CN` と script つきで言うこともあるので、
     * どちらの言い方でも同じ答えに行き着くようにする。
     *
     * ⛔ ここに国コードを増やさないこと（`zh-HK` 等）。**書き方**だけを見るのが要点で、
     * 香港・マカオは繁体字なので `Hant` として来る。
     */
    private val SCRIPT_ALIASES = mapOf(
        "hans" to "zh-CN",
        "hant" to "zh-TW",
    )

    /**
     * 端末のロケール（`ja-JP` / `zh-Hans-CN` のような地域・書き方つき）から、
     * 名簿の中で一番近いものを選ぶ。
     *
     * 判定は 4 段:
     *  1. 言語タグがそのまま一致（`zh-CN` = `zh-CN`）
     *  2. **書き方**で一致（`zh-Hans-CN` → `zh-CN` / `zh-Hant-TW` → `zh-TW`。[SCRIPT_ALIASES]）
     *  3. `言語-地域` で一致（`zh-Hans-CN` の `zh-CN` の部分）
     *  4. 言語部分だけ一致（`ja-JP` → `ja` / `es-MX` → `es`）
     *
     * どれも当たらなければ [FALLBACK]。
     *
     * ⚠ **4 は名簿の並び順で決まる。** 同じ言語で複数の変種を載せるとき
     * （`zh-CN` と `zh-TW`）は、**地域の指定が無いときに出したい方を先に置く**こと。
     */
    fun matchDeviceLocale(languageTag: String): String = matchIn(languageTag, CODES)

    /**
     * [matchDeviceLocale] の中身。**名簿を引数で受ける**のは、まだ載せていない言語の
     * 拾い方を先にテストで縛れるようにするため（簡体字と繁体字の取り違えは、載せてから
     * 実機で気付くのでは遅い）。
     */
    internal fun matchIn(languageTag: String, codes: List<String>): String {
        val parts = languageTag.replace('_', '-').split('-').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return FALLBACK

        fun pick(candidate: String?): String? =
            candidate?.let { c -> codes.firstOrNull { it.equals(c, ignoreCase = true) } }

        pick(parts.joinToString("-"))?.let { return it }
        // 書き方 (Hans/Hant) は 2 番目の要素に来る
        parts.getOrNull(1)?.lowercase()?.let { SCRIPT_ALIASES[it] }?.let { alias ->
            pick(alias)?.let { return it }
        }
        // 書き方を落とした `言語-地域`
        if (parts.size >= 3) pick("${parts[0]}-${parts[2]}")?.let { return it }
        // 言語だけ
        val base = parts[0]
        codes.firstOrNull { it.substringBefore('-').equals(base, ignoreCase = true) }
            ?.let { return it }
        return FALLBACK
    }
}
