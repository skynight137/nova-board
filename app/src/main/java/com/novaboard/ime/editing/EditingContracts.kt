package com.novaboard.ime.editing

/**
 * Returns the number of UTF-16 code units needed to remove the previous word and any whitespace
 * immediately before the cursor.
 */
fun previousWordDeletionCount(textBeforeCursor: String): Int {
    if (textBeforeCursor.isEmpty()) return 0

    var wordEnd = textBeforeCursor.length
    while (wordEnd > 0 && textBeforeCursor[wordEnd - 1].isWhitespace()) {
        wordEnd--
    }

    var wordStart = wordEnd
    while (wordStart > 0 && !textBeforeCursor[wordStart - 1].isWhitespace()) {
        wordStart--
    }
    return textBeforeCursor.length - wordStart
}

/** Returns the UTF-16 code-unit count for the single Unicode code point before the cursor. */
fun previousCodePointDeletionCount(textBeforeCursor: String): Int =
    if (textBeforeCursor.isEmpty()) {
        0
    } else {
        Character.charCount(
            Character.codePointBefore(textBeforeCursor, textBeforeCursor.length),
        )
    }

/**
 * The snapshot captured immediately after an autocorrect replacement.
 *
 * Keeping the complete bounded text-before-cursor snapshot prevents undo from
 * deleting a replacement after another editor mutation changed the text around
 * it while leaving the replacement itself as a suffix.
 */
data class AutocorrectState(
    val original: String,
    val replacement: String,
    val textBeforeCursor: String,
) {
    init {
        require(original.isNotEmpty()) { "Original autocorrect text must not be empty" }
        require(replacement.isNotEmpty()) { "Replacement autocorrect text must not be empty" }
        require(textBeforeCursor.endsWith(replacement)) {
            "Snapshot must end with the autocorrect replacement"
        }
    }
}

fun canUndoAutocorrect(state: AutocorrectState?, textBeforeCursor: String): Boolean =
    state != null && state.textBeforeCursor == textBeforeCursor