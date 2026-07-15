package com.signalspotter.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.signalspotter.app.cellular.CellularScanner
import com.signalspotter.app.coverage.CoverageGridOverlay
import com.signalspotter.app.R
import com.signalspotter.app.data.PreferencesStore
import com.signalspotter.app.data.SignalRepository
import com.signalspotter.app.location.FixSample
import com.signalspotter.app.location.LocationTracker
import com.signalspotter.app.model.NetworkType
import com.signalspotter.app.model.SignalReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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
    /** Networks currently displayed by the coverage grid. Defaults to all. */
    val coverageFilter: Set<NetworkType> = NetworkType.values().toSet(),
    /** Storage-zoom level (Mercator tile Z) the coverage grid bins into.
     *  Defaults to 18 ≈ a city block. Manipulated from the AppBar slider. */
    val coverageZoom: Int = CoverageGridOverlay.DEFAULT_STORAGE_ZOOM,
    /** Retention in days; `0` means "forever" (the default — opt-in expiry). */
    val retentionDays: Int = PreferencesStore.DEFAULT_RETENTION_DAYS,
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
    private val prefs = PreferencesStore(app)

    /**
     * One-shot UI events (snackbar messages, transient notifications). Each
     * event is consumed exactly once, even across configuration changes,
     * because we wrap the underlying `Channel` in `receiveAsFlow()` rather
     * than folding the event into `HomeUiState` (which would re-emit the
     * same snackbar on rotation).
     */
    private val _events = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = _events.receiveAsFlow()

    val readings: StateFlow<List<SignalReading>> = repo.observeReadings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val count: StateFlow<Int> = repo.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    init {
        // Apply retention sweep silently on startup. The user already
        // accepted the policy by leaving it active; no need to spam them
        // with a snackbar mentioning how many rows got cleaned out.
        val initialRetention = prefs.retentionDays
        _ui.value = _ui.value.copy(retentionDays = initialRetention)
        if (initialRetention > 0) applyRetention(initialRetention, announce = false)

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
     * Toggle inclusion of [type] in the visible coverage grid.
     *
     * An empty filter set hides every tile; if the user toggles every
     * network off they can pan around with a blank map and re-enable
     * individual networks one by one.
     */
    fun toggleCoverageFilter(type: NetworkType) {
        _ui.value = _ui.value.let { current ->
            val next = current.coverageFilter.toMutableSet()
            if (!next.add(type)) next.remove(type)
            current.copy(coverageFilter = next)
        }
    }

    /**
     * Set the storage-zoom used to bin readings into Mercator tiles. The
     * AppBar slider drives this; clamped to the legal range.
     *
     * Decoupled from the visible map zoom — users can pan/zoom the map
     * freely without changing the analytics granularity, and vice versa.
     */
    fun setCoverageZoom(z: Int) {
        _ui.value = _ui.value.copy(
            coverageZoom = z.coerceIn(
                CoverageGridOverlay.MIN_STORAGE_ZOOM,
                CoverageGridOverlay.MAX_STORAGE_ZOOM
            )
        )
    }

    /**
     * Update the retention policy. `0` means "forever" (no automatic
     * deletion); any positive value is the number of days readings may
     * live before being pruned.
     *
     * Persists to SharedPreferences and immediately sweeps the database of
     * stale rows. The deletion count is emitted on [events] for the UI to
     * show in a snackbar. Calling with the same value is essentially a
     * re-sweep and produces another (possibly zero) snackbar.
     */
    fun setRetentionDays(days: Int) {
        val normalized = days.coerceAtLeast(0)
        prefs.retentionDays = normalized
        _ui.value = _ui.value.copy(retentionDays = normalized)
        if (normalized > 0) applyRetention(normalized, announce = true)
    }

    /**
     * Sweep readings older than [days] days. When [announce] is true the
     * deletion count is emitted on [events] for the UI to show in a
     * snackbar; when false (used at app start), the sweep is silent.
     */
    private fun applyRetention(days: Int, announce: Boolean) {
        val cutoff = System.currentTimeMillis() -
            days.toLong() * 24L * 60L * 60L * 1000L
        viewModelScope.launch(Dispatchers.IO) {
            val count = repo.deleteOlderThan(cutoff)
            if (announce && count > 0) {
                _events.trySend(
                    getApplication<Application>()
                        .getString(R.string.retention_purge_count, count)
                )
            }
        }
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
