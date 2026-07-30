package com.discipline.hoeren.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import com.discipline.hoeren.ui.theme.Gold
import com.discipline.hoeren.ui.theme.GoldDim
import com.discipline.hoeren.ui.theme.GreenPlan
import com.discipline.hoeren.ui.theme.InkLine
import com.discipline.hoeren.ui.theme.RedBanner
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Путь пятиконечной звезды с вершиной вверх. */
fun starPath(cx: Float, cy: Float, outer: Float): Path {
    val inner = outer * 0.382f
    val path = Path()
    for (i in 0 until 5) {
        val ao = Math.toRadians(-90.0 + i * 72).toFloat()
        val ai = Math.toRadians(-90.0 + i * 72 + 36).toFloat()
        if (i == 0) path.moveTo(cx + outer * cos(ao), cy + outer * sin(ao))
        else path.lineTo(cx + outer * cos(ao), cy + outer * sin(ao))
        path.lineTo(cx + inner * cos(ai), cy + inner * sin(ai))
    }
    path.close()
    return path
}

private fun DrawScope.drawRing(progress: Float, stroke: Float) {
    val d = min(size.width, size.height) - stroke
    val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
    val arcSize = Size(d, d)

    // Дорожка
    drawArc(
        color = InkLine,
        startAngle = -90f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = stroke),
    )

    // Основная дуга: до 100% нормы
    val main = progress.coerceIn(0f, 1f)
    if (main > 0f) {
        drawArc(
            color = if (progress >= 1f) GreenPlan else RedBanner,
            startAngle = -90f,
            sweepAngle = 360f * main,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke),
        )
    }

    // Перевыполнение рисуется золотом поверх, вторым кругом
    val extra = (progress - 1f).coerceIn(0f, 1f)
    if (extra > 0f) {
        drawArc(
            color = Gold,
            startAngle = -90f,
            sweepAngle = 360f * extra,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke * 0.45f),
        )
    }
}

/**
 * Кольцо выполнения с золотой звездой в центре как подложкой.
 *
 * Заполнение сверх 100% рисуется отдельной золотой дугой — перевыполнение видно
 * сразу, а не теряется в «полном» кольце.
 */
@Composable
fun StarRing(
    progress: Float,
    modifier: Modifier = Modifier,
    ringSize: Dp = 210.dp,
    strokeWidth: Dp = 13.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(ringSize)) {
            drawRing(progress, strokeWidth.toPx())
            // звезда-водяной знак под цифрами
            drawPath(
                path = starPath(size.width / 2f, size.height / 2f, size.minDimension * 0.30f),
                color = if (progress >= 1f) Gold.copy(alpha = 0.22f) else GoldDim.copy(alpha = 0.16f),
            )
        }
        content()
    }
}
