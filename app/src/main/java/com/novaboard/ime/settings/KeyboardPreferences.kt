package com.novaboard.ime.settings

import android.content.Context

object KeyboardPreferences {
    private const val PREFS = "novaboard_prefs"

    const val SHOW_NUMBER_ROW = "show_number_row"
    const val SHOW_ARROW_KEYS = "show_arrow_keys"
    const val LONG_PRESS_SYMBOLS = "long_press_symbols"
    const val ACCENTED_CHARACTERS = "accented_characters"
    const val KEY_POPUPS = "key_popups"
    const val LARGE_KEY_TEXT = "large_key_text"
    const val AUTOCORRECT = "autocorrect"
    const val QUICK_PERIOD = "quick_period"
    const val AUTO_CAPITALIZE = "auto_capitalize"
    const val AUTO_SPACE = "auto_space"
    const val CURSOR_CONTROL = "cursor_control"
    const val QUICK_DELETE = "quick_delete"
    const val EMOJI_PREDICTIONS = "emoji_predictions"
    const val DEDICATED_EMOJI_KEY = "dedicated_emoji_key"
    const val EMOJI_ON_ENTER = "emoji_on_enter"
    const val SOUND_ON_KEYPRESS = "sound_on_keypress"
    const val VIBRATION_ON_KEYPRESS = "vibration_on_keypress"
    const val EMOJI_FONT = "emoji_font"
    const val UNDO_AUTOCORRECT = "undo_autocorrect"
    const val QUICK_PREDICTION_INSERT = "quick_prediction_insert"
    const val LONG_PRESS_DURATION = "long_press_duration"
    const val CLEAR_TYPING_DATA = "clear_typing_data"
    const val GESTURE_MODE = "gesture_mode"
    const val INCOGNITO_MODE = "incognito_mode"
    const val IMAGE_CLIPBOARD_HISTORY = "image_clipboard_history"
    const val DEFAULT_IMAGE_CLIPBOARD_HISTORY = false

    private val defaults =
        mapOf(
            SHOW_NUMBER_ROW to true,
            SHOW_ARROW_KEYS to true,
            LONG_PRESS_SYMBOLS to true,
            ACCENTED_CHARACTERS to true,
            KEY_POPUPS to true,
            LARGE_KEY_TEXT to false,
            AUTOCORRECT to false,
            QUICK_PERIOD to true,
            AUTO_CAPITALIZE to true,
            AUTO_SPACE to false,
            CURSOR_CONTROL to true,
            QUICK_DELETE to true,
            EMOJI_PREDICTIONS to true,
            DEDICATED_EMOJI_KEY to true,
            EMOJI_ON_ENTER to false,
            SOUND_ON_KEYPRESS to false,
            VIBRATION_ON_KEYPRESS to false,
            UNDO_AUTOCORRECT to false,
            QUICK_PREDICTION_INSERT to false,
            IMAGE_CLIPBOARD_HISTORY to DEFAULT_IMAGE_CLIPBOARD_HISTORY,
        )

    fun getBoolean(context: Context, key: String): Boolean =
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(key, defaults[key] as? Boolean ?: false)

    fun setBoolean(context: Context, key: String, value: Boolean) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, value)
            .apply()
    }

    fun getLongPressDuration(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(LONG_PRESS_DURATION, 350)

    fun setLongPressDuration(context: Context, value: Int) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(LONG_PRESS_DURATION, value.coerceIn(200, 800))
            .apply()
    }

    fun getEmojiFont(context: Context): String =
        normalizeEmojiFont(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(EMOJI_FONT, "system"),
        )

    fun setEmojiFont(context: Context, value: String) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(EMOJI_FONT, normalizeEmojiFont(value))
            .apply()
    }

    fun getGestureMode(context: Context): GestureMode =
        GestureMode.fromStored(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(GESTURE_MODE, GestureMode.FLOW.storedValue),
        )

    fun setGestureMode(context: Context, mode: GestureMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(GESTURE_MODE, mode.storedValue)
            .apply()
    }

    fun isIncognitoMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(INCOGNITO_MODE, false)

    fun setIncognitoMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(INCOGNITO_MODE, enabled)
            .apply()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
