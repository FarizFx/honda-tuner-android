package id.hondatuner.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Session records, measured cadence, chart domains and the shared LCD renderer. */
final class DashboardTest {
    private interface Checked { void run() throws Exception; }
    private static int passed;
    static int runAll() throws Exception {
        run("zero RPM/TPS/battery charts have finite ranges and visible zero ticks", DashboardTest::zeroAxes);
        run("positive idle values do not create negative physical-sensor axes", DashboardTest::idleAxes);
        run("signed temperature and ignition values retain negatives and zero", DashboardTest::signedAxes);
        run("empty charts retain zero axes without inventing sensor readings", DashboardTest::emptyAxes);
        run("fractional chart ticks remain distinct and never format negative zero", DashboardTest::tickLabels);
        run("records include every reading beyond the 600-sample chart limit", DashboardTest::wholeSession);
        run("session means include real zeros and ignore unread/nonfinite/boolean values", DashboardTest::validRecords);
        run("disconnect clears records while retaining exportable captured samples", DashboardTest::disconnect);
        run("same-session re-detection preserves records and extends CSV columns", DashboardTest::redetect);
        run("actual refresh uses monotonic completion times and independent read duration", DashboardTest::cadence);
        run("pause/manual reads and new live generations cannot dilute refresh measurements", DashboardTest::pause);
        run("refresh adapts to the last eight periods rather than old full-group polling", DashboardTest::recentCadence);
        run("duplicate or backward sample times cannot invent zero/negative refresh rates", DashboardTest::monotonic);
        run("dashboard defaults use VSS only when the ECU schema supplies it", DashboardTest::speedAvailability);
        run("HUD polling includes selected and RPM-bar fields once and omits absent signals", DashboardTest::hudSelection);
        run("focused HUD reads one real group and leaves unpolled O2 data unread", DashboardTest::focusedHud);
        run("unread VSS differs from a measured zero and retains its unverified raw unit", DashboardTest::speedReadings);
        run("LCD drawing fits portrait/landscape and compact/large digits without clipping", DashboardTest::drawingBounds);
        return passed;
    }
    private static void run(String name, Checked test) throws Exception { test.run(); passed++; System.out.println("OK " + name); }
    private static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    private static void near(double expected, double actual) { check(Math.abs(expected - actual) <= 1e-9 * Math.max(1, Math.abs(expected)), expected + " != " + actual); }
    private static void eq(Object expected, Object actual) { check(expected.equals(actual), expected + " != " + actual); }
    private static final List<String> FIELDS = Arrays.asList("engine_speed", "tp_sensor_angle", "battery_voltage", "vss");
    private static LiveProfile.Sample sample(Object rpm, Object tps, Object battery, long millis, double duration) {
        Map<String, Object> values = new LinkedHashMap<>(); values.put("engine_speed", rpm); values.put("tp_sensor_angle", tps); values.put("battery_voltage", battery);
        return new LiveProfile.Sample(values, Collections.emptyMap(), 1, millis * 1000000L, millis)
                .withReadingStarted(millis * 1000000L - (long) (duration * 1000000));
    }
    private static void zeroAxes() {
        for (String field : Arrays.asList("engine_speed", "tp_sensor_angle", "battery_voltage", "vss")) {
            SensorScale scale = SensorScale.forRange(field, 0, 0);
            near(0, scale.minimum); check(scale.maximum > 0 && Double.isFinite(scale.maximum), "Invalid zero range");
            check(scale.ticks().contains(0.0) && scale.hasReadings, "Measured zero is absent");
        }
    }
    private static void idleAxes() {
        String[] fields = {"engine_speed", "tp_sensor_angle", "battery_voltage", "vss"};
        double[] values = {1800, 0.01, 12.85, 42};
        for (int i = 0; i < fields.length; i++) {
            SensorScale scale = SensorScale.forRange(fields[i], values[i], values[i]);
            near(0, scale.minimum); check(scale.maximum >= values[i] && scale.ticks().contains(0.0), "Idle or zero outside axes");
        }
    }
    private static void signedAxes() {
        for (String field : Arrays.asList("ect_sensor_temperature", "spark_advance")) {
            SensorScale scale = SensorScale.forRange(field, -12.5, 7.5);
            check(scale.minimum <= -12.5 && scale.maximum >= 7.5 && scale.ticks().contains(0.0), "Signed reading clipped");
        }
    }
    private static void emptyAxes() {
        SensorScale scale = SensorScale.forPoints("engine_speed", Collections.emptyList());
        check(!scale.hasReadings && scale.ticks().contains(0.0) && scale.maximum > scale.minimum, "Empty graph fabricates data or hides zero");
    }
    private static void tickLabels() {
        SensorScale scale = SensorScale.forRange("battery_voltage", 0, .75);
        List<String> labels = new ArrayList<>();
        for (double tick : scale.ticks()) {
            String label = scale.label(tick); check(!labels.contains(label), "Different ticks have the same label"); labels.add(label);
            check(!label.startsWith("-0"), "Negative zero in nonnegative chart");
        }
    }
    private static void wholeSession() {
        TelemetryHistory history = new TelemetryHistory(600); history.reset(FIELDS);
        for (int i = 0; i < 2000; i++) history.add(sample(i, i / 10.0, 12.5, i, 1));
        eq(600, history.size()); eq(2000L, history.statistics("engine_speed").readings);
        near(0, history.statistics("engine_speed").minimum); near(1999, history.statistics("engine_speed").maximum);
        near(999.5, history.statistics("engine_speed").average); near(99.95, history.statistics("tp_sensor_angle").average);
    }
    private static void validRecords() {
        TelemetryHistory history = new TelemetryHistory(2); history.reset(FIELDS);
        history.add(sample(0, 0.0, Double.NaN, 0, 1)); history.add(sample(null, true, 12.0, 1, 1));
        history.add(sample(2000, Double.NEGATIVE_INFINITY, 14.0, 2, 1));
        eq(2L, history.statistics("engine_speed").readings); near(1000, history.statistics("engine_speed").average);
        eq(1L, history.statistics("tp_sensor_angle").readings); near(0, history.statistics("tp_sensor_angle").average);
        near(13, history.statistics("battery_voltage").average);
        List<TelemetryHistory.Point> points = history.points("tp_sensor_angle", 120);
        check(points.get(0).value == null && points.get(1).value == null, "Unread/nonfinite graph values filled");
    }
    private static void disconnect() {
        TelemetryHistory history = new TelemetryHistory(600); history.reset(FIELDS); history.add(sample(9000, 84.5, 14.0, 0, 1));
        String csv = history.csv(); history.clearStatistics();
        check(history.statistics("engine_speed").average == null && history.statistics("tp_sensor_angle").maximum == null, "Disconnected record survived");
        eq(csv, history.csv()); history.add(sample(1200, 0.0, 12.0, 100, 1)); near(1200, history.statistics("engine_speed").maximum);
    }
    private static void redetect() {
        TelemetryHistory history = new TelemetryHistory(600); history.reset(Collections.singletonList("engine_speed"));
        history.add(sample(1000, null, null, 0, 1)); history.includeFields(FIELDS); history.add(sample(2000, 10.0, 12.0, 100, 1));
        eq(2L, history.statistics("engine_speed").readings); near(1500, history.statistics("engine_speed").average);
        eq(1L, history.statistics("tp_sensor_angle").readings); eq(FIELDS, history.fields());
        check(history.csv().contains("engine_speed,tp_sensor_angle,battery_voltage,vss"), "New CSV fields lost");
    }
    private static void cadence() {
        RefreshTiming timing = new RefreshTiming(); timing.record(1, sample(0, 0, 12, 0, 25.5), true);
        check(timing.actualMillis() == null, "First reading invented a cadence"); near(25.5, timing.readingMillis());
        timing.record(1, sample(0, 0, 12, 100, 30), true); timing.record(1, sample(0, 0, 12, 300, 40), true);
        near(150, timing.actualMillis()); near(200, timing.latestMillis()); near(40, timing.readingMillis()); near(1000.0 / 150, timing.updatesPerSecond());
    }
    private static void pause() {
        RefreshTiming timing = new RefreshTiming(); timing.record(1, sample(0, 0, 12, 0, 20), true);
        timing.record(1, sample(0, 0, 12, 100, 20), true); timing.record(2, sample(0, 0, 12, 10000, 20), true);
        check(timing.actualMillis() == null, "Pause interval included"); timing.record(2, sample(0, 0, 12, 10100, 20), true); near(100, timing.actualMillis());
        timing.record(3, sample(0, 0, 12, 20000, 35), false); check(timing.actualMillis() == null, "Manual reads treated as live cadence"); near(35, timing.readingMillis());
        timing.record(3, sample(0, 0, 12, 30000, 25), true); check(timing.actualMillis() == null, "Manual delay polluted resumed cadence");
    }
    private static void recentCadence() {
        RefreshTiming timing = new RefreshTiming(); long millis = 0; timing.record(1, sample(0, 0, 12, millis, 1), true);
        for (int i = 0; i < 20; i++) { millis += i < 12 ? 250 : 50; timing.record(1, sample(0, 0, 12, millis, 1), true); }
        near(50, timing.actualMillis()); near(20, timing.updatesPerSecond()); timing.reset(); check(timing.readingMillis() == null, "Reset retained reading latency");
    }
    private static void monotonic() {
        RefreshTiming timing = new RefreshTiming(); timing.record(1, sample(0, 0, 12, 100, 20), true);
        timing.record(1, sample(0, 0, 12, 200, 20), true); timing.record(1, sample(0, 0, 12, 200, 20), true);
        timing.record(1, sample(0, 0, 12, 150, 20), true); timing.record(1, sample(0, 0, 12, 300, 20), true); near(100, timing.actualMillis());
    }
    private static void speedAvailability() {
        eq("vss", HudLayout.defaults(FIELDS).primary);
        eq("engine_speed", HudLayout.defaults(Arrays.asList("engine_speed", "tp_sensor_angle")).primary);
        check(!HudLayout.defaults(Collections.singletonList("engine_speed")).pollingFields(Collections.singletonList("engine_speed")).contains("vss"), "Absent VSS queried");
    }
    private static void hudSelection() {
        HudLayout layout = new HudLayout("tp_sensor_angle", Arrays.asList("battery_voltage", "battery_voltage", "missing"), 0, 1,
                false, true, true, true, 15000);
        eq(Arrays.asList("tp_sensor_angle", "battery_voltage", "engine_speed"), layout.pollingFields(FIELDS));
        HudLayout restored = new HudLayout(layout.primary, layout.auxiliary, layout.theme, layout.digitScale,
                layout.mirrored, layout.landscape, layout.focused, layout.rpmBar, layout.rpmLimit);
        eq(layout.pollingFields(FIELDS), restored.pollingFields(FIELDS));
    }
    private static void focusedHud() throws Exception {
        List<Integer> requested = new ArrayList<>();
        LiveProfile.Reader reader = (request, cancel) -> {
            requested.add(request);
            if (request == 0x10) return SensorDecoder.decode(0x10, new byte[17]);
            if (request == 0x20) return SensorDecoder.decode(0x20, new byte[3]);
            throw new IOException("Not supported");
        };
        LiveProfile profile = LiveProfile.detect(reader, CancelSignal.NEVER); requested.clear();
        LiveProfile.Sample reading = profile.read(reader, CancelSignal.NEVER, HudLayout.defaults(profile.fields).pollingFields(profile.fields));
        eq(Collections.singletonList(0x10), requested); eq(1, reading.validGroups);
        check(reading.values.get("vss") != null && reading.values.get("o2_sensor_voltage_1") == null, "Focused HUD fabricated speed/O2");
    }
    private static void speedReadings() {
        TelemetryHistory history = new TelemetryHistory(600); history.reset(FIELDS);
        LcdDashboard.Reading zero = new LcdDashboard.Reading("vss", 0, history.statistics("vss"));
        LcdDashboard.Reading unread = new LcdDashboard.Reading("vss", null, history.statistics("vss"));
        eq("0", zero.value); eq("—", unread.value); eq("RAW", zero.unit); check(unread.number == null, "Missing speed became zero");
    }
    private static void drawingBounds() {
        int[][] sizes = {{320, 640}, {640, 320}, {1080, 2400}, {2400, 1080}};
        TelemetryHistory history = new TelemetryHistory(600); history.reset(FIELDS); history.add(sample(65535, 84.5, 14.4, 0, 1));
        for (int[] size : sizes) for (float digitScale : new float[]{.85f, 1.15f}) for (boolean empty : new boolean[]{false, true}) {
            HudLayout layout = new HudLayout("engine_speed", empty ? Collections.emptyList() : Arrays.asList("tp_sensor_angle", "battery_voltage", "vss"),
                    1, digitScale, false, true, true, true, 15000);
            List<LcdDashboard.Reading> auxiliary = new ArrayList<>();
            if (!empty) for (String field : layout.auxiliary) auxiliary.add(new LcdDashboard.Reading(field, 65535, history.statistics(field)));
            LcdDashboard.Data data = new LcdDashboard.Data(new LcdDashboard.Reading("engine_speed", 65535, history.statistics("engine_speed")),
                    auxiliary, 65535.0, 51.25, 48.5, true, true, 0, "VSS BELUM TERSEDIA");
            LcdDashboard.render(new Bounds(size[0], size[1]), size[0], size[1], layout, data);
        }
    }
    private static final class Bounds implements LcdDashboard.Painter {
        final int width, height;
        Bounds(int width, int height) { this.width = width; this.height = height; }
        private void point(float x, float y) { check(Float.isFinite(x) && Float.isFinite(y) && x >= -.1 && y >= -.1 && x <= width + .1 && y <= height + .1, "LCD drawing outside " + width + "x" + height + ": " + x + "," + y); }
        @Override public void box(float l, float t, float r, float b, float radius, int color) { point(l, t); point(r, b); check(r >= l && b >= t, "Inverted LCD box"); }
        @Override public float measure(String text, float size) { return text.length() * size * .61f; }
        @Override public void text(String text, float x, float y, float size, int color, int alignment) {
            float span = measure(text, size), left = alignment == 1 ? x - span / 2 : alignment == 2 ? x - span : x;
            point(left, y - size); point(left + span, y);
        }
    }
}
