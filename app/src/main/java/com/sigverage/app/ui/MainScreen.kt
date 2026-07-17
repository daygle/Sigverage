package com.sigverage.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sigverage.app.R
import com.sigverage.app.model.SignalReading
import com.sigverage.app.service.SamplingService
import kotlinx.coroutines.launch

private enum class Tab { Map, List, Settings }

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
    // Dialogs that USED to live here (sample-interval, granularity, retention,
    // delete-all confirm) have been moved into SettingsScreen because the
    // user prefers them reachable from the Settings tab.

    // Shared "jump to a specific reading on the map" handler - invoked from
    // both the ListPanel row icon and the DetailsSheet action button.
    // Closes any open details sheet, switches to the Map tab, and pushes the
    // coordinate onto the focusEvents channel that MapPanel is collecting.
    // Kept as a single closure so the next tweak (snackbar, animation,
    // map-zoom policy) lands everywhere at once. Wrapped in `remember` keyed
    // on the (stable) ViewModel so the lambda ref stays stable across
    // recompositions and ListPanel / DetailsSheet memoization can skip work.
    val jumpToReading: (SignalReading) -> Unit = remember(viewModel) {
        { reading: SignalReading ->
            sheetReading = null
            tab = Tab.Map
            viewModel.focusOnLocation(reading.latitude, reading.longitude)
        }
    }

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

    // Auto-record-on-launch. Re-keys on (autoRecordEnabled,
    // onboardingCompleted) so it fires exactly when those bits flip -
    // either on the very first composition after onboarding finishes, or
    // immediately after the user toggles the Settings switch. `start(...)`
    // is itself idempotent (early return in `onStartCommand` when the
    // stream job is already active) so re-firing the effect is safe.
    //
    // Permission gate: typed foreground services for location on Android
    // 14+ raise `SecurityException` if FINE/COARSE/POST_NOTIFICATIONS
    // aren't held, so we route the missing-permissions case to a Snackbar
    // instead of attempting to start the service.
    //
    // We also avoid showing the "Sampling started." snackbar when the
    // service is already running (e.g. the user toggled auto-record ON
    // mid-session after a manual Pause+Play) - in that case nothing new
    // was started and the message would be misleading.
    LaunchedEffect(ui.autoRecordEnabled, ui.onboardingCompleted) {
        if (!ui.autoRecordEnabled || !ui.onboardingCompleted) return@LaunchedEffect
        val missing = missingPermissions(ctx)
        if (missing.isNotEmpty()) {
            snackbar.showSnackbar(ctx.getString(R.string.auto_record_no_permissions))
            return@LaunchedEffect
        }
        if (ui.isSampling) return@LaunchedEffect // already running; nothing to do
        viewModel.setSampling(true)
        SamplingService.start(ctx, ui.samplingIntervalMs)
        snackbar.showSnackbar(ctx.getString(R.string.auto_record_started))
    }

    // Auto-record re-arm hook. The LaunchedEffect above fires only when
    // (autoRecordEnabled, onboardingCompleted) change - but the user might
    // toggle auto-record ON while permissions are still missing, see the
    // "permissions missing" snackbar, then grant them from
    // Settings → Permissions (which lives in the same Activity and
    // doesn't change either key). Without this hook they'd have to toggle
    // auto-record off+on or restart the app to actually start sampling.
    //
    // ON_RESUME also covers the case of returning from system Settings
    // (after granting *Background location* via the "Allow all the time"
    // deep-link Android forces). Silent catch-up: no snackbar here, since
    // the user didn't initiate the restart - surfacing a message every
    // time they come back from the system Settings would be alarming.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val current = viewModel.ui.value
            if (!current.autoRecordEnabled || !current.onboardingCompleted) return@LifecycleEventObserver
            if (missingPermissions(ctx).isNotEmpty()) return@LifecycleEventObserver
            if (current.isSampling) return@LifecycleEventObserver
            viewModel.setSampling(true)
            SamplingService.start(ctx, current.samplingIntervalMs)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = {
                        viewModel.captureNow()
                        scope.launch {
                            val n = viewModel.count.value + 1
                            snackbar.showSnackbar(ctx.getString(R.string.capture_snackbar, n))
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.AddLocation,
                            contentDescription = stringResource(R.string.capture_at_location)
                        )
                    }
                    if (ui.isSampling) {
                        IconButton(onClick = {
                            viewModel.setSampling(false)
                            SamplingService.stop(ctx)
                        }) {
                            Icon(
                                imageVector = Icons.Default.PauseCircle,
                                contentDescription = stringResource(R.string.stop_sampling),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
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
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_list)) }
                )
                NavigationBarItem(
                    selected = tab == Tab.Settings,
                    onClick = { tab = Tab.Settings },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_settings)) }
                )
            }
        },
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
                    focusEvents = viewModel.focusEvents,
                )
                Tab.List -> ListPanel(
                    readings = readings,
                    onClick = { sheetReading = it },
                    onDelete = viewModel::delete,
                    onFocusMap = jumpToReading,
                )
                Tab.Settings -> SettingsScreen(viewModel = viewModel)
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
            },
            onShowOnMap = { jumpToReading(reading) },
        )
    }
}

@Composable
private fun StatusBanner(
    sampling: Boolean,
    count: Int,
    lastFix: com.sigverage.app.location.FixSample?,
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
fun IntervalDialog(
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
