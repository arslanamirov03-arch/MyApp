package com.arslan.stressrelief;

/** Two identical render targets that swap roles every step. */
final class PingPong {

    private RenderTarget a;
    private RenderTarget b;

    PingPong(int w, int h, int internalFormat, int filter, int wrap) {
        a = new RenderTarget(w, h, internalFormat, filter, wrap);
        b = new RenderTarget(w, h, internalFormat, filter, wrap);
    }

    RenderTarget read() {
        return a;
    }

    RenderTarget write() {
        return b;
    }

    void swap() {
        RenderTarget t = a;
        a = b;
        b = t;
    }

    boolean complete() {
        return a.complete() && b.complete();
    }

    void clear(float r, float g, float bb, float al) {
        a.clear(r, g, bb, al);
        b.clear(r, g, bb, al);
    }

    void release() {
        a.release();
        b.release();
    }
}
