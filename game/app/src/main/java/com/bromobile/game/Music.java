package com.bromobile.game;

import java.util.Random;

/**
 * Chiptune generator. Each theme is described by a tempo, a scale and a chord
 * progression; the renderer lays down bass, lead, pad and drum voices over a
 * 16th-note grid and returns one seamless loop as raw PCM.
 */
public final class Music {

    public static final int SR = 22050;

    public static final int CITY = 0, SKY = 1, ICE = 2, RUINS = 3, FACTORY = 4,
            MENU = 5, BOSS = 6, VICTORY = 7;

    // Scales as semitone offsets.
    private static final int[] MINOR = {0, 2, 3, 5, 7, 8, 10};
    private static final int[] MAJOR = {0, 2, 4, 5, 7, 9, 11};
    private static final int[] HARM = {0, 2, 3, 5, 7, 8, 11};
    private static final int[] PHRY = {0, 1, 3, 5, 7, 8, 10};
    private static final int[] LYD = {0, 2, 4, 6, 7, 9, 11};

    private static final class Track {
        int bpm, root, bars;
        int[] scale;
        int[][] chords;      // semitone offsets from root, per bar-pair
        float leadDuty, leadVol, bassVol, padVol, drumVol;
        int bassPattern, leadPattern, drumPattern;
        boolean pad;
    }

    private static Track def(int id) {
        Track t = new Track();
        t.bars = 8;
        t.leadDuty = 0.5f;
        t.leadVol = 0.20f;
        t.bassVol = 0.30f;
        t.padVol = 0.09f;
        t.drumVol = 0.28f;
        t.pad = true;
        switch (id) {
            case CITY:
                t.bpm = 134; t.root = 45; t.scale = MINOR;                 // A minor, gritty
                t.chords = new int[][]{{0, 3, 7}, {0, 3, 7}, {8, 12, 15}, {5, 8, 12},
                        {0, 3, 7}, {0, 3, 7}, {10, 14, 17}, {7, 10, 14}};
                t.bassPattern = 0; t.leadPattern = 0; t.drumPattern = 0;
                t.leadDuty = 0.25f;
                break;
            case SKY:
                t.bpm = 120; t.root = 50; t.scale = LYD;                   // D lydian, airy
                t.chords = new int[][]{{0, 4, 7}, {2, 6, 9}, {5, 9, 12}, {7, 11, 14},
                        {0, 4, 7}, {9, 12, 16}, {5, 9, 12}, {7, 11, 14}};
                t.bassPattern = 1; t.leadPattern = 1; t.drumPattern = 1;
                t.leadVol = 0.19f; t.drumVol = 0.20f; t.padVol = 0.13f;
                break;
            case ICE:
                t.bpm = 104; t.root = 47; t.scale = MINOR;                 // B minor, sparse
                t.chords = new int[][]{{0, 3, 7}, {0, 3, 7}, {5, 8, 12}, {5, 8, 12},
                        {8, 12, 15}, {8, 12, 15}, {3, 7, 10}, {7, 10, 14}};
                t.bassPattern = 2; t.leadPattern = 2; t.drumPattern = 2;
                t.leadVol = 0.17f; t.drumVol = 0.18f; t.leadDuty = 0.5f;
                break;
            case RUINS:
                t.bpm = 96; t.root = 43; t.scale = PHRY;                   // G phrygian, exotic
                t.chords = new int[][]{{0, 3, 7}, {1, 5, 8}, {0, 3, 7}, {8, 12, 15},
                        {0, 3, 7}, {1, 5, 8}, {5, 8, 12}, {0, 3, 7}};
                t.bassPattern = 2; t.leadPattern = 3; t.drumPattern = 3;
                t.leadDuty = 0.35f; t.drumVol = 0.24f;
                break;
            case FACTORY:
                t.bpm = 146; t.root = 40; t.scale = MINOR;                 // E minor, industrial
                t.chords = new int[][]{{0, 3, 7}, {0, 3, 7}, {0, 3, 7}, {10, 14, 17},
                        {0, 3, 7}, {0, 3, 7}, {5, 8, 12}, {3, 7, 10}};
                t.bassPattern = 3; t.leadPattern = 0; t.drumPattern = 4;
                t.leadDuty = 0.125f; t.drumVol = 0.34f; t.bassVol = 0.34f;
                break;
            case BOSS:
                t.bpm = 158; t.root = 41; t.scale = HARM;                  // F harmonic minor
                t.chords = new int[][]{{0, 3, 7}, {0, 3, 7}, {8, 11, 15}, {7, 11, 14},
                        {0, 3, 7}, {5, 8, 12}, {8, 11, 15}, {7, 11, 14}};
                t.bassPattern = 3; t.leadPattern = 4; t.drumPattern = 4;
                t.leadDuty = 0.25f; t.leadVol = 0.23f; t.drumVol = 0.34f;
                break;
            case VICTORY:
                t.bpm = 128; t.root = 48; t.scale = MAJOR;
                t.bars = 4;
                t.chords = new int[][]{{0, 4, 7}, {5, 9, 12}, {7, 11, 14}, {0, 4, 7},
                        {0, 4, 7}, {5, 9, 12}, {7, 11, 14}, {0, 4, 7}};
                t.bassPattern = 1; t.leadPattern = 1; t.drumPattern = 1;
                break;
            case MENU:
            default:
                t.bpm = 126; t.root = 45; t.scale = MINOR;
                t.chords = new int[][]{{0, 3, 7}, {8, 12, 15}, {5, 8, 12}, {7, 10, 14},
                        {0, 3, 7}, {8, 12, 15}, {3, 7, 10}, {7, 10, 14}};
                t.bassPattern = 0; t.leadPattern = 1; t.drumPattern = 1;
                t.leadDuty = 0.3f;
                break;
        }
        return t;
    }

    /** Renders one full loop of the given theme. */
    public static short[] render(int id) {
        Track t = def(id);
        double spb = 60.0 / t.bpm;                 // seconds per beat
        double step = spb / 4.0;                   // 16th note
        int steps = t.bars * 16;
        int total = (int) (steps * step * SR) + 1;
        float[] mix = new float[total];
        Random rnd = new Random(9001 + id * 7717L);

        for (int s = 0; s < steps; s++) {
            int bar = s / 16;
            int[] chord = t.chords[bar % t.chords.length];
            int at = (int) (s * step * SR);
            int sub = s % 16;

            // ---- bass ----
            int bn = bassNote(t.bassPattern, sub, chord);
            if (bn >= 0) {
                double dur = step * (t.bassPattern == 3 ? 1.0 : 1.6);
                voice(mix, at, dur, midi(t.root - 12 + bn), 1, 0.5f, t.bassVol, 0.0015f, 2.2f);
            }

            // ---- lead ----
            int ln = leadNote(t.leadPattern, s, sub, bar, chord, t.scale, rnd);
            if (ln >= 0) {
                double dur = step * 1.7;
                voice(mix, at, dur, midi(t.root + 12 + ln), 0, t.leadDuty, t.leadVol, 0.002f, 2.0f);
                voice(mix, at, dur, midi(t.root + 24 + ln), 2, 0.5f, t.leadVol * 0.35f, 0.002f, 2.0f);
            }

            // ---- pad (once per bar) ----
            if (t.pad && sub == 0) {
                for (int c = 0; c < chord.length; c++) {
                    voice(mix, at, spb * 3.7, midi(t.root + chord[c]), 2, 0.5f,
                            t.padVol, 0.06f, 0.9f);
                }
            }

            // ---- drums ----
            drums(mix, at, t.drumPattern, sub, bar, t.drumVol, rnd);
        }

        // Soft-clip, then apply a short cross-fade so the loop point is seamless.
        short[] out = new short[total];
        int fade = Math.min(700, total / 8);
        for (int i = 0; i < total; i++) {
            float v = mix[i];
            v = (float) Math.tanh(v * 1.25);
            if (i < fade) {
                float k = i / (float) fade;
                v = v * k + (float) Math.tanh(mix[total - fade + i] * 1.25) * (1 - k);
            }
            out[i] = (short) Math.max(-32000, Math.min(32000, v * 21000));
        }
        return out;
    }

    private static int bassNote(int pattern, int sub, int[] chord) {
        switch (pattern) {
            case 0:  // rock eighths with a fifth on the off-beat
                if (sub % 4 == 0) return chord[0];
                if (sub % 8 == 6) return chord[2] - 12;
                return -1;
            case 1:  // relaxed half notes
                if (sub == 0) return chord[0];
                if (sub == 8) return chord[2] - 12;
                return -1;
            case 2:  // sparse whole notes
                return sub == 0 ? chord[0] : -1;
            case 3:  // driving 16ths (industrial / boss)
                if (sub % 2 == 0) return chord[0];
                if (sub == 7 || sub == 15) return chord[1];
                return -1;
            default:
                return sub == 0 ? chord[0] : -1;
        }
    }

    private static int leadNote(int pattern, int s, int sub, int bar, int[] chord,
                                int[] scale, Random rnd) {
        switch (pattern) {
            case 0:  // punchy motif on the off-beats
                if (sub == 0) return chord[0];
                if (sub == 3) return chord[1];
                if (sub == 6) return chord[2];
                if (sub == 10) return chord[1];
                if (sub == 12 && bar % 2 == 1) return chord[2] + 5;
                return -1;
            case 1:  // flowing eighth-note arpeggio
                if (sub % 2 != 0) return -1;
                return chord[(sub / 2) % 3] + ((sub / 2) >= 4 ? 12 : 0);
            case 2:  // sparse bell phrases
                if (sub == 0) return chord[2];
                if (sub == 6) return chord[1] + 12;
                if (sub == 11 && bar % 2 == 0) return chord[0] + 12;
                return -1;
            case 3:  // exotic winding line
                if (sub % 2 != 0) return -1;
                int[] shape = {0, 1, 2, 1, 3, 2, 1, 0};
                int d = shape[(sub / 2) % 8] + (bar % 2 == 1 ? 2 : 0);
                return scale[d % scale.length] + (d >= scale.length ? 12 : 0);
            case 4:  // fast 16th arpeggio for boss fights
                int idx = (s + bar) % 6;
                int[] up = {0, 1, 2, 1, 2, 0};
                return chord[up[idx]] + (idx >= 3 ? 12 : 0);
            default:
                return sub == 0 ? chord[0] : -1;
        }
    }

    private static void drums(float[] mix, int at, int pattern, int sub, int bar,
                              float vol, Random rnd) {
        boolean kick, snare, hat;
        switch (pattern) {
            case 0: kick = sub == 0 || sub == 6 || sub == 8 || sub == 14;
                    snare = sub == 4 || sub == 12;
                    hat = sub % 2 == 0; break;
            case 1: kick = sub == 0 || sub == 8;
                    snare = sub == 4 || sub == 12;
                    hat = sub % 2 == 0; break;
            case 2: kick = sub == 0;
                    snare = sub == 8;
                    hat = sub % 4 == 0; break;
            case 3: kick = sub == 0 || sub == 7;
                    snare = sub == 4 || sub == 12;
                    hat = sub % 2 == 1; break;
            default: kick = sub % 4 == 0 || sub == 6 || sub == 14;
                    snare = sub == 4 || sub == 12;
                    hat = true; break;
        }
        if (kick) noiseHit(mix, at, 0.13, vol * 1.15f, 0, rnd);
        if (snare) noiseHit(mix, at, 0.15, vol * 0.85f, 1, rnd);
        if (hat) noiseHit(mix, at, 0.045, vol * 0.30f, 2, rnd);
        if (sub == 14 && bar % 4 == 3) noiseHit(mix, at, 0.2, vol * 0.7f, 1, rnd);
    }

    private static void noiseHit(float[] mix, int at, double dur, float vol, int kind, Random rnd) {
        int n = (int) (dur * SR);
        float lp = 0, hp = 0, prev = 0;
        for (int i = 0; i < n; i++) {
            int p = at + i;
            if (p >= mix.length) break;
            float t = i / (float) SR;
            float e = (float) Math.pow(1 - i / (double) n, kind == 0 ? 2.4 : (kind == 1 ? 2.0 : 3.6));
            float v;
            if (kind == 0) {                       // kick: pitched sine sweep
                float f = 130f - 95f * (i / (float) n);
                v = (float) Math.sin(2 * Math.PI * f * t) * 1.1f;
            } else {
                float w = rnd.nextFloat() * 2 - 1;
                if (kind == 1) {                   // snare: band-passed noise + body
                    lp += (w - lp) * 0.45f;
                    v = lp * 0.9f + (float) Math.sin(2 * Math.PI * 190 * t) * 0.35f;
                } else {                           // hat: high-passed noise
                    hp = 0.9f * (hp + w - prev);
                    prev = w;
                    v = hp * 0.7f;
                }
            }
            mix[p] += v * e * vol;
        }
    }

    /** One oscillator voice: 0 = square, 1 = saw, 2 = triangle. */
    private static void voice(float[] mix, int at, double dur, double freq, int wave,
                              float duty, float vol, float attack, float decay) {
        int n = (int) (dur * SR);
        double inc = freq / SR;
        double ph = 0;
        for (int i = 0; i < n; i++) {
            int p = at + i;
            if (p >= mix.length) break;
            float t = i / (float) SR;
            float e;
            float a = attack;
            if (t < a) e = t / a;
            else e = (float) Math.pow(Math.max(0, 1 - (t - a) / (dur - a)), decay);
            float f = (float) (ph - Math.floor(ph));
            float v;
            if (wave == 0) v = f < duty ? 1f : -1f;
            else if (wave == 1) v = 2 * f - 1;
            else v = f < 0.5f ? (4 * f - 1) : (3 - 4 * f);
            mix[p] += v * e * vol;
            ph += inc;
        }
    }

    private static double midi(int note) {
        return 440.0 * Math.pow(2, (note - 69) / 12.0);
    }

    private Music() { }
}
