package com.novaboard.ime.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Persists the user's chosen theme and applies it via AppCompatDelegate's night-mode override.
 * Because `values-night/colors.xml` supplies the dark palette, forcing night mode on/off here
 * is enough to make every color resource (used by both the settings screen and the keyboard's
 * custom-drawn view) resolve correctly, independent of the actual system setting.
 */
object ThemeManager {
    private const val PREFS = "novaboard_prefs"
    private const val KEY_THEME = "theme_mode"

    fun get(context: Context): ThemeMode {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, ThemeMode.SYSTEM.name)
        return runCatching { ThemeMode.valueOf(raw ?: ThemeMode.SYSTEM.name) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun set(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, mode.name).apply()
        apply(mode)
    }

    /** Call at process/service startup so the current preference takes effect immediately. */
    fun applyStored(context: Context) = apply(get(context))

    private fun apply(mode: ThemeMode) {
        AppCompatDelegate.setDefaultNightMode(
            when (mode) {
                ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            }
        )
    }
}
