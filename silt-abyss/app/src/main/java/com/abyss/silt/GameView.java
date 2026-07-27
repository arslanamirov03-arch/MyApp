package com.abyss.silt;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/** SurfaceView plus a dedicated render thread running a fixed-step simulation. */
public class GameView extends SurfaceView implements SurfaceHolder.Callback {

    private static final float STEP = 1f / 120f;    // simulation step
    private static final float MAX_FRAME = 0.25f;   // never simulate a huge catch-up

    private Thread thread;
    private volatile boolean running = false;
    private volatile boolean surfaceReady = false;

    public final Game game;

    public GameView(Context ctx, SaveData save) {
        super(ctx);
        game = new Game(save);
        getHolder().addCallback(this);
        setFocusable(true);
        setKeepScreenOn(true);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surfaceReady = true;
        start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        game.resize(width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        stop();
    }

    public void start() {
        if (running || !surfaceReady) return;
        running = true;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                loop();
            }
        }, "silt-render");
        thread.start();
    }

    public void stop() {
        running = false;
        Thread t = thread;
        thread = null;
        if (t != null) {
            try {
                t.join(1200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void loop() {
        long last = System.nanoTime();
        float accumulator = 0f;

        while (running) {
            long now = System.nanoTime();
            float frame = (now - last) / 1_000_000_000f;
            last = now;
            if (frame > MAX_FRAME) frame = MAX_FRAME;
            accumulator += frame;

            int steps = 0;
            while (accumulator >= STEP && steps < 8) {
                game.update(STEP);
                accumulator -= STEP;
                steps++;
            }
            if (steps == 8) accumulator = 0f;   // we are behind; drop the backlog

            SurfaceHolder holder = getHolder();
            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c != null) {
                    c.drawColor(0xFF000000);
                    game.draw(c);
                }
            } catch (Throwable ignored) {
                // A surface can vanish underneath us during rotation or teardown.
            } finally {
                if (c != null) {
                    try {
                        holder.unlockCanvasAndPost(c);
                    } catch (Throwable ignored) {
                    }
                }
            }

            // Yield briefly so we do not spin a core flat on very fast frames.
            long spent = System.nanoTime() - now;
            long target = 1_000_000_000L / 60L;
            if (spent < target) {
                try {
                    Thread.sleep((target - spent) / 1_000_000L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        game.onTouch(event);
        return true;
    }
}
