package com.example.myapplication.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Audiofy palette.
 *
 * The accents keep the blue → cyan → ice ramp, while every *surface* is a neutral grey:
 * a graphite dark scheme (dark, but not black and not blue-tinted) and a paper-white light
 * scheme. That is what most music players do and it keeps the content readable.
 */

/** Mid blue — primary in the light scheme. */
internal val Blue = Color(0xFF0077B6)

/** Vivid cyan — primary in the dark scheme and the "playing" accent everywhere. */
internal val Cyan = Color(0xFF00B4D8)

/** Ice blue — container / on-color stop. */
internal val Ice = Color(0xFF90E0EF)

/** Deep navy — used as an on-color on bright surfaces. */
internal val Navy = Color(0xFF03045E)

private val White = Color(0xFFFFFFFF)

// --- light scheme: neutral paper ---

private val LightBackground = Color(0xFFF7F8FA)
private val LightSurfaceContainer = Color(0xFFFFFFFF)
private val LightSurfaceContainerHigh = Color(0xFFEFF1F5)
private val LightSurfaceContainerHighest = Color(0xFFE5E8ED)
private val LightSurfaceVariant = Color(0xFFDCE0E6)
private val LightOnSurface = Color(0xFF14171B)
private val LightOnSurfaceVariant = Color(0xFF4A5058)
private val LightOutline = Color(0xFF767C86)

// --- dark scheme: graphite, not black, not navy ---

private val DarkBackground = Color(0xFF121316)
private val DarkSurfaceContainer = Color(0xFF1B1E23)
private val DarkSurfaceContainerHigh = Color(0xFF23272D)
private val DarkSurfaceContainerHighest = Color(0xFF2B3037)
private val DarkSurfaceVariant = Color(0xFF31363E)
private val DarkOnSurface = Color(0xFFE5E7EB)
private val DarkOnSurfaceVariant = Color(0xFFB3B9C3)
private val DarkOutline = Color(0xFF8A909B)

internal val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = White,
    primaryContainer = Color(0xFFD3ECF7),
    onPrimaryContainer = Navy,
    secondary = Cyan,
    onSecondary = White,
    secondaryContainer = Color(0xFFD3ECF7),
    onSecondaryContainer = Color(0xFF00303C),
    tertiary = Blue,
    onTertiary = White,
    tertiaryContainer = Color(0xFFE3E6EB),
    onTertiaryContainer = Navy,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightBackground,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceContainerLow = LightSurfaceContainer,
    surfaceContainerLowest = White,
    outline = LightOutline,
    outlineVariant = Color(0xFFE2E5EA),
    inverseSurface = Color(0xFF2A2E34),
    inverseOnSurface = Color(0xFFF1F3F6),
    error = Color(0xFFB3261E),
    onError = White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

internal val DarkColors = darkColorScheme(
    primary = Cyan,
    onPrimary = Color(0xFF00323D),
    primaryContainer = Color(0xFF005166),
    onPrimaryContainer = Ice,
    secondary = Ice,
    onSecondary = Color(0xFF00363F),
    secondaryContainer = Color(0xFF20505C),
    onSecondaryContainer = Color(0xFFB6ECF7),
    tertiary = Color(0xFF7FC4E8),
    onTertiary = Color(0xFF00323D),
    tertiaryContainer = Color(0xFF234A59),
    onTertiaryContainer = Color(0xFFC3E8F7),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkBackground,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceContainerLow = DarkSurfaceContainer,
    surfaceContainerLowest = Color(0xFF0E0F12),
    outline = DarkOutline,
    outlineVariant = Color(0xFF3A3F47),
    inverseSurface = Color(0xFFE5E7EB),
    inverseOnSurface = Color(0xFF1B1E23),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Gradient used behind artwork and on the player screen. */
internal val PlayerGradients = listOf(
    Blue,
    Cyan,
    Ice,
)
