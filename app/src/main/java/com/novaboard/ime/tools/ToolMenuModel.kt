package com.novaboard.ime.tools

enum class ToolAvailability {
    AVAILABLE,
    UNAVAILABLE,
}

data class ToolMenuItem(
    val id: String,
    val label: String,
    val availability: ToolAvailability = ToolAvailability.AVAILABLE,
)

fun visibleToolMenuItems(items: List<ToolMenuItem>): List<ToolMenuItem> =
    items.filter { it.availability == ToolAvailability.AVAILABLE }