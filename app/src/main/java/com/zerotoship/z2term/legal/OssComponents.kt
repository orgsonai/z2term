package com.zerotoship.z2term.legal

import androidx.annotation.StringRes
import com.zerotoship.z2term.R

/**
 * 同梱 OSS の権利表示・ライセンス・対応ソース URL のメタデータ。
 *
 * GPL/LGPL の頒布要件（GPL v2 §3 / GPL v3 §6 / LGPL v3 §4）に応えるため、
 * バイナリ同梱物については **対応ソースの取得手段** を必ず併記する。
 * OSS ライセンス画面 ([LicensesScreen]) でこのリストを一覧表示する。
 *
 * - 追加方針: APK の **実体に含まれる** ものだけ列挙する。設定で OFF にできる依存も実体は同梱しているため列挙対象。
 *   実行エンジン z2root は本体ソースからビルドされるため本体 GPL-3.0 に含まれる。
 *
 * `purposeRes` は `R.string.oss_purpose_*` で言語スイッチに追従する。
 */
data class OssComponent(
    /** ユーザーに見せる名称 (例 "PRoot") */
    val name: String,
    /** SPDX ID。assets/licenses/<id>.txt が存在すれば全文を表示する。 */
    val licenseId: String,
    /** 著作権表記 (Copyright (c) YEAR HOLDER の本文)。SPDX 単独では識別子だけなので別途必要。 */
    val copyright: String,
    /** 対応ソースの取得 URL。GPL/LGPL は必須。それ以外も上流リポジトリを書く。 */
    val sourceUrl: String,
    /** 一言説明 (用途)。LocaleHelper により ja/en で切替わる。 */
    @StringRes val purposeRes: Int,
)

object OssComponents {

    /**
     * 一覧。読みやすさのため、(a) ネイティブ実行物 (b) rootfs 同梱パッケージ (c) Android/Java 依存
     * (d) フォント、の順で並べる。
     */
    private val all: List<OssComponent> = listOf(
        // ===== 本アプリ本体 (先頭固定) =====
        OssComponent(
            name = "z2term（本アプリ本体）",
            licenseId = "GPL-3.0",
            copyright = "Copyright (c) 2026 Zero to Ship",
            sourceUrl = "https://github.com/orgsonai/z2term",
            purposeRes = R.string.oss_purpose_z2term,
        ),

        // ===== Android / Java 依存 (gradle) =====
        OssComponent(
            name = "AndroidX / Jetpack Compose",
            licenseId = "Apache-2.0",
            copyright = "Copyright (c) The Android Open Source Project",
            sourceUrl = "https://android.googlesource.com/",
            purposeRes = R.string.oss_purpose_androidx_compose,
        ),
        OssComponent(
            name = "Kotlin Standard Library",
            licenseId = "Apache-2.0",
            copyright = "Copyright (c) JetBrains s.r.o. and contributors",
            sourceUrl = "https://github.com/JetBrains/kotlin",
            purposeRes = R.string.oss_purpose_kotlin_stdlib,
        ),
        OssComponent(
            name = "JSch",
            licenseId = "BSD-3-Clause",
            copyright = "Copyright (c) ymnk, JCraft, Inc.",
            sourceUrl = "https://github.com/mwiede/jsch",
            purposeRes = R.string.oss_purpose_jsch,
        ),
        OssComponent(
            name = "OkHttp 5.3.0",
            licenseId = "Apache-2.0",
            copyright = "Copyright (c) Square, Inc. and contributors",
            sourceUrl = "https://github.com/square/okhttp",
            purposeRes = R.string.oss_purpose_okhttp,
        ),
        OssComponent(
            name = "SMBJ 0.15.0",
            licenseId = "Apache-2.0",
            copyright = "Copyright (c) SMBJ contributors",
            sourceUrl = "https://github.com/hierynomus/smbj/tree/v0.15.0",
            purposeRes = R.string.oss_purpose_smbj,
        ),
        OssComponent(
            // BouncyCastle License は MIT X11 ライセンスの改変版のため SPDX 代表は MIT。
            name = "Bouncy Castle",
            licenseId = "MIT",
            copyright = "Copyright (c) 2000-2024 The Legion of the Bouncy Castle Inc.",
            sourceUrl = "https://github.com/bcgit/bc-java",
            purposeRes = R.string.oss_purpose_bouncycastle,
        ),
        OssComponent(
            name = "XZ for Java",
            licenseId = "0BSD",  // 実質 Public Domain 相当
            copyright = "Authors: Lasse Collin and others (public domain)",
            sourceUrl = "https://tukaani.org/xz/java.html",
            purposeRes = R.string.oss_purpose_xz_java,
        ),

        // ===== (d) フォント (assets/fonts/) =====
        OssComponent(
            name = "Fira Code",
            licenseId = "OFL-1.1",
            copyright = "Copyright (c) 2014-2024 The Fira Code Project Authors",
            sourceUrl = "https://github.com/tonsky/FiraCode",
            purposeRes = R.string.oss_purpose_fira_code,
        ),
        OssComponent(
            name = "IBM Plex Mono",
            licenseId = "OFL-1.1",
            copyright = "Copyright (c) 2017 IBM Corp.",
            sourceUrl = "https://github.com/IBM/plex",
            purposeRes = R.string.oss_purpose_ibm_plex_mono,
        ),
        OssComponent(
            name = "JetBrains Mono",
            licenseId = "OFL-1.1",
            copyright = "Copyright (c) 2020 The JetBrains Mono Project Authors",
            sourceUrl = "https://github.com/JetBrains/JetBrainsMono",
            purposeRes = R.string.oss_purpose_jetbrains_mono,
        ),
    )

    fun list(): List<OssComponent> = all
}
