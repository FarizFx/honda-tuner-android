package id.hondatuner.core;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/** Bounded session trace. Observers run outside its lock and their errors are isolated. */
public final class DiagnosticLog {
    public interface Sink { void onEntry(Entry entry); }
    public static final class Entry {
        public final long sequence, timestampMillis, elapsedMillis;
        public final String level, source, message;
        private Entry(long sequence, long timestampMillis, long elapsedMillis, String level, String source, String message) {
            this.sequence = sequence; this.timestampMillis = timestampMillis; this.elapsedMillis = elapsedMillis;
            this.level = level; this.source = source; this.message = message;
        }
        public String line() {
            return Instant.ofEpochMilli(timestampMillis) + " +" + elapsedMillis + "ms #" + sequence +
                    " " + level + " [" + source + "] " + message;
        }
    }
    private final Deque<Entry> entries = new ArrayDeque<>();
    private final int maximumEntries, maximumCharacters;
    private final long beganNanos = System.nanoTime();
    private long sequence, dropped;
    private int characters;
    private volatile boolean detailed = true;
    private volatile Sink sink;

    public DiagnosticLog() { this(2000, 256 * 1024); }
    public DiagnosticLog(int maximumEntries, int maximumCharacters) {
        if (maximumEntries < 1 || maximumCharacters < 128) throw new IllegalArgumentException("Log limits must be positive.");
        this.maximumEntries = maximumEntries; this.maximumCharacters = maximumCharacters;
    }
    public void setSink(Sink sink) { this.sink = sink; }
    public void setDetailed(boolean value) { detailed = value; }
    public boolean isDetailed() { return detailed; }
    public synchronized long droppedEntries() { return dropped; }
    public synchronized int size() { return entries.size(); }
    public synchronized void clear() { entries.clear(); characters = 0; dropped = 0; }
    public synchronized List<Entry> snapshot() { return Collections.unmodifiableList(new ArrayList<>(entries)); }
    public void info(String source, String message) { add("INFO", source, message); }
    public void warn(String source, String message) { add("WARN", source, message); }
    public void error(String source, String message) { add("ERROR", source, message); }
    public void error(String source, Throwable error) {
        StringWriter stack = new StringWriter(); error.printStackTrace(new PrintWriter(stack));
        add("ERROR", source, stack.toString());
    }
    public void debug(String source, String message) { if (detailed) add("DEBUG", source, message); }
    public void wire(String source, String direction, byte[] data, int length) {
        if (!detailed) return;
        int count = Math.max(0, Math.min(length, data.length));
        add("DEBUG", source, direction + " " + count + " byte: " + hex(data, count));
    }
    public static String hex(byte[] data, int length) {
        final char[] digits = "0123456789ABCDEF".toCharArray();
        int count = Math.min(Math.max(0, length), Math.min(data.length, 512));
        StringBuilder out = new StringBuilder(count * 3);
        for (int i = 0; i < count; i++) {
            if (i != 0) out.append(' ');
            int value = data[i] & 255; out.append(digits[value >> 4]).append(digits[value & 15]);
        }
        if (length > count) out.append(" ...");
        return out.toString();
    }
    private void add(String level, String source, String message) {
        if (message == null) message = "(no detail)";
        int limit = Math.min(4096, maximumCharacters / 2);
        if (message.length() > limit) message = message.substring(0, limit) + " ... [truncated]";
        Entry entry;
        synchronized (this) {
            entry = new Entry(++sequence, System.currentTimeMillis(), (System.nanoTime() - beganNanos) / 1000000L,
                    level, source == null ? "APP" : source, message);
            entries.addLast(entry); characters += cost(entry);
            while (entries.size() > maximumEntries || characters > maximumCharacters) {
                characters -= cost(entries.removeFirst()); dropped++;
            }
        }
        Sink observer = sink;
        if (observer != null) try { observer.onEntry(entry); } catch (RuntimeException ignored) { /* tracing is observational */ }
    }
    private static int cost(Entry entry) { return entry.message.length() + entry.source.length() + 96; }
    public String text(int maximumLines) {
        List<Entry> copy = snapshot();
        StringBuilder out = new StringBuilder();
        out.append("Honda Tuner diagnostic trace\nUTC ").append(Instant.now())
                .append("\nDetail TX/RX: ").append(detailed).append("; dropped old entries: ").append(droppedEntries()).append("\n\n");
        int begin = maximumLines > 0 ? Math.max(0, copy.size() - maximumLines) : 0;
        for (int i = begin; i < copy.size(); i++) out.append(copy.get(i).line()).append('\n');
        return out.toString();
    }
    public static String elapsed(long beganNanos) {
        return String.format(Locale.ROOT, "%.1f ms", (System.nanoTime() - beganNanos) / 1000000.0);
    }
}
