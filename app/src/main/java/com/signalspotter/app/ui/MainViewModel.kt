package com.signalspotter.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.signalspotter.app.cellular.CellularScanner
import com.signalspotter.app.data.SignalRepository
import com.signalspotter.app.location.FixSample
import com.signalspotter.app.location.LocationTracker
import com.signalspotter.app.model.SignalReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** UI-only state, decoupled from the persistent reading list. */
data class HomeUiState(
    val isSampling: Boolean = false,
    val lastFix: FixSample? = null,
    val latestReading: SignalReading? = null,
    val samplingIntervalMs: Long = 5_000L,
)

/**
 * Single ViewModel for the home screen. It owns:
 *
 *  - `readings` and `count` flows that mirror Room (unidirectional).
 *  - `ui` flow for transient state (sampling flag, latest fix, etc).
 *  - All expensive work runs on `Dispatchers.IO`; CSV writing touches the
 *    filesystem and would ANR if invoked on the main thread.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SignalRepository.get(app)
    private val location = LocationTracker(app)
    private val cellular = CellularScanner(app)

    val readings: StateFlow<List<SignalReading>> = repo.observeReadings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val count: StateFlow<Int> = repo.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.value = _ui.value.copy(lastFix = location.lastKnown())
        }
    }

    /**
     * Capture a single reading at the device's current fix.
     * Returns silently if location is unavailable — UI shows a snackbar from
     * the call site.
     */
    fun captureNow() {
        viewModelScope.launch(Dispatchers.IO) {
            val fix = location.lastKnown() ?: return@launch
            val reading = cellular.snapshot(
                provider = fix.provider,
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracyMeters = fix.accuracyMeters
            )
            repo.add(reading)
            _ui.value = _ui.value.copy(lastFix = fix, latestReading = reading)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { repo.delete(id) }
    }

    fun deleteAll() {
        viewModelScope.launch(Dispatchers.IO) { repo.deleteAll() }
    }

    fun setSampling(active: Boolean) {
        _ui.value = _ui.value.copy(isSampling = active)
    }

    fun setInterval(ms: Long) {
        _ui.value = _ui.value.copy(samplingIntervalMs = ms)
    }

    /**
     * Write every reading to a CSV file at [destination] (a Uri supplied by
     * the Storage Access Framework `ACTION_CREATE_DOCUMENT` flow). Returns the
     * number of rows written or `-1` on failure. Designed to be `await`ed from
     * a coroutine — internally hops to [Dispatchers.IO].
     */
    suspend fun exportCsv(destination: Uri): Int = withContext(Dispatchers.IO) {
        runCatching {
            val data = readings.value // snapshot of the StateFlow
            if (data.isEmpty()) return@runCatching 0
            val app = getApplication<Application>()
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val stream = app.contentResolver.openOutputStream(destination)
                ?: return@runCatching 0
            stream.use { out ->
                out.bufferedWriter().use { writer ->
                    writer.append(
                        "timestamp,latitude,longitude,accuracy_m,provider," +
                            "network_type,signal_dbm,rsrp_dbm,rsrq_db,snr_db," +
                            "mcc,mnc,cell_id,operator\n"
                    )
                    for (r in data) writeRow(writer, r, sdf)
                }
            }
            data.size
        }.getOrDefault(-1)
    }

    private fun writeRow(
        writer: java.io.BufferedWriter,
        r: SignalReading,
        sdf: SimpleDateFormat
    ) {
        writer.append(sdf.format(Date(r.timestamp))).append(',')
        writer.append(r.latitude.toString()).append(',')
        writer.append(r.longitude.toString()).append(',')
        writer.append(r.accuracyMeters.toString()).append(',')
        writer.append(r.provider).append(',')
        writer.append(r.networkType.name).append(',')
        writer.append(r.signalDbm?.toString().orEmpty()).append(',')
        writer.append(r.rsrpDbm?.toString().orEmpty()).append(',')
        writer.append(r.rsrqDb?.toString().orEmpty()).append(',')
        writer.append(r.snrDb?.toString().orEmpty()).append(',')
        writer.append(r.mcc?.toString().orEmpty()).append(',')
        writer.append(r.mnc?.toString().orEmpty()).append(',')
        writer.append(r.cellId?.toString().orEmpty()).append(',')
        writer.append(r.operatorName?.let(::csvEscape) ?: "")
        writer.append('\n')
    }

    /** RFC-4180 style "quote fields with comma/quote/newline, double inner quotes". */
    private fun csvEscape(s: String): String {
        val needsQuote = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = s.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }
}
