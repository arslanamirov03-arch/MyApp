import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;

import com.bromobile.game.Art;
import com.bromobile.game.Controls;
import com.bromobile.game.Enemy;
import com.bromobile.game.Hud;
import com.bromobile.game.Level;
import com.bromobile.game.Player;
import com.bromobile.game.Save;
import com.bromobile.game.Sfx;
import com.bromobile.game.Ui;
import com.bromobile.game.World;

import java.io.File;
import java.util.Locale;

import javax.imageio.ImageIO;

/**
 * Off-device test driver. Runs the real game classes against the Java2D shim so
 * levels can be play-tested and screenshotted in batch runs.
 */
public final class Harness {

    static final int VW = 600, VH = 270;
    static boolean dumpBlock;
    static File outDir;

    public static void main(String[] args) throws Exception {
        outDir = new File(args.length > 1 ? args[1] : "shots");
        outDir.mkdirs();
        Art.init();
        Enemy.initSprites();

        String mode = args.length > 0 ? args[0] : "all";
        if (mode.equals("screens") || mode.equals("all")) screens();
        if (mode.equals("traverse") || mode.equals("all")) {
            System.out.println("\n--- TRAVERSAL (godmode, geometry check) ---");
            useGrenades = false;
            int fails = 0;
            for (int m = 0; m < 5; m++)
                for (int lv = 0; lv < 5; lv++)
                    if (!run(m, lv, 240, true, false, false)) fails++;
            System.out.println(fails == 0 ? "ALL 25 LEVELS TRAVERSABLE"
                    : (fails + " LEVEL(S) NOT TRAVERSABLE"));
        }
        if (mode.equals("boss") || mode.equals("all")) {
            System.out.println("\n--- BOSS FIGHTS (godmode) ---");
            for (int m = 0; m < 5; m++) run(m, 4, 260, true, true, m == 0);
        }
        if (mode.equals("reach") || mode.equals("all")) {
            dumpBlock = mode.equals("reach");
            System.out.println("\n--- REACHABILITY (graph search over the tile grid) ---");
            int bad = 0;
            for (int m = 0; m < 5; m++)
                for (int lv = 0; lv < 5; lv++)
                    if (!reach(m, lv)) bad++;
            System.out.println(bad == 0 ? "EVERY LEVEL'S EXIT IS REACHABLE"
                    : (bad + " LEVEL(S) UNREACHABLE"));
        }
        if (mode.equals("exitdump")) {
            int m = Integer.parseInt(args[2]), lv = Integer.parseInt(args[3]);
            Context ctx = new Context();
            Save sv = new Save(ctx);
            World w = new World(new Sfx(ctx, sv), sv, new Controls(), VW, VH);
            w.load(m, lv);
            Level l = w.level;
            int ex = (int) (l.exitX / Level.TS);
            System.out.printf("map %d lv %d width=%d exitTile=%d exitY=%.0f (row %d)%n",
                    m, lv + 1, l.w, ex, l.exitY, (int) (l.exitY / Level.TS));
            for (int y = 12; y < l.h; y++) {
                StringBuilder sb = new StringBuilder(String.format("%2d ", y));
                for (int x = ex - 30; x < Math.min(l.w, ex + 10); x++) sb.append(ch(l, x, y));
                System.out.println(sb);
            }
            return;
        }
        if (mode.equals("probe")) {
            int m = Integer.parseInt(args[2]), lv = Integer.parseInt(args[3]);
            probe(m, lv);
            return;
        }
        if (mode.equals("play") || mode.equals("all")) {
            System.out.println("\n--- NORMAL DIFFICULTY (no godmode) ---");
            useGrenades = true;
            for (int m = 0; m < 5; m++) run(m, 0, 180, false, false, true);
        }
        System.out.println("\nDone. Output in " + outDir.getAbsolutePath());
    }

    // ------------------------------------------------------------------

    static void screens() throws Exception {
        Context ctx = new Context();
        Save save = new Save(ctx);
        save.unlockedMap = 2;
        save.unlockedLevel = 3;
        save.hasRun = true;
        save.map = 1;
        save.level = 2;
        save.bestScore = 48250;
        save.totalKills = 1337;

        Ui ui = new Ui();
        ui.layout(VW, VH);
        ui.update(1.5f);

        Bitmap b = Bitmap.createBitmap(VW, VH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);

        int[] screens = {Ui.MAIN, Ui.SELECT, Ui.SETTINGS, Ui.PAUSE, Ui.CONFIRM, Ui.OUTRO};
        String[] names = {"menu_main", "menu_select", "menu_settings", "menu_pause",
                "menu_confirm", "menu_outro"};
        for (int i = 0; i < screens.length; i++) {
            ui.go(screens[i]);
            ui.draw(c, save, null);
            save2(b, names[i]);
        }
        System.out.println("Rendered " + screens.length + " menu screens.");
    }

    // ------------------------------------------------------------------

    /** @return true when the level was cleared (or, for traversal, the exit reached). */
    static boolean run(int map, int level, int seconds, boolean godmode,
                       boolean startAtBoss, boolean shots) throws Exception {
        Context ctx = new Context();
        Save save = new Save(ctx);
        Sfx sfx = new Sfx(ctx, save);
        Controls in = new Controls();
        in.layout(VW, VH, save);

        World w = new World(sfx, save, in, VW, VH);
        w.load(map, level);
        w.lives = 3;

        if (startAtBoss) {
            w.player.x = w.level.bossArenaX - 40;
            w.player.y = 20 * Level.TS;
            w.camX = w.player.cx() - VW / 2f;
        }

        Bitmap b = Bitmap.createBitmap(VW, VH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Hud hud = new Hud();

        float dt = 1f / 60f;
        int frames = (int) (seconds / dt);
        float bestX = 0, stuckTime = 0, worstStuck = 0, worstStuckX = 0;
        int deaths = 0, prevLives = w.lives;
        int shotIdx = 0;
        float t = 0, nextShot = 1.2f;
        int maxEnemies = 0, maxShots = 0, embedWarn = 0;
        boolean bossKilled = false, bossSeen = false;
        float bossKillTime = -1;
        jumpCooldown = 0;
        lastX = 0;
        stuck = 0;
        backUp = 0;

        float goalX = w.level.exitX;

        for (int f = 0; f < frames; f++) {
            t += dt;
            drive(w, in, dt, godmode);
            w.update(dt);
            in.endFrame();

            maxEnemies = Math.max(maxEnemies, w.enemies.size());
            maxShots = Math.max(maxShots, w.shots.size());

            if (w.boss != null) bossSeen = true;
            if (bossSeen && w.boss == null && !bossKilled) {
                bossKilled = true;
                bossKillTime = t;
            }

            if (w.lives < prevLives) deaths++;
            prevLives = w.lives;

            if (embedWarn < 3 && w.player.alive()
                    && w.level.boxHits(w.player.x, w.player.y, w.player.w, w.player.h)) {
                embedWarn++;
                System.out.printf(Locale.US,
                        "   EMBEDDED IN TERRAIN t=%.1fs at (%.0f,%.0f) tile(%d,%d) onGround=%b vy=%.0f%n",
                        t, w.player.x, w.player.y, (int) (w.player.cx() / Level.TS),
                        (int) (w.player.feet() / Level.TS), w.player.onGround, w.player.vy);
            }

            if (w.player.cx() > bestX + 2) {
                bestX = w.player.cx();
                if (stuckTime > worstStuck) { worstStuck = stuckTime; worstStuckX = bestX; }
                stuckTime = 0;
            } else {
                stuckTime += dt;
            }

            if (shots && t >= nextShot && shotIdx < 6) {
                w.draw(c);
                hud.draw(c, w, VW, VH, 60, false);
                in.draw(c, w.player);
                save2(b, String.format(Locale.US, "%s_map%d_lv%d_%d",
                        startAtBoss ? "boss" : "play", map, level + 1, shotIdx));
                shotIdx++;
                nextShot += Math.max(2.5f, seconds / 9f);
            } else if (f % 15 == 0) {
                w.draw(c);       // keep exercising the renderer for crashes
            }

            if (w.state == World.CLEARED) break;
        }
        if (stuckTime > worstStuck) { worstStuck = stuckTime; worstStuckX = bestX; }

        boolean cleared = w.state == World.CLEARED;
        boolean reachedExit = bestX >= goalX - 40;
        float progress = Math.min(100f, bestX / goalX * 100f);
        String verdict = cleared ? "CLEARED" : (reachedExit ? "AT EXIT" : "SHORT  ");

        System.out.printf(Locale.US,
                "map %d lv %d | %s | %5.1f%% (%.0f/%.0f) | deaths %2d | max stall %4.1fs @%.0f"
                        + " | enemies %2d shots %2d%s%n",
                map, level + 1, verdict, progress, bestX, goalX, deaths, worstStuck, worstStuckX,
                maxEnemies, maxShots,
                bossSeen ? (bossKilled ? String.format(Locale.US, " | BOSS KILLED in %.0fs", bossKillTime)
                        : " | BOSS ALIVE") : "");
        if (!cleared && reachedExit && !w.level.bossLevel) {
            com.bromobile.game.Prop ex = w.exitProp;
            System.out.printf(Locale.US,
                    "   exit debug: prop=%s box=[%.0f,%.0f %.0fx%.0f] player=[%.0f,%.0f %.0fx%.0f]%n",
                    ex == null ? "NULL" : "ok",
                    ex == null ? 0 : ex.x, ex == null ? 0 : ex.y,
                    ex == null ? 0 : ex.w, ex == null ? 0 : ex.h,
                    w.player.x, w.player.y, w.player.w, w.player.h);
        }
        return cleared || reachedExit;
    }

    // ------------------------------------------------------------------
    // Static reachability: proves the exit can be walked/jumped to, using a
    // conservative model of what the player can actually do (step up two
    // tiles, fall any distance, clear a gap of at most four columns).
    // ------------------------------------------------------------------

    /** Crates are ignored: the rifle has infinite ammo, so they never truly block. */
    static boolean solid(Level l, int x, int y) {
        byte v = l.get(x, y);
        return v == Level.SOLID || v == Level.BEDROCK;
    }

    static boolean standable(Level l, int x, int y) {
        byte v = l.get(x, y);
        boolean floor = v == Level.SOLID || v == Level.BEDROCK
                || v == Level.CRATE || v == Level.PLATFORM;
        if (!floor) return false;
        return !solid(l, x, y - 1) && l.get(x, y - 1) != Level.SPIKE;
    }

    /** Highest standable row in a column at or below {@code from}. */
    static int landing(Level l, int x, int from) {
        for (int y = Math.max(1, from); y < l.h; y++)
            if (standable(l, x, y)) return y;
        return -1;
    }

    static boolean reach(int map, int level) {
        Context ctx = new Context();
        Save sv = new Save(ctx);
        World w = new World(new Sfx(ctx, sv), sv, new Controls(), VW, VH);
        w.load(map, level);
        Level l = w.level;

        int sx = (int) (l.spawnX / Level.TS);
        int sy = landing(l, sx, 1);
        int ex = (int) (l.exitX / Level.TS);
        int ey = landing(l, ex, 1);

        boolean[] seen = new boolean[l.w * l.h];
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        if (sy > 0) { q.add(new int[]{sx, sy}); seen[sy * l.w + sx] = true; }
        int furthest = sx;

        while (!q.isEmpty()) {
            int[] n = q.poll();
            int x = n[0], y = n[1];
            if (x > furthest) furthest = x;
            for (int dir = -1; dir <= 1; dir += 2) {
                // Walk one column: step up at most two rows, fall any distance.
                int nx = x + dir;
                if (nx >= 1 && nx < l.w - 1) {
                    int ny = landing(l, nx, y - 2);
                    if (ny > 0 && !seen[ny * l.w + nx]) {
                        seen[ny * l.w + nx] = true;
                        q.add(new int[]{nx, ny});
                    }
                }
                // Jump across up to four columns (pits, spike patches, ledges).
                for (int g = 2; g <= 4; g++) {
                    int jx = x + dir * g;
                    if (jx < 1 || jx >= l.w - 1) break;
                    int jy = landing(l, jx, y - 2);
                    if (jy < 0) continue;
                    int band = Math.min(y, jy);
                    boolean clear = true;
                    for (int k = 1; k < g && clear; k++) {
                        int cx2 = x + dir * k;
                        for (int yy = Math.max(1, band - 2); yy < band; yy++)
                            if (solid(l, cx2, yy)) { clear = false; break; }
                    }
                    if (!clear) continue;
                    if (!seen[jy * l.w + jx]) { seen[jy * l.w + jx] = true; q.add(new int[]{jx, jy}); }
                }
            }
        }

        boolean ok = ey > 0 && seen[ey * l.w + ex];
        // The door has a padded trigger box, so neighbouring columns count too.
        if (!ok && ey > 0)
            for (int d = -1; d <= 1 && !ok; d++) {
                int cx2 = ex + d, cy2 = landing(l, cx2, 1);
                if (cy2 > 0 && seen[cy2 * l.w + cx2]) ok = true;
            }

        System.out.printf(Locale.US, "map %d lv %d | %s | spawn(%d,%d) exit(%d,%d) | furthest column %d/%d%n",
                map, level + 1, ok ? "REACHABLE  " : "UNREACHABLE", sx, sy, ex, ey, furthest, l.w - 1);
        // The frontier is the first column that has ground the search never
        // reached — pits are skipped, since having no floor is not a blocker.
        int frontier = l.w - 2;
        for (int x = sx + 1; x < l.w - 1; x++) {
            boolean any = false, hasStand = false;
            for (int y = 1; y < l.h; y++) {
                if (seen[y * l.w + x]) { any = true; break; }
                if (standable(l, x, y)) hasStand = true;
            }
            if (!any && hasStand) { frontier = x; break; }
        }
        if (!ok && dumpBlock) {
            System.out.println("   search died at column " + furthest
                    + " (first unreached ground column " + frontier + "); '*' = reached");
            int x0 = Math.max(1, furthest - 22), x1 = Math.min(l.w - 1, furthest + 14);
            for (int y = 6; y < l.h; y++) {
                StringBuilder sb = new StringBuilder(String.format("   %2d ", y));
                for (int x = x0; x < x1; x++)
                    sb.append(seen[y * l.w + x] ? '*' : ch(l, x, y));
                System.out.println(sb);
            }
        }
        return ok;
    }

    static char ch(Level l, int x, int y) {
        switch (l.get(x, y)) {
            case Level.EMPTY: return '.';
            case Level.SOLID: return '#';
            case Level.BEDROCK: return 'B';
            case Level.PLATFORM: return '=';
            case Level.CRATE: return 'C';
            case Level.SPIKE: return '^';
            case Level.LADDER: return 'H';
            default: return ',';
        }
    }

    /** Runs one level and dumps the terrain where the bot gives up. */
    static void probe(int map, int level) throws Exception {
        Context ctx = new Context();
        Save save = new Save(ctx);
        Sfx sfx = new Sfx(ctx, save);
        Controls in = new Controls();
        in.layout(VW, VH, save);
        World w = new World(sfx, save, in, VW, VH);
        w.load(map, level);
        Level l = w.level;

        jumpCooldown = lastX = stuck = backUp = safeX = safeY = 0;
        float dt = 1f / 60f, bestX = 0, stall = 0, t = 0;
        float stallX = 0;
        for (int f = 0; f < 60 * 200; f++) {
            t += dt;
            drive(w, in, dt, true);
            w.update(dt);
            in.endFrame();
            if (w.player.cx() > bestX + 2) { bestX = w.player.cx(); stall = 0; }
            else stall += dt;
            if (stall > 8) { stallX = w.player.cx(); break; }
            if (w.state == World.CLEARED) break;
        }
        System.out.printf("map %d lv %d  width=%d tiles  exitX=%.0f (tile %d)  bestX=%.0f%n",
                map, level + 1, l.w, l.exitX, (int) (l.exitX / Level.TS), bestX);
        if (stallX <= 0) { System.out.println("no stall"); return; }

        int cx = (int) (stallX / Level.TS);
        System.out.println("STALLED at tile " + cx + " (t=" + (int) t + "s)");
        int py = (int) (w.player.feet() / Level.TS);
        for (int y = Math.max(0, py - 12); y < Math.min(l.h, py + 4); y++) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%2d ", y));
            for (int x = Math.max(0, cx - 6); x < Math.min(l.w, cx + 22); x++) {
                byte v = l.get(x, y);
                char ch;
                switch (v) {
                    case Level.EMPTY: ch = '.'; break;
                    case Level.SOLID: ch = '#'; break;
                    case Level.BEDROCK: ch = 'B'; break;
                    case Level.PLATFORM: ch = '='; break;
                    case Level.CRATE: ch = 'C'; break;
                    case Level.SPIKE: ch = '^'; break;
                    case Level.LADDER: ch = 'H'; break;
                    default: ch = ','; break;
                }
                if (x == cx && y == py - 1) ch = 'P';
                sb.append(ch);
            }
            System.out.println(sb);
        }
    }

    // --- autopilot ----------------------------------------------------

    static float jumpCooldown, lastX, stuck, backUp, safeX, safeY;
    static boolean useGrenades = true;

    static void drive(World w, Controls in, float dt, boolean godmode) {
        Player p = w.player;
        Level l = w.level;
        in.moveY = 0;
        in.firing = true;
        in.jumpHeld = false;
        in.jumpPressed = false;
        in.crouchHeld = false;
        if (jumpCooldown > 0) jumpCooldown -= dt;
        if (backUp > 0) backUp -= dt;

        if (p == null || !p.alive()) { lastX = 0; stuck = 0; return; }
        if (godmode) {
            p.invuln = 5f;
            // Godmode also suppresses pit deaths, so put the bot back on solid
            // ground itself — otherwise it sails off the map and fakes progress.
            if (p.onGround && !l.boxHitsSpike(p.x, p.y, p.w, p.h)) {
                safeX = p.x;
                safeY = p.y;
            }
            if (p.feet() > l.h * Level.TS - 4 && safeY > 0) {
                p.x = safeX;
                p.y = safeY;
                p.vx = 0;
                p.vy = 0;
                backUp = 0.5f;
            }
        }

        in.moveX = backUp > 0 ? -1f : 1f;

        boolean wallAhead = l.boxHits(p.x + 7, p.y, p.w, p.h);
        boolean gapAhead = !l.groundAhead(p.cx(), p.feet(), 1);
        boolean spikeAhead = l.boxHitsSpike(p.x + 12, p.y, p.w, p.h);

        if (Math.abs(p.cx() - lastX) < 0.5f) stuck += dt; else stuck = 0;
        lastX = p.cx();

        // Back off and take a run-up when a jump alone is not clearing it.
        if (stuck > 2.2f && backUp <= 0) {
            backUp = 0.45f;
            stuck = 0;
        }

        if ((wallAhead || gapAhead || spikeAhead || stuck > 0.3f)
                && p.onGround && jumpCooldown <= 0) {
            in.jumpPressed = true;
            in.jumpHeld = true;
            in.moveY = -1f;
            jumpCooldown = 0.22f;
        } else if (!p.onGround && p.vy < 0) {
            in.jumpHeld = true;
            in.moveY = -1f;
        }
        if (useGrenades) in.grenadePressed = (((int) (w.levelTime * 10)) % 53 == 0);
    }

    static void save2(Bitmap b, String name) throws Exception {
        java.awt.image.BufferedImage src = b.img;
        java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(
                src.getWidth() * 2, src.getHeight() * 2,
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < out.getHeight(); y++)
            for (int x = 0; x < out.getWidth(); x++)
                out.setRGB(x, y, src.getRGB(x / 2, y / 2));
        ImageIO.write(out, "png", new File(outDir, name + ".png"));
    }
}
