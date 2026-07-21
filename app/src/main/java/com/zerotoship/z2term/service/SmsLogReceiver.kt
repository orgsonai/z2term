package com.zerotoship.z2term.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.zerotoship.z2term.settings.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * SMS 受信検知の常駐部 (汎用入口)。
 *
 * OS の `RECEIVE_SMS` 許可があると、Android は SMS 着信のたびにこの manifest レシーバを起動する
 * (アプリを開かなくても・**画面ロック中でも**動く = SMS 検知デーモン)。設定
 * [AppSettings.smsCaptureEnabled] が ON のとき、受信 SMS を [logFile] (`~/.z2term/sms.jsonl`) へ
 * 1 通 1 行 (JSON) で追記する。
 *
 * **なぜ通知検知と別に SMS を直接読むのか**: Android 15 以降は「高度な通知」が OTP を含む通知を
 * 機微判定し、`RECEIVE_SENSITIVE_NOTIFICATIONS` を持たない通知リスナー (一般アプリはすべてこれ) には
 * 本文を伏せ字にして渡す。SMS を**直接**読むこの経路は、その伏せ字もロック状態も一切通らないので、
 * ワンタイムパスワードを確実に取れる (自動化アプリの「SMS 受信」トリガーと同じ仕組み)。
 *
 * **方針**: 通知検知と同じく、抽出・フィルタ・配信は一切ハードコードしない。z2term は「受信 SMS を
 * ターミナルから読める形で流すだけ」の汎用機能を提供し、加工はユーザーがターミナル側 (tail / 自作
 * スクリプト / 常駐サーバー) で組む。完全ローカル・外部送信なし。
 */
class SmsLogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }.getOrNull()
        if (messages.isNullOrEmpty()) return

        val app = context.applicationContext
        val pending = goAsync()
        EXEC.execute {
            try {
                val s = runBlocking { AppSettings(app).flow.first() }
                if (!s.smsCaptureEnabled) return@execute

                // マルチパート SMS は 1 通が複数 part に割れて届くので、本文を順に連結して 1 通に戻す。
                val from = messages.firstOrNull()?.displayOriginatingAddress.orEmpty()
                val ts = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
                val body = buildString { for (m in messages) append(m.messageBody.orEmpty()) }
                if (from.isEmpty() && body.isEmpty()) return@execute

                val line = render(s.smsLogFormat, ts, ISO.format(Date(ts)), from, body)
                LogWriter.write(logFile(app), line, s.smsLogPrepend)
            } catch (t: Throwable) {
                Log.w(TAG, "sms capture failed: ${t.message}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SmsLog"
        private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        private val EXEC = Executors.newSingleThreadExecutor()

        /** 共有ホーム (= ターミナルの HOME `/root`) 配下の相対パス。ターミナルからは `~/.z2term/sms.jsonl`。 */
        const val LOG_REL = ".z2term/sms.jsonl"

        /** ログの実ファイル (`filesDir/shared_home/.z2term/sms.jsonl`)。 */
        fun logFile(context: Context): File =
            File(File(context.filesDir, "shared_home"), LOG_REL)

        private fun oneline(s: String): String =
            s.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')

        /**
         * 1 通の SMS を [template] に沿って 1 行分の文字列 (末尾改行なし) にする。
         * [template] が空なら JSONL。プレースホルダ `{time}` `{ts}` `{from}` `{body}` と 1 行化
         * `{body1}`、エスケープ `\n` `\t` `\\` に対応。
         */
        fun render(template: String, ts: Long, time: String, from: String, body: String): String {
            if (template.isBlank()) {
                return JSONObject()
                    .put("ts", ts).put("time", time).put("from", from).put("body", body)
                    .toString()
            }
            val vars = mapOf(
                "ts" to ts.toString(), "time" to time, "from" to from,
                "body" to body, "body1" to oneline(body)
            )
            val sb = StringBuilder(template.length + 64)
            var i = 0
            while (i < template.length) {
                val c = template[i]
                when {
                    c == '\\' && i + 1 < template.length -> {
                        when (template[i + 1]) {
                            'n' -> sb.append('\n'); 't' -> sb.append('\t')
                            '\\' -> sb.append('\\'); else -> { sb.append('\\'); sb.append(template[i + 1]) }
                        }
                        i += 2
                    }
                    c == '{' -> {
                        val end = template.indexOf('}', i + 1)
                        if (end < 0) { sb.append(c); i++ }
                        else {
                            val name = template.substring(i + 1, end)
                            sb.append(vars[name] ?: "{$name}")   // 未知プレースホルダはそのまま残す
                            i = end + 1
                        }
                    }
                    else -> { sb.append(c); i++ }
                }
            }
            return sb.toString()
        }
    }
}
