package com.ekts.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.ekts.app.model.CircuitModel;
import com.ekts.app.model.SimulationEngine;

import java.util.HashSet;
import java.util.Set;

/**
 * بوم رسم مدار — جاوا — صفحه مجازی 1000×800
 * ویرایش: ضربه روی جای خالی = جای‌گذاری قطعه انتخاب‌شده، کشیدن = جابه‌جایی با چسبیدن به شبکه
 * اجرا: شستی = فشردن لحظه‌ای، کلید = ضامن، جریان با نقطه‌های متحرک روی سیم‌های برق‌دار
 */
public class CircuitView extends View {

    public interface StatusListener { void onStatus(String status); }
    public interface SelectionListener { void onSelection(CircuitModel.CircuitComponent comp); }
    public interface PlaceListener { void onPlaced(CircuitModel.CircuitComponent comp); }

    private CircuitModel.Circuit circuit = new CircuitModel.Circuit("مدار جدید");
    private boolean running = false;
    private String selectedId = null;
    private CircuitModel.ComponentType pendingType = null;

    public StatusListener onStatus;
    public SelectionListener onSelection;
    public PlaceListener onPlaced;

    private final SimulationEngine engine = new SimulationEngine();
    private final Set<String> pressedIds = new HashSet<>();
    private final Set<String> latchedIds = new HashSet<>();
    private SimulationEngine.Result lastResult = null;
    private float userZoom = 1.0f;
    private float flowPhase = 0f;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (running) {
                lastResult = engine.evaluate(circuit, unionPressed(), System.currentTimeMillis());
                flowPhase += 0.12f;
                if (onStatus != null) onStatus.onStatus(lastResult.statusFa);
                invalidate();
                handler.postDelayed(this, 120L);
            }
        }
    };

    private Set<String> unionPressed() {
        Set<String> s = new HashSet<>(pressedIds);
        s.addAll(latchedIds);
        return s;
    }

    private final Paint gridPaint = new Paint();
    private final Paint wirePaint = new Paint();
    private final Paint wireHotPaint = new Paint();
    private final Paint flowPaint = new Paint();
    private final Paint boxPaint = new Paint();
    private final Paint boxOnPaint = new Paint();
    private final Paint borderPaint = new Paint();
    private final Paint borderSelPaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint labelPaint = new Paint();
    private final Paint termPaint = new Paint();

    private float scale = 1f;
    private float offX = 0f;
    private float offY = 0f;

    public CircuitView(Context context) { super(context); init(); }
    public CircuitView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        gridPaint.setColor(Color.parseColor("#E8EFEA"));
        gridPaint.setStrokeWidth(1f);
        wirePaint.setColor(Color.parseColor("#5A6B62"));
        wirePaint.setStrokeWidth(5f);
        wirePaint.setAntiAlias(true);
        wireHotPaint.setColor(Color.parseColor("#E53935"));
        wireHotPaint.setStrokeWidth(7f);
        wireHotPaint.setAntiAlias(true);
        flowPaint.setColor(Color.parseColor("#FFD600"));
        flowPaint.setAntiAlias(true);
        boxPaint.setColor(Color.WHITE);
        boxPaint.setStyle(Paint.Style.FILL);
        boxPaint.setAntiAlias(true);
        boxOnPaint.setColor(Color.parseColor("#C8E6C9"));
        boxOnPaint.setStyle(Paint.Style.FILL);
        boxOnPaint.setAntiAlias(true);
        borderPaint.setColor(Color.parseColor("#1B5E20"));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3f);
        borderPaint.setAntiAlias(true);
        borderSelPaint.setColor(Color.parseColor("#1565C0"));
        borderSelPaint.setStyle(Paint.Style.STROKE);
        borderSelPaint.setStrokeWidth(5f);
        borderSelPaint.setAntiAlias(true);
        textPaint.setColor(Color.BLACK);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
        labelPaint.setColor(Color.parseColor("#1B5E20"));
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setAntiAlias(true);
        labelPaint.setFakeBoldText(true);
        termPaint.setColor(Color.parseColor("#37474F"));
        termPaint.setStyle(Paint.Style.FILL);
        termPaint.setAntiAlias(true);
    }

    public CircuitModel.Circuit getCircuit() { return circuit; }

    public void setCircuit(CircuitModel.Circuit value) {
        circuit = value;
        selectedId = null;
        pendingType = null;
        pressedIds.clear();
        latchedIds.clear();
        engine.reset();
        invalidate();
    }

    public boolean isRunning() { return running; }

    public void setPendingType(CircuitModel.ComponentType type) { pendingType = type; }
    public CircuitModel.ComponentType getPendingType() { return pendingType; }

    public void setZoom(float z) {
        userZoom = Math.max(0.7f, Math.min(1.8f, z));
        invalidate();
    }
    public float getZoom() { return userZoom; }

    public void startSimulation() {
        running = true;
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    public void stopSimulation() {
        running = false;
        handler.removeCallbacks(tick);
        lastResult = null;
        pressedIds.clear();
        latchedIds.clear();
        invalidate();
    }

    public void deleteSelected() {
        if (selectedId == null) return;
        String id = selectedId;
        circuit.components.removeIf(c -> c.id.equals(id));
        circuit.wires.removeIf(w -> w.fromId.equals(id) || w.toId.equals(id));
        selectedId = null;
        if (onSelection != null) onSelection.onSelection(null);
        invalidate();
    }

    public void rotateSelected() {
        CircuitModel.CircuitComponent comp = selectedComponent();
        if (comp == null) return;
        comp.rotate90();
        invalidate();
    }

    public CircuitModel.CircuitComponent selectedComponent() {
        if (selectedId == null) return null;
        return circuit.component(selectedId);
    }

    public String addComponent(CircuitModel.ComponentType type, float x, float y) {
        String base = type.code;
        int n = 1;
        String id = base + n;
        while (circuit.component(id) != null) {
            n++;
            id = base + n;
        }
        float sx = snap(x);
        float sy = snap(y);
        CircuitModel.CircuitComponent comp = new CircuitModel.CircuitComponent(id, type, sx, sy, id);
        // نام پیش‌فرض بوبین/تایمر: KM1, KT1 ... — اگر id عددی نیست، label همان id است
        circuit.components.add(comp);
        selectedId = id;
        if (onSelection != null) onSelection.onSelection(comp);
        invalidate();
        return id;
    }

    public String addComponentAtCenter(CircuitModel.ComponentType type) {
        float offset = (circuit.components.size() % 5) * 40f;
        return addComponent(type, 450f + offset, 350f + offset);
    }

    private float snap(float v) {
        return Math.round(v / 50f) * 50f;
    }

    public boolean wireBetween(String aId, String bId) {
        if (aId == null || bId == null || aId.equals(bId)) return false;
        for (CircuitModel.Wire w : circuit.wires) {
            if ((w.fromId.equals(aId) && w.toId.equals(bId)) || (w.fromId.equals(bId) && w.toId.equals(aId))) return false;
        }
        circuit.wires.add(new CircuitModel.Wire(aId, bId));
        invalidate();
        return true;
    }

    /** خروجی PNG از مدار — برای File -> Save as در ETKS */
    public Bitmap exportBitmap() {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.WHITE);
        draw(canvas);
        return bmp;
    }

    private float vx(float x) { return offX + x * scale; }
    private float vy(float y) { return offY + y * scale; }
    private float ix(float px) { return (px - offX) / scale; }
    private float iy(float py) { return (py - offY) / scale; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) return;
        scale = Math.min(getWidth() / 1000f, getHeight() / 800f) * userZoom;
        offX = (getWidth() - 1000f * scale) / 2f;
        offY = (getHeight() - 800f * scale) / 2f;

        for (float gx = 0; gx <= 1000; gx += 50) {
            canvas.drawLine(vx(gx), vy(0), vx(gx), vy(800), gridPaint);
        }
        for (float gy = 0; gy <= 800; gy += 50) {
            canvas.drawLine(vx(0), vy(gy), vx(1000), vy(gy), gridPaint);
        }

        for (CircuitModel.Wire wire : circuit.wires) {
            CircuitModel.CircuitComponent a = circuit.component(wire.fromId);
            CircuitModel.CircuitComponent b = circuit.component(wire.toId);
            if (a == null || b == null) continue;
            boolean hot = lastResult != null
                    && lastResult.energizedIds.contains(a.id)
                    && lastResult.energizedIds.contains(b.id);
            Paint paint = (running && hot) ? wireHotPaint : wirePaint;
            float x1 = vx(a.x + 75f);
            float y1 = vy(a.y);
            float x2 = vx(b.x - 75f);
            float y2 = vy(b.y);
            if (Math.abs(y1 - y2) < 4f) {
                canvas.drawLine(x1, y1, x2, y2, paint);
                if (running && hot) drawFlowDot(canvas, x1, y1, x2, y2);
            } else {
                float mx = (x1 + x2) / 2f;
                canvas.drawLine(x1, y1, mx, y1, paint);
                canvas.drawLine(mx, y1, mx, y2, paint);
                canvas.drawLine(mx, y2, x2, y2, paint);
                if (running && hot) drawFlowDot(canvas, x1, y1, x2, y2);
            }
        }

        for (CircuitModel.CircuitComponent comp : circuit.components) {
            drawComponent(canvas, comp);
        }
    }

    private void drawFlowDot(Canvas canvas, float x1, float y1, float x2, float y2) {
        float t = flowPhase - (float) Math.floor(flowPhase);
        float dx = x1 + (x2 - x1) * t;
        float dy = y1 + (y2 - y1) * t;
        canvas.drawCircle(dx, dy, 9f * Math.max(0.7f, scale), flowPaint);
    }

    private void drawComponent(Canvas canvas, CircuitModel.CircuitComponent comp) {
        float cx = vx(comp.x);
        float cy = vy(comp.y);
        boolean vertical = comp.rotation == 90 || comp.rotation == 270;
        float w = (vertical ? 72f : 150f) * scale;
        float h = (vertical ? 150f : 72f) * scale;
        boolean on = lastResult != null && (Boolean.TRUE.equals(lastResult.loadOn.get(comp.id))
                || (Boolean.TRUE.equals(lastResult.coilOn.get(comp.label))
                && (comp.type == CircuitModel.ComponentType.CONTACTOR_COIL
                || comp.type == CircuitModel.ComponentType.RELAY_COIL)));

        Paint fill = boxPaint;
        if (comp.type == CircuitModel.ComponentType.LAMP && on) {
            fill = new Paint(boxOnPaint);
            int lampColor = lampColorFor(comp.color);
            fill.setColor(lampColor);
        } else if (on) {
            fill = boxOnPaint;
        }
        canvas.save();
        canvas.rotate(comp.rotation, cx, cy);
        canvas.drawRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, fill);
        Paint border = comp.id.equals(selectedId) ? borderSelPaint : borderPaint;
        canvas.drawRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, border);
        canvas.drawCircle(cx - w / 2, cy, 8f * scale, termPaint);
        canvas.drawCircle(cx + w / 2, cy, 8f * scale, termPaint);
        String symbol = symbolFor(comp.type);
        textPaint.setTextSize(28f * Math.max(0.7f, scale));
        canvas.drawText(symbol, cx, cy + 10f * scale, textPaint);
        canvas.restore();

        labelPaint.setTextSize(24f * Math.max(0.7f, scale));
        canvas.drawText(comp.label, cx, cy - h / 2 - 10f * scale, labelPaint);
        if (comp.type == CircuitModel.ComponentType.TIMER_ON || comp.type == CircuitModel.ComponentType.TIMER_OFF) {
            labelPaint.setTextSize(20f * Math.max(0.7f, scale));
            canvas.drawText(comp.delaySec + "s", cx, cy + h / 2 + 26f * scale, labelPaint);
        } else if (comp.type == CircuitModel.ComponentType.CONTACTOR_COIL
                || comp.type == CircuitModel.ComponentType.MOTOR
                || comp.type == CircuitModel.ComponentType.MOTOR_1PH) {
            labelPaint.setTextSize(20f * Math.max(0.7f, scale));
            canvas.drawText(on ? "روشن" : "خاموش", cx, cy + h / 2 + 26f * scale, labelPaint);
        }
    }

    private int lampColorFor(String color) {
        if (color == null) return Color.parseColor("#FFF59D");
        switch (color) {
            case "قرمز": return Color.parseColor("#EF9A9A");
            case "سبز": return Color.parseColor("#A5D6A7");
            case "آبی": return Color.parseColor("#90CAF9");
            case "زرد": return Color.parseColor("#FFF59D");
            default: return Color.parseColor("#FFF59D");
        }
    }

    private String symbolFor(CircuitModel.ComponentType type) {
        switch (type) {
            case POWER_L: return "L ~";
            case POWER_N: return "N";
            case POWER_3PH: return "3~";
            case FUSE: return "F";
            case SWITCH_3P: return "Q";
            case START_NO: return "○—○";
            case STOP_NC: return "●—●";
            case JOG_NO: return "J ○";
            case SWITCH: return "—/—";
            case CONTACTOR_COIL: return "KM";
            case RELAY_COIL: return "K";
            case CONTACT_NO: return "—| |—";
            case CONTACT_NC: return "—|/|—";
            case OVERLOAD_NC: return "F2";
            case TIMER_ON: return "KT ↑";
            case TIMER_OFF: return "KT ↓";
            case TIMER_CONTACT_NO: return "KT —| |—";
            case TIMER_CONTACT_NC: return "KT —|/|—";
            case LIMIT_NO: return "LS ○";
            case LIMIT_NC: return "LS ●";
            case MOTOR: return "M 3~";
            case MOTOR_1PH: return "M 1~";
            case LAMP: return "○ H";
            case BUZZER: return "HA";
            default: return "?";
        }
    }

    private String dragId = null;
    private float downX = 0f;
    private float downY = 0f;
    private boolean moved = false;

    private CircuitModel.CircuitComponent hit(float px, float py) {
        float x = ix(px);
        float y = iy(py);
        for (CircuitModel.CircuitComponent comp : circuit.components) {
            if (Math.abs(comp.x - x) < 85f && Math.abs(comp.y - y) < 55f) return comp;
        }
        return null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                downX = event.getX();
                downY = event.getY();
                moved = false;
                CircuitModel.CircuitComponent comp = hit(event.getX(), event.getY());
                // جای‌گذاری با ضربه — آسان‌تر از ETKS: قطعه را انتخاب کن، روی بوم بزن
                if (comp == null && pendingType != null && !running) {
                    String id = addComponent(pendingType, ix(event.getX()), iy(event.getY()));
                    pendingType = null;
                    CircuitModel.CircuitComponent placed = circuit.component(id);
                    if (onPlaced != null) onPlaced.onPlaced(placed);
                    return true;
                }
                if (comp == null) {
                    selectedId = null;
                    if (onSelection != null) onSelection.onSelection(null);
                    invalidate();
                    return true;
                }
                dragId = comp.id;
                if (running) {
                    switch (comp.type) {
                        case START_NO:
                        case JOG_NO:
                        case STOP_NC:
                        case LIMIT_NO:
                        case LIMIT_NC:
                        case OVERLOAD_NC:
                            pressedIds.add(comp.id);
                            break;
                        case SWITCH:
                        case SWITCH_3P:
                            if (latchedIds.contains(comp.id)) latchedIds.remove(comp.id);
                            else latchedIds.add(comp.id);
                            break;
                        default:
                            break;
                    }
                    invalidate();
                } else {
                    selectedId = comp.id;
                    if (onSelection != null) onSelection.onSelection(comp);
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                CircuitModel.CircuitComponent comp = dragId == null ? null : circuit.component(dragId);
                if (comp == null) return true;
                if (!running) {
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (Math.abs(dx) > 8f || Math.abs(dy) > 8f) moved = true;
                    if (moved) {
                        comp.x = snap(ix(event.getX()));
                        if (comp.x < 80) comp.x = 80;
                        if (comp.x > 920) comp.x = 920;
                        comp.y = snap(iy(event.getY()));
                        if (comp.y < 50) comp.y = 50;
                        if (comp.y > 750) comp.y = 750;
                        invalidate();
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (running && dragId != null) pressedIds.remove(dragId);
                dragId = null;
                invalidate();
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        handler.removeCallbacks(tick);
    }
}
