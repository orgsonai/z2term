package com.zerotoship.z2term.service

import org.junit.Assert.*
import org.junit.Test

private typealias Message = NotificationText.Message

/** Regressions: batched updates, repeated new text and notification-history reposts. */
class NotificationFreshMessagesTest {
    private fun body(vararg messages: Message) =
        NotificationText.Body(messages.joinToString("\n") { it.text }, messages.toList())

    private fun content(
        current: List<Message> = emptyList(), history: List<Message> = emptyList(),
        big: String = "", text: String = "", lines: List<String> = emptyList()
    ) = NotificationText.body(current, history, big, text, lines, emptyList())

    @Test fun capturesEveryBatchedMessageAndOnlyNewEntriesOnUpdate() {
        val tracker = NotificationText.History()
        val first = Message(1L, "first")
        val second = Message(2L, "second")
        assertEquals("first\nsecond", tracker.fresh("chat", body(first, second), "title"))
        assertNull(tracker.fresh("chat", body(first, second), "title"))
        assertEquals("third\nfourth", tracker.fresh("chat",
            body(first, second, Message(3L, "third"), Message(4L, "fourth")), "title"))
    }

    @Test fun identicalTextWithANewTimestampIsNotDiscardedByContentDedup() {
        val tracker = NotificationText.History()
        assertEquals("yes", tracker.fresh("chat", body(Message(1L, "yes")), "title"))
        assertEquals("yes", tracker.fresh("chat", body(Message(1L, "yes"), Message(2L, "yes")), "title"))
        assertNull(tracker.fresh("chat", body(Message(1L, "yes"), Message(2L, "yes")), "title"))
    }

    @Test fun repeatedIdenticalMessagesWithoutTimesUseOccurrenceCounts() {
        val tracker = NotificationText.History()
        val message = Message(0L, "yes")
        assertEquals("yes", tracker.fresh("chat", body(message), "title"))
        assertEquals("yes", tracker.fresh("chat", body(message, message), "title"))
        assertNull(tracker.fresh("chat", body(message, message), "title"))
    }

    @Test fun differentSendersAtTheSameTimestampRemainDifferentMessages() {
        val tracker = NotificationText.History()
        val first = Message(1L, "yes", "sender-a")
        assertEquals("yes", tracker.fresh("chat", body(first), "title"))
        assertEquals("yes", tracker.fresh("chat", body(first, Message(1L, "yes", "sender-b")), "title"))
    }

    @Test fun historicAndCurrentArraysOverlapWithoutLosingIntermediateMessages() {
        val tracker = NotificationText.History()
        val first = Message(1L, "first")
        val second = Message(2L, "second")
        val third = Message(3L, "third")
        assertEquals("first", tracker.fresh("chat", body(first), "title"))
        val update = content(current = listOf(second, third), history = listOf(first, second))
        assertEquals("first\nsecond\nthird", update.text)
        assertEquals("second\nthird", tracker.fresh("chat", update, "title"))
        assertNull(tracker.fresh("chat", update, "title"))
    }

    @Test fun inboxLinesTakePrecedenceOverTheShortSummary() {
        val tracker = NotificationText.History()
        val initial = content(text = "New messages", lines = listOf("first", "second"))
        assertEquals("first\nsecond", tracker.fresh("inbox", initial, "title"))
        val update = content(text = "New messages", lines = listOf("first", "second", "third"))
        assertEquals("third", tracker.fresh("inbox", update, "title"))
        assertNull(tracker.fresh("inbox", update, "title"))
    }

    @Test fun expandedTextKeepsItsEndingAndDoesNotRepeatWhenShortHistoryReturns() {
        val tracker = NotificationText.History()
        val full = "begin " + "long message ".repeat(1000) + "THE END"
        val short = Message(1L, "begin…", "sender")
        val expanded = content(current = listOf(short), big = full)
        assertEquals(full, tracker.fresh("chat", expanded, "title"))
        assertNull(tracker.fresh("chat", expanded, "title"))
        val next = content(current = listOf(Message(2L, "next")), history = listOf(short))
        assertEquals("next", tracker.fresh("chat", next, "title"))
    }

    @Test fun laterFullTextCanCorrectAnEarlierShortNotificationOnce() {
        val tracker = NotificationText.History()
        val short = Message(1L, "begin…")
        assertEquals("begin…", tracker.fresh("chat", body(short), "title"))
        val full = content(current = listOf(short), big = "begin and the complete ending")
        assertEquals("begin and the complete ending", tracker.fresh("chat", full, "title"))
        assertNull(tracker.fresh("chat", full, "title"))
        assertNull(tracker.fresh("chat", body(short), "title"))
    }

    @Test fun genericDisplayTextNeverReplacesTheActualMessageOrExpandedBody() {
        assertEquals("yes", content(current = listOf(Message(1L, "yes")), text = "New messages are available").text)
        assertEquals("yes", content(big = "yes", text = "New messages are available").text)
        assertEquals("begin complete ending", content(big = "begin…", text = "begin complete ending").text)
    }

    @Test fun summaryAndChildNotificationsDoNotDuplicateTheSameKnownMessage() {
        val tracker = NotificationText.History()
        val first = body(Message(1L, "hello", "sender"))
        assertEquals("hello", tracker.fresh("child", first, "title", group = "app:group"))
        assertNull(tracker.fresh("summary", first, "group title", group = "app:group"))
        assertEquals("hello", tracker.fresh("summary", body(Message(2L, "hello", "sender")),
            "group title", group = "app:group"))
        assertNull(tracker.fresh("child", body(Message(2L, "hello", "sender")), "title", group = "app:group"))
    }

    @Test fun differentConversationsDoNotSuppressEachOther() {
        val tracker = NotificationText.History()
        val message = body(Message(1L, "yes", "sender"))
        assertEquals("yes", tracker.fresh("one", message, "title", group = "app:one"))
        assertEquals("yes", tracker.fresh("two", message, "title", group = "app:two"))
    }

    @Test fun dismissalRetainsConversationHistoryButAllowsANewOrdinaryNotification() {
        val tracker = NotificationText.History()
        val message = body(Message(1L, "message"))
        assertEquals("message", tracker.fresh("chat", message, "title"))
        tracker.removed("chat")
        assertNull(tracker.fresh("chat", message, "title"))
        val ordinary = NotificationText.Body("status")
        assertEquals("status", tracker.fresh("other", ordinary, "title"))
        assertNull(tracker.fresh("other", ordinary, "title"))
        tracker.removed("other")
        assertEquals("status", tracker.fresh("other", ordinary, "title", now = 20_000L))
    }

    @Test fun ordinaryCrossKeyRepostsAreDeduplicatedWithoutDroppingTimedNewMessages() {
        val tracker = NotificationText.History()
        val text = NotificationText.Body("same")
        assertEquals("same", tracker.fresh("one", text, "title", app = "app", now = 100L))
        assertNull(tracker.fresh("two", text, "title", app = "app", now = 200L))
        assertEquals("same", tracker.fresh("three", text, "title", app = "app", now = 20_000L))
        assertEquals("same", tracker.fresh("chat", body(Message(1L, "same")), "chat title", app = "app"))
        assertNull(tracker.fresh("chat", body(Message(1L, "same")), "chat title", app = "app"))
        assertEquals("same", tracker.fresh("chat", body(Message(2L, "same")), "chat title", app = "app"))
    }

    @Test fun plainGroupRepostsDoNotNeedMatchingNotificationTimes() {
        val tracker = NotificationText.History()
        val text = NotificationText.Body("same")
        assertEquals("same", tracker.fresh("child", text, "title", group = "app:group", app = "app", now = 100L))
        assertNull(tracker.fresh("summary", text, "title", group = "app:group", app = "app", now = 101L))
        assertNull(tracker.fresh("child", text, "title", group = "app:group", app = "app", now = 21_000L))
        assertNull(tracker.fresh("summary", text, "title", group = "app:group", app = "app", now = 21_001L))
    }

    @Test fun plainAndConversationCopiesDeduplicateInEitherOrder() {
        for (plainFirst in listOf(true, false)) {
            val tracker = NotificationText.History()
            val plain = NotificationText.Body("same")
            val conversation = body(Message(1L, "same", "sender"))
            val first = if (plainFirst) plain else conversation
            val second = if (plainFirst) conversation else plain
            assertEquals("same", tracker.fresh("one", first, "title", app = "app", now = 100L))
            assertNull(tracker.fresh("two", second, "title", app = "app", now = 101L))
            assertEquals("same", tracker.fresh("two", body(Message(2L, "same", "sender")),
                "title", app = "app", now = 102L))
        }
    }

    @Test fun aPlainCopyDoesNotEraseTheIdentityOfAnActualMessage() {
        val tracker = NotificationText.History()
        assertEquals("same", tracker.fresh("key", body(Message(1L, "same", "sender")),
            "title", app = "app", now = 100L))
        assertNull(tracker.fresh("key", NotificationText.Body("same"), "title", app = "app", now = 101L))
        assertEquals("same", tracker.fresh("key", body(Message(2L, "same", "sender")),
            "title", app = "app", now = 102L))
    }

    @Test fun untimedInboxCopiesKeepRepeatedOccurrencesAndDifferentConversations() {
        val tracker = NotificationText.History()
        val inbox = content(lines = listOf("same"))
        assertEquals("same", tracker.fresh("one", inbox, "title", group = "group-a", app = "app", now = 100L))
        assertNull(tracker.fresh("two", inbox, "title", group = "group-a", app = "app", now = 101L))
        assertEquals("same", tracker.fresh("two", content(lines = listOf("same", "same")),
            "title", group = "group-a", app = "app", now = 102L))
        assertEquals("same", tracker.fresh("three", inbox, "title", group = "group-b", app = "app", now = 103L))
    }

    @Test fun immediateCancellationAndRepostDoesNotDuplicateOrdinaryText() {
        val tracker = NotificationText.History()
        val plain = NotificationText.Body("same")
        assertEquals("same", tracker.fresh("key", plain, "title", now = 100L))
        tracker.removed("key")
        assertNull(tracker.fresh("key", plain, "title", now = 101L))
        tracker.removed("key")
        assertEquals("same", tracker.fresh("key", plain, "title", now = 20_000L))
    }

    @Test fun copiesOfAnUntimedBatchKeepTheLargestOccurrenceCount() {
        val tracker = NotificationText.History()
        val batch = content(lines = listOf("same", "same"))
        assertEquals("same\nsame", tracker.fresh("one", batch, "title", app = "app", now = 100L))
        assertNull(tracker.fresh("two", batch, "title", app = "app", now = 101L))
        assertNull(tracker.fresh("one", batch, "title", app = "app", now = 102L))
        assertEquals("same", tracker.fresh("two", content(lines = listOf("same", "same", "same")),
            "title", app = "app", now = 103L))
    }
}
