package com.arslan.stressrelief;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import java.io.IOException;
import java.util.Random;

/**
 * Storm audio. Isolated reports while strikes are occasional; once they come
 * several times a second the big rolls would pile into mud, so short cracks
 * take over and the looping bed carries the weight.
 */
final class StormSound {

    private final Context ctx;
    private final Vibrator vibrator;
    private final Random rnd = new Random();

    private SoundPool pool;
    private final int[] thunderSingle = new int[3];
    private final int[] thunderRoll = new int[3];
    private int sFar, sCrack, sLoop;
    private boolean loopReady;
    private int streamLoop;
    private float lastVol = -1f, lastRate = -1f;

    private float bigThunderCooldown;

    StormSound(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        Vibrator v = null;
        try {
            v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        } catch (Throwable ignored) {
        }
        vibrator = v;
    }

    void create() {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(12).setAudioAttributes(attrs).build();
        pool.setOnLoadCompleteListener((sp, id, status) -> {
            if (status == 0 && id == sLoop) loopReady = true;
        });
        for (int i = 0; i < 3; i++) {
            thunderSingle[i] = load("audio/thunder_" + i + ".wav");
            thunderRoll[i] = load("audio/thunder_roll_" + i + ".wav");
        }
        sFar = load("audio/thunder_far.wav");
        sCrack = load("audio/crack.wav");
        sLoop = load("audio/storm_loop.wav");
    }

    private int load(String asset) {
        try (AssetFileDescriptor afd = ctx.getAssets().openFd(asset)) {
            return pool.load(afd, 1);
        } catch (IOException e) {
            Log.e(GLUtil.TAG, "cannot load " + asset, e);
            return 0;
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** power < 0 means a distant flash rather than a strike on screen. */
    void strike(float power, float intensity) {
        if (pool == null) return;

        if (power < 0f) {
            pool.play(sFar, 0.45f, 0.45f, 1, 0, 0.85f + rnd.nextFloat() * 0.2f);
            return;
        }

        // An isolated strike across open ground gives one clear slap-back and
        // then quiet. Once the storm is running, reports pile onto each other and
        // the reflections never get a chance to die out, so the rolling variants
        // take over and the individual cracks ride on top.
        int[] set = intensity < 0.20f ? thunderSingle : thunderRoll;
        float cooldown = intensity < 0.20f ? 0.45f : 1.15f;

        if (intensity < 0.55f && bigThunderCooldown <= 0f) {
            float vol = clamp(0.65f + 0.35f * power, 0f, 1f);
            pool.play(set[rnd.nextInt(3)], vol, vol, 3, 0, 0.92f + rnd.nextFloat() * 0.16f);
            bigThunderCooldown = cooldown;
            vibrate(180, (int) (150 + 90 * power));
            return;
        }

        float vol = clamp(0.40f + 0.45f * power, 0f, 1f);
        pool.play(sCrack, vol, vol, 2, 0, 0.85f + rnd.nextFloat() * 0.45f);
        if (bigThunderCooldown <= 0f) {
            // keep a roll running underneath the cracks at the climax
            pool.play(thunderRoll[rnd.nextInt(3)], 0.75f, 0.75f, 1, 0,
                    0.85f + rnd.nextFloat() * 0.2f);
            bigThunderCooldown = 1.15f;
        }
        if (rnd.nextFloat() < 0.25f + 0.4f * intensity) {
            vibrate(45, (int) (70 + 140 * intensity));
        }
    }

    /** Called every frame; the bed follows the storm up and, slowly, back down. */
    void update(float intensity, float afterglow, float dt) {
        if (pool == null) return;
        bigThunderCooldown = Math.max(0f, bigThunderCooldown - dt);

        // Afterglow is what makes the noise linger: it decays far slower than
        // the strikes do, so the roar fades out over several seconds.
        float level = clamp(Math.max(intensity, afterglow * 0.75f), 0f, 1f);

        if (level < 0.004f) {
            if (streamLoop != 0) {
                pool.stop(streamLoop);
                streamLoop = 0;
                lastVol = -1f;
                lastRate = -1f;
            }
            return;
        }

        if (loopReady && streamLoop == 0) {
            streamLoop = pool.play(sLoop, 0f, 0f, 1, -1, 1f);
        }
        if (streamLoop != 0) {
            float vol = clamp(0.10f + 0.90f * level, 0f, 1f);
            float rate = clamp(0.80f + 0.35f * intensity, 0.5f, 2.0f);
            if (Math.abs(vol - lastVol) > 0.01f) {
                pool.setVolume(streamLoop, vol, vol);
                lastVol = vol;
            }
            if (Math.abs(rate - lastRate) > 0.01f) {
                pool.setRate(streamLoop, rate);
                lastRate = rate;
            }
        }
    }

    private void vibrate(int ms, int amplitude) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms,
                        Math.max(1, Math.min(255, amplitude))));
            } else {
                vibrator.vibrate(ms);
            }
        } catch (Throwable ignored) {
        }
    }

    void pause() {
        if (pool != null) {
            if (streamLoop != 0) {
                pool.stop(streamLoop);
                streamLoop = 0;
                lastVol = -1f;
                lastRate = -1f;
            }
            pool.autoPause();
        }
    }

    void resume() {
        if (pool != null) pool.autoResume();
    }

    void release() {
        if (pool != null) {
            pool.release();
            pool = null;
        }
    }
}
