#!/bin/bash
# 端末内に NLA を要求する RDP サーバーを立てる（Windows も PC も不要の検証台）。
#
#   rdp-testbed.sh start    立てる
#   rdp-testbed.sh stop     止める
#   rdp-testbed.sh check    X.224 で NLA が要求されるか確かめる
#   rdp-testbed.sh probe    z2term の RdpTlsTransport で実 NLA 認証する
#   rdp-testbed.sh trace    FreeRDP の正解のやり取りを TRACE ログで採る
#
# 資格情報は検証用の捨て値。秘密ではないのでそのまま書いてよい。
set -u

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
STATE_DIR="${RDP_TESTBED_STATE_DIR:-$ROOT_DIR/build/rdp-testbed}"
USER_NAME=z2test
PASSWORD=z2pass
PORT=13389
DISPLAY_NUM=9
VNC_PORT=5909
LOG_LEVEL="${RDP_TESTBED_LOG_LEVEL:-INFO}"

SAM="$STATE_DIR/sam"
LOG="$STATE_DIR/server.log"
XLOG="$STATE_DIR/xvnc.log"
SERVER_PID="$STATE_DIR/server.pid"
XVNC_PID="$STATE_DIR/xvnc.pid"

prepare_state() {
  mkdir -p "$STATE_DIR"
}

pid_is_running() {
  local file="$1"
  local marker="$2"
  [ -f "$file" ] || return 1
  local pid
  pid="$(cat "$file")"
  [ -r "/proc/$pid/cmdline" ] || return 1
  tr '\0' ' ' < "/proc/$pid/cmdline" | grep -Fq "$marker"
}

display_is_ready() {
  python3 - "$DISPLAY_NUM" <<'PY'
import socket, sys
s = socket.socket(socket.AF_UNIX)
s.settimeout(1)
try:
    s.connect('/tmp/.X11-unix/X' + sys.argv[1])
except OSError:
    raise SystemExit(1)
finally:
    s.close()
PY
}

start() {
  prepare_state

  # 1. 映す画面を用意する（shadow server は X11 の画面をキャプチャして配信する）
  if ! display_is_ready; then
    # 前回が強制終了した場合にだけ残る一時 socket / lock。
    rm -f "/tmp/.X11-unix/X$DISPLAY_NUM" "/tmp/.X$DISPLAY_NUM-lock"
    nohup setsid /usr/sbin/Xvnc ":$DISPLAY_NUM" -geometry 1024x768 -depth 24 \
      -SecurityTypes None -rfbport "$VNC_PORT" -AlwaysShared > "$XLOG" 2>&1 &
    echo "$!" > "$XVNC_PID"
    sleep 4
    if ! display_is_ready; then
      echo "Xvnc の起動に失敗した: $XLOG" >&2
      return 1
    fi
  fi

  # 2. SAM（NTLM のユーザー名とハッシュ）を作る。
  /usr/bin/winpr-hash3 -u "$USER_NAME" -p "$PASSWORD" -f sam > "$SAM"
  chmod 600 "$SAM"

  # 3. NLA を強制し、高効率 codec を落とした RDP サーバーを立てる。
  if ! pid_is_running "$SERVER_PID" "freerdp-shadow-cli3"; then
    DISPLAY=":$DISPLAY_NUM" nohup setsid /usr/bin/freerdp-shadow-cli3 \
      /port:"$PORT" /sam-file:"$SAM" /sec:nla \
      -gfx -rfx -nsc \
      /log-level:"$LOG_LEVEL" > "$LOG" 2>&1 &
    echo "$!" > "$SERVER_PID"
    sleep 5
  fi

  if ! pid_is_running "$SERVER_PID" "freerdp-shadow-cli3"; then
    echo "RDP server の起動に失敗した: $LOG" >&2
    return 1
  fi

  echo "立てた:"
  echo "  RDP    127.0.0.1:$PORT   ユーザー $USER_NAME / パスワード $PASSWORD"
  echo "  画面   DISPLAY=:$DISPLAY_NUM （VNC でも $VNC_PORT で覗ける）"
  echo "  状態   $STATE_DIR"
}

stop_pid() {
  local name="$1"
  local file="$2"
  local marker="$3"
  if pid_is_running "$file" "$marker"; then
    echo "止める: $name ($(cat "$file"))"
    kill "$(cat "$file")" 2>/dev/null || true
  fi
  rm -f "$file"
}

stop() {
  stop_pid "RDP server" "$SERVER_PID" "freerdp-shadow-cli3"
  stop_pid "Xvnc" "$XVNC_PID" "Xvnc :$DISPLAY_NUM"
}

check() {
  python3 - "$PORT" <<'PY'
import socket, struct, sys
port = int(sys.argv[1])
# X.224 接続要求で SSL|HYBRID を要求する（RdpNegotiation.kt と同じ）。
neg  = struct.pack('<BBHI', 0x01, 0x00, 0x0008, 0x00000003)
x224 = bytes([6 + len(neg), 0xE0, 0, 0, 0, 0, 0]) + neg
tpkt = bytes([3, 0]) + struct.pack('>H', 4 + len(x224)) + x224
s = socket.create_connection(('127.0.0.1', port), timeout=6)
s.sendall(tpkt)
r = s.recv(64)
print('送信:', tpkt.hex(' '))
print('応答:', r.hex(' ') or '(空)')
if len(r) >= 19 and r[11] == 0x02:
    proto = struct.unpack('<I', r[15:19])[0]
    print('選ばれた方式:', {0: 'RDP(平文)', 1: 'TLS', 2: 'HYBRID(=NLA)', 8: 'HYBRID_EX'}.get(proto, hex(proto)))
    if proto not in (2, 8):
        raise SystemExit(1)
elif len(r) >= 19 and r[11] == 0x03:
    print('拒否 failureCode:', struct.unpack('<I', r[15:19])[0])
    raise SystemExit(1)
else:
    raise SystemExit('RDP negotiation response を解釈できない')
s.close()
PY
}

probe() {
  check
  Z2TERM_RDP_TESTBED=1 "$ROOT_DIR/scripts/gw.sh" :app:testDebugUnitTest \
    --tests com.zerotoship.z2term.gui.rdp.RdpTlsTransportLiveTest --rerun
}

# 正解の見本を採る。自前実装が詰まったとき、同じ場面の PDU を突き合わせる用。
trace() {
  prepare_state
  DISPLAY=":$DISPLAY_NUM" timeout 30 /usr/sbin/xfreerdp3 \
    /v:127.0.0.1:"$PORT" /u:"$USER_NAME" /p:"$PASSWORD" \
    /cert:ignore /log-level:TRACE > "$STATE_DIR/trace.log" 2>&1
  echo "採った: $STATE_DIR/trace.log ($(wc -l < "$STATE_DIR/trace.log") 行)"
}

case "${1:-}" in
  start) start ;;
  stop)  stop ;;
  check) check ;;
  probe) probe ;;
  trace) trace ;;
  *) echo "使い方: $0 {start|stop|check|probe|trace}" ;;
esac
