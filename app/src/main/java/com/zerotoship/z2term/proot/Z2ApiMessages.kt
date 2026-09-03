package com.zerotoship.z2term.proot

import com.zerotoship.z2term.icon.IconStore
import com.zerotoship.z2term.tile.TileStore

/**
 * `z2-*` CLI が**端末に出す文言**（先頭のヘルプコメント・usage・メッセージ）を言語ごとに持つ。
 *
 * **なぜ分けたか**: アプリの画面は `values/` と `values-ja/` で日英そろっているのに、
 * 端末側の CLI だけが日本語ベタ書きで、英語話者には使えない状態だった（GitHub 直配布なので
 * README を読むのは英語話者の方が多い）。`z2help` は 1 本だけ `lang` で出し分けていたので、
 * その方式を全体へ広げる。
 *
 * **ロジックは 1 つのまま**にするのが要点。スクリプト全体を 2 セット持つと、片方だけ直して
 * 挙動がズレる（そして端末でしか気付けない）。ここで持つのは**文言だけ**で、
 * [z2ApiScripts] 側は言語に関係なく同じ制御フローを組み立てる。
 *
 * ⚠ 0.8.421 までは `en: Boolean` の 2 値だった（= 3 言語目を置く場所が無かった）。
 * 今は言語コードを受け取り、[CliText] が文言を選ぶ。**挙げていない言語は英語**へ落ちる。
 *
 * ヘルプは行頭 `#` ・末尾改行つきの**完成形**で持つ（`trimMargin` の外で連結するため、
 * マージン `|` の剥がし漏れが起きない）。[d] はシェルの `$`。
 */
internal class Z2ApiMsg(lang: String, private val d: String) {

    /**
     * 言語ごとの文言を選ぶ道具。⭐ **3 言語目はここを通して足す** —
     * `t(en = "…", ja = "…")` の後ろへ変わり値を足す。詳しくは [CliText] と
     * [com.zerotoship.z2term.settings.AppLanguages]。
     */
    private val t = CliText(lang)

    // --- z2-usb ---

    val usbHelp: String = t(
        en = """
        |# z2-usb list              … list USB devices connected to the phone
        |# z2-usb allow [number]    … ask Android to let Linux programs use a device
        |# Run allow once after connecting a device. Permission lasts until it is unplugged.
    """.trimMargin(),
        ja = """
        |# z2-usb list              … スマホに接続された USB 機器を一覧表示
        |# z2-usb allow [番号]      … Linux プログラムから使う許可を Android に求める
        |# 機器を挿した後に allow を 1 回実行します。許可は抜くまで有効です。
    """.trimMargin(),
        "zh-CN" to """
        |# z2-usb list              … 列出连接到手机的 USB 设备
        |# z2-usb allow [编号]      … 请求 Android 允许 Linux 程序使用该设备
        |# 插入设备后执行一次 allow。权限在拔出设备前一直有效。
    """.trimMargin(),
        "zh-TW" to """
        |# z2-usb list              … 列出連線到手機的 USB 裝置
        |# z2-usb allow [編號]      … 請求 Android 允許 Linux 程式使用該裝置
        |# 插入裝置後執行一次 allow。權限在拔出裝置前一直有效。
    """.trimMargin()
    )

    val usbUsage: String = t(
        en = "usage: z2-usb list | z2-usb allow [number|path|VID:PID]",
        ja = "usage: z2-usb list | z2-usb allow [番号|パス|VID:PID]",
        "zh-CN" to "usage: z2-usb list | z2-usb allow [编号|路径|VID:PID]",
        "zh-TW" to "usage: z2-usb list | z2-usb allow [編號|路徑|VID:PID]"
    )

    val usbNoDevices: String = t(
        en = "No USB device is connected to the phone.",
        ja = "スマホに USB 機器が接続されていません。",
        "zh-CN" to "手机上没有连接 USB 设备。",
        "zh-TW" to "手機上沒有連線 USB 裝置。"
    )

    val usbNeedSelector: String = t(
        en = "More than one USB device is connected; specify its number from z2-usb list.",
        ja = "USB 機器が複数あります。z2-usb list の番号を指定してください。",
        "zh-CN" to "连接了多个 USB 设备。请指定 z2-usb list 中的编号。",
        "zh-TW" to "連線了多個 USB 裝置。請指定 z2-usb list 中的編號。"
    )

    fun usbPermissionRequested(path: String): String = t(
        en = "Approve USB access on the Android screen: $path",
        ja = "Android の画面で USB アクセスを許可してください: $path",
        "zh-CN" to "请在 Android 屏幕上允许 USB 访问：$path",
        "zh-TW" to "請在 Android 螢幕上允許 USB 存取：$path"
    )

    fun usbAlreadyAllowed(path: String): String = t(
        en = "USB access is already allowed: $path",
        ja = "USB アクセスは許可済みです: $path",
        "zh-CN" to "USB 访问已经允许：$path",
        "zh-TW" to "USB 存取已經允許：$path"
    )

    fun usbNotFound(selector: String): String = t(
        en = "USB device not found: $selector",
        ja = "USB 機器が見つかりません: $selector",
        "zh-CN" to "找不到 USB 设备：$selector",
        "zh-TW" to "找不到 USB 裝置：$selector"
    )

    fun usbDeviceLine(
        index: Int,
        path: String,
        vendorId: Int,
        productId: Int,
        productName: String,
        allowed: Boolean
    ): String {
        val state = if (allowed) {
            t(en = "allowed", ja = "許可済み", "zh-CN" to "已允许", "zh-TW" to "已允許")
        } else {
            t(en = "needs allow", ja = "要許可", "zh-CN" to "需要允许", "zh-TW" to "需要允許")
        }
        val name = productName.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        return "%d  %04x:%04x  %s  %s%s".format(index, vendorId, productId, state, path, name)
    }

    /** タイルの枠数 ([TileStore.COUNT])。文言へ数を**書き写さない** — 増やしたときにここだけ古くなる。 */
    private val tiles = TileStore.COUNT

    // --- z2-notify ---

    val notifyHelp: String = t(
        en = """
        |# z2-notify [-h] [-n NAME] [-c TEXT] [-b LABEL]... "title" "text"  /  z2-notify [-h] "text"
        |#   -h / --high / --banner : show it as a banner (heads-up) at the top of the screen
        |#   -b <label>             : add a reply button (up to 3). Pressing one appends
        |#                            notify_action to ~/.z2term/events.jsonl
        |#                            ({"event":"notify_action","name":NAME,"action":LABEL})
        |#   -n <name>              : an identifier for this notification (to tell replies apart)
        |#   -c <text>              : add a "Copy" button that puts <text> on the clipboard.
        |#                            Use this instead of z2-clip set when the macro runs in the
        |#                            background: Android 10+ only lets the app in front write to
        |#                            the clipboard, so an unattended z2-clip set is dropped.
    """.trimMargin(),
        ja = """
        |# z2-notify [-h] [-n 名前] [-c 文字列] [-b ラベル]... "タイトル" "本文"  /  z2-notify [-h] "本文"
        |#   -h / --high / --banner : 画面上部にバナー(ヘッドアップ)表示する
        |#   -b <ラベル>            : 返事のボタンを付ける (最大 3 つ)。押すと
        |#                            ~/.z2term/events.jsonl に notify_action が 1 行増える
        |#                            ({"event":"notify_action","name":名前,"action":ラベル})
        |#   -n <名前>              : その通知の識別名 (どの問いかけへの返事か区別する用)
        |#   -c <文字列>            : 「コピー」ボタンを付ける (押すとその文字列がクリップボードへ)。
        |#                            裏で走るマクロは z2-clip set ではなくこちらを使う —
        |#                            Android 10+ は前面のアプリしかクリップボードに書けないので、
        |#                            見ていないときの z2-clip set は黙って捨てられる。
    """.trimMargin(),
        "zh-CN" to """
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
    """.trimMargin(),
        "zh-TW" to """
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
    )

    val notifyUsage: String =
        t(
            en = "usage: z2-notify [-h] [-n name] [-c text] [-b label]... <title> [text]",
            ja = "usage: z2-notify [-h] [-n 名前] [-c 文字列] [-b ラベル]... <タイトル> [本文]",
            "zh-CN" to "usage: z2-notify [-h] [-n 名称] [-c 文本] [-b 标签]... <标题> [正文]",
            "zh-TW" to "usage: z2-notify [-h] [-n 名稱] [-c 文字] [-b 標籤]... <標題> [正文]"
        )

    // --- 単機能のもの (ヘルプ 1〜2 行) ---

    val toastHelp: String = t(
        en = """
        |# z2-toast <message> … a short message at the bottom of the screen (a toast).
        |# It fades by itself and leaves nothing behind. Use z2-notify when it has to stay,
        |# or when you want a button to press.
    """.trimMargin(),
        ja = """
        |# z2-toast <メッセージ> … 画面下に短いメッセージを出す (トースト)。
        |# 数秒で自分で消えて、何も残りません。残したい・押させたいときは z2-notify を使います。
    """.trimMargin(),
        "zh-CN" to """
        |# z2-toast <消息> … 在屏幕下方显示一条短消息 (吐司)。
        |# 它过几秒会自己消失，什么都不留下。想留下来、或者想让人按一下时，请用 z2-notify。
    """.trimMargin(),
        "zh-TW" to """
        |# z2-toast <訊息> … 在螢幕下方顯示一條短訊息 (Toast)。
        |# 它過幾秒會自己消失，什麼都不留下。想留下來、或者想讓人按一下時，請用 z2-notify。
    """.trimMargin()
    )

    val shareHelp: String = t(
        en = """
        |# z2-share <text> … hand text to Android's share sheet (send it on to another app).
        |# All arguments are joined into one body. Which app it goes to is chosen on screen,
        |# so this needs someone to be there — it is not for a macro running unattended.
    """.trimMargin(),
        ja = """
        |# z2-share <テキスト> … Android の共有メニューに渡す (他アプリへ送る)。
        |# 引数はつなげて 1 つの本文にします。送り先は画面で選ぶので、人がいるときのものです
        |# (裏で走らせるマクロ向きではありません)。
    """.trimMargin(),
        "zh-CN" to """
        |# z2-share <文本> … 交给 Android 的分享菜单 (转交给其他应用)。
        |# 所有参数会拼成一段正文。发给哪个应用要在屏幕上选，所以这是给有人在的时候用的
        |# (不适合在后台自己跑的宏)。
    """.trimMargin(),
        "zh-TW" to """
        |# z2-share <文字> … 交給 Android 的分享選單 (轉交給其他應用程式)。
        |# 所有參數會拼成一段正文。發給哪個應用程式要在螢幕上選，所以這是給有人在的時候用的
        |# (不適合在背景自己跑的巨集)。
    """.trimMargin()
    )

    val openHelp: String = t(
        en = """
        |# z2-open <url|path> … open it with the default app (https://… or /sdcard/…).
        |# Which app opens it is Android's choice; z2term only hands it over.
        |# A path is a path on the **phone** (/sdcard/…), not inside the distro.
    """.trimMargin(),
        ja = """
        |# z2-open <URL かパス> … 既定のアプリで開く (https://… も /sdcard/… も)。
        |# どのアプリで開くかを決めるのは Android で、z2term は渡すだけです。
        |# パスは**スマホ側**のパス (/sdcard/…) で、ディストロの中のパスではありません。
    """.trimMargin(),
        "zh-CN" to """
        |# z2-open <URL 或路径> … 用默认应用打开 (https://… 和 /sdcard/… 都行)。
        |# 用哪个应用打开是 Android 决定的，z2term 只负责递过去。
        |# 路径是**手机一侧**的路径 (/sdcard/…)，不是发行版里面的路径。
    """.trimMargin(),
        "zh-TW" to """
        |# z2-open <URL 或路徑> … 用預設應用程式開啟 (https://… 和 /sdcard/… 都行)。
        |# 用哪個應用程式開啟是 Android 決定的，z2term 只負責遞過去。
        |# 路徑是**手機一側**的路徑 (/sdcard/…)，不是發行版裡面的路徑。
    """.trimMargin()
    )

    // --- z2-img ---

    // ⚠ ヘルプに**対応していない端末では化ける**ことを必ず書く。絵を出す手段は端末の外から
    //   見分けられない (ssh の先が kitty 対応かどうかは分からない) ので、止めるのではなく
    //   「そういうものだ」と先に伝えるしかない。qr.sh の -t と同じ立場。
    val imgHelp: String = t(
        en = """
        |# z2-img [-w COLS] [-r ROWS] [--clear] [-f] <file|->… … draw the picture in the terminal.
        |#   PNG / JPEG / WebP / GIF / BMP. Pass - to read one image from stdin.
        |#   -w <cols>  width in terminal columns (default: fit the terminal)
        |#   -r <rows>  height in terminal rows (with -w, the size is used as given)
        |#   --clear    remove every picture drawn so far
        |#   -f         send it even when stdout is not a terminal
        |# ⚠ Pictures only appear in a z2term tab, or in a terminal that speaks the kitty
        |#   graphics protocol. Anywhere else (over ssh, in a pager) you get gibberish.
        |# ⚠ The aspect ratio assumes a cell is twice as tall as it is wide. If it looks
        |#   squashed, tune it: Z2_IMG_ASPECT=0.45 z2-img photo.jpg (smaller = taller).
    """.trimMargin(),
        ja = """
        |# z2-img [-w 桁] [-r 行] [--clear] [-f] <ファイル|->… … 端末にそのまま絵を出す。
        |#   PNG / JPEG / WebP / GIF / BMP。- を渡すと標準入力から 1 枚読みます。
        |#   -w <桁>    横幅を桁数で指定 (既定: 端末の幅に収める)
        |#   -r <行>    高さを行数で指定 (-w と両方渡すとその寸法のまま)
        |#   --clear    出した絵を全部消す
        |#   -f         画面 (tty) でなくても送る
        |# ⚠ 絵が出るのは z2term のタブの中と、kitty graphics に対応した端末だけです。
        |#   それ以外 (ssh の先・ページャの中など) では意味不明な文字が流れます。
        |# ⚠ 縦横比は「1 マスの高さは幅の 2 倍」として計算します。潰れて見えるときは
        |#   Z2_IMG_ASPECT=0.45 z2-img photo.jpg のように調整してください (小さいほど縦長)。
    """.trimMargin(),
        "zh-CN" to """
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
    """.trimMargin(),
        "zh-TW" to """
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
    )

    val imgUsage: String = t(
        en = "usage: z2-img [-w cols] [-r rows] [--clear] [-f] <file|->...",
        ja = "usage: z2-img [-w 桁] [-r 行] [--clear] [-f] <ファイル|->...",
        "zh-CN" to "usage: z2-img [-w 列] [-r 行] [--clear] [-f] <文件|->...",
        "zh-TW" to "usage: z2-img [-w 欄] [-r 列] [--clear] [-f] <檔案|->..."
    )

    val imgNoFile: String = t(
        en = "cannot read the file:",
        ja = "ファイルが読めません:",
        "zh-CN" to "读不到这个文件：",
        "zh-TW" to "讀不到這個檔案："
    )

    // ⚠ 既定で止める。パイプやリダイレクトの先に APC を流すと、受け側には**壊れたバイト列**
    //   としか見えない (画像だと分かる手掛かりが残らない)。-f で通せるようにはしておく。
    val imgNotTty: String = t(
        en = "stdout is not a terminal, so nothing was sent (-f sends it anyway).",
        ja = "標準出力が画面ではないので送りませんでした (-f を付けると送ります)。",
        "zh-CN" to "标准输出不是终端，所以没有发送 (加 -f 可以照样发送)。",
        "zh-TW" to "標準輸出不是終端機，所以沒有傳送 (加 -f 可以照樣傳送)。"
    )

    val imgNoSize: String = t(
        en = "cannot read the pixel size; drawing it as a square:",
        ja = "画素数が読めないので正方形として出します:",
        "zh-CN" to "读不出像素尺寸，按正方形画出：",
        "zh-TW" to "讀不出像素尺寸，按正方形畫出："
    )

    val clipHelp: String = t(
        en = """
        |# z2-clip get        … print the clipboard to stdout
        |# z2-clip set [text] … put text (or stdin when omitted) on the clipboard
        |# ⚠ Android 10+ only lets the app in front (or the input method in use) touch the
        |#   clipboard. From a macro running in the background this is dropped without a word,
        |#   so use z2-notify -c <text> there: it adds a "Copy" button that always works.
    """.trimMargin(),
        ja = """
        |# z2-clip get        … クリップボードを標準出力へ
        |# z2-clip set [text] … text (無ければ標準入力) をクリップボードへ
        |# ⚠ Android 10+ は前面のアプリ (と使用中の入力方法) しかクリップボードを触れない。
        |#   裏で走るマクロからは黙って捨てられるので、そこでは z2-notify -c <文字列> を使う
        |#   (「コピー」ボタンが付き、押せば確実に入る)。
    """.trimMargin(),
        "zh-CN" to """
        |# z2-clip get        … 把剪贴板内容输出到标准输出
        |# z2-clip set [文本] … 把文本 (不给则读标准输入) 放进剪贴板
        |# ⚠ Android 10+ 只让前台应用 (和正在使用的输入法) 碰剪贴板。
        |#   从后台运行的宏里调用会被默默丢掉，那种场合请用 z2-notify -c <文本>
        |#   (它会加一个“复制”按钮，按下就一定能进去)。
    """.trimMargin(),
        "zh-TW" to """
        |# z2-clip get        … 把剪貼簿內容輸出到標準輸出
        |# z2-clip set [文字] … 把文字 (不給則讀標準輸入) 放進剪貼簿
        |# ⚠ Android 10+ 只讓前景應用程式 (和正在使用的輸入法) 碰剪貼簿。
        |#   從背景執行的巨集裡呼叫會被默默丟掉，那種場合請用 z2-notify -c <文字>
        |#   (它會加一個“複製”按鈕，按下就一定能進去)。
    """.trimMargin()
    )

    val batteryHelp: String =
        t(
            en = "# Print level / charging state as JSON ({\"level\":N,\"charging\":bool}).",
            ja = "# 残量/充電状態を JSON ({\"level\":N,\"charging\":bool}) で出力。",
            "zh-CN" to "# 以 JSON ({\"level\":N,\"charging\":bool}) 输出电量/充电状态。",
            "zh-TW" to "# 以 JSON ({\"level\":N,\"charging\":bool}) 輸出電量/充電狀態。"
        )

    val vibrateHelp: String =
        t(
            en = "# z2-vibrate [ms]  (default 200ms)",
            ja = "# z2-vibrate [ms]  (既定 200ms)",
            "zh-CN" to "# z2-vibrate [ms]  (默认 200ms)",
            "zh-TW" to "# z2-vibrate [ms]  (預設 200ms)"
        )

    val sayHelp: String =
        t(
            en = "# z2-say <text>       … speak with the device TTS (reads stdin when no argument)",
            ja = "# z2-say <text>       … 端末標準の TTS で読み上げ (引数無しなら標準入力を読む)",
            "zh-CN" to "# z2-say <text>       … 用设备自带的 TTS 朗读 (不带参数则读标准输入)",
            "zh-TW" to "# z2-say <text>       … 用裝置自帶的 TTS 朗讀 (不帶參數則讀標準輸入)"
        )

    val torchHelp: String =
        t(
            en = "# z2-torch on|off|toggle  (default toggle). Prints the resulting state (on/off).",
            ja = "# z2-torch on|off|toggle  (既定 toggle)。結果の点灯状態 (on/off) を出力。",
            "zh-CN" to "# z2-torch on|off|toggle  (默认 toggle)。输出结果的点亮状态 (on/off)。",
            "zh-TW" to "# z2-torch on|off|toggle  (預設 toggle)。輸出結果的點亮狀態 (on/off)。"
        )

    val mediaHelp: String =
        t(
            en = "# z2-media play|pause|playpause|next|previous|stop  (default playpause)",
            ja = "# z2-media play|pause|playpause|next|previous|stop  (既定 playpause)",
            "zh-CN" to "# z2-media play|pause|playpause|next|previous|stop  (默认 playpause)",
            "zh-TW" to "# z2-media play|pause|playpause|next|previous|stop  (預設 playpause)"
        )

    val volumeHelp: String =
        t(
            en = "# z2-volume up|down|mute|unmute|N|N%   Media volume. Prints the resulting current/max.",
            ja = "# z2-volume up|down|mute|unmute|N|N%   メディア音量を操作。結果の current/max を出力。",
            "zh-CN" to "# z2-volume up|down|mute|unmute|N|N%   操作媒体音量。输出结果的 current/max。",
            "zh-TW" to "# z2-volume up|down|mute|unmute|N|N%   操作媒體音量。輸出結果的 current/max。"
        )

    val sensorHelp: String =
        t(
            en = "# z2-sensor light|accel|proximity  (default light). Reads one sample and returns JSON.",
            ja = "# z2-sensor light|accel|proximity  (既定 light)。センサーを 1 回読んで JSON で返す。",
            "zh-CN" to "# z2-sensor light|accel|proximity  (默认 light)。读一次传感器并以 JSON 返回。",
            "zh-TW" to "# z2-sensor light|accel|proximity  (預設 light)。讀一次感測器並以 JSON 返回。"
        )

    val intentHelp: String = t(
        en = """
        |# z2-intent [-a ACTION] [-d URI] [-t MIME] [-p PKG] [-n PKG/CLS] [-f FLAGS]
        |#           [--es K V] [--ez K true|false] [--ei K N] [--broadcast|--service]
        |# Fire any Android Intent (startActivity by default). A leading non-flag argument is the ACTION.
    """.trimMargin(),
        ja = """
        |# z2-intent [-a ACTION] [-d URI] [-t MIME] [-p PKG] [-n PKG/CLS] [-f FLAGS]
        |#           [--es K V] [--ez K true|false] [--ei K N] [--broadcast|--service]
        |# 任意の Android Intent を発火 (既定は startActivity)。先頭の非フラグ引数は ACTION。
    """.trimMargin(),
        "zh-CN" to """
        |# z2-intent [-a ACTION] [-d URI] [-t MIME] [-p PKG] [-n PKG/CLS] [-f FLAGS]
        |#           [--es K V] [--ez K true|false] [--ei K N] [--broadcast|--service]
        |# 触发任意 Android Intent (默认是 startActivity)。开头的非选项参数即 ACTION。
    """.trimMargin(),
        "zh-TW" to """
        |# z2-intent [-a ACTION] [-d URI] [-t MIME] [-p PKG] [-n PKG/CLS] [-f FLAGS]
        |#           [--es K V] [--ez K true|false] [--ei K N] [--broadcast|--service]
        |# 觸發任意 Android Intent (預設是 startActivity)。開頭的非選項參數即 ACTION。
    """.trimMargin()
    )

    val stateHelp: String = t(
        en = """
        |# z2-state            … the device's current state, all of it, as JSON
        |# z2-state <key>      … just that value, raw (drops straight into a test)
        |# keys: screen(on/off) locked idle charging plug(ac/usb/wireless/none) level temp(C)
        |#       wifi ssid ringer(normal/vibrate/silent) airplane headset bt_audio volume volume_max
        |# e.g. [ "${d}(z2-state charging)" = "true" ] && echo charging
    """.trimMargin(),
        ja = """
        |# z2-state            … 今の端末の状態をまとめて JSON で返す
        |# z2-state <キー>     … その値だけを生で返す (条件式にそのまま書ける)
        |# キー: screen(on/off) locked idle charging plug(ac/usb/wireless/none) level temp(℃)
        |#       wifi ssid ringer(normal/vibrate/silent) airplane headset bt_audio volume volume_max
        |# 例: [ "${d}(z2-state charging)" = "true" ] && echo 充電中
    """.trimMargin(),
        "zh-CN" to """
        |# z2-state            … 把设备现在的状态整个以 JSON 返回
        |# z2-state <键>       … 只返回那个值的原始形式 (可以直接写进条件式)
        |# 键: screen(on/off) locked idle charging plug(ac/usb/wireless/none) level temp(℃)
        |#       wifi ssid ringer(normal/vibrate/silent) airplane headset bt_audio volume volume_max
        |# 例: [ "${d}(z2-state charging)" = "true" ] && echo 充电中
    """.trimMargin(),
        "zh-TW" to """
        |# z2-state            … 把裝置現在的狀態整個以 JSON 返回
        |# z2-state <鍵>       … 只返回那個值的原始形式 (可以直接寫進條件式)
        |# 鍵: screen(on/off) locked idle charging plug(ac/usb/wireless/none) level temp(℃)
        |#       wifi ssid ringer(normal/vibrate/silent) airplane headset bt_audio volume volume_max
        |# 例: [ "${d}(z2-state charging)" = "true" ] && echo 充電中
    """.trimMargin()
    )

    // --- z2-ask ---

    val askHelp: String = t(
        en = """
        |# z2-ask [-t SEC] [-H HINT] [-d DEFAULT] <question>
        |#   Ask the person a question and print their answer on stdout.
        |#   The question arrives as a notification with a **reply field**, so it can be
        |#   answered from the shade without opening the app (a macro running in the
        |#   background can ask too).
        |#   -t <sec>   how long to wait for an answer (default 300 = 5 min)
        |#   -H <hint>  label shown in the reply field
        |#   -d <text>  suggested answer (shown in the notification)
        |# Dismissing the notification without answering, or running out of time, exits
        |# non-zero and prints nothing — so you can write "or give up":
        |#   name=${d}(z2-ask "Branch name?") || exit 1
        |# Compare with z2-notify -b <label>, which only offers the choices you prepared.
    """.trimMargin(),
        ja = """
        |# z2-ask [-t 秒] [-H ヒント] [-d 既定] <質問>
        |#   人に質問して、答えを標準出力へ返す。
        |#   質問は**返信欄つきの通知**で届くので、アプリを開かずシェードのまま答えられる
        |#   (裏で走っているマクロからも聞ける)。
        |#   -t <秒>     答えを待つ時間 (既定 300 = 5 分)
        |#   -H <ヒント> 返信欄に出す見出し
        |#   -d <文字列> 答えの候補 (通知に出す)
        |# 答えずに通知を消したとき・時間切れのときは**非ゼロ終了**で何も出さないので、
        |# 「答えなければ諦める」がそのまま書ける:
        |#   name=${d}(z2-ask "ブランチ名は?") || exit 1
        |# 用意した選択肢から選ばせるだけなら z2-notify -b <ラベル> の方が向いている。
    """.trimMargin(),
        "zh-CN" to """
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
    """.trimMargin(),
        "zh-TW" to """
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
    )

    val askUsage: String =
        t(
            en = "usage: z2-ask [-t sec] [-H hint] [-d default] <question>",
            ja = "usage: z2-ask [-t 秒] [-H ヒント] [-d 既定] <質問>",
            "zh-CN" to "usage: z2-ask [-t 秒] [-H 提示] [-d 默认值] <问题>",
            "zh-TW" to "usage: z2-ask [-t 秒] [-H 提示] [-d 預設值] <問題>"
        )

    // --- z2-screen ---

    val screenHelp: String = t(
        en = """
        |# z2-screen keepon <N|Ns|Nm|Nh> … stop the screen from turning off by itself, for that long
        |# z2-screen keepon off          … put it back now, without waiting for the deadline
        |# z2-screen status              … current state as JSON
        |#   (allowed / keepon / timeout_ms / until / remaining_sec / original_ms)
        |# This changes the OS-wide "screen timeout" setting, so it holds even when the app is
        |# in the background. It is NOT the toolbar's screen lock, which only lasts while the
        |# app is on screen — that one is left untouched.
        |# The original value is saved and always written back at the deadline (even if the app
        |# is killed or the device reboots). Max 24h in one go.
        |# Needs "modify system settings" (Settings > screen timeout > allow).
        |# e.g. z2-screen keepon 1h; make; z2-screen keepon off
    """.trimMargin(),
        ja = """
        |# z2-screen keepon <N|Ns|Nm|Nh> … その時間だけ、画面が自分で消えないようにする
        |# z2-screen keepon off          … 期限を待たずに今すぐ元へ戻す
        |# z2-screen status              … 今の状態を JSON で
        |#   (allowed / keepon / timeout_ms / until / remaining_sec / original_ms)
        |# これは OS 全体の「画面消灯までの時間」を変えるので、アプリを畳んでいても効く。
        |# ツールバーの🔅 (アプリを開いている間だけ消えない) とは別物で、そちらは触らない。
        |# 元の値は保存され、期限が来たら必ず書き戻す (アプリが落ちても・再起動しても)。
        |# 一度に掛けられるのは 24h まで。
        |# 「システム設定の変更」の許可が要ります (設定 › 画面の自動消灯 › 許可)。
        |# 例: z2-screen keepon 1h; make; z2-screen keepon off
    """.trimMargin(),
        "zh-CN" to """
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
    """.trimMargin(),
        "zh-TW" to """
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
    )

    val screenUsage: String =
        t(
            en = "usage: z2-screen keepon <N|Ns|Nm|Nh> | keepon off | status",
            ja = "usage: z2-screen keepon <N|Ns|Nm|Nh> | keepon off | status",
            "zh-CN" to "usage: z2-screen keepon <N|Ns|Nm|Nh> | keepon off | status",
            "zh-TW" to "usage: z2-screen keepon <N|Ns|Nm|Nh> | keepon off | status"
        )

    // --- z2-tile ---

    val tileHelp: String = t(
        en = """
        |# z2-tile set <1-$tiles> <macro.sh | command...> [--off <command...>] [-l <label>] [-i <drawing>]
        |#                           … put something on quick-settings tile 1-$tiles
        |# z2-tile add <1-$tiles>         … ask to put that slot on the panel (Android 13+)
        |# z2-tile list              … all $tiles slots as TSV (slot / label / command; '-' = empty)
        |# z2-tile clear <1-$tiles|all>   … empty a slot
        |#
        |# What you assign
        |#   Either **the file name of a macro** in ~/.z2term/macros/ or **a command** run as
        |#   typed — which one it is, is worked out from the name.
        |#   A macro may take arguments ('remind.sh ask'): the first word decides whether this is
        |#   a macro, the rest is passed to it. Two slots on the same macro end up with the same
        |#   label, so give them -l.
        |#   The command runs with Z2_TILE=<slot> (and Z2_TILE_MACRO for a macro) in the environment.
        |#   ⚠ A name ending in .sh that is not in ~/.z2term/macros/ is rejected here — as a
        |#     command it would be looked up in PATH (which does not include the macro folder),
        |#     so the tile would do nothing at all and only say so in tile/run.log.
        |#
        |# What a tap does
        |#   Tap to run it, tap again to stop (same deal as the widget's buttons).
        |#   The tile looks "on" while it runs (the colour is the OS accent, not ours).
        |#   With --off you get **two commands**: tapping alternates between them and the tile
        |#   stays "on"-looking while it is on. Use it where turning off is its own command
        |#   (z2-torch on / off).
        |#   ⚠ That on/off is only what the app remembers — running z2-torch off in the terminal
        |#     instead leaves the tile showing "on". (z2-screen keepon is the exception: the app
        |#     holds that state for real, so its tile follows the terminal.)
        |#
        |# Putting it on the panel
        |#   On Android 13+ `set` asks you right away whether to put the tile on the panel, and
        |#   that dialog carries **the name and icon you just assigned**. Say no, nothing is placed.
        |#   There are exactly $tiles slots: the number is fixed in the manifest and cannot grow at
        |#   runtime. Slots you have not assigned anything to do not show up in the edit screen
        |#   at all, so having $tiles of them costs you nothing.
        |#   ⚠ It only appears while z2term is in front, and only one can be asked at a time — a
        |#     macro that assigns two slots in a row will only ask about the first. Use
        |#     z2-tile add <slot> afterwards for the rest.
        |#   ⚠ Placing a tile is still your call — Android does not let an app put its own tiles
        |#     there. From the pencil/edit screen of the quick settings panel, look for
        |#     **z2term <slot>**.
        |#   ⚠ That list shows the manifest name and icon, not the ones you assigned (Android has
        |#     no way to change them at runtime), so z2-tile list tells you which number is which.
        |#
        |# The drawing (-i)
        |#   A slot gets a matching icon by itself where the name gives it away (remind.sh -> a
        |#   clock), and z2-icon replaces it with anything you like — once you do, it is left alone.
        |#   -i settles the drawing **before** that dialog appears, so a single line decides the
        |#   name and the icon together (z2-icon sample lists the names you can pass).
        |#   ⚠ An unknown name is refused **before anything is assigned** — a slot that is set but
        |#     wearing the wrong drawing is harder to notice than one that was never set.
        |#
        |# e.g. z2-tile set 1 backup.sh
        |#      z2-tile set 2 'z2-screen keepon 1h' -l "no sleep"
        |#      z2-tile set 3 z2-torch on --off z2-torch off -l torch
        |#      z2-tile set 4 backup.sh -l backup -i sync
    """.trimMargin(),
        ja = """
        |# z2-tile set <1-$tiles> <マクロ.sh | コマンド...> [--off <コマンド...>] [-l <表示名>] [-i <絵の名前>]
        |#                           … クイック設定タイル 1〜$tiles に割り当てる
        |# z2-tile add <1-$tiles>         … その枠をパネルに置いてよいか聞く (Android 13 以降)
        |# z2-tile list              … $tiles 枠すべてを TSV で (枠 / 表示名 / コマンド。'-' は空き)
        |# z2-tile clear <1-$tiles|all>   … 割り当てを消す
        |#
        |# 割り当てるもの
        |#   ~/.z2term/macros/ にある**マクロのファイル名**か、そのまま走らせる**コマンド**の
        |#   どちらでもよい (名前を見て自動で判別します)。
        |#   マクロには**引数を付けられます** ('remind.sh ask')。マクロかどうかは**先頭の語**で
        |#   決まり、残りはそのままマクロへ渡ります。同じマクロを 2 枠に置くと表示名が同じに
        |#   なるので -l を付けてください。
        |#   実行時、環境変数 Z2_TILE に枠番号 (マクロなら Z2_TILE_MACRO も) が入ります。
        |#   ⚠ .sh で終わるのに ~/.z2term/macros/ に無い名前は**ここで弾きます** — コマンド扱いに
        |#     なると PATH (マクロ置き場は入っていません) から探されて見つからず、タイルは押しても
        |#     無反応・理由は tile/run.log にしか出ない、という壊れ方をするためです。
        |#
        |# 押したときの動き
        |#   押すと実行、もう一度押すと停止 (ウィジェットのボタンと同じ約束)。
        |#   実行中はタイルが ON の見た目になります (色は OS のもの)。
        |#   --off を付けると**入 / 切の 2 コマンド**になり、押すたびに交互に走ります
        |#   (入の間タイルは ON の見た目)。切るのが別コマンドのもの (z2-torch on / off) 向けです。
        |#   ⚠ この入 / 切は**アプリが覚えているだけ**です。端末から直接 z2-torch off を打つと
        |#     タイルは入のままになります (z2-screen keepon だけは例外で、アプリが実態を
        |#     持っているので端末から切ってもタイルが揃います)。
        |#
        |# パネルに置く
        |#   Android 13 以降では、set したその場で**パネルに置いてよいか聞きます**。そのダイアログ
        |#   には**いま割り当てた名前とアイコン**が出るので、編集画面で当てものをせずに済みます。
        |#   断れば何も置きません。
        |#   枠はちょうど $tiles 個で、manifest で決め打ちのため実行中に増やせません。まだ割り当てて
        |#   いない枠は編集画面の一覧にも出ないので、$tiles 個あっても邪魔になりません。
        |#   ⚠ 出るのは z2term が前面にいるときだけで、**一度に 1 つ**しか聞けません — 2 枠まとめて
        |#     登録するマクロでは 1 つめしか聞かれないので、残りは z2-tile add <枠> で聞き直して
        |#     ください。
        |#   ⚠ 置くかどうかは**ご自身の判断**です — アプリが勝手に置くことは Android が禁じています。
        |#     クイック設定パネルの鉛筆(編集)から探すときの目印は **z2term <枠番号>** です。
        |#   ⚠ その一覧に出る名前とアイコンは manifest 決め打ちで、**割り当てた名前ではありません**
        |#     (実行中に差し替える手段が Android にありません)。どの番号が何かは z2-tile list で
        |#     見えます。
        |#
        |# アイコン (-i)
        |#   名前から分かるものは**アイコンが自動で付きます** (remind.sh なら時計)。z2-icon で
        |#   好きな絵に変えられ、一度変えたらそれ以降は自動で触りません。
        |#   -i を付けると、**上のダイアログが出る前に**絵まで決まります (1 行で名前も絵も
        |#   済みます)。名前は z2-icon sample の一覧から選びます。
        |#   ⚠ 無い名前を書いたときは**割り当てごと断ります** — 割り当てだけ済んで絵が違うほうが、
        |#     何も起きていない状態より気付きにくいためです。
        |#
        |# 例: z2-tile set 1 backup.sh
        |#     z2-tile set 2 'z2-screen keepon 1h' -l 消灯しない
        |#     z2-tile set 3 z2-torch on --off z2-torch off -l ライト
        |#     z2-tile set 4 backup.sh -l バックアップ -i sync
    """.trimMargin(),
        "zh-CN" to """
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
        |#   ⚠ 这个开 / 关**只是应用自己记着的**。如果直接在终端里敲 z2-torch off，
        |#     磁贴仍然显示为开 (只有 z2-screen keepon 是例外，因为应用持有真实状态，
        |#     从终端关掉磁贴也会跟着变)。
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
    """.trimMargin(),
        "zh-TW" to """
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
        |#   ⚠ 這個開 / 關**只是應用程式自己記著的**。如果直接在終端機裡敲 z2-torch off，
        |#     圖塊仍然顯示為開 (只有 z2-screen keepon 是例外，因為應用程式持有真實狀態，
        |#     從終端機關掉圖塊也會跟著變)。
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
    )

    val tileUsage: String =
        t(
            en = "usage: z2-tile set <1-$tiles> <macro.sh|command...> [--off <command...>] [-l label] " +
                "[-i drawing] | add <1-$tiles> | list | clear <1-$tiles|all>",
            ja = "usage: z2-tile set <1-$tiles> <マクロ.sh|コマンド...> [--off <コマンド...>] [-l 表示名] " +
                "[-i 絵の名前] | add <1-$tiles> | list | clear <1-$tiles|all>",
            "zh-CN" to "usage: z2-tile set <1-$tiles> <宏.sh|命令...> [--off <命令...>] [-l 显示名] " +
                "[-i 图案名] | add <1-$tiles> | list | clear <1-$tiles|all>",
            "zh-TW" to "usage: z2-tile set <1-$tiles> <巨集.sh|指令...> [--off <指令...>] [-l 顯示名] " +
                "[-i 圖案名] | add <1-$tiles> | list | clear <1-$tiles|all>"
        )

    /**
     * `z2-tile set -i <名前>` に無い絵の名前を書いたとき (0.8.357)。
     *
     * ⚠ **割り当てる前に断る**ので「割り当ては済んだ」とは書かない。半端に割り当てが残ると、
     * 打ち間違いに気付かないまま違う絵のタイルが置かれる。
     */
    fun tileNoSuchIcon(name: String): String =
        t(
            en = "z2-tile: no such drawing: $name (list them with z2-icon sample). Nothing was assigned.",
            ja = "z2-tile: その絵はありません: $name (一覧は z2-icon sample)。割り当ては行いませんでした。",
            "zh-CN" to "z2-tile: 没有这个图案: $name (列表见 z2-icon sample)。没有进行分配。",
            "zh-TW" to "z2-tile: 沒有這個圖案: $name (列表見 z2-icon sample)。沒有進行分配。"
        )

    /** `z2-tile add <枠>` で、割り当ての無い枠を指したとき。 */
    fun tileAddEmpty(n: Int): String =
        t(
            en = "z2-tile: slot $n has nothing on it yet (assign it first: z2-tile set $n <...>)",
            ja = "z2-tile: 枠 $n はまだ空です (先に z2-tile set $n <…> で割り当ててください)",
            "zh-CN" to "z2-tile: 位 $n 还是空的 (请先用 z2-tile set $n <…> 分配)",
            "zh-TW" to "z2-tile: 位 $n 還是空的 (請先用 z2-tile set $n <…> 分配)"
        )

    /**
     * 追加を頼めなかったとき。⚠ **Android 12 以前にはこの口が無い** (タイルの追加を頼む API は
     * Android 13 から) し、**z2term が前面にいない**ときも OS が断る。どちらも
     * **割り当て自体は済んでいる**ので、並べ方だけを案内する。
     */
    val tileAddUnsupported: String =
        t(
            en = "z2-tile: cannot ask to add the tile here (needs Android 13, with z2term in the foreground). " +
                "The slot is assigned — place it from the pencil (edit) screen of the quick settings panel.",
            ja = "z2-tile: ここでは追加を頼めません (Android 13 以降 + z2term が前面にいることが要ります)。" +
                "割り当ては済んでいるので、クイック設定パネルの鉛筆(編集)から並べてください。",
            "zh-CN" to "z2-tile: 这里没法请求添加 (需要 Android 13 以上，并且 z2term 在前台)。" +
                "分配已经完成，请从快捷设置面板的铅笔(编辑)里摆上去。",
            "zh-TW" to "z2-tile: 這裡沒法請求新增 (需要 Android 13 以上，並且 z2term 在前景)。" +
                "分配已經完成，請從快速設定面板的鉛筆(編輯)裡擺上去。"
        )

    /** 追加を頼めたとき。⚠ **答えるのは利用者**なので「追加した」とは言い切らない。 */
    val tileAddAsked: String =
        t(
            en = "asked Android whether to add the tile — answer the dialog. " +
                "(Nothing showed up? Place it from the edit screen instead.)",
            ja = "追加してよいか Android に聞いています。出たダイアログで答えてください。" +
                "(何も出なければ、編集画面から並べてください)",
            "zh-CN" to "已经在问 Android 是否添加磁贴，请在弹出的对话框里回答。" +
                "(如果什么都没出现，请从编辑界面摆上去)",
            "zh-TW" to "已經在問 Android 是否新增圖塊，請在彈出的對話方塊裡回答。" +
                "(如果什麼都沒出現，請從編輯介面擺上去)"
        )

    // --- z2-icon ---

    /** 選べる一辺 ([IconStore.GRIDS])。⚠ 文言へ数を書き写さない — 増やしたときにここだけ古くなる。 */
    private val grids = IconStore.GRIDS.joinToString("|")

    /** 何も指定しないときの一辺。 */
    private val grid = IconStore.DEFAULT_GRID

    /**
     * `z2-icon` のヘルプ。
     *
     * ⚠ **やりたいこと順に並べる**。以前はサブコマンドを 8 つ並べてから注意書きを続けており、
     * 「まず何を打てばよいか」が読み取れなかった (利用者の指摘)。いちばん多い用途 (一覧から
     * 選んで入れる) を先頭に置き、残りを「入れる / 自分の絵を残す / 描き方 / 注意」へ分ける。
     */
    val iconHelp: String = t(
        en = """
        |# Replace the status bar and quick-settings tile icons with your own pixel drawing.
        |#
        |# Start here
        |#   z2-icon pick 1     ... choose a drawing from a list and put it on tile slot 1
        |#   z2-icon list       ... what each slot has now ('-p' shows the drawings too)
        |#
        |# What <target> means
        |#   notify    the icon for every notification this app puts out (the status bar one)
        |#   1-$tiles      a quick-settings tile slot - the same number z2-tile uses
        |#
        |# Putting a drawing in
        |#   pick <target>            list them and choose by number (easiest)
        |#   sample <target> <name>   when you already know the name
        |#   edit <target>            draw it, or fix the current one, in ${d}EDITOR
        |#   set <target> <file>      read it from a file ('-' for stdin)
        |#   auto <1-$tiles|all>          pick again from what the tile runs
        |#   clear <target|all>       back to the built-in icon
        |#
        |# How fine the grid is
        |#   grid                     the size new drawings are made at
        |#   grid <$grids>          set it ($grid unless you change it)
        |#   scale <target> <$grids>  lay the drawing that is on <target> out on that grid
        |#                            (halving the diagonal steps on the way). **It gives you
        |#                            room to draw finer** - the icon that comes out is
        |#                            smoothed anyway, so you need not run this to get that.
        |#
        |# Keeping your own drawings in the list
        |#   save <target> <name>     name what is on <target> now and add it to the list
        |#   sample                   the list you can choose from (number / name /
        |#                            'builtin' = shipped, 'mine' = saved by you)
        |#   sample <name>            show that drawing
        |#   forget <name>            drop one of yours from the list (what you already
        |#                            put on a target stays where it is)
        |#
        |# How to draw
        |#   It is a grid of characters. '.' ' ' '0' '-' '_' leave a dot empty and anything else
        |#   fills it in, so use whichever character you find easiest to see. Blank space around
        |#   the drawing is ignored - it gets centred, so you do not have to fill every line
        |#   exactly. The grid is $grids ('z2-icon grid').
        |#   ⚠ A bigger grid does not make a bigger icon. Fill the grid, or the drawing comes
        |#     out smaller than the one you replaced.
        |#
        |# Worth knowing
        |#   ⚠ Only the shape gets through. Android paints these icons a single colour of its own
        |#     (tiles change colour between on and off), so there is no colour to pick.
        |#   ⚠ **The outline is smoothed when it goes out.** A drawing made on $grid dots still
        |#     comes out smooth on a tile (which is drawn much larger than the status bar), so
        |#     the grid is not something to worry about day to day. 48 / 64 are for when **you**
        |#     want to draw finer. 'show' prints the drawing as you drew it; the tile is smoother.
        |#   ⚠ A tile gets a drawing by itself when the name gives it away ('z2-tile set 1
        |#     remind.sh' puts a clock there). Anything you set here wins and is never touched
        |#     again — 'z2-icon auto 1' hands that slot back to the automatic choice.
        |#   ⚠ Two icons cannot be changed: the one in the quick-settings **edit** screen (where
        |#     you drag tiles from) and the launcher icon. Android fixes those at install time.
        |#
        |# e.g. z2-icon pick 1              choose the drawing for slot 1
        |#      z2-icon edit 1              redraw it yourself
        |#      z2-icon save 1 my-face      name that drawing and add it to the list
        |#      z2-icon sample 3 my-face    put the same drawing on slot 3
        |#      z2-icon grid 64             draw new ones on a 64x64 grid from now on
        |#      z2-icon scale 1 48          move slot 1 onto a 48x48 grid to draw it finer
        |#      z2-icon list -p             check what is where, drawings and all
        |#      z2-icon clear notify        put the notification icon back
    """.trimMargin(),
        ja = """
        |# ステータスバーとタイルのアイコンを、自分のドット絵に差し替えます。
        |#
        |# まずこれだけ
        |#   z2-icon pick 1     ... 絵を一覧から選んでタイルの枠 1 に入れる
        |#   z2-icon list       ... いまどの枠に何の絵が入っているか (-p を付けると絵も出ます)
        |#
        |# <対象> の書き方
        |#   notify    このアプリが出す通知すべてのアイコン (ステータスバーに出るもの)
        |#   1〜$tiles      クイック設定タイルの枠番号 - z2-tile の枠と同じ番号です
        |#
        |# 絵を入れる
        |#   pick <対象>              一覧を出して番号で選ぶ (いちばん簡単)
        |#   sample <対象> <名前>     名前が分かっているとき
        |#   edit <対象>              ${d}EDITOR で描く / いまの絵を直す
        |#   set <対象> <ファイル>    ファイルから読む (- で標準入力)
        |#   auto <1〜$tiles|all>         割り当てたコマンドから選び直す
        |#   clear <対象|all>         既定のアイコンに戻す
        |#
        |# 細かさ (マス目の一辺)
        |#   grid                     これから描く絵の一辺を出す
        |#   grid <$grids>          これから描く絵の一辺を決める (既定は $grid)
        |#   scale <対象> <$grids>    いま入っている絵をそのマス目へ敷き直す (斜めの階段を
        |#                            均しながら)。**描き足せる細かさが増えます** — 出る
        |#                            アイコンはもともと均されるので、滑らかにするために
        |#                            打つ必要はありません。
        |#
        |# 自分の絵を一覧に残す
        |#   save <対象> <名前>       いま入っている絵に名前を付けて一覧に足す
        |#   sample                   選べる絵の一覧 (番号 / 名前 /
        |#                            builtin = 同梱の絵・mine = 自分で保存した絵)
        |#   sample <名前>            その絵を表示する
        |#   forget <名前>            自分の絵を一覧から下げる
        |#                            (すでに入れてある絵はそのまま残ります)
        |#
        |# 描き方
        |#   文字のマス目です。'.' ' ' '0' '-' '_' が空きマスで、それ以外の文字はすべて塗りなので、
        |#   自分が見やすい字で描けます。まわりの余白は無視して中央に置き直すので、
        |#   行きっちりでなくてかまいません。一辺は $grids から選べます (z2-icon grid)。
        |#   ⚠ 一辺を大きくしても絵は大きくなりません。マス目いっぱいに描かないと、
        |#     そのぶんアイコンが小さく出ます。
        |#
        |# 覚えておくこと
        |#   ⚠ 伝わるのは形だけです。Android がこれらのアイコンを単色で塗り直します
        |#     (タイルは入 / 切で色が変わります)。色は選べません。
        |#   ⚠ **出すときに輪郭は自動で均されます**。${grid} マスで描いた絵も、タイル
        |#     (ステータスバーよりずっと大きく出ます) では滑らかに出るので、ふだん一辺を
        |#     気にする必要はありません。48 / 64 は**自分で細かく描き込みたいとき**に選びます。
        |#     show が出すのは描いたままの絵で、タイルにはそれより滑らかに出ます。
        |#   ⚠ タイルには、割り当てた名前から分かるものに絵が自動で付きます
        |#     (z2-tile set 1 remind.sh なら時計)。ここで入れた絵はそれより優先され、
        |#     以後は自動で触りません。自動に戻したいときは z2-icon auto 1 です。
        |#   ⚠ 変えられないアイコンが 2 つあります: クイック設定の**編集**画面
        |#     (タイルを引っぱり出すところ) のアイコンと、ランチャーのアイコン。
        |#     Android が導入時に固定するためです。
        |#
        |# 例: z2-icon pick 1                枠 1 の絵を一覧から選ぶ
        |#     z2-icon edit 1                自分で描き直す
        |#     z2-icon save 1 わたしの顔     その絵に名前を付けて一覧に足す
        |#     z2-icon sample 3 わたしの顔   枠 3 にも同じ絵を入れる
        |#     z2-icon grid 64               これから描く絵を 64x64 のマス目にする
        |#     z2-icon scale 1 48            枠 1 の絵を 48x48 へ (細かく描き込めるようにする)
        |#     z2-icon list -p               どこに何が入っているか絵つきで確かめる
        |#     z2-icon clear notify          通知のアイコンを元に戻す
    """.trimMargin(),
        "zh-CN" to """
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
    """.trimMargin(),
        "zh-TW" to """
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
    )

    val iconUsage: String =
        t(
            en = "usage: z2-icon pick <notify|1-$tiles> | sample [name|target name] | " +
                "edit <notify|1-$tiles> | set <notify|1-$tiles> [file|-] | " +
                "save <notify|1-$tiles> <name> | forget <name> | " +
                "show <notify|1-$tiles> | auto <1-$tiles|all> | clear <notify|1-$tiles|all> | " +
                "grid [$grids] | scale <notify|1-$tiles> <$grids> | list [-p]",
            ja = "usage: z2-icon pick <notify|1-$tiles> | sample [名前|対象 名前] | " +
                "edit <notify|1-$tiles> | set <notify|1-$tiles> [ファイル|-] | " +
                "save <notify|1-$tiles> <名前> | forget <名前> | " +
                "show <notify|1-$tiles> | auto <1-$tiles|all> | clear <notify|1-$tiles|all> | " +
                "grid [$grids] | scale <notify|1-$tiles> <$grids> | list [-p]",
            "zh-CN" to "usage: z2-icon pick <notify|1-$tiles> | sample [名字|目标 名字] | " +
                "edit <notify|1-$tiles> | set <notify|1-$tiles> [文件|-] | " +
                "save <notify|1-$tiles> <名字> | forget <名字> | " +
                "show <notify|1-$tiles> | auto <1-$tiles|all> | clear <notify|1-$tiles|all> | " +
                "grid [$grids] | scale <notify|1-$tiles> <$grids> | list [-p]",
            "zh-TW" to "usage: z2-icon pick <notify|1-$tiles> | sample [名字|目標 名字] | " +
                "edit <notify|1-$tiles> | set <notify|1-$tiles> [檔案|-] | " +
                "save <notify|1-$tiles> <名字> | forget <名字> | " +
                "show <notify|1-$tiles> | auto <1-$tiles|all> | clear <notify|1-$tiles|all> | " +
                "grid [$grids] | scale <notify|1-$tiles> <$grids> | list [-p]"
        )

    /** `z2-icon set` にファイルを指定したが無かったときの文言 (後ろにファイル名が付く)。 */
    val iconNoSuchFile: String =
        t(en = "no such file:", ja = "そのファイルはありません:", "zh-CN" to "没有这个文件:", "zh-TW" to "沒有這個檔案:")

    /** `z2-icon edit` で何も変えずに終わったときの文言。 */
    val iconEditUnchanged: String =
        t(en = "unchanged.", ja = "変更なしで終了しました。", "zh-CN" to "没有改动就结束了。", "zh-TW" to "沒有改動就結束了。")

    /** `z2-icon pick` が番号を尋ねるときの文言 (行末で入力を待つので改行を入れない)。 */
    val iconPickPrompt: String =
        t(en = "number (or name), blank to cancel: ", ja = "番号 (または名前) を入力 (空欄で中止): ", "zh-CN" to "请输入编号 (或名字)，留空则中止: ", "zh-TW" to "請輸入編號 (或名字)，留空則中止: ")

    /** `z2-icon pick` を空欄で抜けたときの文言。 */
    val iconPickCancelled: String =
        t(en = "cancelled.", ja = "中止しました。", "zh-CN" to "已中止。", "zh-TW" to "已中止。")

    /** `z2-icon edit` で開くエディタが見つからないときの文言。 */
    val iconNoEditor: String =
        t(
            en = "no editor found. Set ${d}EDITOR, or use: z2-icon set <target> <file>",
            ja = "エディタが見つかりません。${d}EDITOR を設定するか z2-icon set <対象> <ファイル> をお使いください",
            "zh-CN" to "找不到编辑器。请设置 ${d}EDITOR，或者用 z2-icon set <目标> <文件>",
            "zh-TW" to "找不到編輯器。請設定 ${d}EDITOR，或者用 z2-icon set <目標> <檔案>"
        )

    /** 置き場に無いマクロ名を弾くときの文言 (後ろに名前が付く)。 */
    val tileNoSuchMacro: String =
        t(
            en = "no such macro in ~/.z2term/macros/ (use a full path to run it as a command):",
            ja = "そのマクロは ~/.z2term/macros/ にありません (コマンドとして走らせるならフルパスで):",
            "zh-CN" to "~/.z2term/macros/ 里没有这个宏 (要当成命令运行请写完整路径):",
            "zh-TW" to "~/.z2term/macros/ 裡沒有這個巨集 (要當成指令執行請寫完整路徑):"
        )

    // --- z2-noti ---

    val notiHelp: String = t(
        en = """
        |# z2-noti list  … the notifications currently on screen, as TSV
        |#                 (key / package / app name / title / body)
        |# Reading only. There is deliberately no way to press or dismiss a notification:
        |# that would also press other apps' pay and send buttons.
        |# Needs notification access (Settings > resident servers & automation).
        |# See also: z2-when notify:otp / notify:pkg=<part> / notify:contains=<part>
    """.trimMargin(),
        ja = """
        |# z2-noti list  … いま出ている通知を TSV で表示
        |#                 (key / パッケージ / アプリ名 / タイトル / 本文)
        |# 読むだけです。通知のボタンを「押す」「消す」は意図的に用意していません
        |# (他アプリの決済・送信ボタンまで押せてしまうため)。
        |# 通知アクセスの許可が要ります (設定 › 常駐サーバー・自動化 › 通知検知)。
        |# 併せて: z2-when notify:otp / notify:pkg=<部分> / notify:contains=<部分>
    """.trimMargin(),
        "zh-CN" to """
        |# z2-noti list  … 以 TSV 显示当前正在显示的通知
        |#                 (key / 包名 / 应用名 / 标题 / 正文)
        |# 只是读取。“按下”“清除”通知是**有意**没有提供的
        |# (那样连其他应用的支付、发送按钮都能按下去)。
        |# 需要通知使用权 (设置 › 常驻服务与自动化 › 通知检测)。
        |# 另见: z2-when notify:otp / notify:pkg=<片段> / notify:contains=<片段>
    """.trimMargin(),
        "zh-TW" to """
        |# z2-noti list  … 以 TSV 顯示當前正在顯示的通知
        |#                 (key / 套件名稱 / 應用程式名 / 標題 / 正文)
        |# 只是讀取。“按下”“清除”通知是**有意**沒有提供的
        |# (那樣連其他應用程式的支付、發送按鈕都能按下去)。
        |# 需要通知使用權 (設定 › 常駐服務與自動化 › 通知偵測)。
        |# 另見: z2-when notify:otp / notify:pkg=<片段> / notify:contains=<片段>
    """.trimMargin()
    )

    val notiUsage: String =
        t(en = "usage: z2-noti list", ja = "usage: z2-noti list", "zh-CN" to "usage: z2-noti list", "zh-TW" to "usage: z2-noti list")

    // --- z2-alarm ---

    val alarmHelp: String = t(
        en = """
        |# z2-alarm at HH:MM [name]     … once at the next HH:MM (tomorrow if already past)
        |# z2-alarm daily HH:MM [name]  … every day at HH:MM
        |# z2-alarm in <N|Ns|Nm|Nh> [name] … once, N seconds/minutes/hours from now
        |# z2-alarm list                … list what is scheduled (JSON; "exact" says how punctual)
        |# z2-alarm cancel <id|name|all> … cancel
        |# When it fires, a line {"event":"alarm","name":…} is appended to ~/.z2term/events.jsonl.
        |# It wakes the device even in Doze. "exact":true means it fires on the minute;
        |# "exact":false means Doze only offers a slot every 9-15 min, so a phone left with the
        |# screen off can be that late. Turning battery optimisation off for this app is what
        |# flips it to true (no extra permission is asked for).
    """.trimMargin(),
        ja = """
        |# z2-alarm at HH:MM [名前]     … 次の HH:MM に 1 回 (今日を過ぎていれば明日)
        |# z2-alarm daily HH:MM [名前]  … 毎日 HH:MM
        |# z2-alarm in <N|Ns|Nm|Nh> [名前] … N 秒/分/時間後に 1 回
        |# z2-alarm list                … 予約一覧 (JSON。"exact" が時刻どおりに鳴るかどうか)
        |# z2-alarm cancel <id|名前|all> … 取り消し
        |# 発火すると ~/.z2term/events.jsonl に {"event":"alarm","name":…} が 1 行増える。
        |# Doze 中でも起きる。"exact":true なら時刻ちょうどに鳴る。"exact":false のときは
        |# Doze 中の発火の機会が 9〜15 分に 1 回しか無いため、画面を消して放置していると
        |# それくらい遅れる。true にするのは**このアプリの電池の最適化を切る**ことで、
        |# 追加の許可は求めない。
    """.trimMargin(),
        "zh-CN" to """
        |# z2-alarm at HH:MM [名称]     … 在下一个 HH:MM 响一次 (今天已过就是明天)
        |# z2-alarm daily HH:MM [名称]  … 每天 HH:MM
        |# z2-alarm in <N|Ns|Nm|Nh> [名称] … N 秒/分钟/小时后响一次
        |# z2-alarm list                … 列出预约 (JSON。"exact" 表示是否准点响)
        |# z2-alarm cancel <id|名称|all> … 取消
        |# 触发时 ~/.z2term/events.jsonl 会多出一行 {"event":"alarm","name":…}。
        |# 即使在 Doze 中也会唤醒。"exact":true 表示准点响。"exact":false 时，
        |# Doze 中每 9〜15 分钟才有一次触发机会，所以熄屏放着会晚这么多。
        |# 让它变成 true 的办法是**关掉这个应用的电池优化**，不会额外索要权限。
    """.trimMargin(),
        "zh-TW" to """
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
    )

    val alarmNoDate: String =
        t(en = "z2-alarm: no usable date command", ja = "z2-alarm: date が使えません", "zh-CN" to "z2-alarm: date 用不了", "zh-TW" to "z2-alarm: date 用不了")

    // --- z2-session ---

    val sessionHelp: String = t(
        en = """
        |# z2-session list                     … list tabs (index / id / kind / mark / name, TSV)
        |#   marks: * = on screen / ! = running / ? = not started / @ = attached from a shell / - = other
        |# z2-session new [name]               … open one terminal tab (returns index and id)
        |# z2-session send <tab> <text>...     … type text into that tab (does not run it)
        |# z2-session send <tab> <text> --enter … type it, then run it
        |# z2-session key <tab> <key>...      … send **keys** to that tab (C-c / M-x / F5 / Up …)
        |#   modifiers: C- (Ctrl) and M- (Meta=Alt); they stack, as in C-M-a
        |#   special: Up Down Left Right Home End PgUp PgDn Ins Del Tab S-Tab Enter Esc Space BS F1-F12
        |#   Shift-ed keys such as C-S-a are refused (a terminal cannot tell Shift apart: same bytes as C-a)
        |# z2-session key <tab> --raw '\x1b[A' … anything else, as bytes (\xHH \e \n \r \t \0)
        |# z2-session capture [tab] [--all]    … take that tab's screen (--all includes scrollback)
        |# z2-session attach <tab>             … stay connected to that tab and just type in it
        |#   leave with Ctrl+] (any time), or with ~. at the start of a line (as in ssh)
        |#   ⚠ over SSH only Ctrl+] works: the ssh in front of you eats ~. and drops the SSH session
        |#   write ~~ for a literal ~ at the start of a line, ~ then Ctrl+] for a literal Ctrl+]
        |#   while attached the tab follows YOUR window size; it goes back when you leave
        |# z2-session close <tab>              … close that tab (never the last one)
        |#
        |# <tab> can be the index from list, an id, or a tab name. '.' or omitted = the tab on screen.
        |# e.g. n=${d}(z2-session new build | cut -f1); z2-session send "${d}n" 'make -j2' --enter
    """.trimMargin(),
        ja = """
        |# z2-session list                     … タブ一覧 (番号 / id / 種別 / 印 / 名前 の TSV)
        |#   印: * = 表示中 / ! = 何か動作中 / ? = まだ起動していない / @ = 端末から繋がっている / - = それ以外
        |# z2-session new [名前]               … 端末タブを 1 枚開く (番号と id を返す)
        |# z2-session send <先> <文字列>...    … そのタブに文字を入れる (実行はしない)
        |# z2-session send <先> <文字列> --enter … 入れてから実行する
        |# z2-session key <先> <キー>...      … そのタブに**キー**を送る (C-c / M-x / F5 / Up …)
        |#   修飾は C- (Ctrl) と M- (Meta=Alt)。C-M-a のように重ねられる
        |#   特殊キー: Up Down Left Right Home End PgUp PgDn Ins Del Tab S-Tab Enter Esc Space BS F1-F12
        |#   ⛔ C-S-a のような Shift 付きは断る (端末は Shift を区別できず C-a と同じバイトになるため)
        |# z2-session key <先> --raw '\x1b[A'  … 表に無いものはバイト列で (\xHH \e \n \r \t \0)
        |# z2-session capture [先] [--all]     … そのタブの画面を取り出す (--all は遡れる分も)
        |# z2-session attach <先>              … そのタブに繋ぎっぱなしにして、普通に打つ
        |#   抜けるのは Ctrl+] (いつでも) か、行頭で ~. (ssh と同じ)
        |#   ⚠ SSH 越しでは Ctrl+] だけが効く (~. は手前の ssh が食って SSH ごと切れる)
        |#   行頭の ~ そのものは ~~、Ctrl+] そのものは 行頭の ~ に続けて Ctrl+]
        |#   繋いでいる間、タブの広さは**繋いだ側**に合わせる。抜ければ元に戻る
        |# z2-session close <先>               … そのタブを閉じる (最後の 1 枚は閉じない)
        |#
        |# <先> は list の番号 / id / タブ名 のどれでもよい。'.' か省略で今表示しているタブ。
        |# 例: n=${d}(z2-session new build | cut -f1); z2-session send "${d}n" 'make -j2' --enter
    """.trimMargin(),
        "zh-CN" to """
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
    """.trimMargin(),
        "zh-TW" to """
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
    )

    val sessionUsage: String =
        t(
            en = "usage: z2-session list | new [name] | send <tab> <text>... [--enter] | " +
                "key <tab> <key>... | key <tab> --raw <bytes> | capture [tab] [--all] | " +
                "attach <tab> | close <tab>",
            ja = "usage: z2-session list | new [名前] | send <先> <文字列>... [--enter] | " +
                "key <先> <キー>... | key <先> --raw <バイト列> | capture [先] [--all] | " +
                "attach <先> | close <先>",
            "zh-CN" to "usage: z2-session list | new [名称] | send <目标> <文本>... [--enter] | " +
                "key <目标> <键>... | key <目标> --raw <字节序列> | capture [目标] [--all] | " +
                "attach <目标> | close <目标>",
            "zh-TW" to "usage: z2-session list | new [名稱] | send <目標> <文字>... [--enter] | " +
                "key <目標> <鍵>... | key <目標> --raw <位元組序列> | capture [目標] [--all] | " +
                "attach <目標> | close <目標>"
        )

    // --- z2-session key (別のタブへキーを送る) ---

    /** 送るキーが 1 つも無い。 */
    val keyNothing: String =
        t(en = "z2-session key: no key given", ja = "z2-session key: 送るキーがありません", "zh-CN" to "z2-session key: 没有给出要送的键", "zh-TW" to "z2-session key: 沒有給出要送的鍵")

    /** `--raw` の後ろが空。 */
    val keyRawEmpty: String =
        t(
            en = "z2-session key: --raw needs the bytes to send",
            ja = "z2-session key: --raw の後ろにバイト列がありません",
            "zh-CN" to "z2-session key: --raw 后面没有字节序列",
            "zh-TW" to "z2-session key: --raw 後面沒有位元組序列"
        )

    /** 表に無いキー名。⚠ **どこを見れば分かるか**まで書く。 */
    val keyUnknown: String = t(
        en = "z2-session key: unknown key (see 'z2-session -h' for the list):",
        ja = "z2-session key: そんなキー名はありません ('z2-session -h' に一覧):",
        "zh-CN" to "z2-session key: 没有这个键名 (一览见 'z2-session -h'):",
        "zh-TW" to "z2-session key: 沒有這個鍵名 (一覽見 'z2-session -h'):"
    )

    /** `\\xHH` として読めなかった。 */
    val keyBadEscape: String =
        t(
            en = "z2-session key: cannot read the escape:",
            ja = "z2-session key: 読めないエスケープ:",
            "zh-CN" to "z2-session key: 读不懂的转义:",
            "zh-TW" to "z2-session key: 讀不懂的轉義:"
        )

    /**
     * Shift 付きを断る文言。⚠ **なぜ送れないかと、代わりに何を書けばよいか**を必ず出す —
     * 「送れません」だけだと、書き方が悪いのか端末の話なのか区別が付かない。
     */
    fun keyShiftNotDistinguishable(asWritten: String, equivalentTo: String): String = t(
        en = "z2-session key: the terminal cannot tell Shift apart, so '$asWritten' would be " +
            "the very same bytes as '$equivalentTo'. Write '$equivalentTo' if that is what you meant.",
        // ⚠ 行の分け目は**句点**に置く (読点や助詞で割ると lint の TextConcatSpace が
        // 「空白が抜けているのでは」と拾う。日本語では誤検知だが、警告 0 を保つ方を採る)。
        ja = "z2-session key: 端末は Shift を区別できないので、'$asWritten' は '$equivalentTo' とまったく同じバイトになります。" +
                "それでよければ '$equivalentTo' と書いてください。",
        "zh-CN" to "z2-session key: 终端分辨不出 Shift，所以 '$asWritten' 和 '$equivalentTo' 会是完全一样的字节。" +
                "如果这正是你想要的，请写成 '$equivalentTo'。",
        "zh-TW" to "z2-session key: 終端機分辨不出 Shift，所以 '$asWritten' 和 '$equivalentTo' 會是完全一樣的位元組。" +
                "如果這正是你想要的，請寫成 '$equivalentTo'。"
    )

    // --- z2-session attach (タブに繋ぎっぱなしにする) ---

    /**
     * 繋ぎ先が見つからない。⚠ **どう調べればよいか**まで書く
     * (`key` の keyUnknown と同じ約束)。
     */
    fun attachNoSuchTab(target: String): String = t(
        en = "no such tab: '$target' (run 'z2-session list' to see the tabs)",
        ja = "そんなタブはありません: '$target' ('z2-session list' で一覧が出ます)",
        "zh-CN" to "没有这个标签页: '$target' (用 'z2-session list' 可以看到一览)",
        "zh-TW" to "沒有這個分頁: '$target' (用 'z2-session list' 可以看到一覽)"
    )

    /** GUI タブには繋げない (PTY が無い)。 */
    val attachNotTerminal: String = t(
        en = "that is a GUI tab, which has no shell to attach to. Pick a terminal tab.",
        ja = "それは GUI タブなのでシェルがありません。端末タブを指してください。",
        "zh-CN" to "那是图形标签页，没有 shell。请指定一个终端标签页。",
        "zh-TW" to "那是圖形分頁，沒有 shell。請指定一個終端機分頁。"
    )

    /**
     * まだ起動していないタブ。⛔ **こちらから勝手に起こさない** —
     * 繋いだつもりが OS の初回ダウンロードを始める、を作らない。
     */
    val attachNotStarted: String = t(
        en = "that tab has not started yet (marked '?' in list). Open it once in the app, then attach.",
        ja = "そのタブはまだ起動していません (list の印が '?')。アプリで一度開いてから繋いでください。",
        "zh-CN" to "那个标签页还没启动 (list 里的标记是 '?')。请先在应用里打开一次再连接。",
        "zh-TW" to "那個分頁還沒啟動 (list 裡的標記是 '?')。請先在應用程式裡開啟一次再連線。"
    )

    /** プロセスが終わっているタブ。 */
    val attachExited: String = t(
        en = "that tab has already exited. Its screen can still be read with 'z2-session capture'.",
        ja = "そのタブはもう終わっています。画面は 'z2-session capture' で取り出せます。",
        "zh-CN" to "那个标签页已经结束了。画面还可以用 'z2-session capture' 取出来。",
        "zh-TW" to "那個分頁已經結束了。畫面還可以用 'z2-session capture' 取出來。"
    )

    /**
     * 自分自身のタブ。⛔ **繋がせない。** 繋ぐとそのタブの出力がそのタブへ書き戻され、
     * それがまた出力として送られて**止まらなくなる**。
     * ⚠ 「できません」で終えず、**なぜ止められないのか**まで書く (この断りだけが唯一の説明)。
     */
    val attachSelf: String = t(
        en = "that is the tab you are typing in. Attaching a tab to itself makes its own output " +
            "come back as input forever, so it cannot be undone. Pick another tab.",
        ja = "それは今あなたが打っているタブ自身です。自分に繋ぐと、そのタブの出力がそのまま" +
            "自分へ戻り続けて止められなくなります。別のタブを指してください。",
        "zh-CN" to "那就是你正在打字的这个标签页。连到自己身上的话，它自己的输出会原样回到" +
            "自己这边，一直循环停不下来。请指定别的标签页。",
        "zh-TW" to "那就是你正在打字的這個分頁。連到自己身上的話，它自己的輸出會原樣回到" +
            "自己這邊，一直循環停不下來。請指定別的分頁。"
    )

    /**
     * 遠回りで輪になる指定 (A から B へ繋いだ状態で、その中から A へ繋ぐ)。
     * [attachSelf] と同じ暴走の遠回り版。
     */
    val attachLoop: String = t(
        en = "that tab is already attached back to this one, so the two would feed each other " +
            "forever. Detach one of them first (Ctrl+]).",
        ja = "そのタブは既にこちら側へ繋がっているので、互いに送り合って止まらなくなります。" +
            "どちらかを先に外してください (Ctrl+])。",
        "zh-CN" to "那个标签页已经连回到这一边了，两边会互相发送、停不下来。" +
            "请先断开其中一边 (Ctrl+])。",
        "zh-TW" to "那個分頁已經連回到這一邊了，兩邊會互相發送、停不下來。" +
            "請先中斷其中一邊 (Ctrl+])。"
    )

    // --- z2-server ---

    val serverHelp: String = t(
        en = """
        |# z2-server list                … registered servers (index / id / state / mark / name, TSV)
        |#   marks: * = enabled / - = disabled
        |# z2-server start <server>      … run it as a resident server (keeps the device reachable)
        |# z2-server stop <server>       … stop that one (the others keep running)
        |# z2-server status [<server>]   … state, pid, restarts and last exit code
        |#
        |# <server> can be the index from list, an id, or the name you gave it in the app.
        |# Only servers registered in the app can be started; this never registers a new one.
        |#
        |# Why this exists: a daemon started straight from a rule runs OUTSIDE the resident-server
        |# frame (no WakeLock, no WifiLock, no foreground service), so it stops answering once the
        |# screen goes off. Starting it through here puts it inside that frame.
        |# e.g. z2-when wifi:connect run 'z2-server start sshd'
        |#      z2-when wifi:disconnect run 'z2-server stop sshd'
    """.trimMargin(),
        ja = """
        |# z2-server list                … 登録済みサーバー一覧 (番号 / id / 状態 / 印 / 名前 の TSV)
        |#   印: * = 有効 / - = 無効
        |# z2-server start <サーバー>    … 常駐サーバーとして起動する (画面消灯中も届く枠の中で上がる)
        |# z2-server stop <サーバー>     … その 1 本だけ止める (他は動いたまま)
        |# z2-server status [<サーバー>] … 状態・pid・再起動回数・前回の終了コード
        |#
        |# <サーバー> は list の番号 / id / アプリで付けた名前 のどれでもよい。
        |# 起動できるのはアプリに登録済みのものだけ (ここから新しく登録はしない)。
        |#
        |# なぜ要るか: ルールから直接起こしたデーモンは**常駐サーバーの枠の外**で動くため
        |# (WakeLock も WifiLock も FGS も付かない)、画面を消すと応答しなくなる。
        |# ここから起こすと枠の中に入る。
        |# 例: z2-when wifi:connect run 'z2-server start sshd'
        |#     z2-when wifi:disconnect run 'z2-server stop sshd'
    """.trimMargin(),
        "zh-CN" to """
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
    """.trimMargin(),
        "zh-TW" to """
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
    )

    val serverUsage: String =
        t(
            en = "usage: z2-server list | start <server> | stop <server> | status [<server>]",
            ja = "usage: z2-server list | start <サーバー> | stop <サーバー> | status [<サーバー>]",
            "zh-CN" to "usage: z2-server list | start <服务器> | stop <服务器> | status [<服务器>]",
            "zh-TW" to "usage: z2-server list | start <伺服器> | stop <伺服器> | status [<伺服器>]"
        )

    /** 名前 / 番号 / id のどれにも当たらなかった。⚠ 一覧の出し方まで書く (次に何をすればよいか)。 */
    val serverNotFound: String =
        t(
            en = "z2-server: no such server (see 'z2-server list'):",
            ja = "z2-server: そのサーバーはありません ('z2-server list' で一覧):",
            "zh-CN" to "z2-server: 没有这个服务器 (一览见 'z2-server list'):",
            "zh-TW" to "z2-server: 沒有這個伺服器 (一覽見 'z2-server list'):"
        )

    /** 同じ名前が複数あった。⚠ id で指定し直せると分かるように。 */
    val serverAmbiguous: String =
        t(
            en = "z2-server: the name matches more than one server; use the id from 'z2-server list':",
            ja = "z2-server: 同じ名前のサーバーが複数あります。'z2-server list' の id で指定してください:",
            "zh-CN" to "z2-server: 有多个同名的服务器。请用 'z2-server list' 里的 id 指定:",
            "zh-TW" to "z2-server: 有多個同名的伺服器。請用 'z2-server list' 裡的 id 指定:"
        )

    /** 1 件も登録が無い。 */
    val serverNone: String =
        t(
            en = "z2-server: no servers registered yet (add one in the app: 📜 -> Servers).",
            ja = "z2-server: サーバーがまだ 1 件も登録されていません (アプリの 📜 → サーバー で登録します)。",
            "zh-CN" to "z2-server: 还没有登记任何服务器 (在应用的 📜 → 服务器 里登记)。",
            "zh-TW" to "z2-server: 還沒有登記任何伺服器 (在應用程式的 📜 → 伺服器 裡登記)。"
        )

    /**
     * 省電力モード中の警告 (F-5)。⚠ **起動そのものは成功している**ので失敗にはしない。
     * 黙って上げると「起動したのにつながらない」を繰り返すので、その場で理由を出す。
     */
    val serverLowPowerWarn: String = t(
        en = "z2-server: note - low-power mode is on, so no WakeLock/WifiLock is held. " +
            "The server may stop answering while the screen is off " +
            "(Settings -> Automation -> Background process protection).",
        ja = "z2-server: 注意 — 省電力モードが ON のため WakeLock/WifiLock を握りません。" +
            "画面消灯中は応答しなくなることがあります (⚙設定 → 自動化 → バックグラウンドのプロセス保護)。",
        "zh-CN" to "z2-server: 注意 — 省电模式是开着的，所以不持有 WakeLock/WifiLock。" +
            "熄屏期间服务器可能不再响应 (⚙设置 → 自动化 → 后台进程保护)。",
        "zh-TW" to "z2-server: 注意 — 省電模式是開著的，所以不持有 WakeLock/WifiLock。" +
            "螢幕熄滅期間伺服器可能不再回應 (⚙設定 → 自動化 → 背景行程保護)。"
    )

    // --- z2-when ---

    val whenHelp: String = t(
        en = """
        |# z2-when <trigger> run <cmd...>        … register a rule
        |#   triggers: charge:start | charge:stop  (needs detection ON)
        |#            battery:below=N | battery:above=N  (needs detection ON)
        |#            time:daily=HH:MM | time:at=HH:MM | time:every=Nm|Nh
        |#            time:cron='min hour dom month dow'  (dow 0-7 / 0,7=Sunday. Quote it: it has spaces)
        |#            wifi:connect | wifi:disconnect | wifi:ssid=<name>  (needs detection ON)
        |#            net:online | net:offline  … a usable connection appeared / went away
        |#            net:wifi | net:mobile | net:ethernet  … the link in use switched to that
        |#                                        (needs detection ON; counts mobile data, not just Wi-Fi)
        |#            share:any | share:text | share:file | share:contains=<part> | share:ext=<ext>
        |#                                        … something was shared to z2term from another app
        |#            boot                        … the device finished starting up (works with detection OFF)
        |#            sms:any | sms:from=<substr> | sms:contains=<substr> | sms:otp  (needs RECEIVE_SMS)
        |#            sensor:shake | sensor:light>N | sensor:light<N | sensor:proximity=near|far  (detection ON)
        |#            file:new=<dir>[,ext=<ext>]  … a new file landed in that folder (needs detection ON)
        |#            notify:any | notify:otp | notify:pkg=<part> | notify:title=<part> | notify:contains=<part>
        |#                                        … a notification arrived (needs notification access)
        |#            event:<name> | event:<prefix>* | event:*  … any device event, by name
        |#              (z2-when events lists them; same names as in events.jsonl)
        |# Name (optional, right after the trigger — before run):
        |#   name=<text>             … what this rule is for, shown in the Automation tab
        |#                             (quote it if it has spaces). Empty = the trigger is shown.
        |#                             Display only: it changes nothing about when the rule runs.
        |# Filters (any trigger, right after it — before run):
        |#   if=<cond>[,<cond>...]   … only when the device is in that state (AND; ! negates)
        |#                             keys are the ones z2-state prints: wifi charging screen locked
        |#                             idle headset bt_audio airplane plug ssid ringer level temp volume
        |#                             e.g. if=wifi,!screen / if=ssid=Home / if=level<30
        |#   if_any=<cond>[,<cond>...] … run when **any one** of them holds (the comma means OR)
        |#                             together with if=: "all of if AND any one of if_any"
        |#   else=<cmd>              … run this **instead**, when if / if_any did not hold
        |#                             ⚠ only if / if_any reach it. A run skipped by between /
        |#                             days / cooldown runs nothing at all — a rule that is off
        |#                             at night has to stay silent at night, else included.
        |#   cooldown=30m            … do not run again within that time (10s / 30m / 2h)
        |#   between=22:00-07:00     … only inside that window (wraps past midnight)
        |#   days=mon-fri            … only on those days (names or cron numbers 0-7, 0/7 = Sunday)
        |# Skipped runs are recorded too — z2-when fired shows skip:if / skip:if→else / skip:between / skip:days
        |# / skip:cooldown, so a rule that never runs can be explained. The ▶ button in the app
        |# ignores every filter (it is there to try the rule out).
        |# z2-when events                        … list the names usable with event:
        |# z2-when pause / resume                … stop / resume automatic runs (rules are kept)
        |# z2-when fired [n]                     … recent fires (time / id / trigger / run|paused)
        |# z2-when list                          … registered rules (id / on|off / trigger / -> / cmd, TSV;
        |#                                         name and filters, if any, in [] at the end)
        |# z2-when remove <id|all>  (rm works)   … delete
        |# z2-when on <id> / off <id>            … enable / disable
        |# z2-when log <id>                      … that rule's run log (tail)
        |# On fire the command runs on the selected distro with Z2_WHEN_TRIGGER / Z2_WHEN_LEVEL
        |# / Z2_WHEN_SSID (wifi) / Z2_WHEN_SMS_FROM / Z2_WHEN_SMS_BODY / Z2_WHEN_OTP (sms)
        |# / Z2_WHEN_SENSOR / Z2_WHEN_LUX (sensor) in the environment.
        |# For net: you get Z2_WHEN_NET (the link now) and Z2_WHEN_NET_PREV (the one before).
        |# For share: you get Z2_WHEN_SHARE (the text, or the paths of the files taken in)
        |# and Z2_WHEN_SHARE_KIND (text|file). The share is still put on the input line as before.
        |# For file: you get Z2_WHEN_FILE (full path) and Z2_WHEN_DIR.
        |# For notify: you get Z2_WHEN_NOTI_PKG / _APP / _TITLE / _TEXT / _CATEGORY
        |# (and Z2_WHEN_OTP for notify:otp). notify:category= matches exactly: call, missed_call, msg, ...
        |# For event: you also get Z2_WHEN_EVENT (the event name); alarm / notify_action add
        |# Z2_WHEN_EVENT_NAME (the identifier you armed it with) and Z2_WHEN_ACTION (button pressed).
        |# The same rule will not fire twice within 10 seconds (events like screen_on come often).
        |# e.g. z2-when charge:start run ~/.z2term/macros/backup.sh
        |#      z2-when time:cron='0 3 * * *' run ~/.z2term/macros/nightly.sh
        |#      z2-when event:headset_plugged run ~/.z2term/macros/play.sh
        |#      z2-when 'event:ringer_*' run 'z2-toast "ringer: ${d}Z2_WHEN_EVENT"'
        |#      z2-when file:new=/sdcard/Pictures/Screenshots run ~/.z2term/macros/shot.sh
        |#      z2-when net:online cooldown=5m run ~/.z2term/macros/sync.sh
        |#      z2-when boot run 'sshd --lan'
        |#      z2-when share:text run '~/.z2term/macros/fetch.sh "${d}Z2_WHEN_SHARE"'
        |#      z2-when time:daily=07:00 name='Morning report' run ~/.z2term/macros/report.sh
    """.trimMargin(),
        ja = """
        |# z2-when <トリガー> run <コマンド...>   … ルールを登録
        |#   トリガー: charge:start | charge:stop  (検知 ON が前提)
        |#            battery:below=N | battery:above=N  (検知 ON が前提)
        |#            time:daily=HH:MM | time:at=HH:MM | time:every=Nm|Nh
        |#            time:cron='分 時 日 月 曜日'  (曜日 0-7 / 0,7=日曜。空白を含むので要クォート)
        |#            wifi:connect | wifi:disconnect | wifi:ssid=<名前>  (検知 ON が前提)
        |#            net:online | net:offline  … 通信できる回線ができた / 無くなった
        |#            net:wifi | net:mobile | net:ethernet  … 使う回線がそれへ切り替わった
        |#                                        (検知 ON が前提。Wi-Fi だけでなくモバイル回線も見る)
        |#            share:any | share:text | share:file | share:contains=<部分> | share:ext=<拡張子>
        |#                                        … 他アプリの共有シートから z2term へ送られたとき
        |#            boot                        … 端末の起動が終わったとき (検知 OFF でも動く)
        |#            sms:any | sms:from=<部分> | sms:contains=<部分> | sms:otp  (RECEIVE_SMS 許可が前提)
        |#            sensor:shake | sensor:light>N | sensor:light<N | sensor:proximity=near|far  (検知 ON が前提)
        |#            file:new=<フォルダ>[,ext=<拡張子>]  … そのフォルダに新しいファイルが来たとき (検知 ON が前提)
        |#            notify:any | notify:otp | notify:pkg=<部分> | notify:title=<部分> | notify:contains=<部分>
        |#                                        … 通知が届いたとき (通知アクセスの許可が前提)
        |#            event:<名前> | event:<接頭辞>* | event:*  … 端末イベントを名前で拾う
        |#              (名前は z2-when events で一覧。events.jsonl に出るものと同じ)
        |# 名前 (任意。トリガーの直後・run より前に置く):
        |#   name=<文字列>           … 何の自動化かを表す名前。自動化タブの見出しになる
        |#                             (空白を含むならクォート)。空なら今までどおりトリガーが出る。
        |#                             表示だけの項目で、いつ動くかは一切変わらない。
        |# 絞り込み (どのトリガーでも使える。トリガーの直後・run より前に置く):
        |#   if=<条件>[,<条件>...]   … 端末がその状態のときだけ実行 (カンマは AND。頭の ! で否定)
        |#                             使えるキーは z2-state が出すもの: wifi charging screen locked
        |#                             idle headset bt_audio airplane plug ssid ringer level temp volume
        |#                             例: if=wifi,!screen / if=ssid=Home / if=level<30
        |#   if_any=<条件>[,<条件>...] … **このどれか 1 つ**を満たせば実行 (カンマが OR になる)
        |#                             if= と併せると「if を全部満たし、かつ if_any のどれか」
        |#   else=<コマンド>          … if / if_any に合わなかったとき、**代わりに**これを実行
        |#                             ⚠ 効くのは if 系で見送ったときだけ。between / days /
        |#                             cooldown で見送ったときは else も動かない (夜は動かない
        |#                             はずのルールから、夜中に通知が飛ばないように)
        |#   cooldown=30m            … 前回の実行からこの時間は再実行しない (10s / 30m / 2h)
        |#   between=22:00-07:00     … この時間帯だけ実行 (日跨ぎ可)
        |#   days=mon-fri            … この曜日だけ実行 (曜日名か cron と同じ数字 0-7 / 0,7=日曜)
        |# 弾いたことも記録する — z2-when fired に skip:if / skip:if→else / skip:between / skip:days / skip:cooldown
        |# として出るので、「動かない理由」が分かる。アプリ画面の ▶ は絞り込みを無視する
        |# (試すためのボタンなので)。
        |# z2-when events                        … event: で使えるイベント名の一覧
        |# z2-when pause / resume                … 自動実行を一時停止 / 再開 (ルールは消えない)
        |# z2-when fired [n]                     … 直近の発火 (時刻 / id / トリガー / run|paused)
        |# z2-when list                          … 登録一覧 (id / on|off / トリガー / -> / コマンド の TSV。
        |#                                         名前と絞り込みは付いていれば末尾の [] に出る)
        |# z2-when remove <id|all>  (rm でも可)  … 削除
        |# z2-when on <id> / off <id>            … 有効 / 無効
        |# z2-when log <id>                      … そのルールの実行ログ (末尾)
        |# 発火時、コマンドは選択中の distro で実行され、環境変数 Z2_WHEN_TRIGGER / Z2_WHEN_LEVEL
        |# / Z2_WHEN_SSID (wifi) / Z2_WHEN_SMS_FROM / Z2_WHEN_SMS_BODY / Z2_WHEN_OTP (sms)
        |# / Z2_WHEN_SENSOR / Z2_WHEN_LUX (sensor) が入る。
        |# net: のときは Z2_WHEN_NET (今の回線) と Z2_WHEN_NET_PREV (直前の回線) が入る。
        |# share: のときは Z2_WHEN_SHARE (テキストそのもの、またはファイルの取り込み先パス) と
        |# Z2_WHEN_SHARE_KIND (text|file) が入る。共有されたものは今までどおり入力行にも入る。
        |# file: のときは Z2_WHEN_FILE (フルパス) と Z2_WHEN_DIR が入る。
        |# notify: のときは Z2_WHEN_NOTI_PKG / _APP / _TITLE / _TEXT / _CATEGORY
        |# (notify:otp なら Z2_WHEN_OTP も)。notify:category= は完全一致: call, missed_call, msg, ...
        |# event: のときは Z2_WHEN_EVENT (イベント名) が入る。alarm / notify_action では
        |# Z2_WHEN_EVENT_NAME (仕掛けたときの識別名) と Z2_WHEN_ACTION (押したボタン) も入る。
        |# 同じルールは 10 秒以内に続けて発火しない (screen_on のような数の多いイベント対策)。
        |# 例: z2-when charge:start run ~/.z2term/macros/backup.sh
        |#     z2-when time:cron='0 3 * * *' run ~/.z2term/macros/nightly.sh
        |#     z2-when event:headset_plugged run ~/.z2term/macros/play.sh
        |#     z2-when 'event:ringer_*' run 'z2-toast "マナーモード: ${d}Z2_WHEN_EVENT"'
        |#     z2-when file:new=/sdcard/Pictures/Screenshots run ~/.z2term/macros/shot.sh
        |#     z2-when net:online cooldown=5m run ~/.z2term/macros/sync.sh
        |#     z2-when boot run 'sshd --lan'
        |#     z2-when share:text run '~/.z2term/macros/fetch.sh "${d}Z2_WHEN_SHARE"'
        |#     z2-when time:daily=07:00 name='朝の日報' run ~/.z2term/macros/report.sh
    """.trimMargin(),
        "zh-CN" to """
        |# z2-when <触发条件> run <命令...>      … 登记一条规则
        |#   触发条件: charge:start | charge:stop  (前提是检测已开启)
        |#            battery:below=N | battery:above=N  (前提是检测已开启)
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
    """.trimMargin(),
        "zh-TW" to """
        |# z2-when <觸發條件> run <指令...>      … 登記一條規則
        |#   觸發條件: charge:start | charge:stop  (前提是偵測已開啟)
        |#            battery:below=N | battery:above=N  (前提是偵測已開啟)
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
    )

    val whenPaused: String =
        t(
            en = "Automatic runs paused (z2-when resume to start again)",
            ja = "自動実行を一時停止しました (z2-when resume で再開)",
            "zh-CN" to "已暂停自动运行 (用 z2-when resume 恢复)",
            "zh-TW" to "已暫停自動執行 (用 z2-when resume 還原)"
        )

    val whenResumed: String =
        t(en = "Automatic runs resumed", ja = "自動実行を再開しました", "zh-CN" to "已恢复自动运行", "zh-TW" to "已還原自動執行")

    val whenNoFires: String =
        t(en = "(nothing has fired yet)", ja = "(まだ発火していません)", "zh-CN" to "(还没有触发过)", "zh-TW" to "(還沒有觸發過)")

    val whenPausedNote: String =
        t(
            en = "# paused (z2-when resume to start again)",
            ja = "# 一時停止中 (z2-when resume で再開)",
            "zh-CN" to "# 已暂停 (用 z2-when resume 恢复)",
            "zh-TW" to "# 已暫停 (用 z2-when resume 還原)"
        )

    val whenNoLog: String =
        t(en = "(no log yet)", ja = "(ログはまだありません)", "zh-CN" to "(还没有日志)", "zh-TW" to "(還沒有日誌)")

    val whenWriteFailed: String =
        t(en = "z2-when: could not write the rule", ja = "z2-when: 書き込みに失敗しました", "zh-CN" to "z2-when: 写入失败", "zh-TW" to "z2-when: 寫入失敗")

    /** `if=` に知らないキーを書いたとき。**キー名は呼び元がこの後ろに足す**。 */
    val whenUnknownIfKey: String =
        t(
            en = "z2-when: unknown if= key (z2-state lists what you can use):",
            ja = "z2-when: if= に書けない条件です (使えるものは z2-state が出す項目):",
            "zh-CN" to "z2-when: if= 里写不了这个条件 (可用的就是 z2-state 输出的那些项):",
            "zh-TW" to "z2-when: if= 裡寫不了這個條件 (可用的就是 z2-state 輸出的那些項):"
        )

    /** 知らない種別のトリガー (`:` の手前) を書いたとき。**種別は呼び元がこの後ろに足す**。 */
    val whenUnknownTrigger: String =
        t(
            en = "z2-when: unknown trigger (z2-when with no arguments lists them):",
            ja = "z2-when: そんなきっかけはありません (一覧は引数なしの z2-when で出ます):",
            "zh-CN" to "z2-when: 没有这个触发条件 (一览请看不带参数的 z2-when):",
            "zh-TW" to "z2-when: 沒有這個觸發條件 (一覽請看不帶參數的 z2-when):"
        )

    /** 種別は合っているが引数の書き方が違うとき。**トリガー全体は呼び元がこの後ろに足す**。 */
    val whenBadTriggerSpec: String =
        t(
            en = "z2-when: that trigger does not take this argument:",
            ja = "z2-when: そのきっかけにその書き方はできません:",
            "zh-CN" to "z2-when: 那个触发条件不能这么写:",
            "zh-TW" to "z2-when: 那個觸發條件不能這麼寫:"
        )

    val whenPausedWarn: String =
        t(
            en = "note: automatic runs are paused (z2-when resume to start again)",
            ja = "注意: 自動実行は一時停止中です (z2-when resume で再開)",
            "zh-CN" to "注意: 自动运行正处于暂停 (用 z2-when resume 恢复)",
            "zh-TW" to "注意: 自動執行正處於暫停 (用 z2-when resume 還原)"
        )

    /**
     * `run` に改行が入っていたので空白へ直したとき。
     *
     * ルールファイルは 1 行 1 項目なので、改行入りのまま書くと 2 行目以降が別の項目として
     * 読まれ、**途中で切れたコマンド**が黙って登録される（折り返して貼り付けると起きる）。
     * 弾くのではなく直して通すが、直したことは必ず伝える。
     */
    val whenRunJoined: String =
        t(
            en = "note: line breaks in the command were turned into spaces (a rule reads one line only)",
            ja = "注意: コマンドの改行を空白に直しました (ルールは 1 行しか読みません)",
            "zh-CN" to "注意: 已把命令里的换行改成空格 (一条规则只读一行)",
            "zh-TW" to "注意: 已把指令裡的換行改成空格 (一條規則只讀一行)"
        )

    /**
     * `z2-when events` が出すイベント名の一覧。**名前 (1 列目) は言語を問わず同じ**で、
     * 説明と注記だけを訳す（名前はルールに書く識別子なので訳してはいけない）。
     */
    val whenEventList: String = t(
        en = """
        |screen_on              screen turned on          [detection ON]
        |screen_off             screen turned off         [detection ON]
        |unlocked               device was unlocked       [detection ON]
        |power_connected        charging started          [detection ON]
        |power_disconnected     charging stopped          [detection ON]
        |battery_low            battery got low           [detection ON]
        |battery_okay           battery recovered         [detection ON]
        |battery_level          level crossed a 10% mark  [detection ON]
        |wifi_connected         joined Wi-Fi              [detection ON]
        |wifi_disconnected      left Wi-Fi                [detection ON]
        |net_online             a link that works appeared [detection ON]
        |net_offline            no link that works        [detection ON]
        |net_wifi               the link in use is Wi-Fi  [detection ON]
        |net_mobile             the link in use is mobile [detection ON]
        |headset_plugged        wired earphones in        [detection ON]
        |headset_unplugged      wired earphones out       [detection ON]
        |bt_audio_connected     Bluetooth audio connected [detection ON]
        |bt_audio_disconnected  Bluetooth audio gone      [detection ON]
        |airplane_on            airplane mode on          [detection ON]
        |airplane_off           airplane mode off         [detection ON]
        |ringer_normal          ringer set to normal      [detection ON]
        |ringer_vibrate         ringer set to vibrate     [detection ON]
        |ringer_silent          ringer set to silent      [detection ON]
        |boot                   the device started up     [always]
        |alarm                  a z2-alarm fired          [always]
        |notify_action          a notification button     [always]
        |unlock_failed          failed to unlock          [always, needs setup]
        |unlock_succeeded       unlocked after a failure  [always, needs setup]
    """.trimMargin(),
        ja = """
        |screen_on              画面が点いた            [検知 ON]
        |screen_off             画面が消えた            [検知 ON]
        |unlocked               ロックを解除した        [検知 ON]
        |power_connected        充電を開始した          [検知 ON]
        |power_disconnected     充電を止めた            [検知 ON]
        |battery_low            電池が少なくなった      [検知 ON]
        |battery_okay           電池が回復した          [検知 ON]
        |battery_level          残量が 10% 刻みを跨いだ [検知 ON]
        |wifi_connected         Wi-Fi に繋がった        [検知 ON]
        |wifi_disconnected      Wi-Fi が切れた          [検知 ON]
        |net_online             通信できる回線ができた  [検知 ON]
        |net_offline            通信できる回線が無い    [検知 ON]
        |net_wifi               使う回線が Wi-Fi になった [検知 ON]
        |net_mobile             使う回線がモバイルになった [検知 ON]
        |headset_plugged        有線イヤホンを挿した    [検知 ON]
        |headset_unplugged      有線イヤホンを抜いた    [検知 ON]
        |bt_audio_connected     Bluetooth 音声が繋がった [検知 ON]
        |bt_audio_disconnected  Bluetooth 音声が切れた   [検知 ON]
        |airplane_on            機内モードにした        [検知 ON]
        |airplane_off           機内モードを解除した    [検知 ON]
        |ringer_normal          着信音ありにした        [検知 ON]
        |ringer_vibrate         バイブにした            [検知 ON]
        |ringer_silent          マナーにした            [検知 ON]
        |boot                   端末が起動した          [常に]
        |alarm                  z2-alarm が鳴った       [常に]
        |notify_action          通知のボタンを押した    [常に]
        |unlock_failed          ロック解除に失敗した    [常に・要設定]
        |unlock_succeeded       失敗のあと解除できた    [常に・要設定]
    """.trimMargin(),
        "zh-CN" to """
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
        |boot                   设备启动完成              [总是]
        |alarm                  z2-alarm 响了             [总是]
        |notify_action          按了通知上的按钮          [总是]
        |unlock_failed          解锁失败了                [总是，需设置]
        |unlock_succeeded       失败之后解锁成功          [总是，需设置]
    """.trimMargin(),
        "zh-TW" to """
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
        |boot                   裝置啟動完成              [總是]
        |alarm                  z2-alarm 響了             [總是]
        |notify_action          按了通知上的按鈕          [總是]
        |unlock_failed          解鎖失敗了                [總是，需設定]
        |unlock_succeeded       失敗之後解鎖成功          [總是，需設定]
    """.trimMargin()
    )

    /** `z2-when events` の一覧の前に置くコメント（どちらの段が検知 ON を要るか）。 */
    val whenEventListNote: String = t(
        en = """
        |    # Names you can put in event:<name>. Same order as they appear in events.jsonl.
        |    # The upper group needs detection ON (Settings > resident servers & automation),
        |    # the lower group is armed by you, so it works with detection OFF.
    """.trimMargin(),
        ja = """
        |    # event:<名前> に書ける名前。events.jsonl に出るものと同じ並び。
        |    # 上段は検知 ON が前提 (設定 › 常駐サーバー・自動化 › システムイベント検知)、
        |    # 下段は自分で仕掛けるものなので検知 OFF でも動く。
    """.trimMargin(),
        "zh-CN" to """
        |    # event:<名称> 里可以写的名字。顺序和 events.jsonl 里出现的一样。
        |    # 上面一组的前提是检测已开启 (设置 › 常驻服务与自动化 › 系统事件检测)，
        |    # 下面一组是你自己设下的，所以检测关闭也能用。
    """.trimMargin(),
        "zh-TW" to """
        |    # event:<名稱> 裡可以寫的名字。順序和 events.jsonl 裡出現的一樣。
        |    # 上面一組的前提是偵測已開啟 (設定 › 常駐服務與自動化 › 系統事件偵測)，
        |    # 下面一組是你自己設下的，所以偵測關閉也能用。
    """.trimMargin()
    )

    // --- z2-update (アプリ自身の入れ替え) ---

    val updateHelp: String = t(
        en = """
        |# z2-update                     … check, download and hand the new version to the installer
        |# z2-update --check             … only say whether there is a newer version
        |# z2-update --keep              … leave the downloaded .apk behind (it is deleted by default)
        |# z2-update --dir <folder>      … download into that folder instead of the app's own
        |#
        |# Where it comes from
        |#   The GitHub Releases page of z2term, the same file you would download by hand
        |#   (z2term-<version>.apk, signed with the release key). Nothing else is contacted, and
        |#   nothing is contacted at all until you run this.
        |#
        |# What it can and cannot do
        |#   ⚠ **It cannot install silently.** Android always shows its own "install?" screen for
        |#     an app replacing itself, so the last tap is always yours. This command does every
        |#     step up to that screen.
        |#   ⚠ The first time, allow **"Install unknown apps"** for z2term (it will say so, and
        |#     ⚙Settings has the same button). Without it the install screen never appears.
        |#   ⚠ Installed from F-Droid or a store? Then this refuses and tells you to update there —
        |#     the version you have is theirs to replace, not ours.
        |#
        |# The downloaded file
        |#   By default it lands in the app's own folder and is deleted once the install goes
        |#   through (and on the next start, in case the app was killed mid-install — which is
        |#   normal when replacing itself). --keep or --dir change that; ⚙Settings holds the same
        |#   two settings for the button there.
        |#
        |# e.g. z2-update --check
        |#      z2-update --dir /sdcard/Download --keep
        |#      z2-when time:daily=03:00 run 'z2-update'   # look every night (still asks you)
    """.trimMargin(),
        ja = """
        |# z2-update                     … 新版を確認して落とし、入れ替えの確認画面まで出す
        |# z2-update --check             … 新しい版があるかどうかだけ言う
        |# z2-update --keep              … 落とした .apk を残す (既定は入れ終わったら消す)
        |# z2-update --dir <フォルダ>    … アプリ内ではなくそのフォルダへ落とす
        |#
        |# どこから来るか
        |#   z2term の GitHub Releases (手で落とすときと同じ z2term-<版>.apk・公開鍵で署名済み)。
        |#   他のどこにも繋ぎませんし、この命令を打つまでは**一切通信しません**。
        |#
        |# できること・できないこと
        |#   ⚠ **黙って入れることはできません。** Android はアプリが自分を入れ替えるとき必ず
        |#     「インストールしますか」を出します。最後の 1 タップは必ずご自身で押します。
        |#     この命令はその画面が出るところまでを全部やります。
        |#   ⚠ 初回だけ、z2term に**「不明なアプリのインストール」**を許可してください
        |#     (足りなければそう言います。⚙設定にも同じボタンがあります)。許可が無いと確認画面が
        |#     そもそも出ません。
        |#   ⚠ F-Droid など**配布元から入れた版では断ります** — その版はあちらが入れ替えるものです。
        |#
        |# 落としたファイル
        |#   既定ではアプリ内の作業場所に落とし、入れ替えが済んだら消します (入れ替えの途中で
        |#   アプリは落とされるので、**次に起動したときにも掃除します**)。--keep / --dir で変えられ、
        |#   ⚙設定にも同じ 2 つがあります (設定のボタンから更新するときはそちらが効きます)。
        |#
        |# 例: z2-update --check
        |#     z2-update --dir /sdcard/Download --keep
        |#     z2-when time:daily=03:00 run 'z2-update'   # 毎晩見に行く (確認画面は出ます)
    """.trimMargin(),
        "zh-CN" to """
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
    """.trimMargin(),
        "zh-TW" to """
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
    )

    val updateUsage: String =
        t(
            en = "usage: z2-update [--check] [--keep] [--dir <folder>]",
            ja = "usage: z2-update [--check] [--keep] [--dir <フォルダ>]",
            "zh-CN" to "usage: z2-update [--check] [--keep] [--dir <文件夹>]",
            "zh-TW" to "usage: z2-update [--check] [--keep] [--dir <資料夾>]"
        )

    /** 最新だった。⚠ **版名を必ず出す** (「最新です」だけだと何と比べたのか分からない)。 */
    fun updateUpToDate(current: String): String =
        t(
            en = "z2-update: $current is the latest version.",
            ja = "z2-update: $current が最新です。",
            "zh-CN" to "z2-update: $current 已经是最新的了。",
            "zh-TW" to "z2-update: $current 已經是最新的了。"
        )

    /** 新版が見つかった (1 行目)。 */
    fun updateFound(current: String, latest: String, size: String): String =
        t(
            en = "z2-update: $current -> $latest ($size)",
            ja = "z2-update: $current → $latest ($size)",
            "zh-CN" to "z2-update: $current → $latest ($size)",
            "zh-TW" to "z2-update: $current → $latest ($size)"
        )

    /**
     * 問い合わせに入る前に CLI が出す 1 行。
     * ⚠ **黙って数十秒待たせない** — 通信とダウンロードの間、端末には何も出ない。
     */
    val updateChecking: String = t(
        en =         "z2-update: looking for a newer version ...",
        ja =         "z2-update: 新しい版があるか見に行きます ...",
        "zh-CN" to "z2-update: 去看看有没有新版本 ...",
        "zh-TW" to "z2-update: 去看看有沒有新版本 ..."
    )

    /**
     * 確認画面を出した。⚠ **「更新しました」と書かない** — 押すまで入っていない。
     */
    val updateHandedToInstaller: String = t(
        en = "z2-update: the install screen is up on the device — approve it to finish.",
        ja = "z2-update: 端末にインストール画面を出しました。承認すると入れ替わります。",
        "zh-CN" to "z2-update: 已在设备上打开安装界面 — 确认后即可完成替换。",
        "zh-TW" to "z2-update: 已在裝置上開啟安裝介面 — 確認後即可完成替換。"
    )

    /** リリースに APK が付いていない。 */
    fun updateNoApk(url: String): String = t(
        en = "z2-update: that release has no .apk attached. Get it from the release page: $url",
        ja = "z2-update: そのリリースに .apk が付いていません。リリースページから入れてください: $url",
        "zh-CN" to "z2-update: 那个发布里没有附带 .apk。请从发布页面安装: $url",
        "zh-TW" to "z2-update: 那個發布裡沒有附帶 .apk。請從發布頁面安裝: $url"
    )

    /** 「不明なアプリのインストール」が未許可。⚠ **どこで許すか**まで書く。 */
    val updateNeedPermission: String = t(
        en = "z2-update: allow \"Install unknown apps\" for z2term first " +
            "(Settings > Apps > Special app access > Install unknown apps, or the button in z2term's Settings).",
        ja = "z2-update: 先に z2term へ「不明なアプリのインストール」を許可してください " +
            "(設定 › アプリ › 特別なアプリアクセス › 不明なアプリのインストール。z2term の ⚙設定にもボタンがあります)。",
        "zh-CN" to "z2-update: 请先给 z2term 允许“安装未知应用” " +
            "(设置 › 应用 › 特殊应用权限 › 安装未知应用。z2term 的 ⚙设置里也有按钮)。",
        "zh-TW" to "z2-update: 請先給 z2term 允許“安裝未知應用程式” " +
            "(設定 › 應用程式 › 特殊應用程式權限 › 安裝未知應用程式。z2term 的 ⚙設定裡也有按鈕)。"
    )

    /** 配布元から入れた版なので断る。 */
    val updateManagedByStore: String = t(
        en = "z2-update: this build was installed from a store (F-Droid / Play). Update it there.",
        ja = "z2-update: この版は配布元 (F-Droid / Play) から入っています。更新はそちらから行ってください。",
        "zh-CN" to "z2-update: 这个版本是从应用商店 (F-Droid / Play) 装的。请从那里更新。",
        "zh-TW" to "z2-update: 這個版本是從應用程式商店 (F-Droid / Play) 裝的。請從那裡更新。"
    )

    /** 通信・保存・入れ替えのどこかで失敗した。 */
    fun updateFailed(reason: String): String =
        t(en = "z2-update: failed: $reason", ja = "z2-update: 失敗しました: $reason", "zh-CN" to "z2-update: 失败了: $reason", "zh-TW" to "z2-update: 失敗了: $reason")
}
