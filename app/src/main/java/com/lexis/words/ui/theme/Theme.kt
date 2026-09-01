package com.lexis.words.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LexisColorScheme = lightColorScheme(
    primary = Accent,
    background = ScreenBg,
    surface = androidx.compose.ui.graphics.Color.White,
    onBackground = Ink,
    onSurface = Ink,
)

@Composable
fun LexisTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? android.app.Activity)?.window
        window?.let {
            it.statusBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = true
        }
    }
    MaterialTheme(
        colorScheme = LexisColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
