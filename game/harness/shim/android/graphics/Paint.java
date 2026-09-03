package android.graphics;

public class Paint {

    public enum Style { FILL, STROKE, FILL_AND_STROKE }

    public int color = 0xFF000000;
    public Style style = Style.FILL;
    public float strokeWidth = 1;
    public ColorFilter colorFilter;
    public Shader shader;

    public Paint() { }

    public void setColor(int c) { color = c; }

    public int getColor() { return color; }

    public void setAlpha(int a) { color = (color & 0x00FFFFFF) | ((a & 255) << 24); }

    public void setAntiAlias(boolean b) { }

    public void setFilterBitmap(boolean b) { }

    public void setDither(boolean b) { }

    public void setStyle(Style s) { style = s; }

    public void setStrokeWidth(float w) { strokeWidth = w; }

    public ColorFilter setColorFilter(ColorFilter f) { colorFilter = f; return f; }

    public Shader setShader(Shader s) { shader = s; return s; }
}
