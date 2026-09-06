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
 *  - **冪等**。[PACMAN_KEYRING_MARKER] があれば即 exit 0 (起動のたびに呼んでよい)。
 *  - **pacman が無い distro では何もしない**。Alpine/Debian 系で呼ばれても無害。
 *  - **端末の画面で走らせる**。数十秒かかることがあるので、黙って待たせない。止めたければ
 *    Ctrl-C で止められる (次に開いたときにまたやり直す)。
 */
/**
 * 「z2term が鍵束を用意し終えた」印のファイル名 (`/etc/pacman.d/gnupg/` 配下)。
 *
 * ⚠ **pacman が作るファイルで判定しないこと。** pacman は鍵の取得に失敗する過程でも
 * `trustdb.gpg` 等を作るため、それを「済み」と読むと**中身が空のまま初期化が二度と
 * 走らない**状態に固定される (0.8.319 の退行)。印は [pacmanKeyringScript] が
 * `--populate` に成功したときだけ書く。
 */
const val PACMAN_KEYRING_MARKER = ".z2term-keyring-ready"

fun pacmanKeyringScript(lang: String): String {
    // 言語ごとの文言を選ぶ道具。3 言語目は t(en = …, ja = …) の後ろへ変わり値を足す ([CliText])。
    val t = CliText(lang)
    val d = "${'$'}"
    val marker = PACMAN_KEYRING_MARKER

    val msgStart = t(
        en = "🔑 Setting up the pacman keyring (first time only, no network). This takes a moment…",
        ja = "🔑 pacman の鍵束を用意します (初回だけ・通信しません)。少し時間がかかります…",
        "zh-CN" to "🔑 正在准备 pacman 的密钥环 (只有第一次，不联网)。需要稍等一会儿…",
        "zh-TW" to "🔑 正在準備 pacman 的金鑰環 (只有第一次，不聯網)。需要稍等一會兒…",
        "es" to "🔑 Preparando el llavero de pacman (solo la primera vez, sin red). Esto tarda un momento…"
    )
    val msgOk = t(
        en = "✅ Keyring ready. Packages can be installed now.",
        ja = "✅ 鍵束の用意ができました。パッケージを入れられます。",
        "zh-CN" to "✅ 密钥环准备好了。现在可以安装软件包了。",
        "zh-TW" to "✅ 金鑰環準備好了。現在可以安裝套件了。",
        "es" to "✅ Llavero listo. Ya se pueden instalar paquetes."
    )
    val msgInitFail = t(
        en = "❌ pacman-key --init failed. It will be retried the next time this tab opens.",
        ja = "❌ pacman-key --init に失敗しました。次にこのタブを開いたときにやり直します。",
        "zh-CN" to "❌ pacman-key --init 失败了。下次打开这个标签页时会再试一次。",
        "zh-TW" to "❌ pacman-key --init 失敗了。下次開啟這個分頁時會再試一次。",
        "es" to "❌ pacman-key --init falló. Se volverá a intentar la próxima vez que abras esta pestaña."
    )
    val msgNoKeyrings = t(
        en = "❌ No bundled keyrings found under /usr/share/pacman/keyrings.",
        ja = "❌ 同梱の鍵束 (/usr/share/pacman/keyrings) が見つかりません。",
        "zh-CN" to "❌ 找不到随附的密钥环 (/usr/share/pacman/keyrings)。",
        "zh-TW" to "❌ 找不到隨附的金鑰環 (/usr/share/pacman/keyrings)。",
        "es" to "❌ No se encontró ningún llavero incluido en /usr/share/pacman/keyrings."
    )
    val msgPopulateFail = t(
        en = "❌ pacman-key --populate failed. It will be retried the next time this tab opens.",
        ja = "❌ pacman-key --populate に失敗しました。次にこのタブを開いたときにやり直します。",
        "zh-CN" to "❌ pacman-key --populate 失败了。下次打开这个标签页时会再试一次。",
        "zh-TW" to "❌ pacman-key --populate 失敗了。下次開啟這個分頁時會再試一次。",
        "es" to "❌ pacman-key --populate falló. Se volverá a intentar la próxima vez que abras esta pestaña."
    )

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
        |# コマンドを **画面に出しながら、出力を丸ごと診断ファイルにも残して** 実行する。
        |# ⚠ `cmd | tee` だと ${d}? は tee の結果になり、失敗を取りこぼす。サブシェル内で
        |# 終了コードを別ファイルへ書いて拾う (POSIX sh で確実に動く形)。
        |# ⚠ **自分のメッセージだけ残しても意味が無い。** 0.8.320 は「--populate に失敗」しか
        |# 残らず、gpg が何と言ったのか分からないまま実機を往復した。道具の出力ごと残す。
        |run_logged() {
        |  : "${d}{Z2RC:=/tmp/z2-keyring-rc.${d}${d}}"   # ⚠ 先頭の : が無いと値をコマンドとして実行する
        |  { "${d}@" 2>&1; echo ${d}? > "${d}Z2RC"; } | tee -a "${d}DIAG"
        |  rc=${d}(cat "${d}Z2RC" 2>/dev/null || echo 1)
        |  rm -f "${d}Z2RC" 2>/dev/null
        |  return "${d}rc"
        |}
        |
        |# 既に用意できていれば何もしない。
        |# ⚠ **判定は「z2term が populate に成功したときだけ書く印」で行う。** `trustdb.gpg` や
        |# ディレクトリの有無で判定してはいけない — **pacman は鍵の取得に失敗する過程で
        |# /etc/pacman.d/gnupg 配下にファイルを作る**ので、「入れ物はあるが中身は使えない」状態を
        |# 初期化済みと誤判定し、**二度と初期化が走らなくなる** (0.8.319 で実際に踏んだ)。
        |[ -f "${d}GNUPGDIR/$marker" ] && exit 0
        |# pacman を使わない distro では何もしない (Alpine/Debian 系で呼ばれても無害)。
        |command -v pacman-key >/dev/null 2>&1 || exit 0
        |
        |echo "$msgStart"
        |
        |# --init は足りないものを作り足すだけで既存は壊さないので、前回 Ctrl-C で止めた
        |# 中途半端な鍵束があっても、そのまま続きから作れる。
        |mkdir -p "${d}GNUPGDIR" 2>/dev/null
        |
        |# 失敗したときに gpg-agent 側の理由を取る。gpg は agent の起動失敗を
        |# 「exit status 2」としか伝えないので、**agent を直接起こして本人に喋らせる**。
        |# あわせてソケットの実パスも出す — z2root は AF_UNIX のパスをホスト側へ翻訳するため、
        |# sun_path の 108 バイト制限に当たると bind が失敗して agent が即死する。
        |diag_gpg_agent() {
        |  echo "--- z2diag: gpgconf --list-dirs ---" >> "${d}DIAG" 2>/dev/null
        |  gpgconf --homedir "${d}GNUPGDIR" --list-dirs >> "${d}DIAG" 2>&1
        |  # ⚠ **`--daemon` を付けること。** 付けないと gpg-agent は「起動しているか調べるだけ」の
        |  # モードになり、"no gpg-agent running in this session" と言って rc=2 で終わる
        |  # (0.8.322 の診断はこれを踏んで、起動失敗の理由を取れていなかった)。
        |  echo "--- z2diag: ls ${d}GNUPGDIR ---" >> "${d}DIAG" 2>/dev/null
        |  ls -la "${d}GNUPGDIR" >> "${d}DIAG" 2>&1
        |  echo "--- z2diag: gpg-agent --daemon --no-detach (homedir) ---" >> "${d}DIAG" 2>/dev/null
        |  timeout 8 gpg-agent --homedir "${d}GNUPGDIR" --daemon --no-detach -vv >> "${d}DIAG" 2>&1
        |  echo "--- z2diag: rc=${d}? ---" >> "${d}DIAG" 2>/dev/null
        |  # 同じ bind を **短い /tmp のパス**で試す差分テスト。
        |  #   ここも ENOENT   → AF_UNIX の bind 翻訳そのものが効いていない (z2root 側)
        |  #   ここは通る      → /etc/pacman.d/gnupg 固有 (置き場・存在・パス長の問題)
        |  mkdir -p /tmp/z2gpgtest 2>/dev/null
        |  echo "--- z2diag: gpg-agent (homedir=/tmp/z2gpgtest) ---" >> "${d}DIAG" 2>/dev/null
        |  timeout 8 gpg-agent --homedir /tmp/z2gpgtest --daemon --no-detach -vv >> "${d}DIAG" 2>&1
        |  echo "--- z2diag: rc=${d}? ---" >> "${d}DIAG" 2>/dev/null
        |}
        |
        |: > "${d}DIAG" 2>/dev/null
        |
        |if ! run_logged pacman-key --init; then
        |  say_fail "$msgInitFail"
        |  diag_gpg_agent
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
        |if ! run_logged pacman-key --populate ${d}KEYRINGS; then
        |  say_fail "$msgPopulateFail (keyrings:${d}KEYRINGS)"
        |  diag_gpg_agent
        |  exit 1
        |fi
        |
        |# ここまで来たときだけ印を書く (= 次回から素通りしてよい状態)。
        |: > "${d}GNUPGDIR/$marker" 2>/dev/null
        |rm -f "${d}DIAG" 2>/dev/null
        |
        |echo "$msgOk"
    """.trimMargin() + "\n"
}
