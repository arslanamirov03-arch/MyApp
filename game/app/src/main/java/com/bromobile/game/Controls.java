package com.bromobile.game;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;

/**
 * Fixed-position touch controls: an analogue stick on the left (up = jump,
 * down = crouch) and the fire / weapon-cycle / grenade cluster on the right,
 * laid out to match the reference screenshot. All geometry is expressed in
 * virtual (low-resolution) pixels so it matches the rendered frame exactly.
 */
public final class Controls {

    // Stick state
    public float moveX, moveY;
    public boolean jumpHeld, jumpPressed, crouchHeld;
    public boolean firing;
    public boolean grenadePressed;
    public boolean meleePressed;
    public boolean weaponNext, weaponPrev;
    public boolean pausePressed;

    private boolean prevJump;

    private int stickPtr = -1, firePtr = -1;

    private float sx, sy, sr, knobX, knobY;
    private float fx, fy, fr;
    private float gx, gy, gr;
    private float mx, my, mr;
    private float wx, wy, ww, wh;      // weapon box
    private float aLx, aRx, ay, ar;    // cycle arrows
    private float px, py, pr;          // pause / settings gear

    private int vw, vh;
    private float scale = 1f;

    private final Paint p = new Paint();
    private final RectF rf = new RectF();
    private final Rect src = new Rect(), dst = new Rect();

    public Controls() {
        p.setAntiAlias(true);
        p.setFilterBitmap(false);
    }

    /** Recomputes the layout for a new virtual viewport. */
    public void layout(int virtualW, int virtualH, Save save) {
        vw = virtualW;
        vh = virtualH;
        float s = 0.85f + save.padScale * 0.15f;   // 1.0 / 1.15 / 1.30
        boolean lefty = save.leftHanded;

        sr = 34 * s;
        float stickCx = 18 + sr, stickCy = vh - 16 - sr;
        float clusterCx = vw - 18 - sr;

        sx = lefty ? vw - 18 - sr : stickCx;
        sy = stickCy;
        knobX = sx;
        knobY = sy;

        float base = lefty ? stickCx : clusterCx;
        float sgn = lefty ? 1f : -1f;              // direction the cluster grows

        fr = 27 * s;
        fx = base + sgn * 0;
        fy = vh - 14 - fr;

        gr = 17 * s;
        gx = base + sgn * (fr + gr + 8);
        gy = vh - 16 - gr;

        mr = 14 * s;
        mx = base + sgn * (fr + gr * 2 + mr + 12);
        my = vh - 16 - mr;

        ww = 52 * s;
        wh = 26 * s;
        wx = base - ww / 2;
        wy = fy - fr - wh - 12;
        ar = 9 * s;
        aLx = wx - ar - 3;
        aRx = wx + ww + ar + 3;
        ay = wy + wh / 2;

        pr = 13 * s;
        px = vw - 12 - pr;
        py = 12 + pr;
    }

    public void setScale(float screenToVirtual) {
        scale = screenToVirtual;
    }

    public boolean gearHit(float vxp, float vyp) {
        return dist2(vxp, vyp, px, py) < (pr + 8) * (pr + 8);
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    /** Clears the one-shot flags; call once per simulation step. */
    public void endFrame() {
        jumpPressed = false;
        grenadePressed = false;
        meleePressed = false;
        weaponNext = false;
        weaponPrev = false;
        pausePressed = false;
    }

    public void reset() {
        stickPtr = firePtr = -1;
        moveX = moveY = 0;
        knobX = sx;
        knobY = sy;
        jumpHeld = crouchHeld = firing = false;
        prevJump = false;
        endFrame();
    }

    public void onTouch(MotionEvent e, float offX, float offY) {
        int action = e.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = e.getActionIndex();
                down(e.getPointerId(i), vX(e.getX(i), offX), vY(e.getY(i), offY));
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                for (int i = 0; i < e.getPointerCount(); i++)
                    move(e.getPointerId(i), vX(e.getX(i), offX), vY(e.getY(i), offY));
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int i = e.getActionIndex();
                up(e.getPointerId(i));
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                reset();
                break;
        }
        updateStickFlags();
    }

    private float vX(float screenX, float offX) { return (screenX - offX) / scale; }

    private float vY(float screenY, float offY) { return (screenY - offY) / scale; }

    private void down(int id, float x, float y) {
        if (dist2(x, y, px, py) < (pr + 10) * (pr + 10)) {
            pausePressed = true;
            return;
        }
        if (dist2(x, y, fx, fy) < (fr + 10) * (fr + 10)) {
            firePtr = id;
            firing = true;
            return;
        }
        if (dist2(x, y, gx, gy) < (gr + 9) * (gr + 9)) {
            grenadePressed = true;
            return;
        }
        if (dist2(x, y, mx, my) < (mr + 9) * (mr + 9)) {
            meleePressed = true;
            return;
        }
        if (dist2(x, y, aLx, ay) < (ar + 9) * (ar + 9)) {
            weaponPrev = true;
            return;
        }
        if (dist2(x, y, aRx, ay) < (ar + 9) * (ar + 9)) {
            weaponNext = true;
            return;
        }
        if (x >= wx && x <= wx + ww && y >= wy && y <= wy + wh) {
            weaponNext = true;
            return;
        }
        // Anything else in the stick's half of the screen grabs the stick.
        if (stickPtr < 0 && dist2(x, y, sx, sy) < (sr * 2.6f) * (sr * 2.6f)) {
            stickPtr = id;
            moveStick(x, y);
        }
    }

    private void move(int id, float x, float y) {
        if (id == stickPtr) moveStick(x, y);
    }

    private void up(int id) {
        if (id == stickPtr) {
            stickPtr = -1;
            moveX = moveY = 0;
            knobX = sx;
            knobY = sy;
        }
        if (id == firePtr) {
            firePtr = -1;
            firing = false;
        }
    }

    private void moveStick(float x, float y) {
        float dx = x - sx, dy = y - sy;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d > sr) {
            dx = dx / d * sr;
            dy = dy / d * sr;
            d = sr;
        }
        knobX = sx + dx;
        knobY = sy + dy;
        moveX = dx / sr;
        moveY = dy / sr;
    }

    private void updateStickFlags() {
        float ax = Math.abs(moveX), ay2 = Math.abs(moveY);
        // Vertical intent wins only when it clearly dominates, so running does
        // not accidentally trigger a jump.
        jumpHeld = moveY < -0.42f && ay2 > ax * 0.55f;
        crouchHeld = moveY > 0.46f && ay2 > ax * 0.75f;
        if (jumpHeld && !prevJump) jumpPressed = true;
        prevJump = jumpHeld;
    }

    /** Horizontal run input with a dead zone. */
    public float runAxis() {
        float v = moveX;
        if (Math.abs(v) < 0.24f) return 0;
        v = (Math.abs(v) - 0.24f) / 0.76f * Math.signum(v);
        return Math.max(-1f, Math.min(1f, v * 1.35f));
    }

    private static float dist2(float ax, float ay, float bx, float by) {
        float dx = ax - bx, dy = ay - by;
        return dx * dx + dy * dy;
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    public void draw(Canvas c, Player pl) {
        // --- stick ---
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x33000000);
        c.drawCircle(sx, sy, sr + 3, p);
        p.setColor(0x55101018);
        c.drawCircle(sx, sy, sr, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        p.setColor(0x66FFFFFF);
        c.drawCircle(sx, sy, sr, p);
        p.setStrokeWidth(1);
        p.setColor(0x33FFFFFF);
        c.drawCircle(sx, sy, sr * 0.34f, p);

        // Direction hints
        p.setStyle(Paint.Style.FILL);
        p.setColor(jumpHeld ? 0xFFFFD860 : 0x55FFFFFF);
        tri(c, sx, sy - sr + 6, 5, -1);
        p.setColor(crouchHeld ? 0xFF80D0FF : 0x55FFFFFF);
        tri(c, sx, sy + sr - 6, 5, 1);

        p.setColor(0xCC202430);
        c.drawCircle(knobX, knobY, sr * 0.46f, p);
        p.setColor(0xFF6A7488);
        c.drawCircle(knobX, knobY, sr * 0.40f, p);
        p.setColor(0xFF9AA6B8);
        c.drawCircle(knobX - sr * 0.10f, knobY - sr * 0.12f, sr * 0.20f, p);

        // --- fire ---
        boolean canFire = pl == null || pl.ammoLeft() != 0;
        p.setColor(0x44000000);
        c.drawCircle(fx, fy + 2, fr + 2, p);
        p.setColor(firing ? 0xFFE85028 : (canFire ? 0xCC8A2418 : 0x88404048));
        c.drawCircle(fx, fy, fr, p);
        p.setColor(firing ? 0xFFFFC060 : 0xAAD05030);
        c.drawCircle(fx, fy, fr - 4, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        p.setColor(0x99FFFFFF);
        c.drawCircle(fx, fy, fr, p);
        p.setStyle(Paint.Style.FILL);
        drawBulletIcon(c, fx, fy, fr * 0.55f, firing ? 0xFF2A1008 : 0xFFFFE0A0);

        // --- grenade ---
        p.setColor(0x44000000);
        c.drawCircle(gx, gy + 2, gr + 2, p);
        p.setColor(0xCC2E4420);
        c.drawCircle(gx, gy, gr, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        p.setColor(0x99FFFFFF);
        c.drawCircle(gx, gy, gr, p);
        p.setStyle(Paint.Style.FILL);
        drawGrenadeIcon(c, gx, gy, gr * 0.62f);
        if (pl != null) {
            String s = String.valueOf(pl.grenades);
            Font.shadow(c, s, (int) (gx + gr - 4), (int) (gy + gr - 8),
                    pl.grenades > 0 ? 0xFFFFE060 : 0xFFFF6050, 1);
        }

        // --- melee ---
        p.setColor(0x44000000);
        c.drawCircle(mx, my + 2, mr + 2, p);
        p.setColor(0xCC2A3040);
        c.drawCircle(mx, my, mr, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        p.setColor(0x88FFFFFF);
        c.drawCircle(mx, my, mr, p);
        p.setStyle(Paint.Style.FILL);
        drawKnifeIcon(c, mx, my, mr * 0.7f);

        // --- weapon box ---
        rf.set(wx, wy, wx + ww, wy + wh);
        p.setColor(0x66000000);
        c.drawRoundRect(rf.left, rf.top + 2, rf.right, rf.bottom + 2, 5, 5, p);
        p.setColor(0xCC3A2A18);
        c.drawRoundRect(rf, 5, 5, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        p.setColor(0xFFE0A040);
        c.drawRoundRect(rf, 5, 5, p);
        p.setStyle(Paint.Style.FILL);

        if (pl != null) {
            Bitmap icon = pl.weaponIcon();
            if (icon != null) {
                int iw = icon.getWidth(), ih = icon.getHeight();
                float k = Math.min((ww - 10) / iw, (wh - 12) / ih);
                int dw = (int) (iw * k), dh = (int) (ih * k);
                src.set(0, 0, iw, ih);
                dst.set((int) (wx + (ww - dw) / 2), (int) (wy + 2),
                        (int) (wx + (ww - dw) / 2) + dw, (int) (wy + 2) + dh);
                p.setFilterBitmap(false);
                c.drawBitmap(icon, src, dst, p);
            }
            String nm = pl.weaponName();
            Font.shadow(c, nm, (int) (wx + ww / 2 - Font.width(nm, 1) / 2),
                    (int) (wy + wh - 9), 0xFFFFD070, 1);
        }

        // --- cycle arrows ---
        p.setColor(0xAA202430);
        c.drawCircle(aLx, ay, ar, p);
        c.drawCircle(aRx, ay, ar, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.6f);
        p.setColor(0xCCE0A040);
        c.drawCircle(aLx, ay, ar, p);
        c.drawCircle(aRx, ay, ar, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFD070);
        arrow(c, aLx, ay, ar * 0.55f, -1);
        arrow(c, aRx, ay, ar * 0.55f, 1);

        // --- settings gear ---
        p.setColor(0x99202430);
        c.drawCircle(px, py, pr, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        p.setColor(0xCCFFFFFF);
        c.drawCircle(px, py, pr, p);
        p.setStyle(Paint.Style.FILL);
        drawGear(c, px, py, pr * 0.62f, 0xFFE8E4EC);
    }

    private void tri(Canvas c, float cx, float cy, float s, int dir) {
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(cx, cy + dir * s);
        path.lineTo(cx - s, cy - dir * s * 0.6f);
        path.lineTo(cx + s, cy - dir * s * 0.6f);
        path.close();
        c.drawPath(path, p);
    }

    private void arrow(Canvas c, float cx, float cy, float s, int dir) {
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(cx + dir * s, cy);
        path.lineTo(cx - dir * s * 0.7f, cy - s);
        path.lineTo(cx - dir * s * 0.7f, cy + s);
        path.close();
        c.drawPath(path, p);
    }

    private void drawBulletIcon(Canvas c, float cx, float cy, float s, int color) {
        p.setColor(color);
        c.drawRect(cx - s, cy - s * 0.34f, cx + s * 0.4f, cy + s * 0.34f, p);
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(cx + s * 0.4f, cy - s * 0.34f);
        path.lineTo(cx + s, cy);
        path.lineTo(cx + s * 0.4f, cy + s * 0.34f);
        path.close();
        c.drawPath(path, p);
    }

    private void drawGrenadeIcon(Canvas c, float cx, float cy, float s) {
        p.setColor(0xFF3A5426);
        c.drawCircle(cx, cy + s * 0.2f, s * 0.8f, p);
        p.setColor(0xFF6A8A44);
        c.drawCircle(cx - s * 0.2f, cy, s * 0.35f, p);
        p.setColor(0xFF9A9A8A);
        c.drawRect(cx - s * 0.25f, cy - s, cx + s * 0.25f, cy - s * 0.45f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.4f);
        p.setColor(0xFFC0C0B0);
        c.drawArc(cx - s * 0.1f, cy - s * 1.25f, cx + s * 0.9f, cy - s * 0.35f, -90, 200, false, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void drawKnifeIcon(Canvas c, float cx, float cy, float s) {
        p.setColor(0xFFD8DCE4);
        android.graphics.Path path = new android.graphics.Path();
        path.moveTo(cx - s * 0.7f, cy + s * 0.5f);
        path.lineTo(cx + s * 0.5f, cy - s * 0.8f);
        path.lineTo(cx + s * 0.8f, cy - s * 0.4f);
        path.lineTo(cx - s * 0.35f, cy + s * 0.8f);
        path.close();
        c.drawPath(path, p);
        p.setColor(0xFF6A4A2C);
        c.drawRect(cx - s, cy + s * 0.35f, cx - s * 0.3f, cy + s, p);
    }

    private void drawGear(Canvas c, float cx, float cy, float s, int color) {
        p.setColor(color);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            float tx = cx + (float) Math.cos(a) * s;
            float ty = cy + (float) Math.sin(a) * s;
            c.drawRect(tx - s * 0.28f, ty - s * 0.28f, tx + s * 0.28f, ty + s * 0.28f, p);
        }
        c.drawCircle(cx, cy, s * 0.72f, p);
        p.setColor(0xFF202430);
        c.drawCircle(cx, cy, s * 0.30f, p);
    }
}
