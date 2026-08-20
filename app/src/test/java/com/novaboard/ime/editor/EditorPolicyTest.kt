package com.novaboard.ime.editor

import android.text.InputType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPolicyTest {
    @Test
    fun conversationPolicyExcludesSensitiveAndAddressEditors() {
        assertTrue(isConversationEditorInputType(InputType.TYPE_CLASS_TEXT))
        assertFalse(
            isConversationEditorInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            ),
        )
        assertFalse(
            isConversationEditorInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            ),
        )
        assertFalse(
            isConversationEditorInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            ),
        )
    }

    @Test
    fun nonTextEditorsAreNotConversationEditors() {
        assertFalse(isConversationEditorInputType(InputType.TYPE_CLASS_NUMBER))
    }
}