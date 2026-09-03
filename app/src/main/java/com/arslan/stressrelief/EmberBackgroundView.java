package com.arslan.stressrelief;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/** A slow drift of embers behind the menus. Cheap: a few dozen soft dots. */
public final class EmberBackgroundView extends View {

    private static final int COUNT = 46;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint();
    private final Random rnd = new Random(11);

    private final float[] x = new float[COUNT];
    private final float[] y = new float[COUNT];
    private final float[] vy = new float[COUNT];
    private final float[] r = new float[COUNT];
    private final float[] phase = new float[COUNT];
    private final float[] life = new float[COUNT];

    private long last;

    public EmberBackgroundView(Context c) {
        this(c, null);
    }

    public EmberBackgroundView(Context c, AttributeSet a) {
        super(c, a);
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (w == 0 || h == 0) return;
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < COUNT; i++) {
            x[i] = rnd.nextFloat() * w;
            y[i] = rnd.nextFloat() * h;
            vy[i] = (14f + rnd.nextFloat() * 34f) * d;
            r[i] = (0.7f + rnd.nextFloat() * 1.9f) * d;
            phase[i] = rnd.nextFloat() * 6.283f;
            life[i] = rnd.nextFloat();
        }
        bgPaint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xFF08070A, 0xFF0B0809, 0xFF150B06},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        glowPaint.setShader(new RadialGradient(w * 0.5f, h * 1.02f, h * 0.55f,
                new int[]{0x40FF7A1E, 0x14FF5A0A, 0x00000000},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        last = System.nanoTime();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        long now = System.nanoTime();
        float dt = Math.min((now - last) / 1e9f, 0.05f);
        last = now;

        canvas.drawRect(0, 0, w, h, bgPaint);
        canvas.drawRect(0, 0, w, h, glowPaint);

        for (int i = 0; i < COUNT; i++) {
            y[i] -= vy[i] * dt;
            phase[i] += dt * (0.8f + (i % 5) * 0.22f);
            x[i] += (float) Math.sin(phase[i]) * dt * 12f;
            life[i] -= dt * 0.12f;
            if (y[i] < -20f || life[i] <= 0f) {
                y[i] = h + rnd.nextFloat() * 40f;
                x[i] = rnd.nextFloat() * w;
                life[i] = 0.6f + rnd.nextFloat() * 0.4f;
            }
            float a = Math.min(1f, life[i] * 1.6f) * (0.35f + 0.4f * (float) Math.sin(phase[i] * 1.7f) + 0.4f);
            a = Math.max(0f, Math.min(1f, a));
            int alpha = (int) (a * 190);
            paint.setColor(Color.argb(alpha, 255, 140 + (i % 4) * 18, 40));
            canvas.drawCircle(x[i], y[i], r[i], paint);
        }
        postInvalidateOnAnimation();
    }
}
