package id.hondatuner.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Shared LCD drawing code, rendered by Android Canvas and the offline visual preview. */
public final class LcdDashboard {
    public interface Painter {
        void box(float left, float top, float right, float bottom, float radius, int color);
        void text(String text, float x, float baseline, float size, int color, int alignment);
        float measure(String text, float size);
    }
    public static final class Reading {
        public final String field, value, unit;
        public final Double number;
        public final TelemetryHistory.Statistics statistics;
        public Reading(String field, Object value, TelemetryHistory.Statistics statistics) {
            this.field = field; number = TelemetryHistory.numeric(value);
            this.value = number == null ? "—" : SensorInfo.format(value);
            String known = SensorInfo.unit(field); unit = known.isEmpty() ? "RAW" : known;
            this.statistics = statistics;
        }
    }
    public static final class Data {
        public final Reading primary;
        public final List<Reading> auxiliary;
        public final Double rpm, actualMillis, readingMillis;
        public final boolean connected, live;
        public final long ageMillis;
        public final String speedStatus;
        public Data(Reading primary, List<Reading> auxiliary, Double rpm, Double actualMillis,
                    Double readingMillis, boolean connected, boolean live, long ageMillis, String speedStatus) {
            this.primary = primary; this.auxiliary = Collections.unmodifiableList(new ArrayList<>(auxiliary));
            this.rpm = rpm; this.actualMillis = actualMillis; this.readingMillis = readingMillis;
            this.connected = connected; this.live = live; this.ageMillis = ageMillis; this.speedStatus = speedStatus;
        }
    }
    private static final int BACKGROUND = 0xff050e10, PANEL = 0xff0c2020;
    private static final int[] DIGITS = {63, 6, 91, 79, 102, 109, 125, 7, 127, 111};
    private LcdDashboard() {}

    public static void render(Painter output, int width, int height, HudLayout layout, Data data) {
        render(output, width, height, layout, data, width >= height);
    }
    public static void render(Painter output, int width, int height, HudLayout layout, Data data, boolean wide) {
        if (width < 1 || height < 1) return;
        output.box(0, 0, width, height, 0, BACKGROUND);
        int accent = HudLayout.COLORS[layout.theme], muted = alpha(accent, 160);
        List<String> visible = blocks(layout);
        for (String id : layout.geometry.order(visible, wide)) {
            HudGeometry.Box box = layout.geometry.box(id, wide, visible);
            float pixelWidth = box.width * width, pixelHeight = box.height * height;
            boolean primary = HudGeometry.PRIMARY.equals(id), auxiliary = id.startsWith("aux");
            float designHeight = primary ? 250 : auxiliary ? 130 : HudGeometry.RPM.equals(id) ? 46 : 32;
            float minimumWidth = primary ? 250 : auxiliary ? 190 : HudGeometry.RPM.equals(id) ? 260 : 1;
            float designWidth = Math.max(minimumWidth, pixelWidth / pixelHeight * designHeight);
            float scale = Math.min(pixelWidth / designWidth, pixelHeight / designHeight);
            Painter p = new Scaled(output, box.x * width + (pixelWidth - designWidth * scale) / 2,
                    box.y * height + (pixelHeight - designHeight * scale) / 2, scale);
            if (primary || auxiliary) frame(output, box.x * width, box.y * height, box.right() * width, box.bottom() * height, accent);
            if (primary) {
                String label = SensorInfo.compactLabel(data.primary.field);
                if (data.connected && data.ageMillis >= 1500) label += String.format(Locale.getDefault(), " / USIA %.1f s", data.ageMillis / 1000.0);
                fitted(p, label, 14, 26, designWidth - 28, 16, muted, 0);
                digits(p, data.connected ? data.primary.value : "—", 14, 40, designWidth - 94, 140, layout.digitScale, accent);
                fitted(p, data.primary.unit, designWidth - 14, 175, 70, 18, muted, 2);
                stats(p, data.primary.statistics, 14, 205, designWidth - 28, accent, 20);
            } else if (auxiliary) {
                int slot = Integer.parseInt(id.substring(3)), reading = 0;
                for (int i = 0; i < slot; i++) if (!layout.auxiliary.get(i).isEmpty()) reading++;
                if (reading < data.auxiliary.size()) tile(p, data.auxiliary.get(reading), 0, 0, designWidth, designHeight, data.connected, accent);
            } else if (HudGeometry.RPM.equals(id)) {
                rpmBar(p, 0, 8, designWidth, data, layout, accent);
            } else if (HudGeometry.STATUS.equals(id)) {
                p.box(0, 0, designWidth, 32, 5, alpha(accent, 24));
                fitted(p, !data.connected ? "TERPUTUS" : data.live ? "LIVE" : "JEDA", designWidth / 2, 24, designWidth - 8, 17, accent, 1);
            } else if (HudGeometry.BRAND.equals(id)) {
                fitted(p, "HONDA / TUNER", 0, 25, designWidth, 19, accent, 0);
            } else if (HudGeometry.REFRESH.equals(id)) {
                String timing;
                if (!data.connected) timing = "REFRESH — ms";
                else if (!data.live) timing = "JEDA / SAMPEL TERAKHIR";
                else if (data.actualMillis != null) timing = String.format(Locale.getDefault(), "REFRESH %.1f ms", data.actualMillis);
                else if (data.readingMillis != null) timing = String.format(Locale.getDefault(), "PUTARAN %.1f ms", data.readingMillis);
                else timing = "MENUNGGU SAMPEL";
                fitted(p, timing, 0, 24, designWidth, 15, muted, 0);
            } else {
                fitted(p, data.primary.field.equals("vss") ? "VSS / RAW" : data.speedStatus, designWidth, 24, designWidth, 13, muted, 2);
            }
        }
    }
    public static List<String> blocks(HudLayout layout) {
        List<String> ids = new ArrayList<>(); ids.add(HudGeometry.BRAND); ids.add(HudGeometry.STATUS);
        if (layout.rpmBar) ids.add(HudGeometry.RPM);
        ids.add(HudGeometry.PRIMARY);
        for (int i = 0; i < layout.auxiliary.size(); i++) if (!layout.auxiliary.get(i).isEmpty()) ids.add(HudGeometry.auxiliary(i));
        ids.add(HudGeometry.REFRESH); ids.add(HudGeometry.SPEED); return Collections.unmodifiableList(ids);
    }
    public static String blockLabel(String id, HudLayout layout) {
        if (HudGeometry.PRIMARY.equals(id)) return "Angka utama · " + SensorInfo.compactLabel(layout.primary);
        if (id.startsWith("aux")) return "Sensor " + (Integer.parseInt(id.substring(3)) + 1) + " · " + SensorInfo.compactLabel(layout.auxiliary.get(Integer.parseInt(id.substring(3))));
        if (HudGeometry.RPM.equals(id)) return "Bar RPM";
        if (HudGeometry.BRAND.equals(id)) return "Judul aplikasi";
        if (HudGeometry.STATUS.equals(id)) return "Status koneksi";
        return HudGeometry.REFRESH.equals(id) ? "Refresh aktual" : "Info kecepatan / VSS";
    }
    private static void frame(Painter p, float left, float top, float right, float bottom, int color) {
        p.box(left, top, right, bottom, 12, alpha(color, 65));
        p.box(left + 1.5f, top + 1.5f, right - 1.5f, bottom - 1.5f, 11, PANEL);
    }
    private static void rpmBar(Painter p, float x, float y, float width, Data data, HudLayout layout, int color) {
        p.text("RPM", x, y + 12, 13, alpha(color, 160), 0);
        float start = x + 45, end = x + width - 57, gap = 4, cell = (end - start) / 32;
        double rpm = data.connected && data.rpm != null ? Math.max(0, data.rpm) : 0;
        int active = (int) Math.ceil(Math.min(1, rpm / layout.rpmLimit) * 32);
        for (int i = 0; i < 32; i++) p.box(start + i * cell, y, start + (i + 1) * cell - gap, y + 15,
                1.5f, alpha(color, i < active ? 255 : 35));
        p.text(String.format(Locale.ROOT, "%.0fk", layout.rpmLimit / 1000.0), x + width, y + 12, 13, alpha(color, 160), 2);
        p.text("0", start, y + 30, 11, alpha(color, 130), 0);
        p.text(Integer.toString(layout.rpmLimit), end, y + 30, 11, alpha(color, 130), 2);
    }
    private static void tile(Painter p, Reading reading, float x, float y, float width, float height,
                             boolean connected, int color) {
        fitted(p, SensorInfo.compactLabel(reading.field), x + 13, y + 22, width - 26, 16, alpha(color, 155), 0);
        String value = connected ? reading.value : "—";
        fitted(p, value, x + 13, y + 62, width - 75, 34, color, 0);
        fitted(p, reading.unit, x + width - 13, y + 60, 56, 13, alpha(color, 160), 2);
        stats(p, reading.statistics, x + 13, y + height - 38, width - 26, color, 18);
    }
    private static void stats(Painter p, TelemetryHistory.Statistics stats, float x, float y, float width, int color, float size) {
        String[] labels = {"Min", "Avg", "Max"};
        Double[] values = {stats.minimum, stats.average, stats.maximum};
        float cell = width / 3;
        for (int i = 0; i < 3; i++) {
            float center = x + cell * (i + .5f);
            p.text(labels[i], center, y + 10, size * .76f, alpha(color, 155), 1);
            fitted(p, SensorInfo.format(values[i]), center, y + 29, cell - 8, size, color, 1);
        }
    }
    private static void digits(Painter p, String value, float x, float y, float width, float height, float multiplier, int color) {
        String text = value.replace('—', '-');
        float slots = 0; for (int i = 0; i < text.length(); i++) slots += text.charAt(i) == '.' || text.charAt(i) == ',' ? .30f : 1.16f;
        float h = Math.min(height, Math.min(height * .86f * multiplier, width / Math.max(1, slots) / .54f)), w = h * .54f;
        float cursor = x + (width - slots * w) / 2, top = y + (height - h) / 2;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '.' || c == ',') { p.box(cursor, top + h - h * .08f, cursor + h * .08f, top + h, h * .03f, color); cursor += w * .30f; continue; }
            int bits = c >= '0' && c <= '9' ? DIGITS[c - '0'] : 64;
            digit(p, cursor, top, w, h, bits, color); cursor += w * 1.16f;
        }
    }
    private static void digit(Painter p, float x, float y, float w, float h, int bits, int color) {
        float t = h * .075f, half = h / 2, inset = t * .5f;
        float[][] segments = {
            {x + t, y, x + w - t, y + t}, {x + w - t, y + t, x + w, y + half - inset},
            {x + w - t, y + half + inset, x + w, y + h - t}, {x + t, y + h - t, x + w - t, y + h},
            {x, y + half + inset, x + t, y + h - t}, {x, y + t, x + t, y + half - inset},
            {x + t, y + half - inset, x + w - t, y + half + inset}
        };
        for (int i = 0; i < segments.length; i++) {
            float[] r = segments[i]; p.box(r[0], r[1], r[2], r[3], t * .24f, alpha(color, (bits & (1 << i)) != 0 ? 255 : 13));
        }
    }
    private static void fitted(Painter p, String text, float x, float baseline, float width, float size, int color, int alignment) {
        float measured = p.measure(text, size);
        if (measured > width && measured > 0) size *= width / measured;
        p.text(text, x, baseline, size, color, alignment);
    }
    private static int alpha(int color, int alpha) { return (color & 0xffffff) | (alpha << 24); }
    private static final class Scaled implements Painter {
        private final Painter p; private final float x, y, scale;
        Scaled(Painter p, float x, float y, float scale) { this.p = p; this.x = x; this.y = y; this.scale = scale; }
        @Override public void box(float l, float t, float r, float b, float radius, int color) {
            p.box(x + l * scale, y + t * scale, x + r * scale, y + b * scale, radius * scale, color);
        }
        @Override public void text(String text, float left, float baseline, float size, int color, int alignment) {
            p.text(text, x + left * scale, y + baseline * scale, size * scale, color, alignment);
        }
        @Override public float measure(String text, float size) { return p.measure(text, size * scale) / scale; }
    }
}
