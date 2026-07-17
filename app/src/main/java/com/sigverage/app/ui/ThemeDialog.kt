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
import com.sigverage.app.model.ThemeMode

/**
 * Modal dialog for picking the app's theme override.
 *
 *  - [ThemeMode.System] - follow the OS setting (`isSystemInDarkTheme()`).
 *  - [ThemeMode.Light]  - always light.
 *  - [ThemeMode.Dark]   - always dark.
 *
 * Choice is propagated through [MainViewModel.setThemeMode], which writes
 * to SharedPreferences and updates the observed [HomeUiState] so the
 * activity root `SigverageTheme` re-resolves the colour scheme.
 */
@Composable
fun ThemeDialog(
    current: ThemeMode,
    onDismiss: () -> Unit,
    onPick: (ThemeMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme_title)) },
        text = {
            Column(Modifier.selectableGroup()) {
                OPTIONS.forEach { (mode, label) ->
                    val selected = mode == current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selected,
                                onClick = { onPick(mode) },
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
                        Text(stringResource(label))
                    }
                }
                Spacer(Modifier.padding(top = 8.dp))
                Text(
                    text = stringResource(R.string.settings_theme_subtitle),
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

private data class ThemeOption(val mode: ThemeMode, val labelRes: Int)

private val OPTIONS: List<ThemeOption> = listOf(
    ThemeOption(ThemeMode.System, R.string.theme_system),
    ThemeOption(ThemeMode.Light,  R.string.theme_light),
    ThemeOption(ThemeMode.Dark,   R.string.theme_dark),
)
