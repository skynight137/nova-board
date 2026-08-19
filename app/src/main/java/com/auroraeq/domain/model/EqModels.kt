package com.auroraeq.app.domain.model

/**
 * ISO 1/3-octave center frequencies for a 31-band graphic EQ (hardware-style, e.g. Ashly GQX-3102),
 * low -> high.
 */
val EQ_BAND_FREQUENCIES_HZ: List<Int> =
    listOf(
        20,
        25,
        31,
        40,
        50,
        63,
        80,
        100,
        125,
        160,
        200,
        250,
        315,
        400,
        500,
        630,
        800,
        1000,
        1250,
        1600,
        2000,
        2500,
        3150,
        4000,
        5000,
        6300,
        8000,
        10000,
        12500,
        16000,
        20000,
    )
const val EQ_BAND_COUNT = 31

enum class FilterSlope(val dbPerOctave: Int, val label: String) {
    SLOPE_12(12, "12 dB/oct"),
    SLOPE_24(24, "24 dB/oct"),
    SLOPE_36(36, "36 dB/oct"),
}

/**
 * Canonical value ranges enforced by the domain layer itself, not just the UI sliders ("UI slider
 * limits are not a domain guarantee" on their own). Every
 * [com.auroraeq.app.data.repository.EqRepository] setter and
 * [com.auroraeq.app.data.store.ChainStore] load path clamps against these, so a stray UI value,
 * corrupt/legacy persisted JSON, or a future non-UI caller can never push an invalid/out-of-range
 * value toward the platform effect boundary. Kept in one place so UI and persistence can't silently
 * drift apart on what's "valid".
 */
object EqLimits {
    val PREAMP_GAIN_DB = -20f..20f
    val HPF_CUTOFF_HZ = 20f..2000f
    val LPF_CUTOFF_HZ = 2000f..20000f
    val SUB_SHELF_FREQ_HZ = 40f..500f
    val AIR_SHELF_FREQ_HZ = 2000f..16000f
    val SHELF_GAIN_DB = -12f..12f
    val BAND_GAIN_DB = -12f..12f
    val COMPRESSOR_THRESHOLD_DB = -60f..0f
    val COMPRESSOR_RATIO = 1f..20f
    val COMPRESSOR_ATTACK_MS = 1f..100f
    val COMPRESSOR_RELEASE_MS = 10f..500f
    val LIMITER_CEILING_DB = -12f..0f
    val LIMITER_ATTACK_MS = 0.1f..50f
    val LIMITER_RELEASE_MS = 10f..200f
    val OUTPUT_GAIN_DB = -20f..20f
    val SPATIAL_STRENGTH = 0..1000
}

/**
 * Every stage below carries its own [enabled] flag, persisted and applied independently — bypassing
 * one stage must never affect another.
 */
data class PreampState(val enabled: Boolean = true, val gainDb: Float = 0f)

/**
 * HPF/LPF cutoff+slope. NOTE: Android has no dedicated steep-slope filter AudioEffect; this is
 * approximated as a gain roll-off applied to the nearby 31-band EQ bands (see
 * DynamicsEngineManager), not a true Butterworth filter. Documented to the user on the
 * Settings/Help screen.
 */
data class FilterState(
    val enabled: Boolean = false,
    val cutoffHz: Float,
    val slope: FilterSlope = FilterSlope.SLOPE_12,
)

/**
 * A broad low-shelf (Sub Shelf) or high-shelf (Air Shelf) boost/cut, with independent per-channel
 * gain — replaces the old native BassBoost/Loudness effects (refactor spec section 9). Unlike
 * HPF/LPF, a shelf does not remove content past its frequency, it broadly boosts or cuts it;
 * implemented the same way as HPF/LPF, as a gain contour blended into the nearby 31-band EQ bands
 * (see DynamicsEngineManager), since Android has no dedicated native shelf-filter AudioEffect
 * either.
 */
data class ShelfState(
    val enabled: Boolean = false,
    val linked: Boolean = true,
    val freqHz: Float,
    val leftGainDb: Float = 0f,
    val rightGainDb: Float = 0f,
)

data class EqChannelState(val bandGainsDb: List<Float> = List(EQ_BAND_COUNT) { 0f })

data class EqState(
    val enabled: Boolean = true,
    val linked: Boolean = true,
    val left: EqChannelState = EqChannelState(),
    val right: EqChannelState = EqChannelState(),
)

data class CompressorState(
    val enabled: Boolean = false,
    val thresholdDb: Float = -24f,
    val ratio: Float = 2f,
    val attackMs: Float = 10f,
    val releaseMs: Float = 100f,
)

data class LimiterState(
    val enabled: Boolean = true,
    val ceilingDb: Float = -1f,
    val attackMs: Float = 1f,
    val releaseMs: Float = 60f,
)

/**
 * Final trim after the limiter. Implemented via the platform Limiter's own postGain field (there is
 * no separate native "output gain" stage), so in practice this only audibly applies while the
 * Limiter stage itself is enabled — documented in DynamicsEngineManager.
 */
data class OutputGainState(val enabled: Boolean = true, val gainDb: Float = 0f)

/**
 * Cross-channel spatial/phase widening (native Virtualizer). Kept as its own chain stage, renamed
 * "Spatial" — unlike Sub Shelf/Air Shelf, nothing in the EQ chain replicates what Virtualizer does
 * (refactor spec section 9).
 */
data class SpatialState(val enabled: Boolean = false, val strength: Int = 0)

/**
 * Live system media-volume snapshot (STREAM_MUSIC), read directly from [android.media.AudioManager]
 * — not part of [SignalChainState] and not persisted by this app, since Android already persists
 * stream volume itself. Surfaced as the very first Audio Management step so the app can be used as
 * a substitute volume control on devices with unreliable hardware volume buttons.
 */
data class VolumeInfo(val current: Int = 0, val max: Int = 15) {
    val percent: Float
        get() = if (max <= 0) 0f else (current.toFloat() / max) * 100f

    val isMuted: Boolean
        get() = current <= 0
}

/**
 * Input -> Preamp -> HPF -> Sub Shelf -> 31-band EQ (L/R) -> Air Shelf -> LPF -> Compressor ->
 * Limiter -> Output Gain -> Spatial -> Output.
 */
data class SignalChainState(
    val preamp: PreampState = PreampState(),
    val hpf: FilterState = FilterState(cutoffHz = 20f),
    val subShelf: ShelfState = ShelfState(freqHz = 120f),
    val eq: EqState = EqState(),
    val airShelf: ShelfState = ShelfState(freqHz = 8000f),
    val lpf: FilterState = FilterState(cutoffHz = 20000f),
    val compressor: CompressorState = CompressorState(),
    val limiter: LimiterState = LimiterState(),
    val outputGain: OutputGainState = OutputGainState(),
    val spatial: SpatialState = SpatialState(),
)

data class EqUiState(
    val chain: SignalChainState = SignalChainState(),
    val engineReady: Boolean = false,
    /**
     * False when [com.auroraeq.app.data.audio.DynamicsEngineManager] could not attach
     * `DynamicsProcessing` — always the case below API 28, and possible on some OEM builds even at
     * API 28+. When false, Preamp/HPF/LPF/Shelves/ EQ/Compressor/Limiter/Output Gain have no
     * effect; only Spatial (Virtualizer) still works. Surfaced on the Settings screen.
     */
    val dspAvailable: Boolean = true,
)

/**
 * A named, saved snapshot of the full [SignalChainState] — every stage's settings together, not
 * just the 31-band EQ curve. [id] is a stable identifier independent of [name] so renaming a preset
 * never breaks a reference to it. See [com.auroraeq.app.data.store.PresetStore].
 */
data class Preset(
    val id: String,
    val name: String,
    val chain: SignalChainState,
)
