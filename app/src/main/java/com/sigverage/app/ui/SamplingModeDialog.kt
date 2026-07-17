package com.sigverage.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sigverage.app.R
import com.sigverage.app.model.SamplingMode

/**
 * Modal dialog for choosing the location [SamplingMode] - the battery-vs-
 * accuracy trade-off applied while recording.
 *
 * Each option shows a title and a one-line description of its power impact so
 * the choice is self-explanatory. Selecting a row applies it immediately via
 * [MainViewModel.setSamplingMode] and dismisses the dialog.
 */
@Composable
fun SamplingModeDialog(
    current: SamplingMode,
    onDismiss: () -> Unit,
    onPick: (SamplingMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_sampling_mode_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                OPTIONS.forEach { option ->
                    val selected = option.mode == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                onClick = { onPick(option.mode) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = stringResource(option.labelRes),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(option.descRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.detail_close))
            }
        },
    )
}

private data class SamplingModeOption(
    val mode: SamplingMode,
    val labelRes: Int,
    val descRes: Int,
)

private val OPTIONS: List<SamplingModeOption> = listOf(
    SamplingModeOption(
        SamplingMode.Auto,
        R.string.sampling_mode_auto,
        R.string.sampling_mode_auto_desc,
    ),
    SamplingModeOption(
        SamplingMode.PowerSaver,
        R.string.sampling_mode_power_saver,
        R.string.sampling_mode_power_saver_desc,
    ),
    SamplingModeOption(
        SamplingMode.Balanced,
        R.string.sampling_mode_balanced,
        R.string.sampling_mode_balanced_desc,
    ),
    SamplingModeOption(
        SamplingMode.HighAccuracy,
        R.string.sampling_mode_high_accuracy,
        R.string.sampling_mode_high_accuracy_desc,
    ),
)
