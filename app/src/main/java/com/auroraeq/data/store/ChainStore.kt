package com.auroraeq.app.data.store

import android.content.Context
import android.content.SharedPreferences
import com.auroraeq.app.domain.model.CompressorState
import com.auroraeq.app.domain.model.EQ_BAND_COUNT
import com.auroraeq.app.domain.model.EqChannelState
import com.auroraeq.app.domain.model.EqLimits
import com.auroraeq.app.domain.model.EqState
import com.auroraeq.app.domain.model.FilterSlope
import com.auroraeq.app.domain.model.FilterState
import com.auroraeq.app.domain.model.LimiterState
import com.auroraeq.app.domain.model.OutputGainState
import com.auroraeq.app.domain.model.PreampState
import com.auroraeq.app.domain.model.ShelfState
import com.auroraeq.app.domain.model.SignalChainState
import com.auroraeq.app.domain.model.SpatialState
import com.auroraeq.app.util.AppLog
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "ChainStore"
private const val PREFS_NAME = "aurora_eq_prefs"
private const val KEY_CHAIN_STATE = "chain_state_v3"

/**
 * Persists the full signal-chain state as JSON in SharedPreferences. org.json is part of the
 * Android runtime, so this needs no extra dependency, and is exercised by JVM unit tests via the
 * `org.json:json` test-only dependency in app/build.gradle.
 *
 * Every numeric field is clamped against [EqLimits] on load, so corrupt or hand-edited prefs can
 * never hand an out-of-range value to [com.auroraeq.app.data.repository.EqRepository] or the
 * platform effect boundary — the UI's own slider ranges are not a substitute for this.
 *
 * No cross-app-version migration/schema-versioning here by design: releases are distributed as
 * uninstall-then-reinstall, not in-place upgrades, so a fresh install never sees another version's
 * persisted JSON shape.
 *
 * Named presets/config save-load are deferred (refactor spec section 7) — this store only persists
 * the single live chain state, not named snapshots.
 */
class ChainStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * [onError] receives a short, user-facing message when persisted state can't be parsed and
     * defaults are used instead — `Log.w` alone never reaches anyone who isn't reading logcat, so
     * the caller ([com.auroraeq.app.data.repository.EqRepository]) turns this into a one-shot event
     * the UI shows as a Toast.
     */
    fun loadChainState(onError: (String) -> Unit = {}): SignalChainState {
        val raw = prefs.getString(KEY_CHAIN_STATE, null) ?: return SignalChainState()
        return parseChainStateJson(raw, onError)
    }

    fun saveChainState(chain: SignalChainState) {
        prefs.edit().putString(KEY_CHAIN_STATE, chainStateToJson(chain).toString()).apply()
    }
}

/**
 * The reverse of [parseChainStateJson] — shared with [com.auroraeq.app.data.store.PresetStore] so a
 * saved preset's chain is serialized identically to the live chain state, instead of duplicating
 * this field list a second time.
 */
internal fun chainStateToJson(chain: SignalChainState): JSONObject =
    JSONObject().apply {
        put("preampEnabled", chain.preamp.enabled)
        put("preampGainDb", chain.preamp.gainDb.toDouble())
        put("hpf", writeFilter(chain.hpf))
        put("subShelf", writeShelf(chain.subShelf))
        put("eqEnabled", chain.eq.enabled)
        put("eqLinked", chain.eq.linked)
        put("eqLeft", JSONArray(chain.eq.left.bandGainsDb))
        put("eqRight", JSONArray(chain.eq.right.bandGainsDb))
        put("airShelf", writeShelf(chain.airShelf))
        put("lpf", writeFilter(chain.lpf))
        put("compEnabled", chain.compressor.enabled)
        put("compThreshold", chain.compressor.thresholdDb.toDouble())
        put("compRatio", chain.compressor.ratio.toDouble())
        put("compAttack", chain.compressor.attackMs.toDouble())
        put("compRelease", chain.compressor.releaseMs.toDouble())
        put("limEnabled", chain.limiter.enabled)
        put("limCeiling", chain.limiter.ceilingDb.toDouble())
        put("limAttack", chain.limiter.attackMs.toDouble())
        put("limRelease", chain.limiter.releaseMs.toDouble())
        put("outGainEnabled", chain.outputGain.enabled)
        put("outGainDb", chain.outputGain.gainDb.toDouble())
        put("spatialEnabled", chain.spatial.enabled)
        put("spatialStrength", chain.spatial.strength)
    }

/**
 * The actual JSON -> [SignalChainState] parsing, pulled out of [ChainStore] as a top-level
 * `internal` function so a JVM unit test can exercise corrupt-JSON handling directly with a
 * hand-crafted [raw] string, without needing a real [android.content.Context]/`SharedPreferences`
 * (unavailable under plain JUnit — see `app/build.gradle`'s `testOptions` comment). [onError]
 * mirrors [ChainStore.loadChainState]'s callback.
 */
internal fun parseChainStateJson(raw: String, onError: (String) -> Unit = {}): SignalChainState =
    runCatching {
            val obj = JSONObject(raw)
            SignalChainState(
                preamp =
                    PreampState(
                        enabled = obj.optBoolean("preampEnabled", true),
                        gainDb =
                            obj.optDouble("preampGainDb", 0.0)
                                .toFloat()
                                .coerceIn(EqLimits.PREAMP_GAIN_DB),
                    ),
                hpf =
                    readFilter(
                        obj.optJSONObject("hpf"),
                        defaultCutoff = 20f,
                        range = EqLimits.HPF_CUTOFF_HZ,
                    ),
                subShelf =
                    readShelf(
                        obj.optJSONObject("subShelf"),
                        defaultFreq = 120f,
                        freqRange = EqLimits.SUB_SHELF_FREQ_HZ,
                    ),
                eq =
                    EqState(
                        enabled = obj.optBoolean("eqEnabled", true),
                        linked = obj.optBoolean("eqLinked", true),
                        left = EqChannelState(readBandArray(obj.optJSONArray("eqLeft"))),
                        right = EqChannelState(readBandArray(obj.optJSONArray("eqRight"))),
                    ),
                airShelf =
                    readShelf(
                        obj.optJSONObject("airShelf"),
                        defaultFreq = 8000f,
                        freqRange = EqLimits.AIR_SHELF_FREQ_HZ,
                    ),
                lpf =
                    readFilter(
                        obj.optJSONObject("lpf"),
                        defaultCutoff = 20000f,
                        range = EqLimits.LPF_CUTOFF_HZ,
                    ),
                compressor =
                    CompressorState(
                        enabled = obj.optBoolean("compEnabled", false),
                        thresholdDb =
                            obj.optDouble("compThreshold", -24.0)
                                .toFloat()
                                .coerceIn(EqLimits.COMPRESSOR_THRESHOLD_DB),
                        ratio =
                            obj.optDouble("compRatio", 2.0)
                                .toFloat()
                                .coerceIn(EqLimits.COMPRESSOR_RATIO),
                        attackMs =
                            obj.optDouble("compAttack", 10.0)
                                .toFloat()
                                .coerceIn(EqLimits.COMPRESSOR_ATTACK_MS),
                        releaseMs =
                            obj.optDouble("compRelease", 100.0)
                                .toFloat()
                                .coerceIn(EqLimits.COMPRESSOR_RELEASE_MS),
                    ),
                limiter =
                    LimiterState(
                        enabled = obj.optBoolean("limEnabled", true),
                        ceilingDb =
                            obj.optDouble("limCeiling", -1.0)
                                .toFloat()
                                .coerceIn(EqLimits.LIMITER_CEILING_DB),
                        attackMs =
                            obj.optDouble("limAttack", 1.0)
                                .toFloat()
                                .coerceIn(EqLimits.LIMITER_ATTACK_MS),
                        releaseMs =
                            obj.optDouble("limRelease", 60.0)
                                .toFloat()
                                .coerceIn(EqLimits.LIMITER_RELEASE_MS),
                    ),
                outputGain =
                    OutputGainState(
                        enabled = obj.optBoolean("outGainEnabled", true),
                        gainDb =
                            obj.optDouble("outGainDb", 0.0)
                                .toFloat()
                                .coerceIn(EqLimits.OUTPUT_GAIN_DB),
                    ),
                spatial =
                    SpatialState(
                        enabled = obj.optBoolean("spatialEnabled", false),
                        strength =
                            obj.optInt("spatialStrength", 0).coerceIn(EqLimits.SPATIAL_STRENGTH),
                    ),
            )
        }
        .onFailure {
            AppLog.w(TAG, "Corrupt persisted chain state JSON; falling back to defaults", it)
            onError("Saved settings were corrupted and have been reset to defaults.")
        }
        .getOrDefault(SignalChainState())

private fun readFilter(
    obj: JSONObject?,
    defaultCutoff: Float,
    range: ClosedFloatingPointRange<Float>,
): FilterState {
    if (obj == null) return FilterState(cutoffHz = defaultCutoff)
    return FilterState(
        enabled = obj.optBoolean("enabled", false),
        cutoffHz = obj.optDouble("cutoffHz", defaultCutoff.toDouble()).toFloat().coerceIn(range),
        slope =
            FilterSlope.entries.firstOrNull { it.name == obj.optString("slope") }
                ?: FilterSlope.SLOPE_12,
    )
}

private fun writeFilter(filter: FilterState): JSONObject =
    JSONObject().apply {
        put("enabled", filter.enabled)
        put("cutoffHz", filter.cutoffHz.toDouble())
        put("slope", filter.slope.name)
    }

private fun readShelf(
    obj: JSONObject?,
    defaultFreq: Float,
    freqRange: ClosedFloatingPointRange<Float>,
): ShelfState {
    if (obj == null) return ShelfState(freqHz = defaultFreq)
    return ShelfState(
        enabled = obj.optBoolean("enabled", false),
        linked = obj.optBoolean("linked", true),
        freqHz = obj.optDouble("freqHz", defaultFreq.toDouble()).toFloat().coerceIn(freqRange),
        leftGainDb = obj.optDouble("leftGainDb", 0.0).toFloat().coerceIn(EqLimits.SHELF_GAIN_DB),
        rightGainDb = obj.optDouble("rightGainDb", 0.0).toFloat().coerceIn(EqLimits.SHELF_GAIN_DB),
    )
}

private fun writeShelf(shelf: ShelfState): JSONObject =
    JSONObject().apply {
        put("enabled", shelf.enabled)
        put("linked", shelf.linked)
        put("freqHz", shelf.freqHz.toDouble())
        put("leftGainDb", shelf.leftGainDb.toDouble())
        put("rightGainDb", shelf.rightGainDb.toDouble())
    }

/**
 * Always returns exactly [EQ_BAND_COUNT] entries, regardless of what was actually persisted — a
 * truncated/oversized/corrupt array (partial write, hand-edited prefs, or a future band-count
 * change) must never propagate a wrong-length list into [EqChannelState], since callers index into
 * it by band position and would otherwise crash. Missing/invalid entries default to 0 dB (flat)
 * rather than failing the whole chain load; every entry is also clamped to [EqLimits.BAND_GAIN_DB].
 */
private fun readBandArray(array: JSONArray?): List<Float> {
    if (array == null) return List(EQ_BAND_COUNT) { 0f }
    return List(EQ_BAND_COUNT) { i ->
        array.optDouble(i, 0.0).toFloat().coerceIn(EqLimits.BAND_GAIN_DB)
    }
}
