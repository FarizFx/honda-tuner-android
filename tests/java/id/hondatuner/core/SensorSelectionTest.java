package id.hondatuner.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Selection behavior and actual request reduction, independent of Android widgets. */
final class SensorSelectionTest {
    private interface Checked { void run() throws Exception; }
    private static int passed;
    static int runAll() throws Exception {
        run("saved sensor order wins and new ECU fields retain default priority", SensorSelectionTest::ordering);
        run("visibility and moves survive preference roundtrip and different ECU fields", SensorSelectionTest::roundtrip);
        run("reset restores current ECU choices and preserves other ECU settings", SensorSelectionTest::reset);
        run("focused polling requests only the selected sensor group", SensorSelectionTest::focused);
        run("multiple selected groups are read once and unrequested groups stay unread", SensorSelectionTest::multiple);
        run("selection changes affect the next read without copying old values", SensorSelectionTest::changed);
        run("focused group failure is unread and never replaced with a hidden group", SensorSelectionTest::failed);
        run("display choices preserve accumulated history and CSV", SensorSelectionTest::history);
        run("fastest live applies a new selection on the next serialized round", SensorSelectionTest::liveSelection);
        return passed;
    }
    private static void run(String name, Checked test) throws Exception { test.run(); passed++; System.out.println("OK " + name); }
    private static void eq(Object expected, Object actual) { if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual); }
    private static void check(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
    private static final List<String> FIELDS = Arrays.asList("o2_sensor_voltage_1", "engine_speed", "battery_voltage", "tp_sensor_angle");
    private static SensorSelection fresh() { return new SensorSelection(Collections.emptyList(), Collections.emptySet()); }
    private static void ordering() {
        SensorSelection selected = new SensorSelection(Arrays.asList("o2_sensor_voltage_1", "unknown", "o2_sensor_voltage_1"), Collections.emptySet());
        eq(Arrays.asList("o2_sensor_voltage_1", "engine_speed", "tp_sensor_angle", "battery_voltage"), selected.order(FIELDS));
    }
    private static void roundtrip() {
        SensorSelection selected = fresh(); selected.setVisible("battery_voltage", false); selected.move("o2_sensor_voltage_1", -1, FIELDS);
        SensorSelection restored = new SensorSelection(selected.savedOrder(), selected.hidden());
        eq(selected.visible(FIELDS), restored.visible(FIELDS));
        check(!restored.isVisible("battery_voltage") && restored.isVisible("new_sensor"), "Saved visibility lost or new field hidden");
        restored.order(Arrays.asList("new_sensor")); eq(selected.visible(FIELDS), restored.visible(FIELDS));
        List<String> before = restored.order(FIELDS); restored.move("missing", -1, FIELDS); restored.move(before.get(0), -1, FIELDS); eq(before, restored.order(FIELDS));
    }
    private static void reset() {
        SensorSelection selected = new SensorSelection(Arrays.asList("o2_sensor_voltage_1", "other_ecu"), Arrays.asList("battery_voltage", "other_ecu"));
        selected.reset(FIELDS); eq(fresh().order(FIELDS), selected.order(FIELDS));
        check(!selected.isVisible("other_ecu") && selected.isVisible("battery_voltage"), "Reset discarded another ECU preference");
        eq(Collections.singletonList("other_ecu"), selected.savedOrder());
    }
    private static SensorDecoder.Result table(int request) throws IOException {
        if (request == 0x10) return SensorDecoder.decode(0x13, new byte[14]);
        if (request == 0x20) return SensorDecoder.decode(0x20, new byte[3]);
        if (request == 0xd0) return SensorDecoder.decode(0xd0, new byte[21]);
        if (request == 0xd1) return SensorDecoder.decode(0xd1, new byte[6]);
        throw new IOException("Unsupported");
    }
    private static LiveProfile profile() throws IOException { return LiveProfile.detect((request, cancel) -> table(request), CancelSignal.NEVER); }
    private static void focused() throws Exception {
        LiveProfile profile = profile(); List<Integer> requests = new ArrayList<>();
        LiveProfile.Sample sample = profile.read((request, cancel) -> { requests.add(request); return table(request); }, CancelSignal.NEVER, Collections.singletonList("engine_speed"));
        eq(Collections.singletonList(0x10), requests); eq(1, sample.validGroups); eq(0, sample.values.get("engine_speed"));
        check(sample.values.get("battery_voltage") != null && sample.values.get("o2_sensor_voltage_1") == null, "Shared group missing or hidden O2 fabricated");
        eq(profile.fields, new ArrayList<>(sample.values.keySet()));
    }
    private static void multiple() throws Exception {
        List<Integer> requests = new ArrayList<>();
        LiveProfile.Sample sample = profile().read((request, cancel) -> { requests.add(request); return table(request); }, CancelSignal.NEVER,
                Arrays.asList("engine_speed", "battery_voltage", "o2_sensor_voltage_1"));
        eq(Arrays.asList(0x10, 0x20), requests); eq(2, sample.validGroups);
    }
    private static void changed() throws Exception {
        LiveProfile profile = profile(); profile.read((request, cancel) -> table(request), CancelSignal.NEVER);
        List<Integer> requests = new ArrayList<>();
        LiveProfile.Sample sample = profile.read((request, cancel) -> { requests.add(request); return table(request); }, CancelSignal.NEVER, Collections.singletonList("o2_sensor_voltage_1"));
        eq(Collections.singletonList(0x20), requests); check(sample.values.get("engine_speed") == null, "Old RPM copied to unpolled group");
    }
    private static void failed() throws Exception {
        List<Integer> requests = new ArrayList<>();
        LiveProfile.Sample sample = profile().read((request, cancel) -> { requests.add(request); throw new IOException("Missing"); }, CancelSignal.NEVER, Collections.singletonList("engine_speed"));
        eq(Collections.singletonList(0x10), requests); eq(0, sample.validGroups); check(sample.failures.containsKey(0x10), "Selected failure missing");
        for (Object value : sample.values.values()) check(value == null, "Stale/hidden sensor populated on failure");
    }
    private static void history() throws Exception {
        LiveProfile profile = profile(); TelemetryHistory history = new TelemetryHistory(600); history.reset(profile.fields); history.add(profile.initial);
        String csv = history.csv(); SensorSelection selected = fresh(); selected.setVisible("engine_speed", false); selected.move("battery_voltage", -1, profile.fields);
        selected.visible(profile.fields); eq(csv, history.csv()); eq(1, history.size()); eq(1L, history.statistics("engine_speed").readings);
    }
    private static void liveSelection() throws Exception {
        ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(); CountDownLatch enough = new CountDownLatch(3);
        List<Integer> requests = Collections.synchronizedList(new ArrayList<>()); List<LiveProfile.Sample> samples = Collections.synchronizedList(new ArrayList<>());
        LiveAcquisition[] acquisition = new LiveAcquisition[1];
        acquisition[0] = new LiveAcquisition(worker, (request, cancel) -> { requests.add(request); return table(request); }, new LiveAcquisition.Listener() {
            @Override public void onSample(long generation, LiveProfile.Sample sample) {
                samples.add(sample); acquisition[0].setFields(Collections.singletonList("o2_sensor_voltage_1")); enough.countDown();
            }
            @Override public void onStopped(long generation, String reason) { throw new AssertionError(reason); }
        }, 0);
        try {
            acquisition[0].setFields(Collections.singletonList("engine_speed")); acquisition[0].start(profile());
            check(enough.await(2, TimeUnit.SECONDS), "Fastest selected live did not repeat"); acquisition[0].stop();
            worker.submit(() -> {}).get(2, TimeUnit.SECONDS);
            eq(Arrays.asList(0x10, 0x20, 0x20), requests.subList(0, 3));
            check(samples.get(0).values.get("engine_speed") != null && samples.get(1).values.get("engine_speed") == null, "Selection retained stale main-group values");
        } finally { acquisition[0].stop(); worker.shutdownNow(); worker.awaitTermination(2, TimeUnit.SECONDS); }
    }
}
