package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 絵文字パッドの一覧 ([EmojiCatalog.ALL]) に**抜けが無い**ことの検証。
 *
 * ⚠ **打てない字があっても、こちらからは見えない**。気付けるのは打とうとした本人だけで、
 * しかも「このアプリでは打てない」ではなく「なぜか出てこない」という形でしか現れない
 * (実際 😌 が打てないという報告で初めて 13 字の抜けが分かった)。1 字ずつ書き写す表は必ず
 * 抜けるので、連番ブロックが**丸ごと入っていること**を機械で押さえる。
 *
 * `EmojiCatalog.categories()` は `Paint.hasGlyph` を通るため、ふつうのユニットテストからは
 * 呼べない。ここで見るのは**ふるいに掛ける前の表**。
 */
class EmojiCatalogTest {

    private val all: List<String> = EmojiCatalog.ALL.flatMap { it.items }

    private fun codePoint(cp: Int): String = String(Character.toChars(cp))

    /** 報告のあった 13 字。⚠ **ここを消さない** — 同じ抜け方をしたことの記録でもある。 */
    @Test
    fun theReportedGapsArePresent() {
        val reported = listOf(
            0x1F60C, // 😌 これが打てないという報告から見つかった
            0x1F61D, // 😝
            0x1F638, 0x1F639, 0x1F63A, 0x1F63B, 0x1F63C, // 猫の顔
            0x1F63D, 0x1F63E, 0x1F63F, 0x1F640,
            0x1F64D, 0x1F64E // 人のしぐさ
        )
        reported.forEach { cp ->
            assertTrue("U+%04X が一覧に無い".format(cp), codePoint(cp) in all)
        }
    }

    /**
     * 顔と人のしぐさのブロック (U+1F600–U+1F64F) が**全部**あること。
     *
     * 手で選び直すと必ずどれかが落ちるので、範囲そのものを条件にする。
     */
    @Test
    fun theWholeFaceBlockIsThere() {
        val missing = (0x1F600..0x1F64F).filter { codePoint(it) !in all }
        assertEquals("抜けている: " + missing.joinToString { "U+%04X".format(it) }, emptyList<Int>(), missing)
    }

    /** 動物のブロック (U+1F400–U+1F43E) も同じく全部あること。 */
    @Test
    fun theWholeAnimalBlockIsThere() {
        val missing = (0x1F400..0x1F43E).filter { codePoint(it) !in all }
        assertEquals("抜けている: " + missing.joinToString { "U+%04X".format(it) }, emptyList<Int>(), missing)
    }

    /**
     * ⚠ **異体字セレクタが要る字を素で入れない**。U+1F43F 🐿 は `U+FE0F` を付けないと
     * 白黒の記号で出るので、範囲で足す対象から外してある。
     */
    @Test
    fun theTextStyleAnimalIsNotAddedBare() {
        assertFalse(codePoint(0x1F43F) in all)
    }

    /** 同じ字を 2 度並べない (手で選んだ表とブロックが重なるため `distinct()` している)。 */
    @Test
    fun noCategoryRepeatsACharacter() {
        EmojiCatalog.ALL.forEach { c ->
            assertEquals("${c.label} に重複がある", c.items.size, c.items.distinct().size)
        }
    }

    /**
     * ⚠ **よく打つ字が先頭側に残っていること**。ブロックを前に足すと、手で選んだ表が
     * 数十字ぶん下へ押し出されて**探せなくなる** (表を広く持って困るのはそこだけ)。
     */
    @Test
    fun theHandPickedOnesStayAtTheFront() {
        val faces = EmojiCatalog.ALL.first().items
        assertEquals("😀", faces.first())
        // 手で選んだ表のいちばん後ろ (🤩) が、ブロックだけの字より前にあること。
        assertTrue(faces.indexOf("🤩") < faces.indexOf(codePoint(0x1F60C)))
    }
}
