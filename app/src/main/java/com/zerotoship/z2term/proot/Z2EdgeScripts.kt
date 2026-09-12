package com.zerotoship.z2term.proot

/** CLI wrappers keep all arguments separate; definitions are updated atomically by the app. */
fun z2EdgeScripts(lang: String): Map<String, String> {
    val d = '$'
    val help = "awk 'NR>1 && /^#/ { sub(/^# ?/, \"\"); print; next } NR>1 && NF { exit }' \"${d}0\"; exit 0"
    val edgeHelp = if (lang == "ja") """
        |# Android 画面に浮かべるパネル。中身はシェルコマンドとテキスト定義です。
        |# z2-edge permission                     重ねて表示する許可の画面を開く
        |# z2-edge handle ID button --at 85%,60%   自由配置ボタンを定義（ドラッグ位置を保存）
        |# z2-edge handle ID bar --side right --offset 30% --length 6% --size 6 --open swipe
        |# z2-edge handle ID button --run 'z2-key back'  直接実行するボタン
        |# --open swipe|tap|both  --alpha 0.05..1  --label TEXT
        |# --bar-color auto|white|black  バーの自動色／白／黒。自動はAndroid 11以降＋ユーザー補助が必要。
        |# 板は項目だけを表示。余白長押しで設定。取っ手長押し後、動かさず離すと設定。
        |# バー幅: --size 2〜48dp、ボタン: 32〜96dp。バーは300ms長押し後に移動。
        |# z2-edge handle ID off                  取っ手を隠す
        |# z2-edge set ID:memo type=note file=~/memo.txt
        |# メモの色: note-background=#FFF4BD note-color=#000000（空値で自動へ戻す）
        |# z2-edge tab PARENT ID [LABEL]
        |# z2-edge delete ID
        |# z2-edge panel ID width=80% height=60% fit=content|fixed
        |# title=on|off close=on|off tabbar=off|on|auto add=on|off settings=on|off
        |# labels=on|off flow=vertical|horizontal|grid columns=auto|1..16 icon-size=16..192
        |# place=handle|left|right|top|bottom|center at=X%,Y%
        |# z2-edge panel ID 'actions-double-tap=launch:org.example.app|wait:500|swipe-up'
        |# actions-tap / actions-up / actions-down / actions-inward / actions-outward
        |# actions-up=scroll-variable  scroll-x=50 scroll-y=50
        |# z2-edge panel ID gesture-up="command" gesture-down="command" gesture-double-tap="command"
        |# z2-edge panel ID gesture-scroll=off|variable|fixed gesture-speed=600
        |# z2-edge panel ID gesture-range=640    最高速度までの距離32〜2000dp（既定160）。大きいほど緩やか。
        |# 上下スライドで可変速/固定速スクロール。速度は50〜40000dp/秒。タップで停止。
        |# z2-edge panel ID label=名前            パネルの定義を追加・更新
        |# z2-edge set ID:項目 type=text 'run=date' label=時計 every=30
        |# z2-edge get ID                        板の保存済み定義を取得（key=value）
        |# z2-edge get ID:項目 | list [ID] | remove ID:項目
        |# z2-edge on | off | toggle | status | reload     有効化・停止・状態・定義の再読込
        |# z2-edge open ID [--toggle] | close                パネルを開く・閉じる
        |# z2-edge push ID:項目 '文字列'           表示を外から更新（- なら標準入力）
        |# z2-edge state ID:項目 on|off           toggle / ON・OFFボタンの表示を更新
        |# z2-edge badge ID '87%'                 取っ手へ文字を表示（空文字で消去）
        |# 型: run / text / toggle / list / input / note。run が既定。
        |# toggle: run=切替コマンド state=状態を読むコマンド（on/off・1/0・true/false）。
        |# run: button-state=on でON/OFFをボタンの枠と背景色で表示。off=OFF用コマンド（省略時はrunと同じ）。
        |# button-source=auto|torch|screen|process|remember（既定auto）。torch/screenは実態に連動。
        |# autoは直接コマンドとマクロ内のAPI操作から連動先を取得。processは実行中ON、終了・停止時OFF。
        |# state=状態取得コマンド（任意）は連動先より優先。未対応の動作は状態取得または更新通知を設定。
        |# ON/OFFボタンは開き直し・再起動でも状態を保持。コマンド定義を変えると記録を無効化。
        |# list: run=一覧を読むコマンド。各行は 表示<TAB>値。on-select の ${d}1 に値を渡す。
        |# input: 入力した文字を run の標準入力へ渡す。閉じると未送信の入力は消えます。
        |# out=none|panel|toast|notify、order=整数、timeout=1〜300秒（既定30）。
        |# text/toggle/list は開いた時に更新。every=5〜86400秒（既定0）は開いている間だけ。
        |# run/state を省略すれば push/state でのみ更新。イベント更新は z2-when から push。
        |# 定義: ~/.z2term/edge/ID/panel.conf と 項目.item（UTF-8、1行1フィールド）。
        |# 項目ID・パネルIDは英数字・_・-。値に改行は不可。スクリプトは別ファイルに置きます。
        |# icon=文字 | @app:パッケージ | @z2:絵の名前 | @file:~/絵.png
        |# icon 省略時、単純な z2-intent -p パッケージ からアプリの絵を自動取得します。
        |# 閉じる・消灯・再読込で状態を読むコマンドを停止。押した操作は完了まで続行（off で停止）。
        |# 最大64パネル・各64項目・同時4実行。表示は64KiBまで。状態はアプリ再起動で消えます。
        |# 常駐は on で明示的に開始。Android の通知からも停止できます。
        |# ユーザー補助が要る操作は z2-key permission で許可してください。
    """.trimMargin() else """
        |# Floating Android panels driven by shell commands and plain text definitions.
        |# z2-edge permission
        |# z2-edge handle ID button --at 85%,60% [--size 48] [--run 'command']
        |# z2-edge handle ID bar --side right --offset 30% --length 6% --size 6 --open swipe
        |# --open swipe|tap|both  --alpha 0.05..1  --label TEXT
        |# Panels show items only by default. Hold whitespace, or hold/release a handle without moving, for settings.
        |# --bar-color auto|white|black: automatic needs Android 11+ and Accessibility.
        |# Bar: --size 2..48 dp; button: 32..96 dp. Hold 300ms to move a handle; bar hit area is at least 24dp.
        |# z2-edge handle ID off
        |# z2-edge set ID:memo type=note file=~/memo.txt
        |# Note colours: note-background=#FFF4BD note-color=#000000 (empty resets to automatic).
        |# z2-edge tab PARENT ID [LABEL]
        |# z2-edge delete ID
        |# z2-edge panel ID 'actions-double-tap=launch:org.example.app|wait:500|swipe-up'
        |# actions-tap / actions-up / actions-down / actions-inward / actions-outward
        |# actions-up=scroll-variable  scroll-x=50 scroll-y=50
        |# z2-edge panel ID gesture-up="command" gesture-down="command" gesture-double-tap="command"
        |# z2-edge panel ID gesture-scroll=off|variable|fixed gesture-speed=600
        |# z2-edge panel ID gesture-range=640    Distance to maximum: 32–2000dp (default 160); larger is gentler.
        |# Vertical slides set variable/fixed auto-scroll (50–40000 dp/s). Tap to stop.
        |# z2-edge panel ID width=80% height=60% fit=content|fixed
        |# title=on|off close=on|off tabbar=off|on|auto add=on|off settings=on|off
        |# labels=on|off flow=vertical|horizontal|grid columns=auto|1..16 icon-size=16..192
        |# place=handle|left|right|top|bottom|center at=X%,Y%
        |# z2-edge panel ID label=Name
        |# z2-edge set ID:item type=text 'run=date' label=Clock every=30
        |# z2-edge get ID                        Read saved panel fields (key=value)
        |# z2-edge get ID:item | list [ID] | remove ID:item
        |# z2-edge on | off | toggle | status | reload | open ID [--toggle] | close
        |# z2-edge push ID:item 'text'             Use - to read stdin
        |# z2-edge state ID:item on|off
        |# z2-edge badge ID '87%'                  Empty string clears the badge
        |# Types: run (default), text, toggle, list, input, note.
        |# toggle: run changes state; state reads on/off, 1/0, or true/false.
        |# run: button-state=on shows ON/OFF using the button border and background. off=OFF command (default: run).
        |# button-source=auto|torch|screen|process|remember (default auto). torch/screen follow actual state.
        |# auto detects direct commands and API operations in macros; process is ON while running, OFF on exit/stop.
        |# Optional state= query takes priority. Other effects need a state query or pushed updates.
        |# Button state survives reopening/restart and is invalidated when its command definition changes.
        |# list: run outputs label<TAB>value per line; on-select receives the value as ${d}1.
        |# input: run receives entered text on stdin; closing discards unsent input.
        |# out=none|panel|toast|notify, order=integer, timeout=1..300 seconds (default 30).
        |# text/toggle/list refresh on open; every=5..86400 only while open (default 0).
        |# Omit run/state for push-only items. Use z2-when with push for event updates.
        |# Definitions: ~/.z2term/edge/ID/panel.conf and item.item, UTF-8 key=value lines.
        |# IDs: letters/digits/_/-. No multiline values; put scripts in separate files.
        |# icon=text | @app:package | @z2:sample | @file:~/image.png
        |# Simple z2-intent -p package commands get application icons automatically.
        |# Close/screen-off/reload stop readers; explicit actions finish unless off is requested.
        |# Dragging a button saves its position.
        |# Limits: 64 panels, 64 items each, 4 simultaneous commands, 64 KiB display output.
        |# Live values reset on app restart. Enable with on; the notification also offers Stop.
        |# For Android global actions, grant access using z2-key permission.
    """.trimMargin()
    val edge = "#!/bin/sh\n$edgeHelp\n" + """
        |case "${d}{1:-}" in
        |  -h|--help|help|'') $help ;;
        |  push)
        |    if [ "${d}#" -eq 3 ] && [ "${d}3" = - ]; then
        |      payload=${d}(head -c 65537; printf .)
        |      payload=${d}{payload%.}
        |      [ "${d}(printf %s "${d}payload" | wc -c)" -le 65536 ] || { echo 'z2-edge: input exceeds 64 KiB' >&2; exit 1; }
        |      exec z2api 1 edge push "${d}2" "${d}payload"
        |    fi ;;
        |esac
        |exec z2api 1 edge "${d}@"
    """.trimMargin() + "\n"
    val key = "#!/bin/sh\n" + (if (lang == "ja")
        "# Android の操作: z2-key back|home|recents|shade|quicksettings|screenshot|split\n# z2-key status: OSで有効か・接続中か・利用可能な操作。permission: ユーザー補助の詳細画面。app-info: 制限付き設定の入口。\n# OS が提供していない操作は失敗します。split は分割切替のみでアプリは起動しません。\n"
        else "# Android actions: z2-key back|home|recents|shade|quicksettings|screenshot|split\n# z2-key status shows enabled/connected state and available actions; permission opens Accessibility details; app-info opens App info.\n# Unsupported actions fail. split only toggles docking; it does not launch an app.\n") + """
        |case "${d}{1:-}" in -h|--help|help|'') $help ;; esac
        |exec z2api 1 key "${d}@"
    """.trimMargin() + "\n"
    val app = "#!/bin/sh\n" + (if (lang == "ja")
        "# z2-app pick                       一覧から選びパッケージ名を返す（120秒・取消は失敗）\n# z2-app list                       起動可能なアプリをJSONで列挙\n# z2-app icon <package> -o <PNG>     アプリのアイコンをPNGへ保存（- は標準出力）\n"
        else "# z2-app pick                       Choose a package (120 seconds; cancel fails)\n# z2-app list                       Launchable applications as JSON\n# z2-app icon <package> -o <PNG>     Save application icon as PNG (- for stdout)\n") + """
        |case "${d}{1:-}" in
        |  -h|--help|help|'') $help ;;
        |  pick) [ "${d}#" -eq 1 ] || exit 1; Z2API_WAIT=1250 exec z2api 1 app pick ;;
        |  list) [ "${d}#" -eq 1 ] || exit 1; exec z2api 1 app list ;;
        |  icon)
        |    [ "${d}#" -eq 4 ] && [ "${d}3" = -o ] || { echo 'z2-app icon PACKAGE -o FILE.png' >&2; exit 1; }
        |    encoded=${d}(z2api 1 app icon "${d}2") || exit ${d}?
        |    if [ "${d}4" = - ]; then printf %s "${d}encoded" | base64 -d; else
        |      image_tmp=${d}(mktemp -- "${d}4.XXXXXX") || exit 1
        |      trap 'rm -f "${d}image_tmp"' EXIT HUP INT TERM
        |      printf %s "${d}encoded" | base64 -d > "${d}image_tmp" || exit 1
        |      mv -- "${d}image_tmp" "${d}4" || exit 1
        |    fi ;;
        |  *) echo 'z2-app: see --help' >&2; exit 1 ;;
        |esac
    """.trimMargin() + "\n"
    return linkedMapOf("z2-edge" to edge, "z2-key" to key, "z2-app" to app)
}
