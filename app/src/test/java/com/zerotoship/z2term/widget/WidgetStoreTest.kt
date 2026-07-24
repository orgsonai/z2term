package com.zerotoship.z2term.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ウィジェットに並べるマクロの決めかた ([WidgetStore.resolve]) の検証。
 *
 * ここが崩れると「消したはずのマクロのボタンが残る」「設定したのに別のものが出る」といった、
 * ホーム画面でしか気付けない不具合になるので、Android 非依存の判断部分だけ切り出して押さえる。
 */
class WidgetStoreTest {

    private val available = listOf("backup.sh", "daily-report.sh", "otp-clip.sh", "otp-sms.sh", "watch-basic.sh")

    @Test
    fun unconfiguredWidgetShowsFirstMacros() {
        // 設定せずに置いた (API 31+ の configuration_optional) ときは先頭 4 件。
        assertEquals(
            listOf("backup.sh", "daily-report.sh", "otp-clip.sh", "otp-sms.sh"),
            WidgetStore.resolve(saved = null, available = available)
        )
    }

    @Test
    fun savedOrderIsKept() {
        assertEquals(
            listOf("otp-sms.sh", "backup.sh"),
            WidgetStore.resolve(saved = "otp-sms.sh\nbackup.sh", available = available)
        )
    }

    @Test
    fun deletedMacroIsDropped() {
        // 端末側で消したマクロのボタンは残さない (押しても動かないボタンを作らない)。
        assertEquals(
            listOf("backup.sh"),
            WidgetStore.resolve(saved = "backup.sh\ngone.sh", available = available)
        )
    }

    @Test
    fun emptySelectionStaysEmpty() {
        // 「1 つも出さない」を選んだときに先頭 4 件で上書きしない (未設定とは別物)。
        assertEquals(emptyList<String>(), WidgetStore.resolve(saved = "", available = available))
    }

    @Test
    fun neverExceedsButtonCount() {
        val many = (1..10).map { "m$it.sh" }
        assertEquals(WidgetStore.MAX_MACROS, WidgetStore.resolve(saved = null, available = many).size)
        assertEquals(
            WidgetStore.MAX_MACROS,
            WidgetStore.resolve(saved = many.joinToString("\n"), available = many).size
        )
    }

    @Test
    fun labelDropsExtension() {
        assertEquals("backup", WidgetStore.label("backup.sh"))
    }
}
