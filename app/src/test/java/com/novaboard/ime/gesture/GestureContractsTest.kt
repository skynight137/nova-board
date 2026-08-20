package com.novaboard.ime.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GestureContractsTest {
    @Test
    fun shortPathsRemainOrdinaryTaps() {
        assertEquals(GesturePathResult.TAP, classifyGesturePath(10f, 4, cancelled = false))
        assertEquals(GesturePathResult.TAP, classifyGesturePath(30f, 1, cancelled = false))
    }

    @Test
    fun deliberateLetterPathBecomesGestureCandidate() {
        assertEquals(GesturePathResult.CANDIDATE, classifyGesturePath(30f, 3, cancelled = false))
    }

    @Test
    fun cancelledPathNeverBecomesCandidate() {
        assertEquals(GesturePathResult.CANCELLED, classifyGesturePath(100f, 8, cancelled = true))
    }

    @Test
    fun repeatTimingHasStableDefaults() {
        assertEquals(350L, RepeatTiming().initialDelayMs)
        assertEquals(70L, RepeatTiming().intervalMs)
    }

    @Test
    fun gestureLettersNormalizeJitterAndRejectNonLetters() {
        assertEquals("cat", normalizeGestureLetters(listOf("c", "c", "a", "a", "t")))
        assertNull(normalizeGestureLetters(listOf("c", "123", "t")))
    }

    @Test
    fun gestureWordRequiresACommittedPath() {
        assertEquals(
            "cat",
            recognizeGestureWord(listOf("c", "a", "t"), 40f, cancelled = false),
        )
        assertNull(recognizeGestureWord(listOf("c", "a"), 40f, cancelled = true))
    }
}