package com.geekopolis.fordmonitor.core;

import java.util.*;

public final class FordData {
    public record Profile(String key, String name, int year, int cylinders) {}
    public static final Profile F150 = new Profile("f150", "2004 F-150 · 5.4L 3V V8", 2004, 8);
    public static final Profile V10 = new Profile("v10", "2017 Winnebago · Ford V10", 2017, 10);
    public record Misfire(int cylinder, String kind, String unit, double value, Double minimum,
                          Double maximum, int mid, int tid, int uasid, int raw,
                          boolean evaluated, String note) {
        public boolean outsideLimit() {
            return evaluated && ((minimum != null && value < minimum) || (maximum != null && value > maximum));
        }
    }
    private FordData() {}
    public static boolean supports(byte[] b, int mode, int base, int pid) {
        if (b.length != 6 || (b[0] & 255) != mode + 0x40 || (b[1] & 255) != base) return false;
        int bit = pid - base - 1;
        return bit >= 0 && bit < 32 && ((b[2 + bit / 8] & 255) & (0x80 >>> (bit % 8))) != 0;
    }
    public static List<Misfire> misfires(byte[] b, boolean can, Profile profile) {
        List<Misfire> out = new ArrayList<>();
        if (b.length < 2 || (b[0] & 255) != 0x46) return out;
        if (can) {
            if ((b.length - 1) % 9 != 0) return out;
            for (int p = 1; p < b.length; p += 9) {
                int mid = b[p] & 255, tid = b[p + 1] & 255, unit = b[p + 2] & 255;
                int cyl = mid - 0xA1;
                if (cyl < 1 || cyl > profile.cylinders()) continue;
                int value = u16(b, p + 3), min = u16(b, p + 5), max = u16(b, p + 7);
                if (tid != 0x0B && tid != 0x0C) continue;
                // SAE 0x01 is unsigned raw 1/bit; Ford TIDs 0B/0C identify these as counts.
                // UASID 0x24 explicitly encodes counts. Other scaling remains raw.
                boolean count = unit == 0x24 || unit == 0x01;
                String kind = tid == 0x0C ? "Last/current cycle" : "10-cycle weighted average";
                out.add(new Misfire(cyl, kind, count ? "counts" : "raw", value,
                    count ? (double) min : null, count ? (double) max : null, mid, tid, unit, value,
                    count && !(value == 0 && min == 0 && max == 0),
                    count ? (value == 0 && min == 0 && max == 0 ? "Not evaluated / zero limits" : "ECU monitor result")
                          : String.format(Locale.ROOT, "Unverified units 0x%02X; raw only", unit)));
            }
        } else {
            // Legacy records: response, TID, limit-type/CID, value(2), limit(2).
            if (b.length != 7 || (b[1] & 255) != 0x53 || profile.year() != 2004) return out;
            int cid = b[2] & 255, cyl = cid & 0x7F;
            if (cyl < 1 || cyl > profile.cylinders()) return out;
            int raw = u16(b, 3), limit = u16(b, 5);
            // Ford 2004 gasoline OBD summary p12: TIDs 50–55 multiply by 0.000015 percent.
            double value = raw * 0.000015, threshold = limit * 0.000015;
            boolean minimum = (cid & 0x80) != 0;
            out.add(new Misfire(cyl, "Stored misfire rate", "%", value, minimum ? threshold : null,
                minimum ? null : threshold, 0x53, cyl, -1, raw, !(raw == 0 && limit == 0),
                "Updated when DTC sets/clears; not a live event counter"));
        }
        return out;
    }
    public static Double live(byte[] b, int pid) {
        if (b.length < 3 || (b[0] & 255) != 0x41 || (b[1] & 255) != pid) return null;
        int a = b[2] & 255;
        return switch (pid) {
            case 0x0C -> b.length >= 4 ? u16(b, 2) / 4.0 : null;
            case 0x05, 0x0F -> (double) a - 40;
            case 0x06, 0x07, 0x08, 0x09 -> a * 100.0 / 128 - 100;
            case 0x04, 0x11 -> a * 100.0 / 255;
            case 0x0D -> (double) a;
            default -> null;
        };
    }
    /** Multi-PID CAN replies identify each PID, not a fixed response order. Reject unknown widths. */
    public static Map<Integer, Double> liveBundle(byte[] b, Set<Integer> requested) {
        Map<Integer, Double> result = new LinkedHashMap<>();
        if (b.length < 3 || b[0] != 0x41) return result;
        int p = 1;
        while (p < b.length) {
            int pid = b[p] & 255;
            if (!requested.contains(pid) || result.containsKey(pid)) return Collections.emptyMap();
            int n = pid == 0x0C ? 2 : switch (pid) { case 4,5,6,7,8,9,0x0D,0x0F,0x11 -> 1; default -> 0; };
            if (n == 0 || p + n >= b.length) return Collections.emptyMap();
            byte[] single = new byte[n + 2]; single[0] = 0x41;
            System.arraycopy(b,p,single,1,n+1);
            Double value = live(single,pid);
            if (value == null) return Collections.emptyMap();
            result.put(pid,value); p += n + 1;
        }
        return result;
    }
    public static String readiness(byte[] b) {
        if (b.length != 6 || b[0] != 0x41 || b[1] != 1) return null;
        int a = b[2] & 255, bits = b[3] & 255;
        return "MIL " + ((a & 128) != 0 ? "ON" : "off") + " · " + (a & 127) + " stored codes · Misfire "
            + ((bits & 1) == 0 ? "not supported" : (bits & 16) != 0 ? "not ready" : "ready")
            + " since clear (not proof of active monitoring)";
    }
    public static List<String> dtcs(byte[] b, int mode, boolean can) {
        List<String> out = new ArrayList<>();
        if (b.length < 1 || (b[0] & 255) != mode + 0x40) return out;
        int start = can ? 2 : 1;
        if (b.length < start || (b.length - start) % 2 != 0) return out;
        int count = can ? b[1] & 255 : (b.length - start) / 2;
        if (can && count > (b.length - start) / 2) return out;
        for (int i = 0; i < count; i++) {
            int a = b[start + i * 2] & 255, z = b[start + i * 2 + 1] & 255;
            if (a == 0 && z == 0) continue;
            out.add(String.format(Locale.ROOT, "%c%01X%01X%02X", "PCBU".charAt(a >>> 6), (a >>> 4) & 3, a & 15, z));
        }
        return out;
    }
    public static boolean validDtcReply(byte[] b, int mode, boolean can) {
        int start = can ? 2 : 1;
        return b.length >= start && (b[0] & 255) == mode + 0x40 && (b.length - start) % 2 == 0
            && (!can || (b[1] & 255) <= (b.length - start) / 2);
    }
    public static String vin(byte[] b) {
        if (b.length != 20 || b[0] != 0x49 || b[1] != 2 || b[2] != 1) return null;
        String vin = new String(b, 3, 17, java.nio.charset.StandardCharsets.US_ASCII);
        return vin.matches("[A-HJ-NPR-Z0-9]{17}") ? vin : null;
    }
    public static int protocol(String s) {
        String v = s.replaceAll("[\\r\\n> ]", "").toUpperCase(Locale.ROOT);
        if (v.startsWith("A")) v = v.substring(1);
        if (!v.matches("[1-9]")) throw new IllegalArgumentException("Unsupported / unidentified OBD protocol: " + s.trim());
        return Integer.parseInt(v, 16);
    }
    private static int u16(byte[] b, int p) { return ((b[p] & 255) << 8) | (b[p + 1] & 255); }
}
