package com.novaboard.ime.emoji

import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiDataTest {
    @Test
    fun searchMatchesEmojiKeywordsInsteadOfOnlyGlyphs() {
        val results = EmojiData.search("heart")

        assertTrue(results.contains("❤️"))
        assertTrue(results.contains("😍"))
    }

    @Test
    fun blankSearchRestoresTheFullEmojiSet() {
        assertTrue(EmojiData.search(" ").size > 10)
    }

    @Test
    fun searchIsCaseInsensitiveAndUnknownTermsReturnNoMatches() {
        assertTrue(EmojiData.search("HEART").isNotEmpty())
        assertTrue(EmojiData.search("not-a-real-emoji-term").isEmpty())
    }
}
