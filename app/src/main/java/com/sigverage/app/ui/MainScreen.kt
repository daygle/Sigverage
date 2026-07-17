package com.sigverage.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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

private enum class Tab { Map, List, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val ui by viewModel.ui.collectAsState()
    val readings by viewModel.readings.collectAsState()

    var tab by rememberSaveable { mutableStateOf(Tab.Map) }
    var sheetReading by remember { mutableStateOf<SignalReading?>(null) }

    val jumpToReading: (SignalReading) -> Unit = remember(viewModel) {
        { reading: SignalReading ->
            sheetReading = null
            tab = Tab.Map
            viewModel.focusOnLocation(reading.latitude, reading.longitude)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { message ->
            snackbar.showSnackbar(message)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.undoDeleteEvents.collect { reading ->
            val result = snackbar.showSnackbar(
                message = ctx.getString(R.string.reading_deleted),
                actionLabel = ctx.getString(R.string.undo),
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
        val missing = missingPermissions(ctx)
        if (missing.isNotEmpty()) {
            snackbar.showSnackbar(ctx.getString(R.string.auto_record_no_permissions))
            return@LaunchedEffect
        }
        if (ui.isSampling) return@LaunchedEffect
        viewModel.setSampling(true)
        SamplingService.start(ctx)
        snackbar.showSnackbar(ctx.getString(R.string.auto_record_started))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val current = viewModel.ui.value
            if (!current.autoRecordEnabled || !current.onboardingCompleted) return@LifecycleEventObserver
            if (missingPermissions(ctx).isNotEmpty()) return@LifecycleEventObserver
            if (current.isSampling) return@LifecycleEventObserver
            viewModel.setSampling(true)
            SamplingService.start(ctx)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { viewModel.captureNow() }) {
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
                    operatorFilter = ui.operatorFilter,
                    onToggleOperatorFilter = viewModel::toggleOperatorFilter,
                    focusEvents = viewModel.focusEvents,
                )
                Tab.List -> ListPanel(
                    readings = readings,
                    onClick = { sheetReading = it },
                    onDelete = viewModel::deleteReading,
                    onFocusMap = jumpToReading,
                )
                Tab.Settings -> SettingsScreen(viewModel = viewModel)
            }
        }
    }

    sheetReading?.let { reading ->
        DetailsSheet(
            reading = reading,
            onDismiss = { sheetReading = null },
            onDelete = {
                viewModel.deleteReading(reading)
                sheetReading = null
            },
            onShowOnMap = { jumpToReading(reading) },
        )
    }
}

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
