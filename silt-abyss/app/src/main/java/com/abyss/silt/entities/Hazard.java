package com.abyss.silt.entities;

import android.graphics.Canvas;

import com.abyss.silt.World;
import com.abyss.silt.core.Art;
import com.abyss.silt.core.Cam;
import com.abyss.silt.core.V;

/** Passive killers: urchin spines fixed to the rock, and drifting jellies. */
public class Hazard extends Entity {

    public static final int URCHIN = 0;
    public static final int JELLY = 1;

    public final int kind;
    private final float baseX, baseY;
    private final float driftPhase;
    private float pulse = 0f;

    /** Spat by a boss: drifts on its own velocity and expires. */
    public boolean projectile = false;
    public float life = 0f;

    public Hazard(World w, float x, float y, int kind) {
        super(w, x, y);
        this.kind = kind;
        this.baseX = x;
        this.baseY = y;
        this.driftPhase = V.hashF((int) x * 13 + (int) y, 29) * V.TAU;
        r = kind == URCHIN ? 22f : 18f;
    }

    public Hazard asProjectile(float dirX, float dirY, float speed, float seconds) {
        projectile = true;
        life = seconds;
        r = 14f;
        float l = Math.max(V.len(dirX, dirY), 0.0001f);
        vx = dirX / l * speed;
        vy = dirY / l * speed;
        return this;
    }

    @Override
    public float lightRadius() {
        return kind == JELLY ? 110f + pulse * 60f : 0f;
    }

    @Override
    public float lightIntensity() {
        return kind == JELLY ? 0.30f + pulse * 0.35f : 0f;
    }

    @Override
    public void update(float dt) {
        t += dt;
        if (projectile) {
            life -= dt;
            pulse = 0.5f + 0.5f * (float) Math.sin(t * 6f);
            vx *= (float) Math.pow(0.55f, dt);
            vy *= (float) Math.pow(0.55f, dt);
            world.moveAndCollide(this, dt);
            if (life <= 0f || hitWall) removed = true;
            return;
        }
        if (kind == JELLY) {
            pulse = 0.5f + 0.5f * (float) Math.sin(t * 1.5f + driftPhase);
            // A slow bell-driven wander around the anchor point.
            x = baseX + (float) Math.sin(t * 0.42f + driftPhase) * 62f;
            y = baseY + (float) Math.sin(t * 0.63f + driftPhase * 1.7f) * 48f
                    - pulse * 10f;
        }
    }

    @Override
    public void draw(Canvas c, Cam cam, Art art) {
        float s = cam.scale;
        float sx = cam.sx(x), sy = cam.sy(y);
        float vis = V.clamp(lit, 0f, 1f);
        if (vis <= 0.02f) return;
        int ink = Art.argb(vis, 0.015f);

        if (kind == URCHIN) {
            float rr = r * s;
            art.solid.setShader(null);
            art.solid.setColor(ink);
            c.drawCircle(sx, sy, rr * 0.5f, art.solid);
            art.stroke.setColor(ink);
            art.stroke.setStrokeWidth(Math.max(1.1f, rr * 0.09f));
            for (int i = 0; i < 18; i++) {
                float a = i / 18f * V.TAU + V.hashF(i, 3) * 0.2f;
                float l = rr * (0.85f + 0.35f * V.hashF(i, 51))
                        + (float) Math.sin(t * 1.6f + i) * rr * 0.06f;
                c.drawLine(sx + (float) Math.cos(a) * rr * 0.4f,
                        sy + (float) Math.sin(a) * rr * 0.4f,
                        sx + (float) Math.cos(a) * l, sy + (float) Math.sin(a) * l, art.stroke);
            }
        } else {
            float rr = r * s * (0.85f + pulse * 0.3f);
            // Bell.
            art.solid.setShader(null);
            art.solid.setColor(Art.argb(vis * 0.72f, 0.06f));
            c.drawArc(sx - rr, sy - rr, sx + rr, sy + rr * 0.85f, 180f, 180f, true, art.solid);
            // Trailing stingers.
            art.stroke.setColor(Art.argb(vis * 0.6f, 0.05f));
            art.stroke.setStrokeWidth(Math.max(1f, rr * 0.08f));
            for (int i = 0; i < 9; i++) {
                float ox = (i / 8f - 0.5f) * rr * 1.5f;
                float sway = (float) Math.sin(t * 2.1f + i * 0.7f + driftPhase) * rr * 0.35f;
                float len = rr * (1.6f + 0.9f * V.hashF(i, 13)) * (1f - pulse * 0.2f);
                c.drawLine(sx + ox, sy, sx + ox + sway, sy + len, art.stroke);
            }
        }
    }
}
