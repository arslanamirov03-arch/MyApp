package com.abyss.silt;

import android.graphics.Canvas;
import android.view.MotionEvent;

import com.abyss.silt.audio.Synth;
import com.abyss.silt.core.Art;
import com.abyss.silt.core.V;
import com.abyss.silt.entities.Critter;
import com.abyss.silt.level.LevelDef;
import com.abyss.silt.level.Levels;
import com.abyss.silt.ui.Controls;
import com.abyss.silt.ui.Hud;

/**
 * Screen flow and per-level bookkeeping: menus, chapter cards, the hint system
 * and the hand-off into {@link World} while a level is actually running.
 */
public class Game {

    public static final int BOOT = 0, MENU = 1, CHAPTERS = 2, INTRO = 3,
            PLAY = 4, PAUSE = 5, CLEARED = 6, ENDING = 7;

    public int state = BOOT;
    private int nextState = -1;
    private float stateT = 0f;
    private float fade = 1f;          // 1 = fully black
    private boolean fadingOut = false;

    public final World world = new World();
    public final Art art = new Art();
    public final Hud hud = new Hud();
    public final Controls controls = new Controls();
    public final Synth synth = new Synth();
    private final SaveData save;

    public int levelIndex = 0;
    private int chapterShown = -1;

    // Hint state for the current level.
    private int hintStep = 0;
    private String hintText = null;
    private float hintTextT = 0f;

    // Chapter select scroll.
    private float chapterScroll = 0f;

    private int screenW = 1, screenH = 1;
    private float bootT = 0f;

    public Game(SaveData save) {
        this.save = save;
        synth.muted = !save.soundOn();
        world.audio = synth;
    }

    public void resize(int w, int h) {
        screenW = w;
        screenH = h;
        world.cam.resize(w, h);
        hud.resize(w, h);
        controls.resize(w, h);
    }

    public void onTouch(MotionEvent ev) {
        controls.onTouch(ev);
    }

    /** Android back button. Returns false if the game wants to be closed. */
    public boolean onBack() {
        switch (state) {
            case PLAY:
                go(PAUSE);
                return true;
            case PAUSE:
                go(PLAY);
                return true;
            case CHAPTERS:
            case ENDING:
                go(MENU);
                return true;
            case CLEARED:
                advance();
                return true;
            default:
                return false;
        }
    }

    private void go(int s) {
        // PAUSE toggles instantly; everything else crossfades through black.
        if (s == PAUSE || state == PAUSE) {
            state = s;
            stateT = 0f;
            controls.releaseAll();
            return;
        }
        nextState = s;
        fadingOut = true;
    }

    // ================= update =================

    public void update(float dt) {
        stateT += dt;
        controls.update(dt);

        if (fadingOut) {
            fade = Math.min(1f, fade + dt * 2.6f);
            if (fade >= 1f && nextState >= 0) {
                enter(nextState);
                nextState = -1;
                fadingOut = false;
            }
        } else if (fade > 0f) {
            fade = Math.max(0f, fade - dt * 1.7f);
        }

        switch (state) {
            case BOOT:    updateBoot(dt); break;
            case MENU:    updateMenu(dt); break;
            case CHAPTERS: updateChapters(dt); break;
            case INTRO:   updateIntro(dt); break;
            case PLAY:    updatePlay(dt); break;
            case PAUSE:   updatePause(dt); break;
            case CLEARED: updateCleared(dt); break;
            case ENDING:  updateEnding(dt); break;
        }
    }

    private void enter(int s) {
        state = s;
        stateT = 0f;
        controls.releaseAll();
        hintTextT = 0f;
        hintText = null;

        if (s == PLAY) {
            controls.pause.visible = true;
            controls.action.visible = true;
            controls.recall.visible = true;
        } else {
            controls.pause.visible = false;
            controls.action.visible = false;
            controls.recall.visible = false;
            controls.hint.visible = false;
        }
        if (s == INTRO) {
            chapterShown = Levels.get(levelIndex).chapter;
        }
        synth.startAmbient();
    }

    private void updateBoot(float dt) {
        bootT += dt;
        if (bootT > 2.4f || controls.takeTap()) {
            synth.startAmbient();
            go(MENU);
        }
    }

    private void updateMenu(float dt) {
        if (!controls.takeTap()) return;
        float x = controls.tapX, y = controls.tapY;
        float base = screenH * 0.56f, step = hud.u * 0.105f;

        if (hud.hitRow(x, y, base, hud.u * 0.05f)) {
            levelIndex = V.clampI(save.lastPlayed(), 0, Levels.count() - 1);
            synth.sfx(Synth.SFX_UI, 1f);
            go(INTRO);
        } else if (hud.hitRow(x, y, base + step, hud.u * 0.05f)) {
            synth.sfx(Synth.SFX_UI, 1f);
            go(CHAPTERS);
        } else if (hud.hitRow(x, y, base + step * 2f, hud.u * 0.05f)) {
            boolean on = !save.soundOn();
            save.setSoundOn(on);
            synth.muted = !on;
            if (on) {
                synth.startAmbient();
                synth.sfx(Synth.SFX_UI, 1f);
            } else {
                synth.stopAmbient();
            }
        }
    }

    private void updateChapters(float dt) {
        // The stick doubles as a scroller on this screen.
        chapterScroll = V.clamp(chapterScroll + controls.dirY() * dt * 900f,
                0f, Math.max(0f, Levels.count() * hud.u * 0.085f - screenH * 0.55f));

        if (!controls.takeTap()) return;
        float x = controls.tapX, y = controls.tapY;
        if (y < screenH * 0.16f) {
            synth.sfx(Synth.SFX_UI, 1f);
            go(MENU);
            return;
        }
        int idx = chapterRowAt(x, y);
        if (idx >= 0 && idx <= save.unlocked() && idx < Levels.count()) {
            levelIndex = idx;
            synth.sfx(Synth.SFX_UI, 1f);
            go(INTRO);
        }
    }

    private int chapterRowAt(float px, float py) {
        float top = screenH * 0.22f - chapterScroll;
        float rowH = hud.u * 0.085f;
        if (px < screenW * 0.14f || px > screenW * 0.86f) return -1;
        int i = (int) Math.floor((py - top) / rowH);
        return (i >= 0 && i < Levels.count()) ? i : -1;
    }

    private void updateIntro(float dt) {
        if (stateT > 2.2f || controls.takeTap()) {
            startLevel(levelIndex);
            go(PLAY);
        }
    }

    private void startLevel(int index) {
        LevelDef d = Levels.get(index);
        world.load(d);
        hintStep = 0;
        hintText = null;
        hintTextT = 0f;
        save.setLastPlayed(index);
        save.unlock(index);
    }

    private void updatePlay(float dt) {
        world.inputX = controls.dirX();
        world.inputY = controls.dirY();

        // Button labels track whatever the spirit is currently wearing.
        boolean inCritter = world.controlled instanceof Critter;
        controls.action.enabled = inCritter && !world.transferring();
        controls.action.label = inCritter
                ? ((Critter) world.controlled).abilityName() : "—";
        controls.recall.enabled = inCritter && !world.transferring();
        controls.recall.label = "BODY";

        boolean hintReady = hintStep < Levels.get(levelIndex).hints.length
                && (world.sinceProgress > 40f || world.deaths >= 2 || hintStep > 0);
        controls.hint.visible = hintReady || hintTextT > 0f;
        controls.hint.enabled = hintReady;

        if (controls.takePress(controls.pause)) {
            go(PAUSE);
            return;
        }
        if (controls.takePress(controls.action)) world.useAbility();
        if (controls.takePress(controls.recall)) world.releaseSpirit();
        if (controls.takePress(controls.hint) && hintReady) revealHint();

        if (controls.takeTap()) {
            float wx = world.cam.wx(controls.tapX);
            float wy = world.cam.wy(controls.tapY);
            if (!world.tryPossess(wx, wy)) {
                // Tapping empty water while wearing a creature is a quick recall.
                if (inCritter && V.dist(wx, wy, world.diver.x, world.diver.y) < 140f) {
                    world.releaseSpirit();
                }
            }
        }

        world.update(dt);
        hintTextT = Math.max(0f, hintTextT - dt);

        if (world.state == World.COMPLETE && world.stateT > 1.1f) {
            save.recordClear(levelIndex, world.elapsed, world.deaths);
            save.unlock(Math.min(levelIndex + 1, Levels.count() - 1));
            go(CLEARED);
        }
    }

    private void revealHint() {
        String[] hints = Levels.get(levelIndex).hints;
        if (hintStep >= hints.length) return;
        hintText = hints[hintStep];
        hintStep++;
        hintTextT = 13f;
        save.addHintUsed();
        synth.sfx(Synth.SFX_UI, 0.7f);
        // Point at whatever still stands between the diver and the exit.
        float[] target = autoHintTarget();
        if (target != null) world.showHintAt(target[0], target[1]);
    }

    /**
     * Picks the most useful thing to ring: an unsatisfied switch first, then a
     * breakable still in the way, then the exit.
     */
    private float[] autoHintTarget() {
        float bestD = Float.MAX_VALUE;
        float[] best = null;
        float[] exit = null;
        for (int row = 0; row < world.rows; row++) {
            for (int col = 0; col < world.cols; col++) {
                char ch = world.cellAt(col, row);
                float cx = col * World.CS + World.CS * 0.5f;
                float cy = row * World.CS + World.CS * 0.5f;
                if (ch == 'X') {
                    exit = new float[]{cx, cy};
                    continue;
                }
                boolean interesting = false;
                if (LevelDef.isSwitch(ch)) {
                    interesting = true;                       // switches first
                } else if ((ch == 'k' || ch == 'r') && !world.isBroken(col, row)) {
                    interesting = true;
                }
                if (!interesting) continue;
                float d = V.dist(world.diver.x, world.diver.y, cx, cy);
                if (LevelDef.isSwitch(ch)) d *= 0.5f;         // bias toward mechanisms
                if (d < bestD) {
                    bestD = d;
                    best = new float[]{cx, cy};
                }
            }
        }
        return best != null ? best : exit;
    }

    private void updatePause(float dt) {
        if (controls.takePress(controls.pause)) {
            go(PLAY);
            return;
        }
        if (!controls.takeTap()) return;
        float x = controls.tapX, y = controls.tapY;
        float base = screenH * 0.44f, step = hud.u * 0.105f;
        if (hud.hitRow(x, y, base, hud.u * 0.05f)) {
            synth.sfx(Synth.SFX_UI, 1f);
            go(PLAY);
        } else if (hud.hitRow(x, y, base + step, hud.u * 0.05f)) {
            synth.sfx(Synth.SFX_UI, 1f);
            startLevel(levelIndex);
            go(PLAY);
        } else if (hud.hitRow(x, y, base + step * 2f, hud.u * 0.05f)) {
            boolean on = !save.soundOn();
            save.setSoundOn(on);
            synth.muted = !on;
            if (on) synth.startAmbient(); else synth.stopAmbient();
        } else if (hud.hitRow(x, y, base + step * 3f, hud.u * 0.05f)) {
            synth.sfx(Synth.SFX_UI, 1f);
            go(MENU);
        }
    }

    private void updateCleared(float dt) {
        if (stateT > 0.7f && controls.takeTap()) advance();
    }

    private void advance() {
        int next = levelIndex + 1;
        if (next >= Levels.count()) {
            go(ENDING);
            return;
        }
        boolean newChapter = Levels.get(next).chapter != Levels.get(levelIndex).chapter;
        levelIndex = next;
        save.unlock(next);
        if (newChapter) {
            go(INTRO);
        } else {
            startLevel(levelIndex);
            go(PLAY);
        }
    }

    private void updateEnding(float dt) {
        if (stateT > 3f && controls.takeTap()) go(MENU);
    }

    // ================= drawing =================

    public void draw(Canvas c) {
        switch (state) {
            case BOOT:     drawBoot(c); break;
            case MENU:     drawMenu(c); break;
            case CHAPTERS: drawChapters(c); break;
            case INTRO:    drawIntro(c); break;
            case PLAY:     drawPlay(c, 1f); break;
            case PAUSE:    drawPause(c); break;
            case CLEARED:  drawCleared(c); break;
            case ENDING:   drawEnding(c); break;
        }
        if (fade > 0.002f) hud.dim(c, art, fade);
    }

    private void drawBoot(Canvas c) {
        c.drawColor(0xFF000000);
        float a = V.smooth(0.2f, 1.0f, bootT) * (1f - V.smooth(1.9f, 2.4f, bootT));
        hud.label(c, art, "A SILHOUETTE PUZZLE DESCENT",
                screenW * 0.5f, screenH * 0.5f, hud.u * 0.030f, a * 0.55f, 0.4f);
    }

    private void drawMenu(Canvas c) {
        drawMenuBackdrop(c);

        float a = V.smooth(0f, 0.8f, stateT);
        hud.title(c, art, "SILTBOUND", screenH * 0.30f, hud.u * 0.15f, 0.94f * a);
        hud.label(c, art, "T H E   A B Y S S", screenW * 0.5f, screenH * 0.38f,
                hud.u * 0.036f, 0.42f * a, 0.5f);

        float base = screenH * 0.56f, step = hud.u * 0.105f;
        int last = V.clampI(save.lastPlayed(), 0, Levels.count() - 1);
        String cont = save.unlocked() > 0 || save.isCleared(0)
                ? "CONTINUE  ·  " + Levels.get(last).title.toUpperCase()
                : "DESCEND";
        hud.menuItem(c, art, cont, base, hud.u * 0.046f, true, a);
        hud.menuItem(c, art, "CHAPTERS", base + step, hud.u * 0.042f, false, a);
        hud.menuItem(c, art, save.soundOn() ? "SOUND  ON" : "SOUND  OFF",
                base + step * 2f, hud.u * 0.042f, false, a);

        int cleared = save.clearedCount(0, Levels.count());
        hud.label(c, art, cleared + " / " + Levels.count() + "  DEPTHS  CHARTED",
                screenW * 0.5f, screenH * 0.93f, hud.u * 0.026f, 0.3f * a, 0.3f);
    }

    /** A slow silhouette drifting behind the menus. */
    private void drawMenuBackdrop(Canvas c) {
        art.background(c, screenW, screenH, 0.55f, 0.6f);
        art.lightShafts(c, screenW, screenH, stateT * 0.5f, 0.7f);

        // One creature crosses the frame, low and slow, sized to stay clear of
        // the menu text above it.
        float t = stateT * 0.18f;
        float len = hud.u * 0.62f;
        float girth = len * 0.20f;
        // Swings back and forth rather than traversing, so the frame is never empty.
        float dir = (float) Math.cos(t) >= 0 ? 1f : -1f;
        float cx = screenW * 0.5f + (float) Math.sin(t) * screenW * 0.34f;
        float cy = screenH * (0.76f + 0.035f * (float) Math.sin(stateT * 0.5f));
        float ang = dir > 0 ? 0f : (float) Math.PI;
        float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
        int ink = Art.argb(0.95f, 0.0f);

        // The nose sits at (cx, cy) and the body runs backwards from it, so every
        // feature on the head is placed at a NEGATIVE offset along the facing.
        art.body(c, cx, cy, ang, 16, len, girth, girth * 0.35f, stateT * 1.1f, 3.2f, ink);
        art.fin(c, cx - ca * len * 0.94f, cy - sa * len * 0.94f, ang + (float) Math.PI,
                len * 0.30f, girth * 1.4f, ink);
        art.fin(c, cx - ca * len * 0.40f - sa * girth * 0.75f,
                cy - sa * len * 0.40f + ca * girth * 0.75f,
                ang - 1.85f, len * 0.20f, girth * 0.5f, ink);
        // Jaw line along the front third, teeth biting outward from it.
        float jawAx = cx - ca * len * 0.30f, jawAy = cy - sa * len * 0.30f;
        float jawBx = cx - ca * len * 0.02f, jawBy = cy - sa * len * 0.02f;
        art.teeth(c, jawAx, jawAy, jawBx, jawBy, 6, girth * 0.55f, girth * 0.30f, ink);
        art.teeth(c, jawAx, jawAy, jawBx, jawBy, 6, girth * 0.45f, -girth * 0.26f, ink);
        art.eye(c, cx - ca * len * 0.15f - sa * girth * 0.48f,
                cy - sa * len * 0.15f + ca * girth * 0.48f,
                girth * 0.16f, 0.88f, 0.42f);

        art.motes(c, world.cam, stateT, 40, 0.7f);
        art.vignette(c, screenW, screenH, 0.9f);
    }

    private void drawChapters(Canvas c) {
        art.background(c, screenW, screenH, 0.7f, 0.4f);
        art.vignette(c, screenW, screenH, 0.9f);

        hud.label(c, art, "CHAPTERS", screenW * 0.5f, screenH * 0.11f,
                hud.u * 0.055f, 0.85f, 0.4f);
        hud.label(c, art, "TAP TO DIVE  ·  TOP OF SCREEN TO GO BACK",
                screenW * 0.5f, screenH * 0.155f, hud.u * 0.024f, 0.32f, 0.22f);

        float top = screenH * 0.22f - chapterScroll;
        float rowH = hud.u * 0.085f;
        int shownChapter = -1;
        for (int i = 0; i < Levels.count(); i++) {
            float cy = top + i * rowH + rowH * 0.5f;
            if (cy < screenH * 0.17f || cy > screenH * 1.02f) continue;
            LevelDef d = Levels.get(i);
            boolean open = i <= save.unlocked();
            boolean done = save.isCleared(i);

            if (d.chapter != shownChapter) {
                shownChapter = d.chapter;
                hud.label(c, art, Levels.chapterTitle(d.chapter),
                        screenW * 0.5f, cy - rowH * 0.42f, hud.u * 0.023f, 0.30f, 0.45f);
            }

            float a = open ? 1f : 0.28f;
            String name = open ? (i + 1) + ".  " + d.title.toUpperCase() : (i + 1) + ".  " + "· · · · ·";
            hud.label(c, art, name, screenW * 0.5f, cy + rowH * 0.16f,
                    hud.u * 0.033f, (done ? 0.88f : 0.62f) * a, 0.18f);
            if (done) {
                hud.label(c, art, Hud.timeStr(save.bestTime(i)),
                        screenW * 0.84f, cy + rowH * 0.16f, hud.u * 0.026f, 0.45f, 0.1f);
            }
        }
    }

    private void drawIntro(Canvas c) {
        art.background(c, screenW, screenH, 0.8f, 0.25f);
        art.motes(c, world.cam, stateT, 30, 0.5f);
        art.vignette(c, screenW, screenH, 0.95f);

        float a = V.smooth(0.1f, 0.7f, stateT) * (1f - V.smooth(1.9f, 2.2f, stateT));
        LevelDef d = Levels.get(levelIndex);
        hud.label(c, art, Levels.chapterTitle(d.chapter), screenW * 0.5f, screenH * 0.42f,
                hud.u * 0.028f, 0.45f * a, 0.55f);
        hud.title(c, art, d.title.toUpperCase(), screenH * 0.53f, hud.u * 0.070f, 0.92f * a);
        hud.label(c, art, "DEPTH  " + (levelIndex + 1) + " / " + Levels.count(),
                screenW * 0.5f, screenH * 0.62f, hud.u * 0.024f, 0.34f * a, 0.4f);
    }

    private void drawPlay(Canvas c, float alpha) {
        world.draw(c, art);

        // A ring on the creature the spirit could jump to next.
        if (!world.transferring() && world.state == World.PLAYING) {
            Critter cand = world.possessCandidate();
            if (cand != null && !cand.dead) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(stateT * 4f);
                art.ring(c, world.cam.sx(cand.x), world.cam.sy(cand.y),
                        cand.hitRadius() * world.cam.scale * 2.1f,
                        2.2f, Art.argb((0.18f + pulse * 0.18f) * alpha, 1f));
            }
        }

        controls.draw(c, art, alpha);

        hud.whisper(c, art, world.whisperText,
                V.clamp(world.whisperT / 1.2f, 0f, 1f) * alpha);

        if (hintTextT > 0f) {
            hud.hintPlate(c, art, hintText, V.clamp(hintTextT / 1.2f, 0f, 1f) * alpha);
        }

        if (world.state == World.DEAD) {
            hud.dim(c, art, V.clamp(world.stateT / 1.0f, 0f, 1f) * 0.72f * alpha);
            hud.label(c, art, "THE SILT TAKES YOU BACK", screenW * 0.5f, screenH * 0.5f,
                    hud.u * 0.036f, V.clamp(world.stateT - 0.3f, 0f, 1f) * 0.7f * alpha, 0.35f);
        } else if (world.state == World.COMPLETE) {
            hud.dim(c, art, V.clamp(world.stateT / 1.1f, 0f, 1f) * 0.55f * alpha);
        }

        if (world.boss != null && world.boss.hp > 0) drawBossHealth(c, alpha);
    }

    private void drawBossHealth(Canvas c, float alpha) {
        int max = world.boss.id == 1 ? 3 : (world.boss.id == 2 ? 4 : 5);
        float cy = screenH * 0.055f;
        float gap = hud.u * 0.035f;
        float total = (max - 1) * gap;
        for (int i = 0; i < max; i++) {
            float cx = screenW * 0.5f - total * 0.5f + i * gap;
            boolean alive = i < world.boss.hp;
            art.solid.setShader(null);
            art.solid.setColor(Art.argb((alive ? 0.8f : 0.16f) * alpha, 1f));
            c.drawCircle(cx, cy, hud.u * 0.0085f, art.solid);
        }
        if (world.boss.vulnerable()) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(stateT * 9f);
            hud.label(c, art, "THE EYE IS OPEN", screenW * 0.5f, screenH * 0.105f,
                    hud.u * 0.028f, (0.4f + pulse * 0.45f) * alpha, 0.4f);
        }
    }

    private void drawPause(Canvas c) {
        drawPlay(c, 0.35f);
        hud.dim(c, art, 0.68f);

        hud.label(c, art, Levels.get(levelIndex).title.toUpperCase(),
                screenW * 0.5f, screenH * 0.26f, hud.u * 0.050f, 0.9f, 0.35f);
        hud.label(c, art, "TIME  " + Hud.timeStr(world.elapsed)
                        + "      LOSSES  " + world.deaths,
                screenW * 0.5f, screenH * 0.33f, hud.u * 0.026f, 0.4f, 0.3f);

        float base = screenH * 0.44f, step = hud.u * 0.105f;
        hud.menuItem(c, art, "RESUME", base, hud.u * 0.044f, true, 1f);
        hud.menuItem(c, art, "RESTART DEPTH", base + step, hud.u * 0.040f, false, 1f);
        hud.menuItem(c, art, save.soundOn() ? "SOUND  ON" : "SOUND  OFF",
                base + step * 2f, hud.u * 0.040f, false, 1f);
        hud.menuItem(c, art, "LEAVE TO SURFACE", base + step * 3f, hud.u * 0.040f, false, 1f);
    }

    private void drawCleared(Canvas c) {
        drawPlay(c, 0.5f);
        hud.dim(c, art, 0.74f);

        float a = V.smooth(0f, 0.6f, stateT);
        LevelDef d = Levels.get(levelIndex);
        hud.label(c, art, "DEPTH CLEARED", screenW * 0.5f, screenH * 0.32f,
                hud.u * 0.028f, 0.45f * a, 0.55f);
        hud.title(c, art, d.title.toUpperCase(), screenH * 0.43f, hud.u * 0.060f, 0.92f * a);

        hud.label(c, art, "TIME  " + Hud.timeStr(world.elapsed), screenW * 0.5f,
                screenH * 0.56f, hud.u * 0.032f, 0.6f * a, 0.28f);
        hud.label(c, art, "LOSSES  " + world.deaths, screenW * 0.5f,
                screenH * 0.62f, hud.u * 0.032f, 0.6f * a, 0.28f);

        float best = save.bestTime(levelIndex);
        if (best >= 0f) {
            hud.label(c, art, "BEST  " + Hud.timeStr(best), screenW * 0.5f,
                    screenH * 0.68f, hud.u * 0.026f, 0.35f * a, 0.28f);
        }

        float pulse = 0.5f + 0.5f * (float) Math.sin(stateT * 2.2f);
        hud.label(c, art, "TAP TO DESCEND", screenW * 0.5f, screenH * 0.86f,
                hud.u * 0.030f, (0.3f + pulse * 0.35f) * a, 0.45f);
    }

    private void drawEnding(Canvas c) {
        art.background(c, screenW, screenH, 0.05f, 1.2f);
        art.lightShafts(c, screenW, screenH, stateT * 0.4f, 1.4f);
        art.motes(c, world.cam, stateT, 50, 1.2f);
        art.vignette(c, screenW, screenH, 0.7f);

        float a = V.smooth(0.4f, 1.6f, stateT);
        hud.title(c, art, "SURFACE", screenH * 0.34f, hud.u * 0.11f, 0.95f * a);
        hud.paragraph(c, art,
                "You break the meniscus with someone else's mouth, and the light "
                        + "is unbearable, and you keep it anyway.",
                screenH * 0.53f, hud.u * 0.034f, screenW * 0.66f, 0.6f * a);

        int cleared = save.clearedCount(0, Levels.count());
        hud.label(c, art, cleared + " OF " + Levels.count() + " DEPTHS  ·  "
                        + save.hintsUsed() + " HINTS TAKEN",
                screenW * 0.5f, screenH * 0.74f, hud.u * 0.026f, 0.4f * a, 0.3f);

        float pulse = 0.5f + 0.5f * (float) Math.sin(stateT * 1.8f);
        if (stateT > 3f) {
            hud.label(c, art, "TAP TO RETURN", screenW * 0.5f, screenH * 0.88f,
                    hud.u * 0.028f, (0.25f + pulse * 0.3f), 0.45f);
        }
    }

    // ================= lifecycle =================

    public void onPause() {
        synth.stopAmbient();
        controls.releaseAll();
        if (state == PLAY) go(PAUSE);
    }

    public void onResume() {
        synth.startAmbient();
    }

    public void onDestroy() {
        synth.release();
    }
}
