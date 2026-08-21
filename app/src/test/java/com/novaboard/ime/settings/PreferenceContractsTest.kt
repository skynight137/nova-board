package com.novaboard.ime.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PreferenceContractsTest {
    @Test
    fun retainedBooleanPreferencesHaveExplicitDefaults() {
        assertEquals(true, defaultBooleanPreference(KeyboardPreferences.SHOW_NUMBER_ROW))
        assertEquals(false, defaultBooleanPreference(KeyboardPreferences.LONG_PRESS_SYMBOLS))
        assertEquals(false, defaultBooleanPreference(KeyboardPreferences.KEY_POPUPS))
        assertEquals(false, defaultBooleanPreference(KeyboardPreferences.EMOJI_ON_ENTER))
        assertEquals(true, defaultBooleanPreference(KeyboardPreferences.QUICK_DELETE))
        assertEquals(false, defaultBooleanPreference(KeyboardPreferences.IMAGE_CLIPBOARD_HISTORY))
        assertEquals(null, defaultBooleanPreference(KeyboardPreferences.CLEAR_TYPING_DATA))
    }

    @Test
    fun emojiFontNormalizationFailsClosedToSystem() {
        assertEquals("system", normalizeEmojiFont(null))
        assertEquals("system", normalizeEmojiFont("unsupported"))
        assertEquals("system", normalizeEmojiFont("system"))
        assertEquals("google", normalizeEmojiFont("google"))
    }
}