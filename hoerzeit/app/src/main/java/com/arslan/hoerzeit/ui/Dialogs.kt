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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Общий вопрос «точно?» — одинаково выглядит везде.
 * [detail] — необязательная вставка с подробностями того, о чём спрашиваем.
 */
@Composable
fun AskDialog(
    title: String,
    message: String,
    confirmText: String,
    cancelText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    danger: Boolean = false,
    /** Что делать при «Назад» или тапе мимо. По умолчанию — то же, что и кнопка отмены. */
    onDismiss: () -> Unit = onCancel,
    detail: @Composable (() -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(C.Cream)
                .border(1.dp, Color.White, RoundedCornerShape(28.dp))
                .padding(horizontal = 22.dp, vertical = 24.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = C.Ink)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = C.Muted)

            if (detail != null) {
                Spacer(Modifier.height(18.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.75f))
                        .border(1.dp, C.Line, RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    detail()
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogButton(
                    text = cancelText,
                    modifier = Modifier.weight(1f),
                    background = Color.White.copy(alpha = 0.6f),
                    contentColor = C.InkSoft,
                    outlined = true,
                    onClick = onCancel
                )
                DialogButton(
                    text = confirmText,
                    modifier = Modifier.weight(1f),
                    background = if (danger) C.Danger else C.Clay,
                    contentColor = Color.White,
                    outlined = false,
                    onClick = onConfirm
                )
            }
        }
    }
}

@Composable
private fun DialogButton(
    text: String,
    modifier: Modifier,
    background: Color,
    contentColor: Color,
    outlined: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .then(if (outlined) Modifier.border(1.dp, C.Line, RoundedCornerShape(16.dp)) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = contentColor, maxLines = 1)
    }
}
