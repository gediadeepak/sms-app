package com.gediadeepak.smsapp.core

import org.junit.Assert.*
import org.junit.Test

class SpamRulesTest {
    @Test fun `blocked and spam history is recoverable but excluded from every ordinary folder`() {
        val message = Message("sms", "Keep this message", false, 100L, SimLine.WORK)
        val spam = Conversation("thread", "Sender", "VM-BANK1", SimLine.WORK, listOf(message), unread = 2, starred = true, archived = true, spam = true)
        val blocked = spam.copy(id = "blocked", spam = false, blocked = true)
        listOf(Folder.INBOX, Folder.UNREAD, Folder.STARRED, Folder.ARCHIVED).forEach { folder ->
            assertTrue(filterConversations(listOf(spam, blocked), folder, null, "").isEmpty())
        }
        assertEquals(2, filterConversations(listOf(spam, blocked), Folder.SPAM, SimLine.WORK, "keep").size)
        assertTrue(filterConversations(listOf(spam), Folder.SPAM, SimLine.PERSONAL, "").isEmpty())
        val restored = spam.copy(spam = false, archived = false)
        assertEquals(listOf(message), filterConversations(listOf(restored), Folder.INBOX, null, "").single().messages)
    }

    @Test fun `formatting variants match without merging unrelated sender IDs or country codes`() {
        assertEquals(spamSenderKey("+44 (7700) 900123"), spamSenderKey("447700900123"))
        assertEquals(spamSenderKey(" vm-bank1 "), spamSenderKey("VM-BANK1"))
        assertNotEquals(spamSenderKey("VM-BANK1"), spamSenderKey("VM-SHOP1"))
        assertNotEquals(spamSenderKey("VM-BANK1"), spamSenderKey("1"))
        assertNotEquals(spamSenderKey("07700900123"), spamSenderKey("447700900123"))
    }
}
