package id.hondatuner.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Deadline/cancellation regressions and a runner for independent Lite fixtures. */
public final class TransportTest {
    private interface Checked { void run() throws Exception; }
    private interface Responder { void respond(Channel channel, byte[] request, long began); }
    private static int passed;
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    private static void bytes(byte[] expected, byte[] actual) { check(Arrays.equals(expected, actual), "Unexpected payload"); }
    private static void run(String name, Checked action) throws Exception { action.run(); passed++; System.out.println("OK " + name); }
    private static IOException failure(Checked action) throws Exception {
        try { action.run(); } catch (IOException expected) { return expected; }
        throw new AssertionError("Expected I/O failure");
    }
    private static byte[] ack(byte... payload) { return KLineTransport.command(2, payload); }
    private static byte[] hex(String value) {
        check(value.length() % 2 == 0, "Odd hex length");
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) result[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        return result;
    }
    private static String hex(byte[] value) { return DiagnosticLog.hex(value, value.length).replace(" ", "").toLowerCase(java.util.Locale.ROOT); }
    private static final class Event {
        final long at;
        final byte[] data;
        final IOException error;
        Event(long at, byte[] data, IOException error) { this.at = at; this.data = data; this.error = error; }
    }
    private static final class Channel implements ByteChannel, KLineTransport.NanoClock {
        final PriorityQueue<Event> events = new PriorityQueue<>(Comparator.comparingLong(event -> event.at));
        final List<byte[]> writes = new ArrayList<>();
        final List<Long> writeTimes = new ArrayList<>();
        long time, txMillis = 6;
        boolean healthy = true, closed;
        int initializations;
        Responder responder = (channel, request, began) -> {
            channel.at(began + 7, request);
            channel.at(began + 20, ack((byte) 0));
        };
        Consumer<Channel> afterRead;
        IOException writeError;
        @Override public long now() { return time * 1000000L; }
        void at(long at, byte[] data) { events.add(new Event(at, data, null)); }
        void errorAt(long at, IOException error) { events.add(new Event(at, new byte[0], error)); }
        @Override public void initialize(CancelSignal cancel) throws IOException { cancel.check(); initializations++; }
        @Override public void purge() { while (!events.isEmpty() && events.peek().at <= time) events.remove(); }
        @Override public void write(byte[] request, int timeout, CancelSignal cancel) throws IOException {
            cancel.check(); check(!closed, "Write after close");
            writes.add(request.clone()); writeTimes.add(time);
            if (writeError != null) throw writeError;
            responder.respond(this, request.clone(), time); time += txMillis;
        }
        @Override public byte[] read(int maximum, int timeout) throws IOException {
            long until = time + timeout;
            if (events.isEmpty() || events.peek().at > until) { time = until; if (afterRead != null) afterRead.accept(this); return new byte[0]; }
            Event next = events.remove(); time = Math.max(time, next.at);
            if (afterRead != null) afterRead.accept(this);
            if (next.error != null) throw next.error;
            if (next.data.length <= maximum) return next.data;
            events.add(new Event(time, Arrays.copyOfRange(next.data, maximum, next.data.length), null));
            return Arrays.copyOf(next.data, maximum);
        }
        @Override public boolean isHealthy() { return healthy && !closed; }
        @Override public void close() { closed = true; }
        KLineTransport transport(DiagnosticLog log) { return new KLineTransport(this, this, 100, KLineTransport.EchoMode.AUTO, log); }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0 && args[0].equals("--fixtures")) { fixtures(); return; }
        run("damaged echo waits for delayed ECU reply with one TX", TransportTest::damagedEcho);
        run("header-like damaged echo cannot select the wrong receive layout", TransportTest::headerEcho);
        run("extra RX in a later USB fragment cannot complete a frame", TransportTest::fragmentedExtra);
        run("valid-frame-shaped damaged prefix cannot win before its suffix", TransportTest::fragmentedAmbiguity);
        run("valid RX near the selected deadline is still accepted", TransportTest::nearDeadline);
        run("cancelled live response is drained before both DTC groups", TransportTest::cancelledLiveToDtc);
        run("cancel during old RX drain preserves the original deadline", TransportTest::cancelledDrain);
        run("USB failure during old RX drain prevents a new command", TransportTest::failedDrain);
        run("malformed first RX never starts retry before its deadline", TransportTest::retryDeadline);
        run("510-byte combined echo and reply is accepted", TransportTest::maximumFrame);
        run("oversized RX stays bounded and waits before retry", TransportTest::oversizedFrame);
        run("UART error poisons the receive attempt despite a later valid frame", TransportTest::uartFault);
        run("fatal read is not retried or hidden by simultaneous cancellation", TransportTest::fatalRead);
        run("fatal write never appends a second command", TransportTest::fatalWrite);
        run("cancelled uncertain TX remains unhealthy", TransportTest::uncertainCancellation);
        run("USB write InterruptedIOException is a fatal partial TX", TransportTest::usbWriteTimeout);
        run("USB read errors are fatal while status-only timeout stays healthy", TransportTest::usbReadFailure);
        run("UART sample error leaves USB healthy for a later transaction", TransportTest::usbUartFailure);
        run("healthy exact fragmented RX records no false first failure", TransportTest::exactTrace);
        run("damaged echo trace retains full TX RX and wait evidence", TransportTest::damagedTrace);
        run("one-way wake remains one TX and consumes its whole window", TransportTest::wakeWindow);
        System.out.println("PASS: " + passed + " transport regression tests");
    }
    private static void damagedEcho() throws Exception {
        Channel c = new Channel(); c.responder = (channel, request, began) -> { channel.at(began + 7, hex("600000e010")); channel.at(began + 20, ack((byte) 0)); };
        bytes(new byte[]{0}, c.transport(null).exchange(0x72, hex("00f0"), false, CancelSignal.NEVER).payload());
        check(c.writes.size() == 1 && c.time == 44, "Delayed original-compatible reply was preempted or not settled");
    }
    private static void headerEcho() throws Exception {
        Channel c = new Channel(); c.responder = (channel, request, began) -> { channel.at(began + 7, hex("020000e010")); channel.at(began + 20, ack((byte) 0)); };
        bytes(new byte[]{0}, c.transport(null).exchange(0x72, hex("00f0"), false, CancelSignal.NEVER).payload());
        check(c.writes.size() == 1, "Header-like echo caused retry");
    }
    private static void fragmentedExtra() throws Exception {
        Channel c = new Channel(); c.responder = (channel, request, began) -> { channel.at(began + 20, ack((byte) 0)); channel.at(began + 23, new byte[]{0x55}); };
        IOException error = failure(() -> c.transport(null).exchange(0x72, hex("00f0"), false, CancelSignal.NEVER));
        check(error instanceof ProtocolException && c.writes.size() == 2 && c.time == 212, "Later trailing fragment was accepted");
    }
    private static void fragmentedAmbiguity() throws Exception {
        Channel c = new Channel(); c.responder = (channel, request, began) -> { channel.at(began + 7, hex("02050000f9")); channel.at(began + 20, ack((byte) 0)); };
        IOException error = failure(() -> c.transport(null).exchange(0x72, hex("00f0"), false, CancelSignal.NEVER));
        check(error.getMessage().contains("data tambahan") && c.writes.size() == 2, "Ambiguous prefix won before the real reply");
    }
    private static void nearDeadline() throws Exception {
        Channel c = new Channel(); c.responder = (channel, request, began) -> channel.at(began + 100, ack((byte) 0));
        bytes(new byte[]{0}, c.transport(null).exchange(0x72, hex("00f0"), false, CancelSignal.NEVER).payload());
        check(c.writes.size() == 1 && c.time == 106, "Completion guard shortened selected receive window");
    }
    private static HondaClient connected(Channel c) throws IOException {
        c.responder = (channel, request, began) -> {
            channel.at(began + 7, request);
            if ((request[0] & 255) == 0xfe) return;
            channel.at(began + 20, request[2] == 0x72 ? ack((byte) 0x72, (byte) 0, (byte) 5, (byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5) : ack((byte) 0));
        };
        HondaClient client = new HondaClient(c.transport(null)); client.connect(CancelSignal.NEVER); c.writes.clear(); c.writeTimes.clear(); return client;
    }
    private static void cancelledLiveToDtc() throws Exception {
        Channel c = new Channel(); HondaClient client = connected(c); AtomicBoolean cancelled = new AtomicBoolean();
        c.responder = (channel, request, began) -> {
            channel.at(began + 7, request);
            if (request[2] == 0x71) channel.at(began + 20, ack((byte) 0x71, (byte) 0x13, (byte) 0, (byte) 1));
            else channel.at(began + 20, ack(request[2], request[3], (byte) 0, request[2] == 0x74 ? (byte) 1 : (byte) 0, request[2] == 0x74 ? (byte) 1 : (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0));
        };
        c.afterRead = channel -> { cancelled.set(true); channel.afterRead = null; };
        check(failure(() -> client.readTable(0x10, cancelled::get)) instanceof InterruptedIOException, "Live cancellation ignored");
        HondaClient.DtcReport report = client.readDtcs(CancelSignal.NEVER);
        check(report.current.size() == 1 && report.current.get(0).code().equals("1-1") && report.stored.isEmpty(), "DTC consumed old live RX");
        check(c.writes.size() == 3 && c.writeTimes.get(1) >= c.writeTimes.get(0) + 106, "New command overlapped cancelled receive window");
        client.close();
    }
    private static Channel cancelled(Channel c, KLineTransport transport) throws Exception {
        AtomicBoolean cancel = new AtomicBoolean(); c.afterRead = channel -> { cancel.set(true); channel.afterRead = null; };
        check(failure(() -> transport.exchange(0x72, hex("00f0"), false, cancel::get)) instanceof InterruptedIOException, "Cancellation ignored");
        return c;
    }
    private static void cancelledDrain() throws Exception {
        Channel c = new Channel(); KLineTransport transport = c.transport(null); cancelled(c, transport);
        AtomicBoolean cancel = new AtomicBoolean(); c.afterRead = channel -> { cancel.set(true); channel.afterRead = null; };
        check(failure(() -> transport.exchange(0x72, hex("7401"), false, cancel::get)) instanceof InterruptedIOException, "Drain cancellation ignored");
        check(c.writes.size() == 1, "New TX escaped cancelled drain");
        transport.exchange(0x72, hex("7401"), false, CancelSignal.NEVER);
        check(c.writeTimes.get(1) == 106, "Original deadline was shortened or restarted");
    }
    private static void failedDrain() throws Exception {
        Channel c = new Channel(); KLineTransport transport = c.transport(null); cancelled(c, transport);
        c.events.clear(); c.errorAt(20, new IOException("detached during discard"));
        check(failure(() -> transport.exchange(0x72, hex("7401"), false, CancelSignal.NEVER)) instanceof TransportException, "USB discard failure was recoverable");
        check(c.writes.size() == 1 && !transport.isHealthy(), "Failure appended a new command");
    }
    private static void retryDeadline() throws Exception {
        Channel c = new Channel(); c.responder = (channel, request, began) -> channel.at(began + 7, hex("600000e010"));
        failure(() -> c.transport(null).exchange(0x72, hex("00f0"), false, CancelSignal.NEVER));
        check(c.writes.size() == 2 && c.writeTimes.get(1) == 106 && c.time == 212, "Bad prefix triggered an early retransmission");
    }
    private static void maximumFrame() throws Exception {
        Channel c = new Channel(); byte[] payload = new byte[252]; Arrays.fill(payload, (byte) 1);
        c.responder = (channel, request, began) -> {
            byte[] reply = ack(payload), combined = Arrays.copyOf(request, request.length + reply.length);
            System.arraycopy(reply, 0, combined, request.length, reply.length); channel.at(began + 20, combined);
        };
        bytes(payload, c.transport(null).exchange(0x72, payload, false, CancelSignal.NEVER).payload());
        check(c.writes.size() == 1, "Maximum bounded frame retried");
    }
    private static void oversizedFrame() throws Exception {
        Channel c = new Channel(); c.responder = (channel, request, began) -> channel.at(began + 7, new byte[511]);
        IOException error = failure(() -> c.transport(null).exchange(0x72, hex("00f0"), false, CancelSignal.NEVER));
        check(error.getMessage().contains("batas frame") && c.writes.size() == 2 && c.writeTimes.get(1) == 106, "Oversized RX escaped bound/deadline");
    }
    private static IOException uartError() throws IOException {
        try { FtdiCodec.stripStatus(new byte[]{1, 0x68, 0x60}, 3, 64); }
        catch (FtdiCodec.UartException error) { return error; }
        throw new AssertionError("UART fixture not rejected");
    }
    private static void uartFault() throws Exception {
        Channel c = new Channel(); IOException error = uartError(); c.responder = (channel, request, began) -> { channel.errorAt(began + 7, error); channel.at(began + 20, ack((byte) 0)); };
        KLineTransport transport = c.transport(null);
        check(failure(() -> transport.exchange(0x72, hex("00f0"), false, CancelSignal.NEVER)) instanceof ProtocolException, "UART fault was accepted");
        check(c.time == 212 && c.writes.size() == 2 && transport.isHealthy(), "UART sample fault lost full receive window or USB health");
    }
    private static void fatalRead() throws Exception {
        Channel c = new Channel(); AtomicBoolean cancel = new AtomicBoolean();
        c.responder = (channel, request, began) -> channel.errorAt(began + 7, new IOException("USB detached"));
        c.afterRead = channel -> cancel.set(true); KLineTransport transport = c.transport(null);
        check(failure(() -> transport.exchange(0x72, hex("00f0"), false, cancel::get)) instanceof TransportException, "Fatal RX was hidden by cancellation");
        check(c.writes.size() == 1 && !transport.isHealthy(), "Fatal RX retried or left healthy state");
    }
    private static void fatalWrite() throws Exception {
        Channel c = new Channel(); c.writeError = new TransportException("partial TX"); KLineTransport transport = c.transport(null);
        check(failure(() -> transport.exchange(0x72, hex("00f0"), false, CancelSignal.NEVER)) instanceof TransportException, "Fatal TX became a reply timeout");
        check(c.writes.size() == 1 && !transport.isHealthy(), "Fatal TX repeated");
    }
    private static void uncertainCancellation() throws Exception {
        Channel c = new Channel(); c.healthy = true; c.writeError = new InterruptedIOException("cancelled with TX queued");
        c.responder = (channel, request, began) -> {};
        ByteChannel channel = new ByteChannel() {
            @Override public void initialize(CancelSignal cancel) { }
            @Override public void purge() { }
            @Override public void write(byte[] data, int timeout, CancelSignal cancel) throws IOException { c.healthy = false; throw c.writeError; }
            @Override public byte[] read(int max, int timeout) { throw new AssertionError("RX after uncertain TX"); }
            @Override public boolean isHealthy() { return c.healthy; }
            @Override public void close() { c.close(); }
        };
        KLineTransport transport = new KLineTransport(channel, c, 100, KLineTransport.EchoMode.AUTO, null);
        check(failure(() -> transport.exchange(0x72, hex("00f0"), false, CancelSignal.NEVER)) instanceof InterruptedIOException && !transport.isHealthy(), "Uncertain cancellation was hidden");
        check(failure(() -> transport.exchange(0x72, hex("7401"), false, CancelSignal.NEVER)) instanceof TransportException, "Uncertain channel accepted another operation");
    }
    private static final class Usb implements FtdiUart.UsbIo, FtdiUart.Timing {
        long time; int writes;
        IOException writeError, readError;
        byte[] raw;
        @Override public long now() { return time; }
        @Override public void sleep(long millis) { time += millis * 1000000L; }
        @Override public int controlOut(int request, int value, int index, int timeout) { return 0; }
        @Override public int controlIn(int request, int value, int index, byte[] buffer, int timeout) { buffer[0] = 1; buffer[1] = 0x60; return 2; }
        @Override public void configureUart(int baud) { check(baud == 10400, "Baud changed"); }
        @Override public void setBreak(boolean enabled) { }
        @Override public int write(byte[] data, int offset, int count, int timeout) throws IOException { writes++; if (writeError != null) throw writeError; return count; }
        @Override public int read(byte[] buffer, int timeout) throws IOException {
            if (readError != null) throw readError;
            if (raw == null) { time += timeout * 1000000L; return 0; }
            byte[] next = raw; raw = null; System.arraycopy(next, 0, buffer, 0, next.length); return next.length;
        }
        @Override public void close() { }
        FtdiUart driver() throws IOException { FtdiUart driver = new FtdiUart(this, 1, 64, null, this); check(driver.isHealthy(), "Uninitialized channel falsely unhealthy"); driver.initialize(CancelSignal.NEVER); return driver; }
    }
    private static void usbWriteTimeout() throws Exception {
        Usb usb = new Usb(); FtdiUart driver = usb.driver(); InterruptedIOException timeout = new InterruptedIOException("serial timeout"); timeout.bytesTransferred = 2; usb.writeError = timeout;
        IOException error = failure(() -> driver.write(hex("720500f099"), 100, CancelSignal.NEVER));
        check(error instanceof TransportException && error.getCause() == timeout && !driver.isHealthy(), "SerialTimeoutException treated as cancellation");
        failure(() -> driver.write(hex("720500f099"), 100, CancelSignal.NEVER)); check(usb.writes == 1, "Partial TX repeated"); driver.close();
    }
    private static void usbReadFailure() throws Exception {
        Usb usb = new Usb(); FtdiUart driver = usb.driver(); bytes(new byte[0], driver.read(256, 10)); check(driver.isHealthy(), "RX timeout closed USB");
        usb.readError = new IOException("connection lost"); check(failure(() -> driver.read(256, 10)) instanceof TransportException && !driver.isHealthy(), "USB read error hidden as timeout"); driver.close();
    }
    private static void usbUartFailure() throws Exception {
        Usb usb = new Usb(); FtdiUart driver = usb.driver(); usb.raw = new byte[]{1, 0x68, 0x60};
        check(failure(() -> driver.read(256, 10)) instanceof FtdiCodec.UartException && driver.isHealthy(), "UART data error misclassified as USB loss"); driver.close();
    }
    private static void exactTrace() throws Exception {
        Channel c = new Channel(); DiagnosticLog log = new DiagnosticLog(); c.responder = (channel, request, began) -> {
            for (int i = 0; i < request.length; i++) channel.at(began + 7 + i, new byte[]{request[i]});
            channel.at(began + 20, new byte[]{2}); channel.at(began + 23, new byte[]{4}); channel.at(began + 26, new byte[]{0}); channel.at(began + 29, new byte[]{(byte) 0xfa});
        };
        c.transport(log).exchange(0x72, hex("00f0"), false, CancelSignal.NEVER);
        check(log.text(0).contains("first_failure=none") && log.text(0).contains("echo=exact"), "Normal fragmentation logged as a failure");
    }
    private static void damagedTrace() throws Exception {
        Channel c = new Channel(); DiagnosticLog log = new DiagnosticLog(); c.responder = (channel, request, began) -> { channel.at(began + 7, hex("600000e010")); channel.at(began + 20, ack((byte) 0)); };
        c.transport(log).exchange(0x72, hex("00f0"), false, CancelSignal.NEVER); String trace = log.text(0);
        check(trace.contains("result=valid; TX=72 05 00 F0 99; RX=60 00 00 E0 10 02 04 00 FA") && trace.contains("echo=different") && trace.contains("receive_wait=38/100 ms") && trace.contains("UART=no-error-reported"), "Transaction evidence incomplete");
    }
    private static void wakeWindow() throws Exception {
        Channel c = new Channel(); c.responder = (channel, request, began) -> { channel.at(began + 7, hex("fc00fefe")); channel.at(began + 45, hex("00")); };
        KLineTransport transport = c.transport(null); check(transport.exchange(0xfe, hex("ff"), true, CancelSignal.NEVER) == null, "Wake generated an ACK");
        check(c.writes.size() == 1 && c.time == 66 && c.events.isEmpty(), "Wake did not drain complete 60 ms window after TX");
    }
    private static String quote(String value) { return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + '"'; }
    private static void fixtures() throws Exception {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8));
        for (String line; (line = input.readLine()) != null;) {
            String[] fields = line.split("\t", -1); check(fields.length == 7, "Invalid fixture row");
            Channel c = new Channel(); c.txMillis = Long.parseLong(fields[5]); String schedule = fields[6];
            c.responder = (channel, request, began) -> {
                if (!schedule.isEmpty()) for (String piece : schedule.split(";")) { String[] parts = piece.split(":", 2); channel.at(began + Long.parseLong(parts[0]), hex(parts[1])); }
            };
            KLineTransport transport = new KLineTransport(c, c, Integer.parseInt(fields[4]), KLineTransport.EchoMode.valueOf(fields[1]), null);
            byte[] payload = new byte[0]; IOException error = null;
            try { payload = transport.exchange(Integer.parseInt(fields[2]), hex(fields[3]), false, CancelSignal.NEVER).payload(); }
            catch (IOException failed) { error = failed; }
            System.out.println("{\"name\":" + quote(fields[0]) + ",\"success\":" + (error == null) + ",\"writes\":" + c.writes.size() + ",\"tx_hex\":" + quote(c.writes.isEmpty() ? "" : hex(c.writes.get(0))) + ",\"elapsed_ms\":" + c.time + ",\"payload_hex\":" + quote(hex(payload)) + ",\"error\":" + (error == null ? "null" : quote(error.toString())) + "}");
        }
    }
}
