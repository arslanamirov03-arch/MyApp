package com.arslan.hoerzeit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arslan.hoerzeit.data.Goal
import com.arslan.hoerzeit.data.Session
import java.time.LocalDate

private data class DayGroup(
    val date: LocalDate,
    val sessions: List<Session>,
    val totalMs: Long
)

@Composable
fun HistoryScreen(
    sessions: List<Session>,
    onDelete: (Long) -> Unit,
    onManual: () -> Unit,
    modifier: Modifier = Modifier
) {
    val groups = remember(sessions) {
        sessions
            .groupBy { it.date }
            .map { (date, list) ->
                DayGroup(date, list.sortedBy { it.start }, list.sumOf { it.durationMs })
            }
            .sortedByDescending { it.date }
    }

    // Крестик ничего не удаляет сразу — сначала спрашиваем.
    var pendingDelete by remember { mutableStateOf<Session?>(null) }

    LazyColumn(
        modifier = modifier.padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(2.dp))
            SoftCard {
                Text("Всё, что записано", style = MaterialTheme.typography.titleMedium, color = C.Ink)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        value = formatHm(sessions.sumOf { it.durationMs }),
                        label = "всего",
                        accent = C.ClayDeep,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = "${groups.size}",
                        label = "дней",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        value = "${sessions.size}",
                        label = "сессий",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (groups.isEmpty()) {
            item { EmptyState(onManual) }
        }

        items(groups, key = { it.date.toEpochDay() }) { group ->
            DayCard(group = group, onRequestDelete = { pendingDelete = it })
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    pendingDelete?.let { session ->
        ConfirmDeleteDialog(
            session = session,
            onCancel = { pendingDelete = null },
            onConfirm = {
                onDelete(session.id)
                pendingDelete = null
            }
        )
    }
}

/** Спрашиваем перед удалением, чтобы случайное касание не стёрло записанное время. */
@Composable
private fun ConfirmDeleteDialog(
    session: Session,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    AskDialog(
        title = "Удалить сессию?",
        message = "Это время исчезнет из прогресса. Отменить будет нельзя.",
        confirmText = "Удалить",
        cancelText = "Оставить",
        danger = true,
        onConfirm = onConfirm,
        onCancel = onCancel,
        detail = {
            Text(
                formatDay(session.date),
                style = MaterialTheme.typography.labelSmall,
                color = C.Muted
            )
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatTime(session.startTime)} – ${formatTime(session.endTime)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = C.Ink
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatHm(session.durationMs),
                    style = MaterialTheme.typography.titleMedium,
                    color = C.ClayDeep
                )
            }
        }
    )
}

@Composable
private fun DayCard(group: DayGroup, onRequestDelete: (Session) -> Unit) {
    var expanded by rememberSaveable(group.date) { mutableStateOf(false) }
    val done = group.totalMs >= Goal.DAILY_MS

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(C.Card.copy(alpha = 0.78f))
            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (done) C.Good else C.ClaySoft)
            )
            Spacer(Modifier.padding(horizontal = 5.dp))
            Text(
                formatDay(group.date),
                style = MaterialTheme.typography.titleMedium,
                color = C.Ink
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatHm(group.totalMs),
                fontSize = 17.sp,
                fontWeight = FontWeight.Light,
                color = if (done) C.Good else C.InkSoft
            )
        }

        Spacer(Modifier.height(12.dp))

        ThinBar(
            fraction = (group.totalMs.toFloat() / Goal.DAILY_MS).coerceIn(0f, 1f),
            height = 6,
            brush = if (done) Brush.horizontalGradient(listOf(C.Good.copy(alpha = 0.6f), C.Good))
            else Brush.horizontalGradient(listOf(C.ClaySoft, C.Clay))
        )

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(180)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(140)) + fadeOut(tween(100))
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                group.sessions.forEach { session ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${formatTime(session.startTime)} – ${formatTime(session.endTime)}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = C.InkSoft
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            formatShort(session.durationMs),
                            style = MaterialTheme.typography.bodyMedium,
                            color = C.Muted
                        )
                        Spacer(Modifier.padding(horizontal = 6.dp))
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.04f))
                                .clickable { onRequestDelete(session) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Удалить сессию",
                                tint = C.Muted,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onManual: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Пока пусто", style = MaterialTheme.typography.headlineMedium, color = C.InkSoft)
        Spacer(Modifier.height(8.dp))
        Text(
            "Нажми «Старт» на главном экране —\nили впиши прошлую практику вручную.",
            style = MaterialTheme.typography.bodyMedium,
            color = C.Muted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(C.Clay)
                .clickable(onClick = onManual)
                .padding(horizontal = 22.dp, vertical = 13.dp)
        ) {
            Text("Добавить вручную", color = Color.White, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
