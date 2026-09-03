package com.bromobile.game;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.ArrayList;

/**
 * All front-end screens: main menu, mission select, settings, pause, results.
 * Screens are drawn in virtual pixels and expose their hit areas through
 * {@link #tap}, which returns the id of whatever the finger landed on.
 */
public final class Ui {

    public static final int MAIN = 0, SELECT = 1, SETTINGS = 2, PAUSE = 3,
            CONFIRM = 4, COMPLETE = 5, OUTRO = 6;

    private static final class Btn {
        final RectF r = new RectF();
        String id, label;
        boolean enabled = true, small, danger, primary;
        int tint;
    }

    public int screen = MAIN;
    public int settingsReturn = MAIN;

    private final ArrayList<Btn> btns = new ArrayList<>();
    private final Paint p = new Paint();
    private int vw, vh;
    private float anim;
    private String pressed;
    private float pressTime;

    private Theme menuTheme;
    private float menuScroll;

    public Ui() {
        p.setAntiAlias(true);
        p.setFilterBitmap(false);
    }

    public void layout(int vw, int vh) {
        this.vw = vw;
        this.vh = vh;
        if (menuTheme == null || menuTheme.id != Theme.CITY) menuTheme = new Theme(Theme.CITY, vw, vh);
    }

    public void update(float dt) {
        anim += dt;
        menuScroll += dt * 18;
        if (pressTime > 0) {
            pressTime -= dt;
            if (pressTime <= 0) pressed = null;
        }
        if (menuTheme != null) menuTheme.updateWeather(dt, vw, vh);
    }

    public void go(int s) {
        screen = s;
        btns.clear();
    }

    // ------------------------------------------------------------------

    private Btn add(String id, String label, float x, float y, float w, float h) {
        Btn b = new Btn();
        b.id = id;
        b.label = label;
        b.r.set(x, y, x + w, y + h);
        btns.add(b);
        return b;
    }

    public String tap(float x, float y) {
        for (int i = 0; i < btns.size(); i++) {
            Btn b = btns.get(i);
            if (!b.enabled) continue;
            if (b.r.contains(x, y)) {
                pressed = b.id;
                pressTime = 0.14f;
                return b.id;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    public void draw(Canvas c, Save save, World world) {
        btns.clear();
        switch (screen) {
            case MAIN: drawMain(c, save); break;
            case SELECT: drawSelect(c, save); break;
            case SETTINGS: drawSettings(c, save); break;
            case PAUSE: drawPause(c, save); break;
            case CONFIRM: drawConfirm(c); break;
            case COMPLETE: drawComplete(c, save, world); break;
            case OUTRO: drawOutro(c, save, world); break;
        }
    }

    private void backdrop(Canvas c, boolean dim) {
        if (menuTheme != null) {
            menuTheme.drawSky(c);
            menuTheme.drawLayers(c, menuScroll, 300, vw, vh);
            menuTheme.drawWeather(c, vw, vh);
        } else {
            p.setColor(0xFF14121C);
            c.drawRect(0, 0, vw, vh, p);
        }
        if (dim) {
            p.setColor(0xAA0A0810);
            c.drawRect(0, 0, vw, vh, p);
        }
    }

    private void overlayDim(Canvas c) {
        p.setColor(0xCC0A0810);
        c.drawRect(0, 0, vw, vh, p);
    }

    private void title(Canvas c, String s, int y) {
        float wobble = (float) Math.sin(anim * 2) * 1.5f;
        Font.outlineCenter(c, s, vw / 2, (int) (y + wobble), 0xFFFFD040, 0xFF201008, 3);
        Font.outlineCenter(c, s, vw / 2 + 1, (int) (y + wobble + 1), 0xFFE85028, 0x00000000, 3);
        Font.outlineCenter(c, s, vw / 2, (int) (y + wobble), 0xFFFFD040, 0xFF201008, 3);
    }

    private void drawBtn(Canvas c, Btn b) {
        boolean down = b.id.equals(pressed);
        float off = down ? 1 : 0;
        RectF r = new RectF(b.r);
        r.offset(0, off);

        p.setColor(0x88000000);
        c.drawRoundRect(r.left, r.top + 2, r.right, r.bottom + 2, 4, 4, p);

        int base = !b.enabled ? 0xFF2A2A32
                : b.danger ? 0xFF7A2018
                : b.primary ? 0xFF7A4A18 : 0xFF2E3242;
        int edge = !b.enabled ? 0xFF3A3A44
                : b.danger ? 0xFFE05038
                : b.primary ? 0xFFFFB040 : 0xFF6A7488;
        if (b.tint != 0) { base = b.tint; edge = Fx.blend(b.tint, 0xFFFFFFFF, 0.45f); }

        p.setColor(down ? Fx.blend(base, 0xFF000000, 0.25f) : base);
        c.drawRoundRect(r, 4, 4, p);
        p.setColor(Fx.blend(base, 0xFFFFFFFF, 0.14f));
        c.drawRoundRect(r.left + 1, r.top + 1, r.right - 1, r.top + (r.height() / 2), 3, 3, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.6f);
        p.setColor(edge);
        c.drawRoundRect(r, 4, 4, p);
        p.setStyle(Paint.Style.FILL);

        int scale = b.small ? 1 : 1;
        int tc = b.enabled ? 0xFFF4F0E8 : 0xFF6A6A74;
        Font.shadow(c, b.label,
                (int) (r.centerX() - Font.width(b.label, scale) / 2),
                (int) (r.centerY() - Font.height(scale) / 2), tc, scale);
    }

    private void drawAll(Canvas c) {
        for (int i = 0; i < btns.size(); i++) drawBtn(c, btns.get(i));
    }

    // --- main menu ---
    private void drawMain(Canvas c, Save save) {
        backdrop(c, false);
        p.setColor(0x66000000);
        c.drawRect(0, 0, vw, vh, p);

        title(c, "BRO FORCE", 22);
        Font.center(c, "МОБИЛЬНАЯ ВОЙНА", vw / 2, 52, 0xFFE0C890, 1);

        float bw = 176, bh = 23, gap = 5;
        float bx = vw / 2f - bw / 2;
        float by = 76;
        add("new", "НОВАЯ ИГРА", bx, by, bw, bh).primary = true;
        Btn cont = add("continue", "ПРОДОЛЖИТЬ", bx, by + (bh + gap), bw, bh);
        cont.enabled = save.hasRun;
        add("select", "ВЫБОР МИССИИ", bx, by + (bh + gap) * 2, bw, bh);
        add("settings", "НАСТРОЙКИ", bx, by + (bh + gap) * 3, bw, bh);
        Btn del = add("delete", "УДАЛИТЬ", bx, by + (bh + gap) * 4, bw / 2 - 3, bh);
        del.danger = true;
        add("quit", "ВЫХОД", bx + bw / 2 + 3, by + (bh + gap) * 4, bw / 2 - 3, bh);
        drawAll(c);

        Font.shadow(c, "РЕКОРД: " + save.bestScore, 8, vh - 20, 0xFFFFD860, 1);
        if (save.hasRun) {
            Font.shadow(c, "СОХРАНЕНИЕ: КАРТА " + (save.map + 1) + "  УРОВЕНЬ " + (save.level + 1),
                    8, vh - 11, 0xFFA0C8E0, 1);
        } else {
            Font.shadow(c, "СОХРАНЕНИЯ НЕТ", 8, vh - 11, 0xFF8A8A94, 1);
        }
        Font.shadow(c, "УБИТО: " + save.totalKills, vw - 90, vh - 11, 0xFFC0C8D8, 1);
    }

    // --- mission select ---
    private void drawSelect(Canvas c, Save save) {
        backdrop(c, true);
        Font.outlineCenter(c, "ВЫБОР МИССИИ", vw / 2, 10, 0xFFFFD040, 0xFF201008, 2);

        float cw = 34, ch = 20, gapx = 4;
        float left = vw / 2f - (cw * 5 + gapx * 4) / 2 + 46;
        for (int m = 0; m < 5; m++) {
            float ry = 34 + m * 25;
            Theme t = null;
            String nm = mapName(m);
            int col = mapColor(m);
            p.setColor(0x66000000);
            c.drawRect(6, ry - 2, vw - 6, ry + ch + 2, p);
            p.setColor(col);
            c.drawRect(6, ry - 2, 10, ry + ch + 2, p);
            Font.shadow(c, nm, 14, (int) (ry + 6), save.unlockedMap >= m ? 0xFFF0E8D8 : 0xFF70707A, 1);

            for (int lv = 0; lv < 5; lv++) {
                float bx = left + lv * (cw + gapx);
                boolean unlocked = save.isUnlocked(m, lv);
                Btn b = add("lv" + m + "" + lv, lv == 4 ? "БОСС" : String.valueOf(lv + 1),
                        bx, ry, cw, ch);
                b.enabled = unlocked;
                b.small = true;
                if (unlocked) b.tint = lv == 4 ? 0xFF7A2018 : Fx.blend(col, 0xFF000000, 0.45f);
            }
        }
        drawAll(c);

        float bw = 90, bh = 22;
        add("back", "НАЗАД", vw / 2f - bw / 2, vh - 26, bw, bh);
        drawBtn(c, btns.get(btns.size() - 1));
        Font.center(c, "5 КАРТ  x  5 УРОВНЕЙ  =  25 МИССИЙ", vw / 2, vh - 36, 0xFFA0A8B8, 1);
    }

    static String mapName(int m) {
        switch (m) {
            case 1: return "ЛЕСТНИЦА В НЕБО";
            case 2: return "ЛЕДЯНЫЕ ПЕЩЕРЫ";
            case 3: return "ДРЕВНИЕ РУИНЫ";
            case 4: return "ЗАВОД";
            default: return "ГОРОДСКОЙ";
        }
    }

    static int mapColor(int m) {
        switch (m) {
            case 1: return 0xFF4C8AD8;
            case 2: return 0xFF6AC0E8;
            case 3: return 0xFF6A9A3A;
            case 4: return 0xFFE08828;
            default: return 0xFFC0503A;
        }
    }

    // --- settings ---
    private void drawSettings(Canvas c, Save save) {
        backdrop(c, true);
        overlayDim(c);
        Font.outlineCenter(c, "НАСТРОЙКИ", vw / 2, 8, 0xFFFFD040, 0xFF201008, 2);

        String[] labels = {"ЗВУК", "МУЗЫКА", "ВИБРАЦИЯ", "АВТОСТРЕЛЬБА",
                "РАЗМЕР КНОПОК", "ЛЕВОРУКИЙ", "КРОВЬ", "СЧЁТЧИК FPS"};
        String[] ids = {"sfx", "mus", "vib", "auto", "pad", "lefty", "blood", "fps"};
        String[] values = {
                bar(save.sfxVol), bar(save.musicVol),
                save.vibrate ? "ВКЛ" : "ВЫКЛ",
                save.autoFire ? "ВКЛ" : "ВЫКЛ",
                save.padScale == 1 ? "МАЛЫЙ" : (save.padScale == 2 ? "СРЕДНИЙ" : "БОЛЬШОЙ"),
                save.leftHanded ? "ВКЛ" : "ВЫКЛ",
                save.blood ? "ВКЛ" : "ВЫКЛ",
                save.showFps ? "ВКЛ" : "ВЫКЛ"};

        float rowH = 21, top = 28;
        float lx = 22, rw = 150;
        float rx = vw - 22 - rw;
        for (int i = 0; i < labels.length; i++) {
            float ry = top + i * rowH;
            p.setColor(i % 2 == 0 ? 0x33000000 : 0x22000000);
            c.drawRect(14, ry, vw - 14, ry + rowH - 3, p);
            Font.shadow(c, labels[i], (int) lx, (int) (ry + 6), 0xFFE8E4DC, 1);

            Btn dec = add(ids[i] + "-", "<", rx, ry, 18, rowH - 4);
            dec.small = true;
            Btn inc = add(ids[i] + "+", ">", rx + rw - 18, ry, 18, rowH - 4);
            inc.small = true;
            Font.shadow(c, values[i],
                    (int) (rx + rw / 2 - Font.width(values[i], 1) / 2), (int) (ry + 6),
                    0xFFFFD070, 1);
        }

        float bw = 120, bh = 24, by = top + labels.length * rowH + 4;
        Btn save1 = add("save", "СОХРАНИТЬ", vw / 2f - bw - 5, by, bw, bh);
        save1.primary = true;
        add("exitset", "ВЫЙТИ", vw / 2f + 5, by, bw, bh);
        drawAll(c);
    }

    private static String bar(int v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(i < v ? '#' : '-');
        return sb.toString();
    }

    // --- pause ---
    private void drawPause(Canvas c, Save save) {
        overlayDim(c);
        Font.outlineCenter(c, "ПАУЗА", vw / 2, 34, 0xFFFFD040, 0xFF201008, 3);

        float bw = 176, bh = 24, gap = 6;
        float bx = vw / 2f - bw / 2, by = 84;
        add("resume", "ПРОДОЛЖИТЬ", bx, by, bw, bh).primary = true;
        add("settings", "НАСТРОЙКИ", bx, by + (bh + gap), bw, bh);
        add("restart", "НАЧАТЬ УРОВЕНЬ ЗАНОВО", bx, by + (bh + gap) * 2, bw, bh);
        Btn m = add("menu", "В ГЛАВНОЕ МЕНЮ", bx, by + (bh + gap) * 3, bw, bh);
        m.danger = true;
        drawAll(c);
        Font.center(c, "ПРОГРЕСС СОХРАНЯЕТСЯ АВТОМАТИЧЕСКИ", vw / 2, vh - 16, 0xFF9AA0AC, 1);
    }

    // --- delete confirmation ---
    private void drawConfirm(Canvas c) {
        backdrop(c, true);
        overlayDim(c);
        Font.outlineCenter(c, "УДАЛИТЬ СОХРАНЕНИЕ?", vw / 2, 70, 0xFFFF5040, 0xFF200808, 2);
        Font.center(c, "ВЕСЬ ПРОГРЕСС И ОТКРЫТЫЕ МИССИИ", vw / 2, 100, 0xFFD8D0C8, 1);
        Font.center(c, "БУДУТ ПОТЕРЯНЫ НАВСЕГДА.", vw / 2, 112, 0xFFD8D0C8, 1);
        float bw = 130, bh = 26;
        Btn yes = add("confirmyes", "ДА, УДАЛИТЬ", vw / 2f - bw - 6, 150, bw, bh);
        yes.danger = true;
        add("confirmno", "ОТМЕНА", vw / 2f + 6, 150, bw, bh);
        drawAll(c);
    }

    // --- level cleared ---
    private void drawComplete(Canvas c, Save save, World w) {
        overlayDim(c);
        boolean bossLevel = w != null && w.level != null && w.level.bossLevel;
        Font.outlineCenter(c, bossLevel ? "БОСС ПОВЕРЖЕН!" : "УРОВЕНЬ ПРОЙДЕН!",
                vw / 2, 26, 0xFFFFD040, 0xFF201008, 2);

        int y = 62;
        if (w != null) {
            stat(c, "ОЧКИ", String.valueOf(w.score), y);
            stat(c, "УБИТО ВРАГОВ", String.valueOf(w.kills), y + 14);
            stat(c, "СПАСЕНО", String.valueOf(w.rescued), y + 28);
            stat(c, "ВРЕМЯ", (int) w.levelTime + " С", y + 42);
            stat(c, "ЖИЗНЕЙ ОСТАЛОСЬ", String.valueOf(w.lives), y + 56);
        }

        float bw = 150, bh = 26;
        boolean last = save.map == 4 && save.level == 4;
        add("next", last ? "ФИНАЛ" : "СЛЕДУЮЩИЙ УРОВЕНЬ",
                vw / 2f - bw - 6, vh - 42, bw, bh).primary = true;
        add("menu", "В МЕНЮ", vw / 2f + 6, vh - 42, bw, bh);
        drawAll(c);
    }

    private void stat(Canvas c, String label, String value, int y) {
        Font.shadow(c, label, vw / 2 - 120, y, 0xFFB8C0CC, 1);
        Font.shadow(c, value, vw / 2 + 116 - Font.width(value, 1), y, 0xFFFFD860, 1);
        p.setColor(0x33FFFFFF);
        c.drawRect(vw / 2f - 120, y + 8, vw / 2f + 116, y + 8.6f, p);
    }

    // --- campaign finished ---
    private void drawOutro(Canvas c, Save save, World w) {
        backdrop(c, true);
        overlayDim(c);
        title(c, "ПОБЕДА", 30);
        Font.center(c, "ВСЕ 5 КАРТ ЗАЧИЩЕНЫ. ВСЕ БОССЫ ПОВЕРЖЕНЫ.", vw / 2, 74, 0xFFE8E0D0, 1);
        Font.center(c, "ТЫ НАСТОЯЩИЙ БРО.", vw / 2, 88, 0xFFFFD040, 1);
        Font.center(c, "ИТОГОВЫЙ СЧЁТ: " + save.bestScore, vw / 2, 112, 0xFFFFD860, 1);
        Font.center(c, "ВСЕГО УБИТО: " + save.totalKills, vw / 2, 126, 0xFFC0C8D8, 1);
        Font.center(c, "ПРОДОЛЖЕНИЕ СЛЕДУЕТ...", vw / 2, 152, 0xFF90A0B8, 1);
        float bw = 150, bh = 26;
        add("menu", "В ГЛАВНОЕ МЕНЮ", vw / 2f - bw / 2, vh - 44, bw, bh).primary = true;
        drawAll(c);
    }
}
