package com.abyss.silt.entities;

import android.graphics.Canvas;

import com.abyss.silt.World;
import com.abyss.silt.core.Art;
import com.abyss.silt.core.Cam;
import com.abyss.silt.core.V;
import com.abyss.silt.level.LevelDef;

/**
 * A possessable creature. One class covers all seven species; the type id
 * selects stats, silhouette and the ability fired by the action button.
 */
public class Critter extends Entity {

    public final int type;
    public boolean possessed = false;

    public final float homeX, homeY;
    private float respawnT = 0f;
    private float fadeIn = 1f;

    // Ability state
    public float dashT = 0f;        // RAMMER: seconds of dash remaining
    public float cooldown = 0f;
    public float inflate = 0f;      // PUFFER: 0..1
    public boolean wantInflate = false;
    public float charge = 0f;       // SPARK: discharge flash
    public float biteT = 0f;        // BITER: jaw animation
    public float stompT = 0f;       // HEAVY
    public float flashT = 0f;       // GLOWER
    public Entity latched = null;   // LEECH host
    private float latchOffX, latchOffY;

    // Motion / animation
    private float swimPhase = 0f;
    private float wanderAng;
    private float wanderT = 0f;
    private float startleT = 0f;

    // Per-type stats
    private final float accel, maxSpeed, bodyLen, girth;

    public Critter(World w, float x, float y, int type) {
        super(w, x, y);
        this.type = type;
        this.homeX = x;
        this.homeY = y;
        this.wanderAng = V.hashF((int) x * 31 + (int) y, 7) * V.TAU;

        switch (type) {
            case LevelDef.BITER:
                r = 9f;  accel = 1500f; maxSpeed = 300f; bodyLen = 30f; girth = 7f; break;
            case LevelDef.RAMMER:
                r = 12f; accel = 1150f; maxSpeed = 250f; bodyLen = 52f; girth = 11f; break;
            case LevelDef.GLOWER:
                r = 13f; accel = 900f;  maxSpeed = 185f; bodyLen = 34f; girth = 13f; break;
            case LevelDef.SPARK:
                r = 9f;  accel = 1150f; maxSpeed = 240f; bodyLen = 62f; girth = 6f;  break;
            case LevelDef.HEAVY:
                r = 16f; accel = 1500f; maxSpeed = 175f; bodyLen = 36f; girth = 16f; break;
            case LevelDef.PUFFER:
                r = 13f; accel = 950f;  maxSpeed = 195f; bodyLen = 30f; girth = 13f; break;
            case LevelDef.LEECH:
            default:
                r = 8f;  accel = 1300f; maxSpeed = 265f; bodyLen = 24f; girth = 7f;  break;
        }
    }

    public boolean isHeavy() {
        return type == LevelDef.HEAVY;
    }

    /** Predators ignore armoured crawlers and parasites. */
    public boolean ignoredByPredators() {
        return type == LevelDef.HEAVY || type == LevelDef.LEECH
                || (type == LevelDef.PUFFER && inflate > 0.5f);
    }

    @Override
    public float hitRadius() {
        return type == LevelDef.PUFFER ? r * (1f + inflate * 1.15f) : r;
    }

    @Override
    public float lightRadius() {
        if (type != LevelDef.GLOWER) return charge > 0.01f ? 130f * charge : 0f;
        return 250f + flashT * 420f;
    }

    @Override
    public float lightIntensity() {
        if (type == LevelDef.GLOWER) return dead ? 0f : 0.85f + flashT * 0.6f;
        if (charge > 0.01f) return charge * 0.9f;
        return 0f;
    }

    // ================= simulation =================

    @Override
    public void update(float dt) {
        t += dt;

        if (dead) {
            respawnT -= dt;
            if (respawnT <= 0f) revive();
            return;
        }
        if (fadeIn < 1f) fadeIn = Math.min(1f, fadeIn + dt * 1.6f);

        cooldown = Math.max(0f, cooldown - dt);
        dashT = Math.max(0f, dashT - dt);
        biteT = Math.max(0f, biteT - dt);
        stompT = Math.max(0f, stompT - dt);
        flashT = Math.max(0f, flashT - dt * 1.4f);
        charge = Math.max(0f, charge - dt * 2.2f);
        startleT = Math.max(0f, startleT - dt);

        inflate = V.damp(inflate, wantInflate ? 1f : 0f, 0.02f, dt);

        if (latched != null) {
            updateLatched(dt);
            return;
        }

        if (possessed) controlled(dt);
        else wander(dt);

        applyPhysics(dt);
    }

    private void controlled(float dt) {
        float ix = world.inputX, iy = world.inputY;
        float mag = V.len(ix, iy);

        if (isHeavy()) {
            // A bottom walker: horizontal drive only, and only with footing.
            float drive = Math.abs(ix) > 0.15f ? ix : 0f;
            if (onGround) vx += drive * accel * dt;
            else vx += drive * accel * 0.25f * dt;
            if (drive != 0f) angle = drive > 0 ? 0f : (float) Math.PI;
        } else if (dashT > 0f) {
            // Dash locks the heading; steering during it would trivialise aiming.
            float sp = 620f;
            vx = (float) Math.cos(angle) * sp;
            vy = (float) Math.sin(angle) * sp;
        } else if (mag > 0.12f) {
            vx += (ix / mag) * accel * dt * Math.min(mag, 1f);
            vy += (iy / mag) * accel * dt * Math.min(mag, 1f);
            angle = V.angApproach(angle, (float) Math.atan2(iy, ix), 9f * dt);
        }
    }

    private void wander(float dt) {
        wanderT -= dt;
        if (wanderT <= 0f) {
            wanderT = 1.1f + V.hashF((int) (t * 13f) + (int) homeX, 23) * 2.4f;
            // Drift around the spawn point, and turn back if we stray too far.
            float dx = homeX - x, dy = homeY - y;
            float d = V.len(dx, dy);
            if (d > 150f) {
                wanderAng = (float) Math.atan2(dy, dx) + (V.hashF((int) t, 5) - 0.5f) * 1.2f;
            } else {
                wanderAng += (V.hashF((int) (t * 7f), 31) - 0.5f) * 2.6f;
            }
        }
        if (hitWall) wanderAng += 2.1f * dt * 30f * dt;

        float sp = startleT > 0f ? 1.9f : 0.32f;
        vx += (float) Math.cos(wanderAng) * accel * 0.5f * sp * dt;
        vy += (float) Math.sin(wanderAng) * accel * 0.5f * sp * dt;
        faceVelocity(dt, 6f);
    }

    private void updateLatched(float dt) {
        if (latched.dead || latched.removed) {
            latched = null;
            return;
        }
        float ca = (float) Math.cos(latched.angle), sa = (float) Math.sin(latched.angle);
        x = latched.x + latchOffX * ca - latchOffY * sa;
        y = latched.y + latchOffX * sa + latchOffY * ca;
        vx = latched.vx;
        vy = latched.vy;
        angle = latched.angle + (float) Math.PI * 0.5f;
        swimPhase += dt * 4f;
    }

    private void applyPhysics(float dt) {
        // Water resistance, plus buoyancy and gravity where the species calls for it.
        float drag = dashT > 0f ? 0.55f : 0.06f;
        if (isHeavy()) {
            vy += 900f * dt;                       // sinks, walks the seabed
            vx *= (float) Math.pow(onGround ? 0.0009f : 0.25f, dt);
        } else {
            vx *= (float) Math.pow(drag, dt);
            vy *= (float) Math.pow(drag, dt);
            if (type == LevelDef.PUFFER && inflate > 0.05f) {
                vy -= 340f * inflate * dt;         // inflated: rises
            }
        }

        float sp = V.len(vx, vy);
        float cap = maxSpeed * (dashT > 0f ? 3.2f : 1f) * (inflate > 0.5f ? 0.7f : 1f);
        if (sp > cap) {
            vx = vx / sp * cap;
            vy = vy / sp * cap;
        }

        // Currents push everything that is not wedged open.
        if (!(type == LevelDef.PUFFER && inflate > 0.6f) && !isHeavy()) {
            world.applyCurrent(this, dt);
        }

        world.moveAndCollide(this, dt);

        swimPhase += dt * (3.2f + V.len(vx, vy) * 0.035f);
        if (!isHeavy() && !possessed) faceVelocity(dt, 5f);
    }

    // ================= abilities =================

    /** Fired by the action button. Returns true if something happened. */
    public boolean useAbility() {
        if (dead || cooldown > 0f) return false;
        switch (type) {
            case LevelDef.BITER:
                biteT = 0.28f;
                cooldown = 0.34f;
                world.onBite(this);
                return true;
            case LevelDef.RAMMER:
                dashT = 0.34f;
                cooldown = 0.85f;
                world.onDash(this);
                return true;
            case LevelDef.GLOWER:
                flashT = 1f;
                cooldown = 2.6f;
                world.onFlash(this);
                return true;
            case LevelDef.SPARK:
                charge = 1f;
                cooldown = 1.5f;
                world.onDischarge(this);
                return true;
            case LevelDef.HEAVY:
                stompT = 0.45f;
                cooldown = 0.7f;
                world.onStomp(this);
                return true;
            case LevelDef.PUFFER:
                wantInflate = !wantInflate;
                cooldown = 0.45f;
                return true;
            case LevelDef.LEECH:
                if (latched != null) {
                    latched = null;
                    cooldown = 0.6f;
                    // Kick clear so we do not instantly re-latch.
                    vx -= (float) Math.cos(angle) * 90f;
                    vy -= (float) Math.sin(angle) * 90f;
                    return true;
                }
                Entity host = world.findLatchHost(this);
                if (host != null) {
                    latched = host;
                    float ca = (float) Math.cos(-host.angle), sa = (float) Math.sin(-host.angle);
                    float dx = x - host.x, dy = y - host.y;
                    latchOffX = dx * ca - dy * sa;
                    latchOffY = dx * sa + dy * ca;
                    cooldown = 0.5f;
                    return true;
                }
                return false;
        }
        return false;
    }

    /** Short label for the action button. */
    public String abilityName() {
        switch (type) {
            case LevelDef.BITER:  return "BITE";
            case LevelDef.RAMMER: return "DASH";
            case LevelDef.GLOWER: return "FLARE";
            case LevelDef.SPARK:  return "SHOCK";
            case LevelDef.HEAVY:  return "STOMP";
            case LevelDef.PUFFER: return wantInflate ? "DEFLATE" : "INFLATE";
            case LevelDef.LEECH:  return latched != null ? "LET GO" : "LATCH";
        }
        return "";
    }

    public void startle() {
        startleT = 1.2f;
    }

    public void kill() {
        if (dead) return;
        dead = true;
        possessed = false;
        latched = null;
        wantInflate = false;
        inflate = 0f;
        respawnT = 3.2f;
        world.onCritterDied(this);
    }

    private void revive() {
        dead = false;
        x = homeX;
        y = homeY;
        vx = vy = 0f;
        fadeIn = 0f;
        cooldown = 0f;
    }

    public float alpha() {
        return dead ? 0f : fadeIn;
    }

    // ================= rendering =================

    @Override
    public void draw(Canvas c, Cam cam, Art art) {
        if (dead) return;
        float s = cam.scale;
        float sx = cam.sx(x), sy = cam.sy(y);
        float a = alpha();

        // In darkness a creature is only as visible as the light reaching it.
        float vis = a * V.clamp(lit, 0f, 1f);
        if (vis <= 0.02f) return;

        // Silhouettes are black; when they are the only thing in a lit pool we
        // lift them slightly so they read against the rock behind.
        int ink = Art.argb(vis, 0.02f);
        int fin = Art.argb(vis * 0.82f, 0.03f);

        switch (type) {
            case LevelDef.BITER:  drawBiter(c, art, sx, sy, s, vis, ink, fin); break;
            case LevelDef.RAMMER: drawRammer(c, art, sx, sy, s, vis, ink, fin); break;
            case LevelDef.GLOWER: drawGlower(c, art, sx, sy, s, vis, ink, fin); break;
            case LevelDef.SPARK:  drawSpark(c, art, sx, sy, s, vis, ink, fin); break;
            case LevelDef.HEAVY:  drawHeavy(c, art, sx, sy, s, vis, ink, fin); break;
            case LevelDef.PUFFER: drawPuffer(c, art, sx, sy, s, vis, ink, fin); break;
            default:              drawLeech(c, art, sx, sy, s, vis, ink, fin); break;
        }

        if (possessed) drawPossessionMark(c, art, sx, sy, s, vis);
    }

    private void drawBiter(Canvas c, Art art, float sx, float sy, float s,
                           float vis, int ink, int finC) {
        float len = bodyLen * s, g = girth * s;
        art.body(c, sx, sy, angle, 12, len, g, g * 0.7f, swimPhase, 4.2f, ink);
        art.fin(c, sx - (float) Math.cos(angle) * len, sy - (float) Math.sin(angle) * len,
                angle + (float) Math.PI, len * 0.42f, g * 1.5f, finC);
        // Jaw snaps open on bite.
        float open = biteT / 0.28f;
        float jaw = angle + open * 0.55f;
        art.teeth(c, sx, sy - g * 0.15f,
                sx + (float) Math.cos(jaw) * len * 0.34f,
                sy + (float) Math.sin(jaw) * len * 0.34f,
                4, g * 0.45f, -g * 0.1f, ink);
        eyeAt(c, art, sx, sy, s, vis, 0.24f, -0.32f);
    }

    private void drawRammer(Canvas c, Art art, float sx, float sy, float s,
                            float vis, int ink, int finC) {
        float len = bodyLen * s, g = girth * s;
        float stretch = 1f + dashT * 0.9f;
        art.body(c, sx, sy, angle, 14, len * stretch, g / stretch,
                g * 0.5f, swimPhase, 3.4f, ink);
        // Bill.
        float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);
        art.stroke.setStrokeWidth(g * 0.32f);
        art.jagged(c, sx, sy, sx + ca * len * 0.55f, sy + sa * len * 0.55f, g * 0.1f, 3, ink);
        art.fin(c, sx - ca * len * 0.35f, sy - sa * len * 0.35f,
                angle - 1.9f, len * 0.4f, g * 0.9f, finC);
        art.fin(c, sx - ca * len * stretch, sy - sa * len * stretch,
                angle + (float) Math.PI, len * 0.5f, g * 1.9f, finC);
        eyeAt(c, art, sx, sy, s, vis, 0.2f, -0.4f);
        if (dashT > 0f) {
            art.ring(c, sx, sy, len * 0.7f, 2f * s, Art.argb(vis * dashT * 0.5f, 1f));
        }
    }

    private void drawGlower(Canvas c, Art art, float sx, float sy, float s,
                            float vis, int ink, int finC) {
        float len = bodyLen * s, g = girth * s;
        // The lantern is drawn by the light pass; here we do the body only.
        art.body(c, sx, sy, angle, 12, len, g, g * 0.4f, swimPhase, 3.0f, ink);
        float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);
        // Gaping jaw with the long teeth this game is named for.
        // Jaw hinged just behind the nose, gaping a little way past it.
        art.teeth(c, sx - ca * len * 0.22f, sy - sa * len * 0.22f,
                sx + ca * len * 0.16f, sy + sa * len * 0.16f,
                6, g * 0.7f, g * 0.34f, ink);
        art.teeth(c, sx - ca * len * 0.22f, sy - sa * len * 0.22f,
                sx + ca * len * 0.16f, sy + sa * len * 0.16f,
                6, g * 0.58f, -g * 0.2f, ink);
        // Illicium: the stalk carrying the lure.
        float bob = (float) Math.sin(swimPhase * 0.8f) * 0.22f;
        float stalkA = angle - 1.35f + bob;
        float lx = sx + (float) Math.cos(stalkA) * len * 0.72f;
        float ly = sy + (float) Math.sin(stalkA) * len * 0.72f;
        art.stroke.setStrokeWidth(Math.max(1.2f, g * 0.12f));
        art.stroke.setColor(ink);
        c.drawLine(sx + ca * len * 0.12f, sy + sa * len * 0.12f, lx, ly, art.stroke);
        art.fin(c, sx - ca * len, sy - sa * len, angle + (float) Math.PI,
                len * 0.45f, g * 1.2f, finC);
        eyeAt(c, art, sx, sy, s, vis, 0.22f, -0.38f);
    }

    private void drawSpark(Canvas c, Art art, float sx, float sy, float s,
                           float vis, int ink, int finC) {
        float len = bodyLen * s, g = girth * s;
        art.body(c, sx, sy, angle, 20, len, g, g * 2.6f, swimPhase, 8.5f, ink);
        eyeAt(c, art, sx, sy, s, vis, 0.16f, -0.3f);
        if (charge > 0.01f) {
            // Arcs crawling along the body.
            art.stroke.setStrokeWidth(Math.max(1f, 1.6f * s));
            art.stroke.setColor(Art.argb(vis * charge, 1f));
            for (int i = 0; i < 5; i++) {
                float u0 = i / 5f, u1 = (i + 1) / 5f;
                float a0 = angle + (float) Math.PI;
                float n = V.noise1(t * 40f + i, 13) * g * 3.5f * charge;
                float x0 = sx + (float) Math.cos(a0) * len * u0;
                float y0 = sy + (float) Math.sin(a0) * len * u0;
                float x1 = sx + (float) Math.cos(a0) * len * u1 - (float) Math.sin(a0) * n;
                float y1 = sy + (float) Math.sin(a0) * len * u1 + (float) Math.cos(a0) * n;
                c.drawLine(x0, y0, x1, y1, art.stroke);
            }
        }
    }

    private void drawHeavy(Canvas c, Art art, float sx, float sy, float s,
                           float vis, int ink, int finC) {
        float g = girth * s;
        // Low armoured carapace.
        art.roundRect(c, sx - g * 1.25f, sy - g * 0.72f, sx + g * 1.25f, sy + g * 0.85f,
                g * 0.5f, ink);
        float step = (float) Math.sin(swimPhase * 1.6f);
        float squat = stompT > 0f ? g * 0.35f : 0f;
        art.stroke.setColor(ink);
        art.stroke.setStrokeWidth(Math.max(1.4f, g * 0.2f));
        for (int i = -2; i <= 2; i++) {
            if (i == 0) continue;
            float lx = sx + i * g * 0.5f;
            float phase = step * (i % 2 == 0 ? 1f : -1f) * g * 0.3f;
            c.drawLine(lx, sy + g * 0.6f, lx + phase, sy + g * 1.35f - squat, art.stroke);
        }
        // Claw.
        float dir = Math.cos(angle) >= 0 ? 1f : -1f;
        art.fin(c, sx + dir * g * 1.1f, sy - g * 0.2f, dir > 0 ? -0.5f : (float) Math.PI + 0.5f,
                g * 1.1f, g * 0.55f, ink);
        eyeAt(c, art, sx + dir * g * 0.55f, sy - g * 0.42f, s, vis, 0f, 0f);
    }

    private void drawPuffer(Canvas c, Art art, float sx, float sy, float s,
                            float vis, int ink, int finC) {
        float rr = hitRadius() * s;
        art.solid.setShader(null);
        art.solid.setColor(ink);
        c.drawCircle(sx, sy, rr, art.solid);
        if (inflate > 0.15f) {
            // Spines flare out as it puffs.
            art.stroke.setColor(ink);
            art.stroke.setStrokeWidth(Math.max(1.2f, rr * 0.1f));
            for (int i = 0; i < 16; i++) {
                float a = i / 16f * V.TAU + t * 0.2f;
                float o = rr * (1f + 0.42f * inflate);
                c.drawLine(sx + (float) Math.cos(a) * rr * 0.92f,
                        sy + (float) Math.sin(a) * rr * 0.92f,
                        sx + (float) Math.cos(a) * o,
                        sy + (float) Math.sin(a) * o, art.stroke);
            }
        }
        float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);
        art.fin(c, sx - ca * rr, sy - sa * rr, angle + (float) Math.PI,
                rr * 0.7f, rr * 0.5f, finC);
        eyeAt(c, art, sx + ca * rr * 0.45f - sa * rr * 0.3f,
                sy + sa * rr * 0.45f + ca * rr * 0.3f, s, vis, 0f, 0f);
    }

    private void drawLeech(Canvas c, Art art, float sx, float sy, float s,
                           float vis, int ink, int finC) {
        float len = bodyLen * s, g = girth * s;
        art.body(c, sx, sy, angle, 14, len, g, g * 1.8f, swimPhase, 6.5f, ink);
        // Sucker mouth ring at the head.
        art.ring(c, sx + (float) Math.cos(angle) * len * 0.06f,
                sy + (float) Math.sin(angle) * len * 0.06f,
                g * 0.85f, Math.max(1f, g * 0.28f), ink);
        if (latched != null) {
            art.ring(c, sx, sy, g * 1.7f, Math.max(1f, 1.5f * s),
                    Art.argb(vis * 0.35f, 1f));
        }
    }

    private void eyeAt(Canvas c, Art art, float sx, float sy, float s, float vis,
                       float fwd, float side) {
        float len = bodyLen * s, g = girth * s;
        float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);
        float ex = sx + ca * len * fwd - sa * g * side;
        float ey = sy + sa * len * fwd + ca * g * side;
        art.eye(c, ex, ey, Math.max(1.1f, g * 0.3f), vis * 0.92f, 0.42f);
    }

    private void drawPossessionMark(Canvas c, Art art, float sx, float sy, float s, float vis) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3.4f);
        float rr = hitRadius() * s * (1.65f + pulse * 0.12f);
        art.ring(c, sx, sy, rr, Math.max(1.1f, 1.4f * s), Art.argb(0.30f + pulse * 0.18f, 1f));
        // Three orbiting motes, the "spirit in residence".
        art.solid.setShader(null);
        art.solid.setColor(Art.argb(0.55f, 1f));
        for (int i = 0; i < 3; i++) {
            float a = t * 1.5f + i * V.TAU / 3f;
            c.drawCircle(sx + (float) Math.cos(a) * rr, sy + (float) Math.sin(a) * rr,
                    Math.max(1f, 1.6f * s), art.solid);
        }
    }
}
