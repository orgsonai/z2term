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

    /** サンプル。[body] がシェルごとの中身を組み立てる。 */
    enum class Preset(val id: String) {
        /** 記号だけ。一番短く、幅を食わない。 */
        PLAIN("plain"),
        /** `user@host:path$`。SSH でよく見る形。 */
        USER_HOST("user_host"),
        /** パスだけ。自分の端末しか触らないなら user@host は要らない。 */
        PATH_ONLY("path_only"),
        /** 2 行。パスが長くても打つ場所が左端から始まる。 */
        TWO_LINE("two_line"),
        /** 時刻つき。ログを遡るとき「いつ打ったか」が残る。 */
        WITH_TIME("with_time");

        companion object {
            fun of(id: String): Preset = entries.firstOrNull { it.id == id } ?: USER_HOST
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
     * 右端に時刻を出す部分。⚠ **端末の幅を数えない**。
     *
     * `COLUMNS` は sh (busybox ash) では設定されないことがあり、あっても画面を回したときに
     * 更新されないので、幅から引き算する作り方は**回転や分割で必ずズレる**。
     * 代わりに `ESC[999C` で「右端まで動く」(端で止まる) → `ESC[8D` で時刻の分だけ左へ戻る →
     * 書いたら `ESC[u` で元の位置へ帰る。幅を知らなくても右端に揃う。
     */
    private const val CLOCK_WIDTH = 8   // HH:MM:SS

    private fun bash(preset: Preset, rightClock: Boolean): String {
        val g = "\\[\\e[1;32m\\]"   // 緑・太字
        val b = "\\[\\e[1;34m\\]"   // 青・太字
        val y = "\\[\\e[1;33m\\]"   // 黄・太字
        val r = "\\[\\e[0m\\]"      // 戻す
        // ⚠ カーソル移動と時刻を**まとめて** `\[ \]` に入れる。元の位置へ帰ってくるので
        //    実際の幅は 0 で、そう教えないと bash が行の折り返しを誤る。
        val clock = if (!rightClock) "" else
            "\\[\\e[s\\e[999C\\e[${CLOCK_WIDTH}D\\e[1;30m\\t\\e[0m\\e[u\\]"
        return when (preset) {
            Preset.PLAIN -> "PS1='$clock\\$ '"
            Preset.USER_HOST -> "PS1='$clock$g\\u@\\h$r:$b\\w$r\\$ '"
            Preset.PATH_ONLY -> "PS1='$clock$b\\w$r\\$ '"
            Preset.TWO_LINE -> "PS1='$clock$g\\u@\\h$r:$b\\w$r\\n\\$ '"
            Preset.WITH_TIME -> "PS1='$clock$y\\t$r $b\\w$r\\$ '"
        }
    }

    /**
     * zsh。⚠ 右端の時刻は **`RPROMPT` に任せる** — zsh が幅を数えて右へ寄せ、
     * 行が伸びたら自分で引っ込めてくれる。カーソルを動かす小細工より確実。
     */
    private fun zsh(preset: Preset, rightClock: Boolean): String {
        val main = when (preset) {
            Preset.PLAIN -> "PROMPT='%# '"
            Preset.USER_HOST -> "PROMPT='%F{green}%n@%m%f:%F{blue}%~%f%# '"
            Preset.PATH_ONLY -> "PROMPT='%F{blue}%~%f%# '"
            Preset.TWO_LINE -> "PROMPT='%F{green}%n@%m%f:%F{blue}%~%f\n%# '"
            Preset.WITH_TIME -> "PROMPT='%F{yellow}%*%f %F{blue}%~%f%# '"
        }
        return if (rightClock) "$main\nRPROMPT='%F{240}%*%f'" else main
    }

    /**
     * sh (busybox ash)。
     *
     * ⚠ ash は `\u` `\h` `\w` は解釈するが、**色を `\033` と書いても展開しない**ものがある。
     * 実際の ESC を `printf` で作って変数へ入れ、二重引用符で PS1 に埋める。
     * ⚠ 幅を持たない印 (`\[ \]`) が無いので、**色を使うと長い行の折り返しが 1 文字ずれる**。
     * 短いプロンプトほど影響が出ないので、サンプルは色を控えめにしてある。
     */
    private fun sh(preset: Preset, rightClock: Boolean): String {
        val head = "__z2e=\$(printf '\\033')"
        val e = "\${__z2e}"
        // ⚠ sh には幅を持たない印 (`\[ \]`) が無いので、行編集が幅を誤る余地は残る。
        //    カーソルは保存・復帰するので**表示位置そのものはズレない**。
        val clock = if (!rightClock) "" else
            "$e[s$e[999C$e[${CLOCK_WIDTH}D$e[1;30m\\\$(date +%H:%M:%S)$e[0m$e[u"
        return when (preset) {
            Preset.PLAIN ->
                if (!rightClock) "PS1='\\$ '" else "$head\nPS1=\"$clock\\\$ \""
            Preset.USER_HOST ->
                "$head\nPS1=\"$clock$e[1;32m\\u@\\h$e[0m:$e[1;34m\\w$e[0m\\\$ \""
            Preset.PATH_ONLY -> "$head\nPS1=\"$clock$e[1;34m\\w$e[0m\\\$ \""
            Preset.TWO_LINE ->
                "$head\nPS1=\"$clock$e[1;32m\\u@\\h$e[0m:$e[1;34m\\w$e[0m\n\\\$ \""
            // ash に `\t` (時刻) は無いので date で作る。プロンプトを出すたびに評価される。
            Preset.WITH_TIME ->
                "$head\nPS1=\"$clock$e[1;33m\\\$(date +%H:%M:%S)$e[0m $e[1;34m\\w$e[0m\\\$ \""
        }
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
