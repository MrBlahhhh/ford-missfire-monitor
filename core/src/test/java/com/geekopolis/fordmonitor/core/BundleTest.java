package com.geekopolis.fordmonitor.core;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class BundleTest {
    private static byte[] hex(String s) { String[] parts=s.split(" "); byte[] b=new byte[parts.length];for(int i=0;i<b.length;i++) b[i]=(byte)Integer.parseInt(parts[i],16);return b; }
    @Test public void mixedWidthPids() {var m=FordData.liveBundle(hex("41 0C 1F 40 05 50 06 80"),Set.of(12,5,6));assertEquals(3,m.size());assertEquals(2000,m.get(12),0);assertEquals(40,m.get(5),0);}
    @Test public void reversedResponseOrder() {var m=FordData.liveBundle(hex("41 06 80 05 50 0C 1F 40"),Set.of(12,5,6));assertEquals(2000,m.get(12),0);assertEquals(0,m.get(6),0);}
    @Test public void missingPidRemainsMissing() {var m=FordData.liveBundle(hex("41 0C 1F 40"),Set.of(12,5));assertFalse(m.keySet().equals(Set.of(12,5)));}
    @Test public void unknownWidthRejectsEntireMessage() {assertTrue(FordData.liveBundle(hex("41 0C 1F 40 42 50 50"),Set.of(12,0x42)).isEmpty());}
    @Test public void unrequestedPidRejectsEntireMessage() {assertTrue(FordData.liveBundle(hex("41 0C 1F 40 05 50"),Set.of(12)).isEmpty());}
    @Test public void truncatedTailRejectsEntireMessage() {assertTrue(FordData.liveBundle(hex("41 05 50 0C 1F"),Set.of(12,5)).isEmpty());}
    @Test public void duplicatePidRejected() {assertTrue(FordData.liveBundle(hex("41 05 50 05 51"),Set.of(5)).isEmpty());}
    @Test public void negativeResponseRejected() {assertTrue(FordData.liveBundle(hex("7F 01 12"),Set.of(5)).isEmpty());}
    @Test public void sixPidsAllowedSevenRejected() {assertTrue(PromptReader.allowed("010C0506070809"));assertFalse(PromptReader.allowed("010C050607080904"));}
    @Test public void actualMisfireMonitorsNotIllegallyBundled() {assertFalse(PromptReader.allowed("06A2A3"));assertTrue(PromptReader.allowed("06A2"));}
    @Test public void bundledFrameReassemblyAndParsing() {
        var messages=FrameDecoder.fromEngine("7E8 10 0E 41 0C 1F 40 05 50\r7E8 21 06 80 07 90 08 60 09\r7E8 22 A0 00 00 00 00 00 00",true,"7E8");
        var m=FordData.liveBundle(messages.get(0),Set.of(12,5,6,7,8,9));assertEquals(6,m.size());assertEquals(25,m.get(9),0);
    }
}
