package com.novaboard.ime.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationProviderTest {
    @Test
    fun unavailableProviderReportsAnHonestUnavailableResult() {
        var result: TranslationProviderResult? = null

        UnavailableTranslationProvider().translate(
            TranslationProviderRequest("auto", "en", "hola"),
        ) {
            result = it
        }

        assertEquals(
            TranslationProviderResult.Failure(TranslationStatus.UNAVAILABLE),
            result,
        )
    }

    @Test
    fun providerRequestCarriesLanguagesAndSourceText() {
        var received: TranslationProviderRequest? = null
        val provider =
            object : TranslationProvider {
                override fun translate(
                    request: TranslationProviderRequest,
                    callback: (TranslationProviderResult) -> Unit,
                ): TranslationRequestHandle {
                    received = request
                    callback(TranslationProviderResult.Success("hello"))
                    return TranslationRequestHandle {}
                }
            }

        provider.translate(TranslationProviderRequest("es", "en", "hola")) {}

        assertEquals(TranslationProviderRequest("es", "en", "hola"), received)
    }

    @Test
    fun requestHandleCanCancelProviderWork() {
        var cancelled = false
        val handle =
            TranslationRequestHandle {
                cancelled = true
            }

        handle.cancel()

        assertTrue(cancelled)
    }
}