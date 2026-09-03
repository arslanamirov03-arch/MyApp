package com.bromobile.game;

import android.graphics.Canvas;

/** Shared state and tile-collision movement for every moving thing in the world. */
public abstract class Mob {

    public float x, y;          // top-left of the collision box, in world pixels
    public float vx, vy;
    public float w = 8, h = 14;
    public int face = 1;

    public boolean onGround;
    public boolean remove;
    public float hp = 1, maxHp = 1;
    public float hurtFlash;
    public boolean ignorePlatforms;
    public boolean canStepUp;

    public float cx() { return x + w / 2; }

    public float cy() { return y + h / 2; }

    public float feet() { return y + h; }

    public abstract void update(float dt, World world);

    public abstract void draw(Canvas c, float camX, float camY);

    /** Rectangle overlap against another mob's box. */
    public boolean overlaps(Mob o) {
        return x < o.x + o.w && x + w > o.x && y < o.y + o.h && y + h > o.y;
    }

    public boolean overlapsBox(float bx, float by, float bw, float bh) {
        return x < bx + bw && x + w > bx && y < by + bh && y + h > by;
    }

    /** Moves horizontally; returns true if a wall stopped the movement. */
    protected boolean moveX(Level l, float d) {
        if (d == 0) return false;
        int steps = (int) (Math.abs(d) / 3f) + 1;
        float s = d / steps;
        for (int i = 0; i < steps; i++) {
            if (l.boxHits(x + s, y, w, h)) {
                if (canStepUp && onGround) {
                    for (int up = 1; up <= 6; up++) {
                        if (!l.boxHits(x + s, y - up, w, h)) {
                            y -= up;
                            x += s;
                            break;
                        }
                        if (up == 6) return true;
                    }
                    continue;
                }
                return true;
            }
            x += s;
        }
        return false;
    }

    /** Moves vertically; returns true if the mob landed or hit a ceiling. */
    protected boolean moveY(Level l, float d, boolean usePlatforms) {
        if (d == 0) return false;
        int steps = (int) (Math.abs(d) / 3f) + 1;
        float s = d / steps;
        for (int i = 0; i < steps; i++) {
            float ny = y + s;
            if (l.boxHits(x, ny, w, h)) {
                if (s > 0) {
                    y = (float) Math.floor((ny + h) / Level.TS) * Level.TS - h;
                    onGround = true;
                } else {
                    y = (float) Math.ceil(ny / Level.TS) * Level.TS;
                }
                vy = 0;
                return true;
            }
            if (usePlatforms && s > 0 && l.platformUnder(x, w, y + h, ny + h)) {
                y = l.platformTop(x, w, y + h, ny + h) - h;
                vy = 0;
                onGround = true;
                return true;
            }
            y = ny;
        }
        return false;
    }

    /** Standard gravity + integration used by walkers. */
    protected void physics(float dt, Level l, float gravity, float maxFall) {
        vy += gravity * dt;
        if (vy > maxFall) vy = maxFall;
        boolean wasGround = onGround;
        onGround = false;
        moveX(l, vx * dt);
        moveY(l, vy * dt, !ignorePlatforms);
        if (!onGround && wasGround && vy >= 0) {
            // Grace check so walking over a seam does not read as falling.
            if (l.boxHits(x, y + 1.5f, w, h)) onGround = true;
        }
    }

    public void damage(float amount, World world, float knockX) {
        hp -= amount;
        hurtFlash = 0.09f;
        vx += knockX;
        if (hp <= 0) onKilled(world);
    }

    protected void onKilled(World world) {
        remove = true;
    }
}
