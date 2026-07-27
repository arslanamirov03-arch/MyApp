package com.abyss.silt.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.Random;

/**
 * All sound is generated as PCM at startup — the APK ships no audio files.
 * A looping drone carries the room tone; short one-shots are pre-rendered into
 * static AudioTrack buffers and re-triggered from a small pool.
 */
public class Synth {

    public static final int SFX_POSSESS = 0;
    public static final int SFX_ABILITY = 1;
    public static final int SFX_DASH = 2;
    public static final int SFX_BREAK = 3;
    public static final int SFX_SWITCH = 4;
    public static final int SFX_CHECKPOINT = 5;
    public static final int SFX_COMPLETE = 6;
    public static final int SFX_DEATH = 7;
    public static final int SFX_ALERT = 8;
    public static final int SFX_LUNGE = 9;
    public static final int SFX_BOSS_TELL = 10;
    public static final int SFX_BOSS_STRIKE = 11;
    public static final int SFX_BOSS_HURT = 12;
    public static final int SFX_BOSS_DIE = 13;
    public static final int SFX_BOSS_SPIT = 14;
    public static final int SFX_STROKE = 15;
    public static final int SFX_UI = 16;
    private static final int SFX_COUNT = 17;

    private static final int RATE = 22050;
    private static final int VOICES = 2;   // simultaneous copies of one sound

    private final AudioTrack[][] pool = new AudioTrack[SFX_COUNT][VOICES];
    private final int[] next = new int[SFX_COUNT];
    private AudioTrack drone;

    private boolean ready = false;
    public boolean muted = false;

    private final Random rng = new Random(90210);

    public void init() {
        if (ready) return;
        try {
            for (int i = 0; i < SFX_COUNT; i++) {
                short[] pcm = render(i);
                for (int v = 0; v < VOICES; v++) pool[i][v] = makeStatic(pcm);
            }
            drone = makeStatic(renderDrone(8f));
            if (drone != null) {
                drone.setLoopPoints(0, drone.getBufferSizeInFrames(), -1);
                drone.setVolume(0.34f);
            }
            ready = true;
        } catch (Throwable t) {
            // Audio is a luxury; a device that refuses it still plays the game.
            ready = false;
        }
    }

    public void startAmbient() {
        if (!ready || muted || drone == null) return;
        try {
            if (drone.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) drone.play();
        } catch (Throwable ignored) {
        }
    }

    public void stopAmbient() {
        if (drone == null) return;
        try {
            drone.pause();
        } catch (Throwable ignored) {
        }
    }

    public void release() {
        try {
            if (drone != null) {
                drone.stop();
                drone.release();
                drone = null;
            }
            for (int i = 0; i < SFX_COUNT; i++) {
                for (int v = 0; v < VOICES; v++) {
                    if (pool[i][v] != null) {
                        pool[i][v].release();
                        pool[i][v] = null;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        ready = false;
    }

    public void sfx(int id, float volume) {
        if (!ready || muted || id < 0 || id >= SFX_COUNT) return;
        try {
            int v = next[id];
            next[id] = (v + 1) % VOICES;
            AudioTrack tr = pool[id][v];
            if (tr == null) return;
            if (tr.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) tr.pause();
            tr.stop();
            tr.reloadStaticData();
            tr.setVolume(Math.max(0f, Math.min(1f, volume)));
            tr.play();
        } catch (Throwable ignored) {
        }
    }

    private AudioTrack makeStatic(short[] pcm) {
        if (pcm.length == 0) return null;
        int bytes = pcm.length * 2;
        AudioTrack tr = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(bytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        tr.write(pcm, 0, pcm.length);
        return tr;
    }

    // ================= sound design =================

    private short[] render(int id) {
        switch (id) {
            case SFX_POSSESS:
                // A rising shimmer: the spirit leaving the body.
                return mix(sweep(0.42f, 420f, 1180f, 0.30f, 0.02f, 0.30f),
                        filteredNoise(0.42f, 0.22f, 0.16f, 0.35f));
            case SFX_ABILITY:
                return mix(sweep(0.12f, 300f, 180f, 0.34f, 0.004f, 0.09f),
                        filteredNoise(0.10f, 0.42f, 0.14f, 0.06f));
            case SFX_DASH:
                return filteredNoise(0.30f, 0.30f, 0.42f, 0.20f);
            case SFX_BREAK:
                return mix(filteredNoise(0.34f, 0.65f, 0.50f, 0.16f),
                        sweep(0.22f, 190f, 70f, 0.34f, 0.002f, 0.16f));
            case SFX_SWITCH:
                return mix(sweep(0.20f, 150f, 96f, 0.40f, 0.003f, 0.15f),
                        filteredNoise(0.09f, 0.5f, 0.2f, 0.05f));
            case SFX_CHECKPOINT:
                return mix(tone(0.9f, 392f, 0.16f, 0.02f, 0.85f),
                        tone(0.9f, 588f, 0.10f, 0.05f, 0.85f));
            case SFX_COMPLETE:
                return mix(mix(tone(1.7f, 262f, 0.15f, 0.06f, 1.6f),
                                tone(1.7f, 330f, 0.12f, 0.20f, 1.5f)),
                        tone(1.7f, 494f, 0.10f, 0.42f, 1.2f));
            case SFX_DEATH:
                return mix(sweep(1.15f, 260f, 42f, 0.36f, 0.01f, 1.0f),
                        filteredNoise(1.1f, 0.16f, 0.30f, 0.9f));
            case SFX_ALERT:
                return mix(tone(0.30f, 92f, 0.42f, 0.004f, 0.26f),
                        tone(0.30f, 138f, 0.18f, 0.004f, 0.24f));
            case SFX_LUNGE:
                return mix(filteredNoise(0.36f, 0.18f, 0.44f, 0.26f),
                        sweep(0.30f, 120f, 58f, 0.30f, 0.006f, 0.26f));
            case SFX_BOSS_TELL:
                return mix(sweep(1.0f, 58f, 88f, 0.48f, 0.10f, 0.85f),
                        filteredNoise(0.9f, 0.10f, 0.18f, 0.8f));
            case SFX_BOSS_STRIKE:
                return mix(sweep(0.7f, 150f, 34f, 0.60f, 0.003f, 0.6f),
                        filteredNoise(0.6f, 0.34f, 0.55f, 0.4f));
            case SFX_BOSS_HURT:
                return mix(sweep(0.85f, 700f, 130f, 0.40f, 0.004f, 0.7f),
                        filteredNoise(0.7f, 0.5f, 0.45f, 0.5f));
            case SFX_BOSS_DIE:
                return mix(sweep(2.6f, 120f, 26f, 0.55f, 0.02f, 2.4f),
                        filteredNoise(2.4f, 0.09f, 0.30f, 2.2f));
            case SFX_BOSS_SPIT:
                return filteredNoise(0.26f, 0.55f, 0.34f, 0.18f);
            case SFX_STROKE:
                return filteredNoise(0.26f, 0.12f, 0.16f, 0.22f);
            case SFX_UI:
            default:
                return tone(0.16f, 220f, 0.20f, 0.004f, 0.13f);
        }
    }

    /** Steady sine with an exponential decay envelope. */
    private short[] tone(float secs, float freq, float amp, float attack, float decay) {
        int n = (int) (secs * RATE);
        short[] out = new short[n];
        double phase = 0, step = 2 * Math.PI * freq / RATE;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            float env = env(t, secs, attack, decay);
            out[i] = clip(Math.sin(phase) * amp * env);
            phase += step;
        }
        return out;
    }

    /** Sine glide from f0 to f1. */
    private short[] sweep(float secs, float f0, float f1, float amp, float attack, float decay) {
        int n = (int) (secs * RATE);
        short[] out = new short[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            float u = i / (float) n;
            float t = i / (float) RATE;
            float f = f0 + (f1 - f0) * u * u;
            float env = env(t, secs, attack, decay);
            out[i] = clip(Math.sin(phase) * amp * env);
            phase += 2 * Math.PI * f / RATE;
        }
        return out;
    }

    /** White noise through a one-pole low pass; "cutoff" is 0..1, higher is brighter. */
    private short[] filteredNoise(float secs, float cutoff, float amp, float decay) {
        int n = (int) (secs * RATE);
        short[] out = new short[n];
        double lp = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) RATE;
            double white = rng.nextDouble() * 2 - 1;
            lp += (white - lp) * cutoff;
            float env = env(t, secs, 0.004f, decay);
            out[i] = clip(lp * amp * env);
        }
        return out;
    }

    private float env(float t, float total, float attack, float decay) {
        float a = attack <= 0f ? 1f : Math.min(1f, t / attack);
        float d = decay <= 0f ? 1f : (float) Math.exp(-t / decay);
        float tail = Math.min(1f, (total - t) * 40f);   // avoid a click at the end
        return a * d * Math.max(0f, tail);
    }

    /**
     * The room tone: three detuned sub-sines, a slow noise wash and an
     * occasional distant groan. Frequencies are whole multiples of 1/secs so the
     * buffer loops without a seam.
     */
    private short[] renderDrone(float secs) {
        int n = (int) (secs * RATE);
        short[] out = new short[n];
        double lp = 0, lp2 = 0;
        float base = 1f / secs;
        float f1 = Math.round(47f / base) * base;
        float f2 = Math.round(70.6f / base) * base;
        float f3 = Math.round(94.3f / base) * base;
        float fm = Math.round(0.125f / base) * base;   // slow swell
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double swell = 0.62 + 0.38 * Math.sin(2 * Math.PI * fm * t);
            double v = 0;
            v += Math.sin(2 * Math.PI * f1 * t) * 0.42;
            v += Math.sin(2 * Math.PI * f2 * t) * 0.24;
            v += Math.sin(2 * Math.PI * f3 * t) * 0.11;
            // Water hiss, heavily damped.
            double white = rng.nextDouble() * 2 - 1;
            lp += (white - lp) * 0.020;
            lp2 += (lp - lp2) * 0.020;
            v += lp2 * 5.0 * 0.5;
            out[i] = clip(v * 0.5 * swell);
        }
        // Cross-fade the last 0.25s into the first so the loop point is inaudible.
        int fade = Math.min(n / 8, (int) (0.25f * RATE));
        for (int i = 0; i < fade; i++) {
            float u = i / (float) fade;
            int a = out[n - fade + i];
            int b = out[i];
            out[n - fade + i] = (short) (a * (1f - u) + b * u);
        }
        return out;
    }

    private short[] mix(short[] a, short[] b) {
        int n = Math.max(a.length, b.length);
        short[] out = new short[n];
        for (int i = 0; i < n; i++) {
            int v = (i < a.length ? a[i] : 0) + (i < b.length ? b[i] : 0);
            out[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, v));
        }
        return out;
    }

    private short clip(double v) {
        double s = v * 32000.0;
        if (s > 32767) s = 32767;
        if (s < -32768) s = -32768;
        return (short) s;
    }

    @SuppressWarnings("unused")
    private static int legacyStream() {
        return AudioManager.STREAM_MUSIC;
    }
}
