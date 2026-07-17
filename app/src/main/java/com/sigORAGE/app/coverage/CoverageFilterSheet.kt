package com.sigorage.app.coverage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sigorage.app.R
import com.sigorage.app.model.NetworkType
import com.sigorage.app.model.SignalReading
import org.osmdroid.views.MapView

/**
 * Map + filter-chip sheet, hosted in a Material 3 [BottomSheetScaffold].
 *
 * The map (a ready-built [MapView] injected by `MapPanel`) sits in the
 * scaffold's `content` slot; the eight filter chips live in the
 * `sheetContent` slot, gated by a 72 dp peek height so the collapsed
 * state shows only a drag handle and a one-line summary like
 * "Filters: 5G, LTE (3 of 8 active)".
 *
 * **Why a bottom sheet?** The previous top-of-map chip strip clipped the
 * map and forced horizontal scrolling on phones with narrower viewports
 * (and even then the rightmost chips felt like second-class UI). A
 * non-modal bottom sheet keeps the chips one tap away (drag up) without
 * ever obscuring the map — and because the sheet is **not modal**, live
 * preview of the coverage grid continues to work while the user toggles
 * networks on/off inside the sheet.
 *
 * **Initial state.** Always `PartiallyExpanded` (collapsed) on first
 * composition so the user opens to a full-screen map. `skipHiddenState =
 * true` so the sheet can't be dragged fully off-screen — the collapsed
 * peek with the summary line is the at-a-glance indicator of which
 * filters are active. Sheet state is automatically saveable across
 * recompositions by [rememberStandardBottomSheetState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverageFilterSheet(
    mapView: MapView,
    readings: List<SignalReading>,
    coverageFilter: Set<NetworkType>,
    onToggleFilter: (NetworkType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true,
    )
    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = sheetState,
    )

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = SHEET_PEEK_HEIGHT,
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContentColor = MaterialTheme.colorScheme.onSurface,
        // Suppress the scaffold's auto-rendered drag handle. In Material 3
        // 1.2.1+ (Compose BOM 2024.06.00) the default is
        // `BottomSheetDefaults.DragHandle()` which would duplicate the
        // hand-tuned handle we render as the first child of [SheetContents].
        // The sheet body itself is still draggable; this only removes the
        // visual duplicate pill.
        sheetDragHandle = null,
        sheetContent = {
            SheetContents(
                selected = coverageFilter,
                onToggle = onToggleFilter,
            )
        },
        modifier = modifier,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize()
            )
            if (readings.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
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
    }
}

/**
 * The visible contents of the bottom sheet: drag handle, summary line,
 * and the wrap-friendly chip strip. Kept private to this file because
 * it's purely the sheet-body layout — there's no reason for `MapPanel`
 * (or anyone else) to compose it directly.
 */
@Composable
private fun SheetContents(
    selected: Set<NetworkType>,
    onToggle: (NetworkType) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Drag handle — Material 3 convention: a 32 x 4 dp pill, ~40%
        // alpha, centred at the top of the sheet. Doubles as a tap target
        // for expand/collapse because the entire sheet body is also
        // draggable; this just makes the affordance visually obvious.
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 8.dp)
                .size(width = 32.dp, height = 4.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(2.dp),
                )
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = filterSummaryText(selected),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
            // `titleSmall` + `onSurfaceVariant` keeps the 1-line summary
            // reading as a secondary status line — it shouldn't compete
            // visually with the FilterChip labels themselves when the
            // sheet is expanded. `onSurfaceVariant` is the Material 3
            // token for "less prominent than primary content".
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        CoverageFilterChips(
            selected = selected,
            onToggle = onToggle,
        )
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * Compose the 1-line summary shown on the collapsed sheet handle.
 *
 *  - **0 selected** → "No filters active (0 of 8)" — the map will be
 *    blank, this is the only hint the user gets while the sheet is
 *    collapsed.
 *  - **8 selected** → "All networks active (8 of 8)" — avoids the
 *    noise of listing eight short labels including the duplicate "5G"
 *    shared by `FiveG` and `NR_NSA`.
 *  - **1..7 selected** → "Filters: 5G, LTE (3 of 8 active)" with the
 *    label list built from **distinct** short labels — both `FiveG` and
 *    `NR_NSA` collapse to "5G" so we dedup to keep the line short. The
 *    parenthetical count uses `selected.size` (not the distinct count)
 *    because the user actually enabled that many `NetworkType` values;
 *    when they expand the sheet they see both 5G variants rendered as
 *    separate `FilterChip`s, which makes the count consistent with the
 *    chip strip.
 *
 * Kept as a private @Composable so it lives next to the strings it
 * resolves. Returning a String (not a Text) keeps the caller in control
 * of `maxLines` / `overflow` behaviour.
 */
@Composable
private fun filterSummaryText(selected: Set<NetworkType>): String {
    val total = NetworkType.values().size
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

/**
 * Peek height for the collapsed sheet — 72 dp is the smallest height
 * that comfortably fits a Material 3 drag handle (32 x 4 dp with 12 dp
 * top margin + 12 dp bottom margin = 28 dp) plus a single line of
 * `titleMedium` text (≈ 28 dp tall at default density) with breathing
 * room. Tall enough for the summary to be readable at 200 % font scale
 * (Material accessibility guidance).
 */
private val SHEET_PEEK_HEIGHT = 72.dp