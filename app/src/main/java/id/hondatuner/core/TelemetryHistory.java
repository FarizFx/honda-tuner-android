package id.hondatuner.core;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Actual readings only: bounded CSV/chart history and session statistics. */
public final class TelemetryHistory {
    public static final class Statistics {
        public final long readings;
        public final Double minimum, maximum, average;
        private Statistics(long readings, Double minimum, Double maximum, Double average) {
            this.readings = readings; this.minimum = minimum; this.maximum = maximum; this.average = average;
        }
    }
    public static final class Point {
        public final long timestampMillis, timestampNanos;
        public final Double value;
        private Point(LiveProfile.Sample sample, Double value) {
            timestampMillis = sample.completedMillis; timestampNanos = sample.completedNanos; this.value = value;
        }
    }
    private static final class Accumulator {
        long count; double min, max, average;
        void add(double value) {
            if (count++ == 0) { min = max = average = value; }
            else { min = Math.min(min, value); max = Math.max(max, value); average += (value - average) / count; }
        }
        Statistics snapshot() { return new Statistics(count, count == 0 ? null : min, count == 0 ? null : max, count == 0 ? null : average); }
    }
    private final int maximumSamples;
    private final Deque<LiveProfile.Sample> samples = new ArrayDeque<>();
    private final Map<String, Accumulator> statistics = new LinkedHashMap<>();
    private List<String> fields = Collections.emptyList();
    private long total;
    public TelemetryHistory(int maximumSamples) {
        if (maximumSamples < 1) throw new IllegalArgumentException("History limit must be positive.");
        this.maximumSamples = maximumSamples;
    }
    public synchronized void reset(List<String> fields) {
        samples.clear(); statistics.clear(); total = 0;
        this.fields = Collections.unmodifiableList(new ArrayList<>(fields));
        for (String field : fields) statistics.put(field, new Accumulator());
    }
    public synchronized void add(LiveProfile.Sample sample) {
        samples.addLast(sample); total++;
        while (samples.size() > maximumSamples) samples.removeFirst();
        for (String field : fields) {
            Double value = numeric(sample.values.get(field));
            if (value != null) statistics.get(field).add(value);
        }
    }
    /** Re-detection in the same ECU session retains accumulated statistics and CSV columns. */
    public synchronized void includeFields(List<String> added) {
        List<String> combined = new ArrayList<>(fields);
        for (String field : added) if (!statistics.containsKey(field)) {
            combined.add(field); statistics.put(field, new Accumulator());
        }
        fields = Collections.unmodifiableList(combined);
    }
    /** Disconnect ends records immediately; retained CSV/chart samples remain exportable. */
    public synchronized void clearStatistics() {
        for (String field : fields) statistics.put(field, new Accumulator());
    }
    public static Double numeric(Object value) {
        if (!(value instanceof Number)) return null;
        double number = ((Number) value).doubleValue();
        return Double.isFinite(number) ? number : null;
    }
    public synchronized int size() { return samples.size(); }
    public synchronized long totalSamples() { return total; }
    public synchronized List<String> fields() { return fields; }
    public synchronized Statistics statistics(String field) {
        Accumulator values = statistics.get(field);
        return values == null ? new Statistics(0, null, null, null) : values.snapshot();
    }
    public synchronized List<Point> points(String field, int limit) {
        List<Point> result = new ArrayList<>();
        int skip = Math.max(0, samples.size() - Math.max(1, limit)), index = 0;
        for (LiveProfile.Sample sample : samples) if (index++ >= skip) result.add(new Point(sample, numeric(sample.values.get(field))));
        return Collections.unmodifiableList(result);
    }
    public synchronized double updatesPerSecond() {
        if (samples.size() < 2) return 0;
        long elapsed = samples.getLast().completedNanos - samples.getFirst().completedNanos;
        return elapsed <= 0 ? 0 : (samples.size() - 1) * 1000000000.0 / elapsed;
    }
    public synchronized String csv() {
        StringBuilder out = new StringBuilder("sample,epoch_ms,utc_time,valid_groups");
        for (String field : fields) out.append(',').append(quote(field));
        out.append(",error_details\r\n");
        long index = total - samples.size();
        for (LiveProfile.Sample sample : samples) {
            out.append(++index).append(',').append(sample.completedMillis).append(',')
                    .append(Instant.ofEpochMilli(sample.completedMillis)).append(',').append(sample.validGroups);
            for (String field : fields) {
                Object value = sample.values.get(field);
                if (value instanceof Number && numeric(value) == null) value = null;
                out.append(',').append(value == null ? "" : quote(value.toString()));
            }
            StringBuilder errors = new StringBuilder();
            for (Map.Entry<Integer, String> failure : sample.failures.entrySet()) {
                if (errors.length() != 0) errors.append("; ");
                errors.append("0x").append(Integer.toHexString(failure.getKey())).append(": ").append(failure.getValue());
            }
            out.append(',').append(quote(errors.toString())).append("\r\n");
        }
        return out.toString();
    }
    private static String quote(String value) {
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) return value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
