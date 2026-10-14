package com.geekopolis.fordmonitor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import com.geekopolis.fordmonitor.core.FordData;

/** Local bounded files. CSV uses canonical units, ISO timestamps and explicit ECU/profile identity. */
final class SessionLog implements Closeable {
    final File csv, raw;
    private final Writer samples, transcript;
    private final String profile;
    private long sampleBytes, rawBytes;
    private boolean sampleFull, rawFull;
    synchronized boolean limitReached() { return sampleFull || rawFull; }
    private static final long LIMIT = 8 * 1024 * 1024;
    SessionLog(File dir, FordData.Profile p) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Cannot create local log directory");
        String id = System.currentTimeMillis() + "-" + UUID.randomUUID();
        profile = p.key();
        csv = new File(dir, "session-" + id + ".csv"); raw = new File(dir, "session-" + id + ".txt");
        samples = new OutputStreamWriter(new FileOutputStream(csv), StandardCharsets.UTF_8);
        try { transcript = new OutputStreamWriter(new FileOutputStream(raw), StandardCharsets.UTF_8); }
        catch (IOException e) { samples.close(); throw e; }
        samples.write("utc,profile,ecu,channel,cylinder,kind,value,unit,minimum,maximum,evaluated,mid,tid,uasid,raw,note\n");
        samples.flush();
        // Retain 20 local sessions; never touch a file outside this app-owned directory.
        File[] old = dir.listFiles((d, name) -> name.startsWith("session-") && name.endsWith(".csv"));
        if (old != null) {
            Arrays.sort(old, Comparator.comparing(File::getName).reversed());
            for (int i = 20; i < old.length; i++) {
                File txt = new File(dir, old[i].getName().replace(".csv", ".txt"));
                old[i].delete(); txt.delete();
            }
        }
    }
    synchronized void raw(String command, String response) throws IOException {
        String row = Instant.now() + " > " + command + "\n" + response + "\n\n";
        long length = row.getBytes(StandardCharsets.UTF_8).length;
        if (rawFull || rawBytes + length > LIMIT) { rawFull = true; return; }
        rawBytes += length;
        transcript.write(row); transcript.flush();
    }
    synchronized void live(String ecu, String channel, Double value, String unit, String note) throws IOException {
        row(Instant.now(), profile, ecu, channel, "", "Live", value, unit, "", "", "", "", "", "", "", note);
    }
    synchronized void misfire(String ecu, FordData.Misfire m) throws IOException {
        row(Instant.now(), profile, ecu, "misfire", m.cylinder(), m.kind(), m.value(), m.unit(), m.minimum(),
            m.maximum(), m.evaluated(), hex(m.mid()), hex(m.tid()), m.uasid() < 0 ? "" : hex(m.uasid()), m.raw(), m.note());
    }
    private void row(Object... columns) throws IOException {
        StringJoiner join = new StringJoiner(",");
        for (Object o : columns) join.add("\"" + (o == null ? "" : o.toString()).replace("\"", "\"\"") + "\"");
        String row = join + "\n";
        long length = row.getBytes(StandardCharsets.UTF_8).length;
        if (sampleFull || sampleBytes + length > LIMIT) { sampleFull = true; return; }
        sampleBytes += length;
        samples.write(row); samples.flush();
    }
    private static String hex(int value) { return String.format(Locale.ROOT, "%02X", value); }
    @Override public synchronized void close() throws IOException {
        try { samples.close(); } finally { transcript.close(); }
    }
}
