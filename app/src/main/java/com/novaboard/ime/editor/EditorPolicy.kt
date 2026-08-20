package com.novaboard.ime.editor

import android.text.InputType

fun isConversationEditorInputType(inputType: Int): Boolean {
    val editorClass = inputType and InputType.TYPE_MASK_CLASS
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    return editorClass == InputType.TYPE_CLASS_TEXT &&
        variation !in setOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )
}