package com.novaboard.ime.translation

enum class TranslationMode {
    NORMAL,
    LIVE_WRITE,
}

enum class TranslationStatus {
    IDLE,
    LOADING,
    READY,
    UNAVAILABLE,
    ERROR,
}

data class TranslationComposerState(
    val sourceText: String = "",
    val translatedText: String? = null,
    val sourceLanguage: String = "auto",
    val targetLanguage: String = "en",
    val mode: TranslationMode = TranslationMode.NORMAL,
    val status: TranslationStatus = TranslationStatus.IDLE,
    val session: Long = 0L,
    val requestGeneration: Long = 0L,
    val selectedStart: Int = -1,
    val selectedEnd: Int = -1,
    /** Cursor in the target editor captured when the translation panel opened, or -1 if unknown. */
    val insertionCursor: Int = -1,
) {
    val canRequest: Boolean
        get() = sourceText.isNotBlank() && status != TranslationStatus.LOADING

    val hasSelection: Boolean
        get() = selectedStart >= 0 && selectedEnd > selectedStart
}

sealed interface TranslationComposerAction {
    data class EditSource(val text: String) : TranslationComposerAction
    data object SwapLanguages : TranslationComposerAction
    data object ClearSource : TranslationComposerAction
    data object RequestTranslation : TranslationComposerAction
    data class TranslationSucceeded(
        val generation: Long,
        val sourceLanguage: String,
        val targetLanguage: String,
        val text: String,
    ) : TranslationComposerAction
    data class TranslationFailed(
        val generation: Long,
        val sourceLanguage: String,
        val targetLanguage: String,
        val status: TranslationStatus = TranslationStatus.ERROR,
    ) : TranslationComposerAction
    data class PasteResult(val cursor: Int) : TranslationComposerAction
    data class ReplyWithResult(val currentSelectionStart: Int, val currentSelectionEnd: Int) :
        TranslationComposerAction
    data object Cancel : TranslationComposerAction
    data object Dismiss : TranslationComposerAction
}

sealed interface TranslationCommit {
    val text: String

    data class Paste(override val text: String, val cursor: Int) : TranslationCommit

    data class Reply(
        override val text: String,
        val selectionStart: Int,
        val selectionEnd: Int,
    ) : TranslationCommit
}

fun reduceTranslationComposer(
    state: TranslationComposerState,
    action: TranslationComposerAction,
): TranslationComposerState =
    when (action) {
        is TranslationComposerAction.EditSource ->
            state.copy(
                sourceText = action.text,
                translatedText = null,
                status = TranslationStatus.IDLE,
                requestGeneration = state.requestGeneration + 1,
            )
        TranslationComposerAction.SwapLanguages ->
            state.copy(
                sourceLanguage = state.targetLanguage,
                targetLanguage = state.sourceLanguage,
                translatedText = null,
                status = TranslationStatus.IDLE,
                requestGeneration = state.requestGeneration + 1,
            )
        TranslationComposerAction.ClearSource ->
            state.copy(
                sourceText = "",
                translatedText = null,
                status = TranslationStatus.IDLE,
                requestGeneration = state.requestGeneration + 1,
            )
        TranslationComposerAction.RequestTranslation ->
            if (state.canRequest) {
                state.copy(
                    status = TranslationStatus.LOADING,
                    requestGeneration = state.requestGeneration + 1,
                )
            } else {
                state
            }
        is TranslationComposerAction.TranslationSucceeded ->
            if (matchesCurrentRequest(state, action.generation, action.sourceLanguage, action.targetLanguage)) {
                state.copy(translatedText = action.text, status = TranslationStatus.READY)
            } else {
                state
            }
        is TranslationComposerAction.TranslationFailed ->
            if (matchesCurrentRequest(state, action.generation, action.sourceLanguage, action.targetLanguage)) {
                state.copy(translatedText = null, status = action.status)
            } else {
                state
            }
        TranslationComposerAction.Cancel ->
            state.copy(status = TranslationStatus.IDLE, requestGeneration = state.requestGeneration + 1)
        is TranslationComposerAction.PasteResult,
        is TranslationComposerAction.ReplyWithResult,
        TranslationComposerAction.Dismiss -> state
    }

fun translationCommit(
    state: TranslationComposerState,
    action: TranslationComposerAction,
): TranslationCommit? {
    val result = state.translatedText?.takeIf { it.isNotBlank() } ?: return null
    return when (action) {
        is TranslationComposerAction.PasteResult ->
            TranslationCommit.Paste(result, action.cursor.coerceAtLeast(0))
        is TranslationComposerAction.ReplyWithResult ->
            if (
                state.hasSelection &&
                    state.selectedStart == action.currentSelectionStart &&
                    state.selectedEnd == action.currentSelectionEnd
            ) {
                TranslationCommit.Reply(result, state.selectedStart, state.selectedEnd)
            } else {
                null
            }
        else -> null
    }
}

private fun matchesCurrentRequest(
    state: TranslationComposerState,
    generation: Long,
    sourceLanguage: String,
    targetLanguage: String,
): Boolean =
    state.status == TranslationStatus.LOADING &&
        state.requestGeneration == generation &&
        state.sourceLanguage == sourceLanguage &&
        state.targetLanguage == targetLanguage