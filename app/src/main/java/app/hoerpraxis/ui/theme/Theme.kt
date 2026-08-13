package app.hoerpraxis.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// A light, Google-flavoured palette: blue primary, green/amber accents,
// soft tonal surfaces. No dark theme by design — the app is deliberately light.
val GoogleBlue = Color(0xFF1A73E8)
val GoogleBlueDeep = Color(0xFF174EA6)
val GoogleGreen = Color(0xFF188038)
val GoogleAmber = Color(0xFFE37400)
val GoogleRed = Color(0xFFD93025)
val SurfaceSoft = Color(0xFFF8F9FA)
val BlueTint = Color(0xFFE8F0FE)
val GreenTint = Color(0xFFE6F4EA)
val AmberTint = Color(0xFFFEF7E0)
val RedTint = Color(0xFFFCE8E6)
val Ink = Color(0xFF202124)
val InkSoft = Color(0xFF5F6368)

private val LightColors = lightColorScheme(
    primary = GoogleBlue,
    onPrimary = Color.White,
    primaryContainer = BlueTint,
    onPrimaryContainer = GoogleBlueDeep,
    secondary = GoogleGreen,
    onSecondary = Color.White,
    secondaryContainer = GreenTint,
    onSecondaryContainer = Color(0xFF0D652D),
    tertiary = GoogleAmber,
    onTertiary = Color.White,
    tertiaryContainer = AmberTint,
    onTertiaryContainer = Color(0xFF994F00),
    error = GoogleRed,
    errorContainer = RedTint,
    background = Color.White,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = SurfaceSoft,
    onSurfaceVariant = InkSoft,
    outline = Color(0xFFDADCE0),
)

// Everything rounded — no sharp corners anywhere in the app.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private val AppTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 30.sp),
    )
}

@Composable
fun HoerpraxisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
