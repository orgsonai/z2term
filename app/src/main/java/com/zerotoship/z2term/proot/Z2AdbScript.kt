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
    // 言語ごとの文言を選ぶ道具。3 言語目は t(en = …, ja = …) の後ろへ変わり値を足す ([CliText])。
    val t = CliText(lang)

    val mNoPm = t(
        en = "z2adb: no supported package manager (apk/apt-get/pacman) found.",
        ja = "z2adb: 対応パッケージマネージャ (apk/apt-get/pacman) が見つかりません。",
        "zh-CN" to "z2adb: 找不到支持的包管理器 (apk/apt-get/pacman)。",
        "zh-TW" to "z2adb: 找不到支援的套件管理器 (apk/apt-get/pacman)。"
    )
    val mInstalling = t(
        en = "z2adb: installing adb client (${d}ADB_PKG) via ${d}PM ...",
        ja = "z2adb: adb クライアント (${d}ADB_PKG) を ${d}PM で導入します ...",
        "zh-CN" to "z2adb: 正在用 ${d}PM 安装 adb 客户端 (${d}ADB_PKG) ...",
        "zh-TW" to "z2adb: 正在用 ${d}PM 安裝 adb 用戶端 (${d}ADB_PKG) ..."
    )
    val mInstallFail = t(
        en = "z2adb: failed to install adb. Install it manually, then retry.",
        ja = "z2adb: adb の導入に失敗しました。手動で導入してから再実行してください。",
        "zh-CN" to "z2adb: adb 安装失败。请手动安装后再试。",
        "zh-TW" to "z2adb: adb 安裝失敗。請手動安裝後再試。"
    )
    val mHaveAdb = t(
        en = "z2adb: adb is already installed.",
        ja = "z2adb: adb は導入済みです。",
        "zh-CN" to "z2adb: adb 已经安装过了。",
        "zh-TW" to "z2adb: adb 已經安裝過了。"
    )
    val mNeedWireless = t(
        en = "z2adb: if pairing fails, enable Settings > Developer options > Wireless debugging (Android 11+).",
        ja = "z2adb: 失敗する場合は 設定 > 開発者向けオプション > ワイヤレスデバッグ を ON にしてください (Android 11+)。",
        "zh-CN" to "z2adb: 如果失败，请打开 设置 > 开发者选项 > 无线调试 (Android 11+)。",
        "zh-TW" to "z2adb: 如果失敗，請開啟 設定 > 開發者選項 > 無線偵錯 (Android 11+)。"
    )
    val mPairPort = t(
        en = "Pairing port (Pair device with pairing code): ",
        ja = "ペアリング用ポート (「ペアリングコードによるデバイスのペア設定」の表示): ",
        "zh-CN" to "配对用端口 (“使用配对码配对设备”处显示的): ",
        "zh-TW" to "配對用連接埠 (“使用配對碼配對裝置”處顯示的): "
    )
    val mConnPort = t(
        en = "Connect port (shown under Wireless debugging): ",
        ja = "接続用ポート (「ワイヤレスデバッグ」直下に表示): ",
        "zh-CN" to "连接用端口 (显示在“无线调试”正下方): ",
        "zh-TW" to "連線用連接埠 (顯示在“無線偵錯”正下方): "
    )

    // 使い方テキスト (heredoc で素のまま出すので margin マーカーは付けない)。
    val usageText = t(
        en = """
        z2adb - connect to this device's own adb over Wireless debugging (self-adb, no PC).

        Prereq: enable Settings > Developer options > Wireless debugging (Android 11+).

          z2adb setup                install the adb client (apk/apt-get/pacman, auto-detected)
          z2adb pair  <port> [code]  pair (uses the pairing port + 6-digit code from Settings)
          z2adb connect <port>       connect (uses the port under 'Wireless debugging')
          z2adb status               show 'adb devices -l'
          z2adb <anything else>      passed straight to adb (shell, logcat, install, ...)

        Host defaults to 127.0.0.1; pass host:port to override (or set Z2ADB_HOST).
        Example:  z2adb pair 37115 123456  ->  z2adb connect 40123  ->  z2adb shell
    """.trimIndent(),
        ja = """
        z2adb - 端末自身の adb にワイヤレスデバッグ経由で繋ぐ (セルフ adb・PC 不要)。

        前提: 設定 > 開発者向けオプション > ワイヤレスデバッグ を ON (Android 11+)。

          z2adb setup                adb クライアントを導入 (apk/apt-get/pacman を自動判定)
          z2adb pair  <port> [code]  ペアリング (設定の「ペア設定」ポート + 6桁コード)
          z2adb connect <port>       接続 (「ワイヤレスデバッグ」直下のポート)
          z2adb status               'adb devices -l' を表示
          z2adb <それ以外>           そのまま adb へ渡す (shell, logcat, install, ...)

        宛先は既定で 127.0.0.1。host:port を渡せば上書き可 (Z2ADB_HOST でも可)。
        例:  z2adb pair 37115 123456  →  z2adb connect 40123  →  z2adb shell
    """.trimIndent(),
        "zh-CN" to """
        z2adb - 通过无线调试连接本机自己的 adb (自连 adb，不需要电脑)。

        前提: 打开 设置 > 开发者选项 > 无线调试 (Android 11+)。

          z2adb setup                安装 adb 客户端 (自动判断 apk/apt-get/pacman)
          z2adb pair  <port> [code]  配对 (用设置里的配对端口 + 6 位配对码)
          z2adb connect <port>       连接 (用“无线调试”正下方的端口)
          z2adb status               显示 'adb devices -l'
          z2adb <其他任何内容>       原样交给 adb (shell, logcat, install, ...)

        目标默认是 127.0.0.1。传 host:port 可以覆盖 (也可以用 Z2ADB_HOST)。
        例:  z2adb pair 37115 123456  →  z2adb connect 40123  →  z2adb shell
    """.trimIndent(),
        "zh-TW" to """
        z2adb - 透過無線偵錯連線本機自己的 adb (自連 adb，不需要電腦)。

        前提: 開啟 設定 > 開發者選項 > 無線偵錯 (Android 11+)。

          z2adb setup                安裝 adb 用戶端 (自動判斷 apk/apt-get/pacman)
          z2adb pair  <port> [code]  配對 (用設定裡的配對連接埠 + 6 位配對碼)
          z2adb connect <port>       連線 (用“無線偵錯”正下方的連接埠)
          z2adb status               顯示 'adb devices -l'
          z2adb <其他任何內容>       原樣交給 adb (shell, logcat, install, ...)

        目標預設是 127.0.0.1。傳 host:port 可以覆寫 (也可以用 Z2ADB_HOST)。
        例:  z2adb pair 37115 123456  →  z2adb connect 40123  →  z2adb shell
    """.trimIndent()
    )

    val head = """
        |#!/bin/sh
        |# z2term: 端末自身の adb (ワイヤレスデバッグ) に繋ぐヘルパー (セルフ adb・PC 不要)。
        |# 使い方: z2adb {setup|pair <port> [code]|connect <port>|status|help} | <adb 引数...>
        |
        |HOST="${d}{Z2ADB_HOST:-127.0.0.1}"
        |
        |has() { command -v "${d}1" >/dev/null 2>&1; }
        |
        |# adb サーバが TCP LISTEN しているかを adb クライアントを起動せずに判定する
        |# (port は /proc/net/tcp* 上は16進・state 0A=LISTEN)。
        |server_up() {
        |  hex=${d}(printf '%04X' "${d}{1:-5037}")
        |  grep -qE ":${d}hex [0-9A-F]+:[0-9A-F]+ 0A" /proc/net/tcp /proc/net/tcp6 2>/dev/null
        |}
        |
        |# adb サーバを先に立てる。z2root は /proc/self/exe を APK 内 .so として返すため、
        |# adb クライアントが daemon を自己 exec (execl 自パス) しようとすると ENOENT で失敗する。
        |# 自己 exec しない `nodaemon server` を background で立てておけば、以降のクライアントは
        |# fork せず既存サーバに接続できる (LADB 相当のセルフ adb が z2root/proot で成立する)。
        |start_server() {
        |  port="${d}{ADB_SERVER_SOCKET##*:}"
        |  case "${d}port" in ''|*[!0-9]*) port=5037 ;; esac
        |  server_up "${d}port" && return 0
        |  (adb -L "tcp:${d}port" nodaemon server >/dev/null 2>&1 &)
        |  i=0
        |  while [ "${d}i" -lt 30 ]; do server_up "${d}port" && return 0; sleep 0.2; i=${d}((i+1)); done
        |  return 0
        |}
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
        |# adb が無ければ一度だけ導入を試み、その後サーバを先行起動する。
        |ensure_adb() {
        |  if ! has adb; then
        |    install_adb
        |    if ! has adb; then echo "$mInstallFail" >&2; return 1; fi
        |  fi
        |  start_server
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
