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
 * ⭐ **ここに書くのは英語と日本語だけ。** それ以外の言語は `proot/lang/Cli<言語>.kt` の中で
 * このクラスを**上書き** (`override`) して持つ ([CliLanguage.apiMsg])。上書きしていない文言は
 * 英語で出る。⚠ 1.0.83 までは 6 言語の訳をここに並べていた (4,024 行)。
 * ⚠ 文言を足すときは `open` を付けること — 付け忘れると他の言語で上書きできず、
 * `scripts/i18n-status.sh` の数にも入らない。
 *
 * ⛔ **作るときは [of] を使う。** コンストラクタを直接呼ぶと、英語・日本語以外の言語でも
 * 英語が出る。
 *
 * ヘルプは行頭 `#` ・末尾改行つきの**完成形**で持つ（`trimMargin` の外で連結するため、
 * マージン `|` の剥がし漏れが起きない）。[d] はシェルの `$`。
 *
 * ⚠ 文言は `get()` で持つ（初期化の順番に依らないため）。言語ごとのクラスが上書きした文言は、
 * このクラスの初期化が終わる前に読まれると中身が空になる。
 */
internal open class Z2ApiMsg(lang: String, protected val d: String) {

    companion object {
        /** その言語のメッセージ集。訳を持たない言語は英語。 */
        fun of(lang: String, d: String): Z2ApiMsg =
            CliLanguages.of(lang)?.apiMsg(d) ?: Z2ApiMsg(lang, d)
    }

    private val isJa = lang == "ja"

    /** 英語と日本語から選ぶ。⚠ 落とし先は英語 (日本語にしない)。 */
    private fun t(en: String, ja: String): String = if (isJa) ja else en

    open val fileHelp: String get() = t(
        en = "# z2-file pick [MIME] — choose a document; copy it into HOME and print its path.\n# z2-file save FILE [MIME] — choose a destination and save a copy.\n# Cancellation fails. Up to 512 MiB; selection expires after 5 minutes.",
        ja = "# z2-file pick [MIME] — 文書を選び、ホームに取り込んだパスを返す。\n# z2-file save FILE [MIME] — 保存先を選び、ファイルのコピーを書き出す。\n# 取消は失敗扱い。512 MiBまで。選択の待ち時間は5分。"
    )

    // --- z2-usb ---

    open val usbHelp: String get() = t(
        en = """
        |# z2-usb list              … list USB devices connected to the phone
        |# z2-usb allow [number]    … ask Android to let Linux programs use a device
        |# Run allow once after connecting a device. Permission lasts until it is unplugged.
    """.trimMargin(),
        ja = """
        |# z2-usb list              … スマホに接続された USB 機器を一覧表示
        |# z2-usb allow [番号]      … Linux プログラムから使う許可を Android に求める
        |# 機器を挿した後に allow を 1 回実行します。許可は抜くまで有効です。
    """.trimMargin()
    )

    open val usbUsage: String get() = t(
        en = "usage: z2-usb list | z2-usb allow [number|path|VID:PID]",
        ja = "usage: z2-usb list | z2-usb allow [番号|パス|VID:PID]"
    )

    open val usbNoDevices: String get() = t(
        en = "No USB device is connected to the phone.",
        ja = "スマホに USB 機器が接続されていません。"
    )

    open val usbNeedSelector: String get() = t(
        en = "More than one USB device is connected; specify its number from z2-usb list.",
        ja = "USB 機器が複数あります。z2-usb list の番号を指定してください。"
    )

    open fun usbPermissionRequested(path: String): String = t(
        en = "Approve USB access on the Android screen: $path",
        ja = "Android の画面で USB アクセスを許可してください: $path"
    )

    open fun usbAlreadyAllowed(path: String): String = t(
        en = "USB access is already allowed: $path",
        ja = "USB アクセスは許可済みです: $path"
    )

    open fun usbNotFound(selector: String): String = t(
        en = "USB device not found: $selector",
        ja = "USB 機器が見つかりません: $selector"
    )

    fun usbDeviceLine(
        index: Int,
        path: String,
        vendorId: Int,
        productId: Int,
        productName: String,
        allowed: Boolean
    ): String {
        val state = if (allowed) usbStateAllowed else usbStateNeedsAllow
        val name = productName.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        return "%d  %04x:%04x  %s  %s%s".format(index, vendorId, productId, state, path, name)
    }

    /** [usbDeviceLine] の状態欄 (許可済み)。 */
    open val usbStateAllowed: String get() = t(en = "allowed", ja = "許可済み")

    /** [usbDeviceLine] の状態欄 (まだ許可されていない)。 */
    open val usbStateNeedsAllow: String get() = t(en = "needs allow", ja = "要許可")

    /** タイルの枠数 ([TileStore.COUNT])。文言へ数を**書き写さない** — 増やしたときにここだけ古くなる。 */
    protected val tiles: Int get() = TileStore.COUNT

    // --- z2-notify ---

    open val notifyHelp: String get() = t(
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
    """.trimMargin()
    )

    open val notifyUsage: String get() = t(
            en = "usage: z2-notify [-h] [-n name] [-c text] [-b label]... <title> [text]",
            ja = "usage: z2-notify [-h] [-n 名前] [-c 文字列] [-b ラベル]... <タイトル> [本文]"
        )

    // --- 単機能のもの (ヘルプ 1〜2 行) ---

    open val toastHelp: String get() = t(
        en = """
        |# z2-toast <message> … a short message at the bottom of the screen (a toast).
        |# It fades by itself and leaves nothing behind. Use z2-notify when it has to stay,
        |# or when you want a button to press.
    """.trimMargin(),
        ja = """
        |# z2-toast <メッセージ> … 画面下に短いメッセージを出す (トースト)。
        |# 数秒で自分で消えて、何も残りません。残したい・押させたいときは z2-notify を使います。
    """.trimMargin()
    )

    open val qrHelp: String get() = t(
        en = """
        |# z2-qr … open QR tools with the camera already scanning.
        |# After a scan, one button opens the content in its app (browser, LINE and other apps,
        |# dialer, mail, SMS, Wi-Fi, contacts, calendar). Nothing opens by itself.
        |# Put it on a quick-settings tile or an edge panel.
        |# z2-qr encode [-o FILE.png] [-p PIXELS] [-m MODULES] [-t] [TEXT] … make a QR code.
        |#   Nothing to install: the app encodes it (the same encoder the QR screen uses).
        |#   With no TEXT it reads standard input. -o is where the PNG goes
        |#   (default ~/.z2term/qr/qr.png) and the path is printed. -t draws it here instead,
        |#   with block characters. -p is the size to aim for in pixels (default 600),
        |#   -m the quiet zone in modules (default 4). Up to 2000 bytes.
    """.trimMargin(),
        ja = """
        |# z2-qr … QRツールをカメラで読み取る状態で開く。
        |# 読み取ると、中身に合った「開く」ボタンが 1 つ出ます (ブラウザー・LINE などのアプリ・
        |# 電話・メール・SMS・Wi-Fi・連絡先・予定)。読み取っただけでは開きません。
        |# クイック設定のタイルやエッジパネルに置けます。
        |# z2-qr encode [-o ファイル.png] [-p 画素] [-m 升] [-t] [テキスト] … QR を作る。
        |#   入れるものはありません (QR ツール画面と同じ符号化をアプリ側で行います)。
        |#   テキストを省くと標準入力から読みます。-o は PNG の書き出し先で
        |#   (既定 ~/.z2term/qr/qr.png)、書いた場所を 1 行出します。-t なら絵の代わりに
        |#   ブロック文字でこの端末に描きます。-p は目安の一辺の画素数 (既定 600)、
        |#   -m は余白の升数 (既定 4)。2000 バイトまで。
    """.trimMargin()
    )

    open val shareHelp: String get() = t(
        en = """
        |# z2-share <text> … hand text to Android's share sheet (send it on to another app).
        |# --file FILE [FILE ...]: send file contents (up to 32 files, 512 MiB total).
        |# Paths are read in the current local shell. -- forces text mode. Copies older than 24 hours are cleaned up on the next share.
        |# All arguments are joined into one body. Which app it goes to is chosen on screen,
        |# so this needs someone to be there — it is not for a macro running unattended.
    """.trimMargin(),
        ja = """
        |# z2-share <テキスト> … Android の共有メニューに渡す (他アプリへ送る)。
        |# --file ファイル [ファイル ...]: ファイル本体を送る（32件・合計512MiBまで）。
        |# 今のローカルシェルで読めるパスを指定します。-- で文字列モード。24時間経過した共有用コピーは次回共有時に削除。
        |# 引数はつなげて 1 つの本文にします。送り先は画面で選ぶので、人がいるときのものです
        |# (裏で走らせるマクロ向きではありません)。
    """.trimMargin()
    )

    open val viewHelp: String get() = t(
        en = """
        |# z2-view <file.html> [title] … read a page you made here, inside z2term.
        |# No server and no browser: the file is handed to the app and shown as it is.
        |# JavaScript and network loads are off, so nothing on the page can run or phone home.
        |# http:// and https:// links open in your usual browser.
        |# The phone's theme arrives as CSS variables (--z2-bg --z2-bg2 --z2-fg --z2-dim
        |# --z2-line --z2-accent), so a page written with them follows the terminal colours.
        |# Up to 4 MB. Pictures must be embedded (data: URIs); remote ones are not fetched.
        |# Use --edge PANEL:ITEM to send to an existing type=view edge item.
        |# Use --controls FILE.json for generic forms and actions handled by an installed macro.
        |# View items default to manual refresh. view-refresh=auto runs run= on open; z2-view returns to that item.
        |# Use - or a pipe (ls -l | z2-view) to read standard input. Text that does not start with < is shown
        |# wrapped in a monospace font, colour codes removed; --text / --html choose explicitly.
        |# A pinch sizes the text; --pinch page zooms the whole page instead. --text-size 50..300 sets the
        |# text size (percent) for this page. Both apply to the full screen; a panel item uses its own settings.
        |# --no-refresh / --no-find hide the Refresh / Find buttons of the full screen (shown by default).
    """.trimMargin(),
        ja = """
        |# z2-view <ファイル.html> [題名] … ここで作った頁を z2term の中で読みます。
        |# サーバーもブラウザも要りません。ファイルをアプリへ渡して、そのまま出します。
        |# JavaScript も外部への通信も切ってあるので、頁の側からは何も実行できません。
        |# http:// と https:// のリンクは、いつものブラウザで開きます。
        |# 端末のテーマが CSS 変数で届きます (--z2-bg --z2-bg2 --z2-fg --z2-dim
        |# --z2-line --z2-accent)。これを使って書けば、配色がテーマに揃います。
        |# 4 MB まで。絵は頁の中に埋め込んでください (data: URI)。外の絵は取りに行きません。
        |# --edge パネル:項目 で、既存のtype=view項目へ表示を送ります。
        |# --controls ファイル.json で汎用フォームと操作を定義し、導入済みマクロへ渡せます。
        |# ビューは既定で手動更新。view-refresh=autoなら開いた時にrun=を実行し、z2-viewは同じ項目へ表示します。
        |# - かパイプ (ls -l | z2-view) で標準入力を読みます。< で始まらない文字は、色の指定を除き
        |# 折り返し付きの等幅で出します。--text / --html で明示もできます。
        |# ピンチは文字の大きさを変えます。--pinch page なら頁ごと拡大します。--text-size 50〜300 で
        |# この頁の文字の大きさ (%) を決めます。どちらも全画面用で、パネルの項目は項目の設定に従います。
        |# --no-refresh / --no-find で全画面の「更新」「検索」ボタンを消します (既定は表示)。
    """.trimMargin()
    )

    open val viewUsage: String get() = t(
        en = "usage: z2-view [--edge PANEL:ITEM] [--controls FILE.json] [--text|--html] [--pinch text|page] [--text-size N] [--no-refresh] [--no-find] <file|-> [title]",
        ja = "usage: z2-view [--edge PANEL:ITEM] [--controls FILE.json] [--text|--html] [--pinch text|page] [--text-size N] [--no-refresh] [--no-find] <ファイル|-> [題名]"
    )

    open val viewUnreadable: String get() = t(
        en = "z2-view: unreadable file:",
        ja = "z2-view: 読めないファイル:"
    )

    open val openHelp: String get() = t(
        en = """
        |# z2-open <url|path> … open it with the default app (https://… or /sdcard/…).
        |# Which app opens it is Android's choice; z2term only hands it over.
        |# A path is a path on the **phone** (/sdcard/…), not inside the distro.
    """.trimMargin(),
        ja = """
        |# z2-open <URL かパス> … 既定のアプリで開く (https://… も /sdcard/… も)。
        |# どのアプリで開くかを決めるのは Android で、z2term は渡すだけです。
        |# パスは**スマホ側**のパス (/sdcard/…) で、ディストロの中のパスではありません。
    """.trimMargin()
    )

    // --- z2-img ---

    // ⚠ ヘルプに**対応していない端末では化ける**ことを必ず書く。絵を出す手段は端末の外から
    //   見分けられない (ssh の先が kitty 対応かどうかは分からない) ので、止めるのではなく
    //   「そういうものだ」と先に伝えるしかない。qr.sh の -t と同じ立場。
    open val imgHelp: String get() = t(
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
    """.trimMargin()
    )

    open val imgUsage: String get() = t(
        en = "usage: z2-img [-w cols] [-r rows] [--clear] [-f] <file|->...",
        ja = "usage: z2-img [-w 桁] [-r 行] [--clear] [-f] <ファイル|->..."
    )

    open val imgNoFile: String get() = t(
        en = "cannot read the file:",
        ja = "ファイルが読めません:"
    )

    // ⚠ 既定で止める。パイプやリダイレクトの先に APC を流すと、受け側には**壊れたバイト列**
    //   としか見えない (画像だと分かる手掛かりが残らない)。-f で通せるようにはしておく。
    open val imgNotTty: String get() = t(
        en = "stdout is not a terminal, so nothing was sent (-f sends it anyway).",
        ja = "標準出力が画面ではないので送りませんでした (-f を付けると送ります)。"
    )

    open val imgNoSize: String get() = t(
        en = "cannot read the pixel size; drawing it as a square:",
        ja = "画素数が読めないので正方形として出します:"
    )

    open val qrUsage: String get() = t(
        en = "usage: z2-qr  |  z2-qr encode [-o FILE.png] [-p PIXELS] [-m MODULES] [-t] [TEXT]",
        ja = "usage: z2-qr  |  z2-qr encode [-o ファイル.png] [-p 画素] [-m 升] [-t] [テキスト]"
    )

    open val qrEncodeFailed: String get() = t(
        en = "z2-qr: could not make the code (is the text too long? up to 2000 bytes)",
        ja = "z2-qr: QR を作れませんでした (文字が長すぎませんか。2000 バイトまでです)"
    )

    open val clipHelp: String get() = t(
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
    """.trimMargin()
    )

    open val batteryHelp: String get() = t(
            en = "# Print level / charging state as JSON ({\"level\":N,\"charging\":bool}).",
            ja = "# 残量/充電状態を JSON ({\"level\":N,\"charging\":bool}) で出力。"
        )

    open val vibrateHelp: String get() = t(
            en = "# z2-vibrate [ms]  (default 200ms)",
            ja = "# z2-vibrate [ms]  (既定 200ms)"
        )

    open val editHelp: String get() = t(
            en = "# z2-edit <file>      … edit a text file in z2term's own editor (plain text box; Save writes it back)",
            ja = "# z2-edit <ファイル>  … 文字ファイルを z2term の編集画面で書き換える (ふつうの入力欄。「保存」で書き戻す)"
        )

    open val sayHelp: String get() = t(
            en = "# z2-say <text>       … speak with the device TTS (reads stdin when no argument)",
            ja = "# z2-say <text>       … 端末標準の TTS で読み上げ (引数無しなら標準入力を読む)"
        )

    open val torchHelp: String get() = t(
            en = "# z2-torch on|off|toggle|status  (default toggle). status reads Android state, including external changes (on/off).",
            ja = "# z2-torch on|off|toggle|status  (既定 toggle)。statusで実際の点灯状態(on/off)を取得。外部操作も反映します。"
        )

    open val shotHelp: String get() = t(
            en = "# z2-shot [free|rect|oval]  … capture only the part you draw around (default free). Needs Android 11 and z2term Android actions.",
            ja = "# z2-shot [free|rect|oval]  … 囲んだところだけを撮る (既定 free)。Android 11 以降と「z2term Android 操作」が必要。"
        )

    open val mediaHelp: String get() = t(
            en = "# z2-media play|pause|playpause|next|previous|stop  (default playpause)",
            ja = "# z2-media play|pause|playpause|next|previous|stop  (既定 playpause)"
        )

    open val volumeHelp: String get() = t(
            en = "# z2-volume up|down|mute|unmute|N|N%   Media volume. Prints the resulting current/max.",
            ja = "# z2-volume up|down|mute|unmute|N|N%   メディア音量を操作。結果の current/max を出力。"
        )

    open val sensorHelp: String get() = t(
            en = "# z2-sensor light|accel|proximity  (default light). Reads one sample and returns JSON.",
            ja = "# z2-sensor light|accel|proximity  (既定 light)。センサーを 1 回読んで JSON で返す。"
        )

    open val intentHelp: String get() = t(
        en = """
        |# z2-intent [-a ACTION] [-d URI] [-t MIME] [-p PKG] [-n PKG/CLS] [-c CATEGORY] [-f FLAGS]
        |#           [--es K V] [--ez K true|false] [--ei K N] [--broadcast|--service]
        |# Fire any Android Intent (startActivity by default). A leading non-flag argument is the ACTION.
        |# Freeform defaults to task reuse and OS bounds. --reuse-task / --os-bounds remain accepted.
        |# --bounds-only requests 80% bounds without a mode key; cannot combine with --os-bounds.
        |# --no-scale disables Motorola's scaled freeform request for this launch.
        |# -p PKG alone launches that app. --window full|freeform|split|ask selects the launch request (OS support required).
    """.trimMargin(),
        ja = """
        |# z2-intent [-a ACTION] [-d URI] [-t MIME] [-p PKG] [-n PKG/CLS] [-c CATEGORY] [-f FLAGS]
        |#           [--es K V] [--ez K true|false] [--ei K N] [--broadcast|--service]
        |# 任意の Android Intent を発火 (既定は startActivity)。先頭の非フラグ引数は ACTION。
        |# フリーフォームはタスク再利用・OS任せの寸法が既定。--reuse-task / --os-bounds は互換用。
        |# --bounds-only は窓モードを指定せず80%の寸法だけ要求。--os-bounds との併用不可。
        |# --no-scale はこの起動だけMotorolaの縮小フリーフォーム指定を無効にする。
        |# -p PKG だけでアプリを起動。--window full|freeform|split|ask で開き方を指定（OS対応が必要）。
    """.trimMargin()
    )

    open val stateHelp: String get() = t(
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
    """.trimMargin()
    )

    // --- z2-ask ---

    open val askHelp: String get() = t(
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
    """.trimMargin()
    )

    open val askUsage: String get() = t(
            en = "usage: z2-ask [-t sec] [-H hint] [-d default] <question>",
            ja = "usage: z2-ask [-t 秒] [-H ヒント] [-d 既定] <質問>"
        )

    // --- z2-listen (1.0.59) ---

    open val listenHelp: String get() = t(
        en = """
        |# z2-listen [-w WORD,WORD,...] [-l LANG]
        |#   Print what you say on stdout. Recognition runs **on the device only**; your voice
        |#   never goes to the network. Needs Android 12+ and on-device speech recognition
        |#   (for your language, its offline speech data).
        |#   Speak with z2term on screen (Android gives the microphone only to the app on screen).
        |#   It stops when you go quiet. The first run asks for microphone access.
        |#   -w <words>  accept only these words (comma-separated) and print the one heard.
        |#               All of the recogniser's guesses are checked, not just the top one.
        |#   -l <lang>   language to recognise (e.g. en-US, ja-JP; default: the device language)
        |# Exit: 0 = done / 1 = nothing heard, or none of the words / 2 = unavailable (reason on stderr)
        |#   cmd=${d}(z2-listen -w "next,stop") || exit
    """.trimMargin(),
        ja = """
        |# z2-listen [-w 言葉,言葉,...] [-l 言語]
        |#   話した内容を文字にして標準出力へ返す。認識は**端末の中だけ**で行い、声をネットへ出さない。
        |#   Android 12 以降と、端末内の音声認識 (その言語のオフライン認識のデータ) が必要。
        |#   z2term を画面に出したまま話す (Android は画面に出ているアプリにしかマイクを渡さない)。
        |#   黙ると終わる。初めて実行したときにマイクの許可を尋ねる。
        |#   -w <言葉>  決まった言葉だけ受け付け、当たった言葉を出す (カンマ区切り)。
        |#              認識の 1 位だけでなく、ほかの候補も見て判定する。
        |#   -l <言語>  認識する言語 (例: ja-JP, en-US。既定は端末の言語)
        |# 終了コード: 0 = 成功 / 1 = 聞き取れない・どの言葉でもない / 2 = 使えない (理由は標準エラーへ)
        |#   cmd=${d}(z2-listen -w "次の曲,止めて") || exit
    """.trimMargin()
    )

    open val listenUsage: String get() = t(
            en = "usage: z2-listen [-w word,word,...] [-l lang]",
            ja = "usage: z2-listen [-w 言葉,言葉,...] [-l 言語]"
        )

    open val listenPrompt: String get() = t(
            en = "Speak now… (stops when you go quiet)",
            ja = "話してください…（黙ると終わります）"
        )

    open val listenNeedsAndroid12: String get() = t(
        en = "z2-listen needs Android 12 or later (before that, recognition cannot be kept on the device).",
        ja = "z2-listen は Android 12 以降が必要です（それより前は、端末の中だけで認識すると指定できません）。"
    )

    open val listenBusy: String get() = t(
        en = "Already listening (another z2-listen is running).",
        ja = "すでに聞き取り中です（別の z2-listen が動いています）。"
    )

    open val listenEngineBusy: String get() = t(
        en = "The device's speech recognition is busy (another app may be using it). Try again later.",
        ja = "端末の音声認識が使用中です（別のアプリが使っているかもしれません）。少し待ってからもう一度試してください。"
    )

    open val listenEngineStuck: String get() = t(
        en = "Speech recognition isn't responding. If this keeps happening, force stop the device's recognition app (such as Android System Intelligence) in Android Settings › Apps.",
        ja = "音声認識から応答がありません。続くときは、Android の設定 › アプリ で端末の音声認識のアプリ（Android System Intelligence など）を強制停止してください。"
    )

    open val listenStopped: String get() = t(
        en = "Stopped listening.",
        ja = "聞き取りをやめました。"
    )

    open val listenNoOnDevice: String get() = t(
        en = "This device has no on-device speech recognition. z2-listen never sends your voice to the network, so it cannot run here.",
        ja = "この端末には端末内の音声認識がありません。z2-listen は声をネットへ出さないため、ここでは使えません。"
    )

    open val listenNoPermission: String get() = t(
        en = "Microphone access is not allowed. Run z2-listen again with z2term on screen to be asked, or allow it in Android settings.",
        ja = "マイクが許可されていません。z2term を画面に出したままもう一度実行すると許可を尋ねます（Android の設定からも許可できます）。"
    )

    open val listenNotShown: String get() = t(
        en = "Bring z2term to the screen and try again (Android gives the microphone only to the app on screen).",
        ja = "z2term を画面に出してからもう一度実行してください（Android は画面に出ているアプリにしかマイクを渡しません）。"
    )

    open val listenNoMic: String get() = t(
        en = "Could not use the microphone (z2term left the screen, or another app is recording).",
        ja = "マイクを使えませんでした（z2term が画面から外れた・別のアプリが録音中）。"
    )

    open val listenNoLanguage: String get() = t(
        en = "The on-device speech data for this language is not installed. Download it in the device's voice input settings (offline speech recognition).",
        ja = "この言語の端末内認識のデータがありません。端末の音声入力の設定（オフライン音声認識）でダウンロードしてください。"
    )

    open val listenNoSpeech: String get() = t(
        en = "Nothing was heard.",
        ja = "聞き取れませんでした。"
    )

    open val listenTimedOut: String get() = t(
        en = "Timed out.",
        ja = "時間切れです。"
    )

    open fun listenNoMatch(heard: String): String = t(
        en = "None of the words (heard: $heard)",
        ja = "待っている言葉ではありません（聞こえた言葉: $heard）"
    )

    open fun listenFailed(code: Int): String = t(
        en = "Speech recognition failed (error $code).",
        ja = "音声認識に失敗しました（エラー $code）。"
    )

    // --- z2-screen ---

    open val screenHelp: String get() = t(
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
    """.trimMargin()
    )

    open val screenUsage: String get() = t(
            en = "usage: z2-screen keepon <N|Ns|Nm|Nh> | keepon off | status",
            ja = "usage: z2-screen keepon <N|Ns|Nm|Nh> | keepon off | status"
        )

    // --- z2-tile ---

    open val tileHelp: String get() = t(
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
        |#   Ordinary pairs remember successful toggles. Direct z2-torch assignments follow Android
        |#   observations, including external changes. z2-screen keepon follows its actual active setting.
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
        |#   通常の組は切替状態を記憶します。z2-torchの直接割当はAndroidの実態に連動し、
        |#   外部からのON/OFFも反映します。z2-screen keeponも実際の有効状態に連動します。
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
    """.trimMargin()
    )

    open val tileUsage: String get() = t(
            en = "usage: z2-tile set <1-$tiles> <macro.sh|command...> [--off <command...>] [-l label] " +
                "[-i drawing] | add <1-$tiles> | list | clear <1-$tiles|all>",
            ja = "usage: z2-tile set <1-$tiles> <マクロ.sh|コマンド...> [--off <コマンド...>] [-l 表示名] " +
                "[-i 絵の名前] | add <1-$tiles> | list | clear <1-$tiles|all>"
        )

    /**
     * `z2-tile set -i <名前>` に無い絵の名前を書いたとき (0.8.357)。
     *
     * ⚠ **割り当てる前に断る**ので「割り当ては済んだ」とは書かない。半端に割り当てが残ると、
     * 打ち間違いに気付かないまま違う絵のタイルが置かれる。
     */
    open fun tileNoSuchIcon(name: String): String = t(
            en = "z2-tile: no such drawing: $name (list them with z2-icon sample). Nothing was assigned.",
            ja = "z2-tile: その絵はありません: $name (一覧は z2-icon sample)。割り当ては行いませんでした。"
        )

    /** `z2-tile add <枠>` で、割り当ての無い枠を指したとき。 */
    open fun tileAddEmpty(n: Int): String = t(
            en = "z2-tile: slot $n has nothing on it yet (assign it first: z2-tile set $n <...>)",
            ja = "z2-tile: 枠 $n はまだ空です (先に z2-tile set $n <…> で割り当ててください)"
        )

    /**
     * 追加を頼めなかったとき。⚠ **Android 12 以前にはこの口が無い** (タイルの追加を頼む API は
     * Android 13 から) し、**z2term が前面にいない**ときも OS が断る。どちらも
     * **割り当て自体は済んでいる**ので、並べ方だけを案内する。
     */
    open val tileAddUnsupported: String get() = t(
            en = "z2-tile: cannot ask to add the tile here (needs Android 13, with z2term in the foreground). " +
                "The slot is assigned — place it from the pencil (edit) screen of the quick settings panel.",
            ja = "z2-tile: ここでは追加を頼めません (Android 13 以降 + z2term が前面にいることが要ります)。" +
                "割り当ては済んでいるので、クイック設定パネルの鉛筆(編集)から並べてください。"
        )

    /** 追加を頼めたとき。⚠ **答えるのは利用者**なので「追加した」とは言い切らない。 */
    open val tileAddAsked: String get() = t(
            en = "asked Android whether to add the tile — answer the dialog. " +
                "(Nothing showed up? Place it from the edit screen instead.)",
            ja = "追加してよいか Android に聞いています。出たダイアログで答えてください。" +
                "(何も出なければ、編集画面から並べてください)"
        )

    // --- z2-icon ---

    /** 選べる一辺 ([IconStore.GRIDS])。⚠ 文言へ数を書き写さない — 増やしたときにここだけ古くなる。 */
    protected val grids: String get() = IconStore.GRIDS.joinToString("|")

    /** 何も指定しないときの一辺。 */
    protected val grid: Int get() = IconStore.DEFAULT_GRID

    /**
     * `z2-icon` のヘルプ。
     *
     * ⚠ **やりたいこと順に並べる**。以前はサブコマンドを 8 つ並べてから注意書きを続けており、
     * 「まず何を打てばよいか」が読み取れなかった (利用者の指摘)。いちばん多い用途 (一覧から
     * 選んで入れる) を先頭に置き、残りを「入れる / 自分の絵を残す / 描き方 / 注意」へ分ける。
     */
    open val iconHelp: String get() = t(
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
    """.trimMargin()
    )

    open val iconUsage: String get() = t(
            en = "usage: z2-icon pick <notify|1-$tiles> | sample [name|target name] | " +
                "edit <notify|1-$tiles> | set <notify|1-$tiles> [file|-] | " +
                "save <notify|1-$tiles> <name> | forget <name> | " +
                "show <notify|1-$tiles> | auto <1-$tiles|all> | clear <notify|1-$tiles|all> | " +
                "grid [$grids] | scale <notify|1-$tiles> <$grids> | list [-p]",
            ja = "usage: z2-icon pick <notify|1-$tiles> | sample [名前|対象 名前] | " +
                "edit <notify|1-$tiles> | set <notify|1-$tiles> [ファイル|-] | " +
                "save <notify|1-$tiles> <名前> | forget <名前> | " +
                "show <notify|1-$tiles> | auto <1-$tiles|all> | clear <notify|1-$tiles|all> | " +
                "grid [$grids] | scale <notify|1-$tiles> <$grids> | list [-p]"
        )

    /** `z2-icon set` にファイルを指定したが無かったときの文言 (後ろにファイル名が付く)。 */
    open val iconNoSuchFile: String get() = t(en = "no such file:", ja = "そのファイルはありません:")

    /** `z2-icon edit` で何も変えずに終わったときの文言。 */
    open val iconEditUnchanged: String get() = t(en = "unchanged.", ja = "変更なしで終了しました。")

    /** `z2-icon pick` が番号を尋ねるときの文言 (行末で入力を待つので改行を入れない)。 */
    open val iconPickPrompt: String get() = t(en = "number (or name), blank to cancel: ", ja = "番号 (または名前) を入力 (空欄で中止): ")

    /** `z2-icon pick` を空欄で抜けたときの文言。 */
    open val iconPickCancelled: String get() = t(en = "cancelled.", ja = "中止しました。")

    /** `z2-icon edit` で開くエディタが見つからないときの文言。 */
    open val iconNoEditor: String get() = t(
            en = "no editor found. Set ${d}EDITOR, or use: z2-icon set <target> <file>",
            ja = "エディタが見つかりません。${d}EDITOR を設定するか z2-icon set <対象> <ファイル> をお使いください"
        )

    /** 置き場に無いマクロ名を弾くときの文言 (後ろに名前が付く)。 */
    open val tileNoSuchMacro: String get() = t(
            en = "no such macro in ~/.z2term/macros/ (use a full path to run it as a command):",
            ja = "そのマクロは ~/.z2term/macros/ にありません (コマンドとして走らせるならフルパスで):"
        )

    // --- z2-noti ---

    open val notiHelp: String get() = t(
        en = """
        |# z2-noti trace start [package] / trace dump / trace stop
        |# Trace: enable capture first; 5 minutes, latest 128 events, memory only.
        |# Field types and UTF-16 lengths, never text values. dump/stop return JSON; start resets it.
        |# z2-noti list  … the notifications currently on screen, as TSV
        |#                 (key / package / app name / title / body)
        |# Reading only. There is deliberately no way to press or dismiss a notification:
        |# that would also press other apps' pay and send buttons.
        |# Needs notification access (Settings > Permissions and notifications).
        |# See also: z2-when notify:otp / notify:pkg=<part> / notify:contains=<part>
    """.trimMargin(),
        ja = """
        |# z2-noti trace start [package] / trace dump / trace stop
        |# 診断: 通知検知ONが前提。5分間・最新128件をメモリ内だけに保持します。
        |# 型とUTF-16文字数を記録し、本文の値は保持しません。dump/stopでJSON、startで初期化。
        |# z2-noti list  … いま出ている通知を TSV で表示
        |#                 (key / パッケージ / アプリ名 / タイトル / 本文)
        |# 読むだけです。通知のボタンを「押す」「消す」は意図的に用意していません
        |# (他アプリの決済・送信ボタンまで押せてしまうため)。
        |# 通知アクセスの許可が要ります (設定 › 権限・通知 › 通知検知)。
        |# 併せて: z2-when notify:otp / notify:pkg=<部分> / notify:contains=<部分>
    """.trimMargin()
    )

    open val notiUsage: String get() = t(en = "usage: z2-noti list | trace start [package] | trace dump | trace stop", ja = "usage: z2-noti list | trace start [package] | trace dump | trace stop")

    // --- z2-alarm ---

    open val alarmHelp: String get() = t(
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
    """.trimMargin()
    )

    open val alarmNoDate: String get() = t(en = "z2-alarm: no usable date command", ja = "z2-alarm: date が使えません")

    // --- z2-session ---

    open val sessionHelp: String get() = t(
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
    """.trimMargin()
    )

    open val sessionUsage: String get() = t(
            en = "usage: z2-session list | new [name] | send <tab> <text>... [--enter] | " +
                "key <tab> <key>... | key <tab> --raw <bytes> | capture [tab] [--all] | " +
                "attach <tab> | close <tab>",
            ja = "usage: z2-session list | new [名前] | send <先> <文字列>... [--enter] | " +
                "key <先> <キー>... | key <先> --raw <バイト列> | capture [先] [--all] | " +
                "attach <先> | close <先>"
        )

    // --- z2-session key (別のタブへキーを送る) ---

    /** 送るキーが 1 つも無い。 */
    open val keyNothing: String get() = t(en = "z2-session key: no key given", ja = "z2-session key: 送るキーがありません")

    /** `--raw` の後ろが空。 */
    open val keyRawEmpty: String get() = t(
            en = "z2-session key: --raw needs the bytes to send",
            ja = "z2-session key: --raw の後ろにバイト列がありません"
        )

    /** 表に無いキー名。⚠ **どこを見れば分かるか**まで書く。 */
    open val keyUnknown: String get() = t(
        en = "z2-session key: unknown key (see 'z2-session -h' for the list):",
        ja = "z2-session key: そんなキー名はありません ('z2-session -h' に一覧):"
    )

    /** `\\xHH` として読めなかった。 */
    open val keyBadEscape: String get() = t(
            en = "z2-session key: cannot read the escape:",
            ja = "z2-session key: 読めないエスケープ:"
        )

    /**
     * Shift 付きを断る文言。⚠ **なぜ送れないかと、代わりに何を書けばよいか**を必ず出す —
     * 「送れません」だけだと、書き方が悪いのか端末の話なのか区別が付かない。
     */
    open fun keyShiftNotDistinguishable(asWritten: String, equivalentTo: String): String = t(
        en = "z2-session key: the terminal cannot tell Shift apart, so '$asWritten' would be " +
            "the very same bytes as '$equivalentTo'. Write '$equivalentTo' if that is what you meant.",
        // ⚠ 行の分け目は**句点**に置く (読点や助詞で割ると lint の TextConcatSpace が
        // 「空白が抜けているのでは」と拾う。日本語では誤検知だが、警告 0 を保つ方を採る)。
        ja = "z2-session key: 端末は Shift を区別できないので、'$asWritten' は '$equivalentTo' とまったく同じバイトになります。" +
                "それでよければ '$equivalentTo' と書いてください。"
    )

    // --- z2-session attach (タブに繋ぎっぱなしにする) ---

    /**
     * 繋ぎ先が見つからない。⚠ **どう調べればよいか**まで書く
     * (`key` の keyUnknown と同じ約束)。
     */
    open fun attachNoSuchTab(target: String): String = t(
        en = "no such tab: '$target' (run 'z2-session list' to see the tabs)",
        ja = "そんなタブはありません: '$target' ('z2-session list' で一覧が出ます)"
    )

    /** GUI タブには繋げない (PTY が無い)。 */
    open val attachNotTerminal: String get() = t(
        en = "that is a GUI tab, which has no shell to attach to. Pick a terminal tab.",
        ja = "それは GUI タブなのでシェルがありません。端末タブを指してください。"
    )

    /**
     * まだ起動していないタブ。⛔ **こちらから勝手に起こさない** —
     * 繋いだつもりが OS の初回ダウンロードを始める、を作らない。
     */
    open val attachNotStarted: String get() = t(
        en = "that tab has not started yet (marked '?' in list). Open it once in the app, then attach.",
        ja = "そのタブはまだ起動していません (list の印が '?')。アプリで一度開いてから繋いでください。"
    )

    /** プロセスが終わっているタブ。 */
    open val attachExited: String get() = t(
        en = "that tab has already exited. Its screen can still be read with 'z2-session capture'.",
        ja = "そのタブはもう終わっています。画面は 'z2-session capture' で取り出せます。"
    )

    /**
     * 自分自身のタブ。⛔ **繋がせない。** 繋ぐとそのタブの出力がそのタブへ書き戻され、
     * それがまた出力として送られて**止まらなくなる**。
     * ⚠ 「できません」で終えず、**なぜ止められないのか**まで書く (この断りだけが唯一の説明)。
     */
    open val attachSelf: String get() = t(
        en = "that is the tab you are typing in. Attaching a tab to itself makes its own output " +
            "come back as input forever, so it cannot be undone. Pick another tab.",
        ja = "それは今あなたが打っているタブ自身です。自分に繋ぐと、そのタブの出力がそのまま" +
            "自分へ戻り続けて止められなくなります。別のタブを指してください。"
    )

    /**
     * 遠回りで輪になる指定 (A から B へ繋いだ状態で、その中から A へ繋ぐ)。
     * [attachSelf] と同じ暴走の遠回り版。
     */
    open val attachLoop: String get() = t(
        en = "that tab is already attached back to this one, so the two would feed each other " +
            "forever. Detach one of them first (Ctrl+]).",
        ja = "そのタブは既にこちら側へ繋がっているので、互いに送り合って止まらなくなります。" +
            "どちらかを先に外してください (Ctrl+])。"
    )

    // --- z2-server ---

    open val serverHelp: String get() = t(
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
    """.trimMargin()
    )

    open val serverUsage: String get() = t(
            en = "usage: z2-server list | start <server> | stop <server> | status [<server>]",
            ja = "usage: z2-server list | start <サーバー> | stop <サーバー> | status [<サーバー>]"
        )

    /** 名前 / 番号 / id のどれにも当たらなかった。⚠ 一覧の出し方まで書く (次に何をすればよいか)。 */
    open val serverNotFound: String get() = t(
            en = "z2-server: no such server (see 'z2-server list'):",
            ja = "z2-server: そのサーバーはありません ('z2-server list' で一覧):"
        )

    /** 同じ名前が複数あった。⚠ id で指定し直せると分かるように。 */
    open val serverAmbiguous: String get() = t(
            en = "z2-server: the name matches more than one server; use the id from 'z2-server list':",
            ja = "z2-server: 同じ名前のサーバーが複数あります。'z2-server list' の id で指定してください:"
        )

    /** 1 件も登録が無い。 */
    open val serverNone: String get() = t(
            en = "z2-server: no servers registered yet (add one in the app: 📜 -> Servers).",
            ja = "z2-server: サーバーがまだ 1 件も登録されていません (アプリの 📜 → サーバー で登録します)。"
        )

    /**
     * 省電力モード中の警告 (F-5)。⚠ **起動そのものは成功している**ので失敗にはしない。
     * 黙って上げると「起動したのにつながらない」を繰り返すので、その場で理由を出す。
     */
    open val serverLowPowerWarn: String get() = t(
        en = "z2-server: note - low-power mode is on, so no WakeLock/WifiLock is held. " +
            "The server may stop answering while the screen is off " +
            "(Settings -> Automation -> Background process protection).",
        ja = "z2-server: 注意 — 省電力モードが ON のため WakeLock/WifiLock を握りません。" +
            "画面消灯中は応答しなくなることがあります (⚙設定 → 自動化 → バックグラウンドのプロセス保護)。"
    )

    // --- z2-when ---

    open val whenHelp: String get() = t(
        en = """
        |# z2-when <trigger> run <cmd...>        … register a rule
        |#   triggers: charge:start | charge:stop  (needs detection ON)
        |#            battery:below=N | battery:above=N  (needs detection ON)
        |#            battery:temp>N | battery:temp<N  … battery temperature (°C) crossed N (needs detection ON)
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
        |#            file:changed=<dir>[,ext=<ext>] | file:deleted=<dir>[,ext=<ext>]
        |#                                        … a file there was rewritten / went away (needs detection ON)
        |#            bt:connect | bt:disconnect | bt:connect=<name> | bt:disconnect=<name>
        |#                                        … a Bluetooth device connected / disconnected (name: part of it, or its address)
        |#                                        (needs detection ON and, on Android 12+, the Nearby devices permission)
        |#            app:open=<app> | app:close=<app>  … that app came to the front / left it (package or app name, part of it)
        |#                                        (needs z2term turned on in Accessibility; works with detection OFF)
        |#            steps:reach=N               … today's step count reached N (detection ON + Physical activity permission)
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
        |#   answer='<prompt>=><reply>' … when the command asks this on the terminal, type the reply
        |#                             and Enter (repeatable; e.g. answer='[y/N]=>y'). Matches the end
        |#                             of the last output line.
        |#   secret='<prompt>'       … same for a hidden reply (a password). Its content is entered in
        |#                             the app's rule editor and kept encrypted inside the app.
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
        |# For battery:temp you get Z2_WHEN_TEMP (°C). For bt: Z2_WHEN_BT_NAME / Z2_WHEN_BT_ADDR.
        |# For app: Z2_WHEN_APP_PKG (the app in front now) / Z2_WHEN_APP_PREV (the one before).
        |# For steps: Z2_WHEN_STEPS (today's steps). For file: also Z2_WHEN_FILE_ACTION (new|changed|deleted).
        |# app_installed / app_removed / app_updated give Z2_WHEN_PKG, timezone_changed gives Z2_WHEN_TZ,
        |# usb_attached / usb_detached give Z2_WHEN_USB (name) / Z2_WHEN_USB_ID (vendor:product).
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
        |#            battery:temp>N | battery:temp<N  … 電池の温度 (℃) が N を跨いだとき (検知 ON が前提)
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
        |#            file:changed=<フォルダ>[,ext=<拡張子>] | file:deleted=<フォルダ>[,ext=<拡張子>]
        |#                                        … そこのファイルが書き換わった / 消えたとき (検知 ON が前提)
        |#            bt:connect | bt:disconnect | bt:connect=<名前> | bt:disconnect=<名前>
        |#                                        … Bluetooth 機器が繋がった / 切れたとき (名前はその一部、またはアドレス)
        |#                                        (検知 ON と、Android 12 以降は「付近のデバイス」の許可が前提)
        |#            app:open=<アプリ> | app:close=<アプリ>  … そのアプリが前面に来た / 前面から離れたとき
        |#                                        (パッケージ名かアプリ名の一部。ユーザー補助で z2term を ON にするのが前提。検知 OFF でも動く)
        |#            steps:reach=N               … 今日の歩数が N に届いたとき (検知 ON と「身体活動」の許可が前提)
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
        |#   answer='<文言>=><返事>'  … コマンドが端末でこう聞いてきたら、返事と Enter を送る
        |#                             (何個でも書ける。例: answer='[y/N]=>y')。出力の最後の行の
        |#                             終わりと比べる
        |#   secret='<文言>'         … 入力を隠す返事 (パスワード) 用。中身はアプリのルール編集画面で
        |#                             入れる (アプリの中に暗号化して保存。ルールファイルには書かない)
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
        |# battery:temp のときは Z2_WHEN_TEMP (℃)。bt: のときは Z2_WHEN_BT_NAME / Z2_WHEN_BT_ADDR。
        |# app: のときは Z2_WHEN_APP_PKG (今前面のアプリ) / Z2_WHEN_APP_PREV (直前のアプリ)。
        |# steps: のときは Z2_WHEN_STEPS (今日の歩数)。file: では Z2_WHEN_FILE_ACTION (new|changed|deleted) も入る。
        |# app_installed / app_removed / app_updated では Z2_WHEN_PKG、timezone_changed では Z2_WHEN_TZ、
        |# usb_attached / usb_detached では Z2_WHEN_USB (機器名) / Z2_WHEN_USB_ID (製造元:製品) が入る。
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
    """.trimMargin()
    )

    open val whenPaused: String get() = t(
            en = "Automatic runs paused (z2-when resume to start again)",
            ja = "自動実行を一時停止しました (z2-when resume で再開)"
        )

    open val whenResumed: String get() = t(en = "Automatic runs resumed", ja = "自動実行を再開しました")

    open val whenNoFires: String get() = t(en = "(nothing has fired yet)", ja = "(まだ発火していません)")

    open val whenPausedNote: String get() = t(
            en = "# paused (z2-when resume to start again)",
            ja = "# 一時停止中 (z2-when resume で再開)"
        )

    open val whenNoLog: String get() = t(en = "(no log yet)", ja = "(ログはまだありません)")

    open val whenWriteFailed: String get() = t(en = "z2-when: could not write the rule", ja = "z2-when: 書き込みに失敗しました")

    /** `if=` に知らないキーを書いたとき。**キー名は呼び元がこの後ろに足す**。 */
    open val whenUnknownIfKey: String get() = t(
            en = "z2-when: unknown if= key (z2-state lists what you can use):",
            ja = "z2-when: if= に書けない条件です (使えるものは z2-state が出す項目):"
        )

    /** 知らない種別のトリガー (`:` の手前) を書いたとき。**種別は呼び元がこの後ろに足す**。 */
    open val whenUnknownTrigger: String get() = t(
            en = "z2-when: unknown trigger (z2-when with no arguments lists them):",
            ja = "z2-when: そんなきっかけはありません (一覧は引数なしの z2-when で出ます):"
        )

    /** 種別は合っているが引数の書き方が違うとき。**トリガー全体は呼び元がこの後ろに足す**。 */
    open val whenBadTriggerSpec: String get() = t(
            en = "z2-when: that trigger does not take this argument:",
            ja = "z2-when: そのきっかけにその書き方はできません:"
        )

    open val whenPausedWarn: String get() = t(
            en = "note: automatic runs are paused (z2-when resume to start again)",
            ja = "注意: 自動実行は一時停止中です (z2-when resume で再開)"
        )

    /**
     * `run` に改行が入っていたので空白へ直したとき。
     *
     * ルールファイルは 1 行 1 項目なので、改行入りのまま書くと 2 行目以降が別の項目として
     * 読まれ、**途中で切れたコマンド**が黙って登録される（折り返して貼り付けると起きる）。
     * 弾くのではなく直して通すが、直したことは必ず伝える。
     */
    open val whenRunJoined: String get() = t(
            en = "note: line breaks in the command were turned into spaces (a rule reads one line only)",
            ja = "注意: コマンドの改行を空白に直しました (ルールは 1 行しか読みません)"
        )

    /**
     * `z2-when events` が出すイベント名の一覧。**名前 (1 列目) は言語を問わず同じ**で、
     * 説明と注記だけを訳す（名前はルールに書く識別子なので訳してはいけない）。
     */
    open val whenEventList: String get() = t(
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
        |bt_connected           a Bluetooth device connected [detection ON]
        |bt_disconnected        a Bluetooth device disconnected [detection ON]
        |powersave_on           battery saver on          [detection ON]
        |powersave_off          battery saver off         [detection ON]
        |dnd_on                 Do Not Disturb on         [detection ON]
        |dnd_off                Do Not Disturb off        [detection ON]
        |app_installed          an app was installed      [detection ON]
        |app_removed            an app was uninstalled    [detection ON]
        |app_updated            an app was updated        [detection ON]
        |timezone_changed       the time zone changed     [detection ON]
        |usb_attached           a USB device was plugged in [detection ON]
        |usb_detached           a USB device was unplugged [detection ON]
        |boot                   the device started up     [always]
        |alarm                  a z2-alarm fired          [always]
        |notify_action          a notification button     [always]
        |unlock_failed          failed to unlock          [always, needs setup]
        |unlock_succeeded       unlocked after a failure  [always, needs setup]
        |voice                  a voice command was heard [always, from the tile or assistant]
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
        |bt_connected           Bluetooth 機器が繋がった [検知 ON]
        |bt_disconnected        Bluetooth 機器が切れた  [検知 ON]
        |powersave_on           省電力モードにした      [検知 ON]
        |powersave_off          省電力モードを解除した  [検知 ON]
        |dnd_on                 おやすみモードにした    [検知 ON]
        |dnd_off                おやすみモードを解除した [検知 ON]
        |app_installed          アプリを入れた          [検知 ON]
        |app_removed            アプリを消した          [検知 ON]
        |app_updated            アプリが更新された      [検知 ON]
        |timezone_changed       タイムゾーンが変わった  [検知 ON]
        |usb_attached           USB 機器を挿した        [検知 ON]
        |usb_detached           USB 機器を抜いた        [検知 ON]
        |boot                   端末が起動した          [常に]
        |alarm                  z2-alarm が鳴った       [常に]
        |notify_action          通知のボタンを押した    [常に]
        |unlock_failed          ロック解除に失敗した    [常に・要設定]
        |unlock_succeeded       失敗のあと解除できた    [常に・要設定]
        |voice                  声で操作で話した        [常に・タイルかアシスタントから]
    """.trimMargin()
    )

    /** `z2-when events` の一覧の前に置くコメント（どちらの段が検知 ON を要るか）。 */
    open val whenEventListNote: String get() = t(
        en = """
        |    # Names you can put in event:<name>. Same order as they appear in events.jsonl.
        |    # The upper group needs detection ON (Settings > resident servers & automation),
        |    # the lower group is armed by you, so it works with detection OFF.
    """.trimMargin(),
        ja = """
        |    # event:<名前> に書ける名前。events.jsonl に出るものと同じ並び。
        |    # 上段は検知 ON が前提 (設定 › 常駐サーバー・自動化 › システムイベント検知)、
        |    # 下段は自分で仕掛けるものなので検知 OFF でも動く。
    """.trimMargin()
    )

    // --- z2-update (アプリ自身の入れ替え) ---

    open val updateHelp: String get() = t(
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
    """.trimMargin()
    )

    open val updateUsage: String get() = t(
            en = "usage: z2-update [--check] [--keep] [--dir <folder>]",
            ja = "usage: z2-update [--check] [--keep] [--dir <フォルダ>]"
        )

    /** 最新だった。⚠ **版名を必ず出す** (「最新です」だけだと何と比べたのか分からない)。 */
    open fun updateUpToDate(current: String): String = t(
            en = "z2-update: $current is the latest version.",
            ja = "z2-update: $current が最新です。"
        )

    /** 新版が見つかった (1 行目)。 */
    open fun updateFound(current: String, latest: String, size: String): String = t(
            en = "z2-update: $current -> $latest ($size)",
            ja = "z2-update: $current → $latest ($size)"
        )

    /**
     * 問い合わせに入る前に CLI が出す 1 行。
     * ⚠ **黙って数十秒待たせない** — 通信とダウンロードの間、端末には何も出ない。
     */
    open val updateChecking: String get() = t(
        en =         "z2-update: looking for a newer version ...",
        ja =         "z2-update: 新しい版があるか見に行きます ..."
    )

    /**
     * 確認画面を出した。⚠ **「更新しました」と書かない** — 押すまで入っていない。
     */
    open val updateHandedToInstaller: String get() = t(
        en = "z2-update: the install screen is up on the device — approve it to finish.",
        ja = "z2-update: 端末にインストール画面を出しました。承認すると入れ替わります。"
    )

    /** リリースに APK が付いていない。 */
    open fun updateNoApk(url: String): String = t(
        en = "z2-update: that release has no .apk attached. Get it from the release page: $url",
        ja = "z2-update: そのリリースに .apk が付いていません。リリースページから入れてください: $url"
    )

    /** 「不明なアプリのインストール」が未許可。⚠ **どこで許すか**まで書く。 */
    open val updateNeedPermission: String get() = t(
        en = "z2-update: allow \"Install unknown apps\" for z2term first " +
            "(Settings > Apps > Special app access > Install unknown apps, or the button in z2term's Settings).",
        ja = "z2-update: 先に z2term へ「不明なアプリのインストール」を許可してください " +
            "(設定 › アプリ › 特別なアプリアクセス › 不明なアプリのインストール。z2term の ⚙設定にもボタンがあります)。"
    )

    /** 配布元から入れた版なので断る。 */
    open val updateManagedByStore: String get() = t(
        en = "z2-update: this build was installed from a store (F-Droid / Play). Update it there.",
        ja = "z2-update: この版は配布元 (F-Droid / Play) から入っています。更新はそちらから行ってください。"
    )

    /** 通信・保存・入れ替えのどこかで失敗した。 */
    open fun updateFailed(reason: String): String = t(en = "z2-update: failed: $reason", ja = "z2-update: 失敗しました: $reason")
}
