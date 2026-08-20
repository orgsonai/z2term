package com.zerotoship.z2term.settings

/**
 * `if=` / `if_any=` の 1 条件を**組み立てられる形**で持つ (0.8.373)。
 *
 * 端末では `wifi,!screen,level<30` と書けばよいが、画面で同じものを打たせると
 * **1 文字違うだけで「一覧に並ぶのに一生動かないルール」**になる。選ぶだけで組めるように、
 * 文字列 ⇄ 構造の変換をここに置く (画面と CLI で判定が分かれないよう、語彙は
 * [com.zerotoship.z2term.service.WhenGuard] と同じものだけを扱う)。
 */
data class WhenCondition(
    val key: String,
    val op: Op,
    val value: String = "",
    /** 頭の `!`。真偽と一致にだけ付く (数値比較は `<` `>` を選べば足りる)。 */
    val negate: Boolean = false,
) {
    enum class Op { TRUTHY, EQ, LT, GT }
}

object WhenConditionSpec {

    /** 値の入れ方。キーで決まるので、画面はこれを見て入力欄を出し分ける。 */
    enum class Kind { BOOL, TEXT, NUMBER }

    /** 画面のプルダウンに並べるキー (よく使う順)。⚠ [WhenGuard] の KNOWN_KEYS の部分集合。 */
    val KEYS: List<String> = listOf(
        "wifi", "charging", "screen", "locked", "idle", "headset", "bt_audio", "airplane",
        "ssid", "plug", "ringer",
        "level", "temp", "volume",
    )

    fun kindOf(key: String): Kind = when (key) {
        "ssid", "plug", "ringer" -> Kind.TEXT
        "level", "temp", "volume", "volume_max" -> Kind.NUMBER
        else -> Kind.BOOL
    }

    /**
     * 文字列 → 条件の並び。**組み立て直せない書き方が 1 つでも混ざっていれば null**。
     *
     * ⚠ null は「壊れている」ではなく「**画面では触らない**」の意味。端末で書いた
     * `screen=on` や将来増えたキーを、画面が勝手に解釈して**書き換えてしまう**のが一番困る。
     * 呼び出し側 (編集画面) は null を受けたらテキストのまま見せる。
     */
    fun parse(spec: String): List<WhenCondition>? {
        val s = spec.trim()
        if (s.isEmpty()) return emptyList()
        val out = ArrayList<WhenCondition>()
        for (raw in s.split(',')) {
            val term = raw.trim()
            if (term.isEmpty()) return null
            val negate = term.startsWith("!")
            val body = (if (negate) term.substring(1) else term).trim()
            if (body.isEmpty()) return null
            val lt = body.indexOf('<')
            val gt = body.indexOf('>')
            val eq = body.indexOf('=')
            val cond = when {
                lt > 0 -> WhenCondition(
                    body.substring(0, lt).trim(), WhenCondition.Op.LT, body.substring(lt + 1).trim(), negate
                )
                gt > 0 -> WhenCondition(
                    body.substring(0, gt).trim(), WhenCondition.Op.GT, body.substring(gt + 1).trim(), negate
                )
                eq > 0 -> WhenCondition(
                    body.substring(0, eq).trim(), WhenCondition.Op.EQ, body.substring(eq + 1).trim(), negate
                )
                else -> WhenCondition(body, WhenCondition.Op.TRUTHY, "", negate)
            }
            if (!isBuildable(cond)) return null
            out.add(cond)
        }
        return out
    }

    /**
     * その条件を画面の部品で表せるか。⚠ **キーの型と演算子が噛み合わないものは表さない** —
     * `level`(数値) を裸で書いた式や `screen=on` は端末では有効だが、画面の部品には対応する
     * 形が無い。無理に当てはめると保存で別の式に化ける。
     */
    private fun isBuildable(c: WhenCondition): Boolean {
        if (c.key !in KEYS) return false
        return when (kindOf(c.key)) {
            Kind.BOOL -> c.op == WhenCondition.Op.TRUTHY
            Kind.TEXT -> c.op == WhenCondition.Op.EQ && c.value.isNotEmpty()
            Kind.NUMBER ->
                (c.op == WhenCondition.Op.LT || c.op == WhenCondition.Op.GT) &&
                    !c.negate && c.value.toDoubleOrNull() != null
        }
    }

    /** 条件の並び → `if=` / `if_any=` に書く文字列。[parse] と往復できる。 */
    fun build(list: List<WhenCondition>): String = list.joinToString(",") { c ->
        val head = if (c.negate) "!" else ""
        when (c.op) {
            WhenCondition.Op.TRUTHY -> "$head${c.key}"
            WhenCondition.Op.EQ -> "$head${c.key}=${c.value}"
            WhenCondition.Op.LT -> "$head${c.key}<${c.value}"
            WhenCondition.Op.GT -> "$head${c.key}>${c.value}"
        }
    }

    /** 画面が「すべて満たす」「どれか満たす」のどちらを組んでいるか。 */
    enum class Mode { ALL, ANY }

    /**
     * 編集画面の初期状態。[advanced] が true のときは**組み立て直さず、そのまま見せる**。
     *
     * ⚠ `if=` と `if_any=` の**両方**が入っているルール (端末からは書ける) は、画面の
     * 「すべて / どれか」1 つでは表せないので必ず [advanced]。表せないものを黙って
     * 捨てないことがこの型の目的。
     */
    data class Builder(
        val mode: Mode,
        val conditions: List<WhenCondition>,
        val advanced: Boolean,
    )

    fun builderOf(condition: String, conditionAny: String): Builder {
        val all = condition.trim()
        val any = conditionAny.trim()
        if (all.isNotEmpty() && any.isNotEmpty()) {
            return Builder(Mode.ALL, emptyList(), advanced = true)
        }
        val mode = if (any.isNotEmpty()) Mode.ANY else Mode.ALL
        val parsed = parse(if (any.isNotEmpty()) any else all)
            ?: return Builder(mode, emptyList(), advanced = true)
        return Builder(mode, parsed, advanced = false)
    }
}
