package com.discipline.hoeren.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.discipline.hoeren.core.Plan
import com.discipline.hoeren.core.Session
import com.discipline.hoeren.core.formatClock
import com.discipline.hoeren.core.formatHm
import com.discipline.hoeren.core.formatLong
import com.discipline.hoeren.ui.components.Banner
import com.discipline.hoeren.ui.components.BtnKind
import com.discipline.hoeren.ui.components.ClockDialog
import com.discipline.hoeren.ui.components.SovietButton
import com.discipline.hoeren.ui.components.StatRow
import com.discipline.hoeren.ui.components.statusLabel
import com.discipline.hoeren.ui.theme.Gold
import com.discipline.hoeren.ui.theme.GreenPlan
import com.discipline.hoeren.ui.theme.Ink
import com.discipline.hoeren.ui.theme.InkLine
import com.discipline.hoeren.ui.theme.InkSoft
import com.discipline.hoeren.ui.theme.Paper
import com.discipline.hoeren.ui.theme.PaperDim
import com.discipline.hoeren.ui.theme.RedBanner
import com.discipline.hoeren.ui.theme.RedDeep

/** Что именно сейчас выбирается на циферблате. */
private sealed interface Editing {
    data object None : Editing
    data class Start(val id: Long, val current: Int) : Editing
    data class End(val id: Long, val current: Int) : Editing
    data object NewStart : Editing
    data class NewEnd(val start: Int) : Editing
}

/**
 * Редактор одного дня плана.
 *
 * Здесь можно внести практику задним числом, если забыл отметиться, и поправить
 * время начала или конца любой записи — в том числе за сегодня.
 */
@Composable
fun DayEditorDialog(
    state: AppState,
    day: Int,
    onDismiss: () -> Unit,
) {
    val store = state.store
    val sessions = store.sessionsOf(day)
    val norm = Plan.normForDay(store.sessions, day)
    val done = Plan.doneOn(store.sessions, day)
    val status = Plan.statusOf(store.sessions, day, state.today)

    var editing: Editing by remember { mutableStateOf(Editing.None) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .padding(12.dp)
                .background(Ink)
                .border(2.dp, RedBanner, RectangleShape),
        ) {
            Banner("День $day · ${formatDate(day, Plan.MONTH, Plan.YEAR)}")

            Column(
                Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
            ) {
                Text(
                    text = "${weekdayName(Plan.weekdayOf(day))} · ${statusLabel(status)}".uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = PaperDim,
                )

                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().background(InkSoft).padding(12.dp)) {
                    Column {
                        StatRow("Норма дня", formatHm(norm), valueColor = Gold)
                        StatRow("Выполнено", formatHm(done))
                        StatRow(
                            "Осталось",
                            if (done >= norm) "норма закрыта" else formatHm(norm - done),
                            valueColor = if (done >= norm) GreenPlan else RedBanner,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Сессии".uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Paper,
                )
                Spacer(Modifier.height(6.dp))

                if (sessions.isEmpty()) {
                    Text(
                        text = "За этот день ничего не записано.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PaperDim,
                    )
                } else {
                    sessions.forEachIndexed { i, s ->
                        EditableSessionRow(
                            index = i + 1,
                            session = s,
                            onEditStart = { editing = Editing.Start(s.id, s.startMin) },
                            onEditEnd = { editing = Editing.End(s.id, s.endMin) },
                            onDelete = { store.deleteSession(s.id) },
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                SovietButton(
                    text = "Добавить сессию",
                    onClick = { editing = Editing.NewStart },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                SovietButton(
                    text = "Закрыть",
                    onClick = onDismiss,
                    kind = BtnKind.OUTLINE,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Правка дня сразу пересчитывает норму всех последующих дней: " +
                        "цель в 60 часов остаётся неизменной.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PaperDim,
                )
            }
        }
    }

    when (val e = editing) {
        Editing.None -> Unit

        is Editing.Start -> ClockDialog(
            title = "Во сколько начал",
            initialMinuteOfDay = e.current,
            onConfirm = { newStart ->
                store.sessions.firstOrNull { it.id == e.id }?.let {
                    store.updateSession(e.id, newStart, it.endMin)
                }
                editing = Editing.None
            },
            onDismiss = { editing = Editing.None },
        )

        is Editing.End -> ClockDialog(
            title = "Во сколько закончил",
            initialMinuteOfDay = e.current,
            onConfirm = { newEnd ->
                store.sessions.firstOrNull { it.id == e.id }?.let {
                    store.updateSession(e.id, it.startMin, newEnd)
                }
                editing = Editing.None
            },
            onDismiss = { editing = Editing.None },
        )

        Editing.NewStart -> ClockDialog(
            title = "Во сколько начал",
            initialMinuteOfDay = if (day == state.today) state.nowMinute else 9 * 60,
            onConfirm = { editing = Editing.NewEnd(it) },
            onDismiss = { editing = Editing.None },
        )

        is Editing.NewEnd -> ClockDialog(
            title = "Во сколько закончил",
            initialMinuteOfDay = (e.start + 60) % 1440,
            onConfirm = { end ->
                store.addSession(day, e.start, end)
                editing = Editing.None
            },
            onDismiss = { editing = Editing.None },
        )
    }
}

@Composable
private fun EditableSessionRow(
    index: Int,
    session: Session,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, InkLine, RectangleShape)
            .padding(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Сессия $index".uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = PaperDim,
            )
            Text(
                text = formatLong(session.durationMin),
                style = MaterialTheme.typography.bodySmall,
                color = Gold,
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TimeChip("Начало", session.startMin, Modifier.weight(1f), onEditStart)
            TimeChip("Конец", session.endMin, Modifier.weight(1f), onEditEnd)
        }

        Spacer(Modifier.height(8.dp))
        SovietButton(
            text = "Удалить",
            onClick = onDelete,
            kind = BtnKind.OUTLINE,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TimeChip(
    label: String,
    minuteOfDay: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .background(RedDeep)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Paper.copy(alpha = 0.7f),
        )
        Text(
            text = formatClock(minuteOfDay),
            style = MaterialTheme.typography.displayLarge,
            color = Paper,
            fontSize = 26.sp,
        )
    }
}
