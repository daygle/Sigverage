package com.sigverage.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sigverage.app.R

/** Predefined battery thresholds the user can choose from. */
private val THRESHOLD_OPTIONS = intArrayOf(5, 10, 15, 20)

/** Sentinel value meaning the user chose a custom (non-preset) threshold. */
private const val CUSTOM_SENTINEL = -1

/**
 * Dialog that lets the user pick a battery percentage below which
 * the wake lock is not acquired during sampling bursts.
 *
 * Offers four preset options (5%, 10%, 15%, 20%) plus a "Custom" row
 * with a number input for arbitrary percentages (1–100).
 */
@Composable
fun BatteryThresholdDialog(
    /** Currently selected threshold percentage. */
    current: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    // If the current value matches a preset, select it; otherwise select
    // the custom sentinel so the custom field is pre-populated.
    val initialPreset = if (current in THRESHOLD_OPTIONS) current else CUSTOM_SENTINEL
    var selectedPreset by remember { mutableIntStateOf(initialPreset) }
    var customText by remember {
        mutableStateOf(
            if (initialPreset == CUSTOM_SENTINEL) current.toString() else ""
        )
    }
    var customError by remember { mutableStateOf<String?>(null) }

    val resolvedPct =
        if (selectedPreset == CUSTOM_SENTINEL) customText.toIntOrNull() ?: 0
        else selectedPreset

    val isValid =
        if (selectedPreset == CUSTOM_SENTINEL) {
            val n = customText.toIntOrNull()
            n != null && n in 1..100
        } else true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.battery_threshold_dialog_title)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                Text(
                    text = stringResource(R.string.battery_threshold_dialog_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                THRESHOLD_OPTIONS.forEach { pct ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedPreset == pct,
                                onClick = {
                                    selectedPreset = pct
                                    customError = null
                                },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedPreset == pct,
                            onClick = null, // handled by Row's selectable
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.battery_threshold_value, pct),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }

                // Custom percentage row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedPreset == CUSTOM_SENTINEL,
                            onClick = {
                                selectedPreset = CUSTOM_SENTINEL
                                if (customText.isEmpty()) customText = ""
                            },
                            role = Role.RadioButton,
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedPreset == CUSTOM_SENTINEL,
                        onClick = null,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.battery_threshold_custom_label),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { raw ->
                            // Only allow digits; clamp to reasonable length.
                            val filtered = raw.filter { it.isDigit() }.take(3)
                            customText = filtered
                            selectedPreset = CUSTOM_SENTINEL
                            val n = filtered.toIntOrNull()
                            customError = when {
                                n == null -> null  // empty / typing
                                n < 1 -> "Min 1%"
                                n > 100 -> "Max 100%"
                                else -> null
                            }
                        },
                        singleLine = true,
                        isError = customError != null,
                        supportingText = customError?.let { err ->
                            { Text(err, color = MaterialTheme.colorScheme.error) }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(88.dp),
                        textStyle = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPick(resolvedPct) },
                enabled = isValid,
            ) {
                Text(stringResource(R.string.schedule_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.schedule_cancel))
            }
        },
    )
}
