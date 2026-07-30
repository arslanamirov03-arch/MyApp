package com.discipline.hoeren.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Тема фиксирована и не зависит от системной: плакат должен выглядеть одинаково
 * в любое время суток, поэтому системный светлый/тёмный режим не учитывается.
 */
private val Scheme = darkColorScheme(
    primary = RedBanner,
    onPrimary = Paper,
    primaryContainer = RedDeep,
    onPrimaryContainer = Paper,
    secondary = Gold,
    onSecondary = Ink,
    tertiary = GreenPlan,
    onTertiary = Paper,
    background = Ink,
    onBackground = Paper,
    surface = InkSoft,
    onSurface = Paper,
    surfaceVariant = InkLine,
    onSurfaceVariant = PaperDim,
    outline = InkLine,
    error = RedBanner,
    onError = Paper,
)

@Composable
fun DisziplinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = Typography,
        content = content,
    )
}
