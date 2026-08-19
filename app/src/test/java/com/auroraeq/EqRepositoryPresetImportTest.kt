package com.auroraeq.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.auroraeq.app.data.repository.EqRepository
import com.auroraeq.app.data.store.ChainStore
import com.auroraeq.app.data.store.PresetStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val PREFS_NAME = "aurora_eq_prefs"

/**
 * Covers [EqRepository.importPresets]'s de-dupe rule: an imported preset never silently overwrites
 * an existing one, even when the JSON reuses an id or name a current preset already has. Uses a
 * real (Robolectric-simulated) `Context` the same way `ErrorToastRobolectricTest` does, since
 * [EqRepository] requires real [ChainStore]/[PresetStore] instances.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EqRepositoryPresetImportTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun newRepository() = EqRepository(ChainStore(context), PresetStore(context))

    /**
     * All [newRepository] calls in this test class share one Robolectric `Context`, which means
     * they'd otherwise share one underlying `SharedPreferences` file too — call this between an
     * "existing device" repository and a "different/fresh device" one in the same test so the
     * second genuinely starts from an empty preset list.
     */
    private fun clearPersistedPrefs() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    @Test
    fun `importing a single shared preset with a new id and name appends it as-is`() {
        val repository = newRepository()
        repository.saveCurrentAsPreset("Seed")
        val shared = repository.presets.value.first()
        val json = repository.exportPresetJson(shared.id)!!

        // Reset to a fresh repository (simulating a different device/install)
        // sharing that one preset into an otherwise-empty list.
        clearPersistedPrefs()
        val fresh = newRepository()
        val count = fresh.importPresets(json)

        assertEquals(1, count)
        assertEquals(shared.id, fresh.presets.value.single().id)
        assertEquals("Seed", fresh.presets.value.single().name)
    }

    @Test
    fun `importing a preset whose id already exists gets a fresh id, keeping both`() {
        val repository = newRepository()
        repository.saveCurrentAsPreset("Existing")
        val existing = repository.presets.value.single()

        // Simulate a shared file that happens to reuse the same id (e.g.
        // re-importing an already-imported backup).
        val importedJson = repository.exportPresetJson(existing.id)!!
        val count = repository.importPresets(importedJson)

        assertEquals(1, count)
        assertEquals(2, repository.presets.value.size)
        val ids = repository.presets.value.map { it.id }
        assertEquals(2, ids.toSet().size) // no id collision after import
    }

    @Test
    fun `importing a preset whose name already exists disambiguates with a numeric suffix`() {
        val repository = newRepository()
        repository.saveCurrentAsPreset("Bass Boost")
        val existing = repository.presets.value.single()
        val importedJson = repository.exportPresetJson(existing.id)!!

        repository.importPresets(importedJson)

        val names = repository.presets.value.map { it.name }
        assertTrue(names.contains("Bass Boost"))
        assertTrue(names.contains("Bass Boost (2)"))
    }

    @Test
    fun `importing the export-all array appends every preset`() {
        val repository = newRepository()
        repository.saveCurrentAsPreset("One")
        repository.saveCurrentAsPreset("Two")
        val allJson = repository.exportAllPresetsJson()

        clearPersistedPrefs()
        val fresh = newRepository()
        val count = fresh.importPresets(allJson)

        assertEquals(2, count)
        assertEquals(setOf("One", "Two"), fresh.presets.value.map { it.name }.toSet())
    }

    @Test
    fun `importing corrupt json imports nothing and reports an error`() {
        val repository = newRepository()

        val count = repository.importPresets("not valid json")

        assertEquals(0, count)
        assertTrue(repository.presets.value.isEmpty())
    }
}
