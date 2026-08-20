package com.novaboard.ime.settings

/** Stored emoji font values are intentionally limited to the two renderers the app supports. */
fun normalizeEmojiFont(value: String?): String =
    if (value == "google") "google" else "system"