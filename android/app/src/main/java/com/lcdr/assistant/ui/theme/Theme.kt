package com.lcdr.assistant.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LcdrColorScheme = darkColorScheme(
    primary = TacticalBlue,
    onPrimary = Color.White,
    primaryContainer = TacticalBlueDim,
    onPrimaryContainer = TextPrimary,
    secondary = OfficerGold,
    onSecondary = NavyDeep,
    secondaryContainer = OfficerGoldDim,
    onSecondaryContainer = TextPrimary,
    background = NavyDeep,
    onBackground = TextPrimary,
    surface = NavySurface,
    onSurface = TextPrimary,
    surfaceVariant = NavyContainer,
    onSurfaceVariant = TextSecondary,
    outline = NavyBorder,
    error = DangerRed,
    onError = Color.White
)

@Composable
fun LcdrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LcdrColorScheme,
        typography = LcdrTypography,
        content = content
    )
}
