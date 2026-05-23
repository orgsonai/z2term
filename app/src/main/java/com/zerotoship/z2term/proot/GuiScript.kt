package com.zerotoship.z2term.proot

/**
 * z2term 内蔵 VNC クライアントが接続する RFB(VNC) ポート。
 * Xvnc は `:1` で起動するので RFB ポートは 5900 + 1 = 5901。
 * **127.0.0.1 のみで待ち受け**、外部 NIC には bind しない (loopback 限定で安全)。
 */
const val Z2TERM_VNC_PORT = 5901

/** 仮想 X ディスプレイ番号 (`Xvnc :1`)。 */
const val Z2TERM_VNC_DISPLAY = 1

/** GUI 起動に必要な Alpine パッケージ (オンデマンド `apk add`)。 */
const val Z2TERM_GUI_PACKAGES = "tigervnc openbox xterm font-noto ttf-dejavu"

/**
 * Linux GUI ランチャ `z2gui` スクリプト。
 *
 * PRoot は root もハードウェアフレームバッファも使えないため、**仮想 X サーバ Xvnc**
 * を立て、その画面を VNC(RFB) で z2term の内蔵クライアントへ転送する構成にする
 * (詳細は docs/M8-GUI-HANDOFF.md)。
 *
 * これを `/usr/local/bin/z2gui` に配置 (ProotLauncher.ensureGuiScript) することで、
 * 端末から、または z2term の GUI セッションから次のように使える:
 *  - `z2gui` / `z2gui start [WxH]` … Xvnc + openbox + xterm を起動 (未導入なら自動 apk add)
 *  - `z2gui 1080x2160`             … 解像度を直接指定して起動
 *  - `z2gui stop`                  … Xvnc/WM を停止
 *  - `z2gui status`               … 起動状態を表示
 *  - `z2gui install`              … GUI 一式を導入するだけ
 *
 * RFB は `-SecurityTypes None -localhost` で認証なし・loopback 限定。z2term は
 * 127.0.0.1:[Z2TERM_VNC_PORT] へ接続する。
 */
fun z2guiScript(
    rfbPort: Int = Z2TERM_VNC_PORT,
    display: Int = Z2TERM_VNC_DISPLAY,
    packages: String = Z2TERM_GUI_PACKAGES,
    defaultGeometry: String = "1280x720"
): String {
    val d = "${'$'}"  // シェルの $ (Kotlin テンプレートと衝突しないように)
    return """
        |#!/bin/sh
        |# z2term: Linux GUI ランチャ (Xvnc + openbox + アプリ)。
        |# RFB は 127.0.0.1 のみで待ち受け (z2term 内蔵クライアントが接続)。外部公開しない。
        |#   使い方: z2gui [start [WxH] | stop | status | install]
        |DISPLAY_NUM=$display
        |DISP=":$display"
        |RFBPORT=$rfbPort
        |DEFAULT_GEOM="$defaultGeometry"
        |PKGS="$packages"
        |export DISPLAY="${d}DISP"
        |export HOME="${d}{HOME:-/root}"
        |
        |has() { command -v "${d}1" >/dev/null 2>&1; }
        |
        |install_pkgs() {
        |  if ! has apk; then
        |    echo "❌ apk が見つかりません (現状 Alpine のみ対応)。"; return 1
        |  fi
        |  echo "📦 GUI 一式を導入します: ${d}PKGS"
        |  apk update && apk add --no-cache ${d}PKGS
        |}
        |
        |ensure_pkgs() {
        |  if has Xvnc && has openbox; then return 0; fi
        |  install_pkgs
        |}
        |
        |x_running() { [ -e "/tmp/.X11-unix/X${d}{DISPLAY_NUM}" ]; }
        |
        |stop_x() {
        |  if has vncserver; then vncserver -kill "${d}DISP" >/dev/null 2>&1; fi
        |  # pkill/pidof が無い最小 rootfs でも効くよう /proc を直接走査して停止。
        |  for c in /proc/[0-9]*/comm; do
        |    [ -r "${d}c" ] || continue
        |    case "${d}(cat "${d}c" 2>/dev/null)" in
        |      Xvnc|Xtigervnc|openbox|xterm)
        |        pid=${d}{c#/proc/}; pid=${d}{pid%/comm}; kill "${d}pid" 2>/dev/null ;;
        |    esac
        |  done
        |  rm -f "/tmp/.X${d}{DISPLAY_NUM}-lock" "/tmp/.X11-unix/X${d}{DISPLAY_NUM}" 2>/dev/null
        |}
        |
        |status_x() {
        |  if x_running; then
        |    echo "✅ GUI 起動中 (DISPLAY=${d}DISP, RFB 127.0.0.1:${d}RFBPORT)"
        |  else
        |    echo "⏹ GUI は停止中"
        |  fi
        |}
        |
        |start_x() {
        |  GEOM="${d}{1:-${d}DEFAULT_GEOM}"
        |  case "${d}GEOM" in
        |    *[0-9]x[0-9]*) : ;;
        |    *) echo "❌ 解像度の形式が不正: '${d}GEOM' (例 1280x720)"; exit 1 ;;
        |  esac
        |  ensure_pkgs || exit 1
        |  if ! has Xvnc; then echo "❌ Xvnc がありません (tigervnc 未導入)"; exit 1; fi
        |  stop_x
        |  mkdir -p /tmp/.X11-unix 2>/dev/null
        |  chmod 1777 /tmp/.X11-unix 2>/dev/null
        |  echo "▶ Xvnc 起動: ${d}GEOM @ ${d}DISP (RFB 127.0.0.1:${d}RFBPORT)"
        |  Xvnc "${d}DISP" -geometry "${d}GEOM" -depth 24 -SecurityTypes None -localhost -rfbport "${d}RFBPORT" >/tmp/z2gui-xvnc.log 2>&1 &
        |  i=0
        |  while [ ${d}i -lt 50 ]; do
        |    x_running && break
        |    i=${d}((i+1)); sleep 0.1
        |  done
        |  if ! x_running; then
        |    echo "❌ Xvnc 起動失敗。ログ:"; cat /tmp/z2gui-xvnc.log 2>/dev/null; exit 1
        |  fi
        |  openbox >/tmp/z2gui-wm.log 2>&1 &
        |  has xterm && (xterm >/dev/null 2>&1 &)
        |  echo "✅ GUI 準備完了。z2term の GUI タブから 127.0.0.1:${d}RFBPORT に接続してください。"
        |  # proot --kill-on-exit 対策: ここでブロックし続けることで Xvnc/WM を生かす。
        |  # GUI セッションは launch(command="z2gui start WxH") で起動するため、ここで
        |  # 即 return すると proot が終了し Xvnc も道連れに殺される。z2gui stop で殺されるか
        |  # proot 自体が終了するまで待機する。
        |  wait
        |}
        |
        |ACTION="${d}{1:-start}"
        |case "${d}ACTION" in
        |  start)   start_x "${d}2" ;;
        |  stop)    stop_x; echo "⏹ 停止しました" ;;
        |  status)  status_x ;;
        |  install) install_pkgs ;;
        |  *[0-9]x[0-9]*) start_x "${d}ACTION" ;;
        |  *) echo "使い方: z2gui [start [WxH] | stop | status | install]" ;;
        |esac
    """.trimMargin() + "\n"
}
