package com.novaboard.ime.clipboard

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardSearchTest {
    private val items =
        listOf(
            ClipboardItem(1L, ClipType.TEXT, text = "Meeting notes"),
            ClipboardItem(2L, ClipType.TEXT, text = "Shopping list"),
            ClipboardItem(3L, ClipType.IMAGE, imageUri = "content://image"),
        )

    @Test
    fun emptyQueryReturnsAllItems() {
        assertEquals(items, filterClipboardItems(items, "  "))
    }

    @Test
    fun queryMatchesTextCaseInsensitively() {
        assertEquals(listOf(items[0]), filterClipboardItems(items, "MEETING"))
    }

    @Test
    fun imageItemsAreNotTextSearchMatches() {
        assertEquals(emptyList<ClipboardItem>(), filterClipboardItems(items, "image"))
    }
}