package com.abyss.silt.entities;

import android.graphics.Canvas;

import com.abyss.silt.World;
import com.abyss.silt.core.Art;
import com.abyss.silt.core.Cam;
import com.abyss.silt.core.V;

/**
 * The player's body: a slow, fragile diver. While the spirit is out possessing
 * something the body hangs in the water, inert but still killable by hazards.
 */
public class Diver extends Entity {

    public boolean possessed = true;   // true when the spirit is at home in the body
    public float spawnX, spawnY;
    public float deathT = 0f;          // dissolve animation
    private float kick = 0f;           // swim stroke phase
    private float kickCooldown = 0f;
    private float limbPhase = 0f;

    public Diver(World w, float x, float y) {
        super(w, x, y);
        r = 13f;
        spawnX = x;
        spawnY = y;
        angle = 0f;
    }

    @Override
    public float lightRadius() {
        return 190f;
    }

    @Override
    public float lightIntensity() {
        return dead ? 0f : 0.55f;
    }

    @Override
    public void update(float dt) {
        t += dt;

        if (dead) {
            deathT += dt;
            vx *= (float) Math.pow(0.2f, dt);
            vy *= (float) Math.pow(0.2f, dt);
            world.moveAndCollide(this, dt);
            return;
        }

        kickCooldown = Math.max(0f, kickCooldown - dt);
        kick = Math.max(0f, kick - dt * 1.9f);

        if (possessed) {
            float ix = world.inputX, iy = world.inputY;
            float mag = V.len(ix, iy);
            if (mag > 0.12f) {
                // Divers swim in strokes, not a steady glide.
                if (kickCooldown <= 0f) {
                    kick = 1f;
                    kickCooldown = 0.62f;
                    world.onDiverStroke(this);
                }
                float push = 520f + 620f * kick;
                vx += (ix / mag) * push * dt * Math.min(mag, 1f);
                vy += (iy / mag) * push * dt * Math.min(mag, 1f);
                angle = V.angApproach(angle, (float) Math.atan2(iy, ix), 5.5f * dt);
            }
        }

        vx *= (float) Math.pow(0.11f, dt);
        vy *= (float) Math.pow(0.11f, dt);
        vy += 26f * dt;   // very slightly negative buoyancy, so the body settles

        float sp = V.len(vx, vy);
        float cap = 210f;
        if (sp > cap) {
            vx = vx / sp * cap;
            vy = vy / sp * cap;
        }

        world.applyCurrent(this, dt);
        world.moveAndCollide(this, dt);
        limbPhase += dt * (1.6f + sp * 0.02f);
    }

    public void kill() {
        if (dead) return;
        dead = true;
        deathT = 0f;
        world.onDiverDied(this);
    }

    public void respawn(float px, float py) {
        dead = false;
        deathT = 0f;
        x = px;
        y = py;
        vx = vy = 0f;
        possessed = true;
    }

    @Override
    public void draw(Canvas c, Cam cam, Art art) {
        float s = cam.scale;
        float sx = cam.sx(x), sy = cam.sy(y);
        float vis = V.clamp(lit, 0f, 1f);
        if (dead) vis *= Math.max(0f, 1f - deathT / 1.4f);
        if (vis <= 0.02f) return;

        int ink = Art.argb(vis, 0.02f);
        // Drawn a little larger than the collision radius: the diver has to be
        // the most legible thing on screen even when it is not the brightest.
        float g = r * s * 1.35f;
        float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);

        // Legs first, so the torso overlaps them at the hip.
        float sweep = (float) Math.sin(limbPhase * 2f) * 0.42f + kick * 0.55f;
        art.stroke.setShader(null);
        art.stroke.setColor(ink);
        art.stroke.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        for (int side = -1; side <= 1; side += 2) {
            float hx = sx - ca * g * 1.15f - sa * g * 0.20f * side;
            float hy = sy - sa * g * 1.15f + ca * g * 0.20f * side;
            float la = angle + (float) Math.PI + side * (0.32f + sweep * 0.55f);
            float kx = hx + (float) Math.cos(la) * g * 0.95f;
            float ky = hy + (float) Math.sin(la) * g * 0.95f;
            art.stroke.setStrokeWidth(Math.max(2f, g * 0.30f));
            c.drawLine(hx, hy, kx, ky, art.stroke);
            art.fin(c, kx, ky, la, g * 0.95f, g * 0.34f, ink);
        }

        // Torso: a solid, unmistakable mass. This is the thing the player tracks.
        // Art.body runs the spine backwards from the nose, so the nose goes at
        // the shoulders and the taper falls away towards the hips.
        art.body(c, sx + ca * g * 0.72f, sy + sa * g * 0.72f, angle,
                10, g * 2.0f, g * 0.62f, g * 0.10f, limbPhase, 1.8f, ink);

        // Tank on the back.
        art.solid.setShader(null);
        art.solid.setColor(ink);
        c.drawCircle(sx - ca * g * 0.30f + sa * g * 0.40f,
                sy - sa * g * 0.30f - ca * g * 0.40f, g * 0.36f, art.solid);

        // Arms, tucked forward.
        for (int side = -1; side <= 1; side += 2) {
            float ax = sx + ca * g * 0.30f - sa * g * 0.42f * side;
            float ay = sy + sa * g * 0.30f + ca * g * 0.42f * side;
            float aa = angle + side * (0.72f + sweep * 0.38f);
            art.stroke.setStrokeWidth(Math.max(1.8f, g * 0.24f));
            c.drawLine(ax, ay, ax + (float) Math.cos(aa) * g * 0.92f,
                    ay + (float) Math.sin(aa) * g * 0.92f, art.stroke);
        }

        // Helmet.
        c.drawCircle(sx + ca * g * 0.98f, sy + sa * g * 0.98f, g * 0.56f, art.solid);

        // Lamp: the single bright point that says "this one is you".
        if (!dead) {
            float lampX = sx + ca * g * 1.32f - sa * g * 0.30f;
            float lampY = sy + sa * g * 1.32f + ca * g * 0.30f;
            art.lightPool(c, lampX, lampY, g * 3.2f, (possessed ? 0.5f : 0.22f) * vis);
            art.eye(c, lampX, lampY, Math.max(1.8f, g * 0.26f),
                    (possessed ? 0.97f : 0.5f) * vis, 0f);
        }

        if (dead) {
            // Silt rising from the body.
            art.solid.setColor(Art.argb(vis * 0.5f, 1f));
            for (int i = 0; i < 14; i++) {
                float u = V.hashF(i, 41);
                float rise = deathT * (30f + u * 70f) * s;
                float ox = (V.hashF(i, 77) - 0.5f) * g * 3f;
                c.drawCircle(sx + ox, sy - rise, Math.max(0.8f, g * 0.12f * (1f - u)), art.solid);
            }
        }
    }
}
