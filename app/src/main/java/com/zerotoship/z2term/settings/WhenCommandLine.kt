package com.zerotoship.z2term.settings

/**
 * ルール → 端末で**同じものを作る** `z2-when` の 1 行 (0.8.375)。
 *
 * **なぜ要るか**: 画面で組んだ自動化が、端末では何というコマンドになるのかどこにも出ていなかった。
 * ルールの正本は `~/.z2term/when/<id>.rule` のテキストで、画面も CLI (`z2-when`) も同じものを
 * 読み書きしているのに、**画面で覚えたことが端末で使えず、端末で読んだ例を画面のどこに入れるのかも
 * 分からない**。組んだそばから 1 行で見えれば、その 2 つが同じものだと画面の上で分かる。
 *
 * ⚠ **貼れば同じルールがもう 1 本できる**ことがこの関数の責任。引数の並びは `z2-when` の usage と
 * 同じにする (トリガー → `name=` / `if=` / `if_any=` / `else=` / `cooldown=` / `between=` / `days=`
 * → `run` → コマンド)。`z2-when` は `run` の**後ろを全部コマンド**として読む (`"$*"`) ので、
 * コマンドは 1 引数にまとめてクォートする。
 */
object WhenCommandLine {

    /**
     * 裸で出すと**シェルに食われる**文字。`event:ringer_*` の `*` は glob、`sensor:light>50` の
     * `>` はリダイレクトに読まれ、貼った瞬間に別の意味になる。1 つでも含めばクォートする。
     */
    private const val UNSAFE = " \t\n\r'\"\\$`&;|<>()[]{}*?!#"

    /** 端末にそのまま貼れる形。中の `'` は `'\''` で閉じ直す (シェルの決まり文句)。 */
    fun quote(value: String): String =
        if (value.isNotEmpty() && value.none { it in UNSAFE }) value
        else "'" + value.replace("'", "'\\''") + "'"

    /** [rule] を登録する `z2-when` の 1 行。トリガーと run が空でも組み立てはする (呼び出し側で判断)。 */
    fun of(rule: WhenRule): String {
        val parts = ArrayList<String>()
        parts += "z2-when"
        parts += quote(rule.trigger)
        // 空の項目は書かない (ルールファイルに書かないのと同じ — 意味の無い行を端末へ持ち込まない)。
        if (rule.name.isNotEmpty()) parts += "name=" + quote(rule.name)
        if (rule.condition.isNotEmpty()) parts += "if=" + quote(rule.condition)
        if (rule.conditionAny.isNotEmpty()) parts += "if_any=" + quote(rule.conditionAny)
        if (rule.otherwise.isNotEmpty()) parts += "else=" + quote(rule.otherwise)
        if (rule.cooldown.isNotEmpty()) parts += "cooldown=" + quote(rule.cooldown)
        if (rule.between.isNotEmpty()) parts += "between=" + quote(rule.between)
        if (rule.days.isNotEmpty()) parts += "days=" + quote(rule.days)
        parts += "run"
        parts += quote(rule.run)
        return parts.joinToString(" ")
    }
}
