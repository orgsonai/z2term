package com.zerotoship.z2term.proot

/** All inputs remain separate arguments; run waits asynchronously and cancels only its own run. */
fun z2ActionScript(lang: String): String {
    val d = '$'
    val t = CliText(lang)
    val description = t("action.description",
        en = "Named Android action macros. Enable z2term Android actions with z2-key permission.\nSave validates and replaces the definition; screen=current records the display.\nrun waits for completion; start returns a run ID. Interrupting run stops that run.\nOne run at a time, 64 source instructions, 1–300 seconds. Notification and stop cancel remaining steps.\nCoordinates use the foreground app by default and require matching screen size/orientation. Manual gestures: up to 3 seconds; recorded touches: up to 30 seconds.\nUse start from a panel gesture; use run in a shell macro or z2-when command.\nStored in /root/.z2term/actions/NAME.actions (shared home). z2-macro actions is an alias.",
        ja = "名前付きAndroid操作マクロ。z2-key permission でAndroid操作を有効にしてください。\n保存時に検査して定義を置換。screen=current は現在の画面寸法を記録します。\nrun は完了待ち、start は実行IDを返します。run の中断はその実行を停止します。\n同時1本、定義ごとに最大64命令、1〜300秒。通知と stop で残りの手順を停止できます。\n座標操作の既定対象は前面アプリで、画面寸法・向きの一致が必要です。手動操作は最大3秒、記録したタッチは最大30秒。\n取っ手からは start、シェルマクロや z2-when からは run を使えます。\n保存先: /root/.z2term/actions/NAME.actions（共有ホーム）。z2-macro actions も同じ入口です。"
    )
    val controlHelp = t("action.controlHelp",
        en = "version=2 adds repeat N|forever ... end, if CONDITION ... else ... end, and call NAME.\nConditions use z2-state keys or foreground=PACKAGE; commas mean AND, ! negates a term.\nUnavailable condition state fails the run. Calls are validated and frozen before execution.\n64 source instructions per definition; 10000 executed instructions per run. Root and child timeouts apply.",
        ja = "version=2 で repeat 回数|forever ... end、if 条件 ... else ... end、call 名前 を使えます。\n条件は z2-state のキーや foreground=パッケージ名。カンマはAND、!は否定です。\n条件の状態が取得できない場合は失敗します。呼び出し先も実行前に検査・固定します。\n定義ごとに最大64命令、全体で実行10000命令。親と子の制限時間が適用されます。"
    )
    val uiHelp = t("action.uiHelp",
        en = "version=2 also supports click SELECTOR, long-click SELECTOR and wait-ui MS SELECTOR.\nSelectors: text=Continue, desc=More options, id=org.example.app:id/next. Exact matches; no quoting around spaces.\nSet target or launch first. wait-ui searches every 250ms for up to 1–30000ms; multiple matches fail.\nActions are sent once; use wait-ui for the next screen. Input/password fields are excluded.\ninspect PACKAGE returns visible text, descriptions and IDs from the focused target app as JSON.",
        ja = "version=2 では click 対象、long-click 対象、wait-ui ミリ秒 対象 も使えます。\n対象は text=次へ、desc=その他、id=org.example.app:id/next。完全一致で、空白を引用符で囲みません。\n先に target または launch を指定します。wait-ui は250ミリ秒間隔、1〜30000ミリ秒まで検索。複数一致は失敗です。\n操作は1回だけ送り、次の画面には wait-ui を使います。入力欄・パスワード欄は対象外です。\ninspect パッケージ名 は、前面の対象アプリの表示文字・説明・IDをJSONで返します。"
    )
    val pauseHelp = t("action.pauseHelp",
        en = "pause [RUN_ID] holds the run; the step in progress is sent again from its start. Opening the notification shade also pauses.\nresume [RUN_ID] continues once a screen other than z2term stays in front (up to 5 seconds; otherwise it stays paused). It answers after that: state running and exit 0, or paused and a non-zero exit. The time limit stops while paused.",
        ja = "pause [実行ID] で一時停止。途中の手順は再開時に最初から送ります。通知欄を下ろしても一時停止します。\nresume [実行ID] は z2term 以外の画面が前に出て落ち着いてから続けます（5秒まで。出なければ一時停止のまま）。結果が出てから返り、再開できれば state は running で終了コード 0、一時停止のままなら paused で 0 以外です。一時停止の間は制限時間が止まります。"
    )
    val header = (description + "\n" + pauseHelp + "\n" + controlHelp + "\n" + uiHelp).lines().joinToString("\n") { "# $it" }
    return "#!/bin/sh\n$header\n" + """
        |# z2-action list | show NAME | save NAME FILE|- | delete NAME
        |# z2-action run NAME | start NAME | wait RUN_ID | status [RUN_ID] | stop [RUN_ID] | pause [RUN_ID] | resume [RUN_ID]
        |# z2-action screen | history | inspect PACKAGE
        |# version=1|2 / timeout=30 / screen=current
        |# target PACKAGE / launch PACKAGE / wait MILLISECONDS / key back|home|recents|shade|quicksettings|screenshot|split
        |# tap px|percent X Y / long-press px|percent X Y MILLISECONDS
        |# swipe px|percent X1 Y1 X2 Y2 MILLISECONDS [linear|accelerate|decelerate]
        |# double-tap UNIT X Y GAP_MS / pinch-in|pinch-out UNIT CX CY START_SPAN END_SPAN MS [EASING]
        |# swipe-two UNIT X1 Y1 X2 Y2 OFFSET_X OFFSET_Y MS [EASING]
        |# touch UNIT X,Y,MS ... [| X,Y,MS ...] (version=2; recorded contacts and timing)
        |# drag px|percent MS_PER_SEGMENT X,Y X,Y ... (one touch through 2..256 points without lifting; 30 s at most)
        |# command SHELL_TEXT
        |# scroll SIGNED_DP_PER_SECOND MILLISECONDS X_PERCENT Y_PERCENT
        |# click SELECTOR / long-click SELECTOR / wait-ui MILLISECONDS SELECTOR (version=2)
        |# repeat N|forever ... end / if CONDITION ... [else ...] end / call NAME (version=2)
        |case "${d}{1:-}" in
        |  ''|-h|--help|help)
        |    awk 'NR>1 && /^#/ { sub(/^# ?/, ""); print; next } NR>1 && NF { exit }' "${d}0"; exit 0 ;;
        |  save)
        |    [ "${d}#" -eq 3 ] || { echo 'z2-action save NAME FILE|-' >&2; exit 1; }
        |    if [ "${d}3" = - ]; then
        |      action_text=${d}(head -c 65537 || exit 1; printf .) || exit 1
        |    else
        |      action_text=${d}(head -c 65537 < "${d}3" || exit 1; printf .) || exit 1
        |    fi
        |    action_text=${d}{action_text%.}
        |    [ "${d}(printf %s "${d}action_text" | wc -c)" -le 65536 ] || { echo 'z2-action: input exceeds 64 KiB' >&2; exit 1; }
        |    exec z2api 1 action save "${d}2" "${d}action_text" ;;
        |  run)
        |    [ "${d}#" -eq 2 ] || { echo 'z2-action run NAME' >&2; exit 1; }
        |    action_id=${d}(z2api 1 action start "${d}2") || exit ${d}?
        |    Z2API_WAIT=3100 z2api 1 action wait "${d}action_id" &
        |    action_waiter=${d}!
        |    trap 'z2api 1 action stop "${d}action_id" >/dev/null 2>&1; kill "${d}action_waiter" 2>/dev/null; exit 130' HUP INT TERM
        |    wait "${d}action_waiter"
        |    action_status=${d}?
        |    trap - HUP INT TERM
        |    exit "${d}action_status" ;;
        |  wait) Z2API_WAIT=3100 exec z2api 1 action "${d}@" ;;
        |  resume) Z2API_WAIT=100 exec z2api 1 action "${d}@" ;;
        |  *) exec z2api 1 action "${d}@" ;;
        |esac
    """.trimMargin() + "\n"
}
