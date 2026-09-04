package com.arslan.hoerzeit.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Тёплая светлая палитра в духе Claude. */
object C {
    val Cream = Color(0xFFFAF9F5)
    val Card = Color(0xFFFFFDF9)
    val Ink = Color(0xFF1F1E1D)
    val InkSoft = Color(0xFF56534D)
    val Muted = Color(0xFF938D82)
    val Line = Color(0xFFE9E4D8)

    val Clay = Color(0xFFD97757)
    val ClayDeep = Color(0xFFBE5A3C)
    val ClaySoft = Color(0xFFF0B79C)

    val Peach = Color(0xFFF7C9AE)
    val Sand = Color(0xFFF0E4C9)
    val Sky = Color(0xFFCBD8E6)
    val Rose = Color(0xFFF4D3CC)

    val Good = Color(0xFF6E9174)
    val Danger = Color(0xFFB2483A)
}

private val scheme = lightColorScheme(
    primary = C.Clay,
    onPrimary = Color.White,
    secondary = C.ClayDeep,
    background = C.Cream,
    onBackground = C.Ink,
    surface = C.Card,
    onSurface = C.Ink,
    surfaceVariant = Color(0xFFF2EEE4),
    onSurfaceVariant = C.InkSoft,
    outline = C.Line
)

private val typography = Typography(
    displayLarge = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Light, letterSpacing = (-1.5).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.1.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, fontWeight = FontWeight.Normal),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp)
)

@Composable
fun HoerzeitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
