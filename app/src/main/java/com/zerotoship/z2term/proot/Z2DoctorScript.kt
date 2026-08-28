package com.zerotoship.z2term.proot

/**
 * `z2doctor` — 「動きません」を 1 コマンドで切り分けるための自己診断 (0.8.230)。
 *
 * **`z2scan` との違い**: `z2scan self` は「危ない設定を探す」(セキュリティ)、`z2doctor` は
 * 「動かない理由を探す」(トラブル切り分け)。名前が近いので、用途を混ぜないこと。
 *
 * **設計の要点**:
 *  - 各行は `OK` / `NG` / `--`（該当なし）の 3 状態だけ。**`NG` の行には必ず次の一手を 1 行付ける**。
 *    書けない項目は最初から出さない（直し方の分からない `NG` は不安にさせるだけ）。
 *  - 末尾に**そのまま貼れる報告文**を出す。相手が打つのは 1 コマンド、返ってくるのは短い報告、
 *    という形にすると「動きません」→「何が？」の往復が消える。
 *  - **個人情報は既定で伏せる**。SSID・IP・ホスト名は出さず、伏せていることを画面に明記する。
 *    伏せ字を後付けにすると、報告文に社内 IP や SSID が混ざる事故が必ず起きる。
 *  - アプリ側にしか無い情報（許可の有無・設定・常駐の数）は `z2api 1 doctor` で JSON を取り、
 *    端末から見えるもの（kernel・空き容量・sshd・PATH）は**シェル側で調べる**。
 */
// ⚠ /sdcard は z2doctor が**端末に出す行の見出し**で、ディストロ側から見えるパスそのもの。
// Android の外部ストレージ API に置き換える対象ではない。
@Suppress("SdCardPath")
fun z2doctorScript(lang: String = "ja"): String {
    val d = "${'$'}"  // シェルの $ (Kotlin テンプレートと衝突しないように)
    // 言語ごとの文言を選ぶ道具。3 言語目は t(en = …, ja = …) の後ろへ変わり値を足す ([CliText])。
    val t = CliText(lang)

    val head = t(
        en = "== z2doctor (this device) ==",
        ja = "== z2doctor (この端末の状態) ==",
        "zh-CN" to "== z2doctor (这台设备的状态) ==",
        "zh-TW" to "== z2doctor (這台裝置的狀態) =="
    )
    val secApp = t(en = "-- app --", ja = "-- アプリ --", "zh-CN" to "-- 应用 --", "zh-TW" to "-- 應用程式 --")
    val secLinux = t(en = "-- linux side --", ja = "-- Linux 側 --", "zh-CN" to "-- Linux 一侧 --", "zh-TW" to "-- Linux 一側 --")
    val secPerm = t(en = "-- permissions --", ja = "-- 許可 --", "zh-CN" to "-- 权限 --", "zh-TW" to "-- 權限 --")
    val secAuto = t(en = "-- automation --", ja = "-- 自動化 --", "zh-CN" to "-- 自动化 --", "zh-TW" to "-- 自動化 --")
    val secReport = t(
        en = "-- copy this when asking for help --",
        ja = "-- 助けを求めるときはここを貼る --",
        "zh-CN" to "-- 求助时请贴这一段 --",
        "zh-TW" to "-- 求助時請貼這一段 --"
    )

    val lVersion = t(en = "version", ja = "版数", "zh-CN" to "版本", "zh-TW" to "版本")
    val lEngine = t(en = "engine", ja = "実行エンジン", "zh-CN" to "执行引擎", "zh-TW" to "執行引擎")
    val lDistro = t(en = "distro", ja = "ディストロ", "zh-CN" to "发行版", "zh-TW" to "發行版")
    val lKernel = t(en = "kernel", ja = "kernel", "zh-CN" to "kernel", "zh-TW" to "kernel")
    val lDisk = t(en = "free space", ja = "空き容量", "zh-CN" to "可用空间", "zh-TW" to "可用空間")
    val lHome = t(en = "home", ja = "ホーム", "zh-CN" to "主目录", "zh-TW" to "主目錄")
    val lSdcard = t(en = "/sdcard", ja = "/sdcard", "zh-CN" to "/sdcard", "zh-TW" to "/sdcard")
    val lNotify = t(en = "notifications", ja = "通知を出せる", "zh-CN" to "能发通知", "zh-TW" to "能發通知")
    val lNotifyRead = t(en = "notification access", ja = "通知を読める", "zh-CN" to "能读通知", "zh-TW" to "能讀通知")
    val lBattOpt = t(en = "battery optimization", ja = "電池最適化から除外", "zh-CN" to "已排除电池优化", "zh-TW" to "已排除電池最佳化")
    val lStorage = t(en = "storage (all files)", ja = "ストレージ全体", "zh-CN" to "全部存储", "zh-TW" to "全部儲存")
    val lSms = t(en = "SMS receive", ja = "SMS 受信", "zh-CN" to "接收短信", "zh-TW" to "接收簡訊")
    val lCapture = t(en = "event detection", ja = "システムイベント検知", "zh-CN" to "系统事件检测", "zh-TW" to "系統事件偵測")
    val lServers = t(en = "resident servers", ja = "常駐サーバー", "zh-CN" to "常驻服务", "zh-TW" to "常駐服務")
    val lRules = t(en = "automation rules", ja = "自動化ルール", "zh-CN" to "自动化规则", "zh-TW" to "自動化規則")
    val lSshd = t(en = "sshd", ja = "sshd", "zh-CN" to "sshd", "zh-TW" to "sshd")

    // NG のときに出す「次の一手」。ここが書けない項目は診断に出さない。
    val fixNotify = t(
        en = "-> Android settings > Apps > Z2Term > Notifications: allow",
        ja = "-> Android の設定 › アプリ › Z2Term › 通知 を許可してください",
        "zh-CN" to "-> 请到 Android 的 设置 › 应用 › Z2Term › 通知 里允许",
        "zh-TW" to "-> 請到 Android 的 設定 › 應用程式 › Z2Term › 通知 裡允許"
    )
    val fixNotifyRead = t(
        en = "-> Settings > resident servers & automation > notification detection",
        ja = "-> 設定 › 常駐サーバー・自動化 › 通知検知 から許可してください",
        "zh-CN" to "-> 请到 设置 › 常驻服务与自动化 › 通知检测 里允许",
        "zh-TW" to "-> 請到 設定 › 常駐服務與自動化 › 通知偵測 裡允許"
    )
    val fixBattOpt = t(
        en = "-> Settings > background process protection > exclude from battery optimization",
        ja = "-> 設定 › バックグラウンド動作の保護 › 電池最適化から除外 を ON にしてください",
        "zh-CN" to "-> 请打开 设置 › 后台进程保护 › 从电池优化中排除",
        "zh-TW" to "-> 請開啟 設定 › 背景行程保護 › 從電池最佳化中排除"
    )
    val fixStorage = t(
        en = "-> Settings > allow access to all files (needed to see /sdcard)",
        ja = "-> 設定 › ストレージ全体を許可 を ON にしてください (/sdcard を見るのに必要)",
        "zh-CN" to "-> 请打开 设置 › 授予全部存储权限 (看 /sdcard 需要它)",
        "zh-TW" to "-> 請開啟 設定 › 授予全部儲存權限 (看 /sdcard 需要它)"
    )
    val fixSms = t(
        en = "-> Settings > SMS detection (only needed for sms: triggers)",
        ja = "-> 設定 › SMS 検知 から許可してください (sms: トリガーを使うときだけ必要)",
        "zh-CN" to "-> 请到 设置 › 短信检测 里允许 (只有用 sms: 触发条件时才需要)",
        "zh-TW" to "-> 請到 設定 › 簡訊偵測 裡允許 (只有用 sms: 觸發條件時才需要)"
    )
    val fixCapture = t(
        en = "-> Settings > system event detection (needed by charge/battery/wifi/sensor/event triggers)",
        ja = "-> 設定 › システムイベント検知 を ON にしてください (充電/電池/wifi/センサー/event の各トリガーに必要)",
        "zh-CN" to "-> 请打开 设置 › 系统事件检测 (充电/电池/wifi/传感器/event 各触发条件都需要它)",
        "zh-TW" to "-> 請開啟 設定 › 系統事件偵測 (充電/電池/wifi/感測器/event 各觸發條件都需要它)"
    )
    val fixPaused = t(
        en = "-> automation is paused. 'z2-when resume' or the Automation tab",
        ja = "-> 自動化が一時停止中です。z2-when resume か 📜 の自動化タブで再開できます",
        "zh-CN" to "-> 自动化正处于暂停。可以用 z2-when resume 或 📜 的自动化标签页恢复",
        "zh-TW" to "-> 自動化正處於暫停。可以用 z2-when resume 或 📜 的自動化分頁還原"
    )
    val fixDisk = t(
        en = "-> less than 500MB free. Delete unused OS data in Settings",
        ja = "-> 空きが 500MB を切っています。設定の「OS データ削除」で使っていない OS を消せます",
        "zh-CN" to "-> 可用空间不足 500MB。可以在设置的“删除系统数据”里清掉不用的系统",
        "zh-TW" to "-> 可用空間不足 500MB。可以在設定的“刪除系統資料”裡清掉不用的系統"
    )
    val fixSdcard = t(
        en = "-> /sdcard is empty here; the storage permission above is what makes it visible",
        ja = "-> /sdcard が空に見えます。上の「ストレージ全体」の許可が要ります",
        "zh-CN" to "-> /sdcard 看起来是空的。需要上面那项“全部存储”的授权",
        "zh-TW" to "-> /sdcard 看起來是空的。需要上面那項“全部儲存”的授權"
    )

    val noteRedacted = t(
        en = "(SSID / IP / host names are left out on purpose)",
        ja = "(SSID・IP・ホスト名は意図的に伏せています)",
        "zh-CN" to "(SSID、IP、主机名是有意隐去的)",
        "zh-TW" to "(SSID、IP、主機名是有意隱去的)"
    )
    val allGood = t(en = "No problems found.", ja = "問題は見つかりませんでした。", "zh-CN" to "没有发现问题。", "zh-TW" to "沒有發現問題。")
    val someBad = t(en = "issues found:", ja = "気になる点:", "zh-CN" to "值得注意的地方:", "zh-TW" to "值得注意的地方:")
    val usage = t(
        en = "usage: z2doctor [--share | --clip]   (--share hands the report to Android's share sheet)",
        ja = "usage: z2doctor [--share | --clip]   (--share で報告文を共有シートに渡します)",
        "zh-CN" to "usage: z2doctor [--share | --clip]   (--share 会把报告交给 Android 的分享菜单)",
        "zh-TW" to "usage: z2doctor [--share | --clip]   (--share 會把報告交給 Android 的分享選單)"
    )
    val pausedYes = t(en = "paused", ja = "一時停止中", "zh-CN" to "已暂停", "zh-TW" to "已暫停")

    // 前回までの終了 (0.8.376)。落ちた理由は OS しか知らないので、ここに出さないと
    // 利用者からは「また消えた」以上のことが言えない。
    val secExits = t(en = "-- how it ended last time --", ja = "-- 前回までの終了 --", "zh-CN" to "-- 上次是怎么结束的 --", "zh-TW" to "-- 上次是怎麼結束的 --")
    val lExitsNote = t(
        en = "recent abnormal exits (newest first):",
        ja = "直近の異常終了 (新しい順):",
        "zh-CN" to "最近的异常结束 (新的在前):",
        "zh-TW" to "最近的異常結束 (新的在前):"
    )
    val lExitsNone = t(en = "abnormal exits: none recorded", ja = "異常終了: 記録なし", "zh-CN" to "异常结束: 没有记录", "zh-TW" to "異常結束: 沒有記錄")
    val lExitsFile = t(
        en = "(full history: ~/.z2term/exits.jsonl)",
        ja = "(全履歴: ~/.z2term/exits.jsonl)",
        "zh-CN" to "(完整历史: ~/.z2term/exits.jsonl)",
        "zh-TW" to "(完整歷史: ~/.z2term/exits.jsonl)"
    )
    val hintExitMem = t(
        en = "-> killed under memory pressure. Run fewer heavy jobs at once, or trim resident servers / tabs",
        ja = "-> メモリ不足で終了しています。重い作業の同時実行を減らすか、常駐サーバー・タブを整理してください",
        "zh-CN" to "-> 因为内存不足而被结束。请减少同时进行的重活，或者整理一下常驻服务和标签页",
        "zh-TW" to "-> 因為記憶體不足而被結束。請減少同時進行的重活，或者整理一下常駐服務和分頁"
    )

    return """
        |#!/bin/sh
        |# z2doctor: ${t(en = "self-check for \"it does not work\"", ja = "「動きません」の切り分け診断", "zh-CN" to "给“跑不起来”做的自检", "zh-TW" to "給“跑不起來”做的自檢")}
        |# ${t(
            en = "See also: z2scan self (security check, a different tool)",
            ja = "似た名前の z2scan self は「危ない設定を探す」別のコマンドです",
            "zh-CN" to "名字相近的 z2scan self 是另一条命令，用来“找出危险的设置”",
            "zh-TW" to "名字相近的 z2scan self 是另一條指令，用來“找出危險的設定”"
        )}
        |NG=0
        |OUT=""
        |say() { printf '%s\n' "${d}*"; OUT="${d}OUT${d}*
        |"; }
        |ok()   { say "OK  ${d}1"; }
        |bad()  { say "NG  ${d}1"; say "    ${d}2"; NG=${d}((NG+1)); }
        |none() { say "--  ${d}1"; }
        |
        |case "${d}1" in
        |  -h|--help|help) echo "$usage"; exit 0 ;;
        |esac
        |
        |# アプリ側にしか無い情報 (許可・設定・常駐の数)。取れなければ空のまま進める
        |# (アプリが古い / ブリッジが動いていないこと自体が診断結果になる)。
        |J=${d}(/usr/local/bin/z2api 1 doctor 2>/dev/null)
        |jget() { printf '%s' "${d}J" | sed -n "s/.*\"${d}1\":\\([^,}]*\\).*/\\1/p" | tr -d '"' ; }
        |
        |say "$head"
        |say ""
        |say "$secApp"
        |v=${d}(jget version); [ -n "${d}v" ] || v="?"
        |say "    $lVersion: ${d}v"
        |e=${d}(jget engine); [ -n "${d}e" ] || e="?"
        |say "    $lEngine: ${d}e"
        |dd=${d}(jget distro); [ -n "${d}dd" ] || dd="?"
        |say "    $lDistro: ${d}dd"
        |
        |say ""
        |say "$secLinux"
        |say "    $lKernel: ${d}(uname -r 2>/dev/null || echo '?')"
        |say "    $lHome: ${d}HOME"
        |# 空き容量 (MB)。df の出力形式は環境差があるので 4 列目を素直に見る。
        |freem=${d}(df -m "${d}HOME" 2>/dev/null | awk 'NR==2{print ${d}4}')
        |case "${d}freem" in
        |  ''|*[!0-9]*) none "$lDisk: ?" ;;
        |  *) if [ "${d}freem" -lt 500 ]; then bad "$lDisk: ${d}freem MB" "$fixDisk"; else ok "$lDisk: ${d}freem MB"; fi ;;
        |esac
        |# sshd は「動いていない」が正常な場合もあるので NG にしない (事実だけ出す)。
        |if pgrep -x dropbear >/dev/null 2>&1 || pgrep -x sshd >/dev/null 2>&1; then
        |  ok "$lSshd: running"
        |else
        |  none "$lSshd: not running"
        |fi
        |
        |say ""
        |say "$secPerm"
        |case "${d}(jget notifications)" in
        |  true) ok "$lNotify" ;;
        |  false) bad "$lNotify" "$fixNotify" ;;
        |  *) none "$lNotify: ?" ;;
        |esac
        |case "${d}(jget notification_access)" in
        |  true) ok "$lNotifyRead" ;;
        |  false) bad "$lNotifyRead" "$fixNotifyRead" ;;
        |  *) none "$lNotifyRead: ?" ;;
        |esac
        |case "${d}(jget battery_opt_ignored)" in
        |  true) ok "$lBattOpt" ;;
        |  false) bad "$lBattOpt" "$fixBattOpt" ;;
        |  *) none "$lBattOpt: ?" ;;
        |esac
        |case "${d}(jget storage_all)" in
        |  true) ok "$lStorage" ;;
        |  false) bad "$lStorage" "$fixStorage" ;;
        |  *) none "$lStorage: ?" ;;
        |esac
        |# /sdcard は許可があっても中身が無いことがあるので、許可が false のときだけ助言する。
        |if [ -d /sdcard ] && [ -n "${d}(ls -A /sdcard 2>/dev/null | head -n1)" ]; then
        |  ok "$lSdcard"
        |elif [ "${d}(jget storage_all)" = "false" ]; then
        |  bad "$lSdcard" "$fixSdcard"
        |else
        |  none "$lSdcard: ?"
        |fi
        |case "${d}(jget sms_permission)" in
        |  true) ok "$lSms" ;;
        |  false) none "$lSms: off ($fixSms)" ;;
        |  *) none "$lSms: ?" ;;
        |esac
        |
        |say ""
        |say "$secAuto"
        |case "${d}(jget event_capture)" in
        |  true) ok "$lCapture" ;;
        |  false) bad "$lCapture" "$fixCapture" ;;
        |  *) none "$lCapture: ?" ;;
        |esac
        |sr=${d}(jget servers_running); se=${d}(jget servers_enabled)
        |say "    $lServers: ${d}{sr:-?}/${d}{se:-?}"
        |re=${d}(jget rules_enabled); rt=${d}(jget rules_total)
        |if [ "${d}(jget rules_paused)" = "true" ]; then
        |  bad "$lRules: ${d}{re:-?}/${d}{rt:-?} ($pausedYes)" "$fixPaused"
        |else
        |  say "    $lRules: ${d}{re:-?}/${d}{rt:-?}"
        |fi
        |
        |say ""
        |say "$secExits"
        |# 直近の異常終了。OS が持っている記録 (ApplicationExitInfo) をアプリ経由で読む。
        |# ⚠ パイプで while へ渡さないこと — 部分シェルになって OUT (報告文) が空になる。
        |EX=${d}(/usr/local/bin/z2api 1 exitinfo 2>/dev/null)
        |if [ -n "${d}EX" ]; then
        |  say "    $lExitsNote"
        |  while IFS= read -r xl; do
        |    [ -n "${d}xl" ] && say "    ${d}xl"
        |  done <<Z2EXITS
        |${d}EX
        |Z2EXITS
        |  case "${d}EX" in *LOW_MEMORY*|*SIGKILL*) say "    $hintExitMem" ;; esac
        |  say "    $lExitsFile"
        |else
        |  none "$lExitsNone"
        |fi
        |
        |say ""
        |say "$secReport"
        |say "$noteRedacted"
        |if [ "${d}NG" -eq 0 ]; then say "$allGood"; else say "$someBad ${d}NG"; fi
        |
        |case "${d}1" in
        |  --share) printf '%s' "${d}OUT" | /usr/local/bin/z2-share "${d}(cat)" >/dev/null 2>&1 || true ;;
        |  --clip)  printf '%s' "${d}OUT" | /usr/local/bin/z2-clip set >/dev/null 2>&1 || true ;;
        |esac
        |exit 0
    """.trimMargin() + "\n"
}
