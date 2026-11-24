package com.gediadeepak.smsapp.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadoutRouteGuardTest {
    @Test fun `speech stays muted until verified and cannot resume after disconnect`() {
        val guard = ReadoutRouteGuard(7)
        assertEquals(ReadoutRouteDecision.WAIT_MUTED, guard.inspect(emptySet(), 7))
        assertEquals(ReadoutRouteDecision.PLAY, guard.inspect(setOf(7), 7))
        assertEquals(ReadoutRouteDecision.STOP, guard.inspect(emptySet(), 7))
        assertEquals(ReadoutRouteDecision.STOP, guard.inspect(setOf(7), 7))
    }
    @Test fun `speaker fallback and simultaneous secondary output never become audible`() {
        val fallback = ReadoutRouteGuard(7)
        assertEquals(ReadoutRouteDecision.STOP, fallback.inspect(setOf(1), 7))
        assertEquals(ReadoutRouteDecision.STOP, fallback.inspect(setOf(7), 7))
        val dual = ReadoutRouteGuard(7)
        assertEquals(ReadoutRouteDecision.PLAY, dual.inspect(setOf(7), 7))
        assertEquals(ReadoutRouteDecision.STOP, dual.inspect(setOf(7, 8), 7))
    }
    @Test fun `changing the chosen device cancels the current readout even if its old route is live`() {
        val guard = ReadoutRouteGuard(7)
        assertEquals(ReadoutRouteDecision.PLAY, guard.inspect(setOf(7), 7))
        assertEquals(ReadoutRouteDecision.STOP, guard.inspect(setOf(7), 8))
        assertEquals(ReadoutRouteDecision.STOP, guard.inspect(setOf(7), 7))
    }
}
