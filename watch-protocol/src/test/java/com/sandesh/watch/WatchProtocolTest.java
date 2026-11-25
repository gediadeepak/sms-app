package com.sandesh.watch;
import org.junit.Test;
import static org.junit.Assert.*;
public class WatchProtocolTest {
    @Test public void validatesTargetsAndTokens() {
        assertTrue(WatchProtocol.validToken("ac17f374-8574-4e81-bdad-550ce8b01371"));
        assertFalse(WatchProtocol.validToken("anything"));
        assertTrue(WatchProtocol.validThread("123"));
        assertFalse(WatchProtocol.validThread("-1"));
        assertFalse(WatchProtocol.validThread("9223372036854775808"));
        assertFalse(WatchProtocol.validThread("1 OR 1=1"));
        assertFalse(WatchProtocol.validCommand("send"));
        assertTrue(WatchProtocol.validCommand("open"));
    }
    @Test public void preservesWholeEmojiAndBoundsUtf8() {
        assertEquals("Hi😀…",WatchProtocol.clip("Hi😀there",3));
        assertEquals("",WatchProtocol.clip(null,3));
        assertEquals(4,WatchProtocol.bytes("😀",4).length);
        try { WatchProtocol.bytes("😀",3); fail("Should reject bytes beyond limit"); } catch(IllegalArgumentException expected) {}
    }
}
