package com.discipline.hoeren.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.discipline.hoeren.core.MINUTES_PER_DAY
import com.discipline.hoeren.ui.theme.Gold
import com.discipline.hoeren.ui.theme.Ink
import com.discipline.hoeren.ui.theme.InkLine
import com.discipline.hoeren.ui.theme.InkSoft
import com.discipline.hoeren.ui.theme.Paper
import com.discipline.hoeren.ui.theme.PaperDim
import com.discipline.hoeren.ui.theme.RedBanner
import com.discipline.hoeren.ui.theme.RedDeep

/**
 * Выбор времени в 24-часовом формате.
 *
 * @param initialMinuteOfDay время, на котором открыть циферблат
 * @param onConfirm возвращает выбранное время как минуты от полуночи
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockDialog(
    title: String,
    initialMinuteOfDay: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val start = ((initialMinuteOfDay % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
    val state = rememberTimePickerState(
        initialHour = start / 60,
        initialMinute = start % 60,
        is24Hour = true,
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(InkSoft)
                .border(2.dp, RedBanner, RectangleShape),
        ) {
            Banner(title, color = RedDeep)

            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = Ink,
                        clockDialSelectedContentColor = Ink,
                        clockDialUnselectedContentColor = Paper,
                        selectorColor = Gold,
                        containerColor = InkSoft,
                        periodSelectorBorderColor = InkLine,
                        timeSelectorSelectedContainerColor = RedDeep,
                        timeSelectorUnselectedContainerColor = Ink,
                        timeSelectorSelectedContentColor = Paper,
                        timeSelectorUnselectedContentColor = PaperDim,
                    ),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SovietButton(
                        text = "Отмена",
                        onClick = onDismiss,
                        kind = BtnKind.OUTLINE,
                        modifier = Modifier.weight(1f),
                    )
                    SovietButton(
                        text = "Принять",
                        onClick = { onConfirm(state.hour * 60 + state.minute) },
                        kind = BtnKind.GOLD,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Подпись под циферблатом, вынесена отдельно для переиспользования. */
@Composable
fun DialogHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = PaperDim,
        modifier = modifier.fillMaxWidth().padding(top = 6.dp),
    )
}
