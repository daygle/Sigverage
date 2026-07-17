package com.sigverage.app.ui

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sigverage.app.BuildConfig
import com.sigverage.app.R
import com.sigverage.app.model.ThemeMode
import kotlinx.coroutines.launch

/**
 * Top-level Settings screen, reachable via the bottom NavigationBar tab.
 *
 * Sections:
 *  - **Recording** - sample interval + auto-record-on-launch toggle (moved from AppBar)
 *  - **Appearance** - theme + dynamic colour
 *  - **Data** - auto-expire retention + delete everything (moved from AppBar)
 *  - **Permissions** - runtime permission status, grant / open-settings actions
 *  - **About** - version + Android version + privacy blurb
 *
 * Note: coverage cell size used to live here as a slider, but it has been
 * hard-coded to a fixed value (see `CoverageGridOverlay.DEFAULT_STORAGE_ZOOM`)
 * - there is no longer any per-row granularity knob.
 *
 * Each row opens an AlertDialog that owns its own commit-callback. The
 * Scaffold's SnackbarHost (in MainScreen) still receives VM events, so
 * "Removed N old readings" feedback after a retention sweep still surfaces
 * correctly when the user is browsing Settings.
 */
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsState()
    val readings by viewModel.readings.collectAsState()

    var showIntervalDialog by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showPermissionsPage by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val csvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val n = viewModel.exportCsv(uri)
            val msg = when {
                n > 0 -> context.getString(R.string.export_done, n)
                n == 0 -> context.getString(R.string.export_nothing)
                else -> context.getString(R.string.export_failed, "I/O error")
            }
            viewModel.emitEvent(msg)
        }
    }

    // Dedicated permissions sub-page
    if (showPermissionsPage) {
        PermissionsPage(onBack = { showPermissionsPage = false })
        return
    }

    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(vertical = 12.dp)
    ) {
        Section(stringResource(R.string.settings_section_recording)) {
            SettingsRow(
                title = stringResource(R.string.settings_sample_interval_title),
                subtitle = stringResource(R.string.settings_sample_interval_subtitle),
                value = intervalLabelFor(ui.samplingIntervalMs),
                onClick = { showIntervalDialog = true },
            )
            SwitchRow(
                title = stringResource(R.string.settings_auto_record_title),
                subtitle = stringResource(R.string.settings_auto_record_subtitle),
                checked = ui.autoRecordEnabled,
                onCheckedChange = viewModel::setAutoRecordEnabled,
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Spacer(Modifier.height(8.dp))

        Section(stringResource(R.string.settings_section_appearance)) {
            SettingsRow(
                title = stringResource(R.string.settings_theme_title),
                subtitle = stringResource(R.string.settings_theme_subtitle),
                value = themeLabelFor(ui.themeMode),
                onClick = { showThemeDialog = true },
            )
            SwitchRow(
                title = stringResource(R.string.settings_dynamic_color_title),
                subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                checked = ui.dynamicColorEnabled,
                onCheckedChange = viewModel::setDynamicColorEnabled,
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Spacer(Modifier.height(8.dp))

        Section(stringResource(R.string.settings_section_data)) {
            SettingsRow(
                title = stringResource(R.string.settings_retention_title),
                subtitle = stringResource(R.string.settings_retention_subtitle),
                value = retentionLabelFor(ui.retentionDays),
                onClick = { showRetentionDialog = true },
            )
            SettingsRow(
                title = stringResource(R.string.export_csv),
                subtitle = if (readings.isEmpty()) {
                    stringResource(R.string.export_nothing)
                } else {
                    stringResource(R.string.settings_export_count, readings.size)
                },
                enabled = readings.isNotEmpty(),
                onClick = { csvLauncher.launch("sigverage_${System.currentTimeMillis()}.csv") },
            )
            SettingsRow(
                title = stringResource(R.string.settings_delete_all_title),
                subtitle = if (readings.isEmpty()) {
                    stringResource(R.string.settings_delete_all_empty)
                } else {
                    stringResource(R.string.settings_delete_all_count, readings.size)
                },
                destructive = true,
                enabled = readings.isNotEmpty(),
                onClick = { confirmDeleteAll = true },
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Spacer(Modifier.height(8.dp))

        Section(stringResource(R.string.settings_section_permissions)) {
            SettingsRow(
                title = stringResource(R.string.settings_permissions_title),
                subtitle = stringResource(R.string.settings_permissions_subtitle),
                onClick = { showPermissionsPage = true },
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Spacer(Modifier.height(8.dp))

        Section(stringResource(R.string.settings_section_about)) {
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(
                            R.string.about_version_value,
                            BuildConfig.VERSION_NAME,
                        )
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.about_blurb),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            )
            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.about_android_version_label))
                },
                supportingContent = {
                    Text(
                        text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                },
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showIntervalDialog) {
        IntervalDialog(
            current = ui.samplingIntervalMs,
            onDismiss = { showIntervalDialog = false },
            onPicked = { ms ->
                viewModel.setInterval(ms)
                showIntervalDialog = false
            },
        )
    }
    if (showRetentionDialog) {
        RetentionDialog(
            currentDays = ui.retentionDays,
            onDismiss = { showRetentionDialog = false },
            onPick = { days ->
                viewModel.setRetentionDays(days)
                showRetentionDialog = false
            },
        )
    }
    if (showThemeDialog) {
        ThemeDialog(
            current = ui.themeMode,
            onDismiss = { showThemeDialog = false },
            onPick = { mode ->
                viewModel.setThemeMode(mode)
                showThemeDialog = false
            },
        )
    }
    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text(stringResource(R.string.confirm_delete_all_title)) },
            text = { Text(stringResource(R.string.confirm_delete_all_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAll()
                    confirmDeleteAll = false
                }) { Text(stringResource(R.string.confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) {
                    Text(stringResource(R.string.confirm_no))
                }
            }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

/**
 * Variant of [SettingsRow] with a trailing [Switch]. The whole row is
 * tappable so the user doesn't have to fish for the small switch hit-target,
 * matching Android Settings' standard pattern.
 */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        headlineContent = { Text(text = title) },
        supportingContent = {
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
    HorizontalDivider()
}

/**
 * Generic `ListItem`-based settings row.
 *
 * `value` (a coloured primary line) is shown when present, e.g. the current
 * state ("5 s"). `subtitle` is the explanatory grey line. `destructive`
 * turns title red and swaps the trailing chevron for a trash icon - used by
 * "Delete all readings". `enabled = false` greys the row out and disables
 * taps (e.g., when there's nothing to delete).
 */
@Composable
private fun SettingsRow(
    title: String,
    subtitle: String?,
    value: String? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        headlineContent = {
            Text(
                text = title,
                color = when {
                    destructive -> MaterialTheme.colorScheme.error
                    !enabled -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        },
        supportingContent = {
            Column {
                if (value != null) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        },
        trailingContent = {
            if (destructive) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.error
                           else MaterialTheme.colorScheme.outline,
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        },
    )
    HorizontalDivider()
}


@Composable
private fun intervalLabelFor(ms: Long): String = when (ms) {
    3_000L -> stringResource(R.string.interval_option_fast)
    5_000L -> stringResource(R.string.interval_option_normal)
    15_000L -> stringResource(R.string.interval_option_slow)
    60_000L -> stringResource(R.string.interval_option_glacial)
    else -> stringResource(R.string.time_seconds, (ms / 1_000L).toInt())
}

@Composable
private fun themeLabelFor(mode: ThemeMode): String = when (mode) {
    ThemeMode.System -> stringResource(R.string.theme_system)
    ThemeMode.Light -> stringResource(R.string.theme_light)
    ThemeMode.Dark -> stringResource(R.string.theme_dark)
}

@Composable
private fun retentionLabelFor(days: Int): String = when (days) {
    0 -> stringResource(R.string.retention_forever)
    30 -> stringResource(R.string.retention_30_days)
    90 -> stringResource(R.string.retention_90_days)
    180 -> stringResource(R.string.retention_6_months)
    365 -> stringResource(R.string.retention_1_year)
    else -> stringResource(R.string.time_days, days)
}

/**
 * Dedicated permissions sub-page with a back button in the top app bar.
 * Reached by tapping the "Permissions" drill-out row in [SettingsScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionsPage(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_permissions_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_permissions_back),
                        )
                    }
                },
            )
        }
    ) { padding ->
        PermissionsSection(modifier = Modifier.padding(padding))
    }
}
