package com.gediadeepak.smsapp.core

import org.junit.Assert.*
import org.junit.Test

class SendPolicyTest {
    @Test fun holdsImmediateSendButPreservesAnExplicitSchedule() {
        assertEquals(11_000L,SendPolicy.due(1_000,null,true))
        assertEquals(1_000L,SendPolicy.due(1_000,null,false))
        assertEquals(20_000L,SendPolicy.due(1_000,20_000,true))
    }
    @Test fun refusesPastAndVeryDistantSchedulesAndFlagsStaleWork() {
        for(stamp in listOf(999L,1_000L,1_000L+367L*24*60*60*1000)) {
            try {SendPolicy.due(1_000,stamp,true);fail("Invalid schedule accepted")} catch(expected:IllegalArgumentException) {}
        }
        assertFalse(SendPolicy.expired(1_000+SendPolicy.MAX_LATE_MILLIS,1_000))
        assertTrue(SendPolicy.expired(1_001+SendPolicy.MAX_LATE_MILLIS,1_000))
    }
    @Test fun groupsOnlyKnownSenderIdentitiesWithoutGuessingFromMessageBodies() {
        assertEquals("Amazon",BusinessGroups.suggestion("VM-AMAZON-P"))
        assertEquals("HDFC Bank",BusinessGroups.suggestion("VM-HDFCBN-P"))
        assertNull(BusinessGroups.suggestion("+447700900123"))
        assertNull(BusinessGroups.suggestion("AMAZON-FAKE"))
        assertEquals("My deliveries",BusinessGroups.label("My deliveries","VM-AMAZON-P"))
    }
    @Test fun oldPinnedChatStaysAboveRecentChatWithoutExposingSpam() {
        val old=Conversation("old","Old","111111",SimLine.PERSONAL,listOf(Message("1","old",false,1,SimLine.PERSONAL)),pinned=true)
        val recent=old.copy(id="new",pinned=false,messages=listOf(Message("2","new",false,999,SimLine.PERSONAL)))
        val spam=old.copy(id="spam",spam=true)
        assertEquals(listOf("old","new"),filterConversations(listOf(recent,spam,old),Folder.INBOX,null,"").map {it.id})
    }
}
