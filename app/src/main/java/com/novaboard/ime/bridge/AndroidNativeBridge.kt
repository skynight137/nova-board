package com.novaboard.ime.bridge

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import com.novaboard.ime.clipboard.ClipType
import com.novaboard.ime.clipboard.ClipboardItem
import com.novaboard.ime.editing.previousCodePointDeletionCount
import com.novaboard.ime.editing.previousWordDeletionCount
import com.novaboard.ime.gif.GifClientException
import com.novaboard.ime.settings.KeyboardPreferences
import com.novaboard.ime.theme.ThemeManager
import com.novaboard.ime.theme.ThemeMode

/**
 * Android-owned request adapter. The service supplies the current connection and
 * session gate; callers only see explicit commands and typed responses.
 */
class AndroidNativeBridge(
    private val contextProvider: () -> Context,
    private val connectionProvider: () -> InputConnection?,
    private val sessionGate: SessionGate,
    private val clipboardOperations: ClipboardOperationHandler? = null,
    private val gifOperations: GifOperationHandler? = null,
    private val voiceOperations: VoiceOperationHandler? = null,
) : NativeBridge {
    constructor(
        context: Context,
        connectionProvider: () -> InputConnection?,
        sessionGate: SessionGate,
        clipboardOperations: ClipboardOperationHandler? = null,
        gifOperations: GifOperationHandler? = null,
        voiceOperations: VoiceOperationHandler? = null,
    ) : this({ context }, connectionProvider, sessionGate, clipboardOperations, gifOperations, voiceOperations)

    private val context: Context
        get() = contextProvider()

    private val dispatcher =
        SessionScopedNativeBridge(sessionGate, ::dispatch)

    override fun execute(request: NativeBridgeRequest, callback: (BridgeResult) -> Unit) =
        dispatcher.execute(request, callback)

    private fun dispatch(request: NativeBridgeRequest, complete: BridgeCompletion) {
        when (request) {
            is NativeBridgeRequest.Editor -> complete(handleEditor(request.command))
            is NativeBridgeRequest.Preferences -> complete(handlePreferences(request.operation))
            is NativeBridgeRequest.Theme -> complete(handleTheme())
            is NativeBridgeRequest.Haptic -> complete(handleHaptic(request.operation))
            is NativeBridgeRequest.KeyboardMetrics -> complete(handleMetrics())
            is NativeBridgeRequest.Clipboard -> dispatchProvider(request.operation, clipboardOperations, complete)
            is NativeBridgeRequest.Gif -> dispatchProvider(request.operation, gifOperations, complete)
            is NativeBridgeRequest.Voice -> dispatchProvider(request.operation, voiceOperations, complete)
        }
    }

    private fun <T> dispatchProvider(
        operation: T,
        handler: ((T, BridgeCompletion) -> Unit)?,
        complete: BridgeCompletion,
    ) {
        if (handler == null) {
            complete(runtimeUnavailable("This native operation is not attached to the Android adapter yet"))
        } else {
            handler(operation, complete)
        }
    }

    private fun runtimeUnavailable(message: String): BridgeResult.Failure =
        bridgeError(BridgeErrorCode.RUNTIME_UNAVAILABLE, message, retryable = true)

    private fun handleEditor(command: EditorCommand): BridgeResult {
        val connection =
            connectionProvider()
                ?: return bridgeError(
                    BridgeErrorCode.EDITOR_UNAVAILABLE,
                    "No active editor connection is available",
                    retryable = true,
                )

        return when (command) {
            is EditorCommand.CommitText -> acceptedIf(connection.commitText(command.text, 1))
            is EditorCommand.ReplaceSelection -> acceptedIf(connection.commitText(command.text, 1))
            EditorCommand.DeletePreviousCodePoint -> {
                val before = connection.getTextBeforeCursor(2, 0)?.toString().orEmpty()
                acceptedIf(
                    connection.deleteSurroundingText(previousCodePointDeletionCount(before), 0),
                )
            }
            EditorCommand.DeletePreviousWord -> {
                val before = connection.getTextBeforeCursor(128, 0)?.toString().orEmpty()
                acceptedIf(connection.deleteSurroundingText(previousWordDeletionCount(before), 0))
            }
            is EditorCommand.MoveCursor -> moveCursor(connection, command.offset)
        }
    }

    private fun moveCursor(connection: InputConnection, offset: Int): BridgeResult {
        val keyCode = if (offset < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(offset)) {
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
        return BridgeResult.Success(NativeBridgeResponse.Accepted)
    }

    private fun handlePreferences(operation: PreferenceOperation): BridgeResult =
        when (operation) {
            is PreferenceOperation.ReadBoolean ->
                readPreference(operation.key)?.let { BridgeResult.Success(NativeBridgeResponse.BooleanValue(it)) }
                    ?: bridgeError(
                        BridgeErrorCode.INVALID_REQUEST,
                        "Unknown boolean preference",
                    )
            is PreferenceOperation.WriteBoolean -> {
                if (readPreference(operation.key) == null) {
                    bridgeError(BridgeErrorCode.INVALID_REQUEST, "Unknown boolean preference")
                } else {
                    KeyboardPreferences.setBoolean(context, operation.key, operation.value)
                    BridgeResult.Success(NativeBridgeResponse.Accepted)
                }
            }
            PreferenceOperation.ReadSnapshot ->
                BridgeResult.Success(
                    NativeBridgeResponse.PreferenceSnapshot(
                        BOOLEAN_PREFERENCES.associateWith { KeyboardPreferences.getBoolean(context, it) },
                    ),
                )
        }

    private fun readPreference(key: String): Boolean? =
        if (key in BOOLEAN_PREFERENCES) KeyboardPreferences.getBoolean(context, key) else null

    private fun handleTheme(): BridgeResult =
        BridgeResult.Success(
            NativeBridgeResponse.ThemeValue(
                when (ThemeManager.get(context)) {
                    ThemeMode.SYSTEM -> ThemeValueType.SYSTEM
                    ThemeMode.LIGHT -> ThemeValueType.LIGHT
                    ThemeMode.DARK -> ThemeValueType.DARK
                },
            ),
        )

    private fun handleHaptic(operation: HapticOperation): BridgeResult =
        when (operation) {
            is HapticOperation.Press -> performPress(operation.intensity)
        }

    private fun performPress(intensity: Int): BridgeResult {
        val vibrator = context.getSystemService(Vibrator::class.java)
        if (vibrator == null || !vibrator.hasVibrator()) {
            return bridgeError(
                BridgeErrorCode.RUNTIME_UNAVAILABLE,
                "Haptic feedback is unavailable on this device",
            )
        }
        val duration = (20L + intensity / 10L).coerceIn(20L, 30L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, intensity.coerceAtLeast(1)))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
        return BridgeResult.Success(NativeBridgeResponse.Accepted)
    }

    private fun handleMetrics(): BridgeResult =
        BridgeResult.Success(
            NativeBridgeResponse.KeyboardMetrics(
                KeyboardMetricsValue(keyHeightDp = 48, toolbarHeightDp = 48, touchTargetMinDp = 44),
            ),
        )

    private fun acceptedIf(accepted: Boolean): BridgeResult =
        if (accepted) {
            BridgeResult.Success(NativeBridgeResponse.Accepted)
        } else {
            bridgeError(
                BridgeErrorCode.EDITOR_UNAVAILABLE,
                "The active editor rejected the operation",
                retryable = true,
            )
        }

    private companion object {
        val BOOLEAN_PREFERENCES =
            listOf(
                KeyboardPreferences.SHOW_NUMBER_ROW,
                KeyboardPreferences.SHOW_ARROW_KEYS,
                KeyboardPreferences.LONG_PRESS_SYMBOLS,
                KeyboardPreferences.ACCENTED_CHARACTERS,
                KeyboardPreferences.KEY_POPUPS,
                KeyboardPreferences.LARGE_KEY_TEXT,
                KeyboardPreferences.AUTOCORRECT,
                KeyboardPreferences.QUICK_PERIOD,
                KeyboardPreferences.AUTO_CAPITALIZE,
                KeyboardPreferences.AUTO_SPACE,
                KeyboardPreferences.CURSOR_CONTROL,
                KeyboardPreferences.QUICK_DELETE,
                KeyboardPreferences.EMOJI_PREDICTIONS,
                KeyboardPreferences.DEDICATED_EMOJI_KEY,
                KeyboardPreferences.EMOJI_ON_ENTER,
                KeyboardPreferences.SOUND_ON_KEYPRESS,
                KeyboardPreferences.VIBRATION_ON_KEYPRESS,
                KeyboardPreferences.UNDO_AUTOCORRECT,
                KeyboardPreferences.QUICK_PREDICTION_INSERT,
                KeyboardPreferences.IMAGE_CLIPBOARD_HISTORY,
                KeyboardPreferences.INCOGNITO_MODE,
            )
    }
}

internal fun gifSearchError(error: Throwable): BridgeResult.Failure =
    when (error) {
        is GifClientException.NotConfigured ->
            bridgeError(
                BridgeErrorCode.NOT_CONFIGURED,
                "The GIF provider API key is not configured",
            )
        is GifClientException.InvalidResponse ->
            bridgeError(
                BridgeErrorCode.PROVIDER_REJECTED,
                "The GIF provider returned an invalid response",
            )
        is GifClientException.HttpFailure ->
            bridgeError(
                BridgeErrorCode.PROVIDER_REJECTED,
                "The GIF provider request failed with HTTP ${error.code}",
                retryable = true,
            )
        else ->
            bridgeError(
                BridgeErrorCode.PROVIDER_REJECTED,
                "The GIF provider request failed",
                retryable = true,
            )
    }

internal fun ClipboardItem.toPreviewItem(): ClipboardPreviewItem =
    ClipboardPreviewItem(
        id = id,
        text = if (type == ClipType.TEXT) text else null,
        pinned = pinned,
    )
