package com.zerotoship.z2term.legal

import androidx.annotation.StringRes
import com.zerotoship.z2term.BuildConfig
import com.zerotoship.z2term.R

/**
 * 同梱 OSS の権利表示・ライセンス・対応ソース URL のメタデータ。
 *
 * GPL/LGPL の頒布要件（GPL v2 §3 / GPL v3 §6 / LGPL v3 §4）に応えるため、
 * バイナリ同梱物については **対応ソースの取得手段** を必ず併記する。
 * OSS ライセンス画面 ([LicensesScreen]) でこのリストを一覧表示する。
 *
 * - 追加方針: APK の **実体に含まれる** ものだけ列挙する。設定で OFF にできる依存も実体は同梱しているため列挙対象。
 * - FOSS フレーバー: Alpine rootfs (パッケージ群) と PRoot / talloc (third-party prebuilt) は
 *   foss APK から除外されるため表示しない ([onlyFullFlavor] = true)。foss の実行エンジンは
 *   z2term 同梱ソースからビルドする z2root (本体 GPL-3.0 に含まれる) なので別エントリは不要。
 *   フォント (OFL) は両フレーバー同梱なので表示維持。実体に含まれていないものを表示すると誤解を
 *   招き、含まれるのに隠すと告知義務に反するため実態に揃える。
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
    /** Full フレーバーでのみ同梱されるバイナリ (foss では除外) なら true。 */
    val onlyFullFlavor: Boolean = false,
)

object OssComponents {

    /**
     * 一覧。読みやすさのため、(a) ネイティブ実行物 (b) rootfs 同梱パッケージ (c) Android/Java 依存
     * (d) フォント、の順で並べる。
     */
    private val all: List<OssComponent> = listOf(
        // ===== 本アプリ本体 (全フレーバー共通・先頭固定) =====
        OssComponent(
            name = "z2term（本アプリ本体）",
            licenseId = "GPL-3.0",
            copyright = "Copyright (c) 2026 Zero to Ship",
            sourceUrl = "https://github.com/orgsonai/z2term",
            purposeRes = R.string.oss_purpose_z2term,
        ),

        // ===== (a) ネイティブ実行物 (jniLibs に同梱) =====
        // PRoot / talloc は third-party prebuilt (F-Droid 非適合) で full のみ APK 同梱する。
        // foss は同梱しない (実行エンジンはソースビルドの z2root) ため onlyFullFlavor=true。
        OssComponent(
            name = "PRoot",
            licenseId = "GPL-2.0",
            copyright = "Copyright (c) 2010-2024 STMicroelectronics, INRIA and contributors",
            sourceUrl = "https://github.com/termux/proot",
            purposeRes = R.string.oss_purpose_proot,
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "talloc",
            licenseId = "LGPL-3.0",
            copyright = "Copyright (c) Andrew Tridgell and the Samba Team",
            sourceUrl = "https://gitlab.com/samba-team/samba/-/tree/master/lib/talloc",
            purposeRes = R.string.oss_purpose_talloc,
            onlyFullFlavor = true,
        ),

        // ===== (b) Alpine minirootfs (assets/alpine-minirootfs-*.tgz) — 個別パッケージ =====
        OssComponent(
            name = "Alpine Linux base",
            licenseId = "MIT",
            copyright = "Copyright (c) Alpine Linux Development Team",
            sourceUrl = "https://gitlab.alpinelinux.org/alpine/aports",
            purposeRes = R.string.oss_purpose_alpine_base,
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "musl libc",
            licenseId = "MIT",
            copyright = "Copyright (c) 2005-2020 Rich Felker, et al.",
            sourceUrl = "https://musl.libc.org/",
            purposeRes = R.string.oss_purpose_musl_libc,
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "BusyBox",
            licenseId = "GPL-2.0",
            copyright = "Copyright (c) Erik Andersen, Rob Landley, Denys Vlasenko and others",
            sourceUrl = "https://git.busybox.net/busybox/",
            purposeRes = R.string.oss_purpose_busybox,
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "Bash",
            licenseId = "GPL-3.0",
            copyright = "Copyright (c) Free Software Foundation, Inc.",
            sourceUrl = "https://ftp.gnu.org/gnu/bash/",
            purposeRes = R.string.oss_purpose_bash,
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "Zsh",
            licenseId = "Zsh",
            copyright = "Copyright (c) 1992-2024 Paul Falstad, Peter Stephenson, et al.",
            sourceUrl = "https://www.zsh.org/",
            purposeRes = R.string.oss_purpose_zsh,
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "OpenSSH client/server",
            licenseId = "BSD-2-Clause",
            copyright = "Copyright (c) The OpenBSD Project",
            sourceUrl = "https://www.openssh.com/",
            purposeRes = R.string.oss_purpose_openssh,
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "Dropbear",
            licenseId = "MIT",
            copyright = "Copyright (c) 2002-2024 Matt Johnston",
            sourceUrl = "https://matt.ucc.asn.au/dropbear/dropbear.html",
            purposeRes = R.string.oss_purpose_dropbear,
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "coreutils",
            licenseId = "GPL-3.0",
            copyright = "Copyright (c) Free Software Foundation, Inc.",
            sourceUrl = "https://www.gnu.org/software/coreutils/",
            purposeRes = R.string.oss_purpose_coreutils,
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "GNU sed / awk / grep / findutils",
            licenseId = "GPL-3.0",
            copyright = "Copyright (c) Free Software Foundation, Inc.",
            sourceUrl = "https://www.gnu.org/software/",
            purposeRes = R.string.oss_purpose_gnu_textutils,
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "Git",
            licenseId = "GPL-2.0",
            copyright = "Copyright (c) Linus Torvalds and others",
            sourceUrl = "https://git-scm.com/",
            purposeRes = R.string.oss_purpose_git,
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "OpenSSL (libcrypto3 / libssl3)",
            licenseId = "Apache-2.0",
            copyright = "Copyright (c) 1998-2024 The OpenSSL Project",
            sourceUrl = "https://www.openssl.org/source/",
            purposeRes = R.string.oss_purpose_openssl,
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "ncurses / readline",
            licenseId = "MIT",  // ncurses=MIT-like, readline=GPL-3.0; 代表は MIT 側
            copyright = "Copyright (c) Free Software Foundation, Inc. and contributors",
            sourceUrl = "https://invisible-island.net/ncurses/",
            purposeRes = R.string.oss_purpose_ncurses_readline,
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "Vim",
            licenseId = "Vim",
            copyright = "Copyright (c) Bram Moolenaar et al.",
            sourceUrl = "https://www.vim.org/",
            purposeRes = R.string.oss_purpose_vim,
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "tmux / screen",
            licenseId = "BSD-3-Clause",
            copyright = "Copyright (c) Nicholas Marriott (tmux), FSF (screen=GPL-3.0)",
            sourceUrl = "https://github.com/tmux/tmux  /  https://www.gnu.org/software/screen/",
            purposeRes = R.string.oss_purpose_tmux_screen,
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "Mosh",
            licenseId = "GPL-3.0",
            copyright = "Copyright (c) 2012-2024 Keith Winstein and others",
            sourceUrl = "https://mosh.org/",
            purposeRes = R.string.oss_purpose_mosh,
            onlyFullFlavor = true,
        ),

        // ===== (c) Android / Java 依存 (gradle) =====
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

    /** 現在のフレーバーで実体が同梱されているコンポーネントだけ返す。 */
    fun forCurrentFlavor(): List<OssComponent> =
        if (BuildConfig.IS_FOSS) all.filterNot { it.onlyFullFlavor } else all
}
