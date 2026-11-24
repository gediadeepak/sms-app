package com.gediadeepak.smsapp.core

import org.junit.Assert.*
import org.junit.Test

class SmsRulesTest {
    @Test fun `appearance choices preserve saved names and default to blue and white`() {
        assertEquals(ThemeMode.BLUE, Preferences().theme)
        listOf("BLUE", "GREEN", "DARK").forEach { assertEquals(it, ThemeMode.valueOf(it).name) }
        assertEquals(listOf(ThemeMode.BLUE, ThemeMode.GREEN, ThemeMode.DARK, ThemeMode.PURPLE, ThemeMode.ROSE, ThemeMode.AMBER), ThemeMode.entries.toList())
    }
    @Test fun `dual SIM requires a choice when no default exists`() {
        assertNull(resolveSendingSubscription(null, listOf(7, 18)))
        assertEquals(18, resolveSendingSubscription(18, listOf(7, 18)))
    }
    @Test fun `removed default never falls back to the remaining SIM`() {
        assertNull(resolveSendingSubscription(18, listOf(7)))
        assertNull(resolveSendingSubscription(18, emptyList()))
        assertEquals(7, resolveSendingSubscription(null, listOf(7)))
    }
    @Test fun `partial multipart submission is still pending`() {
        assertEquals(Delivery.SENDING, aggregateDelivery(listOf(PartResult(1), PartResult())))
        assertEquals(Delivery.SENT, aggregateDelivery(listOf(PartResult(1), PartResult(1))))
    }
    @Test fun `one part failure cannot become delivered when other reports arrive`() {
        assertEquals(Delivery.FAILED, aggregateDelivery(listOf(PartResult(-1), PartResult(1, 1))))
        assertEquals(Delivery.FAILED, aggregateDelivery(listOf(PartResult(1, 1), PartResult(-1, 1))))
    }
    @Test fun `delivery requires a confirmed report for every part`() {
        assertEquals(Delivery.SENT, aggregateDelivery(listOf(PartResult(1, 1), PartResult(1))))
        assertEquals(Delivery.DELIVERED, aggregateDelivery(listOf(PartResult(1, 1), PartResult(1, 1))))
        assertEquals(Delivery.UNDELIVERED, aggregateDelivery(listOf(PartResult(1, -1), PartResult(1))))
        assertEquals(Delivery.UNKNOWN, aggregateDelivery(emptyList()))
    }
    @Test fun `unknown historical subscription is never called SIM one`() {
        assertEquals("Unknown SIM", Preferences().label(SimLine.UNKNOWN))
    }
}
