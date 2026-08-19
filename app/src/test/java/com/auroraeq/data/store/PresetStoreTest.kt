package com.auroraeq.app.data.store

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [parsePresetsJson]'s malformed-data handling (same testability pattern as [ChainStoreTest]
 * — see its class doc) and confirms a preset's chain round-trips through
 * [chainStateToJson]/[parseChainStateJson] identically to the live chain state.
 */
class PresetStoreTest {

    @Test
    fun `corrupt presets JSON falls back to an empty list and reports an error`() {
        var reported: String? = null
        val presets = parsePresetsJson("not json at all", onError = { reported = it })

        assertTrue(presets.isEmpty())
        assertEquals("Saved presets were corrupted and could not be loaded.", reported)
    }

    @Test
    fun `empty array parses to an empty list without reporting an error`() {
        var reported: String? = null
        val presets = parsePresetsJson(JSONArray().toString(), onError = { reported = it })

        assertTrue(presets.isEmpty())
        assertEquals(null, reported)
    }

    @Test
    fun `a preset's chain round-trips through chainStateToJson and parseChainStateJson`() {
        val chainJson =
            JSONObject().apply {
                put("preampGainDb", 6.0)
                put("eqEnabled", false)
            }
        val raw =
            JSONArray()
                .apply {
                    put(
                        JSONObject().apply {
                            put("id", "abc-123")
                            put("name", "Bass Boost")
                            put("chain", chainJson)
                        }
                    )
                }
                .toString()

        val presets = parsePresetsJson(raw)

        assertEquals(1, presets.size)
        assertEquals("abc-123", presets[0].id)
        assertEquals("Bass Boost", presets[0].name)
        assertEquals(6f, presets[0].chain.preamp.gainDb)
        assertEquals(false, presets[0].chain.eq.enabled)
    }

    @Test
    fun `presetToJson and presetsToJson round-trip through parsePresetsJson`() {
        val presets =
            parsePresetsJson(
                JSONArray()
                    .apply {
                        put(
                            JSONObject().apply {
                                put("id", "abc-123")
                                put("name", "Bass Boost")
                                put("chain", JSONObject().apply { put("preampGainDb", 6.0) })
                            }
                        )
                    }
                    .toString()
            )

        val reExported = presetsToJson(presets)
        val reParsed = parsePresetsJson(reExported)

        assertEquals(1, reParsed.size)
        assertEquals("abc-123", reParsed[0].id)
        assertEquals("Bass Boost", reParsed[0].name)
        assertEquals(6f, reParsed[0].chain.preamp.gainDb)
    }

    @Test
    fun `parseImportedPresetsJson accepts a single shared preset object`() {
        val single =
            presetToJson(
                    com.auroraeq.app.domain.model.Preset(
                        id = "shared-1",
                        name = "Shared EQ",
                        chain = parseChainStateJson("{\"preampGainDb\":3.0}"),
                    )
                )
                .toString()

        val imported = parseImportedPresetsJson(single)

        assertEquals(1, imported.size)
        assertEquals("shared-1", imported[0].id)
        assertEquals("Shared EQ", imported[0].name)
        assertEquals(3f, imported[0].chain.preamp.gainDb)
    }

    @Test
    fun `parseImportedPresetsJson accepts the multi-preset export-all array`() {
        val all =
            presetsToJson(
                parsePresetsJson(
                    JSONArray()
                        .apply {
                            put(
                                JSONObject().apply {
                                    put("id", "a")
                                    put("name", "A")
                                    put("chain", JSONObject())
                                }
                            )
                            put(
                                JSONObject().apply {
                                    put("id", "b")
                                    put("name", "B")
                                    put("chain", JSONObject())
                                }
                            )
                        }
                        .toString()
                )
            )

        val imported = parseImportedPresetsJson(all)

        assertEquals(2, imported.size)
        assertEquals(setOf("a", "b"), imported.map { it.id }.toSet())
    }

    @Test
    fun `parseImportedPresetsJson reports an error for unrecognized content`() {
        var reported: String? = null
        val imported =
            parseImportedPresetsJson(
                "not json and not an object/array",
                onError = { reported = it },
            )

        assertTrue(imported.isEmpty())
        assertEquals("The selected file isn't a valid Aurora EQ preset.", reported)
    }

    @Test
    fun `missing id or name in an entry is treated as corrupt for the whole list`() {
        // getString("id")/getString("name") throw on a missing key, which is
        // caught by parsePresetsJson's runCatching — the whole list falls
        // back to empty rather than silently dropping just the bad entry,
        // matching ChainStore's "corrupt data never partially succeeds"
        // convention.
        var reported: String? = null
        val raw =
            JSONArray()
                .apply {
                    put(
                        JSONObject().apply {
                            put("name", "No ID")
                            put("chain", JSONObject())
                        }
                    )
                }
                .toString()

        val presets = parsePresetsJson(raw, onError = { reported = it })

        assertTrue(presets.isEmpty())
        assertEquals("Saved presets were corrupted and could not be loaded.", reported)
    }
}
