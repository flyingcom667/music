package com.example.myapplication.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * Mirrors a directional icon when the app runs in a right-to-left layout.
 *
 * Material ships auto-mirrored variants for most directional icons (`Icons.AutoMirrored`),
 * but the transport icons (skip previous / skip next) have none, so they are flipped by hand.
 * Icons that are not directional (play, pause, shuffle, repeat, heart, …) are never mirrored.
 */
@Composable
fun Modifier.mirroredInRtl(): Modifier {
    val layoutDirection = LocalLayoutDirection.current
    return if (layoutDirection == LayoutDirection.Rtl) {
        this.scale(scaleX = -1f, scaleY = 1f)
    } else {
        this
    }
}
