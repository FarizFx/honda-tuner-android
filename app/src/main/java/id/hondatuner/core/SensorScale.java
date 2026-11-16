package id.hondatuner.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Zero-inclusive chart bounds. Signed temperature/ignition signals retain negative data. */
public final class SensorScale {
    public final double minimum, maximum, step;
    public final boolean hasReadings;
    private SensorScale(double minimum, double maximum, double step, boolean hasReadings) {
        this.minimum = minimum; this.maximum = maximum; this.step = step; this.hasReadings = hasReadings;
    }
    public static SensorScale forPoints(String field, List<TelemetryHistory.Point> points) {
        double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
        for (TelemetryHistory.Point point : points) if (point.value != null && Double.isFinite(point.value)) {
            low = Math.min(low, point.value); high = Math.max(high, point.value);
        }
        return forRange(field, low, high);
    }
    public static SensorScale forRange(String field, double minimum, double maximum) {
        boolean valid = Double.isFinite(minimum) && Double.isFinite(maximum) && minimum <= maximum;
        double span = field.equals("engine_speed") ? 1000 : field.endsWith("_temperature") ? 10 : 1;
        double low = 0, high = span;
        if (valid) {
            double padding = Math.max(span * .02, (maximum - minimum) * .08);
            // Inherently nonnegative sensors start at zero; signed signals include zero.
            low = SensorInfo.nonNegative(field) ? Math.min(0, minimum) : Math.min(0, minimum - padding);
            high = Math.max(0, maximum + padding);
            if (high - low < span) high = low + span;
        }
        double rough = (high - low) / 4, power = Math.pow(10, Math.floor(Math.log10(rough)));
        double ratio = rough / power;
        double step = (ratio <= 1 ? 1 : ratio <= 2 ? 2 : ratio <= 2.5 ? 2.5 : ratio <= 5 ? 5 : 10) * power;
        double first = Math.floor(low / step) * step, last = Math.ceil(high / step) * step;
        return new SensorScale(first == 0 ? 0 : first, last, step, valid);
    }
    public List<Double> ticks() {
        List<Double> result = new ArrayList<>();
        int first = (int) Math.round(minimum / step), last = (int) Math.round(maximum / step);
        for (int i = first; i <= last; i++) result.add(i == 0 ? 0.0 : i * step);
        return Collections.unmodifiableList(result);
    }
    public String label(double value) {
        if (Math.abs(value) < step * 1e-9) value = 0;
        int decimals = 0; double integral = step;
        while (decimals < 4 && Math.abs(integral - Math.rint(integral)) > 1e-7) { integral *= 10; decimals++; }
        return String.format(Locale.getDefault(), "%." + decimals + "f", value);
    }
}
