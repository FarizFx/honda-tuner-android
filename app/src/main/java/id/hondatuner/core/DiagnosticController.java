package id.hondatuner.core;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** The actual app workflow, shared with JVM regression tests. All wire work is serialized. */
public final class DiagnosticController implements AutoCloseable {
    public enum State { DISCONNECTED, CONNECTING, DISCOVERING, LIVE, PAUSED, READING_DTC, READING_SAMPLE }
    public interface ChannelFactory { ByteChannel open(CancelSignal cancel) throws IOException; }
    public interface TransportFactory { KLineTransport create(ByteChannel channel); }
    public interface Listener {
        void onState(Snapshot snapshot);
        void onProfile(long session, LiveProfile profile);
        void onSample(long session, long generation, LiveProfile.Sample sample);
        void onDtc(long session, long operation, HondaClient.DtcReport report);
        void onDtcError(long session, long operation, String reason);
    }
    public static final class Snapshot {
        public final long session;
        public final State state;
        public final String ecuId, identityStatus, message;
        public final boolean hasSensors;
        private Snapshot(long session, State state, String ecuId, String identityStatus, String message, boolean hasSensors) {
            this.session = session; this.state = state; this.ecuId = ecuId; this.identityStatus = identityStatus; this.message = message; this.hasSensors = hasSensors;
        }
        public boolean connected() { return state != State.DISCONNECTED && state != State.CONNECTING; }
        public boolean busy() { return state == State.CONNECTING || state == State.DISCOVERING || state == State.READING_DTC || state == State.READING_SAMPLE; }
    }
    private final ScheduledExecutorService executor;
    private final Listener listener;
    private long liveInterval;
    private Set<String> liveFields;
    private final TransportFactory transportFactory;
    private final DiagnosticLog log;
    private long session, operation;
    private boolean closed;
    private HondaClient client;
    private LiveProfile profile;
    private LiveAcquisition acquisition;
    private State state = State.DISCONNECTED;
    private String ecuId = "", message = "Hubungkan adaptor FT232R K-Line lewat USB OTG.";
    private String identityStatus = "ID ECU belum dibaca.";

    public DiagnosticController(Listener listener) {
        this(listener, newExecutor(), 500);
    }
    public DiagnosticController(Listener listener, DiagnosticLog log) {
        this(listener, newExecutor(), 500, channel -> new KLineTransport(channel, System::nanoTime, 1000, KLineTransport.EchoMode.AUTO, log), log);
    }
    private static ScheduledExecutorService newExecutor() {
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread worker = new Thread(runnable, "Honda-KLine"); worker.setDaemon(true); return worker;
        });
    }
    public DiagnosticController(Listener listener, ScheduledExecutorService executor, long liveInterval) {
        this(listener, executor, liveInterval, KLineTransport::new);
    }
    public DiagnosticController(Listener listener, ScheduledExecutorService executor, long liveInterval, TransportFactory transportFactory) {
        this(listener, executor, liveInterval, transportFactory, null);
    }
    public DiagnosticController(Listener listener, ScheduledExecutorService executor, long liveInterval, TransportFactory transportFactory, DiagnosticLog log) {
        if (liveInterval < 0) throw new IllegalArgumentException("Interval tidak boleh negatif.");
        this.listener = Objects.requireNonNull(listener); this.executor = Objects.requireNonNull(executor);
        this.liveInterval = liveInterval; this.transportFactory = Objects.requireNonNull(transportFactory);
        this.log = log;
    }
    public synchronized Snapshot snapshot() { return new Snapshot(session, state, ecuId, identityStatus, message, profile != null && acquisition != null); }
    public synchronized boolean isCurrent(long token) { return !closed && session == token; }
    public synchronized boolean isCurrentOperation(long token, long op) { return isCurrent(token) && operation == op; }
    public synchronized boolean isCurrentLive(long token, long generation) {
        return isCurrent(token) && acquisition != null && acquisition.isCurrent(generation);
    }
    private synchronized boolean cancelled(long token, long op) { return !isCurrentOperation(token, op); }
    private void publish(State state, String message) {
        this.state = state; this.message = message == null || message.trim().isEmpty() ? "Tidak ada detail; buka Debug untuk log sesi." : message;
        if (log != null) log.info("STATE", "session=" + session + " " + state + " · " + message);
        listener.onState(snapshot());
    }
    private static String errorMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().trim().isEmpty() ? error.getClass().getSimpleName() : error.getMessage();
    }

    private synchronized boolean transportFailed(HondaClient owner, long token, Throwable error) {
        if (owner == null || !isCurrent(token) || client != owner) return false;
        if (!(error instanceof TransportException) && owner.isHealthy()) return false;
        Throwable failure = error == null
                ? new TransportException("Kanal USB tidak dapat digunakan. Hubungkan ulang adaptor.") : error;
        if (log != null) log.error("USB SESSION", failure);
        // A channel belongs to the session, not to one operation or live
        // generation. Cancellation cannot make an unusable channel healthy.
        disconnect(errorMessage(failure));
        return true;
    }

    private boolean usableClient() {
        return client != null && !transportFailed(client, session, null);
    }

    private void releaseAbandoned(HondaClient opened, long token) {
        synchronized (this) {
            if (opened == null || (isCurrent(token) && client == opened)) return;
        }
        try { opened.close(); }
        catch (RuntimeException error) { if (log != null) log.warn("USB CLOSE", errorMessage(error)); }
    }

    public synchronized void connect(ChannelFactory factory) {
        connect(factory, transportFactory);
    }
    public synchronized void connect(ChannelFactory factory, TransportFactory selectedTransport) {
        if (closed) return;
        disconnect("Mengganti koneksi.");
        final long token = session, op = ++operation;
        publish(State.CONNECTING, "Membuka USB dan memulai sesi ECU...");
        executor.execute(() -> openAndDetect(factory, selectedTransport, token, op));
    }

    private synchronized void progress(long token, long op, State state, String message) {
        if (!cancelled(token, op)) publish(state, message);
    }
    private void openAndDetect(ChannelFactory factory, TransportFactory selectedTransport, long token, long op) {
        HondaClient opened = null;
        ByteChannel unowned = null;
        boolean ready = false;
        CancelSignal cancel = () -> cancelled(token, op);
        try {
            cancel.check();
            unowned = factory.open(cancel);
            opened = new HondaClient(selectedTransport.create(unowned), log);
            unowned = null;
            synchronized (this) {
                if (cancelled(token, op)) return;
                client = opened;
            }
            String identifier = opened.connect(cancel, message -> progress(token, op, State.CONNECTING, message), 3);
            synchronized (this) {
                if (transportFailed(opened, token, null)) return;
                if (cancelled(token, op)) return;
                ready = true; ecuId = identifier;
                identityStatus = opened.identityStatus();
                publish(State.DISCOVERING, "ECU terhubung. Mendeteksi grup sensor...");
            }
            installProfile(LiveProfile.detect(opened, cancel, log, message -> progress(token, op, State.DISCOVERING, message)), token, op);
        } catch (InterruptedIOException error) {
            synchronized (this) {
                if (transportFailed(opened, token, error) || cancelled(token, op)) return;
                if (ready) publish(State.PAUSED, errorMessage(error));
                else disconnect(errorMessage(error));
            }
        } catch (IOException | RuntimeException error) {
            synchronized (this) {
                if (transportFailed(opened, token, error) || cancelled(token, op)) return;
                if (log != null) log.error("CONNECT", error);
                if (ready) publish(State.PAUSED, errorMessage(error));
                else disconnect(errorMessage(error));
            }
        } finally {
            transportFailed(opened, token, null);
            releaseAbandoned(opened, token);
            if (unowned != null) try { unowned.close(); }
            catch (RuntimeException error) { if (log != null) log.warn("USB CLOSE", errorMessage(error)); }
        }
    }

    private synchronized void installProfile(LiveProfile found, long token, long op) {
        if (cancelled(token, op)) return;
        if (!usableClient()) return;
        profile = found;
        listener.onProfile(token, found);
        if (cancelled(token, op)) return;
        if (acquisition != null) acquisition.stop();
        final HondaClient owner = client;
        acquisition = new LiveAcquisition(executor, owner, new LiveAcquisition.Listener() {
            @Override public void onSample(long generation, LiveProfile.Sample sample) {
                synchronized (DiagnosticController.this) {
                    if (transportFailed(owner, token, null)) return;
                    if (isCurrentLive(token, generation)) listener.onSample(token, generation, sample);
                }
            }
            @Override public void onStopped(long generation, String reason) {
                synchronized (DiagnosticController.this) {
                    if (transportFailed(owner, token, null)) return;
                    if (isCurrentLive(token, generation)) publish(State.PAUSED, reason);
                }
            }
            @Override public void onTransportFailure(long generation, IOException error) {
                transportFailed(owner, token, error);
            }
        }, liveInterval, log);
        acquisition.setFields(fieldsFor(profile));
        startLive();
    }

    private void startLive() {
        if (!usableClient()) return;
        acquisition.start(profile);
        publish(State.LIVE, liveMessage());
    }
    private String liveMessage() {
        return liveInterval == 0 ? "Live data aktif. Secepatnya, tanpa jeda tambahan." : "Live data aktif. Target pembacaan setiap " + liveInterval + " ms.";
    }
    public synchronized long liveIntervalMillis() { return liveInterval; }
    public synchronized void setLiveIntervalMillis(long interval) {
        if (interval < 0 || interval > 5000) throw new IllegalArgumentException("Live interval must be 0..5000 ms.");
        liveInterval = interval;
        if (acquisition != null) acquisition.setIntervalMillis(interval);
        if (state == State.LIVE) publish(State.LIVE, liveMessage());
    }
    public synchronized void setLiveFields(Collection<String> fields) {
        if (fields != null && fields.isEmpty()) throw new IllegalArgumentException("Pilih minimal satu sensor.");
        liveFields = fields == null ? null : Collections.unmodifiableSet(new LinkedHashSet<>(fields));
        if (acquisition != null) acquisition.setFields(fieldsFor(profile));
    }
    private Set<String> fieldsFor(LiveProfile selectedProfile) {
        // A different ECU can have different fields. Keep diagnostics running
        // until the UI applies its saved choices to the new layout.
        return liveFields == null || selectedProfile == null || Collections.disjoint(selectedProfile.fields, liveFields) ? null : liveFields;
    }
    public synchronized boolean readOnce() {
        if (closed || !usableClient() || state != State.PAUSED || profile == null || acquisition == null) return false;
        acquisition.stop();
        final long token = session, op = ++operation, generation = acquisition.currentGeneration();
        final HondaClient current = client;
        final LiveProfile currentProfile = profile;
        final Set<String> currentFields = fieldsFor(currentProfile);
        publish(State.READING_SAMPLE, "Membaca satu putaran sensor...");
        executor.execute(() -> {
            try {
                LiveProfile.Sample sample = currentProfile.read(current, () -> cancelled(token, op), currentFields);
                synchronized (this) {
                    if (transportFailed(current, token, null)) return;
                    if (cancelled(token, op)) return;
                    if (log != null) {
                        log.info("READ ONCE", "groups=" + sample.validGroups + ", fields=" + sample.values.size());
                        for (java.util.Map.Entry<Integer, String> failure : sample.failures.entrySet()) log.warn("READ ONCE", "0x" + Integer.toHexString(failure.getKey()) + ": " + failure.getValue());
                        if (sample.validGroups == 0) log.error("READ ONCE", "Tidak ada grup sensor terbaca dalam putaran manual.");
                    }
                    listener.onSample(token, generation, sample);
                    if (!cancelled(token, op)) publish(State.PAUSED, sample.validGroups == 0 ? "Sensor belum terbaca; buka Debug untuk detail." : "Satu putaran selesai. Live tetap dijeda.");
                }
            } catch (InterruptedIOException ignored) { }
            catch (IOException | RuntimeException error) {
                synchronized (this) {
                    if (transportFailed(current, token, error)) return;
                    if (!cancelled(token, op)) {
                        if (log != null) log.error("READ ONCE", error);
                        publish(State.PAUSED, errorMessage(error));
                    }
                }
            } finally {
                transportFailed(current, token, null);
            }
        });
        return true;
    }
    public synchronized boolean toggleLive() {
        if (!usableClient()) return false;
        if (state == State.LIVE) {
            acquisition.stop(); publish(State.PAUSED, "Live dijeda. Angka menampilkan sampel terakhir."); return true;
        }
        if (state == State.PAUSED && acquisition != null && profile != null) { startLive(); return true; }
        return false;
    }

    public synchronized boolean detectAgain() {
        if (!usableClient() || snapshot().busy() || state == State.DISCONNECTED) return false;
        if (acquisition != null) acquisition.stop();
        acquisition = null; profile = null;
        final long token = session, op = ++operation;
        final HondaClient current = client;
        publish(State.DISCOVERING, "Mendeteksi ulang sensor ECU...");
        executor.execute(() -> {
            try { installProfile(LiveProfile.detect(current, () -> cancelled(token, op), log, message -> progress(token, op, State.DISCOVERING, message)), token, op); }
            catch (InterruptedIOException ignored) { }
            catch (IOException | RuntimeException error) {
                synchronized (DiagnosticController.this) {
                    if (transportFailed(current, token, error)) return;
                    if (!cancelled(token, op)) {
                        if (log != null) log.error("REDETECT", error);
                        publish(State.PAUSED, errorMessage(error));
                    }
                }
            } finally {
                transportFailed(current, token, null);
            }
        });
        return true;
    }

    public synchronized boolean readDtcs() {
        if (!usableClient() || snapshot().busy() || state == State.DISCONNECTED) return false;
        final boolean resume = acquisition != null && acquisition.isRunning();
        if (acquisition != null) acquisition.stop();
        final long token = session, op = ++operation;
        final HondaClient current = client;
        publish(State.READING_DTC, "Membaca DTC saat ini dan tersimpan...");
        executor.execute(() -> {
            try {
                HondaClient.DtcReport result = current.readDtcs(() -> cancelled(token, op));
                synchronized (DiagnosticController.this) {
                    if (transportFailed(current, token, null)) return;
                    if (cancelled(token, op)) return;
                    listener.onDtc(token, op, result);
                    if (cancelled(token, op)) return;
                    if (resume) startLive(); else publish(State.PAUSED, "Pembacaan DTC selesai.");
                }
            } catch (InterruptedIOException ignored) { }
            catch (IOException | RuntimeException error) {
                synchronized (DiagnosticController.this) {
                    if (transportFailed(current, token, error)) return;
                    if (cancelled(token, op)) return;
                    if (log != null) log.error("DTC", error);
                    listener.onDtcError(token, op, errorMessage(error));
                    if (cancelled(token, op)) return;
                    if (resume) startLive(); else publish(State.PAUSED, "DTC belum terbaca. " + errorMessage(error));
                }
            } finally {
                transportFailed(current, token, null);
            }
        });
        return true;
    }

    public synchronized void disconnect(String reason) {
        session++; operation++;
        if (acquisition != null) acquisition.stop();
        acquisition = null; profile = null; ecuId = "";
        identityStatus = "ID ECU belum dibaca.";
        if (client != null) client.close();
        client = null;
        publish(State.DISCONNECTED, reason == null ? "Koneksi terputus." : reason);
    }
    @Override public synchronized void close() {
        if (closed) return;
        disconnect("Aplikasi ditutup.");
        closed = true;
        executor.shutdownNow();
    }
}
