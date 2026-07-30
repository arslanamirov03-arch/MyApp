package com.discipline.hoeren.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.discipline.hoeren.core.Plan
import com.discipline.hoeren.core.PlanClock
import com.discipline.hoeren.core.Session
import com.discipline.hoeren.core.formatClock
import com.discipline.hoeren.core.formatHm
import com.discipline.hoeren.core.formatLong
import com.discipline.hoeren.core.formatSigned
import com.discipline.hoeren.ui.components.Banner
import com.discipline.hoeren.ui.components.BtnKind
import com.discipline.hoeren.ui.components.ClockDialog
import com.discipline.hoeren.ui.components.Panel
import com.discipline.hoeren.ui.components.SovietButton
import com.discipline.hoeren.ui.components.StarRing
import com.discipline.hoeren.ui.components.StatRow
import com.discipline.hoeren.ui.theme.Gold
import com.discipline.hoeren.ui.theme.GreenPlan
import com.discipline.hoeren.ui.theme.Ink
import com.discipline.hoeren.ui.theme.InkLine
import com.discipline.hoeren.ui.theme.InkSoft
import com.discipline.hoeren.ui.theme.Paper
import com.discipline.hoeren.ui.theme.PaperDim
import com.discipline.hoeren.ui.theme.RedBanner
import com.discipline.hoeren.ui.theme.RedDeep

@Composable
fun TodayScreen(state: AppState) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Banner("60 часов за август — план не обсуждается")
        DateHeader(state)

        when (state.phase) {
            PlanClock.Phase.BEFORE -> BeforeStart(state)
            PlanClock.Phase.DURING -> DuringPlan(state)
            PlanClock.Phase.AFTER -> AfterPlan(state)
        }

        QuoteBlock(state.today)
        Spacer(Modifier.height(16.dp))
    }
}

// ----------------------------------------------------------------------

@Composable
private fun DateHeader(state: AppState) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(InkSoft)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = "${weekdayName(state.weekdayToday)}, ${formatDate(state.dayOfMonth, state.month, state.year)}"
                .uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = PaperDim,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = formatClock(state.nowMinute),
                style = MaterialTheme.typography.displayLarge,
                fontSize = 38.sp,
                color = Paper,
            )
            if (state.phase == PlanClock.Phase.DURING) {
                Text(
                    text = "День ${state.today} из ${Plan.DAYS}".uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Gold,
                )
            }
        }
    }
}

// ----------------------------------------------------------------------
// До 1 августа
// ----------------------------------------------------------------------

@Composable
private fun BeforeStart(state: AppState) {
    val left = state.planDate.daysUntilStart
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Panel("Мобилизация", accent = RedDeep) {
            Text(
                text = "До начала".uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = PaperDim,
            )
            Text(
                text = "$left ${pluralDays(left)}",
                style = MaterialTheme.typography.displayLarge,
                color = Gold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Учёт открывается 1 августа 2026 года. С этого дня и по 31 августа " +
                    "надлежит выработать 60 часов активной слуховой практики.",
                style = MaterialTheme.typography.bodyMedium,
                color = Paper,
            )
        }

        Panel("Расчёт нормы") {
            StatRow("Цель периода", formatLong(Plan.GOAL_MIN), valueColor = Gold)
            StatRow("Дней в периоде", "${Plan.DAYS}")
            StatRow("Норма на день", formatHm(Plan.normForDay(emptyList(), 1)), valueColor = Gold)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Норма пересчитывается каждый день: пропуск поднимает её на оставшиеся дни, " +
                    "переработка снижает. Итог фиксирован — 60 часов к 31 августа.",
                style = MaterialTheme.typography.bodyMedium,
                color = PaperDim,
            )
        }
    }
}

// ----------------------------------------------------------------------
// После 31 августа
// ----------------------------------------------------------------------

@Composable
private fun AfterPlan(state: AppState) {
    val sessions = state.store.sessions
    val total = Plan.totalDone(sessions)
    val fulfilled = total >= Plan.GOAL_MIN

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Banner(
            if (fulfilled) "План выполнен" else "План не выполнен",
            color = if (fulfilled) GreenPlan else RedBanner,
        )
        Panel("Итог периода") {
            StatRow("Выработано", formatLong(total), valueColor = if (fulfilled) Gold else Paper)
            StatRow("Цель", formatLong(Plan.GOAL_MIN))
            StatRow(
                if (fulfilled) "Сверх плана" else "Недобор",
                formatLong(kotlin.math.abs(Plan.GOAL_MIN - total)),
                valueColor = if (fulfilled) Gold else RedBanner,
            )
            StatRow("Ударных дней", "${Plan.streak(sessions, Plan.DAYS)}")
            StatRow("Прогулов", "${Plan.missedDays(sessions, Plan.DAYS + 1)}")
        }
        Text(
            text = "Журнал остаётся доступным на вкладках «План» и «Сводка».",
            style = MaterialTheme.typography.bodyMedium,
            color = PaperDim,
        )
    }
}

// ----------------------------------------------------------------------
// 1–31 августа
// ----------------------------------------------------------------------

@Composable
private fun DuringPlan(state: AppState) {
    val store = state.store
    val sessions = store.sessions
    val today = state.today

    val norm = Plan.normForDay(sessions, today)
    val done = Plan.doneOn(sessions, today)
    val left = Plan.remainingOn(sessions, today)
    val progress = if (norm > 0) done.toFloat() / norm else 1f
    val closed = done >= norm

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // --- Кольцо нормы дня ---
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StarRing(progress = progress) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Выполнено".uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = PaperDim,
                    )
                    Text(
                        text = formatHm(done),
                        style = MaterialTheme.typography.displayLarge,
                        color = if (closed) Gold else Paper,
                    )
                    Text(
                        text = "из ${formatHm(norm)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = PaperDim,
                    )
                }
            }
        }

        if (closed) {
            val q = Quotes.victory(today)
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Gold)
                    .padding(14.dp),
            ) {
                Text(
                    text = "Норма дня выполнена".uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Ink,
                )
                Text(
                    text = "«${q.text}»",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text = "— ${q.author}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Panel("Норма дня") {
            StatRow("Норма", formatHm(norm), valueColor = Gold)
            StatRow("Выполнено", formatHm(done))
            StatRow(
                "Осталось",
                if (left == 0) "норма закрыта" else formatHm(left),
                valueColor = if (left == 0) GreenPlan else RedBanner,
            )
        }

        PracticeControls(state)

        SessionJournal(state)

        PlanTotals(state)
    }
}

// ----------------------------------------------------------------------
// СТАРТ / СТОП
// ----------------------------------------------------------------------

@Composable
private fun PracticeControls(state: AppState) {
    val store = state.store
    val open = store.openSession
    var picker by remember { mutableStateOf(PickerTarget.NONE) }

    Panel("Учёт практики", accent = RedDeep) {
        if (open == null) {
            Text(
                text = "Практика не начата. Нажми СТАРТ перед тем, как идти слушать.",
                style = MaterialTheme.typography.bodyMedium,
                color = PaperDim,
            )
            Spacer(Modifier.height(12.dp))
            SovietButton(
                text = "Старт сейчас",
                onClick = { store.startPractice(state.today, state.nowMinute) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            SovietButton(
                text = "Указать время начала",
                onClick = { picker = PickerTarget.START },
                kind = BtnKind.OUTLINE,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(RedDeep)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Практика идёт с".uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Paper.copy(alpha = 0.75f),
                    )
                    Text(
                        text = formatClock(open.startMin),
                        style = MaterialTheme.typography.displayLarge,
                        fontSize = 34.sp,
                        color = Paper,
                    )
                }
                if (open.day != state.today) {
                    Text(
                        text = "начата\nв день ${open.day}".uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Gold,
                        textAlign = TextAlign.End,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SovietButton(
                text = "Стоп сейчас",
                onClick = { store.finishPractice(state.nowMinute) },
                kind = BtnKind.GOLD,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            SovietButton(
                text = "Указать время конца",
                onClick = { picker = PickerTarget.END },
                kind = BtnKind.OUTLINE,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SovietButton(
                    text = "Исправить начало",
                    onClick = { picker = PickerTarget.ADJUST },
                    kind = BtnKind.OUTLINE,
                    modifier = Modifier.weight(1f),
                )
                SovietButton(
                    text = "Отменить",
                    onClick = { store.cancelPractice() },
                    kind = BtnKind.OUTLINE,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    when (picker) {
        PickerTarget.NONE -> Unit
        PickerTarget.START -> ClockDialog(
            title = "Во сколько начал",
            initialMinuteOfDay = state.nowMinute,
            onConfirm = {
                store.startPractice(state.today, it)
                picker = PickerTarget.NONE
            },
            onDismiss = { picker = PickerTarget.NONE },
        )
        PickerTarget.END -> ClockDialog(
            title = "Во сколько закончил",
            initialMinuteOfDay = state.nowMinute,
            onConfirm = {
                store.finishPractice(it)
                picker = PickerTarget.NONE
            },
            onDismiss = { picker = PickerTarget.NONE },
        )
        PickerTarget.ADJUST -> ClockDialog(
            title = "Исправить время начала",
            initialMinuteOfDay = open?.startMin ?: state.nowMinute,
            onConfirm = {
                store.adjustPracticeStart(it)
                picker = PickerTarget.NONE
            },
            onDismiss = { picker = PickerTarget.NONE },
        )
    }
}

private enum class PickerTarget { NONE, START, END, ADJUST }

// ----------------------------------------------------------------------

@Composable
private fun SessionJournal(state: AppState) {
    val todaySessions = state.store.sessionsOf(state.today)

    Panel("Журнал за сегодня") {
        if (todaySessions.isEmpty()) {
            Text(
                text = "Записей нет.",
                style = MaterialTheme.typography.bodyMedium,
                color = PaperDim,
            )
        } else {
            todaySessions.forEachIndexed { i, s ->
                SessionRow(index = i + 1, session = s) { state.editorDay = state.today }
            }
        }
        Spacer(Modifier.height(10.dp))
        SovietButton(
            text = "Исправить журнал дня",
            onClick = { state.editorDay = state.today },
            kind = BtnKind.OUTLINE,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun SessionRow(index: Int, session: Session, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .border(1.dp, InkLine, RectangleShape)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$index.  ${formatClock(session.startMin)} — ${formatClock(session.endMin)}",
            style = MaterialTheme.typography.bodySmall,
            color = Paper,
        )
        Text(
            text = formatLong(session.durationMin),
            style = MaterialTheme.typography.bodySmall,
            color = Gold,
        )
    }
}

// ----------------------------------------------------------------------

@Composable
private fun PlanTotals(state: AppState) {
    val sessions = state.store.sessions
    val total = Plan.totalDone(sessions)
    val delta = Plan.paceDeltaMin(sessions, state.today)

    Panel("Весь план") {
        StatRow("Выработано", "${formatHm(total)} из ${formatHm(Plan.GOAL_MIN)}", valueColor = Gold)
        StatRow("Осталось", formatHm(Plan.remainingTotal(sessions)))
        StatRow("Дней до конца", "${Plan.DAYS - state.today + 1}")
        StatRow(
            if (delta >= 0) "Опережение" else "Отставание",
            formatSigned(delta),
            valueColor = if (delta >= 0) GreenPlan else RedBanner,
        )
    }
}

// ----------------------------------------------------------------------

@Composable
private fun QuoteBlock(day: Int) {
    val q = Quotes.ofDay(day)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(InkSoft)
            .border(1.dp, InkLine, RectangleShape)
            .padding(14.dp),
    ) {
        Text(
            text = "«${q.text}»",
            style = MaterialTheme.typography.bodyLarge,
            color = Paper,
        )
        Text(
            text = "— ${q.author}",
            style = MaterialTheme.typography.labelSmall,
            color = Gold,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (q.source.isNotEmpty()) {
            Text(
                text = q.source,
                style = MaterialTheme.typography.bodyMedium,
                color = PaperDim,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
