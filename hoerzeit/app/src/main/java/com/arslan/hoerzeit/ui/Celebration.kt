package com.arslan.hoerzeit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arslan.hoerzeit.data.Progress
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/** Что показать в мини-празднике после нажатия «Стоп». */
data class Celebration(
    val sessionMs: Long,
    val progress: Progress
)

private val PHRASES = listOf(
    "Каждая минута оседает в ухе.",
    "Ты стал ближе к немецкому на слух.",
    "Регулярность важнее длины.",
    "Уши уже привыкают. Продолжай.",
    "Так и строится понимание — по чуть-чуть.",
    "Сегодня ты сделал больше, чем вчера.",
    "Тихая работа, заметный результат.",
    "Ohne Fleiß kein Preis — без труда нет награды."
)

private val CONFETTI_COLORS = listOf(C.Clay, C.ClaySoft, C.Sand, C.Sky, C.Rose, C.Good)

@Composable
fun CelebrationOverlay(
    data: Celebration?,
    onDismiss: () -> Unit
) {
    // Держим последний показанный результат, чтобы карточка успела плавно исчезнуть.
    var last by remember { mutableStateOf<Celebration?>(null) }
    if (data != null) last = data

    AnimatedVisibility(
        visible = data != null,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(160))
    ) {
        val shown = last
        if (shown != null) {
            BackHandler(onBack = onDismiss)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(C.Ink.copy(alpha = 0.28f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    ),
                contentAlignment = Alignment.Center
            ) {
                Confetti(Modifier.fillMaxSize())

                val pop = remember { Animatable(0.9f) }
                LaunchedEffect(Unit) {
                    pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
                }
                Box(Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }) {
                    CelebrationCard(shown, onDismiss)
                }
            }
        }
    }
}

@Composable
private fun CelebrationCard(data: Celebration, onDismiss: () -> Unit) {
    val p = data.progress
    val phrase = remember(data) { PHRASES.random() }

    val percent by animateFloatAsState(
        targetValue = p.percent,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "percent"
    )

    Column(
        modifier = Modifier
            .padding(horizontal = 26.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(C.Cream)
            .border(1.dp, Color.White, RoundedCornerShape(30.dp))
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = when {
                p.goalReached -> "60 часов. Готово!"
                p.dailyDone -> "День закрыт"
                else -> "Записано"
            },
            style = MaterialTheme.typography.labelSmall,
            color = C.Muted
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "+${formatHm(data.sessionMs)}",
            fontSize = 40.sp,
            fontWeight = FontWeight.Light,
            color = C.ClayDeep,
            letterSpacing = (-1).sp
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = phrase,
            style = MaterialTheme.typography.bodyMedium,
            color = C.InkSoft,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(22.dp))

        // Дневная цель
        RowStat(
            left = "Сегодня",
            right = "${formatHm(p.todayMs)} из 30 мин",
            accent = if (p.dailyDone) C.Good else C.Ink
        )
        Spacer(Modifier.height(8.dp))
        ThinBar(
            fraction = p.todayFraction,
            height = 7,
            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                if (p.dailyDone) listOf(C.Good.copy(alpha = 0.7f), C.Good) else listOf(C.ClaySoft, C.Clay)
            )
        )

        Spacer(Modifier.height(18.dp))

        // Общий прогресс
        RowStat(left = "Всего", right = "${formatHm(p.totalMs)} из 60 ч")
        Spacer(Modifier.height(8.dp))
        ThinBar(fraction = p.fraction, height = 7)

        Spacer(Modifier.height(18.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile(
                value = "${"%.1f".format(percent)}%",
                label = "пройдено",
                accent = C.ClayDeep,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                value = formatHm(p.leftMs),
                label = "осталось",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                value = "${p.streakDays} ${plural(p.streakDays, "день", "дня", "дней")}",
                label = "серия",
                accent = if (p.streakDays > 0) C.Good else C.Ink,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(22.dp))

        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.Clay, contentColor = Color.White)
        ) {
            Text("Отлично", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun RowStat(left: String, right: String, accent: Color = C.Ink) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(left, style = MaterialTheme.typography.bodyMedium, color = C.Muted)
        Spacer(Modifier.weight(1f))
        Text(right, style = MaterialTheme.typography.bodyLarge, color = accent)
    }
}

private data class Flake(
    val x: Float,
    val delay: Float,
    val size: Float,
    val color: Color,
    val sway: Float,
    val spin: Float,
    val speed: Float
)

/** Лёгкое конфетти: 46 частиц, одна проходка сверху вниз. */
@Composable
private fun Confetti(modifier: Modifier = Modifier) {
    val flakes = remember {
        val random = Random(System.currentTimeMillis())
        List(46) {
            Flake(
                x = random.nextFloat(),
                delay = random.nextFloat() * 0.35f,
                size = 5f + random.nextFloat() * 7f,
                color = CONFETTI_COLORS[random.nextInt(CONFETTI_COLORS.size)],
                sway = 0.02f + random.nextFloat() * 0.05f,
                spin = (random.nextFloat() - 0.5f) * 8f,
                speed = 0.85f + random.nextFloat() * 0.4f
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 2800, easing = LinearEasing))
    }

    Canvas(modifier) {
        val t = progress.value
        flakes.forEach { flake ->
            val local = ((t - flake.delay) * flake.speed).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) return@forEach
            val fade = if (local > 0.78f) (1f - (local - 0.78f) / 0.22f) else 1f
            val cx = size.width * (flake.x + flake.sway * sin(local * 6f * PI.toFloat()))
            val cy = size.height * (local * 1.15f) - 40f
            val w = flake.size.dp.toPx()
            val h = w * (0.5f + 0.5f * kotlin.math.abs(sin(local * flake.spin * PI.toFloat())))
            drawRoundRect(
                color = flake.color.copy(alpha = 0.9f * fade),
                topLeft = Offset(cx - w / 2f, cy - h / 2f),
                size = Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.3f, w * 0.3f)
            )
        }
    }
}
