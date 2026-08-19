package com.auroraeq.app.presentation.eq

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks in [volumeIndexForPercent] — the percent<->`AudioManager`-index mapping `VolumeScreen`'s
 * `100f / volume.max` nudge step relies on to move exactly one system volume step per tap. Before
 * that fix, a flat percent step didn't align to the device's real (commonly 15, but OEM-dependent)
 * step count and rounded unevenly onto it, producing inconsistent 6%/7% jumps per tap instead of a
 * clean, single step.
 */
class VolumeStepMappingTest {

    /**
     * Simulates `VolumeScreen`'s nudge button: `100f / max` is the step size passed into
     * `GlassLabeledSlider`, applied the same way `EqViewModel.setVolumePercent` receives it
     * (starting from whatever percent the previous index round-trips to).
     */
    private fun nudgeStep(max: Int): Float = 100f / max

    @Test
    fun `each max has a consistent one-index-per-tap nudge across its full range`() {
        for (max in listOf(7, 15, 25, 30)) {
            val step = nudgeStep(max)
            var index = 0

            // Walk every step from 0 up to max, one tap at a time.
            for (expectedNextIndex in 1..max) {
                val currentPercent = (index.toFloat() / max) * 100f
                val nextPercent = currentPercent + step
                index = volumeIndexForPercent(nextPercent, max)
                assertEquals(
                    "max=$max: nudging up from index ${expectedNextIndex - 1} should land on " +
                        "exactly index $expectedNextIndex, not skip or repeat one",
                    expectedNextIndex,
                    index,
                )
            }

            // And back down again, one tap at a time.
            for (expectedNextIndex in (max - 1) downTo 0) {
                val currentPercent = (index.toFloat() / max) * 100f
                val nextPercent = currentPercent - step
                index = volumeIndexForPercent(nextPercent, max)
                assertEquals(
                    "max=$max: nudging down from index ${expectedNextIndex + 1} should land on " +
                        "exactly index $expectedNextIndex, not skip or repeat one",
                    expectedNextIndex,
                    index,
                )
            }
        }
    }

    @Test
    fun `nudging past either end clamps instead of wrapping or overshooting`() {
        val max = 15
        val step = nudgeStep(max)

        // One tap past the top from the last valid index.
        assertEquals(max, volumeIndexForPercent(100f + step, max))
        // One tap past the bottom from the first valid index.
        assertEquals(0, volumeIndexForPercent(0f - step, max))
    }

    @Test
    fun `percent-to-index rounds to the nearest index, not always down or up`() {
        // max = 15: each index is 100/15 = 6.666...% wide.
        assertEquals(0, volumeIndexForPercent(3f, 15)) // below half a step
        assertEquals(1, volumeIndexForPercent(4f, 15)) // past half a step (3.33%)
        assertEquals(15, volumeIndexForPercent(100f, 15))
        assertEquals(0, volumeIndexForPercent(0f, 15))
    }
}
