package com.example.myapplication.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.domain.model.AppFontFamily
import com.example.myapplication.domain.model.AppSettings
import com.example.myapplication.domain.model.PlaybackSpeeds
import com.example.myapplication.domain.model.PlayerTheme
import com.example.myapplication.domain.model.Song
import com.example.myapplication.domain.model.SongListLayout
import com.example.myapplication.domain.model.ThemeMode
import com.example.myapplication.util.AppLocale
import com.example.myapplication.util.Formatters
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    folders: List<String>,
    songCount: Int,
    hiddenCount: Int,
    hiddenSongs: List<Song>,
    isScanning: Boolean,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onToggleExcludedFolder: (String, Boolean) -> Unit,
    onRescan: () -> Unit,
    onClearStatistics: () -> Unit,
    onClearArtworkCache: () -> Unit,
    onOpenHiddenSongs: () -> Unit,
    onRestoreHiddenSong: (Long) -> Unit,
    onRestoreAllHiddenSongs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var folderDialogVisible by remember { mutableStateOf(false) }
    var speedDialogVisible by remember { mutableStateOf(false) }
    var hiddenDialogVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge) })

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item { SectionTitle(stringResource(R.string.settings_section_appearance)) }
            item {
                ChoiceSetting(
                    title = stringResource(R.string.settings_theme),
                    options = listOf(
                        stringResource(R.string.settings_theme_system),
                        stringResource(R.string.settings_theme_light),
                        stringResource(R.string.settings_theme_dark),
                    ),
                    selectedIndex = when (settings.themeMode) {
                        ThemeMode.SYSTEM -> 0
                        ThemeMode.LIGHT -> 1
                        ThemeMode.DARK -> 2
                    },
                    onSelect = { index ->
                        onUpdate {
                            it.copy(
                                themeMode = when (index) {
                                    1 -> ThemeMode.LIGHT
                                    2 -> ThemeMode.DARK
                                    else -> ThemeMode.SYSTEM
                                },
                            )
                        }
                    },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SwitchSetting(
                        title = stringResource(R.string.settings_dynamic_color),
                        subtitle = stringResource(R.string.settings_dynamic_color_summary),
                        checked = settings.dynamicColor,
                        onCheckedChange = { value -> onUpdate { it.copy(dynamicColor = value) } },
                    )
                }
            }
            item {
                ChoiceSetting(
                    title = stringResource(R.string.settings_font),
                    options = listOf(
                        stringResource(R.string.settings_font_system),
                        stringResource(R.string.settings_font_sans),
                        stringResource(R.string.settings_font_serif),
                        stringResource(R.string.settings_font_mono),
                    ),
                    selectedIndex = AppFontFamily.entries.indexOf(settings.fontFamily),
                    onSelect = { index ->
                        onUpdate { it.copy(fontFamily = AppFontFamily.entries[index]) }
                    },
                )
            }
            item {
                SliderSetting(
                    title = stringResource(R.string.settings_font_size),
                    value = settings.fontScale,
                    valueRange = 0.8f..1.4f,
                    steps = 5,
                    valueLabel = "${(settings.fontScale * 100).roundToInt()}%",
                    onValueChange = { value -> onUpdate { it.copy(fontScale = value) } },
                )
            }
            item {
                SwitchSetting(
                    title = stringResource(R.string.settings_animations),
                    subtitle = stringResource(R.string.settings_animations_summary),
                    checked = settings.animationsEnabled,
                    onCheckedChange = { value -> onUpdate { it.copy(animationsEnabled = value) } },
                )
            }
            item {
                SliderSetting(
                    title = stringResource(R.string.settings_glass_intensity),
                    value = settings.glassIntensity,
                    valueRange = 0f..1f,
                    steps = 9,
                    valueLabel = "${(settings.glassIntensity * 100).roundToInt()}%",
                    onValueChange = { value -> onUpdate { it.copy(glassIntensity = value) } },
                )
            }
            item {
                ChoiceSetting(
                    title = stringResource(R.string.settings_list_layout),
                    options = listOf(
                        stringResource(R.string.settings_list_layout_detailed),
                        stringResource(R.string.settings_list_layout_compact),
                        stringResource(R.string.settings_list_layout_artwork),
                    ),
                    selectedIndex = SongListLayout.entries.indexOf(settings.listLayout),
                    onSelect = { index ->
                        onUpdate { it.copy(listLayout = SongListLayout.entries[index]) }
                    },
                )
            }
            item {
                ChoiceSetting(
                    title = stringResource(R.string.settings_player_style),
                    options = listOf(
                        stringResource(R.string.settings_player_glass),
                        stringResource(R.string.settings_player_minimal),
                        stringResource(R.string.settings_player_artwork),
                        stringResource(R.string.settings_player_solid),
                    ),
                    selectedIndex = PlayerTheme.entries.indexOf(settings.playerTheme),
                    onSelect = { index ->
                        onUpdate { it.copy(playerTheme = PlayerTheme.entries[index]) }
                    },
                )
            }

            item { SectionTitle(stringResource(R.string.settings_section_language)) }
            item {
                // The app follows the language of the device, so the current language is only
                // shown — it is not a switch.
                val context = LocalContext.current
                InfoSetting(
                    title = stringResource(R.string.settings_language),
                    value = AppLocale.currentName(context),
                    subtitle = stringResource(R.string.settings_language_summary),
                )
            }

            item { SectionTitle(stringResource(R.string.settings_section_playback)) }
            item {
                RowSetting(
                    title = stringResource(R.string.settings_default_speed),
                    value = Formatters.speed(settings.defaultSpeed),
                    onClick = { speedDialogVisible = true },
                )
            }
            item {
                SwitchSetting(
                    title = stringResource(R.string.settings_resume),
                    subtitle = stringResource(R.string.settings_resume_summary),
                    checked = settings.resumePlayback,
                    onCheckedChange = { value -> onUpdate { it.copy(resumePlayback = value) } },
                )
            }
            item {
                SwitchSetting(
                    title = stringResource(R.string.settings_pause_headphones),
                    checked = settings.pauseWhenHeadphonesDisconnected,
                    onCheckedChange = { value ->
                        onUpdate { it.copy(pauseWhenHeadphonesDisconnected = value) }
                    },
                )
            }
            item {
                SwitchSetting(
                    title = stringResource(R.string.settings_gapless),
                    subtitle = stringResource(R.string.settings_gapless_summary),
                    checked = settings.gaplessPlayback,
                    onCheckedChange = { value -> onUpdate { it.copy(gaplessPlayback = value) } },
                )
            }

            item { SectionTitle(stringResource(R.string.settings_section_background)) }
            item { BatteryOptimisationSetting() }

            item { SectionTitle(stringResource(R.string.settings_section_library)) }
            item {
                RowSetting(
                    title = stringResource(R.string.settings_song_count),
                    value = songCount.toString(),
                    onClick = {},
                )
            }
            item {
                SliderSetting(
                    title = stringResource(R.string.settings_min_duration),
                    value = settings.minimumTrackDurationSec.toFloat(),
                    valueRange = 0f..30f,
                    steps = 29,
                    valueLabel = if (settings.minimumTrackDurationSec == 0) {
                        stringResource(R.string.settings_min_duration_off)
                    } else {
                        "${settings.minimumTrackDurationSec}s"
                    },
                    onValueChange = { value ->
                        onUpdate { it.copy(minimumTrackDurationSec = value.roundToInt()) }
                    },
                )
            }
            item {
                RowSetting(
                    title = stringResource(R.string.settings_excluded_folders),
                    value = if (settings.excludedFolders.isEmpty()) {
                        stringResource(R.string.settings_excluded_folders_none)
                    } else {
                        stringResource(
                            R.string.settings_excluded_folders_count,
                            settings.excludedFolders.size,
                        )
                    },
                    onClick = { folderDialogVisible = true },
                )
            }
            item {
                RowSetting(
                    title = stringResource(R.string.settings_rescan),
                    value = if (isScanning) {
                        stringResource(R.string.settings_rescan_running)
                    } else {
                        stringResource(R.string.settings_rescan_summary)
                    },
                    onClick = { if (!isScanning) onRescan() },
                )
            }
            item {
                RowSetting(
                    title = stringResource(R.string.settings_hidden_songs),
                    value = if (hiddenCount == 0) {
                        stringResource(R.string.settings_hidden_songs_none)
                    } else {
                        stringResource(R.string.settings_hidden_songs_count, hiddenCount)
                    },
                    onClick = {
                        if (hiddenCount > 0) {
                            onOpenHiddenSongs()
                            hiddenDialogVisible = true
                        }
                    },
                )
            }
            item {
                RowSetting(
                    title = stringResource(R.string.settings_clear_statistics),
                    value = stringResource(R.string.settings_clear_statistics_summary),
                    onClick = onClearStatistics,
                )
            }

            item { SectionTitle(stringResource(R.string.settings_section_storage)) }
            item {
                RowSetting(
                    title = stringResource(R.string.settings_clear_artwork),
                    value = stringResource(R.string.settings_clear_artwork_summary),
                    onClick = onClearArtworkCache,
                )
            }

            item { SectionTitle(stringResource(R.string.settings_section_about)) }
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.settings_about_version, versionName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_about_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_privacy_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (folderDialogVisible) {
        ExcludedFoldersDialog(
            folders = folders,
            excluded = settings.excludedFolders,
            onToggle = onToggleExcludedFolder,
            onDismiss = { folderDialogVisible = false },
        )
    }

    if (speedDialogVisible) {
        AlertDialog(
            onDismissRequest = { speedDialogVisible = false },
            title = { Text(stringResource(R.string.settings_default_speed)) },
            text = {
                Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    PlaybackSpeeds.forEach { speed ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdate { it.copy(defaultSpeed = speed) }
                                    speedDialogVisible = false
                                }
                                .padding(vertical = 12.dp),
                        ) {
                            Text(
                                text = Formatters.speed(speed) +
                                    if (speed == settings.defaultSpeed) "   ✓" else "",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { speedDialogVisible = false }) {
                    Text(stringResource(R.string.settings_dialog_close))
                }
            },
        )
    }

    if (hiddenDialogVisible) {
        HiddenSongsDialog(
            songs = hiddenSongs,
            onRestore = onRestoreHiddenSong,
            onRestoreAll = onRestoreAllHiddenSongs,
            onDismiss = { hiddenDialogVisible = false },
        )
    }
}

/** Lists the songs that were removed from the library and brings them back. */
@Composable
private fun HiddenSongsDialog(
    songs: List<Song>,
    onRestore: (Long) -> Unit,
    onRestoreAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_hidden_songs)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                if (songs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_hidden_songs_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                songs.forEach { song ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                            )
                            song.artist?.let { artist ->
                                Text(
                                    text = artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                        TextButton(onClick = { onRestore(song.mediaId) }) {
                            Text(stringResource(R.string.settings_hidden_songs_restore))
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (songs.isNotEmpty()) {
                TextButton(onClick = onRestoreAll) {
                    Text(stringResource(R.string.settings_hidden_songs_restore_all))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_dialog_close)) }
        },
    )
}

@Composable
private fun ExcludedFoldersDialog(
    folders: List<String>,
    excluded: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_excluded_folders)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.settings_excluded_folders_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (folders.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_no_folders),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                folders.forEach { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(folder, folder !in excluded) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = folder in excluded, onCheckedChange = { onToggle(folder, it) })
                        Icon(
                            Icons.Rounded.Folder,
                            contentDescription = null,
                            modifier = Modifier.width(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = folder, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_dialog_done)) }
        },
    )
}

@Composable
private fun SectionTitle(title: String) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun RowSetting(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A read-only row: a title, the current value and an explanation below it.
 */
@Composable
private fun InfoSetting(
    title: String,
    value: String,
    subtitle: String,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceSetting(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(text = label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/**
 * Battery-optimisation exemption. While the app is optimised, Android may kill the playback
 * service after a while in the background; the system dialog lets the user allow unrestricted
 * battery use so music keeps playing.
 */
@Composable
private fun BatteryOptimisationSetting() {
    val context = LocalContext.current
    var unrestricted by remember { mutableStateOf(isIgnoringBatteryOptimisations(context)) }

    // The value is changed on a system screen, so re-read it when the user comes back.
    LaunchedEffect(Unit) {
        while (true) {
            unrestricted = isIgnoringBatteryOptimisations(context)
            delay(1500)
        }
    }

    Column {
        RowSetting(
            title = stringResource(R.string.settings_battery),
            value = if (unrestricted) {
                stringResource(R.string.settings_battery_unrestricted)
            } else {
                stringResource(R.string.settings_battery_restricted)
            },
            onClick = { openBatteryOptimisationSettings(context) },
        )
        if (!unrestricted) {
            Text(
                text = stringResource(R.string.settings_battery_summary),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            )
        }
    }
}

private fun isIgnoringBatteryOptimisations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

private fun openBatteryOptimisationSettings(context: Context) {
    val request = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    )
    val opened = runCatching { context.startActivity(request) }.isSuccess
    if (!opened) {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}

@Composable
private fun SliderSetting(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}
