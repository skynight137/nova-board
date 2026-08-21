package com.novaboard.ime.clipboard

/**
 * Image retention is user-controlled while text clipboard history remains unchanged.
 */
fun shouldCaptureClipboardItem(type: ClipType, imageHistoryEnabled: Boolean): Boolean =
    type != ClipType.IMAGE || imageHistoryEnabled

fun shouldCaptureClipboard(type: ClipType, incognito: Boolean, imageHistoryEnabled: Boolean): Boolean =
    !incognito && shouldCaptureClipboardItem(type, imageHistoryEnabled)

fun clipboardText(directText: String?, coercedText: String?): String? =
    directText?.takeIf { it.isNotBlank() } ?: coercedText?.takeIf { it.isNotBlank() }

fun removeImageClipboardItems(items: List<ClipboardItem>): List<ClipboardItem> =
    items.filterNot { it.type == ClipType.IMAGE }
