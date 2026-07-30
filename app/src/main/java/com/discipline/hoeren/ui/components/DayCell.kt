package com.discipline.hoeren.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.discipline.hoeren.core.Plan
import com.discipline.hoeren.ui.theme.Gold
import com.discipline.hoeren.ui.theme.Ink
import com.discipline.hoeren.ui.theme.InkLine
import com.discipline.hoeren.ui.theme.InkSoft
import com.discipline.hoeren.ui.theme.Paper
import com.discipline.hoeren.ui.theme.PaperDim
import com.discipline.hoeren.ui.theme.RedBanner
import com.discipline.hoeren.ui.theme.RedDark
import com.discipline.hoeren.ui.theme.RedDeep
import com.discipline.hoeren.ui.theme.GreenPlan

/** Цвет заливки ячейки по статусу дня. */
fun statusColor(status: Plan.DayStatus): Color = when (status) {
    Plan.DayStatus.FUTURE -> InkSoft
    Plan.DayStatus.IN_PROGRESS -> InkSoft
    Plan.DayStatus.DONE -> GreenPlan
    Plan.DayStatus.OVER -> Gold
    Plan.DayStatus.UNDER -> RedDark
    // Прогул выделен более насыщенным красным: недовыполнение и полный ноль
    // не должны выглядеть одинаково.
    Plan.DayStatus.MISSED -> RedDeep
}

/** Короткая подпись статуса для легенды и заголовков. */
fun statusLabel(status: Plan.DayStatus): String = when (status) {
    Plan.DayStatus.FUTURE -> "План"
    Plan.DayStatus.IN_PROGRESS -> "Идёт"
    Plan.DayStatus.DONE -> "Выполнено"
    Plan.DayStatus.OVER -> "Перевыполнение"
    Plan.DayStatus.UNDER -> "Недовыполнено"
    Plan.DayStatus.MISSED -> "Прогул"
}

/**
 * Ячейка календаря плана: номер дня, полоса выполнения и минуты.
 * Сегодняшний день обведён золотом.
 */
@Composable
fun DayCell(
    day: Int,
    status: Plan.DayStatus,
    doneMin: Int,
    normMin: Int,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill = statusColor(status)
    val onFill = when (status) {
        Plan.DayStatus.OVER -> Ink
        Plan.DayStatus.FUTURE -> PaperDim
        else -> Paper
    }
    val progress = if (normMin > 0) doneMin.toFloat() / normMin else if (doneMin > 0) 1f else 0f

    Column(
        modifier = modifier
            .aspectRatio(0.86f)
            .background(fill)
            .border(
                width = if (isToday) 2.dp else 1.dp,
                color = if (isToday) Gold else InkLine,
                shape = RectangleShape,
            )
            .clickable { onClick() }
            .padding(3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Black,
            fontSize = 15.sp,
            color = onFill,
        )
        Text(
            text = if (doneMin > 0) "${doneMin / 60}:${(doneMin % 60).toString().padStart(2, '0')}" else "—",
            fontSize = 9.sp,
            color = onFill.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodySmall,
        )
        Box(Modifier.weight(1f))
        // мини-полоса выполнения
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Ink.copy(alpha = 0.45f)),
        ) {
            if (progress > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(if (status == Plan.DayStatus.OVER) Ink else RedBanner),
                )
            }
        }
    }
}
