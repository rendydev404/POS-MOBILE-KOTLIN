package com.sukashawarma.pos.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = ShawarmaOrange,
    onPrimary = CreamSurface,
    primaryContainer = ShawarmaOrangeLight,
    onPrimaryContainer = ShawarmaOrangeDark,
    secondary = ShawarmaOrangeDark,
    background = CreamBackground,
    onBackground = TextDarkPrimary,
    surface = CreamSurface,
    onSurface = TextDarkPrimary,
    surfaceVariant = CreamCard,
    outline = CreamBorder
)

@Composable
fun SukaShawarmaPOSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
