package com.sandesh.watch;
import org.junit.Test;
import static org.junit.Assert.*;
public class FaceDesignTest {
    @Test public void retroDefaultsMatchAWorkingClockWithoutAlarmClaims() {
        FaceDesign face=FaceDesign.retro(); assertEquals("RETRO",face.style); assertEquals("GREEN",face.color);
        assertTrue(face.date); assertTrue(face.battery); assertTrue(face.seconds);
        assertEquals(12,FaceDesign.displayedHour(0,false)); assertEquals(11,FaceDesign.displayedHour(23,false)); assertEquals(0,FaceDesign.displayedHour(0,true)); assertEquals(23,FaceDesign.displayedHour(23,true));
    }
    @Test public void segmentDigitsHaveCorrectClockShapes() {
        assertEquals(7,Integer.bitCount(FaceDesign.segmentMask('8'))); assertEquals(2,Integer.bitCount(FaceDesign.segmentMask('1')));
        assertEquals(0x3f,FaceDesign.segmentMask('0')); assertEquals(0,FaceDesign.segmentMask(' '));
        for(char digit='0';digit<='9';digit++) assertTrue(FaceDesign.segmentMask(digit)>0);
    }
    @Test public void rejectsInvalidNamesStylesAndColors() {
        for(String name:new String[]{"","  ","a".repeat(25)}) try { new FaceDesign("id",name,"RETRO","GREEN",true,true,true,true); fail(); } catch(IllegalArgumentException expected) {}
        try { new FaceDesign("id","Good name","UNKNOWN","GREEN",true,true,true,true); fail(); } catch(IllegalArgumentException expected) {}
        try { new FaceDesign("id","Good name","RETRO","UNKNOWN",true,true,true,true); fail(); } catch(IllegalArgumentException expected) {}
    }
}
