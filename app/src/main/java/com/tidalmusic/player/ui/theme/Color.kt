package com.tidalmusic.player.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Ocean palette — the five supplied brand colours:
 *
 *   #03045E  deep navy   — seek bar (light theme), dark-theme background
 *   #0077B6  ocean blue  — primary: selected items, active controls
 *   #00B4D8  cyan        — secondary: buttons
 *   #90E0EF  light cyan  — tertiary, dark-theme accent
 *   #CAF0F8  foam        — light-theme containers
 *
 * Screens never hard-code a colour: they read roles from MaterialTheme.colorScheme
 * (and [LocalTidalSeekbarColor] for the seek bar) so light, dark and dynamic schemes
 * all stay consistent.
 */

val OceanNavy = Color(0xFF03045E)
val OceanBlue = Color(0xFF0077B6)
val OceanCyan = Color(0xFF00B4D8)
val OceanLightCyan = Color(0xFF90E0EF)
val OceanFoam = Color(0xFFCAF0F8)

/** The animated playing gradient sweeps through these (used on the Now Playing screen). */
val OceanGradientColors = listOf(OceanNavy, OceanBlue, OceanCyan, OceanLightCyan, OceanFoam)

// ---------------------------------------------------------------------------------------
// Derived neutrals, tinted towards the ocean blues instead of plain grey.
// ---------------------------------------------------------------------------------------
private val LightBackground = Color(0xFFF3FBFE)
private val LightOnBackground = Color(0xFF03045E)
private val LightSurfaceContainerLow = Color(0xFFEAF7FC)
private val LightSurfaceContainer = Color(0xFFE1F4FA)
private val LightSurfaceContainerHigh = Color(0xFFD5EFF8)
private val LightSurfaceContainerHighest = Color(0xFFC3E9F4)
private val LightOnSurfaceVariant = Color(0xFF33627D)
private val LightOutline = Color(0xFF6D9DB4)
private val LightOutlineVariant = Color(0xFFB4E3F0)

// Dark theme: a sophisticated gray-black ramp, never pure black. Surfaces step up from
// the #121212 background through #1C1C1C/#242424 cards so sheets, dialogs and cards read
// as separate layers; dividers sit at #333333 and text at #F5F5F5/#B0B0B0. The ocean
// cyan stays as the brand accent on top of the neutral base.
private val DarkBackground = Color(0xFF121212)
private val DarkOnBackground = Color(0xFFF5F5F5)
private val DarkSurfaceContainerLowest = Color(0xFF0D0D0D)
private val DarkSurfaceContainerLow = Color(0xFF1C1C1C)
private val DarkSurfaceContainer = Color(0xFF202020)
private val DarkSurfaceContainerHigh = Color(0xFF242424)
private val DarkSurfaceContainerHighest = Color(0xFF2E2E2E)
private val DarkOnSurfaceVariant = Color(0xFFB0B0B0)
private val DarkOutline = Color(0xFF4A4A4A)
private val DarkOutlineVariant = Color(0xFF333333)


// ---------------------------------------------------------------------------------------
// Light scheme
// ---------------------------------------------------------------------------------------
internal val LightPrimary = OceanBlue
internal val LightOnPrimary = Color(0xFFFFFFFF)
internal val LightPrimaryContainer = OceanFoam
internal val LightOnPrimaryContainer = OceanNavy
internal val LightInversePrimary = OceanLightCyan

internal val LightSecondary = OceanCyan
internal val LightOnSecondary = OceanNavy
internal val LightSecondaryContainer = OceanFoam
internal val LightOnSecondaryContainer = OceanNavy

internal val LightTertiary = OceanBlue
internal val LightOnTertiary = Color(0xFFFFFFFF)
internal val LightTertiaryContainer = OceanLightCyan
internal val LightOnTertiaryContainer = OceanNavy

internal val LightBackgroundColor = LightBackground
internal val LightSurfaceColor = LightBackground
internal val LightSurfaceVariantColor = Color(0xFFDFF2F9)

internal val LightSurfaceContainerLowestColor = Color(0xFFFFFFFF)
internal val LightSurfaceContainerLowColor = LightSurfaceContainerLow
internal val LightSurfaceContainerColor = LightSurfaceContainer
internal val LightSurfaceContainerHighColor = LightSurfaceContainerHigh
internal val LightSurfaceContainerHighestColor = LightSurfaceContainerHighest

internal val LightInverseSurface = Color(0xFF0A2A3A)
internal val LightInverseOnSurface = OceanFoam
internal val LightScrim = Color(0xFF000000)

internal val LightOnSurfaceColor = OceanNavy
internal val LightOnSurfaceVariantColor = LightOnSurfaceVariant
internal val LightOutlineColor = LightOutline
internal val LightOutlineVariantColor = LightOutlineVariant

internal val LightError = Color(0xFFB3261E)
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightErrorContainer = Color(0xFFF9DEDC)
internal val LightOnErrorContainer = Color(0xFF410E0B)

// ---------------------------------------------------------------------------------------
// Dark scheme
// ---------------------------------------------------------------------------------------
internal val DarkPrimary = OceanCyan
internal val DarkOnPrimary = OceanNavy
internal val DarkPrimaryContainerColor = Color(0xFF004C66)
internal val DarkOnPrimaryContainer = OceanFoam
internal val DarkInversePrimary = OceanBlue

internal val DarkSecondary = OceanLightCyan
internal val DarkOnSecondary = OceanNavy
internal val DarkSecondaryContainer = Color(0xFF00394D)
internal val DarkOnSecondaryContainer = OceanFoam

internal val DarkTertiary = OceanLightCyan
internal val DarkOnTertiary = OceanNavy
internal val DarkTertiaryContainerColor = Color(0xFF00394D)
internal val DarkOnTertiaryContainer = OceanFoam

internal val DarkBackgroundColor = DarkBackground
internal val DarkOnBackgroundColor = DarkOnBackground
internal val DarkSurfaceColor = DarkBackground
internal val DarkOnSurfaceColor = DarkOnBackground
internal val DarkSurfaceVariantColor = Color(0xFF1E1E1E)

internal val DarkSurfaceContainerLowestColor = DarkSurfaceContainerLowest
internal val DarkSurfaceContainerLowColor = DarkSurfaceContainerLow
internal val DarkSurfaceContainerColor = DarkSurfaceContainer
internal val DarkSurfaceContainerHighColor = DarkSurfaceContainerHigh
internal val DarkSurfaceContainerHighestColor = DarkSurfaceContainerHighest

internal val DarkInverseSurface = Color(0xFFF5F5F5)
internal val DarkInverseOnSurface = Color(0xFF1C1C1C)
internal val DarkScrim = Color(0xFF000000)

internal val DarkOnSurfaceVariantColor = DarkOnSurfaceVariant
internal val DarkOutlineColor = DarkOutline
internal val DarkOutlineVariantColor = DarkOutlineVariant

internal val DarkError = Color(0xFFFFB4AB)
internal val DarkOnError = Color(0xFF690005)
internal val DarkErrorContainer = Color(0xFF93000A)
internal val DarkOnErrorContainer = Color(0xFFFFDAD6)
