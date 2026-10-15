package com.geekopolis.fordmonitor;

import android.annotation.SuppressLint;
import android.bluetooth.*;
import com.geekopolis.fordmonitor.core.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** One worker owns all adapter transactions. Closing the socket cancels connect/read immediately. */
@SuppressLint("MissingPermission") // MainActivity checks CONNECT before construction; revocation is caught below.
final class ObdSession implements Closeable {
    interface Listener {
        void status(String text, boolean active);
        void live(int pid, Double value);
        void cylinder(int cylinder, List<FordData.Misfire> results, String note);
        void readiness(String text);
        void codes(String text);
    }
    private final BluetoothDevice device;
    private final FordData.Profile profile;
    private final Listener listener;
    private final File directory;
    private volatile boolean closed;
    private volatile BluetoothSocket socket;
    private volatile Thread worker;
    private final CountDownLatch finished = new CountDownLatch(1);
    private final AtomicBoolean codeRequest = new AtomicBoolean();
    private final AtomicBoolean scanRequest = new AtomicBoolean();
    volatile File csvFile, rawFile;
    private SessionLog log;
    private PromptReader reader;
    private boolean can;
    private String ecu;
    private final ScheduledExecutorService transactionTimer = Executors.newSingleThreadScheduledExecutor();
    private boolean bundle = true;
    private volatile int focusedCylinder = 0;
    void focusCylinder(int cylinder) { if(cylinder>=0 && cylinder<=profile.cylinders()) focusedCylinder=cylinder; }
    private String link;
    private final Set<Integer> supported = new HashSet<>();
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int[] LIVE = {0x0C, 0x05, 0x06, 0x07, 0x08, 0x09, 0x04, 0x0D, 0x0F, 0x11};

    ObdSession(BluetoothDevice device, FordData.Profile profile, File directory, Listener listener) {
        this.device = device; this.profile = profile; this.directory = directory; this.listener = listener;
    }
    void start() { worker = new Thread(this::run, "Ford-OBD"); worker.start(); }
    boolean awaitStopped() throws InterruptedException { return finished.await(5, TimeUnit.SECONDS); }
    void requestCodes() { codeRequest.set(true); }
    void requestScan() { scanRequest.set(true); }
    private void run() {
        try {
            log = new SessionLog(directory, profile); csvFile = log.csv; rawFile = log.raw;
            log.raw("# SESSION", "Build " + BuildConfig.VERSION_CODE + " / " + BuildConfig.VERSION_NAME
                + "; profile=" + profile.name() + "; adapter=" + device.getName());
            listener.status("Connecting to " + device.getName() + "…", true);
            connect(false);
            InputStream input = socket.getInputStream();
            // Some adapters print a startup banner before the first command. Drain it only at startup.
            long until = System.nanoTime() + 300_000_000L;
            while (System.nanoTime() < until && !closed) {
                while (input.available() > 0) { if (input.read() < 0) throw new EOFException(); }
                Thread.sleep(10);
            }
            reader = new PromptReader(input, socket.getOutputStream(), this::closeSocket);
            query("ATZ", 5000);
            for (String command : new String[]{"ATE0", "ATL0", "ATS1", "ATH1", "ATAT1", "ATSTFF"}) required(command);
            // AL allows full Mode 06 records and DTC/VIN multi-frame replies; clones may reject it.
            query("ATAL", 2000);
            query("ATRV", 2000); // Adapter supply measurement only; not an ignition-state claim.
            ProtocolNegotiator.Result handshake = ProtocolNegotiator.negotiate(profile, this::query,
                text -> listener.status(text,true));
            String first = handshake.reply();
            int protocol = handshake.protocol();
            can = protocol >= 6;
            ecu = handshake.ecu();
            if (can) {
                required("ATD0"); required("ATCAF1"); required("ATCFC1");
                first = query("0100", 4000);
            }
            boolean identified = false;
            for (byte[] b : data(first)) {
                if (b.length == 6 && b[0] == 0x41 && b[1] == 0) {
                    identified = true;
                    for (int pid : LIVE) if (FordData.supports(b, 1, 0, pid)) supported.add(pid);
                    if (FordData.supports(b, 1, 0, 1)) supported.add(1);
                }
            }
            if (!identified) throw new IOException("No valid engine response from " + ecu + ". Check ignition and adapter; export debug log.");
            String adapter = query("ATI", 2000);
            link = profile.name() + " · " + (can ? "CAN" : "Legacy OBD") + " · ECU " + ecu;
            listener.status(link, true);
            log.live(ecu, "adapter", null, "", adapter);
            if (can) {
                for (byte[] b : data(query("0902", 4000))) {
                    String vin = FordData.vin(b);
                    if (vin != null) {
                        log.live(ecu, "VIN", null, "", vin);
                        link += " · VIN …" + vin.substring(11);
                        char expected = profile.year() == 2004 ? '4' : 'H';
                        if (vin.charAt(9) != expected) link += " · Verify chassis year/profile";
                    }
                }
                listener.status(link, true);
            }
            bundle = can;
            readReady();
            // Query each cylinder immediately, then round-robin to keep live RPM responsive.
            for (int c = 1; c <= profile.cylinders() && !closed; c++) readCylinder(c);
            int c = 1, liveIndex = 0, ticks = 0;
            long nextReady = System.nanoTime() + 30_000_000_000L;
            while (!closed) {
                long start = System.nanoTime();
                if (bundle) readLiveBundles();
                else {
                    if (supported.contains(0x0C)) readLive(0x0C);
                    int pid = LIVE[liveIndex++ % LIVE.length];
                    if (pid != 0x0C && supported.contains(pid)) readLive(pid);
                }
                if (can) {
                    int focus=focusedCylinder;
                    for(int cylinder:PollingPlan.cylinders(profile.cylinders(),focus,c)){
                        readCylinder(cylinder);
                        // Keep load observations interleaved with the all-cylinder sweep.
                        if(focus==0 && cylinder%2==0 && cylinder<profile.cylinders() && supported.contains(4))readLive(4);
                    }
                    c = c % profile.cylinders() + 1;
                }
                else if (ticks++ % 5 == 0) readCylinder(1);
                if (scanRequest.getAndSet(false)) {
                    for (int i = 1; i <= profile.cylinders() && !closed; i++) readCylinder(i);
                }
                if (codeRequest.getAndSet(false)) readCodes();
                if (System.nanoTime() >= nextReady) {
                    readReady(); nextReady = System.nanoTime() + 30_000_000_000L;
                }
                listener.status(link + (can ? bundle ? " · Bundled live PIDs" : " · Single-PID polling" : " · Legacy polling")
                    + (log.limitReached() ? " · Logs full; export/reconnect for more recording" : ""),true);
                long pause = 1000 - (System.nanoTime() - start) / 1_000_000;
                if (pause > 0) Thread.sleep(pause);
            }
        } catch (Exception e) {
            if (!closed) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                if (log != null) try { log.raw("# ERROR", message); } catch (IOException ignored) {}
                listener.status("Disconnected: " + message, false);
            }
        } finally {
            closeSocket(); transactionTimer.shutdownNow();
            if (log != null) try { log.close(); } catch (IOException ignored) {}
            finished.countDown();
            if (closed) listener.status("Disconnected · logs kept on this device", false);
        }
    }
    private void connect(boolean insecure) throws IOException {
        if (closed) throw new IOException("Connection cancelled");
        BluetoothSocket next = insecure ? device.createInsecureRfcommSocketToServiceRecord(SPP)
                                      : device.createRfcommSocketToServiceRecord(SPP);
        synchronized (this) {
            if (closed) { next.close(); throw new IOException("Connection cancelled"); }
            socket = next;
        }
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> timeout = timer.schedule(() -> { try { next.close(); } catch (IOException ignored) {} }, 12, TimeUnit.SECONDS);
        try { next.connect(); }
        catch (IOException e) {
            next.close();
            if (!insecure && !closed) {
                listener.status("Trying paired-device compatibility connection…", true); connect(true);
            } else throw e;
        } finally { timeout.cancel(false); timer.shutdownNow(); }
        if (closed) throw new IOException("Connection cancelled");
    }
    private void required(String cmd) throws IOException {
        String response = query(cmd, 2500);
        if (!Arrays.asList(response.split("[\r\n]+" )).contains("OK")) throw new IOException("Adapter does not support " + cmd + ": " + response);
    }
    private String query(String cmd, long timeout) throws IOException {
        if (closed) throw new IOException("Connection cancelled");
        // Also bound a blocked Bluetooth write/available() call, not just the receive polling loop.
        ScheduledFuture<?> watchdog = transactionTimer.schedule(this::closeSocket, timeout + 1000, TimeUnit.MILLISECONDS);
        try { String r = reader.exchange(cmd, timeout); log.raw(cmd, r); return r; }
        finally { watchdog.cancel(false); }
    }
    private List<byte[]> data(String reply) { return FrameDecoder.fromEngine(reply, can, ecu); }
    private void readLiveBundles() throws IOException {
        List<Integer> all = new ArrayList<>();
        for (int pid : LIVE) if (supported.contains(pid)) all.add(pid);
        if (all.size() < 2) { for (int pid : all) readLive(pid); return; }
        // Maximum six PIDs per J1979 CAN request, selected only from the engine's support bitmap.
        for (int start = 0; start < all.size(); start += 6) {
            List<Integer> group = all.subList(start,Math.min(start + 6,all.size()));
            if (group.size() == 1) { readLive(group.get(0)); continue; }
            Set<Integer> requested = new LinkedHashSet<>(group);
            StringBuilder command = new StringBuilder("01");
            for (int pid : group) command.append(String.format(Locale.ROOT,"%02X",pid));
            Map<Integer,Double> values = new LinkedHashMap<>(); boolean conflict = false;
            for (byte[] b : data(query(command.toString(),4000))) {
                for (Map.Entry<Integer,Double> entry : FordData.liveBundle(b,requested).entrySet()) {
                    Double old = values.put(entry.getKey(),entry.getValue());
                    if (old != null && !old.equals(entry.getValue())) conflict = true;
                }
            }
            if (conflict || !values.keySet().equals(requested)) {
                bundle = false;
                log.live(ecu,"polling",null,"","Bundled reply incomplete / invalid; single-PID fallback for this connection");
                listener.status(link + " · Single-PID fallback",true);
                // Recover all live channels now, not just the missing portion of this bundle.
                for (int pid : all) readLive(pid);
                return;
            }
            for (int pid : group) {
                Double value = values.get(pid); listener.live(pid,value);
                log.live(ecu,String.format(Locale.ROOT,"01%02X",pid),value,unit(pid),"Bundled CAN read");
            }
        }
        listener.status(link + " · Bundled live PIDs",true);
    }
    private void readLive(int pid) throws IOException {
        Double value = null;
        for (byte[] b : data(query(String.format(Locale.ROOT, "01%02X", pid), 4000))) {
            Double v = FordData.live(b, pid); if (v != null) value = v;
        }
        listener.live(pid, value);
        log.live(ecu, String.format(Locale.ROOT, "01%02X", pid), value, unit(pid), value == null ? "No valid result" : "");
    }
    private static String unit(int pid) {
        return switch (pid) { case 0x0C -> "rpm"; case 0x05, 0x0F -> "C"; case 0x0D -> "km/h"; default -> "%"; };
    }
    private void readCylinder(int c) throws IOException {
        if (!can) {
            if (c != 1) return; // One 0653 request returns all legacy cylinders.
            String response = query("0653", 6000);
            List<FordData.Misfire> all = new ArrayList<>();
            for (byte[] b : data(response)) all.addAll(FordData.misfires(b, false, profile));
            for (int i = 1; i <= profile.cylinders(); i++) {
                List<FordData.Misfire> group = new ArrayList<>();
                for (FordData.Misfire m : all) if (m.cylinder() == i) group.add(m);
                publishCylinder(i, group);
            }
        } else {
            List<FordData.Misfire> group = new ArrayList<>();
            for (byte[] b : data(query(String.format(Locale.ROOT, "06%02X", 0xA1 + c), 4000)))
                for (FordData.Misfire m : FordData.misfires(b, true, profile)) if (m.cylinder() == c) group.add(m);
            publishCylinder(c, group);
        }
    }
    private void publishCylinder(int c, List<FordData.Misfire> values) throws IOException {
        listener.cylinder(c, values, values.isEmpty() ? "No supported result returned" : "");
        if (values.isEmpty()) log.live(ecu, "misfire-cylinder-" + c, null, "", "No supported result returned");
        for (FordData.Misfire m : values) log.misfire(ecu, m);
    }
    private void readReady() throws IOException {
        if (!supported.contains(1)) { listener.readiness("Misfire readiness unavailable"); return; }
        String text = null;
        for (byte[] b : data(query("0101", 4000))) { String v = FordData.readiness(b); if (v != null) text = v; }
        listener.readiness(text == null ? "Misfire readiness unavailable" : text);
        log.live(ecu, "readiness", null, "", text == null ? "Unavailable" : text);
    }
    private void readCodes() throws IOException {
        StringBuilder report = new StringBuilder();
        for (int mode : new int[]{3, 7, 0x0A}) {
            if (mode == 0x0A && !can) continue;
            List<String> codes = new ArrayList<>(); boolean valid = false;
            for (byte[] b : data(query(String.format(Locale.ROOT, "%02X", mode), 5000))) {
                if (FordData.validDtcReply(b, mode, can)) { valid = true; codes.addAll(FordData.dtcs(b, mode, can)); }
            }
            String type = mode == 3 ? "Stored" : mode == 7 ? "Pending" : "Permanent";
            String text = type + ": " + (!valid ? "unavailable" : codes.isEmpty() ? "none" : String.join(", ", codes));
            report.append(text).append('\n'); log.live(ecu, type + " DTC", null, "", text);
        }
        if (!can) report.append("Permanent codes: unavailable on this legacy protocol\n");
        listener.codes(report.toString().trim());
    }
    private void closeSocket() {
        BluetoothSocket s = socket;
        if (s != null) try { s.close(); } catch (IOException ignored) {}
    }
    @Override public synchronized void close() {
        closed = true; closeSocket(); Thread t = worker; if (t != null) t.interrupt();
    }
}
