package com.zerotoship.z2term.gui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `z2menu list` の TSV を読むところ ([GuiAppCatalog.parse]) を固める。
 *
 * ⚠ **実際の入力は PTY 越し**なので、改行は `\r\n` で来る（termios の `ONLCR`）。
 * ここが崩れると、一覧が「全部空」ではなく **`\r` の付いた分類だけ壊れる**という
 * 見つけにくい形で出るので、CRLF は必ず例に入れておく。
 */
class GuiAppCatalogTest {

    private fun tsv(vararg rows: String) = rows.joinToString("\r\n") + "\r\n"

    @Test
    fun `CRLF の TSV を読む`() {
        val apps = GuiAppCatalog.parse(
            tsv(
                "gThumb\tgthumb\t画像を表示し管理するツールです\t0\tGraphics",
                "Vim\tvim\tテキストファイルを編集します\t1\tDevelopment",
            )
        )
        assertEquals(2, apps.size)
        assertEquals("gThumb", apps[0].name)
        assertEquals("gthumb", apps[0].exec)
        assertEquals("画像を表示し管理するツールです", apps[0].comment)
        assertEquals(false, apps[0].terminal)
        assertEquals("Graphics", apps[0].category)
        // ⚠ `\r` が category に残っていないこと (CRLF を落とし忘れたときにここが落ちる)。
        assertTrue(apps.none { it.category.contains('\r') })
        assertEquals(true, apps[1].terminal)
    }

    @Test
    fun `列が足りない行と空行は捨てる`() {
        val apps = GuiAppCatalog.parse(
            tsv(
                "",
                "proot: something happened",      // proot が出した 1 行 (タブ無し)
                "Kate\tkate -b\t\t0\tDevelopment", // 説明が空なのは正常
                "壊れた\t\t\t0\tOther",             // コマンドが空 = 起動できないので捨てる
            )
        )
        assertEquals(1, apps.size)
        assertEquals("Kate", apps[0].name)
        assertEquals("kate -b", apps[0].exec)   // フィールドコードは z2menu 側で外れている
        assertEquals("", apps[0].comment)
    }

    @Test
    fun `同じ名前とコマンドの重複は畳む`() {
        val apps = GuiAppCatalog.parse(
            tsv(
                "XTerm\txterm\tterminal\t0\tSystem",
                "XTerm\txterm\tterminal\t0\tSystem",
                "UXTerm\tuxterm\tterminal\t0\tSystem",
            )
        )
        assertEquals(2, apps.size)
    }

    @Test
    fun `空の入力は空の一覧`() {
        assertEquals(emptyList<GuiApp>(), GuiAppCatalog.parse(""))
    }
}
