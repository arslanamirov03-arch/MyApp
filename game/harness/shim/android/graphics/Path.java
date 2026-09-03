package android.graphics;

import java.awt.geom.Path2D;

public class Path {
    public final Path2D.Float p = new Path2D.Float();
    private boolean started;

    public void moveTo(float x, float y) { p.moveTo(x, y); started = true; }

    public void lineTo(float x, float y) {
        if (!started) { p.moveTo(x, y); started = true; } else p.lineTo(x, y);
    }

    public void close() { if (started) p.closePath(); }

    public void reset() { p.reset(); started = false; }
}
