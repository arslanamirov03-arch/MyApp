package com.abyss.silt.ui;

import android.graphics.Canvas;
import android.graphics.Paint;

import com.abyss.silt.core.Art;
import com.abyss.silt.core.V;

import java.util.ArrayList;

/** Text plates and overlays. Everything is centred, serif, and mostly whisper-quiet. */
public class Hud {

    private final ArrayList<String> lines = new ArrayList<>();

    public int w, h;
    public float u;      // layout unit = min(w,h)

    public void resize(int width, int height) {
        w = width;
        h = height;
        u = Math.min(width, height);
    }

    public void dim(Canvas c, Art art, float amount) {
        art.plain.setShader(null);
        art.plain.setXfermode(null);
        art.plain.setColor(Art.argb(amount, 0f));
        c.drawRect(0, 0, w, h, art.plain);
    }

    /**
     * Large spaced title, as used for the game name and chapter cards. The size
     * is reduced until the line fits, so a long chapter name cannot run off the
     * edge of a narrow screen.
     */
    public void title(Canvas c, Art art, String s, float cy, float size, float alpha) {
        art.text.setColor(Art.argb(alpha, 1f));
        art.text.setLetterSpacing(0.34f);
        float maxWidth = w * 0.84f;
        for (int i = 0; i < 12; i++) {
            art.text.setTextSize(size);
            if (art.text.measureText(s) <= maxWidth) break;
            size *= 0.9f;
        }
        c.drawText(s, w * 0.5f, cy, art.text);
        art.text.setLetterSpacing(0f);
    }

    public void label(Canvas c, Art art, String s, float cx, float cy,
                      float size, float alpha, float spacing) {
        art.text.setColor(Art.argb(alpha, 1f));
        art.text.setTextSize(size);
        art.text.setLetterSpacing(spacing);
        c.drawText(s, cx, cy, art.text);
        art.text.setLetterSpacing(0f);
    }

    /** Draws wrapped body text centred on cy; returns the height it used. */
    public float paragraph(Canvas c, Art art, String s, float cy,
                           float size, float maxWidth, float alpha) {
        art.text.setColor(Art.argb(alpha, 1f));
        art.text.setTextSize(size);
        art.text.setLetterSpacing(0.04f);
        wrap(s, maxWidth, art.text);
        float lh = size * 1.42f;
        float top = cy - (lines.size() - 1) * lh * 0.5f;
        for (int i = 0; i < lines.size(); i++) {
            c.drawText(lines.get(i), w * 0.5f, top + i * lh, art.text);
        }
        art.text.setLetterSpacing(0f);
        return lines.size() * lh;
    }

    private void wrap(String s, float maxWidth, Paint p) {
        lines.clear();
        String[] words = s.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String word : words) {
            String probe = cur.length() == 0 ? word : cur + " " + word;
            if (p.measureText(probe) > maxWidth && cur.length() > 0) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(probe);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
    }

    /** The ambient one-liners triggered by whisper markers. */
    public void whisper(Canvas c, Art art, String s, float alpha) {
        if (s == null || alpha <= 0.01f) return;
        paragraph(c, art, s, h * 0.20f, u * 0.042f, w * 0.62f, alpha * 0.72f);
    }

    /** A framed plate for hint text at the bottom of the screen. */
    public void hintPlate(Canvas c, Art art, String s, float alpha) {
        if (s == null || alpha <= 0.01f) return;
        float pad = u * 0.05f;
        float boxW = w * 0.66f;
        art.text.setTextSize(u * 0.040f);
        wrap(s, boxW - pad * 2f, art.text);
        float lh = u * 0.040f * 1.42f;
        float boxH = lines.size() * lh + pad * 1.4f;
        float cx = w * 0.5f, cy = h * 0.845f;

        art.roundRect(c, cx - boxW * 0.5f, cy - boxH * 0.5f, cx + boxW * 0.5f, cy + boxH * 0.5f,
                u * 0.02f, Art.argb(0.62f * alpha, 0f));
        art.stroke.setShader(null);
        art.stroke.setStrokeWidth(1.6f);
        art.stroke.setColor(Art.argb(0.22f * alpha, 1f));
        c.drawRoundRect(cx - boxW * 0.5f, cy - boxH * 0.5f, cx + boxW * 0.5f, cy + boxH * 0.5f,
                u * 0.02f, u * 0.02f, art.stroke);

        art.text.setColor(Art.argb(0.85f * alpha, 1f));
        art.text.setLetterSpacing(0.04f);
        float top = cy - (lines.size() - 1) * lh * 0.5f + lh * 0.12f;
        for (int i = 0; i < lines.size(); i++) {
            c.drawText(lines.get(i), cx, top + i * lh, art.text);
        }
        art.text.setLetterSpacing(0f);
    }

    /** A menu row; returns true if the point falls inside it. */
    public boolean menuItem(Canvas c, Art art, String s, float cy, float size,
                            boolean highlighted, float alpha) {
        float a = (highlighted ? 0.95f : 0.55f) * alpha;
        label(c, art, s, w * 0.5f, cy, size, a, 0.22f);
        return false;
    }

    public boolean hitRow(float px, float py, float cy, float size) {
        return Math.abs(py - cy) < size * 0.95f && px > w * 0.18f && px < w * 0.82f;
    }

    /** A thin progress bar used on the chapter screen. */
    public void bar(Canvas c, Art art, float cx, float cy, float width, float frac, float alpha) {
        float hgt = u * 0.006f;
        art.roundRect(c, cx - width * 0.5f, cy - hgt * 0.5f, cx + width * 0.5f, cy + hgt * 0.5f,
                hgt * 0.5f, Art.argb(0.16f * alpha, 1f));
        float fw = width * V.clamp(frac, 0f, 1f);
        if (fw > 0.5f) {
            art.roundRect(c, cx - width * 0.5f, cy - hgt * 0.5f, cx - width * 0.5f + fw,
                    cy + hgt * 0.5f, hgt * 0.5f, Art.argb(0.72f * alpha, 1f));
        }
    }

    public static String timeStr(float seconds) {
        int s = (int) seconds;
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
