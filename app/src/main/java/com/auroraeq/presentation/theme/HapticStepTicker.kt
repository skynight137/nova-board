package com.auroraeq.app.presentation.theme

import kotlin.math.roundToInt

/**
 * Coalesces a continuous drag's per-pointer-move callbacks into a haptic "tick" fired only when the
 * value crosses into a new discrete step, not on every sub-pixel pointer-move — otherwise a fast
 * drag would feel like a continuous buzz instead of a tactile ladder, and a slow one would barely
 * tick at all relative to how far the thumb has moved.
 *
 * This shares the "how often does onValueChange fire" concern with `FrameThrottler` (see
 * `slider-drag-throttling.md`), but is keyed on value rather than elapsed time: a throttle would
 * skip ticks during a slow, deliberate drag, while this fires exactly once per step regardless of
 * drag speed.
 *
 * The actual vibration is deliberately NOT wired through Compose's
 * `LocalHapticFeedback`/`HapticFeedbackType` — see `vibrateTick` in `Vibrations.kt` for why that
 * route proved unreliable across two separate real-device reports. Callers pass a plain `tick`
 * lambda (typically `{ vibrateTick(context) }`) so this class stays focused on the value-keyed
 * coalescing logic alone.
 */
class HapticStepTicker(private val steps: Int = 40) {
    private var lastStep: Int? = null

    /**
     * [fraction] is the control's normalized 0f..1f position along its range. Invokes [tick] only
     * the first time a drag lands on a new step.
     */
    fun onFractionChanged(fraction: Float, tick: () -> Unit) {
        val step = (fraction.coerceIn(0f, 1f) * steps).roundToInt()
        if (step != lastStep) {
            lastStep = step
            tick()
        }
    }
}
