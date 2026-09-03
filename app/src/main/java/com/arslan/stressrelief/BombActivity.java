package com.arslan.stressrelief;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

public final class BombActivity extends Activity implements BombRenderer.Listener {

    private BombView view;
    private BombSound sound;
    private TextView hint;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private long lastUpdateNanos;
    private boolean hintHidden;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        view = new BombView(this);
        root.addView(view, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        hint = new TextView(this);
        hint.setText(R.string.bomb_hint);
        hint.setTextColor(0x66FFFFFF);
        hint.setTextSize(13f);
        hint.setLetterSpacing(0.18f);
        hint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        hp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin = (int) (56 * getResources().getDisplayMetrics().density);
        root.addView(hint, hp);

        setContentView(root);

        sound = new BombSound(this);
        sound.create();
        view.getBombRenderer().setListener(this);
        lastUpdateNanos = System.nanoTime();
        immersive();
    }

    private void immersive() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    // ------------------------------------------------ renderer callbacks (GL thread)
    @Override
    public void onCharge(int type) {
        ui.post(() -> sound.charge(type));
    }

    @Override
    public void onNuke() {
        ui.post(() -> sound.nuke());
    }

    @Override
    public void onState(float arm01, boolean arming, float nukeT, boolean busy) {
        long now = System.nanoTime();
        float dt = (now - lastUpdateNanos) / 1e9f;
        lastUpdateNanos = now;
        if (dt > 0.5f) dt = 0.5f;
        final float d = dt;

        ui.post(() -> {
            sound.update(arm01, arming, nukeT, d);
            if (!hintHidden && busy) {
                hintHidden = true;
                hint.animate().alpha(0f).setDuration(700).start();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        view.onResume();
        sound.resume();
        lastUpdateNanos = System.nanoTime();
    }

    @Override
    protected void onPause() {
        super.onPause();
        view.onPause();
        sound.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sound.release();
    }
}
