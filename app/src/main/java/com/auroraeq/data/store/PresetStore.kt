package com.auroraeq.app.data.store

import android.content.Context
import android.content.SharedPreferences
import com.auroraeq.app.domain.model.Preset
import com.auroraeq.app.domain.model.SignalChainState
import com.auroraeq.app.util.AppLog
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "PresetStore"
private const val PREFS_NAME = "aurora_eq_prefs"
private const val KEY_PRESETS = "presets_v1"

/**
 * Persists named [Preset] snapshots — each a full [SignalChainState], not just the 31-band EQ curve
 * — as a JSON array in the same SharedPreferences file [ChainStore] uses (same app, same "settings"
 * concept; no need for a second prefs file). Deliberately separate from [ChainStore], which only
 * ever holds the single *live* chain state: saving/loading a preset never touches [ChainStore]
 * directly, it goes through [com.auroraeq.app.data.repository.EqRepository] so an applied preset is
 * pushed to the engine and persisted as the new live state through the same funnel every other
 * mutation uses.
 *
 * Reuses [chainStateToJson]/[parseChainStateJson] for the per-preset chain payload so a preset's
 * chain is always serialized/parsed identically to the live chain state.
 */
class PresetStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * [onError] mirrors [ChainStore.loadChainState]'s callback — fired once if the persisted
     * presets JSON is corrupt, in which case the whole list falls back to empty rather than
     * partially/incorrectly parsed.
     */
    fun loadPresets(onError: (String) -> Unit = {}): List<Preset> {
        val raw = prefs.getString(KEY_PRESETS, null) ?: return emptyList()
        return parsePresetsJson(raw, onError)
    }

    fun savePresets(presets: List<Preset>) {
        prefs.edit().putString(KEY_PRESETS, presetsToJson(presets)).apply()
    }
}

/**
 * The single-preset JSON shape — identical to one entry in [presetsToJson]'s array. Used both for
 * the "share one preset" action (`PresetsScreen`) and as one of the two shapes
 * [parseImportedPresetsJson] accepts, so a shared preset and a persisted one are always the same
 * format — never a second, import-only shape.
 */
internal fun presetToJson(preset: Preset): JSONObject =
    JSONObject().apply {
        put("id", preset.id)
        put("name", preset.name)
        put("chain", chainStateToJson(preset.chain))
    }

/**
 * The "export all" JSON shape — a JSON array of [presetToJson] objects, also what's persisted to
 * SharedPreferences by [PresetStore.savePresets].
 */
internal fun presetsToJson(presets: List<Preset>): String {
    val array = JSONArray()
    presets.forEach { array.put(presetToJson(it)) }
    return array.toString()
}

/**
 * Pulled out of [PresetStore] as a top-level `internal` function for the same JVM-testability
 * reason as [parseChainStateJson] — no real `android.content.Context`/`SharedPreferences` needed to
 * exercise it.
 */
internal fun parsePresetsJson(raw: String, onError: (String) -> Unit = {}): List<Preset> =
    runCatching {
            val array = JSONArray(raw)
            List(array.length()) { i -> parsePresetJsonObject(array.getJSONObject(i)) }
        }
        .onFailure {
            AppLog.w(TAG, "Corrupt persisted presets JSON; falling back to an empty list", it)
            onError("Saved presets were corrupted and could not be loaded.")
        }
        .getOrDefault(emptyList())

private fun parsePresetJsonObject(obj: JSONObject): Preset =
    Preset(
        id = obj.getString("id"),
        name = obj.getString("name"),
        chain = parseChainStateJson(obj.getJSONObject("chain").toString()),
    )

/**
 * Parses a file picked via [PresetsScreen]'s "Import" action — accepts either shape a user could
 * plausibly have on disk: the multi-preset array from "Export all", or a single-preset object from
 * someone else's "Share" action. Never partially imports a malformed array (same "corrupt data
 * never partially succeeds" convention as [parsePresetsJson]/[parseChainStateJson]); an
 * unrecognized shape (neither `[` nor `{`) is reported the same way as corrupt JSON rather than
 * silently importing nothing.
 */
internal fun parseImportedPresetsJson(raw: String, onError: (String) -> Unit = {}): List<Preset> {
    val trimmed = raw.trim()
    return when {
        trimmed.startsWith("[") -> parsePresetsJson(trimmed, onError)
        trimmed.startsWith("{") ->
            runCatching { listOf(parsePresetJsonObject(JSONObject(trimmed))) }
                .onFailure {
                    AppLog.w(TAG, "Corrupt imported preset JSON", it)
                    onError("The selected file isn't a valid Aurora EQ preset.")
                }
                .getOrDefault(emptyList())
        else -> {
            onError("The selected file isn't a valid Aurora EQ preset.")
            emptyList()
        }
    }
}
