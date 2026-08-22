package com.novaboard.ime.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeBridgeContractsTest {
    @Test
    fun editorCommandsDoNotExposeInputConnection() {
        val request =
            NativeBridgeRequest.Editor(
                sessionId = InputSessionId(7L),
                requestId = BridgeRequestId("request-1"),
                command = EditorCommand.DeletePreviousCodePoint,
            )

        assertEquals(InputSessionId(7L), request.sessionId)
        assertTrue(request.command is EditorCommand.DeletePreviousCodePoint)
    }

    @Test
    fun sessionGateRejectsStaleRequestsAndInvalidatedSessions() {
        val gate = SessionGate(InputSessionId(3L))
        val current =
            NativeBridgeRequest.Voice(
                sessionId = InputSessionId(3L),
                requestId = BridgeRequestId("current"),
                operation = VoiceOperation.Stop,
            )
        val stale = current.copy(sessionId = InputSessionId(2L))

        assertTrue(gate.accepts(current))
        assertFalse(gate.accepts(stale))
        gate.invalidate()
        assertFalse(gate.accepts(current))
    }

    @Test
    fun requestValuesValidateAtTheBoundary() {
        assertEquals(100, HapticOperation.Press(100).intensity)
        assertEquals(44, KeyboardMetricsValue(44, 48, 44).touchTargetMinDp)
    }

    @Test(expected = IllegalArgumentException::class)
    fun blankRequestIdsAreRejected() {
        BridgeRequestId(" ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidHapticIntensityIsRejected() {
        HapticOperation.Press(101)
    }

    @Test
    fun sessionScopedBridgeReturnsHandlerResultForCurrentSession() {
        val gate = SessionGate(InputSessionId(8L))
        val bridge =
            SessionScopedNativeBridge(gate) {
                BridgeResult.Success(NativeBridgeResponse.Accepted)
            }
        val results = mutableListOf<BridgeResult>()
        val request =
            NativeBridgeRequest.Editor(
                InputSessionId(8L),
                BridgeRequestId("editor-1"),
                EditorCommand.ReplaceSelection("hello"),
            )

        bridge.execute(request, results::add)

        assertEquals(listOf(BridgeResult.Success(NativeBridgeResponse.Accepted)), results)
    }

    @Test
    fun sessionScopedBridgeRejectsStaleAndUnavailableRequestsExplicitly() {
        val gate = SessionGate(InputSessionId(8L))
        val bridge =
            SessionScopedNativeBridge(gate) {
                bridgeError(
                    BridgeErrorCode.RUNTIME_UNAVAILABLE,
                    "Native runtime is unavailable",
                    retryable = true,
                )
            }
        val results = mutableListOf<BridgeResult>()
        val current =
            NativeBridgeRequest.Voice(
                InputSessionId(8L),
                BridgeRequestId("voice-1"),
                VoiceOperation.Start,
            )
        val stale = current.copy(sessionId = InputSessionId(7L))

        bridge.execute(current, results::add)
        bridge.execute(stale, results::add)

        assertEquals(
            BridgeResult.Failure(
                BridgeError(
                    BridgeErrorCode.RUNTIME_UNAVAILABLE,
                    "Native runtime is unavailable",
                    retryable = true,
                ),
            ),
            results[0],
        )
        assertEquals(BridgeErrorCode.STALE_SESSION, (results[1] as BridgeResult.Failure).error.code)
    }

    @Test
    fun handlerCanExposeProviderAndPermissionFailuresWithoutFakeSuccess() {
        val gate = SessionGate(InputSessionId(1L))
        val bridge =
            SessionScopedNativeBridge(gate) {
                bridgeError(BridgeErrorCode.PROVIDER_REJECTED, "GIF provider rejected the request")
            }
        val results = mutableListOf<BridgeResult>()
        val request =
            NativeBridgeRequest.Gif(
                InputSessionId(1L),
                BridgeRequestId("gif-1"),
                GifOperation.Insert("https://example.invalid/gif"),
            )

        bridge.execute(request, results::add)

        assertTrue(results.single() is BridgeResult.Failure)
        assertEquals(
            BridgeErrorCode.PROVIDER_REJECTED,
            (results.single() as BridgeResult.Failure).error.code,
        )
    }
}