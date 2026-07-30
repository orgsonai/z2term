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
     * `share:*` トリガーが、他アプリから共有された内容で発火すべきか (0.8.266)。
     *
     * spec の書式:
     *  - `any`                  … 共有されたもの全部
     *  - `text` / `file`        … テキストが / ファイルが共有されたとき
     *  - `contains=<部分>`      … 共有されたテキストに含む (大小文字を区別しない)
     *  - `ext=<拡張子>`         … その拡張子のファイルが 1 つでもあるとき (`.` は付けても付けなくてもよい)
     *
     * [kind] は [com.zerotoship.z2term.share.SharedIntake.KIND_TEXT] /
     * `KIND_FILE`。判定は `sms:*` / `notify:*` と同じ考え方に揃えてある (覚えることを増やさない)。
     *
     * ⚠ **`contains=` はファイル共有では当たらない**。ファイルのときの [text] は取り込んだ先の
     * パスなので、当たると「ファイル名にたまたま含まれていた」で発火してしまい、書いた人の
     * 意図 (共有された文章の中身で絞る) とズレる。ファイル側は `ext=` で絞る。
     */
    fun share(spec: String, kind: String, text: String, fileNames: List<String>): Boolean {
        val s = spec.trim()
        return when {
            s == "any" -> true
            s == "text" || s == "file" -> kind == s
            s.startsWith("contains=") -> {
                val want = s.substring("contains=".length).trim()
                want.isNotEmpty() && kind == "text" && text.contains(want, ignoreCase = true)
            }
            s.startsWith("ext=") -> {
                val want = s.substring("ext=".length).trim().removePrefix(".")
                want.isNotEmpty() && fileNames.any {
                    it.substringAfterLast('.', "").equals(want, ignoreCase = true)
                }
            }
            else -> false
        }
    }

    /** 回線が無い (どこにも繋がっていない) ことを表す [net] の値。 */
    const val NET_NONE = "none"

    /**
     * `net:*` トリガーが、既定回線が [prev] から [now] へ変わったことで発火すべきか (0.8.264)。
     *
     * spec の書式:
     *  - `online` / `offline` … 通信できる回線がある / 無い状態へ**変わった**とき
     *  - `wifi` / `mobile` / `ethernet` … 使う回線が**それへ切り替わった**とき
     *
     * [now] / [prev] は `wifi` `mobile` `ethernet` `vpn` `other` [NET_NONE] のいずれか
     * (判定は [SystemEventService.netTransport]。Android 側の値をここへ持ち込まない)。
     *
     * ⚠ **判定に前の状態を要る**のがこのトリガーの肝。回線が Wi‑Fi からモバイルへ替わっても
     * 「通信できる」ことは変わらないので、`net:online` を発火させてはいけない。「今の状態を
     * 満たすか」だけで書くと、そこで誤発火する — だから*変化した項目*を見る。
     * 呼び元 ([SystemEventService.handleNet]) は [now] != [prev] のときしか呼ばない。
     */
    fun net(spec: String, now: String, prev: String): Boolean {
        val online = now != NET_NONE
        val wasOnline = prev != NET_NONE
        return when (val s = spec.trim()) {
            "online" -> online && !wasOnline
            "offline" -> !online && wasOnline
            // 回線種別は「その回線になった」= 直前が別の回線だったとき。
            "wifi", "mobile", "ethernet" -> now == s && prev != s
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
     * `notify:*` トリガーが、いま届いた通知で発火すべきか (0.8.236)。
     *
     * spec の書式:
     *  - `any`                  … すべての通知
     *  - `pkg=<部分>`           … パッケージ名かアプリ名に一致 (大小文字を区別しない・部分一致)
     *  - `title=<部分>`         … タイトルに含む
     *  - `contains=<部分>`      … タイトル**または本文**に含む
     *  - `category=<種別>`      … 通知の種別 (`Notification.category`) に**完全一致** (0.8.293)
     *  - `otp`                  … 本文に OTP らしい数字コードがあるとき ([extractOtp] と同じ判定)
     *
     * SMS 以外で届く確認コード (メール・認証アプリ) を拾うのが主目的なので、判定は
     * `sms:*` と同じ考え方に揃えてある (覚えることを増やさない)。
     *
     * ⚠ `category=` だけは**部分一致にしない**。種別名は Android が決めた固定の語彙で、
     * `call` (着信中) は `missed_call` (不在着信) の部分文字列になっている — 部分一致に
     * すると「着信のとき」と書いたルールが不在着信でも動いてしまい、区別できなくなる。
     */
    fun notify(
        spec: String,
        pkg: String,
        app: String,
        title: String,
        text: String,
        category: String = ""
    ): Boolean {
        val s = spec.trim()
        return when {
            s == "any" -> true
            s == "otp" -> extractOtp(text).isNotEmpty() || extractOtp(title).isNotEmpty()
            s.startsWith("category=") -> {
                val want = s.substring("category=".length).trim()
                want.isNotEmpty() && category.equals(want, ignoreCase = true)
            }
            s.startsWith("pkg=") -> {
                val want = s.substring("pkg=".length).trim()
                want.isNotEmpty() &&
                    (pkg.contains(want, ignoreCase = true) || app.contains(want, ignoreCase = true))
            }
            s.startsWith("title=") -> {
                val want = s.substring("title=".length).trim()
                want.isNotEmpty() && title.contains(want, ignoreCase = true)
            }
            s.startsWith("contains=") -> {
                val want = s.substring("contains=".length).trim()
                want.isNotEmpty() &&
                    (title.contains(want, ignoreCase = true) || text.contains(want, ignoreCase = true))
            }
            else -> false
        }
    }

    /**
     * `file:new=<フォルダ>[,ext=<拡張子>]` の監視先フォルダ (書式が違えば null)。
     *
     * どのフォルダを見張るかは**登録されたルールからしか決まらない**ので、
     * [SystemEventService] が `FileObserver` を張る前にここで取り出す。
     */
    fun fileDir(spec: String): String? {
        val s = spec.trim()
        if (!s.startsWith("new=")) return null
        val body = s.substring("new=".length)
        val dir = body.substringBefore(',').trim().trimEnd('/')
        return dir.ifEmpty { null }
    }

    /**
     * `file:new=…` の絞り込み。[fileName] がそのルールの対象か。
     *
     * `ext=` を付けたときだけ拡張子で絞る (大小文字は区別しない)。付けなければ全部。
     * 隠しファイルと**書きかけの一時ファイル**は常に外す — 同期アプリやカメラは
     * `.pending-xxx` のような名前で書いてから rename するので、拾うと実体の無いパスで
     * マクロが走る。
     */
    fun fileMatches(spec: String, fileName: String): Boolean {
        val name = fileName.trim()
        if (name.isEmpty() || name.startsWith(".")) return false
        val s = spec.trim()
        val ext = s.substringAfter(",ext=", "").trim().removePrefix(".")
        if (ext.isEmpty()) return true
        return name.substringAfterLast('.', "").equals(ext, ignoreCase = true)
    }

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
