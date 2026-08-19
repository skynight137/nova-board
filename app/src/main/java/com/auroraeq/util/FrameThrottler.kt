package com.auroraeq.app.util

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * ~1 frame at 60 Hz — a slider drag firing faster than this gains nothing visually or audibly,
 * since nothing on screen or in the effect chain can update faster than the display refresh anyway.
 */
private const val DEFAULT_INTERVAL_MS = 16L

/**
 * Coalesces a rapid burst of calls — e.g. a slider's `onValueChange` firing on every pointer-move
 * event during a drag — down to at most one actual [request] per [intervalMs], while still
 * guaranteeing the *last* call in a burst always runs.
 *
 * Leading-edge: if [intervalMs] has already elapsed since the last run, the action fires
 * immediately — this is what keeps a slow drag feeling live, since most individual moves during a
 * slow drag are already spaced out further than one frame.
 *
 * Trailing-edge: otherwise the action is scheduled for exactly when the window closes, and every
 * new call within that window *replaces* the pending one with the latest action instead of queuing
 * more work — so a fast drag settles on its true final value instead of dropping it.
 *
 * Existed to fix `EqRepository.updateChain` reapplying the entire 31-band x 2-channel signal chain
 * to `DynamicsProcessing`/`Virtualizer` (and rewriting the full chain state to `SharedPreferences`)
 * on every single pointer-move event of every slider drag in the app.
 *
 * All calls must come from the same thread — this app only ever drives slider drags from the main
 * thread, and [Handler] delivers callbacks on [Looper.getMainLooper]; there is no internal
 * synchronization.
 */
class FrameThrottler(private val intervalMs: Long = DEFAULT_INTERVAL_MS) {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var lastRunAt = 0L

    fun request(action: () -> Unit) {
        pending?.let(handler::removeCallbacks)
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastRunAt
        if (elapsed >= intervalMs) {
            lastRunAt = now
            action()
        } else {
            val runnable = Runnable {
                lastRunAt = SystemClock.elapsedRealtime()
                pending = null
                action()
            }
            pending = runnable
            handler.postDelayed(runnable, intervalMs - elapsed)
        }
    }
}
