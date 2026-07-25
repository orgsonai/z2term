package com.zerotoship.z2term.service

/**
 * `z2-when` トリガーのうち、Android API に触れず**純粋に判定できる**部分を切り出したもの
 * (ユニットテスト可能)。検知の副作用 (レシーバ登録・エンジン起動) は [WhenManager] 側に残す。
 */
object WhenTriggerMatch {

    /**
     * `wifi:*` トリガーが、いま起きた Wi‑Fi の状態変化 ([connected] / [ssid]) で発火すべきか。
     *
     * spec の書式:
     *  - `connect`      … Wi‑Fi に接続したとき
     *  - `disconnect`   … Wi‑Fi が切れたとき
     *  - `ssid=<名前>`  … 指定 SSID に**接続したとき** (大小文字は区別しない)
     *
     * SSID は位置情報権限が無いと空文字になる。その場合 `ssid=` は一致し得ないので発火しない
     * (誤発火より取りこぼしを選ぶ。呼び元 [SystemEventService.handleWifi] のコメントも参照)。
     * 切断イベント ([connected] = false) では SSID を問わないトリガーだけが対象。
     */
    fun wifi(spec: String, connected: Boolean, ssid: String): Boolean {
        val s = spec.trim()
        return when {
            s == "connect" -> connected
            s == "disconnect" -> !connected
            s.startsWith("ssid=") -> {
                val want = s.substring("ssid=".length).trim()
                connected && want.isNotEmpty() && ssid.isNotEmpty() && want.equals(ssid, ignoreCase = true)
            }
            else -> false
        }
    }

    /**
     * `event:*` トリガーが、いま起きた端末イベント [event] で発火すべきか。
     *
     * spec の書式:
     *  - `screen_on` のような**イベント名そのもの** … 完全一致
     *  - `ringer_*` … 末尾 `*` の前方一致 (`ringer_normal` / `ringer_vibrate` / `ringer_silent` をまとめて)
     *  - `*` … すべてのイベント
     *
     * イベント名は `events.jsonl` に書かれるものと同じ (`z2-when events` で一覧できる)。
     * 大小文字は区別しない (ファイルに手で書くものなので、打ち間違いで黙って動かないのを避ける)。
     */
    fun event(spec: String, event: String): Boolean {
        val s = spec.trim()
        val e = event.trim()
        if (s.isEmpty() || e.isEmpty()) return false
        if (s == "*") return true
        if (s.endsWith("*")) {
            val prefix = s.dropLast(1)
            // `*` だけの前方一致は上で処理済み。`_*` のような空でない接頭辞のみ。
            return prefix.isNotEmpty() && e.startsWith(prefix, ignoreCase = true)
        }
        return s.equals(e, ignoreCase = true)
    }

    /** OTP らしい数字コードを抜き出す (前後が数字でない 4〜8 桁の並びの先頭。無ければ空)。 */
    private val OTP_REGEX = Regex("(?<!\\d)(\\d{4,8})(?!\\d)")

    /**
     * 着信 SMS で `sms:*` トリガーが発火すべきか。
     *
     * spec の書式:
     *  - `any`             … すべての着信 SMS
     *  - `from=<部分文字列>` … 送信元に一致 (大小文字を区別しない・部分一致)
     *  - `contains=<部分文字列>` … 本文に含む (大小文字を区別しない)
     *  - `otp`             … 本文に OTP らしい数字コードがあるとき ([extractOtp] が非空)
     */
    fun sms(spec: String, from: String, body: String): Boolean {
        val s = spec.trim()
        return when {
            s == "any" -> true
            s == "otp" -> extractOtp(body).isNotEmpty()
            s.startsWith("from=") -> {
                val want = s.substring("from=".length).trim()
                want.isNotEmpty() && from.contains(want, ignoreCase = true)
            }
            s.startsWith("contains=") -> {
                val want = s.substring("contains=".length).trim()
                want.isNotEmpty() && body.contains(want, ignoreCase = true)
            }
            else -> false
        }
    }

    /**
     * SMS 本文から OTP らしい数字コードを 1 つ取り出す (無ければ空文字)。前後が数字でない 4〜8 桁を
     * 探すので、電話番号や注文番号のような長い数字列 (9 桁以上) は拾わない。`sms:otp` の発火判定と
     * `Z2_WHEN_OTP` の値づくりの両方で使う。純ロジックなのでユニットテスト可能。
     */
    fun extractOtp(body: String): String = OTP_REGEX.find(body)?.groupValues?.get(1).orEmpty()

    /**
     * `sensor:*` トリガーが要求するセンサー種別。`"accel"` (shake) / `"light"` / `"proximity"`、
     * 未知の spec は null。どのセンサーを登録すべきか ([SystemEventService]) の判断に使う。
     */
    fun sensorType(spec: String): String? {
        val s = spec.trim()
        return when {
            s == "shake" -> "accel"
            s.startsWith("light>") || s.startsWith("light<") -> "light"
            s == "proximity=near" || s == "proximity=far" -> "proximity"
            else -> null
        }
    }

    /** `sensor:light>N` / `light<N` を今の照度 [lux] が満たすか (エッジ判定は [WhenManager] 側)。 */
    fun lightSatisfied(spec: String, lux: Float): Boolean {
        val s = spec.trim()
        return when {
            s.startsWith("light>") -> s.substring(6).trim().toFloatOrNull()?.let { lux > it } ?: false
            s.startsWith("light<") -> s.substring(6).trim().toFloatOrNull()?.let { lux < it } ?: false
            else -> false
        }
    }

    /** `sensor:proximity=near` / `=far` を今の近接状態 [near] が満たすか。 */
    fun proximitySatisfied(spec: String, near: Boolean): Boolean = when (spec.trim()) {
        "proximity=near" -> near
        "proximity=far" -> !near
        else -> false
    }
}
