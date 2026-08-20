package com.novaboard.ime.translation

data class TranslationRequest(
    val session: Long,
    val requestId: Long,
    val selectionStart: Int,
    val selectionEnd: Int,
) {
    val isValid: Boolean =
        selectionStart >= 0 && selectionEnd > selectionStart
}

fun shouldAcceptTranslationResult(
    request: TranslationRequest?,
    activeSession: Long,
    activeRequestId: Long,
    currentSelectionStart: Int,
    currentSelectionEnd: Int,
): Boolean =
    request != null &&
        request.isValid &&
        request.session == activeSession &&
        request.requestId == activeRequestId &&
        request.selectionStart == currentSelectionStart &&
        request.selectionEnd == currentSelectionEnd