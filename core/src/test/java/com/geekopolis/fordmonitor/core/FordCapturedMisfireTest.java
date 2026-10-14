package com.geekopolis.fordmonitor.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class FordCapturedMisfireTest {
    @Test public void capturedF150Cylinder5Historical18Current0(){
        String reply="7E8 10 25 46 A6 0B 01 00 12\r7E8 21 00 00 FF FF A6 0C 01\r7E8 22 00 00 00 00 FF FF A6\r7E8 23 80 30 00 00 00 00 3D\r7E8 24 6F A6 81 30 00 00 00\r7E8 25 00 03 D6 00 00 00 00";
        var messages=FrameDecoder.fromEngine(reply,true,"7E8");assertEquals(1,messages.size());
        var results=FordData.misfires(messages.get(0),true,FordData.F150);assertEquals(2,results.size());
        assertEquals(0x0B,results.get(0).tid());assertEquals(18,results.get(0).value(),0);assertEquals("counts",results.get(0).unit());
        assertEquals(0x0C,results.get(1).tid());assertEquals(0,results.get(1).value(),0);assertEquals("counts",results.get(1).unit());
    }
    @Test public void unit01OnlyKnownFordMisfireTestsMapped(){
        byte[] b={(byte)0x46,(byte)0xA6,(byte)0x80,1,0,18,0,0,(byte)0xFF,(byte)0xFF};
        assertTrue(FordData.misfires(b,true,FordData.F150).isEmpty());
    }
}
