package com.arslan.hoerzeit.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arslan.hoerzeit.data.Goal
import com.arslan.hoerzeit.data.Progress
import kotlinx.coroutines.delay

@Composable
fun TodayScreen(
    progress: Progress,
    activeStart: Long?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onManual: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Пока сессия идёт — тикаем раз в секунду. Время считается от метки старта,
    // поэтому оно верное даже после сворачивания приложения.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activeStart) {
        while (activeStart != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val runningMs = if (activeStart != null) (now - activeStart).coerceAtLeast(0L) else 0L
    val liveTotal = progress.totalMs + runningMs
    val liveToday = progress.todayMs + runningMs
    val liveFraction = (liveTotal.toFloat() / Goal.TOTAL_MS).coerceIn(0f, 1f)
    val liveTodayFraction = (liveToday.toFloat() / Goal.DAILY_MS).coerceIn(0f, 1f)
    val animatedPercent by animateFloatAsState(
        targetValue = liveFraction * 100f,
        animationSpec = tween(600),
        label = "percent"
    )

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(6.dp))

        // --- Кольцо ---------------------------------------------------------
        ProgressRing(
            fraction = liveFraction,
            strokeWidth = 15,
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .aspectRatio(1f)
                .align(Alignment.CenterHorizontally)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(
                    targetState = activeStart != null,
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                    label = "center"
                ) { running ->
                    if (running) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "ИДЁТ ПРАКТИКА",
                                style = MaterialTheme.typography.labelSmall,
                                color = C.Clay
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                formatClock(runningMs),
                                fontSize = 44.sp,
                                fontWeight = FontWeight.Light,
                                color = C.Ink,
                                letterSpacing = (-1).sp
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "%.1f%%".format(animatedPercent),
                                fontSize = 52.sp,
                                fontWeight = FontWeight.Light,
                                color = C.Ink,
                                letterSpacing = (-2).sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "${formatHm(liveTotal)}  ·  из 60 ч",
                    style = MaterialTheme.typography.bodyLarge,
                    color = C.InkSoft
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "осталось ${formatHm((Goal.TOTAL_MS - liveTotal).coerceAtLeast(0L))}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = C.Muted
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // --- Сегодня --------------------------------------------------------
        SoftCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Сегодня", style = MaterialTheme.typography.titleMedium, color = C.Ink)
                Spacer(Modifier.weight(1f))
                Text(
                    if (liveToday >= Goal.DAILY_MS) "цель дня закрыта"
                    else "минимум 30 мин",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (liveToday >= Goal.DAILY_MS) C.Good else C.Muted
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                formatHm(liveToday),
                fontSize = 30.sp,
                fontWeight = FontWeight.Light,
                color = if (liveToday >= Goal.DAILY_MS) C.Good else C.Ink
            )
            Spacer(Modifier.height(12.dp))
            ThinBar(
                fraction = liveTodayFraction,
                height = 8,
                brush = if (liveToday >= Goal.DAILY_MS)
                    Brush.horizontalGradient(listOf(C.Good.copy(alpha = 0.65f), C.Good))
                else Brush.horizontalGradient(listOf(C.ClaySoft, C.Clay))
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(
                value = "${progress.streakDays} ${plural(progress.streakDays, "день", "дня", "дней")}",
                label = "серия",
                accent = if (progress.streakDays > 0) C.Good else C.Ink,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                value = "${progress.activeDays}",
                label = "дней практики",
                modifier = Modifier.weight(1f)
            )
            StatTile(
                value = daysLeftEstimate(liveTotal),
                label = "по 30 мин/день",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(22.dp))

        // --- Кнопка ---------------------------------------------------------
        BigActionButton(running = activeStart != null, onClick = { if (activeStart != null) onStop() else onStart() })

        Spacer(Modifier.height(10.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, C.Line, RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.45f))
                .clickable(onClick = onManual)
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Добавить время вручную", style = MaterialTheme.typography.bodyLarge, color = C.InkSoft)
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = if (activeStart != null)
                "Можно свернуть приложение — время продолжает идти."
            else
                "Без дедлайнов. Сколько угодно сессий в день — главное, чтобы набралось 30 минут.",
            style = MaterialTheme.typography.bodyMedium,
            color = C.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BigActionButton(running: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "press"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(66.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (running) Brush.horizontalGradient(listOf(C.Ink, Color(0xFF3A3833)))
                else Brush.horizontalGradient(listOf(C.Clay, C.ClayDeep))
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = running,
            transitionSpec = { fadeIn(tween(140)) togetherWith fadeOut(tween(110)) },
            label = "action"
        ) { isRunning ->
            Text(
                text = if (isRunning) "Стоп" else "Старт",
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}

/** Сколько дней осталось, если держать ровно 30 минут в день. */
private fun daysLeftEstimate(totalMs: Long): String {
    val leftMs = (Goal.TOTAL_MS - totalMs).coerceAtLeast(0L)
    if (leftMs == 0L) return "готово"
    val days = Math.ceil(leftMs.toDouble() / Goal.DAILY_MS).toInt()
    return "$days ${plural(days, "день", "дня", "дней")}"
}
