package com.novaboard.ime.settings

private val booleanPreferenceDefaults =
    mapOf(
        "show_number_row" to true,
        "show_arrow_keys" to true,
        "long_press_symbols" to false,
        "accented_characters" to false,
        "key_popups" to false,
        "large_key_text" to false,
        "autocorrect" to false,
        "quick_period" to true,
        "auto_capitalize" to true,
        "auto_space" to false,
        "cursor_control" to true,
        "quick_delete" to true,
        "emoji_predictions" to true,
        "dedicated_emoji_key" to true,
        "emoji_on_enter" to false,
        "sound_on_keypress" to false,
        "vibration_on_keypress" to false,
        "undo_autocorrect" to false,
        "quick_prediction_insert" to false,
        "image_clipboard_history" to false,
    )

/** Pure default lookup used by Android storage and JVM preference-contract tests. */
fun defaultBooleanPreference(key: String): Boolean? = booleanPreferenceDefaults[key]

/** Stored emoji font values are intentionally limited to the two renderers the app supports. */
fun normalizeEmojiFont(value: String?): String =
    if (value == "google") "google" else "system"