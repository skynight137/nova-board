package com.auroraeq.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local file-based app log — not Crashlytics (explicitly deferred until/if this ships to Play
 * Store). Captures both normal and error entries to a rotating file in app-private storage, plus
 * any uncaught crash, so a user can export/share it after the fact (Settings screen).
 *
 * Wired in *alongside* the existing `android.util.Log` calls and `EqRepository.errorEvents` Toast
 * path, not instead of either — Toasts stay exactly as they are today (rare/one-shot user-facing
 * errors only); this exists purely for after-the-fact developer diagnosis of things that already
 * scrolled off logcat or never showed a Toast.
 */
object AppLog {

    private const val MAX_BYTES = 512 * 1024L // rotate once the log passes 512 KB

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private var logFile: File? = null
    private var previousLogFile: File? = null

    /**
     * Call once, as early as possible in [com.auroraeq.app.EqApplication.onCreate] — every other
     * AppLog call is a no-op until this has run. Safe to call more than once; only the first call
     * takes effect.
     */
    @Synchronized
    fun init(context: Context) {
        if (logFile != null) return
        val dir = File(context.applicationContext.filesDir, "logs").apply { mkdirs() }
        logFile = File(dir, "aurora-eq.log")
        previousLogFile = File(dir, "aurora-eq.log.1")
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

    /**
     * For [Thread.UncaughtExceptionHandler] use only — logs the crash to the file before the
     * default (process-killing) handler runs, so it's captured even though the app is about to die.
     * Does not call `Log.e` itself; the system's default handler already prints the trace to
     * logcat.
     */
    fun crash(throwable: Throwable) {
        write("E", "UncaughtException", throwable.message ?: throwable.toString(), throwable)
    }

    /**
     * The current log file for sharing/exporting (Settings screen). Null before [init] runs, or if
     * nothing has been logged yet.
     */
    fun currentLogFile(): File? = logFile?.takeIf { it.exists() }

    @Synchronized
    private fun write(level: String, tag: String, message: String, throwable: Throwable?) {
        val file = logFile ?: return
        try {
            rotateIfNeeded(file)
            file.appendText(
                buildString {
                    append(timestampFormat.format(Date()))
                    append(' ')
                        .append(level)
                        .append('/')
                        .append(tag)
                        .append(": ")
                        .append(message)
                        .append('\n')
                    if (throwable != null) {
                        val trace = StringWriter()
                        throwable.printStackTrace(PrintWriter(trace))
                        append(trace.toString())
                    }
                }
            )
        } catch (_: Exception) {
            // Logging must never itself crash the app it's trying to diagnose.
        }
    }

    private fun rotateIfNeeded(file: File) {
        val previous = previousLogFile ?: return
        if (file.exists() && file.length() > MAX_BYTES) {
            previous.delete()
            file.renameTo(previous)
        }
    }
}
