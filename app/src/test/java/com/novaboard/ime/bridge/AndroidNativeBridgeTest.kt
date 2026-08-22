package com.novaboard.ime.bridge

import com.novaboard.ime.clipboard.ClipType
import com.novaboard.ime.clipboard.ClipboardItem
import com.novaboard.ime.gif.GifClientException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidNativeBridgeTest {
    @Test
    fun missingProviderHandlersStayExplicitlyUnavailable() {
        val bridge =
            AndroidNativeBridge(
                contextProvider = { error("Context is not needed for provider seams") },
                connectionProvider = { null },
                sessionGate = SessionGate(InputSessionId(1L)),
            )
        val results = mutableListOf<BridgeResult>()
        val request =
            NativeBridgeRequest.Clipboard(
                InputSessionId(1L),
                BridgeRequestId("clip-1"),
                ClipboardOperation.List,
            )

        bridge.execute(request, results::add)

        assertEquals(
            BridgeErrorCode.RUNTIME_UNAVAILABLE,
            (results.single() as BridgeResult.Failure).error.code,
        )
    }

    @Test
    fun attachedClipboardHandlerReceivesOperations() {
        var received: ClipboardOperation? = null
        val bridge =
            AndroidNativeBridge(
                contextProvider = { error("Context is not needed for provider seams") },
                connectionProvider = { null },
                sessionGate = SessionGate(InputSessionId(2L)),
                clipboardOperations = { operation, complete ->
                    received = operation
                    complete(BridgeResult.Success(NativeBridgeResponse.Accepted))
                },
            )
        val results = mutableListOf<BridgeResult>()
        val request =
            NativeBridgeRequest.Clipboard(
                InputSessionId(2L),
                BridgeRequestId("clip-2"),
                ClipboardOperation.Delete(itemId = 9L),
            )

        bridge.execute(request, results::add)

        assertEquals(ClipboardOperation.Delete(itemId = 9L), received)
        assertEquals(listOf<BridgeResult>(BridgeResult.Success(NativeBridgeResponse.Accepted)), results)
    }

    @Test
    fun deferredProviderResultsAreGatedAtCompletion() {
        val gate = SessionGate(InputSessionId(3L))
        var pending: BridgeCompletion? = null
        val bridge =
            AndroidNativeBridge(
                contextProvider = { error("Context is not needed for provider seams") },
                connectionProvider = { null },
                sessionGate = gate,
                gifOperations = { _, completion -> pending = completion },
            )
        val results = mutableListOf<BridgeResult>()
        val request =
            NativeBridgeRequest.Gif(
                InputSessionId(3L),
                BridgeRequestId("gif-1"),
                GifOperation.Search("cats"),
            )

        bridge.execute(request, results::add)
        assertTrue(results.isEmpty())
        gate.begin(InputSessionId(4L))
        pending?.invoke(
            BridgeResult.Success(NativeBridgeResponse.GifItems(emptyList())),
        )

        assertEquals(
            BridgeErrorCode.STALE_SESSION,
            (results.single() as BridgeResult.Failure).error.code,
        )
    }

    @Test
    fun gifSearchFailuresMapToTypedBridgeCodes() {
        assertEquals(
            BridgeErrorCode.NOT_CONFIGURED,
            gifSearchError(GifClientException.NotConfigured).error.code,
        )
        assertEquals(
            BridgeErrorCode.PROVIDER_REJECTED,
            gifSearchError(GifClientException.InvalidResponse).error.code,
        )
        assertTrue(gifSearchError(GifClientException.HttpFailure(503)).error.retryable)
        assertFalse(gifSearchError(IllegalStateException("offline")).error.message.isBlank())
    }

    @Test
    fun imageClipboardItemsDoNotExposeTextToTheBridge() {
        val textItem =
            ClipboardItem(id = 1L, type = ClipType.TEXT, text = "hello", pinned = true)
        val imageItem =
            ClipboardItem(id = 2L, type = ClipType.IMAGE, imageUri = "content://images/1")

        assertEquals("hello", textItem.toPreviewItem().text)
        assertTrue(textItem.toPreviewItem().pinned)
        assertNull(imageItem.toPreviewItem().text)
    }
}

