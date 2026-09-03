package com.arslan.stressrelief;

import java.util.Random;

/**
 * One lightning discharge: a fractal channel plus branches, expanded into
 * camera-facing quads, and an envelope that reproduces the return strokes a
 * real flash is made of.
 *
 * Geometry is built in pixels (y up) and emitted in normalised device
 * coordinates. Mirrored by tools/storm_preview.py.
 */
final class Bolt {

    static final int STYLE_STREAK = 0;   // fast and nearly straight
    static final int STYLE_TREE = 1;     // heavily branched
    static final int STYLE_FORK = 2;     // splits into two main channels
    static final int STYLE_STAIR = 3;    // few, long, sharply angled steps
    static final int STYLE_RIBBON = 4;   // twin parallel channels
    static final int STYLE_CRAWLER = 5;  // wanders sideways before coming down
    static final int STYLE_COUNT = 6;

    private static final int MAIN_LEVELS = 6;                 // 64 segments
    private static final int MAX_POINTS = (1 << MAIN_LEVELS) + 1;
    private static final int MAX_SEGMENTS = 460;
    private static final int FLOATS_PER_VERTEX = 4;
    private static final int VERTS_PER_SEGMENT = 6;

    final float[] verts = new float[MAX_SEGMENTS * VERTS_PER_SEGMENT * FLOATS_PER_VERTEX];
    int vertexCount;

    /** Strike point in uv, used to light the clouds and place the audio. */
    float x, y;
    float age, life;
    float power = 1f;
    int style;
    boolean alive;
    boolean dirty;

    private final float[] px = new float[MAX_POINTS];
    private final float[] py = new float[MAX_POINTS];
    private final float[] bx = new float[MAX_POINTS];
    private final float[] by = new float[MAX_POINTS];
    private final float[] nx = new float[MAX_POINTS];
    private final float[] ny = new float[MAX_POINTS];

    private final float[] strokeT = new float[5];
    private final float[] strokeA = new float[5];
    private int strokes;
    private int nextStroke;
    private float flickerPhase;

    private int screenW, screenH;
    private float densityScale = 1f;
    private Random rnd;
    private float srcX, srcY;   // where the channel entered frame, in pixels

    void spawn(Random r, float targetX, float targetY, int screenW, int screenH,
               float densityScale, float power) {
        this.rnd = r;
        this.screenW = screenW;
        this.screenH = screenH;
        this.densityScale = densityScale;
        this.power = power;
        this.x = targetX;
        this.y = targetY;
        this.style = r.nextInt(STYLE_COUNT);
        this.age = 0f;
        this.alive = true;
        this.flickerPhase = r.nextFloat() * 6.283f;

        strokes = 1 + r.nextInt(4);
        float t = 0f;
        for (int i = 0; i < strokes; i++) {
            strokeT[i] = t;
            strokeA[i] = i == 0 ? 1f : 0.42f + 0.58f * r.nextFloat();
            t += 0.035f + r.nextFloat() * 0.115f;
        }
        life = t + 0.30f;
        nextStroke = 1;

        float spread = style == STYLE_CRAWLER ? 0.85f : 0.34f;
        srcX = targetX * screenW + (r.nextFloat() * 2f - 1f) * screenW * spread;
        srcY = screenH * 1.06f;

        build();
    }

    /** Advances the envelope; returns false once the flash is over. */
    boolean update(float dt) {
        age += dt;
        if (nextStroke < strokes && age >= strokeT[nextStroke]) {
            // a real return stroke re-illuminates a slightly different channel
            nextStroke++;
            if (rnd.nextFloat() < 0.6f) build();
        }
        if (age >= life) alive = false;
        return alive;
    }

    float brightness() {
        float b = 0f;
        for (int i = 0; i < strokes; i++) {
            float d = age - strokeT[i];
            if (d >= 0f) {
                float v = strokeA[i] * (float) Math.exp(-d / 0.045f);
                if (v > b) b = v;
            }
        }
        // the ionised channel keeps glowing faintly between strokes
        float residual = 0.11f * (float) Math.exp(-age / 0.17f);
        if (residual > b) b = residual;
        b *= 0.82f + 0.18f * (float) Math.sin(age * 190.0 + flickerPhase);

        float tail = 1f;
        if (age > life - 0.10f) tail = Math.max(0f, (life - age) / 0.10f);
        return Math.max(b, 0f) * tail * power;
    }

    // ------------------------------------------------------------ geometry
    private void build() {
        vertexCount = 0;
        dirty = true;

        float jag;
        int levels = MAIN_LEVELS;
        float coreW;
        float branchProb;
        int branchDepth;

        switch (style) {
            case STYLE_TREE:
                jag = 0.24f; coreW = 2.6f; branchProb = 0.20f; branchDepth = 2;
                break;
            case STYLE_FORK:
                jag = 0.18f; coreW = 2.8f; branchProb = 0.08f; branchDepth = 1;
                break;
            case STYLE_STAIR:
                jag = 0.44f; levels = 4; coreW = 3.1f; branchProb = 0.10f; branchDepth = 1;
                break;
            case STYLE_RIBBON:
                jag = 0.16f; coreW = 2.2f; branchProb = 0.07f; branchDepth = 1;
                break;
            case STYLE_CRAWLER:
                jag = 0.20f; coreW = 2.4f; branchProb = 0.14f; branchDepth = 1;
                break;
            case STYLE_STREAK:
            default:
                jag = 0.13f; coreW = 2.9f; branchProb = 0.035f; branchDepth = 1;
                break;
        }
        // A channel thinner than a couple of pixels aliases into a dotted line,
        // so the width has a floor regardless of how small the target is.
        coreW = Math.max(coreW * densityScale, 1.8f);

        px[0] = srcX;
        py[0] = srcY;
        px[1] = x * screenW;
        py[1] = y * screenH;
        int n = subdivide(px, py, 2, jag, levels);

        emit(px, py, n, coreW, 1f, 0.25f);

        if (style == STYLE_RIBBON) {
            // a second channel a few pixels away, dimmer: reads as a ribbon flash
            float off = (6f + rnd.nextFloat() * 8f) * densityScale;
            for (int i = 0; i < n; i++) {
                bx[i] = px[i] + off;
                by[i] = py[i] + off * 0.25f;
            }
            emit(bx, by, n, coreW * 0.72f, 0.55f, 0.3f);
        }

        if (style == STYLE_FORK) {
            // one branch nearly as strong as the main channel
            int at = n / 3 + rnd.nextInt(Math.max(n / 3, 1));
            spawnBranch(at, n, coreW * 0.85f, 0.8f, 0.55f, 1);
        }

        for (int i = 4; i < n - 3; i++) {
            if (rnd.nextFloat() < branchProb) {
                spawnBranch(i, n, coreW * 0.55f, 0.58f, 0.45f, branchDepth);
            }
        }
    }

    private void spawnBranch(int at, int n, float width, float bright, float lenFrac,
                             int depth) {
        if (vertexCount >= verts.length - VERTS_PER_SEGMENT * FLOATS_PER_VERTEX * 40) return;

        float ax = px[at], ay = py[at];
        int ref = Math.min(at + 2, n - 1);
        float dx = px[ref] - ax, dy = py[ref] - ay;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-3f) return;
        dx /= len;
        dy /= len;

        double ang = Math.toRadians(22.0 + rnd.nextFloat() * 42.0) * (rnd.nextBoolean() ? 1 : -1);
        float ca = (float) Math.cos(ang), sa = (float) Math.sin(ang);
        float rx = dx * ca - dy * sa;
        float ry = dx * sa + dy * ca;

        float remaining = (float) Math.hypot(px[n - 1] - ax, py[n - 1] - ay);
        float bl = Math.max(remaining * lenFrac * (0.4f + rnd.nextFloat() * 0.8f),
                40f * densityScale);

        bx[0] = ax;
        by[0] = ay;
        bx[1] = ax + rx * bl;
        by[1] = ay + ry * bl;
        int bn = subdivide(bx, by, 2, 0.26f, 4);
        emit(bx, by, bn, width, bright, 0.85f);

        if (depth > 1 && rnd.nextFloat() < 0.45f) {
            // sub-branches need their own scratch, so copy this one out first
            int at2 = bn / 2 + rnd.nextInt(Math.max(bn / 3, 1));
            float sx = bx[at2], sy = by[at2];
            float sdx = bx[Math.min(at2 + 1, bn - 1)] - sx;
            float sdy = by[Math.min(at2 + 1, bn - 1)] - sy;
            float sl = (float) Math.hypot(sdx, sdy);
            if (sl > 1e-3f) {
                double a2 = Math.toRadians(25.0 + rnd.nextFloat() * 45.0)
                        * (rnd.nextBoolean() ? 1 : -1);
                float c2 = (float) Math.cos(a2), s2 = (float) Math.sin(a2);
                float r2x = (sdx / sl) * c2 - (sdy / sl) * s2;
                float r2y = (sdx / sl) * s2 + (sdy / sl) * c2;
                float l2 = bl * (0.25f + rnd.nextFloat() * 0.35f);
                bx[0] = sx;
                by[0] = sy;
                bx[1] = sx + r2x * l2;
                by[1] = sy + r2y * l2;
                int n2 = subdivide(bx, by, 2, 0.30f, 3);
                emit(bx, by, n2, width * 0.6f, bright * 0.6f, 0.9f);
            }
        }
    }

    /** Midpoint displacement: the roughness halves with each level. */
    private int subdivide(float[] xs, float[] ys, int n, float jag, int levels) {
        for (int l = 0; l < levels; l++) {
            int m = n * 2 - 1;
            if (m > MAX_POINTS) break;
            for (int i = n - 1; i >= 0; i--) {
                xs[i * 2] = xs[i];
                ys[i * 2] = ys[i];
            }
            for (int i = 0; i < n - 1; i++) {
                int a = i * 2, b = i * 2 + 2;
                float dx = xs[b] - xs[a], dy = ys[b] - ys[a];
                float len = (float) Math.hypot(dx, dy) + 1e-5f;
                float nx = -dy / len, ny = dx / len;
                float off = len * jag * (rnd.nextFloat() * 2f - 1f);
                xs[a + 1] = (xs[a] + xs[b]) * 0.5f + nx * off;
                ys[a + 1] = (ys[a] + ys[b]) * 0.5f + ny * off;
            }
            n = m;
            jag *= 0.62f;
        }
        return n;
    }

    /**
     * Expands a polyline into quads. The quad is much wider than the visible
     * channel; bolt.frag draws a thin core inside a soft halo across it.
     *
     * Corners use a mitred normal shared by both neighbouring quads. Overlapping
     * them instead would double up under additive blending and leave a string of
     * bright beads down every channel.
     */
    private void emit(float[] xs, float[] ys, int n, float coreW, float bright, float taper) {
        if (n < 2) return;
        float half = coreW * 4.0f;

        for (int i = 0; i < n; i++) {
            int a = Math.max(i - 1, 0);
            int b = Math.min(i, n - 2);
            float ax = xs[a + 1] - xs[a], ay = ys[a + 1] - ys[a];
            float la = (float) Math.hypot(ax, ay) + 1e-5f;
            float cx = xs[b + 1] - xs[b], cy = ys[b + 1] - ys[b];
            float lc = (float) Math.hypot(cx, cy) + 1e-5f;

            float p1x = -ay / la, p1y = ax / la;
            float p2x = -cy / lc, p2y = cx / lc;
            float mx = p1x + p2x, my = p1y + p2y;
            float ml = (float) Math.hypot(mx, my);
            if (ml < 1e-4f) {
                mx = p2x;
                my = p2y;
            } else {
                mx /= ml;
                my /= ml;
            }
            // a hairpin turn would send the mitre to infinity, so cap it
            float scale = half / Math.max(mx * p2x + my * p2y, 0.35f);
            nx[i] = mx * scale;
            ny[i] = my * scale;
        }

        float sx = 2f / screenW, sy = 2f / screenH;
        for (int i = 0; i < n - 1; i++) {
            if (vertexCount + VERTS_PER_SEGMENT * FLOATS_PER_VERTEX > verts.length) return;

            float t0 = i / (float) (n - 1);
            float t1 = (i + 1) / (float) (n - 1);
            float b0 = bright * (1f - taper * t0);
            float b1 = bright * (1f - taper * t1);

            float ax = xs[i], ay = ys[i], cx = xs[i + 1], cy = ys[i + 1];

            put(ax + nx[i], ay + ny[i], 1f, b0, sx, sy);
            put(ax - nx[i], ay - ny[i], -1f, b0, sx, sy);
            put(cx + nx[i + 1], cy + ny[i + 1], 1f, b1, sx, sy);

            put(cx + nx[i + 1], cy + ny[i + 1], 1f, b1, sx, sy);
            put(ax - nx[i], ay - ny[i], -1f, b0, sx, sy);
            put(cx - nx[i + 1], cy - ny[i + 1], -1f, b1, sx, sy);
        }
    }

    private void put(float x, float y, float side, float bright, float sx, float sy) {
        verts[vertexCount++] = x * sx - 1f;
        verts[vertexCount++] = y * sy - 1f;
        verts[vertexCount++] = side;
        verts[vertexCount++] = bright;
    }

    int floatCount() {
        return vertexCount;
    }

    int vertices() {
        return vertexCount / FLOATS_PER_VERTEX;
    }
}
