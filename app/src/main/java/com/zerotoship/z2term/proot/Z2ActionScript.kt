package com.zerotoship.z2term.proot

/** All inputs remain separate arguments; run waits asynchronously and cancels only its own run. */
fun z2ActionScript(lang: String): String {
    val d = '$'
    val t = CliText(lang)
    val description = t("action.description",
        en = "Named Android action macros. Enable z2term Android actions with z2-key permission.\nSave validates and replaces the definition; screen=current records the display.\nrun waits for completion; start returns a run ID. Interrupting run stops that run.\nOne run at a time, 64 source instructions, 0–300 seconds (0 = no limit). Notification and stop cancel remaining steps.\nCoordinates use the foreground app by default and require matching screen size/orientation. Manual gestures: up to 3 seconds; recorded touches: up to 30 seconds.\nUse start from a panel gesture; use run in a shell macro or z2-when command.\nStored in /root/.z2term/actions/NAME.actions (shared home). z2-macro actions is an alias.",
        ja = "名前付きAndroid操作マクロ。z2-key permission でAndroid操作を有効にしてください。\n保存時に検査して定義を置換。screen=current は現在の画面寸法を記録します。\nrun は完了待ち、start は実行IDを返します。run の中断はその実行を停止します。\n同時1本、定義ごとに最大64命令、0〜300秒（0は無制限）。通知と stop で残りの手順を停止できます。\n座標操作の既定対象は前面アプリで、画面寸法・向きの一致が必要です。手動操作は最大3秒、記録したタッチは最大30秒。\n取っ手からは start、シェルマクロや z2-when からは run を使えます。\n保存先: /root/.z2term/actions/NAME.actions（共有ホーム）。z2-macro actions も同じ入口です。"
    )
    val controlHelp = t("action.controlHelp",
        en = "version=2 adds repeat N|forever ... end, if CONDITION ... else ... end, and call NAME.\nConditions use z2-state keys or foreground=PACKAGE; commas mean AND, ! negates a term.\nUnavailable condition state fails the run. Calls are validated and frozen before execution.\n64 source instructions per definition; 10000 executed instructions per run. Root and child timeouts apply.",
        ja = "version=2 で repeat 回数|forever ... end、if 条件 ... else ... end、call 名前 を使えます。\n条件は z2-state のキーや foreground=パッケージ名。カンマはAND、!は否定です。\n条件の状態が取得できない場合は失敗します。呼び出し先も実行前に検査・固定します。\n定義ごとに最大64命令、全体で実行10000命令。親と子の制限時間が適用されます。"
    )
    val conditionHelp = t("action.conditionHelp",
        en = "Conditions may also use ui SELECTOR (an element is on screen), color X,Y=NAME|#RRGGBB (a screen pixel in px; needs screen=) and command SHELL (exit status 0; it reads the rest as shell, so it comes last).\n| means OR and binds looser than the comma (AND). wait-until MS CONDITION / wait-while MS CONDITION check every 250ms; 0 waits up to the macro's own limit. Timing out fails the run.\nScreen terms read the target app (or the foreground one) and need no per-app permission.",
        ja = "条件には ui 対象（要素が画面にある）、color X,Y=色名|#RRGGBB（画面の点、px。screen= が必要）、command シェル（終了コード0で成立。残りをすべてシェルとして読むので最後の項）も使えます。\n| はORで、カンマ（AND）より弱く結び付きます。wait-until ミリ秒 条件 / wait-while ミリ秒 条件 は250ミリ秒ごとに確かめ、0はマクロ全体の制限時間まで待ちます。時間切れは失敗です。\n画面を読む項は対象アプリ（無ければ前面アプリ）を見ます。アプリごとの許可は要りません。"
    )
    val loopHelp = t("action.loopHelp",
        en = "repeat until CONDITION ... end (or repeat N until CONDITION) checks the condition before each pass and leaves once it holds; break leaves the innermost repeat at once.\ntext~=PART / desc~=PART match when the text or description contains PART (click, wait-ui and ui conditions).",
        ja = "repeat until 条件 ... end（または repeat 回数 until 条件）は各回の始めに条件を確かめ、成立したら抜けます。break はいちばん内側の repeat をその場で抜けます。\ntext~=部分 / desc~=部分 は、文字や説明にその部分を含めば一致します（click・wait-ui・条件の ui）。"
    )
    val inputHelp = t("action.inputHelp",
        en = "tap ui SELECTOR taps the centre of the element with a finger (for screens where click does not work); input fields match by their hint.\ntype TEXT replaces the content of the focused input field (empty clears it); enter presses the keyboard's enter key. Password fields are refused. version=2.",
        ja = "tap ui 対象 は要素の真ん中を指でタップします（click が効かない画面向け。入力欄は表示されているヒントの文字で指定できます）。\ntype 文字 はフォーカスのある入力欄の中身をその文字に置き換えます（空なら消す）。enter はキーボードの確定キーです。パスワード欄には入れません。version=2。"
    )
    val tryHelp = t("action.tryHelp",
        en = "try ... else ... end: when a step inside fails (element not found, a wait-ui/wait-until time-out, a shell exit other than 0, a failure in a called macro), the rest is skipped and the else steps run; without else the run continues after end. Stopping, pausing, the time limit, the instruction limit and a locked or rotated screen are not caught. version=2.",
        ja = "try ... else ... end: 中の手順が失敗したら（要素が無い、wait-ui/wait-until の時間切れ、シェルの終了コードが0以外、呼んだマクロの失敗）残りを飛ばして else の手順を動かします。else が無ければ end の後ろへ続けます。停止・一時停止・制限時間・命令数の上限・画面のロックや回転は受け止めません。version=2。"
    )
    val watchHelp = t("action.watchHelp",
        en = "watch CONDITION call NAME checks the condition every 300ms until its block ends (the whole macro at top level). When it holds, NAME runs and the macro then continues; a waiting step is cut short and sent again, other steps finish first. Watch conditions cannot use command.\nrun-output SHELL runs the command and executes the steps it prints (checked first; no headers, call, run-output or watch). A non-zero exit fails the run.",
        ja = "watch 条件 call 名前 は、書いたブロックが終わるまで（最上位ならマクロ全体）300ミリ秒ごとに条件を確かめ、成立したら 名前 を呼んでから続きへ戻ります。待ちの手順は打ち切って最初から送り直し、ほかの手順は終わるのを待ちます。watch の条件に command は使えません。\nrun-output シェル はコマンドを動かし、標準出力に書かれた手順を検査してから実行します（見出し・call・run-output・watch は不可）。終了コードが0以外なら失敗です。"
    )
    val humanHelp = t("action.humanHelp",
        en = "human=1..20 (on = 5, off or 0 = none; larger moves more) makes taps, swipes, drags and waits less mechanical: small position offsets, curved swipes that start and stop slowly, and slightly varied timing. The ~ button on the stop bar switches it while the macro runs.",
        ja = "human=1〜20（on は 5、off と 0 はなし。大きいほど揺れる）で、タップ・スワイプ・なぞる操作と wait を人の指に近づけます（位置を少しずらす、線を少し曲げて動き出しと止まり際を遅く、時間を少しばらつかせる）。停止バーの ～ で実行中にも切り替えられます。"
    )
    val uiHelp = t("action.uiHelp",
        en = "version=2 also supports click SELECTOR, long-click SELECTOR and wait-ui MS SELECTOR.\nSelectors: text=Continue, desc=More options, id=org.example.app:id/next. Exact matches; no quoting around spaces.\nSet target or launch first. wait-ui searches every 250ms for up to 1–30000ms; multiple matches fail.\nActions are sent once; use wait-ui for the next screen. Input/password fields are excluded.\ninspect PACKAGE returns visible text, descriptions and IDs from the focused target app as JSON.",
        ja = "version=2 では click 対象、long-click 対象、wait-ui ミリ秒 対象 も使えます。\n対象は text=次へ、desc=その他、id=org.example.app:id/next。完全一致で、空白を引用符で囲みません。\n先に target または launch を指定します。wait-ui は250ミリ秒間隔、1〜30000ミリ秒まで検索。複数一致は失敗です。\n操作は1回だけ送り、次の画面には wait-ui を使います。入力欄・パスワード欄は対象外です。\ninspect パッケージ名 は、前面の対象アプリの表示文字・説明・IDをJSONで返します。"
    )
    val pauseHelp = t("action.pauseHelp",
        en = "pause [RUN_ID] holds the run; the step in progress is sent again from its start. Opening the notification shade also pauses.\nresume [RUN_ID] continues once a screen other than z2term stays in front (up to 5 seconds; otherwise it stays paused). It answers after that: state running and exit 0, or paused and a non-zero exit. The time limit stops while paused.",
        ja = "pause [実行ID] で一時停止。途中の手順は再開時に最初から送ります。通知欄を下ろしても一時停止します。\nresume [実行ID] は z2term 以外の画面が前に出て落ち着いてから続けます（5秒まで。出なければ一時停止のまま）。結果が出てから返り、再開できれば state は running で終了コード 0、一時停止のままなら paused で 0 以外です。一時停止の間は制限時間が止まります。"
    )
    val header = (description + "\n" + pauseHelp + "\n" + controlHelp + "\n" + conditionHelp + "\n" + loopHelp + "\n" + inputHelp + "\n" + tryHelp + "\n" + watchHelp + "\n" + humanHelp + "\n" + uiHelp).lines().joinToString("\n") { "# $it" }
    return "#!/bin/sh\n$header\n" + """
        |# z2-action list | show NAME | save NAME FILE|- | delete NAME
        |# z2-action run NAME | start NAME | wait RUN_ID | status [RUN_ID] | stop [RUN_ID] | pause [RUN_ID] | resume [RUN_ID]
        |# z2-action screen | history | inspect PACKAGE
        |# z2-action pick   (a bar over the current app: choose a macro, run it with ▶; it then becomes the stop bar)
        |# version=1|2 / timeout=30 / screen=current / human=on|off|0..20
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
        |# tap ui SELECTOR / type TEXT / enter (version=2)
        |# repeat N|forever ... end / if CONDITION ... [else ...] end / call NAME (version=2)
        |# repeat [N|forever] until CONDITION ... end / break / text~=PART desc~=PART (version=2)
        |# try ... [else ...] end (version=2; else runs when a step inside fails)
        |# wait-until MILLISECONDS CONDITION / wait-while MILLISECONDS CONDITION (version=2; 0 = no own limit)
        |# watch CONDITION call NAME / run-output SHELL_TEXT (version=2)
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
        |    Z2API_WAIT=forever z2api 1 action wait "${d}action_id" &
        |    action_waiter=${d}!
        |    trap 'z2api 1 action stop "${d}action_id" >/dev/null 2>&1; kill "${d}action_waiter" 2>/dev/null; exit 130' HUP INT TERM
        |    wait "${d}action_waiter"
        |    action_status=${d}?
        |    trap - HUP INT TERM
        |    exit "${d}action_status" ;;
        |  wait) Z2API_WAIT=forever exec z2api 1 action "${d}@" ;;
        |  resume) Z2API_WAIT=100 exec z2api 1 action "${d}@" ;;
        |  *) exec z2api 1 action "${d}@" ;;
        |esac
    """.trimMargin() + "\n"
}
