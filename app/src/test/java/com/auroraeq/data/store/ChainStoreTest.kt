package com.auroraeq.app.data.store

import com.auroraeq.app.domain.model.EQ_BAND_COUNT
import com.auroraeq.app.domain.model.EqLimits
import com.auroraeq.app.domain.model.SignalChainState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the malformed-persisted-data handling called out as an open item in replit.md's "Known
 * issues" section, plus the `onError` callback added for user-facing Toast reporting (see
 * `.agents/memory/aurora-eq-error-reporting.md`). Exercises [parseChainStateJson] directly rather
 * than going through [ChainStore] itself, since the latter needs a real `android.content.Context` /
 * `SharedPreferences` that aren't available under plain JVM unit tests (see `app/build.gradle`'s
 * `testOptions` comment).
 */
class ChainStoreTest {

    @Test
    fun `corrupt JSON falls back to defaults and reports an error`() {
        var reported: String? = null
        val state = parseChainStateJson("{not valid json at all", onError = { reported = it })

        assertEquals(SignalChainState(), state)
        assertEquals("Saved settings were corrupted and have been reset to defaults.", reported)
    }

    @Test
    fun `valid JSON does not report an error`() {
        var reported: String? = null
        parseChainStateJson(JSONObject().toString(), onError = { reported = it })

        assertEquals(null, reported)
    }

    @Test
    fun `out-of-range persisted values are clamped instead of trusted`() {
        val raw =
            JSONObject()
                .apply {
                    put("preampGainDb", 999.0) // way above EqLimits.PREAMP_GAIN_DB's upper bound
                    put("spatialStrength", -50) // below EqLimits.SPATIAL_STRENGTH's lower bound
                }
                .toString()

        val state = parseChainStateJson(raw)

        assertEquals(EqLimits.PREAMP_GAIN_DB.endInclusive, state.preamp.gainDb)
        assertEquals(EqLimits.SPATIAL_STRENGTH.first, state.spatial.strength)
    }

    @Test
    fun `truncated band array is padded to the full band count instead of crashing`() {
        val raw =
            JSONObject()
                .apply {
                    // Only 3 of the expected 31 bands persisted (simulating a
                    // partial write or a future band-count change).
                    put("eqLeft", JSONArray(listOf(1.0, 2.0, 3.0)))
                }
                .toString()

        val state = parseChainStateJson(raw)

        assertEquals(EQ_BAND_COUNT, state.eq.left.bandGainsDb.size)
        assertEquals(1f, state.eq.left.bandGainsDb[0])
        assertEquals(0f, state.eq.left.bandGainsDb[30]) // missing entries default to flat (0 dB)
    }

    @Test
    fun `oversized band gain is clamped rather than passed straight to the platform effect`() {
        val raw =
            JSONObject()
                .apply {
                    put("eqLeft", JSONArray(List(EQ_BAND_COUNT) { 999.0 }))
                }
                .toString()

        val state = parseChainStateJson(raw)

        assertEquals(EqLimits.BAND_GAIN_DB.endInclusive, state.eq.left.bandGainsDb[0])
    }

    @Test
    fun `missing optional sections fall back to sensible per-field defaults`() {
        val state = parseChainStateJson(JSONObject().toString())

        assertTrue(state.eq.enabled)
        assertFalse(state.hpf.enabled)
        assertEquals(20f, state.hpf.cutoffHz)
        assertEquals(20000f, state.lpf.cutoffHz)
    }
}
