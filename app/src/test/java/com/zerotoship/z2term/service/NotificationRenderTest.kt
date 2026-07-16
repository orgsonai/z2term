package com.zerotoship.z2term.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 通知ログのフォーマットテンプレート置換 ([NotificationLogService.render]) の検証。
 * 空テンプレート (JSONL) 分岐は org.json 依存のためここでは扱わず、テンプレート置換のみを見る。
 */
class NotificationRenderTest {

    private fun r(tpl: String) = NotificationLogService.render(
        template = tpl,
        ts = 1000L, time = "2026-07-16T10:25:19+09:00",
        pkg = "jp.example.app", app = "サンプル",
        title = "件名", text = "本文A\nB\tC", category = "msg", key = "k1"
    )

    @Test
    fun placeholdersSubstituted() {
        assertEquals("2026-07-16T10:25:19+09:00 [サンプル] 件名", r("{time} [{app}] {title}"))
        assertEquals("jp.example.app / msg / k1 / 1000", r("{pkg} / {category} / {key} / {ts}"))
    }

    @Test
    fun onelineCollapsesNewlinesAndTabs() {
        // {text} は生、{text1} は改行/タブ→空白。
        assertEquals("本文A\nB\tC", r("{text}"))
        assertEquals("本文A B C", r("{text1}"))
    }

    @Test
    fun escapesBecomeNewlineAndTab() {
        assertEquals("a\nb\tc", r("a\\nb\\tc"))
        assertEquals("100\\%", r("100\\%"))   // 未知エスケープはバックスラッシュを残す
    }

    @Test
    fun unknownPlaceholderKept() {
        assertEquals("{nope} 件名", r("{nope} {title}"))
    }
}
