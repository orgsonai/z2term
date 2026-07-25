package com.zerotoship.z2term.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

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

    // --- 説明の抽出 (設定画面で「sh が並んでいて何か分からない」を防ぐ) ---

    @Test
    fun descriptionComesFromLeadingComment() {
        assertEquals(
            "入門用マクロ。イベントを見て反応する。",
            WidgetStore.describe(
                "watch-basic.sh",
                listOf("#!/bin/sh", "# watch-basic.sh — 入門用マクロ。イベントを見て反応する。", "# 準備: …")
            )
        )
    }

    @Test
    fun descriptionSkipsSelfReferencingPathLine() {
        // 同梱マクロには 2 行目が自分のパスだけ、というものがある。それは説明ではない。
        assertEquals(
            "画面消灯＝離席タイマー。",
            WidgetStore.describe(
                "away-timer.sh",
                listOf("#!/bin/sh", "# ~/.z2term/macros/away-timer.sh", "# 画面消灯＝離席タイマー。")
            )
        )
    }

    @Test
    fun descriptionKeepsPrefixThatIsNotTheFileName() {
        // 「z2term: …」のようにファイル名以外の接頭辞は説明の一部なので落とさない。
        assertEquals(
            "z2term: 切り分け診断。",
            WidgetStore.describe("ssh-diag.sh", listOf("#!/bin/sh", "# z2term: 切り分け診断。"))
        )
    }

    @Test
    fun descriptionStopsAtFirstCode() {
        // コメント帯が終わったら諦める (本文の途中のコメントを説明に採らない)。
        assertEquals(
            "",
            WidgetStore.describe("x.sh", listOf("#!/bin/sh", "echo hi", "# これは説明ではない"))
        )
    }

    @Test
    fun descriptionIsTruncated() {
        val long = "あ".repeat(200)
        val got = WidgetStore.describe("x.sh", listOf("#!/bin/sh", "# $long"))
        assertEquals(60, got.length)
        assertEquals('…', got.last())
    }

    @Test
    fun descriptionEmptyWhenNoComment() {
        assertEquals("", WidgetStore.describe("x.sh", listOf("#!/bin/sh", "", "echo hi")))
    }

    // --- ✓ の当日限り判定 (日付が変わったらボタンの印と時刻を消す) ---

    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Tokyo")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(zone).apply { clear(); set(year, month - 1, day, hour, minute, 0) }.timeInMillis

    @Test
    fun sameDayHoldsAcrossTheWholeDay() {
        assertTrue(WidgetStore.isSameDay(at(2026, 7, 25, 0, 1), at(2026, 7, 25, 23, 59), zone))
    }

    @Test
    fun midnightClearsTheMark() {
        // 23:59 に走らせたマクロの ✓ は、日付が変わった 00:01 の描画では消えている。
        assertFalse(WidgetStore.isSameDay(at(2026, 7, 25, 23, 59), at(2026, 7, 26, 0, 1), zone))
    }

    @Test
    fun sameDateOfDifferentYearIsNotSameDay() {
        // 日付だけ見て年を見ないと、1 年前の記録が「今日」に化ける。
        assertFalse(WidgetStore.isSameDay(at(2025, 7, 25, 10, 0), at(2026, 7, 25, 10, 0), zone))
    }

    @Test
    fun neverRunIsNotToday() {
        // 一度も走っていない (0) は常に false。無印のままにする。
        assertFalse(WidgetStore.isSameDay(0L, at(2026, 7, 25, 10, 0), zone))
    }

    @Test
    fun descriptionSurvivesBlankLineAfterShebang() {
        // シェバンと説明の間に空行がある書き方でも拾う。
        assertEquals(
            "バックアップを取る。",
            WidgetStore.describe("backup.sh", listOf("#!/bin/sh", "", "# バックアップを取る。", "echo hi"))
        )
    }
}
