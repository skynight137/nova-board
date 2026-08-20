package com.novaboard.ime.gesture

enum class GesturePathResult {
    TAP,
    CANDIDATE,
    CANCELLED,
}

data class GestureThresholds(
    val minimumDistance: Float = 18f,
    val minimumLetters: Int = 2,
) {
    init {
        require(minimumDistance > 0f)
        require(minimumLetters >= 2)
    }
}

fun classifyGesturePath(
    distance: Float,
    crossedLetterCount: Int,
    cancelled: Boolean,
    thresholds: GestureThresholds = GestureThresholds(),
): GesturePathResult =
    when {
        cancelled -> GesturePathResult.CANCELLED
        distance < thresholds.minimumDistance -> GesturePathResult.TAP
        crossedLetterCount < thresholds.minimumLetters -> GesturePathResult.TAP
        else -> GesturePathResult.CANDIDATE
    }

data class RepeatTiming(
    val initialDelayMs: Long = 350L,
    val intervalMs: Long = 70L,
) {
    init {
        require(initialDelayMs in 150L..1_000L)
        require(intervalMs in 40L..250L)
    }
}