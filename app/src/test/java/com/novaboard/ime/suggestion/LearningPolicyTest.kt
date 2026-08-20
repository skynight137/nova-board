package com.novaboard.ime.suggestion

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningPolicyTest {
    @Test
    fun incognitoBlocksLearning() {
        assertFalse(shouldLearnWord(incognito = true, word = "private"))
    }

    @Test
    fun normalModeLearnsWordsButNotSingleCharacters() {
        assertTrue(shouldLearnWord(incognito = false, word = "normal"))
        assertFalse(shouldLearnWord(incognito = false, word = "a"))
    }
}