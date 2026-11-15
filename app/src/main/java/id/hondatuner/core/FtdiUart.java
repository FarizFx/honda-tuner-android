package id.hondatuner.core;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded Honda wake/TX timing around the Android FTDI serial driver. */
public final class FtdiUart implements ByteChannel {
    public interface UsbIo {
        int controlOut(int request, int value, int index, int timeoutMillis) throws IOException;
        int controlIn(int request, int value, int index, byte[] buffer, int timeoutMillis) throws IOException;
        void configureUart(int baudRate) throws IOException;
        void setBreak(boolean enabled) throws IOException;
        int write(byte[] data, int offset, int count, int timeoutMillis) throws IOException;
        /** Raw FTDI USB packets, with their two status bytes still present. */
        int read(byte[] buffer, int timeoutMillis) throws IOException;
        void close();
    }
    public interface Timing extends KLineTransport.NanoClock {
        void sleep(long millis) throws InterruptedException;
    }
    private static final Timing SYSTEM = new Timing() {
        @Override public long now() { return System.nanoTime(); }
        @Override public void sleep(long millis) throws InterruptedException { Thread.sleep(millis); }
    };
    private static final int BAUD = 10400;
    private final UsbIo io;
    private final int port, packetSize;
    private final DiagnosticLog log;
    private final Timing timing;
    private final AtomicBoolean closed = new AtomicBoolean();
    private byte[] pending = new byte[0];
    private boolean transmitReady;

    public FtdiUart(UsbIo io, int port, int packetSize, DiagnosticLog log) {
        this(io, port, packetSize, log, SYSTEM);
    }
    public FtdiUart(UsbIo io, int port, int packetSize, DiagnosticLog log, Timing timing) {
        if (port < 1 || packetSize <= 2 || packetSize > 1024) throw new IllegalArgumentException("Endpoint FT232R tidak valid.");
        this.io = Objects.requireNonNull(io); this.port = port; this.packetSize = packetSize;
        this.log = log; this.timing = Objects.requireNonNull(timing);
    }
    private void requireOpen() throws ProtocolException {
        if (closed.get()) throw new ProtocolException("Adaptor USB sudah ditutup atau dilepas.");
    }
    private String elapsed(long began) {
        return String.format(Locale.ROOT, "%.1f ms", (timing.now() - began) / 1000000.0);
    }
    private void control(int request, int value, int index) throws IOException {
        requireOpen();
        long began = timing.now();
        int result = io.controlOut(request, value, index, 500);
        if (log != null) log.debug("FTDI", "CTRL req=" + request + " value=0x" + Integer.toHexString(value) + " index=" + index + " result=" + result + " elapsed=" + elapsed(began));
        requireOpen();
        if (result != 0) throw new ProtocolException("Pengaturan FT232R gagal (USB request " + request + ").");
    }
    private void configureUart(CancelSignal cancel) throws IOException {
        cancel.check(); control(11, 0, port);
        cancel.check(); io.configureUart(BAUD); // serial library sets 8N1 and no flow control
        requireOpen();
        cancel.check();
    }
    @Override public void initialize(CancelSignal cancel) throws IOException {
        cancel.check(); transmitReady = false;
        try {
            control(0, 0, port); // full initialization deliberately resets the UART
            cancel.check(); purge();
            configureUart(cancel);
            control(9, 8, port);
            cancel.check(); io.setBreak(true); requireOpen();
            long lowBegan = timing.now();
            pauseUntil(lowBegan + 70000000L, cancel);
            cancel.check(); io.setBreak(false); requireOpen();
            if (log != null) log.info("WAKE", "UART BREAK ON/OFF accepted; LOW hold host elapsed=" + elapsed(lowBegan));
            long highBegan = timing.now();
            pauseUntil(highBegan + 130000000L, cancel);
            purge();
            drainBeforeWake(cancel);
            cancel.check(); transmitReady = true;
            if (log != null) log.info("WAKE", "UART 10400 8N1, flow=none; HIGH settling host elapsed=" + elapsed(highBegan));
        } catch (IOException | RuntimeException error) {
            if (!closed.get()) restoreIdle();
            throw error;
        }
    }
    private void drainBeforeWake(CancelSignal cancel) throws IOException {
        // A chip purge cannot retract USB packets already queued at the host.
        // No Honda command has been sent yet: discard these raw samples without
        // treating UART errors from a previous wake as an ECU response.
        long began = timing.now(), deadline = began + 250000000L, lastData = began;
        int discarded = 0;
        byte[] raw = new byte[packetSize * 4];
        while (true) {
            cancel.check(); requireOpen();
            long now = timing.now();
            if (now - lastData >= 24000000L) break;
            if (now >= deadline) throw new ProtocolException("RX FT232R belum tenang dalam 250 ms sebelum wake; perintah ECU dibatalkan.");
            long waitUntil = Math.min(deadline, lastData + 24000000L);
            int waitMillis = (int) Math.min(8, Math.max(1, (waitUntil - now + 999999L) / 1000000L));
            int count = io.read(raw, waitMillis);
            requireOpen(); cancel.check();
            if (count > raw.length) throw new ProtocolException("Panjang RX USB tidak valid saat membersihkan wake.");
            if (count > 0) {
                if (log != null) log.wire("WAKE CLEANUP", "RAW", raw, count);
                int payload = 0;
                for (int offset = 0; offset < count; offset += packetSize) {
                    int size = Math.min(packetSize, count - offset);
                    if (size < 2) throw new ProtocolException("Status USB FTDI terpotong saat membersihkan wake.");
                    payload += size - 2;
                }
                if (payload > 0) { discarded += payload; lastData = timing.now(); }
            }
        }
        cancel.check();
        if (log != null) log.info("WAKE CLEANUP", "RX quiet for 24 ms; discarded UART samples=" + discarded + "; elapsed=" + elapsed(began));
    }
    @Override public void purge() throws IOException {
        pending = new byte[0];
        if (log != null) log.debug("FTDI", "Purge RX only (TX preserved)");
        control(0, 1, port);
    }
    private int slice(long deadline) throws ProtocolException {
        long remaining = deadline - timing.now();
        if (remaining <= 0) throw new ProtocolException("Batas waktu kirim/pengaturan FT232R habis.");
        return (int) Math.min(50, Math.max(1, (remaining + 999999L) / 1000000L));
    }
    private void pauseUntil(long deadline, CancelSignal cancel) throws InterruptedIOException {
        while (timing.now() < deadline) {
            cancel.check();
            long millis = Math.min(10, Math.max(1, (deadline - timing.now() + 999999L) / 1000000L));
            try { timing.sleep(millis); }
            catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Operasi FT232R dibatalkan.");
            }
        }
        cancel.check();
    }
    private void writeQueued(byte[] data, long deadline, CancelSignal cancel) throws IOException {
        int sent = 0;
        long began = timing.now();
        while (sent < data.length) {
            cancel.check(); requireOpen();
            int count = io.write(data, sent, data.length - sent, slice(deadline));
            if (log != null) log.debug("USB TX", "bulk requested=" + (data.length - sent) + " result=" + count + " elapsed=" + elapsed(began));
            requireOpen(); cancel.check();
            if (count <= 0 || count > data.length - sent) throw new ProtocolException("USB tidak menyelesaikan pengiriman.");
            sent += count;
        }
    }
    @Override public void write(byte[] data, int timeoutMillis, CancelSignal cancel) throws IOException {
        Objects.requireNonNull(data);
        if (timeoutMillis <= 0) throw new IllegalArgumentException("Timeout harus positif.");
        cancel.check(); requireOpen();
        if (!transmitReady) throw new ProtocolException("UART FT232R perlu diinisialisasi sebelum mengirim lagi.");
        if (data.length == 0) return;
        // A bulk transfer only queues bytes. Guard against an initially stale
        // TEMT bit, then require both THRE and TEMT from a fresh modem-status poll.
        long wireMillis = (data.length * 10000L + BAUD - 1) / BAUD;
        if (wireMillis >= timeoutMillis) throw new ProtocolException("Frame UART tidak dapat selesai dalam timeout kirim.");
        long began = timing.now(), deadline = began + timeoutMillis * 1000000L;
        try {
            writeQueued(data, deadline, cancel);
            long earliestEmpty = timing.now() + wireMillis * 1000000L;
            pauseUntil(Math.min(earliestEmpty, deadline), cancel);
            byte[] status = new byte[2];
            while (timing.now() < deadline) {
                cancel.check(); requireOpen();
                int count = io.controlIn(5, 0, port, status, slice(deadline));
                requireOpen(); cancel.check();
                if (count != 2) throw new ProtocolException("Status TX FT232R tidak tersedia (" + count + ").");
                if (log != null) log.wire("FTDI", "TX MODEM/LSR", status, count);
                if (timing.now() < deadline && (status[1] & 0x60) == 0x60) {
                    if (log != null) log.debug("UART TX", data.length + " byte drained, elapsed=" + elapsed(began));
                    return;
                }
                pauseUntil(Math.min(deadline, timing.now() + 2000000L), cancel);
            }
            cancel.check();
            throw new ProtocolException("TX UART FT232R belum kosong dalam " + timeoutMillis + " ms; pengiriman berikutnya dibatalkan.");
        } catch (IOException | RuntimeException error) {
            // Never append another command to a transmission with unknown completion.
            // HondaClient may retry a full initialization, which resets the UART.
            transmitReady = false;
            throw error;
        }
    }
    @Override public byte[] read(int maximumBytes, int timeoutMillis) throws IOException {
        requireOpen();
        if (maximumBytes <= 0) return new byte[0];
        if (pending.length != 0) return takePending(maximumBytes);
        int packets = (int) Math.min(4L, Math.max(1L, ((long) maximumBytes + packetSize - 3) / (packetSize - 2)));
        byte[] raw = new byte[packetSize * packets];
        long began = timing.now();
        int count = io.read(raw, Math.max(1, timeoutMillis));
        requireOpen();
        if (count <= 0) {
            if (log != null) log.debug("USB RX", "bulk result=" + count + ", wait=" + timeoutMillis + " ms, elapsed=" + elapsed(began) + " (empty/timeout or USB error)");
            return new byte[0];
        }
        if (count > raw.length) throw new ProtocolException("Panjang RX USB tidak valid.");
        if (log != null) log.wire("USB RX", "RAW", raw, count);
        pending = FtdiCodec.stripStatus(raw, count, packetSize);
        if (log != null && pending.length == 0) log.debug("USB RX", "Status-only packet; no UART data.");
        return takePending(maximumBytes);
    }
    private byte[] takePending(int maximumBytes) {
        int count = Math.min(maximumBytes, pending.length);
        byte[] result = Arrays.copyOfRange(pending, 0, count);
        pending = Arrays.copyOfRange(pending, count, pending.length);
        return result;
    }
    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            try { restoreIdle(); }
            finally { io.close(); }
        }
    }
    private void restoreIdle() {
        // Cancellation may happen while UART BREAK holds TXD LOW. Restore
        // UART idle without writing another byte, including on failed setup.
        for (int[] setting : new int[][]{{0, 0}, {11, 0}, {4, 8}}) {
            try {
                int result = io.controlOut(setting[0], setting[1], port, 100);
                if (log != null && result != 0) log.warn("FTDI CLOSE", "Idle control req=" + setting[0] + " result=" + result);
            } catch (IOException | RuntimeException error) {
                if (log != null) log.warn("FTDI CLOSE", "Idle control: " + error.getMessage());
            }
        }
    }
}
