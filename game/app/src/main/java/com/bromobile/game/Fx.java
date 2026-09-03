package com.bromobile.game;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.Random;

/**
 * Pooled particle system plus the screen-level feedback that sells a hit:
 * shake, hit-stop and full-screen flashes. Everything is drawn as small
 * axis-aligned rectangles into the low-resolution back buffer, so the output
 * stays consistent with the pixel art.
 */
public final class Fx {

    public static final int SPARK = 0, SMOKE = 1, FIRE = 2, BLOOD = 3, GIB = 4,
            DEBRIS = 5, SHELL = 6, EMBER = 7, GLASS = 8, DUST = 9, PLASMA = 10;

    private static final int MAX = 1400;
    private static final int MAX_RING = 40;
    private static final int MAX_TEXT = 24;
    private static final int MAX_DECAL = 260;

    private final float[] x = new float[MAX], y = new float[MAX];
    private final float[] vx = new float[MAX], vy = new float[MAX];
    private final float[] life = new float[MAX], full = new float[MAX];
    private final float[] size = new float[MAX], grav = new float[MAX];
    private final int[] type = new int[MAX], col = new int[MAX];
    private int n;

    // Expanding shock rings.
    private final float[] rx = new float[MAX_RING], ry = new float[MAX_RING];
    private final float[] rr = new float[MAX_RING], rmax = new float[MAX_RING];
    private final int[] rcol = new int[MAX_RING];
    private int rn;

    // Floating score / callout text.
    private final float[] tx = new float[MAX_TEXT], ty = new float[MAX_TEXT];
    private final float[] tl = new float[MAX_TEXT];
    private final int[] tcol = new int[MAX_TEXT];
    private final String[] ts = new String[MAX_TEXT];
    private int tn;

    // Persistent scorch / blood marks on the terrain.
    private final float[] dx = new float[MAX_DECAL], dy = new float[MAX_DECAL];
    private final float[] ds = new float[MAX_DECAL];
    private final int[] dcol = new int[MAX_DECAL];
    private int dn;

    public float shake, shakeDecay = 5f;
    public float hitStop;
    public float flash;
    public int flashColor = 0xFFFFFFFF;

    private final Random rnd = new Random();
    private final Paint p = new Paint();

    public Fx() {
        p.setAntiAlias(false);
        p.setFilterBitmap(false);
        p.setStyle(Paint.Style.FILL);
    }

    public void clear() {
        n = rn = tn = dn = 0;
        shake = hitStop = flash = 0;
    }

    // ------------------------------------------------------------------
    // Emitters
    // ------------------------------------------------------------------

    public void add(int t, float px, float py, float pvx, float pvy,
                    float lifeSec, float sz, int color, float gravity) {
        if (n >= MAX) {
            // Recycle the oldest slot rather than dropping the newest effect.
            n = MAX - 1;
        }
        int i = n++;
        type[i] = t;
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy;
        life[i] = full[i] = lifeSec;
        size[i] = sz; col[i] = color; grav[i] = gravity;
    }

    public void muzzle(float px, float py, float dir, float power) {
        for (int i = 0; i < 4 + power * 3; i++) {
            float a = dir * (0.15f + rnd.nextFloat() * 0.6f);
            float s = 70 + rnd.nextFloat() * 150 * power;
            add(SPARK, px, py, a * s, (rnd.nextFloat() - 0.5f) * 70,
                    0.06f + rnd.nextFloat() * 0.09f, 1, 0xFFFFD060, 30);
        }
        add(FIRE, px + dir * 2, py, dir * 24, -6, 0.07f, 3, 0xFFFFF0A0, 0);
        for (int i = 0; i < 2; i++)
            add(SMOKE, px + dir * 3, py, dir * (18 + rnd.nextFloat() * 24),
                    -12 - rnd.nextFloat() * 14, 0.35f, 2, 0x88C0BCB0, -6);
    }

    public void shell(float px, float py, float dir) {
        add(SHELL, px, py, -dir * (26 + rnd.nextFloat() * 26), -70 - rnd.nextFloat() * 40,
                1.1f, 1, 0xFFE8C040, 260);
    }

    public void explosion(float px, float py, float radius, int tint) {
        int nn = (int) (radius * 1.5f);
        for (int i = 0; i < nn; i++) {
            double a = rnd.nextFloat() * Math.PI * 2;
            float s = radius * (1.2f + rnd.nextFloat() * 2.6f);
            add(FIRE, px, py, (float) Math.cos(a) * s, (float) Math.sin(a) * s - 14,
                    0.22f + rnd.nextFloat() * 0.35f, 2 + rnd.nextFloat() * 2.5f,
                    i % 3 == 0 ? 0xFFFFF0B0 : tint, 34);
        }
        for (int i = 0; i < nn / 2; i++) {
            double a = rnd.nextFloat() * Math.PI * 2;
            float s = radius * (0.5f + rnd.nextFloat() * 1.5f);
            add(SMOKE, px, py, (float) Math.cos(a) * s, (float) Math.sin(a) * s - 26,
                    0.8f + rnd.nextFloat() * 0.9f, 3 + rnd.nextFloat() * 4,
                    0x99504A48, -14);
        }
        for (int i = 0; i < nn / 3; i++) {
            double a = rnd.nextFloat() * Math.PI * 2;
            float s = radius * (2.0f + rnd.nextFloat() * 3.0f);
            add(SPARK, px, py, (float) Math.cos(a) * s, (float) Math.sin(a) * s,
                    0.3f + rnd.nextFloat() * 0.4f, 1, 0xFFFFE070, 120);
        }
        ring(px, py, radius * 2.6f, 0xCCFFD890);
        decal(px, py, radius * 0.8f, 0x66201818);
        flash = Math.max(flash, Math.min(0.22f, radius * 0.008f));
        flashColor = 0xFFFFE0A0;
        shake = Math.max(shake, Math.min(9f, radius * 0.28f));
    }

    public void ring(float px, float py, float radius, int color) {
        if (rn >= MAX_RING) rn = MAX_RING - 1;
        int i = rn++;
        rx[i] = px; ry[i] = py; rr[i] = 1; rmax[i] = radius; rcol[i] = color;
    }

    public void blood(float px, float py, float dirx, float diry, int amount, int color) {
        for (int i = 0; i < amount; i++) {
            double a = Math.atan2(diry, dirx) + (rnd.nextFloat() - 0.5f) * 2.2;
            float s = 40 + rnd.nextFloat() * 150;
            add(BLOOD, px, py, (float) Math.cos(a) * s, (float) Math.sin(a) * s - 30,
                    0.55f + rnd.nextFloat() * 0.6f, 1 + rnd.nextFloat() * 1.6f, color, 300);
        }
    }

    public void gibs(float px, float py, int amount, int color, int color2) {
        for (int i = 0; i < amount; i++) {
            double a = rnd.nextFloat() * Math.PI * 2;
            float s = 50 + rnd.nextFloat() * 130;
            add(GIB, px, py, (float) Math.cos(a) * s, (float) Math.sin(a) * s - 90,
                    0.9f + rnd.nextFloat() * 0.7f, 2 + rnd.nextFloat() * 2,
                    (i & 1) == 0 ? color : color2, 340);
        }
    }

    public void debris(float px, float py, int amount, int color) {
        for (int i = 0; i < amount; i++) {
            double a = rnd.nextFloat() * Math.PI * 2;
            float s = 30 + rnd.nextFloat() * 120;
            add(DEBRIS, px, py, (float) Math.cos(a) * s, (float) Math.sin(a) * s - 60,
                    0.7f + rnd.nextFloat() * 0.8f, 1 + rnd.nextFloat() * 2.4f, color, 320);
        }
    }

    public void glass(float px, float py, int amount, int color) {
        for (int i = 0; i < amount; i++) {
            double a = rnd.nextFloat() * Math.PI * 2;
            float s = 40 + rnd.nextFloat() * 150;
            add(GLASS, px, py, (float) Math.cos(a) * s, (float) Math.sin(a) * s - 50,
                    0.6f + rnd.nextFloat() * 0.6f, 1 + rnd.nextFloat() * 1.5f, color, 300);
        }
    }

    public void smokePuff(float px, float py, int amount, int color) {
        for (int i = 0; i < amount; i++) {
            add(SMOKE, px + (rnd.nextFloat() - 0.5f) * 6, py + (rnd.nextFloat() - 0.5f) * 6,
                    (rnd.nextFloat() - 0.5f) * 26, -16 - rnd.nextFloat() * 22,
                    0.6f + rnd.nextFloat() * 0.7f, 2 + rnd.nextFloat() * 3, color, -12);
        }
    }

    public void sparks(float px, float py, int amount, int color) {
        for (int i = 0; i < amount; i++) {
            double a = rnd.nextFloat() * Math.PI * 2;
            float s = 40 + rnd.nextFloat() * 140;
            add(SPARK, px, py, (float) Math.cos(a) * s, (float) Math.sin(a) * s,
                    0.15f + rnd.nextFloat() * 0.3f, 1, color, 180);
        }
    }

    public void plasma(float px, float py, int amount, int color) {
        for (int i = 0; i < amount; i++) {
            double a = rnd.nextFloat() * Math.PI * 2;
            float s = 20 + rnd.nextFloat() * 80;
            add(PLASMA, px, py, (float) Math.cos(a) * s, (float) Math.sin(a) * s,
                    0.25f + rnd.nextFloat() * 0.35f, 1 + rnd.nextFloat() * 2, color, -20);
        }
    }

    public void decal(float px, float py, float sz, int color) {
        if (dn >= MAX_DECAL) {
            System.arraycopy(dx, 1, dx, 0, MAX_DECAL - 1);
            System.arraycopy(dy, 1, dy, 0, MAX_DECAL - 1);
            System.arraycopy(ds, 1, ds, 0, MAX_DECAL - 1);
            System.arraycopy(dcol, 1, dcol, 0, MAX_DECAL - 1);
            dn = MAX_DECAL - 1;
        }
        int i = dn++;
        dx[i] = px; dy[i] = py; ds[i] = sz; dcol[i] = color;
    }

    public void text(String s, float px, float py, int color) {
        if (tn >= MAX_TEXT) tn = MAX_TEXT - 1;
        int i = tn++;
        ts[i] = s; tx[i] = px; ty[i] = py; tl[i] = 1.0f; tcol[i] = color;
    }

    public void kick(float amount) {
        shake = Math.max(shake, amount);
    }

    public void stop(float seconds) {
        hitStop = Math.max(hitStop, seconds);
    }

    // ------------------------------------------------------------------
    // Simulation
    // ------------------------------------------------------------------

    public void update(float dt, Level level) {
        if (shake > 0) {
            shake -= shakeDecay * dt * (1 + shake * 0.35f);
            if (shake < 0) shake = 0;
        }
        if (flash > 0) {
            flash -= dt * 2.6f;
            if (flash < 0) flash = 0;
        }

        for (int i = 0; i < n; i++) {
            life[i] -= dt;
            if (life[i] <= 0) {
                remove(i);
                i--;
                continue;
            }
            vy[i] += grav[i] * dt;
            float nx = x[i] + vx[i] * dt;
            float ny = y[i] + vy[i] * dt;

            int t = type[i];
            if (t == GIB || t == DEBRIS || t == SHELL || t == GLASS || t == BLOOD) {
                if (level != null && level.solidAt(nx, ny)) {
                    if (t == BLOOD) {
                        decal(x[i], y[i], size[i] + 1, (col[i] & 0x00FFFFFF) | 0x77000000);
                        remove(i);
                        i--;
                        continue;
                    }
                    // Bounce with energy loss; slide along whichever axis is free.
                    if (!level.solidAt(x[i], ny)) {
                        vx[i] = -vx[i] * 0.35f;
                        nx = x[i];
                    } else if (!level.solidAt(nx, y[i])) {
                        vy[i] = -vy[i] * 0.35f;
                        vx[i] *= 0.7f;
                        ny = y[i];
                    } else {
                        vx[i] *= 0.4f;
                        vy[i] = -vy[i] * 0.3f;
                        nx = x[i];
                        ny = y[i];
                    }
                }
            } else if (t == SMOKE || t == FIRE || t == EMBER) {
                vx[i] *= (1 - 1.9f * dt);
                vy[i] *= (1 - 1.3f * dt);
            } else if (t == PLASMA) {
                vx[i] *= (1 - 2.6f * dt);
                vy[i] *= (1 - 2.6f * dt);
            } else if (t == SPARK) {
                vx[i] *= (1 - 1.1f * dt);
            }
            x[i] = nx;
            y[i] = ny;
        }

        for (int i = 0; i < rn; i++) {
            rr[i] += (rmax[i] - rr[i]) * Math.min(1f, dt * 9f) + dt * 40;
            if (rr[i] >= rmax[i] - 0.6f) {
                rn--;
                if (i != rn) {
                    rx[i] = rx[rn]; ry[i] = ry[rn]; rr[i] = rr[rn];
                    rmax[i] = rmax[rn]; rcol[i] = rcol[rn];
                }
                i--;
            }
        }

        for (int i = 0; i < tn; i++) {
            tl[i] -= dt * 1.1f;
            ty[i] -= dt * 22;
            if (tl[i] <= 0) {
                tn--;
                if (i != tn) {
                    tx[i] = tx[tn]; ty[i] = ty[tn]; tl[i] = tl[tn];
                    tcol[i] = tcol[tn]; ts[i] = ts[tn];
                }
                i--;
            }
        }
    }

    private void remove(int i) {
        n--;
        if (i == n) return;
        x[i] = x[n]; y[i] = y[n]; vx[i] = vx[n]; vy[i] = vy[n];
        life[i] = life[n]; full[i] = full[n]; size[i] = size[n];
        type[i] = type[n]; col[i] = col[n]; grav[i] = grav[n];
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    /** Scorch marks and blood, drawn under everything else. */
    public void drawDecals(Canvas c, float camX, float camY) {
        for (int i = 0; i < dn; i++) {
            float sx = dx[i] - camX, sy = dy[i] - camY;
            if (sx < -40 || sx > c.getWidth() + 40) continue;
            p.setColor(dcol[i]);
            float s = ds[i];
            c.drawRect((int) (sx - s), (int) (sy - s * 0.5f),
                    (int) (sx + s), (int) (sy + s * 0.5f), p);
        }
    }

    public void draw(Canvas c, float camX, float camY) {
        int w = c.getWidth();
        for (int i = 0; i < n; i++) {
            float sx = x[i] - camX, sy = y[i] - camY;
            if (sx < -16 || sx > w + 16) continue;
            float k = life[i] / full[i];
            int t = type[i];
            int color = col[i];
            float s = size[i];

            switch (t) {
                case FIRE: {
                    // Yellow -> orange -> red -> smoke as it dies.
                    int cc;
                    if (k > 0.72f) cc = 0xFFFFF4C0;
                    else if (k > 0.5f) cc = 0xFFFFC040;
                    else if (k > 0.28f) cc = 0xFFF06820;
                    else if (k > 0.14f) cc = 0xFF902818;
                    else cc = 0x88403830;
                    color = (k > 0.5f) ? cc : blend(cc, color, 0.35f);
                    s = size[i] * (0.5f + k * 1.1f);
                    break;
                }
                case SMOKE:
                    s = size[i] * (1.6f - k);
                    color = (color & 0x00FFFFFF) | ((int) (0x99 * k * k) << 24);
                    break;
                case SPARK:
                    color = k > 0.4f ? color : blend(color, 0xFF803010, 1 - k / 0.4f);
                    break;
                case EMBER:
                    if (((int) (life[i] * 22)) % 2 == 0) continue;
                    break;
                case PLASMA:
                    s = size[i] * (0.4f + k);
                    color = (color & 0x00FFFFFF) | ((int) (0xFF * Math.min(1, k * 1.6f)) << 24);
                    break;
                case GLASS:
                    color = (color & 0x00FFFFFF) | ((int) (0xFF * Math.min(1, k * 2f)) << 24);
                    break;
                default:
                    if (k < 0.3f) color = (color & 0x00FFFFFF) | ((int) (0xFF * (k / 0.3f)) << 24);
                    break;
            }

            p.setColor(color);
            int is = Math.max(1, (int) s);
            int ix = (int) sx, iy = (int) sy;
            c.drawRect(ix, iy, ix + is, iy + is, p);

            // Motion streak on fast sparks.
            if (t == SPARK && (vx[i] * vx[i] + vy[i] * vy[i]) > 12000) {
                p.setColor((color & 0x00FFFFFF) | 0x66000000 | (color & 0x00FFFFFF));
                p.setColor(blend(color, 0x00000000, 0.55f));
                c.drawRect(ix - (int) (vx[i] * 0.012f), iy - (int) (vy[i] * 0.012f), ix + 1, iy + 1, p);
            }
        }

        for (int i = 0; i < rn; i++) {
            float sx = rx[i] - camX, sy = ry[i] - camY;
            float k = 1 - rr[i] / rmax[i];
            int a = (int) (0xCC * k * k);
            p.setColor((rcol[i] & 0x00FFFFFF) | (a << 24));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1, 2 * k));
            c.drawCircle(sx, sy, rr[i], p);
            p.setStyle(Paint.Style.FILL);
        }

        for (int i = 0; i < tn; i++) {
            int a = (int) (0xFF * Math.min(1f, tl[i] * 1.6f));
            Font.shadow(c, ts[i], (int) (tx[i] - camX) - Font.width(ts[i], 1) / 2,
                    (int) (ty[i] - camY), (tcol[i] & 0x00FFFFFF) | (a << 24), 1);
        }
    }

    /** Full-screen additive flash, drawn last. */
    public void drawFlash(Canvas c) {
        if (flash <= 0) return;
        p.setColor((flashColor & 0x00FFFFFF) | ((int) (Math.min(1f, flash) * 150) << 24));
        c.drawRect(0, 0, c.getWidth(), c.getHeight(), p);
    }

    public float shakeX() {
        return shake <= 0 ? 0 : (rnd.nextFloat() - 0.5f) * shake * 2;
    }

    public float shakeY() {
        return shake <= 0 ? 0 : (rnd.nextFloat() - 0.5f) * shake * 2;
    }

    static int blend(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = (a >>> 24), ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = (b >>> 24), br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24)
                | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ab + (bb - ab) * t);
    }
}
