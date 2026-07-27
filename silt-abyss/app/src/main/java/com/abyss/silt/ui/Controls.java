package com.abyss.silt.ui;

import android.graphics.Canvas;
import android.view.MotionEvent;

import com.abyss.silt.core.Art;
import com.abyss.silt.core.V;

/**
 * Touch layer for phones: a floating stick under the left thumb, two action
 * buttons under the right, and a tap gesture anywhere else that means
 * "send the spirit into that creature".
 */
public class Controls {

    public static class Button {
        public float cx, cy, r;
        public String label = "";
        public boolean visible = true;
        public boolean enabled = true;
        public int pointer = -1;
        public float press = 0f;     // 0..1 press animation
        public boolean consumed = false;

        public boolean hit(float x, float y) {
            return visible && enabled && V.dist(x, y, cx, cy) < r * 1.35f;
        }
    }

    // Stick
    public boolean stickActive = false;
    public float stickOx, stickOy;       // origin where the thumb landed
    public float stickX, stickY;         // current thumb position
    public float stickRadius = 100f;
    private int stickPointer = -1;

    public final Button action = new Button();
    public final Button recall = new Button();
    public final Button pause = new Button();
    public final Button hint = new Button();

    // A tap that was not a drag and not on a button: possess request.
    public boolean tapPending = false;
    public float tapX, tapY;

    private float downTime, downX, downY;
    private int tapPointer = -1;
    private long downMs = 0;

    private int screenW, screenH;

    public void resize(int w, int h) {
        screenW = w;
        screenH = h;
        float u = Math.min(w, h);
        stickRadius = u * 0.135f;

        action.r = u * 0.108f;
        action.cx = w - u * 0.145f;
        action.cy = h - u * 0.155f;

        recall.r = u * 0.078f;
        recall.cx = w - u * 0.145f;
        recall.cy = h - u * 0.155f - action.r - recall.r - u * 0.05f;

        pause.r = u * 0.052f;
        pause.cx = u * 0.085f;
        pause.cy = u * 0.085f;

        hint.r = u * 0.058f;
        hint.cx = w - u * 0.09f;
        hint.cy = u * 0.09f;
    }

    /** Normalised stick vector, magnitude 0..1. */
    public float dirX() {
        if (!stickActive) return 0f;
        float dx = stickX - stickOx;
        float dy = stickY - stickOy;
        float l = V.len(dx, dy);
        if (l < stickRadius * 0.16f) return 0f;
        return dx / Math.max(l, 0.0001f) * Math.min(1f, l / stickRadius);
    }

    public float dirY() {
        if (!stickActive) return 0f;
        float dx = stickX - stickOx;
        float dy = stickY - stickOy;
        float l = V.len(dx, dy);
        if (l < stickRadius * 0.16f) return 0f;
        return dy / Math.max(l, 0.0001f) * Math.min(1f, l / stickRadius);
    }

    public void update(float dt) {
        action.press = V.damp(action.press, action.pointer >= 0 ? 1f : 0f, 0.001f, dt);
        recall.press = V.damp(recall.press, recall.pointer >= 0 ? 1f : 0f, 0.001f, dt);
        pause.press = V.damp(pause.press, pause.pointer >= 0 ? 1f : 0f, 0.001f, dt);
        hint.press = V.damp(hint.press, hint.pointer >= 0 ? 1f : 0f, 0.001f, dt);
    }

    /** Feeds a MotionEvent in. Returns true if it was handled. */
    public void onTouch(MotionEvent ev) {
        int act = ev.getActionMasked();
        switch (act) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int idx = ev.getActionIndex();
                int id = ev.getPointerId(idx);
                float x = ev.getX(idx), y = ev.getY(idx);
                down(id, x, y);
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < ev.getPointerCount(); i++) {
                    int id = ev.getPointerId(i);
                    float x = ev.getX(i), y = ev.getY(i);
                    if (id == stickPointer) {
                        stickX = x;
                        stickY = y;
                    }
                    if (id == tapPointer && V.dist(x, y, downX, downY) > screenH * 0.045f) {
                        tapPointer = -1;   // became a drag, no longer a tap
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                int idx = ev.getActionIndex();
                int id = ev.getPointerId(idx);
                up(id, ev.getX(idx), ev.getY(idx), act == MotionEvent.ACTION_CANCEL);
                break;
            }
        }
    }

    private void down(int id, float x, float y) {
        if (pause.hit(x, y)) { pause.pointer = id; return; }
        if (hint.hit(x, y))  { hint.pointer = id;  return; }
        if (action.hit(x, y)) {
            action.pointer = id;
            action.consumed = false;
            return;
        }
        if (recall.hit(x, y)) {
            recall.pointer = id;
            recall.consumed = false;
            return;
        }

        // Left half drives the stick, unless a stick is already live.
        if (x < screenW * 0.5f && stickPointer < 0) {
            stickPointer = id;
            stickActive = true;
            stickOx = stickX = x;
            stickOy = stickY = y;
        }

        // Any press can still turn out to be a tap.
        tapPointer = id;
        downX = x;
        downY = y;
        downMs = System.currentTimeMillis();
    }

    private void up(int id, float x, float y, boolean cancelled) {
        if (id == stickPointer) {
            stickPointer = -1;
            stickActive = false;
        }
        if (id == action.pointer) action.pointer = -1;
        if (id == recall.pointer) recall.pointer = -1;
        if (id == pause.pointer) pause.pointer = -1;
        if (id == hint.pointer) hint.pointer = -1;

        if (!cancelled && id == tapPointer) {
            long held = System.currentTimeMillis() - downMs;
            if (held < 320 && V.dist(x, y, downX, downY) < screenH * 0.045f) {
                tapPending = true;
                tapX = x;
                tapY = y;
            }
        }
        if (id == tapPointer) tapPointer = -1;
    }

    /** Consumes a queued tap, returning true once per tap. */
    public boolean takeTap() {
        if (!tapPending) return false;
        tapPending = false;
        return true;
    }

    /** Consumes a button press, returning true once per press. */
    public boolean takePress(Button b) {
        if (b.pointer >= 0 && !b.consumed) {
            b.consumed = true;
            return true;
        }
        return false;
    }

    public void releaseAll() {
        stickPointer = -1;
        stickActive = false;
        action.pointer = recall.pointer = pause.pointer = hint.pointer = -1;
        tapPointer = -1;
        tapPending = false;
    }

    // ================= drawing =================

    public void draw(Canvas c, Art art, float alpha) {
        if (stickActive) {
            float a = 0.20f * alpha;
            art.ring(c, stickOx, stickOy, stickRadius, 2.5f, Art.argb(a, 1f));
            float dx = stickX - stickOx, dy = stickY - stickOy;
            float l = V.len(dx, dy);
            if (l > stickRadius) {
                dx = dx / l * stickRadius;
                dy = dy / l * stickRadius;
            }
            art.solid.setShader(null);
            art.solid.setColor(Art.argb(0.30f * alpha, 1f));
            c.drawCircle(stickOx + dx, stickOy + dy, stickRadius * 0.32f, art.solid);
        }

        drawButton(c, art, action, alpha, true);
        drawButton(c, art, recall, alpha, false);
        drawGlyphPause(c, art, alpha);
        if (hint.visible) drawGlyphHint(c, art, alpha);
    }

    private void drawButton(Canvas c, Art art, Button b, float alpha, boolean big) {
        if (!b.visible) return;
        float a = (b.enabled ? 0.30f : 0.10f) * alpha;
        float rr = b.r * (1f + b.press * 0.09f);
        art.solid.setShader(null);
        art.solid.setColor(Art.argb(0.14f * alpha + b.press * 0.16f, 1f));
        c.drawCircle(b.cx, b.cy, rr, art.solid);
        art.ring(c, b.cx, b.cy, rr, big ? 3f : 2.4f, Art.argb(a + b.press * 0.3f, 1f));

        art.text.setColor(Art.argb((b.enabled ? 0.80f : 0.28f) * alpha, 1f));
        art.text.setTextSize(b.r * (b.label.length() > 6 ? 0.30f : 0.38f));
        art.text.setLetterSpacing(0.16f);
        c.drawText(b.label, b.cx, b.cy + b.r * 0.13f, art.text);
    }

    private void drawGlyphPause(Canvas c, Art art, float alpha) {
        if (!pause.visible) return;
        float a = 0.34f * alpha + pause.press * 0.3f;
        art.ring(c, pause.cx, pause.cy, pause.r, 2.2f, Art.argb(a * 0.7f, 1f));
        art.solid.setShader(null);
        art.solid.setColor(Art.argb(a, 1f));
        float bw = pause.r * 0.15f, bh = pause.r * 0.46f;
        c.drawRect(pause.cx - bw * 2.1f, pause.cy - bh, pause.cx - bw * 0.4f, pause.cy + bh, art.solid);
        c.drawRect(pause.cx + bw * 0.4f, pause.cy - bh, pause.cx + bw * 2.1f, pause.cy + bh, art.solid);
    }

    private void drawGlyphHint(Canvas c, Art art, float alpha) {
        float a = (hint.enabled ? 0.55f : 0.15f) * alpha + hint.press * 0.3f;
        art.ring(c, hint.cx, hint.cy, hint.r, 2.2f, Art.argb(a * 0.8f, 1f));
        art.text.setColor(Art.argb(a, 1f));
        art.text.setTextSize(hint.r * 1.05f);
        art.text.setLetterSpacing(0f);
        c.drawText("?", hint.cx, hint.cy + hint.r * 0.38f, art.text);
    }
}
