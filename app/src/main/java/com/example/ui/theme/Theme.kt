package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = Color.White,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = OnTerracottaContainer,
    secondary = CharcoalText,
    onSecondary = Color.White,
    secondaryContainer = WarmSandSurface,
    onSecondaryContainer = CharcoalText,
    tertiary = SageGreen,
    onTertiary = Color.White,
    tertiaryContainer = SageGreenContainer,
    onTertiaryContainer = SageGreen,
    background = WarmOffWhite,
    onBackground = CharcoalText,
    surface = CrispWhiteCard,
    onSurface = CharcoalText,
    surfaceVariant = WarmSandSurface,
    onSurfaceVariant = SlateMutedText,
    outline = WarmOutline,
    error = RoseAlert,
    onError = Color.White,
    errorContainer = RoseContainer,
    onErrorContainer = RoseAlert
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkTerracotta,
    onPrimary = DarkBackground,
    primaryContainer = DarkTerracottaContainer,
    onPrimaryContainer = TerracottaContainer,
    secondary = WarmSandSurface,
    onSecondary = DarkBackground,
    secondaryContainer = DarkCard,
    onSecondaryContainer = WarmOffWhite,
    tertiary = SageGreen,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF173D2D),
    onTertiaryContainer = SageGreenContainer,
    background = DarkBackground,
    onBackground = WarmOffWhite,
    surface = DarkSurface,
    onSurface = WarmOffWhite,
    surfaceVariant = DarkCard,
    onSurfaceVariant = Color(0xFFB3B8C2),
    outline = DarkOutline,
    error = Color(0xFFE57373),
    onError = DarkBackground,
    errorContainer = Color(0xFF4A1E1E),
    onErrorContainer = RoseContainer
)

val PgShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun PgBookingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = PgShapes,
        content = content
    )
}
