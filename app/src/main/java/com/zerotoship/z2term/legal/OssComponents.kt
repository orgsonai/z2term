package com.zerotoship.z2term.legal

import com.zerotoship.z2term.BuildConfig

/**
 * 同梱 OSS の権利表示・ライセンス・対応ソース URL のメタデータ。
 *
 * GPL/LGPL の頒布要件（GPL v2 §3 / GPL v3 §6 / LGPL v3 §4）に応えるため、
 * バイナリ同梱物については **対応ソースの取得手段** を必ず併記する。
 * OSS ライセンス画面 ([LicensesScreen]) でこのリストを一覧表示する。
 *
 * - 追加方針: APK の **実体に含まれる** ものだけ列挙する。設定で OFF にできる依存も実体は同梱しているため列挙対象。
 * - FOSS フレーバー: prebuilt バイナリ (proot/talloc/Alpine rootfs) が APK から除外される構成 (build.gradle.kts) では
 *   そのエントリを表示しない (実体に含まれていないものを「ライセンス対象」と表示すると却って誤解を招く)。
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
    /** 一言説明 (用途)。 */
    val purpose: String,
    /** Full フレーバーでのみ同梱されるバイナリ (foss では除外) なら true。 */
    val onlyFullFlavor: Boolean = false,
)

object OssComponents {

    /**
     * 一覧。読みやすさのため、(a) ネイティブ実行物 (b) rootfs 同梱パッケージ (c) Android/Java 依存
     * (d) フォント、の順で並べる。
     */
    private val all: List<OssComponent> = listOf(
        // ===== (a) ネイティブ実行物 (jniLibs に同梱) =====
        OssComponent(
            name = "PRoot",
            licenseId = "GPL-2.0",
            copyright = "Copyright (c) 2010-2024 STMicroelectronics, INRIA and contributors",
            sourceUrl = "https://github.com/termux/proot",
            purpose = "ユーザー空間 chroot/ptrace で Linux ディストロを動作させる中核",
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "talloc",
            licenseId = "LGPL-3.0",
            copyright = "Copyright (c) Andrew Tridgell and the Samba Team",
            sourceUrl = "https://gitlab.com/samba-team/samba/-/tree/master/lib/talloc",
            purpose = "PRoot が動的リンクするメモリ管理ライブラリ",
            onlyFullFlavor = true,
        ),

        // ===== (b) Alpine minirootfs (assets/alpine-minirootfs-*.tgz) — 個別パッケージ =====
        OssComponent(
            name = "Alpine Linux base",
            licenseId = "MIT",
            copyright = "Copyright (c) Alpine Linux Development Team",
            sourceUrl = "https://gitlab.alpinelinux.org/alpine/aports",
            purpose = "ベースシステム (alpine-base, baselayout, keys)",
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "musl libc",
            licenseId = "MIT",
            copyright = "Copyright (c) 2005-2020 Rich Felker, et al.",
            sourceUrl = "https://musl.libc.org/",
            purpose = "Alpine の C 標準ライブラリ",
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "BusyBox",
            licenseId = "GPL-2.0",
            copyright = "Copyright (c) Erik Andersen, Rob Landley, Denys Vlasenko and others",
            sourceUrl = "https://git.busybox.net/busybox/",
            purpose = "Alpine の主要 UNIX ツール (sh/ls/cp/...)",
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "Bash",
            licenseId = "GPL-3.0",
            copyright = "Copyright (c) Free Software Foundation, Inc.",
            sourceUrl = "https://ftp.gnu.org/gnu/bash/",
            purpose = "対話シェル (alpine-packages.txt より)",
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "Zsh",
            licenseId = "Zsh",
            copyright = "Copyright (c) 1992-2024 Paul Falstad, Peter Stephenson, et al.",
            sourceUrl = "https://www.zsh.org/",
            purpose = "対話シェル",
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "OpenSSH client/server",
            licenseId = "BSD-2-Clause",
            copyright = "Copyright (c) The OpenBSD Project",
            sourceUrl = "https://www.openssh.com/",
            purpose = "SSH クライアント (proot 内サーバは Dropbear を利用)",
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "Dropbear",
            licenseId = "MIT",
            copyright = "Copyright (c) 2002-2024 Matt Johnston",
            sourceUrl = "https://matt.ucc.asn.au/dropbear/dropbear.html",
            purpose = "proot 互換 SSH サーバ (z2term の sshd ラッパー実装で起動)",
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "coreutils",
            licenseId = "GPL-3.0",
            copyright = "Copyright (c) Free Software Foundation, Inc.",
            sourceUrl = "https://www.gnu.org/software/coreutils/",
            purpose = "ls/cp/mv 等の GNU 版 (alpine-packages.txt より)",
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "GNU sed / awk / grep / findutils",
            licenseId = "GPL-3.0",
            copyright = "Copyright (c) Free Software Foundation, Inc.",
            sourceUrl = "https://www.gnu.org/software/",
            purpose = "テキスト処理 / ファイル検索",
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "Git",
            licenseId = "GPL-2.0",
            copyright = "Copyright (c) Linus Torvalds and others",
            sourceUrl = "https://git-scm.com/",
            purpose = "バージョン管理",
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "OpenSSL (libcrypto3 / libssl3)",
            licenseId = "Apache-2.0",
            copyright = "Copyright (c) 1998-2024 The OpenSSL Project",
            sourceUrl = "https://www.openssl.org/source/",
            purpose = "TLS / 暗号ライブラリ",
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "ncurses / readline",
            licenseId = "MIT",  // ncurses=MIT-like, readline=GPL-3.0; 代表は MIT 側
            copyright = "Copyright (c) Free Software Foundation, Inc. and contributors",
            sourceUrl = "https://invisible-island.net/ncurses/",
            purpose = "端末制御 / 行編集 (readline は GPL-3.0 の別ライセンスでもある)",
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "Vim",
            licenseId = "Vim",
            copyright = "Copyright (c) Bram Moolenaar et al.",
            sourceUrl = "https://www.vim.org/",
            purpose = "テキストエディタ",
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "tmux / screen",
            licenseId = "BSD-3-Clause",
            copyright = "Copyright (c) Nicholas Marriott (tmux), FSF (screen=GPL-3.0)",
            sourceUrl = "https://github.com/tmux/tmux  /  https://www.gnu.org/software/screen/",
            purpose = "端末多重化",
            onlyFullFlavor = true,
        ),
        OssComponent(
            name = "Mosh",
            licenseId = "GPL-3.0",
            copyright = "Copyright (c) 2012-2024 Keith Winstein and others",
            sourceUrl = "https://mosh.org/",
            purpose = "ローミング SSH 代替",
            onlyFullFlavor = true,
        ),

        // ===== (c) Android / Java 依存 (gradle) =====
        OssComponent(
            name = "AndroidX / Jetpack Compose",
            licenseId = "Apache-2.0",
            copyright = "Copyright (c) The Android Open Source Project",
            sourceUrl = "https://android.googlesource.com/",
            purpose = "UI / ライフサイクル / DataStore",
        ),
        OssComponent(
            name = "Kotlin Standard Library",
            licenseId = "Apache-2.0",
            copyright = "Copyright (c) JetBrains s.r.o. and contributors",
            sourceUrl = "https://github.com/JetBrains/kotlin",
            purpose = "Kotlin ランタイム",
        ),
        OssComponent(
            name = "JSch",
            licenseId = "BSD-3-Clause",
            copyright = "Copyright (c) ymnk, JCraft, Inc.",
            sourceUrl = "https://github.com/mwiede/jsch",
            purpose = "SSH クライアントライブラリ",
        ),
        OssComponent(
            name = "XZ for Java",
            licenseId = "0BSD",  // 実質 Public Domain 相当
            copyright = "Authors: Lasse Collin and others (public domain)",
            sourceUrl = "https://tukaani.org/xz/java.html",
            purpose = "XZ (LZMA2) 解凍 (Kali rootfs などの .tar.xz)",
        ),

        // ===== (d) フォント (assets/fonts/) =====
        OssComponent(
            name = "Fira Code",
            licenseId = "OFL-1.1",
            copyright = "Copyright (c) 2014-2024 The Fira Code Project Authors",
            sourceUrl = "https://github.com/tonsky/FiraCode",
            purpose = "ターミナル用等幅フォント (合字対応)",
        ),
        OssComponent(
            name = "IBM Plex Mono",
            licenseId = "OFL-1.1",
            copyright = "Copyright (c) 2017 IBM Corp.",
            sourceUrl = "https://github.com/IBM/plex",
            purpose = "ターミナル用等幅フォント",
        ),
        OssComponent(
            name = "JetBrains Mono",
            licenseId = "OFL-1.1",
            copyright = "Copyright (c) 2020 The JetBrains Mono Project Authors",
            sourceUrl = "https://github.com/JetBrains/JetBrainsMono",
            purpose = "ターミナル用等幅フォント",
        ),
    )

    /** 現在のフレーバーで実体が同梱されているコンポーネントだけ返す。 */
    fun forCurrentFlavor(): List<OssComponent> =
        if (BuildConfig.IS_FOSS) all.filterNot { it.onlyFullFlavor } else all
}
