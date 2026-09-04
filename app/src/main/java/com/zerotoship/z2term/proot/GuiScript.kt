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
const val Z2TERM_GUI_PACKAGES = "tigervnc openbox xterm font-noto ttf-dejavu"

/**
 * Linux GUI ランチャ `z2gui` スクリプト。
 *
 * PRoot は root もハードウェアフレームバッファも使えないため、**仮想 X サーバ Xvnc**
 * を立て、その画面を VNC(RFB) で z2term の内蔵クライアントへ転送する構成にする
 * (詳細は 99_private/HANDOFF/z2term/M8-GUI-HANDOFF.md)。
 *
 * これを `/usr/local/bin/z2gui` に配置 (ProotLauncher.ensureGuiScript) することで、
 * 端末から、または z2term の GUI セッションから次のように使える:
 *  - `z2gui` / `z2gui start [WxH]` … Xvnc + openbox + ターミナルを起動 (未導入なら自動導入)
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
 * [terminalBinary] / [terminalPackage]: GUI 内で起動するターミナル。設定 ([GuiTerminal]) から
 * z2term が渡す。ここに来る端末のパッケージ名は apk/apt/pacman で同名。
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
    val noTermFlag: String,           // "ℹ Z2_NO_TERM=1: 端末を起動せず Xvnc+openbox のみ。"
    val terminalNotFound: String,     // "⚠ 端末 {BIN} が見つかりません ..."
    // --- 0.8.343 で追加。端末だけ入らない / 端末だけ起動できない を見えるようにする。
    val terminalInstallFailed: String, // "⚠ 端末パッケージを導入できませんでした ({PKGS})"
    val terminalLog: String,           // "🧾 端末の出力" (ログの場所を添えて出す)
    val ready: String,                // "✅ GUI 準備完了。z2term の GUI タブから ..."
    val running: String,              // "✅ GUI 起動中 (DISPLAY=..., RFB ...)"
    val stopped: String,              // "⏹ GUI は停止中"
    val stoppedMsg: String,           // "⏹ 停止しました"
    val usage: String,                 // "使い方: z2gui [start [WxH] [clean] | stop | status | ...]"
    // --- 0.8.228 で追加。ここだけ日本語のまま端末に出ていた（英語モードでも和文が出る状態）。
    val konsoleRebuild: String,       // ローカル cache から再構成する旨
    val extracting: String,           // "📦 展開:" (方式名は英字なので後ろに付ける)
    val konsoleRebuilt: String,       // 再構成できた
    val konsoleCacheShort: String,    // cache だけでは足りない
    val installFailed: String,        // GUI 一式を導入できなかった
    val installFailedHint: String,    // その次の一手
    val audioInstalling: String,      // PulseAudio を導入します
    val audioNoPulse: String,         // pulseaudio が無いので無音で継続
    val audioNoPactl: String,         // pactl が無いので無音で継続
    val audioStartFailed: String,     // PulseAudio 起動失敗
    val audioReady: String,           // 音声の経路ができた
    val qtFallback: String,           // PySide6 同梱 Qt6 を追加
    val qtFallbackFound: String,      // 任意の libQt6QuickWidgets を発見
    // --- 0.8.498 で追加。デスクトップ右クリックのメニューに出す見出し。
    // ⚠ ここは**画面に出るラベル**なので、必ず全言語を埋めること (英語落ちだと和文の中に英語が混じる)。
    val menuApps: String,             // 「アプリ」(z2menu の pipe menu を開く)
    val menuWindows: String,          // 「窓」(openbox 内蔵の client-list-menu)
    val menuTerminal: String,         // 「端末」
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
            noTermFlag = "ℹ Z2_NO_TERM=1: 端末を起動せず Xvnc+openbox のみ。",
            terminalNotFound = "⚠ 端末が見つかりません (導入失敗?)。openbox のみ起動。",
            terminalInstallFailed = "⚠ 端末パッケージを導入できませんでした。GUI は起動しますが端末の窓は出ません",
            terminalLog = "🧾 端末の出力",
            ready = "✅ GUI 準備完了。z2term の GUI タブから接続してください。",
            running = "✅ GUI 起動中",
            stopped = "⏹ GUI は停止中",
            stoppedMsg = "⏹ 停止しました",
            usage = "使い方: z2gui [start [WxH] [clean] | stop | status | install | clean | check]",
            konsoleRebuild = "🔧 Konsole 関連ファイルが不足 — ローカル cache から再構成 (ネット通信なし)",
            extracting = "📦 展開",
            konsoleRebuilt = "✅ Konsole + Qt6 をローカル cache から再構成済",
            konsoleCacheShort = "⚠️ ローカル cache だけでは揃いません — 設定で「クリーンインストール」を ON にして 🖥 を押してください",
            installFailed = "❌ GUI 一式 (Xvnc / openbox / 端末) を導入できませんでした。",
            installFailedHint = "   ネットワーク接続を確認するか、設定で「クリーンインストール」を ON にして 🖥 を押してください。",
            audioInstalling = "🔊 GUI 音声: PulseAudio を導入します",
            audioNoPulse = "⚠️ GUI 音声: pulseaudio が無いため無音で継続",
            audioNoPactl = "⚠️ GUI 音声: pactl が無いため無音で継続",
            audioStartFailed = "⚠️ GUI 音声: PulseAudio 起動失敗",
            audioReady = "🔊 GUI 音声: z2sink.monitor →",
            qtFallback = "📚 PySide6 同梱 Qt6 を LD_LIBRARY_PATH に追加 (Konsole 救済):",
            qtFallbackFound = "📚 任意の libQt6QuickWidgets.so.6 を発見 → LD_LIBRARY_PATH に追加:",
            menuApps = "アプリ",
            menuWindows = "窓",
            menuTerminal = "端末",
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
            noTermFlag = "ℹ Z2_NO_TERM=1: no terminal, only Xvnc+openbox.",
            terminalNotFound = "⚠ terminal not found (install failed?). Starting openbox only.",
            terminalInstallFailed = "⚠ Could not install the terminal package. The GUI will start, but no terminal window will appear",
            terminalLog = "🧾 terminal output",
            ready = "✅ GUI ready. Connect from a z2term GUI tab.",
            running = "✅ GUI running",
            stopped = "⏹ GUI is stopped",
            stoppedMsg = "⏹ Stopped",
            usage = "Usage: z2gui [start [WxH] [clean] | stop | status | install | clean | check]",
            konsoleRebuild = "🔧 Konsole files are missing — rebuilding from the local cache (no network)",
            extracting = "📦 Extracting",
            konsoleRebuilt = "✅ Rebuilt Konsole + Qt6 from the local cache",
            konsoleCacheShort = "⚠️ The local cache is not enough — turn on \"Clean install\" in settings and press 🖥",
            installFailed = "❌ Could not install the GUI stack (Xvnc / openbox / terminal).",
            installFailedHint = "   Check your network, or turn on \"Clean install\" in settings and press 🖥.",
            audioInstalling = "🔊 GUI audio: installing PulseAudio",
            audioNoPulse = "⚠️ GUI audio: no pulseaudio, continuing without sound",
            audioNoPactl = "⚠️ GUI audio: no pactl, continuing without sound",
            audioStartFailed = "⚠️ GUI audio: PulseAudio failed to start",
            audioReady = "🔊 GUI audio: z2sink.monitor →",
            qtFallback = "📚 Added PySide6's bundled Qt6 to LD_LIBRARY_PATH (Konsole fallback):",
            qtFallbackFound = "📚 Found a libQt6QuickWidgets.so.6 → added to LD_LIBRARY_PATH:",
            menuApps = "Applications",
            menuWindows = "Windows",
            menuTerminal = "Terminal",
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
            noTermFlag = "ℹ Z2_NO_TERM=1: 不启动终端，只启动 Xvnc+openbox。",
            terminalNotFound = "⚠ 找不到终端 (是不是安装失败了?)。只启动 openbox。",
            terminalInstallFailed = "⚠ 没能安装终端软件包。图形环境会启动，但不会出现终端窗口",
            terminalLog = "🧾 终端的输出",
            ready = "✅ 图形环境准备完成。请从 z2term 的图形标签页连接。",
            running = "✅ 图形环境运行中",
            stopped = "⏹ 图形环境已停止",
            stoppedMsg = "⏹ 已停止",
            usage = "用法: z2gui [start [WxH] [clean] | stop | status | install | clean | check]",
            konsoleRebuild = "🔧 Konsole 相关文件缺失 — 正在从本地缓存重建 (不联网)",
            extracting = "📦 解压",
            konsoleRebuilt = "✅ 已从本地缓存重建 Konsole + Qt6",
            konsoleCacheShort = "⚠️ 光靠本地缓存凑不齐 — 请在设置里打开“全新安装”后按 🖥",
            installFailed = "❌ 没能安装整套图形环境 (Xvnc / openbox / 终端)。",
            installFailedHint = "   请确认网络连接，或者在设置里打开“全新安装”后按 🖥。",
            audioInstalling = "🔊 图形界面声音: 正在安装 PulseAudio",
            audioNoPulse = "⚠️ 图形界面声音: 没有 pulseaudio，将以无声继续",
            audioNoPactl = "⚠️ 图形界面声音: 没有 pactl，将以无声继续",
            audioStartFailed = "⚠️ 图形界面声音: PulseAudio 启动失败",
            audioReady = "🔊 图形界面声音: z2sink.monitor →",
            qtFallback = "📚 已把 PySide6 自带的 Qt6 加入 LD_LIBRARY_PATH (救 Konsole):",
            qtFallbackFound = "📚 发现了 libQt6QuickWidgets.so.6 → 已加入 LD_LIBRARY_PATH:",
            menuApps = "应用",
            menuWindows = "窗口",
            menuTerminal = "终端",
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
            noTermFlag = "ℹ Z2_NO_TERM=1: 不啟動終端機，只啟動 Xvnc+openbox。",
            terminalNotFound = "⚠ 找不到終端機 (是不是安裝失敗了?)。只啟動 openbox。",
            terminalInstallFailed = "⚠ 沒能安裝終端機套件。圖形環境會啟動，但不會出現終端機視窗",
            terminalLog = "🧾 終端機的輸出",
            ready = "✅ 圖形環境準備完成。請從 z2term 的圖形分頁連線。",
            running = "✅ 圖形環境執行中",
            stopped = "⏹ 圖形環境已停止",
            stoppedMsg = "⏹ 已停止",
            usage = "用法: z2gui [start [WxH] [clean] | stop | status | install | clean | check]",
            konsoleRebuild = "🔧 Konsole 相關檔案缺失 — 正在從本機快取重建 (不聯網)",
            extracting = "📦 解壓縮",
            konsoleRebuilt = "✅ 已從本機快取重建 Konsole + Qt6",
            konsoleCacheShort = "⚠️ 光靠本機快取湊不齊 — 請在設定裡開啟“全新安裝”後按 🖥",
            installFailed = "❌ 沒能安裝整套圖形環境 (Xvnc / openbox / 終端機)。",
            installFailedHint = "   請確認網路連線，或者在設定裡開啟“全新安裝”後按 🖥。",
            audioInstalling = "🔊 圖形介面聲音: 正在安裝 PulseAudio",
            audioNoPulse = "⚠️ 圖形介面聲音: 沒有 pulseaudio，將以無聲繼續",
            audioNoPactl = "⚠️ 圖形介面聲音: 沒有 pactl，將以無聲繼續",
            audioStartFailed = "⚠️ 圖形介面聲音: PulseAudio 啟動失敗",
            audioReady = "🔊 圖形介面聲音: z2sink.monitor →",
            qtFallback = "📚 已把 PySide6 自帶的 Qt6 加入 LD_LIBRARY_PATH (救 Konsole):",
            qtFallbackFound = "📚 發現了 libQt6QuickWidgets.so.6 → 已加入 LD_LIBRARY_PATH:",
            menuApps = "應用程式",
            menuWindows = "視窗",
            menuTerminal = "終端機",
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
    terminalBinary: String = "xterm",
    terminalPackage: String = "xterm",
    defaultGeometry: String = "1280x720",
    strings: GuiScriptStrings = GuiScriptStrings.ja()
): String {
    val d = "${'$'}"  // シェルの $ (Kotlin テンプレートと衝突しないように)
    return """
        |#!/bin/sh
        |# z2term: Linux GUI ランチャ (Xvnc + openbox + ターミナル)。distro 非依存。
        |# RFB は 127.0.0.1 のみで待ち受け (z2term 内蔵クライアントが接続)。外部公開しない。
        |#   使い方: z2gui [start [WxH] | stop | status | install | check]
        |# ディスプレイ番号と RFB ポートは z2term が環境変数 Z2_DISPLAY / Z2_RFBPORT で渡す
        |# (GUI タブごとに :1/5901, :2/5902 … と別ポートで並走するため)。未指定なら既定値。
        |DISPLAY_NUM="${d}{Z2_DISPLAY:-$display}"
        |DISP=":${d}DISPLAY_NUM"
        |RFBPORT="${d}{Z2_RFBPORT:-$rfbPort}"
        |DEFAULT_GEOM="$defaultGeometry"
        |GUI_TERM_BIN="$terminalBinary"
        |GUI_TERM_PKG="$terminalPackage"
        |# DISPLAY は start_x の中だけで export する。ここで全体に export すると `z2gui stop` の
        |# プロセス自身が DISPLAY=:N を持ち、stop_x のディスプレイ単位 kill が自分を巻き込む。
        |export HOME="${d}{HOME:-/root}"
        |
        |# 重要: GUI 起動時 ProotLauncher は command=z2gui に合わせて SHELL=<このスクリプト> を
        |# 渡してくる。その状態で xterm などが ${d}SHELL を起動すると **z2gui 自身が再帰起動**し、
        |# 再帰側の start_x → stop_x が**動作中の Xvnc を停止**して GUI 全体が落ちる。
        |# GUI 配下のシェルは必ず本物のシェルにするため、ここで SHELL を実体のシェルへ上書きする。
        |# 設定「ログインシェル」が Z2_LOGIN_SHELL で渡ってくるので、それを最優先で採用する
        |# (GUI 内ターミナルだけ bash に戻ってしまうのを防ぐ)。
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
        |# パッケージマネージャと、その distro のサーバ/WM/フォントのパッケージ名を決める。
        |#  端末パッケージ (GUI_TERM_PKG) は apk/apt/pacman で同名なので共通で末尾に足す。
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
        |    PM=apk;    SRV_PKGS="tigervnc openbox font-misc-misc font-alias font-noto ttf-dejavu"
        |  elif has apt-get; then
        |    PM=apt;    SRV_PKGS="tigervnc-standalone-server openbox xfonts-base fonts-noto-core fonts-dejavu"
        |  elif has pacman; then
        |    PM=pacman; SRV_PKGS="tigervnc openbox xorg-fonts-misc noto-fonts ttf-dejavu"
        |  else
        |    PM=""
        |  fi
        |  # 端末パッケージは**サーバ一式と分けて持つ** (0.8.343)。⚠ apk / apt / pacman はどれも
        |  # **1 つでも解決できない名前が混じると、そのコマンド全体が失敗する**。端末の追加依存を
        |  # SRV_PKGS に混ぜていたため、名前が 1 つ違うだけで **tigervnc まで入らなくなる**
        |  # (= GUI がまるごと立たない) 作りになっていた。分けておけば端末側だけが失敗する。
        |  TERM_PKGS="${d}GUI_TERM_PKG"
        |  # Konsole は DBus セッション必須 + Qt6 QuickWidgets/x11 プラグインが必要。Alpine の
        |  # `konsole` パッケージは qt6-qtdeclarative を hard-dep に引かないため、ここで明示追加する
        |  # (Debian/Arch は konsole 本体が依存解決するので dbus 系のみで足りる)。
        |  if [ "${d}GUI_TERM_BIN" = "konsole" ]; then
        |    case "${d}PM" in
        |      apk)    TERM_PKGS="${d}TERM_PKGS dbus dbus-x11 qt6-qtbase-x11 qt6-qtdeclarative qt6-qt5compat" ;;
        |      apt)    TERM_PKGS="${d}TERM_PKGS dbus dbus-x11 libqt6quickwidgets6" ;;
        |      pacman) TERM_PKGS="${d}TERM_PKGS dbus qt6-declarative qt6-5compat" ;;
        |    esac
        |  fi
        |}
        |
        |# 端末パッケージを入れる (0.8.343)。**サーバ一式とは別のコマンドで**入れるので、端末側が
        |# 失敗してもサーバ (Xvnc/openbox) は入る。追加依存 (Konsole の Qt6 等) の名前が distro で
        |# 違っていた場合に端末本体まで巻き添えにしないよう、**まとめて失敗したら端末本体だけで
        |# もう一度**試す。それも駄目なら理由を 1 行出す (黙って端末の無い GUI にしない)。
        |install_term_pkgs() {
        |  case "${d}PM" in
        |    apk)    apk add --no-cache ${d}TERM_PKGS || apk add --no-cache ${d}GUI_TERM_PKG || return 1 ;;
        |    apt)    DEBIAN_FRONTEND=noninteractive apt-get install -y ${d}TERM_PKGS \
        |            || DEBIAN_FRONTEND=noninteractive apt-get install -y ${d}GUI_TERM_PKG || return 1 ;;
        |    pacman) pacman -S --noconfirm ${d}TERM_PKGS || pacman -S --noconfirm ${d}GUI_TERM_PKG || return 1 ;;
        |    *) return 1 ;;
        |  esac
        |  return 0
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
        |  echo "${strings.installing} (${d}PM): ${d}SRV_PKGS ${d}TERM_PKGS"
        |  case "${d}PM" in
        |    apk)    apk update && apk add --no-cache ${d}SRV_PKGS ;;
        |    apt)    apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y ${d}SRV_PKGS ;;
        |    pacman) pacman -Sy --noconfirm ${d}SRV_PKGS ;;
        |    *) echo "${strings.noPackageManager}"; return 1 ;;
        |  esac
        |  install_term_pkgs || echo "${strings.terminalInstallFailed} (${d}TERM_PKGS)"
        |  ensure_konsole_qt6
        |}
        |
        |# クリーンインストール: パッケージマネージャのキャッシュを消してから取り直し、
        |# GUI 一式を強制的に入れ直す。ダウンロード/解凍が途中で失敗して壊れた状態
        |# (毎回同じ所で失敗する等) からの復旧用 (端末側のクリーンインストールと同思想)。
        |clean_pkgs() {
        |  detect_pm
        |  clear_pm_locks
        |  ensure_keyring
        |  PKGS="${d}SRV_PKGS ${d}TERM_PKGS"
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
        |  # まとめ入れで端末が入らなかったときだけ、端末側をもう一度単体で試す (0.8.343)。
        |  has "${d}GUI_TERM_BIN" || install_term_pkgs || echo "${strings.terminalInstallFailed} (${d}TERM_PKGS)"
        |  ensure_konsole_qt6
        |}
        |
        |# Konsole 本体 + Qt6 ランタイムを「ある事」までローカル cache のみで頑張る。
        |# **ネットワーク発信は一切しない** (pacman -Sy / -Sw / apk add / apt-get install を呼ばない)。
        |# クリーンインストール以外でリポジトリを更新したり再ダウンロードしたりしないユーザーポリシー。
        |# 既に展開済キャッシュ (.pkg.tar.zst / .apk / .deb) があれば bsdtar / tar / dpkg -x で
        |# 取り出して /usr/lib に置く。無い場合はそのまま継続 (起動時 LD_LIBRARY_PATH で救う事もある)。
        |ensure_konsole_qt6() {
        |  [ "${d}GUI_TERM_BIN" = "konsole" ] || return 0
        |  # Fast path: 必要な物が全部揃っていれば即 return。通常起動はここを通る。
        |  if has konsole \
        |     && [ -e /usr/lib/libQt6QuickWidgets.so.6 ] \
        |     && ( has dbus-launch || has dbus-daemon ); then
        |    return 0
        |  fi
        |  detect_pm
        |  # ローカル cache から bsdtar / tar / dpkg で再展開を試みる (NO NETWORK)。
        |  echo "${strings.konsoleRebuild}"
        |  case "${d}PM" in
        |    pacman)
        |      for pkg in /var/cache/pacman/pkg/konsole-*.pkg.tar.zst \
        |                 /var/cache/pacman/pkg/qt6-declarative-*.pkg.tar.zst \
        |                 /var/cache/pacman/pkg/qt6-5compat-*.pkg.tar.zst \
        |                 /var/cache/pacman/pkg/qt6-base-*.pkg.tar.zst \
        |                 /var/cache/pacman/pkg/dbus-*.pkg.tar.zst; do
        |        [ -f "${d}pkg" ] || continue
        |        echo "${strings.extracting} (bsdtar): ${d}pkg"
        |        if has bsdtar; then
        |          bsdtar -xf "${d}pkg" -C / --no-same-owner --no-same-permissions 2>/dev/null
        |        else
        |          tar --use-compress-program=unzstd -xf "${d}pkg" -C / --no-same-owner --no-same-permissions 2>/dev/null \
        |            || ( unzstd -c "${d}pkg" 2>/dev/null | tar -xf - -C / --no-same-owner --no-same-permissions 2>/dev/null )
        |        fi
        |      done ;;
        |    apk)
        |      for pkg in /etc/apk/cache/konsole-*.apk \
        |                 /etc/apk/cache/qt6-qtdeclarative-*.apk \
        |                 /etc/apk/cache/qt6-qt5compat-*.apk \
        |                 /etc/apk/cache/qt6-qtbase-x11-*.apk \
        |                 /etc/apk/cache/dbus-*.apk; do
        |        [ -f "${d}pkg" ] || continue
        |        echo "${strings.extracting} (tar -xzf): ${d}pkg"
        |        tar -xzf "${d}pkg" -C / --no-same-owner 2>/dev/null
        |      done ;;
        |    apt)
        |      for pkg in /var/cache/apt/archives/konsole_*.deb \
        |                 /var/cache/apt/archives/libqt6quickwidgets6_*.deb \
        |                 /var/cache/apt/archives/dbus_*.deb; do
        |        [ -f "${d}pkg" ] || continue
        |        echo "${strings.extracting} (dpkg -x): ${d}pkg"
        |        dpkg -x "${d}pkg" / 2>/dev/null
        |      done ;;
        |  esac
        |  if has konsole && [ -e /usr/lib/libQt6QuickWidgets.so.6 ]; then
        |    echo "${strings.konsoleRebuilt}"
        |  else
        |    echo "${strings.konsoleCacheShort}"
        |  fi
        |  return 0
        |}
        |
        |ensure_pkgs() {
        |  # 基本セット (Xvnc + openbox + 選択端末) が揃っていれば **ネットワークを叩かず** 即 return する
        |  # (通常起動の高速パス。導入済みを毎回 update / 再取得しないユーザーポリシー)。
        |  if xbin >/dev/null 2>&1 && has openbox && has "${d}GUI_TERM_BIN"; then
        |    # Konsole 選択時は dbus + Qt6 ランタイムの不足をローカル cache から補修する (NO NETWORK)。
        |    ensure_konsole_qt6
        |    return 0
        |  fi
        |  # 未導入 (初回、または GUI ターミナルを未導入のものへ変更した場合) → 通常インストールで取得する。
        |  # app 側のダウンロード確認ゲート (設定 ON 時) で同意済みなので、ここで取得してよい。clean 指定の
        |  # ように cache を消さず、不足分だけを apk add / apt install / pacman -S で足す。
        |  install_pkgs
        |  # 取得後に再判定。まだ揃っていなければ (ネット無し / PM 無し / 取得失敗) 明確に案内して失敗する。
        |  if xbin >/dev/null 2>&1 && has openbox && has "${d}GUI_TERM_BIN"; then
        |    return 0
        |  fi
        |  echo "${strings.installFailed} (Xvnc / openbox / ${d}GUI_TERM_BIN)"
        |  echo "${strings.installFailedHint}"
        |  return 1
        |}
        |
        |
        |# GUI 一式 (Xvnc + openbox + 選択端末) が導入済みかを判定し、app が事前にダウンロード確認を
        |# 出せるよう "GUI_INSTALLED" / "GUI_MISSING" を 1 行で出す (M8-6 T7)。
        |check_pkgs() {
        |  if xbin >/dev/null 2>&1 && has openbox && has "${d}GUI_TERM_BIN"; then
        |    echo "GUI_INSTALLED"
        |  else
        |    echo "GUI_MISSING"
        |  fi
        |}
        |
        |x_running() { [ -e "/tmp/.X11-unix/X${d}{DISPLAY_NUM}" ]; }
        |
        |# このディスプレイで起動したプロセス (Xvnc/WM/端末) の PID を控えるファイル。
        |# 複数 GUI 並走時に「他ディスプレイのプロセスを巻き込まず :N だけ」止めるために使う。
        |PIDFILE="/tmp/z2gui-${d}{DISPLAY_NUM}.pids"
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
        |  # 1) start_x が控えた :N の既知プロセス (Xvnc/WM/端末) を pidfile から停止。
        |  #    pidfile は :N 専用なので他ディスプレイは巻き込まない。comm 確認で PID 再利用も安全。
        |  if [ -r "${d}PIDFILE" ]; then
        |    while read p; do
        |      [ -n "${d}p" ] || continue
        |      is_gui_proc "${d}p" && kill "${d}p" 2>/dev/null
        |    done < "${d}PIDFILE"
        |  fi
        |  # 2) 念のため X ロックの PID (= Xvnc 本体) も止める。Xvnc が死ねば配下の
        |  #    openbox/端末/GUI アプリは X 切断で自動終了する (取りこぼしの保険)。
        |  xp=${d}(x_pid); [ -n "${d}xp" ] && is_gui_proc "${d}xp" && kill "${d}xp" 2>/dev/null
        |  # GUI 音声を立てていれば (この :N 専用 PA を) 一緒に止める。立てていなければ no-op。
        |  stop_audio
        |  # セッションバスの控え (z2run が読む) も消す。残すと次回に死んだアドレスを掴ませてしまう。
        |  rm -f "/tmp/z2gui-xdg-${d}{DISPLAY_NUM}/dbus-address" "/tmp/z2gui-xdg-${d}{DISPLAY_NUM}/dbus.sock" 2>/dev/null
        |  rm -f "${d}PIDFILE" "/tmp/.X${d}{DISPLAY_NUM}-lock" "/tmp/.X11-unix/X${d}{DISPLAY_NUM}" 2>/dev/null
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
        |  # Qt を X11 backend に固定する (Wayland の無い環境で探させない)。
        |  export QT_QPA_PLATFORM=xcb
        |  [ -n "${d}{DBUS_SESSION_BUS_ADDRESS:-}" ] && return 0
        |  DBUS_SOCK="${d}XDG_RUNTIME_DIR/dbus.sock"
        |  if has dbus-daemon; then
        |    if [ ! -S "${d}DBUS_SOCK" ]; then
        |      rm -f "${d}DBUS_SOCK" 2>/dev/null
        |      setsid dbus-daemon --session --address="unix:path=${d}DBUS_SOCK" --fork \
        |        --print-pid="/tmp/z2gui-dbus-${d}{DISPLAY_NUM}.pid" \
        |        </dev/null >"/tmp/z2gui-dbus-${d}{DISPLAY_NUM}.log" 2>&1
        |      # socket が出来るまで最大 3 秒待つ。fork 直後で間に合わないことがあるため。
        |      j=0
        |      while [ ${d}j -lt 30 ] && [ ! -S "${d}DBUS_SOCK" ]; do sleep 0.1; j=${d}((j+1)); done
        |      [ -f "/tmp/z2gui-dbus-${d}{DISPLAY_NUM}.pid" ] && \
        |        cat "/tmp/z2gui-dbus-${d}{DISPLAY_NUM}.pid" >> "${d}PIDFILE" 2>/dev/null
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
        |  # この start_x 配下の子プロセス (openbox/端末/GUI アプリ) に DISPLAY を渡す。
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
        |    <item label="${strings.menuTerminal}">
        |      <action name="Execute"><execute>${d}GUI_TERM_BIN</execute></action>
        |    </item>
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
        |  # WM が「窓の面倒を見られる状態」になるまで待つ (0.8.351)。
        |  # ⚠ Xvnc には起動待ち (上の x_running ループ) があるのに **openbox には無く**、
        |  #    起こした直後に端末を起こしていた。ptrace 配下は全体が遅いので、端末の
        |  #    XMapWindow が **openbox の起動処理の最中** に着弾しうる。そうなると
        |  #    MapRequest が openbox の待ち行列に残ったまま ppoll で寝てしまい、
        |  #    **窓が永久に map されない** (X も端末も正常なのに画面だけ真っ黒)。
        |  #    次に何か X の動きがあると溜まっていた分がまとめて処理されて窓が出るため、
        |  #    「画面を触ると急に端末が現れる」ように見えていた (実機で 2026-08-15 に確認)。
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
        |  # ターミナルは画面左上 (0,0) に、画面に対して控えめなサイズで開く (大きすぎ対策)。
        |  # 画面 (GEOM) の約 60% 幅 × 約 45% 高さ。文字セルは monospace fs 11 で概算 7x20px。
        |  # もっと大きくしたい時は WM (openbox) のタイトルバーや最大化ボタンで広げられる。
        |  GW="${d}{GEOM%x*}"; GH="${d}{GEOM#*x}"
        |  COLS=${d}(( ${d}{GW:-1280} * 6 / 10 / 7 ))
        |  ROWS=${d}(( ${d}{GH:-720} * 45 / 100 / 20 ))
        |  [ "${d}COLS" -ge 24 ] 2>/dev/null || COLS=24
        |  [ "${d}ROWS" -ge 8 ] 2>/dev/null || ROWS=8
        |  # xterm はコア(ビットマップ)フォント 'fixed' を要求し、distro により (例: Arch) その
        |  # Unicode 版が無くて起動失敗する。Xft(TrueType/fontconfig) フォントを明示すると
        |  # ttf-dejavu/noto 等を使い、コアフォント依存を回避できる (distro 非依存)。
        |  # "monospace" は fontconfig の汎用エイリアス (空白を含まないので語分割で壊れない)。
        |  # -geometry COLSxROWS+0+0 で左上に配置 (xterm/urxvt はこの書式、lxterminal は別書式)。
        |  TERM_ARGS=""
        |  case "${d}GUI_TERM_BIN" in
        |    xterm) TERM_ARGS="-fa monospace -fs 11 -geometry ${d}{COLS}x${d}{ROWS}+0+0" ;;
        |    # ⚠ urxvt も **Xft を明示する** (0.8.343)。既定はコアフォント 'fixed' で、無い distro では
        |    # `unable to load base fontset` を吐いて**即死**する (窓が出ないだけで GUI は立つので
        |    # 原因が見えない)。xft: 指定はコアフォントを一切見に行かない。⚠ TERM_ARGS は語分割前提で
        |    # 展開するので、**空白を含む書き方 (xft:monospace:pixelsize=16 以外) にしないこと**。
        |    urxvt) TERM_ARGS="-fn xft:monospace:size=11 -geometry ${d}{COLS}x${d}{ROWS}+0+0" ;;
        |    lxterminal) TERM_ARGS="--geometry=${d}{COLS}x${d}{ROWS}" ;;
        |    # Konsole は DBus session が必須。`--separate` で IPC fallback を回避し、
        |    # `--nofork` で foreground 起動 (setsid のため backgrounded 状態を維持)、
        |    # `-e ${d}SHELL` で実行シェルを明示。`--hide-*` は古い konsole で arg parse 失敗のため省略。
        |    konsole) TERM_ARGS="--separate --nofork -e ${d}{SHELL:-/bin/sh}" ;;
        |  esac
        |  # ⚠ セッションバスと XDG_RUNTIME_DIR は、0.8.498 から**端末の種類に関係なく**
        |  #    start_session_bus が openbox より前に用意している (ここでは触らない)。
        |  #    konsole 固有の面倒を見るのはこの下のブロックだけ。
        |  if [ "${d}GUI_TERM_BIN" = "konsole" ]; then
        |    # 最終救済: ensure_pkgs で /usr/lib に展開できなかった場合に備え、
        |    # PySide6 / 他経路で持ち込まれた libQt6QuickWidgets.so.6 を LD_LIBRARY_PATH に積む。
        |    # ABI 不一致のリスクはあるが、何も入っていない状態より起動成功率が上がるので試す価値あり。
        |    # 候補パス: PySide6 venv, Anaconda 等の Qt6 同梱 (FOSS app 外で利用者が入れている可能性)
        |    if [ ! -f /usr/lib/libQt6QuickWidgets.so.6 ]; then
        |      QT_FALLBACK=""
        |      for cand in /root/venv/lib/python*/site-packages/PySide6/Qt/lib \
        |                  /root/.venv/lib/python*/site-packages/PySide6/Qt/lib \
        |                  /usr/lib/python*/site-packages/PySide6/Qt/lib \
        |                  /opt/*/lib/python*/site-packages/PySide6/Qt/lib \
        |                  /home/*/.venv*/lib/python*/site-packages/PySide6/Qt/lib; do
        |        [ -d "${d}cand" ] || continue
        |        if [ -f "${d}cand/libQt6QuickWidgets.so.6" ]; then
        |          QT_FALLBACK="${d}cand"; break
        |        fi
        |      done
        |      if [ -n "${d}QT_FALLBACK" ]; then
        |        echo "${strings.qtFallback} ${d}QT_FALLBACK"
        |        export LD_LIBRARY_PATH="${d}QT_FALLBACK:${d}{LD_LIBRARY_PATH:-}"
        |      else
        |        # find は遅いが最後の手段。範囲を /usr と /opt と /root に限定。
        |        FOUND=${d}(find /usr /opt /root -maxdepth 8 -name 'libQt6QuickWidgets.so.6' -print 2>/dev/null | head -1)
        |        if [ -n "${d}FOUND" ]; then
        |          QT_FALLBACK_DIR=${d}(dirname "${d}FOUND")
        |          echo "${strings.qtFallbackFound} ${d}QT_FALLBACK_DIR"
        |          export LD_LIBRARY_PATH="${d}QT_FALLBACK_DIR:${d}{LD_LIBRARY_PATH:-}"
        |        fi
        |      fi
        |    fi
        |    # 起動診断: 最終 env と konsole バージョンを log 先頭に残す → ユーザーが見やすい。
        |    {
        |      echo "=== konsole launch diagnostic ==="
        |      echo "DISPLAY=${d}DISPLAY  DBUS_SESSION_BUS_ADDRESS=${d}{DBUS_SESSION_BUS_ADDRESS:-(unset)}"
        |      echo "XDG_RUNTIME_DIR=${d}XDG_RUNTIME_DIR"
        |      echo "LD_LIBRARY_PATH=${d}{LD_LIBRARY_PATH:-(unset)}"
        |      echo "libQt6QuickWidgets.so.6 in /usr/lib: ${d}([ -f /usr/lib/libQt6QuickWidgets.so.6 ] && echo yes || echo no)"
        |      konsole --version 2>&1 | head -1
        |      echo "TERM_ARGS=${d}TERM_ARGS"
        |      echo "=================================="
        |    } > "/tmp/z2gui-term-${d}{DISPLAY_NUM}.log"
        |  fi
        |  # Z2_NO_TERM=1 のときは端末 (xterm 等) を起動しない (P3 = z2run 経由用)。
        |  # z2run は「ユーザーが指定した GUI アプリだけ」を出したいので、xterm が同時に出ると邪魔。
        |  # 🖥 ボタンの通常起動 (Z2_NO_TERM 未設定) では従来どおり端末も起動して操作起点にする。
        |  if [ "${d}{Z2_NO_TERM:-0}" = "1" ]; then
        |    echo "${strings.noTermFlag}"
        |  elif has "${d}GUI_TERM_BIN"; then
        |    # konsole の場合は事前 diagnostic を上書きしないよう `>>` で append。他端末は `>` truncate。
        |    if [ "${d}GUI_TERM_BIN" = "konsole" ]; then
        |      setsid "${d}GUI_TERM_BIN" ${d}TERM_ARGS </dev/null >>"/tmp/z2gui-term-${d}{DISPLAY_NUM}.log" 2>&1 &
        |    else
        |      setsid "${d}GUI_TERM_BIN" ${d}TERM_ARGS </dev/null >"/tmp/z2gui-term-${d}{DISPLAY_NUM}.log" 2>&1 &
        |    fi
        |    echo ${d}! >> "${d}PIDFILE" 2>/dev/null
        |  else
        |    echo "${strings.terminalNotFound} (${d}GUI_TERM_BIN)"
        |  fi
        |  echo "${strings.ready} (RFB 127.0.0.1:${d}RFBPORT)"
        |  # 端末が**起動直後に死んでいたら理由を出す** (0.8.343)。窓が出ないだけだと
        |  # 「GUI は映るのに端末が無い」としか分からず、原因 (フォント / Qt / DBus) に辿り着けない。
        |  # 正常に動いている端末はここへ何も書かないので、中身があること自体が異常の印。
        |  # (konsole だけは診断を先頭に書いてあるので、その分は常に出る。)
        |  if [ "${d}{Z2_NO_TERM:-0}" != "1" ] && has "${d}GUI_TERM_BIN"; then
        |    sleep 3
        |    if [ -s "/tmp/z2gui-term-${d}{DISPLAY_NUM}.log" ]; then
        |      echo "${strings.terminalLog} (/tmp/z2gui-term-${d}{DISPLAY_NUM}.log):"
        |      tail -n 8 "/tmp/z2gui-term-${d}{DISPLAY_NUM}.log" 2>/dev/null
        |    fi
        |  fi
        |  # 保険 (0.8.351): それでも窓が map されないまま残ったときのために、端末を起こした
        |  # 後に **openbox を 1 回だけ突く**。`openbox --reconfigure` は動いている openbox へ
        |  # X の ClientMessage を送るので、待ち行列に溜まっていた MapRequest がここで
        |  # まとめて処理される。⚠ 突く手段に xprop 等を使わないこと (どの distro でも
        |  # 導入対象に入っておらず、無い環境では保険が効かない)。openbox は GUI 一式に
        |  # 必ず含まれるので、これなら確実に手元にある。
        |  # ⚠ 単に X へ接続するだけでは駄目 (openbox は接続を通知されない。実機で xprop を
        |  #    何度叩いても窓は出なかった)。**openbox 自身が選んでいるイベント**を送ること。
        |  if [ "${d}{Z2_NO_TERM:-0}" != "1" ] && has "${d}GUI_TERM_BIN"; then
        |    openbox --reconfigure >/dev/null 2>&1 || true
        |  fi
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
