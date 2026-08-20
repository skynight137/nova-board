package com.novaboard.ime.model

/** What a key does when pressed. */
enum class KeyType {
    CHAR,
    SHIFT,
    BACKSPACE,
    ENTER,
    SPACE,
    SYMBOLS,
    SYMBOLS_SECONDARY,
    LETTERS,
    SWITCH_NUMBER_ROW,
    EMOJI,
    PERIOD,
    COMMA,
}

/**
 * A single key on the keyboard.
 *
 * [label] is what's drawn on the key. [popupChars] are the characters offered on long-press (e.g.
 * long-pressing "e" offers "è é ê ë 3"), matching the "long press symbol" requirement. [flexWeight]
 * lets keys size proportionally within a row (e.g. space bar is much wider).
 */
data class Key(
    val type: KeyType,
    val label: String = "",
    val code: Int = 0,
    val popupChars: List<String> = emptyList(),
    val flexWeight: Float = 1f,
)

/** A keyboard layout is just rows of keys. */
data class KeyRow(val keys: List<Key>)

data class KeyboardPage(val rows: List<KeyRow>)

object KeyboardLayouts {

    private val numberRow = KeyRow("1234567890".map { Key(KeyType.CHAR, it.toString(), it.code) })

    private val popups =
        mapOf(
            'a' to listOf("à", "á", "â", "ä", "ã", "å"),
            'e' to listOf("è", "é", "ê", "ë"),
            'i' to listOf("ì", "í", "î", "ï"),
            'o' to listOf("ò", "ó", "ô", "ö", "õ"),
            'u' to listOf("ù", "ú", "û", "ü"),
            's' to listOf("ß", "$"),
            'c' to listOf("ç"),
            'n' to listOf("ñ"),
        )

    private fun letterKey(c: Char) =
        Key(
            KeyType.CHAR,
            c.toString(),
            c.code,
            popupChars = popups[c] ?: emptyList(),
        )

    private val qwertyRow1 = KeyRow("qwertyuiop".map(::letterKey))
    private val qwertyRow2 = KeyRow("asdfghjkl".map(::letterKey))
    private val qwertyRow3 =
        KeyRow(
            listOf(Key(KeyType.SHIFT, "\u21e7", flexWeight = 1.5f)) +
                "zxcvbnm".map(::letterKey) +
                listOf(Key(KeyType.BACKSPACE, "\u232b", flexWeight = 1.5f))
        )
    private val lettersBottomRow =
        KeyRow(
            listOf(
                Key(KeyType.SYMBOLS, "123", flexWeight = 1.3f),
                Key(KeyType.EMOJI, "\ud83d\ude00", flexWeight = 1f),
                Key(
                    KeyType.COMMA,
                    ",",
                    ','.code,
                    popupChars = listOf(";", ":", "·"),
                    flexWeight = 1f,
                ),
                Key(KeyType.SPACE, "", ' '.code, flexWeight = 4f),
                Key(
                    KeyType.PERIOD,
                    ".",
                    '.'.code,
                    popupChars = listOf("!", "?", "…"),
                    flexWeight = 1f,
                ),
                Key(KeyType.ENTER, "\u23ce", flexWeight = 1.5f),
            )
        )

    /** Letters page: number row + qwerty + bottom row. */
    val letters =
        KeyboardPage(listOf(numberRow, qwertyRow1, qwertyRow2, qwertyRow3, lettersBottomRow))

    /** Letters page variant used when the user hides the dedicated number row. */
    val lettersWithoutNumberRow =
        KeyboardPage(listOf(qwertyRow1, qwertyRow2, qwertyRow3, lettersBottomRow))

    private val primarySymbolsRow1 =
        KeyRow("@#£&_-()=%".map { Key(KeyType.CHAR, it.toString(), it.code) })
    private val primarySymbolsRow2 =
        KeyRow(
            listOf(Key(KeyType.SYMBOLS_SECONDARY, "{&=", flexWeight = 1.3f)) +
                "\"*':/!?+".map { Key(KeyType.CHAR, it.toString(), it.code) } +
                listOf(Key(KeyType.BACKSPACE, "\u232b", flexWeight = 1.5f))
        )

    private val primarySymbolsBottomRow =
        KeyRow(
            listOf(
                Key(KeyType.LETTERS, "ABC", flexWeight = 1.3f),
                Key(KeyType.COMMA, ",", ','.code, flexWeight = 1f),
                Key(KeyType.SPACE, "", ' '.code, flexWeight = 4f),
                Key(KeyType.PERIOD, ".", '.'.code, flexWeight = 1f),
                Key(KeyType.ENTER, "\u23ce", flexWeight = 1.5f),
            )
        )

    private val secondarySymbolsRow1 =
        KeyRow("$€¥¢©®™~¿".map { Key(KeyType.CHAR, it.toString(), it.code) })
    private val secondarySymbolsRow2 =
        KeyRow(
            listOf(Key(KeyType.SYMBOLS, "123", flexWeight = 1.3f)) +
                "[]{}<>^¡".map { Key(KeyType.CHAR, it.toString(), it.code) } +
                listOf(Key(KeyType.BACKSPACE, "\u232b", flexWeight = 1.5f))
        )
    private val secondarySymbolsRow3 =
        KeyRow("`;÷\\|¦¬".map { Key(KeyType.CHAR, it.toString(), it.code) })

    private val secondarySymbolsBottomRow =
        KeyRow(
            listOf(
                Key(KeyType.LETTERS, "abc", flexWeight = 1.3f),
                Key(KeyType.SPACE, "", ' '.code, flexWeight = 4f),
                Key(KeyType.CHAR, "×", '×'.code),
                Key(KeyType.CHAR, "§", '§'.code),
                Key(KeyType.CHAR, "¶", '¶'.code),
                Key(KeyType.CHAR, "°", '°'.code),
                Key(KeyType.ENTER, "\u23ce", flexWeight = 1.5f),
            )
        )

    /** Primary symbols page shown when the "123" key is tapped. */
    fun symbols(showNumberRow: Boolean) =
        KeyboardPage(
            buildList {
                if (showNumberRow) add(numberRow)
                add(primarySymbolsRow1)
                add(primarySymbolsRow2)
                add(primarySymbolsBottomRow)
            }
        )

    val symbols = symbols(showNumberRow = true)

    /** Secondary symbols page shown from the primary symbols page's "{&=" key. */
    val symbolsSecondary =
        KeyboardPage(
            listOf(
                secondarySymbolsRow1,
                secondarySymbolsRow2,
                secondarySymbolsRow3,
                secondarySymbolsBottomRow,
            )
        )
}
