package com.geekopolis.fordmonitor.core;
import org.junit.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.Assert.*;
public class ProtocolNegotiatorTest {
    private static final String CAN="7E8 06 41 00 BE 3F B8 13 00";
    private static final String LEGACY="48 6B 10 41 00 BE 3F B8 13 AA";
    private static final class Script implements ProtocolNegotiator.Transport {
        final List<String> commands=new ArrayList<>();final String[] replies;int i;
        Script(String... replies){this.replies=replies;}
        public String exchange(String command,long timeout) throws IOException {
            commands.add(command);if(i>=replies.length)throw new IOException("Unexpected extra command: "+command);return replies[i++];
        }
    }
    @Test public void autoSuccessNoExtraProbe() throws Exception {
        Script s=new Script("OK",CAN,"A6");var r=ProtocolNegotiator.negotiate(FordData.F150,s,x->{});
        assertEquals(6,r.protocol());assertEquals(3,s.commands.size());assertEquals("ATTP0",s.commands.get(0));
    }
    @Test public void submittedA0FailureFallsBackToCan() throws Exception {
        Script s=new Script("OK","SEARCHING...\rUNABLE TO CONNECT","A0","OK",CAN,"6");
        var r=ProtocolNegotiator.negotiate(FordData.F150,s,x->{});assertEquals(6,r.protocol());assertEquals("ATTP6",s.commands.get(3));
    }
    @Test public void oldFordPwmFallback() throws Exception {
        Script s=new Script("OK","UNABLE TO CONNECT","A0","OK","NO DATA","6","OK",LEGACY,"1");
        var r=ProtocolNegotiator.negotiate(FordData.F150,s,x->{});assertEquals(1,r.protocol());assertEquals("10",r.ecu());assertEquals("ATTP1",s.commands.get(6));
    }
    @Test public void v10Uses29BitFallbackNotLegacy() throws Exception {
        Script s=new Script("OK","UNABLE TO CONNECT","A0","OK","NO DATA","6","OK","18DAF110 06 41 00 BE 3F B8 13 00","7");
        assertEquals(7,ProtocolNegotiator.negotiate(FordData.V10,s,x->{}).protocol());assertFalse(s.commands.contains("ATTP1"));
    }
    @Test public void transmissionReplyNotSuccess() throws Exception {
        Script s=new Script("OK","7E9 06 41 00 BE 3F B8 13 00","A6","OK",CAN,"6");
        ProtocolNegotiator.negotiate(FordData.F150,s,x->{});assertEquals(6,s.commands.size());
    }
    @Test public void reportedProtocolAloneIsInsufficient() throws Exception {
        Script s=new Script("OK","NO DATA","A6","OK",CAN,"6");assertEquals(6,ProtocolNegotiator.negotiate(FordData.F150,s,x->{}).protocol());assertEquals(6,s.commands.size());
    }
    @Test public void failedAllProbesIsClearAndBounded() throws Exception {
        Script s=new Script("OK","UNABLE TO CONNECT","A0","OK","NO DATA","6","OK","NO DATA","1");
        try{ProtocolNegotiator.negotiate(FordData.F150,s,x->{});fail();}catch(IOException e){assertTrue(e.getMessage().contains("engine ECU did not respond"));assertTrue(e.getMessage().contains("ignition"));}
        assertEquals(9,s.commands.size());
    }
    @Test public void unsupportedTryCommandSkipped() throws Exception {
        Script s=new Script("?","OK",CAN,"6");assertEquals(6,ProtocolNegotiator.negotiate(FordData.F150,s,x->{}).protocol());assertEquals(4,s.commands.size());
    }
    @Test public void lostPromptNeverRetriesDesynchronizedChannel() throws Exception {
        List<String> commands=new ArrayList<>();
        try{ProtocolNegotiator.negotiate(FordData.F150,(cmd,timeout)->{commands.add(cmd);if(cmd.equals("0100"))throw new IOException("prompt timeout");return "OK";},x->{});fail();}
        catch(IOException e){assertEquals("prompt timeout",e.getMessage());}assertEquals(List.of("ATTP0","0100"),commands);
    }
    @Test public void adapterIgnoringForcedProtocolNotAccepted() throws Exception {
        Script s=new Script("OK","NO DATA","A0","OK",CAN,"7","OK",LEGACY,"1");
        assertEquals(1,ProtocolNegotiator.negotiate(FordData.F150,s,x->{}).protocol());
    }
    @Test public void malformedEngineBitmapNotAccepted() throws Exception {
        Script s=new Script("OK","7E8 05 41 00 BE 3F B8","A6","OK",CAN,"6");
        assertEquals(6,ProtocolNegotiator.negotiate(FordData.F150,s,x->{}).protocol());
    }
    @Test public void onlyReadAndAdapterConfigurationCommandsSent() throws Exception {
        Script s=new Script("OK","NO DATA","A0","OK",CAN,"6");ProtocolNegotiator.negotiate(FordData.F150,s,x->{});
        for(String cmd:s.commands)assertTrue(cmd,PromptReader.allowed(cmd));assertFalse(PromptReader.allowed("ATTP9"));assertTrue(PromptReader.allowed("ATTP1"));assertTrue(PromptReader.allowed("ATTP7"));
    }
}
