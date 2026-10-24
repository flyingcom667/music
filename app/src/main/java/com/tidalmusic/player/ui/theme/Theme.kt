package com.tidalmusic.player.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.tidalmusic.player.data.preferences.ThemeMode

/**
 * The colour of seek-bar thumbs and played tracks: deep navy in the light theme, pale
 * cyan in the dark theme (navy would vanish on the dark surfaces). Read through
 * [LocalTidalSeekbarColor] so every slider in the app stays identical.
 */
val LocalTidalSeekbarColor = staticCompositionLocalOf { OceanNavy }

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainerColor,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = DarkInversePrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainerColor,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackgroundColor,
    onBackground = DarkOnBackgroundColor,
    surface = DarkSurfaceColor,
    onSurface = DarkOnSurfaceColor,
    surfaceVariant = DarkSurfaceVariantColor,
    onSurfaceVariant = DarkOnSurfaceVariantColor,
    surfaceTint = DarkPrimary,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    outline = DarkOutlineColor,
    outlineVariant = DarkOutlineVariantColor,
    scrim = DarkScrim,
    surfaceContainerLowest = DarkSurfaceContainerLowestColor,
    surfaceContainerLow = DarkSurfaceContainerLowColor,
    surfaceContainer = DarkSurfaceContainerColor,
    surfaceContainerHigh = DarkSurfaceContainerHighColor,
    surfaceContainerHighest = DarkSurfaceContainerHighestColor,
)

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = LightInversePrimary,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackgroundColor,
    onBackground = LightOnSurfaceColor,
    surface = LightSurfaceColor,
    onSurface = LightOnSurfaceColor,
    surfaceVariant = LightSurfaceVariantColor,
    onSurfaceVariant = LightOnSurfaceVariantColor,
    surfaceTint = LightPrimary,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    outline = LightOutlineColor,
    outlineVariant = LightOutlineVariantColor,
    scrim = LightScrim,
    surfaceContainerLowest = LightSurfaceContainerLowestColor,
    surfaceContainerLow = LightSurfaceContainerLowColor,
    surfaceContainer = LightSurfaceContainerColor,
    surfaceContainerHigh = LightSurfaceContainerHighColor,
    surfaceContainerHighest = LightSurfaceContainerHighestColor,
)

/**
 * Root theme.
 *
 * @param themeMode the persisted preference: system default, light or dark.
 * @param dynamicColor when true and the OS supports it (Android 12+), the wallpaper
 *   palette replaces the brand palette. Off by default so the Ocean identity
 *   is what the user sees unless they explicitly ask otherwise.
 */
@Composable
fun TidalMusicTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }
    val seekbarColor = if (darkTheme) OceanLightCyan else OceanNavy

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalTidalSeekbarColor provides seekbarColor) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TidalTypography,
            shapes = TidalShapes,
            content = content,
        )
    }
}
