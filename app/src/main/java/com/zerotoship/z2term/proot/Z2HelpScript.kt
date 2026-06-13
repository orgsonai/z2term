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
        |
        |[Phone features]
        |  z2-notify "title" [text]       Post a notification
        |  z2-toast "message"             Toast (short on-screen message)
        |  z2-share "text"                Hand text to Android's share sheet
        |  z2-open <url|path>             Open a URL/file in the default app
        |  z2-clip get | set [text]       Get / set the clipboard (set: stdin if no arg)
        |  z2-battery                     Battery level / charging state (JSON)
        |  z2-vibrate [ms]                Vibrate (default 200ms)
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
        |  z2scan self                    Self-check this device/localhost (z2scan help)
        |  z2scan net|host|cve            nmap/lynis/trivy on localhost (remote needs opt-in)
        |
        |[Help]
        |  z2help | z2term                This list
        |
        |More: 'z2adb help', run 'z2gui' with no args, or HANDBOOK section 11.
    """ else """
        |[版数・情報]
        |  z2version [--short]            アプリ版数・実行エンジン・OS(ディストロ)・kernel
        |
        |[スマホの機能を呼ぶ]
        |  z2-notify "タイトル" [本文]    通知を出す
        |  z2-toast "メッセージ"          トースト(画面下の短いメッセージ)
        |  z2-share "テキスト"            Android の共有メニューに渡す
        |  z2-open <URL かパス>           URL/ファイルを既定アプリで開く
        |  z2-clip get | set [テキスト]   クリップボード取得/設定(set は引数なしで標準入力)
        |  z2-battery                     電池残量・充電状態(JSON)
        |  z2-vibrate [ミリ秒]            バイブ(既定 200ms)
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
        |  z2scan self                    自端末/localhost の自己診断(z2scan help)
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
