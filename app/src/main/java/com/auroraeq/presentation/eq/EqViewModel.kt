package com.auroraeq.app.presentation.eq

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.auroraeq.app.EqApplication
import com.auroraeq.app.domain.model.EqUiState
import com.auroraeq.app.domain.model.FilterSlope
import com.auroraeq.app.domain.model.Preset
import com.auroraeq.app.domain.model.VolumeInfo
import com.auroraeq.app.util.FrameThrottler
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Undocumented but long-standing broadcast Android sends whenever any stream's volume changes —
 * including from the hardware volume buttons — so the Volume screen's slider stays in sync no
 * matter what changed it.
 */
private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"

/**
 * Converts a UI percent (0..100) into the nearest `AudioManager` `STREAM_MUSIC` index (0..[max]) —
 * pulled out as a pure function so the mapping that `VolumeScreen`'s `100f / volume.max` nudge step
 * relies on can be unit-tested directly, without mocking `AudioManager`.
 */
internal fun volumeIndexForPercent(percent: Float, max: Int): Int =
    (percent / 100f * max).roundToInt().coerceIn(0, max)

/**
 * Shared ViewModel backing every Audio Management child screen and Settings — they all read/write
 * the same underlying [com.auroraeq.app.data.repository.EqRepository].
 */
class EqViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as EqApplication).eqRepository
    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val uiState: StateFlow<EqUiState> = repository.uiState

    // Named full-chain presets (see com.auroraeq.app.domain.model.Preset)
    val presets: StateFlow<List<Preset>> = repository.presets

    fun saveCurrentAsPreset(name: String) = repository.saveCurrentAsPreset(name)

    fun applyPreset(id: String) = repository.applyPreset(id)

    fun resetAllProcessing() = repository.resetAllProcessing()

    fun renamePreset(id: String, newName: String) = repository.renamePreset(id, newName)

    fun deletePreset(id: String) = repository.deletePreset(id)

    // Preset import/export —
    // the JSON itself is produced/consumed here, but writing/reading a file
    // or Uri stays in PresetsScreen since only it has an Activity/Context
    // for FileProvider/SAF calls.
    fun exportPresetJson(id: String): String? = repository.exportPresetJson(id)

    fun exportAllPresetsJson(): String = repository.exportAllPresetsJson()

    fun importPresets(json: String): Int = repository.importPresets(json)

    /**
     * One-shot user-facing error messages (engine/persistence failures) — see
     * [com.auroraeq.app.data.repository.EqRepository.errorEvents]. `AppNavigation` collects this
     * once at the top level and shows each message as a Toast.
     */
    val errorEvents: SharedFlow<String> = repository.errorEvents

    // ---- System volume (earliest step in Audio Management, ahead of Preamp;
    // controls android.media.AudioManager's STREAM_MUSIC directly, not a
    // DynamicsProcessing stage, so it's not part of SignalChainState) ----

    private val _volumeState = MutableStateFlow(readVolumeInfo())
    val volumeState: StateFlow<VolumeInfo> = _volumeState.asStateFlow()
    private var lastUnmutedVolume: Int? = null

    /**
     * Same fix as `EqRepository.engineApplyThrottler` — the Volume screen's slider fires on every
     * pointer-move event too, and without this it calls into `AudioManager.setStreamVolume` (a live
     * Binder call to audioserver) at the same uncapped rate.
     */
    private val volumeThrottler = FrameThrottler()

    private val volumeReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                _volumeState.value = readVolumeInfo()
            }
        }

    init {
        ContextCompat.registerReceiver(
            application,
            volumeReceiver,
            IntentFilter(VOLUME_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun readVolumeInfo(): VolumeInfo =
        VolumeInfo(
            current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
            max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
        )

    fun setVolumePercent(percent: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val index = volumeIndexForPercent(percent, max)
        // Reflect the target value immediately so the slider itself never lags —
        // only the actual system call is throttled. The volumeReceiver broadcast
        // will also correct this from the real system state once the throttled
        // call actually lands, so this optimistic update can never get "stuck" wrong.
        _volumeState.value = _volumeState.value.copy(current = index)
        volumeThrottler.request {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
        }
    }

    fun setVolumeMuted(muted: Boolean) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (muted) {
            lastUnmutedVolume =
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).takeIf { it > 0 }
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        } else {
            val restored = lastUnmutedVolume ?: (max / 2).coerceAtLeast(1)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restored, 0)
        }
        _volumeState.value = readVolumeInfo()
    }

    override fun onCleared() {
        getApplication<Application>().unregisterReceiver(volumeReceiver)
        super.onCleared()
    }

    // Preamp
    fun setPreampEnabled(enabled: Boolean) = repository.setPreampEnabled(enabled)

    fun setPreampGain(gainDb: Float) = repository.setPreampGain(gainDb)

    fun resetPreamp() = repository.resetPreamp()

    // HPF / LPF
    fun setHpfEnabled(enabled: Boolean) = repository.setHpfEnabled(enabled)

    fun setHpfCutoff(cutoffHz: Float) = repository.setHpfCutoff(cutoffHz)

    fun setHpfSlope(slope: FilterSlope) = repository.setHpfSlope(slope)

    fun resetHpf() = repository.resetHpf()

    fun setLpfEnabled(enabled: Boolean) = repository.setLpfEnabled(enabled)

    fun setLpfCutoff(cutoffHz: Float) = repository.setLpfCutoff(cutoffHz)

    fun setLpfSlope(slope: FilterSlope) = repository.setLpfSlope(slope)

    fun resetLpf() = repository.resetLpf()

    // Sub Shelf / Air Shelf
    fun setSubShelfEnabled(enabled: Boolean) = repository.setSubShelfEnabled(enabled)

    fun setSubShelfLinked(linked: Boolean) = repository.setSubShelfLinked(linked)

    fun setSubShelfFreq(freqHz: Float) = repository.setSubShelfFreq(freqHz)

    fun setSubShelfGain(channelIsLeft: Boolean, gainDb: Float) =
        repository.setSubShelfGain(channelIsLeft, gainDb)

    fun resetSubShelf() = repository.resetSubShelf()

    fun setAirShelfEnabled(enabled: Boolean) = repository.setAirShelfEnabled(enabled)

    fun setAirShelfLinked(linked: Boolean) = repository.setAirShelfLinked(linked)

    fun setAirShelfFreq(freqHz: Float) = repository.setAirShelfFreq(freqHz)

    fun setAirShelfGain(channelIsLeft: Boolean, gainDb: Float) =
        repository.setAirShelfGain(channelIsLeft, gainDb)

    fun resetAirShelf() = repository.resetAirShelf()

    // 31-band EQ
    fun setEqEnabled(enabled: Boolean) = repository.setEqEnabled(enabled)

    fun setEqLinked(linked: Boolean) = repository.setEqLinked(linked)

    fun setBandGain(channelIsLeft: Boolean, band: Int, gainDb: Float) =
        repository.setBandGain(channelIsLeft, band, gainDb)

    fun resetEq() = repository.resetEq()

    // Compressor
    fun setCompressorEnabled(enabled: Boolean) = repository.setCompressorEnabled(enabled)

    fun setCompressorThreshold(db: Float) = repository.setCompressorThreshold(db)

    fun setCompressorRatio(ratio: Float) = repository.setCompressorRatio(ratio)

    fun setCompressorAttack(ms: Float) = repository.setCompressorAttack(ms)

    fun setCompressorRelease(ms: Float) = repository.setCompressorRelease(ms)

    fun resetCompressor() = repository.resetCompressor()

    // Limiter
    fun setLimiterEnabled(enabled: Boolean) = repository.setLimiterEnabled(enabled)

    fun setLimiterCeiling(db: Float) = repository.setLimiterCeiling(db)

    fun setLimiterAttack(ms: Float) = repository.setLimiterAttack(ms)

    fun setLimiterRelease(ms: Float) = repository.setLimiterRelease(ms)

    fun resetLimiter() = repository.resetLimiter()

    // Output gain
    fun setOutputGainEnabled(enabled: Boolean) = repository.setOutputGainEnabled(enabled)

    fun setOutputGain(db: Float) = repository.setOutputGain(db)

    fun resetOutputGain() = repository.resetOutputGain()

    // Spatial (Virtualizer)
    fun setSpatialEnabled(enabled: Boolean) = repository.setSpatialEnabled(enabled)

    fun setSpatialStrength(strength: Int) = repository.setSpatialStrength(strength)

    fun resetSpatial() = repository.resetSpatial()

    companion object {
        fun factory(application: Application) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    EqViewModel(application) as T
            }
    }
}
