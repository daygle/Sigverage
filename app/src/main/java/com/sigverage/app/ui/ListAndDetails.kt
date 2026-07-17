package com.sigverage.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sigverage.app.R
import com.sigverage.app.model.NetworkType
import com.sigverage.app.model.SignalReading
import com.sigverage.app.ui.theme.NetworkColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ListPanel(
    readings: List<SignalReading>,
    onClick: (SignalReading) -> Unit,
    onDelete: (Long) -> Unit,
    onFocusMap: (SignalReading) -> Unit,
) {
    if (readings.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.empty_list_hint),
                    modifier = Modifier.padding(horizontal = 32.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val fmt = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(readings, key = { it.id }) { r ->
            ReadingCard(
                r = r,
                fmt = fmt,
                onClick = { onClick(r) },
                onDelete = { onDelete(r.id) },
                onFocusMap = { onFocusMap(r) },
            )
        }
    }
}

@Composable
private fun ReadingCard(
    r: SignalReading,
    fmt: SimpleDateFormat,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onFocusMap: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NetworkBadge(r.networkType)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = r.networkType.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    r.operatorName?.let { operator ->
                        Text(
                            text = " \u00b7 $operator",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    r.signalDbm?.let { dbm ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = signalStrengthColor(dbm).copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "$dbm dBm",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = signalStrengthColor(dbm)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = fmt.format(Date(r.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "%.5f, %.5f".format(r.latitude, r.longitude),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            IconButton(
                onClick = onFocusMap,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = stringResource(R.string.show_on_map_cd),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.width(4.dp))

            IconButton(
                onClick = onDelete,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_reading),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun signalStrengthColor(dbm: Int): Color = when {
    dbm >= -90 -> Color(0xFF22C55E)   // Green - strong
    dbm >= -105 -> Color(0xFFF59E0B)  // Amber - ok
    else -> Color(0xFFEF4444)          // Red - weak
}

@Composable
fun NetworkBadge(network: NetworkType) {
    val color = NetworkColors[network] ?: Color.Gray
    Surface(
        modifier = Modifier.size(width = 52.dp, height = 38.dp),
        shape = RoundedCornerShape(10.dp),
        color = color
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = network.shortLabel,
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsSheet(
    reading: SignalReading,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onShowOnMap: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val fmt = remember { SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                NetworkBadge(reading.networkType)
                Column {
                    Text(
                        text = reading.networkType.label,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    reading.operatorName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Spacer(Modifier.height(12.dp))

            // Signal section
            reading.signalDbm?.let {
                DetailRow(stringResource(R.string.detail_dbm), stringResource(R.string.detail_dbm_value, it))
            }
            reading.rsrpDbm?.let {
                DetailRow(stringResource(R.string.detail_lte_rsrp), stringResource(R.string.detail_db_value, it))
            }
            reading.rsrqDb?.let {
                DetailRow(stringResource(R.string.detail_lte_rsrq), stringResource(R.string.detail_db_value, it))
            }
            reading.snrDb?.let {
                DetailRow(stringResource(R.string.detail_lte_snr), stringResource(R.string.detail_db_value, it))
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(8.dp))

            // Location section
            DetailRow(
                stringResource(R.string.detail_lat_lng),
                stringResource(R.string.detail_lat_lng_value, reading.latitude, reading.longitude)
            )
            DetailRow(stringResource(R.string.detail_accuracy), stringResource(R.string.detail_accuracy_value, reading.accuracyMeters.toInt().coerceAtLeast(0)))
            DetailRow(stringResource(R.string.detail_provider), reading.provider)

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(8.dp))

            // Network info section
            reading.mcc?.let { mcc ->
                reading.mnc?.let { mnc ->
                    DetailRow(stringResource(R.string.detail_mcc_mnc), stringResource(R.string.detail_mcc_mnc_value, mcc, mnc))
                }
            }
            reading.cellId?.let {
                DetailRow(stringResource(R.string.detail_cell_id), it.toString())
            }
            DetailRow(stringResource(R.string.detail_recorded), fmt.format(Date(reading.timestamp)))

            Spacer(Modifier.height(20.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = {
                    scope.launch { sheetState.hide() }
                        .invokeOnCompletion { onShowOnMap() }
                }) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.show_on_map_action))
                }
                TextButton(
                    onClick = {
                        scope.launch { sheetState.hide() }
                            .invokeOnCompletion { onDelete() }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.delete_reading))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.detail_close))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.5f),
            textAlign = TextAlign.End
        )
    }
}
