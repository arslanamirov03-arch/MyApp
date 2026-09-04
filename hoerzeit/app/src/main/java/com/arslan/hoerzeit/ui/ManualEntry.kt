package com.arslan.hoerzeit.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Ручное добавление сессии: выбираешь день, время начала и время конца.
 * Нужно, если забыл нажать «Старт» или хочешь записать прошлую практику.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntrySheet(
    onDismiss: () -> Unit,
    onSave: (startMillis: Long, endMillis: Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val now = remember { LocalDateTime.now().withSecond(0).withNano(0) }

    var date by remember { mutableStateOf(now.toLocalDate()) }
    var start by remember { mutableStateOf(now.minusMinutes(30).toLocalTime().withSecond(0).withNano(0)) }
    var end by remember { mutableStateOf(now.toLocalTime()) }

    var showDate by remember { mutableStateOf(false) }
    var showStart by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }

    val zone = remember { ZoneId.systemDefault() }
    // Практика перед сном может перейти за полночь: 23:40 → 00:20 считаем следующим днём.
    val crossesMidnight = end.isBefore(start)
    val endDate = if (crossesMidnight) date.plusDays(1) else date
    val startMillis = date.atTime(start).atZone(zone).toInstant().toEpochMilli()
    val endMillis = endDate.atTime(end).atZone(zone).toInstant().toEpochMilli()
    val durationMs = endMillis - startMillis
    val valid = durationMs > 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = C.Cream,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .height(4.dp)
                        .width(40.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(C.Line)
                )
            }
        }
    ) {
        Column(
            Modifier
                .padding(horizontal = 22.dp)
                .padding(bottom = 12.dp)
                .navigationBarsPadding()
        ) {
            Text("Добавить вручную", style = MaterialTheme.typography.headlineMedium, color = C.Ink)
            Spacer(Modifier.height(4.dp))
            Text(
                "Если забыл нажать «Старт» — просто впиши время.",
                style = MaterialTheme.typography.bodyMedium,
                color = C.Muted
            )

            Spacer(Modifier.height(20.dp))

            PickerRow(label = "День", value = formatDayLabel(date)) { showDate = true }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PickerRow(
                    label = "Начало",
                    value = formatTime(start.hour, start.minute),
                    modifier = Modifier.weight(1f)
                ) { showStart = true }
                PickerRow(
                    label = "Конец",
                    value = formatTime(end.hour, end.minute),
                    modifier = Modifier.weight(1f)
                ) { showEnd = true }
            }

            Spacer(Modifier.height(18.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (valid) C.ClaySoft.copy(alpha = 0.28f) else Color(0x14000000))
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (valid) formatHm(durationMs) else "Начало и конец совпадают",
                    fontSize = if (valid) 24.sp else 14.sp,
                    fontWeight = if (valid) FontWeight.Light else FontWeight.Normal,
                    color = if (valid) C.ClayDeep else C.Muted
                )
                if (valid && crossesMidnight) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "заканчивается ${formatDateFull(endDate)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = C.InkSoft
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = { onSave(startMillis, endMillis) },
                enabled = valid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = C.Clay,
                    contentColor = Color.White,
                    disabledContainerColor = C.Line,
                    disabledContentColor = C.Muted
                )
            ) {
                Text("Сохранить", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            colors = DatePickerDefaults.colors(containerColor = C.Cream),
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDate = false
                }) { Text("Готово", color = C.ClayDeep) }
            },
            dismissButton = {
                TextButton(onClick = { showDate = false }) { Text("Отмена", color = C.Muted) }
            }
        ) {
            DatePicker(
                state = state,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = C.Cream,
                    selectedDayContainerColor = C.Clay,
                    todayDateBorderColor = C.Clay
                )
            )
        }
    }

    if (showStart) {
        ClockDialog(
            title = "Время начала",
            initial = start,
            onDismiss = { showStart = false },
            onConfirm = { start = it; showStart = false }
        )
    }

    if (showEnd) {
        ClockDialog(
            title = "Время конца",
            initial = end,
            onDismiss = { showEnd = false },
            onConfirm = { end = it; showEnd = false }
        )
    }
}

@Composable
private fun PickerRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.8f))
            .border(1.dp, C.Line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = C.Muted)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = C.Ink)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClockDialog(
    title: String,
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true
    )
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(C.Cream)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = C.Ink)
            Spacer(Modifier.height(16.dp))
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    selectorColor = C.Clay,
                    containerColor = C.Cream,
                    periodSelectorSelectedContainerColor = C.ClaySoft,
                    timeSelectorSelectedContainerColor = C.ClaySoft.copy(alpha = 0.5f),
                    timeSelectorSelectedContentColor = C.Ink,
                    clockDialColor = Color.White.copy(alpha = 0.75f)
                )
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Отмена", color = C.Muted) }
                TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                    Text("Готово", color = C.ClayDeep)
                }
            }
        }
    }
}

private fun formatDayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    val relative = formatDay(date, today)
    return if (relative == "Сегодня" || relative == "Вчера") "$relative · ${formatDateFull(date)}" else formatDateFull(date)
}
