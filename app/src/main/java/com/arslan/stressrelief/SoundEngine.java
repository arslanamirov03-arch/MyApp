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

/**
 * Flame bed + sub rumble as looping streams whose gain and pitch track the fire,
 * plus the match and detonation one-shots.
 */
final class SoundEngine {

    private final Context ctx;
    private final Vibrator vibrator;

    private SoundPool pool;
    private int sFire, sRumble, sMatch, sBoom;
    private int streamFire, streamRumble;
    private boolean fireReady, rumbleReady;

    private float lastFireVol = -1f, lastFireRate = -1f, lastRumbleVol = -1f;
    private float silence;

    SoundEngine(Context ctx) {
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
        pool = new SoundPool.Builder().setMaxStreams(8).setAudioAttributes(attrs).build();
        pool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            if (status != 0) return;
            if (sampleId == sFire) fireReady = true;
            if (sampleId == sRumble) rumbleReady = true;
        });
        sFire = load("audio/fire_loop.wav");
        sRumble = load("audio/rumble_loop.wav");
        sMatch = load("audio/match.wav");
        sBoom = load("audio/boom.wav");
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

    /** Called every frame with the current fire intensity. */
    void update(float intensity, boolean burning, float dt) {
        if (pool == null) return;

        if (!burning) {
            silence += dt;
            if (silence > 0.35f) {
                stopLoops();
                return;
            }
        } else {
            silence = 0f;
        }

        float q = clamp(intensity, 0f, 1f);

        if (fireReady) {
            if (streamFire == 0) {
                streamFire = pool.play(sFire, 0f, 0f, 1, -1, 1f);
            }
            if (streamFire != 0) {
                float vol = clamp(0.28f + 0.72f * q, 0f, 1f);
                float rate = clamp(1.28f - 0.45f * q, 0.5f, 2.0f);
                if (Math.abs(vol - lastFireVol) > 0.01f) {
                    pool.setVolume(streamFire, vol, vol);
                    lastFireVol = vol;
                }
                if (Math.abs(rate - lastFireRate) > 0.01f) {
                    pool.setRate(streamFire, rate);
                    lastFireRate = rate;
                }
            }
        }

        if (rumbleReady) {
            float vol = clamp((q - 0.12f) / 0.88f, 0f, 1f);
            vol *= vol;
            if (vol > 0.001f && streamRumble == 0) {
                streamRumble = pool.play(sRumble, 0f, 0f, 1, -1, 1f);
            }
            if (streamRumble != 0 && Math.abs(vol - lastRumbleVol) > 0.01f) {
                pool.setVolume(streamRumble, vol, vol);
                lastRumbleVol = vol;
            }
        }
    }

    private void stopLoops() {
        if (streamFire != 0) {
            pool.stop(streamFire);
            streamFire = 0;
            lastFireVol = -1f;
            lastFireRate = -1f;
        }
        if (streamRumble != 0) {
            pool.stop(streamRumble);
            streamRumble = 0;
            lastRumbleVol = -1f;
        }
    }

    void playMatch() {
        if (pool != null) pool.play(sMatch, 0.9f, 0.9f, 2, 0, 1f);
    }

    void playBoom() {
        if (pool != null) pool.play(sBoom, 1f, 1f, 3, 0, 1f);
        vibrateExplosion();
    }

    private void vibrateExplosion() {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                long[] timings = {0, 90, 40, 260, 60, 420};
                int[] amps = {0, 255, 0, 210, 0, 120};
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amps, -1));
            } else {
                vibrator.vibrate(new long[]{0, 90, 40, 260, 60, 420}, -1);
            }
        } catch (Throwable ignored) {
        }
    }

    /** A short tick as the fire crosses each escalation step. */
    void vibrateTick(int amplitude) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(28,
                        Math.max(1, Math.min(255, amplitude))));
            } else {
                vibrator.vibrate(28);
            }
        } catch (Throwable ignored) {
        }
    }

    void pause() {
        if (pool != null) {
            stopLoops();
            pool.autoPause();
        }
    }

    void resume() {
        if (pool != null) pool.autoResume();
    }

    void release() {
        if (pool != null) {
            stopLoops();
            pool.release();
            pool = null;
        }
    }
}
