package com.signalspotter.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.signalspotter.app.R
import com.signalspotter.app.model.SignalReading
import com.signalspotter.app.service.SamplingService
import kotlinx.coroutines.launch

private enum class Tab { Map, List }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val ui by viewModel.ui.collectAsState()
    val readings by viewModel.readings.collectAsState()
    val count by viewModel.count.collectAsState()

    var tab by rememberSaveable { mutableStateOf(Tab.Map) }
    var sheetReading by remember { mutableStateOf<SignalReading?>(null) }
    var showDeleteAll by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }
    var showGranularityDialog by remember { mutableStateOf(false) }
    var showRetentionDialog by remember { mutableStateOf(false) }

    // Pump one-shot UI events (purge counts after a retention change,
    // future export-failed notifications, etc.) into the snackbar host.
    // `LaunchedEffect` keeps the collector in the Composable scope and
    // cancels it automatically when the composable leaves composition,
    // avoiding leaks across configuration changes.
    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbar.showSnackbar(message)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { /* result is reflected by the next permission check on action */ }

    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val n = viewModel.exportCsv(uri)
            val msg = when {
                n > 0 -> ctx.getString(R.string.export_done, n)
                n == 0 -> ctx.getString(R.string.export_nothing)
                else -> ctx.getString(R.string.export_failed, "I/O error")
            }
            snackbar.showSnackbar(msg)
        }
    }

    fun toggleSampling() {
        if (ui.isSampling) {
            viewModel.setSampling(false)
            SamplingService.stop(ctx)
            return
        }
        val missing = missingPermissions(ctx)
        if (missing.isEmpty()) {
            viewModel.setSampling(true)
            SamplingService.start(ctx, ui.samplingIntervalMs)
        } else {
            permissionLauncher.launch(missing.toTypedArray())
            // Optimistically mark sampling on; the service will silently wait for
            // the grant event. If the user denies, the FAB→Stop toggle is the
            // obvious escape.
            viewModel.setSampling(true)
            SamplingService.start(ctx, ui.samplingIntervalMs)
        }
    }                Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { toggleSampling() }) {
                        Icon(
                            imageVector = if (ui.isSampling) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = stringResource(
                                if (ui.isSampling) R.string.stop_sampling else R.string.start_sampling
                            ),
                            tint = if (ui.isSampling) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { showIntervalDialog = true },
                        enabled = !ui.isSampling
                    ) {
                        Icon(
                            imageVector = Icons.Default.IosShare,
                            contentDescription = stringResource(R.string.interval_title)
                        )
                    }
                    IconButton(onClick = { showGranularityDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = stringResource(R.string.granularity_slider_title)
                        )
                    }
                    IconButton(onClick = { showRetentionDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = stringResource(R.string.retention_dialog_title)
                        )
                    }
                    IconButton(
                        onClick = { createCsvLauncher.launch("signal_spotter_${'$'}{System.currentTimeMillis()}.csv") },
                        enabled = readings.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.IosShare,
                            contentDescription = stringResource(R.string.export_csv)
                        )
                    }
                    IconButton(
                        onClick = { showDeleteAll = readings.isNotEmpty() },
                        enabled = readings.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = stringResource(R.string.delete_all)
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.Map,
                    onClick = { tab = Tab.Map },
                    icon = { Icon(Icons.Default.Map, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_map)) }
                )
                NavigationBarItem(
                    selected = tab == Tab.List,
                    onClick = { tab = Tab.List },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_list)) }
                )
            }
        },
        floatingActionButton = {
            if (tab == Tab.Map) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.captureNow()
                        scope.launch {
                            val n = viewModel.count.value + 1
                            snackbar.showSnackbar("Recording… (#${'$'}n)")
                        }
                    },
                    icon = { Icon(Icons.Default.AddLocation, contentDescription = null) },
                    text = { Text(stringResource(R.string.record_at_location)) }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (tab) {
                Tab.Map -> MapPanel(
                    readings = readings,
                    lastFix = ui.lastFix,
                    coverageFilter = ui.coverageFilter,
                    onToggleFilter = viewModel::toggleCoverageFilter,
                    coverageZoom = ui.coverageZoom,
                    onCoverageZoomChange = viewModel::setCoverageZoom,
                )
                Tab.List -> ListPanel(
                    readings = readings,
                    onClick = { sheetReading = it },
                    onDelete = viewModel::delete
                )
            }
            StatusBanner(
                sampling = ui.isSampling,
                count = count,
                lastFix = ui.lastFix,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }

    sheetReading?.let { reading ->
        DetailsSheet(
            reading = reading,
            onDismiss = { sheetReading = null },
            onDelete = {
                viewModel.delete(reading.id)
                sheetReading = null
            }
        )
    }

    if (showDeleteAll) {
        AlertDialog(
            onDismissRequest = { showDeleteAll = false },
            title = { Text(stringResource(R.string.confirm_delete_all_title)) },
            text = { Text(stringResource(R.string.confirm_delete_all_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAll()
                    showDeleteAll = false
                }) { Text(stringResource(R.string.confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAll = false }) {
                    Text(stringResource(R.string.confirm_no))
                }
            }
        )
    }

    if (showIntervalDialog) {
        IntervalDialog(
            current = ui.samplingIntervalMs,
            onDismiss = { showIntervalDialog = false },
            onPicked = { ms ->
                viewModel.setInterval(ms)
                showIntervalDialog = false
            }
        )
    }

    if (showGranularityDialog) {
        GranularityDialog(
            currentZoom = ui.coverageZoom,
            onDismiss = { showGranularityDialog = false },
            onChange = viewModel::setCoverageZoom,
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
}

@Composable
private fun StatusBanner(
    sampling: Boolean,
    count: Int,
    lastFix: com.signalspotter.app.location.FixSample?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (sampling) Icons.Default.PlayCircle else Icons.Default.PauseCircle,
                tint = if (sampling) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                contentDescription = null
            )
            val text = when {
                sampling -> stringResource(R.string.status_recording, count)
                lastFix == null -> stringResource(R.string.status_no_fix)
                else -> stringResource(R.string.status_idle)
            }
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun IntervalDialog(
    current: Long,
    onDismiss: () -> Unit,
    onPicked: (Long) -> Unit
) {
    val options = listOf(
        3_000L to R.string.interval_option_fast,
        5_000L to R.string.interval_option_normal,
        15_000L to R.string.interval_option_slow,
        60_000L to R.string.interval_option_glacial
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.interval_title)) },
        text = {
            Column {
                options.forEach { (ms, label) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = ms == current, onClick = { onPicked(ms) })
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(label))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.confirm_no)) }
        }
    )
}

/** Returns permissions that are required to background-sample, but currently missing. */
private fun missingPermissions(ctx: android.content.Context): List<String> {
    val missing = mutableListOf<String>()
    val fine = Manifest.permission.ACCESS_FINE_LOCATION
    if (ContextCompat.checkSelfPermission(ctx, fine) != PackageManager.PERMISSION_GRANTED) {
        missing += fine
    }
    val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
    if (ContextCompat.checkSelfPermission(ctx, coarse) != PackageManager.PERMISSION_GRANTED) {
        missing += coarse
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notif = Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(ctx, notif) != PackageManager.PERMISSION_GRANTED) {
            missing += notif
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val bg = Manifest.permission.ACCESS_BACKGROUND_LOCATION
        if (ContextCompat.checkSelfPermission(ctx, bg) != PackageManager.PERMISSION_GRANTED) {
            missing += bg
        }
    }
    return missing
}
