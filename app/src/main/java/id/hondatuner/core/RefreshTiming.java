package id.hondatuner.core;

/** Measured completion-to-completion cadence, separate from chart history and pauses. */
public final class RefreshTiming {
    private final double[] intervals = new double[8];
    private long generation = Long.MIN_VALUE, previousNanos;
    private boolean hasPrevious;
    private int count, next;
    private Double readingMillis, latestMillis;

    public synchronized void reset() {
        generation = Long.MIN_VALUE; hasPrevious = false; count = next = 0;
        readingMillis = latestMillis = null;
    }
    public synchronized void record(long run, LiveProfile.Sample sample, boolean continuous) {
        if (generation != run) { reset(); generation = run; }
        readingMillis = sample.readDurationMillis();
        if (!continuous) { hasPrevious = false; count = next = 0; latestMillis = null; return; }
        if (hasPrevious && sample.completedNanos > previousNanos) {
            latestMillis = (sample.completedNanos - previousNanos) / 1000000.0;
            intervals[next] = latestMillis; next = (next + 1) % intervals.length;
            count = Math.min(intervals.length, count + 1);
        }
        if (!hasPrevious || sample.completedNanos > previousNanos) previousNanos = sample.completedNanos;
        hasPrevious = true;
    }
    public synchronized Double actualMillis() {
        if (count == 0) return null;
        double sum = 0; for (int i = 0; i < count; i++) sum += intervals[i];
        return sum / count;
    }
    public synchronized Double latestMillis() { return latestMillis; }
    public synchronized Double readingMillis() { return readingMillis; }
    public synchronized double updatesPerSecond() {
        Double actual = actualMillis(); return actual == null || actual <= 0 ? 0 : 1000.0 / actual;
    }
}
