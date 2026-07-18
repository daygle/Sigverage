package com.sigverage.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sigverage.app.R
import com.sigverage.app.model.RecordingSchedule

/**
 * Dedicated schedules sub-page with a back button in the top app bar.
 * Reached by tapping the "Schedules" drill-out row in [SettingsScreen].
 * Shows all schedules with add/edit/delete, and a FAB to create new ones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesPage(
    schedules: List<RecordingSchedule>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (RecordingSchedule) -> Unit,
    onDelete: (RecordingSchedule) -> Unit,
    onToggleEnabled: (RecordingSchedule) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf<RecordingSchedule?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_schedules_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_permissions_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.schedule_add))
            }
        }
    ) { padding ->
        if (schedules.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.EventBusy,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outlineVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.schedule_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 48.dp),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp, vertical = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(schedules, key = { it.id }) { schedule ->
                    ScheduleCard(
                        schedule = schedule,
                        onClick = { onEdit(schedule) },
                        onDelete = { confirmDelete = schedule },
                        onToggleEnabled = { onToggleEnabled(schedule) },
                    )
                }
            }
        }
    }

    confirmDelete?.let { schedule ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.schedule_delete_confirm_title)) },
            text = { Text(stringResource(R.string.schedule_delete_confirm_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(schedule)
                        confirmDelete = null
                    },
                ) { Text(stringResource(R.string.confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.confirm_no))
                }
            },
        )
    }
}

@Composable
private fun ScheduleCard(
    schedule: RecordingSchedule,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .alpha(if (schedule.enabled) 1f else 0.6f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = schedule.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (schedule.enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = formatDays(schedule.daysOfWeek),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                
                Spacer(Modifier.height(2.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (schedule.enabled) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = formatTimeRange(schedule.startHour, schedule.startMinute, schedule.endHour, schedule.endMinute),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (schedule.enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }

                if (isOvernight(schedule.startHour, schedule.startMinute, schedule.endHour, schedule.endMinute)) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.schedule_overnight_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_reading),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = if (schedule.enabled) 0.8f else 0.5f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Switch(
                    checked = schedule.enabled,
                    onCheckedChange = { onToggleEnabled() },
                    modifier = Modifier.scale(0.85f),
                )
            }
        }
    }
}

private fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
)

@Composable
private fun formatDays(days: Set<Int>): String {
    if (days.size == 7) return stringResource(R.string.schedule_every_day)
    if (days == setOf(1, 2, 3, 4, 5)) return stringResource(R.string.schedule_weekdays)
    if (days == setOf(6, 7)) return stringResource(R.string.schedule_weekends)
    val dayNames = listOf(
        stringResource(R.string.schedule_day_mon),
        stringResource(R.string.schedule_day_tue),
        stringResource(R.string.schedule_day_wed),
        stringResource(R.string.schedule_day_thu),
        stringResource(R.string.schedule_day_fri),
        stringResource(R.string.schedule_day_sat),
        stringResource(R.string.schedule_day_sun),
    )
    return days.sorted().joinToString(", ") { dayNames[it - 1] }
}

private fun formatTimeRange(startH: Int, startM: Int, endH: Int, endM: Int): String {
    return "%02d:%02d - %02d:%02d".format(startH, startM, endH, endM)
}

private fun isOvernight(startH: Int, startM: Int, endH: Int, endM: Int): Boolean =
    (endH * 60 + endM) < (startH * 60 + startM)
