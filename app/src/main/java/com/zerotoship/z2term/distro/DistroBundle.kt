package com.zerotoship.z2term.distro

/**
 * 同梱 rootfs の世代 / メタ情報。
 *
 * APK ビルド時の rootfs (assets 配下の tgz) の中身が変わるたびに
 * [ROOTFS_VERSION] を +1 する。インストール時にこの値を
 * rootfs 直下の `.z2term-version` に書き込み、起動時に
 * [com.zerotoship.z2term.proot.ProotLauncher.isDistroReady] が比較する。
 *
 * 端末上の rootfs バージョン < ROOTFS_VERSION ならば
 * 自動的に再展開する (ユーザーが「ディストロ再展開」を押す必要なし)。
 *
 * Bump 履歴:
 *  - 1: 2026-05-19 初版 (alpine + tier 0+1+2 32pkg)
 *  - 2: 2026-05-20 etc passwd の root シェルを zsh に
 *  - 3: 2026-05-20 sudo / which を追加
 */
object DistroBundle {
    const val ROOTFS_VERSION = 3

    /** rootfs に書き込む version マーカーファイル名 (rootfs 直下) */
    const val VERSION_MARKER = ".z2term-version"
}
