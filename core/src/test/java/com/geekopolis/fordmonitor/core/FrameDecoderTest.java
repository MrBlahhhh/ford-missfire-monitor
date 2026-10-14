package com.geekopolis.fordmonitor.core;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class FrameDecoderTest {
    @Test public void canSingleFrame() {
        var m = FrameDecoder.decode("7E8 04 41 0C 1F 40 00 00 00\r>",true);
        assertEquals(1,m.size()); assertArrayEquals(new byte[]{0x41,0x0C,0x1F,0x40},m.get(0).data());
    }
    @Test public void can29Bit() { assertEquals("18DAF110",FrameDecoder.decode("18DAF110 03 41 05 50",true).get(0).ecu()); }
    @Test public void filterTransmission() {
        assertEquals(1,FrameDecoder.fromEngine("7E9 03 41 05 60\r7E8 03 41 05 50",true,"7E8").size());
    }
    @Test public void legacyHeaderAndChecksumRemoved() {
        var m=FrameDecoder.decode("48 6B 10 46 53 01 00 32 03 E8 AA",false);
        assertEquals("10",m.get(0).ecu()); assertEquals(7,m.get(0).data().length);
    }
    @Test public void legacyTransmissionFiltered() { assertTrue(FrameDecoder.fromEngine("48 6B 18 41 05 50 AA",false,"10").isEmpty()); }
    @Test public void canMultiFrame() {
        var m=FrameDecoder.decode("7E8 10 0A 46 A2 0C 24 00 05\r7E8 21 00 00 FF FF 00 00 00",true);
        assertEquals(1,m.size()); assertEquals(10,m.get(0).data().length); assertEquals(0xFF,m.get(0).data()[9]&255);
    }
    @Test public void interleavedEcusAreNotCombined() {
        var m=FrameDecoder.decode("7E8 10 0A 46 A2 0C 24 00 05\r7E9 21 00 00 FF FF 00 00 00",true);
        assertTrue(m.isEmpty());
    }
    @Test public void interleavedCompleteEcusIndependent() {
        var m=FrameDecoder.decode("7E8 10 0A 46 A2 0C 24 00 05\r7E9 03 41 05 50\r7E8 21 00 00 FF FF 00 00 00",true);
        assertEquals(2,m.size()); assertEquals("7E8",m.get(1).ecu());
    }
    @Test public void wrongSequenceRejected() { assertTrue(FrameDecoder.decode("7E8 10 0A 46 A2 0C 24 00 05\r7E8 22 00 00 FF FF",true).isEmpty()); }
    @Test public void missingConsecutiveFrameRejected() { assertTrue(FrameDecoder.decode("7E8 10 0A 46 A2 0C 24 00 05",true).isEmpty()); }
    @Test public void orphanConsecutiveRejected() { assertTrue(FrameDecoder.decode("7E8 21 00 00 FF FF",true).isEmpty()); }
    @Test public void shortSingleFrameRejected() { assertTrue(FrameDecoder.decode("7E8 05 41 0C 20",true).isEmpty()); }
    @Test public void zeroLengthSingleRejected() { assertTrue(FrameDecoder.decode("7E8 00 41 0C",true).isEmpty()); }
    @Test public void shortFirstFrameRejected() { assertTrue(FrameDecoder.decode("7E8 10 0A 46 A2",true).isEmpty()); }
    @Test public void shortLastFrameRejected() { assertTrue(FrameDecoder.decode("7E8 10 0A 46 A2 0C 24 00 05\r7E8 21 00 00",true).isEmpty()); }
    @Test public void noHeaderNotMisidentified() { assertTrue(FrameDecoder.decode("46 A2 0C 24 00 05 00 00 FF FF",true).isEmpty()); }
    @Test public void statusesAndEchoIgnored() { assertTrue(FrameDecoder.decode("06A2\rSEARCHING...\rNO DATA\rOK\r?>",true).isEmpty()); }
    @Test public void invalidHexRejected() { assertTrue(FrameDecoder.decode("7E8 03 41 05 ZZ",true).isEmpty()); }
    @Test public void newFirstFrameResetsOldOne() {
        var m=FrameDecoder.decode("7E8 10 0A 46 A2 0C 24 00 05\r7E8 10 0A 46 A3 0C 24 00 07\r7E8 21 00 00 FF FF",true);
        assertEquals(0xA3,m.get(0).data()[1]&255);
    }
    @Test public void noCrossTransactionState() {
        FrameDecoder.decode("7E8 10 0A 46 A2 0C 24 00 05",true);
        assertTrue(FrameDecoder.decode("7E8 21 00 00 FF FF",true).isEmpty());
    }
    @Test public void ecuAddresses() {
        assertEquals("10",FrameDecoder.engineEcu(false,1)); assertEquals("7E8",FrameDecoder.engineEcu(true,6));
        assertEquals("18DAF110",FrameDecoder.engineEcu(true,7));
    }
    @Test public void malformedFuzzDoesNotThrow() {
        Random r=new Random(7);
        for(int i=0;i<1000;i++) { StringBuilder b=new StringBuilder(); for(int j=0;j<r.nextInt(200);j++) b.append((char)(32+r.nextInt(96))); FrameDecoder.decode(b.toString(),true); FrameDecoder.decode(b.toString(),false); }
    }
}
