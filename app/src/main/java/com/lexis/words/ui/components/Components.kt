package com.lexis.words.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lexis.words.ui.theme.Accent
import com.lexis.words.ui.theme.BorderSoft
import com.lexis.words.ui.theme.Ink
import com.lexis.words.ui.theme.NeutralBtnBg
import com.lexis.words.ui.theme.NeutralBtnFg
import com.lexis.words.ui.theme.Nunito
import com.lexis.words.ui.theme.ScreenBg
import com.lexis.words.ui.theme.SheetScrim
import com.lexis.words.ui.theme.TextMuted3

@Composable
fun IconTile(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    corner: Dp = 14.dp,
    bg: Color = Color.White,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(4.dp, RoundedCornerShape(corner), clip = false)
            .clip(RoundedCornerShape(corner))
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
fun BackChevron(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    IconTile(modifier = modifier, size = size, onClick = onClick) {
        Text("‹", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = com.lexis.words.ui.theme.TextMuted1)
    }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bg: Color = Accent,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) bg else com.lexis.words.ui.theme.BorderDashed)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 17.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.White)
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(NeutralBtnBg)
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp, color = NeutralBtnFg)
    }
}

@Composable
fun DangerButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(com.lexis.words.ui.theme.ErrorBg)
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp, color = com.lexis.words.ui.theme.ErrorInk)
    }
}

/** Deleting anything in the app goes through this — one tap never destroys data. */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    confirmLabel: String = "Удалить",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 19.sp, color = Ink) },
        text = {
            Text(
                message, fontFamily = Nunito, fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp, color = com.lexis.words.ui.theme.TextMuted2, lineHeight = 20.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = com.lexis.words.ui.theme.ErrorInk)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = com.lexis.words.ui.theme.TextMuted2)
            }
        },
    )
}

@Composable
fun ThinProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 6.dp, track: Color = com.lexis.words.ui.theme.TrackBg) {
    Box(modifier = modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(track)) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxSize()
                .clip(RoundedCornerShape(height / 2))
                .background(color)
        )
    }
}

@Composable
fun Chip(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, color = fg)
    }
}

@Composable
fun ToastHost(message: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Ink)
                .padding(vertical = 15.dp, horizontal = 18.dp)
        ) {
            Text(
                message ?: "", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp,
        color = com.lexis.words.ui.theme.TextMuted3, letterSpacing = 1.sp, modifier = modifier
    )
}

val ScreenHPadding = PaddingValues(horizontal = 18.dp)

/** A bottom sheet matching the design: dark scrim, 28dp top corners, drag handle. */
@Composable
fun SheetScaffold(onDismiss: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(SheetScrim)
            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(ScreenBg)
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { }
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 34.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 18.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(BorderSoft)
            )
            content()
        }
    }
}

@Composable
fun SheetTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 15.dp)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = fontSize, color = TextMuted3)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = fontSize, color = Ink),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
