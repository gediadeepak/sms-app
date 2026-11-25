package com.gediadeepak.smsapp.core

import org.junit.Assert.*
import org.junit.Test

class SmsPagingTest {
    @Test fun `page cursor uses date and numeric ID to resolve equal timestamps`() {
        assertArrayEquals(arrayOf("123", "123", "9"), SmsPaging.before(SmsPageKey(123, 9)))
        assertEquals(SmsPageKey(123, 9), SmsPaging.key(Message("9", "", false, 123, SimLine.UNKNOWN)))
        assertFalse(SmsPage(emptyList<Message>(), null).hasMore)
        assertTrue(SmsPage(emptyList<Message>(), SmsPageKey(123, 9)).hasMore)
    }

    @Test fun `overlapping history pages retain current versions without duplicates and sort numeric IDs`() {
        val a = Message("9", "old", false, 123, SimLine.UNKNOWN)
        val b = Message("10", "same time", false, 123, SimLine.UNKNOWN)
        val c = Message("11", "latest", false, 200, SimLine.UNKNOWN)
        val updated = a.copy(text = "updated")
        assertEquals(listOf(updated, b, c), SmsPaging.merge(listOf(b, a), listOf(updated, c)))
    }

    @Test fun `AI context stays stable when older pages arrive and reports omitted history`() {
        val recent = (51..100).map { Message(it.toString(), "Hello there", false, it.toLong(), SimLine.UNKNOWN) }
        val c = Conversation("1", "Person", "+447700900123", SimLine.UNKNOWN, recent, hasOlderMessages = true)
        val input = AiContextPolicy.prepare(c)
        val older = (1..50).map { Message(it.toString(), "An earlier note", false, it.toLong(), SimLine.UNKNOWN) }
        assertTrue(input.limited)
        assertEquals(input.fingerprint, AiContextPolicy.prepare(c.copy(messages = older + recent, hasOlderMessages = false)).fingerprint)
    }
}
