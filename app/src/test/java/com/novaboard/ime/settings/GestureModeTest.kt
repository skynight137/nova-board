package com.novaboard.ime.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureModeTest {
    @Test
    fun unknownStoredModeSafelyFallsBackToFlow() {
        assertEquals(GestureMode.FLOW, GestureMode.fromStored("future-mode"))
        assertEquals(GestureMode.FLOW, GestureMode.fromStored(null))
    }

    @Test
    fun modesHaveStablePreferenceValues() {
        assertEquals("flow", GestureMode.FLOW.storedValue)
        assertEquals("gestures", GestureMode.GESTURES.storedValue)
    }
}