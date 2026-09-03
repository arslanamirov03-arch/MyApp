package com.arslan.stressrelief;

import android.annotation.SuppressLint;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;

/** Hosts the bomb mode and turns touches into detonation points. */
final class BombView extends GLSurfaceView {

    private final BombRenderer renderer;

    BombView(Context context) {
        super(context);
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 0, 0, 0);
        renderer = new BombRenderer(context);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        setPreserveEGLContextOnPause(true);
    }

    BombRenderer getBombRenderer() {
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
        final float y = clamp(1f - e.getY() / h, 0f, 1f);

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                queueEvent(() -> renderer.onTouch(true, false, x, y));
                break;
            case MotionEvent.ACTION_MOVE:
                queueEvent(() -> renderer.onTouch(false, false, x, y));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                queueEvent(() -> renderer.onTouch(false, true, x, y));
                break;
            default:
                break;
        }
        return true;
    }
}
