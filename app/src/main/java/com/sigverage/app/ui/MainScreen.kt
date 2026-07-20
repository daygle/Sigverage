package com.sigverage.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sigverage.app.R
import com.sigverage.app.model.SignalReading
import com.sigverage.app.ui.theme.appTopBarColors
import com.sigverage.app.service.SamplingService

/**
 * A small floating pill that shows a green dot and "Recording" text when
 * coverage recording is active. Visible from every tab so the user always
 * knows the current recording state without navigating to Settings.
 *
 * [modifier] should position this within a [Box] (e.g. via `Modifier.align`).
 */
@Composable
private fun RecordingIndicator(
    isRecording: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!isRecording) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color(0xFF22C55E), CircleShape)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = stringResource(R.string.recording_status),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private enum class Tab { Map, List, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val ui by viewModel.ui.collectAsState()
    val readings by viewModel.readings.collectAsState()

    val msgDeleted = stringResource(R.string.reading_deleted)
    val actionUndo = stringResource(R.string.undo)
    val msgNoPermissions = stringResource(R.string.auto_record_no_permissions)
    val msgStarted = stringResource(R.string.auto_record_started)

    var tab by rememberSaveable { mutableStateOf(Tab.Map) }
    var sheetReading by remember { mutableStateOf<SignalReading?>(null) }
    // Which Settings drill-out (if any) is open. When set, the Settings screen
    // renders that sub-page full-screen, so we hide the app bar and bottom
    // navigation and let the sub-page's own Scaffold own the system insets.
    var settingsSubPage by remember { mutableStateOf(SettingsSubPage.None) }
    val settingsSubPageActive = tab == Tab.Settings && (settingsSubPage != SettingsSubPage.None)

    val jumpToReading: (SignalReading) -> Unit = remember(viewModel) {
        { reading: SignalReading ->
            sheetReading = null
            tab = Tab.Map
            viewModel.focusOnLocation(reading.latitude, reading.longitude)
        }
    }

    // Opening the Map tab reapplies the user's saved default filters, so any
    // temporary chip overrides from the last visit are discarded. Keyed on
    // `tab` so it re-runs each time the user returns to the map.
    LaunchedEffect(tab) {
        if (tab == Tab.Map) viewModel.applyDefaultMapFilters()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbar.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.undoDeleteEvents.collect { reading ->
            val result = snackbar.showSnackbar(
                message = msgDeleted,
                actionLabel = actionUndo,
                withDismissAction = true,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreReading(reading)
            }
        }
    }

    LaunchedEffect(ui.autoRecordEnabled, ui.onboardingCompleted) {
        if (!ui.autoRecordEnabled || !ui.onboardingCompleted) return@LaunchedEffect
        val missing = viewModel.missingSamplingPermissions(ctx)
        if (missing.isNotEmpty()) {
            snackbar.showSnackbar(msgNoPermissions)
            return@LaunchedEffect
        }
        if (ui.isSampling) return@LaunchedEffect
        viewModel.setSampling(active = true)
        SamplingService.start(ctx)
        snackbar.showSnackbar(msgStarted)
    }

    @Suppress("DEPRECATION")
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val current = viewModel.ui.value
            if (!current.autoRecordEnabled || !current.onboardingCompleted) return@LifecycleEventObserver
            if (viewModel.missingSamplingPermissions(ctx).isNotEmpty()) return@LifecycleEventObserver
            if (current.isSampling) return@LifecycleEventObserver
            viewModel.setSampling(true)
            SamplingService.start(ctx)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            // The Map tab is immersive: the map runs edge-to-edge and its
            // controls (filters, zoom) float on the map itself, so no app bar
            // is drawn there. A Settings drill-out owns the whole screen via
            // its own Scaffold, so no app bar there either. Otherwise a plain
            // title bar shows - recording is started/stopped from the Settings
            // page, so the bar carries no pause action.
            if (tab != Tab.Map && !settingsSubPageActive) {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    colors = appTopBarColors(),
                )
            }
        },
        bottomBar = {
            if (!settingsSubPageActive) {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.Map,
                        onClick = { tab = Tab.Map },
                        icon = { Icon(Icons.Default.Map, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_map)) },
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
            }
        },
        // While a Settings sub-page owns the screen, hand all system-bar insets
        // to its own Scaffold so nothing double-pads around its top app bar.
        contentWindowInsets = if (settingsSubPageActive) {
            WindowInsets(0, 0, 0, 0)
        } else {
            ScaffoldDefaults.contentWindowInsets
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
                    operatorFilter = ui.operatorFilter,
                    onToggleOperatorFilter = viewModel::toggleOperatorFilter,
                    onCapture = viewModel::captureNow,
                    focusEvents = viewModel.focusEvents,
                )
                Tab.List -> ListPanel(
                    readings = readings,
                    timeFormat = ui.timeFormat,
                    dateFormat = ui.dateFormat,
                    onClick = { sheetReading = it },
                    onDelete = viewModel::deleteReading,
                    onFocusMap = jumpToReading,
                )
                Tab.Settings -> SettingsScreen(
                    viewModel = viewModel,
                    subPage = settingsSubPage,
                    onSubPageChange = { settingsSubPage = it },
                )
            }

            // Recording indicator — shown only on the Map tab, positioned in the
            // bottom-start corner where it won't overlap the floating filter bar
            // (top-centre) or zoom controls (bottom-end).
            if (tab == Tab.Map) {
                RecordingIndicator(
                    isRecording = ui.isSampling,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp),
                )
            }
        }
    }

    sheetReading?.let { reading ->
        DetailsSheet(
            reading = reading,
            timeFormat = ui.timeFormat,
            dateFormat = ui.dateFormat,
            onDismiss = { sheetReading = null },
            onDelete = {
                viewModel.deleteReading(reading)
                sheetReading = null
            },
            onShowOnMap = { jumpToReading(reading) }
        )
    }
}
