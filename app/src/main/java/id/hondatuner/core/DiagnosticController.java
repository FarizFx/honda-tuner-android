package id.hondatuner.core;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** The actual app workflow, shared with JVM regression tests. All wire work is serialized. */
public final class DiagnosticController implements AutoCloseable {
    public enum State { DISCONNECTED, CONNECTING, DISCOVERING, LIVE, PAUSED, READING_DTC }
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
        public final String ecuId, message;
        public final boolean hasSensors;
        private Snapshot(long session, State state, String ecuId, String message, boolean hasSensors) {
            this.session = session; this.state = state; this.ecuId = ecuId; this.message = message; this.hasSensors = hasSensors;
        }
        public boolean connected() { return state != State.DISCONNECTED && state != State.CONNECTING; }
        public boolean busy() { return state == State.CONNECTING || state == State.DISCOVERING || state == State.READING_DTC; }
    }
    private final ScheduledExecutorService executor;
    private final Listener listener;
    private final long liveInterval;
    private final TransportFactory transportFactory;
    private long session, operation;
    private boolean closed;
    private HondaClient client;
    private LiveProfile profile;
    private LiveAcquisition acquisition;
    private State state = State.DISCONNECTED;
    private String ecuId = "", message = "Hubungkan adaptor FT232R K-Line lewat USB OTG.";

    public DiagnosticController(Listener listener) {
        this(listener, Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread worker = new Thread(runnable, "Honda-KLine"); worker.setDaemon(true); return worker;
        }), 500);
    }
    public DiagnosticController(Listener listener, ScheduledExecutorService executor, long liveInterval) {
        this(listener, executor, liveInterval, KLineTransport::new);
    }
    public DiagnosticController(Listener listener, ScheduledExecutorService executor, long liveInterval, TransportFactory transportFactory) {
        this.listener = listener; this.executor = executor; this.liveInterval = liveInterval; this.transportFactory = transportFactory;
    }
    public synchronized Snapshot snapshot() { return new Snapshot(session, state, ecuId, message, profile != null && acquisition != null); }
    public synchronized boolean isCurrent(long token) { return !closed && session == token; }
    public synchronized boolean isCurrentOperation(long token, long op) { return isCurrent(token) && operation == op; }
    public synchronized boolean isCurrentLive(long token, long generation) {
        return isCurrent(token) && acquisition != null && acquisition.isCurrent(generation);
    }
    private synchronized boolean cancelled(long token, long op) { return !isCurrentOperation(token, op); }
    private void publish(State state, String message) {
        this.state = state; this.message = message; listener.onState(snapshot());
    }

    public synchronized void connect(ChannelFactory factory) {
        if (closed) return;
        disconnect("Mengganti koneksi.");
        final long token = session, op = ++operation;
        publish(State.CONNECTING, "Membuka USB dan memulai sesi ECU...");
        executor.execute(() -> openAndDetect(factory, token, op));
    }

    private void openAndDetect(ChannelFactory factory, long token, long op) {
        HondaClient opened = null;
        boolean ready = false;
        CancelSignal cancel = () -> cancelled(token, op);
        try {
            cancel.check();
            ByteChannel channel = factory.open(cancel);
            opened = new HondaClient(transportFactory.create(channel));
            synchronized (this) {
                if (cancelled(token, op)) { opened.close(); return; }
                client = opened;
            }
            String identifier = opened.connect(cancel);
            synchronized (this) {
                if (cancelled(token, op)) return;
                ready = true; ecuId = identifier;
                publish(State.DISCOVERING, "ECU terhubung. Mendeteksi grup sensor...");
            }
            installProfile(LiveProfile.detect(opened, cancel), token, op);
        } catch (InterruptedIOException ignored) {
            if (opened != null) opened.close();
        } catch (IOException | RuntimeException error) {
            synchronized (this) {
                if (cancelled(token, op)) { if (opened != null) opened.close(); return; }
                if (ready) publish(State.PAUSED, error.getMessage());
                else disconnect(error.getMessage());
            }
        }
    }

    private synchronized void installProfile(LiveProfile found, long token, long op) {
        if (cancelled(token, op)) return;
        profile = found;
        listener.onProfile(token, found);
        if (cancelled(token, op)) return;
        if (acquisition != null) acquisition.stop();
        acquisition = new LiveAcquisition(executor, client, new LiveAcquisition.Listener() {
            @Override public void onSample(long generation, LiveProfile.Sample sample) {
                synchronized (DiagnosticController.this) {
                    if (isCurrentLive(token, generation)) listener.onSample(token, generation, sample);
                }
            }
            @Override public void onStopped(long generation, String reason) {
                synchronized (DiagnosticController.this) {
                    if (isCurrentLive(token, generation)) publish(State.PAUSED, reason);
                }
            }
        }, liveInterval);
        startLive();
    }

    private void startLive() {
        acquisition.start(profile);
        publish(State.LIVE, "Live data aktif. Target pembacaan setiap 500 ms.");
    }
    public synchronized boolean toggleLive() {
        if (state == State.LIVE) {
            acquisition.stop(); publish(State.PAUSED, "Live dijeda. Angka menampilkan sampel terakhir."); return true;
        }
        if (state == State.PAUSED && acquisition != null && profile != null) { startLive(); return true; }
        return false;
    }

    public synchronized boolean detectAgain() {
        if (client == null || snapshot().busy() || state == State.DISCONNECTED) return false;
        if (acquisition != null) acquisition.stop();
        acquisition = null; profile = null;
        final long token = session, op = ++operation;
        final HondaClient current = client;
        publish(State.DISCOVERING, "Mendeteksi ulang sensor ECU...");
        executor.execute(() -> {
            try { installProfile(LiveProfile.detect(current, () -> cancelled(token, op)), token, op); }
            catch (InterruptedIOException ignored) { }
            catch (IOException | RuntimeException error) {
                synchronized (DiagnosticController.this) { if (!cancelled(token, op)) publish(State.PAUSED, error.getMessage()); }
            }
        });
        return true;
    }

    public synchronized boolean readDtcs() {
        if (client == null || snapshot().busy() || state == State.DISCONNECTED) return false;
        final boolean resume = acquisition != null && acquisition.isRunning();
        if (acquisition != null) acquisition.stop();
        final long token = session, op = ++operation;
        final HondaClient current = client;
        publish(State.READING_DTC, "Membaca DTC saat ini dan tersimpan...");
        executor.execute(() -> {
            try {
                HondaClient.DtcReport result = current.readDtcs(() -> cancelled(token, op));
                synchronized (DiagnosticController.this) {
                    if (cancelled(token, op)) return;
                    listener.onDtc(token, op, result);
                    if (cancelled(token, op)) return;
                    if (resume) startLive(); else publish(State.PAUSED, "Pembacaan DTC selesai.");
                }
            } catch (InterruptedIOException ignored) { }
            catch (IOException | RuntimeException error) {
                synchronized (DiagnosticController.this) {
                    if (cancelled(token, op)) return;
                    listener.onDtcError(token, op, error.getMessage());
                    if (resume) startLive(); else publish(State.PAUSED, "DTC belum terbaca. " + error.getMessage());
                }
            }
        });
        return true;
    }

    public synchronized void disconnect(String reason) {
        session++; operation++;
        if (acquisition != null) acquisition.stop();
        acquisition = null; profile = null; ecuId = "";
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
