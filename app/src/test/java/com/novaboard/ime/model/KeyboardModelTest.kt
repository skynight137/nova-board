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
        assertEquals(KeyType.SYMBOLS, KeyboardLayouts.symbolsSecondary.rows[2].keys.first().type)
        assertEquals(
            KeyType.LETTERS,
            KeyboardLayouts.symbolsSecondary.rows.last().keys.first().type,
        )
    }

    @Test
    fun numberRowPreferenceChangesLettersAndPrimarySymbolsOnly() {
        assertEquals(5, KeyboardLayouts.letters.rows.size)
        assertEquals(4, KeyboardLayouts.lettersWithoutNumberRow.rows.size)
        assertEquals(4, KeyboardLayouts.symbols(showNumberRow = true).rows.size)
        assertEquals(3, KeyboardLayouts.symbols(showNumberRow = false).rows.size)
        assertEquals(
            KeyType.CHAR,
            KeyboardLayouts.symbols(showNumberRow = true).rows.first().keys.first().type,
        )
        assertEquals(
            "@",
            KeyboardLayouts.symbols(showNumberRow = false).rows.first().keys.first().label,
        )
    }

    @Test
    fun symbolSwitcherRowsStayAlignedAcrossPrimaryAndSecondaryPages() {
        val primaryWithNumberRow = KeyboardLayouts.symbols(showNumberRow = true)
        val secondaryWithNumberRow = KeyboardLayouts.symbolsSecondary(showNumberRow = true)
        val primaryWithoutNumberRow = KeyboardLayouts.symbols(showNumberRow = false)
        val secondaryWithoutNumberRow = KeyboardLayouts.symbolsSecondary(showNumberRow = false)

        assertEquals(2, primaryWithNumberRow.rows.indexOfFirst { row ->
            row.keys.first().type == KeyType.SYMBOLS_SECONDARY
        })
        assertEquals(2, secondaryWithNumberRow.rows.indexOfFirst { row ->
            row.keys.first().type == KeyType.SYMBOLS
        })
        assertEquals(1, primaryWithoutNumberRow.rows.indexOfFirst { row ->
            row.keys.first().type == KeyType.SYMBOLS_SECONDARY
        })
        assertEquals(1, secondaryWithoutNumberRow.rows.indexOfFirst { row ->
            row.keys.first().type == KeyType.SYMBOLS
        })
    }

    @Test
    fun secondarySymbolsKeepTheirFourRowShapeForEitherNumberRowPreference() {
        assertEquals(4, KeyboardLayouts.symbolsSecondary.rows.size)
        assertEquals(KeyType.SYMBOLS, KeyboardLayouts.symbolsSecondary.rows[2].keys.first().type)
        assertEquals(
            KeyType.SYMBOLS,
            KeyboardLayouts.symbolsSecondary(showNumberRow = false).rows[1].keys.first().type,
        )
        assertEquals(KeyType.LETTERS, KeyboardLayouts.symbolsSecondary.rows.last().keys.first().type)
    }

    @Test
    fun punctuationKeysOfferMissingSymbolsOnLongPress() {
        val bottomRow = KeyboardLayouts.letters.rows.last().keys

        assertTrue(bottomRow.first { it.type == KeyType.COMMA }.popupChars.contains(";"))
        assertTrue(bottomRow.first { it.type == KeyType.PERIOD }.popupChars.contains("?"))
    }
}
