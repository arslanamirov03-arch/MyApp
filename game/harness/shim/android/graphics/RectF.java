package android.graphics;

public class RectF {
    public float left, top, right, bottom;

    public RectF() { }

    public RectF(float l, float t, float r, float b) { set(l, t, r, b); }

    public RectF(RectF o) { set(o.left, o.top, o.right, o.bottom); }

    public void set(float l, float t, float r, float b) {
        left = l; top = t; right = r; bottom = b;
    }

    public void offset(float dx, float dy) {
        left += dx; right += dx; top += dy; bottom += dy;
    }

    public boolean contains(float x, float y) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    public float centerX() { return (left + right) / 2; }

    public float centerY() { return (top + bottom) / 2; }

    public float width() { return right - left; }

    public float height() { return bottom - top; }
}
