package com.example.myapplication.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The first jump of the button; the jump grows while the button is held down. */
private const val STEP_SMALL_MS = 10_000L
private const val STEP_MEDIUM_MS = 30_000L
private const val STEP_LARGE_MS = 60_000L

/** How long the button has to be held before the jump grows. */
private const val HOLD_DELAY_MS = 500L

/** The delay between two jumps while the button is held down. */
private const val REPEAT_DELAY_MS = 350L

/** How long the button has to be held before the jump grows to the biggest step. */
private const val LARGE_AFTER_MS = 2_000L

/**
 * A button that jumps forward or backward in the track.
 *
 * A short press jumps ten seconds. When the button is held down the jump grows to thirty and
 * then to sixty seconds, and the song keeps moving while the button is held.
 */
@Composable
fun HoldToSeekButton(
    icon: ImageVector,
    contentDescription: String,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var stepMs by remember { mutableLongStateOf(0L) }
    var pressed by remember { mutableStateOf(false) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        IconButton(
            onClick = {},
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        stepMs = STEP_SMALL_MS
                        onSeek(STEP_SMALL_MS)
                        coroutineScope {
                            val job = launch {
                                delay(HOLD_DELAY_MS.milliseconds)
                                stepMs = STEP_MEDIUM_MS
                                var heldFor = HOLD_DELAY_MS
                                while (true) {
                                    delay(REPEAT_DELAY_MS.milliseconds)
                                    heldFor += REPEAT_DELAY_MS
                                    if (heldFor >= LARGE_AFTER_MS) stepMs = STEP_LARGE_MS
                                    onSeek(stepMs)
                                }
                            }
                            tryAwaitRelease()
                            job.cancel()
                        }
                        stepMs = 0L
                        pressed = false
                    },
                )
            },
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (pressed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        if (stepMs > 0L) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(bottom = 4.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = "+${stepMs / 1000}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
