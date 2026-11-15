package id.hondatuner.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Arrays;
import java.util.Objects;

/** Honda frames, optional/required adapter echo, bounded replies and serialized dispatch. */
public final class KLineTransport implements AutoCloseable {
    public interface NanoClock { long now(); }
    public enum EchoMode { AUTO, REQUIRED, NONE }
    private final ByteChannel channel;
    private final NanoClock clock;
    private final int timeoutMillis;
    private final EchoMode echoMode;
    private final DiagnosticLog log;

    public KLineTransport(ByteChannel channel) { this(channel, System::nanoTime, 500); }
    public KLineTransport(ByteChannel channel, NanoClock clock, int timeoutMillis) {
        this(channel, clock, timeoutMillis, EchoMode.REQUIRED, null);
    }
    public KLineTransport(ByteChannel channel, NanoClock clock, int timeoutMillis, EchoMode echoMode, DiagnosticLog log) {
        if (timeoutMillis <= 0) throw new IllegalArgumentException("Timeout harus positif.");
        this.channel = Objects.requireNonNull(channel);
        this.clock = Objects.requireNonNull(clock);
        this.timeoutMillis = timeoutMillis;
        this.echoMode = Objects.requireNonNull(echoMode); this.log = log;
        if (log != null) log.info("KLINE", "Transport: echo=" + echoMode + ", timeout=" + timeoutMillis + " ms");
    }

    public synchronized void initialize(CancelSignal cancel) throws IOException {
        cancel.check();
        if (log != null) log.info("KLINE", "Inisialisasi adaptor / pulsa wake-up");
        channel.initialize(cancel);
    }

    public static byte[] command(int target, byte... payload) {
        if (target < 0 || target > 255 || payload.length > 252) throw new IllegalArgumentException("Frame Honda tidak valid.");
        byte[] frame = new byte[payload.length + 3];
        frame[0] = (byte) target;
        frame[1] = (byte) frame.length;
        System.arraycopy(payload, 0, frame, 2, payload.length);
        int sum = 0;
        for (int i = 0; i < frame.length - 1; i++) sum += frame[i] & 255;
        frame[frame.length - 1] = (byte) -sum;
        return frame;
    }

    public static final class Packet {
        public final int totalLength;
        private final byte[] payload;
        private Packet(byte[] frame) {
            totalLength = frame.length;
            payload = Arrays.copyOfRange(frame, 2, frame.length - 1);
        }
        public byte[] payload() { return payload.clone(); }
    }

    public static Packet parseReply(int target, byte[] frame) throws ProtocolException {
        if (frame.length < 3 || (frame[0] & 255) != (target & 15) || (frame[1] & 255) != frame.length) {
            throw new ProtocolException("Header atau panjang balasan ECU tidak cocok.");
        }
        int sum = 0;
        for (byte value : frame) sum += value & 255;
        if ((sum & 255) != 0) throw new ProtocolException("Checksum balasan ECU tidak valid.");
        return new Packet(frame);
    }

    public synchronized Packet exchange(int target, byte[] payload, boolean noReturn, CancelSignal cancel) throws IOException {
        byte[] request = command(target, payload);
        IOException last = null;
        // A one-way FE/FF wake-up is sent once, even when its echo fails.
        for (int attempt = 0; attempt < (noReturn ? 1 : 2); attempt++) {
            cancel.check();
            if (log != null) log.debug("KLINE", "Transaksi " + Integer.toHexString(target) + ", coba " + (attempt + 1) + ", one-way=" + noReturn);
            try { return exchangeOnce(target, request, noReturn, cancel); }
            catch (InterruptedIOException error) { throw error; }
            catch (IOException error) { last = error; if (log != null) log.warn("KLINE", error.getMessage()); }
        }
        throw last;
    }

    private Packet exchangeOnce(int target, byte[] request, boolean noReturn, CancelSignal cancel) throws IOException {
        channel.purge();
        int receiveTimeout = noReturn && echoMode != EchoMode.REQUIRED ? Math.min(60, timeoutMillis) : timeoutMillis;
        long began = clock.now();
        if (log != null) log.wire("KLINE", "TX", request, request.length);
        channel.write(request, timeoutMillis, cancel);
        // USB acceptance is not UART completion. The channel drains TX before
        // returning, then the reply gets its entire receive window.
        long deadline = clock.now() + receiveTimeout * 1000000L;
        if (noReturn && echoMode != EchoMode.REQUIRED) {
            drainWake(deadline, cancel);
            return null;
        }
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Boolean echoed = echoMode == EchoMode.REQUIRED ? Boolean.TRUE : echoMode == EchoMode.NONE ? Boolean.FALSE : null;
        while (clock.now() < deadline) {
            cancel.check();
            long remaining = deadline - clock.now();
            if (remaining <= 0) break;
            int wait = (int) Math.min(50, Math.max(1, (remaining + 999999) / 1000000));
            byte[] piece = channel.read(256, wait);
            cancel.check();
            if (clock.now() >= deadline) break;
            if (piece.length == 0) continue;
            if (log != null) log.wire("KLINE", "RX UART", piece, piece.length);
            captured.write(piece, 0, piece.length);
            if (captured.size() > 510) throw new ProtocolException("Balasan ECU melebihi batas frame.");
            byte[] bytes = captured.toByteArray();
            if (echoed == null) {
                if ((bytes[0] & 255) == (request[0] & 255)) echoed = Boolean.TRUE;
                else if ((bytes[0] & 255) == (target & 15)) echoed = Boolean.FALSE;
                else if (!noReturn) throw new ProtocolException("Awal RX bukan echo atau header ECU: " + DiagnosticLog.hex(bytes, Math.min(bytes.length, 8)));
                if (log != null && echoed != null) log.debug("KLINE", "Echo terdeteksi: " + echoed);
            }
            if (Boolean.TRUE.equals(echoed)) {
                for (int i = 0; i < Math.min(bytes.length, request.length); i++) {
                    if (bytes[i] != request[i]) throw new ProtocolException("Echo adaptor tidak cocok dengan perintah saat ini.");
                }
                if (noReturn && bytes.length >= request.length) {
                    channel.purge();
                    return null;
                }
                if (bytes.length <= request.length) continue;
            }
            if (noReturn) continue;
            byte[] reply = Boolean.TRUE.equals(echoed) ? Arrays.copyOfRange(bytes, request.length, bytes.length) : bytes;
            if ((reply[0] & 255) != (target & 15)) throw new ProtocolException("Balasan bukan untuk target Honda saat ini.");
            if (reply.length >= 2) {
                int length = reply[1] & 255;
                if (length < 3 || reply.length > length) throw new ProtocolException("Panjang balasan ECU tidak valid.");
                if (reply.length == length) {
                    Packet packet = parseReply(target, reply);
                    if (log != null) log.debug("KLINE", "RX valid " + length + " byte, echo=" + echoed + ", " + (clock.now() - began) / 1000000L + " ms");
                    return packet;
                }
            }
        }
        cancel.check();
        byte[] bytes = captured.toByteArray();
        channel.purge();
        String detail;
        if (bytes.length == 0) detail = "Tidak ada RX dari adaptor/ECU";
        else if (Boolean.TRUE.equals(echoed) && bytes.length == request.length) detail = "Echo TX diterima, tetapi ECU belum membalas";
        else if (Boolean.TRUE.equals(echoed) && bytes.length < request.length) detail = "Echo TX terpotong (" + bytes.length + "/" + request.length + " byte)";
        else detail = "Balasan ECU terpotong (RX " + bytes.length + " byte)";
        throw new ProtocolException(detail + " dalam " + receiveTimeout + " ms. Periksa kontak, daya dan kabel K-Line.");
    }

    private void drainWake(long deadline, CancelSignal cancel) throws IOException {
        // FE/FF is a one-way wake, not a sensor reply. Consume its whole bounded
        // RX window even if the first loopback byte is FC or has a framing error.
        // Data errors stay strict on the subsequent diagnostic entry and reads.
        while (clock.now() < deadline) {
            cancel.check();
            long remaining = deadline - clock.now();
            if (remaining <= 0) break;
            int wait = (int) Math.min(50, Math.max(1, (remaining + 999999) / 1000000));
            try {
                byte[] piece = channel.read(256, wait);
                cancel.check();
                if (log != null && piece.length != 0) log.wire("WAKE", "RX opsional dibuang", piece, piece.length);
            } catch (InterruptedIOException error) { throw error; }
            catch (FtdiCodec.UartException error) {
                cancel.check();
                if (log != null) log.warn("WAKE", "RX wake dibuang: " + error.getMessage());
            }
        }
        cancel.check();
        channel.purge();
        if (log != null) log.debug("KLINE", "Wake TX selesai dan RX dibuang; ACK ECU wajib pada diagnostic entry berikutnya.");
    }

    @Override public void close() { channel.close(); }
}
