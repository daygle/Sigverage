package com.sigverage.app.ui

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sigverage.app.BuildConfig
import com.sigverage.app.R
import com.sigverage.app.model.DateFormat
import com.sigverage.app.model.NetworkType
import com.sigverage.app.model.RecordingSchedule
import com.sigverage.app.model.SamplingMode
import com.sigverage.app.model.ThemeMode
import com.sigverage.app.model.TimeFormat
import com.sigverage.app.ui.theme.rememberNetworkColors
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

/**
 * Full-screen drill-out destinations reachable from the Settings root. Hoisted
 * to [MainScreen] so it can hide the app bar and bottom navigation while a
 * sub-page is on screen, letting the sub-page own the whole screen.
 */
enum class SettingsSubPage { None, Permissions, Schedules, MapFilters, NetworkColors }

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
    subPage: SettingsSubPage,
    onSubPageChange: (SettingsSubPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.ui.collectAsState()
    val readings by viewModel.readings.collectAsState()
    val schedules by viewModel.schedules.collectAsState()

    var showRetentionDialog by remember { mutableStateOf(value = false) }
    var confirmDeleteAll by remember { mutableStateOf(value = false) }
    var showThemeDialog by remember { mutableStateOf(value = false) }
    var showTimeFormatDialog by remember { mutableStateOf(value = false) }
    var showDateFormatDialog by remember { mutableStateOf(value = false) }
    var showSamplingModeDialog by remember { mutableStateOf(value = false) }
    var showScheduleDialog by remember { mutableStateOf(value = false) }
    var editingSchedule by remember { mutableStateOf<RecordingSchedule?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val n = viewModel.exportCsv(uri)
            @Suppress("LocalContextGetResourceValueCall", "LocalContextResourcesRead")
            val msg = when {
                n > 0 -> context.resources.getQuantityString(R.plurals.export_done, n, n)
                n == 0 -> context.getString(R.string.export_nothing)
                else -> context.getString(R.string.export_failed, "I/O error")
            }
            viewModel.emitEvent(msg)
        }
    }
    val csvImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val n = viewModel.importCsv(uri)
            @Suppress("LocalContextGetResourceValueCall", "LocalContextResourcesRead")
            val msg = when {
                n > 0 -> context.resources.getQuantityString(R.plurals.import_done, n, n)
                n == 0 -> context.getString(R.string.import_nothing)
                else -> context.getString(R.string.import_failed, "I/O error")
            }
            viewModel.emitEvent(msg)
        }
    }

    when (subPage) {
        SettingsSubPage.Permissions -> {
            BackHandler { onSubPageChange(SettingsSubPage.None) }
            PermissionsAccessPage { onSubPageChange(SettingsSubPage.None) }
        }
        SettingsSubPage.Schedules -> {
            BackHandler { onSubPageChange(SettingsSubPage.None) }
            SchedulesPage(
                schedules = schedules,
                onBack = { onSubPageChange(SettingsSubPage.None) },
                onAdd = {
                    editingSchedule = null
                    showScheduleDialog = true
                },
                onEdit = { sched ->
                    editingSchedule = sched
                    showScheduleDialog = true
                },
                onDelete = { viewModel.deleteSchedule(it) },
                onToggleEnabled = { viewModel.toggleScheduleEnabled(it) },
            )
        }
        SettingsSubPage.NetworkColors -> {
            BackHandler { onSubPageChange(SettingsSubPage.None) }
            NetworkColorsPage(
                colors = ui.networkColors,
                onPickColor = viewModel::setNetworkColor,
                onResetColor = viewModel::resetNetworkColor,
                onResetAll = viewModel::resetAllNetworkColors,
                onBack = { onSubPageChange(SettingsSubPage.None) },
            )
        }
        SettingsSubPage.MapFilters -> {
            BackHandler { onSubPageChange(SettingsSubPage.None) }
            val operators = remember(readings) {
                readings.asSequence()
                    .mapNotNull { it.operatorName }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                    .toList()
            }
            MapFiltersPage(
                selectedNetworks = ui.defaultNetworkFilter,
                onToggleNetwork = viewModel::toggleDefaultNetwork,
                operators = operators,
                selectedOperators = ui.defaultOperatorFilter,
                onToggleOperator = viewModel::toggleDefaultOperator,
                onBack = { onSubPageChange(SettingsSubPage.None) },
            )
        }
        SettingsSubPage.None -> {
            val scroll = rememberScrollState()
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // -- Recording section --
                SettingsCard(
                    headerIcon = Icons.Default.Schedule,
                    headerTitle = stringResource(R.string.settings_section_recording),
                ) {
                    SwitchRow(
                        title = stringResource(R.string.settings_recording_title),
                        subtitle = stringResource(R.string.settings_recording_subtitle),
                        checked = ui.isSampling,
                        onCheckedChange = { start ->
                            if (start) viewModel.startSampling() else viewModel.stopSampling()
                        },
                    )
                    CardDivider()
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
                        else pluralStringResource(R.plurals.schedule_count, schedules.size, schedules.size),
                        onClick = { onSubPageChange(SettingsSubPage.Schedules) },
                    )
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
                    SettingsRow(
                        title = stringResource(R.string.settings_time_format_title),
                        subtitle = stringResource(R.string.settings_time_format_subtitle),
                        value = timeFormatLabelFor(ui.timeFormat),
                        onClick = { showTimeFormatDialog = true },
                    )
                    CardDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_date_format_title),
                        subtitle = stringResource(R.string.settings_date_format_subtitle),
                        value = dateFormatLabelFor(ui.dateFormat),
                        onClick = { showDateFormatDialog = true },
                    )
                    CardDivider()
                    SwitchRow(
                        title = stringResource(R.string.settings_dynamic_color_title),
                        subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                        checked = ui.dynamicColorEnabled,
                        onCheckedChange = viewModel::setDynamicColorEnabled,
                    )
                    CardDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_network_colors_title),
                        subtitle = stringResource(R.string.settings_network_colors_subtitle),
                        onClick = { onSubPageChange(SettingsSubPage.NetworkColors) },
                    )
                }

                // -- Map section --
                SettingsCard(
                    headerIcon = Icons.Default.Map,
                    headerTitle = stringResource(R.string.settings_section_map),
                ) {
                    SettingsRow(
                        title = stringResource(R.string.settings_map_filters_title),
                        subtitle = stringResource(R.string.settings_map_filters_subtitle),
                        value = mapFilterSummary(ui.defaultNetworkFilter, ui.defaultOperatorFilter),
                        onClick = { onSubPageChange(SettingsSubPage.MapFilters) },
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
                        title = stringResource(R.string.import_csv),
                        subtitle = stringResource(R.string.import_csv_subtitle),
                        enabled = true,
                        onClick = { csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values")) },
                    )
                    CardDivider()
                    SettingsRow(
                        title = stringResource(R.string.export_csv),
                        subtitle = if (readings.isEmpty()) {
                            stringResource(R.string.export_nothing)
                        } else {
                            pluralStringResource(R.plurals.settings_export_count, readings.size, readings.size)
                        },
                        enabled = readings.isNotEmpty(),
                        onClick = { csvExportLauncher.launch("sigverage_${System.currentTimeMillis()}.csv") },
                    )
                    CardDivider()
                    SettingsRow(
                        title = stringResource(R.string.settings_delete_all_title),
                        subtitle = if (readings.isEmpty()) {
                            stringResource(R.string.settings_delete_all_empty)
                        } else {
                            pluralStringResource(R.plurals.settings_delete_all_count, readings.size, readings.size)
                        },
                        destructive = true,
                        enabled = readings.isNotEmpty(),
                        onClick = { confirmDeleteAll = true },
                    )
                }

                // -- Permissions & Access drill-out --
                SettingsCard(
                    headerIcon = Icons.Default.Security,
                    headerTitle = stringResource(R.string.settings_section_permissions),
                ) {
                    SettingsRow(
                        title = stringResource(R.string.settings_permissions_title),
                        subtitle = stringResource(R.string.settings_permissions_subtitle),
                        onClick = { onSubPageChange(SettingsSubPage.Permissions) },
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
        }
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
    if (showTimeFormatDialog) {
        TimeFormatDialog(
            current = ui.timeFormat,
            onDismiss = { showTimeFormatDialog = false },
            onPick = { format ->
                viewModel.setTimeFormat(format)
                showTimeFormatDialog = false
            },
        )
    }
    if (showDateFormatDialog) {
        DateFormatDialog(
            current = ui.dateFormat,
            onDismiss = { showDateFormatDialog = false },
            onPick = { format ->
                viewModel.setDateFormat(format)
                showDateFormatDialog = false
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
            onDismiss = {
                showScheduleDialog = false
                editingSchedule = null
            },
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
                TextButton(
                    onClick = {
                        viewModel.deleteAll()
                        confirmDeleteAll = false
                    },
                ) { Text(stringResource(R.string.confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) {
                    Text(stringResource(R.string.confirm_no))
                }
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Card-based section wrapper
// ---------------------------------------------------------------------------

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

@Composable
private fun CardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}

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
                },
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

@Composable
fun themeLabelFor(mode: ThemeMode): String = when (mode) {
    ThemeMode.System -> stringResource(R.string.theme_system)
    ThemeMode.Light -> stringResource(R.string.theme_light)
    ThemeMode.Dark -> stringResource(R.string.theme_dark)
}

@Composable
fun timeFormatLabelFor(format: TimeFormat): String = when (format) {
    TimeFormat.System -> stringResource(R.string.datetime_system)
    TimeFormat.TwelveHour -> stringResource(R.string.time_12h)
    TimeFormat.TwentyFourHour -> stringResource(R.string.time_24h)
}

@Composable
fun dateFormatLabelFor(format: DateFormat): String = when (format) {
    DateFormat.System -> stringResource(R.string.datetime_system)
    DateFormat.DayMonthYearSlash -> stringResource(R.string.date_pattern_day_month_year_slash)
    DateFormat.MonthDayYearSlash -> stringResource(R.string.date_pattern_month_day_year_slash)
    DateFormat.YearMonthDayDash -> stringResource(R.string.date_pattern_year_month_day_dash)
    DateFormat.DayMonthYearText -> stringResource(R.string.date_pattern_day_month_year_text)
}

private data class ReliabilityStatus(
    val batteryUnrestricted: Boolean,
    val exactAlarmRelevant: Boolean,
    val exactAlarmAllowed: Boolean,
)

private fun readReliabilityStatus(context: Context): ReliabilityStatus {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val batteryUnrestricted =
        pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true

    val exactAlarmRelevant = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val exactAlarmAllowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        am?.canScheduleExactAlarms() ?: false
    } else {
        true
    }
    return ReliabilityStatus(batteryUnrestricted, exactAlarmRelevant, exactAlarmAllowed)
}

private fun openBatteryOptimizationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val perApp = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(perApp) }.onFailure {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/**
 * One-line summary of the saved default map filters, shown on the Settings
 * root row - e.g. "5G, LTE · Vodafone" or "All networks · All operators".
 */
@Composable
private fun mapFilterSummary(
    networks: Set<NetworkType>,
    operators: Set<String>,
): String {
    val netPart = if (networks.size == NetworkType.entries.size) {
        stringResource(R.string.settings_map_filters_all_networks)
    } else {
        NetworkType.entries.filter { it in networks }.map { it.shortLabel }.distinct().joinToString()
    }
    val opPart = if (operators.isEmpty()) {
        stringResource(R.string.settings_map_filters_all_operators)
    } else {
        operators.sorted().joinToString()
    }
    return stringResource(R.string.settings_map_filters_summary, netPart, opPart)
}

/**
 * Settings drill-out for per-network colours. Lists every [NetworkType] with a
 * swatch of its current colour; tapping a row opens [NetworkColorDialog] to
 * recolour it. Edits are persisted immediately and flow through the app's
 * `LocalNetworkColors`, so the map, legend, chips and badges repaint at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkColorsPage(
    colors: Map<NetworkType, Color>,
    onPickColor: (NetworkType, Color) -> Unit,
    onResetColor: (NetworkType) -> Unit,
    onResetAll: () -> Unit,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf<NetworkType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_network_colors_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_permissions_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard(
                headerIcon = Icons.Default.Palette,
                headerTitle = stringResource(R.string.settings_network_colors_title),
            ) {
                Text(
                    text = stringResource(R.string.settings_network_colors_page_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                NetworkType.entries.forEachIndexed { index, type ->
                    if (index > 0) CardDivider()
                    NetworkColorRow(
                        type = type,
                        color = colors[type] ?: MaterialTheme.colorScheme.outline,
                        onClick = { editing = type },
                    )
                }
                CardDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onResetAll) {
                        Text(stringResource(R.string.settings_network_colors_reset_all))
                    }
                }
            }
        }
    }

    editing?.let { type ->
        NetworkColorDialog(
            type = type,
            initial = colors[type] ?: Color.Gray,
            onDismiss = { editing = null },
            onConfirm = { color ->
                onPickColor(type, color)
                editing = null
            },
            onReset = {
                onResetColor(type)
                editing = null
            },
        )
    }
}

/** One tappable row in [NetworkColorsPage]: a colour swatch plus the network label. */
@Composable
private fun NetworkColorRow(
    type: NetworkType,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = type.shortLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (color.luminance() > 0.5f) Color.Black else Color.White,
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = type.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Settings drill-out for the persisted default map filters: which networks and
 * operators the coverage map loads with. Editing these writes straight to
 * [com.sigverage.app.data.PreferencesStore]; the live map picks them up the next
 * time the Map tab is opened (temporary on-map chip overrides are discarded then).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MapFiltersPage(
    selectedNetworks: Set<NetworkType>,
    onToggleNetwork: (NetworkType) -> Unit,
    operators: List<String>,
    selectedOperators: Set<String>,
    onToggleOperator: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_map_filters_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_permissions_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard(
                headerIcon = Icons.Default.Map,
                headerTitle = stringResource(R.string.settings_map_filters_title),
            ) {
                Text(
                    text = stringResource(R.string.settings_map_filters_page_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )

                MapFilterSubLabel(stringResource(R.string.settings_map_filters_networks_header))
                val palette = rememberNetworkColors()
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NetworkType.entries.forEach { type ->
                        val selected = type in selectedNetworks
                        FilterChip(
                            selected = selected,
                            onClick = { onToggleNetwork(type) },
                            label = { Text(type.label) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(
                                            palette[type] ?: MaterialTheme.colorScheme.outline,
                                            CircleShape,
                                        ),
                                )
                            },
                        )
                    }
                }

                CardDivider()
                MapFilterSubLabel(stringResource(R.string.settings_map_filters_operators_header))
                if (operators.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_map_filters_operators_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.settings_map_filters_operators_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        operators.forEach { operator ->
                            val selected = operator in selectedOperators
                            FilterChip(
                                selected = selected,
                                onClick = { onToggleOperator(operator) },
                                label = { Text(operator) },
                                leadingIcon = if (selected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun MapFilterSubLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
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
    else -> pluralStringResource(R.plurals.time_days, days, days)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionsAccessPage(onBack: () -> Unit) {
    val context = LocalContext.current
    @Suppress("DEPRECATION")
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
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard(
                headerIcon = Icons.Default.Security,
                headerTitle = stringResource(R.string.settings_permissions_app_subheader),
            ) {
                PermissionsSection(modifier = Modifier.fillMaxWidth())
            }

            SettingsCard(
                headerIcon = Icons.Default.Bolt,
                headerTitle = stringResource(R.string.settings_permissions_bg_subheader),
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
        }
    }
}
