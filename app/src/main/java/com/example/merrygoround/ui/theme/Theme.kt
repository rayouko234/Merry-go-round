package com.example.merrygoround.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = BrandGreen,
    onPrimary = AppBackground,
    primaryContainer = BrandGreenLight,
    onPrimaryContainer = BrandGreenDark,
    secondary = BrandGreenDark,
    onSecondary = AppBackground,
    secondaryContainer = BrandGreenLight,
    onSecondaryContainer = BrandGreenDark,
    tertiary = InfoBlue,
    onTertiary = AppBackground,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = AppBackground,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceGray,
    onSurfaceVariant = TextSecondary,
    outline = BorderGray,
    outlineVariant = BorderGray,
    error = ErrorRed,
    onError = AppBackground,
    errorContainer = ErrorRedLight,
    onErrorContainer = ErrorRed
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun MerryGoRoundTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
