package com.abyss.silt;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity {

    private GameView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SaveData save = new SaveData(this);
        view = new GameView(this, save);
        setContentView(view);

        // Draw under the notch on devices that have one.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Window w = getWindow();
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            w.setAttributes(lp);
        }

        view.post(new Runnable() {
            @Override
            public void run() {
                view.game.synth.init();
                view.game.synth.startAmbient();
            }
        });
    }

    private void goImmersive() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    /** Back steps out through the game's own screens before it leaves the app. */
    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (!view.game.onBack()) super.onBackPressed();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) goImmersive();
    }

    @Override
    protected void onResume() {
        super.onResume();
        goImmersive();
        view.start();
        view.game.onResume();
    }

    @Override
    protected void onPause() {
        view.game.onPause();
        view.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        view.game.onDestroy();
        super.onDestroy();
    }
}
