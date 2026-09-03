package com.bromobile.game;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Enemies are built from seven silhouettes (grunt, shielded heavy, flyer,
 * crawler, brute, turret, bomber) that each map re-skins and re-tunes, giving
 * twenty distinct opponents across the campaign.
 */
public final class Enemy extends Mob {

    public static final int SOLDIER = 0, HEAVY = 1, FLYER = 2, CRAWLER = 3,
            BRUTE = 4, TURRET = 5, BOMBER = 6;

    /** Static description of one enemy kind. */
    public static final class Def {
        public final int arch;
        public final String name;
        public final float hp, speed, fireRate, shotSpeed, shotDmg;
        public final int shotStyle, shotColor, points;
        public final String pal;
        public final boolean contactKills;

        Def(int arch, String name, float hp, float speed, float fireRate,
            int shotStyle, int shotColor, float shotSpeed, float shotDmg,
            int points, String pal, boolean contactKills) {
            this.arch = arch; this.name = name; this.hp = hp; this.speed = speed;
            this.fireRate = fireRate; this.shotStyle = shotStyle; this.shotColor = shotColor;
            this.shotSpeed = shotSpeed; this.shotDmg = shotDmg; this.points = points;
            this.pal = pal; this.contactKills = contactKills;
        }
    }

    private static final String PAL_FROM = "eEFVvsSkp";

    /** [map][slot] — four kinds per map. */
    public static final Def[][] ROSTER = {
            {   // ГОРОДСКОЙ
                    new Def(SOLDIER, "ГОПНИК", 4, 40, 1.35f, Shot.BULLET, 0xFFFFB040, 210, 1, 100, "eEFVvsSkp", false),
                    new Def(HEAVY, "ОМОН", 14, 26, 1.7f, Shot.BULLET, 0xFFFFC060, 190, 1, 250, "235BbsSkC", true),
                    new Def(BOMBER, "ПОДРЫВНИК", 4, 74, 0, 0, 0, 0, 0, 180, "eEFRrsSkY", true),
                    new Def(TURRET, "СНАЙПЕР", 7, 0, 2.1f, Shot.BULLET, 0xFFFF5030, 340, 1, 200, "234VvsSkR", false),
            },
            {   // ЛЕСТНИЦА В НЕБО
                    new Def(SOLDIER, "ЛУЧНИК", 5, 44, 1.25f, Shot.ARROW, 0xFFE8D8A0, 200, 1, 130, "oyY65sSkp", false),
                    new Def(FLYER, "ВИХРЬ", 5, 58, 1.5f, Shot.ORB, 0xFFCFF0FF, 170, 1, 170, "566VvsSkC", true),
                    new Def(TURRET, "СТРАЖ БУРИ", 9, 0, 1.7f, Shot.PLASMA, 0xFFFFE070, 230, 1, 230, "oyYVvsSkC", false),
                    new Def(BRUTE, "КОПЕЙЩИК", 18, 34, 1.9f, Shot.SPEAR, 0xFFE8D8A0, 250, 1, 320, "oyY65sSkp", true),
            },
            {   // ЛЕДЯНЫЕ ПЕЩЕРЫ
                    new Def(CRAWLER, "ЛЕДОПОЛЗ", 4, 80, 0, 0, 0, 0, 0, 140, "iICVvsSkC", true),
                    new Def(BOMBER, "ЛЕДОБОМБА", 5, 66, 0, 0, 0, 0, 0, 200, "iICiIsSkC", true),
                    new Def(BRUTE, "ЙЕТИ", 22, 40, 1.5f, 0, 0, 0, 0, 360, "56665IicC", true),
                    new Def(TURRET, "СОСУЛЬКОМЁТ", 8, 0, 1.15f, Shot.SHARD, 0xFFB8E4F8, 240, 1, 210, "iICVvsSkC", false),
            },
            {   // ДРЕВНИЕ РУИНЫ
                    new Def(SOLDIER, "МУМИЯ", 7, 30, 1.6f, Shot.ORB, 0xFF9AD858, 160, 1, 160, "dDw7Djdnq", true),
                    new Def(CRAWLER, "СКАРАБЕЙ", 3, 86, 0, 0, 0, 0, 0, 120, "uUmuuuUuq", true),
                    new Def(HEAVY, "АНУБИС", 17, 32, 1.5f, Shot.SPEAR, 0xFFE8D8A0, 260, 1, 300, "dyDuUSknq", true),
                    new Def(TURRET, "ИДОЛ", 11, 0, 1.5f, Shot.PLASMA, 0xFFE878C8, 210, 1, 250, "xXzxxXxxq", false),
            },
            {   // ЗАВОД
                    new Def(SOLDIER, "СВАРЩИК", 6, 46, 1.1f, Shot.PLASMA, 0xFF58C0E0, 250, 1, 170, "eEFonEe2O", false),
                    new Def(FLYER, "ДРОН", 5, 70, 1.0f, Shot.BULLET, 0xFFFF5030, 300, 1, 190, "eEFVvsSkR", true),
                    new Def(HEAVY, "ОГНЕБОТ", 16, 30, 1.2f, Shot.FLAME, 0xFFFF8030, 150, 1, 310, "oOyE2Ee2f", true),
                    new Def(TURRET, "МАНИПУЛЯТОР", 12, 0, 1.35f, Shot.SAW, 0xFFCFD8E0, 190, 1, 260, "eEFVvsSkO", false),
            },
    };

    // Baked sprite cache: [map][slot][frame], plus flipped copies.
    private static Bitmap[][][] SPR;
    private static Bitmap[][][] SPR_L;

    public static synchronized void initSprites() {
        if (SPR != null) return;
        SPR = new Bitmap[5][4][2];
        SPR_L = new Bitmap[5][4][2];
        for (int m = 0; m < 5; m++)
            for (int s = 0; s < 4; s++) {
                Def d = ROSTER[m][s];
                String[][] frames = framesFor(d.arch);
                for (int f = 0; f < 2; f++) {
                    SPR[m][s][f] = Art.bake(frames[f], PAL_FROM, d.pal);
                    SPR_L[m][s][f] = Art.flip(SPR[m][s][f]);
                }
            }
    }

    private static String[][] framesFor(int arch) {
        switch (arch) {
            case HEAVY: return new String[][]{Art.E_HEAVY1, Art.E_HEAVY2};
            case FLYER: return new String[][]{Art.E_FLYER1, Art.E_FLYER2};
            case CRAWLER: return new String[][]{Art.E_CRAWLER1, Art.E_CRAWLER2};
            case BRUTE: return new String[][]{Art.E_BRUTE1, Art.E_BRUTE2};
            case TURRET: return new String[][]{Art.E_TURRET, Art.E_TURRET};
            case BOMBER: return new String[][]{Art.E_BOMBER1, Art.E_BOMBER2};
            default: return new String[][]{Art.E_SOLDIER1, Art.E_SOLDIER2};
        }
    }

    // ------------------------------------------------------------------

    public final Def def;
    private final int map, slot;

    private float cooldown;
    private float animTime;
    private int frame;
    private float alertTime;
    private float telegraph;       // > 0 while winding up a shot
    private float patrolTimer;
    private float bob;
    private float fuse = -1;       // bombers only
    private float slam;            // brutes only
    private float aimAngle;
    private boolean awake;
    private float spawnGlow = 0.4f;
    private float stagger;

    public Enemy(int map, int slot, float px, float py) {
        this.map = map;
        this.slot = slot;
        this.def = ROSTER[map][slot];
        hp = maxHp = def.hp;
        switch (def.arch) {
            case FLYER: w = 12; h = 9; break;
            case CRAWLER: w = 12; h = 7; break;
            case BRUTE: w = 13; h = 19; break;
            case HEAVY: w = 11; h = 17; break;
            case TURRET: w = 12; h = 12; break;
            default: w = 9; h = 15; break;
        }
        x = px - w / 2;
        y = py - h;
        canStepUp = def.arch != TURRET && def.arch != FLYER;
        face = -1;
        bob = (px * 0.05f) % 6.28f;
        aimAngle = (float) Math.PI;
    }

    public boolean hittable() { return hp > 0 && !remove; }

    public int bloodColor() {
        switch (map) {
            case Theme.SKY: return 0xFFE8E4C0;
            case Theme.ICE: return 0xFFA8E0F8;
            case Theme.RUINS: return def.arch == CRAWLER ? 0xFF9AD858 : 0xFFC8B888;
            case Theme.FACTORY: return 0xFFFFC040;
            default: return 0xFFB02820;
        }
    }

    /** Shield check — heavies soak everything that hits the front plate. */
    public boolean blocksFrom(float shotVx) {
        if (def.arch != HEAVY) return false;
        if (slam > 0) return false;                     // open while attacking
        return (shotVx > 0 && face < 0) || (shotVx < 0 && face > 0);
    }

    // ------------------------------------------------------------------

    @Override
    public void update(float dt, World world) {
        if (spawnGlow > 0) spawnGlow -= dt;
        if (hurtFlash > 0) hurtFlash -= dt;
        if (stagger > 0) { stagger -= dt; }

        Player pl = world.player;
        float px = pl != null ? pl.cx() : x;
        float py = pl != null ? pl.cy() : y;
        float dx = px - cx(), dy = py - cy();
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        if (!awake) {
            // Wake when the camera gets close, so off-screen enemies stay idle.
            if (Math.abs(cx() - world.camX - world.vw / 2f) < world.vw * 0.75f) awake = true;
            else return;
        }

        boolean sees = pl != null && pl.alive() && dist < sightRange()
                && world.level.lineClear(cx(), cy(), px, py);
        if (sees) alertTime = 2.6f;
        else if (alertTime > 0) alertTime -= dt;

        animTime += dt * (def.arch == CRAWLER ? 14 : 7);
        frame = ((int) animTime) & 1;

        if (stagger <= 0) {
            switch (def.arch) {
                case SOLDIER: soldier(dt, world, pl, dx, dy, dist, sees); break;
                case HEAVY: heavy(dt, world, pl, dx, dy, dist, sees); break;
                case FLYER: flyer(dt, world, pl, dx, dy, dist, sees); break;
                case CRAWLER: crawler(dt, world, pl, dx, dy, dist, sees); break;
                case BRUTE: brute(dt, world, pl, dx, dy, dist, sees); break;
                case TURRET: turret(dt, world, pl, dx, dy, dist, sees); break;
                case BOMBER: bomber(dt, world, pl, dx, dy, dist, sees); break;
            }
        } else {
            vx *= 0.86f;
        }

        if (def.arch != FLYER && def.arch != TURRET) {
            physics(dt, world.level, 620f, 380f);
            if (world.level.boxHitsSpike(x, y, w, h)) damage(99, world, 0);
            if (y > world.level.h * Level.TS + 60) remove = true;
        }

        if (cooldown > 0) cooldown -= dt;
        if (telegraph > 0) {
            telegraph -= dt;
            if (telegraph <= 0) release(world);
        }

        // Contact damage
        if (def.contactKills && pl != null && pl.alive() && !pl.invulnerable()
                && overlaps(pl) && (def.arch != FLYER || dist < 22)) {
            pl.kill(world, "CONTACT");
        }
    }

    private float sightRange() {
        switch (def.arch) {
            case TURRET: return 220;
            case SOLDIER: return 190;
            case FLYER: return 200;
            case BOMBER: return 210;
            default: return 175;
        }
    }

    // --- behaviours ---------------------------------------------------

    private void soldier(float dt, World world, Player pl, float dx, float dy,
                         float dist, boolean sees) {
        if (sees) {
            face = dx > 0 ? 1 : -1;
            if (dist > 120) walk(world, def.speed);
            else if (dist < 46) walk(world, -def.speed * 0.7f);
            else vx *= 0.82f;
            if (cooldown <= 0 && telegraph <= 0) {
                telegraph = 0.28f;
                cooldown = def.fireRate;
                aimAngle = (float) Math.atan2(dy, dx);
            }
        } else {
            patrol(dt, world);
        }
    }

    private void heavy(float dt, World world, Player pl, float dx, float dy,
                       float dist, boolean sees) {
        if (sees || alertTime > 0) {
            face = dx > 0 ? 1 : -1;
            if (dist > 30) walk(world, def.speed);
            else vx *= 0.8f;
            if (dist < 150 && cooldown <= 0 && telegraph <= 0) {
                telegraph = 0.42f;
                cooldown = def.fireRate;
                slam = 0.42f;                    // shield lowers to fire
                aimAngle = (float) Math.atan2(dy, dx);
            }
        } else {
            patrol(dt, world);
        }
        if (slam > 0) slam -= dt;
    }

    private void flyer(float dt, World world, Player pl, float dx, float dy,
                       float dist, boolean sees) {
        bob += dt * 3.4f;
        float tx, ty;
        if (sees || alertTime > 0) {
            face = dx > 0 ? 1 : -1;
            tx = pl.cx() - face * 44;
            ty = pl.cy() - 34;
            if (cooldown <= 0 && telegraph <= 0 && dist < 170) {
                telegraph = 0.3f;
                cooldown = def.fireRate;
                aimAngle = (float) Math.atan2(dy, dx);
            }
        } else {
            tx = x;
            ty = y - 4;
            patrolTimer -= dt;
            if (patrolTimer <= 0) {
                patrolTimer = 1.4f + world.rndF() * 1.6f;
                face = -face;
            }
            tx = x + face * 40;
        }
        vx += ((tx - x) * 1.8f - vx) * Math.min(1, dt * 3.2f);
        vy += ((ty - y) * 1.8f - vy) * Math.min(1, dt * 3.2f);
        vx = Math.max(-def.speed * 1.7f, Math.min(def.speed * 1.7f, vx));
        vy = Math.max(-def.speed * 1.5f, Math.min(def.speed * 1.5f, vy));
        float ox = x, oy = y;
        if (moveX(world.level, vx * dt)) vx = -vx * 0.4f;
        if (moveY(world.level, (vy + (float) Math.sin(bob) * 16) * dt, false)) vy = -vy * 0.4f;
        if (x == ox && y == oy) { /* wedged; nudge next frame */ }
    }

    private void crawler(float dt, World world, Player pl, float dx, float dy,
                         float dist, boolean sees) {
        if (sees || alertTime > 0) {
            face = dx > 0 ? 1 : -1;
            walk(world, def.speed);
            if (onGround && Math.abs(dy) > 20 && dy < 0 && world.rndF() < 0.03f) vy = -190;
        } else {
            patrol(dt, world);
        }
    }

    private void brute(float dt, World world, Player pl, float dx, float dy,
                       float dist, boolean sees) {
        if (slam > 0) {
            slam -= dt;
            vx *= 0.7f;
            if (slam <= 0) {
                // Ground pound: shockwave that also cracks the floor.
                world.fx.ring(cx(), feet(), 46, 0xCCFFD070);
                world.fx.smokePuff(cx(), feet(), 8, 0x88908878);
                world.fx.kick(4f);
                world.sfx.playAt(Sfx.STOMP, world.distToCam(cx(), cy()), 0.9f, 1f);
                world.level.blast(cx(), feet() + 6, 18, 1, world.fx);
                if (pl != null && pl.alive() && !pl.invulnerable()
                        && Math.abs(pl.cx() - cx()) < 42 && Math.abs(pl.feet() - feet()) < 24
                        && pl.onGround)
                    pl.kill(world, "SLAM");
                if (def.shotStyle == Shot.SPEAR && pl != null) {
                    float a = (float) Math.atan2(dy, dx);
                    world.newShot().set(cx(), cy(), (float) Math.cos(a) * def.shotSpeed,
                            (float) Math.sin(a) * def.shotSpeed, 2.4f, def.shotDmg,
                            Shot.SPEAR, false, def.shotColor);
                }
            }
            return;
        }
        if (sees || alertTime > 0) {
            face = dx > 0 ? 1 : -1;
            if (dist > 40) walk(world, def.speed);
            else {
                vx *= 0.8f;
                if (cooldown <= 0) {
                    slam = 0.55f;
                    cooldown = def.fireRate + 0.6f;
                    world.fx.text("!", cx(), y - 8, 0xFFFF4040);
                }
            }
        } else {
            patrol(dt, world);
        }
    }

    private void turret(float dt, World world, Player pl, float dx, float dy,
                        float dist, boolean sees) {
        vx = vy = 0;
        if (sees) {
            face = dx > 0 ? 1 : -1;
            float want = (float) Math.atan2(dy, dx);
            float d = want - aimAngle;
            while (d > Math.PI) d -= (float) (Math.PI * 2);
            while (d < -Math.PI) d += (float) (Math.PI * 2);
            aimAngle += Math.max(-2.6f * dt, Math.min(2.6f * dt, d));
            if (cooldown <= 0 && telegraph <= 0 && Math.abs(d) < 0.35f) {
                telegraph = 0.45f;               // long tell: the player can dodge
                cooldown = def.fireRate;
            }
        }
    }

    private void bomber(float dt, World world, Player pl, float dx, float dy,
                        float dist, boolean sees) {
        if (fuse > 0) {
            fuse -= dt;
            vx *= 0.9f;
            if (fuse <= 0) {
                blowUp(world);
                return;
            }
            return;
        }
        if (sees || alertTime > 0) {
            face = dx > 0 ? 1 : -1;
            walk(world, def.speed);
            if (dist < 34) {
                fuse = 0.62f;
                world.sfx.playAt(Sfx.ALARM, world.distToCam(cx(), cy()), 0.6f, 1.6f);
            }
            if (onGround && dy < -18 && world.rndF() < 0.035f) vy = -200;
        } else {
            patrol(dt, world);
        }
    }

    private void blowUp(World world) {
        float r = map == Theme.ICE ? 40 : 36;
        world.explode(cx(), cy(), r, 8, false, false);
        if (map == Theme.ICE) {
            for (int i = 0; i < 8; i++) {
                float a = (float) (i * Math.PI / 4);
                world.newShot().set(cx(), cy(), (float) Math.cos(a) * 150,
                        (float) Math.sin(a) * 150, 0.9f, 1, Shot.SHARD, false, 0xFFB8E4F8);
            }
        }
        remove = true;
    }

    private void walk(World world, float speed) {
        float target = face * speed;
        vx += Math.signum(target - vx) * 700 * (1 / 60f);
        if ((target > 0 && vx > target) || (target < 0 && vx < target)) vx = target;
        // Turn at ledges and walls so the AI does not walk into pits.
        if (onGround && !world.level.groundAhead(cx(), feet(), face)) {
            face = -face;
            vx = 0;
        }
    }

    private void patrol(float dt, World world) {
        patrolTimer -= dt;
        if (patrolTimer <= 0) {
            patrolTimer = 1.8f + world.rndF() * 2.2f;
            if (world.rndF() < 0.4f) face = -face;
        }
        walk(world, def.speed * 0.45f);
    }

    /** Fires the shot that was telegraphed. */
    private void release(World world) {
        if (def.fireRate <= 0 || def.shotSpeed <= 0) return;
        Player pl = world.player;
        if (pl == null || !pl.alive()) return;
        float bx = cx() + face * (def.arch == TURRET ? 9 : 8);
        float by = cy() - (def.arch == TURRET ? 1 : 2);
        float a = def.arch == TURRET ? aimAngle : (float) Math.atan2(pl.cy() - by, pl.cx() - bx);

        int n = 1;
        float spread = 0.05f;
        if (def.arch == HEAVY && def.shotStyle == Shot.FLAME) { n = 7; spread = 0.34f; }
        else if (def.arch == HEAVY) { n = 2; spread = 0.09f; }
        else if (def.shotStyle == Shot.SAW) { n = 2; spread = 0.22f; }

        for (int i = 0; i < n; i++) {
            float sa = a + (n > 1 ? (world.rndF() - 0.5f) * spread * 2 : 0);
            float sp = def.shotSpeed * (0.9f + world.rndF() * 0.2f);
            Shot s = world.newShot();
            s.set(bx, by, (float) Math.cos(sa) * sp, (float) Math.sin(sa) * sp,
                    def.shotStyle == Shot.FLAME ? 0.42f : 2.6f, def.shotDmg,
                    def.shotStyle, false, def.shotColor);
            if (def.shotStyle == Shot.ARROW || def.shotStyle == Shot.SPEAR) s.gravity = 150;
            if (def.shotStyle == Shot.SAW) { s.gravity = 180; s.bounces = 3; s.size = 3; }
            if (def.shotStyle == Shot.FLAME) { s.gravity = -30; s.pierces = true; }
        }
        world.fx.muzzle(bx, by, face, 0.8f);
        world.sfx.playAt(def.shotStyle == Shot.PLASMA || def.shotStyle == Shot.ORB
                        ? Sfx.LASER : Sfx.SHOOT, world.distToCam(bx, by), 0.5f,
                0.8f + world.rndF() * 0.3f);
    }

    @Override
    public void damage(float amount, World world, float knockX) {
        if (hp <= 0) return;
        hp -= amount;
        hurtFlash = 0.1f;
        if (def.arch != TURRET && def.arch != BRUTE) vx += knockX * 30;
        if (def.arch == BOMBER && fuse < 0 && hp <= maxHp * 0.4f) fuse = 0.45f;
        if (hp <= 0) onKilled(world);
        else if (amount >= 4) stagger = 0.18f;
    }

    @Override
    protected void onKilled(World world) {
        remove = true;
        if (def.arch == BOMBER) {
            blowUp(world);
        } else {
            world.fx.gibs(cx(), cy(), 7, bloodColor(), world.level.theme.gibColor2);
            world.fx.blood(cx(), cy(), 0, -1, 8, bloodColor());
            world.fx.smokePuff(cx(), cy(), 3, 0x66605850);
        }
        world.sfx.playAt(Sfx.ENEMY_DIE, world.distToCam(cx(), cy()), 0.6f,
                0.85f + world.rndF() * 0.4f);
        if (world.player != null) world.player.addKill(world, cx(), cy(), def.points);
        world.kills++;
        world.maybeDrop(cx(), cy());
    }

    // ------------------------------------------------------------------

    private static final Paint P = new Paint();
    private static final android.graphics.PorterDuffColorFilter FLASH =
            new android.graphics.PorterDuffColorFilter(0xE0FFFFFF,
                    android.graphics.PorterDuff.Mode.SRC_ATOP);

    static {
        P.setAntiAlias(false);
        P.setFilterBitmap(false);
    }

    @Override
    public void draw(Canvas c, float camX, float camY) {
        if (!awake) return;
        Bitmap b = (face > 0 ? SPR[map][slot][frame] : SPR_L[map][slot][frame]);
        float sx = cx() - b.getWidth() / 2f - camX;
        float sy = feet() - b.getHeight() - camY;
        if (def.arch == FLYER) sy += (float) Math.sin(bob) * 2;
        if (def.arch == BRUTE && slam > 0) sy += (0.55f - slam) * 10;

        P.setAlpha(255);
        P.setColorFilter(null);

        // Telegraph glow — the visual cue that a shot is coming.
        if (telegraph > 0) {
            float k = 1 - telegraph / 0.45f;
            P.setColor(0x66FF4040);
            c.drawCircle(cx() - camX + face * 9, cy() - camY, 3 + k * 4, P);
        }
        if (fuse > 0) {
            int blink = (int) (fuse * 14) % 2;
            P.setColor(blink == 0 ? 0xFFFF3020 : 0xFFFFE060);
            c.drawCircle(cx() - camX, cy() - camY - 12, 3, P);
            P.setColor(0x44FF4020);
            c.drawCircle(cx() - camX, cy() - camY, 16 * (1 - fuse / 0.62f) + 8, P);
        }

        c.drawBitmap(b, sx, sy, P);

        if (hurtFlash > 0) {
            P.setColorFilter(FLASH);
            c.drawBitmap(b, sx, sy, P);
            P.setColorFilter(null);
        }
        if (spawnGlow > 0) {
            P.setColor(((int) (spawnGlow * 240) << 24) | 0x00FFFFFF);
            c.drawRect(sx, sy, sx + b.getWidth(), sy + b.getHeight(), P);
        }

        // Health pip for the tougher units.
        if (maxHp >= 10 && hp < maxHp) {
            float ww = b.getWidth();
            P.setColor(0xAA200808);
            c.drawRect(sx, sy - 4, sx + ww, sy - 1, P);
            P.setColor(0xFFE04030);
            c.drawRect(sx, sy - 4, sx + ww * (hp / maxHp), sy - 1, P);
        }

        // Turret barrel follows the aim angle.
        if (def.arch == TURRET) {
            P.setColor(0xFF9AA6B0);
            float bx = cx() - camX, by = cy() - camY - 1;
            for (int i = 4; i < 13; i++)
                c.drawRect(bx + (float) Math.cos(aimAngle) * i - 1,
                        by + (float) Math.sin(aimAngle) * i - 1,
                        bx + (float) Math.cos(aimAngle) * i + 1,
                        by + (float) Math.sin(aimAngle) * i + 1, P);
            if (telegraph > 0) {
                // Laser sight sweeping to the target.
                P.setColor(0x66FF3020);
                for (int i = 14; i < 120; i += 4)
                    c.drawRect(bx + (float) Math.cos(aimAngle) * i,
                            by + (float) Math.sin(aimAngle) * i,
                            bx + (float) Math.cos(aimAngle) * i + 1,
                            by + (float) Math.sin(aimAngle) * i + 1, P);
            }
        }
    }
}
