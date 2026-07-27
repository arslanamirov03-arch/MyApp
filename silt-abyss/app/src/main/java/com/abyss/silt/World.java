package com.abyss.silt;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import com.abyss.silt.audio.Synth;
import com.abyss.silt.core.Art;
import com.abyss.silt.core.Cam;
import com.abyss.silt.core.V;
import com.abyss.silt.entities.Boss;
import com.abyss.silt.entities.Critter;
import com.abyss.silt.entities.Diver;
import com.abyss.silt.entities.Entity;
import com.abyss.silt.entities.Hazard;
import com.abyss.silt.entities.Predator;
import com.abyss.silt.level.LevelDef;

import java.util.ArrayList;

/**
 * The live level: terrain grid, entities, puzzle wiring, light and rendering.
 * <p>
 * Terrain is baked into a Path once at load, so the per-frame cost is a handful
 * of path draws regardless of how big the level is.
 */
public class World {

    public static final float CS = 64f;                 // cell size in world units
    public static final float POSSESS_RANGE = 300f;     // spirit reach, per hop

    public static final int PLAYING = 0, COMPLETE = 1, DEAD = 2;

    // ---- level ----
    public LevelDef def;
    public int cols, rows;
    private char[] cells;
    private boolean[] broken;      // cleared breakables

    // ---- puzzle wiring ----
    private final ArrayList<Integer> switchIdx = new ArrayList<>();
    private char[] switchKind;
    private int[] switchChan;
    private boolean[] switchOn;
    private float[] switchTimer;   // >0 = latched for this long
    private final ArrayList<Integer> gateIdx = new ArrayList<>();
    private int[] gateChan;
    private final boolean[] channelOpen = new boolean[10];
    private final float[] gateAnim = new float[10];

    // ---- entities ----
    public final ArrayList<Entity> entities = new ArrayList<>();
    public Diver diver;
    public Entity controlled;
    public Boss boss;
    private final ArrayList<Entity> spawnQueue = new ArrayList<>();

    // ---- control ----
    public float inputX, inputY;
    public int state = PLAYING;
    public float stateT = 0f;

    // Spirit transfer animation.
    private Entity spiritFrom, spiritTo;
    private float spiritT = 0f, spiritDur = 0f;

    // ---- progress ----
    public float respawnX, respawnY;
    public int deaths = 0;
    public float elapsed = 0f;
    public float sinceProgress = 0f;    // drives the "offer a hint" timer
    public int whisperNext = 0;
    public String whisperText = null;
    public float whisperT = 0f;

    public final Cam cam = new Cam();
    public Synth audio;

    // ---- hint highlight ----
    public float hintX, hintY, hintT = 0f;

    // ---- baked geometry ----
    private final Path rockPath = new Path();
    private final Path rimPath = new Path();
    private final Path farPath = new Path();
    private final Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path scratch = new Path();

    private float time = 0f;

    public World() {
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
    }

    // ================= loading =================

    public void load(LevelDef d) {
        this.def = d;
        cols = d.cols();
        rows = d.rows();
        cells = new char[cols * rows];
        broken = new boolean[cols * rows];
        entities.clear();
        spawnQueue.clear();
        switchIdx.clear();
        gateIdx.clear();
        boss = null;
        diver = null;
        state = PLAYING;
        stateT = 0f;
        deaths = 0;
        elapsed = 0f;
        sinceProgress = 0f;
        whisperNext = 0;
        whisperText = null;
        whisperT = 0f;
        hintT = 0f;
        spiritFrom = spiritTo = null;
        spiritT = 0f;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                char c = d.at(col, row);
                if (c == ' ') c = '.';
                int i = row * cols + col;
                float wx = col * CS + CS * 0.5f;
                float wy = row * CS + CS * 0.5f;

                if (LevelDef.isCreature(c)) {
                    entities.add(new Critter(this, wx, wy, c - '0'));
                    c = '.';
                } else if (c == 'P') {
                    entities.add(new Predator(this, wx, wy));
                    c = '.';
                } else if (c == 'T') {
                    entities.add(new Hazard(this, wx, wy, Hazard.URCHIN));
                    c = '.';
                } else if (c == 'J') {
                    entities.add(new Hazard(this, wx, wy, Hazard.JELLY));
                    c = '.';
                } else if (c == 'B') {
                    boss = new Boss(this, wx, wy, Math.max(1, d.boss));
                    entities.add(boss);
                    c = '.';
                } else if (c == 'S') {
                    diver = new Diver(this, wx, wy);
                    respawnX = wx;
                    respawnY = wy;
                    c = '.';
                } else if (LevelDef.isSwitch(c)) {
                    switchIdx.add(i);
                } else if (c == 'g') {
                    gateIdx.add(i);
                }
                cells[i] = c;
            }
        }

        if (diver == null) {   // defensive: a level without a spawn still loads
            diver = new Diver(this, CS * 1.5f, CS * 1.5f);
            respawnX = diver.x;
            respawnY = diver.y;
        }
        entities.add(diver);
        controlled = diver;
        diver.possessed = true;

        int n = switchIdx.size();
        switchKind = new char[n];
        switchChan = new int[n];
        switchOn = new boolean[n];
        switchTimer = new float[n];
        for (int k = 0; k < n; k++) {
            int i = switchIdx.get(k);
            switchKind[k] = cells[i];
            switchChan[k] = d.channelAt(i % cols, i / cols);
        }
        gateChan = new int[gateIdx.size()];
        for (int k = 0; k < gateIdx.size(); k++) {
            int i = gateIdx.get(k);
            gateChan[k] = d.channelAt(i % cols, i / cols);
        }
        for (int k = 0; k < 10; k++) {
            channelOpen[k] = false;
            gateAnim[k] = 0f;
        }
        recomputeChannels();

        bakeTerrain();

        cam.snapTo(diver.x, diver.y);
    }

    // ================= terrain baking =================

    private void bakeTerrain() {
        rockPath.reset();
        rimPath.reset();
        farPath.reset();

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (cells[row * cols + col] != '#') continue;
                float l = col * CS, t = row * CS, r = l + CS, b = t + CS;
                rockPath.addRect(l, t, r, b, Path.Direction.CW);
                // A disc per cell rounds the square grid off into something that
                // reads as stone rather than as tiles.
                rockPath.addCircle(l + CS * 0.5f, t + CS * 0.5f, CS * 0.56f,
                        Path.Direction.CW);

                // Protrusions into open water, so nothing reads as a tidy square.
                if (!isRock(col, row - 1)) {
                    spikes(rockPath, l, t, r, t, 0f, -1f, col * 71 + row);
                    rim(rimPath, l, t, r, t);
                }
                if (!isRock(col, row + 1)) spikes(rockPath, l, b, r, b, 0f, 1f, col * 13 + row * 7);
                if (!isRock(col - 1, row)) spikes(rockPath, l, t, l, b, -1f, 0f, col * 29 + row * 3);
                if (!isRock(col + 1, row)) spikes(rockPath, r, t, r, b, 1f, 0f, col * 53 + row * 11);
            }
        }

        // A distant parallax ridge line, drawn behind everything.
        float baseY = rows * CS * 0.82f;
        farPath.moveTo(-CS * 4f, rows * CS + CS * 4f);
        for (int i = -4; i <= cols + 4; i++) {
            float x = i * CS;
            float h = baseY
                    + V.noise1(i * 0.21f, 3) * CS * 2.4f
                    + V.noise1(i * 0.07f, 91) * CS * 4.2f;
            farPath.lineTo(x, h);
        }
        farPath.lineTo((cols + 4) * CS, rows * CS + CS * 4f);
        farPath.close();
    }

    private void spikes(Path p, float x0, float y0, float x1, float y1,
                        float nx, float ny, int seed) {
        // Fine, irregular relief. Big regular teeth read as a sawblade, not rock.
        int steps = 6;
        for (int i = 0; i < steps; i++) {
            float t0 = i / (float) steps, t1 = (i + 1) / (float) steps;
            float roll = V.hashF(i + seed, seed);
            if (roll < 0.22f) continue;                 // leave gaps, so it varies
            float h = (0.03f + 0.17f * roll * roll) * CS;
            float ax = V.lerp(x0, x1, t0), ay = V.lerp(y0, y1, t0);
            float bx = V.lerp(x0, x1, t1), by = V.lerp(y0, y1, t1);
            float mx = (ax + bx) * 0.5f + nx * h, my = (ay + by) * 0.5f + ny * h;
            p.moveTo(ax, ay);
            p.lineTo(bx, by);
            p.lineTo(mx, my);
            p.close();
        }
    }

    private void rim(Path p, float x0, float y0, float x1, float y1) {
        p.moveTo(x0, y0);
        p.lineTo(x1, y1);
    }

    // ================= grid queries =================

    public char cellAt(int col, int row) {
        if (col < 0 || row < 0 || col >= cols || row >= rows) return '#';
        return cells[row * cols + col];
    }

    private boolean isRock(int col, int row) {
        return cellAt(col, row) == '#';
    }

    public boolean isBroken(int col, int row) {
        if (col < 0 || row < 0 || col >= cols || row >= rows) return false;
        return broken[row * cols + col];
    }

    /** True if this cell blocks movement right now. */
    public boolean solidCell(int col, int row) {
        char c = cellAt(col, row);
        if (c == '#') return true;
        if (c == 'r' || c == 'k') return !isBroken(col, row);
        if (c == 'g') {
            int ch = def.channelAt(col, row);
            return !channelOpen[V.clampI(ch, 0, 9)];
        }
        return false;
    }

    public boolean solidAtWorld(float wx, float wy) {
        return solidCell((int) Math.floor(wx / CS), (int) Math.floor(wy / CS));
    }

    public boolean lineOfSight(float x0, float y0, float x1, float y1) {
        float d = V.dist(x0, y0, x1, y1);
        int steps = V.clampI((int) (d / 20f), 1, 90);
        for (int i = 1; i < steps; i++) {
            float u = i / (float) steps;
            if (solidAtWorld(V.lerp(x0, x1, u), V.lerp(y0, y1, u))) return false;
        }
        return true;
    }

    // ================= physics =================

    public void moveAndCollide(Entity e, float dt) {
        e.hitWall = false;
        e.onGround = false;

        e.x += e.vx * dt;
        resolveAxis(e, true);
        e.y += e.vy * dt;
        resolveAxis(e, false);

        // Keep everything inside the level bounds.
        float rr = e.hitRadius();
        if (e.x < rr) { e.x = rr; e.vx = 0; e.hitWall = true; }
        if (e.y < rr) { e.y = rr; e.vy = 0; e.hitWall = true; }
        if (e.x > cols * CS - rr) { e.x = cols * CS - rr; e.vx = 0; e.hitWall = true; }
        if (e.y > rows * CS - rr) { e.y = rows * CS - rr; e.vy = 0; e.onGround = true; e.hitWall = true; }
    }

    private void resolveAxis(Entity e, boolean horizontal) {
        float rr = e.hitRadius();
        int c0 = (int) Math.floor((e.x - rr) / CS);
        int c1 = (int) Math.floor((e.x + rr) / CS);
        int r0 = (int) Math.floor((e.y - rr) / CS);
        int r1 = (int) Math.floor((e.y + rr) / CS);

        for (int row = r0; row <= r1; row++) {
            for (int col = c0; col <= c1; col++) {
                if (!solidCell(col, row)) continue;
                float l = col * CS, t = row * CS, r = l + CS, b = t + CS;
                // Closest point on the cell to the circle centre.
                float nx = V.clamp(e.x, l, r), ny = V.clamp(e.y, t, b);
                float dx = e.x - nx, dy = e.y - ny;
                if (dx * dx + dy * dy >= rr * rr) continue;

                e.hitWall = true;
                if (horizontal) {
                    if (e.x < l + CS * 0.5f) e.x = l - rr - 0.01f;
                    else e.x = r + rr + 0.01f;
                    e.vx = 0f;
                } else {
                    if (e.y < t + CS * 0.5f) {
                        e.y = t - rr - 0.01f;
                        e.onGround = true;
                    } else {
                        e.y = b + rr + 0.01f;
                    }
                    e.vy = 0f;
                }
            }
        }
    }

    public void applyCurrent(Entity e, float dt) {
        char c = cellAt((int) Math.floor(e.x / CS), (int) Math.floor(e.y / CS));
        if (!LevelDef.isCurrent(c)) return;
        float push = 1150f;
        switch (c) {
            case '^': e.vy -= push * dt; break;
            case 'v': e.vy += push * dt; break;
            case '<': e.vx -= push * dt; break;
            case '>': e.vx += push * dt; break;
        }
    }

    // ================= possession =================

    public boolean transferring() {
        return spiritT > 0f;
    }

    /**
     * Attempts to send the spirit into a creature near the tapped point. The hop
     * is measured from whatever the spirit currently inhabits, which is what lets
     * you chain across a level and leave your body waiting at a gate.
     */
    public boolean tryPossess(float wx, float wy) {
        if (state != PLAYING || transferring()) return false;
        Critter best = null;
        float bestScore = Float.MAX_VALUE;
        for (Entity e : entities) {
            if (!(e instanceof Critter)) continue;
            Critter cr = (Critter) e;
            if (cr.dead || cr == controlled) continue;
            float tapD = V.dist(wx, wy, cr.x, cr.y);
            if (tapD > 130f) continue;                       // generous touch target
            float hop = V.dist(controlled.x, controlled.y, cr.x, cr.y);
            if (hop > POSSESS_RANGE) continue;
            if (!lineOfSight(controlled.x, controlled.y, cr.x, cr.y)) continue;
            if (tapD < bestScore) {
                bestScore = tapD;
                best = cr;
            }
        }
        if (best == null) return false;
        beginTransfer(controlled, best);
        return true;
    }

    /** Recalls the spirit to the diver's body from any distance. */
    public boolean releaseSpirit() {
        if (state != PLAYING || transferring()) return false;
        if (controlled == diver) return false;
        beginTransfer(controlled, diver);
        return true;
    }

    private void beginTransfer(Entity from, Entity to) {
        spiritFrom = from;
        spiritTo = to;
        spiritDur = V.clamp(V.dist(from.x, from.y, to.x, to.y) / 900f, 0.18f, 0.75f);
        spiritT = spiritDur;
        if (from instanceof Critter) ((Critter) from).possessed = false;
        if (from == diver) diver.possessed = false;
        if (audio != null) audio.sfx(Synth.SFX_POSSESS, 1f);
    }

    private void finishTransfer() {
        controlled = spiritTo;
        if (controlled instanceof Critter) ((Critter) controlled).possessed = true;
        if (controlled == diver) diver.possessed = true;
        spiritFrom = null;
        spiritTo = null;
        sinceProgress = 0f;
    }

    public boolean useAbility() {
        if (state != PLAYING || transferring()) return false;
        if (controlled instanceof Critter) {
            Critter cr = (Critter) controlled;
            boolean ok = cr.useAbility();
            if (ok && audio != null) audio.sfx(Synth.SFX_ABILITY, 1f);
            return ok;
        }
        return false;
    }

    /** True when the diver has a creature in reach worth highlighting. */
    public Critter possessCandidate() {
        Critter best = null;
        float bd = POSSESS_RANGE;
        for (Entity e : entities) {
            if (!(e instanceof Critter)) continue;
            Critter cr = (Critter) e;
            if (cr.dead || cr == controlled) continue;
            float d = V.dist(controlled.x, controlled.y, cr.x, cr.y);
            if (d < bd && lineOfSight(controlled.x, controlled.y, cr.x, cr.y)) {
                bd = d;
                best = cr;
            }
        }
        return best;
    }

    // ================= ability effects =================

    private void forEachCellInRadius(float wx, float wy, float radius, CellVisitor v) {
        int c0 = (int) Math.floor((wx - radius) / CS);
        int c1 = (int) Math.floor((wx + radius) / CS);
        int r0 = (int) Math.floor((wy - radius) / CS);
        int r1 = (int) Math.floor((wy + radius) / CS);
        for (int row = Math.max(0, r0); row <= Math.min(rows - 1, r1); row++) {
            for (int col = Math.max(0, c0); col <= Math.min(cols - 1, c1); col++) {
                float cx = col * CS + CS * 0.5f, cy = row * CS + CS * 0.5f;
                if (V.dist(wx, wy, cx, cy) <= radius + CS * 0.5f) v.visit(col, row);
            }
        }
    }

    private interface CellVisitor {
        void visit(int col, int row);
    }

    public void onBite(final Critter c) {
        final float reach = 52f;
        float bx = c.x + (float) Math.cos(c.angle) * 26f;
        float by = c.y + (float) Math.sin(c.angle) * 26f;
        forEachCellInRadius(bx, by, reach, new CellVisitor() {
            public void visit(int col, int row) {
                char ch = cellAt(col, row);
                if (ch == 'k' && !isBroken(col, row)) {
                    breakCell(col, row);
                } else if (ch == 'l') {
                    toggleLever(col, row);
                }
            }
        });
    }

    public void onDash(final Critter c) {
        if (audio != null) audio.sfx(Synth.SFX_DASH, 1f);
        cam.addShake(4f);
    }

    /** Called every frame while a dash is live, so it can plough through things. */
    private void dashContacts(final Critter c) {
        final float reach = 46f;
        float bx = c.x + (float) Math.cos(c.angle) * 24f;
        float by = c.y + (float) Math.sin(c.angle) * 24f;
        forEachCellInRadius(bx, by, reach, new CellVisitor() {
            public void visit(int col, int row) {
                char ch = cellAt(col, row);
                if (ch == 'r' && !isBroken(col, row)) {
                    breakCell(col, row);
                    cam.addShake(9f);
                } else if (ch == 'l') {
                    toggleLever(col, row);
                }
            }
        });
        for (Entity e : entities) {
            if (e instanceof Predator && V.dist(c.x, c.y, e.x, e.y) < 70f) {
                ((Predator) e).stun(3.4f);
                cam.addShake(7f);
            }
            if (e instanceof Boss) {
                Boss b = (Boss) e;
                if (b.vulnerable() && V.dist(c.x, c.y, b.eyeX, b.eyeY) < b.eyeR * 2.6f + c.r) {
                    b.takeHit();
                    // Bounce clear so the same dash cannot register twice.
                    c.vx = -c.vx * 0.8f;
                    c.vy = -c.vy * 0.8f;
                    c.dashT = 0f;
                }
            }
        }
    }

    public void onFlash(Critter c) {
        for (Entity e : entities) {
            if (e instanceof Predator && V.dist(c.x, c.y, e.x, e.y) < 420f) {
                ((Predator) e).scare(3.2f);
            }
        }
    }

    public void onDischarge(final Critter c) {
        final float reach = 120f;
        forEachCellInRadius(c.x, c.y, reach, new CellVisitor() {
            public void visit(int col, int row) {
                if (cellAt(col, row) == 'n') latchSwitch(col, row, 11f);
            }
        });
        for (Entity e : entities) {
            if (e instanceof Predator && V.dist(c.x, c.y, e.x, e.y) < 150f) {
                ((Predator) e).stun(2.6f);
            }
        }
        cam.addShake(3f);
    }

    public void onStomp(final Critter c) {
        forEachCellInRadius(c.x, c.y + 30f, 60f, new CellVisitor() {
            public void visit(int col, int row) {
                if (cellAt(col, row) == 'p') latchSwitch(col, row, 12f);
            }
        });
        cam.addShake(6f);
        for (Entity e : entities) {
            if (e instanceof Critter && e != c && V.dist(c.x, c.y, e.x, e.y) < 130f) {
                ((Critter) e).startle();
            }
        }
    }

    private void breakCell(int col, int row) {
        broken[row * cols + col] = true;
        if (audio != null) audio.sfx(Synth.SFX_BREAK, 1f);
        sinceProgress = 0f;
        cam.addShake(3f);
    }

    private void toggleLever(int col, int row) {
        int i = row * cols + col;
        for (int k = 0; k < switchKind.length; k++) {
            if (switchIdx.get(k) == i && switchKind[k] == 'l') {
                switchOn[k] = !switchOn[k];
                recomputeChannels();
                sinceProgress = 0f;
                if (audio != null) audio.sfx(Synth.SFX_SWITCH, 1f);
            }
        }
    }

    private void latchSwitch(int col, int row, float seconds) {
        int i = row * cols + col;
        for (int k = 0; k < switchKind.length; k++) {
            if (switchIdx.get(k) == i) {
                boolean was = switchOn[k];
                switchOn[k] = true;
                switchTimer[k] = Math.max(switchTimer[k], seconds);
                if (!was) {
                    recomputeChannels();
                    sinceProgress = 0f;
                    if (audio != null) audio.sfx(Synth.SFX_SWITCH, 1f);
                }
            }
        }
    }

    private void recomputeChannels() {
        for (int ch = 0; ch < 10; ch++) {
            boolean any = false, all = true;
            for (int k = 0; k < switchKind.length; k++) {
                if (switchChan[k] != ch) continue;
                any = true;
                if (!switchOn[k]) all = false;
            }
            channelOpen[ch] = any && all;
        }
    }

    // ================= entity callbacks =================

    public Entity preyFor(Predator p) {
        if (controlled == diver) return diver.dead ? null : diver;
        if (controlled instanceof Critter) {
            Critter c = (Critter) controlled;
            if (c.dead || c.ignoredByPredators()) return null;
            return c;
        }
        return null;
    }

    public Entity findLatchHost(Critter leech) {
        Entity best = null;
        float bd = 90f;
        for (Entity e : entities) {
            if (!(e instanceof Predator) && !(e instanceof Boss)) continue;
            float d = V.dist(leech.x, leech.y, e.x, e.y) - e.hitRadius();
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    public Entity bossTarget() {
        if (controlled != null && !controlled.dead) return controlled;
        return diver.dead ? null : diver;
    }

    public void bossSpit(Boss b, int count) {
        Entity target = bossTarget();
        if (target == null) return;
        for (int i = 0; i < count; i++) {
            float spread = (i - (count - 1) * 0.5f) * 0.30f;
            float a = (float) Math.atan2(target.y - b.y, target.x - b.x) + spread;
            float ox = b.x + (float) Math.cos(a) * b.size * 0.8f;
            float oy = b.y + (float) Math.sin(a) * b.size * 0.8f;
            spawnQueue.add(new Hazard(this, ox, oy, Hazard.JELLY)
                    .asProjectile((float) Math.cos(a), (float) Math.sin(a), 330f, 5.5f));
        }
        if (audio != null) audio.sfx(Synth.SFX_BOSS_SPIT, 1f);
    }

    public void onBossTelegraph(Boss b) {
        if (audio != null) audio.sfx(Synth.SFX_BOSS_TELL, 1f);
    }

    public void onBossStrike(Boss b) {
        cam.addShake(14f);
        if (audio != null) audio.sfx(Synth.SFX_BOSS_STRIKE, 1f);
    }

    public void onBossRecover(Boss b) {
        cam.addShake(6f);
    }

    public void onBossHit(Boss b) {
        cam.addShake(22f);
        sinceProgress = 0f;
        if (audio != null) audio.sfx(Synth.SFX_BOSS_HURT, 1f);
    }

    public void onBossDefeated(Boss b) {
        cam.addShake(30f);
        if (audio != null) audio.sfx(Synth.SFX_BOSS_DIE, 1f);
    }

    public void onPredatorAlert(Predator p) {
        if (audio != null) audio.sfx(Synth.SFX_ALERT, 1f);
    }

    public void onPredatorLunge(Predator p) {
        if (audio != null) audio.sfx(Synth.SFX_LUNGE, 1f);
    }

    public void onCritterDied(Critter c) {
        cam.addShake(8f);
        if (audio != null) audio.sfx(Synth.SFX_DEATH, 1f);
        if (controlled == c) {
            // The spirit is thrown back into the body.
            beginTransfer(c, diver);
        }
    }

    public void onDiverDied(Diver d) {
        deaths++;
        state = DEAD;
        stateT = 0f;
        cam.addShake(16f);
        if (audio != null) audio.sfx(Synth.SFX_DEATH, 1f);
    }

    public void onDiverStroke(Diver d) {
        if (audio != null) audio.sfx(Synth.SFX_STROKE, 0.5f);
    }

    // ================= update =================

    public void update(float dt) {
        time += dt;
        if (state == PLAYING) {
            elapsed += dt;
            sinceProgress += dt;
        }
        hintT = Math.max(0f, hintT - dt);
        whisperT = Math.max(0f, whisperT - dt);
        if (whisperT <= 0f) whisperText = null;

        // Timed switches decay.
        boolean changed = false;
        for (int k = 0; k < switchKind.length; k++) {
            if (switchTimer[k] > 0f) {
                switchTimer[k] -= dt;
                if (switchTimer[k] <= 0f && switchKind[k] != 'l') {
                    switchOn[k] = false;
                    changed = true;
                }
            }
        }
        if (changed) recomputeChannels();
        for (int ch = 0; ch < 10; ch++) {
            gateAnim[ch] = V.damp(gateAnim[ch], channelOpen[ch] ? 1f : 0f, 0.02f, dt);
        }

        // A HEAVY resting on a plate holds it down without stomping.
        for (Entity e : entities) {
            if (e instanceof Critter && ((Critter) e).isHeavy() && !e.dead) {
                final Critter h = (Critter) e;
                forEachCellInRadius(h.x, h.y + h.r, 40f, new CellVisitor() {
                    public void visit(int col, int row) {
                        if (cellAt(col, row) == 'p') latchSwitch(col, row, 0.35f);
                    }
                });
            }
        }

        // Spirit in flight.
        if (spiritT > 0f) {
            spiritT -= dt;
            if (spiritT <= 0f) finishTransfer();
        }

        for (int i = 0; i < entities.size(); i++) {
            entities.get(i).update(dt);
        }
        if (!spawnQueue.isEmpty()) {
            entities.addAll(spawnQueue);
            spawnQueue.clear();
        }
        for (int i = entities.size() - 1; i >= 0; i--) {
            if (entities.get(i).removed) entities.remove(i);
        }

        // A live dash keeps chewing through whatever it touches.
        if (controlled instanceof Critter) {
            Critter cr = (Critter) controlled;
            if (cr.dashT > 0f) dashContacts(cr);
        }

        computeLight();
        if (state == PLAYING) {
            checkContacts();
            checkMarkers();
        }

        if (state == DEAD) {
            stateT += dt;
            if (stateT > 1.6f) respawn();
        } else if (state == COMPLETE) {
            stateT += dt;
        }

        Entity focus = controlled != null && !controlled.dead ? controlled : diver;
        cam.follow(focus.x, focus.y, dt);
        clampCamera();
        cam.update(dt);
    }

    private void clampCamera() {
        float halfW = cam.screenW * 0.5f / cam.scale;
        float halfH = cam.screenH * 0.5f / cam.scale;
        float w = cols * CS, h = rows * CS;
        if (w > halfW * 2f) cam.x = V.clamp(cam.x, halfW, w - halfW);
        else cam.x = w * 0.5f;
        if (h > halfH * 2f) cam.y = V.clamp(cam.y, halfH, h - halfH);
        else cam.y = h * 0.5f;
    }

    private void computeLight() {
        float ambient = 1f - def.dark * 0.94f;
        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            float l = ambient;
            for (int j = 0; j < entities.size(); j++) {
                Entity s = entities.get(j);
                float lr = s.lightRadius();
                if (lr <= 1f) continue;
                float d = V.dist(e.x, e.y, s.x, s.y);
                if (d > lr) continue;
                l += s.lightIntensity() * (1f - d / lr) * (1f - d / lr);
            }
            e.lit = V.clamp(l, 0f, 1f);
        }
    }

    private void checkContacts() {
        Entity player = controlled;
        if (player == null) return;

        // Hazards and predators kill whatever the spirit is currently wearing.
        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            if (e == player) continue;

            boolean lethal = false;
            if (e instanceof Hazard) {
                lethal = V.dist(e.x, e.y, player.x, player.y)
                        < e.hitRadius() + player.hitRadius();
            } else if (e instanceof Predator) {
                Predator p = (Predator) e;
                if (!p.stunned()) {
                    boolean safe = player instanceof Critter
                            && ((Critter) player).ignoredByPredators();
                    boolean latched = player instanceof Critter
                            && ((Critter) player).latched == p;
                    lethal = !safe && !latched
                            && V.dist(e.x, e.y, player.x, player.y)
                            < e.hitRadius() * 1.15f + player.hitRadius();
                }
            } else if (e instanceof Boss) {
                Boss b = (Boss) e;
                boolean latched = player instanceof Critter
                        && ((Critter) player).latched == b;
                lethal = !latched && b.bodyHits(player);
            }

            if (lethal) {
                if (player == diver) diver.kill();
                else if (player instanceof Critter) ((Critter) player).kill();
                return;
            }
        }

        // An inflated puffer shoulders hunters out of the way.
        if (player instanceof Critter && ((Critter) player).type == LevelDef.PUFFER
                && ((Critter) player).inflate > 0.6f) {
            for (Entity e : entities) {
                if (e instanceof Predator
                        && V.dist(e.x, e.y, player.x, player.y) < player.hitRadius() + 60f) {
                    ((Predator) e).shove(e.x - player.x, e.y - player.y, 420f);
                }
            }
        }
    }

    private void checkMarkers() {
        int col = (int) Math.floor(diver.x / CS);
        int row = (int) Math.floor(diver.y / CS);
        char c = cellAt(col, row);

        if (c == 'C') {
            float cx = col * CS + CS * 0.5f, cy = row * CS + CS * 0.5f;
            if (V.dist(respawnX, respawnY, cx, cy) > 4f) {
                respawnX = cx;
                respawnY = cy;
                sinceProgress = 0f;
                if (audio != null) audio.sfx(Synth.SFX_CHECKPOINT, 1f);
            }
        } else if (c == 'W') {
            if (whisperNext < def.whispers.length) {
                whisperText = def.whispers[whisperNext++];
                whisperT = 5.5f;
                cells[row * cols + col] = '.';   // each whisper fires once
            }
        } else if (c == 'X' && exitOpen()) {
            state = COMPLETE;
            stateT = 0f;
            if (audio != null) audio.sfx(Synth.SFX_COMPLETE, 1f);
        }
    }

    public boolean exitOpen() {
        return boss == null || boss.hp <= 0;
    }

    private void respawn() {
        diver.respawn(respawnX, respawnY);
        controlled = diver;
        diver.possessed = true;
        spiritFrom = spiritTo = null;
        spiritT = 0f;
        state = PLAYING;
        stateT = 0f;
        // Free every creature so a botched attempt can always be retried.
        for (Entity e : entities) {
            if (e instanceof Critter) ((Critter) e).possessed = false;
            if (e instanceof Predator) ((Predator) e).scare(0.6f);
        }
        for (int i = entities.size() - 1; i >= 0; i--) {
            Entity e = entities.get(i);
            if (e instanceof Hazard && ((Hazard) e).projectile) entities.remove(i);
        }
    }

    /** Points the hint arrow at a level feature and pulses a ring there. */
    public void showHintAt(float wx, float wy) {
        hintX = wx;
        hintY = wy;
        hintT = 7f;
    }

    // ================= rendering =================

    public void draw(Canvas c, Art art) {
        int w = cam.screenW, h = cam.screenH;
        float lightBoost = 1f - def.dark;

        art.background(c, w, h, def.depth, lightBoost);
        art.lightShafts(c, w, h, time, (1f - def.dark) * (1f - def.depth * 0.6f));

        // Distant ridge, moving at a fraction of the camera.
        c.save();
        c.translate(w * 0.5f - cam.x * cam.scale * 0.55f, h * 0.5f - cam.y * cam.scale * 0.55f);
        c.scale(cam.scale * 0.55f, cam.scale * 0.55f);
        art.solid.setShader(null);
        art.solid.setColor(Art.grey(V.lerp(0.10f, 0.03f, def.depth)));
        c.drawPath(farPath, art.solid);
        c.restore();

        art.motes(c, cam, time, 60, lightBoost);

        // Terrain and level features are authored in world units, so they draw
        // under the camera transform. Entities project themselves via the camera
        // and therefore draw afterwards, in screen space.
        c.save();
        c.translate(cam.sx(0f), cam.sy(0f));
        c.scale(cam.scale, cam.scale);
        drawCurrents(c, art);
        drawTerrain(c, art);
        drawFeatures(c, art);
        c.restore();

        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            if (e == diver || e == controlled) continue;
            if (!cam.visible(e.x, e.y, e.hitRadius() * 3f + 120f)) continue;
            e.draw(c, cam, art);
        }
        // The thing you are driving always renders last, on top.
        if (controlled != null && controlled != diver) controlled.draw(c, cam, art);
        diver.draw(c, cam, art);

        drawSpirit(c, art);
        drawHintRing(c, art);

        drawLightPass(c, art);
        art.motes(c, cam, time * 1.7f, 26, lightBoost);
        art.vignette(c, w, h, 0.85f);
    }

    private void drawTerrain(Canvas c, Art art) {
        art.solid.setShader(null);
        art.solid.setColor(Art.grey(0.0f));
        c.drawPath(rockPath, art.solid);

        // Rim light on upward faces: the one place grey touches the rock.
        art.stroke.setShader(null);
        art.stroke.setColor(Art.argb(0.16f * (1f - def.dark), 1f));
        art.stroke.setStrokeWidth(3.5f);
        c.drawPath(rimPath, art.stroke);
    }

    private void drawCurrents(Canvas c, Art art) {
        art.stroke.setShader(null);
        art.stroke.setStrokeWidth(2.5f);
        int c0 = (int) Math.floor((cam.x - cam.viewHalfW(CS)) / CS);
        int c1 = (int) Math.ceil((cam.x + cam.viewHalfW(CS)) / CS);
        int r0 = (int) Math.floor((cam.y - cam.viewHalfH(CS)) / CS);
        int r1 = (int) Math.ceil((cam.y + cam.viewHalfH(CS)) / CS);
        for (int row = Math.max(0, r0); row <= Math.min(rows - 1, r1); row++) {
            for (int col = Math.max(0, c0); col <= Math.min(cols - 1, c1); col++) {
                char ch = cellAt(col, row);
                if (!LevelDef.isCurrent(ch)) continue;
                float dx = ch == '<' ? -1 : (ch == '>' ? 1 : 0);
                float dy = ch == '^' ? -1 : (ch == 'v' ? 1 : 0);
                float bx = col * CS, by = row * CS;
                for (int k = 0; k < 3; k++) {
                    float phase = (time * 0.9f + k * 0.333f + V.hashF(col * 31 + row, 7)) % 1f;
                    float len = CS * 0.42f;
                    float px = bx + CS * 0.5f + dx * (phase - 0.5f) * CS
                            + (dy != 0 ? (k - 1) * CS * 0.26f : 0f);
                    float py = by + CS * 0.5f + dy * (phase - 0.5f) * CS
                            + (dx != 0 ? (k - 1) * CS * 0.26f : 0f);
                    float a = (float) Math.sin(phase * Math.PI) * 0.30f;
                    art.stroke.setColor(Art.argb(a, 1f));
                    c.drawLine(px - dx * len * 0.5f, py - dy * len * 0.5f,
                            px + dx * len * 0.5f, py + dy * len * 0.5f, art.stroke);
                }
            }
        }
    }

    private void drawFeatures(Canvas c, Art art) {
        int c0 = (int) Math.floor((cam.x - cam.viewHalfW(CS * 2)) / CS);
        int c1 = (int) Math.ceil((cam.x + cam.viewHalfW(CS * 2)) / CS);
        int r0 = (int) Math.floor((cam.y - cam.viewHalfH(CS * 2)) / CS);
        int r1 = (int) Math.ceil((cam.y + cam.viewHalfH(CS * 2)) / CS);

        for (int row = Math.max(0, r0); row <= Math.min(rows - 1, r1); row++) {
            for (int col = Math.max(0, c0); col <= Math.min(cols - 1, c1); col++) {
                char ch = cellAt(col, row);
                float bx = col * CS, by = row * CS;
                switch (ch) {
                    case 'k': drawKelp(c, art, col, row, bx, by); break;
                    case 'r': drawBrittle(c, art, col, row, bx, by); break;
                    case 'g': drawGate(c, art, col, row, bx, by); break;
                    case 'p': drawPlate(c, art, col, row, bx, by); break;
                    case 'n': drawNode(c, art, col, row, bx, by); break;
                    case 'l': drawLever(c, art, col, row, bx, by); break;
                    case 'C': drawCheckpoint(c, art, bx, by); break;
                    case 'X': drawExit(c, art, bx, by); break;
                }
            }
        }
    }

    private void drawKelp(Canvas c, Art art, int col, int row, float bx, float by) {
        if (isBroken(col, row)) return;
        int ink = Art.grey(0.0f);
        for (int i = 0; i < 5; i++) {
            float ox = bx + CS * (0.12f + i * 0.19f);
            float sway = (float) Math.sin(time * 0.8f + i * 0.9f + col) * CS * 0.13f;
            art.frond(c, ox, by + CS, CS * (0.92f + 0.1f * V.hashF(i + col, row)),
                    sway, CS * 0.11f, 5, ink);
        }
    }

    private void drawBrittle(Canvas c, Art art, int col, int row, float bx, float by) {
        if (isBroken(col, row)) return;
        art.solid.setShader(null);
        art.solid.setColor(Art.grey(0.0f));
        c.drawRect(bx, by, bx + CS, by + CS, art.solid);
        // Fracture lines mark it as the rock that can be broken.
        art.stroke.setShader(null);
        art.stroke.setColor(Art.argb(0.30f * (1f - def.dark * 0.6f), 1f));
        art.stroke.setStrokeWidth(2.2f);
        for (int i = 0; i < 3; i++) {
            float u = 0.25f + i * 0.25f;
            float j = (V.hashF(col * 7 + row * 13 + i, 5) - 0.5f) * CS * 0.3f;
            c.drawLine(bx + CS * u + j, by + CS * 0.08f,
                    bx + CS * (1f - u) - j, by + CS * 0.92f, art.stroke);
        }
    }

    private void drawGate(Canvas c, Art art, int col, int row, float bx, float by) {
        int ch = V.clampI(def.channelAt(col, row), 0, 9);
        float open = gateAnim[ch];
        float half = CS * 0.5f * (1f - open);
        art.solid.setShader(null);
        art.solid.setColor(Art.grey(0.0f));
        // Two shutters that withdraw into the frame.
        c.drawRect(bx, by, bx + CS, by + half, art.solid);
        c.drawRect(bx, by + CS - half, bx + CS, by + CS, art.solid);
        art.stroke.setShader(null);
        art.stroke.setColor(Art.argb(0.42f, 1f));
        art.stroke.setStrokeWidth(2.4f);
        c.drawLine(bx + 3f, by + half, bx + CS - 3f, by + half, art.stroke);
        c.drawLine(bx + 3f, by + CS - half, bx + CS - 3f, by + CS - half, art.stroke);
        channelPip(c, art, bx + CS * 0.5f, by + CS * 0.5f, ch, open > 0.5f);
    }

    private void drawPlate(Canvas c, Art art, int col, int row, float bx, float by) {
        int k = switchIndexAt(col, row);
        boolean on = k >= 0 && switchOn[k];
        art.solid.setShader(null);
        art.solid.setColor(Art.grey(0.0f));
        float drop = on ? CS * 0.10f : 0f;
        c.drawRect(bx + CS * 0.08f, by + CS * 0.62f + drop,
                bx + CS * 0.92f, by + CS * 0.82f + drop, art.solid);
        art.stroke.setShader(null);
        art.stroke.setColor(Art.argb(on ? 0.72f : 0.28f, 1f));
        art.stroke.setStrokeWidth(2.6f);
        c.drawLine(bx + CS * 0.1f, by + CS * 0.62f + drop,
                bx + CS * 0.9f, by + CS * 0.62f + drop, art.stroke);
        if (on && k >= 0 && switchTimer[k] > 0.5f) {
            timerArc(c, art, bx + CS * 0.5f, by + CS * 0.35f, switchTimer[k] / 12f);
        }
        channelPip(c, art, bx + CS * 0.5f, by + CS * 0.9f,
                V.clampI(def.channelAt(col, row), 0, 9), on);
    }

    private void drawNode(Canvas c, Art art, int col, int row, float bx, float by) {
        int k = switchIndexAt(col, row);
        boolean on = k >= 0 && switchOn[k];
        float cx = bx + CS * 0.5f, cy = by + CS * 0.5f;
        art.solid.setShader(null);
        art.solid.setColor(Art.grey(0.0f));
        c.drawCircle(cx, cy, CS * 0.26f, art.solid);
        art.ring(c, cx, cy, CS * 0.26f, 2.6f, Art.argb(on ? 0.85f : 0.30f, 1f));
        if (on) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(time * 8f);
            art.stroke.setStrokeWidth(1.8f);
            art.stroke.setColor(Art.argb(0.5f + pulse * 0.4f, 1f));
            for (int i = 0; i < 6; i++) {
                float a = i / 6f * V.TAU + time * 2.2f;
                c.drawLine(cx + (float) Math.cos(a) * CS * 0.28f,
                        cy + (float) Math.sin(a) * CS * 0.28f,
                        cx + (float) Math.cos(a) * CS * 0.40f,
                        cy + (float) Math.sin(a) * CS * 0.40f, art.stroke);
            }
            if (k >= 0 && switchTimer[k] > 0.5f) {
                timerArc(c, art, cx, cy - CS * 0.45f, switchTimer[k] / 11f);
            }
        }
        channelPip(c, art, cx, by + CS * 0.9f, V.clampI(def.channelAt(col, row), 0, 9), on);
    }

    private void drawLever(Canvas c, Art art, int col, int row, float bx, float by) {
        int k = switchIndexAt(col, row);
        boolean on = k >= 0 && switchOn[k];
        float cx = bx + CS * 0.5f, cy = by + CS * 0.62f;
        art.solid.setShader(null);
        art.solid.setColor(Art.grey(0.0f));
        // A clamshell that snaps open.
        float open = on ? 0.55f : 0.06f;
        c.drawArc(cx - CS * 0.34f, cy - CS * 0.34f, cx + CS * 0.34f, cy + CS * 0.34f,
                180f + open * 40f, 180f - open * 80f, true, art.solid);
        c.drawArc(cx - CS * 0.34f, cy - CS * 0.34f, cx + CS * 0.34f, cy + CS * 0.34f,
                -open * 40f, 180f - open * 80f, true, art.solid);
        art.ring(c, cx, cy, CS * 0.34f, 2.2f, Art.argb(on ? 0.7f : 0.26f, 1f));
        if (on) art.eye(c, cx, cy, CS * 0.09f, 0.9f, 0f);
        channelPip(c, art, cx, by + CS * 0.94f, V.clampI(def.channelAt(col, row), 0, 9), on);
    }

    /** Small dot pattern tying a switch to the gate it drives. */
    private void channelPip(Canvas c, Art art, float cx, float cy, int ch, boolean on) {
        art.solid.setShader(null);
        art.solid.setColor(Art.argb(on ? 0.8f : 0.34f, 1f));
        int n = ch + 1;
        for (int i = 0; i < n; i++) {
            float ox = (i - (n - 1) * 0.5f) * CS * 0.11f;
            c.drawCircle(cx + ox, cy, CS * 0.032f, art.solid);
        }
    }

    private void timerArc(Canvas c, Art art, float cx, float cy, float frac) {
        art.stroke.setShader(null);
        art.stroke.setColor(Art.argb(0.7f, 1f));
        art.stroke.setStrokeWidth(2.4f);
        scratch.reset();
        scratch.addArc(cx - CS * 0.16f, cy - CS * 0.16f, cx + CS * 0.16f, cy + CS * 0.16f,
                -90f, 360f * V.clamp(frac, 0f, 1f));
        c.drawPath(scratch, art.stroke);
    }

    private int switchIndexAt(int col, int row) {
        int i = row * cols + col;
        for (int k = 0; k < switchIdx.size(); k++) {
            if (switchIdx.get(k) == i) return k;
        }
        return -1;
    }

    private void drawCheckpoint(Canvas c, Art art, float bx, float by) {
        float cx = bx + CS * 0.5f, cy = by + CS * 0.5f;
        boolean here = V.dist(respawnX, respawnY, cx, cy) < 4f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 1.6f);
        art.stroke.setShader(null);
        art.stroke.setColor(Art.argb((here ? 0.55f : 0.20f) + pulse * 0.12f, 1f));
        art.stroke.setStrokeWidth(2.2f);
        // A ring of bone markers.
        for (int i = 0; i < 8; i++) {
            float a = i / 8f * V.TAU + time * 0.2f;
            float r0 = CS * 0.22f, r1 = CS * (here ? 0.36f : 0.30f);
            c.drawLine(cx + (float) Math.cos(a) * r0, cy + (float) Math.sin(a) * r0,
                    cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1, art.stroke);
        }
        if (here) art.eye(c, cx, cy, CS * 0.07f, 0.8f, 0f);
    }

    private void drawExit(Canvas c, Art art, float bx, float by) {
        float cx = bx + CS * 0.5f, cy = by + CS * 0.5f;
        boolean open = exitOpen();
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 1.1f);
        if (open) {
            art.lightPool(c, cx, cy, CS * (1.5f + pulse * 0.25f), 0.42f);
        }
        art.stroke.setShader(null);
        art.stroke.setColor(Art.argb(open ? 0.5f + pulse * 0.3f : 0.16f, 1f));
        art.stroke.setStrokeWidth(3f);
        for (int i = 0; i < 3; i++) {
            float rr = CS * (0.2f + i * 0.13f) + (open ? pulse * CS * 0.05f : 0f);
            c.drawCircle(cx, cy, rr, art.stroke);
        }
    }

    private void drawSpirit(Canvas c, Art art) {
        if (spiritT <= 0f || spiritFrom == null || spiritTo == null) return;
        float u = 1f - spiritT / spiritDur;
        float e = V.easeOut(u);
        float px = cam.sx(V.lerp(spiritFrom.x, spiritTo.x, e));
        float py = cam.sy(V.lerp(spiritFrom.y, spiritTo.y, e));

        art.stroke.setShader(null);
        art.stroke.setStrokeWidth(3.5f * cam.scale);
        // A comet tail behind the travelling spirit.
        for (int i = 0; i < 10; i++) {
            float u2 = V.clamp(e - i * 0.035f, 0f, 1f);
            float u3 = V.clamp(e - (i + 1) * 0.035f, 0f, 1f);
            art.stroke.setColor(Art.argb(0.55f * (1f - i / 10f), 1f));
            c.drawLine(cam.sx(V.lerp(spiritFrom.x, spiritTo.x, u2)),
                    cam.sy(V.lerp(spiritFrom.y, spiritTo.y, u2)),
                    cam.sx(V.lerp(spiritFrom.x, spiritTo.x, u3)),
                    cam.sy(V.lerp(spiritFrom.y, spiritTo.y, u3)), art.stroke);
        }
        art.lightPool(c, px, py, 34f * cam.scale, 0.5f);
        art.solid.setShader(null);
        art.solid.setColor(Art.argb(0.95f, 1f));
        c.drawCircle(px, py, 5.5f * cam.scale, art.solid);
    }

    private void drawHintRing(Canvas c, Art art) {
        if (hintT <= 0f) return;
        float pulse = (hintT * 2.2f) % 1f;
        float sx = cam.sx(hintX), sy = cam.sy(hintY);
        float rr = CS * (0.35f + pulse * 1.1f) * cam.scale;
        float fade = V.clamp(hintT / 1.5f, 0f, 1f);
        art.ring(c, sx, sy, rr, 3.5f, Art.argb((1f - pulse) * 0.6f * fade, 1f));
        art.ring(c, sx, sy, CS * 0.3f * cam.scale, 2.5f, Art.argb(0.35f * fade, 1f));
    }

    /** Additive glows, then the darkness mask that makes light matter. */
    private void drawLightPass(Canvas c, Art art) {
        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            float lr = e.lightRadius();
            if (lr <= 1f || e.dead) continue;
            if (!cam.visible(e.x, e.y, lr)) continue;
            art.lightPool(c, cam.sx(e.x), cam.sy(e.y), lr * cam.scale, e.lightIntensity() * 0.5f);
        }

        if (def.dark <= 0.01f) return;

        int layer = c.saveLayer(0, 0, cam.screenW, cam.screenH, null);
        art.plain.setXfermode(null);
        art.plain.setShader(null);
        art.plain.setColor(Art.argb(def.dark * 0.96f, 0f));
        c.drawRect(0, 0, cam.screenW, cam.screenH, art.plain);

        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            float lr = e.lightRadius();
            if (lr <= 1f || e.dead) continue;
            if (!cam.visible(e.x, e.y, lr)) continue;
            float rr = lr * cam.scale;
            float inten = V.clamp(e.lightIntensity(), 0f, 1f);
            maskPaint.setShader(new RadialGradient(cam.sx(e.x), cam.sy(e.y), rr,
                    new int[]{Art.argb(inten, 1f), Art.argb(inten * 0.55f, 1f), Art.argb(0f, 1f)},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            c.drawCircle(cam.sx(e.x), cam.sy(e.y), rr, maskPaint);
        }
        maskPaint.setShader(null);
        c.restoreToCount(layer);
    }
}
