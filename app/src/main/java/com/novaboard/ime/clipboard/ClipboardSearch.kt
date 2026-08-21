package com.novaboard.ime.clipboard

/** Filters text clipboard items by their visible content while retaining all items for an empty query. */
fun filterClipboardItems(items: List<ClipboardItem>, query: String): List<ClipboardItem> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return items
    return items.filter { item ->
        item.type == ClipType.TEXT && item.text.orEmpty().contains(normalizedQuery, ignoreCase = true)
    }
}