package com.novaboard.ime.editing

/**
 * Pure state contract for a bounded, session-scoped repeat action.
 *
 * Android scheduling remains at the service boundary. The token makes a delayed
 * callback harmless after release, direction changes, or input-session changes.
 */
class RepeatController(
    private val initialDelayMs: Long,
    private val intervalMs: Long,
) {
    init {
        require(initialDelayMs >= 0) { "Initial repeat delay must be non-negative" }
        require(intervalMs > 0) { "Repeat interval must be positive" }
    }

    private var generation = 0L
    private var active: RepeatToken? = null

    fun start(sessionId: Long, action: Int): RepeatToken {
        stop()
        val token =
            RepeatToken(
                sessionId = sessionId,
                action = action,
                generation = ++generation,
                delayMs = initialDelayMs,
            )
        active = token
        return token
    }

    fun stop() {
        active = null
    }

    /**
     * Returns the next scheduled callback when [token] is still current.
     *
     * A null result means the callback must not act. A callback for a missing
     * input connection should call [stop] at the service boundary.
     */
    fun next(token: RepeatToken, currentSessionId: Long): RepeatToken? {
        if (active != token || token.sessionId != currentSessionId) {
            if (active == token) active = null
            return null
        }
        val next =
            token.copy(
                delayMs = intervalMs,
            )
        active = next
        return next
    }
}

data class RepeatToken(
    val sessionId: Long,
    val action: Int,
    val generation: Long,
    val delayMs: Long,
)