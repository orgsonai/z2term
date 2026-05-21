package com.zerotoship.z2term.ui.theme

import androidx.compose.ui.graphics.Color

// === アプリ UI 配色 ===
// これらは選択中テーマから導出される動的パレット ([AppColors]) を読む getter。
// 値そのものを定数で持たないことで、テーマ変更時にアプリ全体の配色が追従する
// (Composable から読めば AppColors の snapshot state を購読し再コンポーズされる)。

// === ZTS グリーン（アクセント） ===
val ZtsGreen: Color get() = AppColors.accent
val ZtsGreenBright: Color get() = AppColors.accentBright
val ZtsGreenDim: Color get() = AppColors.accentDim

// === 背景 ===
val ZtsBgPrimary: Color get() = AppColors.bgPrimary
val ZtsBgSecondary: Color get() = AppColors.bgSecondary
val ZtsBgCard: Color get() = AppColors.bgCard
val ZtsBorder: Color get() = AppColors.border

// === テキスト ===
val ZtsTextPrimary: Color get() = AppColors.textPrimary
val ZtsTextSecondary: Color get() = AppColors.textSecondary
val ZtsTextTertiary: Color get() = AppColors.textTertiary

// === ステータス ===
val ZtsError: Color get() = AppColors.error
val ZtsWarning: Color get() = AppColors.warning
val ZtsInfo: Color get() = AppColors.info

// === ANSI 16 (ターミナル配色) ===
val AnsiBlack = Color(0xFF1F1F1F)
val AnsiRed = Color(0xFFEF4444)
val AnsiGreen = Color(0xFF22C55E)
val AnsiYellow = Color(0xFFF59E0B)
val AnsiBlue = Color(0xFF3B82F6)
val AnsiMagenta = Color(0xFFA855F7)
val AnsiCyan = Color(0xFF06B6D4)
val AnsiWhite = Color(0xFFFAFAFA)
val AnsiBrightBlack = Color(0xFF525252)
val AnsiBrightRed = Color(0xFFF87171)
val AnsiBrightGreen = Color(0xFF4ADE80)
val AnsiBrightYellow = Color(0xFFFBBF24)
val AnsiBrightBlue = Color(0xFF60A5FA)
val AnsiBrightMagenta = Color(0xFFC084FC)
val AnsiBrightCyan = Color(0xFF22D3EE)
val AnsiBrightWhite = Color(0xFFFFFFFF)
