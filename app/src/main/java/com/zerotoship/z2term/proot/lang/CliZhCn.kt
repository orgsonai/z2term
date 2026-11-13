package com.zerotoship.z2term.proot.lang

import com.zerotoship.z2term.proot.ASK_TRIES
import com.zerotoship.z2term.proot.CliLanguage
import com.zerotoship.z2term.proot.GuiScriptStrings
import com.zerotoship.z2term.proot.SshdScriptStrings
import com.zerotoship.z2term.proot.Z2ApiMsg
import com.zerotoship.z2term.proot.rssNotificationAction

/**
 * 简体中文 (`zh-CN`) の、**端末に出る文言**。
 *
 * 英語と日本語は文言を使う場所に書いてあり、ここには持たない。ここにあるのは
 * その言語の訳だけで、**無い文言は英語で出る**（壊れはしない）。
 * 書き方と、訳の埋まり具合の確かめ方は [CliLanguage] を参照。
 */
internal object CliZhCn : CliLanguage {

    override val code: String = "zh-CN"

    // --- z2-* の文言 (メッセージ集 Z2ApiMsg を上書きしたもの。下の ApiMsgZhCn) ---

    override fun apiMsg(d: String): Z2ApiMsg = ApiMsgZhCn(d)

    // --- GUI (z2gui) / sshd が echo する文言 ---

    override fun gui(): GuiScriptStrings = GuiScriptStrings(
            installing = "📦 正在安装整套图形环境",
            cleanInstalling = "🧹 正在全新安装图形环境",
            noPackageManager = "❌ 找不到支持的包管理器 (apk/apt-get/pacman)。",
            invalidGeometry = "❌ 分辨率的写法不正确",
            noXvnc = "❌ 没有 Xvnc/Xtigervnc (tigervnc 未安装)",
            alreadyRunning = "✅ 图形环境已经在运行",
            startingXvnc = "▶ 启动",
            xvncFailed = "❌ Xvnc 启动失败。日志:",
            ready = "✅ 图形环境准备完成。请从 z2term 的图形标签页连接。",
            running = "✅ 图形环境运行中",
            stopped = "⏹ 图形环境已停止",
            stoppedMsg = "⏹ 已停止",
            usage = "用法: z2gui [start [WxH] [clean] | stop | status | install | clean | check]",
            installFailed = "❌ 没能安装整套图形环境 (Xvnc / openbox / D-Bus)。",
            installFailedHint = "   请确认网络连接。需要恢复时，可在终端中运行 z2gui clean。",
            audioInstalling = "🔊 图形界面声音: 正在安装 PulseAudio",
            audioNoPulse = "⚠️ 图形界面声音: 没有 pulseaudio，将以无声继续",
            audioNoPactl = "⚠️ 图形界面声音: 没有 pactl，将以无声继续",
            audioStartFailed = "⚠️ 图形界面声音: PulseAudio 启动失败",
            audioReady = "🔊 图形界面声音: z2sink.monitor →",
            menuApps = "应用",
            menuWindows = "窗口",
            menuDesktops = "虚拟桌面",
            menuPreviousDesktop = "上一个桌面",
            menuNextDesktop = "下一个桌面",
            menuReload = "重新载入菜单"
        )

    override fun sshd(): SshdScriptStrings = SshdScriptStrings(
            invalidPort = "❌ 端口号不正确",
            configOk = "sshd(dropbear) 配置 OK",
            installingDropbear = "📦 没有 dropbear，正在安装…",
            dropbearInstallFailed = "❌ 无法安装 dropbear。请确认网络连接和软件包名称。",
            privilegedPortWarn = "⚠️ 特权端口。在 proot(非 root) 下很可能 bind 不上 (建议用 1024 以上)。",
            lanExposeWarn = "⚠️ 局域网/广域网开放模式: 必须用密钥认证，并且要用强密钥。",
            noAuthorizedKeys = "⛔ ~/.ssh/authorized_keys 还没有设置。已中止以局域网开放方式启动。",
            noAuthorizedKeysHint = "   请先把公钥登记到 ~/.ssh/authorized_keys 再重新执行。",
            loopbackBind = "🔒 只在 loopback 上启动。要对局域网开放请用 --lan 或 Z2_SSHD_LAN=1。",
            foregroundStart = "▶ 以前台方式启动 dropbear (Ctrl-C 停止)",
            listeningLan = "✅ dropbear listening (root，仅密钥认证)",
            listeningLoopback = "✅ dropbear listening (root，仅密钥认证，仅 loopback)",
            startupFailed = "❌ dropbear 启动失败。日志:",
            deviceIp = "设备IP",
            authKeysHint = "ℹ ~/.ssh/authorized_keys 还没有设置。登记客户端的公钥之后就能连接了。",
            authKeysExample = "   例: cat /tmp/id_ed25519.pub >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
        )

    // --- 声で操作の言葉の表 (voice.sh の bundled_words。下の VOICE_WORDS) ---

    override val voiceWords: String = VOICE_WORDS

    // --- スクリプトの中の文言 (t("名前", en = …, ja = …) の名前で引く) ---

    override val texts: Map<String, String> by lazy {
        mapOf(
            "keyring.msgStart" to "🔑 正在准备 pacman 的密钥环 (只有第一次，不联网)。需要稍等一会儿…",
            "keyring.msgOk" to "✅ 密钥环准备好了。现在可以安装软件包了。",
            "keyring.msgInitFail" to "❌ pacman-key --init 失败了。下次打开这个标签页时会再试一次。",
            "keyring.msgNoKeyrings" to "❌ 找不到随附的密钥环 (/usr/share/pacman/keyrings)。",
            "keyring.msgPopulateFail" to "❌ pacman-key --populate 失败了。下次打开这个标签页时会再试一次。",
            "action.description" to "命名的Android操作宏。用 z2-key permission 启用Android操作。\n保存时验证并替换定义；screen=current 记录当前屏幕尺寸。\nrun 等待完成，start 返回运行ID。中断 run 会停止该次运行。\n同时运行一个，定义最多64条指令、1–300秒。可从通知或 stop 停止后续步骤。\n坐标操作要求目标应用和匹配的屏幕尺寸、方向。单次操作最多3秒。\n手势入口使用 start，Shell宏或 z2-when 使用 run。\n保存到 /root/.z2term/actions/NAME.actions（共享主目录）。也可用 z2-macro actions。",
            "action.controlHelp" to "version=2 支持 repeat 次数|forever ... end、if 条件 ... else ... end 和 call 名称。\n条件使用 z2-state 键或 foreground=包名；逗号表示AND，!表示否定。\n无法获取条件状态时运行失败。运行前验证并固定所有调用的宏。\n每个定义最多64条指令，每次运行最多执行10000条。父宏和子宏的超时均有效。",
            "action.pauseHelp" to "pause [运行ID] 暂停运行；进行中的步骤在继续时从头重新发送。下拉通知栏也会暂停。\nresume [运行ID] 在 z2term 以外的画面稳定在前台后继续（最多5秒，否则保持暂停）。有结果后才返回：继续则 state 为 running、退出码 0；仍暂停则为 paused、退出码非 0。暂停期间时间限制停止计时。",
            "action.uiHelp" to "version=2还支持click 选择器、long-click 选择器和wait-ui 毫秒 选择器。\n选择器示例：text=继续、desc=更多选项、id=org.example.app:id/next。精确匹配，空格不加引号。\n先设置target或launch。wait-ui每250毫秒查询，期限1–30000毫秒；多项匹配时失败。\n操作仅发送一次，下一画面使用wait-ui等待。输入框及密码框除外。\ninspect 包名以JSON返回前台目标应用的显示文本、说明和ID。",
            "adb.mNoPm" to "z2adb: 找不到支持的包管理器 (apk/apt-get/pacman)。",
            "adb.mInstalling" to "z2adb: 正在用 ${d}PM 安装 adb 客户端 (${d}ADB_PKG) ...",
            "adb.mInstallFail" to "z2adb: adb 安装失败。请手动安装后再试。",
            "adb.mHaveAdb" to "z2adb: adb 已经安装过了。",
            "adb.mNeedWireless" to "z2adb: 如果失败，请打开 设置 > 开发者选项 > 无线调试 (Android 11+)。",
            "adb.mPairPort" to "配对用端口 (“使用配对码配对设备”处显示的): ",
            "adb.mConnPort" to "连接用端口 (显示在“无线调试”正下方): ",
            "adb.usageText" to """
        z2adb - 通过无线调试连接本机自己的 adb (自连 adb，不需要电脑)。

        前提: 打开 设置 > 开发者选项 > 无线调试 (Android 11+)。

          z2adb setup                安装 adb 客户端 (自动判断 apk/apt-get/pacman)
          z2adb pair  <port> [code]  配对 (用设置里的配对端口 + 6 位配对码)
          z2adb connect <port>       连接 (用“无线调试”正下方的端口)
          z2adb status               显示 'adb devices -l'
          z2adb <其他任何内容>       原样交给 adb (shell, logcat, install, ...)

        目标默认是 127.0.0.1。传 host:port 可以覆盖 (也可以用 Z2ADB_HOST)。
        例:  z2adb pair 37115 123456  →  z2adb connect 40123  →  z2adb shell
    """.trimIndent(),
            "audio.description" to "无需打开GUI，即可在Android播放本地Linux命令的声音。\ninstall: 在当前Linux系统安装PulseAudio（下载软件包）。\nrun 命令 [参数...]: 连接音频并执行命令，结束时停止专用音频服务器。\n指定Shell可为其中的多个命令启用声音。不会覆盖现有配置文件。\n需要本地终端标签页及支持PulseAudio的播放器。不支持SSH。",
            "audio.missing" to "缺少音频工具。请运行：z2-audio install",
            "audio.failed" to "音频连接失败，未启动指定命令。",
            "doctor.head" to "== z2doctor (这台设备的状态) ==",
            "doctor.secApp" to "-- 应用 --",
            "doctor.secLinux" to "-- Linux 一侧 --",
            "doctor.secPerm" to "-- 权限 --",
            "doctor.secAuto" to "-- 自动化 --",
            "doctor.secReport" to "-- 求助时请贴这一段 --",
            "doctor.lVersion" to "版本",
            "doctor.lEngine" to "执行引擎",
            "doctor.lDistro" to "发行版",
            "doctor.lKernel" to "kernel",
            "doctor.lDisk" to "可用空间",
            "doctor.lHome" to "主目录",
            "doctor.lSdcard" to "/sdcard",
            "doctor.lNotify" to "能发通知",
            "doctor.lNotifyRead" to "能读通知",
            "doctor.lBattOpt" to "已排除电池优化",
            "doctor.lStorage" to "全部存储",
            "doctor.lSms" to "接收短信",
            "doctor.lCapture" to "系统事件检测",
            "doctor.lServers" to "常驻服务",
            "doctor.lRules" to "自动化规则",
            "doctor.lSshd" to "sshd",
            "doctor.fixNotify" to "-> 请到 Android 的 设置 › 应用 › Z2Term › 通知 里允许",
            "doctor.fixNotifyRead" to "-> 请到 设置 › 常驻服务与自动化 › 通知检测 里允许",
            "doctor.fixBattOpt" to "-> 请打开 设置 › 后台进程保护 › 从电池优化中排除",
            "doctor.fixStorage" to "-> 请打开 设置 › 授予全部存储权限 (看 /sdcard 需要它)",
            "doctor.fixSms" to "-> 请到 设置 › 短信检测 里允许 (只有用 sms: 触发条件时才需要)",
            "doctor.fixCapture" to "-> 请打开 设置 › 系统事件检测 (充电/电池/wifi/传感器/event 各触发条件都需要它)",
            "doctor.fixPaused" to "-> 自动化正处于暂停。可以用 z2-when resume 或 📜 的自动化标签页恢复",
            "doctor.fixDisk" to "-> 可用空间不足 500MB。可以在设置的“删除系统数据”里清掉不用的系统",
            "doctor.fixSdcard" to "-> /sdcard 看起来是空的。需要上面那项“全部存储”的授权",
            "doctor.noteRedacted" to "(SSID、IP、主机名是有意隐去的)",
            "doctor.allGood" to "没有发现问题。",
            "doctor.someBad" to "值得注意的地方:",
            "doctor.usage" to "usage: z2doctor [--share | --clip]   (--share 会把报告交给 Android 的分享菜单)",
            "doctor.pausedYes" to "已暂停",
            "doctor.secExits" to "-- 上次是怎么结束的 --",
            "doctor.lExitsNote" to "最近的异常结束 (新的在前):",
            "doctor.lExitsNone" to "异常结束: 没有记录",
            "doctor.lExitsFile" to "(完整历史: ~/.z2term/exits.jsonl)",
            "doctor.hintExitMem" to "-> 因为内存不足而被结束。请减少同时进行的重活，或者整理一下常驻服务和标签页",
            "doctor.title" to "给“跑不起来”做的自检",
            "doctor.seeAlso" to "名字相近的 z2scan self 是另一条命令，用来“找出危险的设置”",
            "help.header" to "Z2Term 自有命令",
            "help.list" to """
        |[版本、信息]
        |  z2version [--short]            应用版本、执行引擎、系统(发行版)、kernel
        |  z2doctor [--share|--clip]      跑不起来时的排查诊断＋可以直接贴出去的报告
        |
        |[调用手机的功能]
        |  z2-notify [-h] [-b 标签]...    通知(-h: 横幅, -b: 回复按钮)
        |  z2-ask "问题"                  用通知的回复框提问，把答案送到标准输出
        |  z2-listen [-w 词,...]          把说的话转成文字(只在设备上识别。z2-listen -h)
        |  z2-toast "消息"                吐司(屏幕下方的短消息)
        |  z2-share "文本"                交给 Android 的分享菜单
        |  z2-open <URL 或路径>           用默认应用打开 URL/文件
        |  z2-view <文件.html>            在应用里阅读这里做好的页面
        |  z2-edit <文件>                 在应用的编辑画面里改写文本文件
        |  z2-qr                          用相机扫描 QR(出现一个打开按钮)
        |  z2-img <文件>                  直接在终端里画出图片(PNG/JPEG/WebP/GIF/BMP)
        |  z2-clip get | set [文本]       获取/设置剪贴板(set 不带参数则读标准输入)
        |  z2-file pick | save FILE     Android 文件选择与导出 (z2-file --help)
        |  z2-battery                     电量、充电状态(JSON)
        |  z2-vibrate [毫秒]              震动(默认 200ms)
        |  z2-say "文本"                  用设备自带 TTS 朗读(不带参数则读标准输入)
        |  z2-torch [on|off|toggle|status]       手电筒(默认 toggle)
        |  z2-media [play|pause|next]     发送媒体键(也支持 previous/stop)
        |  z2-volume up|down|N|N%         媒体音量(输出结果的 current/max)
        |  z2-audio run COMMAND ...      在Android播放Linux命令的声音
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
            "help.edge" to "[悬浮面板]\nz2-edge --help   定义面板并更新内容\nz2-key --help    Android 全局操作\nz2-shot --help   只截取圈出的部分\nz2-app --help    列出应用并导出图标\nz2-action --help 命名的Android操作宏\nz2-ui --help     AI 操作应用（帮助是给 AI 的指南）",
            "macro.rssBody.head" to """
#
# 准备:
#   1) 在这里一行写一个订阅源 URL:  ~/.z2term/rss/feeds.txt
#   2) 每 30 分钟看一次:
#        z2-when time:every=30m run ~/.z2term/macros/rss.sh
#   3) 可选 - 一行一个、不想被埋掉的订阅源或词:  ~/.z2term/rss/important.txt
#      命中的文章会单独发一条通知，这样流量大的源就压不掉它。
#      写 URL 的一部分或标题的一部分都行 (例: example.org)。
#   4) 将通知操作注册到自动化:
#        z2-when event:notify_action run '$rssNotificationAction'
#      “打开”把那篇文章送到浏览器，“列表”把收集到的文章做成读物，在 z2term 里打开。
#      注意: 从通知按钮打开画面需要“显示在其他应用上层”的权限 (和边缘面板同一个)。
#   5) 打开已收集文章的列表: sh ~/.z2term/macros/rss.sh view
#      在“命令列表 -> 自动化 -> 自动化规则”中管理这两条规则。
#      如果通知规则已存在，请编辑原规则，不要重复添加。
#   6) 新文章通知: 默认定期巡查会通知，从列表获取时不通知。
#        sh ~/.z2term/macros/rss.sh notify off        # 定期巡查的通知: on / off
#        sh ~/.z2term/macros/rss.sh notify-view on    # 列表的“刷新”“文章列表”的通知: on / off
#      两者也都是列表“设置”里的开关，随时可以更改。
#
# 需要: python3 (Alpine: apk add python3 / Debian: apt-get install -y python3 / Arch: pacman -S python)
#   使用缩略图 (列表的“设置”) 还需要 Pillow (Alpine: apk add py3-pillow / Debian: apt-get install -y python3-pil / Arch: pacman -S python-pillow)
# 电池: 取得越勤耗得越多。不要短于 30 分钟。
#
# z2-run: z2-when time:every=30m run ~/.z2term/macros/rss.sh
""",
            "macro.cKeep" to "seen/latest 里保留的行数上限",
            "macro.cNoPy" to "需要 python3 (例如 apk add python3)",
            "macro.cWriteFeeds" to "请在这里一行写一个订阅源 URL:",
            "macro.cFeedsHint" to "# 一行写一个订阅源 URL (以 # 开头的行会被忽略)",
            "macro.cSkipFail" to "取得或解析失败的那一条就默默跳过 (其他订阅源继续)",
            "macro.cDiff" to "# 减去已读的部分，只留下新的 (和 z2scan 的基准差分是同一个套路)。\n# 不相信订阅源的日期和排列顺序 — 这两样都靠不住。",
            "macro.cPrepend" to "# 新的堆在上面，这样小组件的“开头 (head)”模式读起来才顺。",
            "macro.cNotify" to "新增 %s 条",
            "macro.cOpen" to "打开",
            "macro.cListNone" to "还什么都没收集到 — 请先不带参数运行一次。",
            "macro.cListHint" to "列出来的方法: rss.sh list [条数]",
            "macro.cList" to "列表",
            "macro.cPageTitle" to "订阅中的源",
            "macro.cPageSites" to "%s 个站点",
            "macro.cPageArticles" to "%s 篇文章",
            "macro.cPageDoc" to "# 做出读物 (HTML)。z2-view 会在应用里打开它 (不需要服务器和浏览器)。\n" +
            "# 按站点分组，新的在前，每篇都放一个“打开”。手机的主题以 CSS 变量送达。",
            "macro.cViewHint" to "阅读的方法: rss.sh view",
            "macro.cHitMax" to "一次运行里单独通知的条数上限 (超出的并到汇总通知里)",
            "macro.cSplitDoc" to "# 把不能错过的挑出来。汇总通知的正文只放得下 3 条，流量大的\n" +
            "# 订阅源要是同时更新，重要的那一条就被挤出去了。命中的文章**单独发通知**\n" +
            "# (通知 ID 由应用一条一条分开发放，所以分开之后既不会被覆盖也不会被省略)。",
            "macro.cNameDoc" to "# 把 URL 放进通知的名字里。按下按钮时它会原样作为 notify_action 的 {name} 回来，\n" +
            "# 所以即使屏幕上有好几条通知，也不会搞错是哪一条的“打开”(准备 4 的规则)。",
            "macro.cListDoc" to "# 把收集到的文章排得好读一些输出。不用颜色 — 要和文件、小组件里看到的一致。\n" +
            "# latest.txt 本身保持一行一篇的原始数据 (小组件的 tail 和 rss-open.sh 会读它)。",
            "macro.cSettings" to "设置",
            "macro.cArchive" to "存档",
            "macro.cDelete" to "删除",
            "macro.cThumbs" to "缩略图",
            "macro.cSave" to "保存过去的文章",
            "macro.cNotifyPoll" to "通知新文章（定期巡查）",
            "macro.cNotifyView" to "通知新文章（从列表获取时）",
            "macro.cDeleteSite" to "删除此站点的全部文章",
            "macro.cConfirmSite" to "从列表中删除 %h 的 %s 篇文章？",
            "macro.cArticles" to "文章列表",
            "macro.cPrev" to "上一页",
            "macro.cNext" to "下一页",
            "macro.cCount" to "%s 篇",
            "macro.cUndated" to "日期不明",
            "macro.cArchiveEmpty" to "还没有保存的文章。在“设置”中把“保存过去的文章”设为“保存”后，获取到的文章会留在这里。",
            "macro.cSearch" to "搜索",
            "macro.cSearchWord" to "搜索词 (用空格分隔时需全部包含)",
            "macro.cByMonth" to "按月查看",
            "macro.cNoHit" to "没有找到。",
            "macro.cDropMonth" to "删除这个月",
            "macro.cDropConfirm" to "删除 %m 保存的 %s 篇文章？此操作无法撤销。",
            "macro.cOnOff" to "请指定 on 或 off",
            "macro.cNoPillow" to "缩略图需要 Python 的 Pillow (Alpine: apk add py3-pillow / Debian: apt-get install -y python3-pil / Arch: pacman -S python-pillow)",
            "macro.rssOpenBody.head" to """
#
# 准备: 先把收集的那一侧装好
#   z2-macro install rss
# 小组件: 在状态小组件的设置里，把“rss-open”分配给一个按钮。
#
# z2-run: 把 rss-open 分配给状态小组件的按钮 (想在终端里试就用 sh ~/.z2term/macros/rss-open.sh)
""",
            "macro.cNone" to "还没有文章",
            "macro.cAllRead" to "没有新的可以打开了",
            "macro.cPick" to "# latest.txt 的每行是“标题  URL”。取出还没打开过的第一个 URL。",
            "macro.cCap" to "给已打开的记录设上限，免得一直涨下去",
            "macro.copied" to "已复制验证码: ${d}{code}",
            "macro.cleared" to "已清除复制的验证码",
            "macro.otpTitle" to "一次性验证码",
            "macro.cTtl" to "复制之后过多少秒清除 (只有直接放进去时才生效)",
            "macro.cCode" to "# 验证码的提取应用一侧已经做好了。取不到的时候什么都不做。",
            "macro.cTry" to "# 先试着直接放进去。只有**你正在看着这个应用**的时候才放得进去 —\n" +
            "# Android 10+ 只让前台应用写剪贴板，在后台跑的那次会被默默丢掉。\n" +
            "# 放没放进去，读回来看一眼就知道 (写不进去也不会报错)。",
            "macro.cFallback" to "  # 没放进去 = 你没在看这个应用。改用通知上的“复制”按钮交给你\n" +
            "  # (按下的那一瞬间应用会到前台)。\n" +
            "  # ⚠ 走这条路放进去的，下面的 TTL 清不掉 — 因为清除同样需要在前台。",
            "macro.cClear" to "# 过了 TTL 秒，只有剪贴板里还是我们复制的那个验证码时才清空\n" +
            "# (这期间复制过别的东西的话，那些不动)。",
            "macro.cHow" to """
# ■ 复制来电通知中的电话号码
#   依次检查标题和正文中的纯电话号码，通过通知的“复制”按钮提供。
#   不查询通讯录，已保存和未保存的联系人都适用。
#   只有名字时无法取得号码。仅使用通知访问权限。
#   为兼容现有规则，保留文件名 unknown-call.sh。
""",
            "macro.cIsNum" to """
# 是不是「号码本身」? 把电话号码会用到的字符 (数字 + - ( ) 空格) 全部去掉，如果**什么都不剩**，
# 并且数字有 7〜15 位，就当成号码。
#   -> 包含名字或其他文字的栏不是纯号码，继续检查另一栏。
#   -> 「未知」「隐藏号码」也会因为数字不够而在这里被排除 (想收就把下面的判断放宽)。
# ⚠ 不要写成 case [!...]: 模式里的 ) 会被当成 case 的分隔符。
""",
            "macro.cScan" to "# 来电者一般在标题里，但有的电话应用会放在正文里。两边都看。",
            "macro.cSkip" to "# 两栏中都没有纯号码时，不显示复制通知。",
            "macro.cWhat" to "# 是哪一种触发的，看类别就知道 (和登记时的 notify:category= 是同一个值)。",
            "macro.cCopy" to """
# 号码通过**通知上的「复制」按钮**交给你 (-c)。在这里调用 z2-clip set 是放不进去的 —
# Android 10+ 只让前台应用写剪贴板，而来电时在前台的是电话应用。
# 按下按钮的那一瞬间 z2term 会到前台，所以那时一定放得进去。
""",
            "macro.missed" to "未接来电",
            "macro.incoming" to "来电",
            "macro.title" to "复制电话号码",
            "macro.stNew" to "未安装",
            "macro.stSame" to "相同",
            "macro.stDiff" to "有差异",
            "macro.msgInstalled" to "已安装:",
            "macro.msgSame" to "同样的内容已经装过了:",
            "macro.msgOutdated" to "和随附版的内容不一样:",
            "macro.msgHowUpdate" to "先看看差别: z2-macro diff %s   /   用随附版替换: z2-macro install -f %s (你自己改的部分会没了)",
            "macro.msgNotInstalled" to "还没有安装 (请先 z2-macro install):",
            "macro.msgRemoved" to "已删除:",
            "macro.msgAbsent" to "没有安装:",
            "macro.msgRemoveDiffers" to "和随附版的内容不一样，所以没有删除:",
            "macro.msgHowRemove" to "先看看差别: z2-macro diff %s   /   仍然删除: z2-macro remove -f %s (你自己改的部分也会没了)",
            "macro.msgNoDiffTool" to "这个环境里没有 diff。内容可以用 z2-macro show 查看。",
            "macro.msgSameAsBundled" to "和随附版相同。",
            "macro.msgNotFound" to "没有这个示例:",
            "macro.msgHintResident" to "要让它常驻，请在 ⚙设置 → 常驻服务 里登记下面这条:",
            "macro.msgHintRun" to "怎么运行:",
            "macro.remindBody.head" to """
#
# 准备 (只做一次):
#   sh ~/.z2term/macros/remind.sh setup   登记两个接口和两个磁贴
#
# 语法:
#   remind.sh <什么时候> <正文>      添加一条提醒
#   remind.sh list                   列出来 (开头是编号)
#   remind.sh del <编号|all>         取消 (rm 也可以)
#   remind.sh peek                   用通知看一览 (带「删除」按钮)
#   remind.sh ask                    在通知的回复框里问 (给磁贴用)
#   remind.sh setup                  登记接口和磁贴 (最开始做一次)
#   remind.sh help                   这段说明
#
# <什么时候> — 一次性:
#   30m / 90s / 2h                   从现在算起
#   18:30                            下一个 18:30 (过了就是明天)
#   sat 21:00                       下一个对应星期几，仅一次（今天尚未到时也算）
#   日语：土曜日の夜九時 = sat 21:00；朝七時 = 07:00；夜九時半 = 21:30
#   日语的夜12時含义不明确；午夜请明确日期并用00:00。
#   tomorrow 18:30 / 3d 07:00        按天 (3d = 3 天后)
#   07/30 19:00                      月/日 (过了就是明年)
#   2030 07/30 19:00                 带年份
#   203007301900 / 07301900          只写数字 (YYYYMMDDHHMM / MMDDHHMM)
#   ⚠ 不写时刻的话就保持**当前的时刻** (3d / 07/30 …)
#
# <什么时候> — 重复:
#   daily 07:00 / weekday 09:00      每天 / 周一到周五
#   weekly mon 09:00                 那个星期几
#   monthly 15 09:00                 那个日子
#   yearly 07/30 19:00               那个月日
#   every 19:00 / every wed 19:00    只写 every 也行 (由后面那个词决定)
#   every 15 19:00 / every 07/30 19:00  -> 每天 / 每周 / 每月 / 每年
#
# 例:
#   remind.sh 30m 吃药
#   remind.sh tomorrow 18:30 倒垃圾
#   remind.sh monthly 25 10:00 房租
#   remind.sh list        ->  1  ⏰ 07/30 18:30  倒垃圾
#   remind.sh del 1
#
# z2-run: sh ~/.z2term/macros/remind.sh setup   (之后只要 remind.sh 30m ... 就行)
""",
            "macro.cStore" to """# 一条提醒 = ${d}DIR/<id>.txt 里的一行，用制表符分隔: 种类 / 显示的写法 / when 的 id / 正文。
#   种类: once (还没响的一次性) / fired (已经响过的一次性) / repeat (重复)
#   when 的 id: 只有 repeat 才有 (删除时要调 z2-when remove)。其余是 '-'。
# 正文放在最后，是为了让它能装下除 TAB 以外的任何字符。""",
            "macro.cParse" to """# 读 1〜3 个词，决定下面这些。
#   KIND … once|repeat   PLAN … 列表里显示的写法   SPEC … 交给 z2-alarm/z2-when 的形式   USED … 用掉了几个词""",
            "macro.pDaily" to "每天 ",
            "macro.pWeekday" to "工作日 ",
            "macro.pWeekly" to "每周",
            "macro.pMonthly" to "每月 ",
            "macro.pYearly" to "每年 ",
            "macro.mDay" to "日",
            "macro.eDaily" to "每天",
            "macro.eWeekly" to "每周",
            "macro.eMonthly" to "每月",
            "macro.eYearly" to "每年",
            "macro.mBadDate" to "没有这个日期 (例: 07/30):",
            "macro.cEvery" to """  # 「every …」的简写。**后面那个词的形状**决定是每天/每周/每月/每年:
  #   every 19:00 -> 每天 / every wed 19:00 -> 每周 / every 15 19:00 -> 每月 /
  #   every 07/30 19:00 -> 每年
  # ⚠ 词的个数不变 (只是一个词换成了另一个词)，所以 USED 保持原样即可。""",
            "macro.cExpand" to "# 从 every 后面来的那个词，决定它指的是哪一种重复。",
            "macro.cDigits" to """    # 只写数字的形式: 202607301900 = YYYYMMDDHHMM / 07301900 = MMDDHHMM。
    # ⚠ 要先看 12 位再看 8 位 (8 位的模式对 12 位的串也会命中)。""",
            "macro.cYmd" to """  # 用年月日写的。⚠ z2-alarm 不能接收日期，所以这里也折算成时间差。""",
            "macro.cCivil" to """# 年月日 → 从 1970-01-01 起的天数 (含闰年・Howard Hinnant 的 days_from_civil)。
# ⚠ date -d "2026-07-30" 在 busybox 上靠不住，所以自己数。
# ⚠ 只用天数之差，时刻交给 [day_epoch]，这样时区就相互抵消了。""",
            "macro.cCron" to """# 从 HH:MM 和星期字段做出 cron 表达式 (分 时 日 月 星期)。
# ⚠ 不要用 expr 去掉开头的 0 — 结果为 0 时退出码是 1，
#   `expr "${d}m" + 0 || echo "${d}m"` 右边也会跑，值就变成两行了。""",
            "macro.afterLabel" to """# "30m" → "30分钟后 (14:35)"。把钟点也显示出来，是为了登记完能马上核对。
after_label() {
  num=${d}{1%[smh]}; u=${d}{1#${d}num}
  case ${d}u in s) sec=${d}num; unit=秒 ;; m) sec=${d}((num*60)); unit=分钟 ;; h) sec=${d}((num*3600)); unit=小时 ;; esac
  at=${d}(date -d "@${d}(( ${d}(date +%s) + sec ))" +%H:%M 2>/dev/null) || at=
  [ -n "${d}at" ] && echo "${d}num${d}unit后 (${d}at)" || echo "${d}num${d}unit后"
}""",
            "macro.mUsageAdd" to "usage: remind.sh <什么时候> <正文>",
            "macro.mBadWhen" to "看不懂时间 (例: 30m / 18:30 / tomorrow 18:30 / 3d 09:00 / daily 07:00):",
            "macro.mBadTime" to "时刻请写成 HH:MM:",
            "macro.mBadRange" to "时刻超出范围 (00:00〜23:59):",
            "macro.mNoTime" to "没有写时刻 (例: daily 07:00):",
            "macro.mBadDow" to "看不懂是星期几:",
            "macro.mPastTime" to "那个时刻已经过去了:",
            "macro.cDays" to """    # 按日期写的说法。不写时刻就保持**当前的时刻** (不擅自定一个默认时刻)。
    # 也可能整个连成一个词送过来，所以粘在一起的写法也接受。""",
            "macro.cDayEpoch" to """# 把「N 天后的 HH:MM」换算成 epoch 秒。⚠ busybox 没有 date -d \"tomorrow\"，
# 所以先求出今天的 0 点，再把天数和时刻加上去。
# ⚠ 带前导 0 的值 (\"08\") 交给 ${d}(()) 会被当成八进制，一定要去掉。
# ⚠ 一天按 86400 秒相加，所以在有夏令时的地区，切换那天会差一小时。""",
            "macro.cFmtAt" to """# 列表里显示的日期时间。⚠ 要是照原样记成「明天」，日期一变就看错了，所以存实际日期。""",
            "macro.mNoBody" to "请写上要提醒什么",
            "macro.mNoAlarm" to "没能预约成功",
            "macro.mNone" to "没有预定",
            "macro.mFired" to " (已通知)",
            "macro.mRemoved" to "已删除:",
            "macro.mNoSuchNum" to "没有这个编号 (用 remind.sh list 确认):",
            "macro.mUsageDel" to "usage: remind.sh del <编号|all>",
            "macro.bDone" to "完成",
            "macro.bDelete" to "删除",
            "macro.mAskDel" to "要删哪一条？",
            "macro.mAskDelH" to "编号 / all",
            "macro.mDeletedTitle" to "🗑 已删除",
            "macro.cPeek" to """# 用通知显示一览 (从「一览」磁贴)。**保留编号** — 删除的时候要靠它来指。
# ⚠ 之前被反映过「列出来了却没法删」，所以加了这个按钮。""",
            "macro.cAskDel" to """# 「删除」按钮。只是问一个编号再交给 [cmd_del] (和终端里的 remind.sh del 同一条路)。""",
            "macro.bS1" to "10分钟后",
            "macro.bS2" to "1小时后",
            "macro.mAgain" to "再提醒一次:",
            "macro.mTitle" to "⏰ 提醒",
            "macro.mAsk1" to "要提醒什么？",
            "macro.mAsk1H" to "例: 吃药",
            "macro.mAsk2" to "什么时候？",
            "macro.mAsk2H" to "30m / 18:30 / tomorrow 18:30 / 3d / daily 07:00",
            "macro.mAskAgain" to "请再输入一次",
            "macro.mAskGiveUp" to "${ASK_TRIES} 次都没读懂。也可以从终端登记: remind.sh 30m 吃药",
            "macro.mOkTitle" to "⏰ 已登记",
            "macro.mNgTitle" to "⚠ 没能登记",
            "macro.cHooks" to """  # 一条用来接「提醒响了」，一条用来接「按了通知上的按钮」。**就这两条**，
  # 不管你加多少条提醒都不会变多。两条都和检测开关的开关状态无关。""",
            "macro.cTiles" to """  # 只往空着的位和已经属于自己的位上放 (绝不覆盖别人的分配)。
  # 带参数的宏名从 0.8.275 起可以直接写 (在那之前要写 sh + 完整路径)。""",
            "macro.mSetupHooks" to "已登记接口:",
            "macro.mSetupTiles" to "磁贴:",
            "macro.mPathHint" to "⚠ 这个标签页还不能用名字直接敲 remind.sh。请开一个新标签页，" +
            "或者在这个标签页里敲 export PATH=${d}HOME/.z2term/macros:${d}PATH。",
            "macro.cPathHint" to """  # ⚠ 宏的目录进入 PATH 是从 0.8.287 开始的。**在那之前打开的标签页**
  # 还带着旧的 PATH，所以先确认名字能不能解析，不行就提示怎么办 (不说的话，
  # `command not found` 的原因根本看不出来)。""",
            "macro.mPlace" to "⚠ 磁贴请你自己从快捷设置面板的铅笔(编辑)里摆上去。",
            "macro.lRemind" to "提醒",
            "macro.lList" to "一览",
            "macro.cSelf" to "# 只搭理自己发出的通知 (id 一定以 r 开头)",
            "macro.cKeepRepeat" to "      # 重复的不在这里删 (明天还要再响)。只收拾一次性的。",
            "macro.cAsk" to """# 从磁贴/通知过来的这条路。⚠ **结果一定要用通知回复** —
# 这是个默认没人在看屏幕的入口，把错误丢到标准错误就结束的话，就成了「按了却什么都没发生」
# (实际就是这样，原因只留在磁贴的 run.log 里)。
#   读不懂    -> 说清哪里不对，最多再问 $ASK_TRIES 次 (上次输入的内容会留在回复框里)
#   登记成功  -> 用通知把预定和正文显示出来""",
            "macro.cSnoozeCancel" to "小睡期间按了完成时，不要把小睡的预约留下",
            "macro.qrBody.head" to """
#
# 用法:
#   qr.sh \"https://example.com\"       把字符串编成二维码，在这里画出来
#   qr.sh -f notes.txt                把文件的内容编成二维码
#   z2-clip get | qr.sh               把当前剪贴板的内容编成二维码
#   qr.sh -o ~/qr.png \"text\"          存成 PNG (画面上不显示)
#   qr.sh -t \"text\"                   用方块字符输出，而不是图片
#   qr.sh -s 24 \"text\"                指定大小 (以列数计。默认按画面宽度)
#
# z2-run: qr.sh \"https://example.com\"   (装好之后只写名字就行。跑一次就结束)
#
# # 前提条件 (不满足就跑不起来，或者看到的样子会不一样)
#
#   1) 编码由应用来做 … 在 z2term 的标签页里什么都不用装 (z2-qr encode)。
#      已经装了 qrencode 的话就用它。通过 ssh 连到别处、应用够不着的地方，
#      每个标签页 (发行版) 装一次:
#        Arch        : pacman -S qrencode
#        Ubuntu/Kali : apt install qrencode
#        Alpine      : apk add libqrencode-tools
#      两个都没有时才会当场打出安装命令并停下。
#
#   2) 图片 (默认) 只有在这个应用的标签页里才看得到 … 它是用 Kitty graphics 画的。
#      通过 ssh 连到别的机器，或者在显示不了图片的终端上，看到的会是一堆乱码。
#      那种场合请加 -t。
#
#   3) -t (方块) 取决于终端字体 … 行与行之间会留缝的字体，眼睛看得清，摄像头却读不出来。
#      要让摄像头扫的话，还是用默认的图片或者 -o 存 PNG 更稳。
#
#   4) 纵横比是假定出来的 … 见下面的「纵横比」。看着被压扁了就用 Z2_QR_ASPECT 调。
#
# 选项:
#   -f FILE   从文件读取输入 (省略则用参数，没有参数就读标准输入)
#   -o PNG    存成 PNG 而不是画出来。多张时会变成
#             qr-1.png / qr-2.png … 这样的连号
#   -t        用方块字符输出。给显示不了图片的终端和 ssh 用
#   -s N      画出来时的大小 (列数)。默认最多 34 列，并受画面宽度限制
#   -h        这段说明
#
# 关于长输入:
#   一张二维码能装的量是固定的 (字母数字最多 2953 字节; 要真能扫得动的话就是几百字节)。
#   超过 900 字节的输入会在换行处切分成好几张，编上 [1/3] 这样的号按顺序输出。
#   ⚠ 单独一行长到一张都装不下时，那一行根本编不出来。
#
# 纵横比:
#   终端不会告诉别人一个字符格有多大，所以这里假定字符的纵横比是 1:2 来把码画成正方形。
#   看着被压扁的话，用环境变量微调:
#     Z2_QR_ASPECT=0.45 qr.sh \"text\"     (越小越竖长。默认 0.5)
""",
            "macro.cMaxBytes" to "一张里装的字节上限 (超过就分开)",
            "macro.cPngModule" to "保存的 PNG 里每个模块占几个点",
            "macro.cTargetPx" to "画出来时想要的像素宽度",
            "macro.cMargin" to "二维码周围的静区 (模块数。扫描时需要)",
            "macro.cUsageFn" to "把开头的注释块原样当作说明输出 (不固定行数)。\n" +
            "# 空行不会让它停下 (由 NF 判断)，所以用空行分段的说明也不会被截断。",
            "macro.cNeedEncoder" to "前提条件 1: 用来编码的东西。有 qrencode 就用它，没有就用应用 (z2-qr)。",
            "macro.mMissing" to "qr.sh: 这里没有可以编码的东西 — 既没有 qrencode，也够不着应用 (z2-qr)",
            "macro.mInstallOnce" to "  请在这个标签页里装一次:",
            "macro.mAnyPm" to "    Arch: pacman -S qrencode / Ubuntu、Kali: apt install qrencode",
            "macro.cMakeFns" to "生成 PNG。qrencode 先试做一张来定每格的像素，应用则直接给目标像素。",
            "macro.cMakeBlocks" to "用方块字符画。两条路都自己带颜色，所以跟终端主题无关都能读。",
            "macro.qrBody.mNoTmp" to "建不了工作目录",
            "macro.qrBody.mUnreadable" to "读不了:",
            "macro.qrBody.mEmpty" to "输入是空的",
            "macro.mSizeNum" to "-s 要跟数字",
            "macro.qrBody.mUsageHint" to "用法见 qr.sh -h",
            "macro.mPieceFail" to "qr.sh: 做不出第 %d 张: %s",
            "macro.qrBody.cCollect" to "---- 收集输入 ----",
            "macro.cSplit" to "---- 太长就在换行处切分 ----",
            "macro.cSplit2" to "在累计字节数超过 MAX_BYTES 之前就换到下一片。绝不在行的中间切，\n# 所以多字节的文字也不会被弄坏。",
            "macro.cTrim" to "去掉末尾的换行 (不把原输入里没有的换行编进二维码)。",
            "macro.cSize" to "---- 根据终端宽度决定显示大小 ----",
            "macro.cPngWidth" to "返回 PNG 的像素宽度 (IHDR 的第 16〜19 字节)。",
            "macro.cModulePx" to "从想要的像素宽度倒推出每个模块画几个点。",
            "macro.cInline" to "用 Kitty graphics protocol 就地画出 PNG。\n# 指定 c/r 把占用的格数定下来，再输出相应的换行，把光标送到图的下方。",
            "macro.cEmit" to "---- 输出 ----",
            "macro.cAnsi" to "ANSIUTF8 会自己带上颜色，所以不管终端配色如何，明暗都能正确显示。",
            "macro.mdBody.head" to """
#
# 用法:
#   md.sh README.md                在这个终端里排好版显示
#   md.sh -v README.md             在应用里打开来读 (z2-view)
#   cat notes.md | md.sh           从标准输入读
#   md.sh -o page.html README.md   只写出 HTML (不打开)
#
# z2-run: md.sh README.md   (装好之后只写名字就行。跑一次就结束)
#
# 选项:
#   -v        在应用里打开来读 (z2-view)
#   -o FILE   把 HTML 写到这里。画面上不显示
#   -w N      按 N 列折行 (默认: 画面宽度，最多 100)
#   -p        不上色也不加装饰 (纯文字)
#   -t 标题   阅读画面的标题 (默认: 第一个标题，没有就用文件名)
#   -h        这个说明
#
# 长文一屏一屏地读: md.sh README.md | less -R   (-R 才能保留颜色)
#
# 不需要另外安装: 有 sh 和 awk 就够了。
# 支持标题、段落、列表 (嵌套、编号、[ ]/[x])、引用、代码、表格、分隔线、
# 链接、图片和开头的 --- 头信息。⚠ 下划线式标题 (=== / ---) 和
# 引用式链接 ([文字][1]) 会按原样显示。
#
# 关于显示:
#   - 图片只有在这个应用的标签页里才会显示在正文的位置 (用 kitty graphics，
#     由 z2-img 画)。通过 ssh 连到别处时，只会看到说明和路径。
#   - 链接可以点 (OSC 8)。不支持的终端上就是普通文字。
#   - 这个终端不画斜体，所以 *强调* 用下划线表示 (和 man 一样)。
#   - 放不进画面宽度的表格会拆成“表头: 内容”的样子。
#   - -v 会把本机上的图片嵌进页面 (每张最多 1.5MB)。阅读画面不会去网上取，
#     所以 http(s) 的图片只留下说明。
""",
            "macro.mdBody.cCollect" to "# 把输入合起来 (给了多个文件就按顺序接成一篇)。",
            "macro.cTitle" to "# 标题: -t → 第一个 # 标题 → 文件名",
            "macro.cWidth" to "# 画面的宽度",
            "macro.cColorEnv" to "# 想把颜色送进管道 (比如分页器)、或者不想送时的出口",
            "macro.cImgWhere" to "# 只有在标签页里 (kitty graphics) 并且装了 z2-img 时，图片才能出现在正文的位置。",
            "macro.cAmbi" to "# 模糊宽度的字符 (制表线、箭头、带圈数字) 算几列。在 ⚙设置里把“模糊宽度”\n" +
            "# 设成全角的人请用 Z2_MD_AMBIWIDTH=2。默认和应用一样是 1。",
            "macro.cAwkFile" to "# ⚠ awk 的本体要**写成文件用 -f 传**。当成参数传的话，多字节字符 (比如这段\n" +
            "#   注释里的日文) 一旦跨过 gawk 每 4096 字节的读取边界，程序就会被当成到此为止，\n" +
            "#   然后以 “unexpected newline or end of string” 报错退出。",
            "macro.cInlineImg" to "# 给阅读画面用的 HTML 里，把图片以 data: URI 的形式嵌进去。\n" +
            "# ⚠ z2-view 不会去网上取图 (JavaScript 和对外通信都关着)，所以不嵌进去\n" +
            "#   图片就不会出现。整页最多 4MB。",
            "macro.cImgLoop" to "# 只有含图片的文档才一行一行地读 (为了在图片的位置调用 z2-img)",
            "macro.mdBody.mUsageHint" to "用法请看 md.sh -h",
            "macro.mdBody.mNoTmp" to "无法创建临时目录",
            "macro.mdBody.mUnreadable" to "无法读取的文件:",
            "macro.mdBody.mEmpty" to "没有内容",
            "macro.mWidthNum" to "-w 要给数字",
            "macro.mNoDir" to "无法创建存放的文件夹",
            "macro.mConvFail" to "转换失败",
            "macro.mNoView" to "这里没有 z2-view (需要更新应用)。HTML 在:",
            "macro.mImgLabel" to "[图片]",
            "menu.noApps" to "(未找到应用)",
            "menu.cat.AudioVideo" to "影音",
            "menu.cat.Development" to "开发",
            "menu.cat.Graphics" to "图像",
            "menu.cat.Game" to "游戏",
            "menu.cat.Network" to "网络",
            "menu.cat.Office" to "办公",
            "menu.cat.System" to "系统",
            "menu.cat.Utility" to "工具",
            "menu.cat.Other" to "其他",
            "menu.usage" to "用法: z2menu [list]   (无参数: openbox pipe menu XML)",
            "reminderView.labels" to "提醒|添加|删除|内容|日期和时间|重复|不重复|每天|工作日|每周（所选日期的星期）|文字指定时间（可选；优先于日期和重复）|已安排|重复|已通知|没有提醒|删除此提醒和待发的通知预约？",
            "rssFeeds.rssFeedsLabel" to "订阅源",
            "run.errMsg" to "z2run: Xvnc :${d}{DISPLAY_NUM} 没有启动。请查看 /tmp/z2run-z2gui-${d}{DISPLAY_NUM}.log。",
            "scan.mNoPm" to "z2scan: 找不到支持的包管理器 (apk/apt-get/pacman)。",
            "scan.mInstalling" to "z2scan: 将要安装:",
            "scan.mInstallFail" to "z2scan: 安装失败:",
            "scan.mRemoteBlocked" to "z2scan: 已拒绝外部目标。请只扫描 localhost，或者仅对已获授权测试的目标加上 --allow-remote。",
            "scan.mRemoteWarn" to "z2scan: 警告 即将扫描外部主机。请只针对明确获得测试授权的对象。",
            "scan.mNoLynis" to "z2scan: 没有 lynis，改用内置的自检 ('z2scan self')。可以用 'z2scan setup' 安装。",
            "scan.mNoCve" to "z2scan: 找不到 CVE 扫描器 (trivy/grype)。请先安装 trivy 或 grype 再重新执行。",
            "scan.mSelfHead" to "== z2scan 自检 (本设备 / localhost) ==",
            "scan.mUidRoot" to "正以 uid 0 运行 (注: proot/z2root 提供的是伪 root)",
            "scan.mPubPort" to "在所有网络接口上监听的 TCP 端口 (不限于 localhost), 端口",
            "scan.mSshdEmpty" to "sshd_config 允许空密码登录 (PermitEmptyPasswords yes)",
            "scan.mSshdPass" to "sshd_config 启用了密码认证 (PasswordAuthentication yes); 密钥认证更安全",
            "scan.mSshdRoot" to "sshd_config 允许 root 登录 (PermitRootLogin yes)",
            "scan.mKeyPerm" to "~/.ssh/authorized_keys 的权限过松, mode",
            "scan.mSshDir" to "~/.ssh 的权限过松, mode",
            "scan.mWorldWrite" to "发现任何人都可写的文件 (最多显示 20 条):",
            "scan.mSuid" to "存在 SUID 可执行文件 (仅供参考; 在 proot/z2root 下是伪 root):",
            "scan.mPath" to "PATH 中含有空元素或 '.' (把当前目录混进 PATH 很危险)",
            "scan.mFound" to "检出条数:",
            "scan.mClean" to "没有发现明显的问题。",
            "scan.mSaved" to "z2scan: 已保存基准:",
            "scan.mSaveFail" to "z2scan: 无法保存基准:",
            "scan.mNoBase" to "z2scan: 还没有基准。请先执行一次 'z2scan self --save' 记录当前状态。",
            "scan.mNoChange" to "[ OK ] 与基准相比没有变化。",
            "scan.mAdded" to "== 相对基准新增的 ==",
            "scan.mRemoved" to "== 相对基准消失的 ==",
            "scan.mCleared" to "z2scan: 已删除基准。",
            "scan.mLangDiff" to "z2scan: 警告 基准是用另一种语言保存的，所以全部都会显示为变化。请用 'z2scan self --save' 重新记录。",
            "scan.langTag" to "zh-CN",
            "scan.usageText" to """
        z2scan - 面向本设备 / localhost 的漏洞检测 (防御用途，不向外发送任何数据)。

        默认只以 localhost 为对象。扫描器从发行版官方软件包 (nmap/lynis/trivy) 安装，
        不增加随附内容，结果也只留在本地。

          z2scan self [--save]         内置的自检 (不需要外部工具): 开放端口、sshd 配置、
                                       SSH 密钥权限、world-writable/SUID、PATH 卫生。
                                       加上 --save 会把结果保存为“基准”。
          z2scan diff [--quiet]        再检一次，只输出相对基准**变化了的部分**。
                                       有新增时退出码为 1，可以直接挂到 z2-when 上。
                                       --quiet 在没有变化时什么都不输出。
          z2scan baseline [clear]      显示保存的基准 (clear 为删除)
          z2scan setup                 用 apk/apt-get/pacman 安装扫描器 (nmap, lynis)
          z2scan net [--allow-remote] [目标]
                                       nmap 的 TCP 扫描。目标默认是 127.0.0.1。
                                       localhost 以外的目标必须加 --allow-remote。
          z2scan host                  用 lynis 做主机审计 (没有则回退到 'self')
          z2scan cve                   有 trivy/grype 时扫描 rootfs 的已知 CVE

        请只扫描明确获得测试授权的对象。
    """.trimIndent(),
            "translationMacro.description" to "使用另行安装的命令翻译文本。",
            "translationMacro.missing" to "此本地环境未安装 trans 命令。请自行安装翻译 CLI 后重试。不会自动下载。",
            "translationMacro.network" to "翻译文本会发送至 CLI 使用的外部服务，受其条款和网络状态影响。",
            "translationSetup.setup" to "请在同一本地 Linux 环境的终端中自行执行以下命令，安装后重试翻译。",
            "translationSetup.alpineRepo" to "若找不到软件包，请在 /etc/apk/repositories 中启用相同 Alpine 版本的 community 仓库。",
            "translationSetup.ubuntuRepo" to "若 Ubuntu 中找不到软件包，请启用 universe 仓库后重试。",
            "translationSetup.unknown" to "无法识别发行版。请选择适合当前本地 Linux 环境的命令。",
            "voice.head" to """# voice.sh — "语音操作"的接收端。用说出的话来控制音乐、音量、计时器、提醒、天气等。
#
# 准备 (只做一次):
#   sh ~/.z2term/macros/voice.sh setup    登记 4 条规则 (语音操作 / 语音操作（锁屏时） / 语音计时器 / 计步)
#   计步规则只是让计步器保持运行，以便询问今天的步数，不会执行任何操作
#   之后用快捷设置磁贴"语音操作"或数字助理 (长按电源键等) 说话
#
# 在终端里:
#   voice.sh words                 打开词表 (~/.z2term/voice/words-<语言>.txt。增加或更换说法)
#   voice.sh try <话>              不说话也能试 (例: voice.sh try 下一首)
#   voice.sh listen                打开听取画面后说话 (与磁贴相同)
#   voice.sh lang [语言]           词表的语言 (用 voice.sh lang - 列出)
#   voice.sh ai [off|codex|claude|agy]   是否使用 AI (一开始是 off)
#   voice.sh model [名字]          AI 的模型 (留空 = 该 AI 的默认)
#   voice.sh setup / help
#
# AI 一开始是关闭的。即使打开，也只有用提示词 ("AI"、"提问" 等) 开头时才会问。
# 天气只在说"天气"时向 wttr.in 查询 (地点: ~/.z2term/voice/config 的 weather_city=)。
# 自己定的话: 在 ~/.z2term/voice/commands.txt 写"话 = 命令" (比词表先看)
# 要打电话的名字: 在 ~/.z2term/voice/contacts.txt 写"名字 号码" / 记录: ~/.z2term/voice/log.txt
# z2-run: sh ~/.z2term/macros/voice.sh setup""",
        )
    }

    // --- 行の並びで持つ文言 (t.lines("名前", …)) ---

    override val lineSets: Map<String, List<String>> by lazy {
        mapOf(
            "macro.watchBasic.1" to listOf(
                "# watch-basic.sh — 入门用的宏。对设备上发生的事情做出反应。",
                "# 等待由应用一侧 (z2-when) 负责，所以它不常驻: 事情发生时跑一次就结束。",
                "# 准备: ⚙设置 → 打开“系统事件检测”",
                "# z2-run: z2-when 'event:power_*' run ~/.z2term/macros/watch-basic.sh   (耳机再登记一条 'event:headset_*')",
            ),
            "macro.watchBasic.2" to listOf(
                "# 发生了什么会放进 Z2_WHEN_EVENT (可用的名字用 z2-when events 列出)。",
                "# 想手动试的话:  Z2_WHEN_EVENT=power_connected sh ~/.z2term/macros/watch-basic.sh",
            ),
            "macro.watchBasic.3" to listOf(
                "[ -n \"${d}event\" ] || { echo \"Z2_WHEN_EVENT 是空的。想手动试就用 Z2_WHEN_EVENT=power_connected sh ${d}0\"; exit 0; }",
            ),
            "macro.watchBasic.4" to listOf(
                "  power_connected)    z2-toast \"开始充电了\" ;;",
                "  power_disconnected) z2-toast \"停止充电了\" ;;",
            ),
            "macro.batteryAlert.1" to listOf(
                "# battery-alert.sh — 电量低了就通知，但要看当时的情况分开处理。",
                "# 用 z2-state 看“屏幕是否亮着”: 亮着就用吐司，熄着就用通知。",
                "# 准备: ⚙设置 → 打开“系统事件检测”",
                "# z2-run: z2-when battery:below=20 run ~/.z2term/macros/battery-alert.sh",
                "",
                "# 电量由 z2-when 传过来。为了手动试也能跑，再用 z2-state 兜一下底。",
            ),
            "macro.batteryAlert.2" to listOf(
                "# 正在充电时就不通知 (并不是电量自己在掉)",
            ),
            "macro.batteryAlert.3" to listOf(
                "  z2-toast \"电量 ${d}{level}%\"",
                "else",
                "  z2-notify -h \"电量偏低\" \"还剩 ${d}{level}%\"",
            ),
            "macro.dailyReport.1" to listOf(
                "# daily-report.sh — 每天早上定时播报电量和连接状况。",
                "# z2-run: z2-when time:daily=07:00 run ~/.z2term/macros/daily-report.sh",
                "# 时刻由 OS 的闹钟唤醒，所以 Doze 中也会响 (为了省电可能晚几分钟)。",
                "# 不依赖检测开关的开关状态。",
            ),
            "macro.dailyReport.2" to listOf(
                "if [ \"${d}(z2-state wifi)\" = \"true\" ]; then net=Wi-Fi; else net=移动数据; fi",
                "z2-say \"早上好。电量 ${d}{level} 个百分点，网络是 ${d}{net}\"",
            ),
            "macro.otpClip" to listOf(
                "# otp-clip.sh — 把通知里的一次性验证码自动放进剪贴板，",
                "# 过 TTL 秒后，如果剪贴板里还是那个值就自动清掉。",
                "# 准备: ⚙设置 → 打开“通知检测” ＋ 授予系统的“通知使用权”",
                "# z2-run: z2-when notify:otp run ~/.z2term/macros/otp-clip.sh",
                "# ⚠ Android 15+ 有时会把敏感通知里的验证码遮蔽掉。想要可靠就用短信版",
                "#    (otp-sms.sh) — 短信正文不会被遮蔽。",
            ),
            "macro.otpSms" to listOf(
                "# otp-sms.sh — 把收到的短信里的一次性验证码自动放进剪贴板，",
                "# 过 TTL 秒后，如果剪贴板里还是那个值就自动清掉。",
                "# 这是 otp-clip.sh 的短信版。和通知不同，短信正文永远不会被敏感通知保护",
                "# (Android 15+) 遮蔽，锁屏状态下也照样能取到。",
                "# 准备: ⚙设置 → 打开“短信检测” ＋ 授予系统的接收短信权限",
                "# z2-run: z2-when sms:otp run ~/.z2term/macros/otp-sms.sh",
            ),
            "macro.unknownCall" to listOf(
                "# unknown-call.sh — 将来电通知中的电话号码显示为复制通知。",
                "# 通知上的“复制”按钮会把号码放进剪贴板 (为什么要等人按，见下面)。",
                "# 准备: ⚙设置 → 打开“通知检测” ＋ 授予系统的“通知使用权”",
                "# z2-run: z2-when notify:category=call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
                "#",
                "# 也想记未接来电的话，用不同的类别再登记一条 (脚本还是这个):",
                "#   z2-when notify:category=missed_call cooldown=20s run ~/.z2term/macros/unknown-call.sh",
            ),
            "macro.rss" to listOf(
                "# rss.sh — 去看订阅源，只留下新的内容，做成通知和文本。",
                "# 和其他示例不同，这个**不常驻**: 它是“由时间触发跑一次就结束”的形态。",
            ),
            "macro.rssOpen" to listOf(
                "# rss-open.sh — 把 rss.sh 收集到的文章从新到旧，每点一次打开一篇。",
                "# 它会出现在状态小组件的宏按钮上，所以每点一下就打开下一篇。",
            ),
            "macro.remind" to listOf(
                "# remind.sh — 用通知来提醒: 一次性 (30 分钟后、18:30) 或者",
                "# 重复 (每天 / 工作日 / 每周)。关着应用也会响 (由 OS 的闹钟唤醒，",
                "# 也不需要打开检测开关)。可以从通知上的按钮小睡。",
            ),
            "macro.qr" to listOf(
                "# qr.sh — 把文本或文件变成二维码: 在这里画出来，或者存成 PNG。",
                "# 不用重新敲一遍就能交给另一台设备。跑一次就结束，不常驻。",
            ),
            "macro.md" to listOf(
                "# md.sh — 把 Markdown 在这里排好版显示，或者在应用里打开来读。",
                "# 标题、列表、表格、代码、链接都排好，图片就在正文的位置显示。跑一次就结束，不常驻。",
            ),
            "macro.cImportantTemplate" to listOf(
                "# 一行一个、不想被埋掉的订阅源或词 (以 # 开头的行会被忽略)。",
                "# 写 URL 的一部分或标题的一部分都行。命中的文章会各自单独发一条通知。",
                "# 例:", "#example.org",
            ),
            "macro.usage" to listOf(
                "usage: z2-macro <子命令>",
                "  list                列出随附的示例 (带 未安装 / 相同 / 有差异 标记)",
                "  install <名字|all>  复制到 ~/.z2term/macros/ (不覆盖已有的。-f 强制覆盖)",
                "  remove <名字>       删除装好的随附示例 (和随附版不同时会停下。-f 强制删除)",
                "  diff <名字>         看设备上的副本和随附版有什么不同 (左边是你自己的)",
                "  show <名字>         显示脚本内容",
                "  run <名字>          就地运行 (Ctrl-C 停止)",
                "  dir                 显示宏放在哪里",
                "  actions ...         Android操作宏 (z2-action --help)",
            ),
            "rssFeeds.labels" to listOf(
            "订阅源", "添加", "文章列表", "修改", "删除", "订阅源 URL",
            "删除此订阅源？已获取的文章将保留。",
            "更改将在下次抓取时生效。已获取的文章将保留。",
            "没有订阅源。请通过“添加”输入 RSS / Atom URL。",
            "请输入不含空白或认证信息的 HTTP(S) URL（最多 2048 个字符）。",
            "此订阅源已存在。",
            "此订阅源已更改。请刷新列表后重试。",
            "正在保存其他更改。请重试。",
            "上一页", "下一页",
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

/** [Z2ApiMsg] の 简体中文 版。上書きしていない文言は英語で出る。 */
private class ApiMsgZhCn(d: String) : Z2ApiMsg("zh-CN", d) {

    override val fileHelp: String get() = "# z2-file pick [MIME] — 选择文档，复制到主目录并返回路径。\n# z2-file save FILE [MIME] — 选择位置并保存文件副本。\n# 取消返回失败。最大512 MiB，选择限时5分钟。"

    override val usbHelp: String get() = """
        |# z2-usb list              … 列出连接到手机的 USB 设备
        |# z2-usb allow [编号]      … 请求 Android 允许 Linux 程序使用该设备
        |# 插入设备后执行一次 allow。权限在拔出设备前一直有效。
    """.trimMargin()

    override val usbUsage: String get() = "usage: z2-usb list | z2-usb allow [编号|路径|VID:PID]"

    override val usbNoDevices: String get() = "手机上没有连接 USB 设备。"

    override val usbNeedSelector: String get() = "连接了多个 USB 设备。请指定 z2-usb list 中的编号。"

    override fun usbPermissionRequested(path: String): String = "请在 Android 屏幕上允许 USB 访问：$path"

    override fun usbAlreadyAllowed(path: String): String = "USB 访问已经允许：$path"

    override fun usbNotFound(selector: String): String = "找不到 USB 设备：$selector"

    override val usbStateAllowed: String get() = "已允许"

    override val usbStateNeedsAllow: String get() = "需要允许"

    override val notifyHelp: String get() = """
        |# z2-notify [-h] [-n 名称] [-c 文本] [-b 标签]... "标题" "正文"  /  z2-notify [-h] "正文"
        |#   -h / --high / --banner : 以横幅(浮动通知)的形式显示在屏幕顶部
        |#   -b <标签>              : 加一个回复按钮 (最多 3 个)。按下后
        |#                            ~/.z2term/events.jsonl 里会多出一行 notify_action
        |#                            ({"event":"notify_action","name":名称,"action":标签})
        |#   -n <名称>              : 这条通知的标识名 (用来区分是对哪次提问的回复)
        |#   -c <文本>              : 加一个“复制”按钮 (按下就把该文本放进剪贴板)。
        |#                            在后台运行的宏里请用它，而不是 z2-clip set —
        |#                            Android 10+ 只让前台应用写剪贴板，
        |#                            没人看着时的 z2-clip set 会被默默丢掉。
    """.trimMargin()

    override val notifyUsage: String get() = "usage: z2-notify [-h] [-n 名称] [-c 文本] [-b 标签]... <标题> [正文]"

    override val toastHelp: String get() = """
        |# z2-toast <消息> … 在屏幕下方显示一条短消息 (吐司)。
        |# 它过几秒会自己消失，什么都不留下。想留下来、或者想让人按一下时，请用 z2-notify。
    """.trimMargin()

    override val qrHelp: String get() = """
        |# z2-qr … 打开 QR 工具并直接用相机扫描。
        |# 扫描后会出现一个与内容对应的「打开」按钮 (浏览器、LINE 等应用、电话、邮件、短信、
        |# Wi-Fi、联系人、日程)。只扫描不会自动打开。可以放到快捷设置磁贴或边缘面板上。
        |# z2-qr encode [-o 文件.png] [-p 像素] [-m 格] [-t] [文本] … 生成二维码。
        |#   不需要安装任何东西 (由应用来编码，和 QR 工具画面用的是同一套)。
        |#   省略文本时从标准输入读。-o 是 PNG 的写出位置 (默认 ~/.z2term/qr/qr.png)，
        |#   并打印写到哪里。-t 则不出图，改用方块字符画在这个终端里。
        |#   -p 是目标边长像素 (默认 600)，-m 是留白的格数 (默认 4)。最多 2000 字节。
    """.trimMargin()

    override val shareHelp: String get() = """
        |# z2-share <文本> … 交给 Android 的分享菜单 (转交给其他应用)。
        |# --file 文件 [文件 ...]: 发送文件内容（最多32个，合计512MiB）。
        |# 路径须在当前本地shell中可读。-- 强制文本模式。超过24小时的共享副本在下次共享时删除。
        |# 所有参数会拼成一段正文。发给哪个应用要在屏幕上选，所以这是给有人在的时候用的
        |# (不适合在后台自己跑的宏)。
    """.trimMargin()

    override val viewHelp: String get() = """
        |# z2-view <文件.html> [标题] … 在 z2term 里阅读你在这里做好的页面。
        |# 不需要服务器和浏览器：把文件交给应用，原样显示。
        |# JavaScript 和对外通信都已关闭，页面里什么都跑不起来。
        |# http:// 和 https:// 的链接会用你平常的浏览器打开。
        |# 手机的主题以 CSS 变量送达 (--z2-bg --z2-bg2 --z2-fg --z2-dim --z2-line --z2-accent)。
        |# 最大 4 MB。图片请嵌入页面 (data: URI)，外部图片不会去取。
        |# --edge 面板:项目 可发送到已有的 type=view 项目。
        |# --controls 文件.json 可定义通用表单和操作，交给已安装的宏处理。
        |# 视图默认手动刷新。view-refresh=auto 时打开会执行 run=，z2-view 返回同一项目。
        |# 用 - 或管道 (ls -l | z2-view) 读取标准输入。不以 < 开头的文字去掉颜色代码后，
        |# 以自动换行的等宽字体显示；--text / --html 可明确指定。
        |# 双指缩放改变文字大小；--pinch page 则缩放整个页面。--text-size 50..300 设定本页文字大小 (%)。
        |# 两者用于全屏显示；面板项目按项目的设置。
        |# --no-refresh / --no-find 隐藏全屏的“刷新”“查找”按钮（默认显示）。
    """.trimMargin()

    override val viewUsage: String get() = "用法: z2-view [--edge PANEL:ITEM] [--controls FILE.json] [--text|--html] [--pinch text|page] [--text-size N] [--no-refresh] [--no-find] <文件|-> [标题]"

    override val viewUnreadable: String get() = "z2-view: 无法读取的文件:"

    override val openHelp: String get() = """
        |# z2-open <URL 或路径> … 用默认应用打开 (https://… 和 /sdcard/… 都行)。
        |# 用哪个应用打开是 Android 决定的，z2term 只负责递过去。
        |# 路径是**手机一侧**的路径 (/sdcard/…)，不是发行版里面的路径。
    """.trimMargin()

    override val imgHelp: String get() = """
        |# z2-img [-w 列] [-r 行] [--clear] [-f] <文件|->… … 直接在终端里画出图片。
        |#   PNG / JPEG / WebP / GIF / BMP。传 - 则从标准输入读一张。
        |#   -w <列>    用列数指定宽度 (默认: 收进终端宽度)
        |#   -r <行>    用行数指定高度 (和 -w 一起给出时就按该尺寸)
        |#   --clear    清掉已经画出的所有图片
        |#   -f         即使标准输出不是终端也照样发送
        |# ⚠ 只有 z2term 的标签页、以及支持 kitty graphics 的终端才会显示图片。
        |#   其它地方 (ssh 过去、分页器里) 只会刷出一堆乱码。
        |# ⚠ 长宽比按“一格的高是宽的两倍”计算。看起来被压扁时可以调整:
        |#   Z2_IMG_ASPECT=0.45 z2-img photo.jpg (数值越小越细长)。
    """.trimMargin()

    override val imgUsage: String get() = "usage: z2-img [-w 列] [-r 行] [--clear] [-f] <文件|->..."

    override val imgNoFile: String get() = "读不到这个文件："

    override val imgNotTty: String get() = "标准输出不是终端，所以没有发送 (加 -f 可以照样发送)。"

    override val imgNoSize: String get() = "读不出像素尺寸，按正方形画出："

    override val qrUsage: String get() = "用法: z2-qr  |  z2-qr encode [-o 文件.png] [-p 像素] [-m 格] [-t] [文本]"

    override val qrEncodeFailed: String get() = "z2-qr: 无法生成二维码 (文本是不是太长了? 最多 2000 字节)"

    override val clipHelp: String get() = """
        |# z2-clip get        … 把剪贴板内容输出到标准输出
        |# z2-clip set [文本] … 把文本 (不给则读标准输入) 放进剪贴板
        |# ⚠ Android 10+ 只让前台应用 (和正在使用的输入法) 碰剪贴板。
        |#   从后台运行的宏里调用会被默默丢掉，那种场合请用 z2-notify -c <文本>
        |#   (它会加一个“复制”按钮，按下就一定能进去)。
    """.trimMargin()

    override val batteryHelp: String get() = "# 以 JSON ({\"level\":N,\"charging\":bool}) 输出电量/充电状态。"

    override val vibrateHelp: String get() = "# z2-vibrate [ms]  (默认 200ms)"

    override val editHelp: String get() = "# z2-edit <文件>      … 在 z2term 自带的编辑画面里改写文本文件 (普通输入框，“保存”写回)"

    override val sayHelp: String get() = "# z2-say <text>       … 用设备自带的 TTS 朗读 (不带参数则读标准输入)"

    override val torchHelp: String get() = "# z2-torch on|off|toggle|status  (默认 toggle)。status查询实际状态(on/off)，包括外部更改。"

    override val shotHelp: String get() = "# z2-shot [free|rect|oval]  … 只截取圈出的部分 (默认 free)。需要 Android 11 与 z2term Android 操作。"

    override val mediaHelp: String get() = "# z2-media play|pause|playpause|next|previous|stop  (默认 playpause)"

    override val volumeHelp: String get() = "# z2-volume up|down|mute|unmute|N|N%   操作媒体音量。输出结果的 current/max。"

    override val sensorHelp: String get() = "# z2-sensor light|accel|proximity  (默认 light)。读一次传感器并以 JSON 返回。"

    override val intentHelp: String get() = """
        |# z2-intent [-a ACTION] [-d URI] [-t MIME] [-p PKG] [-n PKG/CLS] [-c CATEGORY] [-f FLAGS]
        |#           [--es K V] [--ez K true|false] [--ei K N] [--broadcast|--service]
        |# 触发任意 Android Intent (默认是 startActivity)。开头的非选项参数即 ACTION。
        |# --window full|freeform|split|ask 指定窗口模式。freeform 默认复用任务，由系统决定大小。
        |# --profile N 在其他配置文件（如工作资料）中打开应用 (-p/-n)。不支持分屏。N 见边缘面板为该应用保存的命令。
        |# --reuse-task / --os-bounds 保持兼容；--bounds-only 只请求80%大小，不可与 --os-bounds 同用。
        |# --no-scale 停用 Motorola 的缩放自由窗口请求。
    """.trimMargin()

    override val stateHelp: String get() = """
        |# z2-state            … 把设备现在的状态整个以 JSON 返回
        |# z2-state <键>       … 只返回那个值的原始形式 (可以直接写进条件式)
        |# 键: screen(on/off) locked idle charging plug(ac/usb/wireless/none) level temp(℃)
        |#       wifi ssid ringer(normal/vibrate/silent) airplane headset bt_audio volume volume_max
        |# 例: [ "${d}(z2-state charging)" = "true" ] && echo 充电中
    """.trimMargin()

    override val askHelp: String get() = """
        |# z2-ask [-t 秒] [-H 提示] [-d 默认值] <问题>
        |#   向人提问，把答案返回到标准输出。
        |#   问题会以**带回复框的通知**送达，所以不用打开应用，在通知栏里就能回答
        |#   (在后台跑着的宏也能提问)。
        |#   -t <秒>     等待答案的时间 (默认 300 = 5 分钟)
        |#   -H <提示>   回复框里显示的标题
        |#   -d <文本>   答案的候选 (显示在通知里)
        |# 不回答就划掉通知、或者超时的时候，会**以非零退出**且什么都不输出，
        |# 所以“不回答就放弃”可以直接这么写:
        |#   name=${d}(z2-ask "分支名叫什么?") || exit 1
        |# 如果只是让人从准备好的选项里挑，用 z2-notify -b <标签> 更合适。
    """.trimMargin()

    override val askUsage: String get() = "usage: z2-ask [-t 秒] [-H 提示] [-d 默认值] <问题>"

    override val listenHelp: String get() = """
        |# z2-listen [-w 词,词,...] [-l 语言]
        |#   把说的话转成文字输出到标准输出。识别**只在设备上**进行，声音不会发到网络。
        |#   需要 Android 12 以上，以及设备上的语音识别 (该语言的离线识别数据)。
        |#   说话时让 z2term 显示在屏幕上 (Android 只把麦克风交给屏幕上的应用)。
        |#   停顿后结束。第一次运行时会请求麦克风权限。
        |#   -w <词>    只接受这些词 (逗号分隔)，输出命中的那个。
        |#              不只看识别的第一名，也会看其他候选。
        |#   -l <语言>  识别的语言 (例: zh-CN, en-US。默认为设备语言)
        |# 退出码: 0 = 成功 / 1 = 没听清、不是任何一个词 / 2 = 无法使用 (原因输出到标准错误)
        |#   cmd=${d}(z2-listen -w "下一首,停止") || exit
    """.trimMargin()

    override val listenUsage: String get() = "usage: z2-listen [-w 词,词,...] [-l 语言]"

    override val listenPrompt: String get() = "请说话…（停顿后结束）"

    override val listenNeedsAndroid12: String get() = "z2-listen 需要 Android 12 以上（更早的版本无法指定只在设备上识别）。"

    override val listenBusy: String get() = "已经在听了（另一个 z2-listen 正在运行）。"

    override val listenEngineBusy: String get() = "设备的语音识别正在使用中（可能有其他应用在用）。请稍后再试。"

    override val listenEngineStuck: String get() = "语音识别没有响应。如果持续发生，请在 Android 设置 › 应用 中强行停止设备的语音识别应用（如 Android System Intelligence）。"

    override val listenStopped: String get() = "已停止聆听。"

    override val listenNoOnDevice: String get() = "此设备没有设备上的语音识别。z2-listen 不会把声音发到网络，所以这里无法使用。"

    override val listenNoPermission: String get() = "没有麦克风权限。让 z2term 显示在屏幕上再运行一次即可请求权限（也可以在 Android 设置中允许）。"

    override val listenNotShown: String get() = "请让 z2term 显示在屏幕上再试一次（Android 只把麦克风交给屏幕上的应用）。"

    override val listenNoMic: String get() = "无法使用麦克风（z2term 离开了屏幕，或其他应用正在录音）。"

    override val listenNoLanguage: String get() = "没有该语言的设备上识别数据。请在设备的语音输入设置（离线语音识别）中下载。"

    override val listenNoSpeech: String get() = "没有听清。"

    override val listenTimedOut: String get() = "超时。"

    override fun listenNoMatch(heard: String): String = "不是要等的词（听到的是：$heard）"

    override fun listenFailed(code: Int): String = "语音识别失败（错误 $code）。"

    override val screenHelp: String get() = """
        |# z2-screen keepon <N|Ns|Nm|Nh> … 在这段时间内，让屏幕不会自己熄灭
        |# z2-screen keepon off          … 不等到期，现在就恢复原样
        |# z2-screen status              … 以 JSON 输出当前状态
        |#   (allowed / keepon / timeout_ms / until / remaining_sec / original_ms)
        |# 它改的是 OS 全局的“屏幕熄灭时间”，所以把应用收到后台也照样有效。
        |# 和工具栏的🔅 (只在打开应用期间不熄灭) 是两回事，那个不会被碰。
        |# 原来的值会被保存，到期时一定会写回去 (即使应用被杀、设备重启也一样)。
        |# 一次最多只能设 24h。
        |# 需要“修改系统设置”的授权 (设置 › 屏幕自动熄灭 › 允许)。
        |# 例: z2-screen keepon 1h; make; z2-screen keepon off
    """.trimMargin()

    override val screenUsage: String get() = "usage: z2-screen keepon <N|Ns|Nm|Nh> | keepon off | status"

    override val tileHelp: String get() = """
        |# z2-tile set <1-$tiles> <宏.sh | 命令...> [--off <命令...>] [-l <显示名>] [-i <图案名>]
        |#                           … 分配到快捷设置磁贴 1〜$tiles
        |# z2-tile add <1-$tiles>         … 询问是否把那个位放到面板上 (Android 13 以上)
        |# z2-tile list              … 以 TSV 列出全部 $tiles 个位 (位 / 显示名 / 命令。'-' 表示空)
        |# z2-tile clear <1-$tiles|all>   … 清掉分配
        |#
        |# 可以分配什么
        |#   既可以是 ~/.z2term/macros/ 里**宏的文件名**，也可以是照原样执行的**命令**
        |#   (会根据名字自动判断)。
        |#   宏**可以带参数** ('remind.sh ask')。是不是宏由**开头那个词**决定，
        |#   其余部分原样传给宏。同一个宏放到 2 个位上显示名会重复，请用 -l 区分。
        |#   执行时，环境变量 Z2_TILE 里是位号 (宏的话还有 Z2_TILE_MACRO)。
        |#   ⚠ 以 .sh 结尾却不在 ~/.z2term/macros/ 里的名字**会在这里被拒绝** — 因为它会被
        |#     当成命令去 PATH (里面没有宏的目录) 里找，结果找不到，磁贴按了毫无反应、
        |#     原因只出现在 tile/run.log 里，是一种很难察觉的坏法。
        |#
        |# 按下时的动作
        |#   按一下执行，再按一下停止 (和小组件按钮的约定一样)。
        |#   运行期间磁贴看起来是“开”的 (颜色是 OS 的，不是我们的)。
        |#   加上 --off 就变成**开 / 关两条命令**，每按一次交替执行
        |#   (开着的期间磁贴显示为“开”)。适合关闭需要另一条命令的场合 (z2-torch on / off)。
        |#   普通命令对记住切换状态。直接分配的z2-torch跟随Android的实际状态，包括外部更改。
        |#   z2-screen keepon也跟随实际有效状态。
        |#
        |# 放到面板上
        |#   Android 13 以上会在 set 的当场**询问是否放到面板上**，而且那个对话框里会显示
        |#   **你刚刚分配的名字和图标**，不用在编辑界面里猜。拒绝的话就什么都不放。
        |#   位正好有 $tiles 个，在 manifest 里写死，运行时无法增加。还没分配过的位不会
        |#   出现在编辑界面的列表里，所以有 $tiles 个也不碍事。
        |#   ⚠ 只有 z2term 在前台时才会出现，而且**一次只能问一个** — 一口气登记 2 个位的宏
        |#     只会被问第一个，剩下的请用 z2-tile add <位> 重新询问。
        |#   ⚠ 放不放**由你自己决定** — Android 禁止应用擅自放置。在快捷设置面板的
        |#     铅笔(编辑)里找的时候，认准 **z2term <位号>**。
        |#   ⚠ 那个列表里显示的名字和图标是 manifest 写死的，**不是你分配的名字**
        |#     (Android 没有在运行时替换它们的手段)。哪个号是什么，用 z2-tile list 可以看到。
        |#
        |# 图案 (-i)
        |#   能从名字看出来的**会自动配上图标** (remind.sh 就是时钟)。可以用 z2-icon 换成
        |#   喜欢的图案，换过一次之后就不再自动改动。
        |#   加上 -i 的话，**在上面那个对话框出现之前**图案就定下来了 (一行就把名字和图案
        |#   都办完)。名字从 z2-icon sample 的列表里选。
        |#   ⚠ 写了不存在的名字时**连分配一起拒绝** — 因为只分配好了却配着错图案，
        |#     比什么都没发生更难察觉。
        |#
        |# 例: z2-tile set 1 backup.sh
        |#     z2-tile set 2 'z2-screen keepon 1h' -l 不熄屏
        |#     z2-tile set 3 z2-torch on --off z2-torch off -l 手电
        |#     z2-tile set 4 backup.sh -l 备份 -i sync
    """.trimMargin()

    override val tileUsage: String get() = "usage: z2-tile set <1-$tiles> <宏.sh|命令...> [--off <命令...>] [-l 显示名] " +
                "[-i 图案名] | add <1-$tiles> | list | clear <1-$tiles|all>"

    override fun tileNoSuchIcon(name: String): String = "z2-tile: 没有这个图案: $name (列表见 z2-icon sample)。没有进行分配。"

    override fun tileAddEmpty(n: Int): String = "z2-tile: 位 $n 还是空的 (请先用 z2-tile set $n <…> 分配)"

    override val tileAddUnsupported: String get() = "z2-tile: 这里没法请求添加 (需要 Android 13 以上，并且 z2term 在前台)。" +
                "分配已经完成，请从快捷设置面板的铅笔(编辑)里摆上去。"

    override val tileAddAsked: String get() = "已经在问 Android 是否添加磁贴，请在弹出的对话框里回答。" +
                "(如果什么都没出现，请从编辑界面摆上去)"

    override val iconHelp: String get() = """
        |# 把状态栏和快捷设置磁贴的图标，换成你自己的点阵图案。
        |#
        |# 先做这些就够
        |#   z2-icon pick 1     ... 从列表里选一个图案放进磁贴的位 1
        |#   z2-icon list       ... 现在哪个位放着什么图案 (加 -p 会连图案一起显示)
        |#
        |# <目标> 怎么写
        |#   notify    这个应用发出的所有通知的图标 (显示在状态栏上的那个)
        |#   1〜$tiles      快捷设置磁贴的位号 - 和 z2-tile 的位是同一个号
        |#
        |# 放入图案
        |#   pick <目标>              列出来按编号选 (最简单)
        |#   sample <目标> <名字>     已经知道名字的时候
        |#   edit <目标>              在 ${d}EDITOR 里画 / 修改现在的图案
        |#   set <目标> <文件>        从文件读入 (- 表示标准输入)
        |#   auto <1〜$tiles|all>         根据分配的命令重新选一次
        |#   clear <目标|all>         恢复成内置图标
        |#
        |# 精细程度 (格子的边长)
        |#   grid                     显示接下来画图的边长
        |#   grid <$grids>          决定接下来画图的边长 (默认是 $grid)
        |#   scale <目标> <$grids>    把现在的图案重新铺到那个格子上 (顺便把斜边的锯齿
        |#                            抹平)。**能画得更细了** — 输出的图标本来就会被抹平，
        |#                            所以不必为了变平滑而执行它。
        |#
        |# 把自己的图案留在列表里
        |#   save <目标> <名字>       给现在放着的图案起个名字，加进列表
        |#   sample                   可选图案的列表 (编号 / 名字 /
        |#                            builtin = 随附的图案、mine = 自己保存的图案)
        |#   sample <名字>            显示那个图案
        |#   forget <名字>            把自己的图案从列表里撤下
        |#                            (已经放进去的图案仍然留在原处)
        |#
        |# 怎么画
        |#   它是一张字符格子。'.' ' ' '0' '-' '_' 表示空格，其他任何字符都表示填充，
        |#   所以用你自己看得最清楚的字来画就行。周围的空白会被忽略并重新居中，
        |#   所以不必把每行都填满。边长可以从 $grids 里选 (z2-icon grid)。
        |#   ⚠ 把边长调大并不会让图案变大。不把格子画满的话，图标就会显得小。
        |#
        |# 需要记住的
        |#   ⚠ 能传达的只有形状。Android 会把这些图标重新涂成单色
        |#     (磁贴在开 / 关时颜色不同)。颜色是选不了的。
        |#   ⚠ **输出时轮廓会自动抹平**。用 ${grid} 格画的图案，在磁贴上
        |#     (显示得比状态栏大得多) 也会很平滑，所以平时不用在意边长。
        |#     48 / 64 是留给**想自己画得更细**的时候选的。
        |#     show 显示的是你画的原样，磁贴上会比它更平滑。
        |#   ⚠ 磁贴会根据分配的名字自动配上图案
        |#     (z2-tile set 1 remind.sh 就是时钟)。这里放进去的图案优先级更高，
        |#     之后不再自动改动。想恢复自动，用 z2-icon auto 1。
        |#   ⚠ 有 2 个图标改不了: 快捷设置的**编辑**界面
        |#     (往外拖磁贴的地方) 的图标，以及启动器的图标。
        |#     因为 Android 在安装时就把它们固定了。
        |#
        |# 例: z2-icon pick 1                从列表里选位 1 的图案
        |#     z2-icon edit 1                自己重新画
        |#     z2-icon save 1 我的脸         给那个图案起名字并加进列表
        |#     z2-icon sample 3 我的脸       位 3 也放同一个图案
        |#     z2-icon grid 64               接下来画的图案改用 64x64 的格子
        |#     z2-icon scale 1 48            把位 1 的图案挪到 48x48 (好画得更细)
        |#     z2-icon list -p               连图案一起确认哪里放着什么
        |#     z2-icon clear notify          把通知的图标恢复原样
    """.trimMargin()

    override val iconUsage: String get() = "usage: z2-icon pick <notify|1-$tiles> | sample [名字|目标 名字] | " +
                "edit <notify|1-$tiles> | set <notify|1-$tiles> [文件|-] | " +
                "save <notify|1-$tiles> <名字> | forget <名字> | " +
                "show <notify|1-$tiles> | auto <1-$tiles|all> | clear <notify|1-$tiles|all> | " +
                "grid [$grids] | scale <notify|1-$tiles> <$grids> | list [-p]"

    override val iconNoSuchFile: String get() = "没有这个文件:"

    override val iconEditUnchanged: String get() = "没有改动就结束了。"

    override val iconPickPrompt: String get() = "请输入编号 (或名字)，留空则中止: "

    override val iconPickCancelled: String get() = "已中止。"

    override val iconNoEditor: String get() = "找不到编辑器。请设置 ${d}EDITOR，或者用 z2-icon set <目标> <文件>"

    override val tileNoSuchMacro: String get() = "~/.z2term/macros/ 里没有这个宏 (要当成命令运行请写完整路径):"

    override val notiHelp: String get() = """
        |# z2-noti trace start [package] / trace dump / trace stop
        |# 诊断需先开启通知检测；仅在内存保留5分钟内最新128条记录。
        |# 只记录字段类型和UTF-16长度，不保留文本值。dump/stop输出JSON，start清空旧记录。
        |# z2-noti list  … 以 TSV 显示当前正在显示的通知
        |#                 (key / 包名 / 应用名 / 标题 / 正文)
        |# 只是读取。“按下”“清除”通知是**有意**没有提供的
        |# (那样连其他应用的支付、发送按钮都能按下去)。
        |# 需要通知使用权 (设置 › 权限与通知 › 通知检测)。
        |# 另见: z2-when notify:otp / notify:pkg=<片段> / notify:contains=<片段>
    """.trimMargin()

    override val notiUsage: String get() = "usage: z2-noti list | trace start [package] | trace dump | trace stop"

    override val alarmHelp: String get() = """
        |# z2-alarm at HH:MM [名称]     … 在下一个 HH:MM 响一次 (今天已过就是明天)
        |# z2-alarm daily HH:MM [名称]  … 每天 HH:MM
        |# z2-alarm in <N|Ns|Nm|Nh> [名称] … N 秒/分钟/小时后响一次
        |# z2-alarm list                … 列出预约 (JSON。"exact" 表示是否准点响)
        |# z2-alarm cancel <id|名称|all> … 取消
        |# 触发时 ~/.z2term/events.jsonl 会多出一行 {"event":"alarm","name":…}。
        |# 即使在 Doze 中也会唤醒。"exact":true 表示准点响。"exact":false 时，
        |# Doze 中每 9〜15 分钟才有一次触发机会，所以熄屏放着会晚这么多。
        |# 让它变成 true 的办法是**关掉这个应用的电池优化**，不会额外索要权限。
    """.trimMargin()

    override val alarmNoDate: String get() = "z2-alarm: date 用不了"

    override val sessionHelp: String get() = """
        |# z2-session list                     … 标签页一览 (编号 / id / 类别 / 标记 / 名称 的 TSV)
        |#   标记: * = 正在显示 / ! = 有东西在运行 / ? = 还没启动 / @ = 已从终端接入 / - = 其他
        |# z2-session new [名称]               … 开一个终端标签页 (返回编号和 id)
        |# z2-session send <目标> <文本>...    … 往那个标签页里输入文字 (不执行)
        |# z2-session send <目标> <文本> --enter … 输入之后再执行
        |# z2-session key <目标> <键>...      … 往那个标签页送**按键** (C-c / M-x / F5 / Up …)
        |#   修饰键是 C- (Ctrl) 和 M- (Meta=Alt)。可以叠加，如 C-M-a
        |#   特殊键: Up Down Left Right Home End PgUp PgDn Ins Del Tab S-Tab Enter Esc Space BS F1-F12
        |#   ⛔ 像 C-S-a 这样带 Shift 的会被拒绝 (终端分辨不出 Shift，字节和 C-a 完全一样)
        |# z2-session key <目标> --raw '\x1b[A' … 表里没有的就用字节序列 (\xHH \e \n \r \t \0)
        |# z2-session capture [目标] [--all]   … 取出那个标签页的画面 (--all 连可回滚的部分)
        |# z2-session attach <目标>            … 一直连着那个标签页，像平常一样打字
        |#   退出用 Ctrl+] (随时)，或在行首打 ~. (和 ssh 一样)
        |#   ⚠ 通过 SSH 只有 Ctrl+] 有效 (~. 会被前面那层 ssh 吃掉，连 SSH 一起断)
        |#   行首想打出 ~ 本身就写 ~~，想送出 Ctrl+] 本身就在行首的 ~ 之后按 Ctrl+]
        |#   连着的期间，标签页的宽高跟随**连过去的这一侧**。退出后恢复原样
        |# z2-session close <目标>             … 关掉那个标签页 (最后一个不会关)
        |#
        |# <目标> 可以是 list 的编号 / id / 标签页名称。写 '.' 或者省略表示当前显示的标签页。
        |# 例: n=${d}(z2-session new build | cut -f1); z2-session send "${d}n" 'make -j2' --enter
    """.trimMargin()

    override val sessionUsage: String get() = "usage: z2-session list | new [名称] | send <目标> <文本>... [--enter] | " +
                "key <目标> <键>... | key <目标> --raw <字节序列> | capture [目标] [--all] | " +
                "attach <目标> | close <目标>"

    override val keyNothing: String get() = "z2-session key: 没有给出要送的键"

    override val keyRawEmpty: String get() = "z2-session key: --raw 后面没有字节序列"

    override val keyUnknown: String get() = "z2-session key: 没有这个键名 (一览见 'z2-session -h'):"

    override val keyBadEscape: String get() = "z2-session key: 读不懂的转义:"

    override fun keyShiftNotDistinguishable(asWritten: String, equivalentTo: String): String = "z2-session key: 终端分辨不出 Shift，所以 '$asWritten' 和 '$equivalentTo' 会是完全一样的字节。" +
                "如果这正是你想要的，请写成 '$equivalentTo'。"

    override fun attachNoSuchTab(target: String): String = "没有这个标签页: '$target' (用 'z2-session list' 可以看到一览)"

    override val attachNotTerminal: String get() = "那是图形标签页，没有 shell。请指定一个终端标签页。"

    override val attachNotStarted: String get() = "那个标签页还没启动 (list 里的标记是 '?')。请先在应用里打开一次再连接。"

    override val attachExited: String get() = "那个标签页已经结束了。画面还可以用 'z2-session capture' 取出来。"

    override val attachSelf: String get() = "那就是你正在打字的这个标签页。连到自己身上的话，它自己的输出会原样回到" +
            "自己这边，一直循环停不下来。请指定别的标签页。"

    override val attachLoop: String get() = "那个标签页已经连回到这一边了，两边会互相发送、停不下来。" +
            "请先断开其中一边 (Ctrl+])。"

    override val serverHelp: String get() = """
        |# z2-server list                … 已登记的服务器一览 (编号 / id / 状态 / 标记 / 名称 的 TSV)
        |#   标记: * = 已启用 / - = 已禁用
        |# z2-server start <服务器>      … 作为常驻服务启动 (在熄屏时也能收到请求的框架里)
        |# z2-server stop <服务器>       … 只停这一个 (其他照常运行)
        |# z2-server status [<服务器>]   … 状态、pid、重启次数、上次的退出码
        |#
        |# <服务器> 可以是 list 的编号 / id / 你在应用里起的名字。
        |# 能启动的只有已经在应用里登记过的 (这里不会新登记)。
        |#
        |# 为什么需要它: 直接从规则里起的守护进程跑在**常驻服务的框架之外**
        |# (没有 WakeLock、没有 WifiLock、也没有前台服务)，一熄屏就不再响应。
        |# 从这里起就进到框架里面了。
        |# 例: z2-when wifi:connect run 'z2-server start sshd'
        |#     z2-when wifi:disconnect run 'z2-server stop sshd'
    """.trimMargin()

    override val serverUsage: String get() = "usage: z2-server list | start <服务器> | stop <服务器> | status [<服务器>]"

    override val serverNotFound: String get() = "z2-server: 没有这个服务器 (一览见 'z2-server list'):"

    override val serverAmbiguous: String get() = "z2-server: 有多个同名的服务器。请用 'z2-server list' 里的 id 指定:"

    override val serverNone: String get() = "z2-server: 还没有登记任何服务器 (在应用的 📜 → 服务器 里登记)。"

    override val serverLowPowerWarn: String get() = "z2-server: 注意 — 省电模式是开着的，所以不持有 WakeLock/WifiLock。" +
            "熄屏期间服务器可能不再响应 (⚙设置 → 自动化 → 后台进程保护)。"

    override val whenHelp: String get() = """
        |# z2-when <触发条件> run <命令...>      … 登记一条规则
        |#   触发条件: charge:start | charge:stop  (前提是检测已开启)
        |#            battery:below=N | battery:above=N  (前提是检测已开启)
        |#            battery:temp>N | battery:temp<N  … 电池温度 (℃) 跨过 N 时 (前提是检测已开启)
        |#            time:daily=HH:MM | time:at=HH:MM | time:every=Nm|Nh
        |#            time:cron='分 时 日 月 星期'  (星期 0-7 / 0,7=周日。含空格，要加引号)
        |#            wifi:connect | wifi:disconnect | wifi:ssid=<名称>  (前提是检测已开启)
        |#            net:online | net:offline  … 有了能通的网络 / 网络没了
        |#            net:wifi | net:mobile | net:ethernet  … 在用的网络切换成了它
        |#                                        (前提是检测已开启。不只看 Wi-Fi，也看移动数据)
        |#            share:any | share:text | share:file | share:contains=<片段> | share:ext=<扩展名>
        |#                                        … 别的应用通过分享菜单发到 z2term 时
        |#            boot                        … 设备启动完成时 (检测关闭也能用)
        |#            sms:any | sms:from=<片段> | sms:contains=<片段> | sms:otp  (前提是 RECEIVE_SMS 权限)
        |#            sensor:shake | sensor:light>N | sensor:light<N | sensor:proximity=near|far  (前提是检测已开启)
        |#            file:new=<文件夹>[,ext=<扩展名>]  … 那个文件夹里来了新文件时 (前提是检测已开启)
        |#            file:changed=<文件夹>[,ext=<扩展名>] | file:deleted=<文件夹>[,ext=<扩展名>]
        |#                                        … 那里的文件被改写 / 消失时 (前提是检测已开启)
        |#            bt:connect | bt:disconnect | bt:connect=<名称> | bt:disconnect=<名称>
        |#                                        … 蓝牙设备连上 / 断开时 (名称写其中一部分，或者写地址)
        |#                                        (前提是检测已开启，Android 12 起还要「附近的设备」权限)
        |#            app:open=<应用> | app:close=<应用>  … 那个应用来到前台 / 离开前台时
        |#                                        (包名或应用名的一部分。前提是在无障碍里开启 z2term。检测关闭也能用)
        |#            steps:reach=N               … 今天的步数达到 N 时 (前提是检测已开启和「身体活动」权限)
        |#            notify:any | notify:otp | notify:pkg=<片段> | notify:title=<片段> | notify:contains=<片段>
        |#                                        … 收到通知时 (前提是通知使用权)
        |#            event:<名称> | event:<前缀>* | event:*  … 按名字捕捉设备事件
        |#              (名字用 z2-when events 列出。和 events.jsonl 里的一样)
        |# 名称 (可选。放在触发条件之后、run 之前):
        |#   name=<文本>             … 表示这是什么自动化，会成为自动化标签页里的标题
        |#                             (含空格要加引号)。留空则照旧显示触发条件。
        |#                             只是显示用，什么时候运行完全不受影响。
        |# 筛选 (任何触发条件都能用。放在触发条件之后、run 之前):
        |#   if=<条件>[,<条件>...]   … 只在设备处于那个状态时执行 (逗号是 AND。开头加 ! 表示否定)
        |#                             可用的键就是 z2-state 输出的那些: wifi charging screen locked
        |#                             idle headset bt_audio airplane plug ssid ringer level temp volume
        |#                             例: if=wifi,!screen / if=ssid=Home / if=level<30
        |#   if_any=<条件>[,<条件>...] … **满足其中任意一个**就执行 (逗号变成 OR)
        |#                             和 if= 一起用就是「if 全部满足，并且 if_any 满足其一」
        |#   else=<命令>              … 不满足 if / if_any 时，**改为**执行这个
        |#                             ⚠ 只有被 if 系筛掉时才生效。被 between / days /
        |#                             cooldown 筛掉时 else 也不会运行 (免得本该夜里不动的
        |#                             规则，半夜反而发来通知)
        |#   cooldown=30m            … 距上次执行这段时间内不再运行 (10s / 30m / 2h)
        |#   between=22:00-07:00     … 只在这个时间段执行 (可以跨零点)
        |#   days=mon-fri            … 只在这些星期执行 (星期名，或和 cron 一样的数字 0-7 / 0,7=周日)
        |#   answer='<提示>=><回答>'  … 命令在终端这样询问时，送出回答和 Enter
        |#                             (可写多个。例: answer='[y/N]=>y')。与输出最后一行的结尾比较
        |#   secret='<提示>'         … 用于隐藏输入的回答 (密码)。内容在应用的规则编辑画面输入
        |#                             (加密保存在应用内，不写入规则文件)
        |# 筛掉的也会记录 — z2-when fired 里会出现 skip:if / skip:if→else / skip:between / skip:days / skip:cooldown，
        |# 所以「为什么不动」是查得出来的。应用界面上的 ▶ 会无视所有筛选
        |# (它是用来试跑的按钮)。
        |# z2-when events                        … 列出 event: 可以用的事件名
        |# z2-when pause / resume                … 暂停 / 恢复自动运行 (规则不会被删)
        |# z2-when fired [n]                     … 最近的触发 (时刻 / id / 触发条件 / run|paused)
        |# z2-when list                          … 已登记的规则 (id / on|off / 触发条件 / -> / 命令 的 TSV。
        |#                                         名称和筛选如果有，会出现在末尾的 [] 里)
        |# z2-when remove <id|all>  (rm 也可以)  … 删除
        |# z2-when on <id> / off <id>            … 启用 / 禁用
        |# z2-when log <id>                      … 那条规则的执行日志 (末尾)
        |# 触发时，命令在当前选中的发行版上执行，环境变量里会有 Z2_WHEN_TRIGGER / Z2_WHEN_LEVEL
        |# / Z2_WHEN_SSID (wifi) / Z2_WHEN_SMS_FROM / Z2_WHEN_SMS_BODY / Z2_WHEN_OTP (sms)
        |# / Z2_WHEN_SENSOR / Z2_WHEN_LUX (sensor)。
        |# net: 时会有 Z2_WHEN_NET (现在的网络) 和 Z2_WHEN_NET_PREV (之前的网络)。
        |# share: 时会有 Z2_WHEN_SHARE (文本本身，或者收进来的文件路径) 和
        |# Z2_WHEN_SHARE_KIND (text|file)。分享过来的内容照旧也会进到输入行。
        |# file: 时会有 Z2_WHEN_FILE (完整路径) 和 Z2_WHEN_DIR。
        |# notify: 时会有 Z2_WHEN_NOTI_PKG / _APP / _TITLE / _TEXT / _CATEGORY
        |# (notify:otp 还会有 Z2_WHEN_OTP)。notify:category= 是完全匹配: call, missed_call, msg, ...
        |# event: 时会有 Z2_WHEN_EVENT (事件名)。alarm / notify_action 还会有
        |# Z2_WHEN_EVENT_NAME (设下时的标识名) 和 Z2_WHEN_ACTION (按了哪个按钮)。
        |# battery:temp 时会有 Z2_WHEN_TEMP (℃)。bt: 时会有 Z2_WHEN_BT_NAME / Z2_WHEN_BT_ADDR。
        |# app: 时会有 Z2_WHEN_APP_PKG (现在前台的应用) / Z2_WHEN_APP_PREV (之前的应用)。
        |# steps: 时会有 Z2_WHEN_STEPS (今天的步数)。file: 还会有 Z2_WHEN_FILE_ACTION (new|changed|deleted)。
        |# app_installed / app_removed / app_updated 会有 Z2_WHEN_PKG，timezone_changed 会有 Z2_WHEN_TZ，
        |# usb_attached / usb_detached 会有 Z2_WHEN_USB (设备名) / Z2_WHEN_USB_ID (厂商:产品)。
        |# 同一条规则在 10 秒内不会连续触发两次 (针对 screen_on 这类数量多的事件)。
        |# 例: z2-when charge:start run ~/.z2term/macros/backup.sh
        |#     z2-when time:cron='0 3 * * *' run ~/.z2term/macros/nightly.sh
        |#     z2-when event:headset_plugged run ~/.z2term/macros/play.sh
        |#     z2-when 'event:ringer_*' run 'z2-toast "响铃模式: ${d}Z2_WHEN_EVENT"'
        |#     z2-when file:new=/sdcard/Pictures/Screenshots run ~/.z2term/macros/shot.sh
        |#     z2-when net:online cooldown=5m run ~/.z2term/macros/sync.sh
        |#     z2-when boot run 'sshd --lan'
        |#     z2-when share:text run '~/.z2term/macros/fetch.sh "${d}Z2_WHEN_SHARE"'
        |#     z2-when time:daily=07:00 name='早间日报' run ~/.z2term/macros/report.sh
    """.trimMargin()

    override val whenPaused: String get() = "已暂停自动运行 (用 z2-when resume 恢复)"

    override val whenResumed: String get() = "已恢复自动运行"

    override val whenNoFires: String get() = "(还没有触发过)"

    override val whenPausedNote: String get() = "# 已暂停 (用 z2-when resume 恢复)"

    override val whenNoLog: String get() = "(还没有日志)"

    override val whenWriteFailed: String get() = "z2-when: 写入失败"

    override val whenUnknownIfKey: String get() = "z2-when: if= 里写不了这个条件 (可用的就是 z2-state 输出的那些项):"

    override val whenUnknownTrigger: String get() = "z2-when: 没有这个触发条件 (一览请看不带参数的 z2-when):"

    override val whenBadTriggerSpec: String get() = "z2-when: 那个触发条件不能这么写:"

    override val whenPausedWarn: String get() = "注意: 自动运行正处于暂停 (用 z2-when resume 恢复)"

    override val whenRunJoined: String get() = "注意: 已把命令里的换行改成空格 (一条规则只读一行)"

    override val whenEventList: String get() = """
        |screen_on              屏幕点亮了                [检测 ON]
        |screen_off             屏幕熄灭了                [检测 ON]
        |unlocked               解锁了设备                [检测 ON]
        |power_connected        开始充电                  [检测 ON]
        |power_disconnected     停止充电                  [检测 ON]
        |battery_low            电量变少了                [检测 ON]
        |battery_okay           电量恢复了                [检测 ON]
        |battery_level          电量跨过 10% 的刻度       [检测 ON]
        |wifi_connected         连上了 Wi-Fi              [检测 ON]
        |wifi_disconnected      断开了 Wi-Fi              [检测 ON]
        |net_online             有了能通的网络            [检测 ON]
        |net_offline            没有能通的网络            [检测 ON]
        |net_wifi               在用的网络变成 Wi-Fi      [检测 ON]
        |net_mobile             在用的网络变成移动数据    [检测 ON]
        |headset_plugged        插入了有线耳机            [检测 ON]
        |headset_unplugged      拔出了有线耳机            [检测 ON]
        |bt_audio_connected     蓝牙音频连上了            [检测 ON]
        |bt_audio_disconnected  蓝牙音频断开了            [检测 ON]
        |airplane_on            打开了飞行模式            [检测 ON]
        |airplane_off           关闭了飞行模式            [检测 ON]
        |ringer_normal          改成了响铃                [检测 ON]
        |ringer_vibrate         改成了振动                [检测 ON]
        |ringer_silent          改成了静音                [检测 ON]
        |bt_connected           蓝牙设备连上了            [检测 ON]
        |bt_disconnected        蓝牙设备断开了            [检测 ON]
        |powersave_on           打开了省电模式            [检测 ON]
        |powersave_off          关闭了省电模式            [检测 ON]
        |dnd_on                 打开了勿扰模式            [检测 ON]
        |dnd_off                关闭了勿扰模式            [检测 ON]
        |app_installed          安装了应用                [检测 ON]
        |app_removed            卸载了应用                [检测 ON]
        |app_updated            应用更新了                [检测 ON]
        |timezone_changed       时区变了                  [检测 ON]
        |usb_attached           插入了 USB 设备           [检测 ON]
        |usb_detached           拔出了 USB 设备           [检测 ON]
        |boot                   设备启动完成              [总是]
        |alarm                  z2-alarm 响了             [总是]
        |notify_action          按了通知上的按钮          [总是]
        |unlock_failed          解锁失败了                [总是，需设置]
        |unlock_succeeded       失败之后解锁成功          [总是，需设置]
        |voice                  语音操作说了话            [总是，从磁贴或助手]
    """.trimMargin()

    override val whenEventListNote: String get() = """
        |    # event:<名称> 里可以写的名字。顺序和 events.jsonl 里出现的一样。
        |    # 上面一组的前提是检测已开启 (设置 › 常驻服务与自动化 › 系统事件检测)，
        |    # 下面一组是你自己设下的，所以检测关闭也能用。
    """.trimMargin()

    override val updateHelp: String get() = """
        |# z2-update                     … 检查新版本、下载，并把安装确认界面调出来
        |# z2-update --check             … 只说有没有更新的版本
        |# z2-update --keep              … 保留下载的 .apk (默认装完就删)
        |# z2-update --dir <文件夹>      … 下载到那个文件夹，而不是应用内部
        |#
        |# 从哪里来
        |#   z2term 的 GitHub Releases (和你手动下载的是同一个 z2term-<版本>.apk，用发布密钥签名)。
        |#   不会连接任何别的地方，而且在你敲这条命令之前**完全不联网**。
        |#
        |# 能做什么、不能做什么
        |#   ⚠ **它没法悄悄安装。** 应用替换自己时，Android 一定会弹出自己的
        |#     「要安装吗」界面，最后那一下必须由你自己按。这条命令负责做到那个界面出现为止。
        |#   ⚠ 第一次要给 z2term 允许**「安装未知应用」**
        |#     (不够时它会提示。⚙设置里也有同样的按钮)。没有授权的话，确认界面根本不会出现。
        |#   ⚠ 如果是从 F-Droid 之类的应用商店装的版本，**它会拒绝** — 那个版本该由商店来替换。
        |#
        |# 下载的文件
        |#   默认落在应用内部的工作目录里，装完就删 (替换过程中应用会被杀掉，所以
        |#   **下次启动时也会再清理一遍**)。用 --keep / --dir 可以改，
        |#   ⚙设置里也有同样的两项 (从设置的按钮更新时以那边为准)。
        |#
        |# 例: z2-update --check
        |#     z2-update --dir /sdcard/Download --keep
        |#     z2-when time:daily=03:00 run 'z2-update'   # 每晚看一次 (确认界面还是会出现)
    """.trimMargin()

    override val updateUsage: String get() = "usage: z2-update [--check] [--keep] [--dir <文件夹>]"

    override fun updateUpToDate(current: String): String = "z2-update: $current 已经是最新的了。"

    override fun updateFound(current: String, latest: String, size: String): String = "z2-update: $current → $latest ($size)"

    override val updateChecking: String get() = "z2-update: 去看看有没有新版本 ..."

    override val updateHandedToInstaller: String get() = "z2-update: 已在设备上打开安装界面 — 确认后即可完成替换。"

    override fun updateNoApk(url: String): String = "z2-update: 那个发布里没有附带 .apk。请从发布页面安装: $url"

    override val updateNeedPermission: String get() = "z2-update: 请先给 z2term 允许“安装未知应用” " +
            "(设置 › 应用 › 特殊应用权限 › 安装未知应用。z2term 的 ⚙设置里也有按钮)。"

    override val updateManagedByStore: String get() = "z2-update: 这个版本是从应用商店 (F-Droid / Play) 装的。请从那里更新。"

    override fun updateFailed(reason: String): String = "z2-update: 失败了: $reason"
}

/** 声で操作 — 言葉の表 (voice.sh が読む)。 */
private const val VOICE_WORDS = """# 语音操作 — 词表 (简体中文)。由 voice.sh 读取。改写这里就能增加或更换说法。
#
# 写法:
#   键 = 说法|另一种说法|…            用"|"隔开，从上往下看
#   放一个 * 就能取到那个位置的话 (打开* -> "打开地图" 取到 "地图")
#   *X* 取 X 两边的话 (*提醒我* -> "明天7点提醒我倒垃圾" 取到 "明天7点 倒垃圾")
#   键 =                              右边留空，这个说法就不再起作用
#   比较时忽略标点、空格和英文大小写
#   这里没有的键，使用自带词表的值 (删掉一行就回到原来的说法)
#   以 say_ / msg_ / fmt_ / title_ 开头的行是要说出或显示的句子，{…} 里会填入值
#
# 不说话也能试: sh ~/.z2term/macros/voice.sh try 下一首
# "自己定的话 = 命令" 写在 ~/.z2term/voice/commands.txt (比这个词表先看)。

# --- 听取的话 ---
ai = 问ai*|问问ai*|ai*|提问*|问一下*
media_next = 下一首|下一曲|下一个|跳过|切歌|下一首歌
media_previous = 上一首|上一曲|上一个|上一首歌
media_pause = 暂停|停止|停|别放了|暂停音乐|停止播放
media_play = 播放|继续|继续播放|放音乐|播放音乐
volume_up = 调高音量|音量调高|音量调大|调大音量|大声点|声音大一点|音量大一点|大声一点
volume_down = 调低音量|音量调低|音量调小|调小音量|小声点|声音小一点|音量小一点|小声一点
volume_mute = 静音|关掉声音
volume_unmute = 取消静音|打开声音|恢复声音
time = 现在几点|几点了|现在几点了|时间|现在时间|几点|现在是几点
date = 今天几号|今天是几号|今天星期几|星期几|日期|今天的日期|今天是星期几
battery = 电量|电池|还有多少电|电池电量|还剩多少电
steps = 步数|今天的步数|今天走了多少步|走了多少步|我走了多少步
torch_on = 打开手电筒|手电筒|开手电筒|打开闪光灯
torch_off = 关闭手电筒|关手电筒|关掉手电筒|关闭闪光灯
screenshot = 截图|截屏|截个图
home = 主屏幕|回到主屏幕|桌面|回到桌面
news = 新闻|读新闻|今天的新闻|最新消息|念一下新闻|rss
weather = 天气|今天天气|今天的天气|天气怎么样|今天天气怎么样
weather_tomorrow = 明天天气|明天的天气|明天天气怎么样|明天的天气怎么样
memo_read = 读备忘录|读一下备忘|看备忘录|我的备忘|念一下备忘
memo_add = 备忘*|记一下*|记下*|*记下来
remind_list = 提醒列表|我的提醒|有什么提醒|日程|今天的日程|有哪些提醒
remind = 提醒我*|*提醒我*|*提醒我|设置提醒*|设个提醒*
wake = *叫醒我|*叫我起床|*点叫我|把闹钟定在*|闹钟定在*
help = 你能做什么|我能说什么|帮助|能做什么
timer = 设一个*的计时器|设个*的计时器|*的计时器|*计时器|*计时|计时*|倒计时*|定时*
navi = 导航到*|导航去*|去*怎么走|带我去*|我想去*|到*的路线
navi_ask = 导航|开始导航|带路
place_phone = 查一下*的电话号码|*的电话号码|查一下*的电话
call = 给*打电话|打电话给*|打给*|呼叫*
play = 播放*|放一首*|我想听*|来一首*
open = 打开*|启动*|开启*
# 用类别名打开 (设备的默认应用)。例: 打开浏览器
open_browser = 浏览器|网页
open_maps = 地图
open_mail = 邮件|电子邮件
open_camera = 相机|照相机
open_settings = 设置
open_calendar = 日历
open_music = 音乐
open_gallery = 相册|图库|照片
open_contacts = 联系人|通讯录
open_dialer = 电话|拨号
open_messages = 短信|信息
open_calculator = 计算器
# 应用的别名。说了也打不开时写上: 别名:应用名称|别名:应用名称
app_names =

# 带数字的音量 ("音量调低3格"、"音量调到10"、"音量百分之五十"、"音量最大") — 只要包含就算
volume_word = 音量|声音
volume_up_word = 调高|调大|大|增加
volume_down_word = 调低|调小|小|减少|降低
volume_max_word = 最大
volume_min_word = 最小
volume_percent_word = %|百分之
volume_mute_word = 静音
volume_unmute_word = 取消

# 时间长度和时刻 (计时器·提醒·闹钟)
unit_hour = 个小时|小时|个钟头|钟头
unit_minute = 分钟|分
unit_second = 秒钟|秒
unit_day = 天
when_before =
when_after = 以后|之后|后
clock_at =
clock_hour = 点钟|点
clock_minute = 分
clock_half = 半
clock_am = 早上|上午|凌晨|早晨
clock_pm = 下午|晚上|傍晚|中午
day_today = 今天
day_tomorrow = 明天
day_after_tomorrow = 后天
day_daily = 每天
day_weekdays = 工作日|每个工作日
day_every = 每
wd_mon = 星期一|周一|礼拜一
wd_tue = 星期二|周二|礼拜二
wd_wed = 星期三|周三|礼拜三
wd_thu = 星期四|周四|礼拜四
wd_fri = 星期五|周五|礼拜五
wd_sat = 星期六|周六|礼拜六
wd_sun = 星期日|星期天|周日|礼拜天
# 去掉"什么时候"之后，留在正文旁边的连接词
remind_filler = 的|在|要|去|我|记得

# --- 说出或显示的句子 ---
fmt_spaces = no
fmt_sentence_end = 。
title_app = 语音操作
title_navi = 导航
title_ai = AI
title_timer = 计时器
title_news = 新闻
title_memo = 备忘
title_remind = 提醒
title_call = 电话
title_help = 可以说的话
title_rule_voice = 语音操作
title_rule_voice_locked = 语音操作（锁屏时）
title_rule_timer = 语音计时器
title_rule_steps = 计步 (语音操作用)
fmt_clock = {h}点{m}分
fmt_clock_oclock = {h}点
fmt_weekdays = 一|二|三|四|五|六|日
fmt_months = 1|2|3|4|5|6|7|8|9|10|11|12
say_time = 现在是{clock}
say_date = 今天是{month}月{day}日，星期{weekday}
say_battery = 电量还有百分之{n}。{charging}
say_charging = 正在充电。
say_steps = 今天走了{n}步
say_steps_none = 还没有记录到步数。需要"身体活动"权限和计步规则
say_news = 最新的新闻。{titles}
say_news_none = 没有新的新闻
fmt_weather_lang = zh-cn
say_weather = 现在的天气是{desc}，气温{temp}度
say_weather_tomorrow = 明天{desc}，最高{hi}度，最低{lo}度，降雨概率百分之{rain}
say_weather_fail = 没能获取天气
say_memo_saved = 记下了
say_memo_recent = 最近的备忘。{memos}
say_memo_none = 没有备忘
say_remind_ok = 提醒已设置。{res}
say_remind_fail = 没能设置。{res}
say_remind_unclear = 没听清什么时候提醒什么。比如可以说：明天早上7点提醒我倒垃圾
say_remind_missing = 还没有安装提醒的宏。请在终端里运行 z2-macro install remind
fmt_wake_label = 起床时间
say_wake = 该起床了
say_wake_set = {clock}叫醒你
say_wake_unclear = 没听清几点叫醒你
fmt_unit_hour = 小时
fmt_unit_minute = 分钟
fmt_unit_second = 秒
fmt_timer_label = {n}{unit}
say_timer_set = 已设置{label}的计时器
say_timer_done = {label}到了
say_timer_unclear = 没听清计时器要多长
say_call_missing = 没找到{who}的号码
msg_call_missing = {file} 里没有"{who}"。请每行写一个"名字 号码"。
say_open_missing = 没找到叫{name}的应用
say_help = 已经把可以说的话放到通知里了
say_locked = 请解锁手机
msg_help = 时间·日期·电量·步数 / 音量调低3格·音量百分之五十 / 下一首·暂停 / 3分钟的计时器·7点叫醒我 / 明天早上7点提醒我倒垃圾·我的提醒 / 新闻·天气·明天天气 / 记一下买牛奶·读备忘录 / 打开手电筒·截图 / 打开… / 给…打电话 / 导航到…·…的电话号码 / AI … (用 voice.sh ai 开启) / 改说法: voice.sh words
msg_unknown = 没听懂: {text}
msg_locked = 解锁后继续执行“{text}”
msg_navi_where = 请说出目的地
msg_navi_hint = 目的地 (地名)
msg_ai_off = AI 已关闭。按"复制"并粘贴到终端，就会使用 {ai} (sh ~/.z2term/macros/voice.sh ai {ai})
msg_ai_off_none = AI 已关闭。请先安装并登录 codex / claude / agy 中的一个，再在终端里用 sh ~/.z2term/macros/voice.sh ai codex 这样选择
msg_ai_missing = 找不到 {ai}。请安装并登录，或用 sh ~/.z2term/macros/voice.sh ai 选择别的 AI
msg_ai_asking = 正在问 AI ({ai})…
msg_ai_noanswer = 没有收到回答 (记录: {log})
msg_ai_usage = ai: 请指定 off | codex | claude | agy 之一
msg_ai_how = AI 已关闭。要使用: sh ~/.z2term/macros/voice.sh ai {ai}  (codex / claude / agy。只有用提示词"AI"或"提问"开头时才会问)。模型: sh ~/.z2term/macros/voice.sh model <名字> (留空 = 该 AI 的默认)
fmt_ai_prompt = (回答会被朗读出来。不要使用命令或文件，用中文回答 2 到 3 句，不要用符号或列表。)
msg_setup_done = 已登记语音操作的规则 (已有的没有重复添加):
msg_setup_steps = 计步规则 (steps:reach=1000000) 只是让计步器保持运行，以便用语音询问今天的步数。这个条件不会达到，不会执行任何操作。
msg_words_file = 词表: {file}  (改写它就能增加说法。用 sh ~/.z2term/macros/voice.sh words 打开)"""
