package com.arslan.stressrelief;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * The campfire. FluidSim does the physics; this maps the interaction state onto
 * its parameters and shades the result.
 *
 * Constants here are the ones tuned in tools/preview.py; keep the two in sync.
 */
final class FireRenderer implements GLSurfaceView.Renderer, SceneRig.Mode {

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

    private final SceneRig rig = new SceneRig();
    private FluidSim fluid;
    private Prog pRender, pPartDraw;
    private final PostFx.Params comp = new PostFx.Params();

    private float time;
    private float frameMs = 16.6f;
    private long lastNanos;
    private float idleSeconds;
    private int iterations = 26;

    FireRenderer(Context ctx) {
        this.ctx = ctx;
    }

    void setListener(Listener l) {
        this.listener = l;
    }

    void onTouch(boolean down, boolean up, float x, float y, float vx, float vy) {
        synchronized (touchLock) {
            if (down) {
                pendingDown = true;
                touching = true;
            }
            if (up) touching = false;
            touchX = x;
            touchY = y;
            touchVX = vx;
            touchVY = vy;
        }
    }

    // ------------------------------------------------------------- lifecycle
    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        rig.create(ctx, this);
        lastNanos = System.nanoTime();
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        rig.resize(width, height);
        lastNanos = System.nanoTime();
    }

    @Override
    public void buildPrograms(String defines) {
        pRender = new Prog(ctx, "shaders/fullscreen.vert", "shaders/render_fire.frag", defines);
        pPartDraw = new Prog(ctx, "shaders/particles.vert", "shaders/particles.frag", defines);
        fluid = new FluidSim(ctx, defines, rig.floatTargets, rig);
    }

    @Override
    public boolean resizeTargets(SceneRig r) {
        fluid.resize(r.simW, r.simH, r.format());
        fluid.embers = r.floatTargets;
        return fluid.complete();
    }

    @Override
    public void releaseTargets() {
        fluid.release();
    }

    @Override
    public void reset() {
        fluid.reset();
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // ------------------------------------------------------------------ frame
    @Override
    public void onDrawFrame(GL10 unused) {
        if (rig.failed) {
            rig.clearScreen();
            return;
        }
        if (!rig.ready) return;

        long now = System.nanoTime();
        float dt = (now - lastNanos) / 1e9f;
        lastNanos = now;
        frameMs = mix(frameMs, dt * 1000f, 0.05f);
        dt = clamp(dt, 1f / 240f, 1f / 30f);
        time += dt;

        if (frameMs > 24f && iterations > 12) {
            iterations--;
        } else if (frameMs < 15f && iterations < 26) {
            iterations++;
        }

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
        if (state.resetRequested) {
            reset();
            rig.post.clear();
        }
        if (listener != null) {
            listener.onIntensity(state.intensity, state.intensity > 0.003f || state.exploding);
        }

        float q = state.intensity;
        float s = state.strike01();
        boolean inject = state.injecting(isTouching);
        float expl = state.exploding ? state.et : -1f;
        float k = expl < 0f ? 0f : clamp((expl - 0.18f) / 0.35f, 0f, 1f);

        boolean active = isTouching || state.exploding || q > 0.0005f || state.strike > 0f;
        idleSeconds = active ? 0f : idleSeconds + dt;
        if (idleSeconds > 3.0f) {
            rig.clearScreen();
            return;
        }

        GLES30.glBindVertexArray(rig.vao);

        // ---- physics ---------------------------------------------------------
        fluid.iterations = iterations;
        fluid.vorticity = mix(20f, 36f, q);
        fluid.buoyancy = mix(700f, 900f, q);
        fluid.sootWeight = 90f;
        fluid.damping = mix(2.60f, 1.10f, q);
        fluid.noiseAmp = mix(900f, 3200f, q);
        fluid.noiseScale = mix(8.0f, 2.6f, q);

        fluid.tempDiss = expl < 0f ? mix(5.50f, 1.30f, q) : mix(1.40f, 4.50f, k);
        fluid.fuelDiss = mix(1.20f, 0.50f, q);
        fluid.sootDiss = expl < 0f ? mix(2.00f, 0.45f, q) : mix(0.25f, 1.70f, k);
        fluid.cooling = expl < 0f ? mix(2.20f, 5.00f, q) : mix(6.00f, 12.00f, k);
        fluid.burnRate = expl < 0f ? 3.0f : 6.0f;
        fluid.heatRelease = expl < 0f ? mix(1.60f, 1.25f, q) : 1.0f;
        fluid.sootYield = expl < 0f ? mix(0.04f, 0.75f, (float) Math.pow(q, 1.4)) : 1.60f;
        fluid.ignition = 0.08f;

        fluid.injecting = inject;
        fluid.touchX = tx;
        fluid.touchY = ty;
        fluid.touchVX = tvx;
        fluid.touchVY = tvy;
        fluid.touchRadius = mix(0.028f, 0.30f, (float) Math.pow(q, 1.8)) * (1f + 1.1f * s);
        fluid.bedFlat = mix(1.0f, 2.4f, q);
        fluid.injectFuel = mix(2.8f, 3.8f, q) * (1f + 3.0f * s);
        fluid.injectHeat = mix(1.4f, 1.8f, q) * (1f + 3.5f * s);

        fluid.blast = state.blast();
        fluid.blastX = state.blastX;
        fluid.blastY = state.blastY;
        fluid.blastRadius = state.blastRadius();
        fluid.blastHeat = state.blastHeat();
        fluid.blastHeatRadius = state.blastHeatRadius();

        fluid.spawnRadius = mix(0.02f, 0.26f, q);
        fluid.spawnRate = inject || state.exploding ? mix(0.015f, 0.50f, q) : 0f;
        fluid.intensity = q;

        fluid.step(dt, time, rig.aspectX);

        // ---- shading ---------------------------------------------------------
        Prog p = pRender.use();
        p.tex("uFields", 0, fluid.fieldsTex());
        p.tex("uNoise", 1, rig.noiseTex);
        p.f2("uAspect", rig.aspectX, 1f).f("uTime", time);
        p.f("uDetail", expl < 0f ? mix(0.020f, 0.075f, q) : 0.085f);
        p.f("uEmissive", expl < 0f ? mix(3.4f, 5.2f, q) : mix(3.2f, 2.6f, k));
        p.f("uSmokeDensity", expl < 0f ? 3.4f : 5.0f);
        p.f("uIntensity", q);
        p.f3("uTint", 1.0f, 0.80f, 0.52f);
        p.f("uKelvinBase", 850f);
        p.f("uKelvinSpan", 1950f);
        p.f("uAniso", 0.30f);
        p.f("uSmokeGlow", 1.0f);
        p.f2("uTouch", tx, ty);
        p.f("uCoal", expl < 0f ? clamp((q - 0.03f) / 0.22f, 0f, 1f) : 0f);
        p.f("uCoalRadius", mix(0.030f, 0.26f, (float) Math.pow(q, 1.4)));
        p.f("uCoalFlat", 3.2f);
        rig.post.beginScene();
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);

        if (fluid.embers) {
            rig.drawParticles(pPartDraw, fluid.particleTex(),
                    mix(2.0f, 6.5f, q) * rig.sceneW / 300f, q);
        }

        rig.post.bloom(0.65f);

        float amp = state.shakeAmp();
        comp.time = time;
        comp.shakeX = (float) (Math.sin(time * 47.0) * 0.62 + Math.sin(time * 31.3 + 1.7) * 0.38) * amp;
        comp.shakeY = (float) (Math.sin(time * 53.7 + 2.3) * 0.62 + Math.sin(time * 37.1 + 0.9) * 0.38) * amp;
        comp.shakeRot = (float) Math.sin(time * 41.0 + 0.4) * amp * 0.55f;
        comp.zoom = state.zoom();
        comp.flash = state.flash();
        comp.flashR = 1.00f;
        comp.flashG = 0.94f;
        comp.flashB = 0.84f;
        comp.intensity = q;
        comp.bloomAmount = mix(0.55f, 1.00f, q);
        comp.exposure = mix(1.10f, 1.18f, q);
        comp.vignette = 0.55f;
        comp.chroma = 0.0010f + 0.0055f * q + 0.020f * comp.flash;
        comp.danger = 0f;
        comp.shockT = state.shock();
        comp.shockX = state.blastX;
        comp.shockY = state.blastY;
        rig.post.composite(rig.screenW, rig.screenH, rig.aspectX, rig.noiseTex, comp);
    }
}
