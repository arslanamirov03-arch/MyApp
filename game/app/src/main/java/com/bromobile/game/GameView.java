package com.bromobile.game;

import android.content.Context;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/** Placeholder replaced during development; warms the Gradle/AGP cache. */
public class GameView extends SurfaceView implements SurfaceHolder.Callback {
    public GameView(Context c) { super(c); getHolder().addCallback(this); }
    public void onResumeGame() { }
    public void onPauseGame() { }
    public boolean handleBack() { return false; }
    @Override public void surfaceCreated(SurfaceHolder h) { }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int ht) { }
    @Override public void surfaceDestroyed(SurfaceHolder h) { }
}
