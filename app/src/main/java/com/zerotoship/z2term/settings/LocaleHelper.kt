package com.zerotoship.z2term.settings

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import androidx.core.content.edit
import java.util.Locale

/**
 * アプリ内言語スイッチ。OS の言語設定ではなく**アプリ独自**に「端末に合わせる/日本語/English」を
 * 切替えるため、`attachBaseContext` で `Configuration.setLocale` を差し替えてリソース解決を制御する。
 *
 * 永続化は専用 SharedPreferences (`z2term_locale`) を使う。`DataStore` を使わないのは
 * Activity の `attachBaseContext` が **同期** で動作する必要があり、`DataStore` の
 * suspend 取得とライフサイクルが噛み合わないため (Activity 生成のたびに `runBlocking` を
 * 走らせるのは避けたい)。`AppSettings` 経由でも参照したいときは [language] を読めばよい。
 *
 * ⚠ **保存値 ([languageSetting]) と実効言語 ([language]) を分ける** (0.8.363)。
 * [language] は必ず [AppLanguages.CODES] のどれかを返す — 呼び出し側は 20 か所以上あり、
 * `system` をそのまま渡すと**全ての判定が的外れ**になる。解決はこの中で終わらせ、
 * 外へは実在する言語コードしか出さない。
 *
 * ⚠ **対応言語の名簿は [AppLanguages] が持つ** (0.8.422)。ここに一覧を書かない —
 * 2 か所に持つと言語を増やしたとき片方だけ古くなる。
 *
 * ⚠ **`== LANG_JA` で日本語かを見ている箇所は、日本語固有の機能の話** (かな面の有無・
 * IME のかな入力判定)。⛔ **文言の出し分けにこれを使わないこと** — 「日本語でなければ英語」
 * と書くと 3 言語目が英語に化ける。端末に出る文言は
 * [com.zerotoship.z2term.proot.CliText]、画面の文言は `res/values-<言語>/` が受け持つ。
 *
 * 設定変更時は呼び出し側で `Activity.recreate()` し、新ロケールでの再構築を起こす。
 */
object LocaleHelper {
    /** SharedPreferences のファイル名 (DataStore のものと混ぜず分離)。 */
    private const val PREFS = "z2term_locale"
    private const val KEY_LANG = "lang"

    /** 端末の言語に従う (既定)。[language] は解決結果として実在する言語コードを返す。 */
    const val LANG_SYSTEM = "system"
    const val LANG_JA = "ja"
    const val LANG_EN = "en"

    /** 設定画面に並べる選択肢 (`system` + 対応言語)。⭐ 言語を増やすのは [AppLanguages]。 */
    val SETTING_OPTIONS: List<String> = listOf(LANG_SYSTEM) + AppLanguages.CODES

    /**
     * 未保存のときの既定。
     *
     * ⚠ 0.8.362 までは `ja` 固定だった。日本語以外の端末に入れても**日本語で立ち上がる**ので、
     * 設定を開いて言語を切り替えるまで読めない画面が続く。既定を端末の言語に合わせる (0.8.363)。
     */
    const val DEFAULT_LANG = LANG_SYSTEM

    /** 保存されている設定値そのもの (`system`/`ja`/`en`)。設定画面の選択状態に使う。 */
    fun languageSetting(context: Context): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getString(KEY_LANG, DEFAULT_LANG) ?: DEFAULT_LANG
    }

    /**
     * 実効言語。**必ず [AppLanguages.CODES] のどれか**を返す。
     * 設定が [LANG_SYSTEM] のときや名簿に無い言語のときは端末の言語から解決する。
     */
    fun language(context: Context): String {
        val saved = languageSetting(context)
        return if (saved in AppLanguages.CODES) saved else systemLanguage()
    }

    /**
     * 端末そのものの言語。
     *
     * ⚠ **`Locale.getDefault()` は使えない** — [wrap] が `Locale.setDefault` を呼ぶので、
     * 一度でもアプリ側の言語を適用するとプロセス内の既定が上書きされ、「端末に合わせる」が
     * **直前に選んでいた言語に張り付く**。`Resources.getSystem()` はアプリの Configuration を
     * 通さない OS 側の設定を返すので、ここだけは汚れない。
     */
    private fun systemLanguage(): String =
        AppLanguages.matchDeviceLocale(Resources.getSystem().configuration.locales[0].toLanguageTag())

    /** 言語を保存する (`system`/`ja`/`en`)。反映には Activity の `recreate()` を呼ぶこと。 */
    fun setLanguage(context: Context, lang: String) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit { putString(KEY_LANG, lang) }
    }

    /**
     * 与えられた `Context` を、実効言語の Locale で **wrap した Context** に差し替える。
     * Activity / Application の `attachBaseContext` から呼ぶ。
     *
     * ⚠ 「端末に合わせる」のときも**解決後の `ja`/`en` で明示的に wrap する** — base をそのまま
     * 返すと、直前まで適用していた `Locale.setDefault` がプロセスに残ったままになる。
     */
    fun applyLocale(base: Context): Context = wrap(base, language(base))

    private fun wrap(base: Context, lang: String): Context {
        val locale = Locale.forLanguageTag(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        // setLocales でリスト先頭にしておく (リソース解決が二段で見るため)。
        config.setLocales(android.os.LocaleList(locale))
        return base.createConfigurationContext(config)
    }
}
