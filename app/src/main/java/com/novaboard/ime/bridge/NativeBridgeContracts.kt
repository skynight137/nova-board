package com.novaboard.ime.bridge

@JvmInline
value class InputSessionId(val value: Long)

@JvmInline
value class BridgeRequestId(val value: String) {
    init {
        require(value.isNotBlank()) { "Bridge request ID must not be blank" }
    }
}

sealed interface EditorCommand {
    data class CommitText(val text: String) : EditorCommand {
        init {
            require(text.isNotEmpty()) { "Committed text must not be empty" }
        }
    }

    data object DeletePreviousCodePoint : EditorCommand

    data object DeletePreviousWord : EditorCommand

    data class MoveCursor(val offset: Int) : EditorCommand {
        init {
            require(offset != 0) { "Cursor movement must have a non-zero offset" }
        }
    }

    data class ReplaceSelection(val text: String) : EditorCommand
}

sealed interface NativeBridgeRequest {
    val sessionId: InputSessionId
    val requestId: BridgeRequestId

    data class Editor(
        override val sessionId: InputSessionId,
        override val requestId: BridgeRequestId,
        val command: EditorCommand,
    ) : NativeBridgeRequest

    data class Clipboard(
        override val sessionId: InputSessionId,
        override val requestId: BridgeRequestId,
        val operation: ClipboardOperation,
    ) : NativeBridgeRequest

    data class Gif(
        override val sessionId: InputSessionId,
        override val requestId: BridgeRequestId,
        val operation: GifOperation,
    ) : NativeBridgeRequest

    data class Voice(
        override val sessionId: InputSessionId,
        override val requestId: BridgeRequestId,
        val operation: VoiceOperation,
    ) : NativeBridgeRequest

    data class Preferences(
        override val sessionId: InputSessionId,
        override val requestId: BridgeRequestId,
        val operation: PreferenceOperation,
    ) : NativeBridgeRequest

    data class Theme(
        override val sessionId: InputSessionId,
        override val requestId: BridgeRequestId,
        val operation: ThemeOperation,
    ) : NativeBridgeRequest

    data class Haptic(
        override val sessionId: InputSessionId,
        override val requestId: BridgeRequestId,
        val operation: HapticOperation,
    ) : NativeBridgeRequest

    data class KeyboardMetrics(
        override val sessionId: InputSessionId,
        override val requestId: BridgeRequestId,
        val operation: KeyboardMetricsOperation,
    ) : NativeBridgeRequest

    data class Settings(
        override val sessionId: InputSessionId,
        override val requestId: BridgeRequestId,
        val operation: SettingsOperation,
    ) : NativeBridgeRequest

    data class Emoji(
        override val sessionId: InputSessionId,
        override val requestId: BridgeRequestId,
        val operation: EmojiOperation,
    ) : NativeBridgeRequest
}

sealed interface ClipboardOperation {
    data object List : ClipboardOperation
    data class Search(val query: String) : ClipboardOperation
    data class SetPinned(val itemId: Long, val pinned: Boolean) : ClipboardOperation
    data class Delete(val itemId: Long) : ClipboardOperation
}

sealed interface GifOperation {
    data class Search(val query: String) : GifOperation
    data class Insert(val contentUrl: String) : GifOperation
}

sealed interface VoiceOperation {
    data object Start : VoiceOperation
    data object Stop : VoiceOperation
}

sealed interface PreferenceOperation {
    data class ReadBoolean(val key: String) : PreferenceOperation
    data class WriteBoolean(val key: String, val value: Boolean) : PreferenceOperation
    data object ReadSnapshot : PreferenceOperation
}

sealed interface ThemeOperation {
    data object Read : ThemeOperation
}

sealed interface HapticOperation {
    data class Press(val intensity: Int) : HapticOperation {
        init {
            require(intensity in 0..100) { "Haptic intensity must be between 0 and 100" }
        }
    }
}

sealed interface KeyboardMetricsOperation {
    data object Read : KeyboardMetricsOperation
}

sealed interface SettingsOperation {
    data object OpenImeSettings : SettingsOperation

    data object ShowImePicker : SettingsOperation

    data object ReadStatus : SettingsOperation
}

sealed interface EmojiOperation {
    data object List : EmojiOperation

    data class Search(val query: String) : EmojiOperation
}

sealed interface NativeBridgeResponse {
    data object Accepted : NativeBridgeResponse
    data class BooleanValue(val value: Boolean) : NativeBridgeResponse
    data class ClipboardItems(val items: List<ClipboardPreviewItem>) : NativeBridgeResponse
    data class GifItems(val items: List<GifPreviewItem>) : NativeBridgeResponse
    data class VoiceState(val state: VoiceStateValue) : NativeBridgeResponse
    data class PreferenceSnapshot(val values: Map<String, Boolean>) : NativeBridgeResponse
    data class ThemeValue(val theme: ThemeValueType) : NativeBridgeResponse
    data class KeyboardMetrics(val metrics: KeyboardMetricsValue) : NativeBridgeResponse
    data class InputMethodStatus(val status: InputMethodStatusValue) : NativeBridgeResponse
    data class EmojiItems(val items: List<EmojiPreviewItem>) : NativeBridgeResponse
}

data class InputMethodStatusValue(
    val enabled: Boolean,
    val selected: Boolean,
) {
    init {
        require(!selected || enabled) { "A selected input method must also be enabled" }
    }
}

data class ClipboardPreviewItem(
    val id: Long,
    val text: String?,
    val pinned: Boolean,
)

data class GifPreviewItem(
    val slug: String,
    val title: String,
    val previewUrl: String,
)

data class EmojiPreviewItem(
    val emoji: String,
)

enum class VoiceStateValue {
    IDLE,
    LISTENING,
    UNAVAILABLE,
}

enum class ThemeValueType {
    SYSTEM,
    LIGHT,
    DARK,
}

data class KeyboardMetricsValue(
    val keyHeightDp: Int,
    val toolbarHeightDp: Int,
    val touchTargetMinDp: Int,
) {
    init {
        require(keyHeightDp > 0) { "Key height must be positive" }
        require(toolbarHeightDp > 0) { "Toolbar height must be positive" }
        require(touchTargetMinDp > 0) { "Touch target must be positive" }
    }
}

sealed interface BridgeResult {
    data class Success(val response: NativeBridgeResponse) : BridgeResult
    data class Failure(val error: BridgeError) : BridgeResult
}

enum class BridgeErrorCode {
    EDITOR_UNAVAILABLE,
    RUNTIME_UNAVAILABLE,
    STALE_SESSION,
    INVALID_REQUEST,
    PERMISSION_REQUIRED,
    PROVIDER_REJECTED,
    NOT_CONFIGURED,
}

data class BridgeError(
    val code: BridgeErrorCode,
    val message: String,
    val retryable: Boolean,
)

interface NativeBridge {
    fun execute(request: NativeBridgeRequest, callback: (BridgeResult) -> Unit)
}

fun bridgeError(
    code: BridgeErrorCode,
    message: String,
    retryable: Boolean = false,
): BridgeResult.Failure = BridgeResult.Failure(BridgeError(code, message, retryable))

typealias BridgeCompletion = (BridgeResult) -> Unit

typealias ClipboardOperationHandler = (ClipboardOperation, BridgeCompletion) -> Unit

typealias GifOperationHandler = (GifOperation, BridgeCompletion) -> Unit

typealias VoiceOperationHandler = (VoiceOperation, BridgeCompletion) -> Unit

typealias SettingsOperationHandler = (SettingsOperation, BridgeCompletion) -> Unit

typealias EmojiOperationHandler = (EmojiOperation, BridgeCompletion) -> Unit

class SessionScopedNativeBridge(
    private val sessionGate: SessionGate,
    private val handler: (NativeBridgeRequest, BridgeCompletion) -> Unit,
) : NativeBridge {
    constructor(
        sessionGate: SessionGate,
        syncHandler: (NativeBridgeRequest) -> BridgeResult,
    ) : this(sessionGate, { request, completion -> completion(syncHandler(request)) })

    override fun execute(request: NativeBridgeRequest, callback: (BridgeResult) -> Unit) {
        if (!sessionGate.accepts(request)) {
            callback(staleSession("The input session is no longer active"))
            return
        }

        handler(request) { result ->
            callback(
                if (sessionGate.accepts(request)) {
                    result
                } else {
                    staleSession("The input session changed while the request was running")
                },
            )
        }
    }

    private fun staleSession(message: String) =
        bridgeError(BridgeErrorCode.STALE_SESSION, message)
}

class SessionGate(initialSession: InputSessionId? = null) {
    var activeSession: InputSessionId? = initialSession
        private set

    fun begin(sessionId: InputSessionId) {
        activeSession = sessionId
    }

    fun invalidate() {
        activeSession = null
    }

    fun accepts(request: NativeBridgeRequest): Boolean = request.sessionId == activeSession
}