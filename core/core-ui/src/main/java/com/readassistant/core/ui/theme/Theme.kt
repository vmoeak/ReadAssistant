package com.readassistant.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightBackground,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    surfaceTint = LightBackground,
    surfaceContainer = LightBackground,
    surfaceContainerLow = LightBackground,
    surfaceContainerHigh = LightBackground,
    surfaceContainerHighest = LightSurfaceVariant,
    surfaceContainerLowest = LightBackground,
    outline = LightOutline
)

val SepiaColorScheme = lightColorScheme(
    primary = SepiaPrimary,
    onPrimary = SepiaOnPrimary,
    secondary = SepiaSecondary,
    onSecondary = SepiaOnSecondary,
    background = SepiaBackground,
    onBackground = SepiaOnBackground,
    surface = SepiaBackground,
    onSurface = SepiaOnSurface,
    surfaceVariant = SepiaSurfaceVariant,
    surfaceTint = SepiaBackground,
    surfaceContainer = SepiaBackground,
    surfaceContainerLow = SepiaBackground,
    surfaceContainerHigh = SepiaBackground,
    surfaceContainerHighest = SepiaSurfaceVariant,
    surfaceContainerLowest = SepiaBackground,
    outline = SepiaOutline
)

val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkBackground,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    surfaceTint = DarkBackground,
    surfaceContainer = DarkBackground,
    surfaceContainerLow = DarkBackground,
    surfaceContainerHigh = DarkBackground,
    surfaceContainerHighest = DarkSurfaceVariant,
    surfaceContainerLowest = DarkBackground,
    outline = DarkOutline
)

fun colorSchemeForTheme(themeType: ReadingThemeType): ColorScheme {
    return when (themeType) {
        ReadingThemeType.LIGHT -> LightColorScheme
        ReadingThemeType.SEPIA -> SepiaColorScheme
        ReadingThemeType.DARK -> DarkColorScheme
    }
}

@Composable
fun ReadAssistantTheme(
    themeType: ReadingThemeType = ReadingThemeType.LIGHT,
    content: @Composable () -> Unit
) {
    val colorScheme = colorSchemeForTheme(themeType)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
