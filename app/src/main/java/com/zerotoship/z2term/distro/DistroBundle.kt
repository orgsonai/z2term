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
 *  - 4: 2026-05-20 Tier 3 追加 (curl/wget/git/vim/tmux/htop/jq/rsync 等)
 *       + setUnixMode owner-only 修正 (sudo world-writable 解消) の再展開
 *  - 5: 2026-05-20 Tier 4: mosh + musl-locales + LC_ALL=C.UTF-8
 *  - 6: 2026-05-21 dropbear 追加 (PC→端末 SSH。OpenSSH sshd は proot で privsep 破綻)
 *  - 7: 2026-05-26 Tier 3.6 追加 (zip/unzip/openssl/bind-tools/file/diffutils/patch/bc)
 *  - 8: 2026-05-26 hardlink 展開の mode 修正で再展開 (zsh が hardlink で実行ビット欠落→起動不能の修正)
 */
object DistroBundle {
    const val ROOTFS_VERSION = 8

    /** rootfs に書き込む version マーカーファイル名 (rootfs 直下) */
    const val VERSION_MARKER = ".z2term-version"

    /** APK に同梱されている distro の id。これだけ ROOTFS_VERSION 比較の対象。 */
    const val BUNDLED_DISTRO_ID = "alpine"
}
