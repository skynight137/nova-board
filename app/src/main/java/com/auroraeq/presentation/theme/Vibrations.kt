package com.auroraeq.app.presentation.theme

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Short, fixed-duration "tick" used for slider drags and +/- nudge presses. */
private const val TICK_DURATION_MS = 15L

/**
 * Fires a short vibration tick through the platform [Vibrator] API directly, instead of Compose's
 * `LocalHapticFeedback`/`View.performHapticFeedback`.
 *
 * Two separate real-device reports confirmed the Compose haptic-feedback route is unreliable here:
 * 1. `HapticFeedbackType.SegmentTick`/`SegmentFrequentTick` map to platform constants built on
 *    `VibrationEffect` composition primitives, which a large share of real vibrator hardware
 *    silently no-ops on.
 * 2. Switching to `TextHandleMove` (a plain, broadly-supported constant) was *still* not felt on a
 *    later report. `View.performHapticFeedback` also respects the device's own "Touch feedback" /
 *    vibrate-on-tap system setting for every constant unless the caller passes
 *    `FLAG_IGNORE_GLOBAL_SETTING` — a flag Compose's `HapticFeedback` interface never exposes. If a
 *    user has that OS setting off (off by default on several OEM skins), every Compose/View
 *    haptic-feedback call is a silent no-op regardless of which constant is used, with no way for
 *    the app to override it through the Compose API.
 *
 * A direct `Vibrator.vibrate(VibrationEffect)` call is a general-purpose vibration, not a UI
 * "haptic feedback" semantic — it bypasses both failure modes above. It only depends on the
 * `VIBRATE` permission (a normal, install-time-granted permission, no runtime prompt) and the
 * device actually having a vibrator (silently does nothing without one, which is correct).
 */
fun vibrateTick(context: Context) {
    val vibrator = context.vibratorOrNull() ?: return
    if (!vibrator.hasVibrator()) return
    vibrator.vibrate(
        VibrationEffect.createOneShot(TICK_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
    )
}

private fun Context.vibratorOrNull(): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
