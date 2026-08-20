package com.novaboard.ime.translation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationContractTest {
    private val request = TranslationRequest(7L, 3L, 4, 9)

    @Test
    fun acceptsMatchingCurrentRequestAndSelection() {
        assertTrue(shouldAcceptTranslationResult(request, 7L, 3L, 4, 9))
    }

    @Test
    fun rejectsEmptyOrInvalidSelection() {
        assertFalse(shouldAcceptTranslationResult(TranslationRequest(7L, 3L, 4, 4), 7L, 3L, 4, 4))
    }

    @Test
    fun rejectsOlderSession() {
        assertFalse(shouldAcceptTranslationResult(request, 8L, 3L, 4, 9))
    }

    @Test
    fun rejectsChangedSelectionOrRequest() {
        assertFalse(shouldAcceptTranslationResult(request, 7L, 3L, 5, 9))
        assertFalse(shouldAcceptTranslationResult(request, 7L, 4L, 4, 9))
    }
}