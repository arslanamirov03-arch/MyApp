package com.bromobile.game;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Non-combat world objects: pickups, explosive barrels, rescuable prisoners,
 * checkpoint flags and the exit the level is built around.
 */
public final class Prop extends Mob {

    public static final int AMMO = 0, LIFE = 1, W_SHOTGUN = 2, W_ROCKET = 3, W_FLAMER = 4,
            PRISONER = 5, FLAG = 6, EXIT = 7, BARREL = 8;

    public final int kind;
    public boolean used;
    private float bob;
    private float anim;
    private float glow;
    private float fuse = -1;

    private static final Paint P = new Paint();

    static {
        P.setAntiAlias(false);
        P.setFilterBitmap(false);
    }

    public Prop(int kind, float px, float groundY) {
        this.kind = kind;
        switch (kind) {
            case PRISONER: w = 10; h = 15; break;
            case FLAG: w = 8; h = 20; break;
            case EXIT: w = 26; h = 40; break;
            case BARREL: w = 10; h = 15; hp = 3; break;
            case LIFE: w = 9; h = 8; break;
            default: w = 11; h = 10; break;
        }
        x = px - w / 2;
        y = groundY - h;
        bob = (px * 0.07f) % 6.28f;
    }

    public boolean isPickup() {
        return kind == AMMO || kind == LIFE || kind == W_SHOTGUN
                || kind == W_ROCKET || kind == W_FLAMER;
    }

    @Override
    public void update(float dt, World world) {
        anim += dt;
        bob += dt * 3.2f;
        if (glow > 0) glow -= dt;

        if (kind == BARREL) {
            physics(dt, world.level, 620f, 320f);
            vx *= 0.9f;
            if (fuse > 0) {
                fuse -= dt;
                if (fuse <= 0) {
                    world.explode(cx(), cy(), 46, 12, true, true);
                    world.level.blast(cx(), cy(), 34, 3, world.fx);
                    remove = true;
                }
            }
            return;
        }
        if (isPickup()) {
            physics(dt, world.level, 520f, 300f);
            vx *= 0.88f;
        }

        Player pl = world.player;
        if (pl == null || !pl.alive() || used) return;

        if (isPickup() && overlaps(pl)) {
            collect(world, pl);
            return;
        }
        if (kind == PRISONER && overlaps(pl)) {
            used = true;
            remove = true;
            world.rescued++;
            world.score += 500;
            world.fx.text("+500 СПАСЁН!", cx(), y - 6, 0xFF60E060);
            world.fx.sparks(cx(), cy(), 12, 0xFF80FF80);
            world.sfx.play(Sfx.RESCUE, 0.9f, 1f);
            world.sfx.buzz(24);
            if (world.rescued % 5 == 0) {
                world.lives++;
                world.fx.text("+1 ЖИЗНЬ", cx(), y - 18, 0xFFFF6060);
            }
            return;
        }
        if (kind == FLAG && !used && overlaps(pl)) {
            used = true;
            glow = 1.2f;
            world.setCheckpoint(cx(), feet());
            world.fx.text("КОНТРОЛЬНАЯ ТОЧКА", cx(), y - 10, 0xFFFFD040);
            world.fx.sparks(cx(), y, 10, 0xFFFFD040);
            world.sfx.play(Sfx.CHECKPOINT, 0.8f, 1f);
        }
    }

    private void collect(World world, Player pl) {
        used = true;
        remove = true;
        world.sfx.play(Sfx.PICKUP, 0.7f, 1f);
        switch (kind) {
            case AMMO:
                pl.grenades = Math.min(12, pl.grenades + 3);
                world.fx.text("+3 ГРАНАТЫ", cx(), y - 6, 0xFF9AD858);
                break;
            case LIFE:
                world.lives++;
                world.fx.text("+1 ЖИЗНЬ", cx(), y - 6, 0xFFFF6060);
                break;
            case W_SHOTGUN:
                pl.giveWeapon(Player.SHOTGUN, 16);
                world.fx.text("ДРОБОВИК!", cx(), y - 6, 0xFFFFD040);
                break;
            case W_ROCKET:
                pl.giveWeapon(Player.ROCKET, 6);
                world.fx.text("РАКЕТНИЦА!", cx(), y - 6, 0xFFFF8030);
                break;
            case W_FLAMER:
                pl.giveWeapon(Player.FLAMER, 130);
                world.fx.text("ОГНЕМЁТ!", cx(), y - 6, 0xFFFF6020);
                break;
        }
        world.score += 50;
        world.fx.sparks(cx(), cy(), 8, 0xFFFFE070);
    }

    @Override
    public void damage(float amount, World world, float knockX) {
        if (kind != BARREL) return;
        hp -= amount;
        if (hp <= 0 && fuse < 0) {
            fuse = 0.28f;
            world.sfx.playAt(Sfx.ALARM, world.distToCam(cx(), cy()), 0.5f, 2f);
        }
    }

    // ------------------------------------------------------------------

    @Override
    public void draw(Canvas c, float camX, float camY) {
        float sx = x - camX, sy = y - camY;
        float fl = (float) Math.sin(bob) * 1.6f;

        switch (kind) {
            case AMMO:
                c.drawBitmap(Art.pAmmo, sx, sy + fl, P);
                halo(c, sx + w / 2, sy + h / 2 + fl, 0x339AD858, 10);
                break;
            case LIFE:
                c.drawBitmap(Art.pLife, sx, sy + fl, P);
                halo(c, sx + w / 2, sy + h / 2 + fl, 0x33FF6060, 10);
                break;
            case W_SHOTGUN:
                weaponCrate(c, sx, sy + fl, Art.wShotgun, 0xFFB07030);
                break;
            case W_ROCKET:
                weaponCrate(c, sx, sy + fl, Art.wRocket, 0xFFC04030);
                break;
            case W_FLAMER:
                weaponCrate(c, sx, sy + fl, Art.wFlamer, 0xFFC06020);
                break;
            case PRISONER: {
                int f = ((int) (anim * 3)) & 1;
                c.drawBitmap(Art.prisoner[f], sx - 1, sy - 1, P);
                if (((int) (anim * 1.6f)) % 3 == 0) {
                    Font.shadow(c, "!", (int) (sx + 4), (int) (sy - 9), 0xFFFFE060, 1);
                }
                break;
            }
            case FLAG: {
                // Flag stays furled until it is activated.
                P.setColorFilter(used ? null : GREY);
                c.drawBitmap(Art.flag, sx - 1 + (used ? (float) Math.sin(anim * 5) * 0.6f : 0), sy, P);
                P.setColorFilter(null);
                if (glow > 0) halo(c, sx + 4, sy + 4, 0x66FFD040, 14 * glow + 6);
                break;
            }
            case BARREL: {
                if (fuse > 0) {
                    int b = (int) (fuse * 30) % 2;
                    P.setColorFilter(b == 0 ? FLASH : null);
                }
                c.drawBitmap(Art.barrel, sx - 1, sy, P);
                P.setColorFilter(null);
                break;
            }
            case EXIT:
                drawExit(c, sx, sy);
                break;
        }
    }

    private static final android.graphics.PorterDuffColorFilter GREY =
            new android.graphics.PorterDuffColorFilter(0xFF606068,
                    android.graphics.PorterDuff.Mode.MULTIPLY);
    private static final android.graphics.PorterDuffColorFilter FLASH =
            new android.graphics.PorterDuffColorFilter(0xE0FFFFFF,
                    android.graphics.PorterDuff.Mode.SRC_ATOP);

    private void weaponCrate(Canvas c, float sx, float sy, android.graphics.Bitmap icon, int tint) {
        P.setColor(0xFF201810);
        c.drawRect(sx - 1, sy - 1, sx + w + 1, sy + h + 1, P);
        P.setColor(tint);
        c.drawRect(sx, sy, sx + w, sy + h, P);
        P.setColor(0x66FFFFFF);
        c.drawRect(sx, sy, sx + w, sy + 1, P);
        float k = Math.min((w - 2f) / icon.getWidth(), (h - 2f) / icon.getHeight());
        c.drawBitmap(icon, null, new android.graphics.RectF(
                sx + (w - icon.getWidth() * k) / 2, sy + (h - icon.getHeight() * k) / 2,
                sx + (w + icon.getWidth() * k) / 2, sy + (h + icon.getHeight() * k) / 2), P);
        halo(c, sx + w / 2, sy + h / 2, 0x33FFD040, 11);
    }

    private void halo(Canvas c, float cxp, float cyp, int color, float r) {
        P.setColor(color);
        c.drawCircle(cxp, cyp, r, P);
    }

    /**
     * Level exit — themed as a chapel door in the city, a portal in the sky, an
     * ice gate, a temple entrance or a blast door in the factory.
     */
    private void drawExit(Canvas c, float sx, float sy) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(anim * 3);
        P.setColor(0xFF1A1620);
        c.drawRect(sx - 3, sy - 6, sx + w + 3, sy + h, P);

        int frame, door, trim;
        switch (theme) {
            case Theme.SKY: frame = 0xFFE8E0C8; door = 0xFF3A5A96; trim = 0xFFE8C060; break;
            case Theme.ICE: frame = 0xFF9AC8E4; door = 0xFF2A4A66; trim = 0xFFD8F0FC; break;
            case Theme.RUINS: frame = 0xFF6E6A50; door = 0xFF201C14; trim = 0xFFD8B040; break;
            case Theme.FACTORY: frame = 0xFF6A7480; door = 0xFF20242A; trim = 0xFFFFA828; break;
            default: frame = 0xFF7A5238; door = 0xFF201410; trim = 0xFFE8DCC0; break;
        }
        // Surround
        P.setColor(frame);
        c.drawRect(sx - 3, sy - 6, sx + w + 3, sy + h, P);
        P.setColor(Fx.blend(frame, 0xFFFFFFFF, 0.25f));
        c.drawRect(sx - 3, sy - 6, sx + w + 3, sy - 3, P);
        // Arch
        P.setColor(door);
        c.drawRect(sx + 2, sy, sx + w - 2, sy + h, P);
        for (int i = 0; i < 5; i++)
            c.drawRect(sx + 2 + i, sy - i, sx + w - 2 - i, sy + 1 - i, P);
        // Inner glow that pulls the eye
        P.setColor(Fx.blend(0x00000000, trim, 0.25f + pulse * 0.35f));
        c.drawRect(sx + 5, sy + 6, sx + w - 5, sy + h - 2, P);
        P.setColor(Fx.blend(door, trim, pulse * 0.8f));
        c.drawRect(sx + w / 2f - 2, sy + 8, sx + w / 2f + 2, sy + h - 4, P);
        // Keystone / lamp
        P.setColor(trim);
        c.drawRect(sx + w / 2f - 3, sy - 9, sx + w / 2f + 3, sy - 4, P);
        P.setColor(Fx.blend(trim, 0xFFFFFFFF, pulse));
        c.drawRect(sx + w / 2f - 2, sy - 8, sx + w / 2f + 2, sy - 5, P);

        if (theme == Theme.CITY) {
            // Clock face above the chapel door, echoing the map art.
            P.setColor(0xFFE8DCC0);
            c.drawCircle(sx + w / 2f, sy - 16, 7, P);
            P.setColor(0xFF3A2C28);
            c.drawRect(sx + w / 2f - 1, sy - 21, sx + w / 2f + 1, sy - 16, P);
            c.drawRect(sx + w / 2f, sy - 16, sx + w / 2f + 4, sy - 15, P);
        }

        // "ВЫХОД" beacon
        if (((int) (anim * 2)) % 2 == 0) {
            Font.shadow(c, "ВЫХОД", (int) (sx + w / 2 - Font.width("ВЫХОД", 1) / 2),
                    (int) (sy - 30), 0xFFFFE060, 1);
        }
        P.setColor((((int) (60 + 60 * pulse)) << 24) | (trim & 0x00FFFFFF));
        c.drawCircle(sx + w / 2f, sy + h / 2f, 16 + pulse * 5, P);
    }

    public int theme;
}
