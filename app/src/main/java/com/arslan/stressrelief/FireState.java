package com.arslan.stressrelief;

/**
 * The interaction state machine: idle -> a match strike -> a fire that grows for
 * as long as the finger stays down -> explosion -> black again.
 *
 * Kept free of any GL so the same numbers can be replayed by tools/preview.py.
 */
final class FireState {

    static final float RAMP = 9.0f;      // hold time to a full-screen fire
    static final float DECAY = 2.5f;     // fade-out after letting go
    static final float STRIKE = 0.28f;   // match-strike burst length
    static final float EXPL_LEN = 3.2f;  // explosion sequence, then reset

    float intensity;
    float strike;
    boolean exploding;
    float et;                            // seconds since detonation
    float blastX = 0.5f, blastY = 0.3f;
    boolean resetRequested;
    boolean justExploded;

    void touchDown() {
        if (!exploding) {
            strike = STRIKE;
        }
    }

    void update(float dt, boolean touching, float tx, float ty) {
        resetRequested = false;
        justExploded = false;

        if (exploding) {
            et += dt;
            if (et >= 0.25f) {
                intensity = Math.max(0.0f, intensity - dt / 1.5f);
            }
            if (et >= EXPL_LEN) {
                exploding = false;
                et = 0.0f;
                intensity = 0.0f;
                strike = 0.0f;
                resetRequested = true;
            }
            return;
        }

        strike = Math.max(0.0f, strike - dt);

        if (touching) {
            intensity = Math.min(1.0f, intensity + dt / RAMP);
            if (intensity >= 1.0f) {
                exploding = true;
                justExploded = true;
                et = 0.0f;
                blastX = tx;
                blastY = ty;
            }
        } else {
            intensity = Math.max(0.0f, intensity - dt / DECAY);
        }
    }

    /** Injection is kept alive for the whole strike so a quick tap still lights. */
    boolean injecting(boolean touching) {
        return !exploding && (touching || strike > 0.0f);
    }

    float strike01() {
        return STRIKE > 0.0f ? strike / STRIKE : 0.0f;
    }

    float blast() {
        if (!exploding || et >= 0.30f) return 0.0f;
        return 42000.0f * (float) Math.exp(-et / 0.045f);
    }

    float blastRadius() {
        return 0.03f + et * 1.30f;
    }

    float blastHeat() {
        if (!exploding || et >= 0.35f) return 0.0f;
        return 60.0f * (float) Math.exp(-et / 0.055f);
    }

    float blastHeatRadius() {
        return 0.05f + et * 0.85f;
    }

    float flash() {
        if (!exploding) return 0.0f;
        return 1.8f * (float) Math.exp(-et / 0.10f);
    }

    float shock() {
        if (!exploding || et > 0.85f) return -1.0f;
        return et / 0.85f;
    }

    float shakeAmp() {
        float q = intensity;
        float base = 0.0075f * q * q * q;
        if (exploding) {
            base += 0.055f * (float) Math.exp(-et / 0.32f);
        }
        return base;
    }

    float zoom() {
        float z = 0.02f * intensity * intensity;
        if (exploding) {
            z += 0.05f * (float) Math.exp(-et / 0.30f);
        }
        return z;
    }
}
