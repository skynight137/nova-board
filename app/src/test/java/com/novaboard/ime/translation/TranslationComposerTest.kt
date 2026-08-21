package com.novaboard.ime.translation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationComposerTest {
    @Test
    fun emptySourceCannotStartRequest() {
        val state = TranslationComposerState()

        assertEquals(
            TranslationStatus.IDLE,
            reduceTranslationComposer(state, TranslationComposerAction.RequestTranslation).status,
        )
    }

    @Test
    fun requestAndSuccessRequireTheCurrentGenerationAndLanguages() {
        val edited =
            reduceTranslationComposer(
                TranslationComposerState(session = 4L, sourceText = "hola"),
                TranslationComposerAction.RequestTranslation,
            )
        val stale =
            TranslationComposerAction.TranslationSucceeded(
                edited.requestGeneration - 1,
                edited.sourceLanguage,
                edited.targetLanguage,
                "hello",
            )
        val ready =
            reduceTranslationComposer(
                edited,
                TranslationComposerAction.TranslationSucceeded(
                    edited.requestGeneration,
                    edited.sourceLanguage,
                    edited.targetLanguage,
                    "hello",
                ),
            )

        assertEquals(TranslationStatus.LOADING, reduceTranslationComposer(edited, stale).status)
        assertEquals(TranslationStatus.READY, ready.status)
        assertEquals("hello", ready.translatedText)
    }

    @Test
    fun swappingLanguagesInvalidatesVisibleResult() {
        val state =
            TranslationComposerState(
                sourceText = "hola",
                translatedText = "hello",
                status = TranslationStatus.READY,
            )

        val swapped = reduceTranslationComposer(state, TranslationComposerAction.SwapLanguages)

        assertEquals("en", swapped.sourceLanguage)
        assertEquals("auto", swapped.targetLanguage)
        assertNull(swapped.translatedText)
        assertEquals(TranslationStatus.IDLE, swapped.status)
    }

    @Test
    fun pasteAndReplyHaveDistinctSafeCommitContracts() {
        val state =
            TranslationComposerState(
                sourceText = "hola",
                translatedText = "hello",
                status = TranslationStatus.READY,
                selectedStart = 5,
                selectedEnd = 9,
                insertionCursor = 9,
            )

        assertEquals(
            TranslationCommit.Paste("hello", 9),
            translationCommit(state, TranslationComposerAction.PasteResult(state.insertionCursor)),
        )
        assertEquals(
            TranslationCommit.Reply("hello", 5, 9),
            translationCommit(state, TranslationComposerAction.ReplyWithResult(5, 9)),
        )
        assertNull(translationCommit(state, TranslationComposerAction.ReplyWithResult(0, 4)))
        assertTrue(translationCommit(state, TranslationComposerAction.PasteResult(-1)) is TranslationCommit.Paste)
    }

    @Test
    fun pasteCursorComesFromTheTargetEditorStateNotTheSourceField() {
        val state =
            TranslationComposerState(
                sourceText = "hola",
                translatedText = "hello",
                status = TranslationStatus.READY,
                insertionCursor = 17,
            )

        assertEquals(
            TranslationCommit.Paste("hello", 17),
            translationCommit(state, TranslationComposerAction.PasteResult(state.insertionCursor)),
        )
    }
}