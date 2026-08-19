package com.auroraeq.app.presentation.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the value-keyed step-coalescing logic in [HapticStepTicker] — pure Kotlin, no
 * Android/Robolectric dependency needed since the actual vibration call was moved out to a
 * caller-supplied lambda (see `Vibrations.kt` for why it's no longer routed through Compose's
 * `LocalHapticFeedback`).
 */
class HapticStepTickerTest {

    @Test
    fun `first fraction always ticks`() {
        val ticker = HapticStepTicker(steps = 10)
        var ticks = 0
        ticker.onFractionChanged(0f) { ticks++ }
        assertEquals(1, ticks)
    }

    @Test
    fun `repeated fraction within the same step does not re-tick`() {
        val ticker = HapticStepTicker(steps = 10)
        var ticks = 0
        ticker.onFractionChanged(0.5f) { ticks++ }
        ticker.onFractionChanged(0.51f) { ticks++ }
        ticker.onFractionChanged(0.52f) { ticks++ }
        assertEquals(1, ticks)
    }

    @Test
    fun `crossing into a new step ticks again`() {
        val ticker = HapticStepTicker(steps = 10)
        var ticks = 0
        ticker.onFractionChanged(0.10f) { ticks++ } // step 1
        ticker.onFractionChanged(0.21f) { ticks++ } // step 2
        ticker.onFractionChanged(0.32f) { ticks++ } // step 3
        assertEquals(3, ticks)
    }

    @Test
    fun `a fast drag across many steps still ticks once per distinct step`() {
        val ticker = HapticStepTicker(steps = 40)
        var ticks = 0
        // Simulate a fast flick landing on several fractions in one gesture
        // (chosen far enough apart that each rounds to a distinct step).
        listOf(0f, 0.05f, 0.5f, 0.95f, 1f).forEach { fraction ->
            ticker.onFractionChanged(fraction) { ticks++ }
        }
        assertEquals(5, ticks)
    }

    @Test
    fun `moving back to a previously-visited step ticks again`() {
        val ticker = HapticStepTicker(steps = 10)
        var ticks = 0
        ticker.onFractionChanged(0.9f) { ticks++ } // step 9
        ticker.onFractionChanged(0.5f) { ticks++ } // step 5
        ticker.onFractionChanged(0.9f) { ticks++ } // back to step 9
        assertEquals(3, ticks)
    }
}
