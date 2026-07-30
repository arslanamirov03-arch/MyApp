package com.discipline.hoeren.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.discipline.hoeren.ui.theme.Gold
import com.discipline.hoeren.ui.theme.Ink
import com.discipline.hoeren.ui.theme.InkLine
import com.discipline.hoeren.ui.theme.InkSoft
import com.discipline.hoeren.ui.theme.Paper
import com.discipline.hoeren.ui.theme.PaperDim
import com.discipline.hoeren.ui.theme.RedBanner
import com.discipline.hoeren.ui.theme.RedDeep

/**
 * Плакатная кнопка: прямые углы, сплошная заливка, прописные с разрядкой.
 * Скруглений нет намеренно — конструктивизм не знает радиусов.
 */
enum class BtnKind { PRIMARY, GOLD, OUTLINE }

@Composable
fun SovietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.PRIMARY,
    enabled: Boolean = true,
) {
    val bg = when (kind) {
        BtnKind.PRIMARY -> RedBanner
        BtnKind.GOLD -> Gold
        BtnKind.OUTLINE -> Color.Transparent
    }
    val fg = when (kind) {
        BtnKind.PRIMARY -> Paper
        BtnKind.GOLD -> Ink
        BtnKind.OUTLINE -> Paper
    }
    val alpha = if (enabled) 1f else 0.35f

    Box(
        modifier = modifier
            .height(54.dp)
            .background(bg.copy(alpha = if (kind == BtnKind.OUTLINE) 0f else alpha))
            .border(2.dp, (if (kind == BtnKind.OUTLINE) Paper else bg).copy(alpha = alpha), RectangleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = fg.copy(alpha = alpha),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
    }
}

/** Красный транспарант во всю ширину. */
@Composable
fun Banner(text: String, modifier: Modifier = Modifier, color: Color = RedDeep) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(color)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = Paper,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Секция с заголовком-ярлыком и рамкой. */
@Composable
fun Panel(
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = InkLine,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(InkSoft)
            .border(1.dp, accent, RectangleShape),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = PaperDim,
            modifier = Modifier
                .fillMaxWidth()
                .background(accent.copy(alpha = 0.35f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Column(Modifier.padding(12.dp), content = content)
    }
}

/** Строка «показатель — значение». */
@Composable
fun StatRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Paper,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = PaperDim,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
        )
    }
}

/** Плоская полоса выполнения без скруглений. */
@Composable
fun FlatBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = RedBanner,
    overColor: Color = Gold,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .background(InkLine),
    ) {
        val main = progress.coerceIn(0f, 1f)
        if (main > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(main)
                    .height(10.dp)
                    .background(color),
            )
        }
        val extra = (progress - 1f).coerceIn(0f, 1f)
        if (extra > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(extra)
                    .height(4.dp)
                    .background(overColor),
            )
        }
    }
}
