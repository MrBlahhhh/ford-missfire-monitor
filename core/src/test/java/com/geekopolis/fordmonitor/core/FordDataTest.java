package com.geekopolis.fordmonitor.core;
import org.junit.Test;
import static org.junit.Assert.*;
public class FordDataTest {
    private static byte[] hex(String s) { String[] parts=s.split(" "); byte[] b=new byte[parts.length]; for(int i=0;i<b.length;i++) b[i]=(byte)Integer.parseInt(parts[i],16); return b; }
    @Test public void v8Count() { var m=FordData.misfires(hex("46 A2 0C 24 00 2A 00 00 FF FF"),true,FordData.F150).get(0); assertEquals(1,m.cylinder()); assertEquals("counts",m.unit()); assertEquals(42,m.value(),0); assertTrue(m.evaluated()); }
    @Test public void v10CylinderTen() { assertEquals(10,FordData.misfires(hex("46 AB 0C 24 00 07 00 00 FF FF"),true,FordData.V10).get(0).cylinder()); }
    @Test public void v8DoesNotShowV10Cylinder() { assertTrue(FordData.misfires(hex("46 AB 0C 24 00 07 00 00 FF FF"),true,FordData.F150).isEmpty()); }
    @Test public void weightedAverageSeparate() { var m=FordData.misfires(hex("46 A2 0B 24 00 05 00 00 FF FF"),true,FordData.V10).get(0); assertEquals("10-cycle weighted average",m.kind()); }
    @Test public void multipleTestsInOneMessage() { assertEquals(2,FordData.misfires(hex("46 A2 0B 24 00 05 00 00 FF FF A2 0C 24 00 08 00 00 FF FF"),true,FordData.F150).size()); }
    @Test public void zeroLimitsNotEvaluated() { assertFalse(FordData.misfires(hex("46 A2 0C 24 00 00 00 00 00 00"),true,FordData.F150).get(0).evaluated()); }
    @Test public void validZeroIsDifferent() { assertTrue(FordData.misfires(hex("46 A2 0C 24 00 00 00 00 FF FF"),true,FordData.F150).get(0).evaluated()); }
    @Test public void unknownUnitsNotCounts() { var m=FordData.misfires(hex("46 A2 0C 0B 00 20 00 00 FF FF"),true,FordData.F150).get(0); assertEquals("raw",m.unit()); assertFalse(m.evaluated()); assertNull(m.maximum()); }
    @Test public void ecuLimitNotInventedThreshold() { assertTrue(FordData.misfires(hex("46 A2 0C 24 00 15 00 00 00 14"),true,FordData.F150).get(0).outsideLimit()); }
    @Test public void ecuLimitEqualityPasses() { assertFalse(FordData.misfires(hex("46 A2 0C 24 00 14 00 00 00 14"),true,FordData.F150).get(0).outsideLimit()); }
    @Test public void legacyPercentNotCount() { var m=FordData.misfires(hex("46 53 02 03 E8 07 D0"),false,FordData.F150).get(0); assertEquals(2,m.cylinder()); assertEquals("%",m.unit()); assertEquals(.015,m.value(),.0000001); assertEquals(.030,m.maximum(),.0000001); assertNull(m.minimum()); }
    @Test public void legacyMinimumFlag() { var m=FordData.misfires(hex("46 53 82 00 00 03 E8"),false,FordData.F150).get(0); assertEquals(2,m.cylinder()); assertNull(m.maximum()); assertTrue(m.outsideLimit()); }
    @Test public void legacyNotAppliedTo2017() { assertTrue(FordData.misfires(hex("46 53 02 03 E8 07 D0"),false,FordData.V10).isEmpty()); }
    @Test public void engineTotalIsNotCylinderOne() { assertTrue(FordData.misfires(hex("46 53 00 03 E8 07 D0"),false,FordData.F150).isEmpty()); }
    @Test public void truncatedCanRejected() { assertTrue(FordData.misfires(hex("46 A2 0C 24 00 20 00 00 FF"),true,FordData.F150).isEmpty()); }
    @Test public void unrelatedTestsIgnored() { assertTrue(FordData.misfires(hex("46 A2 80 24 00 20 00 00 FF FF"),true,FordData.F150).isEmpty()); }
    @Test public void negativeResponseNotCount() { assertTrue(FordData.misfires(hex("7F 06 12"),true,FordData.F150).isEmpty()); }
    @Test public void rpm() { assertEquals(2000,FordData.live(hex("41 0C 1F 40"),12),0); }
    @Test public void temperature() { assertEquals(40,FordData.live(hex("41 05 50"),5),0); }
    @Test public void trims() { assertEquals(-25,FordData.live(hex("41 06 60"),6),0); assertEquals(0,FordData.live(hex("41 09 80"),9),0); }
    @Test public void wrongPidNotUsed() { assertNull(FordData.live(hex("41 05 50"),12)); }
    @Test public void shortRpmNotZero() { assertNull(FordData.live(hex("41 0C 50"),12)); }
    @Test public void bitmapSupport() { byte[] b=hex("41 00 80 00 00 01"); assertTrue(FordData.supports(b,1,0,1)); assertTrue(FordData.supports(b,1,0,32)); assertFalse(FordData.supports(b,1,0,2)); }
    @Test public void wrongBitmapRejected() { assertFalse(FordData.supports(hex("46 00 FF FF FF FF"),1,0,12)); }
    @Test public void readinessPending() { assertTrue(FordData.readiness(hex("41 01 82 11 00 00")).contains("not ready")); }
    @Test public void readinessUnsupported() { assertTrue(FordData.readiness(hex("41 01 00 00 00 00")).contains("not supported")); }
    @Test public void readinessReadyNotActive() { assertTrue(FordData.readiness(hex("41 01 00 01 00 00")).contains("not proof")); }
    @Test public void canDtcCountByte() { assertEquals("P0310",FordData.dtcs(hex("43 01 03 10"),3,true).get(0)); }
    @Test public void legacyDtcNoCountByte() { assertEquals("P0308",FordData.dtcs(hex("43 03 08 00 00 00 00"),3,false).get(0)); }
    @Test public void unknownAndMalformedDtcNotNone() { assertFalse(FordData.validDtcReply(hex("7F 03 12"),3,true)); assertFalse(FordData.validDtcReply(hex("43 02 03 10"),3,true)); }
    @Test public void zeroDtcValid() { assertTrue(FordData.validDtcReply(hex("43 00"),3,true)); assertTrue(FordData.dtcs(hex("43 00"),3,true).isEmpty()); }
    @Test public void bCUCodes() { assertEquals("B1234",FordData.dtcs(hex("43 01 92 34"),3,true).get(0)); assertEquals("U0100",FordData.dtcs(hex("43 01 C1 00"),3,true).get(0)); }
    @Test public void autoProtocol() { assertEquals(6,FordData.protocol("A6\r\n")); assertEquals(1,FordData.protocol("1")); }
    @Test(expected=IllegalArgumentException.class) public void noProtocolNotGuessed() { FordData.protocol("A0"); }
    @Test public void vinStrictLength() { byte[] b=new byte[20]; b[0]=0x49;b[1]=2;b[2]=1;System.arraycopy("1FTRW14504FA00001".getBytes(java.nio.charset.StandardCharsets.US_ASCII),0,b,3,17); assertEquals("1FTRW14504FA00001",FordData.vin(b)); b[3]='I';assertNull(FordData.vin(b)); }
}
