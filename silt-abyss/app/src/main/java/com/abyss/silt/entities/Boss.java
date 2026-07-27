package com.abyss.silt.entities;

import android.graphics.Canvas;

import com.abyss.silt.World;
import com.abyss.silt.core.Art;
import com.abyss.silt.core.Cam;
import com.abyss.silt.core.V;

/**
 * The three things that live at the bottom. All share one loop:
 * <p>
 * STALK → TELEGRAPH → STRIKE → RECOVER
 * <p>
 * The eye only opens during RECOVER, and only a RAMMER dash into it does harm.
 * Everything else about the fight is about surviving long enough to get a
 * rammer to the eye while it is open.
 */
public class Boss extends Entity {

    public static final int STALK = 0, TELEGRAPH = 1, STRIKE = 2, RECOVER = 3, DYING = 4;

    public final int id;              // 1..3
    public int hp;
    public int state = STALK;
    public float stateT = 0f;
    public float deathT = 0f;

    public final float size;          // head half-length in world units
    private final float anchorX, anchorY;
    private float jaw = 0.1f;
    private float bodyPhase = 0f;
    private float strikeAngle = 0f;
    private float hurtFlash = 0f;
    private float spitCd = 0f;
    private float orbitAng = 0f;

    /** Where the eye currently sits in world space; the only place damage lands. */
    public float eyeX, eyeY, eyeR;
    public boolean eyeOpen = false;

    public Boss(World w, float x, float y, int id) {
        super(w, x, y);
        this.id = id;
        this.anchorX = x;
        this.anchorY = y;
        switch (id) {
            case 1:  size = 150f; hp = 3; break;
            case 2:  size = 190f; hp = 4; break;
            default: size = 250f; hp = 5; break;
        }
        r = size * 0.62f;
        eyeR = size * 0.14f;
        orbitAng = V.hashF((int) x, 5) * V.TAU;
    }

    public boolean vulnerable() {
        return state == RECOVER && hp > 0;
    }

    /** Called by the world when a dash connects with the open eye. */
    public void takeHit() {
        if (!vulnerable()) return;
        hp--;
        hurtFlash = 1f;
        world.onBossHit(this);
        if (hp <= 0) {
            state = DYING;
            stateT = 0f;
            world.onBossDefeated(this);
        } else {
            // Enrage: shorter windows, faster loop.
            state = STALK;
            stateT = Math.max(0.6f, 1.5f - hp * 0.15f);
        }
    }

    /**
     * The boss always carries a wide, weak wash of light. In a dark level the
     * darkness pass only reveals what a light source touches, and a monster you
     * cannot see is a monster you cannot dodge.
     */
    @Override
    public float lightRadius() {
        return eyeOpen ? size * 3.2f : size * 2.4f;
    }

    @Override
    public float lightIntensity() {
        if (state == DYING) return Math.max(0f, 0.5f - deathT * 0.11f);
        return eyeOpen ? 0.8f : 0.42f;
    }

    @Override
    public void update(float dt) {
        t += dt;
        stateT -= dt;
        hurtFlash = Math.max(0f, hurtFlash - dt * 1.8f);
        spitCd = Math.max(0f, spitCd - dt);
        bodyPhase += dt * (state == STRIKE ? 5.5f : 1.5f);

        if (state == DYING) {
            deathT += dt;
            jaw = V.damp(jaw, 0.15f, 0.4f, dt);
            vx *= (float) Math.pow(0.4f, dt);
            vy *= (float) Math.pow(0.4f, dt);
            y += 12f * dt;                 // settles into the silt
            eyeOpen = false;
            updateEye();
            if (deathT > 4.5f) removed = true;
            return;
        }

        Entity target = world.bossTarget();
        float aggression = 1f + (id - 1) * 0.22f + (3 - hp) * 0.08f;

        switch (state) {
            case STALK: {
                eyeOpen = false;
                jaw = V.damp(jaw, 0.22f, 0.08f, dt);
                if (id == 2) {
                    // The Crawler circles its pit rather than hovering.
                    orbitAng += dt * 0.5f * aggression;
                    float rad = size * 1.5f;
                    driftTowards(anchorX + (float) Math.cos(orbitAng) * rad,
                            anchorY + (float) Math.sin(orbitAng) * rad * 0.45f, dt, 260f);
                } else {
                    float bob = (float) Math.sin(t * 0.7f) * size * 0.2f;
                    driftTowards(anchorX, anchorY + bob, dt, 150f);
                }
                if (target != null) {
                    angle = V.angApproach(angle,
                            (float) Math.atan2(target.y - y, target.x - x), 1.4f * dt);
                }
                if (id >= 2 && spitCd <= 0f && target != null) {
                    spitCd = 4.5f / aggression;
                    world.bossSpit(this, 3);
                }
                if (stateT <= 0f && target != null) {
                    state = TELEGRAPH;
                    stateT = Math.max(0.55f, 1.25f / aggression);
                    strikeAngle = (float) Math.atan2(target.y - y, target.x - x);
                    world.onBossTelegraph(this);
                }
                break;
            }

            case TELEGRAPH: {
                eyeOpen = false;
                jaw = V.damp(jaw, 1f, 0.02f, dt);
                // Recoil, and keep tracking a little so it is not trivial to dodge.
                if (target != null) {
                    strikeAngle = V.angApproach(strikeAngle,
                            (float) Math.atan2(target.y - y, target.x - x), 1.9f * dt);
                }
                angle = V.angApproach(angle, strikeAngle, 5f * dt);
                vx -= (float) Math.cos(strikeAngle) * 260f * dt;
                vy -= (float) Math.sin(strikeAngle) * 260f * dt;
                vx *= (float) Math.pow(0.2f, dt);
                vy *= (float) Math.pow(0.2f, dt);
                if (stateT <= 0f) {
                    state = STRIKE;
                    stateT = 0.85f;
                    float lunge = 1250f * aggression;
                    vx = (float) Math.cos(strikeAngle) * lunge;
                    vy = (float) Math.sin(strikeAngle) * lunge;
                    world.onBossStrike(this);
                }
                break;
            }

            case STRIKE: {
                eyeOpen = false;
                jaw = 1f;
                vx *= (float) Math.pow(0.35f, dt);
                vy *= (float) Math.pow(0.35f, dt);
                if (stateT <= 0f || hitWall) {
                    state = RECOVER;
                    // The window that the whole fight hangs on.
                    stateT = Math.max(1.5f, 2.9f - (3 - hp) * 0.3f);
                    world.onBossRecover(this);
                }
                break;
            }

            case RECOVER: {
                eyeOpen = true;
                jaw = V.damp(jaw, 0.05f, 0.05f, dt);
                vx *= (float) Math.pow(0.08f, dt);
                vy *= (float) Math.pow(0.08f, dt);
                if (stateT <= 0f) {
                    state = STALK;
                    stateT = 1.6f / aggression;
                }
                break;
            }
        }

        world.moveAndCollide(this, dt);
        updateEye();
    }

    private void driftTowards(float tx, float ty, float dt, float speed) {
        float dx = tx - x, dy = ty - y;
        float d = Math.max(V.len(dx, dy), 0.0001f);
        vx += dx / d * speed * dt * 3f;
        vy += dy / d * speed * dt * 3f;
        vx *= (float) Math.pow(0.15f, dt);
        vy *= (float) Math.pow(0.15f, dt);
    }

    private void updateEye() {
        float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);
        // Set into the skull, above the jaw line.
        eyeX = x + ca * size * 0.30f - sa * size * 0.38f;
        eyeY = y + sa * size * 0.30f + ca * size * 0.38f;
    }

    /** Contact with the head or the tooth arc is fatal. */
    public boolean bodyHits(Entity e) {
        if (state == DYING) return false;
        float rr = r + e.hitRadius();
        // The gape reaches further out than the head itself.
        float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);
        float mouthX = x + ca * size * 0.55f, mouthY = y + sa * size * 0.55f;
        float mouthR = size * (0.35f + jaw * 0.32f) + e.hitRadius();
        return V.dist2(x, y, e.x, e.y) < rr * rr
                || V.dist2(mouthX, mouthY, e.x, e.y) < mouthR * mouthR;
    }

    @Override
    public void draw(Canvas c, Cam cam, Art art) {
        float s = cam.scale;
        float sx = cam.sx(x), sy = cam.sy(y);
        float vis = V.clamp(Math.max(lit, 0.35f), 0f, 1f);   // never fully invisible
        if (state == DYING) vis *= Math.max(0f, 1f - deathT / 4.5f);
        if (vis <= 0.02f) return;

        int ink = Art.argb(vis, hurtFlash > 0.3f ? 0.35f : 0.01f);
        float S = size * s;
        float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);

        // Trailing body: a chain of shrinking segments behind the head.
        int segs = id == 2 ? 9 : 6;
        for (int i = segs; i >= 1; i--) {
            float u = i / (float) segs;
            float lag = (float) Math.sin(bodyPhase - u * 3.1f) * S * 0.35f * u;
            float bx = sx - ca * S * 1.05f * u * 1.5f - sa * lag;
            float by = sy - sa * S * 1.05f * u * 1.5f + ca * lag;
            art.solid.setShader(null);
            art.solid.setColor(Art.argb(vis * (1f - u * 0.35f), 0.01f));
            c.drawCircle(bx, by, S * (0.62f - u * 0.42f), art.solid);
        }

        // Skull.
        art.solid.setShader(null);
        art.solid.setColor(ink);
        c.drawCircle(sx, sy, S * 0.62f, art.solid);
        art.body(c, sx + ca * S * 0.2f, sy + sa * S * 0.2f, angle + (float) Math.PI,
                10, S * 1.35f, S * 0.6f, S * 0.1f, bodyPhase, 1.8f, ink);

        // Jaws: two hinged arcs of needles that gape wide on the strike.
        float open = jaw * 0.62f;
        float hingeX = sx - ca * S * 0.12f, hingeY = sy - sa * S * 0.12f;
        float reach = S * 1.05f;
        for (int side = -1; side <= 1; side += 2) {
            float ja = angle + open * side;
            float tipX = hingeX + (float) Math.cos(ja) * reach;
            float tipY = hingeY + (float) Math.sin(ja) * reach;
            // The jawbone itself.
            art.stroke.setColor(ink);
            art.stroke.setStrokeWidth(S * 0.16f);
            c.drawLine(hingeX, hingeY, tipX, tipY, art.stroke);
            art.teeth(c, hingeX, hingeY, tipX, tipY, id == 3 ? 11 : 8,
                    S * 0.3f, -side * S * 0.05f, ink);
        }

        // Gill rakes.
        art.stroke.setColor(Art.argb(vis * 0.8f, 0.06f));
        art.stroke.setStrokeWidth(S * 0.05f);
        for (int i = 0; i < 4; i++) {
            float o = S * (0.1f + i * 0.13f);
            c.drawLine(sx - ca * o - sa * S * 0.5f, sy - sa * o + ca * S * 0.5f,
                    sx - ca * o + sa * S * 0.5f, sy - sa * o - ca * S * 0.5f, art.stroke);
        }

        // The eye. Shut it is a dull slit; open it is the brightest thing on screen.
        float ex = cam.sx(eyeX), ey = cam.sy(eyeY);
        if (eyeOpen) {
            float pulse = 0.72f + 0.28f * (float) Math.sin(t * 7f);
            art.lightPool(c, ex, ey, eyeR * s * 5.5f, 0.5f * pulse);
            art.eye(c, ex, ey, eyeR * s, 0.98f, 0.34f);
            art.ring(c, ex, ey, eyeR * s * 1.9f, Math.max(1.5f, 2.2f * s),
                    Art.argb(0.5f + 0.3f * pulse, 1f));
        } else {
            art.solid.setColor(Art.argb(vis * 0.55f, 0.22f));
            c.drawCircle(ex, ey, eyeR * s * 0.65f, art.solid);
        }

        if (state == TELEGRAPH) {
            // A warning arc along the strike line.
            float warn = 1f - V.clamp(stateT / 1.25f, 0f, 1f);
            art.stroke.setColor(Art.argb(0.22f * warn, 1f));
            art.stroke.setStrokeWidth(Math.max(1.5f, 2f * s));
            c.drawLine(sx, sy, sx + (float) Math.cos(strikeAngle) * S * 6f,
                    sy + (float) Math.sin(strikeAngle) * S * 6f, art.stroke);
        }
    }
}
