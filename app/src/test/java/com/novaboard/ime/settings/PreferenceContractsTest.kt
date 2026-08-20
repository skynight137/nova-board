package com.novaboard.ime.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PreferenceContractsTest {
    @Test
    fun emojiFontNormalizationFailsClosedToSystem() {
        assertEquals("system", normalizeEmojiFont(null))
        assertEquals("system", normalizeEmojiFont("unsupported"))
        assertEquals("system", normalizeEmojiFont("system"))
        assertEquals("google", normalizeEmojiFont("google"))
    }
}