package com.novaboard.ime.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardPersistenceTest {
    @Test
    fun malformedEntriesDoNotBlockValidEntriesOrNextId() {
        val raw =
            """[
                {"id": 4, "type": "TEXT", "text": "valid", "imageUri": null, "pinned": false},
                {"id": "bad", "type": "TEXT", "text": "skip"},
                {"id": 8, "type": "UNKNOWN", "text": "skip"},
                {"id": 9, "type": "TEXT", "text": 12},
                {"id": 7, "type": "TEXT", "text": "also valid"}
            ]"""

        val result = ClipboardPersistence.decode(raw)

        assertEquals(listOf(4L, 7L), result.items.map { it.id })
        assertEquals(8L, result.nextId)
    }

    @Test
    fun validNullFieldsRoundTripWithoutInventingContent() {
        val items =
            listOf(
                ClipboardItem(3L, ClipType.TEXT, text = "hello"),
                ClipboardItem(
                    5L,
                    ClipType.IMAGE,
                    imageUri = "content://com.novaboard.ime.clipboard/a.png",
                    pinned = true,
                ),
            )

        val decoded = ClipboardPersistence.decode(ClipboardPersistence.encode(items))

        assertEquals(items, decoded.items)
        assertEquals(6L, decoded.nextId)
    }

    @Test
    fun invalidTopLevelJsonProducesEmptyHistory() {
        val result = ClipboardPersistence.decode("not json")

        assertTrue(result.items.isEmpty())
        assertEquals(1L, result.nextId)
    }
}