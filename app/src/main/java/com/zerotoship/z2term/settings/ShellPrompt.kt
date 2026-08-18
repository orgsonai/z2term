package com.zerotoship.z2term.settings

import android.util.Log
import java.io.File

/**
 * シェルのプロンプト (PS1) を**サンプルから作って rc ファイルへ書き込む**仕組み (0.8.364・要望)。
 *
 * 素の rootfs のプロンプトは distro 任せ (Alpine の ash なら `localhost:~#` だけ) で、
 * 変えるには自分で rc を書くしかなかった。設定画面で **シェルとサンプルを選ぶ → 中身が出る →
 * その場で直せる → 適用** までを閉じるためのもの。
 *
 * ⚠ **アプリの設定として抱え込まず、rootfs の rc ファイルに書く**。後から `vi ~/.bashrc` で
 * 直せることに意味があるので、アプリ側は「書き込む」までを担当し、真実は常にファイルにある。
 *
 * ⚠ 書き込みは [PROMPT_MARKER] で囲んだブロックの**差し替え**。`ProotLauncher` の
 * `appendOnceWithMarker` (履歴 / OSC7 / PATH) は「一度書いたら触らない」だが、プロンプトは
 * **選び直して何度でも適用する**ものなので、同じ作法では 2 回目が効かない。
 */
object ShellPrompt {

    private const val TAG = "ShellPrompt"

    /** 差し替えの目印。ユーザーが自分で書いた部分と混ざらないよう、この間だけを入れ替える。 */
    const val MARKER_BEGIN = "# >>> z2term prompt >>>"
    const val MARKER_END = "# <<< z2term prompt <<<"

    /** 対象シェル。rc ファイルと PS1 の書き方がそれぞれ違う。 */
    enum class Shell(val id: String, val label: String, val rcPath: String) {
        /** busybox ash / dash など POSIX sh。⚠ `\[ \]` も `%F{}` も使えない。 */
        SH("sh", "sh", "root/.ashrc"),
        BASH("bash", "bash", "root/.bashrc"),
        ZSH("zsh", "zsh", "root/.zshrc");

        /** 端末から見たパス (画面に出す用)。 */
        val displayPath: String get() = "~/" + rcPath.removePrefix("root/")

        companion object {
            fun of(id: String): Shell = entries.firstOrNull { it.id == id } ?: SH
        }
    }

    /**
     * サンプル。
     *
     * ⚠ **見本は「そのまま使える出来のもの」を置く**。素っ気ない `user@host:~$` だけを並べても、
     * 結局みんな自分で書き直すことになり、見本を置いた意味が無い (利用者の指摘)。
     * 罫線・2 段・終了ステータスで色が変わる記号まで含めて、**選んだだけで仕上がっている**状態にする。
     */
    enum class Preset(val id: String) {
        /** 記号だけ。幅を食わないので、狭い画面や貼り付け用に。 */
        PLAIN("plain"),
        /** `user@host:~` の下に `$`。2 段で、打つ場所が常に左端から始まる。 */
        USER_HOST("user_host"),
        /** `[時刻] ~` の下に `❯`。⚠ **直前のコマンドが失敗したら ❯ が赤くなる**。 */
        ARROW("arrow"),
        /** `╭─[user]─[~]` / `╰─⚡`。丸い罫線で囲む。 */
        BOX("box"),
        /** `┌─[~]` / `└─$`。角の罫線。パスだけを大きく見せる。 */
        BRACKET("bracket"),
        /** `┌──(user㉿host)-[~]` / `└─#`。Kali の既定そのもの (利用者が実際に使っている形)。 */
        KALI("kali"),
        /**
         * 背景色の帯に user と path を白抜きで乗せ、区切りと右端を「くの字」(``) で見せる。
         *
         * ⚠ `` は U+E0B0 (powerline)。**同梱フォントのうち Fira Code と JetBrains Mono は
         * この字を持っている**ので、そのまま出る (cmap を実測して確認済み)。
         * IBM Plex Mono だけは持たないので、そのフォントを選んでいるときは Android の
         * フォールバックに委ねる形になる。出ないときはボックスで `▶` (U+25B6) 等へ打ち替える。
         */
        BAR("bar");

        companion object {
            fun of(id: String): Preset = entries.firstOrNull { it.id == id } ?: ARROW
        }
    }

    /**
     * サンプルの中身を組み立てる。返るのは **rc へそのまま書ける数行**で、
     * 設定画面のボックスに出してから編集できる。
     *
     * ⚠ 色の付け方がシェルごとに違う:
     *  - bash は `\[ \]` で「幅を持たない」と教える必要がある。無いと**行の折り返しがずれる**
     *    (長いコマンドを打つとプロンプトに食い込む)。
     *  - zsh は `%F{color}` / `%f`。`\[ \]` を書くとそのまま表示される。
     *  - sh (busybox ash) は `\[ \]` も `%F{}` も解さない。**エスケープ文字そのもの**を
     *    `printf` で作って変数に入れる (PS1 の中の `\033` は展開されない実装があるため)。
     */
    fun body(preset: Preset, shell: Shell, rightClock: Boolean = false): String = when (shell) {
        Shell.BASH -> bash(preset, rightClock)
        Shell.ZSH -> zsh(preset, rightClock)
        Shell.SH -> sh(preset, rightClock)
    }

    /**
     * 右端に出す日時の桁数 (`[YYYY/MM/DD HH:MM:SS]`)。
     *
     * ⚠ **端末の幅は数えない**。`COLUMNS` は sh (busybox ash) では設定されないことがあり、
     * あっても画面を回したときに更新されないので、幅から引き算する作りは**回転や分割で必ずズレる**。
     * 代わりに `ESC[999C` で「右端まで動く」(端で止まる) → `ESC[<桁>D` で戻る → 書いたら
     * `ESC[u` で元の位置へ帰る。幅を知らなくても右端に揃う。
     */
    /**
     * 帯の区切りに使う powerline の「くの字」を **rc 側で組み立てるための 1 行** (利用者の案)。
     *
     * ⚠ **文字そのものをここに書かない**。`\uE0B0` を Kotlin ソースへ実文字で埋めると、
     * 経路によっては黙って落ちて**区切りが空になる** (実際に一度そうなった)。
     * rc には `$'\ue0b0'` というエスケープのまま書き、シェルに解釈させる —
     * ソースも rc も ASCII のままなので、どこでも化けない。
     *
     * ⚠ おまけに **利用者が値を変えるだけで区切りを差し替えられる**。同梱フォントのうち
     * Fira Code と JetBrains Mono はこの字を持つ (cmap を実測) が、IBM Plex Mono は持たない。
     * 出ないときは `\u25b6` (▶) などに書き換えればよく、それがこの機能の使い方そのものになる。
     */
    private const val ARROW_VAR = "ARROW_RIGHT"

    private const val CLOCK_WIDTH = 21

    /** 右端の日時の中身 (シェル共通の書式)。 */
    private const val CLOCK_CMD = "$(date '+%Y/%m/%d %H:%M:%S')"

    // ---- bash -----------------------------------------------------------------

    private fun bash(preset: Preset, rightClock: Boolean): String {
        fun c(code: String) = "\\[\\e[${code}m\\]"
        val off = c("0")
        // ⚠ カーソル移動と日時を**まとめて** `\[ \]` に入れる。元の位置へ帰ってくるので
        //    実際の幅は 0 で、そう教えないと bash が行の折り返しを誤る。
        val clock = if (!rightClock) "" else
            "\\[\\e[s\\e[999C\\e[${CLOCK_WIDTH}D\\e[38;5;240m[$CLOCK_CMD]\\e[0m\\e[u\\]"
        // 直前のコマンドが成功したかで色を変える。⚠ `$?` は PS1 を展開する時点＝直前の結果。
        val okRed = "\\[\\e[\$( [ \$? -eq 0 ] && printf 32 || printf 31 )m\\]"
        val arrow = if (preset != Preset.BAR) "" else "$ARROW_VAR=\$'\\ue0b0'\n"
        return arrow + "PS1='" + clock + when (preset) {
            Preset.PLAIN -> "\\$ "
            Preset.USER_HOST ->
                c("1;36") + "\\u@\\h" + off + ":" + c("1;33") + "\\w" + off + "\\n\\$ "
            Preset.ARROW ->
                c("38;5;244") + "[\\t]" + off + " " + c("1;36") + "\\w" + off + "\\n" +
                    okRed + "❯" + off + " "
            Preset.BOX ->
                c("1;35") + "╭─[" + c("1;36") + "\\u" + c("1;35") + "]─[" +
                    c("1;33") + "\\w" + c("1;35") + "]\\n╰─" + c("1;32") + "⚡" + off + " "
            Preset.BRACKET ->
                c("1;34") + "┌─[" + c("1;33") + "\\w" + c("1;34") + "]\\n└─" +
                    c("1;32") + "\\$" + off + " "
            Preset.KALI ->
                c("1;34") + "┌──(" + c("1;31") + "\\u㉿\\h" + c("1;34") + ")-[" +
                    c("1;33") + "\\w" + c("1;34") + "]\\n└─" + off + "\\$ "
            Preset.BAR ->
                // 青の帯 → (境目の ) → 水色の帯 → (右端の ) → 改行して `$`。
                // 境目は「前の帯の色を前景に、次の帯の色を背景に」置くと繋がって見える。
                c("44;97") + " \\u " + c("34;46") + "\${$ARROW_VAR}" + c("46;30") + " \\w " +
                    off + c("36") + "\${$ARROW_VAR}" + off + "\\n\\$ "
        } + "'"
    }

    // ---- zsh ------------------------------------------------------------------

    /**
     * zsh。⚠ 右端の日時は **`RPROMPT` に任せる** — zsh が幅を数えて右へ寄せ、
     * 行が伸びたら自分で引っ込めてくれる。カーソルを動かす小細工より確実。
     */
    private fun zsh(preset: Preset, rightClock: Boolean): String {
        val main = when (preset) {
            Preset.PLAIN -> "PROMPT='%# '"
            Preset.USER_HOST -> "PROMPT=\$'%F{cyan}%n@%m%f:%F{yellow}%~%f\\n$ '"
            Preset.ARROW ->
                "PROMPT=\$'%F{244}[%D{%H:%M:%S}]%f %F{cyan}%~%f\\n%(?.%F{green}❯.%F{red}❯)%f '"
            Preset.BOX ->
                "PROMPT=\$'%F{magenta}╭─[%F{cyan}%n%F{magenta}]─[%F{yellow}%~%F{magenta}]\\n" +
                    "%F{magenta}╰─%F{green}⚡%f '"
            Preset.BRACKET ->
                "PROMPT=\$'%F{blue}┌─[%F{yellow}%~%F{blue}]\\n└─%f%(!.%F{red}#.%F{green}$)%f '"
            Preset.KALI ->
                "PROMPT=\$'%F{blue}┌──(%F{red}%n㉿%m%F{blue})-[%F{yellow}%~%F{blue}]\\n└─%f%# '"
            Preset.BAR ->
                "PROMPT=\$'%K{blue}%F{white} %n %K{cyan}%F{blue}\\ue0b0" +
                    "%K{cyan}%F{black} %~ %k%F{cyan}\\ue0b0%f\\n%# '"
        }
        return if (rightClock) "$main\nRPROMPT=\$'%F{240}[%D{%Y/%m/%d %H:%M:%S}]%f'" else main
    }

    // ---- sh (busybox ash) ------------------------------------------------------

    /**
     * sh (busybox ash)。
     *
     * ⚠ ash は `\u` `\h` `\w` `\$` は解釈するが、**色を `\033` と書いても展開しない**ものがある。
     * 実際の ESC を `printf` で作って変数へ入れ、二重引用符で PS1 に埋める
     * (`$(...)` は代入時に展開されないよう `\$(...)` と書く)。
     * ⚠ 幅を持たない印 (`\[ \]`) が無いので、**色を使うと長い行の折り返しが 1 文字ずれる**ことがある。
     * 位置そのものはカーソルの保存・復帰で保つ。
     */
    private fun sh(preset: Preset, rightClock: Boolean): String {
        val head = "__z2e=\$(printf '\\033')"
        val e = "\${__z2e}"
        fun c(code: String) = "$e[${code}m"
        val off = c("0")
        val clock = if (!rightClock) "" else
            "$e[s$e[999C$e[${CLOCK_WIDTH}D$e[38;5;240m[\\$(date '+%Y/%m/%d %H:%M:%S')]$e[0m$e[u"
        // ash に EUID は無いので、成否は `$?` を直に見る。⚠ `\$` で代入時の展開を止める。
        val okRed = "$e[\\$( [ \\$? -eq 0 ] && printf 32 || printf 31 )m"
        val bodyText = when (preset) {
            Preset.PLAIN -> "\\\$ "
            Preset.USER_HOST ->
                c("1;36") + "\\u@\\h" + off + ":" + c("1;33") + "\\w" + off + "\n\\\$ "
            Preset.ARROW ->
                c("38;5;244") + "[\\$(date +%H:%M:%S)]" + off + " " + c("1;36") + "\\w" + off +
                    "\n" + okRed + "❯" + off + " "
            Preset.BOX ->
                c("1;35") + "╭─[" + c("1;36") + "\\u" + c("1;35") + "]─[" +
                    c("1;33") + "\\w" + c("1;35") + "]\n╰─" + c("1;32") + "⚡" + off + " "
            Preset.BRACKET ->
                c("1;34") + "┌─[" + c("1;33") + "\\w" + c("1;34") + "]\n└─" +
                    c("1;32") + "\\\$" + off + " "
            Preset.KALI ->
                c("1;34") + "┌──(" + c("1;31") + "\\u㉿\\h" + c("1;34") + ")-[" +
                    c("1;33") + "\\w" + c("1;34") + "]\n└─" + off + "\\\$ "
            Preset.BAR ->
                c("44;97") + " \\u " + c("34;46") + "\${$ARROW_VAR}" + c("46;30") + " \\w " +
                    off + c("36") + "\${$ARROW_VAR}" + off + "\n\\\$ "
        }
        // PLAIN で時刻も無いなら ESC が要らないので、余計な行を足さない。
        val needsEsc = rightClock || preset != Preset.PLAIN
        // ⚠ busybox の printf に `\u` は無いので、UTF-8 のバイト列を 8 進で書く (EE 82 B0)。
        val arrow = if (preset != Preset.BAR) "" else
            "\n$ARROW_VAR=\$(printf '\\356\\202\\260')"
        val assign = "PS1=\"$clock$bodyText\""
        return if (needsEsc) "$head$arrow\n$assign" else "PS1='\\$ '"
    }
    /**
     * [body] を rootfs の rc へ書き込む。既に z2term のブロックがあれば**その部分だけ差し替える**。
     *
     * @return 書き込んだファイル。失敗時は null。
     */
    fun apply(rootfsDir: File, shell: Shell, body: String): File? = runCatching {
        val file = File(rootfsDir, shell.rcPath)
        file.parentFile?.mkdirs()
        val existing = if (file.exists()) file.readText() else ""
        val block = "$MARKER_BEGIN\n${body.trimEnd()}\n$MARKER_END"
        val merged = replaceBlock(existing, block)
        file.writeText(merged)
        // rootfs の中身は proot/z2root 越しに root として読む。読めない権限で書かない。
        file.setReadable(true, false)
        Log.i(TAG, "prompt written: ${file.absolutePath} (${shell.id})")
        file
    }.onFailure { Log.w(TAG, "prompt 書込失敗", it) }.getOrNull()

    /** 現在 rc に入っている z2term ブロックの中身 (マーカーの内側)。無ければ null。 */
    fun current(rootfsDir: File, shell: Shell): String? = runCatching {
        val text = File(rootfsDir, shell.rcPath).takeIf { it.isFile }?.readText() ?: return null
        val from = text.indexOf(MARKER_BEGIN)
        if (from < 0) return null
        val to = text.indexOf(MARKER_END, from)
        if (to < 0) return null
        text.substring(from + MARKER_BEGIN.length, to).trim().ifEmpty { null }
    }.getOrNull()

    /** z2term のブロックを rc から取り除く (プロンプトを distro 既定へ戻す)。 */
    fun clear(rootfsDir: File, shell: Shell): Boolean = runCatching {
        val file = File(rootfsDir, shell.rcPath)
        if (!file.isFile) return false
        val text = file.readText()
        if (!text.contains(MARKER_BEGIN)) return false
        file.writeText(replaceBlock(text, null))
        true
    }.getOrDefault(false)

    /**
     * マーカーで囲まれた領域を [block] で差し替える。[block] が null なら取り除く。
     * マーカーが無ければ末尾へ足す。⚠ **マーカーの外は 1 文字も触らない** — 利用者が自分で
     * 書いた設定が消えると、原因の分からない事故になる。
     */
    private fun replaceBlock(existing: String, block: String?): String {
        val from = existing.indexOf(MARKER_BEGIN)
        val endIdx = if (from < 0) -1 else existing.indexOf(MARKER_END, from)
        if (from < 0 || endIdx < 0) {
            if (block == null) return existing
            val sep = if (existing.isEmpty() || existing.endsWith("\n")) "" else "\n"
            return "$existing$sep\n$block\n"
        }
        val head = existing.substring(0, from)
        val tail = existing.substring((endIdx + MARKER_END.length).coerceAtMost(existing.length))
        return if (block == null) {
            (head.trimEnd() + "\n" + tail.trimStart()).trim().let { if (it.isEmpty()) "" else "$it\n" }
        } else {
            head + block + tail
        }
    }
}
