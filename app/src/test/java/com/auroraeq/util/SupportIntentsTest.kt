package com.auroraeq.app.util

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SupportIntentsTest {

    @Test
    fun `release intent opens configured page`() {
        val intent = buildReleasePageIntent("https://github.com/example/aurora-eq/releases")

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(
            Uri.parse("https://github.com/example/aurora-eq/releases"),
            intent.data,
        )
    }

    @Test
    fun `log share intent carries a read-only text attachment`() {
        val uri = Uri.parse("content://com.auroraeq.app.fileprovider/logs/current")
        val intent = buildLogShareIntent(uri)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals(uri, intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertTrue(intent.flags.toLong() and Intent.FLAG_GRANT_READ_URI_PERMISSION.toLong() != 0L)
    }
}
