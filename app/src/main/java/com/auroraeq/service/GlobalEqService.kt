package com.auroraeq.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.auroraeq.app.EqApplication
import com.auroraeq.app.MainActivity
import com.auroraeq.app.R
import com.auroraeq.app.util.AppLog

private const val TAG = "GlobalEqService"
private const val CHANNEL_ID = "aurora_eq_global_mode"
private const val NOTIFICATION_ID = 1001

/**
 * Foreground service that keeps Aurora EQ's audio processing chain alive. There is no longer a
 * "Global Mode" toggle (refactor spec section 2) — this is started unconditionally from
 * [EqApplication.onCreate] and stays up for the app process's lifetime.
 *
 * There is intentionally no in-notification "Stop" action (removed 2026-08-04) — a real-device
 * report confirmed that once the app's task is swiped away, some manufacturers' battery-saving
 * policies strip custom actions from an "orphaned" foreground-service notification regardless of
 * app-side defenses (a re-post-on-onTaskRemoved workaround was tried and did not survive on the
 * reporting device). Rather than ship a control that quietly stops working on a swipe, users
 * needing a full stop use system Settings → Apps → Aurora EQ → Force stop, which is the standard,
 * always-reliable way to stop any Android background service.
 *
 * Reality check (unchanged from v1, see Settings/Help): Android does not let a non-root,
 * third-party app intercept *all* system audio the way a desktop/iOS system EQ can. This attaches
 * the effect chain to audio session 0 (AudioManager.AUDIO_SESSION_ID_GENERATE), which many OEMs
 * route most apps' output through — but behavior varies by device/Android version, and some apps,
 * system UI sounds, or hardware-accelerated paths won't be affected.
 */
class GlobalEqService : Service() {

    override fun onCreate() {
        super.onCreate()
        AppLog.i(TAG, "onCreate: attaching engine and starting foreground notification")
        val app = application as EqApplication
        app.eqRepository.attachEngine(AudioManager.AUDIO_SESSION_ID_GENERATE)
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForegroundService() calls onStartCommand() even when this service is already
        // running. Retrying here makes the Settings screen's Resume action useful after an
        // effect failed to attach or was released without killing the app process.
        val app = application as EqApplication
        if (!app.eqRepository.uiState.value.engineReady) {
            AppLog.i(TAG, "onStartCommand: retrying engine attach")
            app.eqRepository.attachEngine(AudioManager.AUDIO_SESSION_ID_GENERATE)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        AppLog.i(TAG, "onDestroy: releasing engine")
        val app = application as EqApplication
        app.eqRepository.releaseEngine()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                        CHANNEL_ID,
                        "Aurora EQ",
                        NotificationManager.IMPORTANCE_LOW,
                    )
                    .apply { description = "Keeps the best-effort system-wide audio chain active." }
            manager.createNotificationChannel(channel)
        }

        val contentIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Aurora EQ is active")
            .setContentText(
                "Processing audio (best-effort system-wide). To stop, use Force stop in system Settings."
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }
}
