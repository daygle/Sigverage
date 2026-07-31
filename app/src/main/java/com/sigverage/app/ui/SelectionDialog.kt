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

/**
 * A generic single-selection dialog with a list of radio buttons.
 *
 * @param T The type of item being selected.
 * @param title The dialog title.
 * @param options A list of options to display.
 * @param current The currently selected option.
 * @param onOptionSelected Callback when an option is tapped.
 * @param onDismiss Callback when the dialog should be closed.
 * @param labelProvider A function that returns the display string for an option.
 * @param descriptionProvider Optional function that returns a description string for an option.
 * @param footerSubtitle Optional help text shown below the list.
 */
@Composable
fun <T> SelectionDialog(
    title: String,
    options: List<T>,
    current: T,
    onOptionSelected: (T) -> Unit,
    onDismiss: () -> Unit,
    labelProvider: @Composable (T) -> String,
    descriptionProvider: (@Composable (T) -> String)? = null,
    footerSubtitle: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    val selected = option == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                onClick = { onOptionSelected(option) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = labelProvider(option),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (descriptionProvider != null) {
                                Text(
                                    text = descriptionProvider(option),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                if (footerSubtitle != null) {
                    Spacer(Modifier.padding(top = 8.dp))
                    Text(
                        text = footerSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
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
