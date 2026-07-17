package com.sigverage.app.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sigverage.app.BuildConfig
import com.sigverage.app.R
import com.sigverage.app.model.RecordingSchedule
import com.sigverage.app.model.SamplingMode
import com.sigverage.app.model.ThemeMode
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch

/**
 * Modern card-based Settings screen, reachable via the bottom NavigationBar tab.
 *
 * Each section is wrapped in a rounded [Card] with subtle elevation,
 * section headers use icons for visual hierarchy, and rows follow
 * Material 3's ListItem patterns with consistent spacing.
 */
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsState()
    val readings by viewModel.readings.collectAsState()
    val schedules by viewModel.schedules.collectAsState()

    var showRetentionDialog by remember { mutableStateOf(false) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSamplingModeDialog by remember { mutableStateOf(false) }
    var showPermissionsPage by remember { mutableStateOf(false) }
    var showSchedulesPage by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<RecordingSchedule?>(null) }
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

    // Background-reliability status (battery-optimisation exemption + exact
    // alarms). Owned by the OS, not our prefs, so re-read it whenever we
    // return to the app - e.g. after the user changes it in system settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    var reliability by remember { mutableStateOf(readReliabilityStatus(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                reliability = readReliabilityStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Dedicated permissions sub-page
    if (showPermissionsPage) {
        PermissionsPage(onBack = { showPermissionsPage = false })
        return
    }

    // Dedicated schedules sub-page
    if (showSchedulesPage) {
        SchedulesPage(
            schedules = schedules,
            onBack = { showSchedulesPage = false },
            onAdd = { editingSchedule = null; showScheduleDialog = true },
            onEdit = { sched -> editingSchedule = sched; showScheduleDialog = true },
            onDelete = { viewModel.deleteSchedule(it) },
            onToggleEnabled = { viewModel.toggleScheduleEnabled(it) },
        )
        return
    }

    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // -- Recording section --
        SettingsCard(
            headerIcon = Icons.Default.Schedule,
            headerTitle = stringResource(R.string.settings_section_recording),
        ) {
            SwitchRow(
                title = stringResource(R.string.settings_auto_record_title),
                subtitle = stringResource(R.string.settings_auto_record_subtitle),
                checked = ui.autoRecordEnabled,
                onCheckedChange = viewModel::setAutoRecordEnabled,
            )
            CardDivider()
            SettingsRow(
                title = stringResource(R.string.settings_sampling_mode_title),
                subtitle = stringResource(R.string.settings_sampling_mode_subtitle),
                value = samplingModeLabelFor(ui.samplingMode),
                onClick = { showSamplingModeDialog = true },
            )
            CardDivider()
            SettingsRow(
                title = stringResource(R.string.settings_schedules_title),
                subtitle = stringResource(R.string.settings_schedules_subtitle),
                value = if (schedules.isEmpty()) null
                        else stringResource(R.string.schedule_count, schedules.size),
                onClick = { showSchedulesPage = true },
            )
        }

        // -- Reliability section --
        // These control whether Android lets the app keep sampling in the
        // background and fire schedule alarms on time. Both are OS-level
        // toggles we can only deep-link to, not set directly.
        SettingsCard(
            headerIcon = Icons.Default.Bolt,
            headerTitle = stringResource(R.string.settings_section_reliability),
        ) {
            SettingsRow(
                title = stringResource(R.string.settings_battery_opt_title),
                subtitle = stringResource(R.string.settings_battery_opt_subtitle),
                value = stringResource(
                    if (reliability.batteryUnrestricted) R.string.settings_battery_opt_on
                    else R.string.settings_battery_opt_off
                ),
                onClick = { openBatteryOptimizationSettings(context) },
            )
            if (reliability.exactAlarmRelevant) {
                CardDivider()
                SettingsRow(
                    title = stringResource(R.string.settings_exact_alarm_title),
                    subtitle = stringResource(R.string.settings_exact_alarm_subtitle),
                    value = stringResource(
                        if (reliability.exactAlarmAllowed) R.string.settings_exact_alarm_on
                        else R.string.settings_exact_alarm_off
                    ),
                    onClick = { openExactAlarmSettings(context) },
                )
            }
        }

        // -- Appearance section --
        SettingsCard(
            headerIcon = Icons.Default.Palette,
            headerTitle = stringResource(R.string.settings_section_appearance),
        ) {
            SettingsRow(
                title = stringResource(R.string.settings_theme_title),
                subtitle = stringResource(R.string.settings_theme_subtitle),
                value = themeLabelFor(ui.themeMode),
                onClick = { showThemeDialog = true },
            )
            CardDivider()
            SwitchRow(
                title = stringResource(R.string.settings_dynamic_color_title),
                subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                checked = ui.dynamicColorEnabled,
                onCheckedChange = viewModel::setDynamicColorEnabled,
            )
        }

        // -- Data section --
        SettingsCard(
            headerIcon = Icons.Default.CloudDownload,
            headerTitle = stringResource(R.string.settings_section_data),
        ) {
            SettingsRow(
                title = stringResource(R.string.settings_retention_title),
                subtitle = stringResource(R.string.settings_retention_subtitle),
                value = retentionLabelFor(ui.retentionDays),
                onClick = { showRetentionDialog = true },
            )
            CardDivider()
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
            CardDivider()
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

        // -- Permissions section --
        SettingsCard(
            headerIcon = Icons.Default.Security,
            headerTitle = stringResource(R.string.settings_section_permissions),
        ) {
            SettingsRow(
                title = stringResource(R.string.settings_permissions_title),
                subtitle = stringResource(R.string.settings_permissions_subtitle),
                onClick = { showPermissionsPage = true },
            )
        }

        // -- About section --
        SettingsCard(
            headerIcon = Icons.Default.Info,
            headerTitle = stringResource(R.string.settings_section_about),
        ) {
            AboutRow(
                label = stringResource(R.string.about_version_value, BuildConfig.VERSION_NAME),
            )
            CardDivider()
            AboutRow(
                label = stringResource(R.string.about_android_version_label),
                value = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            )
            CardDivider()
            AboutRow(
                label = stringResource(R.string.about_blurb),
                isSubtitle = true,
            )
        }

        Spacer(Modifier.height(16.dp))
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
    if (showSamplingModeDialog) {
        SamplingModeDialog(
            current = ui.samplingMode,
            onDismiss = { showSamplingModeDialog = false },
            onPick = { mode ->
                viewModel.setSamplingMode(mode)
                showSamplingModeDialog = false
            },
        )
    }
    if (showScheduleDialog) {
        ScheduleDialog(
            existing = editingSchedule,
            onDismiss = { showScheduleDialog = false; editingSchedule = null },
            onSave = { schedule ->
                viewModel.saveSchedule(schedule)
                showScheduleDialog = false
                editingSchedule = null
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

// ---------------------------------------------------------------------------
// Card-based section wrapper
// ---------------------------------------------------------------------------

/**
 * A rounded card with an icon + title header, wrapping child rows.
 * Uses [CardDefaults.cardColors] for automatic light/dark surface handling.
 */
@Composable
private fun SettingsCard(
    headerIcon: ImageVector,
    headerTitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            // Section header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = headerIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            content()
        }
    }
}

// ---------------------------------------------------------------------------
// Inner dividers (no outer margins - live inside the card)
// ---------------------------------------------------------------------------

@Composable
private fun CardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

// ---------------------------------------------------------------------------
// Row variants
// ---------------------------------------------------------------------------

/**
 * A row with a trailing [Switch]. The whole row is tappable.
 */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

/**
 * Generic tappable settings row with optional value and trailing icon.
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    destructive -> MaterialTheme.colorScheme.error
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        if (destructive) {
            Icon(
                imageVector = Icons.Default.DeleteForever,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp),
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Read-only info row for the About section.
 */
@Composable
private fun AboutRow(
    label: String,
    value: String? = null,
    isSubtitle: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = if (isSubtitle) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = if (isSubtitle) MaterialTheme.typography.bodySmall
                    else MaterialTheme.typography.bodyLarge,
            color = if (isSubtitle) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
        )
        if (value != null) {
            Spacer(Modifier.weight(1f))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Label helpers
// ---------------------------------------------------------------------------

@Composable
private fun themeLabelFor(mode: ThemeMode): String = when (mode) {
    ThemeMode.System -> stringResource(R.string.theme_system)
    ThemeMode.Light -> stringResource(R.string.theme_light)
    ThemeMode.Dark -> stringResource(R.string.theme_dark)
}

// ---------------------------------------------------------------------------
// Background-reliability status + system deep-links
// ---------------------------------------------------------------------------

/**
 * Live OS-level reliability state for the Settings rows. [exactAlarmRelevant]
 * is false below Android 12, where exact alarms are always permitted and the
 * row is hidden.
 */
private data class ReliabilityStatus(
    val batteryUnrestricted: Boolean,
    val exactAlarmRelevant: Boolean,
    val exactAlarmAllowed: Boolean,
)

private fun readReliabilityStatus(context: Context): ReliabilityStatus {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    // If the service is missing (shouldn't happen), assume unrestricted so we
    // don't nag the user about something they can't change.
    val batteryUnrestricted =
        pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true

    val exactAlarmRelevant = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // Inline the SDK guard (not via the local above) so lint's API check sees it.
    val exactAlarmAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        am?.canScheduleExactAlarms() ?: false
    } else {
        true
    }
    return ReliabilityStatus(batteryUnrestricted, exactAlarmRelevant, exactAlarmAllowed)
}

/**
 * Open the system battery-optimisation list so the user can mark Sigverage
 * "unrestricted". Uses the settings-list action, which needs no extra
 * permission (unlike the direct-request dialog).
 */
private fun openBatteryOptimizationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/**
 * Open the per-app "Alarms & reminders" screen (Android 12+) so the user can
 * allow exact alarms, which schedules rely on to start/stop on time.
 */
private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val perApp = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(perApp) }.onFailure {
        // Some OEMs don't honour the per-app data URI; fall back to the
        // generic action.
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

@Composable
private fun samplingModeLabelFor(mode: SamplingMode): String = when (mode) {
    SamplingMode.Auto -> stringResource(R.string.sampling_mode_auto)
    SamplingMode.PowerSaver -> stringResource(R.string.sampling_mode_power_saver)
    SamplingMode.Balanced -> stringResource(R.string.sampling_mode_balanced)
    SamplingMode.HighAccuracy -> stringResource(R.string.sampling_mode_high_accuracy)
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

// ---------------------------------------------------------------------------
// Permissions sub-page
// ---------------------------------------------------------------------------

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
