package id.hondatuner.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/** Saved dashboard choices; sensor availability and wire selection remain explicit. */
public final class HudLayout {
    public static final int[] COLORS = {0xffa7f4bd, 0xff91e7f2, 0xffffcc81, 0xffedf5ee};
    public final String primary;
    public final List<String> auxiliary;
    public final int theme, rpmLimit;
    public final float digitScale;
    public final boolean mirrored, landscape, focused, rpmBar;

    public HudLayout(String primary, List<String> auxiliary, int theme, float digitScale,
                     boolean mirrored, boolean landscape, boolean focused, boolean rpmBar, int rpmLimit) {
        this.primary = primary == null || primary.isEmpty() ? "engine_speed" : primary;
        List<String> slots = new ArrayList<>();
        for (int i = 0; i < 3; i++) slots.add(i < auxiliary.size() && auxiliary.get(i) != null ? auxiliary.get(i) : "");
        this.auxiliary = Collections.unmodifiableList(slots);
        this.theme = Math.max(0, Math.min(COLORS.length - 1, theme));
        this.digitScale = Float.isFinite(digitScale) ? Math.max(.75f, Math.min(1.25f, digitScale)) : 1f;
        this.mirrored = mirrored; this.landscape = landscape; this.focused = focused;
        this.rpmBar = rpmBar; this.rpmLimit = Math.max(1000, Math.min(30000, rpmLimit));
    }
    public static HudLayout defaults(Collection<String> fields) {
        String primary = fields.contains("vss") ? "vss" : "engine_speed";
        String tps = fields.contains("tp_sensor_1_angle") && !fields.contains("tp_sensor_angle") ? "tp_sensor_1_angle" : "tp_sensor_angle";
        String temperature = fields.contains("eot_sensor_temperature") && !fields.contains("ect_sensor_temperature") ? "eot_sensor_temperature" : "ect_sensor_temperature";
        return new HudLayout(primary, Arrays.asList(tps, "battery_voltage", temperature), 0, 1f, false, true, true, true, 15000);
    }
    public List<String> pollingFields(Collection<String> available) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (available.contains(primary)) result.add(primary);
        for (String name : auxiliary) if (!name.isEmpty() && available.contains(name)) result.add(name);
        if (rpmBar && available.contains("engine_speed")) result.add("engine_speed");
        return Collections.unmodifiableList(new ArrayList<>(result));
    }
}
