package com.bromobile.game;

import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.Random;

/** Owns the level, the entities and the rules that bind them together. */
public final class World {

    public static final int PLAY = 0, DEAD = 1, CLEARED = 2, GAME_OVER = 3, BOSS_DEAD = 4;

    public Level level;
    public Theme theme;
    public Player player;
    public Boss boss;
    public Prop exitProp;

    public final ArrayList<Enemy> enemies = new ArrayList<>();
    public final ArrayList<Prop> props = new ArrayList<>();
    public final ArrayList<Shot> shots = new ArrayList<>();
    private final ArrayList<Shot> pool = new ArrayList<>();

    public final Fx fx = new Fx();
    public Sfx sfx;
    public Save save;
    public Controls controls;

    public int map, levelIndex;
    public int score, lives = 3, kills, rescued;
    public int state = PLAY;
    public float stateTime;

    public float camX, camY;
    public int vw, vh;

    public float checkpointX = -1, checkpointY;
    public float arenaLeft, arenaRight, bossFloorY, bossSpawnX;
    public boolean bossTriggered;
    public float bossIntroTime;
    public float levelTime;
    public String hudMessage;
    public float hudMessageTime;

    private final Random rnd = new Random();
    private final Paint p = new Paint();

    // Lightning strikes drawn as jagged bolts.
    private final float[] boltX = new float[6], boltY = new float[6], boltLife = new float[6];
    private final long[] boltSeed = new long[6];
    private int boltN;

    public World(Sfx sfx, Save save, Controls controls, int vw, int vh) {
        this.sfx = sfx;
        this.save = save;
        this.controls = controls;
        this.vw = vw;
        this.vh = vh;
        p.setAntiAlias(false);
        p.setFilterBitmap(false);
    }

    public float rndF() { return rnd.nextFloat(); }

    public float distToCam(float x, float y) {
        float dx = x - (camX + vw / 2f), dy = y - (camY + vh / 2f);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    public void load(int map, int levelIndex) {
        this.map = map;
        this.levelIndex = levelIndex;
        enemies.clear();
        props.clear();
        shots.clear();
        fx.clear();
        boss = null;
        exitProp = null;
        bossTriggered = false;
        bossIntroTime = 0;
        boltN = 0;
        state = PLAY;
        stateTime = 0;
        levelTime = 0;
        checkpointX = -1;
        hudMessage = null;

        theme = new Theme(map, vw, vh);
        level = LevelGen.generate(this, theme, map, levelIndex);

        player = new Player(level.spawnX, level.spawnY - 14);
        player.grenades = 4 + levelIndex;
        camX = clampCamX(player.cx() - vw / 2f);
        camY = clampCamY(player.cy() - vh / 2f);

        sfx.playMusic(theme.musicTrack());
        message(theme.name + "  " + (levelIndex + 1) + "/5", 2.6f);
    }

    public void message(String s, float t) {
        hudMessage = s;
        hudMessageTime = t;
    }

    // ------------------------------------------------------------------
    // Simulation
    // ------------------------------------------------------------------

    public void update(float dt) {
        stateTime += dt;
        if (hudMessageTime > 0) hudMessageTime -= dt;

        if (fx.hitStop > 0) {
            fx.hitStop -= dt;
            dt *= 0.12f;      // brief slow-motion on big hits
        }

        theme.updateWeather(dt, vw, vh);
        for (int i = 0; i < boltN; i++) {
            boltLife[i] -= dt;
            if (boltLife[i] <= 0) {
                boltN--;
                if (i != boltN) {
                    boltX[i] = boltX[boltN];
                    boltY[i] = boltY[boltN];
                    boltLife[i] = boltLife[boltN];
                    boltSeed[i] = boltSeed[boltN];
                }
                i--;
            }
        }

        switch (state) {
            case PLAY: updatePlay(dt); break;
            case DEAD: {
                player.update(dt, this);      // ticks the death timer
                updateEntities(dt);
                if (player.deathTimer <= 0) respawn();
                break;
            }
            case BOSS_DEAD:
            case CLEARED:
            case GAME_OVER:
                updateEntities(dt);
                break;
        }

        fx.update(dt, level);
        updateCamera(dt);
    }

    private void updatePlay(float dt) {
        levelTime += dt;
        player.update(dt, this);
        updateEntities(dt);

        // Boss trigger when the player enters the arena.
        if (level.bossLevel && !bossTriggered && player.cx() > level.bossArenaX) {
            bossTriggered = true;
            boss = new Boss(map, bossSpawnX, bossFloorY, theme.bossName);
            bossIntroTime = 2.6f;
            sfx.playMusic(Music.BOSS);
            fx.kick(3);
        }
        if (bossIntroTime > 0) bossIntroTime -= dt;

        // Exit reached? The trigger box is padded so a fast run-through or a
        // jump over the threshold still counts.
        if (exitProp != null && (!level.bossLevel || props.contains(exitProp))
                && player.overlapsBox(exitProp.x - 6, exitProp.y - 10,
                exitProp.w + 12, exitProp.h + 14)) {
            complete();
        }
    }

    private void updateEntities(float dt) {
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            e.update(dt, this);
            if (e.remove) { enemies.remove(i); i--; }
        }
        for (int i = 0; i < props.size(); i++) {
            Prop pr = props.get(i);
            pr.update(dt, this);
            if (pr.remove) {
                if (pr == exitProp) exitProp = null;
                props.remove(i);
                i--;
            }
        }
        for (int i = 0; i < shots.size(); i++) {
            Shot s = shots.get(i);
            s.update(dt, this);
            if (s.remove) {
                shots.remove(i);
                pool.add(s);
                i--;
            }
        }
        if (boss != null) {
            boss.update(dt, this);
            if (boss.dying && boss.deathTime <= 0) onBossDead();
        }
    }

    private void updateCamera(float dt) {
        float tx, ty;
        if (boss != null && !boss.dying) {
            // Frame the arena during the fight.
            float mid = (arenaLeft + arenaRight) / 2f;
            tx = Math.min(mid - vw / 2f + 20, player.cx() - vw / 2f + player.face * 24);
            tx = Math.max(arenaLeft - 30, tx);
            ty = bossFloorY - vh + 46;
        } else {
            tx = player.cx() - vw / 2f + player.face * 34;
            ty = player.cy() - vh * 0.56f;
            if (!player.onGround && player.vy > 90) ty += 18;
        }
        float k = Math.min(1, dt * 6.5f);
        camX += (clampCamX(tx) - camX) * k;
        camY += (clampCamY(ty) - camY) * k;
    }

    private float clampCamX(float v) {
        float max = level.w * Level.TS - vw;
        return Math.max(0, Math.min(max, v));
    }

    private float clampCamY(float v) {
        float max = level.h * Level.TS - vh;
        return Math.max(0, Math.min(max, v));
    }

    // ------------------------------------------------------------------
    // Combat helpers
    // ------------------------------------------------------------------

    public Shot newShot() {
        Shot s;
        if (pool.isEmpty()) s = new Shot();
        else s = pool.remove(pool.size() - 1);
        shots.add(s);
        return s;
    }

    public boolean enemyNear(Player pl) {
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            if (e.remove) continue;
            float dx = e.cx() - pl.cx();
            if (dx * pl.face < 0) continue;
            if (Math.abs(dx) < 150 && Math.abs(e.cy() - pl.cy()) < 40) return true;
        }
        return boss != null && !boss.dying && boss.intro <= 0;
    }

    /** Enemy fire hitting crates / barrels / prisoners. */
    public boolean hitProps(float x, float y, float dmg) {
        for (int i = 0; i < props.size(); i++) {
            Prop pr = props.get(i);
            if (pr.kind != Prop.BARREL) continue;
            if (pr.overlapsBox(x - 1, y - 1, 3, 3)) {
                pr.damage(dmg, this, 0);
                return true;
            }
        }
        return false;
    }

    public void explode(float x, float y, float radius, float damage,
                        boolean fromPlayer, boolean incendiary) {
        fx.explosion(x, y, radius, incendiary ? 0xFFFF7020 : 0xFFFF9040);
        sfx.playAt(Sfx.EXPLODE, distToCam(x, y), Math.min(1f, 0.5f + radius / 70f),
                0.85f + rndF() * 0.3f);
        sfx.buzz(radius > 34 ? 40 : 22);
        level.blast(x, y, radius * 0.78f, 3, fx);

        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            if (e.remove) continue;
            float dx = e.cx() - x, dy = e.cy() - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d > radius + 8) continue;
            float falloff = 1f - d / (radius + 8);
            e.damage(damage * falloff * 1.4f, this, Math.signum(dx) * 5 * falloff);
        }
        for (int i = 0; i < props.size(); i++) {
            Prop pr = props.get(i);
            if (pr.kind != Prop.BARREL || pr.remove) continue;
            float dx = pr.cx() - x, dy = pr.cy() - y;
            if (dx * dx + dy * dy < (radius + 10) * (radius + 10)) pr.damage(9, this, 0);
        }
        if (boss != null && boss.hittable()) {
            float dx = boss.cx() - x, dy = boss.cy() - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < radius + 24 && !boss.armoredAt(x, y))
                boss.hurt(damage * 1.2f, this, x, y);
            else if (d < radius + 24)
                fx.sparks(x, y, 5, 0xFFCFE0FF);
        }
        // The blast is lethal to the player too — friendly fire is real.
        if (player != null && player.alive() && !player.invulnerable()) {
            float dx = player.cx() - x, dy = player.cy() - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < radius * (fromPlayer ? 0.72f : 0.95f))
                player.kill(this, "BLAST");
        }
        fx.stop(radius > 34 ? 0.06f : 0.03f);
    }

    /** Knife swipe: a short arc in front of the player. */
    public void melee(Player pl) {
        sfx.play(Sfx.SWITCH, 0.5f, 1.7f);
        float ax = pl.cx() + pl.face * 12, ay = pl.cy();
        boolean hitSomething = false;
        for (int i = 0; i < enemies.size(); i++) {
            Enemy e = enemies.get(i);
            if (e.remove) continue;
            if (Math.abs(e.cx() - ax) < 14 && Math.abs(e.cy() - ay) < 16) {
                e.damage(12, this, pl.face * 8);
                fx.blood(e.cx(), e.cy(), pl.face, 0, 8, e.bloodColor());
                hitSomething = true;
            }
        }
        for (int i = 0; i < props.size(); i++) {
            Prop pr = props.get(i);
            if (pr.kind == Prop.BARREL && Math.abs(pr.cx() - ax) < 14
                    && Math.abs(pr.cy() - ay) < 16) {
                pr.damage(4, this, 0);
                hitSomething = true;
            }
        }
        // Slice crates out of the way.
        int tx = (int) (ax / Level.TS), ty = (int) (ay / Level.TS);
        for (int dy = -1; dy <= 1; dy++)
            if (level.get(tx, ty + dy) == Level.CRATE) {
                level.damage(tx, ty + dy, 3, fx);
                hitSomething = true;
            }
        if (hitSomething) {
            fx.kick(1.6f);
            sfx.buzz(12);
        }
    }

    public void maybeDrop(float x, float y) {
        float roll = rndF();
        if (roll < 0.09f) {
            Prop pr = new Prop(Prop.AMMO, x, y + 8);
            pr.theme = map;
            pr.vy = -70;
            props.add(pr);
        } else if (roll < 0.10f) {
            Prop pr = new Prop(Prop.LIFE, x, y + 8);
            pr.theme = map;
            pr.vy = -80;
            props.add(pr);
        }
    }

    public void spawnMinion(float x, float y, int slot) {
        if (enemies.size() > 26) return;
        Enemy e = new Enemy(map, Math.min(3, slot), x, y);
        enemies.add(e);
        fx.sparks(x, y - 8, 10, theme.accent);
    }

    public void addLightning(float x, float groundY) {
        if (boltN >= boltX.length) boltN = boltX.length - 1;
        int i = boltN++;
        boltX[i] = x;
        boltY[i] = groundY;
        boltLife[i] = 0.24f;
        boltSeed[i] = rnd.nextLong();
    }

    public void setCheckpoint(float x, float y) {
        checkpointX = x;
        checkpointY = y;
    }

    // ------------------------------------------------------------------
    // Life cycle
    // ------------------------------------------------------------------

    public void onPlayerDeath(Player pl, String cause) {
        state = DEAD;
        stateTime = 0;
        lives--;
        fx.blood(pl.cx(), pl.cy(), 0, -1, save.blood ? 22 : 8, 0xFFB02820);
        fx.gibs(pl.cx(), pl.cy(), save.blood ? 9 : 4, 0xFFB02820, 0xFF4A5A32);
        fx.kick(5);
        fx.flash = 0.3f;
        fx.flashColor = 0xFFFF3020;
        fx.stop(0.1f);
        sfx.play(Sfx.DEATH, 0.9f, 1f);
        sfx.buzz(70);
        // Remember where he fell — that is where he comes back.
        pl.respawnX = pl.cx();
        pl.respawnY = pl.feet();
    }

    private void respawn() {
        if (lives <= 0) {
            // Out of lives: back to the start of the run through the level.
            lives = 3;
            state = PLAY;
            reloadFromCheckpoint();
            message("НАЧАЛО ЗАНОВО", 2.2f);
            sfx.play(Sfx.ALARM, 0.7f, 0.7f);
            return;
        }
        float[] spot = safeSpot(player.respawnX, player.respawnY);
        player.respawnAt(spot[0] - player.w / 2, spot[1] - 14);
        fx.sparks(spot[0], spot[1] - 8, 14, theme.accent);
        fx.ring(spot[0], spot[1] - 8, 26, 0x99FFFFFF);
        state = PLAY;
        message("ЖИЗНЕЙ: " + lives, 1.4f);
    }

    /** Finds solid footing near the requested point so respawns are never fatal. */
    private float[] safeSpot(float x, float y) {
        for (int radius = 0; radius < 26; radius++) {
            for (int dir = 0; dir < (radius == 0 ? 1 : 2); dir++) {
                float sx = x + (dir == 0 ? -radius : radius) * Level.TS * 0.5f;
                if (sx < Level.TS * 2) sx = Level.TS * 2;
                if (sx > (level.w - 3) * Level.TS) sx = (level.w - 3) * Level.TS;
                for (int up = 0; up < 12; up++) {
                    float sy = y - up * Level.TS * 0.5f;
                    if (sy < Level.TS * 2) break;
                    if (level.boxHits(sx - 4, sy - 15, 8, 15)) continue;
                    if (level.boxHitsSpike(sx - 4, sy - 15, 8, 15)) continue;
                    // Needs ground within a short drop.
                    boolean ground = false;
                    for (int d = 0; d < 10; d++)
                        if (level.solidAt(sx, sy + 2 + d * 4)) { ground = true; break; }
                    if (ground) return new float[]{sx, sy};
                }
            }
        }
        return new float[]{level.spawnX, level.spawnY};
    }

    private void reloadFromCheckpoint() {
        float rx = checkpointX > 0 ? checkpointX : level.spawnX;
        float ry = checkpointX > 0 ? checkpointY : level.spawnY;
        // Clear the fight around the player and re-arm.
        shots.clear();
        boss = null;
        bossTriggered = false;
        for (int i = 0; i < enemies.size(); i++) enemies.get(i).remove = true;
        enemies.clear();
        // Re-populate by regenerating the level's spawn set only.
        World tmp = new World(sfx, save, controls, vw, vh);
        tmp.map = map;
        Level fresh = LevelGen.generate(tmp, theme, map, levelIndex);
        for (Enemy e : tmp.enemies)
            if (e.cx() > rx - 40) enemies.add(e);
        float[] spot = safeSpot(rx, ry);
        player.respawnAt(spot[0] - player.w / 2, spot[1] - 14);
        player.grenades = Math.max(player.grenades, 4);
        camX = clampCamX(player.cx() - vw / 2f);
        camY = clampCamY(player.cy() - vh / 2f);
        sfx.playMusic(theme.musicTrack());
    }

    public void onBossDying(Boss b) {
        fx.stop(0.35f);
        fx.flash = 0.6f;
        fx.flashColor = 0xFFFFFFFF;
        fx.kick(9);
        sfx.buzz(180);
        sfx.stopMusic();
        score += 5000;
        message("БОСС ПОВЕРЖЕН!", 3f);
    }

    private void onBossDead() {
        for (int i = 0; i < 14; i++)
            fx.explosion(boss.cx() + (rndF() - 0.5f) * boss.w * 1.6f,
                    boss.cy() + (rndF() - 0.5f) * boss.h * 1.4f, 20 + rndF() * 14, 0xFFFFB040);
        fx.flash = 0.9f;
        fx.kick(10);
        sfx.play(Sfx.EXPLODE, 1f, 0.6f);
        sfx.playMusic(Music.VICTORY);
        boss = null;
        // The way out opens where the boss stood.
        if (exitProp != null) {
            exitProp.x = arenaRight - 60;
            exitProp.y = bossFloorY - exitProp.h;
            props.add(exitProp);
        }
        message("ВЫХОД ОТКРЫТ", 3f);
    }

    private void complete() {
        state = CLEARED;
        stateTime = 0;
        score += 1000 + Math.max(0, (int) (600 - levelTime * 4));
        score += rescued * 200;
        sfx.play(Sfx.WIN, 1f, 1f);
        sfx.stopMusic();
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    public void draw(Canvas c) {
        float sx = fx.shakeX(), sy = fx.shakeY();
        float cx = camX + sx, cy = camY + sy;

        theme.drawSky(c);
        theme.drawLayers(c, cx, cy, vw, vh);
        if (theme.weather == 3) theme.drawWeather(c, vw, vh);

        level.draw(c, cx, cy, vw, vh);
        fx.drawDecals(c, cx, cy);

        for (int i = 0; i < props.size(); i++) props.get(i).draw(c, cx, cy);
        for (int i = 0; i < enemies.size(); i++) enemies.get(i).draw(c, cx, cy);
        if (boss != null) boss.draw(c, cx, cy);
        if (player != null) player.draw(c, cx, cy);
        for (int i = 0; i < shots.size(); i++) shots.get(i).draw(c, cx, cy);

        fx.draw(c, cx, cy);
        drawBolts(c, cx, cy);

        if (theme.weather != 3) theme.drawWeather(c, vw, vh);
        drawVignette(c);
        fx.drawFlash(c);

        // Off-screen marker pointing at the exit once it is close.
        if (exitProp != null && player != null) {
            float ex = exitProp.cx() - cx;
            if (ex > vw - 6) {
                p.setColor(0xCCFFE060);
                float ay = vh * 0.32f;
                for (int i = 0; i < 5; i++)
                    c.drawRect(vw - 10 + i, ay - 4 + i, vw - 8 + i, ay + 4 - i, p);
                Font.shadow(c, "ВЫХОД", vw - 48, (int) ay - 14, 0xFFFFE060, 1);
            }
        }
    }

    private void drawBolts(Canvas c, float cx, float cy) {
        for (int i = 0; i < boltN; i++) {
            Random r = new Random(boltSeed[i]);
            float x = boltX[i] - cx;
            float top = -10;
            float bottom = boltY[i] - cy;
            int seg = 12;
            float px = x, py = top;
            int alpha = (int) (255 * Math.min(1, boltLife[i] / 0.24f));
            for (int k = 1; k <= seg; k++) {
                float ny = top + (bottom - top) * k / seg;
                float nx = x + (r.nextFloat() - 0.5f) * 16 * (1 - k / (float) seg);
                p.setColor((alpha << 24) | 0x00FFFFFF);
                drawLineRect(c, px, py, nx, ny, 3);
                p.setColor((alpha << 24) | 0x00A0D8FF);
                drawLineRect(c, px, py, nx, ny, 1);
                px = nx;
                py = ny;
            }
        }
    }

    private void drawLineRect(Canvas c, float x0, float y0, float x1, float y1, float wdt) {
        int steps = (int) (Math.abs(y1 - y0) / 2) + 1;
        for (int i = 0; i <= steps; i++) {
            float k = i / (float) steps;
            float x = x0 + (x1 - x0) * k, y = y0 + (y1 - y0) * k;
            c.drawRect(x - wdt / 2, y, x + wdt / 2, y + 2, p);
        }
    }

    private void drawVignette(Canvas c) {
        p.setColor(0x33000000);
        c.drawRect(0, 0, vw, 6, p);
        c.drawRect(0, vh - 6, vw, vh, p);
        p.setColor(0x22000000);
        c.drawRect(0, 6, 5, vh - 6, p);
        c.drawRect(vw - 5, 6, vw, vh - 6, p);
    }
}
