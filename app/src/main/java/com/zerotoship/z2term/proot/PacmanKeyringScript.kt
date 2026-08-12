package com.zerotoship.z2term.proot

/**
 * `z2-pacman-keyring`: pacman の**鍵束を初期化する**ワンショット (0.8.316)。
 *
 * ## なぜ要るのか
 *
 * Arch (Arch Linux ARM) の rootfs は linuxcontainers のイメージから取る。このイメージには
 * **`/etc/pacman.d/gnupg` が入っていない** — 通常は systemd の初回起動で `pacman-key --init`
 * が走る前提だが、**proot / z2root では systemd が動かないので誰も初期化しない**。
 * 一方 `pacman.conf` は `SigLevel = Required DatabaseOptional` なので署名検証は必須。
 * 結果、パッケージを入れようとすると必ずここで止まる (利用者の報告・実機ログ):
 *
 * ```
 * warning: Public keyring not found; have you run 'pacman-key --init'?
 * error: keyring is not writable          ← /etc/pacman.d/gnupg が無く access(W_OK) が ENOENT
 * error: required key missing from keyring
 * error: failed to commit transaction (unexpected error)
 * ```
 *
 * ⚠ **GUI (`z2gui`) 固有の問題ではない。** たまたま最初に pacman を叩くのが GUI 導入だった
 * だけで、`pacman -S` は何を入れようとしても同じ所で落ちる (`sshd` = dropbear も同様)。
 *
 * ## なぜ SigLevel を切らないのか
 *
 * `SigLevel = Never` にすればエラーは消えるが、それは**症状を隠すだけ**で、以後この端末は
 * 署名を検証せずにパッケージを入れ続けることになる。原因は「鍵束が無い」ことなので、
 * **鍵束を作って条件そのものを壊す**。
 *
 * ## ネットワークは要らない
 *
 * イメージには `/usr/share/pacman/keyrings/archlinuxarm.gpg` と `archlinux.gpg` が同梱されて
 * いる (ミラーは `mirror.archlinuxarm.org`、repo は core/extra/alarm/aur = **Arch Linux ARM の鍵**
 * で署名されている)。`--populate` はこのローカルファイルを読むので、**通信せずに完結**する。
 * 通信するのは `--init` が失敗して pacman が鍵を取りに行く場合だけで、それはもう起きない。
 *
 * ## 作法
 *
 *  - **冪等**。`trustdb.gpg` があれば即 exit 0 (起動のたびに呼んでよい)。
 *  - **pacman が無い distro では何もしない**。Alpine/Debian 系で呼ばれても無害。
 *  - **端末の画面で走らせる**。数十秒かかることがあるので、黙って待たせない。止めたければ
 *    Ctrl-C で止められる (次に開いたときにまたやり直す)。
 */
fun pacmanKeyringScript(lang: String): String {
    val ja = lang != "en"
    val d = "${'$'}"

    val msgStart = if (ja)
        "🔑 pacman の鍵束を用意します (初回だけ・通信しません)。少し時間がかかります…"
    else
        "🔑 Setting up the pacman keyring (first time only, no network). This takes a moment…"
    val msgOk = if (ja)
        "✅ 鍵束の用意ができました。パッケージを入れられます。"
    else
        "✅ Keyring ready. Packages can be installed now."
    val msgInitFail = if (ja)
        "❌ pacman-key --init に失敗しました。次にこのタブを開いたときにやり直します。"
    else
        "❌ pacman-key --init failed. It will be retried the next time this tab opens."
    val msgNoKeyrings = if (ja)
        "❌ 同梱の鍵束 (/usr/share/pacman/keyrings) が見つかりません。"
    else
        "❌ No bundled keyrings found under /usr/share/pacman/keyrings."
    val msgPopulateFail = if (ja)
        "❌ pacman-key --populate に失敗しました。次にこのタブを開いたときにやり直します。"
    else
        "❌ pacman-key --populate failed. It will be retried the next time this tab opens."

    return """
        |#!/bin/sh
        |# z2term: pacman の鍵束を初期化する (冪等)。詳細は PacmanKeyringScript.kt を参照。
        |GNUPGDIR=/etc/pacman.d/gnupg
        |# ⚠ 失敗の記録はファイルにも残す。端末タブの出力はアプリのログ (logcat) に流れないので、
        |# これが無いと「失敗しました」の一行しか手元に残らない。アプリ側 (ProotLauncher) が次の
        |# 起動でこれを読んで logcat へ出し、読んだら消す。
        |# ⚠ **置き場は rootfs の外 (共有ホーム = /root)**。rootfs は再展開のたびに
        |# `deleteRecursively` で丸ごと消える (DistroInstaller.install) ので、/tmp に置くと
        |# **いちばん知りたい失敗の記録が、次の再展開で消える** (0.8.317 で実際に踏んだ)。
        |DIAG=/root/.z2term/pacman-keyring.log
        |mkdir -p /root/.z2term 2>/dev/null
        |say_fail() { echo "${d}1" >&2; echo "${d}1" >> "${d}DIAG" 2>/dev/null; }
        |
        |# 既に初期化済みなら何もしない。trustdb.gpg は --populate まで済んだ証。
        |[ -f "${d}GNUPGDIR/trustdb.gpg" ] && exit 0
        |# pacman を使わない distro では何もしない (Alpine/Debian 系で呼ばれても無害)。
        |command -v pacman-key >/dev/null 2>&1 || exit 0
        |
        |echo "$msgStart"
        |
        |# --init は足りないものを作り足すだけで既存は壊さないので、前回 Ctrl-C で止めた
        |# 中途半端な鍵束があっても、そのまま続きから作れる。
        |mkdir -p "${d}GNUPGDIR" 2>/dev/null
        |
        |: > "${d}DIAG" 2>/dev/null
        |
        |if ! pacman-key --init; then
        |  say_fail "$msgInitFail"
        |  # ⚠ **切り分け材料を必ず残す。** 「失敗しました」だけでは原因に辿り着けず、実機を
        |  # 何度も往復することになる (0.8.316 で実際にそうなった)。pacman-key が弾く条件は
        |  # `EUID != 0` の 1 つだけなので、**誰が 0 を返していないか**が分かれば足りる:
        |  #   id -u   … C から geteuid(2) を直に呼ぶ (coreutils)
        |  #   sh/bash … シェルが起動時に geteuid(2) から作る変数
        |  # エンジンの fakeroot 偽装が全体に効いていないのか、シェルにだけ効いていないのかが
        |  # この 1 行で分かれる。
        |  say_fail "z2diag: id-u=${d}(id -u 2>&1) id-ur=${d}(id -ur 2>&1) sh-EUID=${d}{EUID-unset} sh-UID=${d}{UID-unset} bash-EUID=${d}(bash -c 'echo ${d}EUID' 2>&1)"
        |  exit 1
        |fi
        |
        |# 同梱されている鍵束だけを対象にする (無いものを渡すと --populate ごと失敗する)。
        |# ⚠ archlinuxarm を先に置く。このイメージのミラーは mirror.archlinuxarm.org で、
        |# core/extra/alarm は **Arch Linux ARM の鍵**で署名されている。
        |KEYRINGS=""
        |for k in archlinuxarm archlinux; do
        |  [ -f "/usr/share/pacman/keyrings/${d}k.gpg" ] && KEYRINGS="${d}KEYRINGS ${d}k"
        |done
        |if [ -z "${d}KEYRINGS" ]; then
        |  say_fail "$msgNoKeyrings"
        |  exit 1
        |fi
        |
        |if ! pacman-key --populate ${d}KEYRINGS; then
        |  say_fail "$msgPopulateFail (keyrings:${d}KEYRINGS)"
        |  exit 1
        |fi
        |
        |echo "$msgOk"
    """.trimMargin() + "\n"
}
