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
}
