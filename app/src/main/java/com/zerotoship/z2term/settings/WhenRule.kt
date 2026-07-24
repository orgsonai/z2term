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
 * ```
 *
 * トリガー書式 (stage 1):
 *  - `charge:start` / `charge:stop`        … 充電の開始 / 停止
 *  - `battery:below=N` / `battery:above=N` … 電池残量が N% を下/上へ跨いだとき
 *  - `time:daily=HH:MM`                    … 毎日その時刻
 *  - `time:at=HH:MM`                       … 次の HH:MM に 1 回 (発火後は自動で無効化)
 *  - `time:every=Nm|Nh|Ns`                … N 分/時間/秒ごと
 *  - `time:cron=分 時 日 月 曜日`          … cron 式 (stage 2。曜日 0-7 で 0/7=日曜)
 */
data class WhenRule(
    val id: String,
    val trigger: String,
    val run: String,
    val enabled: Boolean = true,
) {
    /** トリガーの種別 (`:` の手前)。例: `charge` / `battery` / `time`。 */
    val kind: String get() = trigger.substringBefore(':', "").trim()

    /** トリガーの引数 (`:` の後ろ)。例: `start` / `below=20` / `daily=03:00`。 */
    val spec: String get() = trigger.substringAfter(':', "").trim()

    fun serialize(): String = buildString {
        append("trigger=").append(trigger).append('\n')
        append("run=").append(run).append('\n')
        append("enabled=").append(if (enabled) "1" else "0").append('\n')
    }

    companion object {
        /** ルールファイルの内容を [id] 付きで復元する。trigger か run が欠けていれば null。 */
        fun parse(id: String, text: String): WhenRule? {
            var trigger = ""
            var run = ""
            var enabled = true
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
                }
            }
            if (trigger.isBlank() || run.isBlank()) return null
            return WhenRule(id, trigger, run, enabled)
        }
    }
}
