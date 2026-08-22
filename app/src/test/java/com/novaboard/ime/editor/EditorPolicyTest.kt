package com.novaboard.ime.editor

import android.text.InputType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorPolicyTest {
    @Test
    fun supportedConversationVariationsAreAllowed() {
        assertTrue(isConversationEditorInputType(InputType.TYPE_CLASS_TEXT))
        assertTrue(
            isConversationEditorInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL,
            ),
        )
        assertTrue(
            isConversationEditorInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
            ),
        )
        assertTrue(
            isConversationEditorInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE,
            ),
        )
        assertTrue(
            isConversationEditorInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT,
            ),
        )
    }

    @Test
    fun sensitiveAndNonConversationalVariationsAreExcluded() {
        assertFalse(
            isConversationEditorInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            ),
        )
        assertFalse(
            isConversationEditorInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT,
            ),
        )
        assertFalse(
            isConversationEditorInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
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
        assertFalse(
            isConversationEditorInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            ),
        )
        assertFalse(
            isConversationEditorInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            ),
        )
        assertFalse(
            isConversationEditorInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME,
            ),
        )
        assertFalse(
            isConversationEditorInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_FILTER,
            ),
        )
        assertFalse(
            isConversationEditorInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS,
            ),
        )
        assertFalse(
            isConversationEditorInputType(
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PHONETIC,
            ),
        )
    }

    @Test
    fun nonTextEditorsAreNotConversationEditors() {
        assertFalse(isConversationEditorInputType(InputType.TYPE_CLASS_NUMBER))
        assertFalse(
            isConversationEditorInputType(
                InputType.TYPE_CLASS_PHONE,
            ),
        )
    }
}