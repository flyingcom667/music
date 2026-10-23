package com.example.myapplication.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import com.example.myapplication.domain.model.AppFontFamily

fun AppFontFamily.toFontFamily(): FontFamily = when (this) {
    AppFontFamily.SYSTEM -> FontFamily.Default
    AppFontFamily.SANS_SERIF -> FontFamily.SansSerif
    AppFontFamily.SERIF -> FontFamily.Serif
    AppFontFamily.MONOSPACE -> FontFamily.Monospace
}

/**
 * Material 3 typography with the family and scale chosen in settings. Only the system font
 * families are used so Persian/Arabic/Arabic-Indic glyphs are always available
 * (Android's fallback chain provides them) and the APK stays small.
 */
fun musicTypography(fontFamily: FontFamily, scale: Float): Typography {
    val base = Typography()
    fun TextStyle.styled(): TextStyle {
        val scaledSize = if (fontSize.isSpecified) fontSize * scale else fontSize
        val scaledLineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight
        return copy(fontFamily = fontFamily, fontSize = scaledSize, lineHeight = scaledLineHeight)
    }

    return Typography(
        displayLarge = base.displayLarge.styled(),
        displayMedium = base.displayMedium.styled(),
        displaySmall = base.displaySmall.styled(),
        headlineLarge = base.headlineLarge.styled(),
        headlineMedium = base.headlineMedium.styled(),
        headlineSmall = base.headlineSmall.styled(),
        titleLarge = base.titleLarge.styled(),
        titleMedium = base.titleMedium.styled(),
        titleSmall = base.titleSmall.styled(),
        bodyLarge = base.bodyLarge.styled(),
        bodyMedium = base.bodyMedium.styled(),
        bodySmall = base.bodySmall.styled(),
        labelLarge = base.labelLarge.styled(),
        labelMedium = base.labelMedium.styled(),
        labelSmall = base.labelSmall.styled(),
    )
}

internal fun TextUnit.orZero(): Float = if (isSpecified) value else 0f
