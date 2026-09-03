package com.bromobile.game;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.Random;

/**
 * The tile world: storage, collision queries, destructible terrain and drawing.
 * Terrain above the bedrock line can be blown apart by explosions, which is what
 * gives the levels their Broforce-style demolition feel.
 */
public final class Level {

    public static final byte EMPTY = 0, SOLID = 1, BEDROCK = 2, PLATFORM = 3,
            CRATE = 4, SPIKE = 5, LADDER = 6, WALL = 7;

    public static final int TS = Theme.TS;

    public final int w, h;
    public final byte[] t;
    private final byte[] hp;         // remaining hits before a tile breaks

    public final Theme theme;

    public float spawnX, spawnY;
    public float exitX, exitY;
    public boolean bossLevel;
    public float bossArenaX;         // where the arena camera locks

    private final Paint p = new Paint();
    private final Random rnd = new Random();

    public Level(int w, int h, Theme theme) {
        this.w = w;
        this.h = h;
        this.theme = theme;
        t = new byte[w * h];
        hp = new byte[w * h];
        p.setAntiAlias(false);
        p.setFilterBitmap(false);
        p.setDither(false);
    }

    // ------------------------------------------------------------------
    // Access
    // ------------------------------------------------------------------

    public byte get(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= w || ty >= h) return ty >= h ? EMPTY : BEDROCK;
        return t[ty * w + tx];
    }

    public void set(int tx, int ty, byte v) {
        if (tx < 0 || ty < 0 || tx >= w || ty >= h) return;
        t[ty * w + tx] = v;
        hp[ty * w + tx] = defaultHp(v);
    }

    private static byte defaultHp(byte v) {
        switch (v) {
            case SOLID: return 3;
            case CRATE: return 1;
            case PLATFORM: return 1;
            default: return 0;
        }
    }

    public static boolean blocks(byte v) {
        return v == SOLID || v == BEDROCK || v == CRATE;
    }

    /** True if the given world point is inside blocking geometry. */
    public boolean solidAt(float wx, float wy) {
        return blocks(get((int) Math.floor(wx / TS), (int) Math.floor(wy / TS)));
    }

    public boolean spikeAt(float wx, float wy) {
        return get((int) Math.floor(wx / TS), (int) Math.floor(wy / TS)) == SPIKE;
    }

    public boolean ladderAt(float wx, float wy) {
        return get((int) Math.floor(wx / TS), (int) Math.floor(wy / TS)) == LADDER;
    }

    /** Rectangle overlap test against blocking tiles. */
    public boolean boxHits(float x, float y, float bw, float bh) {
        int x0 = (int) Math.floor(x / TS), x1 = (int) Math.floor((x + bw - 0.001f) / TS);
        int y0 = (int) Math.floor(y / TS), y1 = (int) Math.floor((y + bh - 0.001f) / TS);
        for (int ty = y0; ty <= y1; ty++)
            for (int tx = x0; tx <= x1; tx++)
                if (blocks(get(tx, ty))) return true;
        return false;
    }

    public boolean boxHitsSpike(float x, float y, float bw, float bh) {
        int x0 = (int) Math.floor(x / TS), x1 = (int) Math.floor((x + bw - 0.001f) / TS);
        int y0 = (int) Math.floor(y / TS), y1 = (int) Math.floor((y + bh - 0.001f) / TS);
        for (int ty = y0; ty <= y1; ty++)
            for (int tx = x0; tx <= x1; tx++)
                if (get(tx, ty) == SPIKE) return true;
        return false;
    }

    /**
     * One-way platform test: only blocks when the box is falling and its feet
     * are crossing the platform's top edge this step.
     */
    public boolean platformUnder(float x, float bw, float feetPrev, float feetNow) {
        if (feetNow < feetPrev) return false;
        int y0 = (int) Math.floor(feetPrev / TS);
        int y1 = (int) Math.floor(feetNow / TS);
        int x0 = (int) Math.floor(x / TS), x1 = (int) Math.floor((x + bw - 0.001f) / TS);
        for (int ty = y0; ty <= y1; ty++) {
            float top = ty * TS;
            if (feetPrev > top + 0.5f || feetNow < top) continue;
            for (int tx = x0; tx <= x1; tx++)
                if (get(tx, ty) == PLATFORM) return true;
        }
        return false;
    }

    public float platformTop(float x, float bw, float feetPrev, float feetNow) {
        int y0 = (int) Math.floor(feetPrev / TS);
        int y1 = (int) Math.floor(feetNow / TS);
        int x0 = (int) Math.floor(x / TS), x1 = (int) Math.floor((x + bw - 0.001f) / TS);
        for (int ty = y0; ty <= y1; ty++) {
            float top = ty * TS;
            if (feetPrev > top + 0.5f || feetNow < top) continue;
            for (int tx = x0; tx <= x1; tx++)
                if (get(tx, ty) == PLATFORM) return top;
        }
        return feetNow;
    }

    /** Clear line of sight between two world points (used by enemy AI). */
    public boolean lineClear(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0, dy = y1 - y0;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        int steps = (int) (dist / 6) + 1;
        for (int i = 1; i < steps; i++) {
            float k = i / (float) steps;
            if (solidAt(x0 + dx * k, y0 + dy * k)) return false;
        }
        return true;
    }

    /** True when there is ground within a short drop ahead — keeps AI off ledges. */
    public boolean groundAhead(float x, float feetY, int dir) {
        float px = x + dir * 10;
        for (int i = 0; i < 3; i++)
            if (solidAt(px, feetY + 2 + i * TS) || get((int) (px / TS), (int) ((feetY + 2) / TS)) == PLATFORM)
                return true;
        return false;
    }

    // ------------------------------------------------------------------
    // Destruction
    // ------------------------------------------------------------------

    /** Damages one tile. Returns true if it was destroyed. */
    public boolean damage(int tx, int ty, int amount, Fx fx) {
        if (tx < 0 || ty < 0 || tx >= w || ty >= h) return false;
        int i = ty * w + tx;
        byte v = t[i];
        if (v == EMPTY || v == BEDROCK || v == WALL || v == LADDER || v == SPIKE) return false;
        hp[i] -= amount;
        if (hp[i] > 0) {
            if (fx != null) fx.debris(tx * TS + 8, ty * TS + 8, 2, chipColor(v));
            return false;
        }
        t[i] = EMPTY;
        if (fx != null) {
            fx.debris(tx * TS + 8, ty * TS + 8, v == CRATE ? 9 : 6, chipColor(v));
            fx.smokePuff(tx * TS + 8, ty * TS + 8, 2, 0x88605850);
        }
        return true;
    }

    private int chipColor(byte v) {
        if (v == CRATE) return 0xFF9A7040;
        switch (theme.id) {
            case Theme.CITY: return 0xFF6E3A32;
            case Theme.SKY: return 0xFF8A7458;
            case Theme.ICE: return 0xFFA8D0E8;
            case Theme.RUINS: return 0xFF6E6A50;
            default: return 0xFF4A5460;
        }
    }

    /** Blows a roughly circular hole in destructible terrain. */
    public void blast(float wx, float wy, float radius, int power, Fx fx) {
        int r = (int) Math.ceil(radius / TS);
        int cx = (int) (wx / TS), cy = (int) (wy / TS);
        for (int ty = cy - r; ty <= cy + r; ty++)
            for (int tx = cx - r; tx <= cx + r; tx++) {
                float ddx = (tx * TS + 8) - wx, ddy = (ty * TS + 8) - wy;
                float d = (float) Math.sqrt(ddx * ddx + ddy * ddy);
                if (d > radius) continue;
                // Ragged edge: tiles near the rim survive more often.
                float chance = 1f - (d / radius) * 0.65f;
                if (rnd.nextFloat() > chance) continue;
                damage(tx, ty, power, fx);
            }
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    public void draw(Canvas c, float camX, float camY, int vw, int vh) {
        int x0 = Math.max(0, (int) (camX / TS) - 1);
        int x1 = Math.min(w - 1, (int) ((camX + vw) / TS) + 1);
        int y0 = Math.max(0, (int) (camY / TS) - 1);
        int y1 = Math.min(h - 1, (int) ((camY + vh) / TS) + 1);

        // Pass 1: background decoration behind the play field.
        for (int ty = y0; ty <= y1; ty++)
            for (int tx = x0; tx <= x1; tx++) {
                if (t[ty * w + tx] != WALL) continue;
                c.drawBitmap(theme.wallFor(tx, ty), tx * TS - camX, ty * TS - camY, p);
            }

        // Pass 2: solids, platforms, hazards.
        for (int ty = y0; ty <= y1; ty++) {
            for (int tx = x0; tx <= x1; tx++) {
                byte v = t[ty * w + tx];
                if (v == EMPTY || v == WALL) continue;
                float dx = tx * TS - camX, dy = ty * TS - camY;
                Bitmap b;
                switch (v) {
                    case SOLID: {
                        boolean top = get(tx, ty - 1) == EMPTY || get(tx, ty - 1) == WALL
                                || get(tx, ty - 1) == LADDER;
                        b = theme.solidFor(tx, ty, top);
                        break;
                    }
                    case BEDROCK: b = theme.bedrockFor(tx, ty); break;
                    case PLATFORM: b = theme.platformFor(tx); break;
                    case CRATE: b = Art.crate; break;
                    case SPIKE: b = theme.spike; break;
                    case LADDER: b = theme.ladder; break;
                    default: continue;
                }
                c.drawBitmap(b, dx, dy, p);

                // Cracks show accumulated damage.
                if ((v == SOLID || v == CRATE) && hp[ty * w + tx] < defaultHp(v)) {
                    p.setColor(0x55000000);
                    int f = defaultHp(v) - hp[ty * w + tx];
                    for (int k = 0; k < f * 3; k++) {
                        int rx = (int) (dx + ((tx * 31 + ty * 17 + k * 7) % 13) + 1);
                        int ry = (int) (dy + ((tx * 13 + ty * 29 + k * 11) % 13) + 1);
                        c.drawRect(rx, ry, rx + 2, ry + 2, p);
                    }
                }
            }
        }

        // Pass 3: contact shadow under overhangs, sells the depth cheaply.
        p.setColor(0x33000000);
        for (int ty = y0; ty <= y1; ty++)
            for (int tx = x0; tx <= x1; tx++) {
                byte v = t[ty * w + tx];
                if (v != EMPTY && v != WALL) continue;
                if (blocks(get(tx, ty - 1))) {
                    float dx = tx * TS - camX, dy = ty * TS - camY;
                    c.drawRect(dx, dy, dx + TS, dy + 4, p);
                }
            }
    }
}
