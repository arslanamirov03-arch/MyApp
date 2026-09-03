package com.bromobile.game;

import java.util.Random;

/**
 * Builds the 25 campaign levels. Each is a long left-to-right run with a
 * guaranteed walkable ground line, jumpable gaps, themed structures and an exit
 * at the far right; the fifth level of every map ends in a boss arena.
 */
public final class LevelGen {

    private static final int H = 32;          // level height in tiles
    private static final int BEDROCK_ROWS = 2;

    private LevelGen() { }

    public static Level generate(World world, Theme theme, int map, int level) {
        Random r = new Random(map * 977L + level * 131L + 7);
        boolean boss = level == 4;

        int runLen = boss ? 150 + level * 6 : 280 + level * 42;
        int arena = boss ? 38 : 0;
        int width = runLen + arena + 14;

        Level l = new Level(width, H, theme);
        l.bossLevel = boss;

        int ground = 22;
        int[] surface = new int[width];
        boolean[] gap = new boolean[width];

        // ---------------- ground line ----------------
        // Steps are capped at two tiles and pits only open on flat ground with a
        // matching landing shelf, so every gap is a jump the player can make.
        int tx = 0;
        for (; tx < 16; tx++) surface[tx] = ground;      // safe starting shelf

        while (tx < runLen) {
            int segment = 7 + r.nextInt(11);
            if (r.nextInt(100) < 46) {
                int step = 1 + r.nextInt(2);
                ground += r.nextBoolean() ? -step : step;
                ground = Math.max(12, Math.min(26, ground));
            }
            for (int i = 0; i < segment && tx < runLen; i++, tx++) surface[tx] = ground;

            if (tx < runLen - 26 && r.nextInt(100) < 38) {
                int gw = 2 + r.nextInt(2);               // 2-3 tiles: always clearable
                for (int i = 0; i < gw && tx < runLen; i++, tx++) {
                    surface[tx] = ground;
                    gap[tx] = true;
                }
                for (int i = 0; i < 4 && tx < runLen; i++, tx++) surface[tx] = ground;
            }
        }
        for (; tx < width; tx++) surface[tx] = ground;   // flat run-out / arena

        // ---------------- fill terrain ----------------
        for (int x = 0; x < width; x++) {
            if (gap[x]) continue;
            for (int y = surface[x]; y < H; y++)
                l.set(x, y, y >= H - BEDROCK_ROWS ? Level.BEDROCK : Level.SOLID);
        }
        // Sealed side walls so the player cannot leave the level.
        for (int y = 0; y < H; y++) {
            l.set(0, y, Level.BEDROCK);
            l.set(width - 1, y, Level.BEDROCK);
        }

        // ---------------- themed structures ----------------
        int structures = boss ? 5 : 12 + level * 3;
        for (int i = 0; i < structures; i++) {
            int sx = 24 + r.nextInt(Math.max(1, runLen - 50));
            structure(l, r, theme, sx, surface[Math.min(width - 1, sx)], map, level);
        }

        // Floating platform chains give the levels vertical routes.
        int chains = boss ? 3 : 10 + level * 2;
        for (int i = 0; i < chains; i++) {
            int sx = 20 + r.nextInt(Math.max(1, runLen - 40));
            int base = surface[Math.min(width - 1, sx)] - 6 - r.nextInt(6);
            int len = 3 + r.nextInt(6);
            int dir = r.nextBoolean() ? 1 : -1;
            for (int k = 0; k < len; k++) {
                int px = sx + k * 2;
                int py = base + (k * dir) / 2;
                if (px < 1 || px >= width - 1 || py < 3 || py >= H - 3) continue;
                if (l.get(px, py) == Level.EMPTY) l.set(px, py, Level.PLATFORM);
            }
        }

        // ---------------- boss arena ----------------
        if (boss) {
            int ax = runLen + 4;
            // Match the approach so the arena entrance is a step, not a wall.
            int floor = Math.max(14, Math.min(26, surface[Math.max(0, runLen - 4)]));
            for (int x = runLen - 2; x < width - 1; x++) {
                // The arena floor is bedrock: a boss fight must not be winnable
                // (or losable) by blowing a pit in the ground you stand on.
                for (int y = floor; y < H; y++) l.set(x, y, Level.BEDROCK);
                for (int y = 0; y < floor; y++) if (l.get(x, y) != Level.EMPTY) l.set(x, y, Level.EMPTY);
                surface[x] = floor;
            }
            // A pair of cover ledges so the arena is not a flat box.
            for (int k = 0; k < 4; k++) {
                l.set(ax + 6 + k, floor - 5, Level.PLATFORM);
                l.set(ax + 30 + k, floor - 5, Level.PLATFORM);
            }
            for (int k = 0; k < 3; k++) l.set(ax + 18 + k, floor - 10, Level.PLATFORM);
            l.bossArenaX = (runLen - 2) * Level.TS;
            world.arenaLeft = (runLen + 1) * Level.TS;
            world.arenaRight = (width - 2) * Level.TS;
            world.bossFloorY = floor * Level.TS;
            // Two thirds along the arena: far enough to be a set-piece entrance,
            // close enough that the camera can frame both fighters.
            world.bossSpawnX = world.arenaLeft + (world.arenaRight - world.arenaLeft) * 0.66f;
        }

        // ---------------- guarantee a walkable route ----------------
        clipCorridor(l, surface, gap, width);

        // Spike patches. They must stand clear of pits — a spike run butted up
        // against a gap adds up to a jump the player physically cannot make.
        if (!boss) {
            int spikes = 3 + level * 2;
            for (int i = 0; i < spikes; i++) {
                int sx = 40 + r.nextInt(Math.max(1, runLen - 70));
                int run = 1 + r.nextInt(2);          // at most two tiles wide
                boolean clear = true;
                for (int k = -4; k < run + 4 && clear; k++) {
                    int cx = sx + k;
                    if (cx < 1 || cx >= width - 2) { clear = false; break; }
                    if (gap[cx]) clear = false;
                    if (k >= 0 && k < run && surface[cx] != surface[sx]) clear = false;
                }
                if (!clear) continue;
                for (int k = 0; k < run; k++) {
                    int cx = sx + k;
                    int sy = surface[cx] - 1;
                    if (sy > 2 && l.get(cx, sy) == Level.EMPTY
                            && Level.blocks(l.get(cx, sy + 1)))
                        l.set(cx, sy, Level.SPIKE);
                }
            }
        }

        // ---------------- spawn & exit ----------------
        l.spawnX = 5 * Level.TS;
        l.spawnY = (surface[5] - 1) * Level.TS;

        int exitTx = boss ? width - 8 : width - 8;
        int exitTy = surface[Math.min(width - 1, exitTx)];
        l.exitX = exitTx * Level.TS;
        l.exitY = exitTy * Level.TS;
        // Clear a doorway so the exit is never buried.
        for (int y = exitTy - 4; y < exitTy; y++)
            for (int x = exitTx - 2; x <= exitTx + 2; x++) l.set(x, y, Level.EMPTY);

        if (!boss) {
            // A landmark plinth under the door.
            for (int x = exitTx - 3; x <= exitTx + 3; x++)
                for (int y = exitTy; y < exitTy + 2; y++) l.set(x, y, Level.SOLID);
        }

        populate(world, l, r, theme, map, level, surface, gap, runLen, width, boss);
        return l;
    }

    /**
     * The ground line is the guaranteed route: it never steps more than two
     * tiles and every pit has a flat landing shelf. This clears the five tiles
     * directly above it in every column so nothing a structure placed can wall
     * the route off. Structure masonry is demoted to background rather than
     * deleted, which reads as an archway cut through the building.
     */
    private static void clipCorridor(Level l, int[] surface, boolean[] gap, int width) {
        for (int x = 1; x < width - 1; x++) {
            if (gap[x]) {
                // A pit must be a clean shaft. Anything a structure dropped into
                // it would clip the jump arc and make the gap uncrossable.
                for (int y = 1; y < l.h; y++) {
                    byte v = l.get(x, y);
                    if (v != Level.EMPTY && v != Level.PLATFORM) l.set(x, y, Level.EMPTY);
                }
                continue;
            }
            int s = surface[x];
            for (int y = Math.max(1, s - 5); y < s; y++) {
                byte v = l.get(x, y);
                if (v == Level.SOLID) l.set(x, y, Level.WALL);
                else if (v == Level.CRATE) l.set(x, y, Level.EMPTY);
            }
        }
    }

    // ------------------------------------------------------------------

    private static void structure(Level l, Random r, Theme theme, int sx, int gy,
                                  int map, int level) {
        int type = r.nextInt(4);
        switch (map) {
            case Theme.CITY: {
                // Tenement block: hollow shell with floors and a ladder.
                int bw = 6 + r.nextInt(6), bh = 6 + r.nextInt(8);
                int top = gy - bh;
                for (int x = sx; x < sx + bw; x++)
                    for (int y = top; y < gy; y++) {
                        if (x >= l.w - 1 || y < 1) continue;
                        boolean edge = x == sx || x == sx + bw - 1 || y == top;
                        // Never hollow out ground that is already there: a
                        // neighbouring column may sit higher than this one.
                        if (edge) l.set(x, y, Level.SOLID);
                        else if (l.get(x, y) == Level.EMPTY) l.set(x, y, Level.WALL);
                    }
                for (int f = 1; f < bh / 3; f++) {
                    int fy = top + f * 3;
                    for (int x = sx + 1; x < sx + bw - 1; x++)
                        if (l.get(x, fy) == Level.WALL) l.set(x, fy, Level.PLATFORM);
                }
                int lx = sx + 1 + r.nextInt(Math.max(1, bw - 2));
                for (int y = top + 1; y < gy; y++)
                    if (l.get(lx, y) == Level.WALL || l.get(lx, y) == Level.PLATFORM)
                        l.set(lx, y, Level.LADDER);
                // doorway
                for (int y = gy - 3; y < gy; y++) l.set(sx, y, Level.EMPTY);
                break;
            }
            case Theme.SKY: {
                // A flight of the great stairway.
                int steps = 4 + r.nextInt(6);
                int dir = r.nextBoolean() ? 1 : -1;
                for (int k = 0; k < steps; k++) {
                    int px = sx + k * 2;
                    int py = gy - 3 - k * 2 * dir;
                    if (px >= l.w - 2 || py < 3 || py > H - 4) break;
                    l.set(px, py, Level.SOLID);
                    l.set(px + 1, py, Level.SOLID);
                }
                if (type == 0) {   // statue plinth
                    for (int y = gy - 4; y < gy; y++) l.set(sx, y, Level.SOLID);
                }
                break;
            }
            case Theme.ICE: {
                // Ice pillar and a shelf hanging off it.
                int pw = 2 + r.nextInt(2), ph = 5 + r.nextInt(8);
                for (int x = sx; x < sx + pw; x++)
                    for (int y = gy - ph; y < gy; y++)
                        if (y >= 1) l.set(x, y, Level.SOLID);
                int shelf = 3 + r.nextInt(4);
                for (int k = 0; k < shelf; k++) {
                    int px = sx + pw + k;
                    if (px < l.w - 1) l.set(px, gy - ph + 1, Level.PLATFORM);
                }
                if (type < 2)
                    for (int k = 0; k < 3; k++)
                        if (gy - ph - 1 - k >= 1) l.set(sx, gy - ph - 1 - k, Level.SOLID);
                break;
            }
            case Theme.RUINS: {
                // Broken columns and a lintel.
                int gapw = 4 + r.nextInt(4);
                int ch = 5 + r.nextInt(6);
                for (int y = gy - ch; y < gy; y++) {
                    l.set(sx, y, Level.SOLID);
                    if (sx + gapw < l.w - 1) l.set(sx + gapw, y, Level.SOLID);
                }
                if (type != 3)
                    for (int x = sx; x <= sx + gapw && x < l.w - 1; x++)
                        l.set(x, gy - ch, Level.SOLID);
                for (int x = sx + 1; x < sx + gapw && x < l.w - 1; x++)
                    if (l.get(x, gy - ch + 1) == Level.EMPTY)
                        l.set(x, gy - ch + 1, Level.WALL);
                break;
            }
            default: {
                // Factory catwalk on legs, with a pipe run above.
                int bw = 6 + r.nextInt(8);
                int cy = gy - 5 - r.nextInt(5);
                for (int x = sx; x < sx + bw && x < l.w - 1; x++)
                    if (cy >= 2 && l.get(x, cy) == Level.EMPTY) l.set(x, cy, Level.PLATFORM);
                for (int y = cy + 1; y < gy; y++) {
                    l.set(sx, y, Level.LADDER);
                    if (sx + bw - 1 < l.w - 1 && type == 0) l.set(sx + bw - 1, y, Level.SOLID);
                }
                if (type == 1 && cy - 4 >= 2)
                    for (int x = sx; x < sx + bw && x < l.w - 1; x++) l.set(x, cy - 4, Level.SOLID);
                break;
            }
        }
    }

    // ------------------------------------------------------------------

    private static void populate(World world, Level l, Random r, Theme theme, int map,
                                 int level, int[] surface, boolean[] gap,
                                 int runLen, int width, boolean boss) {

        // --- enemies ---
        // Density and variety both climb with the level index.
        int count = boss ? 8 + level : (16 + level * 7 + map * 2);
        int variety = Math.min(4, 1 + level);
        int placed = 0, guard = 0;
        while (placed < count && guard++ < count * 40) {
            int tx = 26 + r.nextInt(Math.max(1, runLen - 40));
            if (gap[tx]) continue;
            int slot = pickSlot(r, variety, level);
            Enemy.Def d = Enemy.ROSTER[map][slot];

            int ty = surface[tx];
            if (d.arch == Enemy.FLYER) {
                ty = surface[tx] - 4 - r.nextInt(6);
                if (ty < 4) continue;
            } else if (d.arch == Enemy.TURRET) {
                // Prefer a ledge so turrets shoot down at the player.
                int found = -1;
                for (int y = surface[tx] - 1; y > surface[tx] - 12 && y > 3; y--)
                    if (l.get(tx, y) == Level.EMPTY && Level.blocks(l.get(tx, y + 1))) { found = y + 1; break; }
                ty = found > 0 ? found : surface[tx];
            }
            if (l.boxHits(tx * Level.TS + 2, (ty - 2) * Level.TS, 12, 30)) continue;

            world.enemies.add(new Enemy(map, slot, tx * Level.TS + 8, ty * Level.TS));
            placed++;
        }

        // --- checkpoints ---
        if (!boss) {
            for (int cp = 1; cp * 78 < runLen - 40; cp++) {
                int tx = cp * 78;
                if (gap[tx]) tx += 4;
                if (tx >= width - 10) break;
                Prop f = new Prop(Prop.FLAG, tx * Level.TS + 8, surface[tx] * Level.TS);
                f.theme = map;
                world.props.add(f);
            }
        }

        // --- prisoners ---
        int prisoners = boss ? 2 : 3 + r.nextInt(3);
        for (int i = 0; i < prisoners; i++) {
            for (int tries = 0; tries < 60; tries++) {
                int tx = 34 + r.nextInt(Math.max(1, runLen - 60));
                if (gap[tx]) continue;
                int ty = surface[tx];
                if (l.boxHits(tx * Level.TS + 3, (ty - 1) * Level.TS, 10, 15)) continue;
                Prop pr = new Prop(Prop.PRISONER, tx * Level.TS + 8, ty * Level.TS);
                pr.theme = map;
                world.props.add(pr);
                break;
            }
        }

        // --- pickups ---
        int ammo = 4 + level;
        for (int i = 0; i < ammo; i++) {
            for (int tries = 0; tries < 40; tries++) {
                int tx = 22 + r.nextInt(Math.max(1, width - 40));
                if (gap[tx]) continue;
                int ty = surface[tx];
                if (l.boxHits(tx * Level.TS + 3, (ty - 1) * Level.TS, 11, 10)) continue;
                Prop pk = new Prop(Prop.AMMO, tx * Level.TS + 8, ty * Level.TS);
                pk.theme = map;
                world.props.add(pk);
                break;
            }
        }

        // One or two special weapons, weighted toward the later maps.
        int specials = 1 + (level >= 2 ? 1 : 0) + (map >= 3 ? 1 : 0);
        for (int i = 0; i < specials; i++) {
            int kind = Prop.W_SHOTGUN + r.nextInt(3);
            if (map == 0 && level == 0) kind = Prop.W_SHOTGUN;
            for (int tries = 0; tries < 40; tries++) {
                int tx = 40 + r.nextInt(Math.max(1, runLen - 60));
                if (gap[tx]) continue;
                int ty = surface[tx];
                if (l.boxHits(tx * Level.TS + 3, (ty - 1) * Level.TS, 11, 10)) continue;
                Prop pk = new Prop(kind, tx * Level.TS + 8, ty * Level.TS);
                pk.theme = map;
                world.props.add(pk);
                break;
            }
        }

        if (level >= 1 && r.nextInt(100) < 55) {
            for (int tries = 0; tries < 40; tries++) {
                int tx = 50 + r.nextInt(Math.max(1, runLen - 70));
                if (gap[tx]) continue;
                Prop pk = new Prop(Prop.LIFE, tx * Level.TS + 8, surface[tx] * Level.TS);
                pk.theme = map;
                world.props.add(pk);
                break;
            }
        }

        // --- crates and explosive barrels ---
        int crates = 18 + level * 5;
        for (int i = 0; i < crates; i++) {
            int tx = 18 + r.nextInt(Math.max(1, width - 30));
            if (gap[tx]) continue;
            int ty = surface[tx] - 1;
            if (l.get(tx, ty) != Level.EMPTY || !Level.blocks(l.get(tx, ty + 1))) continue;
            l.set(tx, ty, Level.CRATE);
            if (r.nextInt(100) < 30 && l.get(tx, ty - 1) == Level.EMPTY)
                l.set(tx, ty - 1, Level.CRATE);
        }

        int barrels = 5 + level * 2;
        for (int i = 0; i < barrels; i++) {
            for (int tries = 0; tries < 30; tries++) {
                int tx = 26 + r.nextInt(Math.max(1, width - 40));
                if (gap[tx]) continue;
                int ty = surface[tx];
                if (l.boxHits(tx * Level.TS + 3, (ty - 1) * Level.TS, 10, 15)) continue;
                Prop b = new Prop(Prop.BARREL, tx * Level.TS + 8, ty * Level.TS);
                b.theme = map;
                world.props.add(b);
                break;
            }
        }

        // --- exit ---
        Prop exit = new Prop(Prop.EXIT, l.exitX + 8, l.exitY);
        exit.theme = map;
        world.exitProp = exit;
        if (!boss) world.props.add(exit);
    }

    /** Later levels lean on the heavier slots in each map's roster. */
    private static int pickSlot(Random r, int variety, int level) {
        int roll = r.nextInt(100);
        if (variety <= 1) return 0;
        if (variety == 2) return roll < 68 ? 0 : 1;
        if (variety == 3) return roll < 48 ? 0 : (roll < 80 ? 1 : 2);
        if (level >= 4) return roll < 30 ? 0 : (roll < 55 ? 1 : (roll < 80 ? 2 : 3));
        return roll < 40 ? 0 : (roll < 66 ? 1 : (roll < 87 ? 2 : 3));
    }
}
