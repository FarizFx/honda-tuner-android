package id.hondatuner.core;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Discover ECU-returned schemas once, then poll only the successful requests. */
public final class LiveProfile {
    public interface Reader { SensorDecoder.Result readTable(int request, CancelSignal cancel) throws IOException; }
    private static final int[][] GROUPS = {{0x10, 0x11, 0x13, 0x15, 0x16, 0x17}, {0x20, 0x21}, {0xd0}, {0xd1}};
    public static final class Table {
        public final int request, response, bodyLength;
        final List<String> fields;
        Table(int request, SensorDecoder.Result result) {
            this.request = request; response = result.tableId; bodyLength = result.bodyLength;
            fields = Collections.unmodifiableList(new ArrayList<>(result.fields.keySet()));
        }
    }
    public static final class Sample {
        public final Map<String, Object> values;
        public final Map<Integer, String> failures;
        public final int validGroups;
        public final long completedNanos;
        public final long completedMillis;
        Sample(Map<String, Object> values, Map<Integer, String> failures, int validGroups) {
            this(values, failures, validGroups, System.nanoTime(), System.currentTimeMillis());
        }
        Sample(Map<String, Object> values, Map<Integer, String> failures, int validGroups, long completedNanos, long completedMillis) {
            this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
            this.failures = Collections.unmodifiableMap(new LinkedHashMap<>(failures));
            this.validGroups = validGroups;
            this.completedNanos = completedNanos;
            this.completedMillis = completedMillis;
        }
    }

    public final List<Table> tables;
    public final List<String> fields;
    public final Sample initial;
    private LiveProfile(List<Table> tables, Map<String, Object> values, Map<Integer, String> failures) {
        this.tables = Collections.unmodifiableList(new ArrayList<>(tables));
        fields = Collections.unmodifiableList(new ArrayList<>(values.keySet()));
        initial = new Sample(values, failures, tables.size());
    }

    public static LiveProfile detect(Reader reader, CancelSignal cancel) throws IOException {
        return detect(reader, cancel, null, message -> {});
    }
    public static LiveProfile detect(Reader reader, CancelSignal cancel, DiagnosticLog log, HondaClient.Progress progress) throws IOException {
        List<Table> tables = new ArrayList<>();
        Map<String, Object> values = new LinkedHashMap<>();
        Map<Integer, String> failures = new LinkedHashMap<>();
        for (int[] group : GROUPS) {
            Map<Integer, String> groupFailures = new LinkedHashMap<>();
            boolean found = false;
            for (int request : group) {
                cancel.check();
                progress.onStage("ECU merespons · deteksi sensor 0x" + Integer.toHexString(request) + " · " + values.size() + " sensor ditemukan");
                try {
                    SensorDecoder.Result result = reader.readTable(request, cancel);
                    boolean correctGroup = false;
                    for (int response : group) if (result.tableId == response) correctGroup = true;
                    if (!correctGroup || result.named().isEmpty()) throw new ProtocolException("Grup sensor yang dibalas ECU berbeda.");
                    tables.add(new Table(request, result));
                    mergeFirst(values, result.named());
                    found = true;
                    if (log != null) log.info("DETECT", "Request 0x" + Integer.toHexString(request) + " berhasil: tabel=0x" + Integer.toHexString(result.tableId) + ", bytes=" + result.bodyLength + ", sensors=" + result.named().size());
                    break;
                } catch (InterruptedIOException error) { throw error; }
                catch (IOException error) {
                    groupFailures.put(request, error.getMessage());
                    if (log != null) log.warn("DETECT", "Request 0x" + Integer.toHexString(request) + ": " + error.getMessage());
                }
            }
            // A failed candidate is not a missing sensor after a fallback succeeds.
            if (!found) failures.putAll(groupFailures);
        }
        if (tables.isEmpty()) {
            String last = failures.isEmpty() ? "" : failures.values().iterator().next();
            throw new ProtocolException("ECU merespons sesi diagnostik, tetapi tabel sensor belum cocok. " + last + " Buka Debug untuk detail TX/RX.");
        }
        return new LiveProfile(tables, values, failures);
    }

    public Sample read(Reader reader, CancelSignal cancel) throws IOException {
        Map<String, Object> values = new LinkedHashMap<>();
        Map<Integer, String> failures = new LinkedHashMap<>();
        int valid = 0;
        for (Table table : tables) {
            cancel.check();
            try {
                SensorDecoder.Result result = reader.readTable(table.request, cancel);
                if (result.tableId != table.response || result.bodyLength != table.bodyLength || !table.fields.equals(new ArrayList<>(result.fields.keySet()))) {
                    throw new ProtocolException("Layout sensor ECU berubah. Deteksi ulang sensor.");
                }
                mergeFirst(values, result.named());
                valid++;
            } catch (InterruptedIOException error) { throw error; }
            catch (IOException error) { failures.put(table.request, error.getMessage()); }
        }
        // Failed groups are unread, never copied from an older sample.
        Map<String, Object> ordered = new LinkedHashMap<>();
        for (String field : fields) ordered.put(field, values.get(field));
        return new Sample(ordered, failures, valid);
    }

    public Sample unread(String reason) {
        Map<String, Object> values = new LinkedHashMap<>();
        Map<Integer, String> failures = new LinkedHashMap<>();
        for (String field : fields) values.put(field, null);
        for (Table table : tables) failures.put(table.request, reason);
        return new Sample(values, failures, 0);
    }

    private static void mergeFirst(Map<String, Object> target, Map<String, Object> source) {
        for (Map.Entry<String, Object> value : source.entrySet()) if (!target.containsKey(value.getKey())) target.put(value.getKey(), value.getValue());
    }
}
