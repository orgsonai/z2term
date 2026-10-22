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
            s == "text" || s == "file" -> kind == s || kind == "mixed"
            s.startsWith("contains=") -> {
                val want = s.substring("contains=".length).trim()
                want.isNotEmpty() && kind in setOf("text", "mixed") && text.contains(want, ignoreCase = true)
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

    /** `file:` の種類。[fileAction] の値で、`file:<種類>=<フォルダ>` の `<種類>` と同じ。 */
    const val FILE_NEW = "new"
    const val FILE_CHANGED = "changed"
    const val FILE_DELETED = "deleted"

    /**
     * `file:<種類>=<フォルダ>[,ext=<拡張子>]` の `<種類>` (`new` / `changed` / `deleted`)。
     * 書式が違えば null。
     */
    fun fileAction(spec: String): String? {
        val s = spec.trim()
        return listOf(FILE_NEW, FILE_CHANGED, FILE_DELETED).firstOrNull { s.startsWith("$it=") }
    }

    /**
     * `file:<種類>=<フォルダ>[,ext=<拡張子>]` の監視先フォルダ (書式が違えば null)。
     *
     * どのフォルダを見張るかは**登録されたルールからしか決まらない**ので、
     * [SystemEventService] が `FileObserver` を張る前にここで取り出す。
     */
    fun fileDir(spec: String): String? {
        val action = fileAction(spec) ?: return null
        val body = spec.trim().substring(action.length + 1)
        val dir = body.substringBefore(',').trim().trimEnd('/')
        return dir.ifEmpty { null }
    }

    /**
     * フォルダで起きたことを `new` / `changed` / `deleted` のどれと見なすか (1.0.54)。
     *
     * 書き込み完了 (`CLOSE_WRITE`) は**新しく作ったときにも上書きしたときにも**来るので、
     * それだけでは見分けられない。見張りを始めた時点のファイル名を [known] に持っておき、
     * 知らない名前なら `new`、知っている名前なら `changed` とする。別名で書いてから
     * 移す書き方 (`MOVED_TO`) は常に `new`、消えたとき・よそへ移したときは `deleted`。
     *
     * ⚠ `CREATE` を見て「作った直後の書き込み」を判定する手は使わない — 共有ストレージの
     * 一部では `CREATE` が届かず、`file:new` が一度も動かなくなる。
     *
     * @param event `closed` (書き込み完了) / `moved_in` / `deleted` / `moved_out`
     */
    fun fileEventKind(event: String, name: String, known: Set<String>): String? = when (event) {
        "closed" -> if (name in known) FILE_CHANGED else FILE_NEW
        "moved_in" -> FILE_NEW
        "deleted", "moved_out" -> FILE_DELETED
        else -> null
    }

    /**
     * `file:<種類>=…` の絞り込み。[fileName] がそのルールの対象か。
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

    /**
     * `battery:temp>N` / `battery:temp<N` を今の電池の温度 [celsius] が満たすか (1.0.54)。
     * 跨いだ瞬間だけ動かす判定 (エッジ) は [WhenManager] 側。それ以外の書式は false。
     */
    fun batteryTempSatisfied(spec: String, celsius: Float): Boolean {
        val s = spec.trim()
        val limit = s.substring(minOf(s.length, "temp>".length)).trim().toFloatOrNull() ?: return false
        return when {
            s.startsWith("temp>") -> celsius > limit
            s.startsWith("temp<") -> celsius < limit
            else -> false
        }
    }

    /**
     * `bt:*` トリガーが、Bluetooth 機器の接続 / 切断で発火すべきか (1.0.54)。
     *
     * spec の書式:
     *  - `connect` / `disconnect`           … どの機器でも
     *  - `connect=<名前>` / `disconnect=<名前>` … 機器の名前に含む (大小文字を区別しない)、
     *    または機器のアドレス (`AA:BB:…`) に完全一致
     *
     * 名前は Android 12 以降「付近のデバイス」の許可が無いと取れない (空になる)。そのときは
     * 名前で絞ったルールは一致しない (誤発火より取りこぼしを選ぶ。`wifi:ssid=` と同じ)。
     */
    fun bluetooth(spec: String, connected: Boolean, name: String, address: String): Boolean {
        val s = spec.trim()
        val want = if (connected) "connect" else "disconnect"
        if (s == want) return true
        if (!s.startsWith("$want=")) return false
        val target = s.substring(want.length + 1).trim()
        if (target.isEmpty()) return false
        return (name.isNotEmpty() && name.contains(target, ignoreCase = true)) ||
            (address.isNotEmpty() && address.equals(target, ignoreCase = true))
    }

    /**
     * `app:open=<アプリ>` / `app:close=<アプリ>` が、前面のアプリが [prev] から [now] へ
     * 替わったことで発火すべきか (1.0.54)。
     *
     * `<アプリ>` はパッケージ名かアプリ名に含む (大小文字を区別しない)。`notify:pkg=` と同じ
     * 考え方。[nowLabel] / [prevLabel] はそれぞれのアプリ名 (取れなければ空)。
     * 呼び元はアプリが替わったときしか呼ばない (同じアプリの中の画面移動では呼ばない)。
     */
    fun app(spec: String, now: String, nowLabel: String, prev: String, prevLabel: String): Boolean {
        val s = spec.trim()
        fun hit(target: String, pkg: String, label: String): Boolean =
            target.isNotEmpty() && pkg.isNotEmpty() &&
                (pkg.contains(target, ignoreCase = true) || label.contains(target, ignoreCase = true))
        return when {
            s.startsWith("open=") -> hit(s.substring(5).trim(), now, nowLabel)
            s.startsWith("close=") -> hit(s.substring(6).trim(), prev, prevLabel)
            else -> false
        }
    }

    /** `steps:reach=N` の N (書式が違えば null)。 */
    fun stepsGoal(spec: String): Int? {
        val s = spec.trim()
        if (!s.startsWith("reach=")) return null
        return s.substring("reach=".length).trim().toIntOrNull()?.takeIf { it > 0 }
    }

    /**
     * 歩数計 (起動からの累計) の読み [counter] を受けて、今日の歩数を出す (1.0.54)。
     *
     * Android の歩数計は**端末を起動してからの累計**しか返さない。「今日」を出すには
     * 日の始まりの値 ([StepsState.base]) を覚えておく必要がある。
     *  - 初めて読んだ: その値を今日の始まりとして置く (今日は 0 歩から)
     *  - 日付が変わった: 前回の読みを今日の始まりにする
     *  - 値が前回より小さい: 端末を再起動した (累計が 0 へ戻った)。今日のぶんを引き継ぐ
     *
     * 返すのは新しい状態。今日の歩数は [StepsState.today]、ひとつ前の読みでの今日の歩数は
     * [StepsState.prevToday] (しきい値を「跨いだか」を見るため)。
     */
    fun stepsUpdate(state: StepsState?, counter: Long, date: String): StepsState {
        if (state == null) return StepsState(date, base = counter, last = counter, prevToday = 0)
        var base = state.base
        var prevToday = state.last - state.base
        // 再起動後の累計は「起動してからの歩数」なので、それに今日のぶんを足した形にする。
        if (counter < state.last) base = -prevToday
        if (date != state.date) {
            base = if (counter < state.last) 0 else state.last
            prevToday = 0
        }
        return StepsState(date, base = base, last = counter, prevToday = prevToday)
    }

    /** 歩数の数え方の状態 ([stepsUpdate])。ファイルに 1 行で残す ([serialize] / [parse])。 */
    data class StepsState(val date: String, val base: Long, val last: Long, val prevToday: Long) {
        val today: Long get() = (last - base).coerceAtLeast(0)
        fun serialize(): String = "$date $base $last $prevToday"

        companion object {
            fun parse(line: String): StepsState? {
                val p = line.trim().split(' ')
                if (p.size < 4) return null
                return StepsState(
                    p[0], p[1].toLongOrNull() ?: return null,
                    p[2].toLongOrNull() ?: return null, p[3].toLongOrNull() ?: return null,
                )
            }
        }
    }
}
