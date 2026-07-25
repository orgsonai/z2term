package com.zerotoship.z2term.core

/**
 * 端末ログに書く前に、秘密らしき部分を伏せ字にする (0.8.243)。
 *
 * ## なぜ「打ったパスワード」が対象ではないのか
 *
 * `sudo` などのパスワード入力は**端末が echo を止めている**ので、そもそも画面にも PTY の
 * 出力にも現れない。つまり端末ログに入るのは「**画面に出た**秘密」だけで、実際に漏れるのは
 * 次の 2 つが圧倒的に多い:
 *
 *  - `export TOKEN=ghp_…` や `.env` を `cat` したときの **`名前=値` 形式**
 *  - 秘密鍵を貼り付けた / `cat` したときの **PEM ブロック**
 *
 * ここはその 2 つと、**形が決まっていて誤爆しない発行元プレフィックス**だけを潰す。
 *
 * ## 誤爆しないことを最優先にする
 *
 * ⚠ ログは端末アプリの信用に直結するので、**「秘密かもしれない」で潰さない**。
 * 6 桁の数字を確認コードとみなす / 長い base64 を全部潰す、といった規則は `ls` の出力や
 * ビルドログを穴だらけにして、最後は「ログが読めないから機能ごと切る」に行き着く。
 * 対象は「その形なら秘密以外にはまず無い」ものに限ること。同じ理由で:
 *
 *  - **潰すのは値 1 つ分だけ**（クォートで括られていればその中身まで）。行末まで潰すと
 *    `TOKEN=x && echo done` の後半のような**秘密でない部分まで消える**。
 *  - **`pass` 単体は対象にしない**。`Passed 12 tests` のようなふつうの出力に当たる。
 *  - **`-p値` のように値がくっついた短いフラグは対象にしない**。`tar -pxvf` と区別が付かない。
 *
 * ⚠ **完全ではない**。独自形式の秘密は素通りする。UI にも必ずその旨を書き、
 * 「伏せ字にしたから安全」と思わせないこと。
 *
 * ## 適用の単位
 *
 * [maskLine] は**改行を含まない 1 行**に対して働く。呼び出し側 ([SessionLogger]) が行に
 * 区切ってから渡すので、塊の切れ目で秘密が半分だけ通ることがない。PEM ブロックだけは
 * 複数行にまたがるので、この class がインスタンスとして状態を持つ。
 */
class SecretMasker {

    /** PEM の `-----BEGIN … PRIVATE KEY-----` を見てから `-----END …` までの間か。 */
    private var inPemBlock = false

    /**
     * 1 行を伏せ字にして返す。伏せる所が無ければ**同じ文字列をそのまま返す**。
     *
     * @param line 改行を含まない 1 行。
     */
    fun maskLine(line: String): String {
        // --- 秘密鍵の本文 (複数行) ---
        // BEGIN / END の行自体は残す。何を伏せたのか分からないログにはしない。
        if (inPemBlock) {
            if (PEM_END.containsMatchIn(line)) {
                inPemBlock = false
                return line
            }
            return if (line.isBlank()) line else MASK_PEM
        }
        if (PEM_BEGIN.containsMatchIn(line)) {
            inPemBlock = true
            return line
        }

        var out = line
        // `名前=値` / `名前: 値` (`--api-key=値` も含む)
        out = ASSIGN.replace(out) { m -> m.groupValues[1] + MASK }
        // `--password 値` のように空白で区切る長いフラグ
        out = FLAG_VALUE.replace(out) { m -> m.groupValues[1] + MASK }
        // `Authorization: Bearer …`
        out = AUTH_HEADER.replace(out) { m -> m.groupValues[1] + MASK }
        // 発行元プレフィックスが決まっているトークン
        out = KNOWN_TOKEN.replace(out) { m -> m.groupValues[1] + MASK }
        return out
    }

    /** 行をまたぐ状態を捨てる (別のファイルへ切り替えるときなど)。 */
    fun reset() {
        inPemBlock = false
    }

    companion object {
        /**
         * 伏せた跡。**何かを伏せたことが分かる形**にする (黙って消さない)。
         * ログは grep されるものなので ASCII に留める。
         */
        const val MASK = "[z2term:masked]"
        private const val MASK_PEM = "[z2term:masked private key]"

        private val PEM_BEGIN = Regex("""-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----""")
        private val PEM_END = Regex("""-----END [A-Z0-9 ]*PRIVATE KEY-----""")

        /**
         * 秘密を示す名前。**名前で判定する**のが誤爆しない肝で、値の見た目では判定しない。
         * 前後に文字が付いてよいので `MYSQL_ROOT_PASSWORD` や `--api-key` も拾う。
         */
        private const val WORDS =
            "password|passwd|passphrase|secret|token|api[_-]?key|access[_-]?key|" +
                "private[_-]?key|credential|client[_-]?secret|session[_-]?key"

        /** 値 1 つ分。クォートで括られていればその中身までを 1 つと数える。 */
        private const val VALUE = """(?:'[^']*'|"[^"]*"|\S+)"""

        /** `NAME=値` / `NAME: 値` / `--api-key=値`。 */
        private val ASSIGN = Regex(
            """((?:^|[\s;&|(\[{])(?:--?)?[A-Za-z0-9_.\-]*(?:$WORDS)[A-Za-z0-9_.\-]*[ \t]*[=:][ \t]*)$VALUE""",
            RegexOption.IGNORE_CASE
        )

        /** `--password 値` のように空白で区切る長いフラグ (短い `-p値` は誤爆するので扱わない)。 */
        private val FLAG_VALUE = Regex(
            """((?:^|\s)--?[A-Za-z0-9_.\-]*(?:$WORDS)[A-Za-z0-9_.\-]*[ \t]+)$VALUE""",
            RegexOption.IGNORE_CASE
        )

        /** `Authorization: Bearer …` / `-H 'Authorization: …'`。 */
        private val AUTH_HEADER = Regex(
            """(Authorization[ \t]*:[ \t]*(?:Bearer|Basic|Token)?[ \t]*)\S+""",
            RegexOption.IGNORE_CASE
        )

        /**
         * 発行元でプレフィックスが決まっているトークン。**形が固有なので誤爆しない**。
         * 頭だけ残すので「どのサービスの鍵が出たか」は読めるが、値は復元できない。
         */
        private val KNOWN_TOKEN = Regex(
            """(ghp_|gho_|ghu_|ghs_|ghr_|github_pat_|xox[baprs]-|sk-|AKIA|ASIA|AIza|eyJ)[A-Za-z0-9_\-./+=]{8,}"""
        )
    }
}
