package com.example.myapplication.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Index character used for a song title: A-Z, or '#' for anything else (digits, symbols, …). */
fun alphabetKeyOf(title: String): Char {
    val first = title.trim().firstOrNull() ?: return '#'
    val upper = first.uppercaseChar()
    return if (upper in 'A'..'Z') upper else '#'
}

/** Default index alphabet: '#' first, then A…Z. */
val DefaultAlphabet: List<Char> = listOf('#') + ('A'..'Z').toList()

/**
 * Vertical A–Z index strip. Drag along it to jump through the list; only letters that really
 * exist in the library are passed in, so the strip never shows a dead letter.
 *
 * The strip is narrow and the letters are spread over the full height, so every letter has a
 * comfortable touch target even with a complete alphabet.
 */
@Composable
fun AlphabetIndexBar(
    letters: List<Char>,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (letters.isEmpty()) return
    val haptic = LocalHapticFeedback.current
    var stripHeight by remember { mutableStateOf(1) }
    var lastLetter by remember { mutableStateOf<Char?>(null) }

    fun selectAt(y: Float) {
        val ratio = (y / stripHeight.toFloat()).coerceIn(0f, 1f)
        val index = (ratio * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val letter = letters[index]
        if (letter != lastLetter) {
            lastLetter = letter
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onLetterSelected(letter)
        }
    }

    val density = LocalDensity.current
    val letterHeight = with(density) {
        ((stripHeight - 12).toDp() - 12.dp) / letters.size
    }.coerceIn(14.dp, 26.dp)

    Box(
        modifier = modifier
            .width(20.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f))
            .onSizeChanged { stripHeight = it.height.coerceAtLeast(1) }
            .pointerInput(letters) {
                detectTapGestures { offset -> selectAt(offset.y) }
            }
            .pointerInput(letters) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> selectAt(offset.y) },
                    onVerticalDrag = { change, _ ->
                        change.consume()
                        selectAt(change.position.y)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp),
        ) {
            letters.forEach { letter ->
                val active = letter == lastLetter
                Box(
                    modifier = Modifier.height(letterHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = letter.toString(),
                        color = if (active) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = if (letters.size <= 10) FontWeight.SemiBold else FontWeight.Medium,
                        modifier = if (active) {
                            Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 3.dp, vertical = 1.dp)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}
