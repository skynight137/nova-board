package com.novaboard.ime.clipboard

/**
 * Image retention is user-controlled while text clipboard history remains unchanged.
 */
fun shouldCaptureClipboardItem(type: ClipType, imageHistoryEnabled: Boolean): Boolean =
    type != ClipType.IMAGE || imageHistoryEnabled

fun removeImageClipboardItems(items: List<ClipboardItem>): List<ClipboardItem> =
    items.filterNot { it.type == ClipType.IMAGE }