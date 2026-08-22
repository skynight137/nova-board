package com.novaboard.ime.theme

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * Persists the user's chosen theme and applies it via AppCompatDelegate's night-mode override.
 * Because `values-night/colors.xml` supplies the dark palette, forcing night mode on/off here is
 * enough to make every color resource (used by both the settings screen and the keyboard's
 * custom-drawn view) resolve correctly, independent of the actual system setting.
 */
object ThemeManager {
    private const val PREFS = "novaboard_prefs"
    const val KEY_THEME = "theme_mode"

    fun get(context: Context): ThemeMode {
        val raw =
            context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_THEME, ThemeMode.SYSTEM.name)
        return runCatching { ThemeMode.valueOf(raw ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    fun set(context: Context, mode: ThemeMode) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, mode.name)
            .apply()
        apply(mode)
    }

    /** Call at process/service startup so the current preference takes effect immediately. */
    fun applyStored(context: Context) = apply(get(context))

    /**
     * InputMethodService is not an AppCompat activity, so AppCompatDelegate does not update its
     * resource configuration. Create a configuration-bound context for keyboard views instead.
     */
    fun keyboardContext(context: Context): Context {
        val mode = get(context)
        if (mode == ThemeMode.SYSTEM) return context
        val configuration = android.content.res.Configuration(context.resources.configuration)
        val nightMask = android.content.res.Configuration.UI_MODE_NIGHT_MASK
        configuration.uiMode =
            (configuration.uiMode and nightMask.inv()) or
                when (mode) {
                    ThemeMode.LIGHT -> android.content.res.Configuration.UI_MODE_NIGHT_NO
                    ThemeMode.DARK -> android.content.res.Configuration.UI_MODE_NIGHT_YES
                    ThemeMode.SYSTEM -> 0
                }
        return context.createConfigurationContext(configuration)
    }

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
