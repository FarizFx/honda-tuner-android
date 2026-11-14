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
    private final long intervalMillis;
    private ScheduledFuture<?> pending;
    private LiveProfile profile;
    private boolean running;
    private long generation;
    private int failedSamples;

    public LiveAcquisition(ScheduledExecutorService executor, LiveProfile.Reader reader, Listener listener, long intervalMillis) {
        if (intervalMillis <= 0) throw new IllegalArgumentException("Interval harus positif.");
        this.executor = executor; this.reader = reader; this.listener = listener; this.intervalMillis = intervalMillis;
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
    public synchronized boolean isCurrent(long run) { return generation == run; }
    private synchronized boolean cancelled(long run) { return !running || generation != run; }

    private void poll(long run) {
        final LiveProfile activeProfile;
        synchronized (this) { if (cancelled(run)) return; pending = null; activeProfile = profile; }
        long began = System.nanoTime();
        LiveProfile.Sample sample;
        try { sample = activeProfile.read(reader, () -> cancelled(run)); }
        catch (InterruptedIOException ignored) { return; }
        catch (IOException error) { sample = activeProfile.unread(error.getMessage()); }
        final boolean terminal;
        synchronized (this) {
            if (cancelled(run)) return;
            failedSamples = sample.validGroups == 0 ? failedSamples + 1 : 0;
            terminal = failedSamples >= 3;
            if (terminal) running = false;
        }
        // Never call application listeners under this lock. UI commits validate
        // the generation again, so Stop can discard a queued callback safely.
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
