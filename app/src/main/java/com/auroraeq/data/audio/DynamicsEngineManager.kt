package com.auroraeq.app.data.audio

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Virtualizer
import android.os.Build
import com.auroraeq.app.domain.model.EQ_BAND_COUNT
import com.auroraeq.app.domain.model.EQ_BAND_FREQUENCIES_HZ
import com.auroraeq.app.domain.model.EqLimits
import com.auroraeq.app.domain.model.FilterState
import com.auroraeq.app.domain.model.ShelfState
import com.auroraeq.app.domain.model.SignalChainState
import com.auroraeq.app.domain.model.SpatialState
import com.auroraeq.app.util.AppLog
import kotlin.math.ln
import kotlin.math.max

private const val TAG = "DynamicsEngineManager"
private const val CHANNEL_COUNT = 2

/**
 * What [DynamicsEngineManager.attach] actually managed to bring up. Neither flag implies the other
 * — a device can lack `DynamicsProcessing` (below API 28, or a platform without it) while
 * `Virtualizer` still works, or vice versa. Consumed by
 * [com.auroraeq.app.data.repository.EqRepository] to report accurate `engineReady`/`dspAvailable`
 * state instead of assuming success.
 */
data class EngineCapabilities(
    val dynamicsProcessingAvailable: Boolean,
    val virtualizerAvailable: Boolean,
) {
    val anyAvailable: Boolean
        get() = dynamicsProcessingAvailable || virtualizerAvailable
}

/**
 * Sanitizes a value coming from domain state right before it crosses into a platform API call —
 * defense in depth alongside the domain-level clamping in
 * [com.auroraeq.app.data.repository.EqRepository] and [com.auroraeq.app.data.store.ChainStore],
 * since parameter validation must not rely solely on the UI/domain layer. A NaN/infinite value here
 * would otherwise be handed straight to native DSP code.
 */
private fun Float.finiteOr(default: Float): Float = if (isFinite()) this else default

/**
 * Owns the platform effect chain for exactly one audio session — always session 0 (the
 * shared/global output mix) in this app, attached for the lifetime of
 * [com.auroraeq.app.service.GlobalEqService].
 *
 * Two independent AudioEffect families are combined here:
 *
 * - android.media.audiofx.DynamicsProcessing (API 28+) models the Preamp -> HPF -> Sub Shelf ->
 *   31-band EQ (per channel) -> Air Shelf -> LPF -> Compressor -> Limiter -> Output Gain chain from
 *   a single per-channel(L/R) effect instance. It's the closest framework match to a hardware
 *   dual-channel graphic EQ + dynamics processor, and — like Equalizer — can attach to session 0.
 *   LIMITATION (flagged in the refactor spec, surfaced to the user on the Settings/Help screen):
 *   DynamicsProcessing's EQ bands are parametric/ peaking, not true steep-slope filters or true
 *   shelf filters. Android has no dedicated HPF/LPF/shelf AudioEffect type, so HPF, LPF, Sub Shelf
 *   and Air Shelf are all approximated as gain contours layered onto the nearby 31-band EQ bands
 *   (see [rollOffDb] and [shelfDb]), not real filters. A true filter would require custom native
 *   DSP (Oboe/AAudio) attached to this app's own session only — a third-party app cannot register
 *   HAL-level effects on the shared session without root. The Limiter's native `postGain` field
 *   doubles as the "Output Gain" UI stage (there is no separate native output-trim stage), so
 *   Output Gain only audibly applies while the Limiter stage itself is enabled.
 * - Virtualizer ("Spatial" in the UI) stays as a separate AudioEffect instance alongside
 *   DynamicsProcessing, independently enabled/bypassed. Bass Boost and Loudness Enhancer were
 *   dropped as separate native effects (refactor spec section 9): their role is now covered by Sub
 *   Shelf and the existing Preamp/Output Gain/Limiter stages, avoiding double-processing the same
 *   frequency range.
 */
class DynamicsEngineManager {

    private var dynamicsProcessing: DynamicsProcessing? = null
    private var virtualizer: Virtualizer? = null

    val isAttached: Boolean
        get() = dynamicsProcessing != null

    /**
     * Attaches both effects to [sessionId] and reports what actually came up. `DynamicsProcessing`
     * is API 28+ only — the `Build.VERSION.SDK_INT` guard below is an early return *before* any
     * reference to the class, so devices on the app's API 26 minimum never execute code that
     * touches it. If construction succeeds but enabling the effect throws, the partially-built
     * effect is released immediately instead of leaking its native handle.
     *
     * [onError] receives a short, user-facing message for anything that goes wrong here — but not
     * for the expected/documented API<28 limitation, which is already explained permanently on the
     * Settings screen. The caller ([com.auroraeq.app.data.repository.EqRepository]) turns it into a
     * one-shot event the UI shows as a Toast, since `Log.w` alone is invisible to anyone not
     * reading logcat.
     */
    fun attach(sessionId: Int, onError: (String) -> Unit = {}): EngineCapabilities {
        release()

        dynamicsProcessing =
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                AppLog.i(
                    TAG,
                    "DynamicsProcessing requires API 28+ (this device is API ${Build.VERSION.SDK_INT}); " +
                        "Preamp/HPF/LPF/Shelves/EQ/Compressor/Limiter/Output Gain will have no effect " +
                        "on this device — Spatial (Virtualizer) is unaffected.",
                )
                null
            } else {
                runCatching {
                        val config =
                            DynamicsProcessing.Config.Builder(
                                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                                    CHANNEL_COUNT,
                                    /* preEqInUse = */ true,
                                    EQ_BAND_COUNT,
                                    /* mbcInUse = */ true,
                                    1,
                                    /* postEqInUse = */ false,
                                    0,
                                    /* limiterInUse = */ true,
                                )
                                .build()
                        val dp = DynamicsProcessing(0, sessionId, config)
                        try {
                            dp.enabled = true
                        } catch (t: Throwable) {
                            AppLog.w(
                                TAG,
                                "DynamicsProcessing.enabled=true failed for session $sessionId; releasing partial effect",
                                t,
                            )
                            runCatching { dp.release() }
                            throw t
                        }
                        dp
                    }
                    .onFailure {
                        AppLog.w(TAG, "DynamicsProcessing unavailable for session $sessionId", it)
                        onError("Advanced EQ processing failed to start on this device.")
                    }
                    .getOrNull()
            }

        virtualizer =
            runCatching { Virtualizer(0, sessionId) }
                .onFailure {
                    AppLog.w(TAG, "Virtualizer unavailable for session $sessionId", it)
                    onError("Spatial audio effect unavailable on this device.")
                }
                .getOrNull()

        return EngineCapabilities(
            dynamicsProcessingAvailable = dynamicsProcessing != null,
            virtualizerAvailable = virtualizer != null,
        )
    }

    fun release() {
        runCatching { dynamicsProcessing?.release() }
        runCatching { virtualizer?.release() }
        dynamicsProcessing = null
        virtualizer = null
    }

    /** Pushes the full signal-chain state to the effect. Idempotent/safe to call repeatedly. */
    fun applyChain(chain: SignalChainState) {
        val dp = dynamicsProcessing ?: return
        for (ch in 0 until CHANNEL_COUNT) {
            runCatching {
                // Note the platform API's unusual casing: setInputGainbyChannel (lowercase "by").
                dp.setInputGainbyChannel(
                    ch,
                    if (chain.preamp.enabled) chain.preamp.gainDb.finiteOr(0f) else 0f,
                )
            }

            val isLeft = ch == 0
            val channelEq = if (isLeft || chain.eq.linked) chain.eq.left else chain.eq.right
            // HPF/LPF/Sub Shelf/Air Shelf are all approximated as gain contours on this
            // same shared pre-EQ (see class doc). Each has its own independent enabled
            // flag, so the shared EQ effect must stay on if ANY of them (or the manual
            // 31-band EQ itself) is enabled — otherwise disabling the manual EQ would
            // silently mute the filters/shelves too, even though their own toggles are on.
            val preEqActive =
                chain.eq.enabled ||
                    chain.hpf.enabled ||
                    chain.lpf.enabled ||
                    chain.subShelf.enabled ||
                    chain.airShelf.enabled
            runCatching {
                dp.setPreEqByChannelIndex(
                    ch,
                    DynamicsProcessing.Eq(true, preEqActive, EQ_BAND_COUNT),
                )
            }
            EQ_BAND_FREQUENCIES_HZ.forEachIndexed { band, freqHz ->
                // Manual band gains only apply while the 31-band EQ itself is enabled;
                // the contour stages below apply independently of it.
                val manualGain =
                    if (chain.eq.enabled) channelEq.bandGainsDb.getOrElse(band) { 0f } else 0f
                val contourDb =
                    rollOffDb(freqHz, chain.hpf, isHighPass = true) +
                        rollOffDb(freqHz, chain.lpf, isHighPass = false) +
                        shelfDb(freqHz, chain.subShelf, isLeft, isLowShelf = true) +
                        shelfDb(freqHz, chain.airShelf, isLeft, isLowShelf = false)
                val effectiveGain = (manualGain + contourDb).coerceIn(-60f, 30f)
                runCatching {
                    dp.setPreEqBandByChannelIndex(
                        ch,
                        band,
                        DynamicsProcessing.EqBand(true, freqHz.toFloat(), effectiveGain),
                    )
                }
            }

            runCatching {
                dp.setMbcByChannelIndex(
                    ch,
                    DynamicsProcessing.Mbc(true, chain.compressor.enabled, 1),
                )
            }
            runCatching {
                dp.setMbcBandByChannelIndex(
                    ch,
                    0,
                    DynamicsProcessing.MbcBand(
                        /* enabled = */ true,
                        /* cutoffFrequency = */ 20f,
                        // Attack/release must stay strictly positive — zero or negative
                        // values are meaningless to the native envelope follower.
                        /* attackTime = */ chain.compressor.attackMs
                            .finiteOr(10f)
                            .coerceAtLeast(0.1f),
                        /* releaseTime = */ chain.compressor.releaseMs
                            .finiteOr(100f)
                            .coerceAtLeast(1f),
                        /* ratio = */ chain.compressor.ratio.finiteOr(2f).coerceAtLeast(1f),
                        /* threshold = */ chain.compressor.thresholdDb.finiteOr(-24f),
                        /* kneeWidth = */ 0f,
                        /* noiseGateThreshold = */ -90f,
                        /* expanderRatio = */ 1f,
                        /* preGain = */ 0f,
                        /* postGain = */ 0f,
                    ),
                )
            }

            runCatching {
                dp.setLimiterByChannelIndex(
                    ch,
                    DynamicsProcessing.Limiter(
                        /* inUse = */ true,
                        /* enabled = */ chain.limiter.enabled,
                        /* linkGroup = */ 0,
                        /* attackTime = */ chain.limiter.attackMs.finiteOr(1f).coerceAtLeast(0.1f),
                        /* releaseTime = */ chain.limiter.releaseMs.finiteOr(60f).coerceAtLeast(1f),
                        /* ratio = */ 10f,
                        /* threshold = */ chain.limiter.ceilingDb.finiteOr(-1f),
                        /* postGain = */ if (chain.outputGain.enabled)
                            chain.outputGain.gainDb.finiteOr(0f)
                        else 0f,
                    ),
                )
            }
        }
    }

    /**
     * Applies both spatial fields together so callers never have to sequence two separate engine
     * calls themselves — spatial used to be the only stage that talked to the engine outside the
     * single state->engine->persistence funnel in `EqRepository`.
     */
    fun applySpatial(spatial: SpatialState) {
        setSpatialEnabled(spatial.enabled)
        setSpatialStrength(spatial.strength)
    }

    fun setSpatialEnabled(enabled: Boolean) = runCatching { virtualizer?.enabled = enabled }

    /**
     * Clamps to the platform's documented 0-1000 strength range before the `Int` -> `Short`
     * narrowing conversion — an unclamped out-of-range Int can silently wrap when truncated to
     * `Short`.
     */
    fun setSpatialStrength(strength: Int) = runCatching {
        virtualizer?.setStrength(strength.coerceIn(EqLimits.SPATIAL_STRENGTH).toShort())
    }

    /**
     * Gain roll-off (dB) approximating an HPF (below cutoff) or LPF (above cutoff) at [filter]'s
     * configured slope, evaluated at [freqHz]. See class doc. `internal` (not `private`) so it's
     * directly unit-testable — see `DynamicsEngineManagerMathTest`; this pure function has no
     * Android/native dependency of its own.
     */
    internal fun rollOffDb(freqHz: Int, filter: FilterState, isHighPass: Boolean): Float {
        if (!filter.enabled) return 0f
        val ratio = if (isHighPass) filter.cutoffHz / freqHz else freqHz / filter.cutoffHz
        if (ratio <= 1f) return 0f
        val octaves = ln(ratio.toDouble()) / ln(2.0)
        return max(-72f, (-filter.slope.dbPerOctave * octaves).toFloat())
    }

    /**
     * Gain contour (dB) approximating a low-shelf (Sub Shelf, [isLowShelf]=true) or high-shelf (Air
     * Shelf) at [shelf]'s frequency, transitioning linearly over one octave rather than filtering —
     * a shelf broadly boosts/cuts without removing content, unlike HPF/LPF. See class doc.
     * `internal` for the same testability reason as [rollOffDb].
     */
    internal fun shelfDb(
        freqHz: Int,
        shelf: ShelfState,
        isLeft: Boolean,
        isLowShelf: Boolean,
    ): Float {
        if (!shelf.enabled) return 0f
        val gainDb = if (isLeft || shelf.linked) shelf.leftGainDb else shelf.rightGainDb
        if (gainDb == 0f) return 0f
        val octavesAboveCutoff = ln((freqHz / shelf.freqHz).toDouble()) / ln(2.0)
        val fraction =
            if (isLowShelf) {
                when {
                    octavesAboveCutoff <= 0.0 -> 1.0
                    octavesAboveCutoff >= 1.0 -> 0.0
                    else -> 1.0 - octavesAboveCutoff
                }
            } else {
                when {
                    octavesAboveCutoff >= 0.0 -> 1.0
                    octavesAboveCutoff <= -1.0 -> 0.0
                    else -> 1.0 + octavesAboveCutoff
                }
            }
        return (gainDb * fraction).toFloat()
    }
}
