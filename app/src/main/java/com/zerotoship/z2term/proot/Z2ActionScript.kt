package com.zerotoship.z2term.proot

/** All inputs remain separate arguments; run waits asynchronously and cancels only its own run. */
fun z2ActionScript(lang: String): String {
    val d = '$'
    val t = CliText(lang)
    val description = t(
        en = "Named Android action macros. Enable z2term Android actions with z2-key permission.\nSave validates and replaces the definition; screen=current records the display.\nrun waits for completion; start returns a run ID. Interrupting run stops that run.\nOne run at a time, 64 source instructions, 1–300 seconds. Notification and stop cancel remaining steps.\nCoordinates use the foreground app by default and require matching screen size/orientation. Manual gestures: up to 3 seconds; recorded touches: up to 30 seconds.\nUse start from a panel gesture; use run in a shell macro or z2-when command.\nStored in /root/.z2term/actions/NAME.actions (shared home). z2-macro actions is an alias.",
        ja = "名前付きAndroid操作マクロ。z2-key permission でAndroid操作を有効にしてください。\n保存時に検査して定義を置換。screen=current は現在の画面寸法を記録します。\nrun は完了待ち、start は実行IDを返します。run の中断はその実行を停止します。\n同時1本、定義ごとに最大64命令、1〜300秒。通知と stop で残りの手順を停止できます。\n座標操作の既定対象は前面アプリで、画面寸法・向きの一致が必要です。手動操作は最大3秒、記録したタッチは最大30秒。\n取っ手からは start、シェルマクロや z2-when からは run を使えます。\n保存先: /root/.z2term/actions/NAME.actions（共有ホーム）。z2-macro actions も同じ入口です。",
        "zh-CN" to "命名的Android操作宏。用 z2-key permission 启用Android操作。\n保存时验证并替换定义；screen=current 记录当前屏幕尺寸。\nrun 等待完成，start 返回运行ID。中断 run 会停止该次运行。\n同时运行一个，定义最多64条指令、1–300秒。可从通知或 stop 停止后续步骤。\n坐标操作要求目标应用和匹配的屏幕尺寸、方向。单次操作最多3秒。\n手势入口使用 start，Shell宏或 z2-when 使用 run。\n保存到 /root/.z2term/actions/NAME.actions（共享主目录）。也可用 z2-macro actions。",
        "zh-TW" to "具名的Android操作巨集。用 z2-key permission 啟用Android操作。\n儲存時驗證並取代定義；screen=current 記錄目前螢幕尺寸。\nrun 等待完成，start 傳回執行ID。中斷 run 會停止該次執行。\n同時執行一個，定義最多64條指令、1–300秒。可從通知或 stop 停止後續步驟。\n座標操作需要目標應用程式及相符的螢幕尺寸、方向。單次操作最多3秒。\n手勢入口使用 start，Shell巨集或 z2-when 使用 run。\n儲存於 /root/.z2term/actions/NAME.actions（共用家目錄）。也可用 z2-macro actions。",
        "es" to "Macros de acciones Android con nombre. Actívalas con z2-key permission.\nAl guardar se valida y sustituye la definición; screen=current registra la pantalla.\nrun espera al final; start devuelve un ID. Interrumpir run detiene esa ejecución.\nUna ejecución a la vez, 64 instrucciones por definición, 1–300 segundos. La notificación y stop detienen los pasos restantes.\nLas coordenadas requieren la aplicación destino y el mismo tamaño y orientación. Máximo 3 segundos por gesto.\nUsa start desde gestos del panel y run desde macros de shell o z2-when.\nSe guarda en /root/.z2term/actions/NAME.actions (carpeta personal compartida). Alias: z2-macro actions.",
        "ko" to "이름이 있는 Android 동작 매크로입니다. z2-key permission으로 Android 동작을 켜세요.\n저장할 때 정의를 검사하고 바꿉니다. screen=current는 현재 화면 크기를 기록합니다.\nrun은 완료를 기다리고 start는 실행 ID를 반환합니다. run 중단 시 해당 실행을 멈춥니다.\n동시에 1개, 정의마다 최대 64개 명령, 1–300초입니다. 알림이나 stop으로 남은 단계를 멈춥니다.\n좌표 동작은 대상 앱과 화면 크기·방향이 일치해야 합니다. 동작당 최대 3초입니다.\n패널 제스처에서는 start, 셸 매크로나 z2-when에서는 run을 사용하세요.\n저장 위치: /root/.z2term/actions/NAME.actions (공유 홈). z2-macro actions도 같은 명령입니다."
    )
    val controlHelp = t(
        en = "version=2 adds repeat N|forever ... end, if CONDITION ... else ... end, and call NAME.\nConditions use z2-state keys or foreground=PACKAGE; commas mean AND, ! negates a term.\nUnavailable condition state fails the run. Calls are validated and frozen before execution.\n64 source instructions per definition; 10000 executed instructions per run. Root and child timeouts apply.",
        ja = "version=2 で repeat 回数|forever ... end、if 条件 ... else ... end、call 名前 を使えます。\n条件は z2-state のキーや foreground=パッケージ名。カンマはAND、!は否定です。\n条件の状態が取得できない場合は失敗します。呼び出し先も実行前に検査・固定します。\n定義ごとに最大64命令、全体で実行10000命令。親と子の制限時間が適用されます。",
        "zh-CN" to "version=2 支持 repeat 次数|forever ... end、if 条件 ... else ... end 和 call 名称。\n条件使用 z2-state 键或 foreground=包名；逗号表示AND，!表示否定。\n无法获取条件状态时运行失败。运行前验证并固定所有调用的宏。\n每个定义最多64条指令，每次运行最多执行10000条。父宏和子宏的超时均有效。",
        "zh-TW" to "version=2 支援 repeat 次數|forever ... end、if 條件 ... else ... end 及 call 名稱。\n條件使用 z2-state 鍵或 foreground=套件名稱；逗號表示AND，!表示否定。\n無法取得條件狀態時執行失敗。執行前驗證並固定所有呼叫的巨集。\n每個定義最多64條指令，每次執行最多10000條。父巨集和子巨集的逾時均有效。",
        "es" to "version=2 añade repeat N|forever ... end, if CONDICIÓN ... else ... end y call NOMBRE.\nLas condiciones usan claves de z2-state o foreground=PAQUETE; coma significa AND y ! niega.\nSi falta el estado, la ejecución falla. Las macros llamadas se validan y fijan antes de empezar.\nMáximo 64 instrucciones por definición y 10000 por ejecución. Se aplican los plazos de la macro raíz y las llamadas.",
        "ko" to "version=2에서 repeat 횟수|forever ... end, if 조건 ... else ... end, call 이름을 사용할 수 있습니다.\n조건은 z2-state 키 또는 foreground=패키지입니다. 쉼표는 AND, !는 부정입니다.\n조건 상태를 얻지 못하면 실패합니다. 호출할 매크로도 실행 전에 검사하고 고정합니다.\n정의마다 최대 64개 명령, 실행 전체에서 최대 10000개 명령입니다. 상위 및 하위 매크로의 제한 시간이 적용됩니다."
    )
    val uiHelp = t(
        en = "version=2 also supports click SELECTOR, long-click SELECTOR and wait-ui MS SELECTOR.\nSelectors: text=Continue, desc=More options, id=org.example.app:id/next. Exact matches; no quoting around spaces.\nSet target or launch first. wait-ui searches every 250ms for up to 1–30000ms; multiple matches fail.\nActions are sent once; use wait-ui for the next screen. Input/password fields are excluded.\ninspect PACKAGE returns visible text, descriptions and IDs from the focused target app as JSON.",
        ja = "version=2 では click 対象、long-click 対象、wait-ui ミリ秒 対象 も使えます。\n対象は text=次へ、desc=その他、id=org.example.app:id/next。完全一致で、空白を引用符で囲みません。\n先に target または launch を指定します。wait-ui は250ミリ秒間隔、1〜30000ミリ秒まで検索。複数一致は失敗です。\n操作は1回だけ送り、次の画面には wait-ui を使います。入力欄・パスワード欄は対象外です。\ninspect パッケージ名 は、前面の対象アプリの表示文字・説明・IDをJSONで返します。",
        "zh-CN" to "version=2还支持click 选择器、long-click 选择器和wait-ui 毫秒 选择器。\n选择器示例：text=继续、desc=更多选项、id=org.example.app:id/next。精确匹配，空格不加引号。\n先设置target或launch。wait-ui每250毫秒查询，期限1–30000毫秒；多项匹配时失败。\n操作仅发送一次，下一画面使用wait-ui等待。输入框及密码框除外。\ninspect 包名以JSON返回前台目标应用的显示文本、说明和ID。",
        "zh-TW" to "version=2還支援click 選擇器、long-click 選擇器和wait-ui 毫秒 選擇器。\n選擇器範例：text=繼續、desc=更多選項、id=org.example.app:id/next。精確比對，空格不加引號。\n先設定target或launch。wait-ui每250毫秒查詢，期限1–30000毫秒；多項符合時失敗。\n操作僅傳送一次，下一畫面使用wait-ui等待。輸入欄位及密碼欄位除外。\ninspect 套件名稱以JSON傳回前景目標應用程式的顯示文字、說明和ID。",
        "es" to "version=2 admite click SELECTOR, long-click SELECTOR y wait-ui MS SELECTOR.\nSelectores: text=Continuar, desc=Más opciones, id=org.example.app:id/next. Coincidencia exacta; espacios sin comillas.\nDefine primero target o launch. wait-ui consulta cada 250ms durante 1–30000ms; varias coincidencias causan un error.\nLa acción se envía una vez; usa wait-ui para la siguiente pantalla. Se excluyen campos de entrada y contraseñas.\ninspect PAQUETE devuelve como JSON el texto visible, las descripciones y los IDs de la aplicación destino enfocada.",
        "ko" to "version=2는 click 선택자, long-click 선택자, wait-ui 밀리초 선택자도 지원합니다.\n선택자 예: text=계속, desc=추가 옵션, id=org.example.app:id/next. 정확히 일치하며 공백을 따옴표로 감싸지 않습니다.\n먼저 target이나 launch를 지정하세요. wait-ui는 250밀리초마다 1–30000밀리초 동안 검색합니다. 여러 항목이 일치하면 실패합니다.\n동작은 한 번만 전송합니다. 다음 화면은 wait-ui로 기다리세요. 입력란과 비밀번호란은 제외됩니다.\ninspect 패키지는 전면 대상 앱의 표시 글자, 설명과 ID를 JSON으로 반환합니다."
    )
    val header = (description + "\n" + controlHelp + "\n" + uiHelp).lines().joinToString("\n") { "# $it" }
    return "#!/bin/sh\n$header\n" + """
        |# z2-action list | show NAME | save NAME FILE|- | delete NAME
        |# z2-action run NAME | start NAME | wait RUN_ID | status [RUN_ID] | stop [RUN_ID]
        |# z2-action screen | history | inspect PACKAGE
        |# version=1|2 / timeout=30 / screen=current
        |# target PACKAGE / launch PACKAGE / wait MILLISECONDS / key back|home|recents|shade|quicksettings|screenshot|split
        |# tap px|percent X Y / long-press px|percent X Y MILLISECONDS
        |# swipe px|percent X1 Y1 X2 Y2 MILLISECONDS [linear|accelerate|decelerate]
        |# double-tap UNIT X Y GAP_MS / pinch-in|pinch-out UNIT CX CY START_SPAN END_SPAN MS [EASING]
        |# swipe-two UNIT X1 Y1 X2 Y2 OFFSET_X OFFSET_Y MS [EASING]
        |# touch UNIT X,Y,MS ... [| X,Y,MS ...] (version=2; recorded contacts and timing)
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
        |  *) exec z2api 1 action "${d}@" ;;
        |esac
    """.trimMargin() + "\n"
}
