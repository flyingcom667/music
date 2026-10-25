package com.tidalmusic.player.audioeditor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tidalmusic.player.R
import com.tidalmusic.player.domain.model.Song
import com.tidalmusic.player.ui.components.AlbumArtwork
import com.tidalmusic.player.ui.components.BouncingIconButton
import com.tidalmusic.player.ui.components.TidalAlertDialog
import com.tidalmusic.player.ui.components.TidalTextButton
import com.tidalmusic.player.ui.theme.LocalTidalSeekbarColor
import com.tidalmusic.player.util.TimeFormat

/**
 * The audio editing screen: select a trim range, preview it, optionally combine other
 * songs, adjust/normalize the volume, and export to WAV or M4A through the system file
 * picker. Progress is visible and the export can be cancelled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioEditorScreen(
    viewModel: AudioEditorViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val song = state.song

    val exportWavLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/wav"),
    ) { uri -> uri?.let(viewModel::export) }
    val exportM4aLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/mp4"),
    ) { uri -> uri?.let(viewModel::export) }

    val exportFileName = song.displayName.substringBeforeLast('.')
        .ifBlank { song.title } + "-edit." + state.format.fileExtension

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.editor_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopPreview()
                        onBack()
                    }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Source card
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AlbumArtwork(song = song, size = 64.dp, cornerRadius = 12.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artist.ifBlank { stringResource(R.string.player_unknown_artist) } +
                            " · " + TimeFormat.duration(state.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            EditorSectionTitle(stringResource(R.string.editor_trim_section))
            if (state.combineMode) {
                Text(
                    text = stringResource(R.string.editor_trim_disabled_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                EditorSlider(
                    label = stringResource(R.string.editor_trim_start),
                    value = state.startMs,
                    range = 0f..(state.durationMs.toFloat() - 1000f).coerceAtLeast(0f),
                    onValueChange = { viewModel.setStart(it.toLong()) },
                )
                EditorSlider(
                    label = stringResource(R.string.editor_trim_end),
                    value = state.endMs,
                    range = 1000f..state.durationMs.toFloat().coerceAtLeast(1000f),
                    onValueChange = { viewModel.setEnd(it.toLong()) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BouncingIconButton(
                        onClick = {
                            if (state.isPreviewing) viewModel.stopPreview() else viewModel.startPreview()
                        },
                    ) {
                        Icon(
                            imageVector = if (state.isPreviewing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(
                                if (state.isPreviewing) R.string.editor_stop_preview else R.string.editor_preview,
                            ),
                        )
                    }
                    Text(
                        text = if (state.isPreviewing) {
                            stringResource(R.string.editor_previewing)
                        } else {
                            stringResource(R.string.editor_preview)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            EditorSectionTitle(stringResource(R.string.editor_combine_section))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.editor_combine_toggle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.combineMode, onCheckedChange = viewModel::setCombineMode)
            }
            if (state.combineMode) {
                val selectedCount = state.selectedCombineIds.size
                Text(
                    text = stringResource(R.string.editor_combine_selected, selectedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LazyColumn(modifier = Modifier.height(220.dp)) {
                        items(items = viewModel.candidates.filter { it.id != song.id }, key = { it.id }) { candidate ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = candidate.id in state.selectedCombineIds,
                                    onCheckedChange = { viewModel.toggleCombineCandidate(candidate.id) },
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = candidate.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = candidate.artist.ifBlank { "—" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            EditorSectionTitle(stringResource(R.string.editor_output_section))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.format == ExportFormat.M4A,
                    onClick = { viewModel.setFormat(ExportFormat.M4A) },
                    label = { Text(stringResource(R.string.editor_format_m4a)) },
                )
                FilterChip(
                    selected = state.format == ExportFormat.WAV,
                    onClick = { viewModel.setFormat(ExportFormat.WAV) },
                    label = { Text(stringResource(R.string.editor_format_wav)) },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.editor_normalize),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = state.normalize, onCheckedChange = viewModel::setNormalize)
            }
            Column {
                Text(
                    text = stringResource(R.string.editor_gain, state.gainPercent),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Slider(
                    value = state.gainPercent.toFloat(),
                    onValueChange = { viewModel.setGainPercent(it.toInt()) },
                    valueRange = 50f..200f,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = LocalTidalSeekbarColor.current,
                        activeTrackColor = LocalTidalSeekbarColor.current,
                    ),
                )
            }

            val exporting = state.exportProgress != null
            if (exporting) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { state.exportProgress ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.editor_exporting),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TidalTextButton(onClick = viewModel::cancelExport) {
                            Text(stringResource(R.string.editor_cancel))
                        }
                    }
                }
            } else {
                Button(
                    onClick = {
                        if (state.format == ExportFormat.WAV) {
                            exportWavLauncher.launch(exportFileName)
                        } else {
                            exportM4aLauncher.launch(exportFileName)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCut,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.editor_export),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            if (state.exportDone) {
                Text(
                    text = stringResource(R.string.editor_export_done),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    state.exportErrorRes?.let { errorRes ->
        TidalAlertDialog(
            onDismissRequest = viewModel::dismissExportError,
            title = { Text(stringResource(R.string.editor_export_failed_title)) },
            text = { Text(stringResource(errorRes)) },
            confirmButton = {
                TidalTextButton(onClick = viewModel::dismissExportError) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

@Composable
private fun EditorSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun EditorSlider(
    label: String,
    value: Long,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Long) -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = TimeFormat.duration(value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.toFloat().coerceIn(range.start, range.endInclusive),
            onValueChange = { onValueChange(it.toLong()) },
            valueRange = range,
            enabled = range.start < range.endInclusive,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = LocalTidalSeekbarColor.current,
                activeTrackColor = LocalTidalSeekbarColor.current,
            ),
        )
    }
}
