package com.novaboard.ime.editing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class RepeatControllerTest {
    private val controller = RepeatController(initialDelayMs = 350L, intervalMs = 70L)

    @Test
    fun startReturnsImmediateTokenWithBoundedInitialDelay() {
        val token = controller.start(sessionId = 4L, action = 22)

        assertEquals(4L, token.sessionId)
        assertEquals(22, token.action)
        assertEquals(350L, token.delayMs)
        assertNotNull(controller.next(token, currentSessionId = 4L))
    }

    @Test
    fun nextCallbackUsesIntervalAndKeepsTheAction() {
        val first = controller.start(sessionId = 4L, action = 22)

        val second = controller.next(first, currentSessionId = 4L)

        assertNotNull(second)
        assertEquals(22, second!!.action)
        assertEquals(70L, second.delayMs)
        assertEquals(first.generation, second.generation)
        assertNotNull(controller.next(second, currentSessionId = 4L))
    }

    @Test
    fun stoppingPreventsFutureCallbacks() {
        val token = controller.start(sessionId = 4L, action = 22)

        controller.stop()

        assertNull(controller.next(token, currentSessionId = 4L))
    }

    @Test
    fun startingAnotherDirectionInvalidatesTheOldToken() {
        val left = controller.start(sessionId = 4L, action = 21)
        val right = controller.start(sessionId = 4L, action = 22)

        assertNull(controller.next(left, currentSessionId = 4L))
        assertEquals(22, controller.next(right, currentSessionId = 4L)!!.action)
    }

    @Test
    fun sessionChangeInvalidatesTheCallback() {
        val token = controller.start(sessionId = 4L, action = 22)

        assertNull(controller.next(token, currentSessionId = 5L))
        assertNull(controller.next(token, currentSessionId = 4L))
    }
}