package com.abyss.silt.entities;

import android.graphics.Canvas;

import com.abyss.silt.World;
import com.abyss.silt.core.Art;
import com.abyss.silt.core.Cam;
import com.abyss.silt.core.V;

/**
 * A hunter. It patrols its home water until something edible enters its senses,
 * then commits to a lunge. Armoured crawlers, parasites and inflated puffers
 * are not on the menu, which is what makes the possession puzzles work.
 */
public class Predator extends Entity {

    private static final int PATROL = 0, HUNT = 1, STUNNED = 2, FLEE = 3;

    private int state = PATROL;
    private float stateT = 0f;
    private final float homeX, homeY;
    private float wanderAng;
    private float wanderT = 0f;
    private float swimPhase = 0f;
    private float lungeCd = 0f;
    private float jaw = 0f;          // 0 closed, 1 gaping

    public float senseRange = 300f;
    public float leash = 460f;

    public Predator(World w, float x, float y) {
        super(w, x, y);
        r = 26f;
        homeX = x;
        homeY = y;
        wanderAng = V.hashF((int) x + (int) y * 17, 3) * V.TAU;
    }

    public boolean stunned() {
        return state == STUNNED;
    }

    public void stun(float seconds) {
        state = STUNNED;
        stateT = Math.max(stateT, seconds);
        vx *= 0.2f;
        vy *= 0.2f;
    }

    public void scare(float seconds) {
        if (state == STUNNED) return;
        state = FLEE;
        stateT = Math.max(stateT, seconds);
    }

    public void shove(float dx, float dy, float force) {
        float l = Math.max(V.len(dx, dy), 0.0001f);
        vx += dx / l * force;
        vy += dy / l * force;
    }

    @Override
    public void update(float dt) {
        t += dt;
        stateT -= dt;
        lungeCd = Math.max(0f, lungeCd - dt);

        Entity prey = world.preyFor(this);

        switch (state) {
            case STUNNED:
                jaw = V.damp(jaw, 0f, 0.02f, dt);
                vx *= (float) Math.pow(0.05f, dt);
                vy *= (float) Math.pow(0.05f, dt);
                if (stateT <= 0f) state = PATROL;
                break;

            case FLEE: {
                jaw = V.damp(jaw, 0f, 0.02f, dt);
                float ax = x - homeX, ay = y - homeY;
                if (prey != null) {
                    ax = x - prey.x;
                    ay = y - prey.y;
                }
                float l = Math.max(V.len(ax, ay), 0.0001f);
                vx += ax / l * 900f * dt;
                vy += ay / l * 900f * dt;
                if (stateT <= 0f) state = PATROL;
                break;
            }

            case HUNT: {
                if (prey == null || V.dist(x, y, homeX, homeY) > leash) {
                    state = PATROL;
                    stateT = 1.4f;
                    break;
                }
                float dx = prey.x - x, dy = prey.y - y;
                float d = Math.max(V.len(dx, dy), 0.0001f);
                jaw = V.damp(jaw, d < 150f ? 1f : 0.35f, 0.02f, dt);
                float drive = d < 190f && lungeCd <= 0f ? 1750f : 900f;
                if (d < 190f && lungeCd <= 0f) {
                    lungeCd = 1.5f;
                    world.onPredatorLunge(this);
                }
                vx += dx / d * drive * dt;
                vy += dy / d * drive * dt;
                break;
            }

            default: {
                jaw = V.damp(jaw, 0.12f + 0.1f * (float) Math.sin(t * 0.9f), 0.05f, dt);
                wanderT -= dt;
                if (wanderT <= 0f) {
                    wanderT = 1.6f + V.hashF((int) (t * 11f), 19) * 2.2f;
                    float dx = homeX - x, dy = homeY - y;
                    if (V.len(dx, dy) > leash * 0.55f) {
                        wanderAng = (float) Math.atan2(dy, dx);
                    } else {
                        wanderAng += (V.hashF((int) (t * 5f), 61) - 0.5f) * 1.8f;
                    }
                }
                vx += (float) Math.cos(wanderAng) * 330f * dt;
                vy += (float) Math.sin(wanderAng) * 330f * dt;

                if (prey != null && V.dist(x, y, prey.x, prey.y) < senseRange
                        && world.lineOfSight(x, y, prey.x, prey.y)) {
                    state = HUNT;
                    world.onPredatorAlert(this);
                }
                break;
            }
        }

        vx *= (float) Math.pow(0.10f, dt);
        vy *= (float) Math.pow(0.10f, dt);
        float sp = V.len(vx, vy);
        float cap = state == HUNT ? 330f : (state == FLEE ? 300f : 130f);
        if (sp > cap) {
            vx = vx / sp * cap;
            vy = vy / sp * cap;
        }

        world.moveAndCollide(this, dt);
        if (hitWall) wanderAng += 2.4f;
        swimPhase += dt * (2.2f + sp * 0.02f);
        faceVelocity(dt, 4.5f);
    }

    @Override
    public void draw(Canvas c, Cam cam, Art art) {
        float s = cam.scale;
        float sx = cam.sx(x), sy = cam.sy(y);
        float vis = V.clamp(lit, 0f, 1f);
        if (vis <= 0.02f) return;

        int ink = Art.argb(vis, 0.015f);
        float len = 96f * s, g = 27f * s;

        art.body(c, sx, sy, angle, 16, len, g, g * 0.85f, swimPhase, 3.6f, ink);

        float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);
        // Dorsal and tail.
        art.fin(c, sx - ca * len * 0.42f - sa * g * 0.8f,
                sy - sa * len * 0.42f + ca * g * 0.8f,
                angle - 1.9f, len * 0.34f, g * 0.7f, ink);
        art.fin(c, sx - ca * len, sy - sa * len, angle + (float) Math.PI,
                len * 0.45f, g * 2.2f, ink);
        art.fin(c, sx - ca * len * 0.2f - sa * g * 0.9f,
                sy - sa * len * 0.2f + ca * g * 0.9f,
                angle + 1.1f, len * 0.28f, g * 0.5f, ink);

        // The gape: two rows of needles hinged at the jaw.
        float open = jaw * 0.5f;
        float mx = sx + ca * len * 0.06f, my = sy + sa * len * 0.06f;
        float upA = angle - open, loA = angle + open;
        art.teeth(c, mx, my, mx + (float) Math.cos(upA) * len * 0.32f,
                my + (float) Math.sin(upA) * len * 0.32f, 7, g * 0.5f, g * 0.12f, ink);
        art.teeth(c, mx, my, mx + (float) Math.cos(loA) * len * 0.32f,
                my + (float) Math.sin(loA) * len * 0.32f, 7, g * 0.5f, -g * 0.12f, ink);

        // Eye: bright when hunting, dulled when stunned.
        float bright = state == HUNT ? 0.95f : (state == STUNNED ? 0.2f : 0.6f);
        art.eye(c, sx + ca * len * 0.14f - sa * g * 0.45f,
                sy + sa * len * 0.14f + ca * g * 0.45f,
                Math.max(1.3f, g * 0.2f), bright * vis, 0.4f);

        if (state == STUNNED) {
            art.solid.setShader(null);
            art.solid.setColor(Art.argb(vis * 0.45f, 1f));
            for (int i = 0; i < 4; i++) {
                float a = t * 4.5f + i * V.TAU / 4f;
                c.drawCircle(sx + (float) Math.cos(a) * g * 2.1f,
                        sy + (float) Math.sin(a) * g * 2.1f - g * 1.6f,
                        Math.max(1f, 1.8f * s), art.solid);
            }
        }
    }
}
