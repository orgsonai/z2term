package com.zerotoship.z2term.proot

/** All inputs remain separate arguments; run waits asynchronously and cancels only its own run. */
fun z2ActionScript(lang: String): String {
    val d = '$'
    val t = CliText(lang)
    val description = t(
        en = "Named Android action macros. Enable z2term Android actions with z2-key permission.\nSave validates and replaces the definition; screen=current records the display.\nrun waits for completion; start returns a run ID. Interrupting run stops that run.\nOne run at a time, 64 steps, 1–300 seconds. Notification and stop cancel remaining steps.\nCoordinates require a target app and matching screen size/orientation. Stroke limit: 3 seconds.\nUse start from a panel gesture; use run in a shell macro or z2-when command.\nStored in /root/.z2term/actions/NAME.actions (shared home). z2-macro actions is an alias.",
        ja = "名前付きAndroid操作マクロ。z2-key permission でAndroid操作を有効にしてください。\n保存時に検査して定義を置換。screen=current は現在の画面寸法を記録します。\nrun は完了待ち、start は実行IDを返します。run の中断はその実行を停止します。\n同時1本、最大64手順、1〜300秒。通知と stop で残りの手順を停止できます。\n座標操作には対象アプリと画面寸法・向きの一致が必要です。1操作は最大3秒。\n取っ手からは start、シェルマクロや z2-when からは run を使えます。\n保存先: /root/.z2term/actions/NAME.actions（共有ホーム）。z2-macro actions も同じ入口です。",
        "zh-CN" to "命名的Android操作宏。用 z2-key permission 启用Android操作。\n保存时验证并替换定义；screen=current 记录当前屏幕尺寸。\nrun 等待完成，start 返回运行ID。中断 run 会停止该次运行。\n同时运行一个，最多64步、1–300秒。可从通知或 stop 停止后续步骤。\n坐标操作要求目标应用和匹配的屏幕尺寸、方向。单次操作最多3秒。\n手势入口使用 start，Shell宏或 z2-when 使用 run。\n保存到 /root/.z2term/actions/NAME.actions（共享主目录）。也可用 z2-macro actions。",
        "zh-TW" to "具名的Android操作巨集。用 z2-key permission 啟用Android操作。\n儲存時驗證並取代定義；screen=current 記錄目前螢幕尺寸。\nrun 等待完成，start 傳回執行ID。中斷 run 會停止該次執行。\n同時執行一個，最多64步、1–300秒。可從通知或 stop 停止後續步驟。\n座標操作需要目標應用程式及相符的螢幕尺寸、方向。單次操作最多3秒。\n手勢入口使用 start，Shell巨集或 z2-when 使用 run。\n儲存於 /root/.z2term/actions/NAME.actions（共用家目錄）。也可用 z2-macro actions。",
        "es" to "Macros de acciones Android con nombre. Actívalas con z2-key permission.\nAl guardar se valida y sustituye la definición; screen=current registra la pantalla.\nrun espera al final; start devuelve un ID. Interrumpir run detiene esa ejecución.\nUna ejecución a la vez, 64 pasos, 1–300 segundos. La notificación y stop detienen los pasos restantes.\nLas coordenadas requieren la aplicación destino y el mismo tamaño y orientación. Máximo 3 segundos por gesto.\nUsa start desde gestos del panel y run desde macros de shell o z2-when.\nSe guarda en /root/.z2term/actions/NAME.actions (carpeta personal compartida). Alias: z2-macro actions.",
        "ko" to "이름이 있는 Android 동작 매크로입니다. z2-key permission으로 Android 동작을 켜세요.\n저장할 때 정의를 검사하고 바꿉니다. screen=current는 현재 화면 크기를 기록합니다.\nrun은 완료를 기다리고 start는 실행 ID를 반환합니다. run 중단 시 해당 실행을 멈춥니다.\n동시에 1개, 최대 64단계, 1–300초입니다. 알림이나 stop으로 남은 단계를 멈춥니다.\n좌표 동작은 대상 앱과 화면 크기·방향이 일치해야 합니다. 동작당 최대 3초입니다.\n패널 제스처에서는 start, 셸 매크로나 z2-when에서는 run을 사용하세요.\n저장 위치: /root/.z2term/actions/NAME.actions (공유 홈). z2-macro actions도 같은 명령입니다."
    )
    val header = description.lines().joinToString("\n") { "# $it" }
    return "#!/bin/sh\n$header\n" + """
        |# z2-action list | show NAME | save NAME FILE|- | delete NAME
        |# z2-action run NAME | start NAME | wait RUN_ID | status [RUN_ID] | stop [RUN_ID]
        |# z2-action screen | history
        |# version=1 / timeout=30 / screen=current
        |# target PACKAGE / launch PACKAGE / wait MILLISECONDS / key back|home|recents|shade|quicksettings|screenshot|split
        |# tap px|percent X Y / long-press px|percent X Y MILLISECONDS
        |# swipe px|percent X1 Y1 X2 Y2 MILLISECONDS / command SHELL_TEXT
        |# scroll SIGNED_DP_PER_SECOND MILLISECONDS X_PERCENT Y_PERCENT
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
