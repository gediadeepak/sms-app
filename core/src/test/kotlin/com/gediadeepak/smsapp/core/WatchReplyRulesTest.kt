package com.gediadeepak.smsapp.core

import org.junit.Assert.*
import org.junit.Test

class WatchReplyRulesTest {
    @Test fun `watch input rejects empty and oversized replies without truncating`() {
        assertNull(WatchReplyRules.reply(null))
        assertNull(WatchReplyRules.reply(" \n "))
        assertNull(WatchReplyRules.reply("x".repeat(1601)))
        assertEquals("x".repeat(1600), WatchReplyRules.reply("x".repeat(1600)))
    }

    @Test fun `watch drafts preserve existing text and reject combined overflow`() {
        assertEquals("Phone draft\nWatch reply", WatchReplyRules.merge("Phone draft", "Watch reply"))
        assertEquals("Reply", WatchReplyRules.merge("", "Reply"))
        assertEquals(" Reply ", WatchReplyRules.merge("", " Reply "))
        assertNull(WatchReplyRules.merge("p".repeat(1599), "r"))
        assertNull(WatchReplyRules.merge("Keep this", ""))
    }
}
