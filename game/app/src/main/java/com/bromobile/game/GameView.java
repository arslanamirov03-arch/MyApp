package com.bromobile.game;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Owns the render thread, the low-resolution back buffer and the top-level
 * state machine that switches between the menus and a live level.
 */
public final class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private static final int MENU = 0, LOADING = 1, PLAYING = 2, PAUSED = 3, RESULT = 4;

    private Thread thread;
    private volatile boolean running;
    private volatile boolean surfaceReady;

    private Bitmap buffer;
    private Canvas bufCanvas;
    private int vw, vh, scale = 3;
    private final Rect srcRect = new Rect(), dstRect = new Rect();
    private final Paint blit = new Paint();
    private final Paint flat = new Paint();

    private final Save save;
    private final Sfx sfx;
    private final Controls controls = new Controls();
    private final Ui ui = new Ui();
    private final Hud hud = new Hud();
    private World world;

    private int state = MENU;
    private volatile boolean assetsReady;
    private float loadingAnim;
    private float fps, fpsAcc;
    private int fpsCount;
    private float transition;      // 1 -> 0 fade in after a screen change
    private float pendingDelay;
    private Runnable pending;

    public GameView(Context c) {
        super(c);
        getHolder().addCallback(this);
        setFocusable(true);
        save = new Save(c);
        sfx = new Sfx(c, save);
        blit.setFilterBitmap(false);
        blit.setAntiAlias(false);
        blit.setDither(false);
        flat.setAntiAlias(false);
        flat.setFilterBitmap(false);

        // Sprite baking is a few dozen tiny bitmaps: do it here so nothing can
        // ever draw against half-initialised statics. Only the (slower) audio
        // synthesis is pushed onto a worker.
        Art.init();
        Enemy.initSprites();
        new Thread(new Runnable() {
            @Override public void run() {
                sfx.buildAll();
                assetsReady = true;
            }
        }, "asset-build").start();
    }

    // ------------------------------------------------------------------
    // Surface lifecycle
    // ------------------------------------------------------------------

    @Override
    public void surfaceCreated(SurfaceHolder h) {
        surfaceReady = true;
        start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder h, int fmt, int w, int ht) {
        if (w <= 0 || ht <= 0) return;
        int s = Math.max(2, Math.round(ht / 268f));
        int nvw = (int) Math.ceil(w / (float) s);
        int nvh = (int) Math.ceil(ht / (float) s);
        if (nvw == vw && nvh == vh && buffer != null) return;
        scale = s;
        vw = nvw;
        vh = nvh;
        buffer = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888);
        bufCanvas = new Canvas(buffer);
        srcRect.set(0, 0, vw, vh);
        dstRect.set(0, 0, vw * scale, vh * scale);
        controls.layout(vw, vh, save);
        controls.setScale(scale);
        ui.layout(vw, vh);
        if (world != null) {
            world.vw = vw;
            world.vh = vh;
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder h) {
        surfaceReady = false;
        stop();
    }

    private void start() {
        if (running) return;
        running = true;
        thread = new Thread(this, "game-loop");
        thread.start();
    }

    private void stop() {
        running = false;
        if (thread != null) {
            try { thread.join(900); } catch (InterruptedException ignored) { }
            thread = null;
        }
    }

    public void onResumeGame() {
        if (surfaceReady) start();
        sfx.resumeMusic();
    }

    public void onPauseGame() {
        if (state == PLAYING) enterPause();
        sfx.pauseMusic();
        save.saveAll();
        stop();
    }

    public boolean handleBack() {
        switch (state) {
            case PLAYING:
                enterPause();
                return true;
            case PAUSED:
                if (ui.screen == Ui.SETTINGS) { ui.go(Ui.PAUSE); return true; }
                state = PLAYING;
                controls.reset();
                sfx.resumeMusic();
                return true;
            case MENU:
                if (ui.screen != Ui.MAIN) { ui.go(Ui.MAIN); return true; }
                return false;
            case RESULT:
                return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Loop
    // ------------------------------------------------------------------

    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = (now - last) / 1_000_000_000f;
            last = now;
            if (dt > 0.05f) dt = 0.05f;          // clamp after a stall
            if (dt <= 0) dt = 1 / 60f;

            fpsAcc += dt;
            fpsCount++;
            if (fpsAcc >= 0.4f) {
                fps = fpsCount / fpsAcc;
                fpsAcc = 0;
                fpsCount = 0;
            }

            try {
                update(dt);
                render();
            } catch (Throwable t) {
                // Never let a frame take the whole app down.
            }

            long frame = System.nanoTime() - now;
            long sleep = 16_000_000L - frame;
            if (sleep > 1_000_000L) {
                try { Thread.sleep(sleep / 1_000_000L); } catch (InterruptedException ignored) { }
            }
        }
    }

    private void update(float dt) {
        if (transition > 0) transition -= dt * 2.6f;
        if (pending != null) {
            pendingDelay -= dt;
            if (pendingDelay <= 0) {
                Runnable r = pending;
                pending = null;
                r.run();
            }
        }
        ui.update(dt);
        hud.update(dt);

        switch (state) {
            case MENU:
                if (assetsReady) sfx.playMusic(Music.MENU);
                break;
            case LOADING:
                loadingAnim += dt;
                break;
            case PLAYING:
                if (world != null) {
                    world.update(dt);
                    if (world.state == World.CLEARED) onLevelCleared();
                }
                controls.endFrame();
                break;
            case PAUSED:
            case RESULT:
                if (world != null) world.fx.update(dt, world.level);
                break;
        }
    }

    private void render() {
        if (buffer == null || !surfaceReady) return;
        Canvas c = bufCanvas;

        switch (state) {
            case MENU:
                ui.draw(c, save, world);
                break;
            case LOADING:
                drawLoading(c);
                break;
            case PLAYING:
                world.draw(c);
                hud.draw(c, world, vw, vh, fps, save.showFps);
                controls.draw(c, world.player);
                break;
            case PAUSED:
                world.draw(c);
                hud.draw(c, world, vw, vh, fps, save.showFps);
                ui.draw(c, save, world);
                break;
            case RESULT:
                world.draw(c);
                ui.draw(c, save, world);
                break;
        }

        if (transition > 0) {
            flat.setColor(((int) (Math.min(1, transition) * 255) << 24));
            c.drawRect(0, 0, vw, vh, flat);
        }

        SurfaceHolder h = getHolder();
        Canvas out = null;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                out = h.lockHardwareCanvas();
            }
            if (out == null) out = h.lockCanvas();
            if (out == null) return;
            out.drawBitmap(buffer, srcRect, dstRect, blit);
        } catch (Throwable ignored) {
        } finally {
            if (out != null) {
                try { h.unlockCanvasAndPost(out); } catch (Throwable ignored) { }
            }
        }
    }

    private void drawLoading(Canvas c) {
        flat.setColor(0xFF12101A);
        c.drawRect(0, 0, vw, vh, flat);
        Font.outlineCenter(c, "ЗАГРУЗКА", vw / 2, vh / 2 - 20, 0xFFFFD040, 0xFF201008, 2);
        int dots = ((int) (loadingAnim * 3)) % 4;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dots; i++) sb.append('.');
        Font.center(c, sb.toString(), vw / 2, vh / 2 + 4, 0xFFFFD040, 2);
        // Progress worm
        flat.setColor(0xFF2A2A38);
        c.drawRect(vw / 2f - 70, vh / 2f + 26, vw / 2f + 70, vh / 2f + 32, flat);
        float k = (loadingAnim * 0.7f) % 1f;
        flat.setColor(0xFFE85028);
        c.drawRect(vw / 2f - 70 + k * 110, vh / 2f + 26, vw / 2f - 40 + k * 110, vh / 2f + 32, flat);
    }

    // ------------------------------------------------------------------
    // Flow
    // ------------------------------------------------------------------

    private void ensureWorld() {
        if (world == null) world = new World(sfx, save, controls, vw, vh);
        world.vw = vw;
        world.vh = vh;
    }

    private void startLevel(final int map, final int level) {
        state = LOADING;
        loadingAnim = 0;
        transition = 1;
        pending = new Runnable() {
            @Override public void run() {
                ensureWorld();
                world.score = save.score;
                world.load(map, level);
                world.lives = 3;
                world.kills = 0;
                world.rescued = 0;
                save.map = map;
                save.level = level;
                save.hasRun = true;
                save.saveAll();
                controls.reset();
                state = PLAYING;
                transition = 1;
            }
        };
        pendingDelay = 0.35f;
    }

    private void enterPause() {
        state = PAUSED;
        ui.go(Ui.PAUSE);
        controls.reset();
        sfx.pauseMusic();
        save.score = world != null ? world.score : save.score;
        save.saveAll();
    }

    private void onLevelCleared() {
        state = RESULT;
        ui.go(Ui.COMPLETE);
        save.score = world.score;
        save.totalKills += world.kills;
        save.rescued += world.rescued;
        if (world.score > save.bestScore) save.bestScore = world.score;
        save.clearLevel();
        controls.reset();
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (buffer == null) return true;
        float vx = e.getX() / scale, vy = e.getY() / scale;

        if (state == PLAYING) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN
                    || e.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                int i = e.getActionIndex();
                if (controls.gearHit(e.getX(i) / scale, e.getY(i) / scale)) {
                    sfx.play(Sfx.CLICK);
                    enterPause();
                    return true;
                }
            }
            controls.onTouch(e, 0, 0);
            if (controls.pausePressed) {
                controls.endFrame();
                enterPause();
            }
            return true;
        }

        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            String id = ui.tap(vx, vy);
            if (id != null) {
                sfx.play(Sfx.CLICK);
                onUiAction(id);
            }
        }
        return true;
    }

    private void onUiAction(String id) {
        switch (state) {
            case MENU: menuAction(id); break;
            case PAUSED: pauseAction(id); break;
            case RESULT: resultAction(id); break;
        }
    }

    private void menuAction(String id) {
        if (ui.screen == Ui.SETTINGS) { settingsAction(id, Ui.MAIN); return; }

        if (ui.screen == Ui.CONFIRM) {
            if (id.equals("confirmyes")) {
                save.deleteRun();
                ui.go(Ui.MAIN);
            } else if (id.equals("confirmno")) {
                ui.go(Ui.MAIN);
            }
            return;
        }

        if (ui.screen == Ui.SELECT) {
            if (id.equals("back")) { ui.go(Ui.MAIN); return; }
            if (id.startsWith("lv")) {
                int m = id.charAt(2) - '0';
                int l = id.charAt(3) - '0';
                save.hasRun = true;
                save.map = m;
                save.level = l;
                save.saveAll();
                startLevel(m, l);
            }
            return;
        }
        if (ui.screen == Ui.OUTRO) {
            ui.go(Ui.MAIN);
            return;
        }

        switch (id) {
            case "new":
                save.newRun();
                startLevel(0, 0);
                break;
            case "continue":
                if (save.hasRun) startLevel(save.map, save.level);
                break;
            case "select":
                ui.go(Ui.SELECT);
                break;
            case "settings":
                ui.settingsReturn = Ui.MAIN;
                ui.go(Ui.SETTINGS);
                break;
            case "delete":
                ui.go(Ui.CONFIRM);
                break;
            case "quit":
                save.saveAll();
                sfx.release();
                if (getContext() instanceof MainActivity) ((MainActivity) getContext()).quitGame();
                break;
        }
    }

    private void pauseAction(String id) {
        if (ui.screen == Ui.SETTINGS) { settingsAction(id, Ui.PAUSE); return; }
        switch (id) {
            case "resume":
                state = PLAYING;
                controls.reset();
                sfx.resumeMusic();
                transition = 0.6f;
                break;
            case "settings":
                ui.settingsReturn = Ui.PAUSE;
                ui.go(Ui.SETTINGS);
                break;
            case "restart":
                startLevel(save.map, save.level);
                break;
            case "menu":
                save.saveAll();
                world = null;
                state = MENU;
                ui.go(Ui.MAIN);
                sfx.playMusic(Music.MENU);
                transition = 1;
                break;
        }
    }

    private void resultAction(String id) {
        if (id.equals("next")) {
            if (save.map == 4 && save.level == 4) {
                state = MENU;
                ui.go(Ui.OUTRO);
                sfx.playMusic(Music.MENU);
                transition = 1;
            } else {
                startLevel(save.map, save.level);
            }
        } else if (id.equals("menu")) {
            save.saveAll();
            world = null;
            state = MENU;
            ui.go(Ui.MAIN);
            sfx.playMusic(Music.MENU);
            transition = 1;
        }
    }

    private void settingsAction(String id, int back) {
        boolean inc = id.endsWith("+");
        String key = id.substring(0, id.length() - 1);
        switch (id) {
            case "save":
                save.saveAll();
                world_scoreSync();
                ui.go(back);
                sfx.play(Sfx.PICKUP, 0.6f, 1.2f);
                return;
            case "exitset":
                save.load();                      // discard unsaved tweaks
                controls.layout(vw, vh, save);
                ui.go(back);
                return;
        }
        switch (key) {
            case "sfx": save.sfxVol = clamp(save.sfxVol + (inc ? 1 : -1), 0, 10); break;
            case "mus":
                save.musicVol = clamp(save.musicVol + (inc ? 1 : -1), 0, 10);
                sfx.setMusicVolume();
                if (save.musicVol == 0) sfx.stopMusic();
                break;
            case "vib": save.vibrate = inc; break;
            case "auto": save.autoFire = inc; break;
            case "pad":
                save.padScale = clamp(save.padScale + (inc ? 1 : -1), 1, 3);
                controls.layout(vw, vh, save);
                break;
            case "lefty":
                save.leftHanded = inc;
                controls.layout(vw, vh, save);
                break;
            case "blood": save.blood = inc; break;
            case "fps": save.showFps = inc; break;
        }
        sfx.play(Sfx.CLICK, 0.5f, 1.4f);
    }

    private void world_scoreSync() {
        if (world != null) save.score = world.score;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
