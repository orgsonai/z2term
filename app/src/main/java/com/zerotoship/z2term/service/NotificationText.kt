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
        private data class ContentStamp(
            val time: Long, val sender: String, val count: Int, val at: Long,
            val structured: Boolean, val group: String
        )
        private val recentContent = lru<ContentStamp>(capacity)

        fun fresh(
            key: String, body: Body, title: String, group: String = "",
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
                    val duplicateContent = rememberContent(key, app, title, group, message,
                        counts.getValue(fullId), now, structured = true)
                    !duplicateHere && !duplicateInGroup && !duplicateContent
                }
                counts.forEach { (id, count) -> remembered[id] = maxOf(count, remembered[id] ?: 0) }
                return fresh.takeIf { it.isNotEmpty() }?.joinToString("\n") { it.text }
            }
            // Notification.when/postTime describe a notification update, not a message identity.
            val sig = digest(title + "\u0000" + body.text)
            val duplicateHere = plain.put(key, sig) == sig
            val duplicateContent = rememberContent(key, app, title, group, Message(0L, body.text),
                1, now, structured = false)
            if (duplicateHere || duplicateContent) return null
            return body.text
        }

        /** Bridge plain/conversation/inbox copies, including notifications recreated under a new key. */
        private fun rememberContent(
            key: String, app: String, title: String, group: String, message: Message,
            count: Int, now: Long, structured: Boolean
        ): Boolean {
            fun id(text: String) = digest(app.ifEmpty { key } + "\u0000" + title + "\u0000" + text)
            val previous = recentContent[id(message.text)]
            val inWindow = previous != null && now - previous.at in 0L until 10_000L
            val sameContext = previous != null &&
                (group.isEmpty() || previous.group.isEmpty() || group == previous.group)
            // Only actual message timestamps distinguish identical new messages. A plain copy
            // has no such identity; neither its post time nor its category supplies one.
            val distinctMessage = previous != null && previous.structured && structured &&
                ((previous.time > 0L && message.time > 0L && previous.time != message.time) ||
                    (previous.sender.isNotEmpty() && message.sender.isNotEmpty() &&
                        previous.sender != message.sender))
            val compatible = inWindow && sameContext && !distinctMessage
            val duplicate = compatible && count <= (previous?.count ?: 0)
            val rememberedCount = if (compatible) maxOf(count, previous?.count ?: 0) else count
            val stamp = if (duplicate && previous != null && previous.structured && !structured) {
                // A plain copy must not erase the message identity needed for the next new message.
                previous.copy(at = now)
            } else ContentStamp(message.time, message.sender, rememberedCount, now, structured, group)
            listOf(message.text, message.original).distinct().forEach { recentContent[id(it)] = stamp }
            return duplicate
        }

        /** Dismissal resets plain state; the short recreation window and conversation history remain. */
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
