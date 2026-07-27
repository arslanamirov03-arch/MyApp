package com.abyss.silt;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import com.abyss.silt.core.Art;
import com.abyss.silt.entities.Critter;
import com.abyss.silt.entities.Entity;
import com.abyss.silt.level.Levels;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Renders real frames to build/screenshots so the art direction can be reviewed
 * without a device. Not an assertion test - it exists to make the look of the
 * game inspectable.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class ScreenshotTest {

    private static final int W = 1280, H = 720;
    private static final float STEP = 1f / 60f;

    @Test
    public void captureFrames() throws Exception {
        File dir = new File("build/screenshots");
        dir.mkdirs();

        // A few levels chosen to show off each chapter's palette and mechanics.
        shoot(dir, "01-wake", 0, 180, false);
        shoot(dir, "02-first-mouth", 1, 240, true);
        shoot(dir, "05-the-shell", 4, 240, true);
        shoot(dir, "10-the-maw-boss", 9, 420, true);
        shoot(dir, "11-blind-dark", 10, 260, true);
        shoot(dir, "16-static-circuits", 15, 240, true);
        shoot(dir, "24-the-hive", 23, 300, true);
        shoot(dir, "30-the-abyssal-boss", 29, 500, true);

        shootMenu(dir);
    }

    /** Runs a level for a while, nudges the spirit into a creature, then draws. */
    private void shoot(File dir, String name, int levelIndex, int frames, boolean possess)
            throws Exception {
        World world = new World();
        world.cam.resize(W, H);
        world.load(Levels.get(levelIndex));

        Critter target = null;
        for (Entity e : world.entities) {
            if (e instanceof Critter) {
                target = (Critter) e;
                break;
            }
        }
        if (possess && target != null) {
            // Put the body beside the creature so the frame shows a possession.
            world.diver.x = target.x - 70f;
            world.diver.y = target.y;
            world.update(STEP);
            world.tryPossess(target.x, target.y);
        }

        for (int f = 0; f < frames; f++) {
            world.inputX = (float) Math.cos(f * 0.02f) * 0.7f;
            world.inputY = (float) Math.sin(f * 0.017f) * 0.5f;
            world.update(STEP);
        }

        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF000000);
        world.draw(c, new Art());
        write(bmp, new File(dir, name + ".png"));
    }

    private void shootMenu(File dir) throws Exception {
        SaveData save = new SaveData(
                org.robolectric.RuntimeEnvironment.getApplication());
        Game game = new Game(save);
        game.resize(W, H);
        game.state = Game.MENU;
        for (int f = 0; f < 200; f++) game.update(STEP);

        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(0xFF000000);
        game.draw(c);
        write(bmp, new File(dir, "00-title.png"));
    }

    private void write(Bitmap bmp, File out) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
    }
}
