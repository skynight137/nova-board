package com.novaboard.ime.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardModelTest {

    @Test
    fun lettersPageKeepsTheMigratedFiveRowShape() {
        assertEquals(5, KeyboardLayouts.letters.rows.size)
        assertEquals(10, KeyboardLayouts.letters.rows.first().keys.size)
        assertEquals(KeyType.SHIFT, KeyboardLayouts.letters.rows[3].keys.first().type)
        assertEquals(KeyType.BACKSPACE, KeyboardLayouts.letters.rows[3].keys.last().type)
    }

    @Test
    fun letterKeysExposeAccentPopupsAndSymbolsReturnToLetters() {
        val eKey = KeyboardLayouts.letters.rows[2].keys[1]
        val cKey = KeyboardLayouts.letters.rows[3].keys[3]

        assertEquals("s", eKey.label)
        assertTrue(cKey.popupChars.contains("ç"))
        assertEquals(KeyType.LETTERS, KeyboardLayouts.symbols.rows[3].keys.first().type)
    }

    @Test
    fun spaceBarHasTheLargestBottomRowWeight() {
        val bottomRow = KeyboardLayouts.letters.rows.last().keys
        val space = bottomRow.first { it.type == KeyType.SPACE }

        assertEquals(4f, space.flexWeight)
        assertTrue(space.flexWeight > bottomRow.maxOf { it.flexWeight } - 0.01f)
    }

    @Test
    fun symbolsPagesMatchSwiftKeySymbolNavigation() {
        val symbolLabels = KeyboardLayouts.symbols.rows.flatMap { row -> row.keys.map { it.label } }
        val secondaryLabels =
            KeyboardLayouts.symbolsSecondary.rows.flatMap { row -> row.keys.map { it.label } }

        assertEquals(10, KeyboardLayouts.symbols.rows[1].keys.size)
        assertTrue(symbolLabels.containsAll(listOf("@", "#", "£", "&", "_", "=", "%", "{&=")))
        assertEquals(KeyType.LETTERS, KeyboardLayouts.symbols.rows.last().keys.first().type)
        assertTrue(secondaryLabels.containsAll(listOf("$", "€", "¥", "©", "™", "~", "¿")))
        assertEquals(4, KeyboardLayouts.symbolsSecondary.rows.size)
        assertEquals(KeyType.SYMBOLS, KeyboardLayouts.symbolsSecondary.rows[1].keys.first().type)
        assertEquals(
            KeyType.LETTERS,
            KeyboardLayouts.symbolsSecondary.rows.last().keys.first().type,
        )
    }

    @Test
    fun punctuationKeysOfferMissingSymbolsOnLongPress() {
        val bottomRow = KeyboardLayouts.letters.rows.last().keys

        assertTrue(bottomRow.first { it.type == KeyType.COMMA }.popupChars.contains(";"))
        assertTrue(bottomRow.first { it.type == KeyType.PERIOD }.popupChars.contains("?"))
    }
}
