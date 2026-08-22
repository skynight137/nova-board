package com.novaboard.ime.gif

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GifEditorSupportTest {
    @Test
    fun `editor without image MIME types does not receive rich content`() {
        assertFalse(supportsGifContent(null))
        assertFalse(supportsGifContent(emptyArray()))
        assertFalse(supportsGifContent(arrayOf("text/plain")))
    }

    @Test
    fun `GIF and image wildcard MIME types support rich content`() {
        assertTrue(supportsGifContent(arrayOf("image/gif")))
        assertTrue(supportsGifContent(arrayOf("image/*")))
    }
}