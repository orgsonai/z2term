package com.zerotoship.z2term.settings

/**
 * `z2-when` の 1 ルール (A6 自動化ハブ)。Android 側の出来事 (トリガー) を検知したら
 * [run] のコマンドを Linux エンジンで実行する、という宣言。
 *
 * **実体はテキストファイル** `~/.z2term/when/<id>.rule` (共有 HOME 配下)。git 同期・バックアップが
 * 効くよう、DataStore ではなくプレーンなファイルに置く。CLI (`z2-when`) が直接読み書きし、
 * アプリ側 ([com.zerotoship.z2term.service.WhenManager]) が監視・実行する。
 *
 * ファイル形式 (1 行 1 項目):
 * ```
 * trigger=charge:start
 * run=~/.z2term/macros/backup.sh
 * enabled=1
 * name=夜のバックアップ
 * if=wifi,!screen
 * if_any=charging,plug=ac
 * else=z2-notify "見送りました"
 * cooldown=30m
 * between=22:00-07:00
 * days=mon-fri
 * order=0
 * ```
 *
 * `order` は**画面での並び順**だけを持つ任意項目 (0.8.249)。CLI (`z2-when`) は書かないので、
 * 端末から登録したルールには付かない ([order] = [NO_ORDER])。[parse] は知らないキーを黙って
 * 無視するので、どちらから書いても壊れない。**この「知らないキーは無視」のおかげで、後から
 * 項目を足しても古い版のアプリがルールを読めなくなることは無い** (0.8.263 の [condition] 等)。
 *
 * 絞り込み ([condition] / [cooldown] / [between] / [days]・0.8.263) は**どのトリガーにも
 * 同じように効く**。判定は [com.zerotoship.z2term.service.WhenGuard]、適用は
 * [com.zerotoship.z2term.service.WhenManager] の実行入口 1 か所。
 *
 * トリガー書式 (stage 1):
 *  - `charge:start` / `charge:stop`        … 充電の開始 / 停止 (検知 ON が前提。0.8.214 で受け口を変更)
 *  - `battery:below=N` / `battery:above=N` … 電池残量が N% を下/上へ跨いだとき (検知 ON が前提)
 *  - `time:daily=HH:MM`                    … 毎日その時刻
 *  - `time:at=HH:MM`                       … 次の HH:MM に 1 回 (発火後は自動で無効化)
 *  - `time:every=Nm|Nh|Ns`                … N 分/時間/秒ごと
 *  - `time:cron=分 時 日 月 曜日`          … cron 式 (stage 2。曜日 0-7 で 0/7=日曜)
 *  - `wifi:connect` / `wifi:disconnect`    … Wi‑Fi 接続 / 切断 (stage 2。検知 ON が前提)
 *  - `wifi:ssid=<名前>`                    … 指定 SSID へ接続 (位置情報権限が無いと SSID は取れない)
 *  - `net:online` / `net:offline`          … 回線が通じた / 途切れた (0.8.264。検知 ON が前提)
 *  - `net:wifi` / `net:mobile` / `net:ethernet` … 使う回線がそれへ切り替わったとき
 *  - `share:any` / `share:text` / `share:file`（0.8.266）… 他アプリの共有シートから届いたとき
 *  - `share:contains=<部分>` / `share:ext=<拡張子>` … 共有テキストの中身 / ファイルの拡張子で絞る
 *  - `boot`                                … 端末が起動したとき (0.8.264)。**引数を取らない**ので
 *    `:` が付かない唯一のトリガー。manifest 宣言のレシーバで受けるため**検知 OFF でも動く**。
 *  - `sms:any`                             … すべての着信 SMS (stage 2。RECEIVE_SMS 許可が前提)
 *  - `sms:from=<部分>` / `sms:contains=<部分>` … 送信元 / 本文の部分一致 (大小文字無視)
 *  - `sms:otp`                             … 本文に OTP らしい数字コードがあるとき
 *  - `sensor:shake`                        … 端末を振ったとき (stage 2。検知 ON が前提・加速度)
 *  - `sensor:light>N` / `sensor:light<N`   … 照度が N lux を上/下へ跨いだとき
 *  - `sensor:proximity=near` / `=far`      … 近接センサーが near/far へ変化したとき
 *  - `event:<名前>`                        … `events.jsonl` に流れる端末イベント (0.8.226)。
 *    `event:ringer_*` の前方一致と `event:*` も可。名前は `z2-when events` で一覧できる。
 *    受動的なイベント (画面・充電・Wi‑Fi 等) は検知 ON が前提、自分で仕掛けたもの
 *    (`alarm` / `notify_action` 等) は検知の ON/OFF に依存しない。
 */
data class WhenRule(
    val id: String,
    val trigger: String,
    val run: String,
    val enabled: Boolean = true,
    /** 画面での並び順。[NO_ORDER] = 未指定 (画面で並べ替えるまでは id 順)。 */
    val order: Int = NO_ORDER,
    /** `if=` … 発火した瞬間の端末の状態で絞る (空 = 絞らない)。書式は [WhenRule] の KDoc 参照。 */
    val condition: String = "",
    /** `cooldown=` … 前回の実行からこの時間は再実行しない (空 = 抑制しない)。例: `30m`。 */
    val cooldown: String = "",
    /** `between=` … この時間帯だけ実行する (空 = いつでも)。例: `22:00-07:00` (日跨ぎ可)。 */
    val between: String = "",
    /** `days=` … この曜日だけ実行する (空 = 毎日)。例: `mon-fri`。 */
    val days: String = "",
    /**
     * `name=` … 一覧に出す名前 (空 = 未記入。0.8.303)。
     *
     * トリガーは「いつ動くか」しか言わないので、`event:screen_on` が 3 本並ぶと**どれが何の
     * 自動化なのか区別が付かない**。名前は表示だけの項目で、発火にも実行にも一切影響しない。
     * 未記入のルール (今まで登録した全部と、CLI で `name=` を付けずに作ったもの) は今までどおり
     * トリガーが名前の代わりに出る ([label])。
     */
    val name: String = "",
    /**
     * `if_any=` … **このどれか 1 つ**を満たせばよい条件 (空 = 絞らない。0.8.372)。
     *
     * 書式は [condition] と同じで、区切りの意味だけが違う (`if=` は全部・`if_any=` はどれか)。
     * 両方あれば「[condition] を全部満たし、**かつ** [conditionAny] のどれか」。
     *
     * ⛔ **1 つの式に `&&` `||` `()` を混ぜない**という判断でこの形にした。優先順位を覚えないと
     * 読めない式は、画面の「すべて満たす / どれか満たす」とも 1:1 で対応しなくなる。
     */
    val conditionAny: String = "",
    /**
     * `else=` … [condition] / [conditionAny] に**合わなかったとき**に代わりに走るコマンド
     * (空 = 何もしない。0.8.372)。
     *
     * ⚠ **効くのは `if` 系で見送ったときだけ**。`between` / `days` / `cooldown` で見送ったときは
     * これも動かさない ([com.zerotoship.z2term.service.WhenManager] の実行入口)。「動かないはずの
     * 時間帯」に通知が飛ぶのは驚きでしかないため。
     */
    val otherwise: String = "",
) {
    /**
     * トリガーの種別 (`:` の手前)。例: `charge` / `battery` / `time`。
     *
     * `:` が無いトリガーは**全体が種別**になる (`boot`・0.8.264)。引数を取らないトリガーに
     * `boot:` と空の引数を書かせるのは不自然なので、書式の方をトリガーに合わせた。
     * 種別が未知なら [com.zerotoship.z2term.service.WhenManager] のどの受け口にも一致しない
     * ＝打ち間違いは今までどおり黙って何も起きないだけで、意味が変わるルールは無い。
     */
    val kind: String get() = trigger.substringBefore(':').trim()

    /** トリガーの引数 (`:` の後ろ)。例: `start` / `below=20` / `daily=03:00`。 */
    val spec: String get() = trigger.substringAfter(':', "").trim()

    /**
     * 一覧に出す見出し。[name] があればそれ、無ければ [trigger] (0.8.303)。
     *
     * 「未記入のときだけトリガー」を 1 か所に閉じ込める — 一覧・直近の発火・将来の表示で
     * 判定がズレると、同じルールが場所によって違う名前で出てしまう。
     */
    val label: String get() = name.ifBlank { trigger }

    /** 絞り込み ([condition] / [cooldown] / [between] / [days]) を 1 つでも持っているか。 */
    val hasFilters: Boolean
        get() = condition.isNotEmpty() || conditionAny.isNotEmpty() || cooldown.isNotEmpty() ||
            between.isNotEmpty() || days.isNotEmpty()

    fun serialize(): String = buildString {
        append("trigger=").append(trigger).append('\n')
        append("run=").append(run).append('\n')
        append("enabled=").append(if (enabled) "1" else "0").append('\n')
        // 未指定のときは書かない (端末から登録したままのルールに余計な行を足さない)。
        if (name.isNotEmpty()) append("name=").append(name).append('\n')
        if (condition.isNotEmpty()) append("if=").append(condition).append('\n')
        if (conditionAny.isNotEmpty()) append("if_any=").append(conditionAny).append('\n')
        // else はコマンドなので run と同じ扱い (中の空白を保つ)。
        if (otherwise.isNotEmpty()) append("else=").append(otherwise).append('\n')
        if (cooldown.isNotEmpty()) append("cooldown=").append(cooldown).append('\n')
        if (between.isNotEmpty()) append("between=").append(between).append('\n')
        if (days.isNotEmpty()) append("days=").append(days).append('\n')
        if (order != NO_ORDER) append("order=").append(order).append('\n')
    }

    companion object {
        /** [order] 未指定。並べ替えたことが無いルールはこれになり、id 順で後ろに並ぶ。 */
        const val NO_ORDER = -1

        /** ルールファイルの内容を [id] 付きで復元する。trigger か run が欠けていれば null。 */
        fun parse(id: String, text: String): WhenRule? {
            var trigger = ""
            var run = ""
            var enabled = true
            var order = NO_ORDER
            var condition = ""
            var cooldown = ""
            var between = ""
            var days = ""
            var name = ""
            var conditionAny = ""
            var otherwise = ""
            text.lineSequence().forEach { line ->
                val eq = line.indexOf('=')
                if (eq <= 0) return@forEach
                val key = line.substring(0, eq).trim()
                val value = line.substring(eq + 1)
                when (key) {
                    // trigger は空白を含まないので trim。run はコマンド全体なので前後空白は保つ
                    // (末尾の CR だけ落とす。CRLF のファイルでも壊れないように)。
                    "trigger" -> trigger = value.trim()
                    "run" -> run = value.trimEnd('\r')
                    "enabled" -> enabled = value.trim() != "0"
                    // 壊れた値 (手書きの typo 等) は未指定として扱う。並び順のためにルールを
                    // 読めなくするのは割に合わない。
                    "order" -> order = value.trim().toIntOrNull()?.takeIf { it >= 0 } ?: NO_ORDER
                    // 絞り込みは空白を含まない書式なので trim する (手書きの余白で効かなくならないように)。
                    "if" -> condition = value.trim()
                    "if_any" -> conditionAny = value.trim()
                    // else はコマンド全体。run と同じく前後の空白を保つ (末尾 CR だけ落とす)。
                    "else" -> otherwise = value.trimEnd('\r')
                    "cooldown" -> cooldown = value.trim()
                    "between" -> between = value.trim()
                    "days" -> days = value.trim()
                    // 名前は人が読む文字列なので中の空白は保つ (前後だけ落とす)。
                    "name" -> name = value.trim()
                }
            }
            if (trigger.isBlank() || run.isBlank()) return null
            return WhenRule(
                id, trigger, run, enabled, order, condition, cooldown, between, days, name,
                conditionAny, otherwise,
            )
        }
    }
}
