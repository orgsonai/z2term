package com.zerotoship.z2term.proot

/**
 * `z2-ui` — AI からのアプリ操作 (設計は docs/ja/AI-BRIDGE.md)。判定はすべてアプリ側
 * ([com.zerotoship.z2term.automation.UiBridge]) で行い、ここは引数を渡して JSON を出すだけ。
 *
 * ⚠ **失敗も標準出力へ JSON で出す。** z2api は失敗の本文を stderr へ出すので、AI が標準出力
 * だけを読む作りでも理由が届くよう 2>&1 でまとめる。アプリが答えないとき (JSON でないとき) は
 * 決まった形の JSON に置き換える。終了コードは成功 0・失敗 非0 のまま。
 *
 * 待ち時間は承認待ち (60 秒) と wait (最大 30 秒) を収める 100 秒。
 */
fun z2UiScript(lang: String): String {
    val d = '$'
    val t = CliText(lang)
    val description = t(
        en = "AI app control: read and operate the foreground Android app.\nTurn it on in commands → automation → AI. Requires z2term Android actions (z2-key permission).\nEvery result is one line of JSON. Failures have \"ok\":false, an error code and a non-zero exit status.\nLoop: look → tap #N / type #N TEXT → look. #N is valid only for the last look; on stale, look again.\ninterrupted: the message is a new instruction from the user. stopped: end the task. paused: wait.\nOther errors: disabled read_only not_allowed protected denied not_found ambiguous locked busy changed.\nPer-app levels: off / look (read only) / ask (each action approved from a notification) / auto. Unlisted apps: look.\nPassword fields are never read or typed into. Log: /root/.z2term/ui/log.jsonl",
        ja = "AI からのアプリ操作。前面の Android アプリを読み、操作します。\nコマンド一覧 → 自動化 → AI 操作 で ON にします。z2term Android 操作（z2-key permission）が必要です。\n結果は常に 1 行の JSON。失敗は \"ok\":false・エラーコード・非0の終了コードです。\n手順: look → tap #N / type #N 文字 → look。#N は直前の look の中だけで通じます。stale なら look し直します。\ninterrupted: message は利用者からの新しい指示です。stopped: 作業を終えます。paused: 待ちます。\nその他のエラー: disabled read_only not_allowed protected denied not_found ambiguous locked busy changed\nアプリごとの段階: off / look（読むだけ）/ ask（1 手ごとに通知で承認）/ auto。一覧に無いアプリは look。\nパスワード欄は読まず、入力もしません。記録: /root/.z2term/ui/log.jsonl",
        "zh-CN" to "AI 操作应用：读取并操作前台的 Android 应用。\n在 命令列表 → 自动化 → AI 操作 中开启。需要 z2term Android 操作（z2-key permission）。\n结果始终是一行 JSON。失败时为 \"ok\":false、错误代码和非 0 退出码。\n流程：look → tap #N / type #N 文字 → look。#N 仅在上一次 look 中有效；遇到 stale 时重新 look。\ninterrupted：message 是用户的新指示。stopped：结束任务。paused：等待。\n其他错误：disabled read_only not_allowed protected denied not_found ambiguous locked busy changed\n按应用的级别：off / look（只读）/ ask（每次操作在通知中确认）/ auto。不在列表中的应用为 look。\n不读取也不输入密码框。记录：/root/.z2term/ui/log.jsonl",
        "zh-TW" to "AI 操作應用程式：讀取並操作前景的 Android 應用程式。\n在 指令清單 → 自動化 → AI 操作 中開啟。需要 z2term Android 操作（z2-key permission）。\n結果一律為一行 JSON。失敗時為 \"ok\":false、錯誤代碼和非 0 結束碼。\n流程：look → tap #N / type #N 文字 → look。#N 僅在上一次 look 中有效；遇到 stale 時重新 look。\ninterrupted：message 是使用者的新指示。stopped：結束工作。paused：等待。\n其他錯誤：disabled read_only not_allowed protected denied not_found ambiguous locked busy changed\n依應用程式的層級：off / look（僅讀取）/ ask（每次操作從通知確認）/ auto。不在清單中的應用程式為 look。\n不讀取也不輸入密碼欄位。紀錄：/root/.z2term/ui/log.jsonl",
        "es" to "Control de apps por IA: lee y maneja la app Android en primer plano.\nActívalo en comandos → automatización → IA. Requiere z2term Android actions (z2-key permission).\nCada resultado es una línea JSON. Los fallos llevan \"ok\":false, un código de error y un estado de salida distinto de 0.\nCiclo: look → tap #N / type #N TEXTO → look. #N solo vale para el último look; ante stale, vuelve a hacer look.\ninterrupted: message es una nueva instrucción del usuario. stopped: termina la tarea. paused: espera.\nOtros errores: disabled read_only not_allowed protected denied not_found ambiguous locked busy changed\nNiveles por app: off / look (solo lectura) / ask (cada acción se aprueba desde una notificación) / auto. Apps sin entrada: look.\nNunca se leen ni se escriben campos de contraseña. Registro: /root/.z2term/ui/log.jsonl",
        "ko" to "AI 앱 조작: 전면 Android 앱을 읽고 조작합니다.\n명령 목록 → 자동화 → AI 조작에서 켭니다. z2term Android 동작(z2-key permission)이 필요합니다.\n결과는 항상 한 줄의 JSON입니다. 실패하면 \"ok\":false, 오류 코드, 0이 아닌 종료 코드를 반환합니다.\n순서: look → tap #N / type #N 글자 → look. #N은 직전 look에서만 유효합니다. stale이면 다시 look 하세요.\ninterrupted: message는 사용자의 새 지시입니다. stopped: 작업을 끝냅니다. paused: 기다립니다.\n그 밖의 오류: disabled read_only not_allowed protected denied not_found ambiguous locked busy changed\n앱별 단계: off / look(읽기만) / ask(조작마다 알림으로 승인) / auto. 목록에 없는 앱은 look입니다.\n비밀번호란은 읽거나 입력하지 않습니다. 기록: /root/.z2term/ui/log.jsonl"
    )
    val header = description.lines().joinToString("\n") { "# $it" }
    return "#!/bin/sh\n$header\n" + """
        |# z2-ui front | look [--all] [--tree] [--in N] | status
        |# z2-ui shot [--scale 0.1..1]   (writes /root/.z2term/ui/shot.png; screen = box.x,y + image.x,y / scale)
        |# z2-ui tap|long|enter TARGET / type TARGET TEXT [--append]
        |# z2-ui scroll TARGET up|down|left|right / swipe X1 Y1 X2 Y2 [MS]   (MS 50..3000, default 300)
        |# z2-ui key back|home|recents / open PACKAGE|URI
        |# z2-ui wait SELECTOR [MS] / wait-change [MS]   (MS 1..30000, default 5000)
        |# z2-ui allow [PACKAGE|* off|look|ask|auto|default]
        |# z2-ui pause / say TEXT / stop
        |# TARGET: #N | text=TEXT | desc=DESCRIPTION | id=PACKAGE:id/NAME | @X,Y (pixels)
        |case "${d}{1:-}" in
        |  ''|-h|--help|help)
        |    awk 'NR>1 && /^#/ { sub(/^# ?/, ""); print; next } NR>1 && NF { exit }' "${d}0"; exit 0 ;;
        |esac
        |out=${d}(Z2API_WAIT=1000 z2api 1 ui "${d}@" 2>&1)
        |rc=${d}?
        |case "${d}out" in
        |  '{'*) printf '%s\n' "${d}out" ;;
        |  *) printf '%s\n' '{"ok":false,"error":"no_response","message":"z2term did not answer"}'; rc=1 ;;
        |esac
        |exit "${d}rc"
    """.trimMargin() + "\n"
}
