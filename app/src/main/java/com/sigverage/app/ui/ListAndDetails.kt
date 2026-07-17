package com.sigverage.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
            Text(
                text = stringResource(R.string.empty_list_hint),
                modifier = Modifier.padding(24.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
        return
    }
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(readings, key = { it.id }) { r ->
            ReadingRow(
                r = r,
                fmt = fmt,
                onClick = { onClick(r) },
                onDelete = { onDelete(r.id) },
                onFocusMap = { onFocusMap(r) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ReadingRow(
    r: SignalReading,
    fmt: SimpleDateFormat,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onFocusMap: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NetworkBadge(r.networkType)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = r.networkType.label,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                text = buildString {
                    r.signalDbm?.let { append("$it dBm · ") }
                    r.operatorName?.let { append("$it · ") }
                    append(fmt.format(Date(r.timestamp)))
                }
            )
            Text(
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.outline,
                text = buildString {
                    append("%.5f,%.5f".format(r.latitude, r.longitude))
                    r.cellId?.let { append(" · cell $it") }
                }
            )
        }
        IconButton(onClick = onFocusMap) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = stringResource(R.string.show_on_map_cd),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete_reading),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun NetworkBadge(network: NetworkType) {
    val color = NetworkColors[network] ?: Color.Gray
    Surface(
        modifier = Modifier.size(width = 56.dp, height = 40.dp),
        shape = RoundedCornerShape(10.dp),
        color = color
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = network.shortLabel,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
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
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NetworkBadge(reading.networkType)
                Text(
                    text = reading.networkType.label,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider()
            DetailRow(
                stringResource(R.string.detail_recorded),
                fmt.format(Date(reading.timestamp))
            )
            DetailRow(
                stringResource(R.string.detail_provider),
                reading.provider
            )
            DetailRow(
                stringResource(R.string.detail_accuracy),
                stringResource(
                    R.string.detail_accuracy_value,
                    reading.accuracyMeters.toInt().coerceAtLeast(0)
                )
            )
            reading.signalDbm?.let {
                DetailRow(
                    stringResource(R.string.detail_dbm),
                    stringResource(R.string.detail_dbm_value, it)
                )
            }
            reading.rsrpDbm?.let {
                DetailRow(
                    stringResource(R.string.detail_lte_rsrp),
                    stringResource(R.string.detail_db_value, it)
                )
            }
            reading.rsrqDb?.let {
                DetailRow(
                    stringResource(R.string.detail_lte_rsrq),
                    stringResource(R.string.detail_db_value, it)
                )
            }
            reading.snrDb?.let {
                DetailRow(
                    stringResource(R.string.detail_lte_snr),
                    stringResource(R.string.detail_db_value, it)
                )
            }
            reading.operatorName?.let {
                DetailRow(stringResource(R.string.detail_operator), it)
            }
            reading.mcc?.let { mcc ->
                reading.mnc?.let { mnc ->
                    DetailRow(
                        stringResource(R.string.detail_mcc_mnc),
                        stringResource(R.string.detail_mcc_mnc_value, mcc, mnc)
                    )
                }
            }
            reading.cellId?.let {
                DetailRow(stringResource(R.string.detail_cell_id), it.toString())
            }
            DetailRow(
                stringResource(R.string.detail_lat_lng),
                stringResource(
                    R.string.detail_lat_lng_value,
                    reading.latitude,
                    reading.longitude
                )
            )
            Spacer(Modifier.height(8.dp))
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
                        contentDescription = null
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
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.delete_reading))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.detail_close))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            modifier = Modifier.weight(2f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
