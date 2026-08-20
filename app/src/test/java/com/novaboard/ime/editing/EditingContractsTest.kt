package com.novaboard.ime.editing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditingContractsTest {
    @Test
    fun previousWordDeletionIncludesWhitespaceAndWord() {
        assertEquals(6, previousWordDeletionCount("hello "))
        assertEquals(5, previousWordDeletionCount("hello world"))
        assertEquals(5, previousWordDeletionCount("  hello"))
        assertEquals(5, previousWordDeletionCount("hello   world"))
        assertEquals(0, previousWordDeletionCount(""))
    }

    @Test
    fun previousWordDeletionHandlesPunctuationAndUnicode() {
        assertEquals(5, previousWordDeletionCount("hi!!!"))
        assertEquals(6, previousWordDeletionCount("hello, world!"))
        assertEquals("こんにちは".length, previousWordDeletionCount("こんにちは"))
        assertEquals("😀".length, previousWordDeletionCount("😀"))
        assertEquals("😀".length, previousWordDeletionCount("x 😀"))
    }

    @Test
    fun undoRequiresTheReplacementToRemainAtCursor() {
        assertTrue(canUndoAutocorrect("teh ", "write teh "))
        assertFalse(canUndoAutocorrect("teh ", "write that "))
        assertFalse(canUndoAutocorrect(null, "write teh "))
    }

    @Test
    fun staleSessionOrRecognizerResultsAreRejected() {
        assertTrue(acceptsInputSessionResult(4L, 4L, 9L, 9L))
        assertFalse(acceptsInputSessionResult(3L, 4L, 9L, 9L))
        assertFalse(acceptsInputSessionResult(4L, 4L, 8L, 9L))
    }

    @Test
    fun selectionChangeOnlyResetsTrackedTypingWhenItNoLongerOwnsTheCursor() {
        assertFalse(shouldResetTrackedTyping(2, 2, 3, 3, "hello", "say hello"))
        assertTrue(shouldResetTrackedTyping(2, 2, 3, 3, "hello", "say world"))
        assertFalse(shouldResetTrackedTyping(2, 2, 2, 2, "hello", "say world"))
    }
}