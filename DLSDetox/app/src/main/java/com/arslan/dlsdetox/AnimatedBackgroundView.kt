package com.arslan.dlsdetox

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * A living gradient backdrop with drifting glow-orbs, twinkling stars, warm
 * embers and — at victory — fireworks. Everything is driven by the current
 * streak tier and transitions smoothly whenever the tier changes.
 */
class AnimatedBackgroundView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // ---- current (interpolated) look ----
    private val fromBg = IntArray(3)
    private val toBg = IntArray(3)
    private var fromAccent = 0
    private var toAccent = 0
    private var fromAccent2 = 0
    private var toAccent2 = 0
    private var fromOrb = 0
    private var toOrb = 0
    private var fromGlow = 0f
    private var toGlow = 0f

    private var starsOn = false
    private var sparksOn = false
    private var fireworksOn = false
    private var starTarget = 0f
    private var starAlpha = 0f

    private var blend = 1f              // 0..1 transition progress
    private var blendDurNs = 1_400_000_000L
    private var blendStartNs = 0L

    private var startNs = 0L
    private var lastNs = 0L
    private var running = false

    // ---- particle pools ----
    private val rnd = Random(4242)
    private val orbs = Array(64) { Orb(rnd) }
    private val stars = Array(70) { Star(rnd) }
    private val embers = Array(20) { Ember(rnd) }
    private val bursts = ArrayList<Burst>()
    private var nextBurstT = 1.0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()
    private val srcRect = Rect()

    private lateinit var softDot: Bitmap

    init {
        buildSoftDot()
        val p = Palettes.list[0]
        setPaletteImmediate(p)
    }

    private fun buildSoftDot() {
        val s = 128
        softDot = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(softDot)
        val pp = Paint(Paint.ANTI_ALIAS_FLAG)
        pp.shader = RadialGradient(
            s / 2f, s / 2f, s / 2f,
            intArrayOf(Color.WHITE, Color.argb(190, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
        )
        c.drawCircle(s / 2f, s / 2f, s / 2f, pp)
        srcRect.set(0, 0, s, s)
    }

    private fun bg3(p: Palette): IntArray {
        return if (p.bg.size >= 3) intArrayOf(p.bg[0], p.bg[1], p.bg[2])
        else intArrayOf(p.bg[0], lerp(p.bg[0], p.bg[1], 0.5f), p.bg[1])
    }

    private fun setPaletteImmediate(p: Palette) {
        val b = bg3(p)
        for (i in 0..2) { fromBg[i] = b[i]; toBg[i] = b[i] }
        fromAccent = p.accent; toAccent = p.accent
        fromAccent2 = p.accent2; toAccent2 = p.accent2
        fromOrb = p.orbCount; toOrb = p.orbCount
        fromGlow = p.glow; toGlow = p.glow
        starsOn = p.stars; sparksOn = p.sparks; fireworksOn = p.fireworks
        starTarget = if (p.stars) 1f else 0f
        starAlpha = starTarget
        blend = 1f
    }

    /** Animate to a new tier. */
    fun applyPalette(p: Palette) {
        // snapshot current interpolated state as the new "from"
        val t = blend
        for (i in 0..2) fromBg[i] = lerp(fromBg[i], toBg[i], t)
        fromAccent = lerp(fromAccent, toAccent, t)
        fromAccent2 = lerp(fromAccent2, toAccent2, t)
        fromOrb = (fromOrb + (toOrb - fromOrb) * t).toInt()
        fromGlow = fromGlow + (toGlow - fromGlow) * t

        val b = bg3(p)
        for (i in 0..2) toBg[i] = b[i]
        toAccent = p.accent
        toAccent2 = p.accent2
        toOrb = p.orbCount
        toGlow = p.glow
        starsOn = p.stars; sparksOn = p.sparks; fireworksOn = p.fireworks
        starTarget = if (p.stars) 1f else 0f

        blend = 0f
        blendStartNs = System.nanoTime()
        invalidate()
    }

    /** A one-off celebratory burst regardless of tier (used on level-up / ticks). */
    fun celebrate(color: Int) {
        if (width == 0) return
        bursts.add(Burst(rnd, 0.5f, 0.32f, color, timeSec()))
        invalidate()
    }

    private fun timeSec(): Float =
        if (startNs == 0L) 0f else (System.nanoTime() - startNs) / 1_000_000_000f

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        startNs = System.nanoTime()
        lastNs = startNs
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        running = false
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val t = (now - startNs) / 1_000_000_000f
        var dt = (now - lastNs) / 1_000_000_000f
        if (dt < 0f) dt = 0f
        if (dt > 0.05f) dt = 0.05f
        lastNs = now

        // advance transition
        if (blend < 1f) {
            blend = ((now - blendStartNs).toFloat() / blendDurNs).coerceIn(0f, 1f)
        }
        val e = easeInOut(blend)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) { scheduleNext(); return }

        val c0 = lerp(fromBg[0], toBg[0], e)
        val c1 = lerp(fromBg[1], toBg[1], e)
        val c2 = lerp(fromBg[2], toBg[2], e)
        val accent = lerp(fromAccent, toAccent, e)
        val accent2 = lerp(fromAccent2, toAccent2, e)
        val orbCount = (fromOrb + (toOrb - fromOrb) * e).toInt().coerceIn(0, orbs.size)
        val glow = fromGlow + (toGlow - fromGlow) * e

        // gentle vertical drift of the middle colour stop for a "breathing" feel
        val mid = 0.5f + 0.06f * sin(t * 0.35f)
        bgPaint.shader = LinearGradient(
            0f, 0f, w * 0.25f, h,
            intArrayOf(c0, c1, c2),
            floatArrayOf(0f, mid, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // soft accent glow near the top, pulsing
        if (glow > 0.01f) {
            val pulse = 0.82f + 0.18f * sin(t * 0.8f)
            val gr = h * 0.55f * pulse
            glowPaint.shader = RadialGradient(
                w * 0.5f, h * 0.24f, gr,
                intArrayOf(withAlpha(accent, (70 * glow).toInt()), Color.TRANSPARENT),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP
            )
            canvas.drawCircle(w * 0.5f, h * 0.24f, gr, glowPaint)
        }

        // stars fade in/out
        starAlpha += (starTarget - starAlpha) * min(1f, dt * 2.2f)
        if (starAlpha > 0.02f) {
            for (s in stars) {
                val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin(t * s.twSpeed + s.phase))
                val a = (s.baseAlpha * tw * starAlpha * 255f).toInt().coerceIn(0, 255)
                if (a <= 2) continue
                paint.color = withAlpha(if (s.warm) accent2 else Color.WHITE, a)
                canvas.drawCircle(s.x * w, s.y * h, s.size, paint)
            }
        }

        // drifting glow orbs
        for (i in 0 until orbCount) {
            val o = orbs[i]
            var y = o.y - o.speed * t
            y -= kotlin.math.floor(y).toFloat()           // wrap 0..1 upward
            val x = (o.x + o.swayAmp * sin(t * o.swaySpeed + o.phase))
            val r = o.r * w
            val col = if (o.useSecond) accent2 else accent
            dotPaint.colorFilter = PorterDuffColorFilter(col, PorterDuff.Mode.SRC_IN)
            dotPaint.alpha = (o.alpha * 255f).toInt().coerceIn(0, 255)
            dst.set(x * w - r, (1f - y) * h - r, x * w + r, (1f - y) * h + r)
            canvas.drawBitmap(softDot, srcRect, dst, dotPaint)
        }

        // warm rising embers
        if (sparksOn) {
            for (em in embers) {
                var y = em.y - em.speed * t
                y -= kotlin.math.floor(y).toFloat()
                val flick = 0.5f + 0.5f * sin(t * em.flick + em.phase)
                val x = em.x + em.swayAmp * sin(t * em.swaySpeed + em.phase)
                val r = em.r * w
                dotPaint.colorFilter = PorterDuffColorFilter(accent2, PorterDuff.Mode.SRC_IN)
                dotPaint.alpha = (em.alpha * flick * 255f).toInt().coerceIn(0, 255)
                dst.set(x * w - r, (1f - y) * h - r, x * w + r, (1f - y) * h + r)
                canvas.drawBitmap(softDot, srcRect, dst, dotPaint)
            }
        }

        // fireworks (victory) + one-off celebrations
        if (fireworksOn) {
            if (t >= nextBurstT) {
                bursts.add(Burst(rnd, rnd.nextFloat() * 0.7f + 0.15f, rnd.nextFloat() * 0.35f + 0.12f, festive(rnd), t))
                nextBurstT = t + 0.9f + rnd.nextFloat() * 0.9f
                if (bursts.size > 6) bursts.removeAt(0)
            }
        }
        if (bursts.isNotEmpty()) {
            val it = bursts.iterator()
            while (it.hasNext()) {
                val b = it.next()
                val age = t - b.t0
                if (age > b.life) { it.remove(); continue }
                val prog = age / b.life
                val fade = 1f - prog
                val reach = (1f - kotlin.math.exp(-age * 3.2f))
                for (pt in b.parts) {
                    val px = b.cx + pt.dx * reach * b.spread
                    val py = b.cy + pt.dy * reach * b.spread + 0.12f * age * age
                    val r = b.dotR * w * (0.6f + 0.4f * fade)
                    dotPaint.colorFilter = PorterDuffColorFilter(b.color, PorterDuff.Mode.SRC_IN)
                    dotPaint.alpha = (fade * 255f).toInt().coerceIn(0, 255)
                    dst.set(px * w - r, py * h - r, px * w + r, py * h + r)
                    canvas.drawBitmap(softDot, srcRect, dst, dotPaint)
                }
            }
        }

        scheduleNext()
    }

    private fun scheduleNext() {
        if (running) postInvalidateOnAnimation()
    }

    // ---------- particle types ----------

    private class Orb(r: Random) {
        val x = r.nextFloat()
        val y = r.nextFloat()
        val r = 0.03f + r.nextFloat() * 0.10f
        val speed = 0.012f + r.nextFloat() * 0.05f
        val phase = r.nextFloat() * 6.28f
        val swaySpeed = 0.15f + r.nextFloat() * 0.4f
        val swayAmp = 0.01f + r.nextFloat() * 0.04f
        val alpha = 0.05f + r.nextFloat() * 0.14f
        val useSecond = r.nextBoolean()
    }

    private class Star(r: Random) {
        val x = r.nextFloat()
        val y = r.nextFloat() * 0.9f
        val size = 1.2f + r.nextFloat() * 2.2f
        val baseAlpha = 0.4f + r.nextFloat() * 0.6f
        val twSpeed = 0.8f + r.nextFloat() * 2.6f
        val phase = r.nextFloat() * 6.28f
        val warm = r.nextFloat() < 0.35f
    }

    private class Ember(r: Random) {
        val x = r.nextFloat()
        val y = r.nextFloat()
        val r = 0.006f + r.nextFloat() * 0.014f
        val speed = 0.06f + r.nextFloat() * 0.12f
        val phase = r.nextFloat() * 6.28f
        val flick = 3f + r.nextFloat() * 5f
        val swaySpeed = 0.5f + r.nextFloat() * 1.2f
        val swayAmp = 0.01f + r.nextFloat() * 0.03f
        val alpha = 0.35f + r.nextFloat() * 0.4f
    }

    private class Burst(r: Random, val cx: Float, val cy: Float, val color: Int, val t0: Float) {
        val life = 1.1f + r.nextFloat() * 0.5f
        val spread = 0.14f + r.nextFloat() * 0.10f
        val dotR = 0.008f + r.nextFloat() * 0.006f
        val parts: Array<Part>

        init {
            val n = 22 + r.nextInt(12)
            parts = Array(n) {
                val a = (it.toFloat() / n) * 6.2832f + r.nextFloat() * 0.25f
                val sp = 0.6f + r.nextFloat() * 0.6f
                Part(cos(a) * sp, sin(a) * sp)
            }
        }
    }

    private class Part(val dx: Float, val dy: Float)

    // ---------- colour helpers ----------

    private fun festive(r: Random): Int {
        val palette = intArrayOf(
            0xFFFFD700.toInt(), 0xFFFF5E5E.toInt(), 0xFF5EEAFF.toInt(),
            0xFFB980FF.toInt(), 0xFF7DFFA8.toInt(), 0xFFFFFFFF.toInt()
        )
        return palette[r.nextInt(palette.size)]
    }

    private fun easeInOut(x: Float): Float =
        if (x < 0.5f) 2f * x * x else 1f - (-2f * x + 2f) * (-2f * x + 2f) / 2f

    companion object {
        fun lerp(a: Int, b: Int, t: Float): Int {
            val tc = t.coerceIn(0f, 1f)
            val ar = Color.red(a); val ag = Color.green(a); val ab = Color.blue(a); val aa = Color.alpha(a)
            val br = Color.red(b); val bg = Color.green(b); val bb = Color.blue(b); val ba = Color.alpha(b)
            return Color.argb(
                (aa + (ba - aa) * tc).toInt(),
                (ar + (br - ar) * tc).toInt(),
                (ag + (bg - ag) * tc).toInt(),
                (ab + (bb - ab) * tc).toInt()
            )
        }

        fun withAlpha(color: Int, a: Int): Int =
            Color.argb(a.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }
}
