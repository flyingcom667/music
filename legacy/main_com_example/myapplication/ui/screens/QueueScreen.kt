package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.myapplication.domain.model.QueueItem
import com.example.myapplication.ui.MetadataTextDirection
import com.example.myapplication.ui.components.EmptyState
import com.example.myapplication.ui.components.SongArtwork
import com.example.myapplication.util.Formatters

private val QueueRowHeight = 68.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    queue: List<QueueItem>,
    currentIndex: Int,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onItemClick: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = remember { mutableStateListOf<QueueItem>() }
    LaunchedEffect(queue) {
        if (items.map { it.mediaId } != queue.map { it.mediaId }) {
            items.clear()
            items.addAll(queue)
        }
    }

    val itemHeightPx = with(LocalDensity.current) { QueueRowHeight.toPx() }
    var draggingMediaId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Queue", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = Formatters.songCount(items.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                TextButton(onClick = onClear, enabled = items.isNotEmpty()) { Text("Clear") }
            },
        )

        if (items.isEmpty()) {
            EmptyState(
                title = "Queue is empty",
                description = "Play a song and it will show up here.",
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            itemsIndexed(items, key = { _, item -> item.mediaId }) { index, item ->
                val indexState = rememberUpdatedState(index)
                val isCurrent = index == currentIndex
                val isDragging = draggingMediaId == item.mediaId

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(QueueRowHeight)
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragging) dragOffset else 0f }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isDragging -> MaterialTheme.colorScheme.primaryContainer
                                isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.surface
                            },
                        )
                        .clickable { onItemClick(indexState.value) }
                        .pointerInput(item.mediaId) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingMediaId = item.mediaId
                                    dragOffset = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount.y
                                    val from = indexState.value
                                    val shift = (dragOffset / itemHeightPx).toInt()
                                    val to = (from + shift).coerceIn(0, items.lastIndex)
                                    if (to != from) {
                                        items.add(to, items.removeAt(from))
                                        onMove(from, to)
                                        dragOffset -= (to - from) * itemHeightPx
                                    }
                                },
                                onDragEnd = {
                                    draggingMediaId = null
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    draggingMediaId = null
                                    dragOffset = 0f
                                },
                            )
                        }
                        .padding(start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = "Reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    SongArtwork(
                        mediaId = item.mediaId,
                        albumId = item.albumId,
                        modifier = Modifier.size(40.dp),
                        cornerRadius = 8.dp,
                        iconSize = 16.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium.copy(textDirection = MetadataTextDirection),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = item.artist.orEmpty(),
                            style = MaterialTheme.typography.labelSmall.copy(textDirection = MetadataTextDirection),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (isCurrent) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = if (isPlaying) "Playing" else "Paused",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = Formatters.duration(item.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box {
                        IconButton(onClick = { onRemove(indexState.value) }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Remove from queue",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
