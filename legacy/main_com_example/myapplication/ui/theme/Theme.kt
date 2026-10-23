package com.example.myapplication.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.myapplication.domain.model.AppSettings
import com.example.myapplication.domain.model.ThemeMode

/** Glass intensity (0..1) used by the player surfaces. */
val LocalGlassIntensity = staticCompositionLocalOf { 0.6f }

/** Whether decorative animations should run. */
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

@Composable
fun MusicTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colorScheme = when {
        // The wallpaper colours are only available from Android 12 on.
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }
    val typography = remember(settings.fontFamily, settings.fontScale) {
        musicTypography(settings.fontFamily.toFontFamily(), settings.fontScale)
    }

    // The status/navigation bar icons must follow the *app* theme, not the system one,
    // otherwise they disappear as soon as the user picks dark mode while the system is light.
    SystemBars(darkTheme = darkTheme, backgroundColor = colorScheme.background)

    CompositionLocalProvider(
        LocalGlassIntensity provides settings.glassIntensity,
        LocalAnimationsEnabled provides settings.animationsEnabled,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}

@Composable
private fun SystemBars(darkTheme: Boolean, backgroundColor: Color) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Android 8 and 9 cannot draw behind the navigation bar, so its colour is set to
            // the colour of the app. That way the bottom of the screen looks like one piece
            // instead of a dark band below the navigation bar of the app.
            @Suppress("DEPRECATION")
            window.navigationBarColor = backgroundColor.toArgb()
        }
    }
}

/** Fallback theme for previews and for the very first frame before settings are read. */
@Composable
fun MusicPreviewTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}

internal val FallbackScheme = darkColorScheme()
internal val FallbackLightScheme = lightColorScheme()
