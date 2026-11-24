package com.gediadeepak.smsapp.core

import org.junit.Assert.*
import org.junit.Test

class ComposerRulesTest {
    @Test fun `paste replaces only the selected range or inserts at the cursor`() {
        assertEquals("Hello there", ComposerRules.insert("Hello friend", "there", 6, 12))
        assertEquals("Hello new friend", ComposerRules.insert("Hello friend", "new ", 6, 6))
        assertNull(ComposerRules.insert("Hello", "x", 4, 2))
    }

    @Test fun `forwarding and AI preserve existing drafts and reject overflow`() {
        assertEquals("Existing draft\nForwarded text", ComposerRules.append("Existing draft", "Forwarded text"))
        assertEquals("AI reply", ComposerRules.append("", "AI reply"))
        assertEquals(1600, ComposerRules.insert("a".repeat(1599), "b")?.length)
        assertNull(ComposerRules.append("a".repeat(1599), "b"))
        assertNull(ComposerRules.insert("untouched", "x".repeat(1600)))
    }

    @Test fun `contact search handles names accents and formatted partial numbers`() {
        val contacts = listOf(PhoneContact("1", "Åsa Patel", "+447700900123", "Mobile"), PhoneContact("2", "Team 2", "+441234560789", "Work"))
        assertEquals(listOf(contacts[0]), searchContacts(contacts, "asa"))
        assertEquals(listOf(contacts[0]), searchContacts(contacts, "7700 900"))
        assertEquals(listOf(contacts[1]), searchContacts(contacts, "team 2"))
        assertEquals(contacts, searchContacts(contacts, ""))
    }

    @Test fun `contact names change display and search without changing sender identity or AI input`() {
        val c = Conversation("thread", "+447700900123", "+447700900123", SimLine.PERSONAL,
            listOf(Message("1", "Meet tomorrow?", false, 1, SimLine.PERSONAL)), contactName = "Private Contact")
        assertEquals("Private Contact", c.displayName)
        assertEquals(listOf(c), filterConversations(listOf(c), Folder.INBOX, null, "private contact"))
        assertEquals(c.number, c.copy(contactName = null).displayName)
        assertFalse(AiContextPolicy.prepare(c).messages.toString().contains(c.contactName!!))
    }

    @Test fun `contact matches do not confuse shared suffixes or country codes`() {
        assertTrue(sameContactNumber("07700 900123", "+447700900123", "+44 7700 900123", "+447700900123"))
        assertFalse(sameContactNumber("+447700900123", "+447700900123", "+917700900123", "+917700900123"))
        assertFalse(sameContactNumber("7700900123", null, "900123", null))
        assertTrue(sameContactNumber("07700 900123", null, "07700900123", null))
        assertFalse(sameContactNumber("VM-611123-P", null, "611123", null))
    }
}
