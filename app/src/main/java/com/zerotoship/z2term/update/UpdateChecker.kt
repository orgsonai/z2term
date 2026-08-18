package com.zerotoship.z2term.update

import com.zerotoship.z2term.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 手動更新チェックの結果。UI はこれを見て文言を出し分ける。
 */
sealed interface UpdateResult {
    /** 現在が最新 (これ以上新しい公開版が無い)。 */
    data class UpToDate(val current: String) : UpdateResult

    /** 新しい版がある。[latest] は表示用の版名、[url] は開くリリースページ。 */
    data class Available(val latest: String, val url: String) : UpdateResult

    /** 通信・解析に失敗 (オフライン等)。[reason] は短い理由。 */
    data class Failed(val reason: String) : UpdateResult
}

/**
 * GitHub Releases の最新版を **その場で 1 回だけ** 問い合わせる更新チェッカ。
 *
 * ⚠ この [check] を呼ばない限りネットワークには一切触れない。自動チェック・
 * バックグラウンド通信・起動時通信はどれも行わない (ユーザーがボタンを押した
 * ときだけ通信する、という方針を守るため)。ダウンロードやインストールもしない
 * — 新版があればリリースページを開くところまでが役割。
 */
object UpdateChecker {
    // 公開リリースはこの 1 リポジトリの Releases だけ。API は latest を返す。
    private const val LATEST_API = "https://api.github.com/repos/orgsonai/z2term/releases/latest"
    private const val RELEASES_PAGE = "https://github.com/orgsonai/z2term/releases/latest"

    suspend fun check(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(LATEST_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                // GitHub API は User-Agent を要求する (無いと 403)。
                setRequestProperty("User-Agent", "z2term-update-check")
            }
            try {
                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    return@withContext UpdateResult.Failed("HTTP $code")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(body)
                val tag = obj.optString("tag_name").ifBlank {
                    return@withContext UpdateResult.Failed("no tag")
                }
                val htmlUrl = obj.optString("html_url").ifBlank { RELEASES_PAGE }
                if (isNewer(numbersOf(tag), numbersOf(BuildConfig.VERSION_NAME))) {
                    UpdateResult.Available(tag.removePrefix("v"), htmlUrl)
                } else {
                    UpdateResult.UpToDate(BuildConfig.VERSION_NAME)
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            UpdateResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * 版名から major.minor.patch の 3 数値だけを取り出す。
     * 例: "v0.8.290-alpha" / "0.8.289-alpha" → [0, 8, 290] / [0, 8, 289]。
     * 先頭 3 個に限るのは、-alpha や過去タグの commit ハッシュ (数字混じり) を
     * 比較に混ぜないため。
     */
    private fun numbersOf(raw: String): List<Int> =
        Regex("""\d+""").findAll(raw).map { it.value.toInt() }.take(3).toList()

    private fun isNewer(latest: List<Int>, current: List<Int>): Boolean {
        val n = maxOf(latest.size, current.size)
        for (i in 0 until n) {
            val a = latest.getOrElse(i) { 0 }
            val b = current.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
