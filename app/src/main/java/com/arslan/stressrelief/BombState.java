package com.arslan.stressrelief;

import java.util.Random;

/**
 * Bomb mode. A tap detonates one charge straight away; keeping the finger down
 * starts arming something much larger, and the wait for it is the longest in the
 * app on purpose.
 */
final class BombState {

    static final float ARM_TIME = 18.0f;   // longest hold of any mode
    static final float NUKE_LEN = 26.0f;   // blast, mushroom, then the fade out

    static final int TYPE_HE = 0;
    static final int TYPE_DEEP = 1;
    static final int TYPE_FLASH = 2;
    static final int TYPE_THUD = 3;
    static final int TYPE_PLASMA = 4;
    static final int TYPE_COUNT = 5;

    private final Random rnd = new Random();

    int type = TYPE_HE;
    float blastT = -1f;     // seconds since a conventional charge went off
    float armT;             // seconds the finger has been down
    float nukeT = -1f;      // seconds since detonation
    boolean arming;

    boolean justFired;
    boolean justNuked;
    boolean resetRequested;

    void touchDown() {
        if (nukeT >= 0f) return;
        type = rnd.nextInt(TYPE_COUNT);
        blastT = 0f;
        armT = 0f;
        arming = true;
        justFired = true;
    }

    void update(float dt, boolean touching) {
        justFired = false;
        justNuked = false;
        resetRequested = false;

        if (nukeT >= 0f) {
            nukeT += dt;
            if (nukeT >= NUKE_LEN) {
                nukeT = -1f;
                blastT = -1f;
                armT = 0f;
                arming = false;
                resetRequested = true;
            }
            return;
        }

        if (blastT >= 0f) {
            blastT += dt;
            if (blastT > 9f) blastT = -1f;
        }

        if (touching && arming) {
            armT += dt;
            if (armT >= ARM_TIME) {
                nukeT = 0f;
                armT = 0f;          // the warning haze must not survive the blast
                arming = false;
                justNuked = true;
            }
        } else if (!touching) {
            arming = false;
            armT = Math.max(0f, armT - dt * 2.5f);
        }
    }

    boolean nuking() {
        return nukeT >= 0f;
    }

    /** 0..1 through the arming countdown. */
    float arm01() {
        return Math.min(armT / ARM_TIME, 1f);
    }

    // ---- conventional charge envelopes -------------------------------------
    private static final float[] BLAST_PUSH = {16000f, 11000f, 18000f, 9500f, 17000f};
    private static final float[] BLAST_HEAT = {40f, 34f, 55f, 30f, 45f};
    private static final float[] BLAST_RAD = {0.075f, 0.105f, 0.060f, 0.090f, 0.065f};
    private static final float[] BLAST_SPEED = {1.0f, 0.72f, 1.5f, 0.8f, 1.35f};

    float push() {
        if (blastT < 0f) return 0f;
        float sp = BLAST_SPEED[type];
        if (blastT >= 0.30f / sp) return 0f;
        return BLAST_PUSH[type] * (float) Math.exp(-blastT * sp / 0.05f);
    }

    float pushRadius() {
        return 0.03f + Math.max(blastT, 0f) * 0.55f * BLAST_SPEED[type];
    }

    float heat() {
        if (blastT < 0f) return 0f;
        float sp = BLAST_SPEED[type];
        if (blastT >= 0.36f / sp) return 0f;
        return BLAST_HEAT[type] * (float) Math.exp(-blastT * sp / 0.06f);
    }

    float heatRadius() {
        return BLAST_RAD[type] + Math.max(blastT, 0f) * 0.30f;
    }

    // ---- the big one --------------------------------------------------------
    float nukePush() {
        if (nukeT < 0f || nukeT >= 0.40f) return 0f;
        return 600f * (float) Math.exp(-nukeT / 0.09f);
    }

    float nukePushRadius() {
        return 0.03f + nukeT * 0.22f;
    }

    float nukeHeat() {
        if (nukeT < 0f || nukeT >= 0.7f) return 0f;
        return 70f * (float) Math.exp(-nukeT / 0.13f);
    }

    float nukeHeatRadius() {
        return 0.035f + Math.min(nukeT, 1.2f) * 0.05f;
    }

    /** 0 while the fireball burns, 1 once it has become a rising cloud. */
    float mushroom01() {
        if (nukeT < 0f) return 0f;
        return clamp((nukeT - 0.5f) / 1.2f, 0f, 1f);
    }

    float flash() {
        if (nukeT >= 0f) {
            // a nuclear flash outlasts a conventional one by a long way
            return 3.0f * (float) Math.exp(-nukeT / 0.30f);
        }
        if (blastT < 0f) return 0f;
        // Short and bright. Stretch this and the additive term stops reading as
        // a flash and just sits over the frame as a grey haze.
        float peak = type == TYPE_FLASH ? 3.6f : (type == TYPE_PLASMA ? 3.0f : 2.6f);
        return peak * (float) Math.exp(-blastT / 0.045f);
    }

    float shock() {
        if (nukeT >= 0f) return nukeT > 1.6f ? -1f : nukeT / 1.6f;
        if (blastT < 0f || blastT > 0.8f) return -1f;
        return blastT / 0.8f;
    }

    float shakeAmp() {
        float a = 0f;
        if (blastT >= 0f) a += 0.050f * (float) Math.exp(-blastT / 0.30f);
        if (nukeT >= 0f) {
            a += 0.090f * (float) Math.exp(-nukeT / 0.9f);
            a += 0.012f * (float) Math.exp(-nukeT / 6.0f);
        }
        // the device shivers while it is arming, harder the closer it gets
        a += 0.0045f * (float) Math.pow(arm01(), 2.5);
        return a;
    }

    float zoom() {
        float z = 0f;
        if (blastT >= 0f) z += 0.045f * (float) Math.exp(-blastT / 0.28f);
        if (nukeT >= 0f) z += 0.075f * (float) Math.exp(-nukeT / 0.7f);
        return z;
    }

    /** Red warning haze, pulsing faster the closer the countdown gets. */
    float dangerPulse(float time) {
        float a = arm01();
        float f = 0.9f + 5.5f * a * a;
        float p = 0.5f + 0.5f * (float) Math.sin(time * 6.2831853 * f);
        return 0.25f + 0.75f * p;
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
