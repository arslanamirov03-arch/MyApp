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
 * A 2D Navier-Stokes fluid solver driving a combusting gas, shaded through a
 * blackbody ramp with bloom.  Every frame runs, on the GPU:
 *
 *   curl -> advect velocity + forces -> divergence -> pressure Jacobi xN ->
 *   project -> advect + burn the gas -> embers -> shade -> bloom -> composite
 *
 * Constants here are the ones tuned in tools/preview.py; keep the two in sync.
 */
final class FireRenderer implements GLSurfaceView.Renderer {

    interface Listener {
        /** Called on the GL thread when the fire detonates. */
        void onExplosion();

        /** Called every frame with the current 0..1 fire intensity. */
        void onIntensity(float intensity, boolean burning);

        /** Called on the GL thread when a touch strikes a new flame. */
        void onStrike();
    }

    private final Context ctx;
    private final FireState state = new FireState();
    private Listener listener;

    // --- input, written from the UI thread --------------------------------
    private final Object touchLock = new Object();
    private boolean touching;
    private boolean pendingDown;
    private float touchX = 0.5f, touchY = 0.3f;
    private float touchVX, touchVY;

    // --- gl objects --------------------------------------------------------
    private Prog pCurl, pVel, pDiv, pPres, pProj, pFields, pRender;
    private Prog pPartUpd, pPartDraw, pPre, pDown, pUp, pComp;

    private PingPong velocity, pressure, fields, particles;
    private RenderTarget curl, divergence, scene;
    private RenderTarget[] mips;
    private int noiseTex;
    private int vao;

    private boolean floatTargets = true;
    private boolean failed;
    private boolean forceLowPrecision;
    private boolean embers = true;
    private boolean ready;
    private float idleSeconds;

    private int screenW = 1, screenH = 1;
    private int simW = 1, simH = 1;
    private int sceneW = 1, sceneH = 1;
    private float aspectX = 0.5f;

    private static final int PARTICLE_DIM = 64;
    private static final int PARTICLE_COUNT = PARTICLE_DIM * PARTICLE_DIM;
    private static final int MIP_LEVELS = 5;

    private int iterations = 26;
    private long lastNanos;
    private float time;
    private float frameMs = 16.6f;

    FireRenderer(Context ctx) {
        this.ctx = ctx;
    }

    void setListener(Listener l) {
        this.listener = l;
    }

    // ---------------------------------------------------------------- input
    void onTouch(boolean down, boolean up, float x, float y, float vx, float vy) {
        synchronized (touchLock) {
            if (down) {
                pendingDown = true;
                touching = true;
            }
            if (up) {
                touching = false;
            }
            touchX = x;
            touchY = y;
            touchVX = vx;
            touchVY = vy;
        }
    }

    // ------------------------------------------------------------- lifecycle
    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        try {
            createGl();
        } catch (RuntimeException e) {
            Log.e(GLUtil.TAG, "GL setup failed", e);
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
        Log.i(GLUtil.TAG, "float render targets: " + floatTargets);

        String defines = "#define LOWPREC " + (floatTargets ? 0 : 1) + "\n";
        String vsFull = "shaders/fullscreen.vert";
        pCurl = new Prog(ctx, vsFull, "shaders/curl.frag", defines);
        pVel = new Prog(ctx, vsFull, "shaders/velocity.frag", defines);
        pDiv = new Prog(ctx, vsFull, "shaders/divergence.frag", defines);
        pPres = new Prog(ctx, vsFull, "shaders/pressure.frag", defines);
        pProj = new Prog(ctx, vsFull, "shaders/project.frag", defines);
        pFields = new Prog(ctx, vsFull, "shaders/fields.frag", defines);
        pRender = new Prog(ctx, vsFull, "shaders/render_fire.frag", defines);
        pPartUpd = new Prog(ctx, vsFull, "shaders/particles_update.frag", defines);
        pPartDraw = new Prog(ctx, "shaders/particles.vert", "shaders/particles.frag", defines);
        pPre = new Prog(ctx, vsFull, "shaders/bloom_prefilter.frag", defines);
        pDown = new Prog(ctx, vsFull, "shaders/bloom_down.frag", defines);
        pUp = new Prog(ctx, vsFull, "shaders/bloom_up.frag", defines);
        pComp = new Prog(ctx, vsFull, "shaders/composite.frag", defines);

        int[] v = new int[1];
        GLES30.glGenVertexArrays(1, v, 0);
        vao = v[0];
        GLES30.glBindVertexArray(vao);

        noiseTex = loadNoise();
        embers = floatTargets;
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
            Log.e(GLUtil.TAG, "GL resize failed", e);
            failed = true;
        }
    }

    private void resize(int width, int height) {
        screenW = Math.max(width, 1);
        screenH = Math.max(height, 1);
        aspectX = screenW / (float) screenH;

        simW = clamp(Math.round(screenW / 5.0f), 128, 240);
        simH = Math.min(Math.round(simW * screenH / (float) screenW), 560);

        float scale = clamp(1080.0f / screenW, 0.55f, 1.0f);
        sceneW = Math.max(Math.round(screenW * scale), 16);
        sceneH = Math.max(Math.round(screenH * scale), 16);

        releaseTargets();

        int fmt = floatTargets ? GLES30.GL_RGBA16F : GLES30.GL_RGBA8;
        int clampWrap = GLES30.GL_CLAMP_TO_EDGE;

        velocity = new PingPong(simW, simH, fmt, GLES30.GL_LINEAR, clampWrap);
        pressure = new PingPong(simW, simH, fmt, GLES30.GL_LINEAR, clampWrap);
        fields = new PingPong(simW, simH, fmt, GLES30.GL_LINEAR, clampWrap);
        curl = new RenderTarget(simW, simH, fmt, GLES30.GL_LINEAR, clampWrap);
        divergence = new RenderTarget(simW, simH, fmt, GLES30.GL_LINEAR, clampWrap);
        particles = new PingPong(PARTICLE_DIM, PARTICLE_DIM, fmt, GLES30.GL_NEAREST, clampWrap);
        scene = new RenderTarget(sceneW, sceneH, fmt, GLES30.GL_LINEAR, clampWrap);

        if (floatTargets && !velocity.complete()) {
            // The extension string lied; fall back and rebuild everything as RGBA8.
            Log.w(GLUtil.TAG, "half-float FBO incomplete, rebuilding at 8 bit");
            forceLowPrecision = true;
            floatTargets = false;
            embers = false;
            releaseTargets();
            createGl();
            resize(width, height);
            return;
        }

        mips = new RenderTarget[MIP_LEVELS];
        int mw = Math.max(sceneW / 2, 2);
        int mh = Math.max(sceneH / 2, 2);
        for (int i = 0; i < MIP_LEVELS; i++) {
            mips[i] = new RenderTarget(mw, mh, fmt, GLES30.GL_LINEAR, clampWrap);
            mw = Math.max(mw / 2, 2);
            mh = Math.max(mh / 2, 2);
        }

        resetSimulation();
        ready = true;
        lastNanos = System.nanoTime();
    }

    private void releaseTargets() {
        if (velocity != null) velocity.release();
        if (pressure != null) pressure.release();
        if (fields != null) fields.release();
        if (particles != null) particles.release();
        if (curl != null) curl.release();
        if (divergence != null) divergence.release();
        if (scene != null) scene.release();
        if (mips != null) {
            for (RenderTarget m : mips) {
                if (m != null) m.release();
            }
        }
        mips = null;
        velocity = null;
        pressure = null;
        fields = null;
        particles = null;
        curl = null;
        divergence = null;
        scene = null;
        ready = false;
    }

    /** Zero in encoded space: 0.5 grey for the 8 bit fallback, black otherwise. */
    private void clearSigned(PingPong p) {
        if (floatTargets) {
            p.clear(0f, 0f, 0f, 0f);
        } else {
            p.clear(0.5f, 0.5f, 0.5f, 1f);
        }
    }

    private void clearSigned(RenderTarget t) {
        if (floatTargets) {
            t.clear(0f, 0f, 0f, 0f);
        } else {
            t.clear(0.5f, 0.5f, 0.5f, 1f);
        }
    }

    private void resetSimulation() {
        clearSigned(velocity);
        clearSigned(pressure);
        clearSigned(curl);
        clearSigned(divergence);
        fields.clear(0f, 0f, 0f, 0f);
        scene.clear(0f, 0f, 0f, 1f);
        if (mips != null) {
            for (RenderTarget m : mips) m.clear(0f, 0f, 0f, 1f);
        }
        seedParticles();
    }

    /** Every ember starts dead, with its own random seed in the alpha channel. */
    private void seedParticles() {
        int n = PARTICLE_COUNT;
        Random rnd = new Random(7);
        if (floatTargets) {
            FloatBuffer fb = ByteBuffer.allocateDirect(n * 4 * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            for (int i = 0; i < n; i++) {
                fb.put(0f).put(0f).put(0f).put(rnd.nextFloat());
            }
            fb.position(0);
            uploadParticles(GLES30.GL_FLOAT, fb);
        } else {
            ByteBuffer bb = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder());
            for (int i = 0; i < n; i++) {
                bb.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) rnd.nextInt(256));
            }
            bb.position(0);
            uploadParticles(GLES30.GL_UNSIGNED_BYTE, bb);
        }
    }

    private void uploadParticles(int type, java.nio.Buffer data) {
        for (int i = 0; i < 2; i++) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, particles.read().tex);
            GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0,
                    PARTICLE_DIM, PARTICLE_DIM, GLES30.GL_RGBA, type, data);
            data.position(0);
            particles.swap();
        }
    }

    private static void blit() {
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // ------------------------------------------------------------------ frame
    @Override
    public void onDrawFrame(GL10 unused) {
        if (failed) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
            GLES30.glViewport(0, 0, screenW, screenH);
            GLES30.glClearColor(0f, 0f, 0f, 1f);
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
            return;
        }
        if (!ready) return;

        long now = System.nanoTime();
        float dt = (now - lastNanos) / 1e9f;
        lastNanos = now;
        frameMs = mix(frameMs, dt * 1000f, 0.05f);
        dt = clamp(dt, 1f / 240f, 1f / 30f);
        time += dt;

        adaptQuality();

        boolean down;
        boolean isTouching;
        float tx, ty, tvx, tvy;
        synchronized (touchLock) {
            down = pendingDown;
            pendingDown = false;
            isTouching = touching;
            tx = touchX;
            ty = touchY;
            tvx = touchVX;
            tvy = touchVY;
            touchVX = 0f;
            touchVY = 0f;
        }
        if (down && !state.exploding) {
            state.touchDown();
            if (listener != null) listener.onStrike();
        }

        state.update(dt, isTouching, tx, ty);
        if (state.justExploded && listener != null) listener.onExplosion();
        if (state.resetRequested) resetSimulation();
        if (listener != null) {
            listener.onIntensity(state.intensity, state.intensity > 0.003f || state.exploding);
        }

        float q = state.intensity;
        float s = state.strike01();
        boolean inject = state.injecting(isTouching);
        float expl = state.exploding ? state.et : -1f;
        float k = expl < 0f ? 0f : clamp((expl - 0.18f) / 0.35f, 0f, 1f);

        // Nothing left burning: stop solving and just hold a black screen.
        boolean active = isTouching || state.exploding || state.intensity > 0.0005f
                || state.strike > 0f;
        idleSeconds = active ? 0f : idleSeconds + dt;
        if (idleSeconds > 3.0f) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
            GLES30.glViewport(0, 0, screenW, screenH);
            GLES30.glClearColor(0f, 0f, 0f, 1f);
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
            return;
        }

        float texelX = 1f / simW, texelY = 1f / simH;

        GLES30.glBindVertexArray(vao);
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);

        // ---- curl -----------------------------------------------------------
        pCurl.use().f2("uTexel", texelX, texelY).tex("uVelocity", 0, velocity.read().tex);
        curl.bindDraw();
        blit();

        // ---- velocity: advection + all body forces --------------------------
        Prog p = pVel.use();
        p.tex("uVelocity", 0, velocity.read().tex);
        p.tex("uCurl", 1, curl.tex);
        p.tex("uFields", 2, fields.read().tex);
        p.tex("uNoise", 3, noiseTex);
        p.f2("uTexel", texelX, texelY).f2("uAspect", aspectX, 1f);
        p.f("uDt", dt).f("uTime", time);
        p.f("uVorticity", mix(20f, 36f, q));
        p.f("uBuoyancy", mix(700f, 900f, q));
        p.f("uSootWeight", 90f);
        p.f("uDamping", mix(2.60f, 1.10f, q));
        p.f("uNoiseAmp", mix(900f, 3200f, q));
        p.f("uNoiseScale", mix(8.0f, 2.6f, q));
        p.f2("uTouch", tx, ty);
        p.f2("uTouchVel", tvx, tvy);
        p.f("uTouchRadius", touchRadius(q, s));
        p.f("uTouchOn", inject ? 1f : 0f);
        p.f("uBlast", state.blast());
        p.f2("uBlastPos", state.blastX, state.blastY);
        p.f("uBlastRadius", state.blastRadius());
        velocity.write().bindDraw();
        blit();
        velocity.swap();

        // ---- divergence ------------------------------------------------------
        pDiv.use().f2("uTexel", texelX, texelY).tex("uVelocity", 0, velocity.read().tex);
        divergence.bindDraw();
        blit();

        // ---- pressure Jacobi -------------------------------------------------
        clearSigned(pressure.read());
        p = pPres.use().f2("uTexel", texelX, texelY);
        for (int i = 0; i < iterations; i++) {
            p.tex("uPressure", 0, pressure.read().tex);
            p.tex("uDivergence", 1, divergence.tex);
            pressure.write().bindDraw();
            blit();
            pressure.swap();
        }

        // ---- project ---------------------------------------------------------
        p = pProj.use().f2("uTexel", texelX, texelY);
        p.tex("uPressure", 0, pressure.read().tex);
        p.tex("uVelocity", 1, velocity.read().tex);
        velocity.write().bindDraw();
        blit();
        velocity.swap();

        // ---- gas transport + combustion --------------------------------------
        p = pFields.use();
        p.tex("uVelocity", 0, velocity.read().tex);
        p.tex("uFields", 1, fields.read().tex);
        p.tex("uNoise", 2, noiseTex);
        p.f2("uTexel", texelX, texelY).f2("uAspect", aspectX, 1f);
        p.f("uDt", dt).f("uTime", time);
        p.f("uTempDiss", expl < 0f ? mix(5.50f, 1.30f, q) : mix(1.40f, 4.50f, k));
        p.f("uFuelDiss", mix(1.20f, 0.50f, q));
        p.f("uSootDiss", expl < 0f ? mix(2.00f, 0.45f, q) : mix(0.25f, 1.70f, k));
        p.f("uCooling", expl < 0f ? mix(2.20f, 5.00f, q) : mix(6.00f, 12.00f, k));
        p.f("uBurnRate", expl < 0f ? 3.0f : 6.0f);
        p.f("uHeatRelease", expl < 0f ? mix(1.60f, 1.25f, q) : 1.0f);
        p.f("uSootYield", expl < 0f ? mix(0.04f, 0.75f, (float) Math.pow(q, 1.4)) : 1.60f);
        p.f("uIgnition", 0.08f);
        p.f2("uTouch", tx, ty);
        p.f("uTouchOn", inject ? 1f : 0f);
        p.f("uTouchRadius", touchRadius(q, s));
        p.f("uInjectFuel", mix(2.6f, 3.2f, q) * (1f + 3.0f * s));
        p.f("uInjectHeat", mix(1.4f, 1.8f, q) * (1f + 3.5f * s));
        p.f("uBlastHeat", state.blastHeat());
        p.f2("uBlastPos", state.blastX, state.blastY);
        p.f("uBlastRadius", state.blastHeatRadius());
        fields.write().bindDraw();
        blit();
        fields.swap();

        // ---- embers ----------------------------------------------------------
        if (embers) {
            p = pPartUpd.use();
            p.tex("uState", 0, particles.read().tex);
            p.tex("uVelocity", 1, velocity.read().tex);
            p.tex("uFields", 2, fields.read().tex);
            p.f2("uTexel", texelX, texelY).f2("uAspect", aspectX, 1f);
            p.f("uDt", dt).f("uTime", time);
            p.f2("uSpawn", tx, ty);
            p.f("uSpawnRadius", mix(0.02f, 0.26f, q));
            p.f("uSpawnRate", inject || state.exploding ? mix(0.015f, 0.50f, q) : 0f);
            p.f("uIntensity", q);
            particles.write().bindDraw();
            blit();
            particles.swap();
        }

        renderScene(q, k, expl);
        bloom(q);
        composite(q);
    }

    private float touchRadius(float q, float strike) {
        return mix(0.028f, 0.30f, (float) Math.pow(q, 1.8)) * (1f + 1.1f * strike);
    }

    private void renderScene(float q, float k, float expl) {
        Prog p = pRender.use();
        p.tex("uFields", 0, fields.read().tex);
        p.tex("uNoise", 1, noiseTex);
        p.f2("uAspect", aspectX, 1f).f("uTime", time);
        p.f("uDetail", expl < 0f ? mix(0.020f, 0.075f, q) : 0.085f);
        p.f("uEmissive", expl < 0f ? mix(2.6f, 4.0f, q) : mix(2.6f, 2.2f, k));
        p.f("uSmokeDensity", expl < 0f ? 3.4f : 5.0f);
        p.f("uIntensity", q);
        scene.bindDraw();
        blit();

        if (!embers) return;
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE);
        p = pPartDraw.use();
        p.tex("uState", 0, particles.read().tex);
        p.i("uTexSize", PARTICLE_DIM);
        p.f("uPointScale", mix(2.0f, 6.5f, q) * sceneW / 300f);
        p.f("uIntensity", q);
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, PARTICLE_COUNT);
        GLES30.glDisable(GLES30.GL_BLEND);
    }

    private void bloom(float q) {
        Prog p = pPre.use();
        p.tex("uTex", 0, scene.tex).f("uThreshold", 0.65f).f("uKnee", 0.6f);
        mips[0].bindDraw();
        blit();

        p = pDown.use();
        for (int i = 1; i < MIP_LEVELS; i++) {
            RenderTarget src = mips[i - 1];
            p.tex("uTex", 0, src.tex).f2("uTexel", 1f / src.width, 1f / src.height);
            mips[i].bindDraw();
            blit();
        }

        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE);
        p = pUp.use().f("uRadius", 1.0f);
        for (int i = MIP_LEVELS - 1; i > 0; i--) {
            RenderTarget src = mips[i];
            p.tex("uTex", 0, src.tex).f2("uTexel", 1f / src.width, 1f / src.height);
            mips[i - 1].bindDraw();
            blit();
        }
        GLES30.glDisable(GLES30.GL_BLEND);
    }

    private void composite(float q) {
        float amp = state.shakeAmp();
        float ox = (float) (Math.sin(time * 47.0) * 0.62 + Math.sin(time * 31.3 + 1.7) * 0.38);
        float oy = (float) (Math.sin(time * 53.7 + 2.3) * 0.62 + Math.sin(time * 37.1 + 0.9) * 0.38);
        float rot = (float) Math.sin(time * 41.0 + 0.4) * amp * 0.55f;

        Prog p = pComp.use();
        p.tex("uScene", 0, scene.tex);
        p.tex("uBloom", 1, mips[0].tex);
        p.tex("uNoise", 2, noiseTex);
        p.f2("uResolution", screenW, screenH).f2("uAspect", aspectX, 1f);
        p.f("uTime", time);
        p.f2("uShakeOffset", ox * amp, oy * amp);
        p.f("uShakeRot", rot);
        p.f("uZoom", state.zoom());
        p.f("uFlash", state.flash());
        p.f("uIntensity", q);
        p.f("uBloomAmount", mix(0.55f, 1.00f, q));
        p.f("uExposure", mix(1.05f, 1.10f, q));
        p.f("uVignette", 0.55f);
        p.f("uShockT", state.shock());
        p.f2("uShockPos", state.blastX, state.blastY);

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(0, 0, screenW, screenH);
        blit();
    }

    /** Trades pressure accuracy for frame rate on slower GPUs. */
    private void adaptQuality() {
        if (frameMs > 24f && iterations > 12) {
            iterations--;
        } else if (frameMs < 15f && iterations < 26) {
            iterations++;
        }
    }
}
