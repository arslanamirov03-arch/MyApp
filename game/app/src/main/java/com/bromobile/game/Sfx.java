package com.bromobile.game;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.SoundPool;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

/**
 * Every sound in the game is synthesised at runtime — there are no audio assets.
 * Short effects are rendered to 8-bit WAVs in the cache directory and played
 * through a {@link SoundPool}; the looping music is rendered into a single PCM
 * buffer per theme and streamed by a static-mode {@link AudioTrack}.
 */
public final class Sfx {

    public static final int SHOOT = 0, SHOTGUN = 1, EXPLODE = 2, THROW = 3, JUMP = 4,
            LAND = 5, HURT = 6, ENEMY_DIE = 7, PICKUP = 8, CLICK = 9, ROAR = 10,
            LASER = 11, RICOCHET = 12, WIN = 13, CHECKPOINT = 14, ICE = 15,
            STEAM = 16, ALARM = 17, ROCKET = 18, FLAME = 19, RESCUE = 20,
            SWITCH = 21, DEATH = 22, STOMP = 23, COUNT = 24;

    private static final int SR = 22050;

    private final Context ctx;
    private final Save save;
    private SoundPool pool;
    private final int[] ids = new int[COUNT];
    private Vibrator vib;

    private AudioTrack music;
    private int musicTrackId = -1;
    private Thread musicBuilder;

    public Sfx(Context c, Save s) {
        ctx = c;
        save = s;
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                pool = new SoundPool.Builder().setMaxStreams(24)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build())
                        .build();
            } else {
                pool = new SoundPool(24, AudioManager.STREAM_MUSIC, 0);
            }
            vib = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------
    // Effect synthesis
    // ------------------------------------------------------------------

    /** Renders and registers every effect. Call from a worker thread. */
    public void buildAll() {
        if (pool == null) return;
        for (int i = 0; i < COUNT; i++) {
            try {
                float[] buf = render(i);
                ids[i] = load("sfx" + i, buf);
            } catch (Throwable t) {
                ids[i] = 0;
            }
        }
    }

    private static float env(float t, float dur, float attack, float decay) {
        if (t < attack) return t / attack;
        float r = (t - attack) / Math.max(0.0001f, dur - attack);
        return (float) Math.pow(Math.max(0, 1 - r), decay);
    }

    private float[] render(int id) {
        Random rnd = new Random(1234 + id * 977L);
        switch (id) {
            case SHOOT: {
                float d = 0.11f;
                float[] b = new float[(int) (SR * d)];
                for (int i = 0; i < b.length; i++) {
                    float t = i / (float) SR;
                    float e = env(t, d, 0.001f, 3.2f);
                    float f = 900 - 700 * (t / d);
                    float tone = (float) Math.sin(2 * Math.PI * f * t);
                    b[i] = (tone * 0.35f + (rnd.nextFloat() * 2 - 1) * 0.65f) * e * 0.85f;
                }
                return b;
            }
            case SHOTGUN: {
                float d = 0.26f;
                float[] b = new float[(int) (SR * d)];
                for (int i = 0; i < b.length; i++) {
                    float t = i / (float) SR;
                    float e = env(t, d, 0.002f, 2.4f);
                    float low = (float) Math.sin(2 * Math.PI * (150 - 90 * t / d) * t);
                    b[i] = ((rnd.nextFloat() * 2 - 1) * 0.75f + low * 0.45f) * e;
                }
                return b;
            }
            case EXPLODE: {
                float d = 0.85f;
                float[] b = new float[(int) (SR * d)];
                float lp = 0;
                for (int i = 0; i < b.length; i++) {
                    float t = i / (float) SR;
                    float e = env(t, d, 0.004f, 2.0f);
                    float n = rnd.nextFloat() * 2 - 1;
                    lp += (n - lp) * (0.55f - 0.45f * (t / d));   // darkens as it decays
                    float rumble = (float) Math.sin(2 * Math.PI * (70 - 45 * t / d) * t);
                    b[i] = (lp * 0.8f + rumble * 0.6f) * e;
                }
                return b;
            }
            case THROW: {
                float d = 0.22f;
                float[] b = new float[(int) (SR * d)];
                for (int i = 0; i < b.length; i++) {
                    float t = i / (float) SR;
                    float e = (float) Math.sin(Math.PI * t / d);
                    float n = rnd.nextFloat() * 2 - 1;
                    b[i] = n * e * 0.35f * (0.4f + 0.6f * (t / d));
                }
                return b;
            }
            case JUMP: {
                float d = 0.16f;
                float[] b = new float[(int) (SR * d)];
                for (int i = 0; i < b.length; i++) {
                    float t = i / (float) SR;
                    float f = 260 + 520 * (t / d);
                    b[i] = square(f * t) * env(t, d, 0.005f, 2.5f) * 0.4f;
                }
                return b;
            }
            case LAND: {
                float d = 0.14f;
                float[] b = new float[(int) (SR * d)];
                for (int i = 0; i < b.length; i++) {
                    float t = i / (float) SR;
                    float e = env(t, d, 0.001f, 4f);
                    b[i] = ((float) Math.sin(2 * Math.PI * (120 - 70 * t / d) * t) * 0.7f
                            + (rnd.nextFloat() * 2 - 1) * 0.3f) * e * 0.6f;
                }
                return b;
            }
            case HURT:
            case DEATH: {
                float d = id == DEATH ? 0.7f : 0.35f;
                float[] b = new float[(int) (SR * d)];
                for (int i = 0; i < b.length; i++) {
                    float t = i / (float) SR;
                    float f = 420 - 340 * (t / d);
                    b[i] = (square(f * t) * 0.5f + (rnd.nextFloat() * 2 - 1) * 0.4f)
                            * env(t, d, 0.004f, 1.8f) * 0.7f;
                }
                return b;
            }
            case ENEMY_DIE: {
                float d = 0.3f;
                float[] b = new float[(int) (SR * d)];
                for (int i = 0; i < b.length; i++) {
                    float t = i / (float) SR;
                    float f = 300 - 200 * (t / d);
                    b[i] = (saw(f * t) * 0.4f + (rnd.nextFloat() * 2 - 1) * 0.6f)
                            * env(t, d, 0.002f, 2.6f) * 0.6f;
                }
                return b;
            }
            case PICKUP: {
                float d = 0.28f;
                float[] b = new float[(int) (SR * d)];
                float[] notes = {880, 1174, 1568};
                for (int i = 0; i < b.length; i++) {
                    float t = i / (float) SR;
                    int step = Math.min(2, (int) (t / (d / 3)));
                    b[i] = square(notes[step] * t) * env(t, d, 0.003f, 1.4f) * 0.32f;
                }
                return b;
            }
            case RESCUE: {
                float d = 0.5f;
                float[] b = new float[(int) (SR * d)];
                float[] notes = {523, 659, 784, 1046};
                for (int i = 0; i < b.length; i++) {
                    float t = i / (float) SR;
                    int step = Math.min(3, (int) (t / (d / 4)));
                    b[i] = (square(notes[step] * t) * 0.5f + tri(notes[step] * 2 * t) * 0.3f)
                            * env(t, d, 0.004f, 1.1f) * 0.3f;
                }
                return b;
            }
            case CLICK: {
                float d = 0.06f;
                float[] b = new float[(int) (SR * d)];
                for (int i = 0; i < b.length; i++) {
                    float t = i / (float) SR;
                    b[i] = square(680 * t) * env(t, d, 0.001f, 3f) * 0.3f;
                }
                return b;
            }
            case SWITCH: {
                float d = 0.1f;
                float[] b = new float[(int) (SR * d)];
                for (int i = 0; i < b.length; i++) {
                    float t = i / (float) SR;
                    float f = t < d / 2 ? 500 : 780;
                    b[i] = square(f * t) * env(t, d, 0.002f, 2f) * 0.28f;
                }
                return b;
            }
            case ROAR: {
                float d = 1.3f;
                float[] b = new float[(int) (SR * d)];
                float lp = 0;
                for (int i = 0; i < b.length; i++) {
                    float t = i / (float) SR;
                    float f = 60 + 40 * (float) Math.sin(t * 7);
                    float growl = saw(f * t) * 0.6f + saw(f * 1.5f * t) * 0.3f;
                    float n = rnd.nextFloat() * 2 - 1;
                    lp += (n - lp) * 0.18f;
                    b[i] = (growl + lp * 0.5f) * env(t, d, 0.08f, 1.3f) * 0.75f;
                }
                return b;
            }
            case LASER: {
                float d = 0.3f;
                float[] b = new float[(int) (SR * d)];
                for (int i = 0; i < b.length; i++) {
                    float t = i / (float) SR;
                    float f = 1500 - 1200 * (t / d);
                    b[i] = (saw(f * t) * 0.5f + square(f * 0.5f * t) * 0.3f)
                            * env(t, d, 0.005f, 1.6f) * 0.4f;
                }
                return b;
            }
            case RICOCHET: {
                float d = 0.14f;
                float[] b = new float[(int) (SR * d)];
                for (int i = 0; i < b.length; i++) {
                    float t = i / (float) SR;
                    float f = 2400 - 1600 * (t / d);
                    b[i] = (float) Math.sin(2 * Math.PI * f * t) * env(t, d, 0.001f, 3f) * 0.25f;
                }
                return b;
            }
            case WIN: {
                float d = 1.5f;
                float[] b = new float[(int) (SR * d)];
                float[] notes = {523, 659, 784, 1046, 784, 1046, 1318, 1568};
                for (int i = 0; i < b.length; i++) {
                    float t = i / (float) SR;
                    int step = Math.min(7, (int) (t / (d / 8)));
                    float lt = t - step * (d / 8);
                    b[i] = (square(notes[step] * t) * 0.4f + tri(notes[step] * 0.5f * t) * 0.35f)
                            * env(lt, d / 8, 0.005f, 1.2f) * 0.3f;
                }
                return b;
            }
            case CHECKPOINT: {
                float d = 0.55f;
                float[] b = new float[(int) (SR * d)];
                float[] notes = {784, 1046};
                for (int i = 0; i < b.length; i++) {
                    float t = i / (float) SR;
                    int step = Math.min(1, (int) (t / (d / 2)));
                    b[i] = tri(notes[step] * t) * env(t, d, 0.005f, 1.1f) * 0.32f;
                }
                return b;
            }
            case ICE: {
                float d = 0.4f;
                float[] b = new float[(int) (SR * d)];
                for (int i = 0; i < b.length; i++) {
                    float t = i / (float) SR;
                    float s = (float) (Math.sin(2 * Math.PI * 2100 * t)
                            + Math.sin(2 * Math.PI * 3300 * t) * 0.6f
                            + Math.sin(2 * Math.PI * 4700 * t) * 0.4f);
                    b[i] = s * env(t, d, 0.002f, 2.4f) * 0.22f;
                }
                return b;
            }
            case STEAM: {
                float d = 0.6f;
                float[] b = new float[(int) (SR * d)];
                float hp = 0, prev = 0;
                for (int i = 0; i < b.length; i++) {
                    float t = i / (float) SR;
                    float n = rnd.nextFloat() * 2 - 1;
                    hp = 0.85f * (hp + n - prev);
                    prev = n;
                    b[i] = hp * (float) Math.sin(Math.PI * t / d) * 0.4f;
                }
                return b;
            }
            case ALARM: {
                float d = 0.7f;
                float[] b = new float[(int) (SR * d)];
                for (int i = 0; i < b.length; i++) {
                    float t = i / (float) SR;
                    float f = ((int) (t / 0.175f) % 2 == 0) ? 620 : 460;
                    b[i] = square(f * t) * 0.3f * (float) Math.sin(Math.PI * t / d);
                }
                return b;
            }
            case ROCKET: {
                float d = 0.5f;
                float[] b = new float[(int) (SR * d)];
                float lp = 0;
                for (int i = 0; i < b.length; i++) {
                    float t = i / (float) SR;
                    float n = rnd.nextFloat() * 2 - 1;
                    lp += (n - lp) * 0.3f;
                    float f = 180 + 260 * (t / d);
                    b[i] = (lp * 0.7f + saw(f * t) * 0.4f) * env(t, d, 0.01f, 1.4f) * 0.55f;
                }
                return b;
            }
            case FLAME: {
                float d = 0.25f;
                float[] b = new float[(int) (SR * d)];
                float lp = 0;
                for (int i = 0; i < b.length; i++) {
                    float t = i / (float) SR;
                    float n = rnd.nextFloat() * 2 - 1;
                    lp += (n - lp) * 0.25f;
                    b[i] = lp * (float) Math.sin(Math.PI * t / d) * 0.5f;
                }
                return b;
            }
            case STOMP:
            default: {
                float d = 0.45f;
                float[] b = new float[(int) (SR * d)];
                for (int i = 0; i < b.length; i++) {
                    float t = i / (float) SR;
                    float e = env(t, d, 0.002f, 2.2f);
                    b[i] = ((float) Math.sin(2 * Math.PI * (95 - 60 * t / d) * t) * 0.85f
                            + (rnd.nextFloat() * 2 - 1) * 0.35f) * e;
                }
                return b;
            }
        }
    }

    private static float square(float phase) {
        return (phase - (float) Math.floor(phase)) < 0.5f ? 1f : -1f;
    }

    private static float saw(float phase) {
        return 2f * (phase - (float) Math.floor(phase)) - 1f;
    }

    private static float tri(float phase) {
        float p = phase - (float) Math.floor(phase);
        return p < 0.5f ? (4 * p - 1) : (3 - 4 * p);
    }

    // ------------------------------------------------------------------
    // WAV writing / SoundPool
    // ------------------------------------------------------------------

    private int load(String name, float[] samples) throws Exception {
        File f = new File(ctx.getCacheDir(), name + ".wav");
        byte[] pcm = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            int v = (int) (Math.max(-1f, Math.min(1f, samples[i])) * 32000);
            pcm[i * 2] = (byte) (v & 0xFF);
            pcm[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
        }
        FileOutputStream out = new FileOutputStream(f);
        out.write(wavHeader(pcm.length));
        out.write(pcm);
        out.close();
        return pool.load(f.getAbsolutePath(), 1);
    }

    private static byte[] wavHeader(int dataLen) {
        int total = 36 + dataLen;
        int byteRate = SR * 2;
        return new byte[]{
                'R', 'I', 'F', 'F',
                (byte) total, (byte) (total >> 8), (byte) (total >> 16), (byte) (total >> 24),
                'W', 'A', 'V', 'E', 'f', 'm', 't', ' ',
                16, 0, 0, 0, 1, 0, 1, 0,
                (byte) SR, (byte) (SR >> 8), (byte) (SR >> 16), (byte) (SR >> 24),
                (byte) byteRate, (byte) (byteRate >> 8), (byte) (byteRate >> 16), (byte) (byteRate >> 24),
                2, 0, 16, 0, 'd', 'a', 't', 'a',
                (byte) dataLen, (byte) (dataLen >> 8), (byte) (dataLen >> 16), (byte) (dataLen >> 24)
        };
    }

    // ------------------------------------------------------------------
    // Playback
    // ------------------------------------------------------------------

    public void play(int id) { play(id, 1f, 1f); }

    public void play(int id, float vol, float rate) {
        if (pool == null || id < 0 || id >= COUNT || ids[id] == 0) return;
        float v = vol * save.sfx();
        if (v <= 0.001f) return;
        try {
            pool.play(ids[id], v, v, 1, 0, Math.max(0.5f, Math.min(2f, rate)));
        } catch (Throwable ignored) { }
    }

    /** Quieter and detuned with distance from the camera. */
    public void playAt(int id, float dist, float vol, float rate) {
        float att = Math.max(0f, 1f - dist / 420f);
        if (att <= 0.02f) return;
        play(id, vol * att, rate);
    }

    public void buzz(int ms) {
        if (!save.vibrate || vib == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vib.vibrate(ms);
            }
        } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------------
    // Music
    // ------------------------------------------------------------------

    /**
     * Starts (or switches to) a looping theme. Track ids 0..4 are the five maps,
     * 5 is the menu and 6 is the boss theme.
     */
    public void playMusic(final int trackId) {
        if (musicTrackId == trackId && music != null) {
            setMusicVolume();
            return;
        }
        musicTrackId = trackId;
        stopMusic();
        if (save.music() <= 0.001f) return;
        musicBuilder = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    short[] pcm = Music.render(trackId);
                    if (musicTrackId != trackId) return;
                    AudioTrack t;
                    int bytes = pcm.length * 2;
                    if (Build.VERSION.SDK_INT >= 21) {
                        t = new AudioTrack.Builder()
                                .setAudioAttributes(new AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_GAME)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                                .setAudioFormat(new AudioFormat.Builder()
                                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                        .setSampleRate(Music.SR)
                                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                                .setBufferSizeInBytes(bytes)
                                .setTransferMode(AudioTrack.MODE_STATIC).build();
                    } else {
                        t = new AudioTrack(AudioManager.STREAM_MUSIC, Music.SR,
                                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                                bytes, AudioTrack.MODE_STATIC);
                    }
                    t.write(pcm, 0, pcm.length);
                    t.setLoopPoints(0, pcm.length, -1);
                    synchronized (Sfx.this) {
                        if (musicTrackId != trackId) { t.release(); return; }
                        music = t;
                        setMusicVolume();
                        t.play();
                    }
                } catch (Throwable ignored) { }
            }
        }, "music-gen");
        musicBuilder.setPriority(Thread.MIN_PRIORITY);
        musicBuilder.start();
    }

    public synchronized void setMusicVolume() {
        if (music == null) return;
        float v = save.music() * 0.55f;
        try { music.setStereoVolume(v, v); } catch (Throwable ignored) { }
    }

    public synchronized void stopMusic() {
        if (music != null) {
            try { music.pause(); music.flush(); music.release(); } catch (Throwable ignored) { }
            music = null;
        }
    }

    /** Silences the music without forgetting which track was selected. */
    public synchronized void pauseMusic() {
        if (music != null) try { music.pause(); } catch (Throwable ignored) { }
    }

    public synchronized void resumeMusic() {
        if (music != null) try { music.play(); } catch (Throwable ignored) { }
        else if (musicTrackId >= 0 && save.music() > 0.001f) {
            int t = musicTrackId;
            musicTrackId = -1;
            playMusic(t);
        }
    }

    public void release() {
        stopMusic();
        musicTrackId = -1;
        if (pool != null) {
            try { pool.release(); } catch (Throwable ignored) { }
            pool = null;
        }
    }
}
