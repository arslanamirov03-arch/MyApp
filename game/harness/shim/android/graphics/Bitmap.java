package android.graphics;

import java.awt.image.BufferedImage;

public final class Bitmap {

    public enum Config { ARGB_8888, RGB_565 }

    public final BufferedImage img;

    private Bitmap(int w, int h) {
        img = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
    }

    public static Bitmap createBitmap(int w, int h, Config c) {
        return new Bitmap(w, h);
    }

    public int getWidth() { return img.getWidth(); }

    public int getHeight() { return img.getHeight(); }

    public void setPixels(int[] px, int offset, int stride, int x, int y, int w, int h) {
        for (int j = 0; j < h; j++)
            for (int i = 0; i < w; i++)
                img.setRGB(x + i, y + j, px[offset + j * stride + i]);
    }

    public void getPixels(int[] px, int offset, int stride, int x, int y, int w, int h) {
        for (int j = 0; j < h; j++)
            for (int i = 0; i < w; i++)
                px[offset + j * stride + i] = img.getRGB(x + i, y + j);
    }
}
