package com.zerotoship.z2term.proot

/**
 * `sshd` が設定/引数が無いときに使う既定ポート。
 * 1024 未満は proot(非root) で bind できないため高ポートを既定にする。
 * (sshd_config の Port や `-p` 指定があればそちらが優先)
 */
const val Z2TERM_SSHD_PORT = 2222

/**
 * `sshd` 互換ラッパースクリプト (バックエンドは dropbear)。
 *
 * OpenSSH の `/usr/sbin/sshd` は proot 環境で **権限分離 (privsep) に失敗**し、
 * さらに新しめの OpenSSH では `sshd_config` の `UsePrivilegeSeparation` が
 * 「Bad configuration option」になって起動すらできない。よって proot 下でも
 * 安定動作する dropbear を使う。
 *
 * これを `/usr/local/sbin/sshd` に配置 (ProotLauncher) することで、端末から
 * `sshd` と打つと **通常の sshd のように振る舞う**:
 *  - ポートは `-p` / `-o Port=N` 指定 → なければ `/etc/ssh/sshd_config` の `Port`
 *    → それも無ければ既定 ([Z2TERM_SSHD_PORT]) の優先順で決定
 *  - `-f <config>` で別の設定ファイルを参照、`-D`/`-d` で前景起動、`-t` で設定確認
 *  - dropbear 未導入なら自動 install、既存 dropbear を確実に停止してから起動
 *
 * 注: dropbear が解釈できるのは実質 Port のみ。sshd_config のそれ以外のディレクティブ
 * (PermitRootLogin 等) は反映されない (dropbear 既定: root ログイン・パスワード認証許可)。
 */
fun dropbearBootstrapScript(defaultPort: Int = Z2TERM_SSHD_PORT): String {
    val d = "${'$'}"  // シェルの $ (Kotlin テンプレートと衝突しないように)
    return """
        |#!/bin/sh
        |# z2term: sshd 互換ラッパー (バックエンド dropbear。OpenSSH sshd は proot 不可)
        |DEFAULT_PORT=$defaultPort
        |CONFIG=/etc/ssh/sshd_config
        |PORT=""
        |FOREGROUND=""
        |TESTONLY=""
        |
        |# sshd 互換の主要オプションを解釈する。
        |while [ ${d}# -gt 0 ]; do
        |  case "${d}1" in
        |    -p) PORT="${d}2"; shift 2 ;;
        |    -p?*) PORT="${d}{1#-p}"; shift ;;
        |    -f) CONFIG="${d}2"; shift 2 ;;
        |    -f?*) CONFIG="${d}{1#-f}"; shift ;;
        |    -o) case "${d}2" in [Pp]ort*) PORT=${d}(printf '%s' "${d}2" | tr -cd '0-9') ;; esac; shift 2 ;;
        |    -D|-d) FOREGROUND=1; shift ;;
        |    -t|-T) TESTONLY=1; shift ;;
        |    -h) shift 2 ;;
        |    -h?*) shift ;;
        |    --) shift; break ;;
        |    *) shift ;;
        |  esac
        |done
        |
        |# ポート決定: -p / -o Port=N  →  sshd_config の Port  →  既定
        |if [ -z "${d}PORT" ] && [ -r "${d}CONFIG" ]; then
        |  PORT=${d}(awk 'tolower(${d}1)=="port" && ${d}2 ~ /^[0-9]+${d}/ {print ${d}2; exit}' "${d}CONFIG")
        |fi
        |[ -z "${d}PORT" ] && PORT=${d}DEFAULT_PORT
        |
        |case "${d}PORT" in
        |  ''|*[!0-9]*) echo "❌ ポート番号が不正です: '${d}PORT'"; exit 1 ;;
        |esac
        |
        |if [ -n "${d}TESTONLY" ]; then
        |  echo "sshd(dropbear) 設定 OK: port=${d}PORT (config: ${d}CONFIG)"
        |  exit 0
        |fi
        |
        |if ! command -v dropbear >/dev/null 2>&1; then
        |  echo "📦 dropbear が無いので導入します…"
        |  if command -v pacman >/dev/null 2>&1; then
        |    pacman -Sy --noconfirm dropbear
        |  elif command -v apt-get >/dev/null 2>&1; then
        |    apt-get update && { apt-get install -y dropbear-bin || apt-get install -y dropbear; }
        |  elif command -v apk >/dev/null 2>&1; then
        |    apk add --no-cache dropbear
        |  elif command -v dnf >/dev/null 2>&1; then
        |    dnf install -y dropbear
        |  elif command -v zypper >/dev/null 2>&1; then
        |    zypper --non-interactive install dropbear
        |  fi
        |fi
        |if ! command -v dropbear >/dev/null 2>&1; then
        |  echo "❌ dropbear を導入できませんでした。ネットワーク接続とパッケージ名を確認してください。"
        |  exit 1
        |fi
        |
        |mkdir -p /etc/dropbear
        |[ -f /etc/dropbear/dropbear_ed25519_host_key ] || dropbearkey -t ed25519 -f /etc/dropbear/dropbear_ed25519_host_key 2>/dev/null
        |[ -f /etc/dropbear/dropbear_rsa_host_key ] || dropbearkey -t rsa -s 2048 -f /etc/dropbear/dropbear_rsa_host_key 2>/dev/null
        |
        |# 既存 dropbear を確実に停止 (ポート競合「Address already in use」の回避)。
        |# pkill / pidof が無い最小 rootfs でも効くよう /proc を直接走査する。
        |pkill -x dropbear 2>/dev/null
        |for p in ${d}(pidof dropbear 2>/dev/null); do kill "${d}p" 2>/dev/null; done
        |[ -f /tmp/dropbear.pid ] && kill "${d}(cat /tmp/dropbear.pid 2>/dev/null)" 2>/dev/null
        |for c in /proc/[0-9]*/comm; do
        |  [ -r "${d}c" ] || continue
        |  if [ "${d}(cat "${d}c" 2>/dev/null)" = dropbear ]; then
        |    pid=${d}{c#/proc/}; pid=${d}{pid%/comm}; kill "${d}pid" 2>/dev/null
        |  fi
        |done
        |rm -f /tmp/dropbear.pid /tmp/dropbear.log
        |sleep 1
        |
        |if [ "${d}PORT" -lt 1024 ]; then
        |  echo "⚠️ ポート ${d}PORT は特権ポート。proot(非root)では bind できない可能性が高いです (1024 以上推奨)。"
        |fi
        |
        |if [ -n "${d}FOREGROUND" ]; then
        |  echo "▶ dropbear をフォアグラウンド起動 (Ctrl-C で停止): port ${d}PORT"
        |  exec dropbear -F -p "${d}PORT" -R -E
        |fi
        |
        |dropbear -p "${d}PORT" -R -E -P /tmp/dropbear.pid 2>>/tmp/dropbear.log
        |sleep 1
        |if [ -s /tmp/dropbear.pid ] && kill -0 "${d}(cat /tmp/dropbear.pid)" 2>/dev/null; then
        |  IP=${d}(ip -4 addr show 2>/dev/null | grep -oE 'inet [0-9.]+' | awk '{print ${d}2}' | grep -v '^127' | head -n1)
        |  echo "✅ dropbear listening on :${d}PORT  (root @ ${d}{IP:-端末IP})"
        |else
        |  echo "❌ dropbear 起動失敗。ログ:"
        |  cat /tmp/dropbear.log 2>/dev/null
        |fi
        |
        |grep -q '^root:[^!*:]' /etc/shadow 2>/dev/null || \
        |  echo "⚠️ root パスワード未設定です。'passwd' で設定してから接続してください。"
    """.trimMargin() + "\n"
}
