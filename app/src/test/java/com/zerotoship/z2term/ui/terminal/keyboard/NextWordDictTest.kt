package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NextWordDictTest {

    @Before
    fun load() {
        NextWordDict.loadFrom("# comment\n天気\tが\tは\tの\n今日\tは\tの\nは\tない\n".reader().buffered())
    }

    @After
    fun unload() = NextWordDict.clear()

    @Test
    fun returnsFollowersInFileOrder() {
        assertEquals(listOf("は", "の"), NextWordDict.next("今日"))
        assertEquals(listOf("が", "は"), NextWordDict.next("天気", limit = 2))
    }

    @Test
    fun longCommitsUseTheirEnding() {
        assertEquals(listOf("が", "は", "の"), NextWordDict.next("今日はいい天気"))
    }

    @Test
    fun oneCharacterEndingIsNotLookedUp() {
        assertTrue(NextWordDict.next("今日は").isEmpty())
    }

    @Test
    fun wholeOneCharacterWordIsLookedUp() {
        assertEquals(listOf("ない"), NextWordDict.next("は"))
    }

    @Test
    fun unknownWordsAndEmptyInputGiveNothing() {
        assertTrue(NextWordDict.next("未知語").isEmpty())
        assertTrue(NextWordDict.next("").isEmpty())
    }
}
