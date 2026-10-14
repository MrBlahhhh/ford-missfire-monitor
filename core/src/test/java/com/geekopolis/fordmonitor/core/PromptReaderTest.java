package com.geekopolis.fordmonitor.core;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
public class PromptReaderTest {
    @Test public void echoOnlyRemovedOnExactLine() throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();
        var r=new PromptReader(new ByteArrayInputStream("0100\r7E8 06 41 00 01 00 00 00\r>".getBytes(StandardCharsets.US_ASCII)),output,()->{});
        assertEquals("7E8 06 41 00 01 00 00 00",r.exchange("0100",100));
        assertEquals("0100\r",output.toString(StandardCharsets.US_ASCII));
    }
    @Test public void embeddedCommandBytesPreserved() throws Exception {
        var r=new PromptReader(new ByteArrayInputStream("7E8 06 41 00 0100 FF FF\r>".getBytes(StandardCharsets.US_ASCII)),new ByteArrayOutputStream(),()->{});
        assertTrue(r.exchange("0100",100).contains("0100"));
    }
    @Test public void missingPromptClosesChannel() throws Exception {
        AtomicBoolean closed=new AtomicBoolean(); var r=new PromptReader(new ByteArrayInputStream("NO DATA".getBytes(StandardCharsets.US_ASCII)),new ByteArrayOutputStream(),()->closed.set(true));
        try {r.exchange("06A2",20); fail();} catch(IOException expected) {assertTrue(closed.get());}
    }
    @Test public void oversizeClosesChannel() throws Exception {
        AtomicBoolean closed=new AtomicBoolean(); var r=new PromptReader(new ByteArrayInputStream(("X".repeat(65537)+">").getBytes(StandardCharsets.US_ASCII)),new ByteArrayOutputStream(),()->closed.set(true));
        try {r.exchange("06A2",2000); fail();} catch(IOException expected) {assertTrue(closed.get());}
    }
    @Test public void clearingAndWritesRejected() {
        for(String s:new String[]{"04","220000","2E123400","31FF","ATSH7E0","010C\r04","ATZ\r04"}) assertFalse(s,PromptReader.allowed(s));
    }
    @Test public void requestedReadCommandsAllowed() {
        for(String s:new String[]{"0100","0101","010C","0653","06A2","06AA","06AB","03","07","0A","0902","ATH1","ATCAF1"}) assertTrue(s,PromptReader.allowed(s));
    }
    @Test public void forbiddenCommandNeverWritten() throws Exception {
        ByteArrayOutputStream output=new ByteArrayOutputStream();var r=new PromptReader(new ByteArrayInputStream(new byte[0]),output,()->{});
        try {r.exchange("04",10);fail();}catch(IOException expected){assertEquals(0,output.size());}
    }
    @Test public void pendingPromptRepliesRemainSeparate() throws Exception {
        var r=new PromptReader(new ByteArrayInputStream("OK>ELM327 v2.2>".getBytes(StandardCharsets.US_ASCII)),new ByteArrayOutputStream(),()->{});
        assertEquals("OK",r.exchange("ATE0",100)); assertEquals("ELM327 v2.2",r.exchange("ATI",100));
    }
}
