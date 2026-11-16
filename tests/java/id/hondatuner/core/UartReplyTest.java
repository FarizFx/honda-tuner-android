package id.hondatuner.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/** Packet-status regressions and offline replay of the user's 1.1.3 USB recording. */
public final class UartReplyTest {
    private interface Checked { void run() throws Exception; }
    private interface Responder { void respond(Channel channel, byte[] request, long began); }
    private static int passed;
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void bytes(byte[] expected, byte[] actual) { check(Arrays.equals(expected, actual), "UART bytes or reply differ"); }
    private static void run(String name, Checked action) throws Exception { action.run(); passed++; System.out.println("OK " + name); }
    private static IOException failure(Checked action) throws Exception {
        try { action.run(); } catch (IOException expected) { return expected; }
        throw new AssertionError("Expected I/O rejection");
    }
    private static byte[] hex(String value) {
        check(value.length() % 2 == 0, "Odd hex length");
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return result;
    }
    private static String hex(byte[] value) { return DiagnosticLog.hex(value, value.length).replace(" ", "").toLowerCase(java.util.Locale.ROOT); }
    private static byte[] join(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length); return result;
    }
    private static byte[] raw(byte[] data, int status) { return join(new byte[]{1, (byte) status}, data); }
    private static byte[] ack(byte... payload) { return KLineTransport.command(2, payload); }
    private static final byte[] DAMAGED_ECHO = hex("600000e010");
    private static final class Event {
        final long at;
        final byte[] raw;
        Event(long at, byte[] raw) { this.at = at; this.raw = raw.clone(); }
    }
    private static final class Channel implements ByteChannel, KLineTransport.NanoClock {
        final PriorityQueue<Event> events = new PriorityQueue<>(Comparator.comparingLong(event -> event.at));
        final List<byte[]> writes = new ArrayList<>();
        final List<Long> writeTimes = new ArrayList<>();
        long time, txMillis = 6;
        int initializations;
        Runnable afterRead;
        Responder responder = (channel, request, began) -> {
            channel.at(began + 7, raw(DAMAGED_ECHO, 0x68));
            channel.at(began + 20, raw(ack((byte) 0), 0x60));
        };
        @Override public long now() { return time * 1000000L; }
        void at(long at, byte[] packet) { events.add(new Event(at, packet)); }
        @Override public void initialize(CancelSignal cancel) throws IOException { cancel.check(); initializations++; }
        @Override public void purge() { while (!events.isEmpty() && events.peek().at <= time) events.remove(); }
        @Override public void write(byte[] request, int timeout, CancelSignal cancel) throws IOException {
            cancel.check(); writes.add(request.clone()); writeTimes.add(time);
            responder.respond(this, request.clone(), time); time += txMillis;
        }
        @Override public byte[] read(int maximum, int timeout) throws IOException {
            long until = time + timeout;
            if (events.isEmpty() || events.peek().at > until) {
                time = until; if (afterRead != null) afterRead.run(); return new byte[0];
            }
            Event event = events.remove(); time = Math.max(time, event.at);
            if (afterRead != null) afterRead.run();
            byte[] data = FtdiCodec.stripStatus(event.raw, event.raw.length, 64);
            check(data.length <= maximum, "Replay event exceeds requested UART bound"); return data;
        }
        @Override public void close() { }
        KLineTransport transport(KLineTransport.EchoMode mode, DiagnosticLog log) { return new KLineTransport(this, this, 100, mode, log); }
    }
    private static KLineTransport.Packet exchange(Channel channel, KLineTransport.EchoMode mode, DiagnosticLog log) throws IOException {
        return channel.transport(mode, log).exchange(0x72, hex("00f0"), false, CancelSignal.NEVER);
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 0 && args[0].equals("--fixtures")) { fixtures(); return; }
        run("AUTO tolerates framing error confined to the full echo", () -> echoError(0x68));
        run("AUTO tolerates parity error confined to the full echo", () -> echoError(0x64));
        run("AUTO tolerates BREAK error confined to the full echo", () -> echoError(0x70));
        run("fragmented damaged echo retains its exact byte count", UartReplyTest::fragmentedEcho);
        run("UART-marked exact echo is accepted only in AUTO", UartReplyTest::exactEcho);
        run("REQUIRED rejects UART-marked echo even when its bytes match", UartReplyTest::requiredEcho);
        run("NONE cannot hide a UART-marked echo prefix", UartReplyTest::noEchoMode);
        run("valid direct ECU frame with UART error is rejected", UartReplyTest::directError);
        run("UART error on the final ECU checksum byte is rejected", UartReplyTest::checksumUartError);
        run("a packet crossing the echo/frame boundary taints ECU bytes", UartReplyTest::crossingPacket);
        run("coalesced USB packets retain clean reply after a bad full packet", UartReplyTest::coalescedPackets);
        run("overrun on echo cannot establish a trustworthy frame boundary", UartReplyTest::overrun);
        run("short damaged echo cannot shift the ECU frame boundary", UartReplyTest::shortEcho);
        run("bad checksum after UART-marked echo remains rejected", UartReplyTest::badChecksum);
        run("wrong ECU target after UART-marked echo remains rejected", UartReplyTest::wrongTarget);
        run("extra UART-marked data in the quiet window remains rejected", UartReplyTest::extraData);
        run("frame-shaped UART-marked prefix remains ambiguous", UartReplyTest::ambiguousPrefix);
        run("truncated USB status after a faulty packet is still malformed", UartReplyTest::truncatedStatus);
        run("codec retains immutable bytes and packet statuses across errors", UartReplyTest::retainedSamples);
        run("status-only BREAK cannot taint the next direct ECU frame", UartReplyTest::statusOnly);
        run("cancellation during damaged echo retains the old RX deadline", UartReplyTest::cancelledEcho);
        run("five-byte identity is recovered after UART-marked seven-byte echo", UartReplyTest::identity);
        System.out.println("PASS: " + passed + " UART reply regression tests");
    }
    private static void echoError(int status) throws Exception {
        Channel c = new Channel(); DiagnosticLog log = new DiagnosticLog();
        c.responder = (channel, request, began) -> { channel.at(began + 7, raw(DAMAGED_ECHO, status)); channel.at(began + 20, raw(ack((byte) 0), 0x60)); };
        bytes(new byte[]{0}, exchange(c, KLineTransport.EchoMode.AUTO, log).payload());
        check(c.writes.size() == 1 && c.time == 44, "Clean ECU response was preempted or retried");
        String trace = log.text(0);
        check(trace.contains("RX=60 00 00 E0 10 02 04 00 FA") && trace.contains("rx_bytes=9") && trace.contains("uart_scope=echo-only") && trace.contains("uart_error_ranges=[0,5):0x" + Integer.toHexString(status)), "Echo error provenance lost");
    }
    private static void fragmentedEcho() throws Exception {
        Channel c = new Channel(); c.responder = (channel, request, began) -> {
            channel.at(began + 7, raw(Arrays.copyOf(DAMAGED_ECHO, 2), 0x68));
            channel.at(began + 10, raw(Arrays.copyOfRange(DAMAGED_ECHO, 2, 5), 0x60));
            channel.at(began + 20, raw(ack((byte) 0), 0x60));
        };
        bytes(new byte[]{0}, exchange(c, KLineTransport.EchoMode.AUTO, null).payload()); check(c.writes.size() == 1, "Fragmented echo retried");
    }
    private static void exactEcho() throws Exception {
        Channel c = new Channel(); c.responder = (channel, request, began) -> { channel.at(began + 7, raw(request, 0x68)); channel.at(began + 20, raw(ack((byte) 0), 0x60)); };
        bytes(new byte[]{0}, exchange(c, KLineTransport.EchoMode.AUTO, null).payload());
    }
    private static void rejected(Channel c, KLineTransport.EchoMode mode) throws Exception {
        check(failure(() -> exchange(c, mode, null)) instanceof ProtocolException, "RX failure changed classification");
        check(c.writes.size() == 2 && c.writeTimes.get(1) == 106 && c.time == 212, "Rejection shortened or exceeded receive deadlines");
    }
    private static void requiredEcho() throws Exception {
        Channel c = new Channel(); c.responder = (channel, request, began) -> { channel.at(began + 7, raw(request, 0x68)); channel.at(began + 20, raw(ack((byte) 0), 0x60)); };
        rejected(c, KLineTransport.EchoMode.REQUIRED);
    }
    private static void noEchoMode() throws Exception { rejected(new Channel(), KLineTransport.EchoMode.NONE); }
    private static void directError() throws Exception {
        for (KLineTransport.EchoMode mode : new KLineTransport.EchoMode[]{KLineTransport.EchoMode.AUTO, KLineTransport.EchoMode.NONE}) {
            Channel c = new Channel(); c.responder = (channel, request, began) -> channel.at(began + 20, raw(ack((byte) 0), 0x68)); rejected(c, mode);
        }
    }
    private static void checksumUartError() throws Exception {
        Channel c = new Channel(); c.responder = (channel, request, began) -> {
            byte[] reply = ack((byte) 0); channel.at(began + 7, raw(request, 0x60));
            channel.at(began + 20, raw(Arrays.copyOf(reply, 3), 0x60)); channel.at(began + 23, raw(new byte[]{reply[3]}, 0x68));
        }; rejected(c, KLineTransport.EchoMode.AUTO);
    }
    private static void crossingPacket() throws Exception {
        Channel c = new Channel(); c.responder = (channel, request, began) -> {
            byte[] reply = ack((byte) 0); channel.at(began + 7, raw(join(DAMAGED_ECHO, new byte[]{reply[0]}), 0x68));
            channel.at(began + 20, raw(Arrays.copyOfRange(reply, 1, 4), 0x60));
        }; rejected(c, KLineTransport.EchoMode.AUTO);
    }
    private static void coalescedPackets() throws Exception {
        Channel c = new Channel(); c.txMillis = 61; byte[] payload = new byte[59];
        c.responder = (channel, request, began) -> {
            byte[] echo = new byte[62]; Arrays.fill(echo, (byte) 0x60);
            channel.at(began + 63, join(raw(echo, 0x68), raw(ack((byte) 0), 0x60)));
        };
        bytes(new byte[]{0}, c.transport(KLineTransport.EchoMode.AUTO, null).exchange(0x72, payload, false, CancelSignal.NEVER).payload());
        check(c.writes.size() == 1 && c.time == 87, "Clean second USB packet was lost");
    }
    private static void overrun() throws Exception {
        Channel c = new Channel(); c.responder = (channel, request, began) -> { channel.at(began + 7, raw(DAMAGED_ECHO, 0x6a)); channel.at(began + 20, raw(ack((byte) 0), 0x60)); };
        rejected(c, KLineTransport.EchoMode.AUTO);
    }
    private static void shortEcho() throws Exception {
        Channel c = new Channel(); c.responder = (channel, request, began) -> { channel.at(began + 7, raw(Arrays.copyOf(DAMAGED_ECHO, 4), 0x68)); channel.at(began + 20, raw(ack((byte) 0), 0x60)); };
        rejected(c, KLineTransport.EchoMode.AUTO);
    }
    private static void badChecksum() throws Exception {
        Channel c = new Channel(); c.responder = (channel, request, began) -> { byte[] reply = ack((byte) 0); reply[3] ^= 1; channel.at(began + 7, raw(DAMAGED_ECHO, 0x68)); channel.at(began + 20, raw(reply, 0x60)); };
        rejected(c, KLineTransport.EchoMode.AUTO);
    }
    private static void wrongTarget() throws Exception {
        Channel c = new Channel(); c.responder = (channel, request, began) -> { channel.at(began + 7, raw(DAMAGED_ECHO, 0x68)); channel.at(began + 20, raw(KLineTransport.command(3, (byte) 0), 0x60)); };
        rejected(c, KLineTransport.EchoMode.AUTO);
    }
    private static void extraData() throws Exception {
        Channel c = new Channel(); c.responder = (channel, request, began) -> {
            channel.at(began + 7, raw(DAMAGED_ECHO, 0x68)); channel.at(began + 20, raw(ack((byte) 0), 0x60)); channel.at(began + 23, raw(new byte[]{0x55}, 0x68));
        }; rejected(c, KLineTransport.EchoMode.AUTO);
    }
    private static void ambiguousPrefix() throws Exception {
        Channel c = new Channel(); c.responder = (channel, request, began) -> { channel.at(began + 7, raw(hex("02050000f9"), 0x68)); channel.at(began + 20, raw(ack((byte) 0), 0x60)); };
        rejected(c, KLineTransport.EchoMode.AUTO);
    }
    private static void truncatedStatus() throws Exception {
        byte[] raw = new byte[65]; raw[0] = 1; raw[1] = 0x68; raw[64] = 1;
        IOException error = failure(() -> FtdiCodec.stripStatus(raw, raw.length, 64));
        check(!(error instanceof FtdiCodec.UartException) && error.getMessage().contains("Status USB FTDI terpotong"), "Earlier UART error hid malformed USB status");
    }
    private static void retainedSamples() throws Exception {
        byte[] first = new byte[62]; Arrays.fill(first, (byte) 0x60);
        byte[] usb = join(raw(first, 0x68), raw(ack((byte) 0), 0x60));
        FtdiCodec.UartException error = (FtdiCodec.UartException) failure(() -> FtdiCodec.stripStatus(usb, usb.length, 64));
        FtdiCodec.RxData samples = error.samples(); bytes(join(first, ack((byte) 0)), samples.data());
        byte[] flags = samples.lineStatuses(); check(flags.length == 66, "Packet samples were truncated");
        for (int i = 0; i < 66; i++) check((flags[i] & 255) == (i < 62 ? 0x68 : 0x60), "Error moved across packet boundary");
        usb[2] = 0; flags[62] = 0x68; byte[] exposed = samples.data(); exposed[62] = 0;
        bytes(ack((byte) 0), samples.slice(62, 66).cleanBytes());
        FtdiCodec.UartException prefix = (FtdiCodec.UartException) failure(() -> samples.slice(0, 5).cleanBytes());
        check(prefix.samples().size() == 5 && prefix.lineStatus == 0x68, "Slice lost UART status or bound");
    }
    private static void statusOnly() throws Exception {
        Channel c = new Channel(); c.responder = (channel, request, began) -> { channel.at(began + 7, new byte[]{1, 0x70}); channel.at(began + 20, raw(ack((byte) 0), 0x60)); };
        bytes(new byte[]{0}, exchange(c, KLineTransport.EchoMode.AUTO, null).payload());
    }
    private static void cancelledEcho() throws Exception {
        Channel c = new Channel(); AtomicBoolean cancelled = new AtomicBoolean(); KLineTransport transport = c.transport(KLineTransport.EchoMode.AUTO, null);
        c.afterRead = () -> { cancelled.set(true); c.afterRead = null; };
        check(failure(() -> transport.exchange(0x72, hex("00f0"), false, cancelled::get)) instanceof InterruptedIOException, "Cancellation during UART error was ignored");
        bytes(new byte[]{0}, transport.exchange(0x72, hex("00f0"), false, CancelSignal.NEVER).payload());
        check(c.writes.size() == 2 && c.writeTimes.get(1) == 106, "Old reply deadline was shortened");
    }
    private static void identity() throws Exception {
        Channel c = new Channel(); c.responder = (channel, request, began) -> {
            byte[] echo = new byte[request.length]; Arrays.fill(echo, (byte) 0x60); channel.at(began + 7, raw(echo, 0x68));
            if ((request[0] & 255) == 0xfe) return;
            channel.at(began + 20, raw(request[2] == 0x72 ? ack((byte) 0x72, (byte) 0, (byte) 5, (byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5) : ack((byte) 0), 0x60));
        };
        HondaClient client = new HondaClient(c.transport(KLineTransport.EchoMode.AUTO, null));
        check(client.connect(CancelSignal.NEVER).equals("0102030405") && client.identityStatus().equals("ID ECU terbaca: 0102030405"), "Clean ID was discarded");
        check(c.initializations == 1 && c.writes.size() == 3, "Identity query pulsed wake or retried valid RX");
        bytes(hex("72077200000510"), c.writes.get(2)); client.close();
    }
    private static String quote(String value) { return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + '"'; }
    private static void fixtures() throws Exception {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        for (String line; (line = input.readLine()) != null;) {
            String[] fields = line.split("\t", -1); check(fields.length == 7, "Invalid phone fixture row");
            Channel c = new Channel(); c.txMillis = Long.parseLong(fields[5]); String schedule = fields[6];
            c.responder = (channel, request, began) -> {
                if (!schedule.isEmpty()) for (String piece : schedule.split(";")) { String[] parts = piece.split(":", 2); channel.at(began + Long.parseLong(parts[0]), hex(parts[1])); }
            };
            KLineTransport transport = new KLineTransport(c, c, Integer.parseInt(fields[4]), KLineTransport.EchoMode.valueOf(fields[1]), null);
            byte[] payload = new byte[0]; IOException error = null;
            int fieldCount = 0;
            try {
                payload = transport.exchange(Integer.parseInt(fields[2]), hex(fields[3]), false, CancelSignal.NEVER).payload();
                if (payload.length >= 2 && payload[0] == 0x71) fieldCount = SensorDecoder.decode(payload[1] & 255, Arrays.copyOfRange(payload, 2, payload.length)).named().size();
            } catch (IOException failed) { error = failed; }
            System.out.println("{\"name\":" + quote(fields[0]) + ",\"success\":" + (error == null) + ",\"writes\":" + c.writes.size() + ",\"tx_hex\":" + quote(hex(c.writes.get(0))) + ",\"elapsed_ms\":" + c.time + ",\"payload_hex\":" + quote(hex(payload)) + ",\"decoded_field_count\":" + fieldCount + ",\"error\":" + (error == null ? "null" : quote(error.toString())) + "}");
        }
    }
}
