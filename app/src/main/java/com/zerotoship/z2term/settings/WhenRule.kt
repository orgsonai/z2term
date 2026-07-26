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
 * order=0
 * ```
 *
 * `order` は**画面での並び順**だけを持つ任意項目 (0.8.249)。CLI (`z2-when`) は書かないので、
 * 端末から登録したルールには付かない ([order] = [NO_ORDER])。[parse] は知らないキーを黙って
 * 無視するので、どちらから書いても壊れない。
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
) {
    /** トリガーの種別 (`:` の手前)。例: `charge` / `battery` / `time`。 */
    val kind: String get() = trigger.substringBefore(':', "").trim()

    /** トリガーの引数 (`:` の後ろ)。例: `start` / `below=20` / `daily=03:00`。 */
    val spec: String get() = trigger.substringAfter(':', "").trim()

    fun serialize(): String = buildString {
        append("trigger=").append(trigger).append('\n')
        append("run=").append(run).append('\n')
        append("enabled=").append(if (enabled) "1" else "0").append('\n')
        // 未指定のときは書かない (端末から登録したままのルールに余計な行を足さない)。
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
                }
            }
            if (trigger.isBlank() || run.isBlank()) return null
            return WhenRule(id, trigger, run, enabled, order)
        }
    }
}
