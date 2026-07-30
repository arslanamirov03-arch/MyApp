package com.discipline.hoeren.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.discipline.hoeren.core.Plan
import com.discipline.hoeren.core.PlanClock
import com.discipline.hoeren.core.formatHm
import com.discipline.hoeren.core.formatLong
import com.discipline.hoeren.core.formatSigned
import com.discipline.hoeren.ui.components.Banner
import com.discipline.hoeren.ui.components.Panel
import com.discipline.hoeren.ui.components.StarRing
import com.discipline.hoeren.ui.components.StatRow
import com.discipline.hoeren.ui.theme.Gold
import com.discipline.hoeren.ui.theme.GreenPlan
import com.discipline.hoeren.ui.theme.InkLine
import com.discipline.hoeren.ui.theme.Paper
import com.discipline.hoeren.ui.theme.PaperDim
import com.discipline.hoeren.ui.theme.RedBanner

@Composable
fun StatsScreen(state: AppState) {
    val sessions = state.store.sessions
    val today = if (state.phase == PlanClock.Phase.BEFORE) 0 else state.today

    val total = Plan.totalDone(sessions)
    val progress = total.toFloat() / Plan.GOAL_MIN
    val delta = Plan.paceDeltaMin(sessions, maxOf(today, 1))
    val (bestDay, bestMin) = Plan.bestDay(sessions)
    val forecast = Plan.forecastDay(sessions, maxOf(today, 1))

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Banner("Сводка выполнения")

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                StarRing(progress = progress) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Всего".uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = PaperDim,
                        )
                        Text(
                            text = formatHm(total),
                            style = MaterialTheme.typography.displayLarge,
                            color = if (total >= Plan.GOAL_MIN) Gold else Paper,
                        )
                        Text(
                            text = "из ${Plan.GOAL_MIN / 60} часов",
                            style = MaterialTheme.typography.titleMedium,
                            color = PaperDim,
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = Gold,
                        )
                    }
                }
            }

            Panel("Показатели") {
                StatRow("Выработано", formatLong(total), valueColor = Gold)
                StatRow("Осталось", formatLong(Plan.remainingTotal(sessions)))
                StatRow(
                    if (delta >= 0) "Опережение графика" else "Отставание от графика",
                    formatSigned(delta),
                    valueColor = if (delta >= 0) GreenPlan else RedBanner,
                )
                StatRow("Норма на сегодня", formatHm(Plan.normForDay(sessions, maxOf(today, 1))))
                StatRow("Средний темп в день", formatHm(Plan.averagePerDay(sessions, maxOf(today, 1))))
                StatRow("Ударных дней подряд", "${Plan.streak(sessions, today)}", valueColor = Gold)
                StatRow("Прогулов", "${Plan.missedDays(sessions, today)}", valueColor = RedBanner)
                StatRow(
                    "Лучший день",
                    if (bestDay == 0) "—" else "$bestDay августа · ${formatHm(bestMin)}",
                )
                StatRow(
                    "Прогноз закрытия",
                    when {
                        total >= Plan.GOAL_MIN -> "план закрыт"
                        forecast == 0 -> "темпа не хватает"
                        else -> "$forecast августа"
                    },
                    valueColor = if (forecast == 0 && total < Plan.GOAL_MIN) RedBanner else GreenPlan,
                )
                StatRow("Всего сессий", "${sessions.size}")
            }

            Panel("Выработка по дням") {
                Text(
                    text = "Высота столбца — минуты за день. Золотая линия — уровень исходной " +
                        "нормы ${formatHm(Plan.normForDay(emptyList(), 1))}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PaperDim,
                )
                Spacer(Modifier.height(10.dp))
                DailyChart(state, today)
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Столбчатая диаграмма выработки: по столбцу на каждый день августа.
 * Нажатие на столбец открывает редактор этого дня.
 */
@Composable
private fun DailyChart(state: AppState, today: Int) {
    val sessions = state.store.sessions
    val baseNorm = Plan.normForDay(emptyList(), 1)
    val maxMin = maxOf(sessions.maxOfOrNull { Plan.doneOn(sessions, it.day) } ?: 0, baseNorm * 2)

    Box(Modifier.fillMaxWidth()) {
        // отметка исходной нормы
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = (120 * (1f - baseNorm.toFloat() / maxMin)).dp)
                .height(1.dp)
                .background(Gold.copy(alpha = 0.5f)),
        )

        Row(
            Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (day in 1..Plan.DAYS) {
                val done = Plan.doneOn(sessions, day)
                val status = Plan.statusOf(sessions, day, today)
                val frac = (done.toFloat() / maxMin).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { state.editorDay = day },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(if (frac > 0f) frac else 0.012f)
                            .background(if (done > 0) barColor(status) else InkLine),
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (label in listOf("1", "8", "15", "22", "31")) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = PaperDim,
                fontSize = 10.sp,
            )
        }
    }
}
