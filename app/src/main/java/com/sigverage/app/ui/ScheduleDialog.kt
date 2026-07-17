package com.sigverage.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sigverage.app.R
import com.sigverage.app.model.RecordingSchedule

private data class DayOption(val isoDay: Int, val labelRes: Int)

private val DAY_OPTIONS = listOf(
    DayOption(1, R.string.schedule_day_mon),
    DayOption(2, R.string.schedule_day_tue),
    DayOption(3, R.string.schedule_day_wed),
    DayOption(4, R.string.schedule_day_thu),
    DayOption(5, R.string.schedule_day_fri),
    DayOption(6, R.string.schedule_day_sat),
    DayOption(7, R.string.schedule_day_sun),
)

/**
 * Dialog for creating or editing a [RecordingSchedule].
 *
 * Shows a name field, day-of-week chip selector, and two TimePickers
 * (from / to). Validates that at least one day is selected and the
 * end time is after the start time.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDialog(
    existing: RecordingSchedule? = null,
    onDismiss: () -> Unit,
    onSave: (RecordingSchedule) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var selectedDays by remember { mutableStateOf(existing?.daysOfWeek ?: emptySet()) }
    var nameError by remember { mutableStateOf(false) }
    var daysError by remember { mutableStateOf(false) }
    var timeError by remember { mutableStateOf(false) }

    val startState = rememberTimePickerState(
        initialHour = existing?.startHour ?: 9,
        initialMinute = existing?.startMinute ?: 0,
        is24Hour = true,
    )
    val endState = rememberTimePickerState(
        initialHour = existing?.endHour ?: 17,
        initialMinute = existing?.endMinute ?: 0,
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = if (existing != null) stringResource(R.string.schedule_edit)
                       else stringResource(R.string.schedule_add),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = false
                    },
                    label = { Text(stringResource(R.string.schedule_name_label)) },
                    placeholder = { Text(stringResource(R.string.schedule_name_hint)) },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text(stringResource(R.string.schedule_error_name_required)) }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )

                // Day-of-week chips
                Column {
                    Text(
                        text = stringResource(R.string.schedule_days_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (day in DAY_OPTIONS) {
                            val selected = day.isoDay in selectedDays
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    selectedDays = if (selected) {
                                        selectedDays - day.isoDay
                                    } else {
                                        selectedDays + day.isoDay
                                    }
                                    daysError = false
                                },
                                label = { Text(stringResource(day.labelRes)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                        }
                    }
                    if (daysError) {
                        Text(
                            text = stringResource(R.string.schedule_error_days_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                // Time pickers
                Column {
                    Text(
                        text = stringResource(R.string.schedule_time_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.schedule_time_from),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TimePicker(
                                state = startState,
                                modifier = Modifier.width(120.dp),
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.schedule_time_to),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TimePicker(
                                state = endState,
                                modifier = Modifier.width(120.dp),
                            )
                        }
                    }
                    if (timeError) {
                        Text(
                            text = stringResource(R.string.schedule_error_time_invalid),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                nameError = name.isBlank()
                daysError = selectedDays.isEmpty()

                val startMinutes = startState.hour * 60 + startState.minute
                val endMinutes = endState.hour * 60 + endState.minute
                timeError = endMinutes <= startMinutes

                if (!nameError && !daysError && !timeError) {
                    val schedule = RecordingSchedule(
                        id = existing?.id ?: 0,
                        name = name.trim(),
                        daysOfWeek = selectedDays,
                        startHour = startState.hour,
                        startMinute = startState.minute,
                        endHour = endState.hour,
                        endMinute = endState.minute,
                        enabled = existing?.enabled ?: true,
                    )
                    onSave(schedule)
                }
            }) {
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
