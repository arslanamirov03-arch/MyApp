package com.arslan.dlsdetox

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/**
 * A single day tile in the 30-day grid. Shows the day number and date, and
 * reflects one of three states: locked (still in the future), available
 * (can be ticked) and done (marked clean — filled with the tier gradient
 * and a check mark).
 */
class DayCellView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class State { FUTURE, AVAILABLE, DONE }

    private var dayNumber = 1
    private var dateLabel = ""
    private var state = State.FUTURE
    private var isToday = false
    private var accent = 0xFF7C8798.toInt()
    private var accent2 = 0xFFAAB4C4.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
    }
    private val check = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val rect = RectF()
    private val glowRect = RectF()
    private val checkPath = Path()

    fun setData(
        dayNumber: Int, dateLabel: String, state: State,
        isToday: Boolean, accent: Int, accent2: Int
    ) {
        this.dayNumber = dayNumber
        this.dateLabel = dateLabel
        this.state = state
        this.isToday = isToday
        this.accent = accent
        this.accent2 = accent2
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = w * 0.22f
        val inset = w * 0.06f
        rect.set(inset, inset, w - inset, h - inset)

        when (state) {
            State.DONE -> {
                // outer glow
                glowRect.set(rect.left - inset, rect.top - inset, rect.right + inset, rect.bottom + inset)
                glow.shader = LinearGradient(
                    0f, 0f, 0f, h,
                    AnimatedBackgroundView.withAlpha(accent, 120),
                    AnimatedBackgroundView.withAlpha(accent2, 120),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(glowRect, r * 1.2f, r * 1.2f, glow)

                fill.shader = LinearGradient(
                    rect.left, rect.top, rect.right, rect.bottom,
                    accent, accent2, Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(rect, r, r, fill)
                fill.shader = null

                // check mark, upper area
                check.color = Color.WHITE
                check.strokeWidth = w * 0.07f
                checkPath.reset()
                val cx = w * 0.5f
                val cy = h * 0.40f
                val u = w * 0.16f
                checkPath.moveTo(cx - u * 1.15f, cy)
                checkPath.lineTo(cx - u * 0.2f, cy + u * 0.9f)
                checkPath.lineTo(cx + u * 1.25f, cy - u * 0.85f)
                canvas.drawPath(checkPath, check)

                numPaint.color = 0xFF10121A.toInt()
                numPaint.textSize = h * 0.20f
                canvas.drawText(dayNumber.toString(), w * 0.5f, h * 0.74f, numPaint)

                datePaint.color = 0xCC10121A.toInt()
                datePaint.textSize = h * 0.115f
                canvas.drawText(dateLabel, w * 0.5f, h * 0.90f, datePaint)
            }

            State.AVAILABLE -> {
                fill.shader = null
                fill.color = 0x1FFFFFFF
                canvas.drawRoundRect(rect, r, r, fill)

                border.strokeWidth = if (isToday) w * 0.05f else w * 0.02f
                border.color = if (isToday) accent2 else 0x40FFFFFF
                canvas.drawRoundRect(rect, r, r, border)

                numPaint.color = Color.WHITE
                numPaint.textSize = h * 0.26f
                canvas.drawText(dayNumber.toString(), w * 0.5f, h * 0.50f, numPaint)

                datePaint.color = 0xAAFFFFFF.toInt()
                datePaint.textSize = h * 0.125f
                canvas.drawText(dateLabel, w * 0.5f, h * 0.72f, datePaint)

                if (isToday) {
                    numPaint.color = accent2
                    numPaint.textSize = h * 0.11f
                    canvas.drawText("сегодня", w * 0.5f, h * 0.88f, numPaint)
                } else {
                    // small hint dot
                    fill.color = 0x33FFFFFF
                    canvas.drawCircle(w * 0.5f, h * 0.86f, w * 0.03f, fill)
                }
            }

            State.FUTURE -> {
                fill.shader = null
                fill.color = 0x0DFFFFFF
                canvas.drawRoundRect(rect, r, r, fill)

                border.strokeWidth = w * 0.015f
                border.color = 0x1AFFFFFF
                canvas.drawRoundRect(rect, r, r, border)

                numPaint.color = 0x55FFFFFF
                numPaint.textSize = h * 0.24f
                canvas.drawText(dayNumber.toString(), w * 0.5f, h * 0.50f, numPaint)

                datePaint.color = 0x44FFFFFF
                datePaint.textSize = h * 0.12f
                canvas.drawText(dateLabel, w * 0.5f, h * 0.72f, datePaint)
            }
        }
    }
}
