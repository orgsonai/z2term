#!/usr/bin/env bash
# docs/images のスクリーンショットを実機から撮る。
#
# PC で実行する。adb で繋いだ端末の画面を撮り、docs/images/<名前>.png に保存する。
# どの画面を開くかは人が操作し、Enter で撮影する (タップ座標の決め打ちは端末ごとに壊れるため)。
# 通知シェード・クイック設定を開く、ホームへ戻る、時計や電池表示の固定、切り抜きはこのスクリプトが行う。
#
# 使い方:
#   scripts/doc-screenshots.sh                    # 全場面を順に撮る (各場面で s を押すと飛ばす)
#   scripts/doc-screenshots.sh --list             # 場面の一覧
#   scripts/doc-screenshots.sh qs-tiles search    # 指定した場面だけ
#
# 環境変数:
#   ADB_SERIAL  複数台つないでいるときの端末シリアル
#   PHONE_SSH   端末内 Linux 環境へ入る ssh の宛先。指定すると z2-* の準備コマンドを自動で流す。
#               未指定なら準備コマンドを表示するので、z2term のタブで実行してから Enter を押す。
#   OUT         保存先 (既定: このリポジトリの docs/images)
#   DEMO        1 = ステータスバーの時計 12:00・電池 100%・電波を固定する (既定) / 0 = しない
#   PKG         アプリの applicationId (既定: com.zerotoship.z2term)
#   TOOLBAR_DP  ツールバーの高さ (既定: 48)。toolbar.png の切り抜きがずれるときに変える
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
OUT=${OUT:-$ROOT/docs/images}
DEMO=${DEMO:-1}
PKG=${PKG:-com.zerotoship.z2term}
TOOLBAR_DP=${TOOLBAR_DP:-48}

SCENES=(
  terminal-overview keyboard-ascii keyboard-kana search
  sheet-automation when-add sheet-servers qr-tools relay-share
  notify-ask qs-tiles statusbar-icon
  widget-status widget-tail
  shot-terminal shot-gui
)
# 一覧には出すが、全場面の実行には含めない (terminal-overview から自動で切り出すため)
EXTRA_SCENES=(toolbar)

declare -A USED=(
  [terminal-overview]="guide-terminal.ja.html"
  [toolbar]="guide-terminal.ja.html"
  [keyboard-ascii]="guide-terminal.ja.html"
  [keyboard-kana]="guide-terminal.ja.html"
  [search]="guide-terminal.ja.html"
  [sheet-automation]="guide-when.ja.html"
  [when-add]="guide-when.ja.html"
  [sheet-servers]="guide-tile.ja.html"
  [qr-tools]="(新規) docs/*/QR-TOOLS.md"
  [relay-share]="(新規) docs/*/QR-TOOLS.md"
  [notify-ask]="guide-macro.ja.html"
  [qs-tiles]="guide-tile.ja.html"
  [statusbar-icon]="guide-tile.ja.html"
  [widget-status]="guide-tile.ja.html"
  [widget-tail]="guide-tile.ja.html"
  [shot-terminal]="README / index.html / F-Droid 1.png"
  [shot-gui]="README / index.html / guide-terminal / F-Droid 2.png"
)
declare -A DESC=(
  [terminal-overview]="端末画面の全体 (toolbar.png も切り出す)"
  [toolbar]="ツールバーの帯だけ撮り直す"
  [keyboard-ascii]="英字キーボードとフリック先の表示"
  [keyboard-kana]="日本語入力中の候補バー"
  [search]="画面内検索とスクロールバーの目盛り"
  [sheet-automation]="📜 → 自動化 → 自動化ルール"
  [when-add]="自動化ルールの追加 (きっかけの一覧)"
  [sheet-servers]="📜 → サーバー"
  [qr-tools]="QR ツール"
  [relay-share]="中継してファイル共有の設定画面"
  [notify-ask]="z2-ask の質問と返信欄 (通知シェード)"
  [qs-tiles]="クイック設定のタイル"
  [statusbar-icon]="ステータスバーの通知アイコン (切り抜き)"
  [widget-status]="ホーム画面の状態ウィジェット"
  [widget-tail]="ホーム画面のライブ tail ウィジェット"
  [shot-terminal]="代表画像: 端末"
  [shot-gui]="代表画像: Linux GUI"
)

adb_() { adb ${ADB_SERIAL:+-s "$ADB_SERIAL"} "$@"; }
ask() { read -r -p "$1" REPLY </dev/tty; }
sq() { printf "'%s'" "${1//\'/\'\\\'\'}"; }

usage() { awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "${BASH_SOURCE[0]}"; }

list_scenes() {
  local s
  printf '%-18s %-44s %s\n' "場面" "内容" "使う場所"
  for s in "${SCENES[@]}" "${EXTRA_SCENES[@]}"; do
    printf '%-18s %-44s %s\n' "$s" "${DESC[$s]}" "${USED[$s]}"
  done
}

# ---- 端末側の準備 ----------------------------------------------------------
read -r -d '' REMOTE_PRE <<'SH' || true
export PATH=/usr/local/bin:$PATH
doc() {
  n=$(z2-session list 2>/dev/null | awk -F '\t' '$5 == "doc" { print $1; exit }')
  [ -n "$n" ] || n=$(z2-session new doc | cut -f1)
  z2-session send "$n" "$1" --enter
}
SH

# z2term の中で走らせるコマンド: phone_run 説明 コマンド
# PHONE_SSH があれば流し、無ければ表示する。失敗しても撮影は続ける。
phone_run() {
  if [[ -z ${PHONE_SSH:-} ]]; then
    echo "  z2term のタブで実行 → $2"
  elif ssh -n -o ConnectTimeout=5 "$PHONE_SSH" "$REMOTE_PRE"$'\n'"$2" >/dev/null 2>&1; then
    echo "  準備: $1"
  else
    echo "  ⚠ 準備コマンドが失敗しました。z2term のタブで実行してください → $2"
  fi
}
# 「doc」という名前の端末タブ (無ければ作る) にコマンドを打ち込んで実行する。
doc_run() {
  if [[ -n ${PHONE_SSH:-} ]]; then
    phone_run "「doc」タブで $1 を実行しました" "doc $(sq "$1")"
  else
    echo "  z2term の端末タブ (撮影用に新しく開くと良い) で実行 → $1"
  fi
}

sysui() { adb_ shell cmd statusbar "$@" >/dev/null 2>&1 || true; }
home() { adb_ shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true; }
front() { adb_ shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true; }

demo() { adb_ shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null 2>&1 || true; }
demo_notifications() { [[ $DEMO != 1 ]] || demo notifications -e visible "$1"; }
DEMO_PREV=
demo_on() {
  [[ $DEMO == 1 ]] || return 0
  DEMO_PREV=$(adb_ shell settings get global sysui_demo_allowed | tr -d '\r')
  adb_ shell settings put global sysui_demo_allowed 1
  demo enter
  demo clock -e hhmm 1200
  demo battery -e level 100 -e plugged false
  demo network -e wifi show -e level 4 -e fully true
  demo network -e mobile show -e level 4 -e datatype none
  demo notifications -e visible false
}

TMP=$(mktemp -d)
cleanup() {
  if [[ -n $DEMO_PREV ]]; then
    demo exit
    if [[ $DEMO_PREV == null ]]; then
      adb_ shell settings delete global sysui_demo_allowed >/dev/null 2>&1 || true
    else
      adb_ shell settings put global sysui_demo_allowed "$DEMO_PREV" >/dev/null 2>&1 || true
    fi
  fi
  rm -rf "$TMP"
}
trap cleanup EXIT

awake() {
  until adb_ shell dumpsys power | grep -q 'mWakefulness=Awake'; do
    ask "  端末が消灯しています。画面をつけてロックを解除し Enter > "
  done
}

# ---- 撮影・保存 ------------------------------------------------------------
SAVED=()

# Enter で撮影 → 撮り直すか聞く。保存したら 0、飛ばしたら 1 を返す。
shoot() {
  local name=$1 dst="$OUT/$1.png" dims
  while true; do
    ask "  [Enter]=撮影  s=飛ばす  q=終了 > "
    case $REPLY in
      s|S) echo "  → 飛ばしました"; return 1 ;;
      q|Q) exit 0 ;;
    esac
    awake
    adb_ exec-out screencap -p > "$TMP/$name.png"
    if ! dims=$(python3 - "$TMP/$name.png" "$dst" <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1])
im.load()
im.convert("RGB").save(sys.argv[2], optimize=True)
print(f"{im.width}x{im.height}")
PY
    ); then
      echo "  ⚠ 画像を保存できませんでした。もう一度撮ります"
      continue
    fi
    ask "  保存: $dst ($dims)  [Enter]=次へ  r=撮り直し > "
    if [[ $REPLY != r && $REPLY != R ]]; then
      SAVED+=("$dst")
      return 0
    fi
  done
}

# 保存済みの画像から矩形を切り出す: crop 元名 先名 x y w h
crop() {
  python3 - "$OUT/$1.png" "$OUT/$2.png" "$3" "$4" "$5" "$6" <<'PY'
import sys
from PIL import Image
x, y, w, h = map(int, sys.argv[3:7])
Image.open(sys.argv[1]).crop((x, y, x + w, y + h)).save(sys.argv[2], optimize=True)
PY
  [[ $1 == "$2" ]] || SAVED+=("$OUT/$2.png")
  echo "  切り出し: $OUT/$2.png (${5}x${6})"
}

say() {
  local line
  for line in "$@"; do echo "  $line"; done
}

scene() {
  local name=$1
  N=$((N + 1))
  echo
  echo "━━ [$N/${#TARGETS[@]}] $name.png — ${DESC[$name]}  (${USED[$name]})"
  case $name in
    terminal-overview)
      front
      doc_run "clear; z2version; z2help"
      say "「doc」タブを表示し、端末をタップしてキーボードを出す。" \
          "上からツールバー / タブ / 端末 / キーボードが全部入る状態で撮る。"
      if shoot "$name"; then
        crop "$name" toolbar 0 "$SB" "$W" "$TB"
        echo "  ※ toolbar.png の帯がずれていたら TOOLBAR_DP を変えて「toolbar」場面だけ撮り直す"
      fi
      ;;
    toolbar)
      front
      say "端末画面を表示する (ツールバーのボタンが全部見える状態)。"
      if shoot "$name"; then
        crop "$name" toolbar 0 "$SB" "$W" "$TB"
      fi
      ;;
    keyboard-ascii)
      front
      say "英字キーボードを表示。キーの角にフリック先の緑の字が見える状態。"
      shoot "$name"
      ;;
    keyboard-kana)
      front
      say "キーボード左の「あ」で日本語に切り替え、「へんかん」などを打って" \
          "候補バーが出た状態で撮る (確定しない)。"
      shoot "$name"
      ;;
    search)
      front
      doc_run "clear; z2help; z2help"
      say "「doc」タブで 🔍 を押し「z2-」を検索。" \
          "右のスクロールバーに見つかった場所の目盛りが出た状態で撮る。"
      shoot "$name"
      ;;
    sheet-automation)
      front
      say "📜 →「自動化」→ 左の「自動化ルール」。ルールが数件並んだ状態。" \
          "ルールのコマンドに個人のパスや宛先が映っていないか確認。"
      shoot "$name"
      ;;
    when-add)
      front
      say "自動化ルールの「追加」を押し、きっかけの一覧が開いた状態。"
      shoot "$name"
      ;;
    sheet-servers)
      front
      say "📜 →「サーバー」。登録したサーバーと稼働状態が並んだ状態。"
      shoot "$name"
      ;;
    qr-tools)
      front
      say "⚙設定 →「QRツール」→「QRツールを開く」。内容欄に https://example.com を入れ、「開く」ボタンが出た状態。"
      shoot "$name"
      ;;
    relay-share)
      front
      say "📜 →「サーバー」の一番下「中継してファイル共有」。" \
          "公開 HTTPS URL に https://share.example、ポートに 8080 を入れた状態 (保存・共有開始はしない)。" \
          "SSH 接続先に実際のホスト名が出る場合は、例示用の接続先を選んでから撮る。"
      shoot "$name"
      ;;
    notify-ask)
      phone_run "z2-ask で質問の通知を出しました" \
        'nohup z2-ask -t 300 "スクリーンショット用の質問です。メモを残しますか?" >/dev/null 2>&1 &'
      sysui expand-notifications
      say "通知シェードの z2-ask の質問で「返信」を押し、入力欄が出た状態 (送信しない)。" \
          "他のアプリの通知の中身が映るなら、先に消しておく。"
      shoot "$name" || true
      sysui collapse
      echo "  ※ 質問は 5 分で自動的に終わります"
      ;;
    qs-tiles)
      sysui expand-settings
      say "z2term のタイルが並んだページまで送る (編集画面ではなく通常の表示)。"
      shoot "$name" || true
      sysui collapse
      ;;
    statusbar-icon)
      demo_notifications true
      phone_run "通知を 1 件出しました" 'z2-notify "Z2Term" "ステータスバーのアイコン確認用"'
      home
      say "通知アイコンを自分のドット絵にしてある状態 (z2-icon) で、ホーム画面を表示。" \
          "ステータスバーの左半分を切り出す。"
      if shoot "$name"; then
        crop "$name" "$name" 0 0 $((W / 2)) "$SB"
      fi
      demo_notifications false
      ;;
    widget-status)
      home
      say "状態ウィジェット (SSH の宛先・稼働数・マクロのボタン) を置いたホーム画面。" \
          "宛先に実際のホスト名が出る場合は注意。"
      shoot "$name"
      ;;
    widget-tail)
      home
      say "ライブ tail ウィジェット (ファイルの末尾が出ている) を置いたホーム画面。"
      shoot "$name"
      ;;
    shot-terminal)
      front
      say "端末らしい代表の 1 枚 (README・トップページ・F-Droid の 1 枚目)。"
      if shoot "$name"; then
        cp "$OUT/$name.png" "$ROOT/metadata/en-US/images/phoneScreenshots/1.png"
        SAVED+=("$ROOT/metadata/en-US/images/phoneScreenshots/1.png")
      fi
      ;;
    shot-gui)
      front
      say "GUI タブで Linux のデスクトップアプリを表示 (z2gui / z2run)。F-Droid の 2 枚目にも使う。"
      if shoot "$name"; then
        cp "$OUT/$name.png" "$ROOT/metadata/en-US/images/phoneScreenshots/2.png"
        SAVED+=("$ROOT/metadata/en-US/images/phoneScreenshots/2.png")
      fi
      ;;
  esac
}

# ---- 本体 ------------------------------------------------------------------
case ${1:-} in
  -h|--help) usage; exit 0 ;;
  --list) list_scenes; exit 0 ;;
esac

if (($#)); then
  TARGETS=("$@")
  for s in "${TARGETS[@]}"; do
    [[ -n ${DESC[$s]:-} ]] || { echo "知らない場面です: $s (--list で一覧)" >&2; exit 2; }
  done
else
  TARGETS=("${SCENES[@]}")
fi

command -v adb >/dev/null || { echo "adb が見つかりません" >&2; exit 1; }
python3 -c 'import PIL' 2>/dev/null || { echo "python3 の Pillow (PIL) が必要です" >&2; exit 1; }
[[ $(adb_ get-state 2>/dev/null) == device ]] || { echo "端末が adb で見えません (ADB_SERIAL を確認)" >&2; exit 1; }
if [[ -n ${PHONE_SSH:-} ]] && ! ssh -n -o ConnectTimeout=5 -o BatchMode=yes "$PHONE_SSH" true 2>/dev/null; then
  echo "⚠ ssh $PHONE_SSH に繋がりません。準備コマンドは表示だけにします" >&2
  PHONE_SSH=
fi

read -r W H < <(adb_ shell wm size | tr -d '\r' | sed -nE 's/.*size: ([0-9]+)x([0-9]+).*/\1 \2/p' | tail -1)
DENSITY=$(adb_ shell wm density | tr -d '\r' | sed -nE 's/.*density: ([0-9]+).*/\1/p' | tail -1)
SB=$(adb_ shell dumpsys window | tr -d '\r' \
  | sed -nE 's/.*type=statusBars frame=\[0,0\]\[[0-9]+,([0-9]+)\].*/\1/p' | head -1)
SB=${SB:-$(( (24 * DENSITY + 80) / 160 ))}
TB=$(( (TOOLBAR_DP * DENSITY + 80) / 160 ))
VERSION=$(adb_ shell dumpsys package "$PKG" | tr -d '\r' | sed -nE 's/.*versionName=(.*)/\1/p' | head -1)

mkdir -p "$OUT"
awake
demo_on

echo "端末 ${W}x${H} / 密度 $DENSITY / ステータスバー ${SB}px / ツールバー ${TB}px"
echo "アプリ $PKG ${VERSION:-(未インストール)} / 保存先 $OUT"
if [[ -n ${PHONE_SSH:-} ]]; then
  echo "準備コマンド: ssh $PHONE_SSH で自動実行"
else
  echo "準備コマンド: 表示のみ (z2term で手入力)"
fi
echo "⚠ 実際の IP・ホスト名・通知の中身・クリップボードの履歴が映らないようにしてください"

N=0
for s in "${TARGETS[@]}"; do
  scene "$s" || true
done

echo
echo "━━ 完了。保存したファイル: ${#SAVED[@]} 枚"
if ((${#SAVED[@]})); then
  printf '  %s\n' "${SAVED[@]}"
fi
