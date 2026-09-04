package com.arslan.hoerzeit.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Живой фон: несколько мягких цветных пятен очень медленно плывут друг сквозь друга.
 * Рисуется одним Canvas без слоёв и теней — дёшево и не тормозит.
 */
@Composable
fun LivingBackground(modifier: Modifier = Modifier, intensity: Float = 1f) {
    val transition = rememberInfiniteTransition(label = "living")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(durationMillis = 46_000, easing = LinearEasing)),
        label = "phase"
    )

    Canvas(modifier.fillMaxSize()) {
        drawRect(C.Cream)
        val w = size.width
        val h = size.height

        blob(
            center = Offset(w * (0.24f + 0.20f * sin(t)), h * (0.16f + 0.10f * cos(t * 0.83f))),
            radius = w * 0.92f,
            color = C.Peach,
            alpha = 0.58f * intensity
        )
        blob(
            center = Offset(w * (0.86f + 0.16f * cos(t * 0.71f + 1.1f)), h * (0.30f + 0.12f * sin(t * 0.64f))),
            radius = w * 0.80f,
            color = C.Sand,
            alpha = 0.62f * intensity
        )
        blob(
            center = Offset(w * (0.72f + 0.22f * sin(t * 0.55f + 2.4f)), h * (0.84f + 0.10f * cos(t * 0.77f))),
            radius = w * 0.95f,
            color = C.Rose,
            alpha = 0.50f * intensity
        )
        blob(
            center = Offset(w * (0.12f + 0.18f * cos(t * 0.47f + 3.7f)), h * (0.74f + 0.13f * sin(t * 0.52f))),
            radius = w * 0.78f,
            color = C.Sky,
            alpha = 0.40f * intensity
        )
    }
}

private fun DrawScope.blob(center: Offset, radius: Float, color: Color, alpha: Float) {
    if (radius <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(
            0f to color.copy(alpha = alpha),
            0.55f to color.copy(alpha = alpha * 0.45f),
            1f to Color.Transparent,
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}
