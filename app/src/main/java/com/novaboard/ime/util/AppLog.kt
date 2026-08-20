package com.novaboard.ime.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.novaboard.ime.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local, user-exported application log.
 *
 * The report intentionally contains only bounded app-owned events and diagnostic metadata. It does
 * not read clipboard contents or system-wide logcat.
 */
object AppLog {
    private const val MAX_BYTES = 512 * 1024L
    private const val MAX_REPORT_BYTES = 768 * 1024
    private const val LOG_DIRECTORY = "logs"
    private const val LOG_NAME = "novaboard.log"
    private const val PREVIOUS_LOG_NAME = "novaboard.log.1"

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private var logFile: File? = null
    private var previousLogFile: File? = null

    @Synchronized
    fun init(context: Context) {
        if (logFile != null) return
        val directory = File(context.applicationContext.filesDir, LOG_DIRECTORY).apply { mkdirs() }
        logFile = File(directory, LOG_NAME)
        previousLogFile = File(directory, PREVIOUS_LOG_NAME)

        val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
        if (currentHandler !is AppLogExceptionHandler) {
            Thread.setDefaultUncaughtExceptionHandler(AppLogExceptionHandler(currentHandler))
        }
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        write("I", tag, message, null)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        write("W", tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        write("E", tag, message, throwable)
    }

    fun crash(throwable: Throwable) {
        write("E", "UncaughtException", throwable.message ?: throwable.toString(), throwable)
    }

    fun currentLogFile(): File? = logFile?.takeIf { it.exists() }

    /** Writes a complete support report to a user-selected destination. */
    fun exportReport(context: Context, destination: Uri) {
        val report = buildReport(context)
        context.contentResolver.openOutputStream(destination)?.use { output ->
            output.writer(Charsets.UTF_8).use { writer -> writer.write(report) }
        } ?: error("Unable to open selected export destination")
    }

    internal fun buildReport(context: Context): String {
        val packageInfo =
            runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
                .getOrNull()
        val versionName = packageInfo?.versionName ?: BuildConfig.VERSION_NAME
        val versionCode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo?.longVersionCode?.toString() ?: "unknown"
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.versionCode?.toString() ?: "unknown"
            }
        val abiList = Build.SUPPORTED_ABIS?.joinToString(", ").orEmpty().ifBlank { "unknown" }
        val locale = Locale.getDefault().toLanguageTag().bounded()
        val appLabel =
            runCatching {
                    context.packageManager.getApplicationLabel(context.applicationInfo).toString()
                }
                .getOrDefault("NovaBoard")

        return buildString {
                appendLine("NovaBoard Diagnostic Report")
                appendLine("Generated: ${timestampFormat.format(Date())}")
                appendLine()
                appendLine("Device Information")
                appendLine("------------------")
                appendLine("Manufacturer: ${Build.MANUFACTURER.bounded()}")
                appendLine("Model: ${Build.MODEL.bounded()}")
                appendLine("Device: ${Build.DEVICE.bounded()}")
                appendLine("Android version: ${Build.VERSION.RELEASE.bounded()}")
                appendLine("SDK: ${Build.VERSION.SDK_INT}")
                appendLine("ABIs: $abiList")
                appendLine("Locale: $locale")
                appendLine()
                appendLine("App Information")
                appendLine("---------------")
                appendLine("Application: ${appLabel.bounded()}")
                appendLine("Package: ${context.packageName}")
                appendLine("Version name: ${versionName.bounded()}")
                appendLine("Version code: $versionCode")
                appendLine("Build type: ${BuildConfig.BUILD_TYPE}")
                appendLine("Release channel: ${BuildConfig.RELEASE_CHANNEL}")
                appendLine()
                appendLine("Application Log")
                appendLine("---------------")
                append(readLogs())
            }
            .take(MAX_REPORT_BYTES)
    }

    @Synchronized
    private fun write(level: String, tag: String, message: String, throwable: Throwable?) {
        val file = logFile ?: return
        runCatching {
            rotateIfNeeded(file)
            file.appendText(
                buildString {
                    append(timestampFormat.format(Date()))
                    append(' ')
                    append(level)
                    append('/')
                    append(tag.bounded())
                    append(": ")
                    append(message.singleLine())
                    append('\n')
                    if (throwable != null) {
                        val trace = StringWriter()
                        throwable.printStackTrace(PrintWriter(trace))
                        append(trace.toString().take(16 * 1024))
                    }
                }
            )
        }
    }

    private fun readLogs(): String {
        val files = listOfNotNull(previousLogFile, logFile).filter { it.exists() }
        if (files.isEmpty()) return "No application log entries recorded.\n"
        return files
            .joinToString(separator = "") { file ->
                "## ${file.name}\n${file.readText().take(MAX_REPORT_BYTES)}\n"
            }
            .take(MAX_REPORT_BYTES)
    }

    private fun rotateIfNeeded(file: File) {
        val previous = previousLogFile ?: return
        if (file.exists() && file.length() > MAX_BYTES) {
            previous.delete()
            file.renameTo(previous)
        }
    }

    private fun String.singleLine(): String = replace(Regex("\\s+"), " ").take(4 * 1024)

    private fun String.bounded(): String = singleLine().ifBlank { "unknown" }

    private class AppLogExceptionHandler(private val delegate: Thread.UncaughtExceptionHandler?) :
        Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, throwable: Throwable) {
            crash(throwable)
            delegate?.uncaughtException(thread, throwable)
        }
    }
}
