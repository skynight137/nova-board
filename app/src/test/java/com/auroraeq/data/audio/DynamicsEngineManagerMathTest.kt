package com.auroraeq.app.data.audio

import com.auroraeq.app.domain.model.FilterSlope
import com.auroraeq.app.domain.model.FilterState
import com.auroraeq.app.domain.model.ShelfState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DynamicsEngineManager.rollOffDb]/[DynamicsEngineManager.shelfDb] — the
 * HPF/LPF/shelf gain-contour approximations described to users on the Settings screen as "not a
 * true filter". These are the most novel, least-standard math in the app, and previously had zero
 * coverage even though they're pure functions with no Android/native dependency — a regression here
 * would silently change the audible EQ curve with no test failure to catch it.
 */
class DynamicsEngineManagerMathTest {

    private val engine = DynamicsEngineManager()

    // ---- rollOffDb (HPF/LPF) ----

    @Test
    fun `disabled filter never rolls off`() {
        val hpf = FilterState(enabled = false, cutoffHz = 200f)
        assertEquals(0f, engine.rollOffDb(20, hpf, isHighPass = true))
    }

    @Test
    fun `HPF passes frequencies at or above cutoff untouched`() {
        val hpf = FilterState(enabled = true, cutoffHz = 200f)
        assertEquals(0f, engine.rollOffDb(200, hpf, isHighPass = true))
        assertEquals(0f, engine.rollOffDb(2000, hpf, isHighPass = true))
    }

    @Test
    fun `HPF attenuates below cutoff proportionally to slope and octaves`() {
        // One octave below a 200 Hz cutoff (100 Hz) at 12 dB/oct should roll
        // off by exactly one slope step.
        val hpf = FilterState(enabled = true, cutoffHz = 200f, slope = FilterSlope.SLOPE_12)
        assertEquals(-12f, engine.rollOffDb(100, hpf, isHighPass = true), 0.01f)

        // Two octaves below, roll-off doubles.
        assertEquals(-24f, engine.rollOffDb(50, hpf, isHighPass = true), 0.01f)

        // A steeper configured slope attenuates more at the same distance.
        val steeper = hpf.copy(slope = FilterSlope.SLOPE_36)
        assertEquals(-36f, engine.rollOffDb(100, steeper, isHighPass = true), 0.01f)
    }

    @Test
    fun `HPF roll-off is clamped at -72 dB far below cutoff`() {
        val hpf = FilterState(enabled = true, cutoffHz = 20000f, slope = FilterSlope.SLOPE_36)
        assertEquals(-72f, engine.rollOffDb(20, hpf, isHighPass = true))
    }

    @Test
    fun `LPF passes frequencies at or below cutoff untouched, attenuates above`() {
        val lpf = FilterState(enabled = true, cutoffHz = 2000f, slope = FilterSlope.SLOPE_12)
        assertEquals(0f, engine.rollOffDb(2000, lpf, isHighPass = false))
        assertEquals(0f, engine.rollOffDb(200, lpf, isHighPass = false))
        assertEquals(-12f, engine.rollOffDb(4000, lpf, isHighPass = false), 0.01f)
    }

    // ---- shelfDb (Sub Shelf / Air Shelf) ----

    @Test
    fun `disabled shelf never contributes gain`() {
        val shelf = ShelfState(enabled = false, freqHz = 120f, leftGainDb = 6f, rightGainDb = 6f)
        assertEquals(0f, engine.shelfDb(20, shelf, isLeft = true, isLowShelf = true))
    }

    @Test
    fun `zero-gain shelf never contributes even when enabled`() {
        val shelf = ShelfState(enabled = true, freqHz = 120f, leftGainDb = 0f, rightGainDb = 0f)
        assertEquals(0f, engine.shelfDb(20, shelf, isLeft = true, isLowShelf = true))
    }

    @Test
    fun `low shelf applies full gain at and below its frequency, fades out over one octave above`() {
        val shelf = ShelfState(enabled = true, freqHz = 120f, leftGainDb = 6f, rightGainDb = 6f)
        // At/below the shelf frequency: full gain.
        assertEquals(6f, engine.shelfDb(120, shelf, isLeft = true, isLowShelf = true), 0.01f)
        assertEquals(6f, engine.shelfDb(60, shelf, isLeft = true, isLowShelf = true), 0.01f)
        // One octave above: fully faded to zero.
        assertEquals(0f, engine.shelfDb(240, shelf, isLeft = true, isLowShelf = true), 0.01f)
        // Half an octave above (~170 Hz): roughly half the gain.
        val halfOctaveUp = (120 * Math.pow(2.0, 0.5)).toInt()
        assertEquals(
            3f,
            engine.shelfDb(halfOctaveUp, shelf, isLeft = true, isLowShelf = true),
            0.5f,
        )
    }

    @Test
    fun `high shelf applies full gain at and above its frequency, fades out over one octave below`() {
        val shelf = ShelfState(enabled = true, freqHz = 8000f, leftGainDb = -6f, rightGainDb = -6f)
        assertEquals(-6f, engine.shelfDb(8000, shelf, isLeft = true, isLowShelf = false), 0.01f)
        assertEquals(-6f, engine.shelfDb(16000, shelf, isLeft = true, isLowShelf = false), 0.01f)
        // One octave below: fully faded to zero.
        assertEquals(0f, engine.shelfDb(4000, shelf, isLeft = true, isLowShelf = false), 0.01f)
    }

    @Test
    fun `unlinked shelf uses the right channel gain only when not linked and not left`() {
        val shelf =
            ShelfState(
                enabled = true,
                linked = false,
                freqHz = 120f,
                leftGainDb = 4f,
                rightGainDb = -2f,
            )
        assertEquals(4f, engine.shelfDb(120, shelf, isLeft = true, isLowShelf = true), 0.01f)
        assertEquals(-2f, engine.shelfDb(120, shelf, isLeft = false, isLowShelf = true), 0.01f)
    }

    @Test
    fun `linked shelf uses the left channel gain for both channels`() {
        val shelf =
            ShelfState(
                enabled = true,
                linked = true,
                freqHz = 120f,
                leftGainDb = 5f,
                rightGainDb = -9f,
            )
        assertEquals(5f, engine.shelfDb(120, shelf, isLeft = true, isLowShelf = true), 0.01f)
        assertEquals(5f, engine.shelfDb(120, shelf, isLeft = false, isLowShelf = true), 0.01f)
    }

    @Test
    fun `roll-off and shelf math never produce NaN or infinite values`() {
        val hpf = FilterState(enabled = true, cutoffHz = 20f)
        val shelf = ShelfState(enabled = true, freqHz = 120f, leftGainDb = 6f, rightGainDb = 6f)
        for (freq in listOf(20, 1, 20000, 200000)) {
            assertTrue(engine.rollOffDb(freq, hpf, isHighPass = true).isFinite())
            assertTrue(engine.shelfDb(freq, shelf, isLeft = true, isLowShelf = true).isFinite())
        }
    }
}
