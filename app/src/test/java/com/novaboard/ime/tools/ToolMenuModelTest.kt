package com.novaboard.ime.tools

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolMenuModelTest {
    @Test
    fun unavailableItemsAreNotPresentedAsDeadControls() {
        val items =
            listOf(
                ToolMenuItem("clipboard", "Clipboard"),
                ToolMenuItem("gif", "GIF", ToolAvailability.UNAVAILABLE),
                ToolMenuItem("settings", "Settings"),
            )

        assertEquals(listOf("clipboard", "settings"), visibleToolMenuItems(items).map { it.id })
    }
}