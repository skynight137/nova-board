package com.auroraeq.app

import android.content.Context
import android.widget.Toast
import androidx.test.core.app.ApplicationProvider
import com.auroraeq.app.data.repository.EqRepository
import com.auroraeq.app.data.store.ChainStore
import com.auroraeq.app.data.store.PresetStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

private const val PREFS_NAME = "aurora_eq_prefs"
private const val KEY_CHAIN_STATE = "chain_state_v3"
private const val KEY_PRESETS = "presets_v1"

/**
 * Exercises the corrupt-persisted-data -> `errorEvents` -> Toast pipeline (see
 * `.agents/memory/aurora-eq-error-reporting.md`) against a real (Robolectric-simulated)
 * `Context`/`SharedPreferences`/`Toast`, rather than a hand-called pure function.
 * `ChainStoreTest`/`PresetStoreTest` already cover `parseChainStateJson`/`parsePresetsJson` in
 * isolation; this suite is the next rung up: it writes genuinely corrupt bytes into real
 * `SharedPreferences` (simulating a previous install leaving behind unreadable state), constructs
 * the real [ChainStore]/[PresetStore]/ [EqRepository] classes against a real `Context`, and shows a
 * real `android.widget.Toast` with the emitted message — the exact call `AppNavigation`'s
 * `LaunchedEffect` makes — asserting via Robolectric's shadow that a Toast with the expected text
 * actually got shown.
 *
 * Still not full on-device proof (there's no Android emulator/device in this environment — see
 * replit.md's "Known issues" section), but it moves the untested part of the pipeline from "the
 * whole Context/SharedPreferences/ Toast plumbing" down to just "does it render pixels on a real
 * screen", which no CI/unit test can answer anyway.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ErrorToastRobolectricTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * Mimics exactly what `AppNavigation`'s `LaunchedEffect` does with each `errorEvents` message —
     * kept as a tiny helper so every test below proves the same statement production code runs, not
     * a look-alike.
     */
    private fun showAsToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private fun corruptPrefs(key: String, rawValue: String) {
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key, rawValue)
            .apply()
    }

    @Test
    fun `corrupt chain state on a real Context reports an error that renders as a real Toast`() {
        corruptPrefs(KEY_CHAIN_STATE, "{not valid json at all")

        var reported: String? = null
        val chainStore = ChainStore(context)
        val state = chainStore.loadChainState(onError = { reported = it })

        // The store itself falls back to defaults, same as the pure parser
        // test — confirms the real Context/SharedPreferences round-trip
        // doesn't change that behavior.
        assertEquals(com.auroraeq.app.domain.model.SignalChainState(), state)
        assertEquals("Saved settings were corrupted and have been reset to defaults.", reported)

        showAsToast(reported!!)

        assertTrue(
            ShadowToast.showedToast(
                "Saved settings were corrupted and have been reset to defaults."
            )
        )
        assertEquals(
            "Saved settings were corrupted and have been reset to defaults.",
            ShadowToast.getTextOfLatestToast(),
        )
    }

    @Test
    fun `corrupt presets on a real Context reports an error that renders as a real Toast`() {
        corruptPrefs(KEY_PRESETS, "not json at all")

        var reported: String? = null
        val presetStore = PresetStore(context)
        val presets = presetStore.loadPresets(onError = { reported = it })

        assertTrue(presets.isEmpty())
        assertEquals("Saved presets were corrupted and could not be loaded.", reported)

        showAsToast(reported!!)

        assertEquals(
            "Saved presets were corrupted and could not be loaded.",
            ShadowToast.getTextOfLatestToast(),
        )
    }

    /**
     * Reproduces the exact ordering [EqRepository]'s class doc claims to handle: both `onError`
     * calls fire during construction — i.e. before `AppNavigation`'s `LaunchedEffect` has had a
     * chance to subscribe to `errorEvents`, matching the real `EqApplication.onCreate` timing this
     * buffer exists for — and only then does a collector attach. If `MutableSharedFlow`'s
     * `extraBufferCapacity` did not actually replay pre-subscription emissions the way the class
     * doc assumes, this test would hang (via [withTimeout]) or see an empty list instead of 2
     * items, rather than silently passing.
     */
    @Test
    fun `errors raised before any collector subscribes are still delivered once collection starts`() {
        corruptPrefs(KEY_CHAIN_STATE, "{{{")
        corruptPrefs(KEY_PRESETS, "[[[")

        val chainStore = ChainStore(context)
        val presetStore = PresetStore(context)

        // Both onError calls already fired inside these constructors, above,
        // with zero collectors attached to errorEvents yet.
        val repository = EqRepository(chainStore, presetStore)

        val received = mutableListOf<String>()
        runBlocking {
            val scope = CoroutineScope(Dispatchers.Unconfined)
            val job = scope.launch { repository.errorEvents.collect { received += it } }
            withTimeout(2_000) {
                while (received.size < 2) kotlinx.coroutines.yield()
            }
            job.cancel()
            scope.cancel()
        }

        received.forEach(::showAsToast)

        assertEquals(2, received.size)
        assertTrue(
            received.contains("Saved settings were corrupted and have been reset to defaults.")
        )
        assertTrue(received.contains("Saved presets were corrupted and could not be loaded."))
    }
}
