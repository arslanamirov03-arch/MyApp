package com.abyss.silt;

import android.content.Context;
import android.content.SharedPreferences;

/** Progress and settings, kept in SharedPreferences. */
public class SaveData {

    private static final String FILE = "silt_abyss_save";

    private final SharedPreferences prefs;

    public SaveData(Context ctx) {
        prefs = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** Index of the furthest level the player has reached. */
    public int unlocked() {
        return prefs.getInt("unlocked", 0);
    }

    public void unlock(int levelIndex) {
        if (levelIndex > unlocked()) {
            prefs.edit().putInt("unlocked", levelIndex).apply();
        }
    }

    public boolean isCleared(int levelIndex) {
        return prefs.getFloat("best_" + levelIndex, -1f) >= 0f;
    }

    public float bestTime(int levelIndex) {
        return prefs.getFloat("best_" + levelIndex, -1f);
    }

    public void recordClear(int levelIndex, float seconds, int deaths) {
        SharedPreferences.Editor e = prefs.edit();
        float prev = bestTime(levelIndex);
        if (prev < 0f || seconds < prev) e.putFloat("best_" + levelIndex, seconds);
        int prevD = prefs.getInt("deaths_" + levelIndex, Integer.MAX_VALUE);
        if (deaths < prevD) e.putInt("deaths_" + levelIndex, deaths);
        e.apply();
    }

    public int clearedCount(int fromLevel, int toLevelExclusive) {
        int n = 0;
        for (int i = fromLevel; i < toLevelExclusive; i++) {
            if (isCleared(i)) n++;
        }
        return n;
    }

    public boolean soundOn() {
        return prefs.getBoolean("sound", true);
    }

    public void setSoundOn(boolean on) {
        prefs.edit().putBoolean("sound", on).apply();
    }

    /** How many hints the player has consumed overall, shown on the ending card. */
    public int hintsUsed() {
        return prefs.getInt("hints", 0);
    }

    public void addHintUsed() {
        prefs.edit().putInt("hints", hintsUsed() + 1).apply();
    }

    public int lastPlayed() {
        return prefs.getInt("last", 0);
    }

    public void setLastPlayed(int levelIndex) {
        prefs.edit().putInt("last", levelIndex).apply();
    }
}
