package com.sigverage.app.coverage

import androidx.compose.foundation.background

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

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sigverage.app.R
import com.sigverage.app.model.NetworkType
import com.sigverage.app.model.SignalReading
import com.sigverage.app.ui.theme.NetworkColors
import java.util.Locale
import org.osmdroid.views.MapView

/**
 * Per-network row inside a selected tile's details sheet: how many readings
 * that network had in the cell, its mean signal, and the best/worst readings
 * seen. [meanDbm], [bestDbm] and [worstDbm] are null when no reading in the
 * cell carried a usable dBm value.
 */
data class TileNetworkStat(
    val type: NetworkType,
    val count: Int,
    val meanDbm: Int?,
    /** Strongest (least-negative) reading seen; null when none carried a dBm. */
    val bestDbm: Int?,
    /** Weakest (most-negative) reading seen; null when none carried a dBm. */
    val worstDbm: Int?,
)

/**
 * Everything the tile-details sheet shows for a tapped coverage square:
 * its centre coordinate, the currently-dominant network (matching the drawn
 * fill), the full per-network breakdown, the operators seen there, and the
 * total reading count.
 */
data class TileDetails(
    val centerLat: Double,
    val centerLng: Double,
    val dominant: NetworkType?,
    val networks: List<TileNetworkStat>,
    val operators: List<String>,
    val totalReadings: Int,
)

/**
 * Full-bleed coverage map with all controls floating **on** the map
 * surface - the modern, immersive map idiom that keeps the map edge-to-edge
 * and puts every affordance one tap away without a separate chrome bar.
 *
 * Layout (all overlaid on the [MapView] in a single [Box]):
 *
 *  - **Top:** a "Filters" pill that opens the full [FilterSheet] (networks
 *    + operators). A single compact button rather than a bar of quick-toggles,
 *    so all filter state is managed in one place.
 *  - **Bottom-end:** recenter / zoom-in / zoom-out
 *    [SmallFloatingActionButton]s. Recording (start/stop and one-off
 *    captures) is driven entirely from the Settings page, so the map
 *    carries no recording controls.
 *
 * The **full** filter set (networks + operators) lives in a [ModalBottomSheet]
 * opened by the "Filters" pill, keeping the map surface clean and all filter
 * state in one place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverageMapScreen(
    mapView: MapView,
    readings: List<SignalReading>,
    coverageFilter: Set<NetworkType>,
    onToggleFilter: (NetworkType) -> Unit,
    operatorFilter: Set<String>,
    onToggleOperatorFilter: (String) -> Unit,
    modifier: Modifier = Modifier,
    onZoomIn: () -> Unit = {},
    onZoomOut: () -> Unit = {},
    onRecenter: () -> Unit = {},
    tileDetails: TileDetails? = null,
    onDismissTileDetails: () -> Unit = {},
) {
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    val allOperators = remember(readings) {
        readings.mapNotNull { it.operatorName }.filter { it.isNotBlank() }.distinct().sorted()
    }
    // Only surface network toggles for technologies that actually appear in
    // the current readings, so the filter bar and sheet don't advertise
    // networks with nothing to show. Enum order is preserved for a stable,
    // predictable chip layout.
    val presentNetworks = remember(readings) {
        val present = readings.mapTo(HashSet()) { it.networkType }
        NetworkType.entries.filter { it in present }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )

        FloatingFilterBar(
            onOpenFilters = { showFilterSheet = true },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        )

        MapControls(
            onZoomIn = onZoomIn,
            onZoomOut = onZoomOut,
            onRecenter = onRecenter,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )

        if (readings.isEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 4.dp,
            ) {
                Text(
                    text = stringResource(R.string.coverage_empty_hint),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    if (showFilterSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            FilterSheet(
                networks = presentNetworks,
                selected = coverageFilter,
                onToggle = onToggleFilter,
                selectedOperators = operatorFilter,
                onToggleOperator = onToggleOperatorFilter,
                allOperators = allOperators,
            )
        }
    }

    if (tileDetails != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissTileDetails,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            TileDetailsSheet(details = tileDetails)
        }
    }
}

/**
 * Bottom-sheet contents for a tapped coverage square: its centre coordinate,
 * the dominant network (matching the painted fill), a per-network breakdown
 * of reading counts and mean signal, and the operators observed in the cell.
 */
@Composable
private fun TileDetailsSheet(details: TileDetails) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.tile_details_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(
                R.string.tile_details_center,
                String.format(Locale.US, "%.5f", details.centerLat),
                String.format(Locale.US, "%.5f", details.centerLng),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = pluralStringResource(
                R.plurals.tile_details_readings,
                details.totalReadings,
                details.totalReadings,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (details.dominant != null) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NetworkDot(details.dominant)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(
                        R.string.tile_details_dominant,
                        details.dominant.label,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.tile_details_networks_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        details.networks.forEach { stat ->
            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NetworkDot(stat.type)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = stat.type.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (stat.meanDbm != null) {
                            stringResource(R.string.tile_details_dbm, stat.meanDbm)
                        } else {
                            stringResource(R.string.tile_details_no_signal)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = pluralStringResource(
                            R.plurals.tile_details_readings,
                            stat.count,
                            stat.count,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Best/worst range, aligned under the label (past the 12dp dot
                // + 8dp gap). Only shown when there is an actual spread - a
                // single reading has best == worst == mean, already displayed.
                if (stat.bestDbm != null && stat.worstDbm != null &&
                    stat.bestDbm != stat.worstDbm
                ) {
                    Text(
                        text = stringResource(
                            R.string.tile_details_best_worst,
                            stat.bestDbm,
                            stat.worstDbm,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 2.dp),
                    )
                }
            }
        }

        if (details.operators.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.tile_details_operators_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = details.operators.joinToString {
                    it.replaceFirstChar { c ->
                        if (c.isLowerCase()) c.titlecase(locale) else c.toString()
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** A small colour-coded dot for [type], matching the coverage-grid palette. */
@Composable
private fun NetworkDot(type: NetworkType) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(
                color = NetworkColors[type] ?: Color.Gray,
                shape = CircleShape,
            )
    )
}

/**
 * A single "Filters" pill floating at the top of the map. Opens the full
 * [FilterSheet] (networks + operators) in a [ModalBottomSheet]. A compact
 * entry point that keeps the map surface clean.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FloatingFilterBar(
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chipColors = AssistChipDefaults.assistChipColors(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    )
    AssistChip(
        onClick = onOpenFilters,
        label = { Text(stringResource(R.string.map_filters)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
        shape = RoundedCornerShape(20.dp),
        colors = chipColors,
        modifier = modifier,
    )
}

/**
 * A vertical stack of the standard interactive map controls - recenter,
 * zoom-in, zoom-out - rendered as Material 3 [SmallFloatingActionButton]s
 * overlaid on the map's bottom-right corner (the platform-conventional
 * spot). These replace osmdroid's dated built-in zoom buttons with
 * touch-target-sized, theme-aware FABs that match the rest of the app.
 * Recording is driven from the Settings page, so no capture control lives
 * here.
 */
@Composable
private fun MapControls(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SmallFloatingActionButton(
            onClick = onRecenter,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = stringResource(R.string.map_recenter),
            )
        }
        SmallFloatingActionButton(onClick = onZoomIn) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.map_zoom_in),
            )
        }
        SmallFloatingActionButton(onClick = onZoomOut) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = stringResource(R.string.map_zoom_out),
            )
        }
    }
}

/**
 * The full filter surface shown in the modal sheet opened by the "Filters"
 * pill: every network toggle plus the (dynamic) operator toggles.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    networks: List<NetworkType>,
    selected: Set<NetworkType>,
    onToggle: (NetworkType) -> Unit,
    selectedOperators: Set<String>,
    onToggleOperator: (String) -> Unit,
    allOperators: List<String>,
) {
    val locale = LocalConfiguration.current.locales[0]
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.map_filters),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = filterSummaryText(selected),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (networks.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.filter_network_label),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CoverageFilterChips(
                types = networks,
                selected = selected,
                onToggle = onToggle,
            )
        }

        if (allOperators.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.filter_operator_label),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                allOperators.forEach { operator ->
                    // Hidden operators are shown as deselected (untinted).
                    // Visible operators are shown as selected (tinted).
                    val isHidden = operator in selectedOperators
                    FilterChip(
                        selected = !isHidden,
                        onClick = { onToggleOperator(operator) },
                        label = {
                            Text(operator.replaceFirstChar {
                                if (it.isLowerCase()) it.titlecase(locale) else it.toString()
                            })
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * One-line summary of the active network filters, shown under the sheet
 * title. Both 5G variants (`FiveG`, `NR_NSA`) collapse to a single "5G"
 * label, so the list is built from **distinct** short labels while the
 * parenthetical count reflects the actual number of enabled
 * [NetworkType]s (which matches the chip strip below).
 */
@Composable
private fun filterSummaryText(selected: Set<NetworkType>): String {
    val total = NetworkType.entries.size
    val distinctLabels = selected.map { it.shortLabel }.distinct()
    return when (selected.size) {
        0 -> stringResource(R.string.filter_sheet_summary_none, total)
        total -> stringResource(R.string.filter_sheet_summary_all, total, total)
        else -> pluralStringResource(
            R.plurals.filter_sheet_summary_partial,
            selected.size,
            distinctLabels.joinToString(),
            selected.size,
            total,
        )
    }
}
