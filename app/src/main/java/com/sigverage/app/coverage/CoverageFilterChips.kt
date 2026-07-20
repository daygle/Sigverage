package com.sigverage.app.coverage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sigverage.app.model.NetworkType
import com.sigverage.app.ui.theme.rememberNetworkColors

/**
 * Material 3 [FilterChip] strip for cellular network types.
 *
 * Renders one chip per [NetworkType] in [types] - callers pass only the
 * networks worth showing (e.g. those actually present in the current
 * readings), so the strip never advertises an empty network.
 *
 * Backed by [FlowRow] (Compose 1.6+, `@OptIn(ExperimentalLayoutApi)`)
 * so chips wrap onto additional rows when the parent doesn't provide
 * enough horizontal space. No horizontal scroll - the parent is expected
 * to give the row enough width or live with wrapping. Used inside the
 * "Filters" modal sheet (`ModalBottomSheet`) hosted by `CoverageMapScreen`.
 *
 * Unselected chips show the network colour at 30 % alpha (so they're
 * still recognisable but muted); selected chips show the full colour
 * plus a tinted container background. Each chip carries a 10 dp
 * colour-dot leading icon so users can match the chip to the tile on
 * the map at a glance.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CoverageFilterChips(
    types: List<NetworkType>,
    selected: Set<NetworkType>,
    onToggle: (NetworkType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = rememberNetworkColors()
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        types.forEach { type ->
            val isSelected = type in selected
            val swatch = palette[type] ?: Color.Gray
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(type) },
                label = { Text(type.shortLabel) },
                leadingIcon = {
                    // 10 dp colour dot so users can match the chip to the
                    // tile in the map at a glance.
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
    }
}