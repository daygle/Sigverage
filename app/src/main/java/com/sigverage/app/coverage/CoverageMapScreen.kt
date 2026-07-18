package com.sigverage.app.coverage

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sigverage.app.R
import com.sigverage.app.model.NetworkType
import com.sigverage.app.model.SignalReading
import com.sigverage.app.ui.theme.NetworkColors
import org.osmdroid.views.MapView

/**
 * Full-bleed coverage map with all controls floating **on** the map
 * surface - the modern, immersive map idiom that keeps the map edge-to-edge
 * and puts every affordance one tap away without a separate chrome bar.
 *
 * Layout (all overlaid on the [MapView] in a single [Box]):
 *
 *  - **Top:** a floating, horizontally-scrollable filter bar. A leading
 *    "Filters" pill opens the full [FilterSheet] (networks + operators);
 *    the network quick-toggles follow it as colour-dotted [FilterChip]s.
 *    Because these chips sit directly on the map (no scrim), toggling one
 *    gives an instant, fully-visible live preview of the coverage grid -
 *    the property the old non-modal bottom sheet was built to preserve.
 *  - **Bottom-centre:** the primary "Capture here" [ExtendedFloatingActionButton],
 *    and - only while sampling - a pause [SmallFloatingActionButton] beside
 *    it. These used to live as easy-to-miss icons in the app's top bar.
 *  - **Bottom-end:** recenter / zoom-in / zoom-out [SmallFloatingActionButton]s.
 *
 * The **full** filter set (operators, plus the networks again for
 * completeness) lives in a [ModalBottomSheet] opened by the "Filters" pill;
 * that's the "advanced" surface, so a scrim there is acceptable - the
 * quick network toggles on the always-visible bar are what most people
 * reach for, and those preview live.
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
    isSampling: Boolean,
    onCapture: () -> Unit,
    onStopSampling: () -> Unit,
    modifier: Modifier = Modifier,
    onZoomIn: () -> Unit = {},
    onZoomOut: () -> Unit = {},
    onRecenter: () -> Unit = {},
) {
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    val allOperators = remember(readings) {
        readings.mapNotNull { it.operatorName }.filter { it.isNotBlank() }.distinct().sorted()
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )

        FloatingFilterBar(
            selected = coverageFilter,
            onToggle = onToggleFilter,
            activeOperatorCount = operatorFilter.size,
            onOpenFilters = { showFilterSheet = true },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
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

        // Recording actions grouped bottom-centre: the prominent "Capture
        // here" FAB, plus a pause button while sampling so recording can be
        // stopped without leaving the map.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSampling) {
                SmallFloatingActionButton(
                    onClick = onStopSampling,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Icon(
                        imageVector = Icons.Default.PauseCircle,
                        contentDescription = stringResource(R.string.stop_sampling),
                    )
                }
            }
            ExtendedFloatingActionButton(
                onClick = onCapture,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = {
                    Icon(
                        imageVector = Icons.Default.AddLocation,
                        contentDescription = null,
                    )
                },
                text = { Text(stringResource(R.string.map_capture_here)) },
            )
        }

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
                selected = coverageFilter,
                onToggle = onToggleFilter,
                selectedOperators = operatorFilter,
                onToggleOperator = onToggleOperatorFilter,
                allOperators = allOperators,
            )
        }
    }
}

/**
 * The always-visible, horizontally-scrollable filter bar floating at the
 * top of the map. Wrapped in a translucent rounded [Surface] so the chips
 * stay legible over arbitrary map tiles. Leads with a "Filters" pill
 * (opens the full [FilterSheet]) followed by one colour-dotted quick
 * toggle per [NetworkType].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FloatingFilterBar(
    selected: Set<NetworkType>,
    onToggle: (NetworkType) -> Unit,
    activeOperatorCount: Int,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = onOpenFilters,
                label = {
                    Text(
                        if (activeOperatorCount > 0) {
                            stringResource(R.string.map_filters_count, activeOperatorCount)
                        } else {
                            stringResource(R.string.map_filters)
                        }
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
            )
            NetworkType.entries.forEach { type ->
                NetworkQuickChip(
                    type = type,
                    isSelected = type in selected,
                    onClick = { onToggle(type) },
                )
            }
        }
    }
}

/**
 * A single colour-dotted network toggle used in the floating filter bar.
 * Mirrors the chip styling used inside [FilterSheet] so the two surfaces
 * feel like one control.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NetworkQuickChip(
    type: NetworkType,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val swatch = NetworkColors[type] ?: Color.Gray
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(type.shortLabel) },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = if (isSelected) swatch else swatch.copy(alpha = 0.3f),
                        shape = CircleShape,
                    )
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = swatch.copy(alpha = 0.18f),
        ),
    )
}

/**
 * A vertical stack of the standard interactive map controls - recenter,
 * zoom-in, zoom-out - rendered as Material 3 [SmallFloatingActionButton]s
 * overlaid on the map's bottom-right corner (the platform-conventional
 * spot). These replace osmdroid's dated built-in zoom buttons with
 * touch-target-sized, theme-aware FABs that match the rest of the app.
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
 * pill: every network toggle plus the (dynamic) operator toggles. This is
 * the "advanced" view; the most-used network toggles are also on the
 * always-visible floating bar for quick, live-previewing access.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
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
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.filter_network_label),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CoverageFilterChips(
            selected = selected,
            onToggle = onToggle,
        )

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
                    val isSelected = operator in selectedOperators
                    FilterChip(
                        selected = isSelected,
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
        0 -> stringResource(R.string.filter_sheet_summary_none)
        total -> stringResource(R.string.filter_sheet_summary_all)
        else -> stringResource(
            R.string.filter_sheet_summary_partial,
            distinctLabels.joinToString(),
            selected.size,
        )
    }
}
