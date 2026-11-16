package id.hondatuner.core;

import java.io.IOException;
import java.io.InterruptedIOException;
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
    private FtdiCodec.RxData pending;
    private boolean transmitReady;
    private volatile boolean failed;

    public FtdiUart(UsbIo io, int port, int packetSize, DiagnosticLog log) {
        this(io, port, packetSize, log, SYSTEM);
    }
    public FtdiUart(UsbIo io, int port, int packetSize, DiagnosticLog log, Timing timing) {
        if (port < 1 || packetSize <= 2 || packetSize > 1024) throw new IllegalArgumentException("Endpoint FT232R tidak valid.");
        this.io = Objects.requireNonNull(io); this.port = port; this.packetSize = packetSize;
        this.log = log; this.timing = Objects.requireNonNull(timing);
    }
    @Override public boolean isHealthy() { return !closed.get() && !failed; }
    private void requireOpen() throws TransportException {
        if (closed.get()) throw new TransportException("Adaptor USB sudah ditutup atau dilepas.");
    }
    private TransportException failed(String message, Throwable cause) {
        failed = true; transmitReady = false;
        return cause == null ? new TransportException(message) : new TransportException(message, cause);
    }
    private int readUsb(byte[] buffer, int timeoutMillis) throws TransportException {
        try { return io.read(buffer, timeoutMillis); }
        catch (IOException | RuntimeException error) { throw failed("Pembacaan USB FT232R gagal: " + error.getMessage(), error); }
    }
    private void setBreak(boolean enabled) throws TransportException {
        try { io.setBreak(enabled); }
        catch (IOException | RuntimeException error) { throw failed("Pengaturan BREAK FT232R gagal: " + error.getMessage(), error); }
        requireOpen();
    }
    private String elapsed(long began) {
        return String.format(Locale.ROOT, "%.1f ms", (timing.now() - began) / 1000000.0);
    }
    private void control(int request, int value, int index) throws IOException {
        requireOpen();
        long began = timing.now();
        int result;
        try { result = io.controlOut(request, value, index, 500); }
        catch (IOException | RuntimeException error) { throw failed("Kontrol USB FT232R gagal: " + error.getMessage(), error); }
        if (log != null) log.debug("FTDI", "CTRL req=" + request + " value=0x" + Integer.toHexString(value) + " index=" + index + " result=" + result + " elapsed=" + elapsed(began));
        requireOpen();
        if (result != 0) throw failed("Pengaturan FT232R gagal (USB request " + request + ").", null);
    }
    private void configureUart(CancelSignal cancel) throws IOException {
        cancel.check(); control(11, 0, port);
        cancel.check();
        try { io.configureUart(BAUD); } // serial library sets 8N1 and no flow control
        catch (IOException | RuntimeException error) { throw failed("Pengaturan UART FT232R gagal: " + error.getMessage(), error); }
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
            cancel.check(); setBreak(true);
            long lowBegan = timing.now();
            pauseUntil(lowBegan + 70000000L, cancel);
            cancel.check(); setBreak(false);
            if (log != null) log.info("WAKE", "UART BREAK ON/OFF accepted; LOW hold host elapsed=" + elapsed(lowBegan));
            long highBegan = timing.now();
            pauseUntil(highBegan + 130000000L, cancel);
            purge();
            drainBeforeWake(cancel);
            cancel.check(); transmitReady = true; failed = false;
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
            int count = readUsb(raw, waitMillis);
            requireOpen(); cancel.check();
            if (count > raw.length) throw failed("Panjang RX USB tidak valid saat membersihkan wake.", null);
            if (count > 0) {
                if (log != null) log.wire("WAKE CLEANUP", "RAW", raw, count);
                int payload = 0;
                for (int offset = 0; offset < count; offset += packetSize) {
                    int size = Math.min(packetSize, count - offset);
                    if (size < 2) throw failed("Status USB FTDI terpotong saat membersihkan wake.", null);
                    payload += size - 2;
                }
                if (payload > 0) { discarded += payload; lastData = timing.now(); }
            }
        }
        cancel.check();
        if (log != null) log.info("WAKE CLEANUP", "RX quiet for 24 ms; discarded UART samples=" + discarded + "; elapsed=" + elapsed(began));
    }
    @Override public void purge() throws IOException {
        pending = null;
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
    private void writeQueued(byte[] data, long deadline, CancelSignal cancel, boolean[] attempted) throws IOException {
        int sent = 0;
        long began = timing.now();
        while (sent < data.length) {
            cancel.check(); requireOpen();
            int timeout = slice(deadline);
            attempted[0] = true;
            int count;
            try { count = io.write(data, sent, data.length - sent, timeout); }
            catch (IOException | RuntimeException error) {
                // SerialTimeoutException is an InterruptedIOException too, but
                // it represents potentially partial USB TX, not user cancellation.
                throw failed("Pengiriman USB FT232R tidak pasti: " + error.getMessage(), error);
            }
            if (log != null) log.debug("USB TX", "bulk requested=" + (data.length - sent) + " result=" + count + " elapsed=" + elapsed(began));
            requireOpen(); cancel.check();
            if (count <= 0 || count > data.length - sent) throw failed("USB tidak menyelesaikan pengiriman.", null);
            sent += count;
        }
    }
    @Override public void write(byte[] data, int timeoutMillis, CancelSignal cancel) throws IOException {
        Objects.requireNonNull(data);
        if (timeoutMillis <= 0) throw new IllegalArgumentException("Timeout harus positif.");
        cancel.check(); requireOpen();
        if (!transmitReady) throw failed("UART FT232R perlu diinisialisasi sebelum mengirim lagi.", null);
        if (data.length == 0) return;
        // A bulk transfer only queues bytes. Guard against an initially stale
        // TEMT bit, then require both THRE and TEMT from a fresh modem-status poll.
        long wireMillis = (data.length * 10000L + BAUD - 1) / BAUD;
        if (wireMillis >= timeoutMillis) throw new ProtocolException("Frame UART tidak dapat selesai dalam timeout kirim.");
        long began = timing.now(), deadline = began + timeoutMillis * 1000000L;
        boolean[] attempted = {false};
        try {
            writeQueued(data, deadline, cancel, attempted);
            long earliestEmpty = timing.now() + wireMillis * 1000000L;
            pauseUntil(Math.min(earliestEmpty, deadline), cancel);
            byte[] status = new byte[2];
            while (timing.now() < deadline) {
                cancel.check(); requireOpen();
                int count;
                try { count = io.controlIn(5, 0, port, status, slice(deadline)); }
                catch (IOException | RuntimeException error) { throw failed("Status TX USB FT232R gagal: " + error.getMessage(), error); }
                requireOpen(); cancel.check();
                if (count != 2) throw failed("Status TX FT232R tidak tersedia (" + count + ").", null);
                if (log != null) log.wire("FTDI", "TX MODEM/LSR", status, count);
                if (timing.now() < deadline && (status[1] & 0x60) == 0x60) {
                    if (log != null) log.debug("UART TX", data.length + " byte drained, elapsed=" + elapsed(began));
                    return;
                }
                pauseUntil(Math.min(deadline, timing.now() + 2000000L), cancel);
            }
            cancel.check();
            throw failed("TX UART FT232R belum kosong dalam " + timeoutMillis + " ms; pengiriman berikutnya dibatalkan.", null);
        } catch (IOException | RuntimeException error) {
            // Never append another command to a transmission with unknown completion.
            // A cancelled operation can still have queued TX. Expose that state
            // even when the operation's normal callback has been revoked.
            if (attempted[0]) { transmitReady = false; failed = true; }
            if (attempted[0] && error instanceof ProtocolException && !(error instanceof TransportException)) {
                throw failed(error.getMessage(), error);
            }
            throw error;
        }
    }
    @Override public byte[] read(int maximumBytes, int timeoutMillis) throws IOException {
        requireOpen();
        if (maximumBytes <= 0) return new byte[0];
        if (pending != null && pending.size() != 0) return takePending(maximumBytes);
        int packets = (int) Math.min(4L, Math.max(1L, ((long) maximumBytes + packetSize - 3) / (packetSize - 2)));
        byte[] raw = new byte[packetSize * packets];
        long began = timing.now();
        int count = readUsb(raw, Math.max(1, timeoutMillis));
        requireOpen();
        if (count <= 0) {
            if (log != null) log.debug("USB RX", "bulk result=" + count + ", wait=" + timeoutMillis + " ms, elapsed=" + elapsed(began) + " (empty/timeout)");
            return new byte[0];
        }
        if (count > raw.length) throw failed("Panjang RX USB tidak valid.", null);
        if (log != null) log.wire("USB RX", "RAW", raw, count);
        try { pending = FtdiCodec.decodeStatus(raw, count, packetSize); }
        catch (ProtocolException error) { throw failed(error.getMessage(), error); }
        if (log != null && pending.size() == 0) log.debug("USB RX", "Status-only packet; no UART data.");
        return takePending(maximumBytes);
    }
    private byte[] takePending(int maximumBytes) throws FtdiCodec.UartException {
        int count = Math.min(maximumBytes, pending.size());
        FtdiCodec.RxData result = pending.slice(0, count);
        pending = pending.slice(count, pending.size());
        return result.cleanBytes();
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
