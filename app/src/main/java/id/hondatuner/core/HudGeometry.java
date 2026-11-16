package id.hondatuner.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable placement in fractions of the HUD canvas, separately for each orientation. */
public final class HudGeometry {
    public static final String PRIMARY = "primary", RPM = "rpm", BRAND = "brand", STATUS = "status",
            REFRESH = "refresh", SPEED = "speed";
    public static final int LEFT = 1, TOP = 2, RIGHT = 4, BOTTOM = 8;
    private static final List<String> IDS = Collections.unmodifiableList(Arrays.asList(
            BRAND, STATUS, RPM, PRIMARY, "aux0", "aux1", "aux2", REFRESH, SPEED));
    private final Map<String, Box> portrait, landscape;

    public static final class Box {
        public final float x, y, width, height;
        public Box(float x, float y, float width, float height) {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || !Float.isFinite(height))
                throw new IllegalArgumentException("HUD box must have finite coordinates");
            this.width = clamp(width, .035f, 1); this.height = clamp(height, .035f, 1);
            this.x = clamp(x, 0, 1 - this.width); this.y = clamp(y, 0, 1 - this.height);
        }
        public float right() { return x + width; }
        public float bottom() { return y + height; }
        public boolean contains(float px, float py) { return px >= x && px <= right() && py >= y && py <= bottom(); }
    }

    public HudGeometry() { this(Collections.emptyMap(), Collections.emptyMap()); }
    private HudGeometry(Map<String, Box> portrait, Map<String, Box> landscape) {
        this.portrait = Collections.unmodifiableMap(new LinkedHashMap<>(portrait));
        this.landscape = Collections.unmodifiableMap(new LinkedHashMap<>(landscape));
    }
    private Map<String, Box> positions(boolean wide) { return wide ? landscape : portrait; }
    public static String auxiliary(int index) { return "aux" + index; }
    public boolean hasPlacement(String id, boolean wide) { return positions(wide).containsKey(id); }
    public Box box(String id, boolean wide, List<String> visible) {
        requireId(id);
        Box custom = positions(wide).get(id);
        return custom == null ? defaultBox(id, wide, visible) : custom;
    }
    public List<String> order(List<String> visible, boolean wide) {
        List<String> result = new ArrayList<>();
        for (String id : visible) if (IDS.contains(id) && !result.contains(id)) result.add(id);
        for (String id : positions(wide).keySet()) if (result.remove(id)) result.add(id);
        return Collections.unmodifiableList(result);
    }
    public String hit(float x, float y, boolean wide, List<String> visible) {
        List<String> order = order(visible, wide);
        for (int i = order.size() - 1; i >= 0; i--) if (box(order.get(i), wide, visible).contains(x, y)) return order.get(i);
        return null;
    }
    public HudGeometry with(String id, boolean wide, Box box) {
        requireId(id);
        Map<String, Box> updated = new LinkedHashMap<>(positions(wide));
        updated.remove(id); updated.put(id, constrain(id, box));
        return wide ? new HudGeometry(portrait, updated) : new HudGeometry(updated, landscape);
    }
    public HudGeometry reset(boolean wide) {
        return wide ? new HudGeometry(portrait, Collections.emptyMap()) : new HudGeometry(Collections.emptyMap(), landscape);
    }
    public HudGeometry reset(String id, boolean wide) {
        Map<String, Box> updated = new LinkedHashMap<>(positions(wide)); updated.remove(id);
        return wide ? new HudGeometry(portrait, updated) : new HudGeometry(updated, landscape);
    }
    public static Box move(Box start, float dx, float dy) {
        if (!Float.isFinite(dx) || !Float.isFinite(dy)) return start;
        return new Box(start.x + dx, start.y + dy, start.width, start.height);
    }
    public static Box resize(String id, Box start, int edges, float dx, float dy) {
        requireId(id);
        if (!Float.isFinite(dx) || !Float.isFinite(dy)) return start;
        float left = start.x, top = start.y, right = start.right(), bottom = start.bottom();
        if ((edges & LEFT) != 0) left = clamp(left + dx, 0, right - minimumWidth(id));
        else if ((edges & RIGHT) != 0) right = clamp(right + dx, left + minimumWidth(id), 1);
        if ((edges & TOP) != 0) top = clamp(top + dy, 0, bottom - minimumHeight(id));
        else if ((edges & BOTTOM) != 0) bottom = clamp(bottom + dy, top + minimumHeight(id), 1);
        return constrain(id, new Box(left, top, right - left, bottom - top));
    }
    private static Box constrain(String id, Box box) {
        return new Box(box.x, box.y, Math.max(minimumWidth(id), box.width), Math.max(minimumHeight(id), box.height));
    }
    private static float minimumWidth(String id) {
        if (PRIMARY.equals(id)) return .18f;
        if (id.startsWith("aux")) return .16f;
        return RPM.equals(id) || REFRESH.equals(id) || SPEED.equals(id) ? .24f : .12f;
    }
    private static float minimumHeight(String id) {
        if (PRIMARY.equals(id)) return .16f;
        if (id.startsWith("aux")) return .10f;
        return RPM.equals(id) ? .06f : .035f;
    }
    private static Box defaultBox(String id, boolean wide, List<String> visible) {
        List<String> auxiliary = new ArrayList<>();
        for (String candidate : visible) if (candidate.startsWith("aux") && IDS.contains(candidate)) auxiliary.add(candidate);
        int count = auxiliary.size(), index = Math.max(0, auxiliary.indexOf(id));
        if (BRAND.equals(id)) return wide ? new Box(.03f, .02f, .36f, .07f) : new Box(.05f, .013f, .5f, .04f);
        if (STATUS.equals(id)) return wide ? new Box(.82f, .024f, .15f, .065f) : new Box(.72f, .013f, .23f, .04f);
        if (RPM.equals(id)) return wide ? new Box(.032f, .11f, .936f, .075f) : new Box(.053f, .068f, .894f, .06f);
        if (PRIMARY.equals(id)) {
            float top = visible.contains(RPM) ? (wide ? .20f : .14f) : (wide ? .135f : .075f);
            return new Box(wide ? .028f : .047f, top, wide ? .944f : .906f,
                    (count == 0 ? .9f : wide ? .65f : .434f) - top);
        }
        if (id.startsWith("aux")) {
            if (wide) {
                float cell = (.944f - .008f * Math.max(0, count - 1)) / Math.max(1, count);
                return new Box(.028f + index * (cell + .008f), .682f, cell, .226f);
            }
            return new Box(.047f, .458f + index * .148f, .906f, .131f);
        }
        if (REFRESH.equals(id)) return new Box(wide ? .03f : .05f, .946f, .58f, .04f);
        return new Box(wide ? .65f : .64f, .946f, wide ? .32f : .31f, .04f);
    }
    public String encode(boolean wide) {
        StringBuilder value = new StringBuilder("v1");
        for (Map.Entry<String, Box> entry : positions(wide).entrySet()) {
            Box box = entry.getValue();
            value.append(';').append(entry.getKey()).append(',').append(box.x).append(',').append(box.y)
                    .append(',').append(box.width).append(',').append(box.height);
        }
        return value.toString();
    }
    public static HudGeometry decode(String portrait, String landscape) {
        return new HudGeometry(decodePositions(portrait), decodePositions(landscape));
    }
    private static Map<String, Box> decodePositions(String value) {
        Map<String, Box> result = new LinkedHashMap<>();
        if (value == null || value.length() > 4096 || !value.startsWith("v1;")) return result;
        for (String record : value.substring(3).split(";")) {
            String[] fields = record.split(",");
            if (fields.length != 5 || !IDS.contains(fields[0])) continue;
            try {
                Box box = constrain(fields[0], new Box(Float.parseFloat(fields[1]), Float.parseFloat(fields[2]),
                        Float.parseFloat(fields[3]), Float.parseFloat(fields[4])));
                result.remove(fields[0]); result.put(fields[0], box);
            } catch (IllegalArgumentException ignored) { /* A damaged entry does not erase other saved boxes. */ }
        }
        return result;
    }
    private static void requireId(String id) { if (!IDS.contains(id)) throw new IllegalArgumentException("Unknown HUD box: " + id); }
    private static float clamp(float value, float low, float high) { return Math.max(low, Math.min(high, value)); }
}
