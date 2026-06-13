package com.zerotoship.z2term.proot

/**
 * `z2adb` — 端末自身の adb デーモン (Android のワイヤレスデバッグ) に繋ぐためのヘルパー。
 *
 * PC を繋がず、端末が **自分自身**の adb (TCP) に `localhost` で繋ぐ「セルフ adb」方式
 * (LADB 相当)。root 不要・USB 不要。前提は Android 11+ の開発者オプション →
 * 「ワイヤレスデバッグ」を ON にすること。
 *
 * 手順:
 *   1. `z2adb setup`               … adb クライアントを導入 (apk/apt/pacman を自動判定)
 *   2. `z2adb pair <port> [code]`  … 設定画面「デバイスのペア設定」のポート/6桁コードでペアリング
 *   3. `z2adb connect <port>`      … 設定画面「ワイヤレスデバッグ」直下のポートで接続
 *   4. `z2adb shell ...` 等         … 以降は通常の adb として自端末を操作
 *
 * `setup`/`pair`/`connect`/`status`/`help` 以外の語は **そのまま adb へ渡す** (passthrough)。
 * `pair`/`connect`/`status` は adb 未導入なら自動で導入を一度試みる。
 *
 * `pair`/`connect` の宛先は「ポート番号のみ」なら `Z2ADB_HOST` (既定 127.0.0.1) を補い、
 * `host:port` 形式ならそのまま使う。distro 非依存 (スクリプト内でパッケージマネージャを判定)。
 * launch 毎に上書きするので内容は常に最新。
 */
fun z2adbScript(lang: String = "ja"): String {
    val d = "${'$'}"  // シェルの $ (Kotlin テンプレートと衝突しないように)
    val en = lang == "en"

    val mNoPm = if (en)
        "z2adb: no supported package manager (apk/apt-get/pacman) found." else
        "z2adb: 対応パッケージマネージャ (apk/apt-get/pacman) が見つかりません。"
    val mInstalling = if (en)
        "z2adb: installing adb client (${d}ADB_PKG) via ${d}PM ..." else
        "z2adb: adb クライアント (${d}ADB_PKG) を ${d}PM で導入します ..."
    val mInstallFail = if (en)
        "z2adb: failed to install adb. Install it manually, then retry." else
        "z2adb: adb の導入に失敗しました。手動で導入してから再実行してください。"
    val mHaveAdb = if (en) "z2adb: adb is already installed." else "z2adb: adb は導入済みです。"
    val mNeedWireless = if (en)
        "z2adb: if pairing fails, enable Settings > Developer options > Wireless debugging (Android 11+)." else
        "z2adb: 失敗する場合は 設定 > 開発者向けオプション > ワイヤレスデバッグ を ON にしてください (Android 11+)。"
    val mPairPort = if (en) "Pairing port (Pair device with pairing code): " else
        "ペアリング用ポート (「ペアリングコードによるデバイスのペア設定」の表示): "
    val mConnPort = if (en) "Connect port (shown under Wireless debugging): " else
        "接続用ポート (「ワイヤレスデバッグ」直下に表示): "

    // 使い方テキスト (heredoc で素のまま出すので margin マーカーは付けない)。
    val usageText = if (en) """
        z2adb - connect to this device's own adb over Wireless debugging (self-adb, no PC).

        Prereq: enable Settings > Developer options > Wireless debugging (Android 11+).

          z2adb setup                install the adb client (apk/apt-get/pacman, auto-detected)
          z2adb pair  <port> [code]  pair (uses the pairing port + 6-digit code from Settings)
          z2adb connect <port>       connect (uses the port under 'Wireless debugging')
          z2adb status               show 'adb devices -l'
          z2adb <anything else>      passed straight to adb (shell, logcat, install, ...)

        Host defaults to 127.0.0.1; pass host:port to override (or set Z2ADB_HOST).
        Example:  z2adb pair 37115 123456  ->  z2adb connect 40123  ->  z2adb shell
    """.trimIndent() else """
        z2adb - 端末自身の adb にワイヤレスデバッグ経由で繋ぐ (セルフ adb・PC 不要)。

        前提: 設定 > 開発者向けオプション > ワイヤレスデバッグ を ON (Android 11+)。

          z2adb setup                adb クライアントを導入 (apk/apt-get/pacman を自動判定)
          z2adb pair  <port> [code]  ペアリング (設定の「ペア設定」ポート + 6桁コード)
          z2adb connect <port>       接続 (「ワイヤレスデバッグ」直下のポート)
          z2adb status               'adb devices -l' を表示
          z2adb <それ以外>           そのまま adb へ渡す (shell, logcat, install, ...)

        宛先は既定で 127.0.0.1。host:port を渡せば上書き可 (Z2ADB_HOST でも可)。
        例:  z2adb pair 37115 123456  →  z2adb connect 40123  →  z2adb shell
    """.trimIndent()

    val head = """
        |#!/bin/sh
        |# z2term: 端末自身の adb (ワイヤレスデバッグ) に繋ぐヘルパー (セルフ adb・PC 不要)。
        |# 使い方: z2adb {setup|pair <port> [code]|connect <port>|status|help} | <adb 引数...>
        |
        |HOST="${d}{Z2ADB_HOST:-127.0.0.1}"
        |
        |has() { command -v "${d}1" >/dev/null 2>&1; }
        |
        |# パッケージマネージャと adb パッケージ名を判定 (apk/apt/pacman で名前が違う)。
        |detect_pm() {
        |  if has apk; then PM=apk; ADB_PKG="android-tools"
        |  elif has apt-get; then PM=apt; ADB_PKG="adb"
        |  elif has pacman; then PM=pacman; ADB_PKG="android-tools"
        |  else PM=""; ADB_PKG=""; fi
        |}
        |
        |install_adb() {
        |  detect_pm
        |  if [ -z "${d}PM" ]; then echo "$mNoPm" >&2; return 1; fi
        |  echo "$mInstalling"
        |  case "${d}PM" in
        |    apk)    apk add --no-cache "${d}ADB_PKG" ;;
        |    apt)    apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y "${d}ADB_PKG" ;;
        |    pacman) pacman -Sy --noconfirm --needed "${d}ADB_PKG" ;;
        |  esac
        |}
        |
        |# adb が無ければ一度だけ導入を試みる。導入できなければ非ゼロで返す。
        |ensure_adb() {
        |  has adb && return 0
        |  install_adb
        |  if ! has adb; then echo "$mInstallFail" >&2; return 1; fi
        |}
        |
        |# "port" だけなら HOST を補い、"host:port" ならそのまま。
        |target() {
        |  case "${d}1" in
        |    *:*) echo "${d}1" ;;
        |    *)   echo "${d}HOST:${d}1" ;;
        |  esac
        |}
        |
        |cmd="${d}1"; [ ${d}# -gt 0 ] && shift
        |case "${d}cmd" in
        |  ""|help|-h|--help)
        |    cat <<'Z2ADB_USAGE'
    """.trimMargin()

    val tail = """
        |Z2ADB_USAGE
        |    ;;
        |  setup)
        |    if has adb; then echo "$mHaveAdb"; else install_adb; fi
        |    ;;
        |  pair)
        |    ensure_adb || exit 1
        |    port="${d}1"
        |    if [ -z "${d}port" ]; then printf '%s' "$mPairPort"; read port; fi
        |    [ -z "${d}port" ] && exit 1
        |    [ ${d}# -gt 0 ] && shift
        |    t=${d}(target "${d}port")
        |    echo "$mNeedWireless" >&2
        |    # 残り引数 (6桁コード) があれば adb へ渡す。無ければ adb が対話で訊く。
        |    exec adb pair "${d}t" "${d}@"
        |    ;;
        |  connect)
        |    ensure_adb || exit 1
        |    port="${d}1"
        |    if [ -z "${d}port" ]; then printf '%s' "$mConnPort"; read port; fi
        |    [ -z "${d}port" ] && exit 1
        |    t=${d}(target "${d}port")
        |    exec adb connect "${d}t"
        |    ;;
        |  status)
        |    ensure_adb || exit 1
        |    exec adb devices -l
        |    ;;
        |  *)
        |    # passthrough: 上記以外は素の adb として実行 (shell/logcat/install/push/pull...)。
        |    ensure_adb || exit 1
        |    exec adb "${d}cmd" "${d}@"
        |    ;;
        |esac
    """.trimMargin()

    return head + "\n" + usageText + "\n" + tail + "\n"
}
