package com.sigverage.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sigverage.app.R

/**
 * Modal dialog for picking reading retention policy. Five discrete options,
 * no freeform input - keeps the UX simple and predictable. `0` (forever) is
 * an explicit choice so it's never an accidental default.
 */
@Composable
fun RetentionDialog(
    currentDays: Int,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.retention_dialog_title)) },
        text = {
            Column {
                OPTIONS.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option.days == currentDays,
                            onClick = { onPick(option.days) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(option.labelRes))
                    }
                }
                Spacer(Modifier.padding(top = 8.dp))
                Text(
                    text = stringResource(R.string.retention_dialog_help),
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

private data class RetentionOption(val days: Int, val labelRes: Int)

private val OPTIONS: List<RetentionOption> = listOf(
    RetentionOption(days = 0,   labelRes = R.string.retention_forever),
    RetentionOption(days = 30,  labelRes = R.string.retention_30_days),
    RetentionOption(days = 90,  labelRes = R.string.retention_90_days),
    RetentionOption(days = 180, labelRes = R.string.retention_6_months),
    RetentionOption(days = 365, labelRes = R.string.retention_1_year),
)
