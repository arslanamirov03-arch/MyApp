package com.arslan.stressrelief;

import android.opengl.GLES30;

/** A texture with its own framebuffer. */
final class RenderTarget {

    final int tex;
    final int fbo;
    final int width;
    final int height;

    RenderTarget(int width, int height, int internalFormat, int filter, int wrap) {
        this.width = width;
        this.height = height;

        int[] t = new int[1];
        GLES30.glGenTextures(1, t, 0);
        tex = t[0];
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex);
        GLES30.glTexStorage2D(GLES30.GL_TEXTURE_2D, 1, internalFormat, width, height);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filter);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, filter);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, wrap);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, wrap);

        int[] f = new int[1];
        GLES30.glGenFramebuffers(1, f, 0);
        fbo = f[0];
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo);
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                GLES30.GL_TEXTURE_2D, tex, 0);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
    }

    boolean complete() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo);
        int s = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        return s == GLES30.GL_FRAMEBUFFER_COMPLETE;
    }

    void bindDraw() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo);
        GLES30.glViewport(0, 0, width, height);
    }

    void clear(float r, float g, float b, float a) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo);
        GLES30.glViewport(0, 0, width, height);
        GLES30.glClearColor(r, g, b, a);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
    }

    void release() {
        GLES30.glDeleteFramebuffers(1, new int[]{fbo}, 0);
        GLES30.glDeleteTextures(1, new int[]{tex}, 0);
    }
}
