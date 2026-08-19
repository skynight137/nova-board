package com.auroraeq.app.data.repository

import com.auroraeq.app.data.audio.DynamicsEngineManager
import com.auroraeq.app.data.store.ChainStore
import com.auroraeq.app.data.store.PresetStore
import com.auroraeq.app.data.store.parseImportedPresetsJson
import com.auroraeq.app.data.store.presetToJson
import com.auroraeq.app.data.store.presetsToJson
import com.auroraeq.app.domain.model.EqChannelState
import com.auroraeq.app.domain.model.EqLimits
import com.auroraeq.app.domain.model.EqUiState
import com.auroraeq.app.domain.model.FilterSlope
import com.auroraeq.app.domain.model.Preset
import com.auroraeq.app.domain.model.ShelfState
import com.auroraeq.app.domain.model.SignalChainState
import com.auroraeq.app.util.FrameThrottler
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Single source of truth for the signal-chain state, shared between the UI and
 * [com.auroraeq.app.service.GlobalEqService]. The engine is attached exactly once, unconditionally,
 * for the service's lifetime — there is no "Global Mode" toggle (see refactor spec section 2).
 *
 * Every stage below has its own persisted enabled flag and is applied to the engine independently —
 * no shared "master" flag ever forces a child stage's enabled state (fixes the bug in refactor spec
 * section 4).
 */
class EqRepository(private val chainStore: ChainStore, private val presetStore: PresetStore) {

    private val engine = DynamicsEngineManager()

    /**
     * Serializes every read-modify-write against [_uiState] together with the engine calls and
     * persistence write it triggers, so a state update, an `attachEngine`/`releaseEngine` lifecycle
     * call, and the resulting engine application can never interleave — spatial used to be the only
     * stage applying its engine call outside this funnel, and lifecycle/state operations were
     * otherwise unsynchronized. Plain `synchronized` is sufficient here: every method it guards is
     * a fast, non-suspending call.
     */
    private val lock = Any()

    /**
     * One-shot, user-facing error messages (engine attach failures, corrupt persisted state, etc.).
     * Uses `replay`, not just `extraBufferCapacity`, so an error raised before the UI subscribes
     * (e.g. during [com.auroraeq.app.EqApplication.onCreate], well before `AppNavigation`'s
     * `LaunchedEffect` attaches its collector) is still delivered once collection starts, rather
     * than lost — `extraBufferCapacity` alone only helps an *already-subscribed* slow collector, it
     * does not replay to a subscriber that attaches later (confirmed by
     * `ErrorToastRobolectricTest`, which caught this as a real bug: with `replay = 0`, both
     * startup-time corruption errors were silently dropped before `EqApplication.onCreate` ever
     * finished). The UI (`AppNavigation`) collects this and shows each message as a Toast; this is
     * a deliberate upgrade from `Log.w`-only reporting, which is invisible to anyone not reading
     * logcat. Replay count of 8 comfortably covers every error this app can raise at startup
     * (corrupt chain state, corrupt presets, DynamicsProcessing attach failure, Virtualizer attach
     * failure) with room to spare; the trade-off is that a *second* collector attaching later (e.g.
     * after an Activity recreation that doesn't retain the ViewModel) would see already-handled
     * errors replayed once more — an acceptable cost against silently losing them.
     */
    private val _errorEvents = MutableSharedFlow<String>(replay = 8)
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    private val _uiState =
        MutableStateFlow(
            EqUiState(chain = chainStore.loadChainState(onError = _errorEvents::tryEmit))
        )
    val uiState: StateFlow<EqUiState> = _uiState.asStateFlow()

    /**
     * Named full-chain snapshots (see [Preset]) — independent of the live [uiState]/[ChainStore]
     * state; applying one overwrites the live state (through the same [updateChain] funnel as every
     * other mutation), it doesn't replace it permanently.
     */
    private val _presets =
        MutableStateFlow(presetStore.loadPresets(onError = _errorEvents::tryEmit))
    val presets: StateFlow<List<Preset>> = _presets.asStateFlow()

    /**
     * Coalesces [updateChain]'s engine-apply + persist step to at most once per frame — without
     * this, dragging any slider reapplies the *entire* 31-band x 2-channel chain to the engine and
     * rewrites the full chain state to `SharedPreferences` on every single pointer-move event. The
     * `_uiState` update below stays outside the throttle so the UI itself never lags.
     */
    private val engineApplyThrottler = FrameThrottler()

    fun saveCurrentAsPreset(name: String) =
        synchronized(lock) {
            val preset =
                Preset(id = UUID.randomUUID().toString(), name = name, chain = _uiState.value.chain)
            _presets.update { it + preset }
            presetStore.savePresets(_presets.value)
        }

    /**
     * Overwrites the live chain state with [id]'s saved snapshot, applying it to the engine and
     * persisting it exactly like any other chain edit — a preset is a shortcut for "set every stage
     * to this", not a separate mode. No-ops if [id] no longer exists (e.g. deleted from another
     * screen instance).
     */
    fun applyPreset(id: String) {
        val preset = _presets.value.firstOrNull { it.id == id } ?: return
        updateChain { preset.chain }
    }

    /**
     * Restores the live signal chain to its domain defaults. System media volume and named presets
     * are intentionally outside this operation and remain unchanged.
     */
    fun resetAllProcessing() = updateChain { SignalChainState() }

    fun renamePreset(id: String, newName: String) =
        synchronized(lock) {
            _presets.update { list ->
                list.map { if (it.id == id) it.copy(name = newName) else it }
            }
            presetStore.savePresets(_presets.value)
        }

    fun deletePreset(id: String) =
        synchronized(lock) {
            _presets.update { list -> list.filterNot { it.id == id } }
            presetStore.savePresets(_presets.value)
        }

    /**
     * JSON for sharing a single preset (`PresetsScreen`'s per-row share action) — the same shape
     * one entry of [exportAllPresetsJson] uses, so a shared file and an "export all" file are
     * interchangeable on import. Null if [id] no longer exists.
     */
    fun exportPresetJson(id: String): String? =
        _presets.value.firstOrNull { it.id == id }?.let { presetToJson(it).toString() }

    /** JSON for the "Export all" header action — every current preset as one JSON array file. */
    fun exportAllPresetsJson(): String = presetsToJson(_presets.value)

    /**
     * Imports presets from [raw] JSON (either shape [exportPresetJson] or [exportAllPresetsJson]
     * produces) and appends them to the current list. Never overwrites an existing preset: an id
     * collision gets a fresh generated id, and a name collision keeps both by suffixing " (2)", "
     * (3)", etc. Returns how many presets were actually imported (0 if [raw] was
     * empty/corrupt/unrecognized, in which case [errorEvents] also reports it).
     */
    fun importPresets(raw: String): Int =
        synchronized(lock) {
            val imported = parseImportedPresetsJson(raw, onError = _errorEvents::tryEmit)
            if (imported.isEmpty()) return@synchronized 0

            val takenIds = _presets.value.mapTo(mutableSetOf()) { it.id }
            val takenNames = _presets.value.mapTo(mutableSetOf()) { it.name }
            val toAdd = imported.map { preset ->
                val id =
                    if (takenIds.add(preset.id)) preset.id
                    else UUID.randomUUID().toString().also { takenIds += it }
                val name = uniqueName(preset.name, takenNames)
                takenNames += name
                preset.copy(id = id, name = name)
            }

            _presets.update { it + toAdd }
            presetStore.savePresets(_presets.value)
            toAdd.size
        }

    /**
     * [base] unchanged if it's not already in [taken]; otherwise the first "[base] (n)" (n = 2, 3,
     * ...) not already in [taken] — keeps both presets instead of silently overwriting one with the
     * same name.
     */
    private fun uniqueName(base: String, taken: Set<String>): String {
        if (base !in taken) return base
        var suffix = 2
        while ("$base ($suffix)" in taken) suffix++
        return "$base ($suffix)"
    }

    // ---- Engine lifecycle (owned by GlobalEqService) ----

    fun attachEngine(sessionId: Int) =
        synchronized(lock) {
            val capabilities = engine.attach(sessionId, onError = _errorEvents::tryEmit)
            pushFullStateToEngine()
            _uiState.update {
                it.copy(
                    engineReady = capabilities.anyAvailable,
                    dspAvailable = capabilities.dynamicsProcessingAvailable,
                )
            }
        }

    fun releaseEngine() =
        synchronized(lock) {
            engine.release()
            _uiState.update { it.copy(engineReady = false) }
        }

    private fun pushFullStateToEngine() {
        val state = _uiState.value
        engine.applyChain(state.chain)
        engine.applySpatial(state.chain.spatial)
    }

    // ---- Preamp ----

    fun setPreampEnabled(enabled: Boolean) = updateChain {
        it.copy(preamp = it.preamp.copy(enabled = enabled))
    }

    fun setPreampGain(gainDb: Float) = updateChain {
        it.copy(preamp = it.preamp.copy(gainDb = gainDb.coerceIn(EqLimits.PREAMP_GAIN_DB)))
    }

    fun resetPreamp() = updateChain { it.copy(preamp = SignalChainState().preamp) }

    // ---- HPF / LPF ----

    fun setHpfEnabled(enabled: Boolean) = updateChain {
        it.copy(hpf = it.hpf.copy(enabled = enabled))
    }

    fun setHpfCutoff(cutoffHz: Float) = updateChain {
        it.copy(hpf = it.hpf.copy(cutoffHz = cutoffHz.coerceIn(EqLimits.HPF_CUTOFF_HZ)))
    }

    fun setHpfSlope(slope: FilterSlope) = updateChain { it.copy(hpf = it.hpf.copy(slope = slope)) }

    fun resetHpf() = updateChain { it.copy(hpf = SignalChainState().hpf) }

    fun setLpfEnabled(enabled: Boolean) = updateChain {
        it.copy(lpf = it.lpf.copy(enabled = enabled))
    }

    fun setLpfCutoff(cutoffHz: Float) = updateChain {
        it.copy(lpf = it.lpf.copy(cutoffHz = cutoffHz.coerceIn(EqLimits.LPF_CUTOFF_HZ)))
    }

    fun setLpfSlope(slope: FilterSlope) = updateChain { it.copy(lpf = it.lpf.copy(slope = slope)) }

    fun resetLpf() = updateChain { it.copy(lpf = SignalChainState().lpf) }

    // ---- Sub Shelf / Air Shelf ----

    fun setSubShelfEnabled(enabled: Boolean) = updateChain {
        it.copy(subShelf = it.subShelf.copy(enabled = enabled))
    }

    fun setSubShelfLinked(linked: Boolean) = updateChain {
        it.copy(subShelf = it.subShelf.linkedCopy(linked))
    }

    fun setSubShelfFreq(freqHz: Float) = updateChain {
        it.copy(subShelf = it.subShelf.copy(freqHz = freqHz.coerceIn(EqLimits.SUB_SHELF_FREQ_HZ)))
    }

    fun setSubShelfGain(channelIsLeft: Boolean, gainDb: Float) = updateChain {
        it.copy(
            subShelf = it.subShelf.withGain(channelIsLeft, gainDb.coerceIn(EqLimits.SHELF_GAIN_DB))
        )
    }

    fun resetSubShelf() = updateChain { it.copy(subShelf = SignalChainState().subShelf) }

    fun setAirShelfEnabled(enabled: Boolean) = updateChain {
        it.copy(airShelf = it.airShelf.copy(enabled = enabled))
    }

    fun setAirShelfLinked(linked: Boolean) = updateChain {
        it.copy(airShelf = it.airShelf.linkedCopy(linked))
    }

    fun setAirShelfFreq(freqHz: Float) = updateChain {
        it.copy(airShelf = it.airShelf.copy(freqHz = freqHz.coerceIn(EqLimits.AIR_SHELF_FREQ_HZ)))
    }

    fun setAirShelfGain(channelIsLeft: Boolean, gainDb: Float) = updateChain {
        it.copy(
            airShelf = it.airShelf.withGain(channelIsLeft, gainDb.coerceIn(EqLimits.SHELF_GAIN_DB))
        )
    }

    fun resetAirShelf() = updateChain { it.copy(airShelf = SignalChainState().airShelf) }

    private fun ShelfState.linkedCopy(linked: Boolean): ShelfState =
        copy(linked = linked, rightGainDb = if (linked) leftGainDb else rightGainDb)

    private fun ShelfState.withGain(channelIsLeft: Boolean, gainDb: Float): ShelfState =
        when {
            linked -> copy(leftGainDb = gainDb, rightGainDb = gainDb)
            channelIsLeft -> copy(leftGainDb = gainDb)
            else -> copy(rightGainDb = gainDb)
        }

    // ---- 31-band EQ ----

    fun setEqEnabled(enabled: Boolean) = updateChain { it.copy(eq = it.eq.copy(enabled = enabled)) }

    fun setEqLinked(linked: Boolean) = updateChain { chain ->
        val eq = chain.eq
        chain.copy(eq = eq.copy(linked = linked, right = if (linked) eq.left else eq.right))
    }

    fun setBandGain(channelIsLeft: Boolean, band: Int, gainDb: Float) = updateChain { chain ->
        val clampedGain = gainDb.coerceIn(EqLimits.BAND_GAIN_DB)
        val eq = chain.eq
        val newLeft = if (channelIsLeft) eq.left.withBand(band, clampedGain) else eq.left
        val newRight =
            when {
                eq.linked -> newLeft
                channelIsLeft -> eq.right
                else -> eq.right.withBand(band, clampedGain)
            }
        chain.copy(eq = eq.copy(left = newLeft, right = newRight))
    }

    /**
     * Defensively no-ops on an out-of-range [band] instead of throwing — belt-and-suspenders
     * alongside [ChainStore]'s fixed-length band arrays, in case [bandGainsDb] is ever shorter than
     * expected (corrupt/legacy persisted state, or a future band-count change).
     */
    private fun EqChannelState.withBand(band: Int, gainDb: Float): EqChannelState {
        if (band !in bandGainsDb.indices) return this
        val updated = bandGainsDb.toMutableList().also { it[band] = gainDb }
        return copy(bandGainsDb = updated)
    }

    /**
     * Resets band gains and Link L/R back to defaults, but keeps the stage's own enabled flag
     * untouched — flattening every band's dB back to 0 shouldn't silently disable the EQ if it was
     * on.
     */
    fun resetEq() = updateChain {
        it.copy(eq = SignalChainState().eq.copy(enabled = it.eq.enabled))
    }

    // ---- Compressor ----

    fun setCompressorEnabled(enabled: Boolean) = updateChain {
        it.copy(compressor = it.compressor.copy(enabled = enabled))
    }

    fun setCompressorThreshold(db: Float) = updateChain {
        it.copy(
            compressor =
                it.compressor.copy(thresholdDb = db.coerceIn(EqLimits.COMPRESSOR_THRESHOLD_DB))
        )
    }

    fun setCompressorRatio(ratio: Float) = updateChain {
        it.copy(compressor = it.compressor.copy(ratio = ratio.coerceIn(EqLimits.COMPRESSOR_RATIO)))
    }

    fun setCompressorAttack(ms: Float) = updateChain {
        it.copy(
            compressor = it.compressor.copy(attackMs = ms.coerceIn(EqLimits.COMPRESSOR_ATTACK_MS))
        )
    }

    fun setCompressorRelease(ms: Float) = updateChain {
        it.copy(
            compressor = it.compressor.copy(releaseMs = ms.coerceIn(EqLimits.COMPRESSOR_RELEASE_MS))
        )
    }

    fun resetCompressor() = updateChain { it.copy(compressor = SignalChainState().compressor) }

    // ---- Limiter ----

    fun setLimiterEnabled(enabled: Boolean) = updateChain {
        it.copy(limiter = it.limiter.copy(enabled = enabled))
    }

    fun setLimiterCeiling(db: Float) = updateChain {
        it.copy(limiter = it.limiter.copy(ceilingDb = db.coerceIn(EqLimits.LIMITER_CEILING_DB)))
    }

    fun setLimiterAttack(ms: Float) = updateChain {
        it.copy(limiter = it.limiter.copy(attackMs = ms.coerceIn(EqLimits.LIMITER_ATTACK_MS)))
    }

    fun setLimiterRelease(ms: Float) = updateChain {
        it.copy(limiter = it.limiter.copy(releaseMs = ms.coerceIn(EqLimits.LIMITER_RELEASE_MS)))
    }

    fun resetLimiter() = updateChain { it.copy(limiter = SignalChainState().limiter) }

    // ---- Output gain ----

    fun setOutputGainEnabled(enabled: Boolean) = updateChain {
        it.copy(outputGain = it.outputGain.copy(enabled = enabled))
    }

    fun setOutputGain(db: Float) = updateChain {
        it.copy(outputGain = it.outputGain.copy(gainDb = db.coerceIn(EqLimits.OUTPUT_GAIN_DB)))
    }

    fun resetOutputGain() = updateChain { it.copy(outputGain = SignalChainState().outputGain) }

    // ---- Spatial (Virtualizer) ----

    /**
     * Routed through the same [updateChain] funnel as every other stage — previously the only stage
     * that issued its own separate engine calls outside it, which let a stale call apply after a
     * newer state transition under interleaving.
     */
    fun setSpatialEnabled(enabled: Boolean) = updateChain {
        it.copy(spatial = it.spatial.copy(enabled = enabled))
    }

    fun setSpatialStrength(strength: Int) = updateChain {
        it.copy(spatial = it.spatial.copy(strength = strength.coerceIn(EqLimits.SPATIAL_STRENGTH)))
    }

    fun resetSpatial() = updateChain { it.copy(spatial = SignalChainState().spatial) }

    private fun updateChain(transform: (SignalChainState) -> SignalChainState) =
        synchronized(lock) {
            _uiState.update { it.copy(chain = transform(it.chain)) }
            val newChain = _uiState.value.chain
            engineApplyThrottler.request {
                synchronized(lock) {
                    engine.applyChain(newChain)
                    engine.applySpatial(newChain.spatial)
                    chainStore.saveChainState(newChain)
                }
            }
        }
}
