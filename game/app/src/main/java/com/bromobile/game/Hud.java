package com.bromobile.game;

import android.graphics.Canvas;
import android.graphics.Paint;

/** In-game overlay: bro portrait, one-hit warning, score, lives and boss bar. */
public final class Hud {

    private final Paint p = new Paint();
    private float pulse;

    public Hud() {
        p.setAntiAlias(false);
        p.setFilterBitmap(false);
    }

    public void update(float dt) {
        pulse += dt;
    }

    public void draw(Canvas c, World w, int vw, int vh, float fps, boolean showFps) {
        Player pl = w.player;

        // --- portrait ---
        int px = 6, py = 6, ps = 26;
        p.setColor(0xAA101018);
        c.drawRect(px, py, px + ps, py + ps, p);
        p.setColor(0xFF6A5A48);
        c.drawRect(px, py, px + ps, py + 1, p);
        c.drawRect(px, py + ps - 1, px + ps, py + ps, p);
        c.drawRect(px, py, px + 1, py + ps, p);
        c.drawRect(px + ps - 1, py, px + ps, py + ps, p);
        // Face crop of the idle sprite, scaled 2x.
        android.graphics.Rect src = new android.graphics.Rect(1, 0, 11, 10);
        android.graphics.Rect dst = new android.graphics.Rect(px + 3, py + 3, px + 23, py + 23);
        c.drawBitmap(Art.broIdle, src, dst, p);

        int tx = px + ps + 5;
        Font.shadow(c, "HEALTH: 1", tx, py + 1, 0xFFF0F0F0, 1);

        // One-hit warning, pulsing red
        int a = (int) (170 + 85 * Math.sin(pulse * 5));
        p.setColor((0xFF << 24) | 0x00C82820);
        heart(c, tx + 1, py + 11, 1);
        Font.shadow(c, "1 ПОПАДАНИЕ = СМЕРТЬ", tx + 11, py + 10,
                (a << 24) | 0x00FF4040, 1);

        Font.shadow(c, "БРОФОРС: " + w.score, tx, py + 19, 0xFFFFD860, 1);

        // --- lives ---
        int lx = px, ly = py + ps + 4;
        for (int i = 0; i < Math.min(8, w.lives); i++) {
            p.setColor(0xFFC82820);
            heart(c, lx + i * 9, ly, 1);
        }
        if (w.lives > 8) Font.shadow(c, "x" + w.lives, lx + 74, ly, 0xFFFF8080, 1);

        // --- weapon ammo readout, above the pad cluster ---
        if (pl != null) {
            String ammo;
            if (pl.weapon == Player.AK) ammo = "∞";
            else if (pl.weapon == Player.GRENADE) ammo = String.valueOf(pl.grenades);
            else ammo = String.valueOf(pl.ammo[pl.weapon]);
            if (ammo.equals("∞")) ammo = "---";
            Font.shadow(c, ammo, vw - 30, 30, 0xFFFFD070, 1);
        }

        // --- boss health ---
        if (w.boss != null && w.boss.intro <= 0 && !w.boss.dying) {
            Boss b = w.boss;
            int bw = Math.min(300, vw - 60);
            int bx = (vw - bw) / 2, by = 12;
            p.setColor(0xCC100810);
            c.drawRect(bx - 2, by - 2, bx + bw + 2, by + 9, p);
            p.setColor(0xFF3A1414);
            c.drawRect(bx, by, bx + bw, by + 7, p);
            float k = Math.max(0, b.hp / b.maxHp);
            p.setColor(0xFFB01810);
            c.drawRect(bx, by, bx + bw * k, by + 7, p);
            p.setColor(0xFFE85030);
            c.drawRect(bx, by, bx + bw * k, by + 2, p);
            p.setColor(0x66FFFFFF);
            for (int i = 1; i < 8; i++) c.drawRect(bx + bw * i / 8f, by, bx + bw * i / 8f + 1, by + 7, p);
            Font.center(c, b.name, vw / 2, by - 10, 0xFFFFE0A0, 1);
            String hint = b.hint();
            Font.center(c, hint, vw / 2, by + 11,
                    b.armoredAt(b.cx(), b.cy() + 6) ? 0xFFB0B0C0 : 0xFFFFD040, 1);
        }

        // --- boss name card ---
        if (w.boss != null && w.boss.intro > 0) {
            float t = w.boss.intro;
            int alpha = (int) (255 * Math.min(1, t / 0.5f));
            p.setColor(((int) (alpha * 0.55f) << 24));
            c.drawRect(0, vh / 2 - 30, vw, vh / 2 + 22, p);
            Font.outlineCenter(c, w.boss.name, vw / 2, vh / 2 - 24,
                    (alpha << 24) | 0x00FF4030, (alpha << 24), 2);
            Font.center(c, "ФИНАЛЬНЫЙ БОСС", vw / 2, vh / 2 + 4,
                    (alpha << 24) | 0x00FFE060, 1);
            Font.center(c, w.boss.hint(), vw / 2, vh / 2 + 14,
                    (alpha << 24) | 0x00C0C8D8, 1);
        }

        // --- transient message banner ---
        if (w.hudMessageTime > 0 && w.hudMessage != null) {
            int alpha = (int) (255 * Math.min(1, w.hudMessageTime / 0.6f));
            Font.outlineCenter(c, w.hudMessage, vw / 2, 40,
                    (alpha << 24) | 0x00FFE060, (alpha << 24), 1);
        }

        // --- combo ---
        if (pl != null && pl.combo >= 4 && pl.comboTimer > 0) {
            int mult = Math.min(5, 1 + pl.combo / 4);
            Font.shadow(c, "x" + mult + " КОМБО", vw / 2 - 24, 52,
                    mult >= 4 ? 0xFFFF6030 : 0xFFFFD040, 1);
        }

        if (showFps) Font.shadow(c, ((int) fps) + " FPS", vw - 40, vh - 10, 0xFF90FF90, 1);
    }

    private void heart(Canvas c, float x, float y, float s) {
        c.drawRect(x, y + s, x + 2 * s, y + 5 * s, p);
        c.drawRect(x + 5 * s, y + s, x + 7 * s, y + 5 * s, p);
        c.drawRect(x + s, y, x + 6 * s, y + 4 * s, p);
        c.drawRect(x + 2 * s, y + 4 * s, x + 5 * s, y + 6 * s, p);
        c.drawRect(x + 3 * s, y + 6 * s, x + 4 * s, y + 7 * s, p);
    }
}
