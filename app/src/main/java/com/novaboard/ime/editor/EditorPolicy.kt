package com.novaboard.ime.editor

import android.text.InputType

/**
 * Editor variations that should receive conversation-style behavior such as
 * emoji-on-enter. This is an explicit allowlist so new or sensitive
 * variations are excluded by default rather than relying on a growing list
 * of things to reject.
 */
private val SUPPORTED_CONVERSATION_VARIATIONS = setOf(
    InputType.TYPE_TEXT_VARIATION_NORMAL,
    InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
    InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE,
    InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT,
)

fun isConversationEditorInputType(inputType: Int): Boolean {
    val editorClass = inputType and InputType.TYPE_MASK_CLASS
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    return editorClass == InputType.TYPE_CLASS_TEXT &&
        variation in SUPPORTED_CONVERSATION_VARIATIONS
}