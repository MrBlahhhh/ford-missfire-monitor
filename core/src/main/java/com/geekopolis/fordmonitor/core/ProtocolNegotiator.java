package com.geekopolis.fordmonitor.core;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/** Read-only engine handshake. Try commands do not persist a forced protocol on the adapter. */
public final class ProtocolNegotiator {
    @FunctionalInterface public interface Transport { String exchange(String command, long timeoutMs) throws IOException; }
    public record Result(int protocol, String reply, String ecu) {}
    private ProtocolNegotiator() {}
    public static Result negotiate(FordData.Profile profile, Transport transport, Consumer<String> status) throws IOException {
        // Auto first; newer Ford CAN 500k/11-bit next; 2004 can also be J1850 PWM.
        // V10 heavy-duty chassis can use 29-bit CAN addressing, not the 2004 J1850 scaling.
        int[] attempts = profile.year() == 2004 ? new int[]{0,6,1} : new int[]{0,6,7};
        List<String> evidence = new ArrayList<>();
        for (int i=0; i<attempts.length; i++) {
            int preferred=attempts[i];
            String label=preferred==0 ? "Auto" : preferred==1 ? "Ford J1850 PWM" : preferred==6 ? "CAN 500k / 11-bit" : "CAN 500k / 29-bit";
            status.accept("Adapter connected · finding engine ECU: " + label + " (" + (i+1) + "/" + attempts.length + ")");
            String config=transport.exchange("ATTP"+preferred,2500);
            boolean ok=Arrays.stream(config.split("[\r\n]+" )).anyMatch(s->s.trim().equals("OK"));
            if (!ok) { evidence.add(label+": adapter rejected protocol selection"); continue; }
            String reply=transport.exchange("0100",preferred==0 ? 16000 : 8000);
            String number=transport.exchange("ATDPN",2000);
            int protocol;
            try { protocol=FordData.protocol(number); }
            catch (IllegalArgumentException e) { evidence.add(label+": no protocol established"); continue; }
            if (preferred!=0 && protocol!=preferred) { evidence.add(label+": adapter reported a different protocol"); continue; }
            boolean can=protocol>=6;
            String ecu=FrameDecoder.engineEcu(can,protocol);
            for (byte[] b:FrameDecoder.fromEngine(reply,can,ecu)) {
                if (b.length==6 && b[0]==0x41 && b[1]==0) return new Result(protocol,reply,ecu);
            }
            evidence.add(label+": no valid engine response");
        }
        throw new IOException("Bluetooth/adapter connected, but engine ECU did not respond. Check ignition on/engine running, OBD plug and other scanner apps. "
            + "Tried " + String.join("; ",evidence) + ". Export debug log if this continues.");
    }
}
