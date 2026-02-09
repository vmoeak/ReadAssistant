package com.readassistant.core.domain.model

data class ReaderSettings(
    val themeType: ThemeType = ThemeType.LIGHT,
    val fontSize: Int = 16,
    val lineHeight: Float = 1.6f,
    val fontFamily: String = "default",
    val contentMaxWidthDp: Int = 800,
    val contentPaddingDp: Int = 16
)

enum class ThemeType {
    LIGHT, SEPIA, DARK
}
