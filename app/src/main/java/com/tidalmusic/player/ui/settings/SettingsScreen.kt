package com.tidalmusic.player.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tidalmusic.player.R
import com.tidalmusic.player.data.preferences.AppPreferences
import com.tidalmusic.player.data.preferences.ListDensity
import com.tidalmusic.player.data.preferences.ThemeMode
import com.tidalmusic.player.domain.model.SortOrder
import com.tidalmusic.player.ui.components.TidalAlertDialog
import com.tidalmusic.player.ui.components.SectionHeader
import com.tidalmusic.player.ui.components.TidalTextButton
import com.tidalmusic.player.ui.viewmodel.StorageInfo
import com.tidalmusic.player.util.TimeFormat

/**
 * The settings screen.
 *
 * Every switch and choice here changes real application behaviour; nothing is decorative.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppPreferences.Settings,
    storage: StorageInfo,
    appVersion: String,
    hasAudioPermission: Boolean,
    onBack: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onArtworkAnimationChange: (Boolean) -> Unit,
    onDensityChange: (ListDensity) -> Unit,
    onResumePlaybackChange: (Boolean) -> Unit,
    onShuffleNewSessionsChange: (Boolean) -> Unit,
    onDefaultRepeatChange: (String) -> Unit,
    onDefaultSpeedChange: (Float) -> Unit,
    onIncludeNonMusicChange: (Boolean) -> Unit,
    onShowArtworkChange: (Boolean) -> Unit,
    onDefaultSortChange: (SortOrder) -> Unit,
    onRescan: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenSystemVolume: () -> Unit,
    batteryExempt: Boolean,
    onRequestBatteryExemption: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var themeDialog by remember { mutableStateOf(false) }
    var densityDialog by remember { mutableStateOf(false) }
    var repeatDialog by remember { mutableStateOf(false) }
    var sortDialog by remember { mutableStateOf(false) }
    var speedDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item(key = "appearance-header") {
                SectionHeader(title = stringResource(R.string.settings_appearance))
            }
            item(key = "theme") {
                SettingsRow(
                    title = stringResource(R.string.settings_theme),
                    summary = stringResource(settings.themeMode.labelRes()),
                    onClick = { themeDialog = true },
                )
            }
            item(key = "dynamic") {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    summary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        stringResource(R.string.settings_dynamic_color_summary)
                    } else {
                        stringResource(R.string.settings_dynamic_color_unavailable)
                    },
                    checked = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                    onCheckedChange = onDynamicColorChange,
                )
            }
            item(key = "animation") {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_artwork_animation),
                    summary = stringResource(R.string.settings_artwork_animation_summary),
                    checked = settings.artworkAnimation,
                    onCheckedChange = onArtworkAnimationChange,
                )
            }
            item(key = "density") {
                SettingsRow(
                    title = stringResource(R.string.settings_density),
                    summary = stringResource(settings.density.labelRes()),
                    onClick = { densityDialog = true },
                )
            }

            item(key = "playback-header") {
                SectionHeader(title = stringResource(R.string.settings_playback))
            }
            item(key = "resume") {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_resume_playback),
                    summary = stringResource(R.string.settings_resume_playback_summary),
                    checked = settings.resumePlayback,
                    onCheckedChange = onResumePlaybackChange,
                )
            }
            item(key = "shuffle") {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_shuffle_new),
                    summary = stringResource(R.string.settings_shuffle_new_summary),
                    checked = settings.shuffleNewSessions,
                    onCheckedChange = onShuffleNewSessionsChange,
                )
            }
            item(key = "repeat") {
                SettingsRow(
                    title = stringResource(R.string.settings_default_repeat),
                    summary = stringResource(settings.defaultRepeatMode.repeatLabelRes()),
                    onClick = { repeatDialog = true },
                )
            }
            item(key = "speed") {
                SettingsRow(
                    title = stringResource(R.string.settings_playback_speed),
                    summary = formatSpeedValue(settings.defaultPlaybackSpeed),
                    onClick = { speedDialog = true },
                )
            }

            item(key = "library-header") {
                SectionHeader(title = stringResource(R.string.settings_library))
            }
            item(key = "rescan") {
                SettingsRow(
                    title = stringResource(R.string.settings_rescan),
                    summary = stringResource(R.string.settings_rescan_summary),
                    onClick = onRescan,
                    trailing = {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
            item(key = "non-music") {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_include_non_music),
                    summary = stringResource(R.string.settings_include_non_music_summary),
                    checked = settings.includeNonMusic,
                    onCheckedChange = onIncludeNonMusicChange,
                )
            }
            item(key = "artwork") {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_show_artwork),
                    summary = stringResource(R.string.settings_show_artwork_summary),
                    checked = settings.showArtwork,
                    onCheckedChange = onShowArtworkChange,
                )
            }
            item(key = "sort") {
                SettingsRow(
                    title = stringResource(R.string.settings_default_sort),
                    summary = stringResource(settings.defaultSortOrder.labelRes()),
                    onClick = { sortDialog = true },
                )
            }
            item(key = "audio-header") {
                SectionHeader(title = stringResource(R.string.settings_audio))
            }
            item(key = "volume") {
                SettingsRow(
                    title = stringResource(R.string.settings_volume),
                    summary = stringResource(R.string.settings_system_volume_summary),
                    onClick = onOpenSystemVolume,
                )
            }
            item(key = "battery") {
                SettingsRow(
                    title = stringResource(R.string.settings_battery_title),
                    summary = stringResource(
                        if (batteryExempt) {
                            R.string.settings_battery_summary_on
                        } else {
                            R.string.settings_battery_summary_off
                        },
                    ),
                    onClick = { if (!batteryExempt) onRequestBatteryExemption() },
                )
            }
            item(key = "storage-header") {
                SectionHeader(title = stringResource(R.string.settings_storage))
            }
            item(key = "permission") {
                SettingsRow(
                    title = stringResource(R.string.settings_permission_title),
                    summary = if (hasAudioPermission) {
                        stringResource(R.string.settings_permission_granted)
                    } else {
                        stringResource(R.string.settings_permission_denied)
                    },
                    onClick = { if (hasAudioPermission) onOpenAppSettings() else onRequestPermission() },
                )
            }
            item(key = "storage-info") {
                SettingsRow(
                    title = stringResource(R.string.details_size_field),
                    summary = if (storage.songCount > 0) {
                        stringResource(
                            R.string.settings_storage_info,
                            storage.songCount,
                            TimeFormat.fileSize(storage.totalBytes),
                        )
                    } else {
                        stringResource(R.string.settings_storage_info_unknown, storage.songCount)
                    },
                    onClick = { },
                    enabled = false,
                )
            }
            item(key = "version") {
                SettingsRow(
                    title = stringResource(R.string.settings_version, appVersion),
                    summary = null,
                    onClick = { },
                    enabled = false,
                )
            }
        }
    }

    if (themeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = ThemeMode.entries,
            selected = settings.themeMode,
            label = { stringResource(it.labelRes()) },
            onSelect = {
                onThemeChange(it)
                themeDialog = false
            },
            onDismiss = { themeDialog = false },
        )
    }

    if (densityDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_density),
            options = ListDensity.entries,
            selected = settings.density,
            label = { stringResource(it.labelRes()) },
            onSelect = {
                onDensityChange(it)
                densityDialog = false
            },
            onDismiss = { densityDialog = false },
        )
    }

    if (repeatDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_default_repeat),
            options = listOf("off", "all", "one"),
            selected = settings.defaultRepeatMode,
            label = { stringResource(it.repeatLabelRes()) },
            onSelect = {
                onDefaultRepeatChange(it)
                repeatDialog = false
            },
            onDismiss = { repeatDialog = false },
        )
    }

    if (sortDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_default_sort),
            options = SortOrder.entries,
            selected = settings.defaultSortOrder,
            label = { stringResource(it.labelRes()) },
            onSelect = {
                onDefaultSortChange(it)
                sortDialog = false
            },
            onDismiss = { sortDialog = false },
        )
    }

    if (speedDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_playback_speed),
            options = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f),
            selected = settings.defaultPlaybackSpeed,
            label = { formatSpeedValue(it) },
            onSelect = {
                onDefaultSpeedChange(it)
                speedDialog = false
            },
            onDismiss = { speedDialog = false },
        )
    }
}

/** A row with a title, an optional summary and a trailing control. */
@Composable
fun SettingsRow(
    title: String,
    summary: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        supportingContent = if (summary != null) {
            {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
        trailingContent = trailing,
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** A row with a switch. */
@Composable
fun SettingsSwitchRow(
    title: String,
    summary: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        supportingContent = if (summary != null) {
            {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            null
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
        modifier = modifier.fillMaxWidth(),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** A radio-button dialog used for every single-choice setting. */
@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    TidalAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = { onSelect(option) })
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TidalTextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

internal fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}

internal fun ListDensity.labelRes(): Int = when (this) {
    ListDensity.COMFORTABLE -> R.string.settings_density_comfortable
    ListDensity.COMPACT -> R.string.settings_density_compact
}

internal fun String.repeatLabelRes(): Int = when (this) {
    "all" -> R.string.action_repeat_all
    "one" -> R.string.action_repeat_one
    else -> R.string.action_repeat_off
}

internal fun SortOrder.labelRes(): Int = when (this) {
    SortOrder.TITLE -> R.string.library_sort_title_label
    SortOrder.ARTIST -> R.string.library_sort_artist_label
    SortOrder.ALBUM -> R.string.library_sort_album_label
    SortOrder.DURATION -> R.string.library_sort_duration_label
    SortOrder.DATE_ADDED -> R.string.library_sort_date_added_label
}

internal fun formatSpeedValue(speed: Float): String {
    val text = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()
    return "$text×"
}
