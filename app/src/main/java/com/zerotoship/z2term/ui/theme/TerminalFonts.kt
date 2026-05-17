package com.zerotoship.z2term.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

/**
 * ターミナル用フォントの選択肢。
 *
 * - monospace : Android 標準 FontFamily.Monospace
 * - assets/fonts/ 配下に TTF を置くと追加候補として認識される
 *
 * カスタムフォントは TerminalFontOption.assetFile が assets 内に存在するかを
 * チェックし、ある場合のみ実体を返す。なければ Monospace にフォールバック。
 */
data class TerminalFontOption(
    val id: String,
    val displayName: String,
    val assetFile: String? = null
)

object TerminalFontOptions {
    val MONOSPACE = TerminalFontOption("monospace", "System Monospace", null)
    val IBM_PLEX_MONO = TerminalFontOption("ibm-plex-mono", "IBM Plex Mono", "IBMPlexMono-Regular.ttf")
    val JETBRAINS_MONO = TerminalFontOption("jetbrains-mono", "JetBrains Mono", "JetBrainsMono-Regular.ttf")
    val FIRA_CODE = TerminalFontOption("fira-code", "Fira Code", "FiraCode-Regular.ttf")

    val ALL = listOf(MONOSPACE, IBM_PLEX_MONO, JETBRAINS_MONO, FIRA_CODE)

    fun byId(id: String): TerminalFontOption = ALL.firstOrNull { it.id == id } ?: MONOSPACE

    /** assets に実体ファイルがあるか確認 */
    fun isAvailable(context: Context, option: TerminalFontOption): Boolean {
        val file = option.assetFile ?: return true
        return try {
            context.assets.list("fonts")?.contains(file) == true
        } catch (e: Exception) { false }
    }
}

/** 指定オプションに対応する Compose FontFamily を返す。不在なら Monospace */
@Composable
fun rememberTerminalFontFamily(option: TerminalFontOption): FontFamily {
    val context = LocalContext.current
    return remember(option.id) {
        val file = option.assetFile ?: return@remember FontFamily.Monospace
        try {
            val tf: Typeface = Typeface.createFromAsset(context.assets, "fonts/$file")
            FontFamily(tf)
        } catch (e: Exception) {
            FontFamily.Monospace
        }
    }
}
