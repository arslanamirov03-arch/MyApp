package com.bromobile.game;

import android.content.Context;
import android.content.SharedPreferences;

/** Persistent progress and options, backed by SharedPreferences. */
public final class Save {

    private static final String FILE = "broforce_save";

    // --- settings ---
    public int sfxVol = 8;        // 0..10
    public int musicVol = 6;      // 0..10
    public boolean vibrate = true;
    public boolean autoFire = false;
    public boolean leftHanded = false;
    public int padScale = 2;      // 1 small, 2 normal, 3 large
    public boolean showFps = false;
    public boolean blood = true;

    // --- progress ---
    public boolean hasRun;        // a run exists that "CONTINUE" can resume
    public int map;               // 0..4
    public int level;             // 0..4
    public int lives = 3;
    public int score;
    public int unlockedMap;       // furthest map reached
    public int unlockedLevel;     // furthest level within that map
    public int bestScore;
    public int rescued;           // prisoners freed this run
    public int totalKills;

    private final SharedPreferences p;

    public Save(Context c) {
        p = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        load();
    }

    public void load() {
        sfxVol = p.getInt("sfx", 8);
        musicVol = p.getInt("mus", 6);
        vibrate = p.getBoolean("vib", true);
        autoFire = p.getBoolean("auto", false);
        leftHanded = p.getBoolean("lefty", false);
        padScale = p.getInt("padsc", 2);
        showFps = p.getBoolean("fps", false);
        blood = p.getBoolean("blood", true);

        hasRun = p.getBoolean("run", false);
        map = p.getInt("map", 0);
        level = p.getInt("lvl", 0);
        lives = p.getInt("lives", 3);
        score = p.getInt("score", 0);
        unlockedMap = p.getInt("umap", 0);
        unlockedLevel = p.getInt("ulvl", 0);
        bestScore = p.getInt("best", 0);
        rescued = p.getInt("resc", 0);
        totalKills = p.getInt("kills", 0);
    }

    /** Writes settings only — safe to call from a settings screen mid-level. */
    public void saveSettings() {
        p.edit()
                .putInt("sfx", sfxVol)
                .putInt("mus", musicVol)
                .putBoolean("vib", vibrate)
                .putBoolean("auto", autoFire)
                .putBoolean("lefty", leftHanded)
                .putInt("padsc", padScale)
                .putBoolean("fps", showFps)
                .putBoolean("blood", blood)
                .apply();
    }

    /** Writes settings plus the current run. */
    public void saveAll() {
        p.edit()
                .putInt("sfx", sfxVol)
                .putInt("mus", musicVol)
                .putBoolean("vib", vibrate)
                .putBoolean("auto", autoFire)
                .putBoolean("lefty", leftHanded)
                .putInt("padsc", padScale)
                .putBoolean("fps", showFps)
                .putBoolean("blood", blood)
                .putBoolean("run", hasRun)
                .putInt("map", map)
                .putInt("lvl", level)
                .putInt("lives", lives)
                .putInt("score", score)
                .putInt("umap", unlockedMap)
                .putInt("ulvl", unlockedLevel)
                .putInt("best", bestScore)
                .putInt("resc", rescued)
                .putInt("kills", totalKills)
                .apply();
    }

    /** Wipes the run and all unlocks, keeping the player's option choices. */
    public void deleteRun() {
        hasRun = false;
        map = level = 0;
        lives = 3;
        score = 0;
        unlockedMap = unlockedLevel = 0;
        rescued = 0;
        totalKills = 0;
        saveAll();
    }

    public void newRun() {
        hasRun = true;
        map = level = 0;
        lives = 3;
        score = 0;
        rescued = 0;
        saveAll();
    }

    /** Records that a level was cleared and opens the next one. */
    public void clearLevel() {
        int nm = map, nl = level + 1;
        if (nl > 4) { nl = 0; nm++; }
        if (nm > 4) { nm = 4; nl = 4; }   // campaign finished
        if (nm > unlockedMap || (nm == unlockedMap && nl > unlockedLevel)) {
            unlockedMap = nm;
            unlockedLevel = nl;
        }
        map = nm;
        level = nl;
        if (score > bestScore) bestScore = score;
        saveAll();
    }

    public boolean isUnlocked(int m, int l) {
        return m < unlockedMap || (m == unlockedMap && l <= unlockedLevel);
    }

    public float sfx() { return sfxVol / 10f; }

    public float music() { return musicVol / 10f; }
}
