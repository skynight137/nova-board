package com.auroraeq.app.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLogTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `normal app event creates a shareable log under private logs directory`() {
        AppLog.init(context)
        AppLog.i("AppLogTest", "support-log-entry")

        val logFile = AppLog.currentLogFile()

        assertNotNull(logFile)
        assertTrue(logFile!!.canonicalPath.endsWith("/files/logs/aurora-eq.log"))
        assertTrue(logFile.exists())
        assertTrue(logFile.readText().contains("I/AppLogTest: support-log-entry"))
    }

    @Test
    fun `log entry contains only the app-owned message passed to the logger`() {
        AppLog.init(context)
        AppLog.i("AppLogTest", "preset-json-and-credentials-never-enter-log")

        val contents = AppLog.currentLogFile()!!.readText()

        assertTrue(contents.contains("preset-json-and-credentials-never-enter-log"))
        assertTrue(!contents.contains("\"bands\""))
        assertTrue(!contents.contains("password="))
    }
}
