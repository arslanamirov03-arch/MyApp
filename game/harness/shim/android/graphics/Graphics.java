package android.graphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Desktop stand-in for the slice of android.graphics the game uses, backed by
 * Java2D. Only exists so the game logic and rendering can be exercised and
 * screenshotted off-device; it is never compiled into the APK.
 */
final class Graphics { }
