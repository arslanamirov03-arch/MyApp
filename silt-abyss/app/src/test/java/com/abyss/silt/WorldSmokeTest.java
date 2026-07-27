package com.abyss.silt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import com.abyss.silt.core.Art;
import com.abyss.silt.entities.Critter;
import com.abyss.silt.entities.Entity;
import com.abyss.silt.level.LevelDef;
import com.abyss.silt.level.Levels;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Random;

/**
 * Runs every level through the real simulation and the real renderer. This is
 * the test that would have caught a crash on the phone: it exercises the update
 * loop, all seven abilities, possession, death and respawn, and draws frames
 * through Skia.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class WorldSmokeTest {

    private static final int W = 1280, H = 720;
    private static final float STEP = 1f / 60f;

    private World newWorld() {
        World w = new World();
        w.cam.resize(W, H);
        return w;
    }

    @Test
    public void everyLevelSimulatesAndRendersWithoutThrowing() {
        Art art = new Art();
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Random rng = new Random(1234);

        for (int i = 0; i < Levels.count(); i++) {
            LevelDef def = Levels.get(i);
            World world = newWorld();
            world.load(def);

            for (int frame = 0; frame < 600; frame++) {
                // Wander, and keep pushing every button along the way.
                if (frame % 37 == 0) {
                    world.inputX = rng.nextFloat() * 2f - 1f;
                    world.inputY = rng.nextFloat() * 2f - 1f;
                }
                if (frame % 23 == 0) world.useAbility();
                if (frame % 61 == 0) world.releaseSpirit();
                if (frame % 17 == 0) {
                    Critter c = world.possessCandidate();
                    if (c != null) world.tryPossess(c.x, c.y);
                }
                world.update(STEP);

                if (frame % 20 == 0) world.draw(canvas, art);

                assertTrue(def.title + ": diver left the grid",
                        world.diver.x >= 0 && world.diver.y >= 0
                                && world.diver.x <= world.cols * World.CS
                                && world.diver.y <= world.rows * World.CS);
            }
        }
    }

    @Test
    public void nothingSpawnsInsideRock() {
        for (int i = 0; i < Levels.count(); i++) {
            World world = newWorld();
            world.load(Levels.get(i));
            String title = Levels.get(i).title;
            for (Entity e : world.entities) {
                int col = (int) Math.floor(e.x / World.CS);
                int row = (int) Math.floor(e.y / World.CS);
                assertFalse(title + ": entity spawned inside rock at " + col + "," + row,
                        world.cellAt(col, row) == '#');
            }
        }
    }

    @Test
    public void bitingALeverOpensItsGates() {
        for (int i = 0; i < Levels.count(); i++) {
            LevelDef def = Levels.get(i);
            World world = newWorld();
            world.load(def);
            if (!hasCell(world, 'l')) continue;

            Critter tool = findCritter(world, LevelDef.BITER);
            if (tool == null) tool = findCritter(world, LevelDef.RAMMER);
            assertTrue(def.title + ": lever with no biter or rammer", tool != null);

            // Every lever in the level, flipped where it stands.
            for (int row = 0; row < world.rows; row++) {
                for (int col = 0; col < world.cols; col++) {
                    if (world.cellAt(col, row) != 'l') continue;
                    tool.x = col * World.CS + World.CS * 0.5f;
                    tool.y = row * World.CS + World.CS * 0.5f;
                    tool.angle = 0f;
                    world.onBite(tool);
                }
            }
            world.update(STEP);
            assertGatesOpen(world, def, 'l');
        }
    }

    @Test
    public void shockingANodeOpensItsGatesUntilItFades() {
        for (int i = 0; i < Levels.count(); i++) {
            LevelDef def = Levels.get(i);
            World world = newWorld();
            world.load(def);
            if (!hasCell(world, 'n')) continue;

            Critter spark = findCritter(world, LevelDef.SPARK);
            assertTrue(def.title + ": node with no spark", spark != null);

            for (int row = 0; row < world.rows; row++) {
                for (int col = 0; col < world.cols; col++) {
                    if (world.cellAt(col, row) != 'n') continue;
                    spark.x = col * World.CS + World.CS * 0.5f;
                    spark.y = row * World.CS + World.CS * 0.5f;
                    world.onDischarge(spark);
                }
            }
            world.update(STEP);
            assertGatesOpen(world, def, 'n');
        }
    }

    @Test
    public void stompingAPlateOpensItsGates() {
        for (int i = 0; i < Levels.count(); i++) {
            LevelDef def = Levels.get(i);
            World world = newWorld();
            world.load(def);
            if (!hasCell(world, 'p')) continue;

            Critter heavy = findCritter(world, LevelDef.HEAVY);
            assertTrue(def.title + ": plate with no heavy", heavy != null);

            for (int row = 0; row < world.rows; row++) {
                for (int col = 0; col < world.cols; col++) {
                    if (world.cellAt(col, row) != 'p') continue;
                    heavy.x = col * World.CS + World.CS * 0.5f;
                    heavy.y = row * World.CS + World.CS * 0.5f;
                    world.onStomp(heavy);
                }
            }
            world.update(STEP);
            assertGatesOpen(world, def, 'p');
        }
    }

    @Test
    public void bitingKelpAndDashingBrittleRockClearsThem() {
        for (int i = 0; i < Levels.count(); i++) {
            LevelDef def = Levels.get(i);
            World world = newWorld();
            world.load(def);

            Critter biter = findCritter(world, LevelDef.BITER);
            if (biter != null && hasCell(world, 'k')) {
                int[] cell = firstCell(world, 'k');
                biter.x = cell[0] * World.CS + World.CS * 0.5f;
                biter.y = cell[1] * World.CS + World.CS * 0.5f;
                biter.angle = 0f;
                world.onBite(biter);
                assertTrue(def.title + ": bite did not clear kelp",
                        world.isBroken(cell[0], cell[1]));
            }
        }
    }

    @Test
    public void reachingTheExitCompletesANonBossLevel() {
        for (int i = 0; i < Levels.count(); i++) {
            LevelDef def = Levels.get(i);
            if (def.boss > 0) continue;
            World world = newWorld();
            world.load(def);

            int[] exit = firstCell(world, 'X');
            world.diver.x = exit[0] * World.CS + World.CS * 0.5f;
            world.diver.y = exit[1] * World.CS + World.CS * 0.5f;
            world.update(STEP);

            assertEquals(def.title + ": swimming onto the exit did not finish the level",
                    World.COMPLETE, world.state);
        }
    }

    @Test
    public void aBossLevelExitStaysShutUntilTheBossIsDead() {
        for (int i = 0; i < Levels.count(); i++) {
            LevelDef def = Levels.get(i);
            if (def.boss == 0) continue;
            World world = newWorld();
            world.load(def);
            assertFalse(def.title + ": boss exit was open from the start", world.exitOpen());

            int[] exit = firstCell(world, 'X');
            world.diver.x = exit[0] * World.CS + World.CS * 0.5f;
            world.diver.y = exit[1] * World.CS + World.CS * 0.5f;
            world.update(STEP);
            assertEquals(def.title + ": boss exit let the player leave early",
                    World.PLAYING, world.state);

            world.boss.hp = 0;
            world.update(STEP);
            assertEquals(def.title + ": exit stayed shut after the boss died",
                    World.COMPLETE, world.state);
        }
    }

    @Test
    public void killingTheDiverCostsALifeAndRespawnsAtTheCheckpoint() {
        World world = newWorld();
        world.load(Levels.get(0));
        float sx = world.respawnX, sy = world.respawnY;

        world.diver.x = sx + 400f;
        world.diver.kill();
        assertEquals(World.DEAD, world.state);
        assertEquals(1, world.deaths);

        for (int f = 0; f < 300 && world.state != World.PLAYING; f++) world.update(STEP);

        assertEquals("never came back from the death screen", World.PLAYING, world.state);
        assertEquals("respawned away from the checkpoint", sx, world.respawnX, 0.01f);
        assertEquals("diver did not return to the checkpoint", sx, world.diver.x, 1f);
        assertEquals("diver did not return to the checkpoint", sy, world.diver.y, 1f);
    }

    @Test
    public void aKilledCreatureComesBackSoTheLevelCannotDeadlock() {
        World world = newWorld();
        world.load(Levels.get(1));   // First Mouth: one biter, one kelp wall

        Critter biter = findCritter(world, LevelDef.BITER);
        assertTrue(biter != null);
        biter.kill();
        assertTrue(biter.dead);

        for (int f = 0; f < 400; f++) world.update(STEP);
        assertFalse("the only biter never came back - the level would be unwinnable",
                biter.dead);
    }

    @Test
    public void possessionIsLimitedByRangeButRecallIsNot() {
        World world = newWorld();
        world.load(Levels.get(1));
        Critter biter = findCritter(world, LevelDef.BITER);
        assertTrue(biter != null);

        // Far away: refused.
        biter.x = world.diver.x + World.POSSESS_RANGE + 200f;
        biter.y = world.diver.y;
        assertFalse("possessed a creature beyond range",
                world.tryPossess(biter.x, biter.y));

        // Close enough: accepted.
        biter.x = world.diver.x + 80f;
        assertTrue("failed to possess a creature in range",
                world.tryPossess(biter.x, biter.y));
        for (int f = 0; f < 120; f++) world.update(STEP);
        assertEquals("spirit never arrived", biter, world.controlled);

        // Recall works from any distance at all.
        biter.x = world.diver.x + 4000f;
        assertTrue(world.releaseSpirit());
        for (int f = 0; f < 200; f++) world.update(STEP);
        assertEquals("spirit failed to come home", world.diver, world.controlled);
    }

    // ---- helpers ----

    private Critter findCritter(World w, int type) {
        for (Entity e : w.entities) {
            if (e instanceof Critter && ((Critter) e).type == type) return (Critter) e;
        }
        return null;
    }

    private boolean hasCell(World w, char c) {
        return firstCell(w, c) != null;
    }

    private int[] firstCell(World w, char c) {
        for (int row = 0; row < w.rows; row++) {
            for (int col = 0; col < w.cols; col++) {
                if (w.cellAt(col, row) == c) return new int[]{col, row};
            }
        }
        return null;
    }

    /**
     * After every switch of one kind has been triggered, each gate whose channel
     * is driven *only* by that kind must be open. Mixed channels are skipped:
     * Chorus deliberately needs a lever and a plate held at the same time, and
     * that gate is correctly still shut when only one of them has fired.
     */
    private void assertGatesOpen(World w, LevelDef def, char kind) {
        int checked = 0;
        for (int row = 0; row < w.rows; row++) {
            for (int col = 0; col < w.cols; col++) {
                if (w.cellAt(col, row) != 'g') continue;
                int ch = def.channelAt(col, row);
                if (!channelIsOnly(w, def, ch, kind)) continue;
                assertFalse(def.title + ": gate on channel " + ch
                                + " still shut after every '" + kind + "' was triggered",
                        w.solidCell(col, row));
                checked++;
            }
        }
        if (channelCountFor(w, def, kind) > 0) {
            assertTrue(def.title + ": no gate is driven purely by '" + kind + "'",
                    checked > 0 || hasMixedChannel(w, def, kind));
        }
    }

    private boolean channelIsOnly(World w, LevelDef def, int channel, char kind) {
        boolean any = false;
        for (int row = 0; row < w.rows; row++) {
            for (int col = 0; col < w.cols; col++) {
                char c = w.cellAt(col, row);
                if (!LevelDef.isSwitch(c) || def.channelAt(col, row) != channel) continue;
                any = true;
                if (c != kind) return false;
            }
        }
        return any;
    }

    private int channelCountFor(World w, LevelDef def, char kind) {
        int n = 0;
        for (int row = 0; row < w.rows; row++) {
            for (int col = 0; col < w.cols; col++) {
                if (w.cellAt(col, row) == kind) n++;
            }
        }
        return n;
    }

    private boolean hasMixedChannel(World w, LevelDef def, char kind) {
        for (int row = 0; row < w.rows; row++) {
            for (int col = 0; col < w.cols; col++) {
                if (w.cellAt(col, row) != kind) continue;
                if (!channelIsOnly(w, def, def.channelAt(col, row), kind)) return true;
            }
        }
        return false;
    }
}
