package com.sigverage.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sigverage.app.R

/**
 * Modal dialog with a slider that lets the user pick the storage zoom for
 * the coverage grid. The visible value is shown as a tile-size estimate
 * ("150 m per cell" / "9.6 km per cell" / etc.) so the user has a
 * physical-world anchor for what they're choosing.
 *
 * Slider range is [CoverageGridOverlay.MIN_STORAGE_ZOOM, MAX_STORAGE_ZOOM]
 * snapping to integer steps. The dialog maintains its own local Float
 * state during drag so the slider feels smooth, then commits to the
 * ViewModel on `onValueChangeFinished`.
 */
@Composable
fun GranularityDialog(
    currentZoom: Int,
    onDismiss: () -> Unit,
    onChange: (Int) -> Unit,
) {
    var drag by remember { mutableStateOf(currentZoom.toFloat()) }
    val display = drag.toInt()
        .coerceIn(MIN_DISPLAY, MAX_DISPLAY)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.granularity_slider_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = drag,
                    onValueChange = { drag = it },
                    onValueChangeFinished = { onChange(display) },
                    valueRange = MIN_DISPLAY.toFloat()..MAX_DISPLAY.toFloat(),
                    steps = MAX_DISPLAY - MIN_DISPLAY - 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.granularity_current_label,
                        stringResource(sizeLabelFor(display))
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.granularity_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.detail_close))
            }
        },
    )
}

@StringRes
private fun sizeLabelFor(zoom: Int): Int = when (zoom) {
    12 -> R.string.granularity_size_9600
    13 -> R.string.granularity_size_4800
    14 -> R.string.granularity_size_2400
    15 -> R.string.granularity_size_1200
    16 -> R.string.granularity_size_600
    17 -> R.string.granularity_size_300
    18 -> R.string.granularity_size_150
    19 -> R.string.granularity_size_75
    else -> R.string.granularity_size_unknown
}

private const val MIN_DISPLAY = 12
private const val MAX_DISPLAY = 19
