package com.auroraeq.app.util

import android.content.Intent
import android.net.Uri

fun buildReleasePageIntent(url: String): Intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

fun buildLogShareIntent(uri: Uri): Intent =
    Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
