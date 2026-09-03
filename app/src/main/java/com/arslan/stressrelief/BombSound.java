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
 * Bomb audio: one report per charge, a warning siren whose pips close up as the
 * countdown runs down, and for the big one a report that keeps rolling long
 * after the picture has stopped moving.
 */
final class BombSound {

    private final Context ctx;
    private final Vibrator vibrator;
    private final Random rnd = new Random();

    private SoundPool pool;
    private int sHe, sDeep, sFlash, sThud, sAlarm, sBeep, sNuke, sTail;
    private boolean alarmReady, tailReady;

    private int streamAlarm, streamTail;
    private float lastAlarmVol = -1f, lastAlarmRate = -1f, lastTailVol = -1f;
    private float beepTimer;

    BombSound(Context ctx) {
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
        pool = new SoundPool.Builder().setMaxStreams(10).setAudioAttributes(attrs).build();
        pool.setOnLoadCompleteListener((sp, id, status) -> {
            if (status != 0) return;
            if (id == sAlarm) alarmReady = true;
            if (id == sTail) tailReady = true;
        });
        sHe = load("audio/bomb_he.wav");
        sDeep = load("audio/bomb_deep.wav");
        sFlash = load("audio/bomb_flash.wav");
        sThud = load("audio/bomb_thud.wav");
        sAlarm = load("audio/alarm_loop.wav");
        sBeep = load("audio/alarm_beep.wav");
        sNuke = load("audio/nuke.wav");
        sTail = load("audio/nuke_tail_loop.wav");
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

    void charge(int type) {
        if (pool == null) return;
        int id;
        float rate = 0.94f + rnd.nextFloat() * 0.12f;
        switch (type) {
            case BombState.TYPE_DEEP:
                id = sDeep;
                break;
            case BombState.TYPE_FLASH:
                id = sFlash;
                break;
            case BombState.TYPE_THUD:
                id = sThud;
                break;
            case BombState.TYPE_PLASMA:
                id = sFlash;
                rate = 1.22f + rnd.nextFloat() * 0.16f;
                break;
            case BombState.TYPE_HE:
            default:
                id = sHe;
                break;
        }
        pool.play(id, 1f, 1f, 4, 0, rate);
        vibrate(220, 235);
    }

    void nuke() {
        if (pool == null) return;
        pool.play(sNuke, 1f, 1f, 6, 0, 1f);
        if (vibrator != null && vibrator.hasVibrator()) {
            try {
                long[] timings = {0, 700, 120, 900, 200, 1400, 300, 1800};
                int[] amps = {0, 255, 0, 220, 0, 170, 0, 110};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amps, -1));
                } else {
                    vibrator.vibrate(timings, -1);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * @param arm01  0..1 through the countdown
     * @param arming true while the finger is still down and the timer running
     * @param nukeT  seconds since detonation, or negative
     */
    void update(float arm01, boolean arming, float nukeT, float dt) {
        if (pool == null) return;

        // --- warning siren --------------------------------------------------
        if (arming && arm01 > 0.02f) {
            if (alarmReady && streamAlarm == 0) {
                streamAlarm = pool.play(sAlarm, 0f, 0f, 2, -1, 1f);
            }
            if (streamAlarm != 0) {
                float vol = clamp(0.12f + 0.78f * arm01, 0f, 1f);
                float rate = clamp(0.85f + 0.55f * arm01, 0.5f, 2.0f);
                if (Math.abs(vol - lastAlarmVol) > 0.01f) {
                    pool.setVolume(streamAlarm, vol, vol);
                    lastAlarmVol = vol;
                }
                if (Math.abs(rate - lastAlarmRate) > 0.01f) {
                    pool.setRate(streamAlarm, rate);
                    lastAlarmRate = rate;
                }
            }
            // the pips close up as the countdown runs out
            beepTimer -= dt;
            if (beepTimer <= 0f) {
                float interval = 1.25f - 1.12f * (float) Math.pow(arm01, 1.4);
                beepTimer = Math.max(interval, 0.11f);
                pool.play(sBeep, clamp(0.35f + 0.6f * arm01, 0f, 1f),
                        clamp(0.35f + 0.6f * arm01, 0f, 1f), 3, 0,
                        clamp(0.9f + 0.5f * arm01, 0.5f, 2.0f));
                vibrate(18, (int) (60 + 140 * arm01));
            }
        } else {
            stopAlarm();
            beepTimer = 0f;
        }

        // --- the roar that outlasts the blast --------------------------------
        if (nukeT >= 0f) {
            if (tailReady && streamTail == 0) {
                streamTail = pool.play(sTail, 0f, 0f, 1, -1, 0.85f);
            }
            if (streamTail != 0) {
                // holds for a few seconds, then takes its time going away
                float vol = nukeT < 3f ? clamp(nukeT / 1.2f, 0f, 1f)
                        : clamp(1f - (nukeT - 3f) / 17f, 0f, 1f);
                vol *= 0.85f;
                if (Math.abs(vol - lastTailVol) > 0.01f) {
                    pool.setVolume(streamTail, vol, vol);
                    lastTailVol = vol;
                }
            }
        } else if (streamTail != 0) {
            pool.stop(streamTail);
            streamTail = 0;
            lastTailVol = -1f;
        }
    }

    private void stopAlarm() {
        if (streamAlarm != 0) {
            pool.stop(streamAlarm);
            streamAlarm = 0;
            lastAlarmVol = -1f;
            lastAlarmRate = -1f;
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
            stopAlarm();
            if (streamTail != 0) {
                pool.stop(streamTail);
                streamTail = 0;
                lastTailVol = -1f;
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
