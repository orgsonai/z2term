package com.zerotoship.z2term.proot

import com.zerotoship.z2term.proot.lang.CliDe
import com.zerotoship.z2term.proot.lang.CliEs
import com.zerotoship.z2term.proot.lang.CliFr
import com.zerotoship.z2term.proot.lang.CliKo
import com.zerotoship.z2term.proot.lang.CliPtBr
import com.zerotoship.z2term.proot.lang.CliZhCn
import com.zerotoship.z2term.proot.lang.CliZhTw

/**
 * 英語・日本語**以外**の 1 言語ぶんの、**端末に出る文言**の訳。
 *
 * ⭐ **1 言語 = 1 ファイル** (`proot/lang/Cli<言語>.kt`)。英語と日本語は文言を使う場所に
 * 書いてあり ([CliText] の `t("名前", en = …, ja = …)` / [Z2ApiMsg] / [GuiScriptStrings] の `en()` `ja()`)、
 * ここには持たない。⚠ 1.0.83 までは 6 言語の訳を使う場所に並べていて、言語を足すほど
 * 処理が訳に埋もれていた。
 *
 * ## 訳の置き場は 4 つ
 *
 * | 置き場 | 中身 | 使う側 |
 * |---|---|---|
 * | [texts] | スクリプトの中の文言。名前 → 訳 | `t("doctor.head", en = …, ja = …)` |
 * | [lineSets] | 行の並びで持つ文言 | `t.lines("macro.watchBasic.1", …)` |
 * | [values] | 文言ではない言語ごとの値 (列の幅など) | `t.of("macro.stCols", …)` |
 * | [apiMsg] / [gui] / [sshd] | `z2-*` のメッセージ集と GUI / sshd の組 | [Z2ApiMsg] を上書きしたもの など |
 * | [voiceWords] | 声で操作 (`voice.sh`) の言葉の表 | `Z2VoiceMacro.kt` |
 *
 * ⚠ **無い訳は英語で出る**（壊れはしない）。ただし名簿 ([com.zerotoship.z2term.settings.AppLanguages])
 * で `cliComplete` を付けた言語は `scripts/i18n-status.sh --check` が 100% を要求し、
 * 欠けていればテスト ([CliTranslationCheckTest]) が落ちる。
 *
 * ## 言語を 1 つ足す
 *
 * 1. 既存の言語ファイル (例: `proot/lang/CliEs.kt`) を写して `Cli<言語>.kt` を作り、
 *    [code] と訳を書き換える。⚠ 写したまま訳し忘れると**元の言語のまま出る**ので、
 *    写したら訳を一度全部消して、`bash scripts/i18n-status.sh --missing <言語>` で
 *    英語の原文を見ながら埋める方が安全。
 * 2. 下の [CliLanguages] の一覧に 1 行足す。
 *
 * ⚠ **シェルの `$` は `$d`** と書く (各言語ファイルの `d`)。Kotlin のテンプレートと衝突するため。
 */
internal interface CliLanguage {

    /** 言語コード ([com.zerotoship.z2term.settings.AppLanguages] の名簿と同じ書き方: `zh-CN` など)。 */
    val code: String

    /** スクリプトの中の文言 ([CliText.invoke])。名前 → 訳。 */
    val texts: Map<String, String> get() = emptyMap()

    /** 行の並びで持つ文言 ([CliText.lines])。 */
    val lineSets: Map<String, List<String>> get() = emptyMap()

    /** 文言ではない言語ごとの値 ([CliText.of])。 */
    val values: Map<String, Any> get() = emptyMap()

    /** `z2-*` のメッセージ集。null なら英語。 */
    fun apiMsg(d: String): Z2ApiMsg? = null

    /** z2gui が echo する文言。null なら英語。 */
    fun gui(): GuiScriptStrings? = null

    /** sshd の wrapper が echo する文言。null なら英語。 */
    fun sshd(): SshdScriptStrings? = null

    /**
     * 声で操作 (`voice.sh`) の言葉の表。null なら英語の表で動く。
     * ⚠ これは**言い方の表**で、ただの訳ではない (その言語で自然に言う言い方を並べる)。
     * 書き方は `Z2VoiceMacro.kt` の英語の表 (`VOICE_WORDS_EN`) の先頭の説明を参照。
     */
    val voiceWords: String? get() = null
}

/** 言語ファイルの一覧。⭐ **言語を足したらここに 1 行**。 */
internal object CliLanguages {

    /** すべての言語ファイル。並び順は `voice.sh` の言葉の表の並び (英語の前に置かれる)。 */
    val all: List<CliLanguage> by lazy {
        listOf(
            CliZhCn,
            CliZhTw,
            CliEs,
            CliKo,
            CliPtBr,
            CliDe,
            CliFr,
        )
    }

    private val byCode: Map<String, CliLanguage> by lazy { all.associateBy { it.code } }

    /** その言語の訳。英語・日本語と、訳を持たない言語では null。 */
    fun of(lang: String): CliLanguage? = byCode[lang]
}
