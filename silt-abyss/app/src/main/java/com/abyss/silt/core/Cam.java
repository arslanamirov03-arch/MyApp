package com.abyss.silt.core;

/** Smoothed follow camera with screen shake, mapping world units to screen pixels. */
public class Cam {
    public float x, y;          // world-space centre
    public float targetX, targetY;
    public float scale = 1f;    // pixels per world unit
    public float shake = 0f;    // decaying shake amplitude in world units
    public float shakeT = 0f;

    public int screenW, screenH;

    private float shakeOffX, shakeOffY;

    public void resize(int w, int h) {
        screenW = w;
        screenH = h;
        // Frame roughly 17 cells of 64 world units across the wider axis.
        float desiredWorldWidth = 17f * 64f;
        scale = w / desiredWorldWidth;
        // Never zoom so far out that the vertical view exceeds ~11 cells.
        float maxWorldHeight = 11.5f * 64f;
        if (h / scale > maxWorldHeight) scale = h / maxWorldHeight;
    }

    public void snapTo(float wx, float wy) {
        x = targetX = wx;
        y = targetY = wy;
    }

    public void follow(float wx, float wy, float dt) {
        targetX = wx;
        targetY = wy;
        x = V.damp(x, targetX, 0.0015f, dt);
        y = V.damp(y, targetY, 0.0015f, dt);
    }

    public void addShake(float amount) {
        shake = Math.max(shake, amount);
    }

    public void update(float dt) {
        shakeT += dt;
        if (shake > 0.01f) {
            shakeOffX = V.noise1(shakeT * 34f, 11) * shake;
            shakeOffY = V.noise1(shakeT * 31f, 27) * shake;
            shake = V.damp(shake, 0f, 0.0006f, dt);
        } else {
            shake = 0f;
            shakeOffX = shakeOffY = 0f;
        }
    }

    public float sx(float worldX) {
        return (worldX - x + shakeOffX) * scale + screenW * 0.5f;
    }

    public float sy(float worldY) {
        return (worldY - y + shakeOffY) * scale + screenH * 0.5f;
    }

    public float wx(float screenX) {
        return (screenX - screenW * 0.5f) / scale + x - shakeOffX;
    }

    public float wy(float screenY) {
        return (screenY - screenH * 0.5f) / scale + y - shakeOffY;
    }

    /** Half-width of the visible world region, plus a margin, for culling. */
    public float viewHalfW(float margin) {
        return screenW * 0.5f / scale + margin;
    }

    public float viewHalfH(float margin) {
        return screenH * 0.5f / scale + margin;
    }

    public boolean visible(float worldX, float worldY, float radius) {
        return Math.abs(worldX - x) < viewHalfW(radius + 32f)
                && Math.abs(worldY - y) < viewHalfH(radius + 32f);
    }
}
