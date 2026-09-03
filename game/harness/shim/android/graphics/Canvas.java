package android.graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

public class Canvas {

    private final BufferedImage target;
    private final Graphics2D g;

    public Canvas(Bitmap b) {
        target = b.img;
        g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
    }

    public int getWidth() { return target.getWidth(); }

    public int getHeight() { return target.getHeight(); }

    private static Color col(int argb) {
        return new Color((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >>> 24));
    }

    private void apply(Paint p, float x0, float y0, float x1, float y1) {
        if (p.shader instanceof LinearGradient) {
            LinearGradient lg = (LinearGradient) p.shader;
            g.setPaint(new GradientPaint(lg.x0, lg.y0, col(lg.c0), lg.x1, lg.y1, col(lg.c1)));
        } else {
            g.setPaint(col(p.color));
        }
        if (p.style == Paint.Style.STROKE) g.setStroke(new BasicStroke(Math.max(0.5f, p.strokeWidth)));
    }

    public void drawRect(float l, float t, float r, float b, Paint p) {
        apply(p, l, t, r, b);
        float w = r - l, h = b - t;
        if (w <= 0 || h <= 0) return;
        if (p.style == Paint.Style.STROKE) g.draw(new java.awt.geom.Rectangle2D.Float(l, t, w, h));
        else g.fill(new java.awt.geom.Rectangle2D.Float(l, t, w, h));
    }

    public void drawRect(Rect rc, Paint p) {
        drawRect(rc.left, rc.top, rc.right, rc.bottom, p);
    }

    public void drawCircle(float cx, float cy, float rad, Paint p) {
        if (rad <= 0) return;
        apply(p, cx - rad, cy - rad, cx + rad, cy + rad);
        Ellipse2D.Float e = new Ellipse2D.Float(cx - rad, cy - rad, rad * 2, rad * 2);
        if (p.style == Paint.Style.STROKE) g.draw(e); else g.fill(e);
    }

    public void drawRoundRect(RectF rc, float rx, float ry, Paint p) {
        drawRoundRect(rc.left, rc.top, rc.right, rc.bottom, rx, ry, p);
    }

    public void drawRoundRect(float l, float t, float r, float b, float rx, float ry, Paint p) {
        apply(p, l, t, r, b);
        RoundRectangle2D.Float rr = new RoundRectangle2D.Float(l, t, r - l, b - t, rx * 2, ry * 2);
        if (p.style == Paint.Style.STROKE) g.draw(rr); else g.fill(rr);
    }

    public void drawArc(float l, float t, float r, float b, float start, float sweep,
                        boolean useCenter, Paint p) {
        apply(p, l, t, r, b);
        Arc2D.Float a = new Arc2D.Float(l, t, r - l, b - t, -start, -sweep,
                useCenter ? Arc2D.PIE : Arc2D.OPEN);
        if (p.style == Paint.Style.STROKE) g.draw(a); else g.fill(a);
    }

    public void drawPath(Path path, Paint p) {
        apply(p, 0, 0, 1, 1);
        if (p.style == Paint.Style.STROKE) g.draw(path.p); else g.fill(path.p);
    }

    public void drawBitmap(Bitmap b, float x, float y, Paint p) {
        BufferedImage img = filtered(b, p);
        g.drawImage(img, Math.round(x), Math.round(y), null);
    }

    public void drawBitmap(Bitmap b, Rect src, Rect dst, Paint p) {
        BufferedImage img = filtered(b, p);
        int sl = src == null ? 0 : src.left, st = src == null ? 0 : src.top;
        int sr = src == null ? b.getWidth() : src.right, sb = src == null ? b.getHeight() : src.bottom;
        g.drawImage(img, dst.left, dst.top, dst.right, dst.bottom, sl, st, sr, sb, null);
    }

    public void drawBitmap(Bitmap b, Rect src, RectF dst, Paint p) {
        BufferedImage img = filtered(b, p);
        int sl = src == null ? 0 : src.left, st = src == null ? 0 : src.top;
        int sr = src == null ? b.getWidth() : src.right, sb = src == null ? b.getHeight() : src.bottom;
        g.drawImage(img, Math.round(dst.left), Math.round(dst.top),
                Math.round(dst.right), Math.round(dst.bottom), sl, st, sr, sb, null);
    }

    /** Applies the paint's colour filter by producing a tinted copy. */
    private BufferedImage filtered(Bitmap b, Paint p) {
        if (p == null || !(p.colorFilter instanceof PorterDuffColorFilter)) return b.img;
        PorterDuffColorFilter f = (PorterDuffColorFilter) p.colorFilter;
        BufferedImage out = new BufferedImage(b.getWidth(), b.getHeight(), BufferedImage.TYPE_INT_ARGB);
        int fr = (f.color >> 16) & 255, fg = (f.color >> 8) & 255, fb = f.color & 255;
        int fa = f.color >>> 24;
        for (int y = 0; y < b.getHeight(); y++)
            for (int x = 0; x < b.getWidth(); x++) {
                int c = b.img.getRGB(x, y);
                int a = c >>> 24;
                if (a == 0) { out.setRGB(x, y, 0); continue; }
                int r = (c >> 16) & 255, gg = (c >> 8) & 255, bb = c & 255;
                switch (f.mode) {
                    case SRC_IN:
                        out.setRGB(x, y, ((a * fa / 255) << 24) | (fr << 16) | (fg << 8) | fb);
                        break;
                    case SRC_ATOP: {
                        int k = fa;
                        int nr = (fr * k + r * (255 - k)) / 255;
                        int ng = (fg * k + gg * (255 - k)) / 255;
                        int nb = (fb * k + bb * (255 - k)) / 255;
                        out.setRGB(x, y, (a << 24) | (nr << 16) | (ng << 8) | nb);
                        break;
                    }
                    default:
                        out.setRGB(x, y, (a << 24) | ((r * fr / 255) << 16)
                                | ((gg * fg / 255) << 8) | (bb * fb / 255));
                        break;
                }
            }
        return out;
    }
}
