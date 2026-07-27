package com.abyss.silt.level;

/**
 * One level, authored as a character grid. Pure Java on purpose: the offline
 * validator in tools/ compiles this package on its own to check every level
 * before the APK is built.
 *
 * TERRAIN LEGEND (map)
 * <pre>
 *   '.' or ' '  open water
 *   '#'         rock, solid forever
 *   'r'         brittle rock, shattered by a RAMMER dash
 *   'k'         kelp, cut by a BITER bite
 *   'g'         gate, solid until every switch on its channel is active
 *   'p'         pressure plate  (switch: only HEAVY is heavy enough; a stomp latches it briefly)
 *   'n'         electric node   (switch: needs a SPARK discharge in range)
 *   'l'         shell lever     (switch: needs a BITER bite or a RAMMER dash)
 *   '^' 'v' '&lt;' '&gt;'  current emitter pushing in that direction
 *   'T'         urchin spines, static hazard
 *   'J'         drifting jelly, moving hazard
 *   'P'         predator spawn
 *   'B'         boss anchor
 *   'S'         diver spawn (exactly one per level)
 *   'X'         exit (exactly one per level)
 *   'C'         checkpoint
 *   'W'         whisper marker, shows the next line of ambient text
 *   '1'..'7'    creature spawn, see CREATURE ids below
 * </pre>
 *
 * WIRE LEGEND (wire, optional, same dimensions as map)
 * <pre>
 *   '0'..'9'    channel id for the gate/plate/node/lever in that cell
 *   anything else  no channel (defaults to 0)
 * </pre>
 */
public class LevelDef {

    // Creature ids, matching map characters '1'..'7'.
    public static final int BITER  = 1;  // cuts kelp, trips shell levers
    public static final int RAMMER = 2;  // dash: shatters brittle rock, stuns
    public static final int GLOWER = 3;  // wide light, flash scares predators
    public static final int SPARK  = 4;  // discharge: powers electric nodes
    public static final int HEAVY  = 5;  // floor walker, stomp latches plates, armoured
    public static final int PUFFER = 6;  // inflates: rises, shoulders predators aside
    public static final int LEECH  = 7;  // latches onto big movers and rides

    public final int index;
    public final int chapter;
    public final String title;
    public final String[] map;
    public final String[] wire;      // may be null
    public final String[] hints;     // progressive, 1-3 entries
    public final String[] whispers;  // ambient lines, consumed by 'W' markers
    public final int boss;           // 0 = none, else boss id 1..3
    public float dark = 0f;          // 0 = lit, 1 = only light sources reveal the world
    public float depth = 0f;         // 0 = shallow haze, 1 = crushing black

    public LevelDef lit(float darkness, float depth01) {
        this.dark = darkness;
        this.depth = depth01;
        return this;
    }

    public LevelDef(int index, int chapter, String title, String[] map, String[] wire,
                    String[] hints, String[] whispers, int boss) {
        this.index = index;
        this.chapter = chapter;
        this.title = title;
        this.map = map;
        this.wire = wire;
        this.hints = hints == null ? new String[0] : hints;
        this.whispers = whispers == null ? new String[0] : whispers;
        this.boss = boss;
    }

    public int rows() {
        return map.length;
    }

    public int cols() {
        int w = 0;
        for (String s : map) w = Math.max(w, s.length());
        return w;
    }

    public char at(int col, int row) {
        if (row < 0 || row >= map.length) return '#';
        String s = map[row];
        if (col < 0 || col >= s.length()) return '#';
        return s.charAt(col);
    }

    /** Channel id for a cell, 0 when no wire layer covers it. */
    public int channelAt(int col, int row) {
        if (wire == null || row < 0 || row >= wire.length) return 0;
        String s = wire[row];
        if (col < 0 || col >= s.length()) return 0;
        char c = s.charAt(col);
        return (c >= '0' && c <= '9') ? c - '0' : 0;
    }

    // ---- classification helpers, shared by the engine and the validator ----

    public static boolean isSolidStatic(char c) {
        return c == '#';
    }

    /** Blocks movement until the matching ability clears it. */
    public static boolean isBreakable(char c) {
        return c == 'r' || c == 'k' || c == 'g';
    }

    public static boolean isSwitch(char c) {
        return c == 'p' || c == 'n' || c == 'l';
    }

    public static boolean isCurrent(char c) {
        return c == '^' || c == 'v' || c == '<' || c == '>';
    }

    public static boolean isCreature(char c) {
        return c >= '1' && c <= '7';
    }

    public static boolean isHazard(char c) {
        return c == 'T' || c == 'J';
    }

    /** Cells a swimmer can never pass through, ignoring abilities. */
    public static boolean blocksSwimming(char c) {
        return isSolidStatic(c) || isBreakable(c);
    }

    /** Ability required to clear a breakable cell, or 0 if none. */
    public static int abilityFor(char c) {
        switch (c) {
            case 'k': return BITER;
            case 'r': return RAMMER;
            default:  return 0;
        }
    }

    /** Ability that can trip a switch cell, or 0. */
    public static int abilityForSwitch(char c) {
        switch (c) {
            case 'p': return HEAVY;   // PUFFER also works, handled by the engine
            case 'n': return SPARK;
            case 'l': return BITER;   // RAMMER also works
            default:  return 0;
        }
    }
}
