package id.hondatuner.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Last 50 measured samples. Missing replies break the line rather than extending it. */
final class RpmGraph extends View {
    private final List<Double> samples = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    RpmGraph(Context context) { super(context); setContentDescription("Grafik RPM, belum ada sampel."); }
    void clear() { samples.clear(); setContentDescription("Grafik RPM, belum ada sampel."); invalidate(); }
    void add(Double rpm) {
        samples.add(rpm != null && Double.isFinite(rpm) ? rpm : null);
        if (samples.size() > 50) samples.remove(0);
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float left = 5 * density, right = getWidth() - 5 * density, top = 12 * density, bottom = getHeight() - 28 * density;
        if (right <= left || bottom <= top) return;
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(density); paint.setColor(MainActivity.MUTED); paint.setAlpha(35);
        for (int i = 0; i < 3; i++) canvas.drawLine(left, top + (bottom - top) * i / 2, right, top + (bottom - top) * i / 2, paint);
        paint.setAlpha(255); paint.setStyle(Paint.Style.FILL); paint.setTextSize(11 * density); paint.setColor(MainActivity.MUTED);
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (Double value : samples) if (value != null) { min = Math.min(min, value); max = Math.max(max, value); }
        if (!Double.isFinite(min)) { canvas.drawText("RPM belum terbaca", left, top + (bottom - top) / 2, paint); return; }
        Path path = new Path(); boolean segment = false; double span = Math.max(1, max - min);
        for (int i = 0; i < samples.size(); i++) {
            Double value = samples.get(i); if (value == null) { segment = false; continue; }
            float x = left + (right - left) * i / Math.max(1, samples.size() - 1);
            float y = max == min ? top + (bottom - top) / 2 : bottom - (float) ((value - min) / span) * (bottom - top);
            if (!segment) { path.moveTo(x, y); canvas.drawCircle(x, y, 2 * density, paint); } else path.lineTo(x, y);
            segment = true;
        }
        paint.setColor(MainActivity.GREEN); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2 * density); canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL); paint.setColor(MainActivity.MUTED);
        String range = String.format(Locale.getDefault(), "Min %.0f   ·   Maks %.0f RPM", min, max);
        canvas.drawText(range, left, getHeight() - 8 * density, paint); setContentDescription("Grafik RPM. " + range);
    }
}
