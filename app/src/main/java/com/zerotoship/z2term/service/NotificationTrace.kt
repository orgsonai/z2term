package com.zerotoship.z2term.service

import android.os.Bundle
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Opt-in, bounded metadata trace. Never retain notification text or Parcelable payloads. */
internal class NotificationTrace(
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val capacity: Int = 128,
    private val durationMs: Long = 5 * 60_000L
) {
    init { require(capacity > 0 && durationMs > 0L) }

    private var deadline = 0L
    private var target = ""
    private var dropped = 0
    private val events = ArrayDeque<JSONObject>()

    @Synchronized fun command(action: String, pkg: String = ""): String {
        require(action in listOf("start", "dump", "stop")) { "trace: start [package] | dump | stop" }
        require(action == "start" || pkg.isEmpty()) { "trace: package is only valid with start" }
        require(pkg.length <= 255 && (pkg.isEmpty() || pkg.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")))) {
            "trace: invalid package name"
        }
        when (action) {
            "start" -> {
                target = pkg
                events.clear()
                dropped = 0
                deadline = clock() + durationMs
            }
            "stop" -> deadline = 0L
        }
        val remaining = (deadline - clock()).coerceAtLeast(0L)
        return JSONObject().put("active", remaining > 0L).put("remainingMs", remaining)
            .put("package", target).put("capacity", capacity).put("dropped", dropped)
            .put("events", JSONArray(events.toList())).toString()
    }

    @Synchronized fun record(sbn: StatusBarNotification, body: NotificationText.Body?) {
        val now = clock()
        if (now >= deadline || (target.isNotEmpty() && target != sbn.packageName)) return
        val n = sbn.notification ?: return
        val event = JSONObject().put("receivedElapsedMs", now).put("postTime", sbn.postTime)
            .put("package", sbn.packageName).put("key", fingerprint(sbn.key.orEmpty()))
            .put("group", if (sbn.isGroup) fingerprint(sbn.groupKey.orEmpty()) else "")
            .put("groupSummary", n.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0)
            .put("extras", shape(n.extras)).put("ticker", shape(n.tickerText))
            .put("extractionFailed", body == null)
            .put("selectedUtf16", body?.text?.length ?: JSONObject.NULL)
            .put("selectedMessages", body?.messages?.size ?: JSONObject.NULL)
        if (events.size >= capacity) { events.removeFirst(); dropped++ }
        events.addLast(event)
    }

    companion object {
        /** Values are described by type/count/UTF-16 length, never by toString(). */
        @Suppress("DEPRECATION")
        internal fun shape(value: Any?): Any = describe(value, 0, intArrayOf(256))

        @Suppress("DEPRECATION")
        private fun describe(value: Any?, depth: Int, budget: IntArray): Any {
            if (budget[0]-- <= 0) return JSONObject().put("nodeLimit", true)
            if (value == null) return JSONObject.NULL
            if (value is CharSequence) return JSONObject().put("type", "text").put("utf16", value.length)
            val result = JSONObject().put("type", value.javaClass.simpleName)
            if (depth >= 4) return result.put("depthLimit", true)
            when (value) {
                is Bundle -> {
                    val keys = value.keySet()
                    val fields = JSONObject()
                    keys.take(48).sorted().forEach { key ->
                        if (budget[0] <= 0) return@forEach
                        fields.put(key.take(128), runCatching { describe(value.get(key), depth + 1, budget) }
                            .getOrElse { JSONObject().put("unreadable", true) })
                    }
                    result.put("count", keys.size).put("fields", fields)
                }
                is Array<*> -> result.put("count", value.size)
                    .put("items", JSONArray(value.take(32).map { describe(it, depth + 1, budget) }))
                is List<*> -> result.put("count", value.size)
                    .put("items", JSONArray(value.take(32).map { describe(it, depth + 1, budget) }))
            }
            return result
        }

        private fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).take(12)
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    }
}
