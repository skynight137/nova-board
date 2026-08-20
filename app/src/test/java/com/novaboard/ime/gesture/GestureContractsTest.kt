package com.novaboard.ime.gesture

import org.junit.Assert.assertEquals
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
}