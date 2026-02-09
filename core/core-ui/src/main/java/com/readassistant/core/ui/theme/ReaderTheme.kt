package com.readassistant.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ReadingThemeType {
    LIGHT, SEPIA, DARK
}

@Immutable
data class ReaderColors(
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val translationBorder: Color,
    val translationBackground: Color,
    val translationText: Color
)

@Immutable
data class ReaderTypography(
    val fontSize: TextUnit = 16.sp,
    val lineHeight: TextUnit = 28.sp,
    val fontFamily: String = "default"
)

@Immutable
data class ReaderDimensions(
    val contentMaxWidth: Dp = 720.dp,
    val contentPadding: Dp = 24.dp
)

@Immutable
data class ReaderThemeData(
    val themeType: ReadingThemeType = ReadingThemeType.LIGHT,
    val colors: ReaderColors = lightReaderColors,
    val typography: ReaderTypography = ReaderTypography(),
    val dimensions: ReaderDimensions = ReaderDimensions()
)

val lightReaderColors = ReaderColors(
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    translationBorder = TranslationBorder,
    translationBackground = TranslationBackground,
    translationText = TranslationText
)

val sepiaReaderColors = ReaderColors(
    background = SepiaBackground,
    onBackground = SepiaOnBackground,
    surface = SepiaSurface,
    translationBorder = Color(0xFF8D6E63),
    translationBackground = Color(0x0A000000),
    translationText = Color(0xFF5D4037)
)

val darkReaderColors = ReaderColors(
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    translationBorder = Color(0xFF64B5F6),
    translationBackground = Color(0x14FFFFFF),
    translationText = Color(0xFF90A4AE)
)

val LocalReaderTheme = staticCompositionLocalOf { ReaderThemeData() }

@Composable
fun ReaderTheme(
    themeData: ReaderThemeData = ReaderThemeData(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalReaderTheme provides themeData) {
        content()
    }
}
