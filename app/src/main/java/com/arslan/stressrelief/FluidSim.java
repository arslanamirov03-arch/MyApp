package com.arslan.stressrelief;

import android.content.Context;
import android.opengl.GLES30;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

/**
 * The GPU fluid solver: a 2D Navier-Stokes step driving a combusting gas, plus
 * the ember particles that ride the velocity field.
 *
 * One frame is: curl -> advect velocity + forces -> divergence -> pressure
 * Jacobi xN -> project -> MacCormack transport of the gas + chemistry -> embers.
 *
 * Both the campfire and the bomb are the same solver with different numbers in
 * the parameter block below; only the shading differs between them.
 */
final class FluidSim {

    static final int PARTICLE_DIM = 64;
    static final int PARTICLE_COUNT = PARTICLE_DIM * PARTICLE_DIM;

    // --- forces -------------------------------------------------------------
    float vorticity = 24f;
    float buoyancy = 800f;
    float sootWeight = 90f;
    float damping = 1.5f;
    float noiseAmp = 1500f;
    float noiseScale = 5f;

    // --- chemistry ----------------------------------------------------------
    float tempDiss = 2f;
    float fuelDiss = 0.8f;
    float sootDiss = 1f;
    float cooling = 3f;
    float burnRate = 3f;
    float heatRelease = 1.4f;
    float sootYield = 0.4f;
    float ignition = 0.08f;

    // --- injection ----------------------------------------------------------
    boolean injecting;
    float touchX = 0.5f, touchY = 0.3f;
    float touchVX, touchVY;
    float touchRadius = 0.05f;
    float bedFlat = 1f;
    float injectFuel = 3f;
    float injectHeat = 1.5f;

    // --- blast --------------------------------------------------------------
    float blast;
    float blastX = 0.5f, blastY = 0.3f;
    float blastRadius = 0.2f;
    float blastHeat;
    float blastHeatRadius = 0.2f;

    // --- embers -------------------------------------------------------------
    boolean embers = true;
    float spawnRadius = 0.1f;
    float spawnRate;
    float intensity;

    int iterations = 26;

    private final Prog pCurl, pVel, pDiv, pPres, pProj, pAdvect, pFields, pPartUpd;
    private final boolean floatTargets;
    private final SceneRig rig;

    private PingPong velocity, pressure, fields, particles;
    private RenderTarget curl, divergence, fieldsA, fieldsB;
    private int simW, simH;

    FluidSim(Context ctx, String defines, boolean floatTargets, SceneRig rig) {
        this.floatTargets = floatTargets;
        this.rig = rig;
        String vs = "shaders/fullscreen.vert";
        pCurl = new Prog(ctx, vs, "shaders/curl.frag", defines);
        pVel = new Prog(ctx, vs, "shaders/velocity.frag", defines);
        pDiv = new Prog(ctx, vs, "shaders/divergence.frag", defines);
        pPres = new Prog(ctx, vs, "shaders/pressure.frag", defines);
        pProj = new Prog(ctx, vs, "shaders/project.frag", defines);
        pAdvect = new Prog(ctx, vs, "shaders/advect.frag", defines);
        pFields = new Prog(ctx, vs, "shaders/fields.frag", defines);
        pPartUpd = new Prog(ctx, vs, "shaders/particles_update.frag", defines);
    }

    void resize(int simW, int simH, int format) {
        release();
        this.simW = simW;
        this.simH = simH;
        int wrap = GLES30.GL_CLAMP_TO_EDGE;
        velocity = new PingPong(simW, simH, format, GLES30.GL_LINEAR, wrap);
        pressure = new PingPong(simW, simH, format, GLES30.GL_LINEAR, wrap);
        fields = new PingPong(simW, simH, format, GLES30.GL_LINEAR, wrap);
        curl = new RenderTarget(simW, simH, format, GLES30.GL_LINEAR, wrap);
        divergence = new RenderTarget(simW, simH, format, GLES30.GL_LINEAR, wrap);
        fieldsA = new RenderTarget(simW, simH, format, GLES30.GL_LINEAR, wrap);
        fieldsB = new RenderTarget(simW, simH, format, GLES30.GL_LINEAR, wrap);
        particles = new PingPong(PARTICLE_DIM, PARTICLE_DIM, format, GLES30.GL_NEAREST, wrap);
    }

    boolean complete() {
        return velocity != null && velocity.complete();
    }

    int fieldsTex() {
        return fields.read().tex;
    }

    int velocityTex() {
        return velocity.read().tex;
    }

    int particleTex() {
        return particles.read().tex;
    }

    /** Zero in encoded space: mid grey for the 8 bit fallback, black otherwise. */
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

    void reset() {
        clearSigned(velocity);
        clearSigned(pressure);
        clearSigned(curl);
        clearSigned(divergence);
        fields.clear(0f, 0f, 0f, 0f);
        fieldsA.clear(0f, 0f, 0f, 0f);
        fieldsB.clear(0f, 0f, 0f, 0f);
        seedParticles();
    }

    /** Every ember starts dead, with its own random seed in the alpha channel. */
    private void seedParticles() {
        Random rnd = new Random(7);
        if (floatTargets) {
            FloatBuffer fb = ByteBuffer.allocateDirect(PARTICLE_COUNT * 4 * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            for (int i = 0; i < PARTICLE_COUNT; i++) {
                fb.put(0f).put(0f).put(0f).put(rnd.nextFloat());
            }
            fb.position(0);
            uploadParticles(GLES30.GL_FLOAT, fb);
        } else {
            ByteBuffer bb = ByteBuffer.allocateDirect(PARTICLE_COUNT * 4)
                    .order(ByteOrder.nativeOrder());
            for (int i = 0; i < PARTICLE_COUNT; i++) {
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

    void step(float dt, float time, float aspectX) {
        float tx = 1f / simW, ty = 1f / simH;
        float on = injecting ? 1f : 0f;

        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);

        // ---- curl ----------------------------------------------------------
        pCurl.use().f2("uTexel", tx, ty).tex("uVelocity", 0, velocity.read().tex);
        curl.bindDraw();
        blit();

        // ---- velocity: advection plus every body force -----------------------
        Prog p = pVel.use();
        p.tex("uVelocity", 0, velocity.read().tex);
        p.tex("uCurl", 1, curl.tex);
        p.tex("uFields", 2, fields.read().tex);
        p.tex("uNoise", 3, rig.noiseTex);
        p.f2("uTexel", tx, ty).f2("uAspect", aspectX, 1f);
        p.f("uDt", dt).f("uTime", time);
        p.f("uVorticity", vorticity);
        p.f("uBuoyancy", buoyancy);
        p.f("uSootWeight", sootWeight);
        p.f("uDamping", damping);
        p.f("uNoiseAmp", noiseAmp);
        p.f("uNoiseScale", noiseScale);
        p.f2("uTouch", touchX, touchY);
        p.f2("uTouchVel", touchVX, touchVY);
        p.f("uTouchRadius", touchRadius);
        p.f("uTouchOn", on);
        p.f("uBlast", blast);
        p.f2("uBlastPos", blastX, blastY);
        p.f("uBlastRadius", blastRadius);
        velocity.write().bindDraw();
        blit();
        velocity.swap();

        // ---- divergence ------------------------------------------------------
        pDiv.use().f2("uTexel", tx, ty).tex("uVelocity", 0, velocity.read().tex);
        divergence.bindDraw();
        blit();

        // ---- pressure Jacobi -------------------------------------------------
        clearSigned(pressure.read());
        p = pPres.use().f2("uTexel", tx, ty);
        for (int i = 0; i < iterations; i++) {
            p.tex("uPressure", 0, pressure.read().tex);
            p.tex("uDivergence", 1, divergence.tex);
            pressure.write().bindDraw();
            blit();
            pressure.swap();
        }

        // ---- project ---------------------------------------------------------
        p = pProj.use().f2("uTexel", tx, ty);
        p.tex("uPressure", 0, pressure.read().tex);
        p.tex("uVelocity", 1, velocity.read().tex);
        velocity.write().bindDraw();
        blit();
        velocity.swap();

        // ---- forward and backward advection for the MacCormack correction ----
        p = pAdvect.use().f2("uTexel", tx, ty);
        p.tex("uVelocity", 0, velocity.read().tex);
        p.tex("uSource", 1, fields.read().tex);
        p.f("uDt", dt);
        fieldsA.bindDraw();
        blit();

        p.tex("uVelocity", 0, velocity.read().tex);
        p.tex("uSource", 1, fieldsA.tex);
        p.f("uDt", -dt);
        fieldsB.bindDraw();
        blit();

        // ---- transport + chemistry -------------------------------------------
        p = pFields.use();
        p.tex("uVelocity", 0, velocity.read().tex);
        p.tex("uFields", 1, fields.read().tex);
        p.tex("uNoise", 2, rig.noiseTex);
        p.tex("uPhiHat", 4, fieldsA.tex);
        p.tex("uPhiTilde", 5, fieldsB.tex);
        p.f2("uTexel", tx, ty).f2("uAspect", aspectX, 1f);
        p.f("uDt", dt).f("uTime", time);
        p.f("uTempDiss", tempDiss);
        p.f("uFuelDiss", fuelDiss);
        p.f("uSootDiss", sootDiss);
        p.f("uCooling", cooling);
        p.f("uBurnRate", burnRate);
        p.f("uHeatRelease", heatRelease);
        p.f("uSootYield", sootYield);
        p.f("uIgnition", ignition);
        p.f2("uTouch", touchX, touchY);
        p.f("uTouchOn", on);
        p.f("uTouchRadius", touchRadius);
        p.f("uBedFlat", bedFlat);
        p.f("uInjectFuel", injectFuel);
        p.f("uInjectHeat", injectHeat);
        p.f("uBlastHeat", blastHeat);
        p.f2("uBlastPos", blastX, blastY);
        p.f("uBlastRadius", blastHeatRadius);
        fields.write().bindDraw();
        blit();
        fields.swap();

        // ---- embers ----------------------------------------------------------
        if (embers) {
            p = pPartUpd.use();
            p.tex("uState", 0, particles.read().tex);
            p.tex("uVelocity", 1, velocity.read().tex);
            p.tex("uFields", 2, fields.read().tex);
            p.f2("uTexel", tx, ty).f2("uAspect", aspectX, 1f);
            p.f("uDt", dt).f("uTime", time);
            p.f2("uSpawn", touchX, touchY);
            p.f("uSpawnRadius", spawnRadius);
            p.f("uSpawnRate", spawnRate);
            p.f("uIntensity", intensity);
            particles.write().bindDraw();
            blit();
            particles.swap();
        }
    }

    void release() {
        if (velocity != null) velocity.release();
        if (pressure != null) pressure.release();
        if (fields != null) fields.release();
        if (particles != null) particles.release();
        if (curl != null) curl.release();
        if (divergence != null) divergence.release();
        if (fieldsA != null) fieldsA.release();
        if (fieldsB != null) fieldsB.release();
        velocity = null;
        pressure = null;
        fields = null;
        particles = null;
        curl = null;
        divergence = null;
        fieldsA = null;
        fieldsB = null;
    }
}
