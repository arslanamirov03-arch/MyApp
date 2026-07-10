package com.arslan.dlsdetox

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Circular progress ring: the arc shows how many of the 30 days are marked
 * clean, and the centre shows the current streak as the hero number.
 */
class StreakRingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var progress = 0f          // 0..1 (marked / total)
    private var shown = 0f             // animated progress
    private var streak = 0
    private var completed = 0
    private var total = Challenge.TOTAL_DAYS
    private var accent = 0xFF7C8798.toInt()
    private var accent2 = 0xFFAAB4C4.toInt()

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glowArc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val bigText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val smallText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val tinyText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val oval = RectF()

    fun setData(streak: Int, completed: Int, total: Int, accent: Int, accent2: Int) {
        this.streak = streak
        this.completed = completed
        this.total = total
        this.accent = accent
        this.accent2 = accent2
        this.progress = if (total > 0) completed.toFloat() / total else 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val stroke = min(w, h) * 0.075f
        val pad = stroke * 1.4f
        val d = min(w, h) - pad * 2
        val cx = w / 2f
        val cy = h / 2f
        oval.set(cx - d / 2, cy - d / 2, cx + d / 2, cy + d / 2)

        // animate the fill
        shown += (progress - shown) * 0.15f
        if (kotlin.math.abs(progress - shown) < 0.002f) shown = progress

        track.strokeWidth = stroke
        track.color = 0x22FFFFFF
        canvas.drawArc(oval, 0f, 360f, false, track)

        val sweep = 360f * shown
        val grad = SweepGradient(cx, cy, intArrayOf(accent, accent2, accent), null)
        arc.shader = grad
        arc.strokeWidth = stroke

        glowArc.shader = grad
        glowArc.strokeWidth = stroke * 2.6f
        glowArc.alpha = 60

        canvas.save()
        canvas.rotate(-90f, cx, cy)
        if (sweep > 0.5f) {
            canvas.drawArc(oval, 0f, sweep, false, glowArc)
            canvas.drawArc(oval, 0f, sweep, false, arc)
        }
        canvas.restore()

        // centre texts
        bigText.textSize = d * 0.34f
        bigText.color = accent2
        val baseline = cy + bigText.textSize * 0.34f
        canvas.drawText(streak.toString(), cx, baseline, bigText)

        smallText.textSize = d * 0.085f
        canvas.drawText("дней подряд", cx, baseline + d * 0.15f, smallText)

        tinyText.textSize = d * 0.075f
        tinyText.color = accent2
        canvas.drawText("отмечено  $completed / $total", cx, cy - d * 0.24f, tinyText)

        if (shown != progress) postInvalidateOnAnimation()
    }
}
