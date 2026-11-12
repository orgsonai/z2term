package com.zerotoship.z2term.proot.lang

import com.zerotoship.z2term.proot.ASK_TRIES
import com.zerotoship.z2term.proot.CliLanguage
import com.zerotoship.z2term.proot.GuiScriptStrings
import com.zerotoship.z2term.proot.SshdScriptStrings
import com.zerotoship.z2term.proot.Z2ApiMsg
import com.zerotoship.z2term.proot.rssNotificationAction

/**
 * 繁體中文 (`zh-TW`) の、**端末に出る文言**。
 *
 * 英語と日本語は文言を使う場所に書いてあり、ここには持たない。ここにあるのは
 * その言語の訳だけで、**無い文言は英語で出る**（壊れはしない）。
 * 書き方と、訳の埋まり具合の確かめ方は [CliLanguage] を参照。
 */
internal object CliZhTw : CliLanguage {

    override val code: String = "zh-TW"

    // --- z2-* の文言 (メッセージ集 Z2ApiMsg を上書きしたもの。下の ApiMsgZhTw) ---

    override fun apiMsg(d: String): Z2ApiMsg = ApiMsgZhTw(d)

    // --- GUI (z2gui) / sshd が echo する文言 ---

    override fun gui(): GuiScriptStrings = GuiScriptStrings(
            installing = "📦 正在安裝整套圖形環境",
            cleanInstalling = "🧹 正在全新安裝圖形環境",
            noPackageManager = "❌ 找不到支援的套件管理器 (apk/apt-get/pacman)。",
            invalidGeometry = "❌ 解析度的寫法不正確",
            noXvnc = "❌ 沒有 Xvnc/Xtigervnc (tigervnc 未安裝)",
            alreadyRunning = "✅ 圖形環境已經在執行",
            startingXvnc = "▶ 啟動",
            xvncFailed = "❌ Xvnc 啟動失敗。日誌:",
            ready = "✅ 圖形環境準備完成。請從 z2term 的圖形分頁連線。",
            running = "✅ 圖形環境執行中",
            stopped = "⏹ 圖形環境已停止",
            stoppedMsg = "⏹ 已停止",
            usage = "用法: z2gui [start [WxH] [clean] | stop | status | install | clean | check]",
            installFailed = "❌ 沒能安裝整套圖形環境 (Xvnc / openbox / D-Bus)。",
            installFailedHint = "   請確認網路連線。需要還原時，可在終端中執行 z2gui clean。",
            audioInstalling = "🔊 圖形介面聲音: 正在安裝 PulseAudio",
            audioNoPulse = "⚠️ 圖形介面聲音: 沒有 pulseaudio，將以無聲繼續",
            audioNoPactl = "⚠️ 圖形介面聲音: 沒有 pactl，將以無聲繼續",
            audioStartFailed = "⚠️ 圖形介面聲音: PulseAudio 啟動失敗",
            audioReady = "🔊 圖形介面聲音: z2sink.monitor →",
            menuApps = "應用程式",
            menuWindows = "視窗",
            menuDesktops = "虛擬桌面",
            menuPreviousDesktop = "上一個桌面",
            menuNextDesktop = "下一個桌面",
            menuReload = "重新載入選單"
        )

    override fun sshd(): SshdScriptStrings = SshdScriptStrings(
            invalidPort = "❌ 連接埠號不正確",
            configOk = "sshd(dropbear) 設定 OK",
            installingDropbear = "📦 沒有 dropbear，正在安裝…",
            dropbearInstallFailed = "❌ 無法安裝 dropbear。請確認網路連線和套件名稱。",
            privilegedPortWarn = "⚠️ 特權連接埠。在 proot(非 root) 下很可能 bind 不上 (建議用 1024 以上)。",
            lanExposeWarn = "⚠️ 區域網路/廣域網開放模式: 必須用金鑰認證，並且要用強金鑰。",
            noAuthorizedKeys = "⛔ ~/.ssh/authorized_keys 還沒有設定。已中止以區域網路開放方式啟動。",
            noAuthorizedKeysHint = "   請先把公鑰登記到 ~/.ssh/authorized_keys 再重新執行。",
            loopbackBind = "🔒 只在 loopback 上啟動。要對區域網路開放請用 --lan 或 Z2_SSHD_LAN=1。",
            foregroundStart = "▶ 以前景方式啟動 dropbear (Ctrl-C 停止)",
            listeningLan = "✅ dropbear listening (root，僅金鑰認證)",
            listeningLoopback = "✅ dropbear listening (root，僅金鑰認證，僅 loopback)",
            startupFailed = "❌ dropbear 啟動失敗。日誌:",
            deviceIp = "裝置IP",
            authKeysHint = "ℹ ~/.ssh/authorized_keys 還沒有設定。登記用戶端的公鑰之後就能連線了。",
            authKeysExample = "   例: cat /tmp/id_ed25519.pub >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
        )

    // --- 声で操作の言葉の表 (voice.sh の bundled_words。下の VOICE_WORDS) ---

    override val voiceWords: String = VOICE_WORDS

    // --- スクリプトの中の文言 (t("名前", en = …, ja = …) の名前で引く) ---

    override val texts: Map<String, String> by lazy {
        mapOf(
            "keyring.msgStart" to "🔑 正在準備 pacman 的金鑰環 (只有第一次，不聯網)。需要稍等一會兒…",
            "keyring.msgOk" to "✅ 金鑰環準備好了。現在可以安裝套件了。",
            "keyring.msgInitFail" to "❌ pacman-key --init 失敗了。下次開啟這個分頁時會再試一次。",
            "keyring.msgNoKeyrings" to "❌ 找不到隨附的金鑰環 (/usr/share/pacman/keyrings)。",
            "keyring.msgPopulateFail" to "❌ pacman-key --populate 失敗了。下次開啟這個分頁時會再試一次。",
            "action.description" to "具名的Android操作巨集。用 z2-key permission 啟用Android操作。\n儲存時驗證並取代定義；screen=current 記錄目前螢幕尺寸。\nrun 等待完成，start 傳回執行ID。中斷 run 會停止該次執行。\n同時執行一個，定義最多64條指令、1–300秒。可從通知或 stop 停止後續步驟。\n座標操作需要目標應用程式及相符的螢幕尺寸、方向。單次操作最多3秒。\n手勢入口使用 start，Shell巨集或 z2-when 使用 run。\n儲存於 /root/.z2term/actions/NAME.actions（共用家目錄）。也可用 z2-macro actions。",
            "action.controlHelp" to "version=2 支援 repeat 次數|forever ... end、if 條件 ... else ... end 及 call 名稱。\n條件使用 z2-state 鍵或 foreground=套件名稱；逗號表示AND，!表示否定。\n無法取得條件狀態時執行失敗。執行前驗證並固定所有呼叫的巨集。\n每個定義最多64條指令，每次執行最多10000條。父巨集和子巨集的逾時均有效。",
            "action.uiHelp" to "version=2還支援click 選擇器、long-click 選擇器和wait-ui 毫秒 選擇器。\n選擇器範例：text=繼續、desc=更多選項、id=org.example.app:id/next。精確比對，空格不加引號。\n先設定target或launch。wait-ui每250毫秒查詢，期限1–30000毫秒；多項符合時失敗。\n操作僅傳送一次，下一畫面使用wait-ui等待。輸入欄位及密碼欄位除外。\ninspect 套件名稱以JSON傳回前景目標應用程式的顯示文字、說明和ID。",
            "adb.mNoPm" to "z2adb: 找不到支援的套件管理器 (apk/apt-get/pacman)。",
            "adb.mInstalling" to "z2adb: 正在用 ${d}PM 安裝 adb 用戶端 (${d}ADB_PKG) ...",
            "adb.mInstallFail" to "z2adb: adb 安裝失敗。請手動安裝後再試。",
            "adb.mHaveAdb" to "z2adb: adb 已經安裝過了。",
            "adb.mNeedWireless" to "z2adb: 如果失敗，請開啟 設定 > 開發者選項 > 無線偵錯 (Android 11+)。",
            "adb.mPairPort" to "配對用連接埠 (“使用配對碼配對裝置”處顯示的): ",
            "adb.mConnPort" to "連線用連接埠 (顯示在“無線偵錯”正下方): ",
            "adb.usageText" to """
        z2adb - 透過無線偵錯連線本機自己的 adb (自連 adb，不需要電腦)。

        前提: 開啟 設定 > 開發者選項 > 無線偵錯 (Android 11+)。

          z2adb setup                安裝 adb 用戶端 (自動判斷 apk/apt-get/pacman)
          z2adb pair  <port> [code]  配對 (用設定裡的配對連接埠 + 6 位配對碼)
          z2adb connect <port>       連線 (用“無線偵錯”正下方的連接埠)
          z2adb status               顯示 'adb devices -l'
          z2adb <其他任何內容>       原樣交給 adb (shell, logcat, install, ...)

        目標預設是 127.0.0.1。傳 host:port 可以覆寫 (也可以用 Z2ADB_HOST)。
        例:  z2adb pair 37115 123456  →  z2adb connect 40123  →  z2adb shell
    """.trimIndent(),
            "audio.description" to "無需開啟GUI，即可在Android播放本機Linux命令的聲音。\ninstall: 在目前Linux系統安裝PulseAudio（下載套件）。\nrun 命令 [參數...]: 連接音訊並執行命令，結束時停止專用音訊伺服器。\n指定Shell可為其中多個命令啟用聲音。不會覆寫現有設定檔。\n需要本機終端分頁及支援PulseAudio的播放器。不支援SSH。",
            "audio.missing" to "缺少音訊工具。請執行：z2-audio install",
            "audio.failed" to "音訊連接失敗，未啟動指定命令。",
            "doctor.head" to "== z2doctor (這台裝置的狀態) ==",
            "doctor.secApp" to "-- 應用程式 --",
            "doctor.secLinux" to "-- Linux 一側 --",
            "doctor.secPerm" to "-- 權限 --",
            "doctor.secAuto" to "-- 自動化 --",
            "doctor.secReport" to "-- 求助時請貼這一段 --",
            "doctor.lVersion" to "版本",
            "doctor.lEngine" to "執行引擎",
            "doctor.lDistro" to "發行版",
            "doctor.lKernel" to "kernel",
            "doctor.lDisk" to "可用空間",
            "doctor.lHome" to "主目錄",
            "doctor.lSdcard" to "/sdcard",
            "doctor.lNotify" to "能發通知",
            "doctor.lNotifyRead" to "能讀通知",
            "doctor.lBattOpt" to "已排除電池最佳化",
            "doctor.lStorage" to "全部儲存",
            "doctor.lSms" to "接收簡訊",
            "doctor.lCapture" to "系統事件偵測",
            "doctor.lServers" to "常駐服務",
            "doctor.lRules" to "自動化規則",
            "doctor.lSshd" to "sshd",
            "doctor.fixNotify" to "-> 請到 Android 的 設定 › 應用程式 › Z2Term › 通知 裡允許",
            "doctor.fixNotifyRead" to "-> 請到 設定 › 常駐服務與自動化 › 通知偵測 裡允許",
            "doctor.fixBattOpt" to "-> 請開啟 設定 › 背景行程保護 › 從電池最佳化中排除",
            "doctor.fixStorage" to "-> 請開啟 設定 › 授予全部儲存權限 (看 /sdcard 需要它)",
            "doctor.fixSms" to "-> 請到 設定 › 簡訊偵測 裡允許 (只有用 sms: 觸發條件時才需要)",
            "doctor.fixCapture" to "-> 請開啟 設定 › 系統事件偵測 (充電/電池/wifi/感測器/event 各觸發條件都需要它)",
            "doctor.fixPaused" to "-> 自動化正處於暫停。可以用 z2-when resume 或 📜 的自動化分頁還原",
            "doctor.fixDisk" to "-> 可用空間不足 500MB。可以在設定的“刪除系統資料”裡清掉不用的系統",
            "doctor.fixSdcard" to "-> /sdcard 看起來是空的。需要上面那項“全部儲存”的授權",
            "doctor.noteRedacted" to "(SSID、IP、主機名是有意隱去的)",
            "doctor.allGood" to "沒有發現問題。",
            "doctor.someBad" to "值得注意的地方:",
            "doctor.usage" to "usage: z2doctor [--share | --clip]   (--share 會把報告交給 Android 的分享選單)",
            "doctor.pausedYes" to "已暫停",
            "doctor.secExits" to "-- 上次是怎麼結束的 --",
            "doctor.lExitsNote" to "最近的異常結束 (新的在前):",
            "doctor.lExitsNone" to "異常結束: 沒有記錄",
            "doctor.lExitsFile" to "(完整歷史: ~/.z2term/exits.jsonl)",
            "doctor.hintExitMem" to "-> 因為記憶體不足而被結束。請減少同時進行的重活，或者整理一下常駐服務和分頁",
            "doctor.title" to "給“跑不起來”做的自檢",
            "doctor.seeAlso" to "名字相近的 z2scan self 是另一條指令，用來“找出危險的設定”",
            "help.header" to "Z2Term 自有指令",
            "help.list" to """
        |[版本、訊息]
        |  z2version [--short]            應用程式版本、執行引擎、系統(發行版)、kernel
        |  z2doctor [--share|--clip]      跑不起來時的排查診斷＋可以直接貼出去的報告
        |
        |[呼叫手機的功能]
        |  z2-notify [-h] [-b 標籤]...    通知(-h: 橫幅, -b: 回覆按鈕)
        |  z2-ask "問題"                  用通知的回覆框提問，把答案送到標準輸出
        |  z2-listen [-w 詞,...]          把說的話轉成文字(只在裝置上辨識。z2-listen -h)
        |  z2-toast "訊息"                快顯訊息(螢幕下方的短訊息)
        |  z2-share "文字"                交給 Android 的分享選單
        |  z2-open <URL 或路徑>           用預設應用程式開啟 URL/檔案
        |  z2-view <檔案.html>            在應用程式裡閱讀這裡做好的頁面
        |  z2-edit <檔案>                 在應用程式的編輯畫面裡改寫文字檔
        |  z2-qr                          用相機掃描 QR(出現一個開啟按鈕)
        |  z2-img <檔案>                  直接在終端機裡畫出圖片(PNG/JPEG/WebP/GIF/BMP)
        |  z2-clip get | set [文字]       取得/設定剪貼簿(set 不帶參數則讀標準輸入)
        |  z2-file pick | save FILE     Android 檔案選擇與匯出 (z2-file --help)
        |  z2-battery                     電量、充電狀態(JSON)
        |  z2-vibrate [毫秒]              震動(預設 200ms)
        |  z2-say "文字"                  用裝置自帶 TTS 朗讀(不帶參數則讀標準輸入)
        |  z2-torch [on|off|toggle|status]       手電筒(預設 toggle)
        |  z2-media [play|pause|next]     發送媒體鍵(也支援 previous/stop)
        |  z2-volume up|down|N|N%         媒體音量(輸出結果的 current/max)
        |  z2-audio run COMMAND ...      在Android播放Linux命令的聲音
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
    """,
            "help.edge" to "[浮動面板]\nz2-edge --help   定義面板並更新內容\nz2-key --help    Android 全域操作\nz2-shot --help   只擷取圈出的部分\nz2-app --help    列出應用程式並匯出圖示\nz2-action --help 具名的Android操作巨集\nz2-ui --help     AI 操作應用程式（說明是給 AI 的指南）",
            "macro.rssBody.head" to """
#
# 準備:
#   1) 在這裡一行寫一個訂閱源 URL:  ~/.z2term/rss/feeds.txt
#   2) 每 30 分鐘看一次:
#        z2-when time:every=30m run ~/.z2term/macros/rss.sh
#   3) 可選 - 一行一個、不想被埋掉的訂閱源或詞:  ~/.z2term/rss/important.txt
#      命中的文章會單獨發一條通知，這樣流量大的源就壓不掉它。
#      寫 URL 的一部分或標題的一部分都行 (例: example.org)。
#   4) 將通知操作註冊到自動化:
#        z2-when event:notify_action run '$rssNotificationAction'
#      “開啟”把那篇文章送到瀏覽器，“列表”把收集到的文章做成讀物，在 z2term 裡開啟。
#      注意: 從通知按鈕開啟畫面需要“顯示在其他應用程式上層”的權限 (和邊緣面板同一個)。
#   5) 開啟已收集文章的列表: sh ~/.z2term/macros/rss.sh view
#      在「指令列表 -> 自動化 -> 自動化規則」中管理這兩條規則。
#      如果通知規則已存在，請編輯原規則，不要重複新增。
#   6) 新文章通知: 預設定期巡查會通知，從列表取得時不通知。
#        sh ~/.z2term/macros/rss.sh notify off        # 定期巡查的通知: on / off
#        sh ~/.z2term/macros/rss.sh notify-view on    # 列表的「重新整理」「文章列表」的通知: on / off
#      兩者也都是列表「設定」裡的開關，隨時可以變更。
#
# 需要: python3 (Alpine: apk add python3 / Debian: apt-get install -y python3 / Arch: pacman -S python)
#   使用縮圖 (列表的「設定」) 還需要 Pillow (Alpine: apk add py3-pillow / Debian: apt-get install -y python3-pil / Arch: pacman -S python-pillow)
# 電池: 取得越勤耗得越多。不要短於 30 分鐘。
#
# z2-run: z2-when time:every=30m run ~/.z2term/macros/rss.sh
""",
            "macro.cKeep" to "seen/latest 裡保留的行數上限",
            "macro.cNoPy" to "需要 python3 (例如 apk add python3)",
            "macro.cWriteFeeds" to "請在這裡一行寫一個訂閱源 URL:",
            "macro.cFeedsHint" to "# 一行寫一個訂閱源 URL (以 # 開頭的行會被忽略)",
            "macro.cSkipFail" to "取得或解析失敗的那一條就默默跳過 (其他訂閱源繼續)",
            "macro.cDiff" to "# 減去已讀的部分，只留下新的 (和 z2scan 的基準差分是同一個套路)。\n# 不相信訂閱源的日期和排列順序 — 這兩樣都靠不住。",
            "macro.cPrepend" to "# 新的堆在上面，這樣小工具的“開頭 (head)”模式讀起來才順。",
            "macro.cNotify" to "新增 %s 條",
            "macro.cOpen" to "開啟",
            "macro.cListNone" to "還什麼都沒收集到 — 請先不帶參數執行一次。",
            "macro.cListHint" to "列出來的方法: rss.sh list [條數]",
            "macro.cList" to "列表",
            "macro.cPageTitle" to "訂閱中的源",
            "macro.cPageSites" to "%s 個站點",
            "macro.cPageArticles" to "%s 篇文章",
            "macro.cPageDoc" to "# 做出讀物 (HTML)。z2-view 會在應用程式裡開啟它 (不需要伺服器和瀏覽器)。\n" +
            "# 按站點分組，新的在前，每篇都放一個“開啟”。手機的主題以 CSS 變數送達。",
            "macro.cViewHint" to "閱讀的方法: rss.sh view",
            "macro.cHitMax" to "一次執行裡單獨通知的條數上限 (超出的並到匯總通知裡)",
            "macro.cSplitDoc" to "# 把不能錯過的挑出來。匯總通知的正文只放得下 3 條，流量大的\n" +
            "# 訂閱源要是同時更新，重要的那一條就被擠出去了。命中的文章**單獨發通知**\n" +
            "# (通知 ID 由應用程式一條一條分開發放，所以分開之後既不會被覆寫也不會被省略)。",
            "macro.cNameDoc" to "# 把 URL 放進通知的名字裡。按下按鈕時它會原樣作為 notify_action 的 {name} 回來，\n" +
            "# 所以即使螢幕上有好幾條通知，也不會搞錯是哪一條的“開啟”(準備 4 的規則)。",
            "macro.cListDoc" to "# 把收集到的文章排得好讀一些輸出。不用顏色 — 要和檔案、小工具裡看到的一致。\n" +
            "# latest.txt 本身保持一行一篇的原始資料 (小工具的 tail 和 rss-open.sh 會讀它)。",
            "macro.cSettings" to "設定",
            "macro.cArchive" to "封存",
            "macro.cDelete" to "刪除",
            "macro.cThumbs" to "縮圖",
            "macro.cSave" to "保存過去的文章",
            "macro.cNotifyPoll" to "通知新文章（定期巡查）",
            "macro.cNotifyView" to "通知新文章（從列表取得時）",
            "macro.cDeleteSite" to "刪除此站點的全部文章",
            "macro.cConfirmSite" to "從列表中刪除 %h 的 %s 篇文章？",
            "macro.cArticles" to "文章列表",
            "macro.cPrev" to "上一頁",
            "macro.cNext" to "下一頁",
            "macro.cCount" to "%s 篇",
            "macro.cUndated" to "日期不明",
            "macro.cArchiveEmpty" to "還沒有保存的文章。在「設定」中把「保存過去的文章」設為「保存」後，取得的文章會留在這裡。",
            "macro.cSearch" to "搜尋",
            "macro.cSearchWord" to "搜尋詞 (以空格分隔時需全部包含)",
            "macro.cByMonth" to "按月檢視",
            "macro.cNoHit" to "沒有找到。",
            "macro.cDropMonth" to "刪除這個月",
            "macro.cDropConfirm" to "刪除 %m 保存的 %s 篇文章？此操作無法復原。",
            "macro.cOnOff" to "請指定 on 或 off",
            "macro.cNoPillow" to "縮圖需要 Python 的 Pillow (Alpine: apk add py3-pillow / Debian: apt-get install -y python3-pil / Arch: pacman -S python-pillow)",
            "macro.rssOpenBody.head" to """
#
# 準備: 先把收集的那一側裝好
#   z2-macro install rss
# 小工具: 在狀態小工具的設定裡，把“rss-open”分配給一個按鈕。
#
# z2-run: 把 rss-open 分配給狀態小工具的按鈕 (想在終端機裡試就用 sh ~/.z2term/macros/rss-open.sh)
""",
            "macro.cNone" to "還沒有文章",
            "macro.cAllRead" to "沒有新的可以開啟了",
            "macro.cPick" to "# latest.txt 的每行是“標題  URL”。取出還沒開啟過的第一個 URL。",
            "macro.cCap" to "給已開啟的記錄設上限，免得一直漲下去",
            "macro.copied" to "已複製驗證碼: ${d}{code}",
            "macro.cleared" to "已清除複製的驗證碼",
            "macro.otpTitle" to "一次性驗證碼",
            "macro.cTtl" to "複製之後過多少秒清除 (只有直接放進去時才生效)",
            "macro.cCode" to "# 驗證碼的提取應用程式一側已經做好了。取不到的時候什麼都不做。",
            "macro.cTry" to "# 先試著直接放進去。只有**你正在看著這個應用程式**的時候才放得進去 —\n" +
            "# Android 10+ 只讓前景應用程式寫剪貼簿，在背景跑的那次會被默默丟掉。\n" +
            "# 放沒放進去，讀回來看一眼就知道 (寫不進去也不會報錯)。",
            "macro.cFallback" to "  # 沒放進去 = 你沒在看這個應用程式。改用通知上的“複製”按鈕交給你\n" +
            "  # (按下的那一瞬間應用程式會到前景)。\n" +
            "  # ⚠ 走這條路放進去的，下面的 TTL 清不掉 — 因為清除同樣需要在前景。",
            "macro.cClear" to "# 過了 TTL 秒，只有剪貼簿裡還是我們複製的那個驗證碼時才清空\n" +
            "# (這期間複製過別的東西的話，那些不動)。",
            "macro.cHow" to """
# ■ 複製來電通知中的電話號碼
#   依序檢查標題和內文中的純電話號碼，透過通知的「複製」按鈕提供。
#   不查詢通訊錄，已儲存和未儲存的聯絡人都適用。
#   只有名字時無法取得號碼。僅使用通知存取權限。
#   為相容現有規則，保留檔名 unknown-call.sh。
""",
            "macro.cIsNum" to """
# 是不是「號碼本身」? 把電話號碼會用到的字元 (數字 + - ( ) 空格) 全部去掉，如果**什麼都不剩**，
# 並且數字有 7〜15 位，就當成號碼。
#   -> 包含名字或其他文字的欄不是純號碼，繼續檢查另一欄。
#   -> 「未知」「隱藏號碼」也會因為數字不夠而在這裡被排除 (想收就把下面的判斷放寬)。
# ⚠ 不要寫成 case [!...]: 模式裡的 ) 會被當成 case 的分隔符。
""",
            "macro.cScan" to "# 來電者一般在標題裡，但有的電話應用程式會放在正文裡。兩邊都看。",
            "macro.cSkip" to "# 兩欄中都沒有純號碼時，不顯示複製通知。",
            "macro.cWhat" to "# 是哪一種觸發的，看類別就知道 (和登記時的 notify:category= 是同一個值)。",
            "macro.cCopy" to """
# 號碼透過**通知上的「複製」按鈕**交給你 (-c)。在這裡呼叫 z2-clip set 是放不進去的 —
# Android 10+ 只讓前景應用程式寫剪貼簿，而來電時在前景的是電話應用程式。
# 按下按鈕的那一瞬間 z2term 會到前景，所以那時一定放得進去。
""",
            "macro.missed" to "未接來電",
            "macro.incoming" to "來電",
            "macro.title" to "複製電話號碼",
            "macro.stNew" to "未安裝",
            "macro.stSame" to "相同",
            "macro.stDiff" to "有差異",
            "macro.msgInstalled" to "已安裝:",
            "macro.msgSame" to "同樣的內容已經裝過了:",
            "macro.msgOutdated" to "和隨附版的內容不一樣:",
            "macro.msgHowUpdate" to "先看看差別: z2-macro diff %s   /   用隨附版替換: z2-macro install -f %s (你自己改的部分會沒了)",
            "macro.msgNotInstalled" to "還沒有安裝 (請先 z2-macro install):",
            "macro.msgRemoved" to "已刪除:",
            "macro.msgAbsent" to "沒有安裝:",
            "macro.msgRemoveDiffers" to "和隨附版的內容不一樣，所以沒有刪除:",
            "macro.msgHowRemove" to "先看看差別: z2-macro diff %s   /   仍然刪除: z2-macro remove -f %s (你自己改的部分也會沒了)",
            "macro.msgNoDiffTool" to "這個環境裡沒有 diff。內容可以用 z2-macro show 查看。",
            "macro.msgSameAsBundled" to "和隨附版相同。",
            "macro.msgNotFound" to "沒有這個示例:",
            "macro.msgHintResident" to "要讓它常駐，請在 ⚙設定 → 常駐服務 裡登記下面這條:",
            "macro.msgHintRun" to "怎麼執行:",
            "macro.remindBody.head" to """
#
# 準備 (只做一次):
#   sh ~/.z2term/macros/remind.sh setup   登記兩個介面和兩個圖塊
#
# 語法:
#   remind.sh <什麼時候> <正文>      新增一條提醒
#   remind.sh list                   列出來 (開頭是編號)
#   remind.sh del <編號|all>         取消 (rm 也可以)
#   remind.sh peek                   用通知看一覽 (帶「刪除」按鈕)
#   remind.sh ask                    在通知的回覆框裡問 (給圖塊用)
#   remind.sh setup                  登記介面和圖塊 (最開始做一次)
#   remind.sh help                   這段說明
#
# <什麼時候> — 一次性:
#   30m / 90s / 2h                   從現在算起
#   18:30                            下一個 18:30 (過了就是明天)
#   sat 21:00                       下一個對應星期幾，僅一次（今天尚未到時也算）
#   日語：土曜日の夜九時 = sat 21:00；朝七時 = 07:00；夜九時半 = 21:30
#   日語的夜12時含義不明確；午夜請明確日期並用00:00。
#   tomorrow 18:30 / 3d 07:00        按天 (3d = 3 天後)
#   07/30 19:00                      月/日 (過了就是明年)
#   2030 07/30 19:00                 帶年份
#   203007301900 / 07301900          只寫數字 (YYYYMMDDHHMM / MMDDHHMM)
#   ⚠ 不寫時刻的話就保持**當前的時刻** (3d / 07/30 …)
#
# <什麼時候> — 重複:
#   daily 07:00 / weekday 09:00      每天 / 週一到週五
#   weekly mon 09:00                 那個星期幾
#   monthly 15 09:00                 那個日子
#   yearly 07/30 19:00               那個月日
#   every 19:00 / every wed 19:00    只寫 every 也行 (由後面那個詞決定)
#   every 15 19:00 / every 07/30 19:00  -> 每天 / 每週 / 每月 / 每年
#
# 例:
#   remind.sh 30m 吃藥
#   remind.sh tomorrow 18:30 倒垃圾
#   remind.sh monthly 25 10:00 房租
#   remind.sh list        ->  1  ⏰ 07/30 18:30  倒垃圾
#   remind.sh del 1
#
# z2-run: sh ~/.z2term/macros/remind.sh setup   (之後只要 remind.sh 30m ... 就行)
""",
            "macro.cStore" to """# 一條提醒 = ${d}DIR/<id>.txt 裡的一行，用定位字元分隔: 種類 / 顯示的寫法 / when 的 id / 正文。
#   種類: once (還沒響的一次性) / fired (已經響過的一次性) / repeat (重複)
#   when 的 id: 只有 repeat 才有 (刪除時要調 z2-when remove)。其餘是 '-'。
# 正文放在最後，是為了讓它能裝下除 TAB 以外的任何字元。""",
            "macro.cParse" to """# 讀 1〜3 個詞，決定下面這些。
#   KIND … once|repeat   PLAN … 列表裡顯示的寫法   SPEC … 交給 z2-alarm/z2-when 的形式   USED … 用掉了幾個詞""",
            "macro.pDaily" to "每天 ",
            "macro.pWeekday" to "工作日 ",
            "macro.pWeekly" to "每週",
            "macro.pMonthly" to "每月 ",
            "macro.pYearly" to "每年 ",
            "macro.mDay" to "日",
            "macro.eDaily" to "每天",
            "macro.eWeekly" to "每週",
            "macro.eMonthly" to "每月",
            "macro.eYearly" to "每年",
            "macro.mBadDate" to "沒有這個日期 (例: 07/30):",
            "macro.cEvery" to """  # 「every …」的簡寫。**後面那個詞的形狀**決定是每天/每週/每月/每年:
  #   every 19:00 -> 每天 / every wed 19:00 -> 每週 / every 15 19:00 -> 每月 /
  #   every 07/30 19:00 -> 每年
  # ⚠ 詞的個數不變 (只是一個詞換成了另一個詞)，所以 USED 保持原樣即可。""",
            "macro.cExpand" to "# 從 every 後面來的那個詞，決定它指的是哪一種重複。",
            "macro.cDigits" to """    # 只寫數字的形式: 202607301900 = YYYYMMDDHHMM / 07301900 = MMDDHHMM。
    # ⚠ 要先看 12 位再看 8 位 (8 位的模式對 12 位的串也會命中)。""",
            "macro.cYmd" to """  # 用年月日寫的。⚠ z2-alarm 不能接收日期，所以這裡也折算成時間差。""",
            "macro.cCivil" to """# 年月日 → 從 1970-01-01 起的天數 (含閏年・Howard Hinnant 的 days_from_civil)。
# ⚠ date -d "2026-07-30" 在 busybox 上靠不住，所以自己數。
# ⚠ 只用天數之差，時刻交給 [day_epoch]，這樣時區就相互抵消了。""",
            "macro.cCron" to """# 從 HH:MM 和星期字段做出 cron 表達式 (分 時 日 月 星期)。
# ⚠ 不要用 expr 去掉開頭的 0 — 結果為 0 時退出碼是 1，
#   `expr "${d}m" + 0 || echo "${d}m"` 右邊也會跑，值就變成兩行了。""",
            "macro.afterLabel" to """# "30m" → "30分鐘後 (14:35)"。把鐘點也顯示出來，是為了登記完能馬上核對。
after_label() {
  num=${d}{1%[smh]}; u=${d}{1#${d}num}
  case ${d}u in s) sec=${d}num; unit=秒 ;; m) sec=${d}((num*60)); unit=分鐘 ;; h) sec=${d}((num*3600)); unit=小時 ;; esac
  at=${d}(date -d "@${d}(( ${d}(date +%s) + sec ))" +%H:%M 2>/dev/null) || at=
  [ -n "${d}at" ] && echo "${d}num${d}unit後 (${d}at)" || echo "${d}num${d}unit後"
}""",
            "macro.mUsageAdd" to "usage: remind.sh <什麼時候> <正文>",
            "macro.mBadWhen" to "看不懂時間 (例: 30m / 18:30 / tomorrow 18:30 / 3d 09:00 / daily 07:00):",
            "macro.mBadTime" to "時刻請寫成 HH:MM:",
            "macro.mBadRange" to "時刻超出範圍 (00:00〜23:59):",
            "macro.mNoTime" to "沒有寫時刻 (例: daily 07:00):",
            "macro.mBadDow" to "看不懂是星期幾:",
            "macro.mPastTime" to "那個時刻已經過去了:",
            "macro.cDays" to """    # 按日期寫的說法。不寫時刻就保持**當前的時刻** (不擅自定一個預設時刻)。
    # 也可能整個連成一個詞送過來，所以黏在一起的寫法也接受。""",
            "macro.cDayEpoch" to """# 把「N 天後的 HH:MM」換算成 epoch 秒。⚠ busybox 沒有 date -d \"tomorrow\"，
# 所以先求出今天的 0 點，再把天數和時刻加上去。
# ⚠ 帶前導 0 的值 (\"08\") 交給 ${d}(()) 會被當成八進位，一定要去掉。
# ⚠ 一天按 86400 秒相加，所以在有日光節約時間的地區，切換那天會差一小時。""",
            "macro.cFmtAt" to """# 列表裡顯示的日期時間。⚠ 要是照原樣記成「明天」，日期一變就看錯了，所以存實際日期。""",
            "macro.mNoBody" to "請寫上要提醒什麼",
            "macro.mNoAlarm" to "沒能預約成功",
            "macro.mNone" to "沒有預定",
            "macro.mFired" to " (已通知)",
            "macro.mRemoved" to "已刪除:",
            "macro.mNoSuchNum" to "沒有這個編號 (用 remind.sh list 確認):",
            "macro.mUsageDel" to "usage: remind.sh del <編號|all>",
            "macro.bDone" to "完成",
            "macro.bDelete" to "刪除",
            "macro.mAskDel" to "要刪哪一條？",
            "macro.mAskDelH" to "編號 / all",
            "macro.mDeletedTitle" to "🗑 已刪除",
            "macro.cPeek" to """# 用通知顯示一覽 (從「一覽」圖塊)。**保留編號** — 刪除的時候要靠它來指。
# ⚠ 之前被反映過「列出來了卻沒法刪」，所以加了這個按鈕。""",
            "macro.cAskDel" to """# 「刪除」按鈕。只是問一個編號再交給 [cmd_del] (和終端機裡的 remind.sh del 同一條路)。""",
            "macro.bS1" to "10分鐘後",
            "macro.bS2" to "1小時後",
            "macro.mAgain" to "再提醒一次:",
            "macro.mTitle" to "⏰ 提醒",
            "macro.mAsk1" to "要提醒什麼？",
            "macro.mAsk1H" to "例: 吃藥",
            "macro.mAsk2" to "什麼時候？",
            "macro.mAsk2H" to "30m / 18:30 / tomorrow 18:30 / 3d / daily 07:00",
            "macro.mAskAgain" to "請再輸入一次",
            "macro.mAskGiveUp" to "${ASK_TRIES} 次都沒讀懂。也可以從終端機登記: remind.sh 30m 吃藥",
            "macro.mOkTitle" to "⏰ 已登記",
            "macro.mNgTitle" to "⚠ 沒能登記",
            "macro.cHooks" to """  # 一條用來接「提醒響了」，一條用來接「按了通知上的按鈕」。**就這兩條**，
  # 不管你加多少條提醒都不會變多。兩條都和偵測開關的開關狀態無關。""",
            "macro.cTiles" to """  # 只往空著的位和已經屬於自己的位上放 (絕不覆寫別人的分配)。
  # 帶參數的巨集名從 0.8.275 起可以直接寫 (在那之前要寫 sh + 完整路徑)。""",
            "macro.mSetupHooks" to "已登記介面:",
            "macro.mSetupTiles" to "圖塊:",
            "macro.mPathHint" to "⚠ 這個分頁還不能用名字直接敲 remind.sh。請開一個新分頁，" +
            "或者在這個分頁裡敲 export PATH=${d}HOME/.z2term/macros:${d}PATH。",
            "macro.cPathHint" to """  # ⚠ 巨集的目錄進入 PATH 是從 0.8.287 開始的。**在那之前開啟的分頁**
  # 還帶著舊的 PATH，所以先確認名字能不能解析，不行就提示怎麼辦 (不說的話，
  # `command not found` 的原因根本看不出來)。""",
            "macro.mPlace" to "⚠ 圖塊請你自己從快速設定面板的鉛筆(編輯)裡擺上去。",
            "macro.lRemind" to "提醒",
            "macro.lList" to "一覽",
            "macro.cSelf" to "# 只搭理自己發出的通知 (id 一定以 r 開頭)",
            "macro.cKeepRepeat" to "      # 重複的不在這裡刪 (明天還要再響)。只收拾一次性的。",
            "macro.cAsk" to """# 從圖塊/通知過來的這條路。⚠ **結果一定要用通知回覆** —
# 這是個預設沒人在看螢幕的入口，把錯誤丟到標準錯誤就結束的話，就成了「按了卻什麼都沒發生」
# (實際就是這樣，原因只留在圖塊的 run.log 裡)。
#   讀不懂    -> 說清哪裡不對，最多再問 $ASK_TRIES 次 (上次輸入的內容會留在回覆框裡)
#   登記成功  -> 用通知把預定和正文顯示出來""",
            "macro.cSnoozeCancel" to "小睡期間按了完成時，不要把小睡的預約留下",
            "macro.qrBody.head" to """
#
# 用法:
#   qr.sh \"https://example.com\"       把字串編成二維條碼，在這裡畫出來
#   qr.sh -f notes.txt                把檔案的內容編成二維條碼
#   z2-clip get | qr.sh               把當前剪貼簿的內容編成二維條碼
#   qr.sh -o ~/qr.png \"text\"          存成 PNG (畫面上不顯示)
#   qr.sh -t \"text\"                   用方塊字元輸出，而不是圖片
#   qr.sh -s 24 \"text\"                指定大小 (以列數計。預設按畫面寬度)
#
# z2-run: qr.sh \"https://example.com\"   (裝好之後只寫名字就行。跑一次就結束)
#
# # 前提條件 (不滿足就跑不起來，或者看到的樣子會不一樣)
#
#   1) 編碼由應用程式來做 … 在 z2term 的分頁裡什麼都不用裝 (z2-qr encode)。
#      已經裝了 qrencode 的話就用它。透過 ssh 連到別處、應用程式搆不著的地方，
#      每個分頁 (發行版) 裝一次:
#        Arch        : pacman -S qrencode
#        Ubuntu/Kali : apt install qrencode
#        Alpine      : apk add libqrencode-tools
#      兩個都沒有時才會當場打出安裝指令並停下。
#
#   2) 圖片 (預設) 只有在這個應用程式的分頁裡才看得到 … 它是用 Kitty graphics 畫的。
#      透過 ssh 連到別的機器，或者在顯示不了圖片的終端機上，看到的會是一堆亂碼。
#      那種場合請加 -t。
#
#   3) -t (方塊) 取決於終端機字型 … 行與行之間會留縫的字型，眼睛看得清，攝像頭卻讀不出來。
#      要讓攝像頭掃的話，還是用預設的圖片或者 -o 存 PNG 更穩。
#
#   4) 縱橫比是假定出來的 … 見下面的「縱橫比」。看著被壓扁了就用 Z2_QR_ASPECT 調。
#
# 選項:
#   -f FILE   從檔案讀取輸入 (省略則用參數，沒有參數就讀標準輸入)
#   -o PNG    存成 PNG 而不是畫出來。多張時會變成
#             qr-1.png / qr-2.png … 這樣的連號
#   -t        用方塊字元輸出。給顯示不了圖片的終端機和 ssh 用
#   -s N      畫出來時的大小 (列數)。預設最多 34 列，並受畫面寬度限制
#   -h        這段說明
#
# 關於長輸入:
#   一張二維條碼能裝的量是固定的 (字母數字最多 2953 位元組; 要真能掃得動的話就是幾百位元組)。
#   超過 900 位元組的輸入會在換行處切分成好幾張，編上 [1/3] 這樣的號按順序輸出。
#   ⚠ 單獨一行長到一張都裝不下時，那一行根本編不出來。
#
# 縱橫比:
#   終端機不會告訴別人一個字元格有多大，所以這裡假定字元的縱橫比是 1:2 來把碼畫成正方形。
#   看著被壓扁的話，用環境變數微調:
#     Z2_QR_ASPECT=0.45 qr.sh \"text\"     (越小越豎長。預設 0.5)
""",
            "macro.cMaxBytes" to "一張裡裝的位元組上限 (超過就分開)",
            "macro.cPngModule" to "儲存的 PNG 裡每個模組佔幾個點",
            "macro.cTargetPx" to "畫出來時想要的像素寬度",
            "macro.cMargin" to "二維條碼周圍的靜區 (模組數。掃描時需要)",
            "macro.cUsageFn" to "把開頭的註解塊原樣當作說明輸出 (不固定行數)。\n" +
            "# 空行不會讓它停下 (由 NF 判斷)，所以用空行分段的說明也不會被截斷。",
            "macro.cNeedEncoder" to "前提條件 1: 用來編碼的東西。有 qrencode 就用它，沒有就用應用程式 (z2-qr)。",
            "macro.mMissing" to "qr.sh: 這裡沒有可以編碼的東西 — 既沒有 qrencode，也搆不著應用程式 (z2-qr)",
            "macro.mInstallOnce" to "  請在這個分頁裡裝一次:",
            "macro.mAnyPm" to "    Arch: pacman -S qrencode / Ubuntu、Kali: apt install qrencode",
            "macro.cMakeFns" to "產生 PNG。qrencode 先試做一張來定每格的像素，應用程式則直接給目標像素。",
            "macro.cMakeBlocks" to "用方塊字元畫。兩條路都自己帶顏色，所以跟終端機主題無關都能讀。",
            "macro.qrBody.mNoTmp" to "建不了工作目錄",
            "macro.qrBody.mUnreadable" to "讀不了:",
            "macro.qrBody.mEmpty" to "輸入是空的",
            "macro.mSizeNum" to "-s 要跟數字",
            "macro.qrBody.mUsageHint" to "用法見 qr.sh -h",
            "macro.mPieceFail" to "qr.sh: 做不出第 %d 張: %s",
            "macro.qrBody.cCollect" to "---- 收集輸入 ----",
            "macro.cSplit" to "---- 太長就在換行處切分 ----",
            "macro.cSplit2" to "在累計位元組數超過 MAX_BYTES 之前就換到下一片。絕不在行的中間切，\n# 所以多位元組的文字也不會被弄壞。",
            "macro.cTrim" to "去掉末尾的換行 (不把原輸入裡沒有的換行編進二維條碼)。",
            "macro.cSize" to "---- 根據終端機寬度決定顯示大小 ----",
            "macro.cPngWidth" to "返回 PNG 的像素寬度 (IHDR 的第 16〜19 位元組)。",
            "macro.cModulePx" to "從想要的像素寬度倒推出每個模組畫幾個點。",
            "macro.cInline" to "用 Kitty graphics protocol 就地畫出 PNG。\n# 指定 c/r 把佔用的格數定下來，再輸出相應的換行，把游標送到圖的下方。",
            "macro.cEmit" to "---- 輸出 ----",
            "macro.cAnsi" to "ANSIUTF8 會自己帶上顏色，所以不管終端機配色如何，明暗都能正確顯示。",
            "macro.mdBody.head" to """
#
# 用法:
#   md.sh README.md                在這個終端機裡排好版顯示
#   md.sh -v README.md             在應用程式裡開啟來讀 (z2-view)
#   cat notes.md | md.sh           從標準輸入讀
#   md.sh -o page.html README.md   只寫出 HTML (不開啟)
#
# z2-run: md.sh README.md   (裝好之後只寫名字就行。跑一次就結束)
#
# 選項:
#   -v        在應用程式裡開啟來讀 (z2-view)
#   -o FILE   把 HTML 寫到這裡。畫面上不顯示
#   -w N      按 N 欄折行 (預設: 畫面寬度，最多 100)
#   -p        不上色也不加裝飾 (純文字)
#   -t 標題   閱讀畫面的標題 (預設: 第一個標題，沒有就用檔案名)
#   -h        這個說明
#
# 長文一頁一頁地讀: md.sh README.md | less -R   (-R 才能保留顏色)
#
# 不需要另外安裝: 有 sh 和 awk 就夠了。
# 支援標題、段落、清單 (巢狀、編號、[ ]/[x])、引用、程式碼、表格、分隔線、
# 連結、圖片和開頭的 --- 頭資訊。⚠ 底線式標題 (=== / ---) 和
# 參照式連結 ([文字][1]) 會按原樣顯示。
#
# 關於顯示:
#   - 圖片只有在這個應用程式的分頁裡才會顯示在正文的位置 (用 kitty graphics，
#     由 z2-img 畫)。透過 ssh 連到別處時，只會看到說明和路徑。
#   - 連結可以按 (OSC 8)。不支援的終端機上就是普通文字。
#   - 這個終端機不畫斜體，所以 *強調* 用底線表示 (和 man 一樣)。
#   - 放不進畫面寬度的表格會拆成「表頭: 內容」的樣子。
#   - -v 會把本機上的圖片嵌進頁面 (每張最多 1.5MB)。閱讀畫面不會去網路上取，
#     所以 http(s) 的圖片只留下說明。
""",
            "macro.mdBody.cCollect" to "# 把輸入合起來 (給了多個檔案就按順序接成一篇)。",
            "macro.cTitle" to "# 標題: -t → 第一個 # 標題 → 檔案名",
            "macro.cWidth" to "# 畫面的寬度",
            "macro.cColorEnv" to "# 想把顏色送進管道 (比如分頁器)、或者不想送時的出口",
            "macro.cImgWhere" to "# 只有在分頁裡 (kitty graphics) 並且有 z2-img 時，圖片才能出現在正文的位置。",
            "macro.cAmbi" to "# 模糊寬度的字元 (製表線、箭頭、帶圈數字) 算幾欄。在 ⚙設定裡把「模糊寬度」\n" +
            "# 設成全形的人請用 Z2_MD_AMBIWIDTH=2。預設和應用程式一樣是 1。",
            "macro.cAwkFile" to "# ⚠ awk 的本體要**寫成檔案用 -f 傳**。當成參數傳的話，多位元組字元 (比如這段\n" +
            "#   註解裡的日文) 一旦跨過 gawk 每 4096 位元組的讀取邊界，程式就會被當成到此為止，\n" +
            "#   然後以「unexpected newline or end of string」報錯結束。",
            "macro.cInlineImg" to "# 給閱讀畫面用的 HTML 裡，把圖片以 data: URI 的形式嵌進去。\n" +
            "# ⚠ z2-view 不會去網路上取圖 (JavaScript 和對外連線都關著)，所以不嵌進去\n" +
            "#   圖片就不會出現。整頁最多 4MB。",
            "macro.cImgLoop" to "# 只有含圖片的文件才一行一行地讀 (為了在圖片的位置呼叫 z2-img)",
            "macro.mdBody.mUsageHint" to "用法請看 md.sh -h",
            "macro.mdBody.mNoTmp" to "無法建立暫存目錄",
            "macro.mdBody.mUnreadable" to "無法讀取的檔案:",
            "macro.mdBody.mEmpty" to "沒有內容",
            "macro.mWidthNum" to "-w 要給數字",
            "macro.mNoDir" to "無法建立存放的資料夾",
            "macro.mConvFail" to "轉換失敗",
            "macro.mNoView" to "這裡沒有 z2-view (需要更新應用程式)。HTML 在:",
            "macro.mImgLabel" to "[圖片]",
            "menu.noApps" to "(找不到應用程式)",
            "menu.cat.AudioVideo" to "影音",
            "menu.cat.Development" to "開發",
            "menu.cat.Graphics" to "圖像",
            "menu.cat.Game" to "遊戲",
            "menu.cat.Network" to "網路",
            "menu.cat.Office" to "辦公",
            "menu.cat.System" to "系統",
            "menu.cat.Utility" to "工具",
            "menu.cat.Other" to "其他",
            "menu.usage" to "用法: z2menu [list]   (無參數: openbox pipe menu XML)",
            "reminderView.labels" to "提醒|新增|刪除|內容|日期與時間|重複|不重複|每天|工作日|每週（所選日期的星期）|文字指定時間（選填；優先於日期和重複）|已安排|重複|已通知|沒有提醒|刪除此提醒與待發的通知預約？",
            "rssFeeds.rssFeedsLabel" to "訂閱源",
            "run.errMsg" to "z2run: Xvnc :${d}{DISPLAY_NUM} 沒有啟動。請查看 /tmp/z2run-z2gui-${d}{DISPLAY_NUM}.log。",
            "scan.mNoPm" to "z2scan: 找不到支援的套件管理器 (apk/apt-get/pacman)。",
            "scan.mInstalling" to "z2scan: 將要安裝:",
            "scan.mInstallFail" to "z2scan: 安裝失敗:",
            "scan.mRemoteBlocked" to "z2scan: 已拒絕外部目標。請只掃描 localhost，或者僅對已獲授權測試的目標加上 --allow-remote。",
            "scan.mRemoteWarn" to "z2scan: 警告 即將掃描外部主機。請只針對明確獲得測試授權的對象。",
            "scan.mNoLynis" to "z2scan: 沒有 lynis，改用內建的自檢 ('z2scan self')。可以用 'z2scan setup' 安裝。",
            "scan.mNoCve" to "z2scan: 找不到 CVE 掃描器 (trivy/grype)。請先安裝 trivy 或 grype 再重新執行。",
            "scan.mSelfHead" to "== z2scan 自檢 (本裝置 / localhost) ==",
            "scan.mUidRoot" to "正以 uid 0 執行 (注: proot/z2root 提供的是偽 root)",
            "scan.mPubPort" to "在所有網路介面上監聽的 TCP 連接埠 (不限於 localhost), 連接埠",
            "scan.mSshdEmpty" to "sshd_config 允許空密碼登入 (PermitEmptyPasswords yes)",
            "scan.mSshdPass" to "sshd_config 啟用了密碼認證 (PasswordAuthentication yes); 金鑰認證更安全",
            "scan.mSshdRoot" to "sshd_config 允許 root 登入 (PermitRootLogin yes)",
            "scan.mKeyPerm" to "~/.ssh/authorized_keys 的權限過鬆, mode",
            "scan.mSshDir" to "~/.ssh 的權限過鬆, mode",
            "scan.mWorldWrite" to "發現任何人都可寫的檔案 (最多顯示 20 條):",
            "scan.mSuid" to "存在 SUID 可執行檔案 (僅供參考; 在 proot/z2root 下是偽 root):",
            "scan.mPath" to "PATH 中含有空元素或 '.' (把當前目錄混進 PATH 很危險)",
            "scan.mFound" to "檢出條數:",
            "scan.mClean" to "沒有發現明顯的問題。",
            "scan.mSaved" to "z2scan: 已儲存基準:",
            "scan.mSaveFail" to "z2scan: 無法儲存基準:",
            "scan.mNoBase" to "z2scan: 還沒有基準。請先執行一次 'z2scan self --save' 記錄當前狀態。",
            "scan.mNoChange" to "[ OK ] 與基準相比沒有變化。",
            "scan.mAdded" to "== 相對基準新增的 ==",
            "scan.mRemoved" to "== 相對基準消失的 ==",
            "scan.mCleared" to "z2scan: 已刪除基準。",
            "scan.mLangDiff" to "z2scan: 警告 基準是用另一種語言儲存的，所以全部都會顯示為變化。請用 'z2scan self --save' 重新記錄。",
            "scan.langTag" to "zh-TW",
            "scan.usageText" to """
        z2scan - 面向本裝置 / localhost 的漏洞偵測 (防禦用途，不向外發送任何資料)。

        預設只以 localhost 為對象。掃描器從發行版官方套件 (nmap/lynis/trivy) 安裝，
        不增加隨附內容，結果也只留在本機。

          z2scan self [--save]         內建的自檢 (不需要外部工具): 開放連接埠、sshd 設定、
                                       SSH 金鑰權限、world-writable/SUID、PATH 衛生。
                                       加上 --save 會把結果儲存為“基準”。
          z2scan diff [--quiet]        再檢一次，只輸出相對基準**變化了的部分**。
                                       有新增時退出碼為 1，可以直接掛到 z2-when 上。
                                       --quiet 在沒有變化時什麼都不輸出。
          z2scan baseline [clear]      顯示儲存的基準 (clear 為刪除)
          z2scan setup                 用 apk/apt-get/pacman 安裝掃描器 (nmap, lynis)
          z2scan net [--allow-remote] [目標]
                                       nmap 的 TCP 掃描。目標預設是 127.0.0.1。
                                       localhost 以外的目標必須加 --allow-remote。
          z2scan host                  用 lynis 做主機審計 (沒有則退回到 'self')
          z2scan cve                   有 trivy/grype 時掃描 rootfs 的已知 CVE

        請只掃描明確獲得測試授權的對象。
    """.trimIndent(),
            "translationMacro.description" to "使用另行安裝的命令翻譯文字。",
            "translationMacro.missing" to "此本機環境未安裝 trans 命令。請自行安裝翻譯 CLI 後重試。不會自動下載。",
            "translationMacro.network" to "翻譯文字會傳送至 CLI 使用的外部服務，受其條款與網路狀態影響。",
            "translationSetup.setup" to "請在同一本機 Linux 環境的終端中自行執行以下命令，安裝後重試翻譯。",
            "translationSetup.alpineRepo" to "若找不到套件，請在 /etc/apk/repositories 中啟用相同 Alpine 版本的 community 軟體庫。",
            "translationSetup.ubuntuRepo" to "若 Ubuntu 中找不到套件，請啟用 universe 軟體庫後重試。",
            "translationSetup.unknown" to "無法辨識發行版。請選擇適合目前本機 Linux 環境的命令。",
            "voice.head" to """# voice.sh — "語音操作"的接收端。用說出的話來控制音樂、音量、計時器、提醒、天氣等。
#
# 準備 (只做一次):
#   sh ~/.z2term/macros/voice.sh setup    登記 4 條規則 (語音操作 / 語音操作（鎖定時） / 語音計時器 / 計步)
#   計步規則只是讓計步器保持運作，以便詢問今天的步數，不會執行任何操作
#   之後用快速設定圖塊"語音操作"或數位助理 (長按電源鍵等) 說話
#
# 在終端機裡:
#   voice.sh words                 開啟詞表 (~/.z2term/voice/words-<語言>.txt。增加或更換說法)
#   voice.sh try <話>              不說話也能試 (例: voice.sh try 下一首)
#   voice.sh listen                打開聽取畫面後說話 (與圖塊相同)
#   voice.sh lang [語言]           詞表的語言 (用 voice.sh lang - 列出)
#   voice.sh ai [off|codex|claude|agy]   是否使用 AI (一開始是 off)
#   voice.sh model [名字]          AI 的模型 (留空 = 該 AI 的預設)
#   voice.sh setup / help
#
# AI 一開始是關閉的。即使開啟，也只有用提示詞 ("AI"、"提問" 等) 開頭時才會問。
# 天氣只在說"天氣"時向 wttr.in 查詢 (地點: ~/.z2term/voice/config 的 weather_city=)。
# 自己定的話: 在 ~/.z2term/voice/commands.txt 寫"話 = 命令" (比詞表先看)
# 要打電話的名字: 在 ~/.z2term/voice/contacts.txt 寫"名字 號碼" / 記錄: ~/.z2term/voice/log.txt
# z2-run: sh ~/.z2term/macros/voice.sh setup""",
        )
    }

    // --- 行の並びで持つ文言 (t.lines("名前", …)) ---

    override val lineSets: Map<String, List<String>> by lazy {
        mapOf(
            "macro.watchBasic.1" to listOf(
                "# watch-basic.sh — 入門用的巨集。對裝置上發生的事情做出反應。",
                "# 等待由應用程式一側 (z2-when) 負責，所以它不常駐: 事情發生時跑一次就結束。",
                "# 準備: ⚙設定 → 開啟“系統事件偵測”",
                "# z2-run: z2-when 'event:power_*' run ~/.z2term/macros/watch-basic.sh   (耳機再登記一條 'event:headset_*')",
            ),
            "macro.watchBasic.2" to listOf(
                "# 發生了什麼會放進 Z2_WHEN_EVENT (可用的名字用 z2-when events 列出)。",
                "# 想手動試的話:  Z2_WHEN_EVENT=power_connected sh ~/.z2term/macros/watch-basic.sh",
            ),
            "macro.watchBasic.3" to listOf(
                "[ -n \"${d}event\" ] || { echo \"Z2_WHEN_EVENT 是空的。想手動試就用 Z2_WHEN_EVENT=power_connected sh ${d}0\"; exit 0; }",
            ),
            "macro.watchBasic.4" to listOf(
                "  power_connected)    z2-toast \"開始充電了\" ;;",
                "  power_disconnected) z2-toast \"停止充電了\" ;;",
            ),
            "macro.batteryAlert.1" to listOf(
                "# battery-alert.sh — 電量低了就通知，但要看當時的情況分開處理。",
                "# 用 z2-state 看“螢幕是否亮著”: 亮著就用快顯訊息，熄著就用通知。",
                "# 準備: ⚙設定 → 開啟“系統事件偵測”",
                "# z2-run: z2-when battery:below=20 run ~/.z2term/macros/battery-alert.sh",
                "",
                "# 電量由 z2-when 傳過來。為了手動試也能跑，再用 z2-state 兜一下底。",
            ),
            "macro.batteryAlert.2" to listOf(
                "# 正在充電時就不通知 (並不是電量自己在掉)",
            ),
            "macro.batteryAlert.3" to listOf(
                "  z2-toast \"電量 ${d}{level}%\"",
                "else",
                "  z2-notify -h \"電量偏低\" \"還剩 ${d}{level}%\"",
            ),
            "macro.dailyReport.1" to listOf(
                "# daily-report.sh — 每天早上定時播報電量和連線狀況。",
                "# z2-run: z2-when time:daily=07:00 run ~/.z2term/macros/daily-report.sh",
                "# 時刻由 OS 的鬧鐘喚醒，所以 Doze 中也會響 (為了省電可能晚幾分鐘)。",
                "# 不依賴偵測開關的開關狀態。",
            ),
            "macro.dailyReport.2" to listOf(
                "if [ \"${d}(z2-state wifi)\" = \"true\" ]; then net=Wi-Fi; else net=行動數據; fi",
                "z2-say \"早上好。電量 ${d}{level} 個百分點，網路是 ${d}{net}\"",
            ),
            "macro.otpClip" to listOf(
                "# otp-clip.sh — 把通知裡的一次性驗證碼自動放進剪貼簿，",
                "# 過 TTL 秒後，如果剪貼簿裡還是那個值就自動清掉。",
                "# 準備: ⚙設定 → 開啟“通知偵測” ＋ 授予系統的“通知使用權”",
                "# z2-run: z2-when notify:otp run ~/.z2term/macros/otp-clip.sh",
                "# ⚠ Android 15+ 有時會把敏感通知裡的驗證碼遮蔽掉。想要可靠就用簡訊版",
                "#    (otp-sms.sh) — 簡訊正文不會被遮蔽。",
            ),
            "macro.otpSms" to listOf(
                "# otp-sms.sh — 把收到的簡訊裡的一次性驗證碼自動放進剪貼簿，",
                "# 過 TTL 秒後，如果剪貼簿裡還是那個值就自動清掉。",
                "# 這是 otp-clip.sh 的簡訊版。和通知不同，簡訊正文永遠不會被敏感通知保護",
                "# (Android 15+) 遮蔽，鎖屏狀態下也照樣能取到。",
                "# 準備: ⚙設定 → 開啟“簡訊偵測” ＋ 授予系統的接收簡訊權限",
                "# z2-run: z2-when sms:otp run ~/.z2term/macros/otp-sms.sh",
            ),
            "macro.unknownCall" to listOf(
                "# unknown-call.sh — 將來電通知中的電話號碼顯示為複製通知。",
                "# 通知上的“複製”按鈕會把號碼放進剪貼簿 (為什麼要等人按，見下面)。",
                "# 準備: ⚙設定 → 開啟“通知偵測” ＋ 授予系統的“通知使用權”",
                "# z2-run: z2-when notify:category=call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
                "#",
                "# 也想記未接來電的話，用不同的類別再登記一條 (指令碼還是這個):",
                "#   z2-when notify:category=missed_call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
            ),
            "macro.rss" to listOf(
                "# rss.sh — 去看訂閱源，只留下新的內容，做成通知和文字。",
                "# 和其他示例不同，這個**不常駐**: 它是“由時間觸發跑一次就結束”的形態。",
            ),
            "macro.rssOpen" to listOf(
                "# rss-open.sh — 把 rss.sh 收集到的文章從新到舊，每點一次開啟一篇。",
                "# 它會出現在狀態小工具的巨集按鈕上，所以每點一下就開啟下一篇。",
            ),
            "macro.remind" to listOf(
                "# remind.sh — 用通知來提醒: 一次性 (30 分鐘後、18:30) 或者",
                "# 重複 (每天 / 工作日 / 每週)。關著應用程式也會響 (由 OS 的鬧鐘喚醒，",
                "# 也不需要開啟偵測開關)。可以從通知上的按鈕小睡。",
            ),
            "macro.qr" to listOf(
                "# qr.sh — 把文字或檔案變成二維條碼: 在這裡畫出來，或者存成 PNG。",
                "# 不用重新敲一遍就能交給另一台裝置。跑一次就結束，不常駐。",
            ),
            "macro.md" to listOf(
                "# md.sh — 把 Markdown 在這裡排好版顯示，或者在應用程式裡開啟來讀。",
                "# 標題、清單、表格、程式碼、連結都排好，圖片就在正文的位置顯示。跑一次就結束，不常駐。",
            ),
            "macro.cImportantTemplate" to listOf(
                "# 一行一個、不想被埋掉的訂閱源或詞 (以 # 開頭的行會被忽略)。",
                "# 寫 URL 的一部分或標題的一部分都行。命中的文章會各自單獨發一條通知。",
                "# 例:", "#example.org",
            ),
            "macro.usage" to listOf(
                "usage: z2-macro <子指令>",
                "  list                列出隨附的示例 (帶 未安裝 / 相同 / 有差異 標記)",
                "  install <名字|all>  複製到 ~/.z2term/macros/ (不覆寫已有的。-f 強制覆寫)",
                "  remove <名字>       刪除裝好的隨附示例 (和隨附版不同時會停下。-f 強制刪除)",
                "  diff <名字>         看裝置上的副本和隨附版有什麼不同 (左邊是你自己的)",
                "  show <名字>         顯示指令碼內容",
                "  run <名字>          就地執行 (Ctrl-C 停止)",
                "  dir                 顯示巨集放在哪裡",
                "  actions ...         Android操作巨集 (z2-action --help)",
            ),
            "rssFeeds.labels" to listOf(
            "訂閱源", "新增", "文章列表", "修改", "刪除", "訂閱源 URL",
            "刪除此訂閱源？已取得的文章將保留。",
            "變更將在下次擷取時生效。已取得的文章將保留。",
            "沒有訂閱源。請透過「新增」輸入 RSS / Atom URL。",
            "請輸入不含空白或認證資訊的 HTTP(S) URL（最多 2048 個字元）。",
            "此訂閱源已存在。",
            "此訂閱源已變更。請重新整理列表後重試。",
            "正在儲存其他變更。請重試。",
            "上一頁", "下一頁",
        ),
        )
    }

    // --- 文言ではない、言語ごとの値 (t.of("名前", …)。列の幅など) ---

    override val values: Map<String, Any> by lazy {
        mapOf(
            "macro.stCols" to 7,
        )
    }
}

/** シェルの `$` (Kotlin のテンプレートと衝突しないように)。 */
private const val d = "\$"

/** [Z2ApiMsg] の 繁體中文 版。上書きしていない文言は英語で出る。 */
private class ApiMsgZhTw(d: String) : Z2ApiMsg("zh-TW", d) {

    override val fileHelp: String get() = "# z2-file pick [MIME] — 選取文件，複製到家目錄並傳回路徑。\n# z2-file save FILE [MIME] — 選取位置並儲存檔案副本。\n# 取消傳回失敗。最大512 MiB，選取限時5分鐘。"

    override val usbHelp: String get() = """
        |# z2-usb list              … 列出連線到手機的 USB 裝置
        |# z2-usb allow [編號]      … 請求 Android 允許 Linux 程式使用該裝置
        |# 插入裝置後執行一次 allow。權限在拔出裝置前一直有效。
    """.trimMargin()

    override val usbUsage: String get() = "usage: z2-usb list | z2-usb allow [編號|路徑|VID:PID]"

    override val usbNoDevices: String get() = "手機上沒有連線 USB 裝置。"

    override val usbNeedSelector: String get() = "連線了多個 USB 裝置。請指定 z2-usb list 中的編號。"

    override fun usbPermissionRequested(path: String): String = "請在 Android 螢幕上允許 USB 存取：$path"

    override fun usbAlreadyAllowed(path: String): String = "USB 存取已經允許：$path"

    override fun usbNotFound(selector: String): String = "找不到 USB 裝置：$selector"

    override val usbStateAllowed: String get() = "已允許"

    override val usbStateNeedsAllow: String get() = "需要允許"

    override val notifyHelp: String get() = """
        |# z2-notify [-h] [-n 名稱] [-c 文字] [-b 標籤]... "標題" "正文"  /  z2-notify [-h] "正文"
        |#   -h / --high / --banner : 以橫幅(浮動通知)的形式顯示在螢幕頂部
        |#   -b <標籤>              : 加一個回覆按鈕 (最多 3 個)。按下後
        |#                            ~/.z2term/events.jsonl 裡會多出一行 notify_action
        |#                            ({"event":"notify_action","name":名稱,"action":標籤})
        |#   -n <名稱>              : 這條通知的標識名 (用來區分是對哪次提問的回覆)
        |#   -c <文字>              : 加一個“複製”按鈕 (按下就把該文字放進剪貼簿)。
        |#                            在背景執行的巨集裡請用它，而不是 z2-clip set —
        |#                            Android 10+ 只讓前景應用程式寫剪貼簿，
        |#                            沒人看著時的 z2-clip set 會被默默丟掉。
    """.trimMargin()

    override val notifyUsage: String get() = "usage: z2-notify [-h] [-n 名稱] [-c 文字] [-b 標籤]... <標題> [正文]"

    override val toastHelp: String get() = """
        |# z2-toast <訊息> … 在螢幕下方顯示一條短訊息 (Toast)。
        |# 它過幾秒會自己消失，什麼都不留下。想留下來、或者想讓人按一下時，請用 z2-notify。
    """.trimMargin()

    override val qrHelp: String get() = """
        |# z2-qr … 開啟 QR 工具並直接用相機掃描。
        |# 掃描後會出現一個與內容對應的「開啟」按鈕 (瀏覽器、LINE 等應用程式、電話、郵件、簡訊、
        |# Wi-Fi、聯絡人、行事曆)。只掃描不會自動開啟。可以放到快速設定圖塊或邊緣面板上。
        |# z2-qr encode [-o 檔案.png] [-p 像素] [-m 格] [-t] [文字] … 產生二維條碼。
        |#   不需要安裝任何東西 (由應用程式編碼，和 QR 工具畫面用的是同一套)。
        |#   省略文字時從標準輸入讀。-o 是 PNG 的寫出位置 (預設 ~/.z2term/qr/qr.png)，
        |#   並印出寫到哪裡。-t 則不出圖，改用方塊字元畫在這個終端機裡。
        |#   -p 是目標邊長像素 (預設 600)，-m 是留白的格數 (預設 4)。最多 2000 位元組。
    """.trimMargin()

    override val shareHelp: String get() = """
        |# z2-share <文字> … 交給 Android 的分享選單 (轉交給其他應用程式)。
        |# --file 檔案 [檔案 ...]: 傳送檔案內容（最多32個，合計512MiB）。
        |# 路徑須在目前本機shell中可讀。-- 強制文字模式。超過24小時的分享副本在下次分享時刪除。
        |# 所有參數會拼成一段正文。發給哪個應用程式要在螢幕上選，所以這是給有人在的時候用的
        |# (不適合在背景自己跑的巨集)。
    """.trimMargin()

    override val viewHelp: String get() = """
        |# z2-view <檔案.html> [標題] … 在 z2term 裡閱讀你在這裡做好的頁面。
        |# 不需要伺服器和瀏覽器：把檔案交給應用程式，原樣顯示。
        |# JavaScript 和對外連線都已關閉，頁面裡什麼都跑不起來。
        |# http:// 和 https:// 的連結會用你平常的瀏覽器開啟。
        |# 手機的主題以 CSS 變數送達 (--z2-bg --z2-bg2 --z2-fg --z2-dim --z2-line --z2-accent)。
        |# 最大 4 MB。圖片請嵌入頁面 (data: URI)，外部圖片不會去取。
        |# --edge 面板:項目 可傳送到既有的 type=view 項目。
        |# --controls 檔案.json 可定義通用表單與操作，交給已安裝的巨集處理。
        |# 檢視預設手動重新整理。view-refresh=auto 時開啟會執行 run=，z2-view 回到同一項目。
        |# 用 - 或管線 (ls -l | z2-view) 讀取標準輸入。不以 < 開頭的文字去掉顏色碼後，
        |# 以自動換行的等寬字型顯示；--text / --html 可明確指定。
        |# 雙指縮放改變文字大小；--pinch page 則縮放整個頁面。--text-size 50..300 設定本頁文字大小 (%)。
        |# 兩者用於全螢幕顯示；面板項目依項目的設定。
        |# --no-refresh / --no-find 隱藏全螢幕的「重新整理」「搜尋」按鈕（預設顯示）。
    """.trimMargin()

    override val viewUsage: String get() = "用法: z2-view [--edge PANEL:ITEM] [--controls FILE.json] [--text|--html] [--pinch text|page] [--text-size N] [--no-refresh] [--no-find] <檔案|-> [標題]"

    override val viewUnreadable: String get() = "z2-view: 無法讀取的檔案:"

    override val openHelp: String get() = """
        |# z2-open <URL 或路徑> … 用預設應用程式開啟 (https://… 和 /sdcard/… 都行)。
        |# 用哪個應用程式開啟是 Android 決定的，z2term 只負責遞過去。
        |# 路徑是**手機一側**的路徑 (/sdcard/…)，不是發行版裡面的路徑。
    """.trimMargin()

    override val imgHelp: String get() = """
        |# z2-img [-w 欄] [-r 列] [--clear] [-f] <檔案|->… … 直接在終端機裡畫出圖片。
        |#   PNG / JPEG / WebP / GIF / BMP。傳 - 則從標準輸入讀一張。
        |#   -w <欄>    用欄數指定寬度 (預設: 收進終端機寬度)
        |#   -r <列>    用列數指定高度 (和 -w 一起給出時就按該尺寸)
        |#   --clear    清掉已經畫出的所有圖片
        |#   -f         即使標準輸出不是終端機也照樣傳送
        |# ⚠ 只有 z2term 的分頁、以及支援 kitty graphics 的終端機才會顯示圖片。
        |#   其它地方 (ssh 過去、分頁器裡) 只會刷出一堆亂碼。
        |# ⚠ 長寬比按「一格的高是寬的兩倍」計算。看起來被壓扁時可以調整:
        |#   Z2_IMG_ASPECT=0.45 z2-img photo.jpg (數值越小越細長)。
    """.trimMargin()

    override val imgUsage: String get() = "usage: z2-img [-w 欄] [-r 列] [--clear] [-f] <檔案|->..."

    override val imgNoFile: String get() = "讀不到這個檔案："

    override val imgNotTty: String get() = "標準輸出不是終端機，所以沒有傳送 (加 -f 可以照樣傳送)。"

    override val imgNoSize: String get() = "讀不出像素尺寸，按正方形畫出："

    override val qrUsage: String get() = "用法: z2-qr  |  z2-qr encode [-o 檔案.png] [-p 像素] [-m 格] [-t] [文字]"

    override val qrEncodeFailed: String get() = "z2-qr: 無法產生二維條碼 (文字是不是太長了? 最多 2000 位元組)"

    override val clipHelp: String get() = """
        |# z2-clip get        … 把剪貼簿內容輸出到標準輸出
        |# z2-clip set [文字] … 把文字 (不給則讀標準輸入) 放進剪貼簿
        |# ⚠ Android 10+ 只讓前景應用程式 (和正在使用的輸入法) 碰剪貼簿。
        |#   從背景執行的巨集裡呼叫會被默默丟掉，那種場合請用 z2-notify -c <文字>
        |#   (它會加一個“複製”按鈕，按下就一定能進去)。
    """.trimMargin()

    override val batteryHelp: String get() = "# 以 JSON ({\"level\":N,\"charging\":bool}) 輸出電量/充電狀態。"

    override val vibrateHelp: String get() = "# z2-vibrate [ms]  (預設 200ms)"

    override val editHelp: String get() = "# z2-edit <檔案>      … 在 z2term 自帶的編輯畫面裡改寫文字檔 (一般輸入框，「儲存」寫回)"

    override val sayHelp: String get() = "# z2-say <text>       … 用裝置自帶的 TTS 朗讀 (不帶參數則讀標準輸入)"

    override val torchHelp: String get() = "# z2-torch on|off|toggle|status  (預設 toggle)。status查詢實際狀態(on/off)，包括外部變更。"

    override val shotHelp: String get() = "# z2-shot [free|rect|oval]  … 只擷取圈出的部分 (預設 free)。需要 Android 11 與 z2term Android 操作。"

    override val mediaHelp: String get() = "# z2-media play|pause|playpause|next|previous|stop  (預設 playpause)"

    override val volumeHelp: String get() = "# z2-volume up|down|mute|unmute|N|N%   操作媒體音量。輸出結果的 current/max。"

    override val sensorHelp: String get() = "# z2-sensor light|accel|proximity  (預設 light)。讀一次感測器並以 JSON 返回。"

    override val intentHelp: String get() = """
        |# z2-intent [-a ACTION] [-d URI] [-t MIME] [-p PKG] [-n PKG/CLS] [-c CATEGORY] [-f FLAGS]
        |#           [--es K V] [--ez K true|false] [--ei K N] [--broadcast|--service]
        |# 觸發任意 Android Intent (預設是 startActivity)。開頭的非選項參數即 ACTION。
        |# --window full|freeform|split|ask 指定視窗模式。freeform 預設重用工作，由系統決定大小。
        |# --reuse-task / --os-bounds 保持相容；--bounds-only 只要求80%大小，不可與 --os-bounds 同用。
        |# --no-scale 停用 Motorola 的縮放自由視窗要求。
    """.trimMargin()

    override val stateHelp: String get() = """
        |# z2-state            … 把裝置現在的狀態整個以 JSON 返回
        |# z2-state <鍵>       … 只返回那個值的原始形式 (可以直接寫進條件式)
        |# 鍵: screen(on/off) locked idle charging plug(ac/usb/wireless/none) level temp(℃)
        |#       wifi ssid ringer(normal/vibrate/silent) airplane headset bt_audio volume volume_max
        |# 例: [ "${d}(z2-state charging)" = "true" ] && echo 充電中
    """.trimMargin()

    override val askHelp: String get() = """
        |# z2-ask [-t 秒] [-H 提示] [-d 預設值] <問題>
        |#   向人提問，把答案返回到標準輸出。
        |#   問題會以**帶回覆框的通知**送達，所以不用開啟應用程式，在通知列裡就能回答
        |#   (在背景跑著的巨集也能提問)。
        |#   -t <秒>     等待答案的時間 (預設 300 = 5 分鐘)
        |#   -H <提示>   回覆框裡顯示的標題
        |#   -d <文字>   答案的候選 (顯示在通知裡)
        |# 不回答就滑掉通知、或者逾時的時候，會**以非零退出**且什麼都不輸出，
        |# 所以“不回答就放棄”可以直接這麼寫:
        |#   name=${d}(z2-ask "分支名叫什麼?") || exit 1
        |# 如果只是讓人從準備好的選項裡挑，用 z2-notify -b <標籤> 更合適。
    """.trimMargin()

    override val askUsage: String get() = "usage: z2-ask [-t 秒] [-H 提示] [-d 預設值] <問題>"

    override val listenHelp: String get() = """
        |# z2-listen [-w 詞,詞,...] [-l 語言]
        |#   把說的話轉成文字輸出到標準輸出。辨識**只在裝置上**進行，聲音不會傳到網路。
        |#   需要 Android 12 以上，以及裝置上的語音辨識 (該語言的離線辨識資料)。
        |#   說話時讓 z2term 顯示在螢幕上 (Android 只把麥克風交給螢幕上的應用程式)。
        |#   停頓後結束。第一次執行時會要求麥克風權限。
        |#   -w <詞>    只接受這些詞 (逗號分隔)，輸出命中的那個。
        |#              不只看辨識的第一名，也會看其他候選。
        |#   -l <語言>  辨識的語言 (例: zh-TW, en-US。預設為裝置語言)
        |# 結束碼: 0 = 成功 / 1 = 沒聽清、不是任何一個詞 / 2 = 無法使用 (原因輸出到標準錯誤)
        |#   cmd=${d}(z2-listen -w "下一首,停止") || exit
    """.trimMargin()

    override val listenUsage: String get() = "usage: z2-listen [-w 詞,詞,...] [-l 語言]"

    override val listenPrompt: String get() = "請說話…（停頓後結束）"

    override val listenNeedsAndroid12: String get() = "z2-listen 需要 Android 12 以上（更早的版本無法指定只在裝置上辨識）。"

    override val listenBusy: String get() = "已經在聽了（另一個 z2-listen 正在執行）。"

    override val listenEngineBusy: String get() = "裝置的語音辨識正在使用中（可能有其他應用程式在用）。請稍後再試。"

    override val listenEngineStuck: String get() = "語音辨識沒有回應。如果持續發生，請在 Android 設定 › 應用程式 中強制停止裝置的語音辨識應用程式（如 Android System Intelligence）。"

    override val listenStopped: String get() = "已停止聆聽。"

    override val listenNoOnDevice: String get() = "此裝置沒有裝置上的語音辨識。z2-listen 不會把聲音傳到網路，所以這裡無法使用。"

    override val listenNoPermission: String get() = "沒有麥克風權限。讓 z2term 顯示在螢幕上再執行一次即可要求權限（也可以在 Android 設定中允許）。"

    override val listenNotShown: String get() = "請讓 z2term 顯示在螢幕上再試一次（Android 只把麥克風交給螢幕上的應用程式）。"

    override val listenNoMic: String get() = "無法使用麥克風（z2term 離開了螢幕，或其他應用程式正在錄音）。"

    override val listenNoLanguage: String get() = "沒有該語言的裝置上辨識資料。請在裝置的語音輸入設定（離線語音辨識）中下載。"

    override val listenNoSpeech: String get() = "沒有聽清。"

    override val listenTimedOut: String get() = "逾時。"

    override fun listenNoMatch(heard: String): String = "不是要等的詞（聽到的是：$heard）"

    override fun listenFailed(code: Int): String = "語音辨識失敗（錯誤 $code）。"

    override val screenHelp: String get() = """
        |# z2-screen keepon <N|Ns|Nm|Nh> … 在這段時間內，讓螢幕不會自己熄滅
        |# z2-screen keepon off          … 不等到期，現在就還原原樣
        |# z2-screen status              … 以 JSON 輸出當前狀態
        |#   (allowed / keepon / timeout_ms / until / remaining_sec / original_ms)
        |# 它改的是 OS 全域的“螢幕熄滅時間”，所以把應用程式收到背景也照樣有效。
        |# 和工具列的🔅 (只在開啟應用程式期間不熄滅) 是兩回事，那個不會被碰。
        |# 原來的值會被儲存，到期時一定會寫回去 (即使應用程式被殺、裝置重新啟動也一樣)。
        |# 一次最多只能設 24h。
        |# 需要“修改系統設定”的授權 (設定 › 螢幕自動熄滅 › 允許)。
        |# 例: z2-screen keepon 1h; make; z2-screen keepon off
    """.trimMargin()

    override val screenUsage: String get() = "usage: z2-screen keepon <N|Ns|Nm|Nh> | keepon off | status"

    override val tileHelp: String get() = """
        |# z2-tile set <1-$tiles> <巨集.sh | 指令...> [--off <指令...>] [-l <顯示名>] [-i <圖案名>]
        |#                           … 分配到快速設定圖塊 1〜$tiles
        |# z2-tile add <1-$tiles>         … 詢問是否把那個位放到面板上 (Android 13 以上)
        |# z2-tile list              … 以 TSV 列出全部 $tiles 個位 (位 / 顯示名 / 指令。'-' 表示空)
        |# z2-tile clear <1-$tiles|all>   … 清掉分配
        |#
        |# 可以分配什麼
        |#   既可以是 ~/.z2term/macros/ 裡**巨集的檔名**，也可以是照原樣執行的**指令**
        |#   (會根據名字自動判斷)。
        |#   巨集**可以帶參數** ('remind.sh ask')。是不是巨集由**開頭那個詞**決定，
        |#   其餘部分原樣傳給巨集。同一個巨集放到 2 個位上顯示名會重複，請用 -l 區分。
        |#   執行時，環境變數 Z2_TILE 裡是位號 (巨集的話還有 Z2_TILE_MACRO)。
        |#   ⚠ 以 .sh 結尾卻不在 ~/.z2term/macros/ 裡的名字**會在這裡被拒絕** — 因為它會被
        |#     當成指令去 PATH (裡面沒有巨集的目錄) 裡找，結果找不到，圖塊按了毫無反應、
        |#     原因只出現在 tile/run.log 裡，是一種很難察覺的壞法。
        |#
        |# 按下時的動作
        |#   按一下執行，再按一下停止 (和小工具按鈕的約定一樣)。
        |#   執行期間圖塊看起來是“開”的 (顏色是 OS 的，不是我們的)。
        |#   加上 --off 就變成**開 / 關兩條指令**，每按一次交替執行
        |#   (開著的期間圖塊顯示為“開”)。適合關閉需要另一條指令的場合 (z2-torch on / off)。
        |#   一般指令組記住切換狀態。直接指派的z2-torch跟隨Android的實際狀態，包括外部變更。
        |#   z2-screen keepon也跟隨實際有效狀態。
        |#
        |# 放到面板上
        |#   Android 13 以上會在 set 的當場**詢問是否放到面板上**，而且那個對話方塊裡會顯示
        |#   **你剛剛分配的名字和圖示**，不用在編輯介面裡猜。拒絕的話就什麼都不放。
        |#   位正好有 $tiles 個，在 manifest 裡寫死，執行時無法增加。還沒分配過的位不會
        |#   出現在編輯介面的列表裡，所以有 $tiles 個也不礙事。
        |#   ⚠ 只有 z2term 在前景時才會出現，而且**一次只能問一個** — 一口氣登記 2 個位的巨集
        |#     只會被問第一個，剩下的請用 z2-tile add <位> 重新詢問。
        |#   ⚠ 放不放**由你自己決定** — Android 禁止應用程式擅自放置。在快速設定面板的
        |#     鉛筆(編輯)裡找的時候，認準 **z2term <位號>**。
        |#   ⚠ 那個列表裡顯示的名字和圖示是 manifest 寫死的，**不是你分配的名字**
        |#     (Android 沒有在執行時替換它們的手段)。哪個號是什麼，用 z2-tile list 可以看到。
        |#
        |# 圖案 (-i)
        |#   能從名字看出來的**會自動配上圖示** (remind.sh 就是時鐘)。可以用 z2-icon 換成
        |#   喜歡的圖案，換過一次之後就不再自動改動。
        |#   加上 -i 的話，**在上面那個對話方塊出現之前**圖案就定下來了 (一行就把名字和圖案
        |#   都辦完)。名字從 z2-icon sample 的列表裡選。
        |#   ⚠ 寫了不存在的名字時**連分配一起拒絕** — 因為只分配好了卻配著錯圖案，
        |#     比什麼都沒發生更難察覺。
        |#
        |# 例: z2-tile set 1 backup.sh
        |#     z2-tile set 2 'z2-screen keepon 1h' -l 螢幕常亮
        |#     z2-tile set 3 z2-torch on --off z2-torch off -l 手電
        |#     z2-tile set 4 backup.sh -l 備份 -i sync
    """.trimMargin()

    override val tileUsage: String get() = "usage: z2-tile set <1-$tiles> <巨集.sh|指令...> [--off <指令...>] [-l 顯示名] " +
                "[-i 圖案名] | add <1-$tiles> | list | clear <1-$tiles|all>"

    override fun tileNoSuchIcon(name: String): String = "z2-tile: 沒有這個圖案: $name (列表見 z2-icon sample)。沒有進行分配。"

    override fun tileAddEmpty(n: Int): String = "z2-tile: 位 $n 還是空的 (請先用 z2-tile set $n <…> 分配)"

    override val tileAddUnsupported: String get() = "z2-tile: 這裡沒法請求新增 (需要 Android 13 以上，並且 z2term 在前景)。" +
                "分配已經完成，請從快速設定面板的鉛筆(編輯)裡擺上去。"

    override val tileAddAsked: String get() = "已經在問 Android 是否新增圖塊，請在彈出的對話方塊裡回答。" +
                "(如果什麼都沒出現，請從編輯介面擺上去)"

    override val iconHelp: String get() = """
        |# 把狀態列和快速設定圖塊的圖示，換成你自己的點陣圖案。
        |#
        |# 先做這些就夠
        |#   z2-icon pick 1     ... 從列表裡選一個圖案放進圖塊的位 1
        |#   z2-icon list       ... 現在哪個位放著什麼圖案 (加 -p 會連圖案一起顯示)
        |#
        |# <目標> 怎麼寫
        |#   notify    這個應用程式發出的所有通知的圖示 (顯示在狀態列上的那個)
        |#   1〜$tiles      快速設定圖塊的位號 - 和 z2-tile 的位是同一個號
        |#
        |# 放入圖案
        |#   pick <目標>              列出來按編號選 (最簡單)
        |#   sample <目標> <名字>     已經知道名字的時候
        |#   edit <目標>              在 ${d}EDITOR 裡畫 / 修改現在的圖案
        |#   set <目標> <檔案>        從檔案讀入 (- 表示標準輸入)
        |#   auto <1〜$tiles|all>         根據分配的指令重新選一次
        |#   clear <目標|all>         還原成內建圖示
        |#
        |# 精細程度 (格子的邊長)
        |#   grid                     顯示接下來畫圖的邊長
        |#   grid <$grids>          決定接下來畫圖的邊長 (預設是 $grid)
        |#   scale <目標> <$grids>    把現在的圖案重新鋪到那個格子上 (順便把斜邊的鋸齒
        |#                            抹平)。**能畫得更細了** — 輸出的圖示本來就會被抹平，
        |#                            所以不必為了變平滑而執行它。
        |#
        |# 把自己的圖案留在列表裡
        |#   save <目標> <名字>       給現在放著的圖案起個名字，加進列表
        |#   sample                   可選圖案的列表 (編號 / 名字 /
        |#                            builtin = 隨附的圖案、mine = 自己儲存的圖案)
        |#   sample <名字>            顯示那個圖案
        |#   forget <名字>            把自己的圖案從列表裡撤下
        |#                            (已經放進去的圖案仍然留在原處)
        |#
        |# 怎麼畫
        |#   它是一張字元格子。'.' ' ' '0' '-' '_' 表示空格，其他任何字元都表示填充，
        |#   所以用你自己看得最清楚的字來畫就行。周圍的空白會被忽略並重新居中，
        |#   所以不必把每行都填滿。邊長可以從 $grids 裡選 (z2-icon grid)。
        |#   ⚠ 把邊長調大並不會讓圖案變大。不把格子畫滿的話，圖示就會顯得小。
        |#
        |# 需要記住的
        |#   ⚠ 能傳達的只有形狀。Android 會把這些圖示重新塗成單色
        |#     (圖塊在開 / 關時顏色不同)。顏色是選不了的。
        |#   ⚠ **輸出時輪廓會自動抹平**。用 ${grid} 格畫的圖案，在圖塊上
        |#     (顯示得比狀態列大得多) 也會很平滑，所以平時不用在意邊長。
        |#     48 / 64 是留給**想自己畫得更細**的時候選的。
        |#     show 顯示的是你畫的原樣，圖塊上會比它更平滑。
        |#   ⚠ 圖塊會根據分配的名字自動配上圖案
        |#     (z2-tile set 1 remind.sh 就是時鐘)。這裡放進去的圖案優先級更高，
        |#     之後不再自動改動。想還原自動，用 z2-icon auto 1。
        |#   ⚠ 有 2 個圖示改不了: 快速設定的**編輯**介面
        |#     (往外拖圖塊的地方) 的圖示，以及啟動器的圖示。
        |#     因為 Android 在安裝時就把它們固定了。
        |#
        |# 例: z2-icon pick 1                從列表裡選位 1 的圖案
        |#     z2-icon edit 1                自己重新畫
        |#     z2-icon save 1 我的臉         給那個圖案起名字並加進列表
        |#     z2-icon sample 3 我的臉       位 3 也放同一個圖案
        |#     z2-icon grid 64               接下來畫的圖案改用 64x64 的格子
        |#     z2-icon scale 1 48            把位 1 的圖案挪到 48x48 (好畫得更細)
        |#     z2-icon list -p               連圖案一起確認哪裡放著什麼
        |#     z2-icon clear notify          把通知的圖示還原原樣
    """.trimMargin()

    override val iconUsage: String get() = "usage: z2-icon pick <notify|1-$tiles> | sample [名字|目標 名字] | " +
                "edit <notify|1-$tiles> | set <notify|1-$tiles> [檔案|-] | " +
                "save <notify|1-$tiles> <名字> | forget <名字> | " +
                "show <notify|1-$tiles> | auto <1-$tiles|all> | clear <notify|1-$tiles|all> | " +
                "grid [$grids] | scale <notify|1-$tiles> <$grids> | list [-p]"

    override val iconNoSuchFile: String get() = "沒有這個檔案:"

    override val iconEditUnchanged: String get() = "沒有改動就結束了。"

    override val iconPickPrompt: String get() = "請輸入編號 (或名字)，留空則中止: "

    override val iconPickCancelled: String get() = "已中止。"

    override val iconNoEditor: String get() = "找不到編輯器。請設定 ${d}EDITOR，或者用 z2-icon set <目標> <檔案>"

    override val tileNoSuchMacro: String get() = "~/.z2term/macros/ 裡沒有這個巨集 (要當成指令執行請寫完整路徑):"

    override val notiHelp: String get() = """
        |# z2-noti trace start [package] / trace dump / trace stop
        |# 診斷需先開啟通知偵測；僅在記憶體保留5分鐘內最新128筆紀錄。
        |# 只記錄欄位型別和UTF-16長度，不保留文字值。dump/stop輸出JSON，start清空舊紀錄。
        |# z2-noti list  … 以 TSV 顯示當前正在顯示的通知
        |#                 (key / 套件名稱 / 應用程式名 / 標題 / 正文)
        |# 只是讀取。“按下”“清除”通知是**有意**沒有提供的
        |# (那樣連其他應用程式的支付、發送按鈕都能按下去)。
        |# 需要通知使用權 (設定 › 權限與通知 › 通知偵測)。
        |# 另見: z2-when notify:otp / notify:pkg=<片段> / notify:contains=<片段>
    """.trimMargin()

    override val notiUsage: String get() = "usage: z2-noti list | trace start [package] | trace dump | trace stop"

    override val alarmHelp: String get() = """
        |# z2-alarm at HH:MM [名稱]     … 在下一個 HH:MM 響一次 (今天已過就是明天)
        |# z2-alarm daily HH:MM [名稱]  … 每天 HH:MM
        |# z2-alarm in <N|Ns|Nm|Nh> [名稱] … N 秒/分鐘/小時後響一次
        |# z2-alarm list                … 列出預約 (JSON。"exact" 表示是否準點響)
        |# z2-alarm cancel <id|名稱|all> … 取消
        |# 觸發時 ~/.z2term/events.jsonl 會多出一行 {"event":"alarm","name":…}。
        |# 即使在 Doze 中也會喚醒。"exact":true 表示準點響。"exact":false 時，
        |# Doze 中每 9〜15 分鐘才有一次觸發機會，所以螢幕熄滅放著會晚這麼多。
        |# 讓它變成 true 的辦法是**關掉這個應用程式的電池最佳化**，不會額外索要權限。
    """.trimMargin()

    override val alarmNoDate: String get() = "z2-alarm: date 用不了"

    override val sessionHelp: String get() = """
        |# z2-session list                     … 分頁一覽 (編號 / id / 類別 / 標記 / 名稱 的 TSV)
        |#   標記: * = 正在顯示 / ! = 有東西在執行 / ? = 還沒啟動 / @ = 已從終端機接入 / - = 其他
        |# z2-session new [名稱]               … 開一個終端機分頁 (返回編號和 id)
        |# z2-session send <目標> <文字>...    … 往那個分頁裡輸入文字 (不執行)
        |# z2-session send <目標> <文字> --enter … 輸入之後再執行
        |# z2-session key <目標> <鍵>...      … 往那個分頁送**按鍵** (C-c / M-x / F5 / Up …)
        |#   修飾鍵是 C- (Ctrl) 和 M- (Meta=Alt)。可以疊加，如 C-M-a
        |#   特殊鍵: Up Down Left Right Home End PgUp PgDn Ins Del Tab S-Tab Enter Esc Space BS F1-F12
        |#   ⛔ 像 C-S-a 這樣帶 Shift 的會被拒絕 (終端機分辨不出 Shift，位元組和 C-a 完全一樣)
        |# z2-session key <目標> --raw '\x1b[A' … 表裡沒有的就用位元組序列 (\xHH \e \n \r \t \0)
        |# z2-session capture [目標] [--all]   … 取出那個分頁的畫面 (--all 連可回捲的部分)
        |# z2-session attach <目標>            … 一直連著那個分頁，像平常一樣打字
        |#   退出用 Ctrl+] (隨時)，或在行首打 ~. (和 ssh 一樣)
        |#   ⚠ 透過 SSH 只有 Ctrl+] 有效 (~. 會被前面那層 ssh 吃掉，連 SSH 一起斷)
        |#   行首想打出 ~ 本身就寫 ~~，想送出 Ctrl+] 本身就在行首的 ~ 之後按 Ctrl+]
        |#   連著的期間，分頁的寬高跟隨**連過去的這一側**。退出後還原原樣
        |# z2-session close <目標>             … 關掉那個分頁 (最後一個不會關)
        |#
        |# <目標> 可以是 list 的編號 / id / 分頁名稱。寫 '.' 或者省略表示當前顯示的分頁。
        |# 例: n=${d}(z2-session new build | cut -f1); z2-session send "${d}n" 'make -j2' --enter
    """.trimMargin()

    override val sessionUsage: String get() = "usage: z2-session list | new [名稱] | send <目標> <文字>... [--enter] | " +
                "key <目標> <鍵>... | key <目標> --raw <位元組序列> | capture [目標] [--all] | " +
                "attach <目標> | close <目標>"

    override val keyNothing: String get() = "z2-session key: 沒有給出要送的鍵"

    override val keyRawEmpty: String get() = "z2-session key: --raw 後面沒有位元組序列"

    override val keyUnknown: String get() = "z2-session key: 沒有這個鍵名 (一覽見 'z2-session -h'):"

    override val keyBadEscape: String get() = "z2-session key: 讀不懂的轉義:"

    override fun keyShiftNotDistinguishable(asWritten: String, equivalentTo: String): String = "z2-session key: 終端機分辨不出 Shift，所以 '$asWritten' 和 '$equivalentTo' 會是完全一樣的位元組。" +
                "如果這正是你想要的，請寫成 '$equivalentTo'。"

    override fun attachNoSuchTab(target: String): String = "沒有這個分頁: '$target' (用 'z2-session list' 可以看到一覽)"

    override val attachNotTerminal: String get() = "那是圖形分頁，沒有 shell。請指定一個終端機分頁。"

    override val attachNotStarted: String get() = "那個分頁還沒啟動 (list 裡的標記是 '?')。請先在應用程式裡開啟一次再連線。"

    override val attachExited: String get() = "那個分頁已經結束了。畫面還可以用 'z2-session capture' 取出來。"

    override val attachSelf: String get() = "那就是你正在打字的這個分頁。連到自己身上的話，它自己的輸出會原樣回到" +
            "自己這邊，一直循環停不下來。請指定別的分頁。"

    override val attachLoop: String get() = "那個分頁已經連回到這一邊了，兩邊會互相發送、停不下來。" +
            "請先中斷其中一邊 (Ctrl+])。"

    override val serverHelp: String get() = """
        |# z2-server list                … 已登記的伺服器一覽 (編號 / id / 狀態 / 標記 / 名稱 的 TSV)
        |#   標記: * = 已啟用 / - = 已停用
        |# z2-server start <伺服器>      … 作為常駐服務啟動 (在螢幕熄滅時也能收到請求的框架裡)
        |# z2-server stop <伺服器>       … 只停這一個 (其他照常執行)
        |# z2-server status [<伺服器>]   … 狀態、pid、重新啟動次數、上次的退出碼
        |#
        |# <伺服器> 可以是 list 的編號 / id / 你在應用程式裡起的名字。
        |# 能啟動的只有已經在應用程式裡登記過的 (這裡不會新登記)。
        |#
        |# 為什麼需要它: 直接從規則裡起的守護行程跑在**常駐服務的框架之外**
        |# (沒有 WakeLock、沒有 WifiLock、也沒有前景服務)，螢幕一熄滅就不再回應。
        |# 從這裡起就進到框架裡面了。
        |# 例: z2-when wifi:connect run 'z2-server start sshd'
        |#     z2-when wifi:disconnect run 'z2-server stop sshd'
    """.trimMargin()

    override val serverUsage: String get() = "usage: z2-server list | start <伺服器> | stop <伺服器> | status [<伺服器>]"

    override val serverNotFound: String get() = "z2-server: 沒有這個伺服器 (一覽見 'z2-server list'):"

    override val serverAmbiguous: String get() = "z2-server: 有多個同名的伺服器。請用 'z2-server list' 裡的 id 指定:"

    override val serverNone: String get() = "z2-server: 還沒有登記任何伺服器 (在應用程式的 📜 → 伺服器 裡登記)。"

    override val serverLowPowerWarn: String get() = "z2-server: 注意 — 省電模式是開著的，所以不持有 WakeLock/WifiLock。" +
            "螢幕熄滅期間伺服器可能不再回應 (⚙設定 → 自動化 → 背景行程保護)。"

    override val whenHelp: String get() = """
        |# z2-when <觸發條件> run <指令...>      … 登記一條規則
        |#   觸發條件: charge:start | charge:stop  (前提是偵測已開啟)
        |#            battery:below=N | battery:above=N  (前提是偵測已開啟)
        |#            battery:temp>N | battery:temp<N  … 電池溫度 (℃) 跨過 N 時 (前提是偵測已開啟)
        |#            time:daily=HH:MM | time:at=HH:MM | time:every=Nm|Nh
        |#            time:cron='分 時 日 月 星期'  (星期 0-7 / 0,7=週日。含空格，要加引號)
        |#            wifi:connect | wifi:disconnect | wifi:ssid=<名稱>  (前提是偵測已開啟)
        |#            net:online | net:offline  … 有了能通的網路 / 網路沒了
        |#            net:wifi | net:mobile | net:ethernet  … 在用的網路切換成了它
        |#                                        (前提是偵測已開啟。不只看 Wi-Fi，也看行動數據)
        |#            share:any | share:text | share:file | share:contains=<片段> | share:ext=<副檔名>
        |#                                        … 別的應用程式透過分享選單發到 z2term 時
        |#            boot                        … 裝置啟動完成時 (偵測關閉也能用)
        |#            sms:any | sms:from=<片段> | sms:contains=<片段> | sms:otp  (前提是 RECEIVE_SMS 權限)
        |#            sensor:shake | sensor:light>N | sensor:light<N | sensor:proximity=near|far  (前提是偵測已開啟)
        |#            file:new=<資料夾>[,ext=<副檔名>]  … 那個資料夾裡來了新檔案時 (前提是偵測已開啟)
        |#            file:changed=<資料夾>[,ext=<副檔名>] | file:deleted=<資料夾>[,ext=<副檔名>]
        |#                                        … 那裡的檔案被改寫 / 消失時 (前提是偵測已開啟)
        |#            bt:connect | bt:disconnect | bt:connect=<名稱> | bt:disconnect=<名稱>
        |#                                        … 藍牙裝置連上 / 中斷時 (名稱寫其中一部分，或者寫位址)
        |#                                        (前提是偵測已開啟，Android 12 起還要「鄰近裝置」權限)
        |#            app:open=<應用程式> | app:close=<應用程式>  … 那個應用程式來到前景 / 離開前景時
        |#                                        (套件名稱或應用程式名稱的一部分。前提是在無障礙裡開啟 z2term。偵測關閉也能用)
        |#            steps:reach=N               … 今天的步數達到 N 時 (前提是偵測已開啟和「體能活動」權限)
        |#            notify:any | notify:otp | notify:pkg=<片段> | notify:title=<片段> | notify:contains=<片段>
        |#                                        … 收到通知時 (前提是通知使用權)
        |#            event:<名稱> | event:<前綴>* | event:*  … 按名字捕捉裝置事件
        |#              (名字用 z2-when events 列出。和 events.jsonl 裡的一樣)
        |# 名稱 (可選。放在觸發條件之後、run 之前):
        |#   name=<文字>             … 表示這是什麼自動化，會成為自動化分頁裡的標題
        |#                             (含空格要加引號)。留空則照舊顯示觸發條件。
        |#                             只是顯示用，什麼時候執行完全不受影響。
        |# 篩選 (任何觸發條件都能用。放在觸發條件之後、run 之前):
        |#   if=<條件>[,<條件>...]   … 只在裝置處於那個狀態時執行 (逗號是 AND。開頭加 ! 表示否定)
        |#                             可用的鍵就是 z2-state 輸出的那些: wifi charging screen locked
        |#                             idle headset bt_audio airplane plug ssid ringer level temp volume
        |#                             例: if=wifi,!screen / if=ssid=Home / if=level<30
        |#   if_any=<條件>[,<條件>...] … **滿足其中任意一個**就執行 (逗號變成 OR)
        |#                             和 if= 一起用就是「if 全部滿足，並且 if_any 滿足其一」
        |#   else=<指令>              … 不滿足 if / if_any 時，**改為**執行這個
        |#                             ⚠ 只有被 if 系篩掉時才生效。被 between / days /
        |#                             cooldown 篩掉時 else 也不會執行 (免得本該夜裡不動的
        |#                             規則，半夜反而發來通知)
        |#   cooldown=30m            … 距上次執行這段時間內不再執行 (10s / 30m / 2h)
        |#   between=22:00-07:00     … 只在這個時間段執行 (可以跨零點)
        |#   days=mon-fri            … 只在這些星期執行 (星期名，或和 cron 一樣的數字 0-7 / 0,7=週日)
        |#   answer='<提示>=><回答>'  … 指令在終端這樣詢問時，送出回答和 Enter
        |#                             (可寫多個。例: answer='[y/N]=>y')。與輸出最後一行的結尾比較
        |#   secret='<提示>'         … 用於隱藏輸入的回答 (密碼)。內容在應用程式的規則編輯畫面輸入
        |#                             (加密儲存在應用程式內，不寫入規則檔)
        |# 篩掉的也會記錄 — z2-when fired 裡會出現 skip:if / skip:if→else / skip:between / skip:days / skip:cooldown，
        |# 所以「為什麼不動」是查得出來的。應用程式介面上的 ▶ 會無視所有篩選
        |# (它是用來試跑的按鈕)。
        |# z2-when events                        … 列出 event: 可以用的事件名
        |# z2-when pause / resume                … 暫停 / 還原自動執行 (規則不會被刪)
        |# z2-when fired [n]                     … 最近的觸發 (時刻 / id / 觸發條件 / run|paused)
        |# z2-when list                          … 已登記的規則 (id / on|off / 觸發條件 / -> / 指令 的 TSV。
        |#                                         名稱和篩選如果有，會出現在末尾的 [] 裡)
        |# z2-when remove <id|all>  (rm 也可以)  … 刪除
        |# z2-when on <id> / off <id>            … 啟用 / 停用
        |# z2-when log <id>                      … 那條規則的執行日誌 (末尾)
        |# 觸發時，指令在當前選中的發行版上執行，環境變數裡會有 Z2_WHEN_TRIGGER / Z2_WHEN_LEVEL
        |# / Z2_WHEN_SSID (wifi) / Z2_WHEN_SMS_FROM / Z2_WHEN_SMS_BODY / Z2_WHEN_OTP (sms)
        |# / Z2_WHEN_SENSOR / Z2_WHEN_LUX (sensor)。
        |# net: 時會有 Z2_WHEN_NET (現在的網路) 和 Z2_WHEN_NET_PREV (之前的網路)。
        |# share: 時會有 Z2_WHEN_SHARE (文字本身，或者收進來的檔案路徑) 和
        |# Z2_WHEN_SHARE_KIND (text|file)。分享過來的內容照舊也會進到輸入行。
        |# file: 時會有 Z2_WHEN_FILE (完整路徑) 和 Z2_WHEN_DIR。
        |# notify: 時會有 Z2_WHEN_NOTI_PKG / _APP / _TITLE / _TEXT / _CATEGORY
        |# (notify:otp 還會有 Z2_WHEN_OTP)。notify:category= 是完全匹配: call, missed_call, msg, ...
        |# event: 時會有 Z2_WHEN_EVENT (事件名)。alarm / notify_action 還會有
        |# Z2_WHEN_EVENT_NAME (設下時的標識名) 和 Z2_WHEN_ACTION (按了哪個按鈕)。
        |# battery:temp 時會有 Z2_WHEN_TEMP (℃)。bt: 時會有 Z2_WHEN_BT_NAME / Z2_WHEN_BT_ADDR。
        |# app: 時會有 Z2_WHEN_APP_PKG (現在前景的應用程式) / Z2_WHEN_APP_PREV (之前的應用程式)。
        |# steps: 時會有 Z2_WHEN_STEPS (今天的步數)。file: 還會有 Z2_WHEN_FILE_ACTION (new|changed|deleted)。
        |# app_installed / app_removed / app_updated 會有 Z2_WHEN_PKG，timezone_changed 會有 Z2_WHEN_TZ，
        |# usb_attached / usb_detached 會有 Z2_WHEN_USB (裝置名稱) / Z2_WHEN_USB_ID (廠商:產品)。
        |# 同一條規則在 10 秒內不會連續觸發兩次 (針對 screen_on 這類數量多的事件)。
        |# 例: z2-when charge:start run ~/.z2term/macros/backup.sh
        |#     z2-when time:cron='0 3 * * *' run ~/.z2term/macros/nightly.sh
        |#     z2-when event:headset_plugged run ~/.z2term/macros/play.sh
        |#     z2-when 'event:ringer_*' run 'z2-toast "響鈴模式: ${d}Z2_WHEN_EVENT"'
        |#     z2-when file:new=/sdcard/Pictures/Screenshots run ~/.z2term/macros/shot.sh
        |#     z2-when net:online cooldown=5m run ~/.z2term/macros/sync.sh
        |#     z2-when boot run 'sshd --lan'
        |#     z2-when share:text run '~/.z2term/macros/fetch.sh "${d}Z2_WHEN_SHARE"'
        |#     z2-when time:daily=07:00 name='早間日報' run ~/.z2term/macros/report.sh
    """.trimMargin()

    override val whenPaused: String get() = "已暫停自動執行 (用 z2-when resume 還原)"

    override val whenResumed: String get() = "已還原自動執行"

    override val whenNoFires: String get() = "(還沒有觸發過)"

    override val whenPausedNote: String get() = "# 已暫停 (用 z2-when resume 還原)"

    override val whenNoLog: String get() = "(還沒有日誌)"

    override val whenWriteFailed: String get() = "z2-when: 寫入失敗"

    override val whenUnknownIfKey: String get() = "z2-when: if= 裡寫不了這個條件 (可用的就是 z2-state 輸出的那些項):"

    override val whenUnknownTrigger: String get() = "z2-when: 沒有這個觸發條件 (一覽請看不帶參數的 z2-when):"

    override val whenBadTriggerSpec: String get() = "z2-when: 那個觸發條件不能這麼寫:"

    override val whenPausedWarn: String get() = "注意: 自動執行正處於暫停 (用 z2-when resume 還原)"

    override val whenRunJoined: String get() = "注意: 已把指令裡的換行改成空格 (一條規則只讀一行)"

    override val whenEventList: String get() = """
        |screen_on              螢幕點亮了                [偵測 ON]
        |screen_off             螢幕熄滅了                [偵測 ON]
        |unlocked               解鎖了裝置                [偵測 ON]
        |power_connected        開始充電                  [偵測 ON]
        |power_disconnected     停止充電                  [偵測 ON]
        |battery_low            電量變少了                [偵測 ON]
        |battery_okay           電量還原了                [偵測 ON]
        |battery_level          電量跨過 10% 的刻度       [偵測 ON]
        |wifi_connected         連上了 Wi-Fi              [偵測 ON]
        |wifi_disconnected      中斷了 Wi-Fi              [偵測 ON]
        |net_online             有了能通的網路            [偵測 ON]
        |net_offline            沒有能通的網路            [偵測 ON]
        |net_wifi               在用的網路變成 Wi-Fi      [偵測 ON]
        |net_mobile             在用的網路變成行動數據    [偵測 ON]
        |headset_plugged        插入了有線耳機            [偵測 ON]
        |headset_unplugged      拔出了有線耳機            [偵測 ON]
        |bt_audio_connected     藍牙音訊連上了            [偵測 ON]
        |bt_audio_disconnected  藍牙音訊中斷了            [偵測 ON]
        |airplane_on            開啟了飛行模式            [偵測 ON]
        |airplane_off           關閉了飛行模式            [偵測 ON]
        |ringer_normal          改成了響鈴                [偵測 ON]
        |ringer_vibrate         改成了振動                [偵測 ON]
        |ringer_silent          改成了靜音                [偵測 ON]
        |bt_connected           藍牙裝置連上了            [偵測 ON]
        |bt_disconnected        藍牙裝置中斷了            [偵測 ON]
        |powersave_on           開啟了省電模式            [偵測 ON]
        |powersave_off          關閉了省電模式            [偵測 ON]
        |dnd_on                 開啟了零打擾模式          [偵測 ON]
        |dnd_off                關閉了零打擾模式          [偵測 ON]
        |app_installed          安裝了應用程式            [偵測 ON]
        |app_removed            解除安裝了應用程式        [偵測 ON]
        |app_updated            應用程式更新了            [偵測 ON]
        |timezone_changed       時區變了                  [偵測 ON]
        |usb_attached           插入了 USB 裝置           [偵測 ON]
        |usb_detached           拔出了 USB 裝置           [偵測 ON]
        |boot                   裝置啟動完成              [總是]
        |alarm                  z2-alarm 響了             [總是]
        |notify_action          按了通知上的按鈕          [總是]
        |unlock_failed          解鎖失敗了                [總是，需設定]
        |unlock_succeeded       失敗之後解鎖成功          [總是，需設定]
        |voice                  語音操作說了話            [總是，從圖塊或助理]
    """.trimMargin()

    override val whenEventListNote: String get() = """
        |    # event:<名稱> 裡可以寫的名字。順序和 events.jsonl 裡出現的一樣。
        |    # 上面一組的前提是偵測已開啟 (設定 › 常駐服務與自動化 › 系統事件偵測)，
        |    # 下面一組是你自己設下的，所以偵測關閉也能用。
    """.trimMargin()

    override val updateHelp: String get() = """
        |# z2-update                     … 檢查新版本、下載，並把安裝確認介面調出來
        |# z2-update --check             … 只說有沒有更新的版本
        |# z2-update --keep              … 保留下載的 .apk (預設裝完就刪)
        |# z2-update --dir <資料夾>      … 下載到那個資料夾，而不是應用程式內部
        |#
        |# 從哪裡來
        |#   z2term 的 GitHub Releases (和你手動下載的是同一個 z2term-<版本>.apk，用發布金鑰簽名)。
        |#   不會連線任何別的地方，而且在你敲這條指令之前**完全不聯網**。
        |#
        |# 能做什麼、不能做什麼
        |#   ⚠ **它沒法悄悄安裝。** 應用程式替換自己時，Android 一定會彈出自己的
        |#     「要安裝嗎」介面，最後那一下必須由你自己按。這條指令負責做到那個介面出現為止。
        |#   ⚠ 第一次要給 z2term 允許**「安裝未知應用程式」**
        |#     (不夠時它會提示。⚙設定裡也有同樣的按鈕)。沒有授權的話，確認介面根本不會出現。
        |#   ⚠ 如果是從 F-Droid 之類的應用程式商店裝的版本，**它會拒絕** — 那個版本該由商店來替換。
        |#
        |# 下載的檔案
        |#   預設落在應用程式內部的工作目錄裡，裝完就刪 (替換過程中應用程式會被殺掉，所以
        |#   **下次啟動時也會再清理一遍**)。用 --keep / --dir 可以改，
        |#   ⚙設定裡也有同樣的兩項 (從設定的按鈕更新時以那邊為準)。
        |#
        |# 例: z2-update --check
        |#     z2-update --dir /sdcard/Download --keep
        |#     z2-when time:daily=03:00 run 'z2-update'   # 每晚看一次 (確認介面還是會出現)
    """.trimMargin()

    override val updateUsage: String get() = "usage: z2-update [--check] [--keep] [--dir <資料夾>]"

    override fun updateUpToDate(current: String): String = "z2-update: $current 已經是最新的了。"

    override fun updateFound(current: String, latest: String, size: String): String = "z2-update: $current → $latest ($size)"

    override val updateChecking: String get() = "z2-update: 去看看有沒有新版本 ..."

    override val updateHandedToInstaller: String get() = "z2-update: 已在裝置上開啟安裝介面 — 確認後即可完成替換。"

    override fun updateNoApk(url: String): String = "z2-update: 那個發布裡沒有附帶 .apk。請從發布頁面安裝: $url"

    override val updateNeedPermission: String get() = "z2-update: 請先給 z2term 允許“安裝未知應用程式” " +
            "(設定 › 應用程式 › 特殊應用程式權限 › 安裝未知應用程式。z2term 的 ⚙設定裡也有按鈕)。"

    override val updateManagedByStore: String get() = "z2-update: 這個版本是從應用程式商店 (F-Droid / Play) 裝的。請從那裡更新。"

    override fun updateFailed(reason: String): String = "z2-update: 失敗了: $reason"
}

/** 声で操作 — 言葉の表 (voice.sh が読む)。 */
private const val VOICE_WORDS = """# 語音操作 — 詞表 (繁體中文)。由 voice.sh 讀取。改寫這裡就能增加或更換說法。
#
# 寫法:
#   鍵 = 說法|另一種說法|…            用"|"隔開，從上往下看
#   放一個 * 就能取到那個位置的話 (打開* -> "打開地圖" 取到 "地圖")
#   *X* 取 X 兩邊的話 (*提醒我* -> "明天7點提醒我倒垃圾" 取到 "明天7點 倒垃圾")
#   鍵 =                              右邊留空，這個說法就不再起作用
#   比較時忽略標點、空格和英文大小寫
#   這裡沒有的鍵，使用內建詞表的值 (刪掉一行就回到原來的說法)
#   以 say_ / msg_ / fmt_ / title_ 開頭的行是要說出或顯示的句子，{…} 裡會填入值
#
# 不說話也能試: sh ~/.z2term/macros/voice.sh try 下一首
# "自己定的話 = 命令" 寫在 ~/.z2term/voice/commands.txt (比這個詞表先看)。

# --- 聽取的話 ---
ai = 問ai*|問問ai*|ai*|提問*|問一下*
media_next = 下一首|下一曲|下一個|跳過|切歌|下一首歌
media_previous = 上一首|上一曲|上一個|上一首歌
media_pause = 暫停|停止|停|別放了|暫停音樂|停止播放
media_play = 播放|繼續|繼續播放|放音樂|播放音樂
volume_up = 調高音量|音量調高|音量調大|調大音量|大聲點|聲音大一點|音量大一點|大聲一點
volume_down = 調低音量|音量調低|音量調小|調小音量|小聲點|聲音小一點|音量小一點|小聲一點
volume_mute = 靜音|關掉聲音
volume_unmute = 取消靜音|打開聲音|恢復聲音
time = 現在幾點|幾點了|現在幾點了|時間|現在時間|幾點|現在是幾點
date = 今天幾號|今天是幾號|今天星期幾|星期幾|日期|今天的日期|今天是星期幾
battery = 電量|電池|還有多少電|電池電量|還剩多少電
steps = 步數|今天的步數|今天走了多少步|走了多少步|我走了多少步
torch_on = 打開手電筒|手電筒|開手電筒|打開閃光燈
torch_off = 關閉手電筒|關手電筒|關掉手電筒|關閉閃光燈
screenshot = 截圖|截螢幕|螢幕截圖
home = 主畫面|回到主畫面|桌面|回到桌面
news = 新聞|讀新聞|今天的新聞|最新消息|念一下新聞|rss
weather = 天氣|今天天氣|今天的天氣|天氣怎麼樣|今天天氣怎麼樣|天氣如何
weather_tomorrow = 明天天氣|明天的天氣|明天天氣怎麼樣|明天的天氣怎麼樣
memo_read = 讀備忘錄|讀一下備忘|看備忘錄|我的備忘|念一下備忘
memo_add = 備忘*|記一下*|記下*|*記下來
remind_list = 提醒清單|我的提醒|有什麼提醒|行程|今天的行程|有哪些提醒
remind = 提醒我*|*提醒我*|*提醒我|設定提醒*|設個提醒*
wake = *叫醒我|*叫我起床|*點叫我|把鬧鐘定在*|鬧鐘定在*
help = 你能做什麼|我能說什麼|說明|能做什麼
timer = 設一個*的計時器|設個*的計時器|*的計時器|*計時器|*計時|計時*|倒數*|定時*
navi = 導航到*|導航去*|去*怎麼走|帶我去*|我想去*|到*的路線
navi_ask = 導航|開始導航|帶路
place_phone = 查一下*的電話號碼|*的電話號碼|查一下*的電話
call = 給*打電話|打電話給*|打給*|撥給*
play = 播放*|放一首*|我想聽*|來一首*
open = 打開*|開啟*|啟動*
# 用類別名稱開啟 (裝置的預設應用程式)。例: 打開瀏覽器
open_browser = 瀏覽器|網頁
open_maps = 地圖
open_mail = 郵件|電子郵件
open_camera = 相機
open_settings = 設定
open_calendar = 日曆
open_music = 音樂
open_gallery = 相簿|圖庫|照片
open_contacts = 聯絡人|通訊錄
open_dialer = 電話|撥號
open_messages = 簡訊|訊息
open_calculator = 計算機
# 應用程式的別名。說了也開不了時寫上: 別名:應用程式名稱|別名:應用程式名稱
app_names =

# 帶數字的音量 ("音量調低3格"、"音量調到10"、"音量百分之五十"、"音量最大") — 只要包含就算
volume_word = 音量|聲音
volume_up_word = 調高|調大|大|增加
volume_down_word = 調低|調小|小|減少|降低
volume_max_word = 最大
volume_min_word = 最小
volume_percent_word = %|百分之
volume_mute_word = 靜音
volume_unmute_word = 取消

# 時間長度和時刻 (計時器·提醒·鬧鐘)
unit_hour = 個小時|小時|個鐘頭|鐘頭
unit_minute = 分鐘|分
unit_second = 秒鐘|秒
unit_day = 天
when_before =
when_after = 以後|之後|後
clock_at =
clock_hour = 點鐘|點
clock_minute = 分
clock_half = 半
clock_am = 早上|上午|凌晨|早晨
clock_pm = 下午|晚上|傍晚|中午
day_today = 今天
day_tomorrow = 明天
day_after_tomorrow = 後天
day_daily = 每天
day_weekdays = 工作日|平日|每個工作日
day_every = 每
wd_mon = 星期一|週一|禮拜一
wd_tue = 星期二|週二|禮拜二
wd_wed = 星期三|週三|禮拜三
wd_thu = 星期四|週四|禮拜四
wd_fri = 星期五|週五|禮拜五
wd_sat = 星期六|週六|禮拜六
wd_sun = 星期日|星期天|週日|禮拜天
# 去掉"什麼時候"之後，留在正文旁邊的連接詞
remind_filler = 的|在|要|去|我|記得

# --- 說出或顯示的句子 ---
fmt_spaces = no
fmt_sentence_end = 。
title_app = 語音操作
title_navi = 導航
title_ai = AI
title_timer = 計時器
title_news = 新聞
title_memo = 備忘
title_remind = 提醒
title_call = 電話
title_help = 可以說的話
title_rule_voice = 語音操作
title_rule_voice_locked = 語音操作（鎖定時）
title_rule_timer = 語音計時器
title_rule_steps = 計步 (語音操作用)
fmt_clock = {h}點{m}分
fmt_clock_oclock = {h}點
fmt_weekdays = 一|二|三|四|五|六|日
fmt_months = 1|2|3|4|5|6|7|8|9|10|11|12
say_time = 現在是{clock}
say_date = 今天是{month}月{day}日，星期{weekday}
say_battery = 電量還有百分之{n}。{charging}
say_charging = 正在充電。
say_steps = 今天走了{n}步
say_steps_none = 還沒有記錄到步數。需要"體能活動"權限和計步規則
say_news = 最新的新聞。{titles}
say_news_none = 沒有新的新聞
fmt_weather_lang = zh-tw
say_weather = 現在的天氣是{desc}，氣溫{temp}度
say_weather_tomorrow = 明天{desc}，最高{hi}度，最低{lo}度，降雨機率百分之{rain}
say_weather_fail = 沒能取得天氣
say_memo_saved = 記下了
say_memo_recent = 最近的備忘。{memos}
say_memo_none = 沒有備忘
say_remind_ok = 提醒已設定。{res}
say_remind_fail = 沒能設定。{res}
say_remind_unclear = 沒聽清楚什麼時候提醒什麼。比如可以說：明天早上7點提醒我倒垃圾
say_remind_missing = 還沒有安裝提醒的巨集。請在終端機裡執行 z2-macro install remind
fmt_wake_label = 起床時間
say_wake = 該起床了
say_wake_set = {clock}叫醒你
say_wake_unclear = 沒聽清楚幾點叫醒你
fmt_unit_hour = 小時
fmt_unit_minute = 分鐘
fmt_unit_second = 秒
fmt_timer_label = {n}{unit}
say_timer_set = 已設定{label}的計時器
say_timer_done = {label}到了
say_timer_unclear = 沒聽清楚計時器要多長
say_call_missing = 沒找到{who}的號碼
msg_call_missing = {file} 裡沒有"{who}"。請每行寫一個"名字 號碼"。
say_open_missing = 沒找到叫{name}的應用程式
say_help = 已經把可以說的話放到通知裡了
say_locked = 請解鎖手機
msg_help = 時間·日期·電量·步數 / 音量調低3格·音量百分之五十 / 下一首·暫停 / 3分鐘的計時器·7點叫醒我 / 明天早上7點提醒我倒垃圾·我的提醒 / 新聞·天氣·明天天氣 / 記一下買牛奶·讀備忘錄 / 打開手電筒·截圖 / 打開… / 給…打電話 / 導航到…·…的電話號碼 / AI … (用 voice.sh ai 開啟) / 改說法: voice.sh words
msg_unknown = 沒聽懂: {text}
msg_locked = 解鎖後繼續執行「{text}」
msg_navi_where = 請說出目的地
msg_navi_hint = 目的地 (地名)
msg_ai_off = AI 已關閉。按"複製"並貼到終端機，就會使用 {ai} (sh ~/.z2term/macros/voice.sh ai {ai})
msg_ai_off_none = AI 已關閉。請先安裝並登入 codex / claude / agy 其中之一，再在終端機裡用 sh ~/.z2term/macros/voice.sh ai codex 這樣選擇
msg_ai_missing = 找不到 {ai}。請安裝並登入，或用 sh ~/.z2term/macros/voice.sh ai 選擇別的 AI
msg_ai_asking = 正在問 AI ({ai})…
msg_ai_noanswer = 沒有收到回答 (記錄: {log})
msg_ai_usage = ai: 請指定 off | codex | claude | agy 之一
msg_ai_how = AI 已關閉。要使用: sh ~/.z2term/macros/voice.sh ai {ai}  (codex / claude / agy。只有用提示詞"AI"或"提問"開頭時才會問)。模型: sh ~/.z2term/macros/voice.sh model <名字> (留空 = 該 AI 的預設)
fmt_ai_prompt = (回答會被朗讀出來。不要使用命令或檔案，用中文回答 2 到 3 句，不要用符號或清單。)
msg_setup_done = 已登記語音操作的規則 (已有的沒有重複新增):
msg_setup_steps = 計步規則 (steps:reach=1000000) 只是讓計步器保持運作，以便用語音詢問今天的步數。這個條件不會達到，不會執行任何操作。
msg_words_file = 詞表: {file}  (改寫它就能增加說法。用 sh ~/.z2term/macros/voice.sh words 開啟)"""
