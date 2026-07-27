package com.abyss.silt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.abyss.silt.level.LevelDef;
import com.abyss.silt.level.Levels;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 * Guards the generated level table. tools/levels.py proves solvability at
 * authoring time; this re-checks the invariants the engine depends on, so a
 * hand-edit to Levels.java cannot quietly ship a broken level.
 */
public class LevelIntegrityTest {

    @Test
    public void everyLevelHasExactlyOneSpawnAndOneExit() {
        for (int i = 0; i < Levels.count(); i++) {
            LevelDef d = Levels.get(i);
            int spawns = count(d, 'S');
            int exits = count(d, 'X');
            assertEquals(d.title + ": spawn count", 1, spawns);
            assertEquals(d.title + ": exit count", 1, exits);
        }
    }

    @Test
    public void everyRowIsTheSameWidthAndFullyEnclosed() {
        for (int i = 0; i < Levels.count(); i++) {
            LevelDef d = Levels.get(i);
            int w = d.map[0].length();
            for (int row = 0; row < d.map.length; row++) {
                assertEquals(d.title + ": row " + row + " width", w, d.map[row].length());
            }
            // The border must be solid, or entities can escape the grid.
            for (int col = 0; col < w; col++) {
                assertEquals(d.title + ": top border", '#', d.at(col, 0));
                assertEquals(d.title + ": bottom border", '#', d.at(col, d.rows() - 1));
            }
            for (int row = 0; row < d.rows(); row++) {
                assertEquals(d.title + ": left border", '#', d.at(0, row));
                assertEquals(d.title + ": right border", '#', d.at(w - 1, row));
            }
        }
    }

    @Test
    public void wireLayerMatchesMapDimensions() {
        for (int i = 0; i < Levels.count(); i++) {
            LevelDef d = Levels.get(i);
            if (d.wire == null) continue;
            assertEquals(d.title + ": wire rows", d.map.length, d.wire.length);
            for (int row = 0; row < d.wire.length; row++) {
                assertEquals(d.title + ": wire row " + row,
                        d.map[row].length(), d.wire[row].length());
            }
        }
    }

    @Test
    public void everyGateChannelHasASwitchThatSomethingCanWork() {
        for (int i = 0; i < Levels.count(); i++) {
            LevelDef d = Levels.get(i);
            Set<Integer> gateChannels = new HashSet<>();
            Set<Integer> switchChannels = new HashSet<>();
            Set<Integer> creatures = new HashSet<>();

            for (int row = 0; row < d.rows(); row++) {
                for (int col = 0; col < d.cols(); col++) {
                    char c = d.at(col, row);
                    if (c == 'g') gateChannels.add(d.channelAt(col, row));
                    if (LevelDef.isSwitch(c)) switchChannels.add(d.channelAt(col, row));
                    if (LevelDef.isCreature(c)) creatures.add(c - '0');
                }
            }
            for (int ch : gateChannels) {
                assertTrue(d.title + ": gate channel " + ch + " has no switch",
                        switchChannels.contains(ch));
            }
            // A mechanism with no creature able to work it is an unwinnable level.
            for (int row = 0; row < d.rows(); row++) {
                for (int col = 0; col < d.cols(); col++) {
                    char c = d.at(col, row);
                    if (c == 'p') {
                        assertTrue(d.title + ": plate but no HEAVY",
                                creatures.contains(LevelDef.HEAVY));
                    } else if (c == 'n') {
                        assertTrue(d.title + ": node but no SPARK",
                                creatures.contains(LevelDef.SPARK));
                    } else if (c == 'l') {
                        assertTrue(d.title + ": lever but nothing to trip it",
                                creatures.contains(LevelDef.BITER)
                                        || creatures.contains(LevelDef.RAMMER));
                    } else if (c == 'k') {
                        assertTrue(d.title + ": kelp but no BITER",
                                creatures.contains(LevelDef.BITER));
                    } else if (c == 'r') {
                        assertTrue(d.title + ": brittle rock but no RAMMER",
                                creatures.contains(LevelDef.RAMMER));
                    }
                }
            }
        }
    }

    @Test
    public void bossLevelsCarryABossAnchor() {
        for (int i = 0; i < Levels.count(); i++) {
            LevelDef d = Levels.get(i);
            int anchors = count(d, 'B');
            if (d.boss > 0) {
                assertEquals(d.title + ": boss anchors", 1, anchors);
            } else {
                assertEquals(d.title + ": stray boss anchor", 0, anchors);
            }
        }
    }

    @Test
    public void everyLevelHasHintsAndAChapter() {
        for (int i = 0; i < Levels.count(); i++) {
            LevelDef d = Levels.get(i);
            assertTrue(d.title + ": no hints", d.hints.length > 0);
            assertTrue(d.title + ": bad chapter", d.chapter >= 1 && d.chapter <= 6);
            assertFalse(d.title + ": blank title", d.title.trim().isEmpty());
            assertTrue(d.title + ": whisper markers exceed lines",
                    count(d, 'W') <= d.whispers.length);
        }
    }

    @Test
    public void thereAreThirtyLevelsAcrossSixChapters() {
        assertEquals(30, Levels.count());
        for (int ch = 1; ch <= 6; ch++) {
            int n = 0;
            for (int i = 0; i < Levels.count(); i++) {
                if (Levels.get(i).chapter == ch) n++;
            }
            assertEquals("chapter " + ch + " level count", 5, n);
            assertFalse("chapter " + ch + " has no title",
                    Levels.chapterTitle(ch).isEmpty());
        }
    }

    @Test
    public void difficultyRampsDownward() {
        // Depth should never go backwards; the descent is the whole story.
        float prev = -1f;
        for (int i = 0; i < Levels.count(); i++) {
            float d = Levels.get(i).depth;
            assertTrue("level " + i + " depth went backwards", d >= prev - 0.001f);
            prev = d;
        }
    }

    private int count(LevelDef d, char target) {
        int n = 0;
        for (int row = 0; row < d.rows(); row++) {
            for (int col = 0; col < d.cols(); col++) {
                if (d.at(col, row) == target) n++;
            }
        }
        return n;
    }
}
