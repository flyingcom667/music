package com.tidalmusic.player.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.tidalmusic.player.R
import com.tidalmusic.player.util.BluetoothDeviceInfo
import com.tidalmusic.player.util.connectedBluetoothDevices
import com.tidalmusic.player.domain.model.PlaybackProgress
import com.tidalmusic.player.domain.model.PlaybackState
import com.tidalmusic.player.domain.model.Song
import com.tidalmusic.player.ui.components.AlbumArtwork
import com.tidalmusic.player.ui.components.ConnectedDeviceChip
import com.tidalmusic.player.ui.components.PlaybackControls
import com.tidalmusic.player.ui.components.PlaybackProgressSlider
import com.tidalmusic.player.ui.components.PlayingGradientBackdrop
import com.tidalmusic.player.ui.components.SAFE_VOLUME_FRACTION
import com.tidalmusic.player.ui.components.safeLevelMarker
import com.tidalmusic.player.util.rememberAudioOutputKind
import com.tidalmusic.player.ui.theme.LocalTidalSeekbarColor
import com.tidalmusic.player.ui.components.TidalTextButton

/**
 * The full Now Playing screen.
 *
 * The layout adapts to the available height: the artwork shrinks on small screens instead of
 * pushing the transport controls off the bottom.
 */
@Composable
fun NowPlayingScreen(
    state: PlaybackState,
    progress: PlaybackProgress,
    isFavorite: Boolean,
    artworkAnimationEnabled: Boolean,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenMore: () -> Unit,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeed: (Float) -> Unit,
    onSongInfo: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = state.currentSong
    val artworkSize = 300.dp
    var showVolumeSheet by remember { mutableStateOf(false) }
    // Bluetooth and wired outputs go straight into the ears: the safe-level red line is only
    // meaningful for those routes, not for the loudspeaker.
    val outputKind = rememberAudioOutputKind()
    val showSafeLevel = outputKind.isPersonal
    var lastAudibleVolume by remember { mutableFloatStateOf(volume.takeIf { it > 0f } ?: 1f) }

    androidx.compose.runtime.LaunchedEffect(volume) {
        if (volume > 0f) lastAudibleVolume = volume
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Calm ocean-gradient sweep while the music plays; frozen in place while paused.
        PlayingGradientBackdrop(isPlaying = state.isPlaying, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.player_minimize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.player_now_playing),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenQueue, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = stringResource(R.string.action_queue),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpenMore, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.action_more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (song != null) {
            Artwork(
                song = song,
                size = artworkSize,
                animate = artworkAnimationEnabled && state.isPlaying,
            )

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artist.ifBlank { stringResource(R.string.player_unknown_artist) },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (song.album.isNotBlank()) {
                        Text(
                            text = song.album,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = if (isFavorite) {
                            Icons.Rounded.Favorite
                        } else {
                            Icons.Rounded.FavoriteBorder
                        },
                        contentDescription = stringResource(
                            if (isFavorite) R.string.action_unfavorite else R.string.action_favorite,
                        ),
                        tint = if (isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            PlaybackProgressSlider(
                positionMs = progress.positionMs,
                durationMs = progress.durationMs,
                onSeek = onSeek,
            )

            Spacer(Modifier.height(8.dp))

            PlaybackControls(
                isPlaying = state.isPlaying,
                isBuffering = state.isBuffering,
                shuffleEnabled = state.shuffleEnabled,
                repeatMode = state.repeatMode,
                enabled = true,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onShuffle = onShuffle,
                onRepeat = onRepeat,
                onSeekBackward = {
                    onSeek((progress.positionMs - SEEK_JUMP_MS).coerceAtLeast(0L))
                },
                onSeekForward = {
                    onSeek(
                        (progress.positionMs + SEEK_JUMP_MS)
                            .let { target -> progress.durationMs.takeIf { it > 0L }?.let(target::coerceAtMost) ?: target },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerSecondaryAction(
                    image = if (volume > 0f) {
                        Icons.AutoMirrored.Rounded.VolumeUp
                    } else {
                        Icons.AutoMirrored.Rounded.VolumeOff
                    },
                    label = stringResource(R.string.action_volume),
                    onClick = { showVolumeSheet = true },
                )
                PlayerSecondaryAction(
                    image = Icons.Rounded.Speed,
                    label = stringResource(
                        R.string.player_speed_value,
                        formatSpeed(playbackSpeed),
                    ),
                    onClick = { onPlaybackSpeed(nextSpeed(playbackSpeed)) },
                )
                PlayerSecondaryAction(
                    image = Icons.Rounded.Info,
                    label = stringResource(R.string.action_song_info),
                    onClick = onSongInfo,
                )
                PlayerSecondaryAction(
                    image = Icons.Rounded.Share,
                    label = stringResource(R.string.action_share),
                    onClick = onShare,
                )
            }

            // Advanced connected-device pill: shows when headphones/Bluetooth carry the audio.
            ConnectedDeviceChip()

            Spacer(Modifier.height(24.dp))
            } else {
                EmptyPlayerState(modifier = Modifier.padding(top = 80.dp))
            }
        }
    }

    if (showVolumeSheet) {
        VolumeAdjustmentSheet(
            volume = volume,
            showSafeLevel = showSafeLevel,
            onVolumeChange = {
                if (it > 0f) lastAudibleVolume = it
                onVolumeChange(it)
            },
            onToggleMute = {
                if (volume > 0f) {
                    lastAudibleVolume = volume
                    onVolumeChange(0f)
                } else {
                    onVolumeChange(lastAudibleVolume.coerceIn(0.01f, 1f))
                }
            },
            onDismiss = { showVolumeSheet = false },
        )
    }
}

@Composable
private fun PlayerSecondaryAction(
    image: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = image,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VolumeAdjustmentSheet(
    volume: Float,
    showSafeLevel: Boolean,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (volume > 0f) {
                        Icons.AutoMirrored.Rounded.VolumeUp
                    } else {
                        Icons.AutoMirrored.Rounded.VolumeOff
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.player_volume),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
                TidalTextButton(onClick = onToggleMute) {
                    Text(
                        stringResource(
                            if (volume > 0f) R.string.player_mute else R.string.player_unmute,
                        ),
                    )
                }
            }
            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                valueRange = 0f..1f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .safeLevelMarker(
                        visible = showSafeLevel,
                        color = MaterialTheme.colorScheme.error,
                    ),
                colors = SliderDefaults.colors(
                    thumbColor = LocalTidalSeekbarColor.current,
                    activeTrackColor = if (volume > SAFE_VOLUME_FRACTION) {
                        MaterialTheme.colorScheme.error
                    } else {
                        LocalTidalSeekbarColor.current
                    },
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            )
            if (volume > SAFE_VOLUME_FRACTION) {
                Text(
                    text = stringResource(R.string.player_volume_above_safe_level),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Connected Bluetooth audio devices: name always, battery when the headset
            // reports it (whole-device or per-bud); unsupported devices show name only.
            rememberBluetoothDeviceInfos().forEach { device ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bluetooth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = device.name.ifBlank {
                                stringResource(R.string.bt_device_fallback)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val batteryText = when {
                            device.leftBatteryPercent != null && device.rightBatteryPercent != null ->
                                stringResource(
                                    R.string.bt_battery_lr,
                                    device.leftBatteryPercent,
                                    device.rightBatteryPercent,
                                )

                            device.batteryPercent != null ->
                                stringResource(R.string.bt_battery_level, device.batteryPercent)

                            else -> null
                        }
                        if (batteryText != null) {
                            Text(
                                text = batteryText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

    }
}

private const val SEEK_JUMP_MS = 20_000L

/**
 * The artwork.
 *
 * A very slight scale change marks that the track is playing; the motion is short and stops
 * as soon as playback pauses, and it is disabled entirely when the user turned artwork
 * animation off.
 */
@Composable
private fun Artwork(song: Song, size: Dp, animate: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (animate) 1.005f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "artworkScale",
    )
    Box(
        modifier = Modifier
            .size(size)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        AlbumArtwork(
            song = song,
            size = size,
            cornerRadius = 24.dp,
            modifier = Modifier.scale(scale),
            contentDescription = stringResource(R.string.player_artwork_description, song.title),
        )
    }
}

/**
 * The connected Bluetooth audio outputs, refreshed while the sheet is open: the list
 * recomputes when a device connects or disconnects, and the first open requests
 * BLUETOOTH_CONNECT so battery figures can be read on Android 13+.
 */
@Composable
private fun rememberBluetoothDeviceInfos(): List<BluetoothDeviceInfo> {
    val context = LocalContext.current
    var canAccess by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> canAccess = granted }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canAccess) {
            launcher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
    var refreshTick by remember { mutableIntStateOf(0) }
    DisposableEffect(context) {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                refreshTick++
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                refreshTick++
            }
        }
        audioManager?.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose {
            audioManager?.unregisterAudioDeviceCallback(callback)
        }
    }
    return remember(canAccess, refreshTick) { connectedBluetoothDevices(context, canAccess) }
}

@Composable
private fun EmptyPlayerState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.queue_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** A circular icon button used for the transport row in the player sheet. */
@Composable
internal fun CircleIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .background(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick) { content() }
    }
}
