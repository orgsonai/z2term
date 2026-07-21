package com.zerotoship.z2term.settings

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.core.content.edit
import java.util.Locale

/**
 * アプリ内言語スイッチ。OS の言語設定ではなく**アプリ独自**に「日本語/English」を切替えるため、
 * `attachBaseContext` で `Configuration.setLocale` を差し替えてリソース解決を制御する。
 *
 * 永続化は専用 SharedPreferences (`z2term_locale`) を使う。`DataStore` を使わないのは
 * Activity の `attachBaseContext` が **同期** で動作する必要があり、`DataStore` の
 * suspend 取得とライフサイクルが噛み合わないため (Activity 生成のたびに `runBlocking` を
 * 走らせるのは避けたい)。`AppSettings` 経由でも参照したいときは [language] を読めばよい。
 *
 * 設定変更時は呼び出し側で `Activity.recreate()` し、新ロケールでの再構築を起こす。
 */
object LocaleHelper {
    /** SharedPreferences のファイル名 (DataStore のものと混ぜず分離)。 */
    private const val PREFS = "z2term_locale"
    private const val KEY_LANG = "lang"

    /** サポートする言語コード。`null`/未保存は `ja` (既定: 日本語)。 */
    const val LANG_JA = "ja"
    const val LANG_EN = "en"
    const val DEFAULT_LANG = LANG_JA

    /** 現在の言語コード。未保存なら [DEFAULT_LANG]。 */
    fun language(context: Context): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return p.getString(KEY_LANG, DEFAULT_LANG) ?: DEFAULT_LANG
    }

    /** 言語を保存する。反映には Activity の `recreate()` を呼ぶこと。 */
    fun setLanguage(context: Context, lang: String) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit { putString(KEY_LANG, lang) }
    }

    /**
     * 与えられた `Context` を、保存済み言語の Locale で **wrap した Context** に差し替える。
     * Activity / Application の `attachBaseContext` から呼ぶ。
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
