package com.abyss.silt.core;

/** Small math helpers used across the game. Pure Java, no Android deps. */
public final class V {
    private V() {}

    public static final float TAU = (float) (Math.PI * 2.0);

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static int clampI(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Frame-rate independent exponential smoothing. rate is "fraction remaining per second". */
    public static float damp(float a, float b, float rate, float dt) {
        return lerp(a, b, 1f - (float) Math.pow(rate, dt));
    }

    public static float len(float x, float y) {
        return (float) Math.sqrt(x * x + y * y);
    }

    public static float dist(float ax, float ay, float bx, float by) {
        return len(bx - ax, by - ay);
    }

    public static float dist2(float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay;
        return dx * dx + dy * dy;
    }

    /** Smoothstep between edges. */
    public static float smooth(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    public static float easeOut(float t) {
        float u = 1f - clamp(t, 0f, 1f);
        return 1f - u * u * u;
    }

    public static float easeIn(float t) {
        t = clamp(t, 0f, 1f);
        return t * t * t;
    }

    /** Shortest signed angular difference from a to b, in radians. */
    public static float angDelta(float a, float b) {
        float d = (b - a) % TAU;
        if (d > Math.PI) d -= TAU;
        if (d < -Math.PI) d += TAU;
        return d;
    }

    public static float angLerp(float a, float b, float t) {
        return a + angDelta(a, b) * t;
    }

    /** Rotate a towards b by at most maxStep radians. */
    public static float angApproach(float a, float b, float maxStep) {
        float d = angDelta(a, b);
        if (d > maxStep) d = maxStep;
        if (d < -maxStep) d = -maxStep;
        return a + d;
    }

    public static float sign(float v) {
        return v < 0 ? -1f : (v > 0 ? 1f : 0f);
    }

    /** Cheap deterministic value noise in 1D, output roughly in [-1,1]. */
    public static float noise1(float x, int seed) {
        int i = (int) Math.floor(x);
        float f = x - i;
        float a = hashF(i, seed);
        float b = hashF(i + 1, seed);
        float t = f * f * (3f - 2f * f);
        return lerp(a, b, t) * 2f - 1f;
    }

    public static float hashF(int n, int seed) {
        int h = n * 374761393 + seed * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h = h ^ (h >>> 16);
        return (h & 0x7fffffff) / (float) 0x7fffffff;
    }
}
