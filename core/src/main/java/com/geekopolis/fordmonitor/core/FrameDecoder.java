package com.geekopolis.fordmonitor.core;

import java.io.ByteArrayOutputStream;
import java.util.*;

/** ELM H1, S1, D0, CAF1 replies: preserve ECU identity and assemble ISO-TP strictly. */
public final class FrameDecoder {
    public record Message(String ecu, byte[] data) {}
    private static final class Pending {
        final int length;
        int sequence = 1;
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Pending(int length) { this.length = length; }
    }
    private FrameDecoder() {}
    public static List<Message> decode(String reply, boolean can) {
        List<Message> out = new ArrayList<>();
        Map<String, Pending> partial = new HashMap<>();
        for (String line : reply.toUpperCase(Locale.ROOT).split("[\r\n>]+")) {
            String[] words = line.trim().split("\\s+");
            if (can) {
                if (words.length < 3 || !words[0].matches("[0-9A-F]{3}|[0-9A-F]{8}")) continue;
                String ecu = words[0];
                byte[] b = bytes(words, 1, words.length);
                if (b == null || b.length < 2 || b.length > 8) continue;
                int pci = b[0] & 255, kind = pci >>> 4;
                if (kind == 0) {
                    partial.remove(ecu);
                    int n = pci & 15;
                    if (n > 0 && n <= b.length - 1) out.add(new Message(ecu, Arrays.copyOfRange(b, 1, n + 1)));
                } else if (kind == 1) {
                    partial.remove(ecu);
                    int n = ((pci & 15) << 8) | (b[1] & 255);
                    if (n <= 7 || n > 4095 || b.length != 8) continue;
                    Pending p = new Pending(n);
                    p.bytes.write(b, 2, b.length - 2);
                    partial.put(ecu, p);
                } else if (kind == 2) {
                    Pending p = partial.get(ecu);
                    if (p == null) continue;
                    if ((pci & 15) != p.sequence) { partial.remove(ecu); continue; }
                    int left = p.length - p.bytes.size();
                    if (b.length - 1 < Math.min(left, 7)) { partial.remove(ecu); continue; }
                    p.bytes.write(b, 1, Math.min(left, b.length - 1));
                    p.sequence = (p.sequence + 1) & 15;
                    if (p.bytes.size() == p.length) {
                        out.add(new Message(ecu, p.bytes.toByteArray()));
                        partial.remove(ecu);
                    }
                }
            } else {
                byte[] b = bytes(words, 0, words.length);
                // J1850/ISO headers are three bytes; ELM validates the trailing checksum.
                if (b == null || b.length < 5) continue;
                String ecu = String.format(Locale.ROOT, "%02X", b[2] & 255);
                out.add(new Message(ecu, Arrays.copyOfRange(b, 3, b.length - 1)));
            }
        }
        return out;
    }
    private static byte[] bytes(String[] words, int from, int to) {
        byte[] b = new byte[to - from];
        for (int i = from; i < to; i++) {
            if (!words[i].matches("[0-9A-F]{2}")) return null;
            b[i - from] = (byte) Integer.parseInt(words[i], 16);
        }
        return b;
    }
    public static String engineEcu(boolean can, int protocol) {
        return !can ? "10" : (protocol == 7 || protocol == 9 ? "18DAF110" : "7E8");
    }
    public static List<byte[]> fromEngine(String reply, boolean can, String ecu) {
        List<byte[]> result = new ArrayList<>();
        for (Message m : decode(reply, can)) if (m.ecu().equals(ecu)) result.add(m.data());
        return result;
    }
}
