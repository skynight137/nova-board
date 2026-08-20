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

/**
 * Removes consecutive repeats caused by finger jitter and rejects paths that cannot represent a word.
 */
fun normalizeGestureLetters(labels: List<String>): String? {
    val normalized = buildList {
        labels.forEach { label ->
            val letter = label.singleOrNull()?.lowercaseChar()
            if (letter == null || !letter.isLetter()) return null
            if (lastOrNull() != letter) add(letter)
        }
    }
    return normalized.takeIf { it.size >= 2 }?.joinToString("")
}

fun recognizeGestureWord(
    labels: List<String>,
    distance: Float,
    cancelled: Boolean,
    thresholds: GestureThresholds = GestureThresholds(),
): String? {
    if (classifyGesturePath(distance, labels.distinct().size, cancelled, thresholds) !=
        GesturePathResult.CANDIDATE
    ) {
        return null
    }
    return normalizeGestureLetters(labels)
}