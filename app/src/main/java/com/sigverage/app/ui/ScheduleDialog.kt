package com.sigverage.app.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sigverage.app.R
import com.sigverage.app.model.RecordingSchedule

private data class DayOption(val isoDay: Int, val initial: String)

private val DAY_OPTIONS = listOf(
    DayOption(1, "M"),
    DayOption(2, "T"),
    DayOption(3, "W"),
    DayOption(4, "T"),
    DayOption(5, "F"),
    DayOption(6, "S"),
    DayOption(7, "S"),
)

/**
 * Dialog for creating or editing a [RecordingSchedule].
 *
 * Refined UI with a circular day picker and tappable time rows that open
 * a clock-face TimePicker dialog. Supports overnight schedules.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    var startHour by remember { mutableIntStateOf(existing?.startHour ?: 9) }
    var startMinute by remember { mutableIntStateOf(existing?.startMinute ?: 0) }
    var endHour by remember { mutableIntStateOf(existing?.endHour ?: 17) }
    var endMinute by remember { mutableIntStateOf(existing?.endMinute ?: 0) }

    var pickingStartTime by remember { mutableStateOf(false) }
    var pickingEndTime by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = {
            Text(
                text = if (existing != null) stringResource(R.string.schedule_edit)
                       else stringResource(R.string.schedule_add),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
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

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                // Day-of-week circular picker
                Column {
                    Text(
                        text = stringResource(R.string.schedule_days_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        for (day in DAY_OPTIONS) {
                            val isSelected = day.isoDay in selectedDays
                            DayCircle(
                                initial = day.initial,
                                isSelected = isSelected,
                                onClick = {
                                    selectedDays = if (isSelected) {
                                        selectedDays - day.isoDay
                                    } else {
                                        selectedDays + day.isoDay
                                    }
                                    daysError = false
                                },
                            )
                        }
                    }
                    if (daysError) {
                        Text(
                            text = stringResource(R.string.schedule_error_days_required),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                )

                // Time selection rows
                Column {
                    Text(
                        text = stringResource(R.string.schedule_active_time_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))

                    TimeRow(
                        label = stringResource(R.string.schedule_starts_at),
                        hour = startHour,
                        minute = startMinute,
                        onClick = { pickingStartTime = true },
                    )

                    Spacer(Modifier.height(8.dp))

                    TimeRow(
                        label = stringResource(R.string.schedule_ends_at),
                        hour = endHour,
                        minute = endMinute,
                        onClick = { pickingEndTime = true },
                    )

                    val identical = startHour == endHour && startMinute == endMinute
                    val overnight = !identical &&
                        (endHour * 60 + endMinute) < (startHour * 60 + startMinute)
                    if (identical) {
                        Text(
                            text = stringResource(R.string.schedule_error_time_identical),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else if (overnight) {
                        Text(
                            text = stringResource(R.string.schedule_overnight_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    nameError = name.isBlank()
                    daysError = selectedDays.isEmpty()
                    val identicalTime = startHour == endHour && startMinute == endMinute

                    if (!nameError && !daysError && !identicalTime) {
                        val schedule = RecordingSchedule(
                            id = existing?.id ?: 0,
                            name = name.trim(),
                            daysOfWeek = selectedDays,
                            startHour = startHour,
                            startMinute = startMinute,
                            endHour = endHour,
                            endMinute = endMinute,
                            enabled = existing?.enabled ?: true,
                        )
                        onSave(schedule)
                    }
                }
            ) {
                Text(stringResource(R.string.schedule_save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.schedule_cancel))
            }
        },
    )

    // Time Picker Dialogs
    if (pickingStartTime) {
        val state = rememberTimePickerState(startHour, startMinute, is24Hour = true)
        TimePickerDialog(
            title = stringResource(R.string.schedule_select_start_time),
            onDismissRequest = { pickingStartTime = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        startHour = state.hour
                        startMinute = state.minute
                        pickingStartTime = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingStartTime = false }) {
                    Text(stringResource(R.string.schedule_cancel))
                }
            },
        ) {
            TimePicker(state = state)
        }
    }

    if (pickingEndTime) {
        val state = rememberTimePickerState(endHour, endMinute, is24Hour = true)
        TimePickerDialog(
            title = stringResource(R.string.schedule_select_end_time),
            onDismissRequest = { pickingEndTime = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        endHour = state.hour
                        endMinute = state.minute
                        pickingEndTime = false
                    },
                ) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { pickingEndTime = false }) {
                    Text(stringResource(R.string.schedule_cancel))
                }
            },
        ) {
            TimePicker(state = state)
        }
    }
}

@Composable
private fun DayCircle(
    initial: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TimeRow(
    label: String,
    hour: Int,
    minute: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.AccessTime,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = "%02d:%02d".format(hour, minute),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
