package com.abyss.silt.core;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * Monochrome silhouette renderer. Everything the game draws is built here from
 * paths and gradients, so the APK carries no bitmap art at all.
 *
 * Palette rule: the world is a grey haze, creatures and terrain are pure black
 * cut-outs, and the only bright values in the frame are light sources.
 */
public class Art {

    // ---- shared paints (never allocate inside the render loop) ----
    public final Paint solid = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint plain = new Paint();

    private final Path tmp = new Path();
    private final RectF rect = new RectF();

    // Spine scratch buffers for creature bodies.
    private final float[] spx = new float[32];
    private final float[] spy = new float[32];
    private final float[] spw = new float[32];

    public Art() {
        solid.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        glow.setStyle(Paint.Style.FILL);
        text.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
        text.setTextAlign(Paint.Align.CENTER);
    }

    // ================= background =================

    /** Vertical depth gradient: lighter near the surface, ink at the bottom. */
    public void background(Canvas c, int w, int h, float depth01, float lightBoost) {
        // The haze never goes fully black: silhouettes only read against light.
        float top = V.lerp(0.30f, 0.15f, depth01) * (0.80f + lightBoost * 0.4f);
        float bot = V.lerp(0.12f, 0.055f, depth01) * (0.80f + lightBoost * 0.4f);
        int cTop = grey(top);
        int cBot = grey(bot);
        solid.setShader(new LinearGradient(0, 0, 0, h, cTop, cBot, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, solid);
        solid.setShader(null);
    }

    /** Slow god-rays falling from above. */
    public void lightShafts(Canvas c, int w, int h, float t, float strength) {
        if (strength <= 0.001f) return;
        solid.setShader(null);
        for (int i = 0; i < 6; i++) {
            float phase = i * 1.37f;
            float x = w * (0.08f + 0.17f * i) + V.noise1(t * 0.09f + phase, 5) * w * 0.05f;
            float wide = w * (0.035f + 0.02f * V.hashF(i, 3));
            float lean = w * 0.12f * (V.hashF(i, 9) - 0.5f);
            float a = strength * (0.05f + 0.045f * (0.5f + 0.5f * V.noise1(t * 0.13f + phase, 17)));

            tmp.reset();
            tmp.moveTo(x - wide * 0.35f, -h * 0.1f);
            tmp.lineTo(x + wide * 0.35f, -h * 0.1f);
            tmp.lineTo(x + wide + lean, h * 1.1f);
            tmp.lineTo(x - wide + lean, h * 1.1f);
            tmp.close();

            solid.setShader(new LinearGradient(0, 0, 0, h,
                    argb(a, 1f), argb(0f, 1f), Shader.TileMode.CLAMP));
            c.drawPath(tmp, solid);
        }
        solid.setShader(null);
    }

    /** Marine snow — the drifting particulate that sells "deep water". */
    public void motes(Canvas c, Cam cam, float t, int count, float lightBoost) {
        solid.setShader(null);
        float hw = cam.viewHalfW(64f), hh = cam.viewHalfH(64f);
        float spanX = hw * 2f, spanY = hh * 2f;
        for (int i = 0; i < count; i++) {
            float seedA = V.hashF(i, 101);
            float seedB = V.hashF(i, 211);
            float par = 0.35f + seedA * 0.65f;      // parallax depth
            float drift = t * (6f + seedB * 14f) * par;

            float bx = seedA * spanX;
            float by = (seedB * spanY + drift) % spanY;
            float wx = cam.x - hw + ((bx + cam.x * (1f - par) * 0.35f) % spanX + spanX) % spanX;
            float wy = cam.y - hh + by;

            float r = (0.7f + seedB * 1.9f) * par;
            float a = (0.10f + 0.30f * seedA) * par * (0.6f + lightBoost * 0.7f);
            solid.setColor(argb(a, 1f));
            c.drawCircle(cam.sx(wx), cam.sy(wy), r * cam.scale * 1.6f, solid);
        }
    }

    /** Heavy corner vignette; the frame should always fall away into black. */
    public void vignette(Canvas c, int w, int h, float amount) {
        float cx = w * 0.5f, cy = h * 0.5f;
        float rad = V.len(cx, cy);
        solid.setShader(new RadialGradient(cx, cy, rad,
                new int[]{argb(0f, 0f), argb(amount * 0.35f, 0f), argb(amount, 0f)},
                new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, solid);
        solid.setShader(null);
    }

    /** A soft pool of light around a source. */
    public void lightPool(Canvas c, float sx, float sy, float radiusPx, float intensity) {
        if (radiusPx <= 1f || intensity <= 0.002f) return;
        glow.setShader(new RadialGradient(sx, sy, radiusPx,
                new int[]{argb(intensity * 0.55f, 1f), argb(intensity * 0.16f, 1f), argb(0f, 1f)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(sx, sy, radiusPx, glow);
        glow.setShader(null);
    }

    // ================= creature silhouettes =================

    /**
     * Builds a tapered body around an undulating spine and fills it.
     *
     * @param segs   spine resolution
     * @param length body length in pixels
     * @param girth  max half-thickness in pixels
     * @param wave   undulation amplitude in pixels
     * @param phase  swim animation phase
     */
    public void body(Canvas c, float sx, float sy, float angle, int segs,
                     float length, float girth, float wave, float phase,
                     float waveFreq, int color) {
        segs = V.clampI(segs, 4, 30);
        float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);

        for (int i = 0; i < segs; i++) {
            float u = i / (float) (segs - 1);          // 0 at nose, 1 at tail
            // Local space: +x forward from nose, y lateral.
            float lx = -u * length;
            float ly = (float) Math.sin(phase + u * waveFreq) * wave * (0.15f + u * u * 1.25f);
            // Fish taper: fat just behind the head, pinched at the tail.
            float tw = girth * (float) Math.sin(Math.pow(1f - u, 0.62f) * Math.PI * 0.92f + 0.13f);
            spx[i] = sx + lx * ca - ly * sa;
            spy[i] = sy + lx * sa + ly * ca;
            spw[i] = Math.max(tw, 0.6f);
        }
        outline(c, segs, color);
    }

    /** Closes a smooth outline around the current spine buffers and fills it. */
    private void outline(Canvas c, int segs, int color) {
        tmp.reset();
        // Walk down one side...
        for (int i = 0; i < segs; i++) {
            float nx, ny;
            if (i == 0) {
                nx = spx[1] - spx[0];
                ny = spy[1] - spy[0];
            } else {
                nx = spx[i] - spx[i - 1];
                ny = spy[i] - spy[i - 1];
            }
            float l = Math.max(V.len(nx, ny), 0.0001f);
            float px = -ny / l, py = nx / l;
            float x = spx[i] + px * spw[i], y = spy[i] + py * spw[i];
            if (i == 0) tmp.moveTo(x, y);
            else tmp.lineTo(x, y);
        }
        // ...and back up the other.
        for (int i = segs - 1; i >= 0; i--) {
            float nx, ny;
            if (i == 0) {
                nx = spx[1] - spx[0];
                ny = spy[1] - spy[0];
            } else {
                nx = spx[i] - spx[i - 1];
                ny = spy[i] - spy[i - 1];
            }
            float l = Math.max(V.len(nx, ny), 0.0001f);
            float px = ny / l, py = -nx / l;
            tmp.lineTo(spx[i] + px * spw[i], spy[i] + py * spw[i]);
        }
        tmp.close();
        solid.setShader(null);
        solid.setColor(color);
        c.drawPath(tmp, solid);
    }

    /** A single tapered fin/flipper blade. */
    public void fin(Canvas c, float sx, float sy, float angle, float len, float wide, int color) {
        float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);
        tmp.reset();
        tmp.moveTo(sx, sy);
        tmp.quadTo(sx + (len * 0.5f * ca - wide * sa), sy + (len * 0.5f * sa + wide * ca),
                sx + len * ca, sy + len * sa);
        tmp.quadTo(sx + (len * 0.55f * ca + wide * 0.25f * sa),
                sy + (len * 0.55f * sa - wide * 0.25f * ca), sx, sy);
        tmp.close();
        solid.setShader(null);
        solid.setColor(color);
        c.drawPath(tmp, solid);
    }

    /** A row of needle teeth along a line — the SILT signature. */
    public void teeth(Canvas c, float ax, float ay, float bx, float by,
                      int count, float lengthPx, float sidePx, int color) {
        float dx = bx - ax, dy = by - ay;
        float l = Math.max(V.len(dx, dy), 0.0001f);
        float ux = dx / l, uy = dy / l;
        float nx = -uy * Math.signum(sidePx), ny = ux * Math.signum(sidePx);
        float abs = Math.abs(sidePx);
        solid.setShader(null);
        solid.setColor(color);
        for (int i = 0; i < count; i++) {
            float t0 = (i + 0.12f) / count, t1 = (i + 0.88f) / count;
            float mx = (t0 + t1) * 0.5f;
            float jitter = 0.62f + 0.38f * V.hashF(i, 71);
            float tipLen = lengthPx * jitter * (float) Math.sin(mx * Math.PI) * 1.15f + lengthPx * 0.25f;
            tmp.reset();
            tmp.moveTo(ax + ux * l * t0, ay + uy * l * t0);
            tmp.lineTo(ax + ux * l * t1, ay + uy * l * t1);
            tmp.lineTo(ax + ux * l * mx + nx * (tipLen + abs), ay + uy * l * mx + ny * (tipLen + abs));
            tmp.close();
            c.drawPath(tmp, solid);
        }
    }

    /** Eye: a bright disc with a dark pupil, the only high value on a creature. */
    public void eye(Canvas c, float sx, float sy, float r, float brightness, float pupil) {
        solid.setShader(null);
        solid.setColor(argb(brightness, 1f));
        c.drawCircle(sx, sy, r, solid);
        if (pupil > 0.02f) {
            solid.setColor(argb(0.92f, 0f));
            c.drawCircle(sx, sy, r * pupil, solid);
        }
    }

    /** Kelp / weed frond swaying from an anchor point. */
    public void frond(Canvas c, float sx, float sy, float len, float sway,
                      float thickness, int segs, int color) {
        stroke.setShader(null);
        stroke.setColor(color);
        tmp.reset();
        tmp.moveTo(sx, sy);
        float x = sx, y = sy;
        for (int i = 1; i <= segs; i++) {
            float u = i / (float) segs;
            float bend = sway * u * u;
            x = sx + bend;
            y = sy - len * u;
            tmp.lineTo(x, y);
        }
        stroke.setStrokeWidth(thickness);
        c.drawPath(tmp, stroke);
    }

    /** Jagged rock edge along a horizontal or vertical span. */
    public void jagged(Canvas c, float x0, float y0, float x1, float y1,
                       float amp, int seed, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        float l = Math.max(V.len(dx, dy), 0.0001f);
        int steps = V.clampI((int) (l / 9f), 3, 22);
        float ux = dx / l, uy = dy / l;
        float nx = -uy, ny = ux;
        stroke.setShader(null);
        stroke.setColor(color);
        tmp.reset();
        tmp.moveTo(x0, y0);
        for (int i = 1; i <= steps; i++) {
            float u = i / (float) steps;
            float o = (V.hashF(i * 31 + seed, seed) - 0.35f) * amp;
            tmp.lineTo(x0 + ux * l * u + nx * o, y0 + uy * l * u + ny * o);
        }
        tmp.lineTo(x1, y1);
        c.drawPath(tmp, stroke);
    }

    // ================= helpers =================

    public void roundRect(Canvas c, float l, float t, float r, float b, float rad, int color) {
        rect.set(l, t, r, b);
        solid.setShader(null);
        solid.setColor(color);
        c.drawRoundRect(rect, rad, rad, solid);
    }

    public void ring(Canvas c, float sx, float sy, float r, float widthPx, int color) {
        stroke.setShader(null);
        stroke.setColor(color);
        stroke.setStrokeWidth(widthPx);
        c.drawCircle(sx, sy, r, stroke);
    }

    /** Greyscale colour from a 0..1 luminance. */
    public static int grey(float v) {
        int g = V.clampI((int) (V.clamp(v, 0f, 1f) * 255f), 0, 255);
        return Color.rgb(g, g, g);
    }

    /** Colour with alpha a (0..1) and luminance lum (0..1). */
    public static int argb(float a, float lum) {
        int al = V.clampI((int) (V.clamp(a, 0f, 1f) * 255f), 0, 255);
        int g = V.clampI((int) (V.clamp(lum, 0f, 1f) * 255f), 0, 255);
        return Color.argb(al, g, g, g);
    }
}
