package com.arslan.stressrelief;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

/**
 * Boilerplate every GL mode needs: capability detection, the noise texture, the
 * render sizes, the post chain, and the 8-bit fallback dance for GPUs that
 * cannot render to float targets.
 */
final class SceneRig {

    /** What a mode has to provide so the rig can build and rebuild it. */
    interface Mode {
        void buildPrograms(String defines);

        /** Create the mode's own targets; return false if any is incomplete. */
        boolean resizeTargets(SceneRig rig);

        void releaseTargets();

        void reset();
    }

    boolean floatTargets = true;
    boolean failed;
    boolean ready;

    int noiseTex;
    int vao;
    PostFx post;

    int screenW = 1, screenH = 1;
    int sceneW = 1, sceneH = 1;
    int simW = 1, simH = 1;
    float aspectX = 0.5f;

    private Context ctx;
    private Mode mode;
    private boolean forceLowPrecision;

    int format() {
        return floatTargets ? GLES30.GL_RGBA16F : GLES30.GL_RGBA8;
    }

    void create(Context ctx, Mode mode) {
        this.ctx = ctx;
        this.mode = mode;
        try {
            build();
        } catch (RuntimeException e) {
            Log.e(GLUtil.TAG, "GL setup failed", e);
            failed = true;
        }
    }

    private void build() {
        String ext = GLES30.glGetString(GLES30.GL_EXTENSIONS);
        if (ext == null) ext = "";
        String ver = GLES30.glGetString(GLES30.GL_VERSION);
        floatTargets = !forceLowPrecision
                && (ext.contains("GL_EXT_color_buffer_half_float")
                    || ext.contains("GL_EXT_color_buffer_float")
                    || (ver != null && ver.contains("ES 3.2")));
        Log.i(GLUtil.TAG, "float render targets: " + floatTargets);

        String defines = "#define LOWPREC " + (floatTargets ? 0 : 1) + "\n";
        post = new PostFx(ctx, defines);
        mode.buildPrograms(defines);

        int[] v = new int[1];
        GLES30.glGenVertexArrays(1, v, 0);
        vao = v[0];
        GLES30.glBindVertexArray(vao);
        noiseTex = loadNoise();
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

    void resize(int width, int height) {
        if (failed) {
            screenW = Math.max(width, 1);
            screenH = Math.max(height, 1);
            return;
        }
        try {
            doResize(width, height);
        } catch (RuntimeException e) {
            Log.e(GLUtil.TAG, "GL resize failed", e);
            failed = true;
        }
    }

    private void doResize(int width, int height) {
        screenW = Math.max(width, 1);
        screenH = Math.max(height, 1);
        aspectX = screenW / (float) screenH;

        simW = clamp(Math.round(screenW / 5.0f), 128, 240);
        simH = Math.min(Math.round(simW * screenH / (float) screenW), 560);

        float scale = clamp(1080.0f / screenW, 0.55f, 1.0f);
        sceneW = Math.max(Math.round(screenW * scale), 16);
        sceneH = Math.max(Math.round(screenH * scale), 16);

        post.resize(sceneW, sceneH, format());
        boolean ok = mode.resizeTargets(this) && post.complete();

        if (floatTargets && !ok) {
            // The extension string lied; rebuild the whole pipeline at 8 bit.
            Log.w(GLUtil.TAG, "half-float FBO incomplete, rebuilding at 8 bit");
            mode.releaseTargets();
            post.release();
            forceLowPrecision = true;
            build();
            doResize(width, height);
            return;
        }

        post.clear();
        mode.reset();
        ready = true;
    }

    void clearScreen() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(0, 0, screenW, screenH);
        GLES30.glClearColor(0f, 0f, 0f, 1f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
    }

    /** Additive point sprites straight out of the particle state texture. */
    void drawParticles(Prog prog, int stateTex, float pointScale, float intensity) {
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE);
        Prog p = prog.use();
        p.tex("uState", 0, stateTex);
        p.i("uTexSize", FluidSim.PARTICLE_DIM);
        p.f("uPointScale", pointScale);
        p.f("uIntensity", intensity);
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, FluidSim.PARTICLE_COUNT);
        GLES30.glDisable(GLES30.GL_BLEND);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
