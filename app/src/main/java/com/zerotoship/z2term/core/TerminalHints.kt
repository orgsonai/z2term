package com.zerotoship.z2term.core

/**
 * よくあるつまずきを、その場で 1 行だけ言い換える (0.8.237)。
 *
 * ハンドブックの FAQ に答えは書いてあるが、**詰まっている本人はそのとき読まない**。
 * 端末の出力に既知のパターンが現れたら、画面の下に「次の一手」を 1 行出す。
 *
 * **やらないこと**:
 *  - **端末の出力そのものは絶対に書き換えない**。書き換えは端末アプリとしての信用に直結する。
 *    出すのは別の場所に 1 行だけ。
 *  - パターンを増やしすぎない。**誤爆すると一気にうっとうしい**ので、
 *    「答えが 1 行で書けて、実際によく詰まる」ものだけに絞る（いまは 4 つ）。
 *  - **「コマンドが無い」は出さない**（0.8.304 で撤去）。`command not found` は端末で
 *    最も普通に起きる出来事で詰まりですらなく、案内も 3 ディストロのコマンドを並べる
 *    ことしかできないため、当たっても役に立たなかった。
 *
 * 判定は Android に触れない純粋な文字列処理なので [TerminalHintsTest] が固定する。
 */
object TerminalHints {

    /**
     * 出せるヒントの種類。文言は `strings.xml` に置き、ここでは**どれを出すか**だけ決める
     * (日英の切り替えは Android のリソース解決に任せる)。
     */
    enum class Hint {
        /** `ping` は仕組み上使えない (raw socket が要る)。 */
        PING,

        /** 1024 未満のポートに bind しようとした。 */
        LOW_PORT,

        /** `/usr/sbin/sshd` を直接叩いた (このアプリでは `sshd` ラッパーを使う)。 */
        SSHD_PATH,

        /** `/sdcard` が見えない (ストレージ許可)。 */
        STORAGE,
    }

    /**
     * 端末に出た [text] から、出すべきヒントを 1 つ返す (無ければ null)。
     *
     * 複数当たったときは**この順**で先頭のものを返す。より具体的なパターンを先に置く
     * (`/usr/sbin/sshd: not found` は「コマンドが無い」でもあるが、案内すべきは sshd の方)。
     */
    // ⚠ /sdcard は Android の外部ストレージ API に置き換える対象ではない — ここで見ているのは
    // **端末の出力に含まれる文字列**で、ディストロ側から見えるパスそのもの。
    @Suppress("SdCardPath")
    fun detect(text: String): Hint? {
        if (text.isEmpty()) return null
        return when {
            // ping は raw socket が要るので、非 root の Android では原理的に通らない。
            text.contains("ping: socket:") ||
                (text.contains("ping:") && text.contains("Operation not permitted")) -> Hint.PING

            // このアプリの sshd は dropbear ラッパー (/usr/local/sbin/sshd)。絶対パス直叩きは外れる。
            text.contains("/usr/sbin/sshd") && looksMissing(text) -> Hint.SSHD_PATH

            // 1024 未満は非 root では bind できない。
            text.contains("bind:") && text.contains("Permission denied") -> Hint.LOW_PORT
            text.contains("Permission denied") && text.contains("bind to port") -> Hint.LOW_PORT

            // /sdcard が見えないのは、たいてい「すべてのファイルへのアクセス」が未許可。
            text.contains("/sdcard") &&
                (text.contains("Permission denied") || text.contains("No such file or directory")) -> Hint.STORAGE

            else -> null
        }
    }

    /** シェルの「そんなコマンドは無い」の言い回し (シェルによって違う)。 */
    private fun looksMissing(text: String): Boolean =
        text.contains("command not found") || text.contains(": not found")

    /**
     * 同じヒントを続けて出さない最小間隔。
     *
     * `command not found` を連打したときに毎回バーが出ると、**お節介なアプリ**になる。
     * 「気付かせる」のが目的なので、1 度出れば当分は黙っていてよい。
     */
    const val REPEAT_SUPPRESS_MS = 60_000L

    /**
     * チャンクをまたいだ検出のために、直前の出力から持ち越す文字数。
     *
     * PTY からは 8KB 単位で来るので、`ping: socket: Operation not permitted` のような 1 行が
     * **境目で割れる**ことがある。少しだけ持ち越して繋いでから見る。
     */
    const val CARRY_CHARS = 256
}
