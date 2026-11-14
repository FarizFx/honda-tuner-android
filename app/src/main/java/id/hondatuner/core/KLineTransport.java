package id.hondatuner.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Arrays;

/** Honda frames, complete adapter echo, bounded replies and serialized dispatch. */
public final class KLineTransport implements AutoCloseable {
    public interface NanoClock { long now(); }
    private final ByteChannel channel;
    private final NanoClock clock;
    private final int timeoutMillis;

    public KLineTransport(ByteChannel channel) { this(channel, System::nanoTime, 500); }
    public KLineTransport(ByteChannel channel, NanoClock clock, int timeoutMillis) {
        if (timeoutMillis <= 0) throw new IllegalArgumentException("Timeout harus positif.");
        this.channel = channel;
        this.clock = clock;
        this.timeoutMillis = timeoutMillis;
    }

    public synchronized void initialize(CancelSignal cancel) throws IOException {
        cancel.check();
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
            try { return exchangeOnce(target, request, noReturn, cancel); }
            catch (InterruptedIOException error) { throw error; }
            catch (IOException error) { last = error; }
        }
        throw last;
    }

    private Packet exchangeOnce(int target, byte[] request, boolean noReturn, CancelSignal cancel) throws IOException {
        channel.purge();
        long deadline = clock.now() + timeoutMillis * 1000000L;
        channel.write(request, timeoutMillis, cancel);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        while (clock.now() < deadline) {
            cancel.check();
            long remaining = deadline - clock.now();
            if (remaining <= 0) break;
            int wait = (int) Math.min(50, Math.max(1, (remaining + 999999) / 1000000));
            byte[] piece = channel.read(256, wait);
            cancel.check();
            if (clock.now() >= deadline) break;
            if (piece.length == 0) continue;
            captured.write(piece, 0, piece.length);
            if (captured.size() > 510) throw new ProtocolException("Balasan ECU melebihi batas frame.");
            byte[] bytes = captured.toByteArray();
            for (int i = 0; i < Math.min(bytes.length, request.length); i++) {
                if (bytes[i] != request[i]) throw new ProtocolException("Echo adaptor tidak cocok dengan perintah saat ini.");
            }
            if (noReturn && bytes.length >= request.length) {
                channel.purge();
                return null; // successful transmission, no ECU acknowledgement claimed
            }
            if (bytes.length <= request.length) continue;
            byte[] reply = Arrays.copyOfRange(bytes, request.length, bytes.length);
            if ((reply[0] & 255) != (target & 15)) throw new ProtocolException("Balasan bukan untuk target Honda saat ini.");
            if (reply.length >= 2) {
                int length = reply[1] & 255;
                if (length < 3 || reply.length > length) throw new ProtocolException("Panjang balasan ECU tidak valid.");
                if (reply.length == length) return parseReply(target, reply);
            }
        }
        channel.purge();
        throw new ProtocolException("ECU tidak membalas lengkap. Periksa kontak, daya, dan kabel K-Line.");
    }

    @Override public void close() { channel.close(); }
}
