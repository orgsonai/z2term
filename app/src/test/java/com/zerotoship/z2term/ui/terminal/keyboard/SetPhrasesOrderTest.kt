package com.zerotoship.z2term.ui.terminal.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 候補バーで句読点や記号が語より前に出ないこと (1.0.168)。 */
class SetPhrasesOrderTest {

    @Test
    fun idleCandidatesPutSymbolsLast() {
        val idle = SetPhrases.idleCandidates(listOf("開発", "連絡"))
        assertEquals(listOf("開発", "連絡"), idle.take(2))
        assertEquals(SetPhrases.symbols, idle.takeLast(SetPhrases.symbols.size))
    }

    @Test
    fun idleCandidatesWithoutLearningStartWithPhrases() {
        assertEquals(SetPhrases.all.first().second, SetPhrases.idleCandidates(emptyList()).first())
    }

    @Test
    fun learnedPunctuationFollowsWords() {
        assertEquals(
            listOf("します", "が", "を", "、", "。"),
            SetPhrases.orderNextWords(listOf("、", "します", "。"), listOf("が", "を"), 8),
        )
    }

    @Test
    fun limitKeepsWordsBeforeSymbols() {
        assertEquals(listOf("が", "を"), SetPhrases.orderNextWords(listOf("、", "。"), listOf("が", "を"), 2))
    }

    @Test
    fun onlySymbolsFallBackToIdle() {
        assertTrue(SetPhrases.orderNextWords(listOf("、", "。"), emptyList(), 8).isEmpty())
    }

    @Test
    fun longVowelMarkIsASymbolButWordsAreNot() {
        assertTrue(SetPhrases.isSymbol("ー"))
        assertTrue(SetPhrases.isSymbol("？！"))
        assertTrue(!SetPhrases.isSymbol("コーヒー"))
    }
}
