package com.arslan.stressrelief;

import android.annotation.SuppressLint;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

/** Hosts the GL fire and turns touches into simulation coordinates. */
final class FireView extends GLSurfaceView {

    private final FireRenderer renderer;

    private float lastX, lastY;
    private long lastTime;

    /** Finger drag is converted to an acceleration in simulation cells/s^2. */
    private static final float DRAG_FORCE = 1400f;
    private static final float DRAG_MAX = 4500f;

    FireView(Context context) {
        super(context);
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 0, 0, 0);
        renderer = new FireRenderer(context);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        setPreserveEGLContextOnPause(true);
    }

    FireRenderer getFireRenderer() {
        return renderer;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return true;

        final float x = clamp(e.getX() / w, 0f, 1f);
        // GL texture space has its origin at the bottom
        final float y = clamp(1f - e.getY() / h, 0f, 1f);
        final long now = System.nanoTime();

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                lastX = x;
                lastY = y;
                lastTime = now;
                queueEvent(() -> renderer.onTouch(true, false, x, y, 0f, 0f));
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                float dt = (now - lastTime) / 1e9f;
                float vx = 0f, vy = 0f;
                if (dt > 1e-4f) {
                    vx = clamp((x - lastX) / dt * DRAG_FORCE, -DRAG_MAX, DRAG_MAX);
                    vy = clamp((y - lastY) / dt * DRAG_FORCE, -DRAG_MAX, DRAG_MAX);
                }
                lastX = x;
                lastY = y;
                lastTime = now;
                final float fvx = vx, fvy = vy;
                queueEvent(() -> renderer.onTouch(false, false, x, y, fvx, fvy));
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                queueEvent(() -> renderer.onTouch(false, true, x, y, 0f, 0f));
                break;
            }
            default:
                break;
        }
        return true;
    }
}
