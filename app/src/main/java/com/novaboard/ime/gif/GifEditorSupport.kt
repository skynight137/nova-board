package com.novaboard.ime.gif

internal fun supportsGifContent(mimeTypes: Array<String>?): Boolean =
    mimeTypes.orEmpty().any { mimeType ->
        mimeType.equals("image/gif", ignoreCase = true) ||
            mimeType.equals("image/*", ignoreCase = true)
    }