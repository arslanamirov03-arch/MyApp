package com.bromobile.game;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * The bro. One bullet kills him; he respawns where he fell until his three
 * lives are gone, at which point the level restarts from the last flag.
 */
public final class Player extends Mob {

    public static final int AK = 0, GRENADE = 1, SHOTGUN = 2, ROCKET = 3, FLAMER = 4, WEAPONS = 5;

    public static final String[] WEAPON_NAME = {"AK-47", "ГРАНАТА", "ДРОБОВИК", "РАКЕТА", "ОГНЕМЁТ"};

    private static final float RUN = 96f, ACCEL = 900f, FRICTION = 1200f, AIR_ACCEL = 480f;
    private static final float GRAVITY = 640f, JUMP_V = -222f, MAX_FALL = 380f;
    private static final float STAND_H = 14f, CROUCH_H = 9f;

    public int weapon = AK;
    public final boolean[] unlocked = new boolean[WEAPONS];
    public final int[] ammo = new int[WEAPONS];
    public int grenades = 4;

    public boolean crouching;
    public boolean dead;
    public float deathTimer;
    public float invuln;
    public float respawnX, respawnY;

    private float cooldown;
    private float animTime;
    private int animFrame;
    private float coyote, jumpBuffer;
    private boolean jumpHolding;
    private float jumpHold;
    private float meleeTime;
    private float recoil;
    private float onLadder;
    private float dropThrough;
    private float flamerTick;
    private float walkDust;

    public int combo;
    public float comboTimer;

    private final Paint p = new Paint();

    public Player(float sx, float sy) {
        w = 8;
        h = STAND_H;
        x = sx;
        y = sy;
        respawnX = sx;
        respawnY = sy;
        canStepUp = true;
        unlocked[AK] = true;
        unlocked[GRENADE] = true;
        ammo[AK] = -1;
        p.setAntiAlias(false);
        p.setFilterBitmap(false);
    }

    public boolean alive() { return !dead; }

    public boolean invulnerable() { return invuln > 0; }

    public int ammoLeft() {
        if (weapon == GRENADE) return grenades;
        return ammo[weapon];
    }

    public String weaponName() { return WEAPON_NAME[weapon]; }

    public Bitmap weaponIcon() {
        switch (weapon) {
            case GRENADE: return Art.wGrenade;
            case SHOTGUN: return Art.wShotgun;
            case ROCKET: return Art.wRocket;
            case FLAMER: return Art.wFlamer;
            default: return Art.wRifle;
        }
    }

    public void giveWeapon(int wp, int rounds) {
        unlocked[wp] = true;
        ammo[wp] += rounds;
        weapon = wp;
    }

    public void cycleWeapon(int dir, Sfx sfx) {
        for (int i = 1; i <= WEAPONS; i++) {
            int n = ((weapon + dir * i) % WEAPONS + WEAPONS) % WEAPONS;
            if (!unlocked[n]) continue;
            if (n == GRENADE ? grenades > 0 : (ammo[n] != 0)) {
                weapon = n;
                if (sfx != null) sfx.play(Sfx.SWITCH);
                return;
            }
        }
    }

    /** Falls back to the rifle when the current weapon runs dry. */
    private void checkAmmo() {
        if (weapon == AK) return;
        int left = weapon == GRENADE ? grenades : ammo[weapon];
        if (left <= 0) {
            if (weapon != GRENADE) unlocked[weapon] = false;
            weapon = AK;
        }
    }

    // ------------------------------------------------------------------

    @Override
    public void update(float dt, World world) {
        if (dead) {
            deathTimer -= dt;
            return;
        }
        Controls in = world.controls;
        Level l = world.level;

        if (invuln > 0) invuln -= dt;
        if (hurtFlash > 0) hurtFlash -= dt;
        if (recoil > 0) recoil -= dt * 5;
        if (meleeTime > 0) meleeTime -= dt;
        if (cooldown > 0) cooldown -= dt;
        if (dropThrough > 0) dropThrough -= dt;
        if (comboTimer > 0) {
            comboTimer -= dt;
            if (comboTimer <= 0) combo = 0;
        }

        float axis = in.runAxis();
        boolean wantCrouch = in.crouchHeld && onGround;

        // --- ladders ---
        boolean onLadderNow = l.ladderAt(cx(), cy()) || l.ladderAt(cx(), feet() - 2);
        onLadder = onLadderNow ? 1 : 0;
        if (onLadderNow && (in.moveY < -0.35f || (in.moveY > 0.35f && !onGround))) {
            vy = in.moveY * 74f;
            vx = axis * RUN * 0.55f;
            moveX(l, vx * dt);
            moveY(l, vy * dt, false);
            animTime += dt * 5;
            crouching = false;
            fireLogic(dt, world, in);
            return;
        }

        // --- crouch changes the hitbox from the feet up ---
        if (wantCrouch != crouching) {
            if (wantCrouch) {
                y += STAND_H - CROUCH_H;
                h = CROUCH_H;
                crouching = true;
            } else if (!l.boxHits(x, y - (STAND_H - CROUCH_H), w, STAND_H)) {
                y -= STAND_H - CROUCH_H;
                h = STAND_H;
                crouching = false;
            }
        }

        // --- horizontal ---
        float target = axis * RUN * (crouching ? 0.42f : 1f);
        float accel = onGround ? ACCEL : AIR_ACCEL;
        if (axis != 0) {
            vx += Math.signum(target - vx) * accel * dt;
            if ((target > 0 && vx > target) || (target < 0 && vx < target)) vx = target;
            face = axis > 0 ? 1 : -1;
        } else {
            float f = (onGround ? FRICTION : FRICTION * 0.25f) * dt;
            if (Math.abs(vx) <= f) vx = 0;
            else vx -= Math.signum(vx) * f;
        }

        // --- jump ---
        if (onGround) coyote = 0.1f;
        else if (coyote > 0) coyote -= dt;
        if (in.jumpPressed) jumpBuffer = 0.14f;
        else if (jumpBuffer > 0) jumpBuffer -= dt;

        if (jumpBuffer > 0 && coyote > 0) {
            // Crouch + jump drops through a one-way platform.
            if (crouching && l.get((int) (cx() / Level.TS), (int) ((feet() + 2) / Level.TS)) == Level.PLATFORM) {
                dropThrough = 0.22f;
                y += 3;
            } else {
                vy = JUMP_V;
                jumpHolding = true;
                jumpHold = 0.20f;
                onGround = false;
                world.fx.smokePuff(cx(), feet(), 3, 0x77B0A898);
                world.sfx.play(Sfx.JUMP, 0.55f, 0.95f + world.rndF() * 0.15f);
            }
            jumpBuffer = 0;
            coyote = 0;
        }
        if (jumpHolding) {
            jumpHold -= dt;
            if (!in.jumpHeld || jumpHold <= 0) {
                jumpHolding = false;
                if (vy < -70) vy = -70;   // variable jump height
            }
        }

        boolean wasAir = !onGround;
        ignorePlatforms = dropThrough > 0;
        physics(dt, l, GRAVITY, MAX_FALL);
        ignorePlatforms = false;

        if (wasAir && onGround) {
            world.fx.smokePuff(cx(), feet(), 4, 0x66B0A898);
            world.sfx.play(Sfx.LAND, 0.4f, 1f);
        }

        // Running dust
        if (onGround && Math.abs(vx) > 40) {
            walkDust -= dt;
            if (walkDust <= 0) {
                walkDust = 0.09f;
                world.fx.add(Fx.DUST, cx() - face * 3, feet() - 1,
                        -face * 20, -14, 0.3f, 1, 0x66C0B8A8, 30);
            }
        }

        // --- hazards ---
        if (l.boxHitsSpike(x, y, w, h)) {
            kill(world, "SPIKE");
            return;
        }
        if (y > l.h * Level.TS + 40) {
            kill(world, "PIT");
            return;
        }

        // --- animation ---
        if (onGround) {
            if (Math.abs(vx) > 12) {
                animTime += dt * (6 + Math.abs(vx) / 22);
                animFrame = ((int) animTime) & 3;
            } else {
                animTime += dt * 2.4f;
                animFrame = 0;
            }
        }

        fireLogic(dt, world, in);
    }

    private void fireLogic(float dt, World world, Controls in) {
        if (in.weaponNext) cycleWeapon(1, world.sfx);
        if (in.weaponPrev) cycleWeapon(-1, world.sfx);

        if (in.meleePressed && meleeTime <= 0) {
            meleeTime = 0.26f;
            world.melee(this);
        }

        if (in.grenadePressed && grenades > 0 && cooldown <= 0) {
            throwGrenade(world);
            cooldown = 0.42f;
        }

        boolean fire = in.firing || (world.save.autoFire && world.enemyNear(this));
        if (fire && cooldown <= 0) shoot(world);
    }

    private float mzX, mzY;

    /**
     * Muzzle position, pulled back out of any wall the barrel is buried in.
     * Standing flush against a wall would otherwise spawn every bullet inside
     * solid rock, where it dies on its first step and the gun goes silent.
     */
    private void muzzle(World world) {
        mzY = y + (crouching ? 3.5f : 5.5f);
        float tip = cx() + face * (crouching ? 10 : 11) - recoil * face * 3;
        mzX = tip;
        if (world.level.solidAt(mzX, mzY)) {
            mzX = cx();
            if (world.level.solidAt(mzX, mzY)) mzX = cx() - face * 4;
        }
    }

    /**
     * Aim assist. There is no manual aiming on a touchscreen, so the shot is
     * steered onto the best target inside a wide cone ahead — flyers overhead
     * and boss weak points included, otherwise they would be unreachable.
     */
    private float aimAngle(World world, float spreadDeg) {
        float base = face > 0 ? 0 : (float) Math.PI;
        float bx = mzX, by = mzY;
        float best = base;
        float bestScore = Float.MAX_VALUE;
        float lim = (float) Math.toRadians(42 + spreadDeg);

        Boss b = world.boss;
        // While a boss has its weak point open, that window is short and the
        // fight is the point: escorts must not steal the aim assist.
        boolean bossFirst = b != null && !b.remove && b.hittable() && b.vulnerable();

        if (!bossFirst) {
            for (int i = 0; i < world.enemies.size(); i++) {
                Enemy e = world.enemies.get(i);
                if (e.remove || !e.hittable()) continue;
                float dx = e.cx() - bx, dy = e.cy() - by;
                if (dx * face < 0) continue;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d > 210) continue;
                float off = angleOff(dx, dy, base);
                if (Math.abs(off) > lim) continue;
                if (!world.level.lineClear(bx, by, e.cx(), e.cy())) continue;
                float score = d + Math.abs(off) * 130;
                if (score < bestScore) {
                    bestScore = score;
                    best = (float) Math.atan2(dy, dx);
                }
            }
        }

        if (b != null && !b.remove && b.hittable()) {
            // Prefer the weak point; a wider cone so the fight works from the floor.
            float tx = b.vulnerable() ? b.weakX() : b.cx();
            float ty = b.vulnerable() ? b.weakY() : b.cy();
            float dx = tx - bx, dy = ty - by;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float off = angleOff(dx, dy, base);
            if (dx * face >= 0 && d < 320 && Math.abs(off) < (float) Math.toRadians(76)) {
                float score = (b.vulnerable() ? d * 0.4f : d) + Math.abs(off) * 90;
                if (score < bestScore) best = (float) Math.atan2(dy, dx);
            }
        }
        return best;
    }

    private static float angleOff(float dx, float dy, float base) {
        float off = (float) Math.atan2(dy, dx) - base;
        while (off > Math.PI) off -= (float) (Math.PI * 2);
        while (off < -Math.PI) off += (float) (Math.PI * 2);
        return off;
    }

    private void shoot(World world) {
        muzzle(world);
        float mx = mzX, my = mzY;
        switch (weapon) {
            case AK: {
                cooldown = 0.085f;
                float a = aimAngle(world, 0) + (world.rndF() - 0.5f) * 0.05f;
                Shot s = world.newShot();
                s.set(mx, my, (float) Math.cos(a) * 430, (float) Math.sin(a) * 430,
                        1.5f, 2.4f, Shot.BULLET, true, 0xFFFFD860);
                world.fx.muzzle(mx, my, face, 1f);
                world.fx.shell(cx(), my, face);
                world.sfx.play(Sfx.SHOOT, 0.55f, 0.95f + world.rndF() * 0.16f);
                recoil = 0.18f;
                vx -= face * 6;
                world.fx.kick(0.7f);
                break;
            }
            case GRENADE: {
                if (grenades <= 0) { checkAmmo(); return; }
                throwGrenade(world);
                cooldown = 0.5f;
                break;
            }
            case SHOTGUN: {
                if (ammo[SHOTGUN] <= 0) { checkAmmo(); return; }
                cooldown = 0.52f;
                ammo[SHOTGUN]--;
                float a = aimAngle(world, -6);
                for (int i = 0; i < 7; i++) {
                    float sa = a + (world.rndF() - 0.5f) * 0.34f;
                    float sp = 300 + world.rndF() * 90;
                    world.newShot().set(mx, my, (float) Math.cos(sa) * sp,
                            (float) Math.sin(sa) * sp, 0.35f, 2.2f, Shot.PELLET, true, 0xFFFFC050);
                }
                world.fx.muzzle(mx, my, face, 2.2f);
                world.fx.shell(cx(), my, face);
                world.sfx.play(Sfx.SHOTGUN, 0.8f, 0.95f + world.rndF() * 0.12f);
                world.sfx.buzz(18);
                recoil = 0.42f;
                vx -= face * 44;
                world.fx.kick(2.4f);
                checkAmmo();
                break;
            }
            case ROCKET: {
                if (ammo[ROCKET] <= 0) { checkAmmo(); return; }
                cooldown = 0.8f;
                ammo[ROCKET]--;
                float a = aimAngle(world, -12);
                Shot s = world.newShot();
                s.set(mx, my, (float) Math.cos(a) * 250, (float) Math.sin(a) * 250,
                        2.4f, 14f, Shot.ROCKET, true, 0xFFFF8030);
                s.blast = 40;
                world.fx.muzzle(mx, my, face, 2.6f);
                world.sfx.play(Sfx.ROCKET, 0.85f, 1f);
                world.sfx.buzz(22);
                recoil = 0.6f;
                vx -= face * 52;
                world.fx.kick(2.8f);
                checkAmmo();
                break;
            }
            case FLAMER: {
                if (ammo[FLAMER] <= 0) { checkAmmo(); return; }
                cooldown = 0.035f;
                ammo[FLAMER]--;
                float a = aimAngle(world, -4) + (world.rndF() - 0.5f) * 0.30f;
                float sp = 150 + world.rndF() * 70;
                Shot s = world.newShot();
                s.set(mx, my, (float) Math.cos(a) * sp, (float) Math.sin(a) * sp,
                        0.32f, 1.1f, Shot.FLAME, true, 0xFFFF9030);
                s.pierces = true;
                s.gravity = -40;
                flamerTick -= 0.035f;
                if (flamerTick <= 0) {
                    flamerTick = 0.14f;
                    world.sfx.play(Sfx.FLAME, 0.4f, 0.9f + world.rndF() * 0.3f);
                }
                recoil = 0.1f;
                checkAmmo();
                break;
            }
        }
    }

    private void throwGrenade(World world) {
        grenades--;
        muzzle(world);
        float mx = mzX, my = y + 4;
        Shot s = world.newShot();
        // Short arc: strong lob, heavy gravity — big boom, small range.
        s.set(mx, my, face * 138 + vx * 0.35f, -132, 2.2f, 9.5f, Shot.GRENADE, true, 0xFF4A6B2E);
        s.gravity = 520;
        s.blast = 36;
        s.bounces = 3;
        world.sfx.play(Sfx.THROW, 0.5f, 1f);
        checkAmmo();
    }

    // ------------------------------------------------------------------

    public void kill(World world, String cause) {
        if (dead || invuln > 0) return;
        dead = true;
        deathTimer = 1.05f;
        vx = vy = 0;
        world.onPlayerDeath(this, cause);
    }

    public void respawnAt(float rx, float ry) {
        dead = false;
        x = rx;
        y = ry;
        vx = vy = 0;
        h = STAND_H;
        crouching = false;
        invuln = 2.8f;
        onGround = false;
        combo = 0;
    }

    public void addKill(World world, float ex, float ey, int points) {
        combo++;
        comboTimer = 2.6f;
        int mult = Math.min(5, 1 + combo / 4);
        int gained = points * mult;
        world.score += gained;
        world.fx.text((mult > 1 ? "x" + mult + " " : "") + gained, ex, ey - 8,
                mult > 1 ? 0xFFFFD040 : 0xFFFFFFFF);
        if (combo == 8) world.fx.text("КОМБО!", ex, ey - 20, 0xFFFF8030);
        if (combo == 16) world.fx.text("БРОФОРС!", ex, ey - 20, 0xFFFF4040);
    }

    // ------------------------------------------------------------------

    @Override
    public void draw(Canvas c, float camX, float camY) {
        if (dead) return;
        if (invuln > 0 && ((int) (invuln * 22)) % 2 == 0) return;

        Bitmap body;
        boolean right = face > 0;
        if (crouching) body = right ? Art.broCrouch : Art.broCrouchL;
        else if (!onGround) body = right ? Art.broJump : Art.broJumpL;
        else if (Math.abs(vx) > 12) body = right ? Art.broRun[animFrame] : Art.broRunL[animFrame];
        else body = (((int) animTime) & 1) == 0
                    ? (right ? Art.broIdle : Art.broIdleL)
                    : (right ? Art.broIdle2 : Art.broIdle2L);

        float sx = cx() - 6 - camX;
        float sy = feet() - 16 - camY;

        p.setAlpha(255);
        c.drawBitmap(body, sx, sy, p);

        // Weapon in hand, nudged back by recoil.
        Bitmap gun = null;
        switch (weapon) {
            case AK: gun = right ? Art.wRifle : Art.wRifleL; break;
            case SHOTGUN: gun = right ? Art.wShotgun : Art.wShotgunL; break;
            case ROCKET: gun = right ? Art.wRocket : Art.wRocketL; break;
            case FLAMER: gun = right ? Art.wFlamer : Art.wFlamerL; break;
            case GRENADE: gun = right ? Art.wGrenade : Art.wGrenadeL; break;
        }
        if (gun != null) {
            float gy = sy + (crouching ? 11 : 9);
            float gx = right ? sx + 6 - recoil * 3 : sx + 6 - gun.getWidth() + recoil * 3;
            if (weapon == GRENADE) {
                gx = right ? sx + 9 : sx + 12 - gun.getWidth();
                gy = sy + (crouching ? 10 : 8);
            }
            c.drawBitmap(gun, gx, gy, p);
        }

        // Knife swipe arc.
        if (meleeTime > 0) {
            float k = 1 - meleeTime / 0.26f;
            p.setColor(0xCCFFFFFF);
            float ax = cx() - camX + face * (6 + k * 12);
            float ay = cy() - camY - 6 + k * 12;
            c.drawRect(ax, ay, ax + 3, ay + 3, p);
            p.setColor(0x88E0F0FF);
            c.drawRect(ax - face * 4, ay - 3, ax - face * 4 + 3, ay, p);
        }
    }
}
