package com.sigverage.app.coverage

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sigverage.app.model.NetworkType
import com.sigverage.app.ui.theme.NetworkColors

/**
 * Horizontally-scrollable strip of Material 3 `FilterChip`s, one per
 * [NetworkType], that toggles inclusion in the visible coverage grid.
 *
 * Unselected chips show the network colour at 30% alpha (so they're still
 * recognisable but muted); selected chips show the full colour plus a tinted
 * container background.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverageFilterChips(
    selected: Set<NetworkType>,
    onToggle: (NetworkType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NetworkType.values().forEach { type ->
            val isSelected = type in selected
            val swatch = NetworkColors[type] ?: Color.Gray
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
