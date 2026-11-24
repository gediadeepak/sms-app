package com.gediadeepak.smsapp.core

import org.junit.Assert.*
import org.junit.Test

class AiContextPolicyTest {
    private fun message(id: Int, text: String) = Message(id.toString(), text, false, id.toLong(), SimLine.PERSONAL)
    private fun conversation(messages: List<Message>) = Conversation("thread", "Private name", "+441234567890", SimLine.PERSONAL, messages)

    @Test fun `filters codes and financial messages before truncation without dropping ordinary plans`() {
        val input = conversation(listOf(message(1, "Meet at 3 tomorrow?"), message(2, "Your OTP is 1 2 3 4 5 6"),
            message(3, "Code: १२३४५६"), message(4, "Bank account updated"), message(5, "x".repeat(1100) + " password")))
        val result = AiContextPolicy.prepare(input)
        assertEquals(listOf("Meet at 3 tomorrow?"), result.messages.map { it.text })
        assertEquals(4, result.excluded)
        assertFalse(result.messages.toString().contains(input.number))
        assertFalse(result.messages.toString().contains(input.name))
    }

    @Test fun `bounds context and keeps newest eligible text in chronological order`() {
        val result = AiContextPolicy.prepare(conversation((1..60).map { message(it, "a".repeat(1200)) }))
        assertEquals(6, result.messages.size)
        assertEquals(listOf("55", "56", "57", "58", "59", "60"), result.messages.map { it.id })
        assertEquals(6000, result.messages.sumOf { it.text.length })
        assertTrue(result.limited)
    }

    @Test fun `fingerprint changes when source text or direction changes`() {
        val original = conversation(listOf(message(1, "Meet tomorrow?")))
        val changed = original.copy(messages = listOf(original.messages.single().copy(outgoing = true)))
        assertNotEquals(AiContextPolicy.prepare(original).fingerprint, AiContextPolicy.prepare(changed).fingerprint)
        assertTrue(AiContextPolicy.prepare(conversation(listOf(message(1, "OTP: 567890")))).messages.isEmpty())
        assertFalse(Preferences().aiEnabled)
        assertTrue(AiContextPolicy.prepare(original.copy(blocked = true)).messages.isEmpty())
        assertTrue(AiContextPolicy.prepare(original.copy(spam = true)).messages.isEmpty())
        val embeddedSeparator = AiContext(listOf(AiMessage("1", false, "hello\u00002\u0001false\u0001world")), 0, false)
        val separateMessages = AiContext(listOf(AiMessage("1", false, "hello"), AiMessage("2", false, "world")), 0, false)
        assertNotEquals(embeddedSeparator.fingerprint, separateMessages.fingerprint)
    }
}
