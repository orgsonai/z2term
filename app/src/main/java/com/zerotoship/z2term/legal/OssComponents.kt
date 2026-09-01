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
    /** 同一 SPDX ID でも著作権者ごとの原文が必要な場合に使う assets/licenses/ 配下のファイル名（拡張子なし）。 */
    val licenseAsset: String = licenseId,
)

object OssComponents {

    /**
     * 一覧。読みやすさのため、(a) ネイティブ実行物 (b) rootfs 同梱パッケージ (c) Android/Java 依存
     * (d) フォント (e) かな漢字変換データ、の順で並べる。
     *
     * ⚠ **コードだけでなく assets の「データ」も告知対象**。辞書・統計データにも著作権があり、
     * SKK 辞書は GPL-2.0-or-later、IPADIC は NAIST ライセンス、共起データの元は CC BY-SA の
     * ウィキペディアで、いずれも著作権表示と対応ソースの明示を求めている。ここに並べるのを
     * 忘れると、告知義務を果たさないまま配布することになる (0.8.473 で 3 件まとめて補完)。
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
            name = "Kotlin Coroutines 1.7.3",
            licenseId = "Apache-2.0",
            copyright = "Copyright (c) JetBrains s.r.o. and Kotlin contributors",
            sourceUrl = "https://github.com/Kotlin/kotlinx.coroutines/tree/1.7.3",
            purposeRes = R.string.oss_purpose_kotlin_coroutines,
        ),
        OssComponent(
            name = "JSch",
            licenseId = "BSD-3-Clause",
            copyright = "Copyright (c) ymnk, JCraft, Inc.",
            sourceUrl = "https://github.com/mwiede/jsch",
            purposeRes = R.string.oss_purpose_jsch,
        ),
        OssComponent(
            name = "JZlib (JSch bundled copy)",
            licenseId = "BSD-3-Clause",
            copyright = "Copyright (c) 2000-2011 ymnk, JCraft, Inc. All rights reserved.",
            sourceUrl = "https://github.com/ymnk/jzlib",
            purposeRes = R.string.oss_purpose_jzlib,
            licenseAsset = "JZlib-BSD-3-Clause",
        ),
        OssComponent(
            name = "jBCrypt (JSch bundled copy)",
            licenseId = "ISC",
            copyright = "Copyright (c) 2006 Damien Miller <djm@mindrot.org>",
            sourceUrl = "https://www.mindrot.org/projects/jBCrypt/",
            purposeRes = R.string.oss_purpose_jbcrypt,
            licenseAsset = "jBCrypt-ISC",
        ),
        OssComponent(
            name = "OkHttp 5.3.0",
            licenseId = "Apache-2.0",
            copyright = "Copyright (c) Square, Inc. and contributors",
            sourceUrl = "https://github.com/square/okhttp",
            purposeRes = R.string.oss_purpose_okhttp,
        ),
        OssComponent(
            name = "Okio 3.16.2",
            licenseId = "Apache-2.0",
            copyright = "Copyright (c) 2013 Square, Inc. and contributors",
            sourceUrl = "https://github.com/square/okio/tree/3.16.2",
            purposeRes = R.string.oss_purpose_okio,
        ),
        OssComponent(
            name = "SMBJ 0.15.0",
            licenseId = "Apache-2.0",
            copyright = "Copyright (c) 2016 SMBJ Contributors",
            sourceUrl = "https://github.com/hierynomus/smbj/tree/v0.15.0",
            purposeRes = R.string.oss_purpose_smbj,
        ),
        OssComponent(
            name = "asn-one 0.6.0",
            licenseId = "Apache-2.0",
            copyright = "Copyright 2016 Jeroen van Erp <jeroen@hierynomus.com>",
            sourceUrl = "https://github.com/hierynomus/asn-one/tree/v0.6.0",
            purposeRes = R.string.oss_purpose_asn_one,
        ),
        OssComponent(
            name = "MBassador 1.3.2",
            licenseId = "MIT",
            copyright = "Copyright (c) 2012 Benjamin Diedrichsen",
            sourceUrl = "https://github.com/bennidi/mbassador",
            purposeRes = R.string.oss_purpose_mbassador,
            licenseAsset = "MBassador-MIT",
        ),
        OssComponent(
            name = "SLF4J API 2.0.18",
            licenseId = "MIT",
            copyright = "Copyright (c) 2004-2022 QOS.ch Sarl (Switzerland)",
            sourceUrl = "https://github.com/qos-ch/slf4j/tree/v_2.0.18",
            purposeRes = R.string.oss_purpose_slf4j,
            licenseAsset = "SLF4J-MIT",
        ),
        OssComponent(
            // BouncyCastle License は MIT X11 ライセンスの改変版のため SPDX 代表は MIT。
            name = "Bouncy Castle",
            licenseId = "MIT",
            copyright = "Copyright (c) 2000-2026 The Legion of the Bouncy Castle Inc.",
            sourceUrl = "https://github.com/bcgit/bc-java/tree/r1rv85v2",
            purposeRes = R.string.oss_purpose_bouncycastle,
            licenseAsset = "Bouncy-Castle-MIT",
        ),
        OssComponent(
            name = "XZ for Java",
            licenseId = "0BSD",  // 実質 Public Domain 相当
            copyright = "Authors: Lasse Collin and others (public domain)",
            sourceUrl = "https://tukaani.org/xz/java.html",
            purposeRes = R.string.oss_purpose_xz_java,
        ),
        OssComponent(
            name = "Guava ListenableFuture 1.0",
            licenseId = "Apache-2.0",
            copyright = "Copyright (c) The Guava Authors",
            sourceUrl = "https://github.com/google/guava",
            purposeRes = R.string.oss_purpose_listenablefuture,
        ),
        OssComponent(
            name = "JetBrains Java Annotations 23.0.0",
            licenseId = "Apache-2.0",
            copyright = "Copyright 2000-2022 JetBrains s.r.o.",
            sourceUrl = "https://github.com/JetBrains/java-annotations",
            purposeRes = R.string.oss_purpose_jetbrains_annotations,
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

        // ===== (e) かな漢字変換データ (assets/) =====
        // 出所の詳細は assets/KKC-DICT-NOTICE.txt にまとめてある。
        OssComponent(
            // 送り仮名なしの見出しだけを取り出し、注釈を除いて UTF-8 化した派生物。
            // 著作権表示は z2dict.txt の先頭にそのまま残してある。
            name = "SKK-JISYO.L（同梱辞書 z2dict.txt の原本）",
            licenseId = "GPL-2.0",
            copyright = "Copyright (C) 1988-1995, 1997, 1999-2014 " +
                "Masahiko Sato, Hironobu Takahashi, Yukiyoshi Kameyama, NAKAJIMA Mikio, " +
                "MITA Yuusuke and SKK Development Team <skk@ring.gr.jp>",
            sourceUrl = "https://github.com/skk-dev/dict",
            purposeRes = R.string.oss_purpose_skk_jisyo,
        ),
        OssComponent(
            // SPDX に該当 ID が無いため原文 (mecab-ipadic の COPYING) を丸ごと同梱する。
            // 語彙の多くが由来する ICOT Free Software の条件も同ファイルに含まれる。
            name = "IPADIC（mecab-ipadic 2.7.0-20070801）",
            licenseId = "IPADIC",
            copyright = "Copyright 2000, 2001, 2002, 2003 " +
                "Nara Institute of Science and Technology. All Rights Reserved.",
            sourceUrl = "https://github.com/taku910/mecab",
            purposeRes = R.string.oss_purpose_ipadic,
            licenseAsset = "IPADIC-NAIST",
        ),
        OssComponent(
            // 共起 2-gram を頻度で足切りして Bloom フィルタ化したもの (原文は保持しない)。
            // 生成手順は scripts/build-collocation.sh。
            name = "日本語版ウィキペディア（共起データ kkc_colloc.bloom の元）",
            licenseId = "CC-BY-SA-4.0",
            copyright = "Copyright (c) Wikipedia contributors",
            sourceUrl = "https://dumps.wikimedia.org/jawiki/",
            purposeRes = R.string.oss_purpose_jawiki_collocation,
        ),
    )

    fun list(): List<OssComponent> = all
}
