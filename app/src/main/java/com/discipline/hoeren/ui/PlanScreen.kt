package com.discipline.hoeren.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.discipline.hoeren.core.Plan
import com.discipline.hoeren.core.PlanClock
import com.discipline.hoeren.core.formatHm
import com.discipline.hoeren.ui.components.Banner
import com.discipline.hoeren.ui.components.DayCell
import com.discipline.hoeren.ui.components.Panel
import com.discipline.hoeren.ui.components.StatRow
import com.discipline.hoeren.ui.components.statusColor
import com.discipline.hoeren.ui.components.statusLabel
import com.discipline.hoeren.ui.theme.InkLine
import com.discipline.hoeren.ui.theme.Gold
import com.discipline.hoeren.ui.theme.GreenPlan
import com.discipline.hoeren.ui.theme.Paper
import com.discipline.hoeren.ui.theme.PaperDim
import com.discipline.hoeren.ui.theme.RedBanner

@Composable
fun PlanScreen(state: AppState) {
    val sessions = state.store.sessions
    // До 1 августа ни один день ещё не наступил, поэтому «сегодня» = 0:
    // так все дни показываются как плановые, а не как прогулы.
    val today = if (state.phase == PlanClock.Phase.BEFORE) 0 else state.today

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Banner("Август 2026 — сводный план")

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

            Panel("Состояние плана") {
                StatRow(
                    "Выработано",
                    "${formatHm(Plan.totalDone(sessions))} из ${formatHm(Plan.GOAL_MIN)}",
                    valueColor = Gold,
                )
                StatRow("Осталось", formatHm(Plan.remainingTotal(sessions)))
                StatRow("Прогулов", "${Plan.missedDays(sessions, today)}", valueColor = RedBanner)
                StatRow("Норма на сегодня", formatHm(Plan.normForDay(sessions, maxOf(today, 1))))
            }

            Text(
                text = "Нажми на любой день, чтобы внести или исправить практику.".uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = PaperDim,
            )

            CalendarGrid(state, today)

            Legend()
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CalendarGrid(state: AppState, today: Int) {
    val sessions = state.store.sessions

    Column {
        // Шапка недели
        Row(Modifier.fillMaxWidth()) {
            for (w in 0 until 7) {
                Text(
                    text = weekdayShort(w),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (w >= 5) Gold else PaperDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(bottom = 6.dp),
                )
            }
        }

        // 1 августа 2026 — суббота, поэтому в первой строке пять пустых клеток
        val lead = Plan.weekdayOf(1)
        val cells = lead + Plan.DAYS
        val rows = (cells + 6) / 7

        for (r in 0 until rows) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (c in 0 until 7) {
                    val index = r * 7 + c
                    val day = index - lead + 1
                    if (day in 1..Plan.DAYS) {
                        DayCell(
                            day = day,
                            status = Plan.statusOf(sessions, day, today),
                            doneMin = Plan.doneOn(sessions, day),
                            normMin = Plan.normForDay(sessions, day),
                            isToday = day == today,
                            onClick = { state.editorDay = day },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Box(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend() {
    Panel("Обозначения") {
        LegendRow(Plan.DayStatus.DONE)
        LegendRow(Plan.DayStatus.OVER)
        LegendRow(Plan.DayStatus.UNDER)
        LegendRow(Plan.DayStatus.MISSED)
        LegendRow(Plan.DayStatus.FUTURE)
    }
}

@Composable
private fun LegendRow(status: Plan.DayStatus) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(14.dp)
                .background(statusColor(status))
                // рамка нужна, иначе образец «План» сливается с фоном панели
                .border(1.dp, InkLine, RectangleShape),
        )
        Text(
            text = statusLabel(status).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Paper,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/** Цвет для полос диаграммы в сводке. */
fun barColor(status: Plan.DayStatus): Color = when (status) {
    Plan.DayStatus.OVER -> Gold
    Plan.DayStatus.DONE -> GreenPlan
    else -> RedBanner
}
