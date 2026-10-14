package com.geekopolis.fordmonitor.core;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** One transaction at a time. A missing prompt is fatal; never reuse a desynchronized stream. */
public final class PromptReader {
    private final InputStream input;
    private final OutputStream output;
    private final Runnable close;
    public PromptReader(InputStream input, OutputStream output, Runnable close) {
        this.input = input; this.output = output; this.close = close;
    }
    public synchronized String exchange(String command, long timeoutMs) throws IOException {
        if (!allowed(command)) throw new IOException("Command is not an approved read-only request");
        output.write((command + "\r").getBytes(StandardCharsets.US_ASCII));
        output.flush();
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            while (System.nanoTime() < deadline) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Disconnected");
                if (input.available() == 0) {
                    try { Thread.sleep(5); } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); throw new InterruptedIOException("Disconnected");
                    }
                    continue;
                }
                int b = input.read();
                if (b == -1) throw new EOFException("Adapter disconnected");
                if (b == '>') {
                    StringBuilder clean = new StringBuilder();
                    for (String line : new String(bytes.toByteArray(), StandardCharsets.US_ASCII).split("[\\r\\n]+")) {
                        if (!line.trim().equalsIgnoreCase(command)) clean.append(line).append('\n');
                    }
                    return clean.toString().trim();
                }
                if (bytes.size() >= 65536) throw new IOException("Adapter reply exceeded 64 KiB");
                bytes.write(b);
            }
            throw new IOException("Adapter timed out waiting for >; reconnect to resynchronize");
        } catch (IOException e) { close.run(); throw e; }
    }
    public static boolean allowed(String command) {
        return command.matches("01(00|01|04|05|06|07|08|09|0C|0D|0F|11)")
            || command.matches("01(04|05|06|07|08|09|0C|0D|0F|11){2,6}")
            || command.matches("06(00|20|40|60|80|A0|A[2-9AB]|53)")
            || command.matches("03|07|0A|0902")
            || command.matches("ATZ|ATE0|ATL0|ATS1|ATH1|ATD0|ATAL|ATCAF1|ATCFC1|ATSP0|ATTP0|ATTP1|ATTP6|ATTP7|ATRV|ATAT1|ATSTFF|ATDPN|ATI");
    }
}
