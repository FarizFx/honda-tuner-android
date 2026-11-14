package id.hondatuner.core;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/** Real Java transport and scheduled worker regression tests using bounded UART fixtures. */
public final class CoreTest {
    private interface Checked { void run() throws Exception; }
    private static int passed;
    public static void main(String[] args) throws Exception {
        run("Honda diagnostic, table, wake and DTC command bytes", CoreTest::commands);
        run("fragmented current-request echo and reply", CoreTest::fragmented);
        run("echo-only response cannot become an ECU acknowledgement", CoreTest::echoOnly);
        run("wrong echo, header, length and checksum are rejected", CoreTest::malformed);
        run("one-way wake sends once and drains trailing input", CoreTest::noReturn);
        run("cancel during receive stops before another command", CoreTest::cancelReceive);
        run("reply received after the deadline is discarded", CoreTest::lateReply);
        run("cold handshake and optional identity without another pulse", CoreTest::coldConnection);
        run("warm ECU and missing identity keep normal diagnostics usable", CoreTest::warmAndMissingId);
        run("failed diagnostic connection cannot read sensors", CoreTest::failedConnection);
        run("live reply selects the returned schema", CoreTest::returnedSchema);
        run("discovery fallbacks stay inside the correct sensor group", CoreTest::discovery);
        run("polling reuses successful requests and marks missing sensors unread", CoreTest::partialLoss);
        run("changed sensor schema is discarded", CoreTest::changedSchema);
        run("raw bytes and unnamed bits stay out of automatic sensors", CoreTest::namedFields);
        run("DTC slots, continuation and truncation", CoreTest::dtcSlots);
        run("current and stored DTC pages including an empty valid group", CoreTest::dtcPages);
        run("stale DTC command/page rejected and endless pages bounded", CoreTest::dtcGuards);
        run("FT232R 10400-baud encoding", CoreTest::baud);
        run("FTDI status bytes removed from every USB packet", CoreTest::usbPackets);
        run("truncated USB status and UART errors rejected", CoreTest::usbErrors);
        run("real scheduled live loop updates RPM, throttle and battery", CoreTest::liveRepeats);
        run("Stop discards a late in-flight sample and queued polling", CoreTest::stopInFlight);
        run("three failed samples stop acquisition without stale values", CoreTest::persistentLoss);
        run("slow live samples never overlap", CoreTest::noOverlap);
        run("app controller connects and starts live automatically", CoreTest::controllerAutoLive);
        run("DTC pauses serialized live work and resumes afterward", CoreTest::controllerDtc);
        run("pause revokes queued UI callbacks and resume starts fresh", CoreTest::controllerPause);
        run("disconnect during USB opening closes the late handle", CoreTest::controllerLateOpen);
        run("disconnect during live receive discards the old session sample", CoreTest::controllerLateSample);
        run("failed DTC never becomes an empty successful report", CoreTest::controllerDtcFailure);
        run("diagnostic connection without supported sensors can still read DTC", CoreTest::controllerNoSensors);
        run("failed rediscovery revokes the previous sensor profile", CoreTest::controllerFailedRediscovery);
        System.out.println("PASS: " + passed + " core regression tests");
    }
    private static void run(String name, Checked test) throws Exception { test.run(); passed++; System.out.println("OK " + name); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void eq(Object expected, Object actual) { check(expected.equals(actual), "Expected " + expected + ", got " + actual); }
    private static void bytes(byte[] expected, byte[] actual) { check(Arrays.equals(expected, actual), "Different command bytes"); }
    private static void io(Checked test) throws Exception {
        try { test.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Expected I/O failure");
    }
    private static byte[] join(byte[] a, byte[] b) {
        byte[] result = Arrays.copyOf(a, a.length + b.length); System.arraycopy(b, 0, result, a.length, b.length); return result;
    }
    private static byte[] reply(byte... payload) { return KLineTransport.command(2, payload); }
    private static byte[] mainBody(int rpm, int throttle, int battery) {
        byte[] body = new byte[14]; body[0] = (byte) (rpm >> 8); body[1] = (byte) rpm; body[3] = (byte) throttle; body[10] = (byte) battery;
        return body;
    }
    private static SensorDecoder.Result mainResult(int rpm) throws IOException { return SensorDecoder.decode(0x13, mainBody(rpm, 30, 123)); }

    private static final class FakeChannel implements ByteChannel, KLineTransport.NanoClock {
        final List<byte[]> writes = new ArrayList<>();
        final Deque<byte[]> pieces = new ArrayDeque<>();
        Function<byte[], byte[]> responder = request -> reply((byte) 0, (byte) 0xf0);
        int fragment = 512, initializations, purges;
        long time;
        volatile boolean closed;
        Runnable duringRead;
        @Override public long now() { return time; }
        @Override public void initialize(CancelSignal cancel) throws IOException { cancel.check(); initializations++; }
        @Override public void purge() { pieces.clear(); purges++; }
        @Override public void write(byte[] data, int timeout, CancelSignal cancel) throws IOException {
            cancel.check(); writes.add(data.clone()); time += 1000000L;
            byte[] wire = data.clone();
            byte[] response = responder.apply(wire);
            byte[] combined = response == null ? wire : join(wire, response);
            for (int offset = 0; offset < combined.length; offset += fragment) pieces.add(Arrays.copyOfRange(combined, offset, Math.min(combined.length, offset + fragment)));
        }
        @Override public byte[] read(int max, int timeout) {
            time += (pieces.isEmpty() ? timeout : 1) * 1000000L;
            if (duringRead != null) duringRead.run();
            return pieces.isEmpty() ? new byte[0] : pieces.remove();
        }
        @Override public void close() { closed = true; }
        KLineTransport transport() { return new KLineTransport(this, this, 500); }
        void standard() {
            responder = request -> {
                if ((request[0] & 255) == 0xfe) return null;
                if ((request[2] & 255) == 0x72) return reply((byte) 0x72, (byte) 0, (byte) 5, (byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5);
                if ((request[2] & 255) == 0x71) return join(new byte[]{2, 19, 0x71, 0x13}, new byte[0]);
                return reply((byte) 0, (byte) 0xf0);
            };
        }
        HondaClient connected() throws IOException { standard(); HondaClient client = new HondaClient(transport()); client.connect(CancelSignal.NEVER); writes.clear(); return client; }
    }

    private static void commands() {
        bytes(new byte[]{0x72, 5, 0, (byte) 0xf0, (byte) 0x99}, KLineTransport.command(0x72, (byte) 0, (byte) 0xf0));
        bytes(new byte[]{(byte) 0xfe, 4, (byte) 0xff, (byte) 0xff}, KLineTransport.command(0xfe, (byte) 0xff));
        bytes(new byte[]{0x72, 5, 0x71, 0x10, 8}, KLineTransport.command(0x72, (byte) 0x71, (byte) 0x10));
        bytes(new byte[]{0x72, 5, 0x74, 1, 0x14}, KLineTransport.command(0x72, (byte) 0x74, (byte) 1));
    }
    private static void fragmented() throws Exception {
        FakeChannel channel = new FakeChannel(); channel.fragment = 1;
        byte[] data = {0x71, 0x13, 1, 2, 3}; channel.responder = request -> reply(data);
        bytes(data, channel.transport().exchange(0x72, new byte[]{0x71, 0x10}, false, CancelSignal.NEVER).payload()); eq(1, channel.writes.size());
    }
    private static void echoOnly() throws Exception {
        FakeChannel channel = new FakeChannel(); channel.responder = request -> null;
        io(() -> channel.transport().exchange(0x72, new byte[]{0x71, 0x10}, false, CancelSignal.NEVER)); eq(2, channel.writes.size());
    }
    private static void malformed() throws Exception {
        byte[][] replies = {new byte[]{3, 3, (byte) 0xfa}, new byte[]{2, 3, 0}, new byte[]{2, 2, (byte) 0xfc}, new byte[]{2, 5, 1}};
        for (byte[] response : replies) { FakeChannel c = new FakeChannel(); c.responder = request -> response; io(() -> c.transport().exchange(0x72, new byte[]{0}, false, CancelSignal.NEVER)); }
        FakeChannel wrong = new FakeChannel(); wrong.responder = request -> { request[2] ^= 1; return reply((byte) 0); };
        io(() -> wrong.transport().exchange(0x72, new byte[]{0}, false, CancelSignal.NEVER));
    }
    private static void noReturn() throws Exception {
        FakeChannel c = new FakeChannel(); c.fragment = 1; c.responder = request -> new byte[]{0x55, 0x44};
        check(c.transport().exchange(0xfe, new byte[]{(byte) 0xff}, true, CancelSignal.NEVER) == null, "No-return produced an ACK");
        eq(1, c.writes.size()); check(c.pieces.isEmpty(), "Wake trailing bytes not drained"); check(c.time < 500000000L, "Wake waited for reply");
        FakeChannel wrong = new FakeChannel(); wrong.responder = request -> { request[0] = 0; return null; };
        io(() -> wrong.transport().exchange(0xfe, new byte[]{(byte) 0xff}, true, CancelSignal.NEVER)); eq(1, wrong.writes.size());
    }
    private static void cancelReceive() throws Exception {
        FakeChannel c = new FakeChannel(); AtomicBoolean cancel = new AtomicBoolean(); c.duringRead = () -> cancel.set(true);
        try { c.transport().exchange(0x72, new byte[]{0}, false, cancel::get); throw new AssertionError("Cancellation ignored"); }
        catch (InterruptedIOException expected) { eq(1, c.writes.size()); }
    }
    private static void lateReply() throws Exception {
        FakeChannel c = new FakeChannel(); c.duringRead = () -> c.time += 600000000L;
        io(() -> c.transport().exchange(0x72, new byte[]{0}, false, CancelSignal.NEVER)); eq(2, c.writes.size());
    }
    private static void coldConnection() throws Exception {
        FakeChannel c = new FakeChannel(); c.standard(); HondaClient client = new HondaClient(c.transport());
        eq("0102030405", client.connect(CancelSignal.NEVER)); eq(1, c.initializations); eq(3, c.writes.size());
        bytes(KLineTransport.command(0xfe, (byte) 0xff), c.writes.get(0)); bytes(KLineTransport.command(0x72, (byte) 0, (byte) 0xf0), c.writes.get(1));
    }
    private static void warmAndMissingId() throws Exception {
        FakeChannel c = new FakeChannel(); c.standard(); c.initializations = 1; Function<byte[], byte[]> old = c.responder;
        c.responder = request -> request[2] == 0x72 ? null : old.apply(request);
        HondaClient client = new HondaClient(c.transport()); eq("", client.connect(CancelSignal.NEVER)); eq(2, c.initializations);
        c.responder = request -> reply(join(new byte[]{0x71, 0x13}, mainBody(1800, 20, 123)));
        eq(1800, client.readTable(0x10, CancelSignal.NEVER).fields.get("engine_speed"));
    }
    private static void failedConnection() throws Exception {
        FakeChannel c = new FakeChannel(); c.responder = request -> null; HondaClient client = new HondaClient(c.transport());
        io(() -> client.connect(CancelSignal.NEVER)); io(() -> client.readTable(0x10, CancelSignal.NEVER)); eq(3, c.writes.size());
    }
    private static void returnedSchema() throws Exception {
        FakeChannel c = new FakeChannel(); HondaClient client = c.connected(); c.responder = request -> reply(join(new byte[]{0x71, 0x13}, mainBody(2200, 60, 117)));
        SensorDecoder.Result result = client.readTable(0x10, CancelSignal.NEVER); eq(0x13, result.tableId); eq(2200, result.fields.get("engine_speed"));
        c.responder = request -> reply(join(new byte[]{0x73, 0x13}, mainBody(2200, 60, 117))); io(() -> client.readTable(0x10, CancelSignal.NEVER));
    }
    private static LiveProfile profile() throws IOException { return LiveProfile.detect((request, cancel) -> { if (request == 0x10) return mainResult(1800); throw new IOException("unsupported"); }, CancelSignal.NEVER); }
    private static void discovery() throws Exception {
        List<Integer> requests = new ArrayList<>();
        LiveProfile p = LiveProfile.detect((request, cancel) -> {
            requests.add(request); if (request == 0x10) return SensorDecoder.decode(0x20, new byte[3]);
            if (request == 0x11) return SensorDecoder.decode(0x11, new byte[20]); throw new IOException("unsupported");
        }, CancelSignal.NEVER);
        eq(Arrays.asList(0x10, 0x11, 0x20, 0x21, 0xd0, 0xd1), requests); eq(1, p.tables.size()); eq(0x11, p.tables.get(0).request);
        io(() -> LiveProfile.detect((request, cancel) -> { throw new IOException("unsupported"); }, CancelSignal.NEVER));
    }
    private static void partialLoss() throws Exception {
        LiveProfile p = LiveProfile.detect((request, cancel) -> { if (request == 0x10) return mainResult(1800); if (request == 0x20) return SensorDecoder.decode(0x20, new byte[3]); throw new IOException("unsupported"); }, CancelSignal.NEVER);
        List<Integer> requests = new ArrayList<>();
        LiveProfile.Sample sample = p.read((request, cancel) -> { requests.add(request); if (request == 0x10) return mainResult(2100); throw new IOException("lost"); }, CancelSignal.NEVER);
        eq(Arrays.asList(0x10, 0x20), requests); eq(2100, sample.values.get("engine_speed")); check(sample.values.get("o2_sensor_voltage_1") == null, "Old O2 copied"); eq(1, sample.validGroups);
    }
    private static void changedSchema() throws Exception {
        LiveProfile.Sample sample = profile().read((request, cancel) -> SensorDecoder.decode(0x10, new byte[17]), CancelSignal.NEVER);
        eq(0, sample.validGroups); check(sample.values.get("engine_speed") == null, "Changed schema accepted");
    }
    private static void namedFields() throws Exception {
        check(!SensorDecoder.decode(0xd0, new byte[29]).named().containsKey("byte01"), "Raw field visible");
        SensorDecoder.Result bits = SensorDecoder.decode(0xd1, new byte[]{(byte) 0x81, 0, 0, 0, 0, 0});
        eq(true, bits.named().get("byte01.eop_switch")); eq(true, bits.named().get("byte01.nlsw")); check(!bits.named().containsKey("byte01.6"), "Unnamed bit visible");
    }
    private static void dtcSlots() throws Exception {
        HondaClient.DtcPage page = HondaClient.DtcPage.parse(new byte[]{0x74, 1, 1, 1, 1, 0, 0, 7, 2}); eq(2, page.codes.size()); check(page.more, "Continuation lost");
        eq("1-1", page.codes.get(0).code()); eq("7-2", page.codes.get(1).code()); eq(0, HondaClient.DtcPage.parse(new byte[9]).codes.size());
        io(() -> HondaClient.DtcPage.parse(new byte[8]));
    }
    private static void dtcPages() throws Exception {
        FakeChannel c = new FakeChannel(); HondaClient client = c.connected();
        c.responder = request -> {
            int command = request[2] & 255, page = request[3] & 255;
            if (command == 0x74 && page == 1) return reply((byte) 0x74, (byte) 1, (byte) 1, (byte) 1, (byte) 1, (byte) 0, (byte) 0, (byte) 7, (byte) 2);
            return reply((byte) command, (byte) page, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0);
        };
        HondaClient.DtcReport result = client.readDtcs(CancelSignal.NEVER); eq(2, result.current.size()); eq(0, result.stored.size()); eq(3, c.writes.size());
        eq("MAP sensor circuit low voltage", result.current.get(0).description());
    }
    private static void dtcGuards() throws Exception {
        FakeChannel c = new FakeChannel(); HondaClient client = c.connected(); c.responder = request -> reply((byte) 0x73, (byte) 1, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0);
        io(() -> client.readDtcs(CancelSignal.NEVER));
        c.writes.clear(); c.responder = request -> reply(request[2], request[3], (byte) 1, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0);
        io(() -> client.readDtcs(CancelSignal.NEVER)); eq(255, c.writes.size());
    }
    private static void baud() { eq(0x4120, FtdiCodec.baudDivisor(10400)); eq(0, FtdiCodec.baudDivisor(3000000)); eq(1, FtdiCodec.baudDivisor(2000000)); }
    private static void usbPackets() throws Exception {
        byte[] raw = new byte[70]; raw[0] = 0x10; raw[1] = 0x60; for (int i = 2; i < 64; i++) raw[i] = (byte) i;
        raw[64] = 0x11; raw[65] = 0x60; for (int i = 66; i < 70; i++) raw[i] = (byte) i;
        byte[] data = FtdiCodec.stripStatus(raw, raw.length, 64); eq(66, data.length); eq((byte) 66, data[62]); eq(0, FtdiCodec.stripStatus(new byte[]{0, 0x60}, 2, 64).length);
    }
    private static void usbErrors() throws Exception { io(() -> FtdiCodec.stripStatus(new byte[]{0}, 1, 64)); io(() -> FtdiCodec.stripStatus(new byte[]{0, 0x68, 4}, 3, 64)); }

    private static final class Samples implements LiveAcquisition.Listener {
        final List<LiveProfile.Sample> values = new ArrayList<>();
        final CountDownLatch enough, stopped = new CountDownLatch(1);
        Samples(int count) { enough = new CountDownLatch(count); }
        @Override public synchronized void onSample(long generation, LiveProfile.Sample sample) { values.add(sample); enough.countDown(); }
        @Override public void onStopped(long generation, String reason) { stopped.countDown(); }
    }
    private static void liveRepeats() throws Exception {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(); AtomicInteger calls = new AtomicInteger(); Samples samples = new Samples(3);
        LiveAcquisition live = new LiveAcquisition(executor, (request, cancel) -> { int n = calls.incrementAndGet(); return SensorDecoder.decode(0x13, mainBody(1500 + n * 300, 20 + n * 20, 120 + n)); }, samples, 15);
        try { live.start(profile()); check(samples.enough.await(2, TimeUnit.SECONDS), "Live did not repeat automatically"); live.stop();
            eq(1800, samples.values.get(0).values.get("engine_speed")); eq(2100, samples.values.get(1).values.get("engine_speed"));
            check(!samples.values.get(0).values.get("tp_sensor_angle").equals(samples.values.get(1).values.get("tp_sensor_angle")), "Throttle frozen");
            check(!samples.values.get(0).values.get("battery_voltage").equals(samples.values.get(1).values.get("battery_voltage")), "Battery frozen");
        } finally { live.stop(); executor.shutdownNow(); check(executor.awaitTermination(2, TimeUnit.SECONDS), "Worker did not stop"); }
    }
    private static void stopInFlight() throws Exception {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(); CountDownLatch began = new CountDownLatch(1), release = new CountDownLatch(1); AtomicInteger calls = new AtomicInteger(); Samples samples = new Samples(1);
        LiveAcquisition live = new LiveAcquisition(executor, (request, cancel) -> { calls.incrementAndGet(); began.countDown(); try { release.await(2, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new InterruptedIOException(); } return mainResult(2200); }, samples, 10);
        try { long run = live.start(profile()); check(began.await(2, TimeUnit.SECONDS), "Read not started"); live.stop(); release.countDown();
            executor.submit(() -> {}).get(2, TimeUnit.SECONDS); Thread.sleep(35); eq(0, samples.values.size()); eq(1, calls.get()); check(!live.isCurrent(run), "Old UI callback still current");
        } finally { release.countDown(); live.stop(); executor.shutdownNow(); executor.awaitTermination(2, TimeUnit.SECONDS); }
    }
    private static void persistentLoss() throws Exception {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(); Samples samples = new Samples(3); AtomicInteger calls = new AtomicInteger();
        LiveAcquisition live = new LiveAcquisition(executor, (request, cancel) -> { calls.incrementAndGet(); throw new IOException("lost"); }, samples, 10);
        try { live.start(profile()); check(samples.stopped.await(2, TimeUnit.SECONDS), "Persistent loss did not stop"); eq(3, calls.get()); eq(3, samples.values.size()); check(!live.isRunning(), "Still polling");
            for (LiveProfile.Sample sample : samples.values) for (Object value : sample.values.values()) check(value == null, "Stale sensor displayed");
        } finally { live.stop(); executor.shutdownNow(); executor.awaitTermination(2, TimeUnit.SECONDS); }
    }
    private static void noOverlap() throws Exception {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(); Samples samples = new Samples(3); AtomicInteger active = new AtomicInteger(), maximum = new AtomicInteger();
        LiveAcquisition live = new LiveAcquisition(executor, (request, cancel) -> { int n = active.incrementAndGet(); maximum.accumulateAndGet(n, Math::max);
            try { Thread.sleep(30); return mainResult(2000); } catch (InterruptedException e) { throw new InterruptedIOException(); } finally { active.decrementAndGet(); }
        }, samples, 10);
        try { live.start(profile()); check(samples.enough.await(2, TimeUnit.SECONDS), "Slow reader stopped"); live.stop(); eq(1, maximum.get()); }
        finally { live.stop(); executor.shutdownNow(); executor.awaitTermination(2, TimeUnit.SECONDS); }
    }

    private static final class ControllerProbe implements DiagnosticController.Listener {
        volatile DiagnosticController.Snapshot snapshot;
        volatile HondaClient.DtcReport dtc;
        volatile String dtcError;
        volatile long generation;
        final AtomicInteger samples = new AtomicInteger();
        Runnable whenProfile;
        @Override public void onState(DiagnosticController.Snapshot value) { snapshot = value; }
        @Override public void onProfile(long session, LiveProfile profile) { if (whenProfile != null) whenProfile.run(); }
        @Override public void onSample(long session, long run, LiveProfile.Sample sample) { generation = run; samples.incrementAndGet(); }
        @Override public void onDtc(long session, long operation, HondaClient.DtcReport result) { dtc = result; }
        @Override public void onDtcError(long session, long operation, String reason) { dtcError = reason; }
    }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean()) { if (System.nanoTime() >= deadline) throw new AssertionError("Controller event did not arrive"); Thread.sleep(5); }
    }
    private static void gate(CountDownLatch release) {
        try { if (!release.await(2, TimeUnit.SECONDS)) throw new AssertionError("Fixture gate timeout"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }
    private static void allDiagnostics(FakeChannel c) {
        c.standard(); Function<byte[], byte[]> original = c.responder; AtomicInteger rpm = new AtomicInteger();
        c.responder = request -> {
            if ((request[0] & 255) == 0xfe) return null;
            int command = request[2] & 255;
            if (command == 0x71) {
                int table = request[3] & 255;
                if (table == 0x10) return reply(join(new byte[]{0x71, 0x13}, mainBody(1500 + 100 * rpm.incrementAndGet(), 40, 124)));
                if (table == 0x20) return reply(join(new byte[]{0x71, 0x20}, new byte[3]));
                if (table == 0xd0) return reply(join(new byte[]{0x71, (byte) 0xd0}, new byte[14]));
                if (table == 0xd1) return reply(join(new byte[]{0x71, (byte) 0xd1}, new byte[6]));
                return null;
            }
            if (command == 0x73 || command == 0x74) return reply(request[2], request[3], (byte) 0, (byte) (command == 0x74 ? 1 : 0), (byte) (command == 0x74 ? 1 : 0), (byte) 0, (byte) 0, (byte) 0, (byte) 0);
            return original.apply(request);
        };
    }
    private static DiagnosticController controller(ControllerProbe probe, ScheduledExecutorService executor) {
        return new DiagnosticController(probe, executor, 15, channel -> ((FakeChannel) channel).transport());
    }
    private static void controllerAutoLive() throws Exception {
        FakeChannel c = new FakeChannel(); allDiagnostics(c); ControllerProbe probe = new ControllerProbe(); ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(); DiagnosticController app = controller(probe, worker);
        try { app.connect(cancel -> c); await(() -> probe.samples.get() >= 2); eq(DiagnosticController.State.LIVE, app.snapshot().state); eq("0102030405", app.snapshot().ecuId); eq(1, c.initializations); }
        finally { app.close(); check(worker.awaitTermination(2, TimeUnit.SECONDS), "Controller worker leaked"); }
    }
    private static void controllerDtc() throws Exception {
        FakeChannel c = new FakeChannel(); allDiagnostics(c); ControllerProbe probe = new ControllerProbe(); ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(); DiagnosticController app = controller(probe, worker);
        CountDownLatch release = new CountDownLatch(1);
        try {
            app.connect(cancel -> c); await(() -> probe.samples.get() >= 2); int prior = probe.samples.get();
            Function<byte[], byte[]> original = c.responder; c.responder = request -> { if ((request[2] & 255) == 0x74) gate(release); return original.apply(request); };
            check(app.readDtcs(), "DTC refused in live state"); check(!app.readDtcs(), "Second DTC operation accepted while busy"); check(!app.detectAgain(), "Detection overlapped DTC"); release.countDown();
            await(() -> probe.dtc != null && probe.samples.get() > prior && app.snapshot().state == DiagnosticController.State.LIVE);
            eq(1, probe.dtc.current.size()); eq(0, probe.dtc.stored.size());
        } finally { release.countDown(); app.close(); worker.awaitTermination(2, TimeUnit.SECONDS); }
    }
    private static void controllerPause() throws Exception {
        FakeChannel c = new FakeChannel(); allDiagnostics(c); ControllerProbe probe = new ControllerProbe(); ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(); DiagnosticController app = controller(probe, worker);
        try {
            app.connect(cancel -> c); await(() -> probe.samples.get() >= 2); long session = app.snapshot().session, run = probe.generation;
            check(app.toggleLive(), "Pause refused"); check(!app.isCurrentLive(session, run), "Old UI generation still current");
            worker.submit(() -> {}).get(2, TimeUnit.SECONDS); int stopped = probe.samples.get(); Thread.sleep(35); eq(stopped, probe.samples.get());
            check(app.toggleLive(), "Resume refused"); await(() -> probe.samples.get() > stopped); check(!app.isCurrentLive(session, run), "Resume reauthorized old callback");
        } finally { app.close(); worker.awaitTermination(2, TimeUnit.SECONDS); }
    }
    private static void controllerLateOpen() throws Exception {
        FakeChannel c = new FakeChannel(); ControllerProbe probe = new ControllerProbe(); ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(); DiagnosticController app = controller(probe, worker);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try {
            app.connect(cancel -> { entered.countDown(); gate(release); return c; }); check(entered.await(2, TimeUnit.SECONDS), "Open did not start"); long token = app.snapshot().session;
            app.disconnect("cancelled"); release.countDown(); worker.submit(() -> {}).get(2, TimeUnit.SECONDS);
            check(c.closed, "Late USB handle leaked"); check(!app.isCurrent(token), "Old session still current"); eq(0, c.writes.size()); eq(DiagnosticController.State.DISCONNECTED, app.snapshot().state);
        } finally { release.countDown(); app.close(); worker.awaitTermination(2, TimeUnit.SECONDS); }
    }
    private static void controllerLateSample() throws Exception {
        FakeChannel c = new FakeChannel(); allDiagnostics(c); ControllerProbe probe = new ControllerProbe(); ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(); DiagnosticController app = controller(probe, worker);
        AtomicBoolean block = new AtomicBoolean(); CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        probe.whenProfile = () -> block.set(true); c.duringRead = () -> { if (block.get()) { entered.countDown(); gate(release); } };
        try {
            app.connect(cancel -> c); check(entered.await(2, TimeUnit.SECONDS), "Live read did not start"); app.disconnect("detached"); release.countDown();
            worker.submit(() -> {}).get(2, TimeUnit.SECONDS); eq(0, probe.samples.get()); check(c.closed, "USB not closed on detach");
        } finally { release.countDown(); app.close(); worker.awaitTermination(2, TimeUnit.SECONDS); }
    }
    private static void controllerDtcFailure() throws Exception {
        FakeChannel c = new FakeChannel(); allDiagnostics(c); ControllerProbe probe = new ControllerProbe(); ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(); DiagnosticController app = controller(probe, worker);
        try {
            app.connect(cancel -> c); await(() -> probe.samples.get() >= 1); Function<byte[], byte[]> original = c.responder;
            c.responder = request -> (request[2] & 255) == 0x74 ? reply((byte) 0x73, (byte) 1, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0) : original.apply(request);
            check(app.readDtcs(), "DTC refused"); await(() -> probe.dtcError != null); check(probe.dtc == null, "DTC failure reported as a successful empty result");
        } finally { app.close(); worker.awaitTermination(2, TimeUnit.SECONDS); }
    }
    private static void controllerNoSensors() throws Exception {
        FakeChannel c = new FakeChannel(); allDiagnostics(c); Function<byte[], byte[]> original = c.responder;
        c.responder = request -> (request[2] & 255) == 0x71 ? null : original.apply(request);
        ControllerProbe probe = new ControllerProbe(); ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(); DiagnosticController app = controller(probe, worker);
        try {
            app.connect(cancel -> c); await(() -> app.snapshot().state == DiagnosticController.State.PAUSED); check(app.snapshot().connected(), "Unsupported sensors discarded diagnostic connection"); check(!app.toggleLive(), "Live started without profile");
            check(app.readDtcs(), "DTC blocked without sensors"); await(() -> probe.dtc != null); eq(1, probe.dtc.current.size());
        } finally { app.close(); worker.awaitTermination(2, TimeUnit.SECONDS); }
    }
    private static void controllerFailedRediscovery() throws Exception {
        FakeChannel c = new FakeChannel(); allDiagnostics(c); ControllerProbe probe = new ControllerProbe(); ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(); DiagnosticController app = controller(probe, worker);
        try {
            app.connect(cancel -> c); await(() -> probe.samples.get() >= 1); check(app.snapshot().hasSensors, "No detected profile");
            Function<byte[], byte[]> original = c.responder; c.responder = request -> (request[2] & 255) == 0x71 ? null : original.apply(request);
            check(app.detectAgain(), "Rediscovery refused"); await(() -> app.snapshot().state == DiagnosticController.State.PAUSED);
            check(!app.snapshot().hasSensors, "Old profile survived failed detection"); check(!app.toggleLive(), "Old live profile resumed"); check(app.readDtcs(), "Diagnostic session was lost"); await(() -> probe.dtc != null);
        } finally { app.close(); worker.awaitTermination(2, TimeUnit.SECONDS); }
    }
}
