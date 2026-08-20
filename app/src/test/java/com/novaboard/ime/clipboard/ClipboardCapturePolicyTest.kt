package com.novaboard.ime.clipboard

import com.novaboard.ime.settings.KeyboardPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardCapturePolicyTest {
    @Test
    fun imageCaptureIsDisabledByDefault() {
        assertFalse(KeyboardPreferences.DEFAULT_IMAGE_CLIPBOARD_HISTORY)
        assertFalse(shouldCaptureClipboardItem(ClipType.IMAGE, imageHistoryEnabled = false))
    }

    @Test
    fun enablingImageHistoryAllowsOnlyFutureImageCapture() {
        assertTrue(shouldCaptureClipboardItem(ClipType.IMAGE, imageHistoryEnabled = true))
    }

    @Test
    fun imagePreferenceDoesNotChangeTextCapture() {
        assertTrue(shouldCaptureClipboardItem(ClipType.TEXT, imageHistoryEnabled = false))
    }
}