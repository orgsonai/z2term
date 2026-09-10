package com.zerotoship.z2term.service

import java.security.MessageDigest

/** Pure text selection and notification history, without app-specific rules. */
internal object NotificationText {
    data class Message(val time: Long, val text: String, val sender: String = "", val original: String = text)
    data class Body(val text: String, val messages: List<Message> = emptyList())

    /** Historic/current arrays may overlap; keep the largest occurrence count across them. */
    fun mergeMessages(history: List<Message>, current: List<Message>): List<Message> {
        val remaining = history.groupingBy { it }.eachCount().toMutableMap()
        return history + current.filter {
            val count = remaining[it] ?: 0
            if (count > 0) { remaining[it] = count - 1; false } else true
        }
    }

    /** Expand matching prefixes only: a longer summary must not replace an actual message. */
    fun fuller(text: String, candidates: List<String>): String {
        val prefix = text.removeSuffix("…").removeSuffix("...").trimEnd()
        if (prefix.isEmpty()) return text
        return (listOf(text) + candidates.filter { it.startsWith(prefix) })
            .maxByOrNull { it.length } ?: text
    }

    fun body(
        current: List<Message>, history: List<Message>, bigText: String,
        text: String, lines: List<String>, fallback: List<String>
    ): Body {
        val conversation = mergeMessages(history, current)
        if (conversation.isNotEmpty()) {
            val expanded = conversation.toMutableList()
            val last = expanded.last()
            expanded[expanded.lastIndex] = last.copy(text = fuller(last.text, listOf(bigText, text)))
            return Body(expanded.joinToString("\n") { it.text }, expanded)
        }
        val inbox = lines.filter { it.isNotBlank() }
        if (inbox.isNotEmpty()) {
            val expanded = if (inbox.size == 1) listOf(fuller(inbox.single(), listOf(bigText, text))) else inbox
            return Body(expanded.joinToString("\n"), expanded.mapIndexed { index, value -> Message(0L, value, original = inbox[index]) })
        }
        val plain = when {
            bigText.isNotBlank() -> fuller(bigText, listOf(text))
            text.isNotBlank() -> text
            else -> fallback.firstOrNull { it.isNotBlank() }.orEmpty()
        }
        return Body(plain)
    }

    /** Occurrence counts distinguish repeated messages from a reposted conversation. */
    class History(capacity: Int = 256, private val messagesPerKey: Int = 128) {
        private val seen = lru<LinkedHashMap<String, Int>>(capacity)
        private val grouped = lru<LinkedHashMap<String, Int>>(capacity)
        private val plain = lru<String>(capacity)
        private val plainGroups = lru<String>(capacity)
        private val recentPlain = lru<Pair<Long, String>>(capacity)

        fun fresh(
            key: String, body: Body, title: String, eventTime: Long = 0L, group: String = "",
            app: String = "", now: Long = 0L
        ): String? {
            if (body.messages.isNotEmpty()) {
                val remembered = seen.getOrPut(key) { lru(messagesPerKey) }
                val shared = if (group.isEmpty()) null else grouped.getOrPut(group) { lru(messagesPerKey) }
                val counts = mutableMapOf<String, Int>()
                val fresh = body.messages.filter { message ->
                    val ids = listOf(message.text, message.original).distinct().map {
                        digest(message.time.toString() + "\u0000" + message.sender + "\u0000" + it)
                    }
                    ids.forEach { counts[it] = (counts[it] ?: 0) + 1 }
                    val fullId = ids.first()
                    val duplicateHere = counts.getValue(fullId) <= (remembered[fullId] ?: 0)
                    val duplicateInGroup = message.time > 0L &&
                        counts.getValue(fullId) <= (shared?.get(fullId) ?: 0)
                    if (message.time > 0L && shared != null) ids.forEach {
                        shared[it] = maxOf(counts.getValue(it), shared[it] ?: 0)
                    }
                    !duplicateHere && !duplicateInGroup
                }
                counts.forEach { (id, count) -> remembered[id] = maxOf(count, remembered[id] ?: 0) }
                return fresh.takeIf { it.isNotEmpty() }?.joinToString("\n") { it.text }
            }
            val sig = digest(eventTime.toString() + "\u0000" + title + "\u0000" + body.text)
            if (plain[key] == sig) return null
            plain[key] = sig
            // Cross-key suppression requires a shared group and the same explicit event time.
            if (group.isNotEmpty() && eventTime > 0L) {
                val id = digest(group + "\u0000" + sig)
                val previousKey = plainGroups.put(id, key)
                if (previousKey != null && previousKey != key) return null
            }
            // Preserve short-window cross-key dedup for ordinary notifications without message IDs.
            if (eventTime == 0L && app.isNotEmpty()) {
                val id = digest(app + "\u0000" + sig)
                val previous = recentPlain.put(id, now to key)
                if (previous != null && previous.second != key && now - previous.first in 0L until 10_000L) return null
            }
            return body.text
        }

        /** Dismissal resets ordinary reposts, but conversation history must stay remembered. */
        fun removed(key: String) { plain.remove(key) }
    }

    private fun digest(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

    private fun <V> lru(max: Int): LinkedHashMap<String, V> =
        object : LinkedHashMap<String, V>(max, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>): Boolean = size > max
        }
}
