package com.bromobile.game;

import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Every projectile in the game — player bullets, thrown grenades, enemy shots
 * and boss ordnance — driven by one data-oriented class with a render style.
 */
public final class Shot {

    public static final int BULLET = 0, PELLET = 1, ROCKET = 2, GRENADE = 3, FLAME = 4,
            ORB = 5, ARROW = 6, SHARD = 7, SPEAR = 8, PLASMA = 9, FIREBALL = 10,
            SAW = 11, BEAM = 12;

    public float x, y, vx, vy;
    public float life;
    public float damage;
    public float blast;          // > 0 means it detonates
    public int style;
    public boolean fromPlayer;
    public float gravity;
    public int bounces;
    public float homing;         // radians/sec of steering toward the player
    public int color;
    public boolean remove;
    public float size = 2;
    public float age;
    public boolean pierces;
    public float wobble;

    private static final Paint P = new Paint();

    static {
        P.setAntiAlias(false);
        P.setFilterBitmap(false);
    }

    public Shot set(float x, float y, float vx, float vy, float life, float dmg,
                    int style, boolean fromPlayer, int color) {
        this.x = x; this.y = y; this.vx = vx; this.vy = vy;
        this.life = life; this.damage = dmg; this.style = style;
        this.fromPlayer = fromPlayer; this.color = color;
        this.blast = 0; this.gravity = 0; this.bounces = 0; this.homing = 0;
        this.remove = false; this.age = 0; this.size = 2;
        this.pierces = false; this.wobble = 0;
        return this;
    }

    public void update(float dt, World world) {
        age += dt;
        life -= dt;
        if (life <= 0) {
            if (blast > 0) detonate(world);
            remove = true;
            return;
        }

        if (homing > 0 && world.player != null && world.player.alive()) {
            float tx = world.player.cx() - x, ty = world.player.cy() - y;
            float cur = (float) Math.atan2(vy, vx);
            float want = (float) Math.atan2(ty, tx);
            float d = want - cur;
            while (d > Math.PI) d -= (float) (Math.PI * 2);
            while (d < -Math.PI) d += (float) (Math.PI * 2);
            float turn = Math.max(-homing * dt, Math.min(homing * dt, d));
            float sp = (float) Math.sqrt(vx * vx + vy * vy);
            cur += turn;
            vx = (float) Math.cos(cur) * sp;
            vy = (float) Math.sin(cur) * sp;
        }

        vy += gravity * dt;

        float nx = x + vx * dt, ny = y + vy * dt;
        Level l = world.level;

        // --- terrain ---
        if (l.solidAt(nx, ny)) {
            if (blast > 0) {
                x = nx; y = ny;
                detonate(world);
                remove = true;
                return;
            }
            if (bounces > 0) {
                bounces--;
                if (!l.solidAt(x, ny)) { vx = -vx * 0.5f; nx = x; }
                else if (!l.solidAt(nx, y)) { vy = -vy * 0.45f; vx *= 0.7f; ny = y; }
                else { vx = -vx * 0.4f; vy = -vy * 0.4f; nx = x; ny = y; }
                world.sfx.playAt(Sfx.RICOCHET, world.distToCam(x, y), 0.35f, 1.3f);
            } else {
                // Small arms splinter crates but leave the terrain itself to
                // explosives — otherwise sustained fire dissolves the level.
                int tx = (int) (nx / Level.TS), ty = (int) (ny / Level.TS);
                if ((style == BULLET || style == PELLET || style == SHARD)
                        && l.get(tx, ty) == Level.CRATE)
                    l.damage(tx, ty, 1, world.fx);
                world.fx.sparks(nx, ny, style == FLAME ? 2 : 4,
                        style == FLAME ? 0xFFFF8030 : 0xFFFFD070);
                if (style != FLAME)
                    world.sfx.playAt(Sfx.RICOCHET, world.distToCam(x, y), 0.3f, 0.9f + world.rndF() * 0.5f);
                remove = true;
                return;
            }
        }
        x = nx;
        y = ny;

        if (x < world.level.w * Level.TS * -0.1f || x > world.level.w * Level.TS * 1.1f
                || y > world.level.h * Level.TS + 200 || y < -400) {
            remove = true;
            return;
        }

        // --- trails ---
        if (style == ROCKET) {
            world.fx.add(Fx.SMOKE, x, y, -vx * 0.06f, -vy * 0.06f + 6, 0.5f, 2, 0x99807870, -10);
            if (((int) (age * 60)) % 2 == 0)
                world.fx.add(Fx.FIRE, x, y, -vx * 0.1f, -vy * 0.1f, 0.14f, 2, 0xFFFFC040, 0);
        } else if (style == GRENADE) {
            if (((int) (age * 30)) % 2 == 0)
                world.fx.add(Fx.SMOKE, x, y, 0, -8, 0.28f, 1, 0x77A0A0A0, -6);
        } else if (style == PLASMA || style == ORB || style == FIREBALL) {
            world.fx.add(Fx.PLASMA, x, y, -vx * 0.05f, -vy * 0.05f, 0.2f, 2, color, 0);
        } else if (style == FLAME) {
            world.fx.add(Fx.FIRE, x, y, vx * 0.12f, vy * 0.12f - 12, 0.24f, 2.5f, 0xFFFF9030, -14);
        }

        // --- entities ---
        if (fromPlayer) {
            for (int i = 0; i < world.enemies.size(); i++) {
                Enemy e = world.enemies.get(i);
                if (e.remove || !e.hittable()) continue;
                if (!e.overlapsBox(x - 1, y - 1, 3, 3)) continue;
                if (e.blocksFrom(vx)) {
                    world.fx.sparks(x, y, 5, 0xFFFFE0A0);
                    world.sfx.playAt(Sfx.RICOCHET, world.distToCam(x, y), 0.5f, 1.1f);
                    if (blast > 0) { detonate(world); }
                    remove = true;
                    return;
                }
                hit(e, world);
                if (!pierces) return;
            }
            if (world.boss != null && !world.boss.remove && world.boss.hittable()
                    && world.boss.hitTest(x, y)) {
                if (world.boss.armoredAt(x, y)) {
                    world.fx.sparks(x, y, 6, 0xFFCFE0FF);
                    world.sfx.playAt(Sfx.RICOCHET, world.distToCam(x, y), 0.6f, 0.8f);
                    world.fx.text("БРОНЯ", x, y - 6, 0xFFA0C8FF);
                    if (blast > 0) detonate(world);
                    remove = true;
                    return;
                }
                if (blast > 0) {
                    detonate(world);
                } else {
                    world.boss.hurt(damage, world, x, y);
                    world.fx.sparks(x, y, 4, 0xFFFFD070);
                }
                if (!pierces) { remove = true; return; }
            }
        } else {
            Player p = world.player;
            if (p != null && p.alive() && !p.invulnerable()
                    && p.overlapsBox(x - 1, y - 1, 3, 3)) {
                if (blast > 0) detonate(world);
                else p.kill(world, "SHOT");
                remove = true;
                return;
            }
            // Enemy fire frees prisoners too — and destroys crates in the way.
            if (world.hitProps(x, y, damage)) {
                if (blast > 0) detonate(world);
                remove = true;
            }
        }
    }

    private void hit(Enemy e, World world) {
        if (blast > 0) {
            detonate(world);
            remove = true;
            return;
        }
        e.damage(damage, world, vx * 0.02f);
        world.fx.blood(x, y, vx, vy, 4, e.bloodColor());
        world.sfx.playAt(Sfx.RICOCHET, world.distToCam(x, y), 0.25f, 1.4f);
        if (!pierces) remove = true;
    }

    private void detonate(World world) {
        world.explode(x, y, blast, damage, fromPlayer, style == FIREBALL);
        remove = true;
    }

    // ------------------------------------------------------------------

    public void draw(Canvas c, float camX, float camY) {
        float sx = x - camX, sy = y - camY;
        switch (style) {
            case BULLET:
            case PELLET: {
                P.setColor(0x66FFCC60);
                c.drawRect(sx - vx * 0.014f, sy - vy * 0.014f, sx + 1, sy + 1, P);
                P.setColor(color);
                c.drawRect(sx - 1, sy - 1, sx + 2, sy + 1, P);
                P.setColor(0xFFFFF8D0);
                c.drawRect(sx, sy - 1, sx + 1, sy, P);
                break;
            }
            case ARROW: {
                P.setColor(0xFF6A4A2C);
                float a = (float) Math.atan2(vy, vx);
                float dx = (float) Math.cos(a) * 5, dy = (float) Math.sin(a) * 5;
                c.drawRect(sx - dx, sy - dy, sx - dx + 1.5f, sy - dy + 1.5f, P);
                c.drawRect(sx - dx / 2, sy - dy / 2, sx - dx / 2 + 1.5f, sy - dy / 2 + 1.5f, P);
                P.setColor(0xFFD8D8E0);
                c.drawRect(sx - 1, sy - 1, sx + 2, sy + 2, P);
                break;
            }
            case ROCKET: {
                P.setColor(0xFF3A4048);
                c.drawRect(sx - 3, sy - 2, sx + 3, sy + 2, P);
                P.setColor(0xFFC03020);
                c.drawRect(sx + 1, sy - 2, sx + 3, sy + 2, P);
                P.setColor(0xFFFFD060);
                c.drawRect(sx - 5, sy - 1, sx - 3, sy + 1, P);
                break;
            }
            case GRENADE: {
                int rot = (int) (age * 9) % 4;
                P.setColor(0xFF2C4020);
                c.drawRect(sx - 2, sy - 2, sx + 3, sy + 3, P);
                P.setColor(0xFF4A6B2E);
                c.drawRect(sx - 1, sy - 2, sx + 2, sy + 1, P);
                P.setColor(rot < 2 ? 0xFFFFE060 : 0xFFFF6020);
                c.drawRect(sx - 3 + rot, sy - 4, sx - 2 + rot, sy - 3, P);
                break;
            }
            case FLAME:
                break;   // rendered entirely by its particle trail
            case SHARD: {
                P.setColor(0xFFB8E4F8);
                c.drawRect(sx - 2, sy - 1, sx + 3, sy + 1, P);
                P.setColor(0xFFF0FCFF);
                c.drawRect(sx, sy - 1, sx + 2, sy, P);
                break;
            }
            case SPEAR: {
                float a = (float) Math.atan2(vy, vx);
                float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
                P.setColor(0xFF6A4A2C);
                for (int i = -6; i <= 0; i++)
                    c.drawRect(sx + dx * i, sy + dy * i, sx + dx * i + 1.5f, sy + dy * i + 1.5f, P);
                P.setColor(0xFFD8C880);
                c.drawRect(sx - 1, sy - 1, sx + 3, sy + 2, P);
                break;
            }
            case SAW: {
                int k = (int) (age * 22) % 4;
                P.setColor(0xFF9AA6B0);
                c.drawCircle(sx, sy, size + 1, P);
                P.setColor(0xFFE0E8F0);
                for (int i = 0; i < 4; i++) {
                    double a = (i * Math.PI / 2) + k * 0.4;
                    c.drawRect(sx + (float) Math.cos(a) * size - 1, sy + (float) Math.sin(a) * size - 1,
                            sx + (float) Math.cos(a) * size + 2, sy + (float) Math.sin(a) * size + 2, P);
                }
                P.setColor(0xFF3A4048);
                c.drawCircle(sx, sy, size * 0.4f, P);
                break;
            }
            case ORB:
            case PLASMA:
            case FIREBALL:
            default: {
                float pulse = 1 + 0.25f * (float) Math.sin(age * 22);
                P.setColor((color & 0x00FFFFFF) | 0x55000000);
                c.drawCircle(sx, sy, (size + 2) * pulse, P);
                P.setColor(color);
                c.drawCircle(sx, sy, size * pulse, P);
                P.setColor(0xFFFFFFFF);
                c.drawCircle(sx - size * 0.25f, sy - size * 0.25f, size * 0.4f, P);
                break;
            }
        }
    }
}
