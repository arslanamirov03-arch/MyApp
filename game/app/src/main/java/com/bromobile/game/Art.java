package com.bromobile.game;

import android.graphics.Bitmap;

/**
 * All character / prop artwork, authored as pixel strings against one global
 * palette. Sprites are baked into {@link Bitmap}s once at startup; per-theme
 * variants are produced by substituting palette characters before baking, so a
 * single silhouette can serve as a city thug, a mummy or a factory bot.
 */
public final class Art {

    // ------------------------------------------------------------------
    // Global palette. Character -> ARGB.
    // ------------------------------------------------------------------
    private static final String PK =
            ".0123456 7sSk rRp oOyY gGh bBcC nNw uUm iI eEF dD xXz aftT vV qjl";
    private static final int[] PC = {
            0x00000000, // .
            0xFF0B0910, // 0 outline black
            0xFF1A1420, // 1 outline dark
            0xFF2E2A38, // 2 shadow grey
            0xFF4E4A58, // 3 mid grey
            0xFF7A7688, // 4 light grey
            0xFFAAA6B4, // 5 pale grey
            0xFFF2F0F4, // 6 white
            0x00000000, // (space)
            0xFFE8DCC0, // 7 bone / cream
            0xFFE8B888, // s skin light
            0xFFC89058, // S skin mid
            0xFF9A6840, // k skin shade
            0x00000000, // (space)
            0xFF8E1F1C, // r red dark
            0xFFC8322A, // R red
            0xFFE8564A, // p red light
            0x00000000, // (space)
            0xFFC06018, // o orange dark
            0xFFF08828, // O orange
            0xFFF8C838, // y yellow
            0xFFFFEE88, // Y yellow bright
            0x00000000, // (space)
            0xFF2C4020, // g green dark
            0xFF4A6B2E, // G green
            0xFF7A9A48, // h green light
            0x00000000, // (space)
            0xFF1E2E50, // b blue dark
            0xFF3A5A96, // B blue
            0xFF58C0E0, // c cyan
            0xFFA8F0FF, // C cyan bright
            0x00000000, // (space)
            0xFF3A2A1C, // n brown dark
            0xFF6A4A2C, // N brown
            0xFF9A7040, // w wood light
            0x00000000, // (space)
            0xFF3A2050, // u purple dark
            0xFF7A40A0, // U purple
            0xFFD060C0, // m magenta
            0x00000000, // (space)
            0xFF6A9AC0, // i ice dark
            0xFFB8E4F8, // I ice light
            0x00000000, // (space)
            0xFF343A44, // e metal dark
            0xFF6A7480, // E metal
            0xFF9AA6B0, // F metal light
            0x00000000, // (space)
            0xFF6A5A38, // d sand dark
            0xFFB09858, // D sand
            0x00000000, // (space)
            0xFF4A4A44, // x stone dark
            0xFF7A7A70, // X stone
            0xFFA8A89C, // z stone light
            0x00000000, // (space)
            0xFF2A6A6A, // a teal
            0xFFFF6020, // f fire
            0xFF8A6038, // t leather
            0xFFB88A58, // T leather light
            0x00000000, // (space)
            0xFF2A3A22, // v uniform dark
            0xFF445A34, // V uniform
            0x00000000, // (space)
            0xFFE878C8, // q rune pink
            0xFF5A9A6A, // j jade
            0xFFA8D858, // l lime
    };

    private static final int[] LUT = new int[128];

    static {
        for (int i = 0; i < LUT.length; i++) LUT[i] = 0;
        for (int i = 0; i < PK.length(); i++) {
            char c = PK.charAt(i);
            if (c != ' ' && c < 128) LUT[c] = PC[i];
        }
        // Cyrillic-free palette, but guard against stray characters.
        LUT['.'] = 0;
    }

    static int color(char c) {
        return c < 128 ? LUT[c] : 0;
    }

    // ------------------------------------------------------------------
    // Baking
    // ------------------------------------------------------------------

    /** Bakes pixel rows into a bitmap; '.' is transparent. */
    public static Bitmap bake(String[] rows) {
        return bake(rows, null, null);
    }

    /**
     * Bakes with a palette substitution: every character found in {@code from}
     * is replaced by the character at the same position in {@code to}.
     */
    public static Bitmap bake(String[] rows, String from, String to) {
        int h = rows.length, w = rows[0].length();
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            String r = rows[y];
            for (int x = 0; x < w; x++) {
                char c = x < r.length() ? r.charAt(x) : '.';
                if (from != null) {
                    int i = from.indexOf(c);
                    if (i >= 0 && i < to.length()) c = to.charAt(i);
                }
                px[y * w + x] = color(c);
            }
        }
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        b.setPixels(px, 0, w, 0, 0, w, h);
        return b;
    }

    /** Horizontally mirrored copy, used for left-facing frames. */
    public static Bitmap flip(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        int[] a = new int[w * h], b = new int[w * h];
        src.getPixels(a, 0, w, 0, 0, w, h);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) b[y * w + x] = a[y * w + (w - 1 - x)];
        Bitmap o = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        o.setPixels(b, 0, w, 0, 0, w, h);
        return o;
    }

    /** Solid silhouette in one colour — used for hit flashes and spawn fades. */
    public static Bitmap silhouette(Bitmap src, int color) {
        int w = src.getWidth(), h = src.getHeight();
        int[] a = new int[w * h];
        src.getPixels(a, 0, w, 0, 0, w, h);
        for (int i = 0; i < a.length; i++) if ((a[i] >>> 24) != 0) a[i] = color;
        Bitmap o = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        o.setPixels(a, 0, w, 0, 0, w, h);
        return o;
    }

    // ==================================================================
    // PLAYER  (12 x 16, facing right)
    // ==================================================================

    public static final String[] BRO_IDLE = {
            "..00000000..",
            ".0RRRRRRRR0.",
            ".0RrrRRRRR0.",
            ".0ssssssss0.",
            ".0sss0ss0s0.",
            ".0ssssssss0.",
            ".0kssssssk0.",
            "..0kkkkkk0..",
            ".0VVVVVVVV0.",
            "0SVVVVVVVVS0",
            "0S0VVVVVV0S0",
            ".0VVVVVVVV0.",
            "..0nnnnnn0..",
            "..0nn00nn0..",
            "..0nn00nn0..",
            "..011..110..",
    };

    public static final String[] BRO_IDLE2 = {
            "............",
            "..00000000..",
            ".0RRRRRRRR0.",
            ".0RrrRRRRR0.",
            ".0ssssssss0.",
            ".0sss0ss0s0.",
            ".0ssssssss0.",
            ".0kssssssk0.",
            "..0kkkkkk0..",
            ".0VVVVVVVV0.",
            "0SVVVVVVVVS0",
            ".0VVVVVVVV0.",
            "..0nnnnnn0..",
            "..0nn00nn0..",
            "..0nn00nn0..",
            "..011..110..",
    };

    public static final String[] BRO_RUN1 = {
            "..00000000..",
            ".0RRRRRRRR0.",
            ".0RrrRRRRR0.",
            ".0ssssssss0.",
            ".0sss0ss0s0.",
            ".0ssssssss0.",
            ".0kssssssk0.",
            "..0kkkkkk0..",
            ".0VVVVVVVV0.",
            "0SVVVVVVVVS0",
            "0S0VVVVVV0S0",
            ".0VVVVVVVV0.",
            "..0nnnnnn0..",
            ".0nn0.0nnn0.",
            "011.....0nn0",
            "..........11",
    };

    public static final String[] BRO_RUN2 = {
            "............",
            "..00000000..",
            ".0RRRRRRRR0.",
            ".0RrrRRRRR0.",
            ".0ssssssss0.",
            ".0sss0ss0s0.",
            ".0ssssssss0.",
            ".0kssssssk0.",
            "..0kkkkkk0..",
            ".0VVVVVVVV0.",
            "0SVVVVVVVVS0",
            ".0VVVVVVVV0.",
            "..0nnnnnn0..",
            "..0nnnnnn0..",
            "..0nn00nn0..",
            "..011..110..",
    };

    public static final String[] BRO_RUN3 = {
            "..00000000..",
            ".0RRRRRRRR0.",
            ".0RrrRRRRR0.",
            ".0ssssssss0.",
            ".0sss0ss0s0.",
            ".0ssssssss0.",
            ".0kssssssk0.",
            "..0kkkkkk0..",
            ".0VVVVVVVV0.",
            "0SVVVVVVVVS0",
            "0S0VVVVVV0S0",
            ".0VVVVVVVV0.",
            "..0nnnnnn0..",
            ".0nnn0.0nn0.",
            "0nn0.....110",
            "11..........",
    };

    public static final String[] BRO_JUMP = {
            "............",
            "..00000000..",
            ".0RRRRRRRR0.",
            ".0RrrRRRRR0.",
            ".0ssssssss0.",
            ".0sss0ss0s0.",
            ".0ssssssss0.",
            ".0kssssssk0.",
            "0S0kkkkkk0S0",
            "0SVVVVVVVVS0",
            ".0VVVVVVVV0.",
            "..0nnnnnn0..",
            ".0nnn00nnn0.",
            "011.0..0.110",
            "............",
            "............",
    };

    public static final String[] BRO_CROUCH = {
            "............",
            "............",
            "............",
            "............",
            "..00000000..",
            ".0RRRRRRRR0.",
            ".0RrrRRRRR0.",
            ".0ssssssss0.",
            ".0sss0ss0s0.",
            ".0kssssssk0.",
            "0SVVVVVVVVS0",
            "0S0VVVVVV0S0",
            ".0nnnnnnnn0.",
            ".0nnnnnnnn0.",
            ".011nn00110.",
            "..0110..011.",
    };

    // ==================================================================
    // WEAPONS  (drawn at the hands; hand anchor is the left edge, row 2)
    // ==================================================================

    public static final String[] W_RIFLE = {
            "..0000000000",
            ".0EEEEEEEEE0",
            "0nnEEeeeee00",
            "0nn000000...",
            ".00.........",
    };

    public static final String[] W_SHOTGUN = {
            "...000000000",
            "..0EEEEEEEE0",
            "0nnnEEEEEE00",
            "0nnn0000000.",
            ".000........",
    };

    public static final String[] W_ROCKET = {
            "..0000000000",
            ".0eEEEEEEEE0",
            "0nEEeeeeeeE0",
            "0nn000000000",
            ".00.........",
    };

    public static final String[] W_FLAMER = {
            "..000000000.",
            ".0eEEEEEEE00",
            "0nnEEeeeeeE0",
            "0nn00000000.",
            ".00.........",
    };

    public static final String[] W_GRENADE_HAND = {
            ".000.",
            "0GgG0",
            "0GGG0",
            "0gGg0",
            ".000.",
    };

    // ==================================================================
    // ENEMY ARCHETYPES
    // Authored in "neutral" colours: e/E/F metal, V/v uniform, s/S/k skin.
    // ==================================================================

    /** Rifle grunt, 12x16. */
    public static final String[] E_SOLDIER1 = {
            "..00000000..",
            ".0eeeeeeee0.",
            ".0eEEEEEEe0.",
            ".0ssssssss0.",
            ".0sss0ss0s0.",
            ".0ssssssss0.",
            ".0kssssssk0.",
            "..0kkkkkk0..",
            ".0VVVVVVVV0.",
            "0SVVVVVVVVS0",
            "0S0VVVVVV0S0",
            ".0VVVVVVVV0.",
            "..0vvvvvv0..",
            "..0vv00vv0..",
            "..0vv00vv0..",
            "..011..110..",
    };

    public static final String[] E_SOLDIER2 = {
            "............",
            "..00000000..",
            ".0eeeeeeee0.",
            ".0eEEEEEEe0.",
            ".0ssssssss0.",
            ".0sss0ss0s0.",
            ".0ssssssss0.",
            ".0kssssssk0.",
            "..0kkkkkk0..",
            ".0VVVVVVVV0.",
            "0SVVVVVVVVS0",
            ".0VVVVVVVV0.",
            "..0vvvvvv0..",
            ".0vv0..0vv0.",
            "011......110",
            "............",
    };

    /** Shielded heavy, 16x18. The shield is the front column block. */
    public static final String[] E_HEAVY1 = {
            "...00000000.....",
            "..0eeeeeeee0....",
            "..0eEEEEEEe0....",
            "..0eeeeeeee0.00.",
            "..0ssssssss00FF0",
            "..0ss0ss0ss00EF0",
            "..0ssssssss00FF0",
            ".0VVVVVVVVVV0EF0",
            "0SVVVVVVVVVV0FF0",
            "0SVVVVVVVVVV0FE0",
            "0S0VVVVVVVV00FF0",
            ".0VVVVVVVVVV0EF0",
            ".0VVVVVVVVVV0FF0",
            "..0vvvvvvvv00FE0",
            "..0vvvvvvvv0.00.",
            "..0vv0000vv0....",
            "..0vv0..0vv0....",
            "..0110..0110....",
    };

    public static final String[] E_HEAVY2 = {
            "................",
            "...00000000.....",
            "..0eeeeeeee0....",
            "..0eEEEEEEe0....",
            "..0eeeeeeee0.00.",
            "..0ssssssss00FF0",
            "..0ss0ss0ss00EF0",
            "..0ssssssss00FF0",
            ".0VVVVVVVVVV0EF0",
            "0SVVVVVVVVVV0FF0",
            "0SVVVVVVVVVV0FE0",
            ".0VVVVVVVVVV0FF0",
            ".0VVVVVVVVVV0EF0",
            "..0vvvvvvvv00FF0",
            "..0vvvvvvvv0.00.",
            "..0vvvvvvvv0....",
            "..0vv0..0vv0....",
            "..0110..0110....",
    };

    /** Hovering attacker, 16x12 (wings included). */
    public static final String[] E_FLYER1 = {
            "..0..........0..",
            ".0F0........0F0.",
            "0FFF00....00FFF0",
            "0FFFFF0000FFFFF0",
            ".0FFF0eeee0FFF0.",
            "..000eEEEEe000..",
            "....0eEccEe0....",
            "....0eEccEe0....",
            "....0eEEEEe0....",
            ".....0eeee0.....",
            "......0000......",
            "................",
    };

    public static final String[] E_FLYER2 = {
            "................",
            "................",
            "....0......0....",
            "...0F0....0F0...",
            "..0FFF0000FFF0..",
            "..00F0eeee0F00..",
            "....0eEEEEe0....",
            "....0eEccEe0....",
            "....0eEEEEe0....",
            ".....0eeee0.....",
            "......0000......",
            "................",
    };

    /** Low scuttler, 14x9. */
    public static final String[] E_CRAWLER1 = {
            "....000000....",
            "..00eeeeee00..",
            ".0eEEEEEEEEe0.",
            "0eEEppEEppEEe0",
            "0eEEEEEEEEEEe0",
            "0eeEEEEEEEEee0",
            ".00eeeeeeee00.",
            "..0.0.00.0.0..",
            "..0.0.00.0.0..",
    };

    public static final String[] E_CRAWLER2 = {
            "..............",
            "....000000....",
            "..00eeeeee00..",
            ".0eEEEEEEEEe0.",
            "0eEEppEEppEEe0",
            "0eEEEEEEEEEEe0",
            ".0eeEEEEEEee0.",
            "..0eeeeeeee0..",
            ".0.0.0..0.0.0.",
    };

    /** Big melee unit, 18x22. */
    public static final String[] E_BRUTE1 = {
            ".....000000.......",
            "....0eeeeee0......",
            "...0eEEEEEEe0.....",
            "...0eEEEEEEe0.....",
            "...0ssssssss0.....",
            "...0ss0ss0ss0.....",
            "...0ssssssss0.....",
            "...0kssssssk0.....",
            "..0kkkkkkkkkk0....",
            ".0VVVVVVVVVVVV0...",
            "0SVVVVVVVVVVVVS0..",
            "0SVVVVVVVVVVVVS0..",
            "0SVVVVVVVVVVVVS0..",
            "0S0VVVVVVVVVV0S0..",
            "0S0VVVVVVVVVV0S0..",
            ".00VVVVVVVVVV00...",
            "..0vvvvvvvvvv0....",
            "..0vvv0000vvv0....",
            "..0vvv0..0vvv0....",
            "..0vvv0..0vvv0....",
            "..0110....0110....",
            "..000......000....",
    };

    public static final String[] E_BRUTE2 = {
            "..................",
            ".....000000.......",
            "....0eeeeee0......",
            "...0eEEEEEEe0.....",
            "...0eEEEEEEe0.....",
            "...0ssssssss0.....",
            "...0ss0ss0ss0.....",
            "...0ssssssss0.....",
            "...0kssssssk0.....",
            "..0kkkkkkkkkk0....",
            ".0VVVVVVVVVVVV0...",
            "0SVVVVVVVVVVVVS0..",
            "0SVVVVVVVVVVVVS0..",
            "0SVVVVVVVVVVVVS0..",
            ".00VVVVVVVVVV00...",
            "..0vvvvvvvvvv0....",
            "..0vvvvvvvvvv0....",
            "..0vvv0000vvv0....",
            "..0vvv0..0vvv0....",
            "..0110....0110....",
            "..000......000....",
            "..................",
    };

    /** Fixed emplacement, 14x14 (barrel points right). */
    public static final String[] E_TURRET = {
            "..............",
            "....000000....",
            "...0eeeeee0...",
            "..0eEEEEEEe0..",
            "..0EEppEEEe0..",
            "..0EEppEEEe000",
            "..0eEEEEEe0FF0",
            "..0eEEEEEe0FF0",
            "...0eeeeee0000",
            "..0EEEEEEEE0..",
            ".0EEEEEEEEEE0.",
            "0eeeeeeeeeeee0",
            "0eeeeeeeeeeee0",
            "0000000000000.",
    };

    /** Suicide bomber, 12x16 — round body with a lit charge. */
    public static final String[] E_BOMBER1 = {
            "..00000000..",
            ".0eeeeeeee0.",
            ".0ssssssss0.",
            ".0sss0ss0s0.",
            ".0ssssssss0.",
            "..0kkkkkk0..",
            ".0VVVVVVVV0.",
            "0VVVVVVVVVV0",
            "0VVRRRRRRVV0",
            "0VVRppppRVV0",
            "0VVRRRRRRVV0",
            "0VVVVVVVVVV0",
            ".0VVVVVVVV0.",
            "..0vv00vv0..",
            "..0vv00vv0..",
            "..011..110..",
    };

    public static final String[] E_BOMBER2 = {
            "............",
            "..00000000..",
            ".0eeeeeeee0.",
            ".0ssssssss0.",
            ".0sss0ss0s0.",
            ".0ssssssss0.",
            "..0kkkkkk0..",
            "0VVVVVVVVVV0",
            "0VVyyyyyyVV0",
            "0VVyYYYYyVV0",
            "0VVyyyyyyVV0",
            "0VVVVVVVVVV0",
            ".0VVVVVVVV0.",
            ".0vv0..0vv0.",
            "011......110",
            "............",
    };

    // ==================================================================
    // PROPS AND PICKUPS
    // ==================================================================

    public static final String[] CRATE = {
            "0000000000000000",
            "0wwwwwwwwwwwwww0",
            "0wNNwwwwwwwwNNw0",
            "0wNwNwwwwwwNwNw0",
            "0wwwwNwwwwNwwww0",
            "0wwwwwNwwNwwwww0",
            "0wwwwwwNNwwwwww0",
            "0NNNNNNNNNNNNNN0",
            "0NNNNNNNNNNNNNN0",
            "0wwwwwwNNwwwwww0",
            "0wwwwwNwwNwwwww0",
            "0wwwwNwwwwNwwww0",
            "0wNwNwwwwwwNwNw0",
            "0wNNwwwwwwwwNNw0",
            "0wwwwwwwwwwwwww0",
            "0000000000000000",
    };

    public static final String[] BARREL = {
            ".0000000000.",
            "0RRRRRRRRRR0",
            "0RrrrrrrrrR0",
            "0RrRRRRRRrR0",
            "0RrRyyyyRrR0",
            "0RrRy00yRrR0",
            "0RrRy00yRrR0",
            "0RrRyyyyRrR0",
            "0RrRRRRRRrR0",
            "0RrrrrrrrrR0",
            "0RRRRRRRRRR0",
            "0rrrrrrrrrr0",
            "0RRRRRRRRRR0",
            "0rrrrrrrrrr0",
            "0RRRRRRRRRR0",
            ".0000000000.",
    };

    public static final String[] P_AMMO = {
            "000000000000",
            "0GGGGGGGGGG0",
            "0GggggggggG0",
            "0Gg000000gG0",
            "0Gg0yyyy0gG0",
            "0Gg0y00y0gG0",
            "0Gg0yyyy0gG0",
            "0Gg000000gG0",
            "0GggggggggG0",
            "0GGGGGGGGGG0",
            "000000000000",
            "............",
    };

    public static final String[] P_LIFE = {
            "..00..00..",
            ".0RR00RR0.",
            "0RppRRppR0",
            "0RpRRRRpR0",
            "0RRRRRRRR0",
            ".0RRRRRR0.",
            "..0RRRR0..",
            "...0RR0...",
            "....00....",
            "..........",
    };

    /** Tied-up bro waiting to be freed. */
    public static final String[] PRISONER1 = {
            "..00000000..",
            ".0nnnnnnnn0.",
            ".0ssssssss0.",
            ".0s00ss00s0.",
            ".0ssssssss0.",
            "..0kkkkkk0..",
            ".0666666660.",
            "0n66666666n0",
            "0nnnnnnnnnn0",
            "0n66666666n0",
            ".0666666660.",
            "..06666660..",
            "..0nn00nn0..",
            "..0nn00nn0..",
            "..011..110..",
            "............",
    };

    public static final String[] PRISONER2 = {
            "............",
            "..00000000..",
            ".0nnnnnnnn0.",
            ".0ssssssss0.",
            ".0s00ss00s0.",
            ".0ssssssss0.",
            "..0kkkkkk0..",
            ".0666666660.",
            "0n66666666n0",
            "0nnnnnnnnnn0",
            "0n66666666n0",
            ".0666666660.",
            "..06666660..",
            "..0nn00nn0..",
            "..0nn00nn0..",
            "..011..110..",
    };

    /** Checkpoint flag, 12x20. */
    public static final String[] FLAG = {
            "00..........",
            "05RRRRRRR0..",
            "05RRRRRRRR0.",
            "05RRRRRRR0..",
            "05RRRRR0....",
            "05RRRRRRR0..",
            "05RRRRRRRR0.",
            "05RRRRRRR0..",
            "05000000....",
            "05..........",
            "05..........",
            "05..........",
            "05..........",
            "05..........",
            "05..........",
            "05..........",
            "05..........",
            "05..........",
            "055550......",
            "000000......",
    };

    // ==================================================================
    // Cached bitmaps
    // ==================================================================

    public static Bitmap[] broRun = new Bitmap[4];
    public static Bitmap[] broRunL = new Bitmap[4];
    public static Bitmap broIdle, broIdleL, broIdle2, broIdle2L;
    public static Bitmap broJump, broJumpL, broCrouch, broCrouchL;

    public static Bitmap wRifle, wRifleL, wShotgun, wShotgunL;
    public static Bitmap wRocket, wRocketL, wFlamer, wFlamerL, wGrenade, wGrenadeL;

    public static Bitmap crate, barrel, pAmmo, pLife, flag;
    public static Bitmap[] prisoner = new Bitmap[2];

    private static boolean built;

    public static synchronized void init() {
        if (built) return;
        built = true;

        broIdle = bake(BRO_IDLE);
        broIdle2 = bake(BRO_IDLE2);
        broJump = bake(BRO_JUMP);
        broCrouch = bake(BRO_CROUCH);
        broRun[0] = bake(BRO_RUN1);
        broRun[1] = bake(BRO_RUN2);
        broRun[2] = bake(BRO_RUN3);
        broRun[3] = bake(BRO_RUN2);
        broIdleL = flip(broIdle);
        broIdle2L = flip(broIdle2);
        broJumpL = flip(broJump);
        broCrouchL = flip(broCrouch);
        for (int i = 0; i < 4; i++) broRunL[i] = flip(broRun[i]);

        wRifle = bake(W_RIFLE);
        wShotgun = bake(W_SHOTGUN);
        wRocket = bake(W_ROCKET);
        wFlamer = bake(W_FLAMER);
        wGrenade = bake(W_GRENADE_HAND);
        wRifleL = flip(wRifle);
        wShotgunL = flip(wShotgun);
        wRocketL = flip(wRocket);
        wFlamerL = flip(wFlamer);
        wGrenadeL = flip(wGrenade);

        crate = bake(CRATE);
        barrel = bake(BARREL);
        pAmmo = bake(P_AMMO);
        pLife = bake(P_LIFE);
        flag = bake(FLAG);
        prisoner[0] = bake(PRISONER1);
        prisoner[1] = bake(PRISONER2);
    }

    private Art() { }
}
