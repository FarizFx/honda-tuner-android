package id.hondatuner.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import id.hondatuner.core.HudGeometry;
import id.hondatuner.core.HudLayout;
import id.hondatuner.core.LcdDashboard;

/** LCD canvas with a separate draft editor; ordinary taps never change placement. */
final class SpeedometerView extends View {
    interface EditorListener { void changed(String selected); }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private HudLayout layout;
    private LcdDashboard.Data data;
    private HudGeometry draft, beforeDrag;
    private HudGeometry.Box dragBox;
    private String selected;
    private EditorListener editorListener;
    private boolean editing, dragWide;
    private int pointer = -1, edges;
    private float downX, downY;
    SpeedometerView(Context context) {
        super(context); paint.setTypeface(Typeface.MONOSPACE); setClickable(true); setFocusable(true);
    }
    void update(HudLayout layout, LcdDashboard.Data data) {
        this.layout = layout; this.data = data;
        if (editing && selected != null && !LcdDashboard.blocks(layout).contains(selected)) selected = HudGeometry.PRIMARY;
        setContentDescription("Speedometer LCD. " + data.primary.field + ": " +
                (data.connected ? data.primary.value + " " + data.primary.unit : "terputus") + ". " +
                id.hondatuner.core.SensorInfo.statisticsLabel(data.primary.statistics));
        invalidate();
    }
    void setEditorListener(EditorListener listener) { editorListener = listener; }
    boolean isEditing() { return editing; }
    String selectedBlock() { return selected; }
    HudGeometry editedGeometry() { return draft; }
    void beginEdit(HudGeometry geometry) {
        cancelDrag(); draft = geometry == null ? layout.geometry : geometry; editing = true;
        selected = HudGeometry.PRIMARY; changed();
    }
    HudGeometry finishEdit(boolean save) {
        cancelDrag(); HudGeometry result = save ? draft : layout.geometry;
        editing = false; draft = null; selected = null;
        if (save) layout = layout.withGeometry(result);
        changed(); return result;
    }
    void resetPlacement() { cancelDrag(); draft = draft.reset(wide()); selected = HudGeometry.PRIMARY; changed(); }
    void resetSelected() { if (selected != null) { cancelDrag(); draft = draft.reset(selected, wide()); changed(); } }
    void selectBlock(String id) {
        cancelDrag(); selected = id;
        if (id != null) draft = draft.with(id, wide(), draft.box(id, wide(), LcdDashboard.blocks(layout)));
        changed();
    }
    private void changed() { invalidate(); if (editorListener != null) editorListener.changed(selected); }
    private boolean wide() { return getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE; }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private RectF pixels(HudGeometry.Box box) { return new RectF(box.x * getWidth(), box.y * getHeight(), box.right() * getWidth(), box.bottom() * getHeight()); }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (layout == null || getWidth() == 0 || getHeight() == 0) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            pointer = event.getPointerId(0); downX = event.getX(); downY = event.getY();
            if (editing) {
                beforeDrag = draft; dragWide = wide(); edges = handleAt(downX, downY);
                if (edges == 0) selected = draft.hit(downX / getWidth(), downY / getHeight(), dragWide, LcdDashboard.blocks(layout));
                if (selected != null) {
                    dragBox = draft.box(selected, dragWide, LcdDashboard.blocks(layout));
                    draft = draft.with(selected, dragWide, dragBox);
                }
                changed();
            }
            getParent().requestDisallowInterceptTouchEvent(true); return true;
        }
        int index = event.findPointerIndex(pointer);
        if (action == MotionEvent.ACTION_MOVE && index >= 0) {
            if (editing && dragBox != null) dragTo(event.getX(index), event.getY(index));
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.getActionIndex()) == pointer) {
            cancelDrag(); changed(); return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            if (editing && dragBox != null && index >= 0) dragTo(event.getX(index), event.getY(index));
            float distance = index < 0 ? Float.MAX_VALUE : (float) Math.hypot(event.getX(index) - downX, event.getY(index) - downY);
            clearDrag(); getParent().requestDisallowInterceptTouchEvent(false);
            if (distance <= ViewConfiguration.get(getContext()).getScaledTouchSlop()) performClick();
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) { cancelDrag(); changed(); getParent().requestDisallowInterceptTouchEvent(false); }
        return true;
    }
    private int handleAt(float x, float y) {
        if (selected == null) return 0;
        RectF r = pixels(draft.box(selected, wide(), LcdDashboard.blocks(layout)));
        float radius = dp(22);
        boolean left = Math.abs(x - r.left) <= radius, right = Math.abs(x - r.right) <= radius;
        boolean top = Math.abs(y - r.top) <= radius, bottom = Math.abs(y - r.bottom) <= radius;
        // When a small box has overlapping touch targets, choose the closest corner.
        if (left && right) { left = Math.abs(x - r.left) <= Math.abs(x - r.right); right = !left; }
        if (top && bottom) { top = Math.abs(y - r.top) <= Math.abs(y - r.bottom); bottom = !top; }
        return (left || right) && (top || bottom) ? (left ? HudGeometry.LEFT : HudGeometry.RIGHT) | (top ? HudGeometry.TOP : HudGeometry.BOTTOM) : 0;
    }
    private void dragTo(float x, float y) {
        float dx = (x - downX) / getWidth(), dy = (y - downY) / getHeight();
        HudGeometry.Box updated = edges == 0 ? HudGeometry.move(dragBox, dx, dy) : HudGeometry.resize(selected, dragBox, edges, dx, dy);
        draft = draft.with(selected, dragWide, updated); changed();
    }
    private void clearDrag() { pointer = -1; dragBox = null; beforeDrag = null; edges = 0; }
    private void cancelDrag() { if (beforeDrag != null && editing) draft = beforeDrag; clearDrag(); }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh); cancelDrag(); if (editing) changed();
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (layout == null || data == null) return;
        int saved = canvas.save();
        if (layout.mirrored && !editing) canvas.scale(-1, 1, getWidth() / 2f, getHeight() / 2f);
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
        }, getWidth(), getHeight(), editing ? layout.withGeometry(draft) : layout, data, wide());
        canvas.restoreToCount(saved);
        if (editing) drawEditor(canvas);
    }
    private void drawEditor(Canvas canvas) {
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1)); paint.setColor(0x187cd7ec);
        for (int i = 1; i < 10; i++) {
            canvas.drawLine(getWidth() * i / 10f, 0, getWidth() * i / 10f, getHeight(), paint);
            canvas.drawLine(0, getHeight() * i / 10f, getWidth(), getHeight() * i / 10f, paint);
        }
        for (String id : LcdDashboard.blocks(layout)) {
            paint.setColor(id.equals(selected) ? 0xfff0f6ff : 0x887cd7ec);
            paint.setStrokeWidth(dp(id.equals(selected) ? 2 : 1));
            RectF r = pixels(draft.box(id, wide(), LcdDashboard.blocks(layout)));
            canvas.drawRect(r.left + 1, r.top + 1, r.right - 1, r.bottom - 1, paint);
        }
        if (selected != null) {
            RectF r = pixels(draft.box(selected, wide(), LcdDashboard.blocks(layout)));
            paint.setStyle(Paint.Style.FILL); paint.setColor(0xfff0f6ff);
            for (float x : new float[]{r.left, r.right}) for (float y : new float[]{r.top, r.bottom}) canvas.drawCircle(x, y, dp(7), paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }
}
