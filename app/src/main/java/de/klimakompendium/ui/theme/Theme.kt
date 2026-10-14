package de.klimakompendium.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/** Dunkles, kontrastreiches Baustellen-Design (App ist bewusst immer dunkel). */
private val Scheme = darkColorScheme(
    primary = Color(0xFF4FC3F7), onPrimary = Color(0xFF00344A),
    primaryContainer = Color(0xFF004D6B), onPrimaryContainer = Color(0xFFC3E8FF),
    secondary = Color(0xFFFFB74D), onSecondary = Color(0xFF442B00),
    secondaryContainer = Color(0xFF4F3500), onSecondaryContainer = Color(0xFFFFDDB0),
    tertiary = Color(0xFF81C784), onTertiary = Color(0xFF00390F),
    background = Color(0xFF0F1720), onBackground = Color(0xFFE6EBF1),
    surface = Color(0xFF0F1720), onSurface = Color(0xFFE6EBF1),
    surfaceVariant = Color(0xFF1F2D3B), onSurfaceVariant = Color(0xFFB9C5D1),
    surfaceContainerLowest = Color(0xFF0B1118), surfaceContainerLow = Color(0xFF131D28),
    surfaceContainer = Color(0xFF172330), surfaceContainerHigh = Color(0xFF1D2B3A),
    surfaceContainerHighest = Color(0xFF243546),
    outline = Color(0xFF7A8B9C), outlineVariant = Color(0xFF34475A),
    error = Color(0xFFFF8A80), onError = Color(0xFF4A0000),
    errorContainer = Color(0xFF6B1A12), onErrorContainer = Color(0xFFFFDAD4),
)

private val base = Typography()
private val AppTypography = base.copy(
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
    labelLarge = base.labelLarge.copy(fontSize = 15.sp),
)

val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp)
val WarnColor = Color(0xFFFFB74D)
val OkColor = Color(0xFF81C784)

/** Mindesthöhe für alle antippbaren Elemente (Handschuh-tauglich). */
val TouchTarget = 56.dp

@Composable
fun KlimaTheme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val ignoreSystem = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = Scheme,
        typography = AppTypography,
        shapes = Shapes(medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(18.dp)),
        content = content,
    )
}
