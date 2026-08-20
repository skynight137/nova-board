package com.novaboard.ime.editing

/**
 * Pure acceptance rules for callbacks that can outlive an editor or recognizer.
 * The service remains the owner of the actual session and recognizer counters.
 */
fun acceptsInputSessionResult(
    resultSession: Long,
    activeSession: Long,
    resultRecognizer: Long,
    activeRecognizer: Long,
): Boolean =
    resultSession == activeSession &&
        resultRecognizer == activeRecognizer

fun shouldResetTrackedTyping(
    oldSelectionStart: Int,
    oldSelectionEnd: Int,
    newSelectionStart: Int,
    newSelectionEnd: Int,
    trackedWord: String,
    textBeforeCursor: String,
): Boolean {
    if (oldSelectionStart == newSelectionStart && oldSelectionEnd == newSelectionEnd) {
        return false
    }
    return trackedWord.isEmpty() || !textBeforeCursor.endsWith(trackedWord)
}