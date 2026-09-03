package com.arslan.stressrelief;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * The lightning mode. A cloud deck lit from inside by live discharges, with the
 * bolts themselves drawn as additive quads and pushed through the shared bloom
 * and composite chain.
 */
final class StormRenderer implements GLSurfaceView.Renderer {

    interface Listener {
        /** A discharge just fired. power 0..1, q is the current storm intensity. */
        void onStrike(float power, float q);

        void onState(float intensity, float afterglow);
    }

    private static final int MAX_BOLTS = 16;
    private static final int LIGHTS = 6;

    private final Context ctx;
    private final StormState state = new StormState();
    private final Random rnd = new Random();
    private final Bolt[] bolts = new Bolt[MAX_BOLTS];
    private final int[] boltFirst = new int[MAX_BOLTS];
    private final int[] boltCount = new int[MAX_BOLTS];
    private final float[] lights = new float[LIGHTS * 3];

    private Listener listener;

    private final Object touchLock = new Object();
    private boolean touching;
    private boolean pendingDown;
    private float touchX = 0.5f, touchY = 0.35f;

    private Prog pSky, pBolt;
    private PostFx post;
    private final PostFx.Params comp = new PostFx.Params();

    private int noiseTex;
    private int plainVao, boltVao, boltVbo;
    private FloatBuffer boltData;
    private int boltCapacityFloats;
    private boolean geometryDirty;

    private boolean floatTargets = true;
    private boolean forceLowPrecision;
    private boolean failed;
    private boolean ready;

    private int screenW = 1, screenH = 1;
    private int sceneW = 1, sceneH = 1;
    private float aspectX = 0.5f;
    private float densityScale = 1f;

    private long lastNanos;
    private float time;
    private float idleSeconds;

    StormRenderer(Context ctx) {
        this.ctx = ctx;
        for (int i = 0; i < MAX_BOLTS; i++) bolts[i] = new Bolt();
    }

    void setListener(Listener l) {
        this.listener = l;
    }

    void onTouch(boolean down, boolean up, float x, float y) {
        synchronized (touchLock) {
            if (down) {
                pendingDown = true;
                touching = true;
            }
            if (up) touching = false;
            touchX = x;
            touchY = y;
        }
    }

    // ------------------------------------------------------------- lifecycle
    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        try {
            createGl();
        } catch (RuntimeException e) {
            Log.e(GLUtil.TAG, "storm GL setup failed", e);
            failed = true;
        }
    }

    private void createGl() {
        String ext = GLES30.glGetString(GLES30.GL_EXTENSIONS);
        if (ext == null) ext = "";
        String ver = GLES30.glGetString(GLES30.GL_VERSION);
        floatTargets = !forceLowPrecision
                && (ext.contains("GL_EXT_color_buffer_half_float")
                    || ext.contains("GL_EXT_color_buffer_float")
                    || (ver != null && ver.contains("ES 3.2")));

        String defines = "#define LOWPREC " + (floatTargets ? 0 : 1) + "\n";
        pSky = new Prog(ctx, "shaders/fullscreen.vert", "shaders/storm_sky.frag", defines);
        pBolt = new Prog(ctx, "shaders/bolt.vert", "shaders/bolt.frag", defines);
        post = new PostFx(ctx, defines);

        int[] v = new int[2];
        GLES30.glGenVertexArrays(2, v, 0);
        plainVao = v[0];
        boltVao = v[1];

        int[] b = new int[1];
        GLES30.glGenBuffers(1, b, 0);
        boltVbo = b[0];

        GLES30.glBindVertexArray(boltVao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, boltVbo);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, 0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, 8);
        GLES30.glBindVertexArray(plainVao);

        noiseTex = loadNoise();
        lastNanos = System.nanoTime();
    }

    private int loadNoise() {
        Bitmap bmp;
        try (InputStream in = ctx.getAssets().open("noise.png")) {
            bmp = BitmapFactory.decodeStream(in);
        } catch (IOException e) {
            throw new RuntimeException("noise.png missing", e);
        }
        int[] t = new int[1];
        GLES30.glGenTextures(1, t, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t[0]);
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT);
        bmp.recycle();
        return t[0];
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        if (failed) {
            screenW = Math.max(width, 1);
            screenH = Math.max(height, 1);
            return;
        }
        try {
            resize(width, height);
        } catch (RuntimeException e) {
            Log.e(GLUtil.TAG, "storm resize failed", e);
            failed = true;
        }
    }

    private void resize(int width, int height) {
        screenW = Math.max(width, 1);
        screenH = Math.max(height, 1);
        aspectX = screenW / (float) screenH;

        float scale = clamp(1080.0f / screenW, 0.55f, 1.0f);
        sceneW = Math.max(Math.round(screenW * scale), 16);
        sceneH = Math.max(Math.round(screenH * scale), 16);
        densityScale = Math.max(sceneW / 1080f, 0.55f);

        int fmt = floatTargets ? GLES30.GL_RGBA16F : GLES30.GL_RGBA8;
        post.resize(sceneW, sceneH, fmt);
        if (floatTargets && !post.complete()) {
            forceLowPrecision = true;
            floatTargets = false;
            post.release();
            createGl();
            resize(width, height);
            return;
        }
        post.clear();

        for (Bolt b : bolts) b.alive = false;
        geometryDirty = true;
        ready = true;
        lastNanos = System.nanoTime();
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    // ----------------------------------------------------------------- frame
    @Override
    public void onDrawFrame(GL10 unused) {
        if (failed) {
            clearScreen();
            return;
        }
        if (!ready) return;

        long now = System.nanoTime();
        float dt = (now - lastNanos) / 1e9f;
        lastNanos = now;
        dt = clamp(dt, 1f / 240f, 1f / 30f);
        time += dt;

        boolean down;
        boolean isTouching;
        float tx, ty;
        synchronized (touchLock) {
            down = pendingDown;
            pendingDown = false;
            isTouching = touching;
            tx = touchX;
            ty = touchY;
        }

        state.update(dt, isTouching);

        // a tap is one bolt, straight away
        if (down) {
            fire(tx, ty, 0f, 1f);
            state.strikeTimer = state.strikeInterval();
        }

        if (isTouching && state.strikeTimer <= 0f) {
            int n = state.burst();
            for (int i = 0; i < n; i++) {
                float sp = state.spread();
                float bx = clamp(tx + (rnd.nextFloat() * 2f - 1f) * sp, 0.04f, 0.96f);
                float by = clamp(ty + (rnd.nextFloat() * 2f - 1f) * sp * 0.75f, 0.03f, 0.88f);
                fire(bx, by, state.intensity, 0.75f + 0.25f * rnd.nextFloat());
            }
            state.strikeTimer = state.strikeInterval();
        }

        // the storm keeps flickering on the horizon after you let go
        if (state.wantFarFlash()) {
            state.flash = Math.min(1.2f, state.flash + 0.28f * state.afterglow);
            if (listener != null) listener.onStrike(-1f, state.intensity);
        }

        int live = 0;
        float peak = 0f;
        for (Bolt b : bolts) {
            if (!b.alive) continue;
            if (!b.update(dt)) {
                geometryDirty = true;
                continue;
            }
            if (b.dirty) geometryDirty = true;
            live++;
            peak = Math.max(peak, b.brightness());
        }

        if (listener != null) listener.onState(state.intensity, state.afterglow);

        boolean active = isTouching || live > 0 || state.afterglow > 0.01f;
        idleSeconds = active ? 0f : idleSeconds + dt;
        if (idleSeconds > 3.0f) {
            clearScreen();
            return;
        }

        if (geometryDirty) {
            uploadGeometry();
            geometryDirty = false;
        }

        collectLights();

        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDisable(GLES30.GL_BLEND);

        // ---- sky ---------------------------------------------------------
        GLES30.glBindVertexArray(plainVao);
        Prog p = pSky.use();
        p.tex("uNoise", 0, noiseTex);
        p.f2("uAspect", aspectX, 1f).f("uTime", time);
        p.f("uIntensity", state.intensity);
        p.f("uAmbient", state.ambient() + state.flash * 0.22f);
        p.f("uCloudBase", state.cloudBase());
        p.f3v("uLights", lights, LIGHTS);
        post.beginScene();
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);

        // ---- bolts -------------------------------------------------------
        if (live > 0) {
            GLES30.glEnable(GLES30.GL_BLEND);
            GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE);
            GLES30.glBindVertexArray(boltVao);
            p = pBolt.use();
            p.f("uCoreFrac", 0.28f);
            p.f3("uCoreColor", 0.88f, 0.94f, 1.00f);
            p.f3("uHaloColor", 0.24f, 0.46f, 1.00f);
            for (int i = 0; i < MAX_BOLTS; i++) {
                if (!bolts[i].alive || boltCount[i] == 0) continue;
                p.f("uBright", bolts[i].brightness());
                GLES30.glDrawArrays(GLES30.GL_TRIANGLES, boltFirst[i], boltCount[i]);
            }
            GLES30.glDisable(GLES30.GL_BLEND);
            GLES30.glBindVertexArray(plainVao);
        }

        // ---- post --------------------------------------------------------
        post.bloom(0.42f);

        float q = state.intensity;
        float amp = state.shakeAmp();
        comp.time = time;
        comp.shakeX = (float) (Math.sin(time * 51.0) * 0.6 + Math.sin(time * 33.7 + 1.1) * 0.4) * amp;
        comp.shakeY = (float) (Math.sin(time * 44.3 + 2.7) * 0.6 + Math.sin(time * 61.1 + 0.5) * 0.4) * amp;
        comp.shakeRot = (float) Math.sin(time * 39.0 + 1.9) * amp * 0.5f;
        comp.zoom = 0.012f * q * q + 0.02f * state.jolt;
        comp.flash = Math.min(0.42f, peak * 0.085f + state.flash * 0.10f);
        comp.flashR = 0.72f;
        comp.flashG = 0.83f;
        comp.flashB = 1.00f;
        comp.intensity = q;
        comp.bloomAmount = mix(0.85f, 1.25f, q);
        comp.exposure = mix(1.05f, 1.15f, q);
        comp.vignette = 0.45f;
        comp.chroma = 0.0005f + 0.0009f * q;
        comp.shockT = -1f;
        post.composite(screenW, screenH, aspectX, noiseTex, comp);
    }

    private void clearScreen() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(0, 0, screenW, screenH);
        GLES30.glClearColor(0f, 0f, 0f, 1f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
    }

    private void fire(float x, float y, float q, float power) {
        for (Bolt b : bolts) {
            if (b.alive) continue;
            b.spawn(rnd, x, y, sceneW, sceneH, densityScale, power);
            geometryDirty = true;
            state.registerStrike(power);
            if (listener != null) listener.onStrike(power, q);
            return;
        }
    }

    /** The six brightest bolts light the cloud deck. */
    private void collectLights() {
        for (int i = 0; i < LIGHTS * 3; i++) lights[i] = 0f;
        int slot = 0;
        for (Bolt b : bolts) {
            if (!b.alive) continue;
            float br = b.brightness();
            if (br < 0.02f) continue;
            if (slot < LIGHTS) {
                lights[slot * 3] = b.x;
                lights[slot * 3 + 1] = b.y;
                lights[slot * 3 + 2] = br;
                slot++;
            } else {
                // replace the dimmest slot if this one beats it
                int weakest = 0;
                for (int i = 1; i < LIGHTS; i++) {
                    if (lights[i * 3 + 2] < lights[weakest * 3 + 2]) weakest = i;
                }
                if (br > lights[weakest * 3 + 2]) {
                    lights[weakest * 3] = b.x;
                    lights[weakest * 3 + 1] = b.y;
                    lights[weakest * 3 + 2] = br;
                }
            }
        }
    }

    /** Bolt geometry only changes when one is born, dies or re-strikes. */
    private void uploadGeometry() {
        int total = 0;
        for (Bolt b : bolts) {
            if (b.alive) total += b.floatCount();
        }
        if (total == 0) {
            for (int i = 0; i < MAX_BOLTS; i++) boltCount[i] = 0;
            return;
        }
        if (boltData == null || boltCapacityFloats < total) {
            boltCapacityFloats = Math.max(total, 24576);
            boltData = ByteBuffer.allocateDirect(boltCapacityFloats * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
        }
        boltData.position(0);
        int vertexCursor = 0;
        for (int i = 0; i < MAX_BOLTS; i++) {
            Bolt b = bolts[i];
            if (!b.alive || b.floatCount() == 0) {
                boltFirst[i] = 0;
                boltCount[i] = 0;
                continue;
            }
            boltFirst[i] = vertexCursor;
            boltCount[i] = b.vertices();
            boltData.put(b.verts, 0, b.floatCount());
            vertexCursor += b.vertices();
            b.dirty = false;
        }
        boltData.position(0);

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, boltVbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, total * 4, boltData, GLES30.GL_STREAM_DRAW);
    }
}
