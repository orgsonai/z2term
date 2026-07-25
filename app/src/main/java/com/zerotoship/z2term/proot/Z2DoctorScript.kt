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
fun z2doctorScript(lang: String = "ja"): String {
    val d = "${'$'}"  // シェルの $ (Kotlin テンプレートと衝突しないように)
    val en = lang == "en"

    val head = if (en) "== z2doctor (this device) ==" else "== z2doctor (この端末の状態) =="
    val secApp = if (en) "-- app --" else "-- アプリ --"
    val secLinux = if (en) "-- linux side --" else "-- Linux 側 --"
    val secPerm = if (en) "-- permissions --" else "-- 許可 --"
    val secAuto = if (en) "-- automation --" else "-- 自動化 --"
    val secReport = if (en) "-- copy this when asking for help --" else "-- 助けを求めるときはここを貼る --"

    val lVersion = if (en) "version" else "版数"
    val lEngine = if (en) "engine" else "実行エンジン"
    val lDistro = if (en) "distro" else "ディストロ"
    val lKernel = if (en) "kernel" else "kernel"
    val lDisk = if (en) "free space" else "空き容量"
    val lHome = if (en) "home" else "ホーム"
    val lSdcard = if (en) "/sdcard" else "/sdcard"
    val lNotify = if (en) "notifications" else "通知を出せる"
    val lNotifyRead = if (en) "notification access" else "通知を読める"
    val lBattOpt = if (en) "battery optimization" else "電池最適化から除外"
    val lStorage = if (en) "storage (all files)" else "ストレージ全体"
    val lSms = if (en) "SMS receive" else "SMS 受信"
    val lCapture = if (en) "event detection" else "システムイベント検知"
    val lServers = if (en) "resident servers" else "常駐サーバー"
    val lRules = if (en) "automation rules" else "自動化ルール"
    val lSshd = if (en) "sshd" else "sshd"

    // NG のときに出す「次の一手」。ここが書けない項目は診断に出さない。
    val fixNotify = if (en) "-> Android settings > Apps > Z2Term > Notifications: allow"
    else "-> Android の設定 › アプリ › Z2Term › 通知 を許可してください"
    val fixNotifyRead = if (en) "-> Settings > resident servers & automation > notification detection"
    else "-> 設定 › 常駐サーバー・自動化 › 通知検知 から許可してください"
    val fixBattOpt = if (en) "-> Settings > background process protection > exclude from battery optimization"
    else "-> 設定 › バックグラウンド動作の保護 › 電池最適化から除外 を ON にしてください"
    val fixStorage = if (en) "-> Settings > allow access to all files (needed to see /sdcard)"
    else "-> 設定 › ストレージ全体を許可 を ON にしてください (/sdcard を見るのに必要)"
    val fixSms = if (en) "-> Settings > SMS detection (only needed for sms: triggers)"
    else "-> 設定 › SMS 検知 から許可してください (sms: トリガーを使うときだけ必要)"
    val fixCapture = if (en) "-> Settings > system event detection (needed by charge/battery/wifi/sensor/event triggers)"
    else "-> 設定 › システムイベント検知 を ON にしてください (充電/電池/wifi/センサー/event の各トリガーに必要)"
    val fixPaused = if (en) "-> automation is paused. 'z2-when resume' or the Automation tab"
    else "-> 自動化が一時停止中です。z2-when resume か 📜 の自動化タブで再開できます"
    val fixDisk = if (en) "-> less than 500MB free. Delete unused OS data in Settings"
    else "-> 空きが 500MB を切っています。設定の「OS データ削除」で使っていない OS を消せます"
    val fixSdcard = if (en) "-> /sdcard is empty here; the storage permission above is what makes it visible"
    else "-> /sdcard が空に見えます。上の「ストレージ全体」の許可が要ります"

    val noteRedacted = if (en)
        "(SSID / IP / host names are left out on purpose)"
    else "(SSID・IP・ホスト名は意図的に伏せています)"
    val allGood = if (en) "No problems found." else "問題は見つかりませんでした。"
    val someBad = if (en) "issues found:" else "気になる点:"
    val usage = if (en)
        "usage: z2doctor [--share | --clip]   (--share hands the report to Android's share sheet)"
    else "usage: z2doctor [--share | --clip]   (--share で報告文を共有シートに渡します)"
    val pausedYes = if (en) "paused" else "一時停止中"

    return """
        |#!/bin/sh
        |# z2doctor: ${if (en) "self-check for \"it does not work\"" else "「動きません」の切り分け診断"}
        |# ${if (en) "See also: z2scan self (security check, a different tool)" else "似た名前の z2scan self は「危ない設定を探す」別のコマンドです"}
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
