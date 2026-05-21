package com.zerotoship.z2term.proot

/** PC から端末へ SSH 接続するときの待受ポート (1024 未満は Android kernel が拒否)。 */
const val Z2TERM_SSHD_PORT = 2222

/**
 * dropbear (SSH サーバ) を起動する POSIX sh スクリプト。
 *
 * OpenSSH の `/usr/sbin/sshd` は proot 環境で **権限分離 (privsep) に失敗**し、
 * さらに新しめの OpenSSH では `sshd_config` の `UsePrivilegeSeparation` が
 * 「Bad configuration option」になって起動すらできない。よって proot 下でも
 * 安定動作する dropbear を使う。
 *
 * この内容を `/usr/local/sbin/sshd` に配置する (ProotLauncher) ことで、端末から
 * `sshd` と打つだけで dropbear が立ち上がる (PATH 上 /usr/local/sbin が優先)。
 *
 *  - dropbear 未導入なら distro のパッケージマネージャで自動 install
 *  - 既存 dropbear を pkill / pidof / pidfile / /proc 走査で確実に停止 (ポート競合回避)
 *  - ホスト鍵が無ければ生成、pidfile のプロセス生存で起動判定、失敗時はログ表示
 *  - root にパスワードが無ければ警告 (dropbear は空パスワード接続を拒否)
 */
fun dropbearBootstrapScript(port: Int = Z2TERM_SSHD_PORT): String {
    val d = "${'$'}"  // シェルの $ (Kotlin テンプレートと衝突しないように)
    return """
        |#!/bin/sh
        |# z2term: dropbear (SSH server) 起動スクリプト (OpenSSH sshd は proot 不可)
        |PORT=$port
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
        |
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
        |dropbear -p "${d}PORT" -R -E -P /tmp/dropbear.pid 2>>/tmp/dropbear.log
        |sleep 1
        |
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
