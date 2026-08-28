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
    // 言語ごとの文言を選ぶ道具。3 言語目は t(en = …, ja = …) の後ろへ変わり値を足す ([CliText])。
    val t = CliText(lang)

    val header = t(en = "Z2Term own commands", ja = "Z2Term 独自コマンド", "zh-CN" to "Z2Term 自有命令", "zh-TW" to "Z2Term 自有指令")

    val list = t(
        en = """
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
        |  z2-icon edit 1 | edit notify   Draw the tile / status-bar icon yourself (24/48/64 dots)
        |  z2-alarm at|daily HH:MM [name] Time trigger -> events.jsonl (list/cancel too)
        |  z2-when <trigger> run <cmd>    Auto-run on charge/battery/time/events (z2-when for usage)
        |  z2-macro list|install <name>   Bundled automation samples (see MACRO-GUIDE)
        |  z2-session list|new|send|...   Drive this app's own tabs (z2-session for usage)
        |  z2-usb list | allow [device]   Use a USB device from Linux (Android asks permission)
        |  z2-update [--check]            Update z2term itself from GitHub Releases (you approve it)
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
        |Every command above explains itself: add --help (e.g. 'z2-tile --help').
        |More: 'z2adb help', run 'z2gui' with no args, or HANDBOOK section 11.
    """,
        ja = """
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
        |  z2-icon edit 1 | edit notify   タイル/ステータスバーのアイコンを自分で描く(24〜64マス)
        |  z2-alarm at|daily HH:MM [名前] 時刻トリガー→events.jsonl(list/cancel も)
        |  z2-when <トリガー> run <コマンド> 充電/電池/時刻/端末イベントで自動実行(使い方は z2-when)
        |  z2-macro list|install <名前>   自動化マクロの同梱サンプル(MACRO-GUIDE 参照)
        |  z2-session list|new|send|...   このアプリのタブを操る(使い方は z2-session)
        |  z2-usb list | allow [機器]     USB機器をLinuxから使う(Androidの許可あり)
        |  z2-update [--check]            z2term 自身を GitHub Releases から更新(承認は自分で押す)
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
        |上のコマンドは自分で説明します: --help を付けてください (例: 'z2-tile --help')。
        |詳しくは: 'z2adb help' / 引数なしの 'z2gui' / HANDBOOK 第11節。
    """,
        "zh-CN" to """
        |[版本、信息]
        |  z2version [--short]            应用版本、执行引擎、系统(发行版)、kernel
        |  z2doctor [--share|--clip]      跑不起来时的排查诊断＋可以直接贴出去的报告
        |
        |[调用手机的功能]
        |  z2-notify [-h] [-b 标签]...    通知(-h: 横幅, -b: 回复按钮)
        |  z2-ask "问题"                  用通知的回复框提问，把答案送到标准输出
        |  z2-toast "消息"                吐司(屏幕下方的短消息)
        |  z2-share "文本"                交给 Android 的分享菜单
        |  z2-open <URL 或路径>           用默认应用打开 URL/文件
        |  z2-clip get | set [文本]       获取/设置剪贴板(set 不带参数则读标准输入)
        |  z2-battery                     电量、充电状态(JSON)
        |  z2-vibrate [毫秒]              震动(默认 200ms)
        |  z2-say "文本"                  用设备自带 TTS 朗读(不带参数则读标准输入)
        |  z2-torch [on|off|toggle]       手电筒(默认 toggle)
        |  z2-media [play|pause|next]     发送媒体键(也支持 previous/stop)
        |  z2-volume up|down|N|N%         媒体音量(输出结果的 current/max)
        |  z2-sensor [light|accel|prox]   读一次传感器(JSON，光照/加速度/接近)
        |  z2-intent -a ACT -d URI ...    触发任意 Android Intent(见 MACRO-GUIDE)
        |  z2-state [键]                  用 JSON 输出当前状态(指定键则只出那个值)
        |  z2-screen keepon 1h | off      在这段时间内让屏幕不会自己熄灭
        |  z2-tile set 1 backup.sh        把宏/命令分配到快捷设置磁贴(12 个位)
        |  z2-icon edit 1 | edit notify   自己画磁贴/状态栏图标(24〜64 格)
        |  z2-alarm at|daily HH:MM [名称] 时间触发→events.jsonl(也有 list/cancel)
        |  z2-when <触发条件> run <命令>  按充电/电池/时刻/设备事件自动运行(用法见 z2-when)
        |  z2-macro list|install <名称>   自动化宏的随附示例(见 MACRO-GUIDE)
        |  z2-session list|new|send|...   操作这个应用的标签页(用法见 z2-session)
        |  z2-usb list | allow [设备]     从Linux使用USB设备(Android会请求许可)
        |  z2-update [--check]            从 GitHub Releases 更新 z2term 自身(确认要自己按)
        |
        |[带界面(GUI)的应用]
        |  z2gui start [宽x高] | stop | status Linux 桌面(例 z2gui start 1280x720)
        |  z2run <GUI应用>                启动图形应用(也会自动打开图形标签页)
        |
        |[连接]
        |  z2adb pair/connect/shell ...   给这台手机自己上 adb(不需要电脑)  (z2adb help)
        |  sshd [-p N]                    SSH 服务器(默认只监听 127.0.0.1、密钥认证)
        |
        |[安全]
        |  z2scan self [--save]           自检本设备/localhost(z2scan help)
        |  z2scan diff                    只列出相对基准变化的部分(有增加则退出码 1)
        |  z2scan net|host|cve            对 localhost 跑 nmap/lynis/trivy(外部需显式许可)
        |
        |[帮助]
        |  z2help | z2term                这个列表
        |
        |上面的命令都会自己说明用法: 加上 --help (例: 'z2-tile --help')。
        |详情: 'z2adb help' / 不带参数的 'z2gui' / HANDBOOK 第 11 节。
    """,
        "zh-TW" to """
        |[版本、訊息]
        |  z2version [--short]            應用程式版本、執行引擎、系統(發行版)、kernel
        |  z2doctor [--share|--clip]      跑不起來時的排查診斷＋可以直接貼出去的報告
        |
        |[呼叫手機的功能]
        |  z2-notify [-h] [-b 標籤]...    通知(-h: 橫幅, -b: 回覆按鈕)
        |  z2-ask "問題"                  用通知的回覆框提問，把答案送到標準輸出
        |  z2-toast "訊息"                快顯訊息(螢幕下方的短訊息)
        |  z2-share "文字"                交給 Android 的分享選單
        |  z2-open <URL 或路徑>           用預設應用程式開啟 URL/檔案
        |  z2-clip get | set [文字]       取得/設定剪貼簿(set 不帶參數則讀標準輸入)
        |  z2-battery                     電量、充電狀態(JSON)
        |  z2-vibrate [毫秒]              震動(預設 200ms)
        |  z2-say "文字"                  用裝置自帶 TTS 朗讀(不帶參數則讀標準輸入)
        |  z2-torch [on|off|toggle]       手電筒(預設 toggle)
        |  z2-media [play|pause|next]     發送媒體鍵(也支援 previous/stop)
        |  z2-volume up|down|N|N%         媒體音量(輸出結果的 current/max)
        |  z2-sensor [light|accel|prox]   讀一次感測器(JSON，光照/加速度/接近)
        |  z2-intent -a ACT -d URI ...    觸發任意 Android Intent(見 MACRO-GUIDE)
        |  z2-state [鍵]                  用 JSON 輸出當前狀態(指定鍵則只出那個值)
        |  z2-screen keepon 1h | off      在這段時間內讓螢幕不會自己熄滅
        |  z2-tile set 1 backup.sh        把巨集/指令分配到快速設定圖塊(12 個位)
        |  z2-icon edit 1 | edit notify   自己畫圖塊/狀態列圖示(24〜64 格)
        |  z2-alarm at|daily HH:MM [名稱] 時間觸發→events.jsonl(也有 list/cancel)
        |  z2-when <觸發條件> run <指令>  按充電/電池/時刻/裝置事件自動執行(用法見 z2-when)
        |  z2-macro list|install <名稱>   自動化巨集的隨附示例(見 MACRO-GUIDE)
        |  z2-session list|new|send|...   操作這個應用程式的分頁(用法見 z2-session)
        |  z2-usb list | allow [裝置]     從Linux使用USB裝置(Android會請求許可)
        |  z2-update [--check]            從 GitHub Releases 更新 z2term 自身(確認要自己按)
        |
        |[帶介面(GUI)的應用程式]
        |  z2gui start [寬x高] | stop | status Linux 桌面(例 z2gui start 1280x720)
        |  z2run <GUI應用程式>                啟動圖形應用程式(也會自動開啟圖形分頁)
        |
        |[連線]
        |  z2adb pair/connect/shell ...   給這台手機自己上 adb(不需要電腦)  (z2adb help)
        |  sshd [-p N]                    SSH 伺服器(預設只監聽 127.0.0.1、金鑰認證)
        |
        |[安全]
        |  z2scan self [--save]           自檢本裝置/localhost(z2scan help)
        |  z2scan diff                    只列出相對基準變化的部分(有增加則退出碼 1)
        |  z2scan net|host|cve            對 localhost 跑 nmap/lynis/trivy(外部需顯式許可)
        |
        |[說明]
        |  z2help | z2term                這個列表
        |
        |上面的指令都會自己說明用法: 加上 --help (例: 'z2-tile --help')。
        |詳情: 'z2adb help' / 不帶參數的 'z2gui' / HANDBOOK 第 11 節。
    """
    )

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
