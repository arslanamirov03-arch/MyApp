package com.arslan.stressrelief;

import android.content.Context;
import android.opengl.GLES30;

/**
 * The HDR scene target plus the bloom chain and final composite, shared by
 * every mode so shake, tonemapping and glow behave identically across them.
 */
final class PostFx {

    /** Everything the final pass needs; reused frame to frame to avoid churn. */
    static final class Params {
        float time;
        float shakeX, shakeY, shakeRot, zoom;
        float flash;
        float flashR = 1.00f, flashG = 0.94f, flashB = 0.84f;
        float intensity;
        float bloomAmount = 0.8f;
        float exposure = 1.1f;
        float vignette = 0.55f;
        float chroma = 0.0015f;
        float threshold = 0.65f;
        float shockT = -1f;
        float shockX = 0.5f, shockY = 0.5f;
    }

    private static final int MIP_LEVELS = 5;

    private final Prog pPre, pDown, pUp, pComp;

    RenderTarget scene;
    private RenderTarget[] mips;

    PostFx(Context ctx, String defines) {
        String vs = "shaders/fullscreen.vert";
        pPre = new Prog(ctx, vs, "shaders/bloom_prefilter.frag", defines);
        pDown = new Prog(ctx, vs, "shaders/bloom_down.frag", defines);
        pUp = new Prog(ctx, vs, "shaders/bloom_up.frag", defines);
        pComp = new Prog(ctx, vs, "shaders/composite.frag", defines);
    }

    void resize(int sceneW, int sceneH, int format) {
        release();
        int wrap = GLES30.GL_CLAMP_TO_EDGE;
        scene = new RenderTarget(sceneW, sceneH, format, GLES30.GL_LINEAR, wrap);
        mips = new RenderTarget[MIP_LEVELS];
        int w = Math.max(sceneW / 2, 2);
        int h = Math.max(sceneH / 2, 2);
        for (int i = 0; i < MIP_LEVELS; i++) {
            mips[i] = new RenderTarget(w, h, format, GLES30.GL_LINEAR, wrap);
            w = Math.max(w / 2, 2);
            h = Math.max(h / 2, 2);
        }
    }

    boolean complete() {
        return scene != null && scene.complete();
    }

    void clear() {
        if (scene == null) return;
        scene.clear(0f, 0f, 0f, 1f);
        for (RenderTarget m : mips) m.clear(0f, 0f, 0f, 1f);
    }

    /** Binds the HDR target so a mode can draw its scene into it. */
    void beginScene() {
        scene.bindDraw();
    }

    private static void blit() {
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3);
    }

    void bloom(float threshold) {
        Prog p = pPre.use();
        p.tex("uTex", 0, scene.tex).f("uThreshold", threshold).f("uKnee", 0.6f);
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

    void composite(int screenW, int screenH, float aspectX, int noiseTex, Params c) {
        Prog p = pComp.use();
        p.tex("uScene", 0, scene.tex);
        p.tex("uBloom", 1, mips[0].tex);
        p.tex("uNoise", 2, noiseTex);
        p.f2("uResolution", screenW, screenH).f2("uAspect", aspectX, 1f);
        p.f("uTime", c.time);
        p.f2("uShakeOffset", c.shakeX, c.shakeY);
        p.f("uShakeRot", c.shakeRot);
        p.f("uZoom", c.zoom);
        p.f("uFlash", c.flash);
        GLES30.glUniform3f(GLES30.glGetUniformLocation(pComp.id, "uFlashColor"),
                c.flashR, c.flashG, c.flashB);
        p.f("uIntensity", c.intensity);
        p.f("uBloomAmount", c.bloomAmount);
        p.f("uExposure", c.exposure);
        p.f("uVignette", c.vignette);
        p.f("uChroma", c.chroma);
        p.f("uShockT", c.shockT);
        p.f2("uShockPos", c.shockX, c.shockY);

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(0, 0, screenW, screenH);
        blit();
    }

    void release() {
        if (scene != null) {
            scene.release();
            scene = null;
        }
        if (mips != null) {
            for (RenderTarget m : mips) {
                if (m != null) m.release();
            }
            mips = null;
        }
    }
}
