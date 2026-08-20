package com.novaboard.ime.editing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditingContractsTest {
    @Test
    fun previousWordDeletionIncludesWhitespaceAndWord() {
        assertEquals(6, previousWordDeletionCount("hello "))
        assertEquals(11, previousWordDeletionCount("hello world"))
        assertEquals(0, previousWordDeletionCount(""))
    }

    @Test
    fun previousWordDeletionHandlesPunctuationAndUnicode() {
        assertEquals(5, previousWordDeletionCount("hi!!!"))
        assertEquals("こんにちは".length, previousWordDeletionCount("こんにちは"))
    }

    @Test
    fun undoRequiresTheReplacementToRemainAtCursor() {
        assertTrue(canUndoAutocorrect("teh ", "write teh "))
        assertFalse(canUndoAutocorrect("teh ", "write that "))
        assertFalse(canUndoAutocorrect(null, "write teh "))
    }
}