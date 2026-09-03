package com.arslan.stressrelief;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Bomb mode. The same fluid solver as the campfire, driven by blast impulses
 * instead of a burning fuel bed, with a colour grade per charge type.
 *
 * The mushroom cloud is not authored: a compact, very hot, very buoyant blob
 * released low in a low-damping field with strong vorticity confinement rolls
 * itself into a cap and drags a stem behind it, which is what a real thermal
 * does. All this mode has to do is hold those parameters long enough.
 */
final class BombRenderer implements GLSurfaceView.Renderer, SceneRig.Mode {

    interface Listener {
        void onCharge(int type);

        void onNuke();

        void onState(float arm01, boolean arming, float nukeT, boolean busy);
    }

    // per type: tint rgb, kelvin base, kelvin span, soot yield, flash rgb
    private static final float[][] GRADE = {
            //  r     g     b     kBase  kSpan  soot  fr    fg    fb
            {1.00f, 0.76f, 0.42f,  900f, 2100f, 0.90f, 1.00f, 0.90f, 0.72f}, // HE
            {1.00f, 0.58f, 0.24f,  800f, 1700f, 1.70f, 1.00f, 0.72f, 0.45f}, // thermobaric
            {0.90f, 0.95f, 1.00f, 2600f, 3600f, 1.90f, 0.95f, 0.97f, 1.00f}, // phosphorus
            {1.00f, 0.70f, 0.38f,  850f, 1800f, 1.30f, 1.00f, 0.85f, 0.62f}, // heavy thud
            {0.40f, 0.68f, 1.00f, 3200f, 4800f, 0.40f, 0.55f, 0.78f, 1.00f}, // plasma
    };

    private final Context ctx;
    private final BombState state = new BombState();
    private Listener listener;

    private final Object touchLock = new Object();
    private boolean touching;
    private boolean pendingDown;
    private float touchX = 0.5f, touchY = 0.35f;

    private final SceneRig rig = new SceneRig();
    private FluidSim fluid;
    private Prog pRender, pPartDraw;
    private final PostFx.Params comp = new PostFx.Params();

    private float time;
    private long lastNanos;
    private float idleSeconds;
    private float blastX = 0.5f, blastY = 0.3f;

    BombRenderer(Context ctx) {
        this.ctx = ctx;
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

    // ----------------------------------------------------------------- frame
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

        if (down) {
            state.touchDown();
            if (state.justFired) {
                blastX = tx;
                // leave room above for a cloud to climb into
                blastY = Math.min(ty, 0.22f);
                if (listener != null) listener.onCharge(state.type);
            }
        }

        state.update(dt, isTouching);
        if (state.justNuked && listener != null) listener.onNuke();
        if (state.resetRequested) {
            reset();
            rig.post.clear();
        }

        boolean nuking = state.nuking();
        float arm = state.arm01();
        boolean busy = state.blastT >= 0f || nuking;
        if (listener != null) listener.onState(arm, state.arming, state.nukeT, busy);

        boolean active = isTouching || busy || state.armT > 0.01f;
        idleSeconds = active ? 0f : idleSeconds + dt;
        if (idleSeconds > 3.0f) {
            rig.clearScreen();
            return;
        }

        GLES30.glBindVertexArray(rig.vao);

        float[] g = GRADE[state.type];
        float mush = state.mushroom01();

        // ---- physics ---------------------------------------------------------
        // The stem of a mushroom cloud is material dragged up behind the
        // fireball, so for a couple of seconds after detonation a narrow column
        // is fed in at the base and the rising thermal pulls it along.
        boolean stem = nuking && state.nukeT > 0.45f && state.nukeT < 1.9f;
        fluid.injecting = stem;
        fluid.touchRadius = 0.032f;
        fluid.bedFlat = 0.30f;
        fluid.injectFuel = 0.40f;
        fluid.injectHeat = 0.25f;
        fluid.touchX = blastX;
        fluid.touchY = blastY;
        fluid.blastX = blastX;
        fluid.blastY = blastY;
        fluid.ignition = 0.06f;
        fluid.fuelDiss = 0.6f;
        fluid.heatRelease = 1.0f;
        fluid.burnRate = 6.0f;

        if (nuking) {
            float t = state.nukeT;
            // fireball first, then a buoyant thermal that builds its own cap
            fluid.vorticity = mix(26f, 55f, mush);
            // A cloud that climbs too fast never rolls: it leaves the frame
            // before the vortex ring has time to form. Buoyancy drops hard once
            // the fireball becomes a thermal.
            fluid.buoyancy = mix(300f, 120f, mush);
            fluid.sootWeight = mix(55f, 10f, mush);
            fluid.damping = mix(1.40f, 0.85f, mush);
            fluid.noiseAmp = mix(500f, 220f, mush);
            fluid.noiseScale = mix(3.0f, 2.2f, mush);
            fluid.tempDiss = mix(0.50f, 0.10f, mush);
            fluid.cooling = mix(2.00f, 0.20f, mush);
            fluid.sootDiss = mix(0.20f, 0.035f, mush);
            fluid.sootYield = 2.0f;
            fluid.blast = state.nukePush();
            fluid.blastRadius = state.nukePushRadius();
            fluid.blastHeat = state.nukeHeat();
            fluid.blastHeatRadius = state.nukeHeatRadius();
            fluid.spawnRadius = 0.30f;
            fluid.spawnRate = t < 1.4f ? 1.6f : 0.05f;
            fluid.intensity = clamp(1f - t / 18f, 0f, 1f);
        } else {
            float b = Math.max(state.blastT, 0f);
            float age = clamp((b - 0.15f) / 0.55f, 0f, 1f);
            fluid.vorticity = 34f;
            fluid.buoyancy = 620f;
            fluid.sootWeight = 55f;
            fluid.damping = 1.30f;
            fluid.noiseAmp = 2600f;
            fluid.noiseScale = 3.2f;
            fluid.tempDiss = mix(1.20f, 5.00f, age);
            fluid.cooling = mix(5.00f, 14.00f, age);
            fluid.sootDiss = mix(0.20f, 1.10f, clamp(b / 3.0f, 0f, 1f));
            fluid.sootYield = g[5];
            fluid.blast = state.push();
            fluid.blastRadius = state.pushRadius();
            fluid.blastHeat = state.heat();
            fluid.blastHeatRadius = state.heatRadius();
            fluid.spawnRadius = 0.14f;
            fluid.spawnRate = b < 0.4f && state.blastT >= 0f ? 1.2f : 0f;
            fluid.intensity = state.blastT >= 0f ? clamp(1f - b / 4f, 0f, 1f) : 0f;
        }

        fluid.step(dt, time, rig.aspectX);

        // ---- shading ---------------------------------------------------------
        float tintR = g[0], tintG = g[1], tintB = g[2];
        float kBase = g[3], kSpan = g[4];
        if (nuking) {
            // white hot at first, cooling into the orange of a settling cloud
            tintR = mix(1.00f, 1.00f, mush);
            tintG = mix(0.92f, 0.66f, mush);
            tintB = mix(0.78f, 0.34f, mush);
            kBase = mix(2400f, 800f, mush);
            kSpan = mix(3200f, 1700f, mush);
        }

        Prog p = pRender.use();
        p.tex("uFields", 0, fluid.fieldsTex());
        p.tex("uNoise", 1, rig.noiseTex);
        p.f2("uAspect", rig.aspectX, 1f).f("uTime", time);
        p.f("uDetail", nuking ? mix(0.085f, 0.055f, mush) : 0.080f);
        p.f("uEmissive", nuking ? mix(3.0f, 2.4f, mush) : 3.2f);
        p.f("uSmokeDensity", nuking ? mix(4.5f, 6.5f, mush) : 4.6f);
        p.f("uSmokeGlow", nuking ? mix(1.4f, 7.0f, mush) : 1.6f);
        p.f("uIntensity", fluid.intensity);
        p.f3("uTint", tintR, tintG, tintB);
        p.f("uKelvinBase", kBase);
        p.f("uKelvinSpan", kSpan);
        p.f("uAniso", nuking ? mix(0.90f, 0.72f, mush) : 0.85f);
        p.f2("uTouch", blastX, blastY);
        p.f("uCoal", 0f);
        p.f("uCoalRadius", 0.05f);
        p.f("uCoalFlat", 3f);
        rig.post.beginScene();
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);

        if (fluid.embers) {
            rig.drawParticles(pPartDraw, fluid.particleTex(),
                    (nuking ? 5.5f : 4.0f) * rig.sceneW / 300f, fluid.intensity);
        }

        rig.post.bloom(nuking ? 0.75f : 0.85f);

        // ---- composite -------------------------------------------------------
        float amp = state.shakeAmp();
        comp.time = time;
        comp.shakeX = (float) (Math.sin(time * 61.0) * 0.6 + Math.sin(time * 37.3 + 1.3) * 0.4) * amp;
        comp.shakeY = (float) (Math.sin(time * 49.7 + 2.1) * 0.6 + Math.sin(time * 71.1 + 0.7) * 0.4) * amp;
        comp.shakeRot = (float) Math.sin(time * 43.0 + 1.1) * amp * 0.5f;
        comp.zoom = state.zoom();
        comp.flash = state.flash();
        if (nuking) {
            comp.flashR = 1.00f;
            comp.flashG = 0.97f;
            comp.flashB = 0.90f;
        } else {
            comp.flashR = g[6];
            comp.flashG = g[7];
            comp.flashB = g[8];
        }
        comp.intensity = fluid.intensity;
        comp.bloomAmount = nuking ? 0.72f : 0.55f;
        comp.exposure = 1.12f;
        comp.vignette = 0.50f;
        comp.chroma = 0.0012f + 0.010f * Math.min(comp.flash, 1f);
        comp.danger = state.arming || state.armT > 0.01f ? arm : 0f;
        comp.dangerPulse = state.dangerPulse(time);
        comp.shockT = state.shock();
        comp.shockX = blastX;
        comp.shockY = blastY;
        rig.post.composite(rig.screenW, rig.screenH, rig.aspectX, rig.noiseTex, comp);
    }
}
