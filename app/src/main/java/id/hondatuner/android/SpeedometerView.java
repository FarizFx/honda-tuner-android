package id.hondatuner.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;
import id.hondatuner.core.HudLayout;
import id.hondatuner.core.LcdDashboard;

/** LCD instrument surface. Controls stay outside the optionally mirrored display. */
final class SpeedometerView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private HudLayout layout;
    private LcdDashboard.Data data;
    SpeedometerView(Context context) { super(context); paint.setTypeface(Typeface.MONOSPACE); }
    void update(HudLayout layout, LcdDashboard.Data data) {
        this.layout = layout; this.data = data;
        setContentDescription("Speedometer LCD. " + data.primary.field + ": " +
                (data.connected ? data.primary.value + " " + data.primary.unit : "terputus") + ". " +
                id.hondatuner.core.SensorInfo.statisticsLabel(data.primary.statistics));
        invalidate();
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (layout == null || data == null) return;
        int saved = canvas.save();
        if (layout.mirrored) canvas.scale(-1, 1, getWidth() / 2f, getHeight() / 2f);
        LcdDashboard.render(new LcdDashboard.Painter() {
            @Override public void box(float l, float t, float r, float b, float radius, int color) {
                paint.setColor(color); paint.setStyle(Paint.Style.FILL); canvas.drawRoundRect(l, t, r, b, radius, radius, paint);
            }
            @Override public void text(String text, float x, float baseline, float size, int color, int alignment) {
                paint.setColor(color); paint.setTextSize(size);
                paint.setTextAlign(alignment == 1 ? Paint.Align.CENTER : alignment == 2 ? Paint.Align.RIGHT : Paint.Align.LEFT);
                canvas.drawText(text, x, baseline, paint);
            }
            @Override public float measure(String text, float size) { paint.setTextSize(size); return paint.measureText(text); }
        }, getWidth(), getHeight(), layout, data);
        canvas.restoreToCount(saved);
    }
}
