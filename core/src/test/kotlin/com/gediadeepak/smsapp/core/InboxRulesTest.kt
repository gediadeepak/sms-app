package com.gediadeepak.smsapp.core

import org.junit.Assert.*
import org.junit.Test

class InboxRulesTest {
    private val items = DemoData.conversations(1_800_000_000_000L)

    @Test fun `archive never appears in inbox unread or starred views`() {
        val archived = items.first().copy(archived = true, starred = true, unread = 3)
        listOf(Folder.INBOX, Folder.UNREAD, Folder.STARRED).forEach { folder ->
            assertTrue(filterConversations(listOf(archived), folder, null, "").isEmpty())
        }
        assertEquals(listOf(archived), filterConversations(listOf(archived), Folder.ARCHIVED, null, ""))
    }

    @Test fun `search checks message text and respects both folder and line`() {
        assertEquals("alex", filterConversations(items, Folder.UNREAD, SimLine.WORK, "DESIGNS").single().id)
        assertTrue(filterConversations(items, Folder.UNREAD, SimLine.PERSONAL, "DESIGNS").isEmpty())
        assertEquals("maya", filterConversations(items, Folder.STARRED, null, "900142").single().id)
    }

    @Test fun `new drafts sort first and recent messages follow`() {
        val draft = Conversation("draft", "New person", "+44 7700 900999", SimLine.PERSONAL)
        val filtered = filterConversations(items + draft, Folder.INBOX, null, "")
        assertEquals("draft", filtered[0].id)
        assertEquals("maya", filtered[1].id)
    }

    @Test fun `phone validation rejects missing digits and excessive length`() {
        assertTrue(validNumber("+44 (7700) 900123"))
        assertTrue(validNumber("07700 900123"))
        assertFalse(validNumber("------"))
        assertFalse(validNumber("+1234567890123456"))
        assertFalse(validNumber("call-me"))
        assertEquals(normaliseNumber("+44 (7700) 900123"), normaliseNumber("447700900123"))
    }

    @Test fun `AI context preparation does not modify message history`() {
        val original = items.first()
        val before = original.messages.toList()
        AiContextPolicy.prepare(original)
        assertEquals(before, original.messages)
    }
}
