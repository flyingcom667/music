package com.tidalmusic.player.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Ocean Periwinkle palette.
 *
 * The seven brand colours are fixed and used in this order of intensity:
 *
 *   #EDF2FB  main light background
 *   #E2EAFC  subtle grouped surfaces
 *   #D7E3FC  cards and secondary surfaces
 *   #CCDBFD  selected containers
 *   #C1D3FE  interactive containers
 *   #B6CCFE  active accents / selected states
 *   #ABC4FF  strongest brand accent
 *
 * Screens never hard-code a colour: they read roles from MaterialTheme.colorScheme so the
 * light, dark and dynamic schemes all stay consistent.
 */

// ---------------------------------------------------------------------------------------
// The seven supplied brand colours.
// ---------------------------------------------------------------------------------------
val Periwinkle50 = Color(0xFFEDF2FB)
val Periwinkle100 = Color(0xFFE2EAFC)
val Periwinkle200 = Color(0xFFD7E3FC)
val Periwinkle300 = Color(0xFFCCDBFD)
val Periwinkle400 = Color(0xFFC1D3FE)
val Periwinkle500 = Color(0xFFB6CCFE)
val Periwinkle600 = Color(0xFFABC4FF)

// Deep navy foregrounds — readable on every pale periwinkle surface.
private val Navy900 = Color(0xFF172033)
private val Navy800 = Color(0xFF17294B)
private val Navy700 = Color(0xFF263957)
private val Navy600 = Color(0xFF414D66)
private val Navy500 = Color(0xFF52668F)
private val Navy400 = Color(0xFF49618A)
private val Navy300 = Color(0xFF65738D)

// Dark theme surfaces — deep navy, never a plain inversion of the light theme.
private val DarkBackground = Color(0xFF101522)
private val DarkOnBackground = Color(0xFFEAF0FF)
private val DarkSurfaceVariant = Color(0xFF252E43)
private val DarkOnSurfaceVariant = Color(0xFFC0CBE1)
private val DarkSurfaceLowest = Color(0xFF0B101B)
private val DarkSurfaceLow = Color(0xFF151C2B)
private val DarkSurface = Color(0xFF1B2334)
private val DarkSurfaceHigh = Color(0xFF252E43)
private val DarkSurfaceHighest = Color(0xFF303B52)
private val DarkOutline = Color(0xFF8998B5)
private val DarkOutlineVariant = Color(0xFF45536E)
private val DarkContainerNavy = Color(0xFF354A73)

// ---------------------------------------------------------------------------------------
// Light scheme
// ---------------------------------------------------------------------------------------
internal val LightPrimary = Periwinkle600
internal val LightOnPrimary = Navy800
internal val LightPrimaryContainer = Periwinkle200
internal val LightOnPrimaryContainer = Navy800
internal val LightInversePrimary = Periwinkle400

internal val LightSecondary = Navy400
internal val LightOnSecondary = Color(0xFFFFFFFF)
internal val LightSecondaryContainer = Periwinkle100
internal val LightOnSecondaryContainer = Navy700

internal val LightTertiary = Navy500
internal val LightOnTertiary = Color(0xFFFFFFFF)
internal val LightTertiaryContainer = Periwinkle300
internal val LightOnTertiaryContainer = Navy800

internal val LightBackground = Periwinkle50
internal val LightOnBackground = Navy900
internal val LightSurface = Periwinkle50
internal val LightOnSurface = Navy900
internal val LightSurfaceVariant = Periwinkle100
internal val LightOnSurfaceVariant = Navy600
internal val LightOutline = Navy300
internal val LightOutlineVariant = Periwinkle400

internal val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
internal val LightSurfaceContainerLow = Color(0xFFF7F9FF)
internal val LightSurfaceContainer = Periwinkle50
internal val LightSurfaceContainerHigh = Periwinkle100
internal val LightSurfaceContainerHighest = Periwinkle200

internal val LightInverseSurface = Color(0xFF2B3346)
internal val LightInverseOnSurface = Periwinkle50
internal val LightScrim = Color(0xFF000000)

internal val LightError = Color(0xFFB3261E)
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightErrorContainer = Color(0xFFF9DEDC)
internal val LightOnErrorContainer = Color(0xFF410E0B)

// ---------------------------------------------------------------------------------------
// Dark scheme
// ---------------------------------------------------------------------------------------
internal val DarkPrimary = Periwinkle500
internal val DarkOnPrimary = Navy800
internal val DarkPrimaryContainerColor = DarkContainerNavy
internal val DarkOnPrimaryContainer = Periwinkle100
internal val DarkInversePrimary = Navy400

internal val DarkSecondary = Periwinkle400
internal val DarkOnSecondary = Navy800
internal val DarkSecondaryContainer = Color(0xFF2A3A57)
internal val DarkOnSecondaryContainer = Periwinkle200

internal val DarkTertiary = Periwinkle600
internal val DarkOnTertiary = Navy800
internal val DarkTertiaryContainerColor = DarkContainerNavy
internal val DarkOnTertiaryContainer = Periwinkle100

internal val DarkBackgroundColor = DarkBackground
internal val DarkOnBackgroundColor = DarkOnBackground
internal val DarkSurfaceColor = DarkBackground
internal val DarkOnSurfaceColor = DarkOnBackground
internal val DarkSurfaceVariantColor = DarkSurfaceVariant
internal val DarkOnSurfaceVariantColor = DarkOnSurfaceVariant
internal val DarkOutlineColor = DarkOutline
internal val DarkOutlineVariantColor = DarkOutlineVariant

internal val DarkSurfaceContainerLowestColor = DarkSurfaceLowest
internal val DarkSurfaceContainerLowColor = DarkSurfaceLow
internal val DarkSurfaceContainerColor = DarkSurface
internal val DarkSurfaceContainerHighColor = DarkSurfaceHigh
internal val DarkSurfaceContainerHighestColor = DarkSurfaceHighest

internal val DarkInverseSurface = DarkOnBackground
internal val DarkInverseOnSurface = DarkSurface
internal val DarkScrim = Color(0xFF000000)

internal val DarkError = Color(0xFFFFB4AB)
internal val DarkOnError = Color(0xFF690005)
internal val DarkErrorContainer = Color(0xFF93000A)
internal val DarkOnErrorContainer = Color(0xFFFFDAD6)
