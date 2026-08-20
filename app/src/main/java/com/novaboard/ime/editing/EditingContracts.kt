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

/**
 * Undo is safe only while the replacement is still the exact suffix immediately before the cursor.
 */
fun canUndoAutocorrect(replacement: String?, textBeforeCursor: String): Boolean =
    !replacement.isNullOrEmpty() && textBeforeCursor.endsWith(replacement)