package com.zerotoship.z2term.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * システムイベントログのフォーマットテンプレート置換 ([SystemEventService.render]) の検証。
 * 空テンプレート (JSONL) 分岐は org.json 依存のためここでは扱わず、テンプレート置換のみを見る。
 */
class EventRenderTest {

    private fun r(tpl: String, level: Int? = null, ssid: String = "") =
        SystemEventService.render(
            template = tpl,
            ts = 1000L, time = "2026-07-16T10:25:19+09:00",
            event = "power_connected", level = level, ssid = ssid
        )

    @Test
    fun placeholdersSubstituted() {
        assertEquals("2026-07-16T10:25:19+09:00 power_connected", r("{time} {event}"))
        assertEquals("1000 power_connected", r("{ts} {event}"))
    }

    @Test
    fun levelPresentAndAbsent() {
        assertEquals("power_connected 87", r("{event} {level}", level = 87))
        // level が無いイベントでは {level} は空文字。
        assertEquals("power_connected ", r("{event} {level}"))
    }

    @Test
    fun ssidOnelinedAndBlankWhenAbsent() {
        assertEquals("home wifi", r("{ssid}", ssid = "home\twifi"))
        assertEquals("", r("{ssid}"))
    }

    @Test
    fun escapesBecomeNewlineAndTab() {
        assertEquals("power_connected\t87", r("{event}\\t{level}", level = 87))
        assertEquals("a\nb", r("a\\nb"))
    }

    @Test
    fun unknownPlaceholderKept() {
        assertEquals("{nope} power_connected", r("{nope} {event}"))
    }
}
