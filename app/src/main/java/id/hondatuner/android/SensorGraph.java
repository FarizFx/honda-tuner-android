package id.hondatuner.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import id.hondatuner.core.SensorInfo;
import id.hondatuner.core.SensorScale;
import id.hondatuner.core.TelemetryHistory;

/** Measured time on the X axis. Null readings break the line rather than inventing data. */
final class SensorGraph extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<TelemetryHistory.Point> points = new ArrayList<>();
    private String field = "";
    SensorGraph(Context context) { super(context); setContentDescription("Grafik sensor, belum ada sampel."); }
    void setSeries(String field, List<TelemetryHistory.Point> points) {
        this.field = field; this.points = points;
        int valid = 0; for (TelemetryHistory.Point point : points) if (point.value != null) valid++;
        setContentDescription("Grafik " + (field.isEmpty() ? "sensor" : SensorInfo.label(field)) + ". " + valid + " dari " + points.size() + " sampel valid. Celah menunjukkan data tidak terbaca."); invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); float density = getResources().getDisplayMetrics().density;
        SensorScale scale = SensorScale.forPoints(field, points);
        paint.setTextSize(11 * density);
        float labels = 0; for (double tick : scale.ticks()) labels = Math.max(labels, paint.measureText(scale.label(tick)));
        float left = Math.max(48 * density, labels + 12 * density), right = getWidth() - 12 * density, top = 30 * density, bottom = getHeight() - 35 * density;
        if (right <= left || bottom <= top) return;
        paint.setStyle(Paint.Style.FILL); paint.setColor(MainActivity.MUTED); paint.setAlpha(255); paint.setTextSize(11 * density); paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(field.isEmpty() ? "Sensor belum tersedia" : SensorInfo.label(field) + (SensorInfo.unit(field).isEmpty() ? " · raw" : " · " + SensorInfo.unit(field)), 0, 11 * density, paint);
        double low = scale.minimum, high = scale.maximum, span = high - low;
        for (double tick : scale.ticks()) {
            float y = bottom - (float) ((tick - low) / span) * (bottom - top);
            paint.setColor(MainActivity.MUTED); paint.setAlpha(tick == 0 ? 110 : 40); paint.setStrokeWidth(density); canvas.drawLine(left, y, right, y, paint);
            paint.setAlpha(255); paint.setTextAlign(Paint.Align.RIGHT); canvas.drawText(scale.label(tick), left - 6 * density, y + 4 * density, paint);
        }
        if (!scale.hasReadings) {
            paint.setTextAlign(Paint.Align.LEFT); canvas.drawText("Sensor belum terbaca", left + 8 * density, (top + bottom) / 2, paint); return;
        }
        long first = points.get(0).timestampNanos, last = points.get(points.size() - 1).timestampNanos;
        long duration = Math.max(1, last - first); Path path = new Path(); boolean segment = false;
        paint.setColor(MainActivity.GREEN); paint.setTextAlign(Paint.Align.LEFT);
        for (TelemetryHistory.Point point : points) {
            if (point.value == null) { segment = false; continue; }
            float x = points.size() == 1 ? (left + right) / 2 : left + (right - left) * (float) ((point.timestampNanos - first) / (double) duration);
            float y = bottom - (float) ((point.value - low) / span) * (bottom - top);
            if (!segment) { path.moveTo(x, y); canvas.drawCircle(x, y, 2.5f * density, paint); } else path.lineTo(x, y);
            segment = true;
        }
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2 * density); canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL); paint.setColor(MainActivity.MUTED);
        canvas.drawText("0 s", left, bottom + 20 * density, paint); paint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(String.format(Locale.getDefault(), "%.1f s", Math.max(0, last - first) / 1000000000.0), right, bottom + 20 * density, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }
}
