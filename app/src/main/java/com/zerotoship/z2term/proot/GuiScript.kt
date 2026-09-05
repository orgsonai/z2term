package com.zerotoship.z2term.proot

import com.zerotoship.z2term.settings.AppLanguages

/**
 * z2term 内蔵 VNC クライアントが接続する RFB(VNC) ポート。
 * Xvnc は `:1` で起動するので RFB ポートは 5900 + 1 = 5901。
 * **127.0.0.1 のみで待ち受け**、外部 NIC には bind しない (loopback 限定で安全)。
 */
const val Z2TERM_VNC_PORT = 5901

/** 仮想 X ディスプレイ番号 (`Xvnc :1`)。 */
const val Z2TERM_VNC_DISPLAY = 1

/** （参考）Alpine の GUI パッケージ。実際の導入は [z2guiScript] が distro 判定して切替える。 */
const val Z2TERM_GUI_PACKAGES =
    "tigervnc openbox dbus bash gsettings-desktop-schemas xdg-desktop-portal xdg-desktop-portal-gtk font-misc-misc font-alias font-noto ttf-dejavu"

/** Alpine の gThumb が起動時に必ず読む schema。Android 側のダウンロード確認にも使う。 */
const val Z2TERM_ALPINE_DESKTOP_SCHEMA =
    "usr/share/glib-2.0/schemas/org.gnome.desktop.background.gschema.xml"

/**
 * Linux GUI ランチャ `z2gui` スクリプト。
 *
 * PRoot は root もハードウェアフレームバッファも使えないため、**仮想 X サーバ Xvnc**
 * を立て、その画面を VNC(RFB) で z2term の内蔵クライアントへ転送する構成にする
 * (詳細は 99_private/HANDOFF/z2term/M8-GUI-HANDOFF.md)。
 *
 * これを `/usr/local/bin/z2gui` に配置 (ProotLauncher.ensureGuiScript) することで、
 * 端末から、または z2term の GUI セッションから次のように使える:
 *  - `z2gui` / `z2gui start [WxH]` … Xvnc + openbox を起動 (未導入なら自動導入)
 *  - `z2gui start [WxH] clean`     … キャッシュごと GUI を入れ直して起動 (救済)
 *  - `z2gui 1080x2160`             … 解像度を直接指定して起動
 *  - `z2gui stop`                  … Xvnc/WM を停止
 *  - `z2gui status`               … 起動状態を表示
 *  - `z2gui install`              … GUI 一式を導入するだけ
 *  - `z2gui clean`                … GUI 一式をキャッシュごと入れ直すだけ
 *
 * **distro 非依存**: スクリプト内でパッケージマネージャ (apk / apt-get / pacman) を判定し、
 * その distro のパッケージ名・X サーバ名 (Xvnc または Xtigervnc) を使う。選択中の OS で
 * そのまま GUI が立ち上がる（HANDOFF「選択中のOSで立ち上げ」要望）。
 *
 * RFB は `-SecurityTypes None -localhost` で認証なし・loopback 限定。z2term は
 * 127.0.0.1:[Z2TERM_VNC_PORT] へ接続する。
 */
/**
 * z2gui スクリプトが echo する各種メッセージ。アプリ内言語スイッチに追従させるため、
 * 生成時 ([z2guiScript]) に外から差し込む。English モードでは英語版が埋め込まれる。
 */
data class GuiScriptStrings(
    val installing: String,           // "📦 GUI 一式を導入します ({PM}): {PKGS}"
    val cleanInstalling: String,      // "🧹 GUI をクリーンインストールします ({PM})..."
    val noPackageManager: String,     // "❌ 対応パッケージマネージャ ... が見つかりません"
    val invalidGeometry: String,      // "❌ 解像度の形式が不正: '{GEOM}' (例 1280x720)"
    val noXvnc: String,               // "❌ Xvnc/Xtigervnc がありません ..."
    val alreadyRunning: String,       // "✅ GUI は既に起動中 (DISPLAY={DISP}, RFB ...)"
    val startingXvnc: String,         // "▶ {XSERVER} 起動: {GEOM} @ {DISP} ..."
    val xvncFailed: String,           // "❌ Xvnc 起動失敗。ログ:"
    val ready: String,                // "✅ GUI 準備完了。z2term の GUI タブから ..."
    val running: String,              // "✅ GUI 起動中 (DISPLAY=..., RFB ...)"
    val stopped: String,              // "⏹ GUI は停止中"
    val stoppedMsg: String,           // "⏹ 停止しました"
    val usage: String,                 // "使い方: z2gui [start [WxH] [clean] | stop | status | ...]"
    val installFailed: String,        // GUI 一式を導入できなかった
    val installFailedHint: String,    // その次の一手
    val audioInstalling: String,      // PulseAudio を導入します
    val audioNoPulse: String,         // pulseaudio が無いので無音で継続
    val audioNoPactl: String,         // pactl が無いので無音で継続
    val audioStartFailed: String,     // PulseAudio 起動失敗
    val audioReady: String,           // 音声の経路ができた
    // --- 0.8.498 で追加。デスクトップ右クリックのメニューに出す見出し。
    // ⚠ ここは**画面に出るラベル**なので、必ず全言語を埋めること (英語落ちだと和文の中に英語が混じる)。
    val menuApps: String,             // 「アプリ」(z2menu の pipe menu を開く)
    val menuWindows: String,          // 「窓」(openbox 内蔵の client-list-menu)
    val menuReload: String            // 「メニューを読み直す」(openbox の Reconfigure)
) {
    companion object {
        fun ja(): GuiScriptStrings = GuiScriptStrings(
            installing = "📦 GUI 一式を導入します",
            cleanInstalling = "🧹 GUI をクリーンインストールします",
            noPackageManager = "❌ 対応パッケージマネージャ (apk/apt-get/pacman) が見つかりません。",
            invalidGeometry = "❌ 解像度の形式が不正",
            noXvnc = "❌ Xvnc/Xtigervnc がありません (tigervnc 未導入)",
            alreadyRunning = "✅ GUI は既に起動中",
            startingXvnc = "▶ 起動",
            xvncFailed = "❌ Xvnc 起動失敗。ログ:",
            ready = "✅ GUI 準備完了。z2term の GUI タブから接続してください。",
            running = "✅ GUI 起動中",
            stopped = "⏹ GUI は停止中",
            stoppedMsg = "⏹ 停止しました",
            usage = "使い方: z2gui [start [WxH] [clean] | stop | status | install | clean | check]",
            installFailed = "❌ GUI 一式 (Xvnc / openbox / D-Bus) を導入できませんでした。",
            installFailedHint = "   ネットワーク接続を確認してください。復旧する場合は端末で z2gui clean を実行できます。",
            audioInstalling = "🔊 GUI 音声: PulseAudio を導入します",
            audioNoPulse = "⚠️ GUI 音声: pulseaudio が無いため無音で継続",
            audioNoPactl = "⚠️ GUI 音声: pactl が無いため無音で継続",
            audioStartFailed = "⚠️ GUI 音声: PulseAudio 起動失敗",
            audioReady = "🔊 GUI 音声: z2sink.monitor →",
            menuApps = "アプリ",
            menuWindows = "窓",
            menuReload = "メニューを読み直す"
        )
        fun en(): GuiScriptStrings = GuiScriptStrings(
            installing = "📦 Installing GUI stack",
            cleanInstalling = "🧹 Clean-installing the GUI stack",
            noPackageManager = "❌ No supported package manager (apk/apt-get/pacman) found.",
            invalidGeometry = "❌ Invalid geometry",
            noXvnc = "❌ Xvnc/Xtigervnc not present (tigervnc is not installed)",
            alreadyRunning = "✅ GUI already running",
            startingXvnc = "▶ Starting",
            xvncFailed = "❌ Xvnc startup failed. Log:",
            ready = "✅ GUI ready. Connect from a z2term GUI tab.",
            running = "✅ GUI running",
            stopped = "⏹ GUI is stopped",
            stoppedMsg = "⏹ Stopped",
            usage = "Usage: z2gui [start [WxH] [clean] | stop | status | install | clean | check]",
            installFailed = "❌ Could not install the GUI stack (Xvnc / openbox / D-Bus).",
            installFailedHint = "   Check your network. For recovery, you can run z2gui clean in a terminal.",
            audioInstalling = "🔊 GUI audio: installing PulseAudio",
            audioNoPulse = "⚠️ GUI audio: no pulseaudio, continuing without sound",
            audioNoPactl = "⚠️ GUI audio: no pactl, continuing without sound",
            audioStartFailed = "⚠️ GUI audio: PulseAudio failed to start",
            audioReady = "🔊 GUI audio: z2sink.monitor →",
            menuApps = "Applications",
            menuWindows = "Windows",
            menuReload = "Reload menu"
        )
        fun zhCN(): GuiScriptStrings = GuiScriptStrings(
            installing = "📦 正在安装整套图形环境",
            cleanInstalling = "🧹 正在全新安装图形环境",
            noPackageManager = "❌ 找不到支持的包管理器 (apk/apt-get/pacman)。",
            invalidGeometry = "❌ 分辨率的写法不正确",
            noXvnc = "❌ 没有 Xvnc/Xtigervnc (tigervnc 未安装)",
            alreadyRunning = "✅ 图形环境已经在运行",
            startingXvnc = "▶ 启动",
            xvncFailed = "❌ Xvnc 启动失败。日志:",
            ready = "✅ 图形环境准备完成。请从 z2term 的图形标签页连接。",
            running = "✅ 图形环境运行中",
            stopped = "⏹ 图形环境已停止",
            stoppedMsg = "⏹ 已停止",
            usage = "用法: z2gui [start [WxH] [clean] | stop | status | install | clean | check]",
            installFailed = "❌ 没能安装整套图形环境 (Xvnc / openbox / D-Bus)。",
            installFailedHint = "   请确认网络连接。需要恢复时，可在终端中运行 z2gui clean。",
            audioInstalling = "🔊 图形界面声音: 正在安装 PulseAudio",
            audioNoPulse = "⚠️ 图形界面声音: 没有 pulseaudio，将以无声继续",
            audioNoPactl = "⚠️ 图形界面声音: 没有 pactl，将以无声继续",
            audioStartFailed = "⚠️ 图形界面声音: PulseAudio 启动失败",
            audioReady = "🔊 图形界面声音: z2sink.monitor →",
            menuApps = "应用",
            menuWindows = "窗口",
            menuReload = "重新载入菜单"
        )
        fun zhTW(): GuiScriptStrings = GuiScriptStrings(
            installing = "📦 正在安裝整套圖形環境",
            cleanInstalling = "🧹 正在全新安裝圖形環境",
            noPackageManager = "❌ 找不到支援的套件管理器 (apk/apt-get/pacman)。",
            invalidGeometry = "❌ 解析度的寫法不正確",
            noXvnc = "❌ 沒有 Xvnc/Xtigervnc (tigervnc 未安裝)",
            alreadyRunning = "✅ 圖形環境已經在執行",
            startingXvnc = "▶ 啟動",
            xvncFailed = "❌ Xvnc 啟動失敗。日誌:",
            ready = "✅ 圖形環境準備完成。請從 z2term 的圖形分頁連線。",
            running = "✅ 圖形環境執行中",
            stopped = "⏹ 圖形環境已停止",
            stoppedMsg = "⏹ 已停止",
            usage = "用法: z2gui [start [WxH] [clean] | stop | status | install | clean | check]",
            installFailed = "❌ 沒能安裝整套圖形環境 (Xvnc / openbox / D-Bus)。",
            installFailedHint = "   請確認網路連線。需要還原時，可在終端中執行 z2gui clean。",
            audioInstalling = "🔊 圖形介面聲音: 正在安裝 PulseAudio",
            audioNoPulse = "⚠️ 圖形介面聲音: 沒有 pulseaudio，將以無聲繼續",
            audioNoPactl = "⚠️ 圖形介面聲音: 沒有 pactl，將以無聲繼續",
            audioStartFailed = "⚠️ 圖形介面聲音: PulseAudio 啟動失敗",
            audioReady = "🔊 圖形介面聲音: z2sink.monitor →",
            menuApps = "應用程式",
            menuWindows = "視窗",
            menuReload = "重新載入選單"
        )
        /**
         * 言語ごとの組。⭐ **3 言語目はここに 1 行足す** (言語コード to その組を返す関数)。
         * 名簿 ([AppLanguages]) にあっても訳が無い言語は英語へ落ちる。
         */
        private val byLang: Map<String, () -> GuiScriptStrings> = mapOf(
            "en" to ::en,
            "ja" to ::ja,
            "zh-CN" to ::zhCN,
            "zh-TW" to ::zhTW,
        )

        /** ⚠ **知らない言語は英語**。「英語でなければ日本語」と書かないこと。 */
        fun forLang(lang: String): GuiScriptStrings =
            (byLang[AppLanguages.resolve(lang)] ?: byLang.getValue(AppLanguages.FALLBACK))()
    }
}

fun z2guiScript(
    rfbPort: Int = Z2TERM_VNC_PORT,
    display: Int = Z2TERM_VNC_DISPLAY,
    defaultGeometry: String = "1280x720",
    strings: GuiScriptStrings = GuiScriptStrings.ja()
): String {
    val d = "${'$'}"  // シェルの $ (Kotlin テンプレートと衝突しないように)
    return """
        |#!/bin/sh
        |# z2term: Linux GUI ランチャ (Xvnc + openbox)。distro 非依存。
        |# RFB は 127.0.0.1 のみで待ち受け (z2term 内蔵クライアントが接続)。外部公開しない。
        |#   使い方: z2gui [start [WxH] | stop | status | install | check]
        |# ディスプレイ番号と RFB ポートは z2term が環境変数 Z2_DISPLAY / Z2_RFBPORT で渡す
        |# (GUI タブごとに :1/5901, :2/5902 … と別ポートで並走するため)。未指定なら既定値。
        |DISPLAY_NUM="${d}{Z2_DISPLAY:-$display}"
        |DISP=":${d}DISPLAY_NUM"
        |RFBPORT="${d}{Z2_RFBPORT:-$rfbPort}"
        |DEFAULT_GEOM="$defaultGeometry"
        |# DISPLAY は start_x の中だけで export する。ここで全体に export すると `z2gui stop` の
        |# プロセス自身が DISPLAY=:N を持ち、stop_x のディスプレイ単位 kill が自分を巻き込む。
        |export HOME="${d}{HOME:-/root}"
        |# openbox以下のGUI子プロセスだけにglycin互換入口を見せる。通常の端末PATHは変えない。
        |export PATH="$Z2TERM_GUI_COMPAT_DIR:${d}{PATH:-/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin}"
        |
        |# GUI アプリが ${d}SHELL を参照する場合に備え、実体のログインシェルを渡す。
        |# ProotLauncher が OS の /etc/passwd から解決したシェルを最優先で採用する。
        |for _sh in "${d}{Z2_LOGIN_SHELL:-}" /bin/bash /bin/ash /bin/sh; do
        |  [ -n "${d}_sh" ] && [ -x "${d}_sh" ] && { SHELL="${d}_sh"; break; }
        |done
        |export SHELL
        |
        |# accept(2) の橋渡しシムを必ず立てる (0.8.347)。
        |# Android の untrusted_app seccomp は accept(202) を禁じている (bionic は accept4 しか
        |# 使わない) ため、X サーバの accept は SIGSYS で弾かれ ENOSYS になる。X サーバは
        |# **listen fd が readable な限り accept をやり直す**作りなので、これに当たると
        |# **接続を 1 つも受け付けないまま CPU を焼き続ける**: 実測で 30 秒に 23 万回の
        |# `_XSERVTransSocketUNIXAccept: accept() failed`、CPU 40〜45%、端末の窓は一生出ない。
        |# エンジンは LD_PRELOAD でこのシムを渡しているが、**環境変数を作り直す経路
        |# (ssh のログインシェル経由など) では落ちる**。GUI を起こすのはここだけなので、
        |# 渡ってこなかったときは自分で立てる (既にあれば触らない)。
        |Z2_ACCEPT_SHIM=/usr/local/lib/libz2accept.so
        |if [ -f "${d}Z2_ACCEPT_SHIM" ]; then
        |  case ":${d}{LD_PRELOAD:-}:" in
        |    *":${d}Z2_ACCEPT_SHIM:"*) ;;
        |    *) LD_PRELOAD="${d}Z2_ACCEPT_SHIM${d}{LD_PRELOAD:+:${d}LD_PRELOAD}"; export LD_PRELOAD ;;
        |  esac
        |fi
        |
        |has() { command -v "${d}1" >/dev/null 2>&1; }
        |
        |# X サーバの実体名を解決 (TigerVNC は distro により Xvnc または Xtigervnc)。
        |xbin() { for b in Xvnc Xtigervnc; do has "${d}b" && { echo "${d}b"; return 0; }; done; return 1; }
        |
        |# パッケージマネージャと、その distro のサーバ/WM/D-Bus/シェル/フォントのパッケージ名を決める。
        |PM=""; INSTALL=""; SRV_PKGS=""
        |# フォント: コア(ビットマップ)フォントパッケージも入れておく (xterm 以外のコアフォント
        |# 利用アプリ向けの保険)。ただし xterm 自体は下の start_x で Xft(-fa) を使い、コアフォント
        |# 'fixed' 依存を回避する (distro により misc-fixed の Unicode 版が無く起動失敗するため)。
        |detect_pm() {
        |  if has apk; then
        |    # ⚠ **コアフォント (font-misc-misc + エイリアス font-alias) を必ず入れる** (0.8.343)。
        |    # apt の xfonts-base / pacman の xorg-fonts-misc に当たるものが apk だけ抜けていて、
        |    # **コアフォント 'fixed' を既定で使う端末 (urxvt 等) が Alpine で起動できなかった**
        |    # (パッケージは入るので `has urxvt` は true → GUI は立つのに窓だけ出ない、という
        |    # 一番分かりにくい形で出る)。TrueType (font-noto / ttf-dejavu) では代わりにならない。
        |    # gsettings-desktop-schemas: Alpine の gthumb は org.gnome.desktop.background を
        |    # 参照するのにこの package を依存へ含めない。無いと一覧には出るのに起動直後
        |    # GLib-GIO-ERROR で停止するため、Alpine の GUI 土台に含める。
        |    # bash: Alpine の最小 rootfs には無いが、GUI パッケージの .desktop が指す
        |    # ラッパーには `#!/usr/bin/env bash` のものがある。実行ファイルだけ存在しても
        |    # interpreter 不在なら exit 127 で窓が出ないため、GUI ランタイムとして保証する。
        |    PM=apk;    SRV_PKGS="tigervnc openbox dbus bash gsettings-desktop-schemas xdg-desktop-portal xdg-desktop-portal-gtk font-misc-misc font-alias font-noto ttf-dejavu"
        |  elif has apt-get; then
        |    PM=apt;    SRV_PKGS="tigervnc-standalone-server openbox dbus xdg-desktop-portal xdg-desktop-portal-gtk xfonts-base fonts-noto-core fonts-dejavu"
        |  elif has pacman; then
        |    PM=pacman; SRV_PKGS="tigervnc openbox dbus xdg-desktop-portal xdg-desktop-portal-gtk xorg-fonts-misc noto-fonts ttf-dejavu"
        |  else
        |    PM=""
        |  fi
        |}
        |
        |# パッケージマネージャの stale ロックを除去する。前回の導入が途中で失敗 (ネット切れ等) すると
        |# pacman の db.lck / apt の各 lock が残り、以降の導入が「unable to lock database」等で
        |# 永久に失敗する。proot は単一ユーザーで同時実行も無いので、残ったロックは安全に消してよい。
        |# (これが無いと konsole 等の大物が一度失敗すると二度と入れ直せなくなる。)
        |clear_pm_locks() {
        |  rm -f /var/lib/pacman/db.lck 2>/dev/null
        |  rm -f /var/lib/dpkg/lock /var/lib/dpkg/lock-frontend \
        |        /var/cache/apt/archives/lock /var/lib/apt/lists/lock 2>/dev/null
        |  return 0
        |}
        |
        |# pacman の鍵束を用意する (0.8.316)。Arch の rootfs は /etc/pacman.d/gnupg を持たずに
        |# 来るのに SigLevel=Required なので、初期化しないと **どのパッケージも入らない**
        |# (「error: required key missing from keyring」で毎回ここで止まる)。端末タブの起動時にも
        |# 流しているが、GUI から先に始めた人はまだ通っていないのでここでも呼ぶ。冪等。
        |ensure_keyring() {
        |  [ -x /usr/local/bin/z2-pacman-keyring ] || return 0
        |  /usr/local/bin/z2-pacman-keyring || return 0
        |}
        |
        |install_pkgs() {
        |  detect_pm
        |  clear_pm_locks
        |  ensure_keyring
        |  echo "${strings.installing} (${d}PM): ${d}SRV_PKGS"
        |  case "${d}PM" in
        |    apk)    apk update && apk add --no-cache ${d}SRV_PKGS ;;
        |    apt)    apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y ${d}SRV_PKGS ;;
        |    pacman) pacman -Sy --noconfirm ${d}SRV_PKGS ;;
        |    *) echo "${strings.noPackageManager}"; return 1 ;;
        |  esac
        |}
        |
        |# クリーンインストール: パッケージマネージャのキャッシュを消してから取り直し、
        |# GUI 一式を強制的に入れ直す。ダウンロード/解凍が途中で失敗して壊れた状態
        |# (毎回同じ所で失敗する等) からの復旧用 (端末側のクリーンインストールと同思想)。
        |clean_pkgs() {
        |  detect_pm
        |  clear_pm_locks
        |  ensure_keyring
        |  PKGS="${d}SRV_PKGS"
        |  echo "${strings.cleanInstalling} (${d}PM)"
        |  case "${d}PM" in
        |    apk)
        |      rm -rf /var/cache/apk/* 2>/dev/null
        |      apk update && apk add --no-cache ${d}PKGS && apk fix ${d}PKGS 2>/dev/null ;;
        |    apt)
        |      apt-get clean
        |      rm -rf /var/lib/apt/lists/* 2>/dev/null
        |      dpkg --configure -a 2>/dev/null
        |      apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --reinstall ${d}PKGS ;;
        |    pacman)
        |      rm -rf /var/cache/pacman/pkg/* 2>/dev/null
        |      pacman -Syy --noconfirm && pacman -S --noconfirm ${d}PKGS ;;
        |    *) echo "${strings.noPackageManager}"; return 1 ;;
        |  esac
        |}
        |
        |gui_stack_ready() {
        |  xbin >/dev/null 2>&1 && has openbox && has dbus-daemon || return 1
        |  # KDE/GTK/Qt共通のDesktop Portal。ファイル選択・テーマ・URI連携を提供するだけでなく、
        |  # KDE Frameworks 6.8以前はSettings portal不在時の空DBus応答を読んでNULL参照するため必須。
        |  [ -x /usr/libexec/xdg-desktop-portal ] || [ -x /usr/lib/xdg-desktop-portal ] || return 1
        |  [ -x /usr/libexec/xdg-desktop-portal-gtk ] || [ -x /usr/lib/xdg-desktop-portal-gtk ] || return 1
        |  # 既に GUI 基盤を入れてある Alpine にも、後から追加した必須ランタイムを補う。
        |  if [ "${d}PM" = "apk" ]; then
        |    # `.desktop` が指すスクリプトの interpreter。PATH に実体があることを直接見る。
        |    has bash || return 1
        |    # schema は `has` では調べられない data package なので apk の登録情報を見る。
        |    apk info -e gsettings-desktop-schemas >/dev/null 2>&1 || return 1
        |  fi
        |  return 0
        |}
        |
        |ensure_pkgs() {
        |  detect_pm
        |  # 基本セット (Xvnc + openbox + D-Bus) が揃っていれば **ネットワークを叩かず** 即 return する
        |  # (通常起動の高速パス。導入済みを毎回 update / 再取得しないユーザーポリシー)。
        |  if gui_stack_ready; then return 0; fi
        |  # 未導入の基盤だけを通常インストールで取得する。
        |  # app 側のダウンロード確認ゲート (設定 ON 時) で同意済みなので、ここで取得してよい。clean 指定の
        |  # ように cache を消さず、不足分だけを apk add / apt install / pacman -S で足す。
        |  install_pkgs
        |  # 取得後に再判定。まだ揃っていなければ (ネット無し / PM 無し / 取得失敗) 明確に案内して失敗する。
        |  detect_pm
        |  if gui_stack_ready; then
        |    return 0
        |  fi
        |  echo "${strings.installFailed} (Xvnc / openbox / dbus-daemon / desktop schemas)"
        |  echo "${strings.installFailedHint}"
        |  return 1
        |}
        |
        |
        |# GUI 一式 (Xvnc + openbox + D-Bus) が導入済みかを判定し、app が事前にダウンロード確認を
        |# 出せるよう "GUI_INSTALLED" / "GUI_MISSING" を 1 行で出す (M8-6 T7)。
        |check_pkgs() {
        |  detect_pm
        |  if gui_stack_ready; then
        |    echo "GUI_INSTALLED"
        |  else
        |    echo "GUI_MISSING"
        |  fi
        |}
        |
        |x_running() { [ -e "/tmp/.X11-unix/X${d}{DISPLAY_NUM}" ]; }
        |
        |# このディスプレイで起動したプロセス (Xvnc/WM/D-Bus) の PID を控えるファイル。
        |# 複数 GUI 並走時に「他ディスプレイのプロセスを巻き込まず :N だけ」止めるために使う。
        |PIDFILE="/tmp/z2gui-${d}{DISPLAY_NUM}.pids"
        |DBUS_PIDFILE="/tmp/z2gui-dbus-${d}{DISPLAY_NUM}.pid"
        |
        |# X サーバ (:N) の PID。X は起動時に /tmp/.X<N>-lock へ自分の PID を空白詰めで書くので、
        |# それを数字だけ取り出して使う (cmdline/environ の NUL 解析が要らず最小 rootfs でも堅い)。
        |x_pid() {
        |  [ -r "/tmp/.X${d}{DISPLAY_NUM}-lock" ] || return 1
        |  tr -dc '0-9' < "/tmp/.X${d}{DISPLAY_NUM}-lock" 2>/dev/null
        |}
        |
        |# このディスプレイの Xvnc が実際に生きているか (stale ソケットだけの状態と区別する)。
        |# lock の PID がこの画面のプロセスとして生存していれば true。他ディスプレイは誤検知しない。
        |# ⚠ 判定は is_gui_proc に任せる (z2root では comm が実体名にならないため。下の注意書き)。
        |x_alive() {
        |  p=${d}(x_pid); [ -n "${d}p" ] || return 1
        |  is_gui_proc "${d}p"
        |}
        |
        |# このディスプレイ (:N) だけを停止する。他の GUI タブ (:他) は一切触らない。
        |# kill 前に必ず comm を確認し、PID 再利用で無関係プロセスを殺さないようにする。
        |is_gui_proc() {  # ${d}1=pid : 既知の GUI プロセス種別なら 0
        |  [ -r "/proc/${d}1/comm" ] || return 1
        |  case "${d}(cat "/proc/${d}1/comm" 2>/dev/null)" in
        |    # ⚠ dbus-daemon を必ず含める (0.8.498)。PIDFILE には前から控えていたのに
        |    #    ここに無かったので stop で殺されず、次の起動が**死んだ前回のバス**を
        |    #    掴んで KDE/GTK アプリが固まっていた。
        |    Xvnc|Xtigervnc|openbox|xterm|urxvt|lxterminal|konsole|dbus-daemon) return 0 ;;
        |    # ⛔ **z2root エンジンではゲストの comm が全部 `libz2root.so` になる** (実体名は出ない)。
        |    #    名前で見分けられないので、environ の DISPLAY=:N で「この画面のプロセス」だけを拾う。
        |    #    これが無いと is_gui_proc は**必ず false** になり、`z2gui stop` は 1 つも kill できず
        |    #    Xvnc が残る。残った Xvnc はディスプレイを掴んだままなので、次の起動が
        |    #    "Cannot establish any listening sockets" で落ちる (= GUI が二度と開かない)。
        |    #    ⚠ 実測: z2root 配下の Xvnc は comm=libz2root.so / environ に DISPLAY=:N を持つ。
        |    libz2root.so|z2root)
        |      tr '\0' '\n' < "/proc/${d}1/environ" 2>/dev/null | grep -qx "DISPLAY=${d}DISP" && return 0 ;;
        |  esac
        |  return 1
        |}
        |stop_x() {
        |  if has vncserver; then vncserver -kill "${d}DISP" >/dev/null 2>&1; fi
        |  # 1) start_x が控えた :N の既知プロセス (Xvnc/WM/D-Bus) を pidfile から停止。
        |  #    pidfile は :N 専用なので他ディスプレイは巻き込まない。comm 確認で PID 再利用も安全。
        |  if [ -r "${d}PIDFILE" ]; then
        |    while read p; do
        |      [ -n "${d}p" ] || continue
        |      is_gui_proc "${d}p" && kill "${d}p" 2>/dev/null
        |    done < "${d}PIDFILE"
        |  fi
        |  # 2) 念のため X ロックの PID (= Xvnc 本体) も止める。Xvnc が死ねば配下の
        |  #    openbox/GUI アプリは X 切断で自動終了する (取りこぼしの保険)。
        |  xp=${d}(x_pid); [ -n "${d}xp" ] && is_gui_proc "${d}xp" && kill "${d}xp" 2>/dev/null
        |  # GUI 音声を立てていれば (この :N 専用 PA を) 一緒に止める。立てていなければ no-op。
        |  stop_audio
        |  # セッションバスの控え (z2run が読む) も消す。残すと次回に死んだアドレスを掴ませてしまう。
        |  rm -f "/tmp/z2gui-xdg-${d}{DISPLAY_NUM}/dbus-address" "/tmp/z2gui-xdg-${d}{DISPLAY_NUM}/dbus.sock" 2>/dev/null
        |  rm -f "${d}DBUS_PIDFILE" "${d}PIDFILE" "/tmp/.X${d}{DISPLAY_NUM}-lock" "/tmp/.X11-unix/X${d}{DISPLAY_NUM}" 2>/dev/null
        |}
        |
        |status_x() {
        |  if x_running; then
        |    echo "${strings.running} (DISPLAY=${d}DISP, RFB 127.0.0.1:${d}RFBPORT)"
        |  else
        |    echo "${strings.stopped}"
        |  fi
        |}
        |
        |# GUI 音声 (オプトイン): Z2_AUDIO=1 のときだけ proot 内に PulseAudio を起こし、null-sink (z2sink)
        |# の monitor を module-simple-protocol-tcp で 127.0.0.1:Z2_AUDIO_PORT へ s16le/48k/2ch で流す。
        |# Android 側 AudioBridge が受けて端末スピーカーで鳴らす。Z2_AUDIO 未設定なら**一切何もしない**(依存ゼロ)。
        |# XDG_RUNTIME_DIR はディスプレイ毎に分離するので、複数 GUI が別 PA・別ポートで並走できる。
        |start_audio() {
        |  [ "${d}{Z2_AUDIO:-0}" = "1" ] || return 0
        |  APORT="${d}{Z2_AUDIO_PORT:-0}"
        |  [ "${d}APORT" -gt 0 ] 2>/dev/null || return 0
        |  # pulseaudio / pactl が無ければ導入する。GUI 音声トグル ON がユーザー同意なのでここは取得してよい。
        |  if ! has pulseaudio || ! has pactl; then
        |    detect_pm
        |    clear_pm_locks
        |    echo "${strings.audioInstalling} (${d}PM)"
        |    case "${d}PM" in
        |      apk)    apk add --no-cache pulseaudio pulseaudio-utils ;;
        |      apt)    apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y pulseaudio pulseaudio-utils ;;
        |      pacman) pacman -Sy --noconfirm --needed pulseaudio ;;
        |      *) echo "${strings.noPackageManager}"; return 0 ;;
        |    esac
        |  fi
        |  has pulseaudio || { echo "${strings.audioNoPulse}"; return 0; }
        |  has pactl || { echo "${strings.audioNoPactl}"; return 0; }
        |  # 実行時ディレクトリを :N 専用にして PA をディスプレイ毎に分離する。
        |  export XDG_RUNTIME_DIR="${d}{XDG_RUNTIME_DIR:-/tmp/z2gui-xdg-${d}{DISPLAY_NUM}}"
        |  mkdir -p "${d}XDG_RUNTIME_DIR"; chmod 0700 "${d}XDG_RUNTIME_DIR" 2>/dev/null
        |  # proot/Android では shm/自動起動が不安定なので、設定で無効化して堅く起動させる。
        |  PA_CFG="${d}HOME/.config/pulse"
        |  mkdir -p "${d}PA_CFG" 2>/dev/null
        |  [ -f "${d}PA_CFG/daemon.conf" ] || printf 'enable-shm = no\nexit-idle-time = -1\nflat-volumes = no\n' > "${d}PA_CFG/daemon.conf" 2>/dev/null
        |  [ -f "${d}PA_CFG/client.conf" ] || printf 'autospawn = no\nenable-shm = no\n' > "${d}PA_CFG/client.conf" 2>/dev/null
        |  # 既に起動済みなら起こし直さない (start_x 再入や複数アプリ起動でも 1 つに保つ)。
        |  if ! pactl info >/dev/null 2>&1; then
        |    # 重要: 既定 /etc/pulse/default.pa は module-udev-detect / module-alsa 等のハード検出を
        |    # load するが、proot では inotify/ALSA が無く「Daemon startup failed」で起動ごと落ちる。
        |    # `-n` で既定スクリプトを読まず、必要なモジュールだけ明示 load した最小構成で起こす。
        |    # --daemonize は使わない: PulseAudio は detach 時に /proc/self/exe を自己 re-exec するが、
        |    # z2root では /proc/self/exe が launcher(libz2root.so)を指すため「cannot self execute」で
        |    # daemon が立ち上がらない。setsid で新セッションへ起こし、`&` で背景化して同等にする
        |    # (proot でも同じ起こし方で問題なく動く)。停止は stop_audio の `pactl exit`。
        |    setsid pulseaudio -n --exit-idle-time=-1 \
        |      --load="module-native-protocol-unix" \
        |      --load="module-null-sink sink_name=z2sink rate=48000 channels=2 sink_properties=device.description=z2term" \
        |      --load="module-simple-protocol-tcp record=true source=z2sink.monitor format=s16le rate=48000 channels=2 listen=127.0.0.1 port=${d}APORT" \
        |      --log-target="file:/tmp/z2gui-audio-${d}{DISPLAY_NUM}.log" </dev/null >/dev/null 2>&1 &
        |    k=0
        |    while [ ${d}k -lt 30 ] && ! pactl info >/dev/null 2>&1; do sleep 0.1; k=${d}((k+1)); done
        |  fi
        |  if ! pactl info >/dev/null 2>&1; then
        |    echo "${strings.audioStartFailed} (log: /tmp/z2gui-audio-${d}{DISPLAY_NUM}.log)"; return 0
        |  fi
        |  # null-sink (z2sink) を既定 sink にし、その monitor を TCP で出す。二重ロードは避ける。
        |  pactl list short sinks 2>/dev/null | grep -q z2sink || \
        |    pactl load-module module-null-sink sink_name=z2sink rate=48000 channels=2 \
        |      sink_properties=device.description=z2term >/dev/null 2>&1
        |  pactl set-default-sink z2sink 2>/dev/null
        |  pactl list short modules 2>/dev/null | grep -q "port=${d}APORT" || \
        |    pactl load-module module-simple-protocol-tcp record=true source=z2sink.monitor \
        |      format=s16le rate=48000 channels=2 listen=127.0.0.1 port=${d}APORT >/dev/null 2>&1
        |  # ALSA アプリ (mpv 等) も PulseAudio 経由で z2sink へ流す。SDL も pulse 既定に。
        |  [ -e /etc/asound.conf ] || printf 'pcm.!default pulse\nctl.!default pulse\n' > /etc/asound.conf 2>/dev/null
        |  export PULSE_SINK=z2sink
        |  export SDL_AUDIODRIVER=pulseaudio
        |  echo "${strings.audioReady} 127.0.0.1:${d}APORT (s16le/48k/2ch)"
        |}
        |
        |# この :N の音声を止める。XDG_RUNTIME_DIR が :N 専用なので他ディスプレイの PA には影響しない。
        |stop_audio() {
        |  has pactl || return 0
        |  export XDG_RUNTIME_DIR="${d}{XDG_RUNTIME_DIR:-/tmp/z2gui-xdg-${d}{DISPLAY_NUM}}"
        |  pactl info >/dev/null 2>&1 || return 0
        |  pactl exit >/dev/null 2>&1   # このディスプレイ専用 PA daemon を終了 (sink/module ごと片付く)。
        |}
        |
        |# GUI 内で動くアプリの共通の土台 (XDG_RUNTIME_DIR + D-Bus セッションバス) を用意する。
        |#
        |# ⚠ 0.8.497 まで**選んだ端末が konsole のときだけ**立てていた。そのため右クリックメニューや
        |#    別タブの z2run から起動したアプリは D-Bus 無しで動いていて、補助プロセスを別プロセスとして
        |#    起こす作りのファイル管理系 (KIO 等) が軒並み起動に失敗していた
        |#    (サムネイル・ゴミ箱・接続機器の一覧が全部出ない)。端末の種類に関係なく立てる。
        |#
        |# ⭐ アドレスは **XDG_RUNTIME_DIR 配下の決め打ちのパス**にする。dbus-launch は起動のたびに
        |#    アドレスが変わるので、別タブの z2run から同じバスへ相乗りできない。dbus-daemon を先に
        |#    試し、無い環境でだけ dbus-launch へ落として、いずれの場合もアドレスを控えに書き出す。
        |start_session_bus() {
        |  export XDG_RUNTIME_DIR="${d}{XDG_RUNTIME_DIR:-/tmp/z2gui-xdg-${d}{DISPLAY_NUM}}"
        |  mkdir -p "${d}XDG_RUNTIME_DIR"; chmod 0700 "${d}XDG_RUNTIME_DIR" 2>/dev/null
        |  # Xvnc は MIT-SHM を無効化している。Qt/GTK にも通常画像経路を明示し、
        |  # 共有メモリ前提のバッファが半分しか更新されない/窓が出ない現象を防ぐ。
        |  export QT_QPA_PLATFORM=xcb
        |  export QT_XCB_NO_MITSHM=1
        |  export QT_X11_NO_MITSHM=1
        |  export GDK_BACKEND=x11
        |  export GDK_RENDERING=image
        |  # gtk.portalのUseIn=gnomeを選択する。dbus-daemonもこの値を継承するため、最初の
        |  # portal呼び出しで本体とGTK backendが自動起動し、Settings.Readへ有効な応答を返せる。
        |  export XDG_CURRENT_DESKTOP="${d}{XDG_CURRENT_DESKTOP:-GNOME}"
        |  [ -n "${d}{DBUS_SESSION_BUS_ADDRESS:-}" ] && return 0
        |  DBUS_SOCK="${d}XDG_RUNTIME_DIR/dbus.sock"
        |  if has dbus-daemon; then
        |    # `--print-pid` の値は保存先ではなく既に開いた fd 番号。以前はここへ
        |    # `/tmp/...pid` を渡していたため Alpine の dbus-daemon が
        |    # "Invalid file descriptor" で終了し、GUI アプリだけが起動直後に消えていた。
        |    # 自前 fork をさせず背景化すれば `${d}!` がそのまま daemon の PID になり、
        |    # stdout の fd 細工も自己再 exec も要らない。
        |    DBUS_PID=""
        |    [ -r "${d}DBUS_PIDFILE" ] && DBUS_PID=${d}(cat "${d}DBUS_PIDFILE" 2>/dev/null)
        |    if [ -S "${d}DBUS_SOCK" ] && [ -n "${d}DBUS_PID" ] && is_gui_proc "${d}DBUS_PID"; then
        |      :
        |    else
        |      rm -f "${d}DBUS_SOCK" "${d}DBUS_PIDFILE" 2>/dev/null
        |      setsid dbus-daemon --session --nofork --address="unix:path=${d}DBUS_SOCK" \
        |        </dev/null >"/tmp/z2gui-dbus-${d}{DISPLAY_NUM}.log" 2>&1 &
        |      DBUS_PID=${d}!
        |      echo "${d}DBUS_PID" > "${d}DBUS_PIDFILE" 2>/dev/null
        |      # socket が出来るまで最大 3 秒待つ。fork 直後で間に合わないことがあるため。
        |      j=0
        |      while [ ${d}j -lt 30 ] && [ ! -S "${d}DBUS_SOCK" ]; do sleep 0.1; j=${d}((j+1)); done
        |    fi
        |    if [ -S "${d}DBUS_SOCK" ] && [ -n "${d}DBUS_PID" ] && is_gui_proc "${d}DBUS_PID"; then
        |      echo "${d}DBUS_PID" >> "${d}PIDFILE" 2>/dev/null
        |    else
        |      [ -n "${d}DBUS_PID" ] && is_gui_proc "${d}DBUS_PID" && kill "${d}DBUS_PID" 2>/dev/null
        |      rm -f "${d}DBUS_SOCK" "${d}DBUS_PIDFILE" 2>/dev/null
        |      DBUS_PID=""
        |    fi
        |    [ -S "${d}DBUS_SOCK" ] && export DBUS_SESSION_BUS_ADDRESS="unix:path=${d}DBUS_SOCK"
        |  fi
        |  if [ -z "${d}{DBUS_SESSION_BUS_ADDRESS:-}" ] && has dbus-launch; then
        |    # dbus-launch は `bus_address=...; bus_pid=...` 形式で env を返す → 行を eval。
        |    DBUS_OUT=${d}(dbus-launch --sh-syntax 2>"/tmp/z2gui-dbus-${d}{DISPLAY_NUM}.log")
        |    if [ -n "${d}DBUS_OUT" ]; then
        |      eval "${d}DBUS_OUT"
        |      export DBUS_SESSION_BUS_ADDRESS DBUS_SESSION_BUS_PID
        |      echo "${d}{DBUS_SESSION_BUS_PID:-0}" >> "${d}PIDFILE" 2>/dev/null
        |    fi
        |  fi
        |  # 別タブの z2run が同じバスへ繋げるようアドレスを控える (stop_x で消す)。
        |  if [ -n "${d}{DBUS_SESSION_BUS_ADDRESS:-}" ]; then
        |    echo "${d}DBUS_SESSION_BUS_ADDRESS" > "${d}XDG_RUNTIME_DIR/dbus-address" 2>/dev/null
        |  fi
        |}
        |
        |# ファイル管理系が「この種類は何で開くか」を決められるようにアプリ台帳を用意する。
        |# ⛔ **毎回は走らせない。** .desktop を全部読み直すので起動が目に見えて遅くなる。
        |#    台帳がまだ無いときだけ作る (アプリを入れ直したときは各 distro の後処理が作り直す)。
        |ensure_desktop_db() {
        |  has update-desktop-database || return 0
        |  for adir in /usr/share/applications /usr/local/share/applications \
        |              "${d}{HOME:-/root}/.local/share/applications"; do
        |    [ -d "${d}adir" ] || continue
        |    [ -f "${d}adir/mimeinfo.cache" ] && continue
        |    update-desktop-database "${d}adir" >/dev/null 2>&1 || true
        |  done
        |}
        |
        |start_x() {
        |  GEOM="${d}{1:-${d}DEFAULT_GEOM}"
        |  CLEAN="${d}2"
        |  case "${d}GEOM" in
        |    *[0-9]x[0-9]*) : ;;
        |    *) echo "${strings.invalidGeometry}: '${d}GEOM' (e.g. 1280x720)"; exit 1 ;;
        |  esac
        |  # 第2引数が clean のときはキャッシュごと入れ直す (救済)。それ以外は通常導入。
        |  if [ "${d}CLEAN" = "clean" ]; then clean_pkgs || exit 1; else ensure_pkgs || exit 1; fi
        |  XSERVER=${d}(xbin) || { echo "${strings.noXvnc}"; exit 1; }
        |  # 再入ガード: Xvnc が実際に生きているなら stop_x で**動作中のセッションを壊さない**。
        |  # (z2gui が誤って再起動された場合の安全網。本来の再帰起動は上の SHELL 上書きで防ぐ。
        |  #  stale ソケットだけ残った状態では x_alive=false となり下の stop_x で掃除される。)
        |  if x_alive; then
        |    echo "${strings.alreadyRunning} (DISPLAY=${d}DISP, RFB 127.0.0.1:${d}RFBPORT)"
        |    exec "${d}{SHELL:-/bin/sh}"
        |  fi
        |  stop_x
        |  # この start_x 配下の子プロセス (openbox/GUI アプリ) に DISPLAY を渡す。
        |  # 全体ではなく start_x 内だけで export する (stop の自己巻き込み回避。冒頭コメント参照)。
        |  export DISPLAY="${d}DISP"
        |  : > "${d}PIDFILE" 2>/dev/null   # このディスプレイの PID 控えを初期化
        |  mkdir -p /tmp/.X11-unix 2>/dev/null
        |  chmod 1777 /tmp/.X11-unix 2>/dev/null
        |  echo "${strings.startingXvnc} ${d}XSERVER: ${d}GEOM @ ${d}DISP (RFB 127.0.0.1:${d}RFBPORT)"
        |  # GUI 配下のプロセスは launcher の制御端末 (アプリ側 PtyProcess が握る PTY) から
        |  # setsid で切り離して起動する (GUI プロセスが端末を共有する必要はない)。
        |  # stdin は /dev/null に向ける。
        |  # MIT-SHM (X 共有メモリ画像転送) は無効化する。クライアント (mpv 等) が ShmAttach を
        |  # 試みると、z2root エンジンでは SysV 共有メモリの相乗りが通らず X が BadAccess を返し、
        |  # その非同期エラーで mpv 等が segfault する (proot では shmget が失敗してアプリ側が自動で
        |  # 非 SHM 描画に落ちるため顕在化しなかった)。VNC はローカルなので SHM の利点はほぼ無く、
        |  # 拡張ごと切れば全クライアントが確実に通常描画にフォールバックする (両エンジンで安全)。
        |  setsid "${d}XSERVER" "${d}DISP" -geometry "${d}GEOM" -depth 24 -SecurityTypes None -localhost -rfbport "${d}RFBPORT" -extension MIT-SHM -noreset </dev/null >"/tmp/z2gui-xvnc-${d}{DISPLAY_NUM}.log" 2>&1 &
        |  echo ${d}! >> "${d}PIDFILE" 2>/dev/null
        |  i=0
        |  while [ ${d}i -lt 50 ]; do
        |    x_running && break
        |    i=${d}((i+1)); sleep 0.1
        |  done
        |  if ! x_running; then
        |    echo "${strings.xvncFailed}"; cat "/tmp/z2gui-xvnc-${d}{DISPLAY_NUM}.log" 2>/dev/null; exit 1
        |  fi
        |  # GUI 音声 (Z2_AUDIO=1 のときだけ)。X とは独立だが Xvnc 起動確認後に立てる。失敗しても続行。
        |  start_audio
        |  # GUI アプリ共通の土台 (XDG_RUNTIME_DIR + セッションバス)。
        |  # ⭐ **openbox より先に立てる**: 右クリックメニューから起こしたアプリは openbox の
        |  #    環境をそのまま継ぐので、ここで export した分が全部渡る。
        |  start_session_bus
        |  ensure_desktop_db
        |  # ---- openbox の設定 ----
        |  # ⚠ 0.8.497 まではここで最小の rc.xml を書いて **既定を丸ごと差し替えて** いた。
        |  #    openbox はキー/マウスの割り当てもメニューもプログラムに内蔵しておらず、全部 rc.xml の
        |  #    データでしかない。差し替えた瞬間に「窓を左上に置く」以外が消える
        |  #    (タイトルバーのボタン・リサイズ・Alt+Tab・デスクトップの右クリックメニュー)。
        |  #    右クリックメニューは **GUI の中でアプリを起こす唯一の入口** なので、これが消えると
        |  #    「端末しか出せない」状態になっていた。distro の既定を土台にして、
        |  #    **必要な 2 点 (メニューの指し先・窓の位置) だけ**差し替える方式にする。
        |  OBRC="/tmp/z2-openbox-rc-${d}{DISPLAY_NUM}.xml"
        |  OBMENU="/tmp/z2-openbox-menu-${d}{DISPLAY_NUM}.xml"
        |  # (1) メニューの中身。既定の menu.xml は distro が用意した**固定の一覧**で、入っていない
        |  #     アプリが大量に並ぶ (押しても何も起きない項目ばかりのメニューは無いより分かりにくい)。
        |  #     z2menu が .desktop を読んで**実際に入っているものだけ**を出す。
        |  #     窓の一覧は openbox 内蔵の client-list-menu をそのまま使う (依存を増やさない)。
        |  cat > "${d}OBMENU" <<OBMEOF
        |<?xml version="1.0" encoding="UTF-8"?>
        |<openbox_menu xmlns="http://openbox.org/3.4/menu">
        |  <menu id="root-menu" label="z2term">
        |    <menu id="z2-apps" label="${strings.menuApps}" execute="/usr/local/bin/z2menu"/>
        |    <menu id="client-list-menu" label="${strings.menuWindows}"/>
        |    <separator/>
        |    <item label="${strings.menuReload}"><action name="Reconfigure"/></item>
        |  </menu>
        |</openbox_menu>
        |OBMEOF
        |  # (2) rc.xml。既定があればそれを土台にする。
        |  #     ⚠ <file> は全部落として 1 つだけ入れ直す (複数のメニューファイルを持つ distro があり、
        |  #        1 つ目だけ差し替えると既定の固定一覧が残ってしまう)。
        |  #     ⚠ 窓の位置固定は <applications> の**末尾**に足す。openbox は一致するルールを順に
        |  #        適用して後のものが勝つので、既定の個別ルールより後ろでないと効かない。
        |  OBBASE=""
        |  for c in "${d}{HOME:-/root}/.config/openbox/rc.xml" /etc/xdg/openbox/rc.xml; do
        |    [ -f "${d}c" ] && { OBBASE="${d}c"; break; }
        |  done
        |  if [ -n "${d}OBBASE" ]; then
        |    awk -v menu="${d}OBMENU" '
        |      function emit() {
        |        print "  <application class=\"*\">"
        |        print "    <position force=\"yes\"><x>0</x><y>0</y></position>"
        |        print "  </application>"
        |      }
        |      { line = ${d}0; sub(/^[ \t]+/, "", line); sub(/[ \t]+${d}/, "", line) }
        |      line ~ /^<file>[^<]*<\/file>${d}/ { next }
        |      line == "<menu>" && !mdone { print; print "  <file>" menu "</file>"; mdone = 1; next }
        |      line ~ /^<\/applications>${d}/ && !adone { emit(); adone = 1 }
        |      line ~ /^<\/openbox_config>${d}/ && !adone {
        |        print "<applications>"; emit(); print "</applications>"; adone = 1
        |      }
        |      { print }
        |    ' "${d}OBBASE" > "${d}OBRC" 2>/dev/null
        |  fi
        |  # 既定が無い distro / 加工に失敗したときは最小構成で立てる。
        |  # ⚠ この経路では窓の移動もリサイズもできない。あくまで最後の砦。
        |  if [ ! -s "${d}OBRC" ]; then
        |    cat > "${d}OBRC" <<OBEOF
        |<?xml version="1.0" encoding="UTF-8"?>
        |<openbox_config xmlns="http://openbox.org/3.4/rc">
        |  <placement><policy>UnderMouse</policy><center>no</center></placement>
        |  <menu><file>${d}OBMENU</file></menu>
        |  <applications>
        |    <application class="*">
        |      <position force="yes"><x>0</x><y>0</y></position>
        |    </application>
        |  </applications>
        |</openbox_config>
        |OBEOF
        |  fi
        |  setsid openbox --config-file "${d}OBRC" </dev/null >"/tmp/z2gui-wm-${d}{DISPLAY_NUM}.log" 2>&1 &
        |  echo ${d}! >> "${d}PIDFILE" 2>/dev/null
        |  # WM がメニューとアプリ窓を扱える状態になるまで待つ。
        |  wait_wm() {
        |    # WM の名乗り (_NET_SUPPORTING_WM_CHECK) を待つ。⚠ xprop はどの distro でも
        |    # 導入対象に入れていないので、無ければこの待ちは飛ばす。
        |    if has xprop; then
        |      k=0
        |      while [ ${d}k -lt 50 ]; do
        |        xprop -root _NET_SUPPORTING_WM_CHECK 2>/dev/null | grep -q '0x' && break
        |        k=${d}((k+1)); sleep 0.1
        |      done
        |    fi
        |    # ⚠ 名乗りは openbox が既存の窓を拾い終える **前** に立つので、名乗りだけでは
        |    #    競合の窓を塞ぎ切れない。拾い終わるまでの分を一律で足す (この時点で窓は
        |    #    1 つも無いので短くてよい)。
        |    sleep 1
        |  }
        |  wait_wm
        |  echo "${strings.ready} (RFB 127.0.0.1:${d}RFBPORT)"
        |  # proot --kill-on-exit 対策: ここでブロックし続けることで Xvnc/WM を生かす。
        |  # setsid したプロセスはジョブ制御から外れるため wait では待てない。X ソケットの
        |  # 存在を監視し、Xvnc が生きている限り z2gui (= proot のルート) をブロックさせる。
        |  while x_running; do sleep 2; done
        |}
        |
        |ACTION="${d}{1:-start}"
        |case "${d}ACTION" in
        |  start)   start_x "${d}2" "${d}3" ;;
        |  stop)    stop_x; echo "${strings.stoppedMsg}" ;;
        |  status)  status_x ;;
        |  install) install_pkgs ;;
        |  clean)   clean_pkgs ;;
        |  check)   check_pkgs ;;
        |  *[0-9]x[0-9]*) start_x "${d}ACTION" "${d}2" ;;
        |  *) echo "${strings.usage}" ;;
        |esac
    """.trimMargin() + "\n"
}
