package id.hondatuner.core;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Session ownership and fatal/cancelled channel regressions, using actual app workflow code. */
public final class ControllerHealthTest {
    private interface Checked { void run() throws Exception; }
    private static int passed;

    public static void main(String[] args) throws Exception {
        run("fatal optional ECU ID never publishes a connected state", ControllerHealthTest::fatalIdentity);
        run("optional protocol ID failure keeps diagnostics usable", ControllerHealthTest::optionalIdentity);
        run("fatal sensor discovery stops before another candidate", ControllerHealthTest::fatalDiscovery);
        run("typed table failures cannot become unsupported or unread sensors", ControllerHealthTest::profileFatal);
        run("cancelled live fatal RX disconnects the owning session", ControllerHealthTest::cancelledLive);
        run("cancelled live fatal RX prevents queued DTC transmission", ControllerHealthTest::liveToDtcFatal);
        run("cancelled uncertain TX still disconnects through channel health", ControllerHealthTest::cancelledUncertainTx);
        run("fatal DTC failure never resumes live or returns an empty report", ControllerHealthTest::fatalDtc);
        run("recoverable DTC protocol failure keeps live usable", ControllerHealthTest::recoverableDtc);
        run("fatal manual read discards its sample and sensor profile", ControllerHealthTest::fatalManual);
        run("fatal rediscovery cannot leave an apparently connected session", ControllerHealthTest::fatalRediscovery);
        run("an old cancelled fatal callback cannot close a replacement session", ControllerHealthTest::replacementSession);
        run("UI pause, health snapshot and disconnect stay responsive during RX", ControllerHealthTest::nonblockingUi);
        System.out.println("PASS: " + passed + " controller health regression tests");
    }

    private static void run(String name, Checked test) throws Exception {
        test.run(); passed++; System.out.println("OK " + name);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void equal(Object expected, Object actual) { check(expected.equals(actual), "Expected " + expected + ", got " + actual); }
    private static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(2);
        check(condition.getAsBoolean(), "Timed out waiting for workflow");
    }
    private static void barrier(ScheduledExecutorService worker) throws Exception { worker.submit(() -> {}).get(3, TimeUnit.SECONDS); }
    private static void gate(CountDownLatch release) throws IOException {
        try { check(release.await(3, TimeUnit.SECONDS), "Fixture was not released"); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new InterruptedIOException("Fixture interrupted"); }
    }
    private static byte[] join(byte[] a, byte[] b) {
        byte[] result = Arrays.copyOf(a, a.length + b.length); System.arraycopy(b, 0, result, a.length, b.length); return result;
    }
    private static byte[] reply(byte... payload) { return KLineTransport.command(2, payload); }
    private static SensorDecoder.Result mainResult() throws IOException { return SensorDecoder.decode(0x13, new byte[14]); }
    private static LiveProfile oneTable() throws IOException {
        return LiveProfile.detect((request, cancel) -> {
            if (request == 0x10) return mainResult(); throw new ProtocolException("unsupported");
        }, CancelSignal.NEVER);
    }

    private enum Phase { WRITE, READ }
    private static final class Fault {
        final int opcode;
        final Phase phase;
        final boolean uncertain;
        final CountDownLatch entered = new CountDownLatch(1), release;
        final AtomicBoolean fired = new AtomicBoolean();
        volatile Thread executingThread;
        Fault(int opcode, Phase phase, boolean blocked, boolean uncertain) {
            this.opcode = opcode; this.phase = phase; this.uncertain = uncertain;
            release = new CountDownLatch(blocked ? 1 : 0);
        }
        void awaitEntry() throws Exception { check(entered.await(3, TimeUnit.SECONDS), "Fatal fixture not entered"); }
        void finish() { release.countDown(); }
    }

    private static final class FixtureChannel implements ByteChannel, KLineTransport.NanoClock {
        final Deque<byte[]> pieces = new ArrayDeque<>();
        final AtomicInteger writes = new AtomicInteger(), tableWrites = new AtomicInteger(), dtcWrites = new AtomicInteger();
        volatile boolean healthy = true, closed, missingIdentity, badDtc;
        volatile Fault fault;
        private byte[] current;
        private long nanos;
        @Override public long now() { return nanos; }
        @Override public boolean isHealthy() { return healthy && !closed; }
        @Override public void initialize(CancelSignal cancel) throws IOException { cancel.check(); }
        @Override public void purge() { pieces.clear(); }
        @Override public void write(byte[] request, int timeout, CancelSignal cancel) throws IOException {
            cancel.check(); current = request.clone(); writes.incrementAndGet(); nanos += 1000000L;
            int opcode = current[2] & 255;
            if (opcode == 0x71) tableWrites.incrementAndGet();
            if (opcode == 0x74 || opcode == 0x73) dtcWrites.incrementAndGet();
            failIfSelected(Phase.WRITE);
            byte[] response = response(current);
            pieces.add(response == null ? current.clone() : join(current, response));
        }
        @Override public byte[] read(int maximum, int timeout) throws IOException {
            nanos += (pieces.isEmpty() ? timeout : 1) * 1000000L;
            failIfSelected(Phase.READ);
            if (!isHealthy()) throw new TransportException("USB fixture is closed");
            return pieces.isEmpty() ? new byte[0] : pieces.remove();
        }
        private void failIfSelected(Phase phase) throws IOException {
            Fault active = fault;
            if (active == null || active.phase != phase || current == null || (current[2] & 255) != active.opcode || !active.fired.compareAndSet(false, true)) return;
            active.executingThread = Thread.currentThread(); active.entered.countDown(); gate(active.release); healthy = false;
            if (active.uncertain) throw new InterruptedIOException("TX cancelled after acceptance; UART state uncertain");
            throw new TransportException("USB fixture failed at opcode " + Integer.toHexString(active.opcode));
        }
        private byte[] response(byte[] request) {
            if ((request[0] & 255) == 0xfe) return null;
            int opcode = request[2] & 255;
            if (opcode == 0x72) return missingIdentity ? null : reply((byte) 0x72, (byte) 0, (byte) 5, (byte) 1, (byte) 2, (byte) 3, (byte) 4, (byte) 5);
            if (opcode == 0x71) {
                int table = request[3] & 255, responseTable = table, size;
                if (table >= 0x10 && table <= 0x17) { responseTable = 0x13; size = 14; }
                else if (table == 0x20 || table == 0x21) { responseTable = 0x20; size = 3; }
                else if (table == 0xd0) size = 29;
                else size = 6;
                return reply(join(new byte[]{0x71, (byte) responseTable}, new byte[size]));
            }
            if (opcode == 0x74 || opcode == 0x73) {
                return reply((byte) (badDtc && opcode == 0x74 ? 0x73 : opcode), request[3], (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0);
            }
            return reply();
        }
        @Override public void close() { closed = true; }
        KLineTransport transport() { return new KLineTransport(this, this, 100, KLineTransport.EchoMode.AUTO, null); }
    }

    private static final class Probe implements DiagnosticController.Listener {
        final AtomicInteger samples = new AtomicInteger(), connectedStates = new AtomicInteger(), dtcErrors = new AtomicInteger();
        volatile long lastSampleSession;
        volatile HondaClient.DtcReport dtc;
        @Override public void onState(DiagnosticController.Snapshot snapshot) { if (snapshot.connected()) connectedStates.incrementAndGet(); }
        @Override public void onProfile(long session, LiveProfile profile) { }
        @Override public void onSample(long session, long generation, LiveProfile.Sample sample) { lastSampleSession = session; samples.incrementAndGet(); }
        @Override public void onDtc(long session, long operation, HondaClient.DtcReport result) { dtc = result; }
        @Override public void onDtcError(long session, long operation, String reason) { dtcErrors.incrementAndGet(); }
    }
    private static final class Harness implements AutoCloseable {
        final FixtureChannel channel = new FixtureChannel();
        final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor();
        final Probe probe = new Probe();
        final DiagnosticController app = new DiagnosticController(probe, worker, 5000, channel -> ((FixtureChannel) channel).transport());
        void start() throws Exception {
            app.connect(cancel -> channel); await(() -> probe.samples.get() >= 1); barrier(worker);
            equal(DiagnosticController.State.LIVE, app.snapshot().state);
        }
        void pause() throws Exception { check(app.toggleLive(), "Live pause refused"); barrier(worker); equal(DiagnosticController.State.PAUSED, app.snapshot().state); }
        Fault blockLive(Phase phase, boolean uncertain) throws Exception {
            pause(); Fault selected = new Fault(0x71, phase, true, uncertain); channel.fault = selected;
            check(app.toggleLive(), "Live resume refused"); selected.awaitEntry(); return selected;
        }
        void disconnected() throws Exception { await(() -> app.snapshot().state == DiagnosticController.State.DISCONNECTED); barrier(worker); check(channel.closed, "Fatal channel was not closed"); }
        @Override public void close() {
            if (channel.fault != null) channel.fault.finish();
            app.close();
            try { check(worker.awaitTermination(3, TimeUnit.SECONDS), "Controller worker survived close"); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
        }
    }

    private static void fatalIdentity() throws Exception {
        try (Harness h = new Harness()) {
            h.channel.fault = new Fault(0x72, Phase.READ, false, false); h.app.connect(cancel -> h.channel); h.channel.fault.awaitEntry(); h.disconnected();
            equal(0, h.probe.connectedStates.get()); equal(0, h.channel.tableWrites.get());
        }
    }
    private static void optionalIdentity() throws Exception {
        try (Harness h = new Harness()) {
            h.channel.missingIdentity = true; h.start(); equal("", h.app.snapshot().ecuId); check(h.channel.isHealthy(), "Optional ID protocol timeout poisoned USB");
            check(h.app.readDtcs(), "DTC refused after missing ID"); await(() -> h.probe.dtc != null); barrier(h.worker);
            equal(DiagnosticController.State.LIVE, h.app.snapshot().state);
        }
    }
    private static void fatalDiscovery() throws Exception {
        try (Harness h = new Harness()) {
            h.channel.fault = new Fault(0x71, Phase.READ, false, false); h.app.connect(cancel -> h.channel); h.channel.fault.awaitEntry(); h.disconnected();
            equal(1, h.channel.tableWrites.get()); check(!h.app.snapshot().hasSensors, "Failed discovery left a profile");
        }
    }
    private static void profileFatal() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        LiveProfile.Reader fatal = (request, cancel) -> { calls.incrementAndGet(); throw new TransportException("physical channel failure"); };
        try { LiveProfile.detect(fatal, CancelSignal.NEVER); throw new AssertionError("Fatal discovery became a fallback"); }
        catch (TransportException expected) { equal(1, calls.get()); }
        calls.set(0);
        try { oneTable().read(fatal, CancelSignal.NEVER); throw new AssertionError("Fatal live read became an unread sample"); }
        catch (TransportException expected) { equal(1, calls.get()); }
    }
    private static void cancelledLive() throws Exception {
        try (Harness h = new Harness()) {
            h.start(); Fault selected = h.blockLive(Phase.READ, false); int before = h.probe.samples.get();
            check(h.app.toggleLive(), "Pause during RX refused"); selected.finish(); h.disconnected();
            equal(before, h.probe.samples.get()); check(!h.app.toggleLive() && !h.app.readDtcs(), "Fatal paused channel accepted work");
        }
    }
    private static void liveToDtcFatal() throws Exception {
        try (Harness h = new Harness()) {
            h.start(); Fault selected = h.blockLive(Phase.READ, false); int before = h.probe.samples.get();
            check(h.app.readDtcs(), "DTC handoff refused"); selected.finish(); h.disconnected();
            equal(0, h.channel.dtcWrites.get()); equal(before, h.probe.samples.get()); check(h.probe.dtc == null, "Cancelled fatal RX created a DTC report");
        }
    }
    private static void cancelledUncertainTx() throws Exception {
        try (Harness h = new Harness()) {
            h.start(); Fault selected = h.blockLive(Phase.WRITE, true);
            check(h.app.toggleLive(), "Pause during TX refused"); selected.finish(); h.disconnected();
            check(h.app.snapshot().message.contains("USB") || h.app.snapshot().message.contains("TX"), "Fatal cancellation had no transport explanation");
        }
    }
    private static void fatalDtc() throws Exception {
        try (Harness h = new Harness()) {
            h.start(); h.channel.fault = new Fault(0x74, Phase.READ, false, false); int before = h.probe.samples.get();
            check(h.app.readDtcs(), "DTC refused"); h.channel.fault.awaitEntry(); h.disconnected();
            equal(before, h.probe.samples.get()); equal(1, h.channel.dtcWrites.get()); check(h.probe.dtc == null, "Fatal DTC became an empty successful report");
        }
    }
    private static void recoverableDtc() throws Exception {
        try (Harness h = new Harness()) {
            h.start(); h.channel.badDtc = true; int before = h.probe.samples.get(); check(h.app.readDtcs(), "DTC refused");
            await(() -> h.probe.dtcErrors.get() == 1 && h.probe.samples.get() > before); barrier(h.worker);
            equal(DiagnosticController.State.LIVE, h.app.snapshot().state); check(h.channel.isHealthy(), "Protocol DTC error closed USB"); check(h.probe.dtc == null, "Bad DTC accepted");
            h.channel.badDtc = false; check(h.app.readDtcs(), "Recovery DTC refused"); await(() -> h.probe.dtc != null); barrier(h.worker);
        }
    }
    private static void fatalManual() throws Exception {
        try (Harness h = new Harness()) {
            h.start(); h.pause(); int before = h.probe.samples.get(); h.channel.fault = new Fault(0x71, Phase.READ, false, false);
            check(h.app.readOnce(), "Manual read refused"); h.channel.fault.awaitEntry(); h.disconnected();
            equal(before, h.probe.samples.get()); check(!h.app.snapshot().hasSensors, "Fatal manual read kept a stale profile");
        }
    }
    private static void fatalRediscovery() throws Exception {
        try (Harness h = new Harness()) {
            h.start(); h.channel.fault = new Fault(0x71, Phase.READ, false, false);
            check(h.app.detectAgain(), "Rediscovery refused"); h.channel.fault.awaitEntry(); h.disconnected();
            check(!h.app.snapshot().hasSensors, "Fatal rediscovery kept a profile");
        }
    }
    private static void replacementSession() throws Exception {
        FixtureChannel old = new FixtureChannel(), replacement = new FixtureChannel(); Probe probe = new Probe();
        Fault selected = new Fault(0x71, Phase.READ, true, false);
        CountDownLatch oldCompleted = new CountDownLatch(1);
        ScheduledExecutorService worker = new ScheduledThreadPoolExecutor(2) {
            @Override protected void afterExecute(Runnable task, Throwable error) {
                super.afterExecute(task, error);
                if (Thread.currentThread() == selected.executingThread && !old.healthy) oldCompleted.countDown();
            }
        };
        DiagnosticController app = new DiagnosticController(probe, worker, 5000, channel -> ((FixtureChannel) channel).transport());
        try {
            app.connect(cancel -> old); await(() -> probe.samples.get() >= 1); app.toggleLive(); barrier(worker);
            old.fault = selected; app.toggleLive(); selected.awaitEntry(); app.connect(cancel -> replacement);
            long newSession = app.snapshot().session;
            await(() -> probe.lastSampleSession == newSession && app.snapshot().state == DiagnosticController.State.LIVE);
            selected.finish(); check(oldCompleted.await(3, TimeUnit.SECONDS), "Old fatal callback did not complete");
            equal(newSession, app.snapshot().session); equal(DiagnosticController.State.LIVE, app.snapshot().state);
            check(!replacement.closed, "Old fatal callback closed replacement USB");
        } finally { selected.finish(); app.close(); worker.awaitTermination(3, TimeUnit.SECONDS); }
    }
    private static void nonblockingUi() throws Exception {
        try (Harness h = new Harness()) {
            h.start(); Fault selected = h.blockLive(Phase.READ, false);
            ScheduledExecutorService ui = Executors.newSingleThreadScheduledExecutor();
            try {
                ui.submit(() -> { h.app.snapshot(); check(h.app.toggleLive(), "Blocked RX prevented pause"); h.app.snapshot(); h.app.disconnect("USB detached"); }).get(500, TimeUnit.MILLISECONDS);
                check(h.channel.closed, "UI disconnect did not revoke channel immediately"); selected.finish(); barrier(h.worker);
                equal(DiagnosticController.State.DISCONNECTED, h.app.snapshot().state);
            } finally { selected.finish(); ui.shutdownNow(); ui.awaitTermination(3, TimeUnit.SECONDS); }
        }
    }
}
