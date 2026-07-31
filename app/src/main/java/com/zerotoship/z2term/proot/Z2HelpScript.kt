package com.zerotoship.z2term.proot

/**
 * `z2help` — z2term がディストロに注入する「独自コマンド」の早見表を端末から引けるヘルプ。
 *
 * 引数なしで全 `z2*` コマンドの一覧＋一行説明を表示する。内容は全て静的テキスト
 * (外部入力なし)。先頭でアプリ版数 (`z2version --short`) があれば併記する。
 *
 * `z2term` は当面この `z2help` のエイリアス (薄いラッパー) として同梱する。将来 `z2term`
 * を別用途のコマンドに使いたくなったら、[z2termAliasScript] を差し替える (= ラッパーを
 * 本来のコマンドへ置き換える) だけでよい。launch 毎に上書きするので内容は常に最新。
 *
 * 実装メモ: 一覧本体は quote 付き heredoc (`<<'Z2HELP_EOF'`) に入れるのでシェル展開されず
 * `$` をそのまま書ける。終端 `Z2HELP_EOF` を確実に行頭へ出すため trimMargin (`|`) で組む。
 */
fun z2helpScript(lang: String = "ja"): String {
    val d = "${'$'}"  // シェルの $ (Kotlin テンプレートと衝突しないように)
    val en = lang == "en"

    val header = if (en) "Z2Term own commands" else "Z2Term 独自コマンド"

    val list = if (en) """
        |[Version / info]
        |  z2version [--short]            App version, engine, OS (distro), kernel
|  z2doctor [--share|--clip]      Why isn't it working? Self-check + a report you can paste
        |
        |[Phone features]
        |  z2-notify [-h] [-b LABEL]...   Notification (-h: banner, -b: reply button)
        |  z2-ask "question"              Ask via a notification reply field; prints the answer
        |  z2-toast "message"             Toast (short on-screen message)
        |  z2-share "text"                Hand text to Android's share sheet
        |  z2-open <url|path>             Open a URL/file in the default app
        |  z2-clip get | set [text]       Get / set the clipboard (set: stdin if no arg)
        |  z2-battery                     Battery level / charging state (JSON)
        |  z2-vibrate [ms]                Vibrate (default 200ms)
        |  z2-say "text"                  Speak via device TTS (stdin if no arg)
        |  z2-torch [on|off|toggle]       Flashlight (default toggle)
        |  z2-media [play|pause|next]     Media keys (also previous/stop)
        |  z2-volume up|down|N|N%         Media volume (returns current/max)
        |  z2-sensor [light|accel|prox]   Read one sensor sample (JSON)
        |  z2-intent -a ACT -d URI ...    Fire any Android Intent (see MACRO-GUIDE)
        |  z2-state [key]                 Current state as JSON (or one key raw)
        |  z2-screen keepon 1h | off      Stop the screen turning off by itself, for a while
        |  z2-tile set 1 backup.sh        Put a macro/command on a quick-settings tile (12 slots)
        |  z2-icon edit 1 | edit notify   Draw the tile / status-bar icon yourself (24x24 dots)
        |  z2-alarm at|daily HH:MM [name] Time trigger -> events.jsonl (list/cancel too)
        |  z2-when <trigger> run <cmd>    Auto-run on charge/battery/time/events (z2-when for usage)
        |  z2-macro list|install <name>   Bundled automation samples (see MACRO-GUIDE)
        |  z2-session list|new|send|...   Drive this app's own tabs (z2-session for usage)
        |
        |[Graphical (GUI) apps]
        |  z2gui start [WxH] | stop | status   Linux desktop (e.g. z2gui start 1280x720)
        |  z2run <gui-app>                Launch a GUI app (also opens the GUI tab)
        |
        |[Connecting]
        |  z2adb pair/connect/shell ...   adb to this phone itself, no PC  (z2adb help)
        |  sshd [-p N]                    SSH server (default: 127.0.0.1 only, key auth)
        |
        |[Security]
        |  z2scan self [--save]           Self-check this device/localhost (z2scan help)
        |  z2scan diff                    Only what changed since the baseline (exit 1 = new)
        |  z2scan net|host|cve            nmap/lynis/trivy on localhost (remote needs opt-in)
        |
        |[Help]
        |  z2help | z2term                This list
        |
        |More: 'z2adb help', run 'z2gui' with no args, or HANDBOOK section 11.
    """ else """
        |[版数・情報]
        |  z2version [--short]            アプリ版数・実行エンジン・OS(ディストロ)・kernel
|  z2doctor [--share|--clip]      動かないときの切り分け診断＋貼れる報告文
        |
        |[スマホの機能を呼ぶ]
        |  z2-notify [-h] [-b ラベル]...  通知(-h: バナー, -b: 返事のボタン)
        |  z2-ask "質問"                  通知の返信欄で聞いて、答えを標準出力へ
        |  z2-toast "メッセージ"          トースト(画面下の短いメッセージ)
        |  z2-share "テキスト"            Android の共有メニューに渡す
        |  z2-open <URL かパス>           URL/ファイルを既定アプリで開く
        |  z2-clip get | set [テキスト]   クリップボード取得/設定(set は引数なしで標準入力)
        |  z2-battery                     電池残量・充電状態(JSON)
        |  z2-vibrate [ミリ秒]            バイブ(既定 200ms)
        |  z2-say "テキスト"              端末標準 TTS で読み上げ(引数なしで標準入力)
        |  z2-torch [on|off|toggle]       フラッシュライト(既定 toggle)
        |  z2-media [play|pause|next]     メディアキー送出(previous/stop も)
        |  z2-volume up|down|N|N%         メディア音量(結果の current/max を出力)
        |  z2-sensor [light|accel|prox]   センサーを1回読む(JSON・照度/加速度/近接)
        |  z2-intent -a ACT -d URI ...    任意の Android Intent を発火(MACRO-GUIDE 参照)
        |  z2-state [キー]                今の状態を JSON で(キー指定でその値だけ)
        |  z2-screen keepon 1h | off      その時間だけ画面が自分で消えないようにする
        |  z2-tile set 1 backup.sh        クイック設定タイルにマクロ/コマンドを割り当て(12枠)
        |  z2-icon edit 1 | edit notify   タイル/ステータスバーのアイコンを自分で描く(24x24)
        |  z2-alarm at|daily HH:MM [名前] 時刻トリガー→events.jsonl(list/cancel も)
        |  z2-when <トリガー> run <コマンド> 充電/電池/時刻/端末イベントで自動実行(使い方は z2-when)
        |  z2-macro list|install <名前>   自動化マクロの同梱サンプル(MACRO-GUIDE 参照)
        |  z2-session list|new|send|...   このアプリのタブを操る(使い方は z2-session)
        |
        |[画面つき(GUI)アプリ]
        |  z2gui start [横x縦] | stop | status   Linux デスクトップ(例 z2gui start 1280x720)
        |  z2run <GUIアプリ>              GUI アプリを起動(GUI タブも自動で開く)
        |
        |[つなぐ]
        |  z2adb pair/connect/shell ...   このスマホ自身に adb(PC 不要)  (z2adb help)
        |  sshd [-p N]                    SSH サーバ(既定 127.0.0.1 のみ・鍵認証)
        |
        |[セキュリティ]
        |  z2scan self [--save]           自端末/localhost の自己診断(z2scan help)
        |  z2scan diff                    基準から変わった所だけ(増えたら終了コード 1)
        |  z2scan net|host|cve            localhost に nmap/lynis/trivy(外部は明示許可制)
        |
        |[ヘルプ]
        |  z2help | z2term                この一覧
        |
        |詳しくは: 'z2adb help' / 引数なしの 'z2gui' / HANDBOOK 第11節。
    """

    return """
        |#!/bin/sh
        |# z2term: 独自コマンド早見表 (launch 毎にアプリが再生成)。本体は静的テキスト。
        |ver=${d}(z2version --short 2>/dev/null)
        |if [ -n "${d}ver" ]; then echo "$header (app ${d}ver)"; else echo "$header"; fi
        |echo
        |cat <<'Z2HELP_EOF'
        ${list.trimMargin()}
        |Z2HELP_EOF
    """.trimMargin() + "\n"
}

/**
 * `z2term` の当面の中身 = `z2help` への薄いラッパー (エイリアス)。
 * 将来 `z2term` を別用途に使うときは、このラッパーを本来のコマンドに差し替える。
 */
fun z2termAliasScript(): String {
    val d = "${'$'}"
    return """
        |#!/bin/sh
        |# z2term: 当面は z2help のエイリアス (予約コマンド)。
        |# 将来 z2term を別用途に使うときはこのファイル(生成元 Z2HelpScript.z2termAliasScript)を差し替える。
        |exec /usr/local/bin/z2help "${d}@"
    """.trimMargin() + "\n"
}
