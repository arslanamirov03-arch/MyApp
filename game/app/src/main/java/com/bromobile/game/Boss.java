package com.bromobile.game;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * The five map bosses. Each runs its own attack cycle and each can only be hurt
 * during a specific window — a stuck claw, a charging staff, an open throat, a
 * lit rune or a retracted shutter — so every fight is beaten differently.
 */
public final class Boss extends Mob {

    public static final int TITAN = 0, ARCHON = 1, GLACIODON = 2, GOLEM = 3, CORE = 4;

    public final int kind;
    public final String name;

    private int state;
    private float stateTime;
    private float timer;
    private int cycle;
    private float anim;
    private float openness;       // 0 armoured .. 1 weak point exposed
    private float rage;           // 0..1, ramps as health drops
    public float intro = 2.6f;
    public boolean dying;
    public float deathTime;

    private float homeX, homeY;
    private float floorY;
    private float armPhase, armExtend;
    private float aimA;
    private float markX, markTimer;
    private float breathT;
    private final float[] runeHp = new float[4];
    private int litRune = -1;
    private float shutter = 1f;   // 1 closed, 0 open
    private float sweepA;
    private float spawnCd;
    private float hitCd;

    private final Paint p = new Paint();

    public Boss(int kind, float px, float floorY, String name) {
        this.kind = kind;
        this.name = name;
        this.floorY = floorY;
        p.setAntiAlias(false);
        p.setFilterBitmap(false);
        switch (kind) {
            case TITAN:
                w = 44; h = 66; hp = maxHp = 260;
                x = px - w / 2; y = floorY - h;
                break;
            case ARCHON:
                w = 34; h = 46; hp = maxHp = 220;
                x = px - w / 2; y = floorY - 130;
                break;
            case GLACIODON:
                w = 66; h = 50; hp = maxHp = 280;
                x = px - w / 2; y = floorY - 120;
                break;
            case GOLEM:
                w = 46; h = 68; hp = maxHp = 200;
                x = px - w / 2; y = floorY - h;
                for (int i = 0; i < 4; i++) runeHp[i] = 34;
                litRune = 0;
                break;
            default:
                w = 60; h = 54; hp = maxHp = 300;
                x = px - w / 2; y = floorY - 96;
                break;
        }
        homeX = x;
        homeY = y;
        face = -1;
    }

    public boolean hittable() { return !dying && intro <= 0; }

    public boolean hitTest(float wx, float wy) {
        if (kind == GLACIODON) return wx > x && wx < x + w && wy > y && wy < y + h;
        return wx > x - 2 && wx < x + w + 2 && wy > y - 2 && wy < y + h + 2;
    }

    /** True when the shot lands on armour instead of the weak point. */
    public boolean armoredAt(float wx, float wy) {
        switch (kind) {
            case TITAN:
                // Chest core only.
                return !(openness > 0.4f && wy > y + 20 && wy < y + 40
                        && wx > x + 10 && wx < x + w - 10);
            case ARCHON:
                return openness < 0.4f;
            case GLACIODON:
                // The open mouth / throat.
                return !(openness > 0.35f && wx < x + w * 0.5f && wy > y + 12 && wy < y + h - 8);
            case GOLEM: {
                if (litRune < 0) return openness < 0.5f;
                float[] rp = runePos(litRune);
                return !(Math.abs(wx - rp[0]) < 8 && Math.abs(wy - rp[1]) < 8);
            }
            default:
                return shutter > 0.35f;
        }
    }

    /** True while the weak point is exposed. */
    public boolean vulnerable() {
        switch (kind) {
            case TITAN: return openness > 0.4f;
            case ARCHON: return openness >= 0.4f;
            case GLACIODON: return openness > 0.35f;
            case GOLEM: return true;          // a rune (or the core) is always a target
            default: return shutter <= 0.35f;
        }
    }

    /** World-space point the player's aim assist should converge on. */
    public float weakX() {
        if (kind == GOLEM && litRune >= 0) return runePos(litRune)[0];
        if (kind == GLACIODON) return x + 16;
        return cx();
    }

    public float weakY() {
        switch (kind) {
            case TITAN: return y + 30;
            case GLACIODON: return y + h - 17;
            case GOLEM: return litRune >= 0 ? runePos(litRune)[1] : y + 36;
            case CORE: return y + 25;
            default: return cy();
        }
    }

    private float[] runePos(int i) {
        switch (i) {
            case 0: return new float[]{x + 12, y + 26};
            case 1: return new float[]{x + w - 12, y + 26};
            case 2: return new float[]{x + 12, y + 46};
            default: return new float[]{x + w - 12, y + 46};
        }
    }

    public void hurt(float amount, World world, float hx, float hy) {
        if (!hittable()) return;
        if (kind == GOLEM && litRune >= 0) {
            runeHp[litRune] -= amount;
            world.fx.sparks(hx, hy, 5, 0xFFE878C8);
            if (runeHp[litRune] <= 0) {
                world.fx.explosion(hx, hy, 16, 0xFFE878C8);
                world.sfx.play(Sfx.ICE, 0.9f, 0.7f);
                world.fx.text("РУНА РАЗБИТА", cx(), y - 10, 0xFFE878C8);
                int next = -1;
                for (int i = 0; i < 4; i++) if (runeHp[i] > 0) { next = i; break; }
                litRune = next;
                hp -= maxHp * 0.12f;
                if (next < 0) {
                    openness = 1;
                    world.fx.text("ЯДРО ОТКРЫТО!", cx(), y - 20, 0xFFFFD040);
                }
            }
            return;
        }
        hp -= amount;
        hurtFlash = 0.07f;
        if (hitCd <= 0) {
            hitCd = 0.05f;
            world.fx.sparks(hx, hy, 3, 0xFFFFE070);
        }
        if (hp <= 0) {
            hp = 0;
            dying = true;
            deathTime = 3.2f;
            world.onBossDying(this);
        }
    }

    // ==================================================================

    @Override
    public void update(float dt, World world) {
        anim += dt;
        if (hitCd > 0) hitCd -= dt;
        if (hurtFlash > 0) hurtFlash -= dt;
        rage = 1f - hp / maxHp;

        if (intro > 0) {
            intro -= dt;
            if (intro <= 0) world.sfx.play(Sfx.ROAR, 1f, kind == GLACIODON ? 0.75f : 1f);
            return;
        }
        if (dying) {
            deathTime -= dt;
            // Chain of explosions across the body while it collapses.
            if (world.rndF() < dt * 14) {
                float ex = x + world.rndF() * w, ey = y + world.rndF() * h;
                world.fx.explosion(ex, ey, 12 + world.rndF() * 10, 0xFFFF9030);
                world.sfx.playAt(Sfx.EXPLODE, 0, 0.6f, 1.2f + world.rndF() * 0.4f);
            }
            y += 12 * dt;
            return;
        }

        stateTime += dt;
        timer -= dt;
        switch (kind) {
            case TITAN: titan(dt, world); break;
            case ARCHON: archon(dt, world); break;
            case GLACIODON: glaciodon(dt, world); break;
            case GOLEM: golem(dt, world); break;
            default: core(dt, world); break;
        }

        Player pl = world.player;
        if (pl != null && pl.alive() && !pl.invulnerable() && bodyHurts()
                && pl.overlapsBox(x, y, w, h))
            pl.kill(world, "BOSS");
    }

    private boolean bodyHurts() {
        return kind != GLACIODON || openness < 0.3f;
    }

    private void go(int s) {
        state = s;
        stateTime = 0;
    }

    // ---------------- TITAN: claw slams, rockets, exposed core -------------

    private void titan(float dt, World world) {
        Player pl = world.player;
        float speed = 20 + rage * 22;
        switch (state) {
            case 0: {   // stalk
                if (pl != null) {
                    face = pl.cx() < cx() ? -1 : 1;
                    float d = pl.cx() - cx();
                    vx = Math.abs(d) > 60 ? Math.signum(d) * speed : 0;
                }
                x += vx * dt;
                clampArena(world);
                openness = Math.max(0, openness - dt * 2);
                if (stateTime > 1.4f - rage * 0.5f) {
                    go(cycle % 3 == 2 ? 2 : 1);
                    cycle++;
                    armPhase = 0;
                }
                break;
            }
            case 1: {   // claw slam
                armPhase += dt;
                if (armPhase < 0.55f) {
                    armExtend = armPhase / 0.55f * -0.6f;      // wind up
                    if (armPhase < dt * 2) world.fx.text("!", cx() + face * 20, y - 6, 0xFFFF4040);
                } else if (armPhase < 0.78f) {
                    armExtend = (armPhase - 0.55f) / 0.23f * 1.9f;
                } else {
                    // Impact
                    float ix = cx() + face * 40, iy = floorY - 4;
                    world.fx.explosion(ix, iy, 22, 0xFFC08040);
                    world.fx.ring(ix, iy, 70, 0xCCFFD070);
                    world.fx.kick(7);
                    world.sfx.play(Sfx.STOMP, 1f, 0.75f);
                    world.sfx.buzz(45);
                    world.level.blast(ix, iy + 8, 26, 2, world.fx);
                    // Ground shockwave travels along the floor.
                    for (int s = -1; s <= 1; s += 2)
                        for (int i = 0; i < 3; i++) {
                            Shot sh = world.newShot();
                            sh.set(ix, floorY - 5, s * (110 + i * 26), 0, 1.5f, 6,
                                    Shot.FIREBALL, false, 0xFFFFA030);
                            sh.size = 3;
                        }
                    go(3);                                     // arm sticks in the ground
                }
                break;
            }
            case 2: {   // rocket barrage
                if (timer <= 0 && stateTime < 1.8f) {
                    timer = 0.28f - rage * 0.08f;
                    float bx = cx() + face * 14, by = y + 14;
                    float a = (float) Math.atan2((pl != null ? pl.cy() : y) - by,
                            (pl != null ? pl.cx() : x) - bx) + (world.rndF() - 0.5f) * 0.5f;
                    Shot s = world.newShot();
                    s.set(bx, by, (float) Math.cos(a) * 155, (float) Math.sin(a) * 155,
                            3f, 6, Shot.ROCKET, false, 0xFFFF8030);
                    s.blast = 26;
                    s.homing = 0.7f;
                    world.fx.muzzle(bx, by, face, 1.6f);
                    world.sfx.play(Sfx.ROCKET, 0.6f, 1.2f);
                }
                if (stateTime > 2.4f) go(0);
                break;
            }
            case 3: {   // claw stuck — core exposed
                armExtend = 1.9f;
                openness = Math.min(1, openness + dt * 4);
                if (stateTime < dt * 2) {
                    world.fx.text("ЯДРО ОТКРЫТО!", cx(), y + 6, 0xFFFFD040);
                    world.sfx.play(Sfx.ALARM, 0.7f, 1f);
                }
                if (stateTime > 2.3f - rage * 0.6f) {
                    armExtend = 0;
                    openness = 0;
                    go(0);
                }
                break;
            }
        }
    }

    // ---------------- ARCHON: lightning, storms, charged beam --------------

    private void archon(float dt, World world) {
        Player pl = world.player;
        float bobY = (float) Math.sin(anim * 1.7f) * 8;
        switch (state) {
            case 0: {   // drift and pick an attack
                if (pl != null) {
                    face = pl.cx() < cx() ? -1 : 1;
                    float tx = pl.cx() + face * -70;
                    float ty = floorY - 120 + bobY;
                    x += (tx - x) * Math.min(1, dt * 1.5f);
                    y += (ty - y) * Math.min(1, dt * 1.8f);
                }
                openness = 0;
                if (stateTime > 1.3f) {
                    cycle++;
                    go(cycle % 4 == 3 ? 3 : (cycle % 2 == 0 ? 1 : 2));
                }
                break;
            }
            case 1: {   // targeted lightning strikes
                if (timer <= 0 && stateTime < 2.2f) {
                    timer = 0.62f - rage * 0.16f;
                    markX = pl != null ? pl.cx() : cx();
                    markTimer = 0.62f;
                    world.sfx.play(Sfx.ALARM, 0.4f, 2f);
                }
                if (markTimer > 0) {
                    markTimer -= dt;
                    if (markTimer <= 0) strike(world, markX);
                }
                y += ((floorY - 130 + bobY) - y) * Math.min(1, dt * 2);
                if (stateTime > 2.9f) go(0);
                break;
            }
            case 2: {   // spiral of storm orbs
                if (timer <= 0 && stateTime < 2.0f) {
                    timer = 0.14f;
                    float a = stateTime * 6.5f;
                    for (int i = 0; i < 2; i++) {
                        float aa = a + i * (float) Math.PI;
                        Shot s = world.newShot();
                        s.set(cx(), cy(), (float) Math.cos(aa) * 120,
                                (float) Math.sin(aa) * 120, 3.2f, 4, Shot.ORB, false, 0xFFCFF0FF);
                        s.size = 3;
                        s.gravity = 26;
                    }
                    world.sfx.play(Sfx.LASER, 0.3f, 1.5f);
                }
                if (stateTime > 2.5f) go(0);
                break;
            }
            case 3: {   // charging the great beam — the opening
                openness = Math.min(1, stateTime / 0.5f);
                vx = vy = 0;
                if (stateTime < dt * 2) {
                    world.fx.text("УЯЗВИМ!", cx(), y - 12, 0xFFFFD040);
                    world.sfx.play(Sfx.ALARM, 0.6f, 0.8f);
                }
                world.fx.add(Fx.PLASMA, cx() + face * 12 + (world.rndF() - .5f) * 14,
                        cy() - 10 + (world.rndF() - .5f) * 14,
                        0, 0, 0.3f, 2, 0xFFCFF0FF, 0);
                if (stateTime > 1.9f) {
                    // Release: a wide fan of bolts.
                    for (int i = -4; i <= 4; i++) {
                        float a = (float) Math.PI * 0.5f + i * 0.13f;
                        Shot s = world.newShot();
                        s.set(cx(), cy() + 8, (float) Math.cos(a) * 210,
                                (float) Math.sin(a) * 210, 2.2f, 6, Shot.PLASMA, false, 0xFFFFE070);
                        s.size = 3;
                    }
                    world.fx.explosion(cx(), cy() + 12, 18, 0xFFFFE070);
                    world.sfx.play(Sfx.LASER, 0.9f, 0.6f);
                    openness = 0;
                    go(0);
                }
                break;
            }
        }
        if (spawnCd > 0) spawnCd -= dt;
        if (rage > 0.5f && spawnCd <= 0) {
            spawnCd = 7f;
            world.spawnMinion(cx() + (world.rndF() - 0.5f) * 80, floorY - 60, 1);
        }
    }

    private void strike(World world, float sxp) {
        world.fx.ring(sxp, floorY - 6, 40, 0xCCFFF0A0);
        world.fx.explosion(sxp, floorY - 8, 16, 0xFFFFE070);
        world.fx.flash = 0.4f;
        world.fx.flashColor = 0xFFDDEEFF;
        world.fx.kick(4);
        world.sfx.play(Sfx.EXPLODE, 0.8f, 1.6f);
        Player pl = world.player;
        if (pl != null && pl.alive() && !pl.invulnerable() && Math.abs(pl.cx() - sxp) < 16)
            pl.kill(world, "BOLT");
        world.addLightning(sxp, floorY);
    }

    // ---------------- GLACIODON: breath, icicles, open throat --------------

    private void glaciodon(float dt, World world) {
        Player pl = world.player;
        float ty;
        switch (state) {
            case 0: {   // track the player vertically
                ty = (pl != null ? pl.cy() - 24 : y);
                ty = Math.max(floorY - 150, Math.min(floorY - 70, ty));
                y += (ty - y) * Math.min(1, dt * 1.6f);
                openness = Math.max(0, openness - dt * 2.5f);
                if (stateTime > 1.2f) {
                    cycle++;
                    go(cycle % 3 == 2 ? 3 : 1);
                }
                break;
            }
            case 1: {   // inhale — throat glows, this is the window
                openness = Math.min(1, stateTime / 0.6f);
                if (stateTime < dt * 2) {
                    world.fx.text("ВДОХ — БЕЙ В ПАСТЬ!", cx(), y - 12, 0xFF8AE0FF);
                    world.sfx.play(Sfx.ICE, 0.7f, 0.6f);
                }
                // Suck particles toward the mouth.
                for (int i = 0; i < 2; i++) {
                    float a = (float) (Math.PI * (0.7f + world.rndF() * 0.6f));
                    float d = 40 + world.rndF() * 50;
                    world.fx.add(Fx.PLASMA, cx() + (float) Math.cos(a) * d,
                            cy() + (float) Math.sin(a) * d,
                            -(float) Math.cos(a) * 90, -(float) Math.sin(a) * 90,
                            0.4f, 1, 0xFFB8E4F8, 0);
                }
                if (stateTime > 1.5f - rage * 0.4f) { go(2); breathT = 0; }
                break;
            }
            case 2: {   // freezing breath sweep
                breathT += dt;
                openness = 1;
                if (timer <= 0) {
                    timer = 0.045f;
                    float base = (float) Math.PI;
                    float sweep = (float) Math.sin(breathT * 2.4f) * 0.55f;
                    for (int i = 0; i < 2; i++) {
                        float a = base + sweep + (world.rndF() - 0.5f) * 0.3f;
                        float sp = 200 + world.rndF() * 90;
                        Shot s = world.newShot();
                        s.set(x + 6, cy() + 4, (float) Math.cos(a) * sp,
                                (float) Math.sin(a) * sp, 1.1f, 4, Shot.SHARD, false, 0xFFB8E4F8);
                        s.gravity = 60;
                    }
                    world.sfx.play(Sfx.STEAM, 0.3f, 1.4f);
                }
                if (breathT > 1.6f) { openness = 0; go(0); }
                break;
            }
            case 3: {   // icicles drop from the ceiling
                if (stateTime < dt * 2) {
                    world.fx.text("СОСУЛЬКИ!", cx(), y - 12, 0xFF8AE0FF);
                    world.sfx.play(Sfx.ICE, 0.8f, 1f);
                }
                if (timer <= 0 && stateTime < 2.4f) {
                    timer = 0.2f - rage * 0.06f;
                    float sxp = world.arenaLeft + world.rndF() * (world.arenaRight - world.arenaLeft);
                    Shot s = world.newShot();
                    s.set(sxp, floorY - 190, 0, 40, 4f, 5, Shot.SHARD, false, 0xFFD8F0FC);
                    s.gravity = 320;
                    world.fx.add(Fx.PLASMA, sxp, floorY - 186, 0, 0, 0.3f, 2, 0xFFB8E4F8, 0);
                }
                if (rage > 0.4f && spawnCd <= 0) {
                    spawnCd = 6f;
                    world.spawnMinion(world.arenaLeft + 30, floorY - 30, 0);
                }
                if (stateTime > 3.0f) go(0);
                break;
            }
        }
        if (spawnCd > 0) spawnCd -= dt;
    }

    // ---------------- GOLEM: runes must fall first -------------------------

    private void golem(float dt, World world) {
        Player pl = world.player;
        switch (state) {
            case 0: {
                if (pl != null) {
                    face = pl.cx() < cx() ? -1 : 1;
                    float d = pl.cx() - cx();
                    if (Math.abs(d) > 70) x += Math.signum(d) * (16 + rage * 20) * dt;
                }
                clampArena(world);
                if (stateTime > 1.5f) {
                    cycle++;
                    go(1 + cycle % 3);
                }
                break;
            }
            case 1: {   // rune beams
                if (timer <= 0 && stateTime < 1.8f) {
                    timer = 0.4f;
                    for (int i = 0; i < 4; i++) {
                        if (runeHp[i] <= 0) continue;
                        float[] rp = runePos(i);
                        float a = (float) Math.atan2((pl != null ? pl.cy() : y) - rp[1],
                                (pl != null ? pl.cx() : x) - rp[0]);
                        Shot s = world.newShot();
                        s.set(rp[0], rp[1], (float) Math.cos(a) * 170,
                                (float) Math.sin(a) * 170, 2.4f, 5, Shot.PLASMA, false, 0xFFE878C8);
                        s.size = 3;
                    }
                    world.sfx.play(Sfx.LASER, 0.55f, 0.9f);
                }
                if (stateTime > 2.2f) go(0);
                break;
            }
            case 2: {   // stomp shockwave
                if (stateTime < 0.5f) {
                    if (stateTime < dt * 2) world.fx.text("!", cx(), y - 8, 0xFFFF4040);
                } else if (stateTime < 0.56f) {
                    world.fx.ring(cx(), floorY - 4, 80, 0xCC9AD858);
                    world.fx.kick(6);
                    world.sfx.play(Sfx.STOMP, 0.95f, 0.8f);
                    world.level.blast(cx(), floorY + 6, 22, 2, world.fx);
                    for (int s = -1; s <= 1; s += 2) {
                        Shot sh = world.newShot();
                        sh.set(cx(), floorY - 6, s * 130, 0, 1.6f, 6, Shot.FIREBALL, false, 0xFF9AD858);
                        sh.size = 3;
                    }
                } else if (stateTime > 1.1f) go(0);
                break;
            }
            default: {  // summon scarabs
                if (stateTime < dt * 2) {
                    world.spawnMinion(cx() - 30, floorY - 20, 1);
                    world.spawnMinion(cx() + 30, floorY - 20, 1);
                    world.sfx.play(Sfx.ALARM, 0.5f, 1.4f);
                }
                if (stateTime > 1.0f) go(0);
                break;
            }
        }
        // The lit rune pulses so the player knows where to shoot.
        if (litRune >= 0 && world.rndF() < dt * 20) {
            float[] rp = runePos(litRune);
            world.fx.add(Fx.PLASMA, rp[0] + (world.rndF() - .5f) * 10,
                    rp[1] + (world.rndF() - .5f) * 10, 0, -18, 0.35f, 1, 0xFFE878C8, 0);
        }
    }

    // ---------------- CORE A: shutters, sweeps, mines ----------------------

    private void core(float dt, World world) {
        Player pl = world.player;
        float bobY = (float) Math.sin(anim * 1.3f) * 6;
        y += ((floorY - 96 + bobY) - y) * Math.min(1, dt * 2);
        if (pl != null) {
            float tx = pl.cx() - 40 * face;
            tx = Math.max(world.arenaLeft + 40, Math.min(world.arenaRight - 40, tx));
            x += (tx - w / 2 - x) * Math.min(1, dt * 0.7f);
            face = pl.cx() < cx() ? -1 : 1;
        }

        switch (state) {
            case 0: {
                shutter = Math.min(1, shutter + dt * 3);
                if (stateTime > 1.2f) {
                    cycle++;
                    go(1 + cycle % 3);
                }
                break;
            }
            case 1: {   // rotating laser sweep
                shutter = 1;
                sweepA += dt * (2.2f + rage);
                if (timer <= 0 && stateTime < 2.2f) {
                    timer = 0.1f;
                    for (int i = 0; i < 3; i++) {
                        float a = sweepA + i * (float) (Math.PI * 2 / 3);
                        Shot s = world.newShot();
                        s.set(cx(), cy(), (float) Math.cos(a) * 165,
                                (float) Math.sin(a) * 165, 2.4f, 5, Shot.PLASMA, false, 0xFF58C0E0);
                        s.size = 2;
                    }
                    world.sfx.play(Sfx.LASER, 0.3f, 1.3f);
                }
                if (stateTime > 2.6f) go(4);
                break;
            }
            case 2: {   // saw blades bounce along the floor
                shutter = 1;
                if (timer <= 0 && stateTime < 1.6f) {
                    timer = 0.45f;
                    Shot s = world.newShot();
                    s.set(cx(), cy() + 10, face * 140, -60, 4f, 6, Shot.SAW, false, 0xFFCFD8E0);
                    s.gravity = 300;
                    s.bounces = 6;
                    s.size = 4;
                    world.sfx.play(Sfx.SWITCH, 0.6f, 0.6f);
                }
                if (stateTime > 2.0f) go(4);
                break;
            }
            case 3: {   // flame jets from the arms
                shutter = 1;
                if (timer <= 0 && stateTime < 1.7f) {
                    timer = 0.04f;
                    for (int s2 = -1; s2 <= 1; s2 += 2) {
                        float a = (float) (Math.PI * 0.5) + s2 * 0.5f + (world.rndF() - .5f) * 0.35f;
                        Shot s = world.newShot();
                        s.set(cx() + s2 * 22, cy() + 12, (float) Math.cos(a) * 170,
                                (float) Math.sin(a) * 170, 0.55f, 4, Shot.FLAME, false, 0xFFFF8030);
                        s.pierces = true;
                    }
                    if (world.rndF() < 0.2f) world.sfx.play(Sfx.FLAME, 0.4f, 1f);
                }
                if (stateTime > 2.1f) go(4);
                break;
            }
            default: {  // vent cycle: shutters open, core exposed
                shutter = Math.max(0, shutter - dt * 2.4f);
                if (stateTime < dt * 2) {
                    world.fx.text("ЗАСЛОНКИ ОТКРЫТЫ!", cx(), y - 14, 0xFFFFA828);
                    world.sfx.play(Sfx.STEAM, 0.8f, 0.8f);
                }
                world.fx.smokePuff(cx() + (world.rndF() - .5f) * 40, y + 8, 1, 0x99C0C8D0);
                if (stateTime > 2.4f - rage * 0.7f) go(0);
                break;
            }
        }
        openness = 1 - shutter;
        if (spawnCd > 0) spawnCd -= dt;
        if (rage > 0.55f && spawnCd <= 0) {
            spawnCd = 8f;
            world.spawnMinion(world.arenaLeft + 40, floorY - 40, 1);
        }
    }

    private void clampArena(World world) {
        if (x < world.arenaLeft + 10) x = world.arenaLeft + 10;
        if (x + w > world.arenaRight - 10) x = world.arenaRight - 10 - w;
    }

    // ==================================================================
    // Drawing
    // ==================================================================

    private void rect(Canvas c, float rx, float ry, float rw, float rh, int color) {
        p.setColor(color);
        c.drawRect((int) rx, (int) ry, (int) (rx + rw), (int) (ry + rh), p);
    }

    @Override
    public void draw(Canvas c, float camX, float camY) {
        float ox = x - camX, oy = y - camY;
        if (dying) {
            ox += (float) Math.sin(deathTime * 40) * 2;
        }
        int flash = hurtFlash > 0 ? 0x40FFFFFF : 0;
        switch (kind) {
            case TITAN: drawTitan(c, ox, oy, camX, camY); break;
            case ARCHON: drawArchon(c, ox, oy); break;
            case GLACIODON: drawGlaciodon(c, ox, oy); break;
            case GOLEM: drawGolem(c, ox, oy, camX, camY); break;
            default: drawCore(c, ox, oy); break;
        }
        if (flash != 0) {
            p.setColor(flash);
            c.drawRect((int) ox, (int) oy, (int) (ox + w), (int) (oy + h), p);
        }
    }

    // --- city titan: rusted mech fused with the clock tower
    private void drawTitan(Canvas c, float ox, float oy, float camX, float camY) {
        int dark = 0xFF3A2C28, mid = 0xFF6E4A3A, light = 0xFF9A6A4A, steel = 0xFF5A5A66;
        // legs
        rect(c, ox + 6, oy + 44, 12, 22, dark);
        rect(c, ox + w - 18, oy + 44, 12, 22, dark);
        rect(c, ox + 6, oy + 44, 12, 4, mid);
        rect(c, ox + w - 18, oy + 44, 12, 4, mid);
        rect(c, ox + 2, oy + 62, 18, 5, steel);
        rect(c, ox + w - 20, oy + 62, 18, 5, steel);
        // torso
        rect(c, ox + 2, oy + 14, w - 4, 34, mid);
        rect(c, ox + 2, oy + 14, w - 4, 4, light);
        rect(c, ox + 2, oy + 44, w - 4, 4, dark);
        for (int i = 0; i < 4; i++) rect(c, ox + 6 + i * 9, oy + 20, 3, 3, dark);
        // clock-tower head
        rect(c, ox + 10, oy - 4, w - 20, 20, 0xFF7A5238);
        rect(c, ox + 10, oy - 4, w - 20, 3, 0xFFA07048);
        p.setColor(0xFFE8DCC0);
        c.drawCircle(ox + w / 2f, oy + 6, 7, p);
        p.setColor(0xFF3A2C28);
        c.drawCircle(ox + w / 2f, oy + 6, 7.5f, p);
        p.setColor(0xFFE8DCC0);
        c.drawCircle(ox + w / 2f, oy + 6, 6, p);
        p.setColor(0xFF2A1E1A);
        float ha = anim * 0.9f;
        c.drawRect(ox + w / 2f - 1, oy + 6 - 5, ox + w / 2f + 1, oy + 6, p);
        c.drawRect(ox + w / 2f, oy + 6, ox + w / 2f + (float) Math.cos(ha) * 5,
                oy + 6 + (float) Math.sin(ha) * 5 + 1, p);
        // eyes
        p.setColor(openness > 0.4f ? 0xFFFFD040 : 0xFFFF3020);
        c.drawRect(ox + 14, oy + 2, ox + 18, oy + 5, p);
        c.drawRect(ox + w - 18, oy + 2, ox + w - 14, oy + 5, p);
        // chest core
        float k = openness;
        if (k > 0.05f) {
            p.setColor(0xFF201410);
            c.drawRect(ox + 10, oy + 20, ox + w - 10, oy + 40, p);
            float pulse = 0.6f + 0.4f * (float) Math.sin(anim * 12);
            p.setColor(Fx.blend(0xFFFF6020, 0xFFFFF0A0, pulse));
            c.drawRect(ox + 12, oy + 22 + (1 - k) * 8, ox + w - 12, oy + 38 - (1 - k) * 8, p);
            p.setColor(0x66FFD070);
            c.drawCircle(ox + w / 2f, oy + 30, 10 + pulse * 5, p);
        } else {
            rect(c, ox + 10, oy + 22, w - 20, 16, steel);
            for (int i = 0; i < 3; i++) rect(c, ox + 12, oy + 24 + i * 5, w - 24, 2, dark);
        }
        // arms + claw
        float ax = ox + (face > 0 ? w - 4 : -8);
        float reach = armExtend * 26;
        rect(c, ax - (face > 0 ? 0 : 4), oy + 16, 12, 10, mid);
        float clawX = ax + face * (10 + reach);
        float clawY = oy + 18 + Math.max(0, armExtend) * 26;
        rect(c, Math.min(ax, clawX), oy + 20, Math.abs(clawX - ax) + 4, 5, steel);
        for (int i = 0; i < 3; i++) {
            rect(c, clawX + face * (i * 2), clawY - 8 + i * 7, 5, 8, 0xFF8A8A96);
            rect(c, clawX + face * (i * 2), clawY - 8 + i * 7, 5, 2, 0xFFB0B0BC);
        }
        // smoke stacks
        rect(c, ox + 4, oy + 6, 5, 10, steel);
        rect(c, ox + w - 9, oy + 6, 5, 10, steel);
    }

    // --- sky archon: cloud body, wings, lightning staff
    private void drawArchon(Canvas c, float ox, float oy) {
        float t = anim;
        int cloudA = 0xFFF0F4FC, cloudB = 0xFFC8D8EC, gold = 0xFFE8C060, goldD = 0xFFA07830;
        // wings
        float flap = (float) Math.sin(t * 4) * 4;
        for (int s = -1; s <= 1; s += 2) {
            float wx = ox + (s < 0 ? -22 : w + 2);
            for (int i = 0; i < 5; i++) {
                float ly = oy + 4 + i * 5 + (s < 0 ? flap : -flap) * (i / 5f);
                float lw = 20 - i * 2;
                rect(c, s < 0 ? wx + (20 - lw) : wx, ly, lw, 4, i % 2 == 0 ? cloudA : cloudB);
            }
        }
        // cloud body
        rect(c, ox + 2, oy + 24, w - 4, 22, cloudA);
        rect(c, ox - 2, oy + 30, w + 4, 12, cloudA);
        rect(c, ox + 2, oy + 42, w - 4, 6, cloudB);
        // armour
        rect(c, ox + 6, oy + 18, w - 12, 12, gold);
        rect(c, ox + 6, oy + 18, w - 12, 3, 0xFFFFE8A0);
        rect(c, ox + 8, oy + 26, w - 16, 3, goldD);
        // head
        rect(c, ox + 10, oy + 4, w - 20, 14, cloudA);
        rect(c, ox + 8, oy + 2, w - 16, 5, gold);
        p.setColor(openness > 0.4f ? 0xFFFFF0A0 : 0xFF3A5A96);
        c.drawRect(ox + 12, oy + 9, ox + 16, oy + 12, p);
        c.drawRect(ox + w - 16, oy + 9, ox + w - 12, oy + 12, p);
        // staff
        float sxp = ox + (face > 0 ? w + 4 : -8);
        rect(c, sxp, oy + 6, 3, 44, 0xFFB09060);
        float glow = openness > 0.1f ? (0.4f + 0.6f * (float) Math.sin(t * 16)) : 0.25f;
        p.setColor(Fx.blend(0xFF6A8ACF, 0xFFFFFFFF, glow));
        c.drawCircle(sxp + 1.5f, oy + 4, 4 + glow * 3, p);
        if (openness > 0.3f) {
            p.setColor(0x66CFF0FF);
            c.drawCircle(ox + w / 2f, oy + h / 2f, 26 + glow * 10, p);
        }
    }

    // --- ice dragon head bursting from the cavern wall
    private void drawGlaciodon(Canvas c, float ox, float oy) {
        int iceD = 0xFF6A9AC0, iceM = 0xFF9AC8E4, iceL = 0xFFD8F0FC, maw = 0xFF2A4A66;
        // neck
        rect(c, ox + w - 22, oy + 6, 30, h - 10, iceD);
        rect(c, ox + w - 22, oy + 6, 30, 5, iceM);
        // skull
        rect(c, ox + 8, oy + 6, w - 16, h - 18, iceM);
        rect(c, ox + 8, oy + 6, w - 16, 5, iceL);
        rect(c, ox + 4, oy + 12, 14, 16, iceM);
        // horns
        for (int i = 0; i < 3; i++) {
            rect(c, ox + 20 + i * 12, oy - 6 - i * 2, 5, 12 + i * 3, iceL);
            rect(c, ox + 20 + i * 12, oy - 6 - i * 2, 2, 12 + i * 3, 0xFFFFFFFF);
        }
        // jaw opens with `openness`
        float open = openness * 16;
        rect(c, ox + 2, oy + h - 14 + open * 0.3f, w - 20, 10, iceM);
        rect(c, ox + 2, oy + h - 6 + open * 0.3f, w - 20, 3, iceD);
        if (openness > 0.05f) {
            // throat: the weak point
            p.setColor(maw);
            c.drawRect(ox + 4, oy + h - 22, ox + 30, oy + h - 12 + open * 0.3f, p);
            float pulse = 0.5f + 0.5f * (float) Math.sin(anim * 14);
            p.setColor(Fx.blend(0xFF3AA0D0, 0xFFCFF8FF, pulse * openness));
            c.drawRect(ox + 8, oy + h - 20, ox + 26, oy + h - 14 + open * 0.3f, p);
            p.setColor(0x55CFF0FF);
            c.drawCircle(ox + 16, oy + h - 17, 8 + pulse * 5, p);
        }
        // teeth
        p.setColor(0xFFF8FCFF);
        for (int i = 0; i < 6; i++) {
            c.drawRect(ox + 4 + i * 6, oy + h - 24, ox + 7 + i * 6, oy + h - 18, p);
            c.drawRect(ox + 4 + i * 6, oy + h - 12 + open * 0.3f,
                    ox + 7 + i * 6, oy + h - 6 + open * 0.3f, p);
        }
        // eye
        p.setColor(0xFF102030);
        c.drawRect(ox + 22, oy + 16, ox + 34, oy + 24, p);
        p.setColor(openness > 0.35f ? 0xFFFFE060 : 0xFF60D8FF);
        c.drawRect(ox + 25, oy + 18, ox + 31, oy + 22, p);
        // frost aura
        p.setColor(0x22CFF0FF);
        c.drawCircle(ox + w / 2f, oy + h / 2f, 40, p);
    }

    // --- pharaoh golem with four runes
    private void drawGolem(Canvas c, float ox, float oy, float camX, float camY) {
        int st = 0xFF6E6A50, stD = 0xFF3C3A2C, stL = 0xFF98957A, gold = 0xFFD8B040;
        rect(c, ox + 4, oy + 46, 14, 22, st);
        rect(c, ox + w - 18, oy + 46, 14, 22, st);
        rect(c, ox, oy + 64, 20, 5, stD);
        rect(c, ox + w - 20, oy + 64, 20, 5, stD);
        rect(c, ox + 2, oy + 16, w - 4, 32, st);
        rect(c, ox + 2, oy + 16, w - 4, 4, stL);
        rect(c, ox + 2, oy + 44, w - 4, 4, stD);
        // arms
        float swing = (float) Math.sin(anim * 1.6f) * 3;
        rect(c, ox - 10, oy + 18 + swing, 12, 28, st);
        rect(c, ox + w - 2, oy + 18 - swing, 12, 28, st);
        rect(c, ox - 12, oy + 42 + swing, 16, 10, stL);
        rect(c, ox + w - 4, oy + 42 - swing, 16, 10, stL);
        // nemes headdress
        rect(c, ox + 8, oy - 2, w - 16, 20, gold);
        rect(c, ox + 4, oy + 4, 8, 18, gold);
        rect(c, ox + w - 12, oy + 4, 8, 18, gold);
        for (int i = 0; i < 4; i++) {
            rect(c, ox + 8, oy + i * 5, w - 16, 2, 0xFF3050A0);
        }
        rect(c, ox + 12, oy + 6, w - 24, 12, 0xFFC8B888);
        p.setColor(0xFF201810);
        c.drawRect(ox + 15, oy + 10, ox + 20, oy + 13, p);
        c.drawRect(ox + w - 20, oy + 10, ox + w - 15, oy + 13, p);
        // runes
        for (int i = 0; i < 4; i++) {
            float[] rp = runePos(i);
            float rx = rp[0] - camX, ry = rp[1] - camY;
            if (runeHp[i] <= 0) {
                p.setColor(0xFF241E18);
                c.drawRect(rx - 5, ry - 5, rx + 5, ry + 5, p);
                continue;
            }
            boolean lit = i == litRune;
            float pulse = lit ? (0.55f + 0.45f * (float) Math.sin(anim * 10)) : 0.28f;
            p.setColor(Fx.blend(0xFF4A2050, 0xFFE878C8, pulse));
            c.drawRect(rx - 6, ry - 6, rx + 6, ry + 6, p);
            p.setColor(Fx.blend(0xFF8A40A0, 0xFFFFC8F0, pulse));
            c.drawRect(rx - 3, ry - 4, rx - 1, ry + 4, p);
            c.drawRect(rx - 3, ry - 4, rx + 3, ry - 2, p);
            c.drawRect(rx + 1, ry, rx + 3, ry + 4, p);
            if (lit) {
                p.setColor(0x55E878C8);
                c.drawCircle(rx, ry, 10 + pulse * 5, p);
            }
        }
        if (litRune < 0) {
            // Core exposed once every rune is gone.
            float pulse = 0.5f + 0.5f * (float) Math.sin(anim * 12);
            p.setColor(Fx.blend(0xFFB03020, 0xFFFFE060, pulse));
            c.drawRect(ox + w / 2f - 8, oy + 30, ox + w / 2f + 8, oy + 42, p);
            p.setColor(0x66FFD040);
            c.drawCircle(ox + w / 2f, oy + 36, 14 + pulse * 6, p);
        }
    }

    // --- factory core: spider machine around a glowing "A"
    private void drawCore(Canvas c, float ox, float oy) {
        int mD = 0xFF343A44, mM = 0xFF6A7480, mL = 0xFF9AA6B0, brass = 0xFFB08840;
        // legs
        for (int s = -1; s <= 1; s += 2)
            for (int i = 0; i < 3; i++) {
                float bx = s < 0 ? ox + 6 : ox + w - 6;
                float ang = (float) Math.sin(anim * 2 + i) * 4;
                rect(c, bx + s * (i * 8), oy + 26 + i * 4, 5, 20 + ang, mM);
                rect(c, bx + s * (i * 8 + 4), oy + 44 + i * 4 + ang, 5, 16, mD);
            }
        // hull
        rect(c, ox + 4, oy + 8, w - 8, 34, mM);
        rect(c, ox + 4, oy + 8, w - 8, 4, mL);
        rect(c, ox + 4, oy + 38, w - 8, 4, mD);
        rect(c, ox + 2, oy + 14, w - 4, 22, brass);
        rect(c, ox + 2, oy + 14, w - 4, 3, 0xFFD8B060);
        // core well
        float ccx = ox + w / 2f, ccy = oy + 25;
        p.setColor(0xFF1A1410);
        c.drawCircle(ccx, ccy, 15, p);
        float pulse = 0.55f + 0.45f * (float) Math.sin(anim * (7 + rage * 8));
        int glow = Fx.blend(0xFFC06010, 0xFFFFF0A0, pulse * (0.4f + openness * 0.6f));
        p.setColor(glow);
        c.drawCircle(ccx, ccy, 12, p);
        p.setColor(0xFF2A1808);
        // stylised "A" in the core
        c.drawRect(ccx - 5, ccy - 6, ccx - 2, ccy + 7, p);
        c.drawRect(ccx + 2, ccy - 6, ccx + 5, ccy + 7, p);
        c.drawRect(ccx - 5, ccy - 7, ccx + 5, ccy - 4, p);
        c.drawRect(ccx - 5, ccy, ccx + 5, ccy + 2, p);
        // shutters slide over the core
        float sh = shutter * 15;
        p.setColor(mL);
        c.drawRect(ccx - 16, ccy - 16, ccx + 16, ccy - 16 + sh, p);
        c.drawRect(ccx - 16, ccy + 16 - sh, ccx + 16, ccy + 16, p);
        p.setColor(mD);
        c.drawRect(ccx - 16, ccy - 17 + sh, ccx + 16, ccy - 15 + sh, p);
        c.drawRect(ccx - 16, ccy + 15 - sh, ccx + 16, ccy + 17 - sh, p);
        if (openness > 0.4f) {
            p.setColor(0x55FFC040);
            c.drawCircle(ccx, ccy, 20 + pulse * 8, p);
        }
        // sensor lights
        for (int i = 0; i < 4; i++) {
            p.setColor(((int) (anim * 6) % 4 == i) ? 0xFF60F060 : 0xFF204020);
            c.drawRect(ox + 8 + i * 6, oy + 10, ox + 12 + i * 6, oy + 13, p);
        }
        // arm pods
        rect(c, ox - 8, oy + 20, 12, 14, mM);
        rect(c, ox + w - 4, oy + 20, 12, 14, mM);
        p.setColor(0xFFFF6020);
        c.drawRect(ox - 8, oy + 26, ox - 4, oy + 30, p);
        c.drawRect(ox + w + 4, oy + 26, ox + w + 8, oy + 30, p);
    }

    /** Hint line shown under the boss health bar. */
    public String hint() {
        switch (kind) {
            case TITAN: return openness > 0.4f ? "БЕЙ В ЯДРО!" : "ЖДИ УДАРА КЛЕШНЁЙ";
            case ARCHON: return openness > 0.4f ? "БЕЙ ПОКА ЗАРЯЖАЕТ!" : "УКЛОНЯЙСЯ ОТ МОЛНИЙ";
            case GLACIODON: return openness > 0.35f ? "БЕЙ В ПАСТЬ!" : "ЖДИ ВДОХА";
            case GOLEM: return litRune >= 0 ? "РАЗБЕЙ СВЕТЯЩУЮСЯ РУНУ" : "ЯДРО ОТКРЫТО — ДОБЕЙ!";
            default: return shutter > 0.35f ? "ЖДИ ОТКРЫТИЯ ЗАСЛОНОК" : "БЕЙ В ЯДРО!";
        }
    }
}
