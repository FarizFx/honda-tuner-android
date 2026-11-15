package id.hondatuner.core;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** One serialized loop; stopping revokes both queued work and in-flight results. */
public final class LiveAcquisition {
    public interface Listener {
        void onSample(long generation, LiveProfile.Sample sample);
        void onStopped(long generation, String reason);
    }
    private final ScheduledExecutorService executor;
    private final LiveProfile.Reader reader;
    private final Listener listener;
    private long intervalMillis;
    private final DiagnosticLog log;
    private ScheduledFuture<?> pending;
    private LiveProfile profile;
    private boolean running;
    private long generation;
    private int failedSamples;

    public LiveAcquisition(ScheduledExecutorService executor, LiveProfile.Reader reader, Listener listener, long intervalMillis) {
        this(executor, reader, listener, intervalMillis, null);
    }
    public LiveAcquisition(ScheduledExecutorService executor, LiveProfile.Reader reader, Listener listener, long intervalMillis, DiagnosticLog log) {
        if (intervalMillis <= 0) throw new IllegalArgumentException("Interval harus positif.");
        this.executor = executor; this.reader = reader; this.listener = listener; this.intervalMillis = intervalMillis;
        this.log = log;
    }

    public synchronized long start(LiveProfile profile) {
        stop();
        this.profile = profile;
        running = true;
        failedSamples = 0;
        final long run = generation;
        pending = executor.schedule(() -> poll(run), 0, TimeUnit.MILLISECONDS);
        return run;
    }
    public synchronized void stop() {
        running = false;
        generation++;
        if (pending != null) pending.cancel(false);
        pending = null;
    }
    public synchronized boolean isRunning() { return running; }
    public synchronized long currentGeneration() { return generation; }
    public synchronized void setIntervalMillis(long value) {
        if (value <= 0) throw new IllegalArgumentException("Interval harus positif.");
        intervalMillis = value;
    }
    public synchronized boolean isCurrent(long run) { return generation == run; }
    private synchronized boolean cancelled(long run) { return !running || generation != run; }

    private void poll(long run) {
        final LiveProfile activeProfile;
        synchronized (this) { if (cancelled(run)) return; pending = null; activeProfile = profile; }
        long began = System.nanoTime();
        LiveProfile.Sample sample;
        try { sample = activeProfile.read(reader, () -> cancelled(run)); }
        catch (InterruptedIOException ignored) { return; }
        catch (IOException error) {
            if (cancelled(run)) return;
            if (log != null) log.warn("LIVE", error.getMessage()); sample = activeProfile.unread(error.getMessage());
        }
        catch (RuntimeException error) {
            if (cancelled(run)) return;
            if (log != null) log.error("LIVE", error); sample = activeProfile.unread(error.getClass().getSimpleName() + ": " + error.getMessage());
        }
        final boolean terminal;
        synchronized (this) {
            if (cancelled(run)) return;
            failedSamples = sample.validGroups == 0 ? failedSamples + 1 : 0;
            terminal = failedSamples >= 3;
            if (terminal) running = false;
        }
        // Never call application listeners under this lock. UI commits validate
        // the generation again, so Stop can discard a queued callback safely.
        if (log != null) {
            log.debug("LIVE", "groups=" + sample.validGroups + " fields=" + sample.values.size() + " elapsed=" + DiagnosticLog.elapsed(began));
            for (java.util.Map.Entry<Integer, String> failure : sample.failures.entrySet()) log.warn("LIVE", "0x" + Integer.toHexString(failure.getKey()) + ": " + failure.getValue());
            if (terminal) log.error("LIVE", "Live berhenti: 3 putaran berturut-turut tanpa balasan sensor yang valid.");
        }
        listener.onSample(run, sample);
        if (terminal) {
            if (isCurrent(run)) listener.onStopped(run, "Live berhenti setelah 3 pembacaan gagal. Periksa kabel atau deteksi ulang sensor.");
            return;
        }
        synchronized (this) {
            if (cancelled(run)) return;
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - began);
            pending = executor.schedule(() -> poll(run), Math.max(0, intervalMillis - elapsedMillis), TimeUnit.MILLISECONDS);
        }
    }
}
