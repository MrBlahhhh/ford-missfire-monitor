package com.geekopolis.fordmonitor;
import com.geekopolis.fordmonitor.core.FordData;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.nio.file.Files;
import static org.junit.Assert.*;
public class SessionLogTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    @Test public void localUniqueSessionNames() throws Exception {
        try(var a=new SessionLog(temp.getRoot(),FordData.F150); var b=new SessionLog(temp.getRoot(),FordData.F150)) {assertNotEquals(a.csv,b.csv);assertNotEquals(a.raw,b.raw);}
    }
    @Test public void csvQuotesAndCanonicalUnits() throws Exception {
        try(var a=new SessionLog(temp.getRoot(),FordData.V10)) {
            a.live("7E8","0105",90.0,"C","comma, quote\" line");
            String csv=Files.readString(a.csv.toPath());assertTrue(csv.contains("\"90.0\",\"C\""));assertTrue(csv.contains("comma, quote\"\" line"));assertTrue(csv.contains("\"v10\",\"7E8\""));
        }
    }
    @Test public void saturatedSampleLogDoesNotStopMonitoring() throws Exception {
        try(var a=new SessionLog(temp.getRoot(),FordData.F150)) {
            var field=SessionLog.class.getDeclaredField("sampleBytes");field.setAccessible(true);field.setLong(a,8*1024*1024-1);
            long before=a.csv.length();a.live("7E8","010C",2000.0,"rpm","");assertTrue(a.limitReached());assertEquals(before,a.csv.length());
            a.raw("010C","7E8 04 41 0C 1F 40");assertTrue(a.raw.length()>0);
        }
    }
    @Test public void saturatedDebugLogDoesNotStopMonitoring() throws Exception {
        try(var a=new SessionLog(temp.getRoot(),FordData.F150)) {
            var field=SessionLog.class.getDeclaredField("rawBytes");field.setAccessible(true);field.setLong(a,8*1024*1024-1);
            a.raw("010C","7E8 04 41 0C 1F 40");assertTrue(a.limitReached());assertEquals(0,a.raw.length());
            a.live("7E8","010C",2000.0,"rpm","");assertTrue(Files.readString(a.csv.toPath()).contains("2000.0"));
        }
    }
    @Test public void retentionKeepsOnlyLast20OwnSessions() throws Exception {
        var unrelated=temp.newFile("leave-me.txt");
        for(int i=0;i<22;i++)try(var a=new SessionLog(temp.getRoot(),FordData.F150)){a.raw("ATI","ELM327");}
        assertTrue(unrelated.isFile());assertEquals(20,temp.getRoot().listFiles((d,n)->n.startsWith("session-")&&n.endsWith(".csv")).length);
        assertEquals(20,temp.getRoot().listFiles((d,n)->n.startsWith("session-")&&n.endsWith(".txt")).length);
    }
}
