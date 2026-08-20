package com.novaboard.ime.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionEngineTest {

    @Test
    fun emptyWordUsesPreviousWordBigramsBeforeFrequencyFillers() {
        val suggestions = SuggestionEngine().suggest("", "I")

        assertEquals(listOf("am", "think", "know"), suggestions)
    }

    @Test
    fun completionAlwaysPreservesTheTypedPrefix() {
        val prefix = "th"
        val suggestions = SuggestionEngine().suggest(prefix, null)

        assertTrue(suggestions.any { it.equals(prefix, ignoreCase = true) })
        assertTrue(suggestions.size <= 3)
    }

    @Test
    fun learningAddsAnUnknownWordToFutureCompletions() {
        val engine = SuggestionEngine()

        engine.learn("novaboard")

        assertTrue(engine.suggest("nova", null).contains("novaboard"))
        assertFalse(engine.suggest("n", null).isEmpty())
    }

    @Test
    fun clearingLearnedDataRestoresTheBuiltInDictionary() {
        val engine = SuggestionEngine()
        engine.learn("novaboard")

        engine.clearLearnedData()

        assertFalse(engine.suggest("nova", null).contains("novaboard"))
        assertEquals(listOf("am", "think", "know"), engine.suggest("", "I"))
    }

    @Test
    fun knownWordsDoNotNeedAutocorrection() {
        assertEquals(null, SuggestionEngine().autocorrect("THE"))
    }
}
