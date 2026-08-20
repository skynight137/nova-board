package com.novaboard.ime.translation

data class TranslationProviderRequest(
    val sourceLanguage: String,
    val targetLanguage: String,
    val sourceText: String,
)

sealed interface TranslationProviderResult {
    data class Success(val text: String) : TranslationProviderResult

    data class Failure(val status: TranslationStatus) : TranslationProviderResult
}

fun interface TranslationRequestHandle {
    fun cancel()
}

interface TranslationProvider {
    fun translate(
        request: TranslationProviderRequest,
        callback: (TranslationProviderResult) -> Unit,
    ): TranslationRequestHandle
}

/**
 * The default provider is deliberately honest until a supported translation
 * integration is configured. It never launches another application.
 */
class UnavailableTranslationProvider : TranslationProvider {
    override fun translate(
        request: TranslationProviderRequest,
        callback: (TranslationProviderResult) -> Unit,
    ): TranslationRequestHandle {
        callback(TranslationProviderResult.Failure(TranslationStatus.UNAVAILABLE))
        return TranslationRequestHandle {}
    }
}
