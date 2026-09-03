package com.arslan.stressrelief;

/**
 * Storm escalation. Holding builds the strike rate and spread until the whole
 * screen is being hit; letting go stops the bolts fairly quickly but leaves the
 * storm rolling away for several seconds.
 */
final class StormState {

    static final float RAMP = 9.0f;      // hold time to a full-screen storm
    static final float FALL = 1.10f;     // strikes stop this quickly
    static final float AFTER = 6.50f;    // ...but the noise takes this long to go

    float intensity;
    float afterglow;
    float strikeTimer;
    float jolt;          // decaying shake impulse from the last strike
    float flash;         // decaying sky flash, separate from any single bolt
    float farTimer = 1.6f;

    void update(float dt, boolean touching) {
        if (touching) {
            intensity = Math.min(1f, intensity + dt / RAMP);
            afterglow = 1f;
        } else {
            intensity = Math.max(0f, intensity - dt / FALL);
            afterglow = Math.max(0f, afterglow - dt / AFTER);
        }
        jolt = Math.max(0f, jolt - dt / 0.42f);
        flash = Math.max(0f, flash - dt * 3.4f);
        strikeTimer -= dt;
        farTimer -= dt;
    }

    /** Seconds between strikes; drops off fast so the climax feels frantic. */
    float strikeInterval() {
        return mix(0.85f, 0.045f, (float) Math.pow(intensity, 1.30));
    }

    /** How far from the finger bolts may land, in uv. */
    float spread() {
        return mix(0.015f, 0.62f, (float) Math.pow(intensity, 1.50));
    }

    /** How many bolts a single strike event fires at once. */
    int burst() {
        if (intensity > 0.90f) return 3;
        if (intensity > 0.72f) return 2;
        return 1;
    }

    void registerStrike(float power) {
        jolt = Math.min(1f, jolt + power * 0.85f);
        flash = Math.min(1.6f, flash + power * 0.55f);
    }

    float shakeAmp() {
        float q = intensity;
        return 0.0035f * q * q + 0.020f * jolt * (0.35f + 0.65f * q);
    }

    /** Sky brightness with no bolt in frame: the storm's own glow. */
    float ambient() {
        return 0.015f + 0.24f * intensity + 0.07f * afterglow * (1f - intensity);
    }

    float cloudBase() {
        return mix(0.80f, 0.58f, intensity);
    }

    /** True when a distant flash should light the clouds as the storm recedes. */
    boolean wantFarFlash() {
        if (afterglow <= 0.02f || intensity > 0.05f || farTimer > 0f) return false;
        // they get rarer as the storm moves off
        farTimer = 0.8f + (1f - afterglow) * 3.2f;
        return true;
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
