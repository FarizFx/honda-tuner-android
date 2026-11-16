package id.hondatuner.core;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/** Offline preview of the actual shared drawing code; requires only the existing JDK. */
public final class DashboardPreview {
    private DashboardPreview() {}
    public static void main(String[] args) throws Exception {
        File directory = new File(args[0]); if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Preview directory unavailable");
        draw(new File(directory, "lcd-landscape.png"), 1600, 720, false, false);
        draw(new File(directory, "lcd-portrait.png"), 600, 1000, false, false);
        draw(new File(directory, "lcd-zero.png"), 1600, 720, true, false);
        draw(new File(directory, "lcd-mirrored.png"), 1600, 720, false, true);
        System.out.println("Rendered four previews using the shared LCD renderer.");
    }
    private static void draw(File file, int width, int height, boolean zero, boolean mirrored) throws Exception {
        TelemetryHistory history = new TelemetryHistory(600);
        history.reset(Arrays.asList("vss", "engine_speed", "tp_sensor_angle", "battery_voltage", "ect_sensor_temperature"));
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i <= 20; i++) {
            values.put("vss", zero ? 0 : (int) (42 * i / 20.0)); values.put("engine_speed", zero ? 0 : 1200 + i * 280);
            values.put("tp_sensor_angle", zero ? 0.0 : 84.5 * i / 20); values.put("battery_voltage", zero ? 12.8 : 12.2 + 1.8 * i / 20);
            values.put("ect_sensor_temperature", zero ? 25.0 : 60 + 25.0 * i / 20);
            history.add(new LiveProfile.Sample(values, Collections.emptyMap(), 1, i * 61250000L, i * 61));
        }
        HudLayout layout = new HudLayout(zero ? "engine_speed" : "vss", Arrays.asList("tp_sensor_angle", "battery_voltage", "ect_sensor_temperature"),
                0, 1f, mirrored, ! (width < height), true, true, 15000);
        List<LcdDashboard.Reading> auxiliary = new ArrayList<>();
        for (String field : layout.auxiliary) auxiliary.add(new LcdDashboard.Reading(field, values.get(field), history.statistics(field)));
        LcdDashboard.Data data = new LcdDashboard.Data(new LcdDashboard.Reading(layout.primary, values.get(layout.primary), history.statistics(layout.primary)),
                auxiliary, TelemetryHistory.numeric(values.get("engine_speed")), 61.25, 57.2, true, true, 0, "VSS TERSEDIA / RAW");
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (mirrored) { graphics.translate(width, 0); graphics.scale(-1, 1); }
        LcdDashboard.render(new LcdDashboard.Painter() {
            private void font(float size) { graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 1).deriveFont(size)); }
            @Override public void box(float l, float t, float r, float b, float radius, int color) {
                graphics.setColor(new Color(color, true)); graphics.fill(new RoundRectangle2D.Float(l, t, r - l, b - t, radius * 2, radius * 2));
            }
            @Override public float measure(String text, float size) { font(size); return (float) graphics.getFontMetrics().getStringBounds(text, graphics).getWidth(); }
            @Override public void text(String text, float x, float y, float size, int color, int alignment) {
                float span = measure(text, size); graphics.setColor(new Color(color, true));
                graphics.drawString(text, alignment == 1 ? x - span / 2 : alignment == 2 ? x - span : x, y);
            }
        }, width, height, layout, data);
        graphics.dispose(); ImageIO.write(image, "png", file);
    }
}
