package com.bromobile.game;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;

import java.util.Random;

/**
 * Per-map look: tile artwork, parallax layers and ambient weather. Everything is
 * generated procedurally from a fixed seed at level load, so the five maps in
 * the reference art each get their own material set without shipping textures.
 */
public final class Theme {

    public static final int CITY = 0, SKY = 1, ICE = 2, RUINS = 3, FACTORY = 4;
    public static final int TS = 16;   // tile size

    private static final int BRICK = 0, ROCK = 1, ICEY = 2, BLOCK = 3, METAL = 4;

    public final int id;
    public final String name;
    public final String bossName;
    public final String subtitle;

    public int skyTop, skyBottom;
    public int fogColor;
    public int accent;            // UI / highlight colour for this map
    public int gibColor, gibColor2;

    public Bitmap[] solid = new Bitmap[4];
    public Bitmap[] solidTop = new Bitmap[4];
    public Bitmap[] bedrock = new Bitmap[2];
    public Bitmap[] platform = new Bitmap[2];
    public Bitmap[] bgwall = new Bitmap[3];
    public Bitmap ladder, spike;

    public Bitmap[] layers = new Bitmap[3];
    public final float[] layerSpeed = {0.12f, 0.30f, 0.55f};
    public final float[] layerY = {0, 0, 0};

    /** 0 none, 1 rain, 2 snow, 3 clouds, 4 embers, 5 spores. */
    public int weather;

    private Bitmap sky;
    private final Paint p = new Paint();
    private final float[] wpx = new float[220], wpy = new float[220],
            wvx = new float[220], wvy = new float[220], wsz = new float[220];
    private int wn;
    private final Random rnd = new Random();

    public Theme(int id, int viewW, int viewH) {
        this.id = id;
        switch (id) {
            case SKY:
                name = "ЛЕСТНИЦА В НЕБО";
                subtitle = "ВЫШЕ ОБЛАКОВ";
                bossName = "НЕБЕСНЫЙ АРХОНТ";
                break;
            case ICE:
                name = "ЛЕДЯНЫЕ ПЕЩЕРЫ";
                subtitle = "ЗАМЁРЗШАЯ БЕЗДНА";
                bossName = "ГЛАЦИОДОН";
                break;
            case RUINS:
                name = "ДРЕВНИЕ РУИНЫ";
                subtitle = "ЗАБЫТЫЙ ХРАМ";
                bossName = "ФАРАОН-ГОЛЕМ";
                break;
            case FACTORY:
                name = "ЗАВОД";
                subtitle = "СТАЛЬНОЕ СЕРДЦЕ";
                bossName = "ЯДРО А";
                break;
            case CITY:
            default:
                name = "ГОРОДСКОЙ";
                subtitle = "ГОРОД В ОГНЕ";
                bossName = "ТИТАН БАШНИ";
                break;
        }
        build(viewW, viewH);
    }

    // ==================================================================
    // Tiles
    // ==================================================================

    private void build(int vw, int vh) {
        Random r = new Random(4242L + id * 1013L);
        switch (id) {
            case CITY:
                skyTop = 0xFF1A1A34; skyBottom = 0xFF43304A;
                fogColor = 0x33301C2E; accent = 0xFFE07030;
                gibColor = 0xFF7A2A22; gibColor2 = 0xFF3A2A2A;
                weather = 1;
                mkSet(r, 0xFF6E3A32, 0xFF3E2018, 0xFF9A5A44, BRICK,
                        0xFF4A4A54, 0xFF2E2E38);
                break;
            case SKY:
                skyTop = 0xFF4C8AD8; skyBottom = 0xFFBFE0F4;
                fogColor = 0x22FFFFFF; accent = 0xFFFFD860;
                gibColor = 0xFFE0E8F0; gibColor2 = 0xFF9AA8C0;
                weather = 3;
                mkSet(r, 0xFF8A7458, 0xFF54432E, 0xFFB8A382, ROCK,
                        0xFF6A9A48, 0xFF4A7030);
                break;
            case ICE:
                skyTop = 0xFF20406A; skyBottom = 0xFF9EC8E4;
                fogColor = 0x33A8D8F0; accent = 0xFF8AE0FF;
                gibColor = 0xFFB8E4F8; gibColor2 = 0xFF6A9AC0;
                weather = 2;
                mkSet(r, 0xFF5C6E86, 0xFF33404F, 0xFF8AA0B8, ICEY,
                        0xFFD8F0FC, 0xFFA8D0E8);
                break;
            case RUINS:
                skyTop = 0xFF1C2A1A; skyBottom = 0xFF3E5230;
                fogColor = 0x3320301A; accent = 0xFF9AD858;
                gibColor = 0xFF7A6A48; gibColor2 = 0xFF4A4030;
                weather = 5;
                mkSet(r, 0xFF6E6A50, 0xFF3C3A2C, 0xFF98957A, BLOCK,
                        0xFF4A7A38, 0xFF2E5024);
                break;
            case FACTORY:
            default:
                skyTop = 0xFF14161C; skyBottom = 0xFF322028;
                fogColor = 0x33301818; accent = 0xFFFFA828;
                gibColor = 0xFF8A6030; gibColor2 = 0xFF4A4A52;
                weather = 4;
                mkSet(r, 0xFF4A5460, 0xFF262C34, 0xFF78848E, METAL,
                        0xFF8A5A28, 0xFF5A3A18);
                break;
        }
        buildSky(vw, vh);
        buildLayers(vw, vh);
        initWeather(vw, vh);
    }

    private void mkSet(Random r, int base, int dark, int light, int style,
                       int topA, int topB) {
        for (int i = 0; i < 4; i++) {
            solid[i] = tile(r.nextLong(), base, dark, light, style, 0, 0, 0);
            solidTop[i] = tile(r.nextLong(), base, dark, light, style, 1, topA, topB);
        }
        for (int i = 0; i < 2; i++)
            bedrock[i] = tile(r.nextLong(), mul(dark, 0.72f), mul(dark, 0.45f),
                    mul(base, 0.6f), style, 0, 0, 0);
        for (int i = 0; i < 2; i++)
            platform[i] = platformTile(r.nextLong(), base, dark, light, topA, style);
        for (int i = 0; i < 3; i++)
            bgwall[i] = tile(r.nextLong(), mul(base, 0.42f), mul(dark, 0.4f),
                    mul(light, 0.4f), style, 0, 0, 0);
        ladder = ladderTile(style == METAL ? 0xFF7A828C : 0xFF8A6A3A,
                style == METAL ? 0xFF444A54 : 0xFF4A3820);
        spike = spikeTile(style, light, dark);
    }

    private static int mul(int c, float k) {
        int a = c >>> 24;
        int r = (int) (((c >> 16) & 255) * k);
        int g = (int) (((c >> 8) & 255) * k);
        int b = (int) ((c & 255) * k);
        return (a << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
    }

    private static int jitter(Random r, int c, int amt) {
        int d = r.nextInt(amt * 2 + 1) - amt;
        int rr = clamp(((c >> 16) & 255) + d);
        int gg = clamp(((c >> 8) & 255) + d);
        int bb = clamp((c & 255) + d);
        return (c & 0xFF000000) | (rr << 16) | (gg << 8) | bb;
    }

    private static int clamp(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    private static Bitmap tile(long seed, int base, int dark, int light, int style,
                               int top, int topA, int topB) {
        Random r = new Random(seed);
        int[] px = new int[TS * TS];
        for (int i = 0; i < px.length; i++) px[i] = jitter(r, base, 9);

        switch (style) {
            case BRICK: {
                // Two staggered courses of bricks with mortar lines.
                for (int y = 0; y < TS; y++)
                    for (int x = 0; x < TS; x++) {
                        int row = y / 5;
                        int off = (row % 2) * 4;
                        boolean mortar = (y % 5 == 4) || ((x + off) % 8 == 7);
                        if (mortar) px[y * TS + x] = jitter(r, dark, 6);
                        else if (y % 5 == 0) px[y * TS + x] = jitter(r, light, 8);
                    }
                break;
            }
            case ROCK: {
                for (int i = 0; i < 14; i++) {
                    int cx = r.nextInt(TS), cy = r.nextInt(TS), rad = 1 + r.nextInt(3);
                    int c = r.nextBoolean() ? jitter(r, light, 10) : jitter(r, dark, 10);
                    blob(px, cx, cy, rad, c);
                }
                break;
            }
            case ICEY: {
                for (int y = 0; y < TS; y++)
                    for (int x = 0; x < TS; x++) {
                        int d = (x + y * 2) % 11;
                        if (d < 2) px[y * TS + x] = jitter(r, light, 12);
                        else if (d > 8) px[y * TS + x] = jitter(r, dark, 8);
                    }
                for (int i = 0; i < 5; i++)
                    blob(px, r.nextInt(TS), r.nextInt(TS), 1 + r.nextInt(2), jitter(r, light, 6));
                break;
            }
            case BLOCK: {
                for (int y = 0; y < TS; y++)
                    for (int x = 0; x < TS; x++) {
                        if (y == 0 || y == 8) px[y * TS + x] = jitter(r, light, 8);
                        if (y == 7 || y == 15 || x == 0 || x == 15)
                            px[y * TS + x] = jitter(r, dark, 6);
                    }
                for (int i = 0; i < 9; i++)   // weathering pits
                    blob(px, r.nextInt(TS), r.nextInt(TS), 1, jitter(r, dark, 12));
                break;
            }
            case METAL: {
                for (int y = 0; y < TS; y++)
                    for (int x = 0; x < TS; x++) {
                        if (x == 0 || y == 0) px[y * TS + x] = jitter(r, light, 6);
                        if (x == 15 || y == 15) px[y * TS + x] = jitter(r, dark, 6);
                    }
                int[] rv = {3, 12};
                for (int a : rv)
                    for (int b : rv) {
                        px[b * TS + a] = light;
                        px[(b + 1) * TS + a] = dark;
                        px[b * TS + a + 1] = dark;
                    }
                for (int i = 0; i < 4; i++)   // rust streaks
                    blob(px, r.nextInt(TS), r.nextInt(TS), 1, 0xFF6A4020);
                break;
            }
        }

        if (top != 0) {
            // Irregular surface crust: grass, snow, moss or scorched plating.
            int h = 3;
            for (int x = 0; x < TS; x++) {
                int hh = h + (r.nextInt(3) - 1);
                for (int y = 0; y < hh && y < TS; y++)
                    px[y * TS + x] = (y == hh - 1) ? jitter(r, topB, 8) : jitter(r, topA, 10);
                if (r.nextInt(3) == 0 && hh < TS - 1)
                    px[hh * TS + x] = jitter(r, topB, 8);
            }
            for (int x = 0; x < TS; x++) px[x] = jitter(r, topA, 6);
        }

        Bitmap b = Bitmap.createBitmap(TS, TS, Bitmap.Config.ARGB_8888);
        b.setPixels(px, 0, TS, 0, 0, TS, TS);
        return b;
    }

    private static void blob(int[] px, int cx, int cy, int rad, int color) {
        for (int y = cy - rad; y <= cy + rad; y++)
            for (int x = cx - rad; x <= cx + rad; x++) {
                if (x < 0 || y < 0 || x >= TS || y >= TS) continue;
                if ((x - cx) * (x - cx) + (y - cy) * (y - cy) > rad * rad) continue;
                px[y * TS + x] = color;
            }
    }

    private static Bitmap platformTile(long seed, int base, int dark, int light,
                                       int topA, int style) {
        Random r = new Random(seed);
        int[] px = new int[TS * TS];
        int h = 6;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < TS; x++) {
                int c = y == 0 ? light : (y >= h - 2 ? dark : base);
                if (style == METAL && y > 0 && y < h - 2 && x % 4 == 0) c = light;
                px[y * TS + x] = jitter(r, c, 7);
            }
        for (int x = 0; x < TS; x++) px[x] = jitter(r, topA, 8);
        // Support brackets hanging under the lip.
        for (int x = 2; x < TS; x += 6)
            for (int y = h; y < h + 3; y++) {
                px[y * TS + x] = jitter(r, dark, 5);
                if (x + 1 < TS) px[y * TS + x + 1] = jitter(r, dark, 5);
            }
        Bitmap b = Bitmap.createBitmap(TS, TS, Bitmap.Config.ARGB_8888);
        b.setPixels(px, 0, TS, 0, 0, TS, TS);
        return b;
    }

    private static Bitmap ladderTile(int rail, int shade) {
        int[] px = new int[TS * TS];
        for (int y = 0; y < TS; y++) {
            for (int x = 3; x <= 4; x++) px[y * TS + x] = x == 3 ? rail : shade;
            for (int x = 11; x <= 12; x++) px[y * TS + x] = x == 11 ? rail : shade;
        }
        for (int y : new int[]{2, 3, 10, 11})
            for (int x = 3; x <= 12; x++) px[y * TS + x] = y % 2 == 0 ? rail : shade;
        Bitmap b = Bitmap.createBitmap(TS, TS, Bitmap.Config.ARGB_8888);
        b.setPixels(px, 0, TS, 0, 0, TS, TS);
        return b;
    }

    private static Bitmap spikeTile(int style, int light, int dark) {
        int[] px = new int[TS * TS];
        int tip = style == ICEY ? 0xFFD8F0FC : (style == METAL ? 0xFFC0C8D0 : 0xFFB0A890);
        for (int s = 0; s < 4; s++) {
            int cx = s * 4 + 2;
            for (int y = 0; y < TS; y++) {
                int half = (y * 2) / TS;
                int w = half == 0 ? y / 4 : 2;
                for (int x = cx - w; x <= cx + w; x++) {
                    if (x < 0 || x >= TS) continue;
                    px[y * TS + x] = (x == cx - w) ? tip : (x > cx ? dark : light);
                }
            }
        }
        Bitmap b = Bitmap.createBitmap(TS, TS, Bitmap.Config.ARGB_8888);
        b.setPixels(px, 0, TS, 0, 0, TS, TS);
        return b;
    }

    // ==================================================================
    // Sky + parallax
    // ==================================================================

    private void buildSky(int vw, int vh) {
        sky = Bitmap.createBitmap(Math.max(8, vw), vh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(sky);
        Paint g = new Paint();
        g.setShader(new LinearGradient(0, 0, 0, vh, skyTop, skyBottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, vw, vh, g);
        g.setShader(null);
        Random r = new Random(77L + id);

        if (id == CITY) {
            g.setColor(0xFFF0EAD0);
            c.drawCircle(vw * 0.78f, vh * 0.17f, 13, g);      // moon
            g.setColor(0x22F0EAD0);
            c.drawCircle(vw * 0.78f, vh * 0.17f, 22, g);
            for (int i = 0; i < 70; i++) {
                g.setColor(0x66FFFFFF | (r.nextInt(0x60) << 24));
                c.drawRect(r.nextInt(vw), r.nextInt(vh / 2), r.nextInt(vw) + 1, 1, g);
                int x = r.nextInt(vw), y = r.nextInt((int) (vh * 0.55f));
                c.drawRect(x, y, x + 1, y + 1, g);
            }
        } else if (id == SKY) {
            g.setColor(0x44FFF8D0);
            c.drawCircle(vw * 0.2f, vh * 0.14f, 40, g);
            g.setColor(0x88FFF8D0);
            c.drawCircle(vw * 0.2f, vh * 0.14f, 24, g);
            g.setColor(0xFFFFFCE8);
            c.drawCircle(vw * 0.2f, vh * 0.14f, 14, g);
        } else if (id == ICE) {
            for (int i = 0; i < 40; i++) {
                g.setColor(0x55FFFFFF);
                int x = r.nextInt(vw), y = r.nextInt(vh / 2);
                c.drawRect(x, y, x + 1, y + 1, g);
            }
        } else if (id == FACTORY) {
            g.setColor(0x33FF5010);
            c.drawRect(0, vh * 0.55f, vw, vh, g);
            for (int i = 0; i < 20; i++) {
                g.setColor(0x22FFAA40);
                int x = r.nextInt(vw), y = (int) (vh * 0.5f) + r.nextInt(vh / 2);
                c.drawCircle(x, y, 2 + r.nextInt(6), g);
            }
        } else {
            g.setColor(0x33000000);
            c.drawRect(0, 0, vw, vh * 0.35f, g);
        }
    }

    private void buildLayers(int vw, int vh) {
        int lw = 512;
        for (int i = 0; i < 3; i++) {
            layers[i] = Bitmap.createBitmap(lw, vh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(layers[i]);
            Random r = new Random(313L + id * 71L + i * 17L);
            switch (id) {
                case CITY: cityLayer(c, r, i, lw, vh); break;
                case SKY: skyLayer(c, r, i, lw, vh); break;
                case ICE: iceLayer(c, r, i, lw, vh); break;
                case RUINS: ruinsLayer(c, r, i, lw, vh); break;
                default: factoryLayer(c, r, i, lw, vh); break;
            }
        }
    }

    // --- city: silhouetted skyline, lit windows, brick facade with fire escapes
    private void cityLayer(Canvas c, Random r, int layer, int w, int h) {
        Paint g = new Paint();
        if (layer == 0) {
            g.setColor(0xFF241E3A);
            int x = 0;
            while (x < w) {
                int bw = 24 + r.nextInt(40), bh = 50 + r.nextInt(90);
                c.drawRect(x, h - bh, x + bw, h, g);
                if (r.nextInt(4) == 0) {                     // spire / clock tower
                    c.drawRect(x + bw / 2 - 4, h - bh - 26, x + bw / 2 + 4, h - bh, g);
                    g.setColor(0xFF3A3050);
                    c.drawCircle(x + bw / 2, h - bh - 26, 6, g);
                    g.setColor(0xFF241E3A);
                }
                x += bw + 2 + r.nextInt(8);
            }
        } else if (layer == 1) {
            g.setColor(0xFF2E2542);
            int x = 0;
            while (x < w) {
                int bw = 40 + r.nextInt(46), bh = 70 + r.nextInt(110);
                c.drawRect(x, h - bh, x + bw, h, g);
                g.setColor(0xFF241C36);
                c.drawRect(x, h - bh, x + bw, h - bh + 3, g);
                for (int wy = h - bh + 8; wy < h - 10; wy += 12)
                    for (int wx = x + 5; wx < x + bw - 6; wx += 10) {
                        int lit = r.nextInt(10);
                        if (lit < 4) g.setColor(lit == 0 ? 0xFFFFD070 : 0xFFE8A840);
                        else g.setColor(0xFF1A1428);
                        c.drawRect(wx, wy, wx + 5, wy + 7, g);
                    }
                g.setColor(0xFF2E2542);
                x += bw + 3 + r.nextInt(10);
            }
        } else {
            g.setColor(0xFF3A2620);
            c.drawRect(0, 0, w, h, g);
            for (int y = 0; y < h; y += 6)                     // brick courses
                for (int x = (y / 6 % 2) * 6; x < w; x += 12) {
                    g.setColor(r.nextInt(6) == 0 ? 0xFF4A302A : 0xFF33211C);
                    c.drawRect(x, y, x + 11, y + 5, g);
                }
            for (int x = 20; x < w; x += 90) {                 // fire escapes
                g.setColor(0xFF1C1A20);
                c.drawRect(x, 20, x + 42, 23, g);
                c.drawRect(x, 80, x + 42, 83, g);
                c.drawRect(x, 140, x + 42, 143, g);
                for (int i = 0; i < 12; i++) {
                    c.drawRect(x + 4, 23 + i * 5, x + 6, 25 + i * 5, g);
                    c.drawRect(x + 16, 23 + i * 5, x + 18, 25 + i * 5, g);
                }
                g.setColor(0xFF262430);
                c.drawRect(x + 36, 23, x + 38, 80, g);
            }
        }
    }

    // --- sky: soft cloud banks and floating islands
    private void skyLayer(Canvas c, Random r, int layer, int w, int h) {
        Paint g = new Paint();
        g.setAntiAlias(false);
        if (layer == 2) {
            for (int i = 0; i < 7; i++) {                      // floating rocks
                int x = r.nextInt(w), y = 40 + r.nextInt(h - 90);
                int rw = 22 + r.nextInt(34);
                g.setColor(0xFF6A5A42);
                c.drawRect(x, y, x + rw, y + 10, g);
                for (int k = 0; k < 8; k++)
                    c.drawRect(x + 3 + k, y + 10 + k, x + rw - 3 - k, y + 12 + k, g);
                g.setColor(0xFF5A8A38);
                c.drawRect(x, y - 3, x + rw, y + 2, g);
                g.setColor(0xFF7AAA48);
                c.drawRect(x + 2, y - 4, x + rw - 4, y - 2, g);
            }
        }
        int n = layer == 0 ? 16 : (layer == 1 ? 11 : 7);
        int alpha = layer == 0 ? 0x55 : (layer == 1 ? 0x99 : 0xCC);
        for (int i = 0; i < n; i++) {
            int x = r.nextInt(w), y = r.nextInt(h - 40);
            int s = 12 + r.nextInt(20) + layer * 6;
            cloud(c, g, x, y, s, alpha);
        }
    }

    private void cloud(Canvas c, Paint g, int x, int y, int s, int alpha) {
        g.setColor((alpha << 24) | 0x00FFFFFF);
        c.drawRect(x, y + s / 2, x + s * 3, y + s, g);
        c.drawRect(x + s / 2, y + s / 4, x + s * 2, y + s / 2, g);
        c.drawRect(x + s, y, x + s * 2 - s / 3, y + s / 4, g);
        g.setColor(((alpha / 2) << 24) | 0x00C8D8F0);
        c.drawRect(x, y + s, x + s * 3, y + s + 3, g);
    }

    // --- ice: mountains, hanging icicles, frozen pillars
    private void iceLayer(Canvas c, Random r, int layer, int w, int h) {
        Paint g = new Paint();
        if (layer == 0) {
            g.setColor(0xFF6E8CAE);
            Path path = new Path();
            path.moveTo(0, h);
            int x = 0;
            while (x < w) {
                int pw = 40 + r.nextInt(60);
                path.lineTo(x + pw / 2f, h - 70 - r.nextInt(70));
                path.lineTo(x + pw, h - 20 - r.nextInt(30));
                x += pw;
            }
            path.lineTo(w, h);
            path.close();
            c.drawPath(path, g);
        } else if (layer == 1) {
            g.setColor(0xFF3E5872);
            c.drawRect(0, 0, w, h, g);
            g.setColor(0xFF4E6A88);
            for (int i = 0; i < 26; i++) {
                int x = r.nextInt(w), y = r.nextInt(h), s = 10 + r.nextInt(28);
                c.drawRect(x, y, x + s, y + s / 2, g);
            }
            g.setColor(0xFF9AC8E4);                            // icicles from ceiling
            for (int i = 0; i < 30; i++) {
                int x = r.nextInt(w), len = 10 + r.nextInt(34), wdt = 3 + r.nextInt(4);
                for (int k = 0; k < len; k++) {
                    int ww = Math.max(1, wdt - k * wdt / len);
                    c.drawRect(x - ww / 2, k, x + ww / 2 + 1, k + 1, g);
                }
            }
        } else {
            for (int i = 0; i < 9; i++) {                      // frozen pillars
                int x = r.nextInt(w), pw = 10 + r.nextInt(10);
                int top = 30 + r.nextInt(60), bot = h - r.nextInt(50);
                g.setColor(0xFF7EAECE);
                c.drawRect(x, top, x + pw, bot, g);
                g.setColor(0xFFB8E4F8);
                c.drawRect(x, top, x + 3, bot, g);
                g.setColor(0xFF5A88A8);
                c.drawRect(x + pw - 3, top, x + pw, bot, g);
                g.setColor(0xFFD8F0FC);
                c.drawRect(x - 2, top, x + pw + 2, top + 4, g);
                c.drawRect(x - 2, bot - 4, x + pw + 2, bot, g);
            }
        }
    }

    // --- ruins: temple silhouettes, mossy columns
    private void ruinsLayer(Canvas c, Random r, int layer, int w, int h) {
        Paint g = new Paint();
        if (layer == 0) {
            g.setColor(0xFF243020);
            int x = 0;
            while (x < w) {
                int bw = 30 + r.nextInt(50), bh = 40 + r.nextInt(70);
                c.drawRect(x, h - bh, x + bw, h, g);
                if (r.nextInt(3) == 0) {
                    Path pth = new Path();                     // stepped pyramid
                    pth.moveTo(x, h - bh);
                    pth.lineTo(x + bw / 2f, h - bh - 30);
                    pth.lineTo(x + bw, h - bh);
                    pth.close();
                    c.drawPath(pth, g);
                }
                x += bw + r.nextInt(20);
            }
        } else if (layer == 1) {
            g.setColor(0xFF32421F);
            c.drawRect(0, 0, w, h, g);
            for (int i = 0; i < 8; i++) {                      // columns
                int x = r.nextInt(w), pw = 14 + r.nextInt(8);
                int top = 10 + r.nextInt(50);
                g.setColor(0xFF5A5A44);
                c.drawRect(x, top, x + pw, h, g);
                g.setColor(0xFF74746A);
                c.drawRect(x, top, x + 4, h, g);
                g.setColor(0xFF3E3E30);
                c.drawRect(x + pw - 4, top, x + pw, h, g);
                g.setColor(0xFF7A7A6E);
                c.drawRect(x - 3, top, x + pw + 3, top + 6, g);
                g.setColor(0xFF3E6A2A);                        // vines
                for (int k = 0; k < 5; k++) {
                    int vy = top + r.nextInt(h - top - 20);
                    c.drawRect(x + r.nextInt(pw), vy, x + r.nextInt(pw) + 2, vy + 14, g);
                }
            }
        } else {
            g.setColor(0xFF3A4428);
            c.drawRect(0, 0, w, h, g);
            for (int y = 0; y < h; y += 14)
                for (int x = (y / 14 % 2) * 10; x < w; x += 20) {
                    g.setColor(r.nextInt(5) == 0 ? 0xFF4A5A32 : 0xFF303A22);
                    c.drawRect(x, y, x + 19, y + 13, g);
                }
            g.setColor(0xFF2E5A24);
            for (int i = 0; i < 60; i++) {
                int x = r.nextInt(w), y = r.nextInt(h);
                c.drawRect(x, y, x + 3 + r.nextInt(9), y + 2 + r.nextInt(5), g);
            }
        }
    }

    // --- factory: machinery, pipes, catwalks
    private void factoryLayer(Canvas c, Random r, int layer, int w, int h) {
        Paint g = new Paint();
        if (layer == 0) {
            g.setColor(0xFF1E2028);
            int x = 0;
            while (x < w) {
                int bw = 26 + r.nextInt(40), bh = 40 + r.nextInt(90);
                c.drawRect(x, h - bh, x + bw, h, g);
                if (r.nextInt(3) == 0) c.drawRect(x + 6, h - bh - 30, x + 16, h - bh, g);
                x += bw + r.nextInt(14);
            }
            g.setColor(0x33FF6020);
            for (int i = 0; i < 12; i++) {
                int x2 = r.nextInt(w), y = h - 30 - r.nextInt(60);
                c.drawRect(x2, y, x2 + 6, y + 6, g);
            }
        } else if (layer == 1) {
            g.setColor(0xFF26292F);
            c.drawRect(0, 0, w, h, g);
            for (int i = 0; i < 10; i++) {                     // tanks
                int x = r.nextInt(w), tw = 20 + r.nextInt(22), th = 40 + r.nextInt(60);
                int y = h - th - r.nextInt(30);
                g.setColor(0xFF3A4048);
                c.drawRect(x, y, x + tw, y + th, g);
                g.setColor(0xFF4E555E);
                c.drawRect(x, y, x + 4, y + th, g);
                g.setColor(0xFF20242A);
                c.drawRect(x, y, x + tw, y + 4, g);
                g.setColor(0xFF8A5A28);
                c.drawRect(x + 3, y + th / 2, x + tw - 3, y + th / 2 + 3, g);
            }
            g.setColor(0xFF44484E);                            // pipe runs
            for (int i = 0; i < 8; i++) {
                int y = r.nextInt(h);
                c.drawRect(0, y, w, y + 5, g);
                g.setColor(0xFF5A6068);
                c.drawRect(0, y, w, y + 1, g);
                g.setColor(0xFF44484E);
                for (int x = r.nextInt(30); x < w; x += 40) c.drawRect(x, y - 2, x + 4, y + 7, g);
            }
        } else {
            g.setColor(0xFF32363C);
            c.drawRect(0, 0, w, h, g);
            for (int y = 0; y < h; y += 20)                    // plating
                for (int x = 0; x < w; x += 26) {
                    g.setColor(0xFF3A3E46);
                    c.drawRect(x + 1, y + 1, x + 25, y + 19, g);
                    g.setColor(0xFF4A505A);
                    c.drawRect(x + 1, y + 1, x + 25, y + 2, g);
                    g.setColor(0xFF6A727C);
                    c.drawRect(x + 3, y + 3, x + 5, y + 5, g);
                    c.drawRect(x + 21, y + 3, x + 23, y + 5, g);
                }
            g.setColor(0xFF20242A);                            // catwalk
            for (int i = 0; i < 3; i++) {
                int y = 30 + i * 70;
                c.drawRect(0, y, w, y + 4, g);
                for (int x = 0; x < w; x += 8) c.drawRect(x, y - 8, x + 2, y, g);
            }
        }
    }

    // ==================================================================
    // Weather
    // ==================================================================

    private void initWeather(int vw, int vh) {
        int count;
        switch (weather) {
            case 1: count = 150; break;   // rain
            case 2: count = 110; break;   // snow
            case 3: count = 26; break;    // cloud wisps
            case 4: count = 60; break;    // embers
            case 5: count = 55; break;    // spores
            default: count = 0;
        }
        wn = Math.min(count, wpx.length);
        Random r = new Random(5);
        for (int i = 0; i < wn; i++) {
            wpx[i] = r.nextFloat() * (vw + 60) - 30;
            wpy[i] = r.nextFloat() * vh;
            switch (weather) {
                case 1: wvx[i] = -60; wvy[i] = 460 + r.nextFloat() * 220; wsz[i] = 4 + r.nextInt(5); break;
                case 2: wvx[i] = -12 + r.nextFloat() * 24; wvy[i] = 22 + r.nextFloat() * 30; wsz[i] = 1 + r.nextInt(2); break;
                case 3: wvx[i] = -8 - r.nextFloat() * 14; wvy[i] = 0; wsz[i] = 6 + r.nextInt(16); break;
                case 4: wvx[i] = -6 + r.nextFloat() * 16; wvy[i] = -18 - r.nextFloat() * 26; wsz[i] = 1 + r.nextInt(2); break;
                case 5: wvx[i] = -5 + r.nextFloat() * 12; wvy[i] = -6 - r.nextFloat() * 10; wsz[i] = 1 + r.nextInt(2); break;
            }
        }
    }

    public void updateWeather(float dt, int vw, int vh) {
        for (int i = 0; i < wn; i++) {
            wpx[i] += wvx[i] * dt;
            wpy[i] += wvy[i] * dt;
            if (weather == 2 || weather == 5)
                wpx[i] += (float) Math.sin((wpy[i] + i * 30) * 0.05f) * 12 * dt;
            if (wpy[i] > vh + 20) { wpy[i] = -12; wpx[i] = rnd.nextFloat() * (vw + 60) - 30; }
            if (wpy[i] < -24) { wpy[i] = vh + 10; wpx[i] = rnd.nextFloat() * (vw + 60) - 30; }
            if (wpx[i] < -40) wpx[i] = vw + 20;
            if (wpx[i] > vw + 40) wpx[i] = -20;
        }
    }

    public void drawSky(Canvas c) {
        p.setColorFilter(null);
        p.setAlpha(255);
        if (sky != null) c.drawBitmap(sky, 0, 0, p);
    }

    public void drawLayers(Canvas c, float camX, float camY, int vw, int vh) {
        for (int i = 0; i < 3; i++) {
            Bitmap b = layers[i];
            if (b == null) continue;
            float off = -(camX * layerSpeed[i]) % b.getWidth();
            if (off > 0) off -= b.getWidth();
            float yoff = -camY * layerSpeed[i] * 0.35f + layerY[i];
            p.setAlpha(255);
            for (float x = off; x < vw; x += b.getWidth())
                c.drawBitmap(b, x, yoff, p);
        }
        if ((fogColor >>> 24) != 0) {
            p.setColor(fogColor);
            c.drawRect(0, 0, vw, vh, p);
        }
    }

    public void drawWeather(Canvas c, int vw, int vh) {
        for (int i = 0; i < wn; i++) {
            switch (weather) {
                case 1:
                    p.setColor(0x66AECCE8);
                    c.drawRect(wpx[i], wpy[i], wpx[i] + 1, wpy[i] + wsz[i], p);
                    break;
                case 2:
                    p.setColor(0xCCF0F8FF);
                    c.drawRect(wpx[i], wpy[i], wpx[i] + wsz[i], wpy[i] + wsz[i], p);
                    break;
                case 3:
                    p.setColor(0x22FFFFFF);
                    c.drawRect(wpx[i], wpy[i], wpx[i] + wsz[i] * 3, wpy[i] + wsz[i], p);
                    break;
                case 4:
                    p.setColor(((int) (140 + 100 * Math.sin(wpy[i] * 0.2f)) << 24) | 0x00FF9030);
                    c.drawRect(wpx[i], wpy[i], wpx[i] + wsz[i], wpy[i] + wsz[i], p);
                    break;
                case 5:
                    p.setColor(0x66C8E890);
                    c.drawRect(wpx[i], wpy[i], wpx[i] + wsz[i], wpy[i] + wsz[i], p);
                    break;
            }
        }
    }

    public Bitmap solidFor(int tx, int ty, boolean top) {
        int h = (tx * 73856093) ^ (ty * 19349663);
        int i = Math.abs(h) & 3;
        return top ? solidTop[i] : solid[i];
    }

    public Bitmap bedrockFor(int tx, int ty) {
        return bedrock[Math.abs((tx * 31 + ty * 17)) & 1];
    }

    public Bitmap platformFor(int tx) {
        return platform[Math.abs(tx) & 1];
    }

    public Bitmap wallFor(int tx, int ty) {
        int h = (tx * 40503) ^ (ty * 12289);
        return bgwall[Math.abs(h) % 3];
    }

    public int musicTrack() {
        return id;   // Music track ids line up with map ids
    }
}
