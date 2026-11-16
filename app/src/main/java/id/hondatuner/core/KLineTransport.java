package id.hondatuner.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Arrays;
import java.util.Objects;

/** Honda frames, optional/required adapter echo, bounded replies and serialized dispatch. */
public final class KLineTransport implements AutoCloseable {
    // Three 8 ms FTDI latency intervals, matching wake cleanup. A completed
    // frame must settle so a later USB fragment cannot hide trailing data.
    private static final int REPLY_QUIET_MILLIS = 24;
    public interface NanoClock { long now(); }
    public enum EchoMode { AUTO, REQUIRED, NONE }
    private final ByteChannel channel;
    private final NanoClock clock;
    private final int timeoutMillis;
    private final EchoMode echoMode;
    private final DiagnosticLog log;
    private volatile boolean closed;
    private volatile TransportException failure;
    // Wire operations are serialized, but cancellation can leave an ECU reply
    // in flight. Keep its original deadline before allowing a different command.
    private long discardUntil;

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
        requireHealthy();
        try {
            discardPrevious(cancel);
            if (log != null) log.info("KLINE", "Inisialisasi adaptor / pulsa wake-up");
            channel.initialize(cancel);
            requireHealthy();
        } catch (IOException error) { throw observeFailure(error); }
    }

    public boolean isHealthy() { return !closed && failure == null && channel.isHealthy(); }
    private void requireHealthy() throws TransportException {
        if (failure != null) throw failure;
        if (closed || !channel.isHealthy()) throw new TransportException("Kanal USB tidak siap; hubungkan ulang adaptor.");
    }
    private IOException observeFailure(IOException error) {
        if (error instanceof InterruptedIOException) {
            if (!channel.isHealthy()) failure = new TransportException("Kanal USB gagal saat operasi dibatalkan.", error);
            return error;
        }
        if (error instanceof TransportException) failure = (TransportException) error;
        else if (!(error instanceof ProtocolException) || !channel.isHealthy()) {
            failure = new TransportException("Komunikasi USB gagal: " + error.getMessage(), error);
        }
        return failure == null ? error : failure;
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
            requireHealthy();
            if (log != null) log.debug("KLINE", "Transaksi " + Integer.toHexString(target) + ", coba " + (attempt + 1) + ", one-way=" + noReturn);
            try {
                discardPrevious(cancel);
                return exchangeOnce(target, request, noReturn, cancel);
            } catch (IOException error) {
                last = observeFailure(error);
                if (last instanceof InterruptedIOException || last instanceof TransportException) throw last;
                if (log != null) log.warn("KLINE", last.getMessage());
            }
        }
        throw last;
    }

    private Packet exchangeOnce(int target, byte[] request, boolean noReturn, CancelSignal cancel) throws IOException {
        channel.purge();
        int receiveTimeout = noReturn ? Math.min(60, timeoutMillis) : timeoutMillis;
        long began = clock.now();
        TransactionTrace trace = new TransactionTrace(request, began, receiveTimeout);
        try {
            cancel.check(); requireHealthy();
            if (log != null) log.wire("KLINE", "TX", request, request.length);
            channel.write(request, timeoutMillis, cancel);
            // USB acceptance is not UART completion. Start RX after the channel
            // confirms TX drained, and retain this window if reception is cancelled.
            long deadline = clock.now() + receiveTimeout * 1000000L;
            discardUntil = deadline;
            trace.receiveBegan = clock.now();
            if (noReturn && echoMode != EchoMode.REQUIRED) {
                drainWake(deadline, cancel, trace);
                discardUntil = 0;
                trace.outcome = "wake_sent";
                return null;
            }
            String detail = null;
            ReplyChoice candidate = null;
            long quietUntil = deadline;
            while (clock.now() < deadline) {
                cancel.check(); requireHealthy();
                if (candidate != null && clock.now() >= quietUntil) return complete(candidate, trace, cancel);
                byte[] piece;
                byte[] statuses = null;
                try { piece = channel.read(256, waitMillis(candidate == null ? deadline : Math.min(deadline, quietUntil))); }
                catch (FtdiCodec.UartException error) {
                    cancel.check();
                    // Retain the bad packet's bytes and positions. AUTO can
                    // tolerate faults in exactly the TX-length echo prefix,
                    // but never in a byte of the selected ECU frame.
                    trace.uart(error);
                    piece = error.samples().data();
                    statuses = error.samples().lineStatuses();
                }
                cancel.check(); requireHealthy();
                if (piece == null) throw new TransportException("Driver USB tidak mengembalikan RX yang valid.");
                trace.capture(piece, statuses);
                if (piece.length != 0) candidate = null;
                if (clock.now() >= deadline) break;
                if (piece.length == 0) continue;
                if (log != null) log.wire("KLINE", "RX UART", piece, piece.length);
                if (trace.received > 510) throw new ProtocolException("Balasan ECU melebihi batas frame.");
                byte[] bytes = trace.rx.toByteArray();
                if (noReturn) {
                    if (!prefixMatches(bytes, request)) {
                        detail = "Echo adaptor tidak cocok dengan perintah saat ini.";
                        trace.first(detail);
                    } else if (bytes.length >= request.length && !trace.hasUartErrors(0, 0x1e)) {
                        trace.echo = "exact";
                        drainWake(deadline, cancel, trace);
                        discardUntil = 0;
                        trace.outcome = "wake_sent";
                        return null;
                    }
                    continue;
                }
                ReplyChoice choice = chooseReply(target, request, bytes);
                if (choice.packet != null) {
                    detail = trace.replyUartFailure(choice.echo);
                    if (detail == null) {
                        candidate = choice;
                        quietUntil = Math.min(deadline, clock.now() + REPLY_QUIET_MILLIS * 1000000L);
                        continue;
                    }
                    trace.first(detail);
                }
                if (choice.detail != null) {
                    detail = choice.detail;
                    trace.first(choice.detail);
                }
            }
            cancel.check(); requireHealthy();
            // A frame received before the deadline remains valid if the rest of
            // the available window is empty, even if less than 24 ms remained.
            if (candidate != null) return complete(candidate, trace, cancel);
            channel.purge();
            discardUntil = 0;
            byte[] bytes = trace.rx.toByteArray();
            if (trace.hasUartErrors(0, 0x1e)) detail = "RX ditolak: " + (detail == null ? "belum ada frame ECU bersih yang valid." : detail) + " UART=" + trace.uartStatus;
            else if (bytes.length == 0) detail = "Tidak ada RX dari adaptor/ECU";
            else if (Arrays.equals(bytes, request) && echoMode != EchoMode.NONE) detail = "Echo TX diterima, tetapi ECU belum membalas";
            else if (prefixMatches(bytes, request) && bytes.length < request.length && echoMode != EchoMode.NONE) detail = "Echo TX terpotong (" + bytes.length + "/" + request.length + " byte)";
            else if (detail == null) detail = "Balasan ECU terpotong (RX " + bytes.length + " byte)";
            throw new ProtocolException(detail + " dalam " + receiveTimeout + " ms.");
        } catch (IOException error) {
            trace.first(error.getMessage());
            trace.outcome = error instanceof InterruptedIOException ? "cancelled" : "failed";
            throw error;
        } finally { trace.publish(); }
    }

    private Packet complete(ReplyChoice choice, TransactionTrace trace, CancelSignal cancel) throws IOException {
        cancel.check(); requireHealthy();
        trace.echo = choice.echo; trace.outcome = "valid";
        trace.uartScope = trace.hasUartErrors(0, 0x1e) ? "echo-only" : "none";
        if (log != null) log.debug("KLINE", "Echo terdeteksi: " + !"none".equals(choice.echo) + "; kualitas=" + choice.echo);
        discardUntil = 0;
        return choice.packet;
    }

    private static boolean prefixMatches(byte[] bytes, byte[] request) {
        for (int i = 0; i < Math.min(bytes.length, request.length); i++) if (bytes[i] != request[i]) return false;
        return true;
    }
    private static final class ReplyChoice {
        final Packet packet;
        final String echo, detail;
        ReplyChoice(Packet packet, String echo, String detail) { this.packet = packet; this.echo = echo; this.detail = detail; }
    }
    private static ReplyChoice replyAt(int target, byte[] bytes, int offset) {
        int available = bytes.length - offset;
        if (available <= 0) return new ReplyChoice(null, null, null);
        if ((bytes[offset] & 255) != (target & 15)) return new ReplyChoice(null, null, "Balasan bukan untuk target Honda saat ini.");
        if (available < 2) return new ReplyChoice(null, null, null);
        int length = bytes[offset + 1] & 255;
        if (length < 3 || available > length) return new ReplyChoice(null, null, "Panjang balasan ECU tidak valid.");
        if (available < length) return new ReplyChoice(null, null, null);
        try { return new ReplyChoice(parseReply(target, Arrays.copyOfRange(bytes, offset, bytes.length)), null, null); }
        catch (ProtocolException error) { return new ReplyChoice(null, null, error.getMessage()); }
    }
    private ReplyChoice chooseReply(int target, byte[] request, byte[] bytes) {
        if (echoMode == EchoMode.NONE) {
            ReplyChoice direct = replyAt(target, bytes, 0);
            return new ReplyChoice(direct.packet, "none", direct.detail);
        }
        if (echoMode == EchoMode.REQUIRED) {
            if (!prefixMatches(bytes, request)) return new ReplyChoice(null, "different", "Echo adaptor tidak cocok dengan perintah saat ini.");
            ReplyChoice echoed = replyAt(target, bytes, request.length);
            return new ReplyChoice(echoed.packet, "exact", echoed.detail);
        }
        ReplyChoice direct = replyAt(target, bytes, 0);
        ReplyChoice echoed = replyAt(target, bytes, request.length);
        // A complete ECU frame followed by more input cannot be relabelled as
        // damaged echo. In particular, two replies may align at the TX length.
        if (bytes.length >= 3 && (bytes[0] & 255) == (target & 15)) {
            int leadingLength = bytes[1] & 255;
            if (leadingLength >= 3 && leadingLength < bytes.length) {
                try {
                    parseReply(target, Arrays.copyOfRange(bytes, 0, leadingLength));
                    return new ReplyChoice(null, "ambiguous", "RX berisi frame ECU lengkap diikuti data tambahan.");
                } catch (ProtocolException ignored) { /* not a valid leading reply */ }
            }
        }
        if (direct.packet != null && echoed.packet != null) return new ReplyChoice(null, "ambiguous", "Dua bentuk balasan ECU sama-sama valid; transaksi ambigu.");
        if (direct.packet != null) return new ReplyChoice(direct.packet, "none", null);
        if (echoed.packet != null) return new ReplyChoice(echoed.packet, prefixMatches(bytes, request) ? "exact" : "different", null);
        if (prefixMatches(bytes, request)) return new ReplyChoice(null, null, echoed.detail);
        if (direct.detail == null) return new ReplyChoice(null, null, null);
        String detail = bytes.length > request.length && echoed.detail != null ? echoed.detail : direct.detail;
        if (detail == null && !prefixMatches(bytes, request) && bytes.length >= request.length) detail = "Prefix echo berbeda; menunggu frame ECU lengkap.";
        return new ReplyChoice(null, null, detail);
    }

    private int waitMillis(long deadline) {
        long remaining = deadline - clock.now();
        return (int) Math.min(50, Math.max(1, (remaining + 999999L) / 1000000L));
    }
    private void discardPrevious(CancelSignal cancel) throws IOException {
        if (discardUntil == 0) return;
        long began = clock.now();
        int discarded = 0;
        while (clock.now() < discardUntil) {
            cancel.check(); requireHealthy();
            try {
                byte[] piece = channel.read(256, waitMillis(discardUntil));
                if (piece == null) throw new TransportException("Driver USB tidak mengembalikan RX yang valid.");
                discarded += piece.length;
                if (log != null && piece.length != 0) log.wire("RX DISCARD", "Transaksi sebelumnya", piece, piece.length);
            } catch (FtdiCodec.UartException error) {
                discarded += error.samples().size();
                if (log != null) log.warn("RX DISCARD", error.getMessage());
            }
            cancel.check(); requireHealthy();
        }
        cancel.check(); requireHealthy();
        channel.purge();
        discardUntil = 0;
        if (log != null) log.info("RX DISCARD", "Jendela RX sebelumnya selesai; dibuang=" + discarded + " byte, tunggu=" + (clock.now() - began) / 1000000L + " ms.");
    }

    private void drainWake(long deadline, CancelSignal cancel, TransactionTrace trace) throws IOException {
        // FE/FF is a one-way wake, not a sensor reply. Consume its whole bounded
        // RX window even if the first loopback byte is FC or has a framing error.
        // Data errors stay strict on the subsequent diagnostic entry and reads.
        while (clock.now() < deadline) {
            cancel.check(); requireHealthy();
            try {
                byte[] piece = channel.read(256, waitMillis(deadline));
                cancel.check(); requireHealthy();
                if (piece == null) throw new TransportException("Driver USB tidak mengembalikan RX yang valid.");
                trace.capture(piece);
                if (log != null && piece.length != 0) log.wire("WAKE", "RX opsional dibuang", piece, piece.length);
            } catch (InterruptedIOException error) { throw error; }
            catch (FtdiCodec.UartException error) {
                cancel.check(); trace.uart(error);
                trace.capture(error.samples().data(), error.samples().lineStatuses());
                if (log != null) log.warn("WAKE", "RX wake dibuang: " + error.getMessage());
            }
        }
        cancel.check(); requireHealthy();
        channel.purge();
        if (log != null) log.debug("KLINE", "Wake TX selesai dan RX dibuang; ACK ECU wajib pada diagnostic entry berikutnya.");
    }

    private final class TransactionTrace {
        final byte[] request;
        final long began;
        final int receiveTimeout;
        final ByteArrayOutputStream rx = new ByteArrayOutputStream();
        final ByteArrayOutputStream statuses = new ByteArrayOutputStream();
        long receiveBegan = -1;
        int received;
        String echo = "undetermined", uartStatus = "no-error-reported", firstFailure, outcome = "failed";
        String uartScope = "none";
        TransactionTrace(byte[] request, long began, int receiveTimeout) { this.request = request; this.began = began; this.receiveTimeout = receiveTimeout; }
        void first(String reason) { if (firstFailure == null && reason != null) firstFailure = reason; }
        void capture(byte[] piece) { capture(piece, null); }
        void capture(byte[] piece, byte[] flags) {
            if (flags != null && flags.length != piece.length) throw new IllegalArgumentException("UART status/sample count differs");
            received += piece.length;
            int kept = Math.min(piece.length, 510 - rx.size());
            rx.write(piece, 0, kept);
            for (int index = 0; index < kept; index++) statuses.write(flags == null ? 0 : flags[index] & 255);
        }
        void uart(FtdiCodec.UartException error) {
            uartStatus = "LSR 0x" + Integer.toHexString(error.lineStatus);
            uartScope = "unclassified";
            first(error.getMessage());
        }
        boolean hasUartErrors(int start, int mask) {
            byte[] flags = statuses.toByteArray();
            for (int index = start; index < flags.length; index++) if ((flags[index] & mask) != 0) return true;
            return false;
        }
        String replyUartFailure(String selectedEcho) {
            if (!hasUartErrors(0, 0x1e)) return null;
            if (echoMode != EchoMode.AUTO || "none".equals(selectedEcho)) {
                uartScope = echoMode == EchoMode.REQUIRED ? "required-echo-or-frame" : "ecu-frame";
                return "Error UART pada RX wajib bersih; balasan ECU tidak digunakan.";
            }
            // Overrun may have lost bytes, so even a TX-length prefix cannot
            // establish a reliable echo/frame boundary in that case.
            if (hasUartErrors(0, 0x02)) {
                uartScope = "overrun";
                return "UART overrun; batas echo dan frame ECU tidak dapat dipastikan.";
            }
            if (hasUartErrors(request.length, 0x1e)) {
                uartScope = "ecu-frame";
                return "Error UART mengenai frame ECU; balasan tidak digunakan.";
            }
            return null;
        }
        String uartRanges() {
            byte[] flags = statuses.toByteArray();
            StringBuilder ranges = new StringBuilder();
            for (int start = 0; start < flags.length;) {
                if ((flags[start] & 0x1e) == 0) { start++; continue; }
                int end = start + 1;
                while (end < flags.length && flags[end] == flags[start]) end++;
                if (ranges.length() != 0) ranges.append(',');
                ranges.append('[').append(start).append(',').append(end).append("):0x").append(Integer.toHexString(flags[start] & 255));
                start = end;
            }
            return ranges.length() == 0 ? "none" : ranges.toString();
        }
        void publish() {
            if (log == null) return;
            byte[] bytes = rx.toByteArray();
            if ("undetermined".equals(echo)) {
                if (bytes.length != 0 && prefixMatches(bytes, request)) echo = bytes.length >= request.length ? "exact" : "incomplete";
                else if (bytes.length >= request.length) echo = "different-or-absent";
            }
            String txText = log.isDetailed() ? DiagnosticLog.hex(request, request.length) : request.length + " byte";
            String rxText = log.isDetailed() ? DiagnosticLog.hex(bytes, bytes.length) : received + " byte";
            log.info("TRANSACTION", "result=" + outcome + "; TX=" + txText + "; RX=" + rxText + "; rx_bytes=" + received +
                    "; echo=" + echo + "; UART=" + uartStatus + "; uart_scope=" + uartScope + "; uart_error_ranges=" + uartRanges() + "; receive_wait=" + (receiveBegan < 0 ? 0 : (clock.now() - receiveBegan) / 1000000L) +
                    "/" + receiveTimeout + " ms; frame_quiet=" + REPLY_QUIET_MILLIS + " ms; elapsed=" + (clock.now() - began) / 1000000L + " ms; first_failure=" + (firstFailure == null ? "none" : firstFailure));
        }
    }

    @Override public void close() { closed = true; channel.close(); }
}
