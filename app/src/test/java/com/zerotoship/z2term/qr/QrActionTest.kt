package com.zerotoship.z2term.qr

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class QrActionTest {
    @Test fun linksOpenInTheirAppsAndUnsafeOrPlainTextDoesNot() {
        assertEquals(QrAction.Web("https://line.me/R/au/q/abc", "line.me"), QrAction.parse("https://line.me/R/au/q/abc"))
        assertEquals(QrAction.Link("line://ti/p/@example", "line"), QrAction.parse("line://ti/p/@example"))
        assertEquals("geo", (QrAction.parse("geo:35.68,139.76") as QrAction.Link).scheme)
        for (value in listOf("javascript:alert(1)", "intent://x#Intent;end", "file:///sdcard/a", "content://a/b",
            "https://user:pass@example.org", "z2term://command?text=ls", "ssh://host.example", "Order:123",
            "普通のテキスト", "https://exa mple.org", "tel:0312345678;rm -rf")) {
            assertNull(value, QrAction.parse(value))
        }
    }

    @Test fun phoneMailAndSmsBecomeComposeForms() {
        assertEquals(QrAction.Dial("+81-90-1234-5678"), QrAction.parse("tel:+81-90-1234-5678"))
        assertEquals(QrAction.Email("a@example.org", "件名 1+1", "本文"),
            QrAction.parse("mailto:a@example.org?subject=%E4%BB%B6%E5%90%8D%201+1&body=%E6%9C%AC%E6%96%87"))
        assertEquals(QrAction.Email("b@example.org", "Hi; there", "Line"),
            QrAction.parse("MATMSG:TO:b@example.org;SUB:Hi\\; there;BODY:Line;;"))
        assertEquals(QrAction.Sms("+819012345678", "hello: world"), QrAction.parse("SMSTO:+819012345678:hello: world"))
        assertEquals(QrAction.Sms("0312345678", "a b"), QrAction.parse("sms:0312345678?body=a%20b"))
    }

    @Test fun wifiUnescapesFieldsAndAssumesWpaWhenOnlyAPasswordIsGiven() {
        assertEquals(QrAction.Wifi("my;net", "WPA", "p:a\\ss", true),
            QrAction.parse("WIFI:T:WPA;S:my\\;net;P:p\\:a\\\\ss;H:true;;"))
        assertEquals(QrAction.Wifi("open", "nopass", "", false), QrAction.parse("WIFI:S:open;T:nopass;;"))
        assertEquals(QrAction.Wifi("cafe", "WPA", "secret123", false), QrAction.parse("WIFI:S:\"cafe\";P:secret123;;"))
        assertNull(QrAction.parse("WIFI:T:WPA;P:nossid;;"))
    }

    @Test fun contactsReadVcardAndMecard() {
        val vcard = "BEGIN:VCARD\r\nVERSION:3.0\r\nN:山田;太郎;;;\r\nTEL;TYPE=CELL:090-1111-2222\r\n" +
            "item1.EMAIL:taro@example.org\r\nORG:Example\\, Inc.;Dev\r\nNOTE:line1\\nline\r\n 2\r\nEND:VCARD"
        assertEquals(QrAction.Contact("山田 太郎", listOf("090-1111-2222"), listOf("taro@example.org"),
            "Example, Inc.", "", "", "line1\nline2"), QrAction.parse(vcard))
        assertEquals(QrAction.Contact("John Doe", listOf("+1555"), listOf("j@example.org"), "", "", "",
            "https://example.org"), QrAction.parse("MECARD:N:Doe,John;TEL:+1555;EMAIL:j@example.org;URL:https://example.org;;"))
        assertNull(QrAction.parse("BEGIN:VCARD\nVERSION:3.0\nEND:VCARD"))
    }

    @Test fun eventsConvertUtcZonedFloatingAndAllDayTimes() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val utc = QrAction.parse("BEGIN:VEVENT\nSUMMARY:会議\nLOCATION:本社\nDTSTART:20260915T010000Z\n" +
            "DTEND:20260915T020000Z\nEND:VEVENT", tokyo) as QrAction.Event
        assertEquals("会議", utc.title)
        assertEquals("本社", utc.location)
        assertEquals(ZonedDateTime.of(2026, 9, 15, 1, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli(), utc.begin)
        assertEquals(ZonedDateTime.of(2026, 9, 15, 2, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli(), utc.end)
        assertFalse(utc.allDay)

        val tenInTokyo = ZonedDateTime.of(2026, 9, 15, 10, 0, 0, 0, tokyo).toInstant().toEpochMilli()
        val zoned = QrAction.parse("BEGIN:VCALENDAR\nBEGIN:VEVENT\nSUMMARY:x\nDTSTART;TZID=Asia/Tokyo:20260915T100000\n" +
            "END:VEVENT\nEND:VCALENDAR", ZoneId.of("UTC")) as QrAction.Event
        assertEquals(tenInTokyo, zoned.begin)
        val floating = QrAction.parse("BEGIN:VEVENT\nSUMMARY:x\nDTSTART:20260915T100000\nEND:VEVENT", tokyo) as QrAction.Event
        assertEquals(tenInTokyo, floating.begin)

        val allDay = QrAction.parse("BEGIN:VEVENT\nSUMMARY:休み\nDTSTART;VALUE=DATE:20260916\nEND:VEVENT", tokyo) as QrAction.Event
        assertTrue(allDay.allDay)
        assertEquals(LocalDate.of(2026, 9, 16).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), allDay.begin)
        assertNull(allDay.end)
    }
}
