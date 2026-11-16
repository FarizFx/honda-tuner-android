package id.hondatuner.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

final class HudGeometryTest {
    private interface Checked { void run(); }
    private static int passed;
    private static final HudLayout LAYOUT = HudLayout.defaults(Arrays.asList("engine_speed", "tp_sensor_angle", "battery_voltage", "ect_sensor_temperature"));
    private static final List<String> VISIBLE = LcdDashboard.blocks(LAYOUT);
    static int runAll() {
        run("all HUD blocks have valid defaults in both orientations", HudGeometryTest::defaults);
        run("dragging clamps to canvas edges without changing size", HudGeometryTest::move);
        run("all four resize corners retain the opposite corner", HudGeometryTest::resize);
        run("oversized and inverted resize gestures stay usable and inside the canvas", HudGeometryTest::limits);
        run("portrait and landscape placements are independent", HudGeometryTest::orientations);
        run("saved positions, dimensions and stacking order survive serialization", HudGeometryTest::persistence);
        run("corrupt saved coordinates cannot hide valid neighboring HUD blocks", HudGeometryTest::corrupt);
        run("draft changes and reset leave the previous immutable layout untouched", HudGeometryTest::draft);
        run("overlapping HUD blocks select the topmost layer", HudGeometryTest::overlap);
        run("hidden auxiliary slots retain their positions and sensor identity", HudGeometryTest::hidden);
        run("moving and resizing HUD boxes does not alter ECU polling choices", HudGeometryTest::polling);
        run("custom resized cards fit compact, tall and wide canvases without clipped drawing", HudGeometryTest::drawing);
        return passed;
    }
    private static void run(String name, Checked test) { test.run(); passed++; System.out.println("OK " + name); }
    private static void check(boolean okay, String reason) { if (!okay) throw new AssertionError(reason); }
    private static void near(float expected, float actual) { check(Math.abs(expected - actual) < .00001f, expected + " != " + actual); }
    private static void box(HudGeometry.Box b) { check(b.x >= 0 && b.y >= 0 && b.width > 0 && b.height > 0 && b.right() <= 1.00001f && b.bottom() <= 1.00001f, "Box escaped the canvas"); }
    private static void same(HudGeometry.Box a, HudGeometry.Box b) { near(a.x, b.x); near(a.y, b.y); near(a.width, b.width); near(a.height, b.height); }
    private static void defaults() {
        for (boolean wide : new boolean[]{false, true}) for (String id : VISIBLE) box(new HudGeometry().box(id, wide, VISIBLE));
        HudLayout empty = new HudLayout("engine_speed", Collections.emptyList(), 0, 1, false, false, true, false, 15000);
        for (boolean wide : new boolean[]{false, true}) box(empty.geometry.box(HudGeometry.PRIMARY, wide, LcdDashboard.blocks(empty)));
    }
    private static void move() {
        HudGeometry.Box start = new HudGeometry.Box(.2f, .3f, .4f, .25f);
        HudGeometry.Box moved = HudGeometry.move(start, .1f, -.2f); near(.3f, moved.x); near(.1f, moved.y);
        near(start.width, moved.width); near(start.height, moved.height);
        moved = HudGeometry.move(start, 100, -100); near(.6f, moved.x); near(0, moved.y); box(moved);
        same(start, HudGeometry.move(start, Float.NaN, 1));
    }
    private static void resize() {
        HudGeometry.Box start = new HudGeometry.Box(.25f, .25f, .4f, .4f);
        for (int edges : new int[]{3, 6, 12, 9}) {
            HudGeometry.Box b = HudGeometry.resize(HudGeometry.PRIMARY, start, edges, .05f, .05f);
            if ((edges & HudGeometry.LEFT) != 0) near(start.right(), b.right()); else near(start.x, b.x);
            if ((edges & HudGeometry.TOP) != 0) near(start.bottom(), b.bottom()); else near(start.y, b.y);
            box(b);
        }
    }
    private static void limits() {
        for (String id : VISIBLE) for (int edges : new int[]{3, 6, 12, 9}) for (float delta : new float[]{-100, 100}) {
            HudGeometry.Box b = HudGeometry.resize(id, new HudGeometry.Box(.25f, .25f, .4f, .4f), edges, delta, delta);
            box(b); check(b.width >= .119f && b.height >= .034f, "Resize made the box unusable");
        }
    }
    private static void orientations() {
        HudGeometry g = new HudGeometry().with("primary", true, new HudGeometry.Box(.1f, .2f, .5f, .5f));
        check(g.hasPlacement("primary", true) && !g.hasPlacement("primary", false), "Landscape leaked into portrait");
        g = g.with("primary", false, new HudGeometry.Box(.2f, .5f, .7f, .3f));
        near(.1f, g.box("primary", true, VISIBLE).x); near(.2f, g.box("primary", false, VISIBLE).x);
    }
    private static void persistence() {
        HudGeometry g = new HudGeometry();
        for (boolean wide : new boolean[]{false, true}) for (String id : VISIBLE) g = g.with(id, wide, new HudGeometry.Box(.05f, .05f, .75f, .7f));
        g = g.with("primary", true, new HudGeometry.Box(.2f, .3f, .4f, .4f));
        HudGeometry loaded = HudGeometry.decode(g.encode(false), g.encode(true));
        for (boolean wide : new boolean[]{false, true}) {
            check(g.order(VISIBLE, wide).equals(loaded.order(VISIBLE, wide)), "Layer order was lost");
            for (String id : VISIBLE) same(g.box(id, wide, VISIBLE), loaded.box(id, wide, VISIBLE));
        }
    }
    private static void corrupt() {
        HudGeometry g = HudGeometry.decode("v1;primary,NaN,0,.5,.5;aux0,.1,.2,.3,.3;unknown,0,0,1,1;status,Infinity,0,.2,.1", "bad-format");
        check(!g.hasPlacement("primary", false) && g.hasPlacement("aux0", false) && !g.hasPlacement("status", false), "Invalid values reached the canvas");
        check(!g.hasPlacement("aux0", true), "Broken orientation did not fall back");
        g = HudGeometry.decode("v1;primary,-20,-10,40,30", null); box(g.box("primary", false, VISIBLE));
        near(1, g.box("primary", false, VISIBLE).width);
    }
    private static void draft() {
        HudGeometry saved = new HudGeometry().with("primary", false, new HudGeometry.Box(.2f, .2f, .4f, .4f));
        String before = saved.encode(false);
        HudGeometry draft = saved.with("primary", false, new HudGeometry.Box(.5f, .5f, .5f, .5f)).with("aux1", true, new HudGeometry.Box(.3f, .3f, .3f, .3f));
        check(before.equals(saved.encode(false)), "Draft mutated saved positions");
        draft = draft.reset(false); check(!draft.hasPlacement("primary", false) && draft.hasPlacement("aux1", true), "Reset erased the other orientation");
        HudGeometry one = saved.with("aux0", false, new HudGeometry.Box(.1f, .1f, .2f, .2f)).reset("primary", false);
        check(!one.hasPlacement("primary", false) && one.hasPlacement("aux0", false), "Reset erased other boxes");
    }
    private static void overlap() {
        HudGeometry.Box b = new HudGeometry.Box(.25f, .25f, .3f, .3f);
        HudGeometry g = new HudGeometry().with("primary", true, b).with("aux0", true, b);
        check("aux0".equals(g.hit(.3f, .3f, true, VISIBLE)), "Wrong topmost hit");
        g = g.with("primary", true, b);
        check("primary".equals(g.hit(.3f, .3f, true, VISIBLE)), "Selected block did not come forward");
        check(g.hit(-1, -1, true, VISIBLE) == null && g.hit(Float.NaN, 0, true, VISIBLE) == null, "Outside tap selected a block");
    }
    private static void hidden() {
        HudGeometry g = new HudGeometry().with("aux1", true, new HudGeometry.Box(.3f, .3f, .4f, .3f));
        HudLayout hidden = new HudLayout("engine_speed", Arrays.asList("", "", "battery_voltage"), 0, 1, false, true, true, true, 15000, g);
        check(!LcdDashboard.blocks(hidden).contains("aux1") && LcdDashboard.blocks(hidden).contains("aux2"), "Hidden slot shifted identity");
        check(g.hasPlacement("aux1", true) && !g.order(LcdDashboard.blocks(hidden), true).contains("aux1"), "Hidden slot lost its saved position");
    }
    private static void polling() {
        List<String> fields = Arrays.asList("engine_speed", "battery_voltage", "tp_sensor_angle", "ect_sensor_temperature");
        HudLayout moved = LAYOUT.withGeometry(LAYOUT.geometry.with("primary", true, new HudGeometry.Box(.5f, .2f, .4f, .4f)));
        check(LAYOUT.pollingFields(fields).equals(moved.pollingFields(fields)), "Placement altered wire selection");
    }
    private static void drawing() {
        TelemetryHistory history = new TelemetryHistory(10);
        LcdDashboard.Reading primary = new LcdDashboard.Reading("engine_speed", 65535, history.statistics("engine_speed"));
        List<LcdDashboard.Reading> auxiliary = new ArrayList<>();
        for (String field : LAYOUT.auxiliary) auxiliary.add(new LcdDashboard.Reading(field, -12.5, history.statistics(field)));
        LcdDashboard.Data data = new LcdDashboard.Data(primary, auxiliary, 65535.0, 51.2, 49.5, true, true, 3000, "VSS BELUM TERSEDIA");
        for (boolean wide : new boolean[]{false, true}) for (int[] size : new int[][]{{320,640},{640,320},{600,520},{2400,1080},{1080,2400}}) {
            HudGeometry g = new HudGeometry();
            for (String id : VISIBLE) {
                g = g.with(id, wide, new HudGeometry.Box(.12f, .02f, .19f, .92f));
                LcdDashboard.render(new Bounds(size[0], size[1]), size[0], size[1], LAYOUT.withGeometry(g), data, wide);
                g = g.with(id, wide, new HudGeometry.Box(.04f, .75f, .96f, .16f));
                LcdDashboard.render(new Bounds(size[0], size[1]), size[0], size[1], LAYOUT.withGeometry(g), data, wide);
            }
        }
    }
    private static final class Bounds implements LcdDashboard.Painter {
        final int width, height;
        Bounds(int width, int height) { this.width = width; this.height = height; }
        private void point(float x, float y) { check(Float.isFinite(x) && Float.isFinite(y) && x >= -.1f && y >= -.1f && x <= width + .1f && y <= height + .1f, "Resized block drew outside canvas: " + x + ", " + y); }
        @Override public void box(float l, float t, float r, float b, float radius, int color) { point(l,t); point(r,b); check(r >= l && b >= t, "Inverted drawing"); }
        @Override public float measure(String text, float size) { return text.length() * size * .61f; }
        @Override public void text(String text, float x, float y, float size, int color, int alignment) {
            float span = measure(text, size), left = alignment == 1 ? x - span / 2 : alignment == 2 ? x - span : x;
            point(left, y - size); point(left + span, y);
        }
    }
}
