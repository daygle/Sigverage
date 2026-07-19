package com.sigverage.app.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sigverage.app.R
import com.sigverage.app.data.PreferencesStore
import com.sigverage.app.data.SignalRepository
import com.sigverage.app.location.FixSample
import com.sigverage.app.location.LocationTracker
import com.sigverage.app.model.DateFormat
import com.sigverage.app.model.NetworkType
import com.sigverage.app.model.RecordingSchedule
import com.sigverage.app.model.SamplingMode
import com.sigverage.app.model.SignalReading
import com.sigverage.app.model.ThemeMode
import com.sigverage.app.model.TimeFormat
import com.sigverage.app.service.SamplingService
import com.sigverage.app.service.ScheduleManager
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
    /**
     * Networks currently displayed by the coverage grid. This is the *active*
     * (possibly temporarily overridden) filter; it is reset to
     * [defaultNetworkFilter] every time the Map tab is opened.
     */
    val coverageFilter: Set<NetworkType> = NetworkType.entries.toSet(),
    /**
     * Operators currently displayed by the coverage grid. Empty = show all.
     * Active filter; reset to [defaultOperatorFilter] on each Map open.
     */
    val operatorFilter: Set<String> = emptySet(),
    /**
     * Persisted default networks the map loads with (configured in Settings).
     * On-map chip toggles change [coverageFilter] only, not this.
     */
    val defaultNetworkFilter: Set<NetworkType> = NetworkType.entries.toSet(),
    /**
     * Persisted default operators the map loads with (configured in Settings).
     * Empty = all operators. On-map chip toggles change [operatorFilter] only.
     */
    val defaultOperatorFilter: Set<String> = emptySet(),
    /** Retention in days; `0` means "forever" (the default - opt-in expiry). */
    val retentionDays: Int = PreferencesStore.DEFAULT_RETENTION_DAYS,
    /** Light/dark theme override (default: follow OS via [ThemeMode.System]). */
    val themeMode: ThemeMode = ThemeMode.Default,
    /**
     * Material You palette opt-in. Drives the `dynamicColor` argument of
     * `SigverageTheme` at the activity root. No-op on Android <12;
     * see [com.sigverage.app.ui.theme.SigverageTheme] for the
     * dynamic-vs-static palette resolution.
     */
    val dynamicColorEnabled: Boolean = PreferencesStore.DEFAULT_DYNAMIC_COLOR_ENABLED,
    /**
     * Whether the first-launch permission-onboarding screen has been
     * completed (or skipped). Defaults to `false` so a fresh install starts
     * on the onboarding screen instead of dropping the user straight into
     * a Map tab that immediately fails to record.
     */
    val onboardingCompleted: Boolean = PreferencesStore.DEFAULT_ONBOARDING_COMPLETED,
    /**
     * Whether the foreground sampling service should be started
     * automatically when `MainScreen` enters composition (i.e. once
     * onboarding has finished). Defaults to `false` because this is an
     * opt-in power-user feature: it posts a persistent notification and
     * keeps the GPS radio hot until the user explicitly pauses recording.
     *
     * The actual start is performed by the `LaunchedEffect` inside
     * `MainScreen`; this flag is only the persisted bit. Surfacing it on
     * `HomeUiState` keeps the Settings switch in lock-step with what the
     * UI will do on the next composition.
     */
    val autoRecordEnabled: Boolean = PreferencesStore.DEFAULT_AUTO_RECORD_ENABLED,
    /**
     * Location sampling mode: the battery-vs-accuracy trade-off applied while
     * recording. Consumed by the foreground service; surfaced here so the
     * Settings row reflects the persisted choice.
     */
    val samplingMode: SamplingMode = SamplingMode.Default,
    /** Preferred time format for UI display. */
    val timeFormat: TimeFormat = TimeFormat.System,
    /** Preferred date format for UI display. */
    val dateFormat: DateFormat = DateFormat.System,
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

    /**
     * One-shot "centre the map on this coordinate" requests, fired when the
     * user taps a row's "Show on map" affordance on the list page or
     * details sheet. Modeled as a Channel (not a StateFlow) so identical
     * consecutive locations - e.g. tapping the same row twice - still trigger
     * a fresh `animateTo`. Buffered so a request fired before the Map tab is
     * composed (the user is on List at the time) gets picked up the moment
     * MapPanel enters composition.
     */
    private val _focusEvents = Channel<Pair<Double, Double>>(Channel.BUFFERED)
    val focusEvents: Flow<Pair<Double, Double>> = _focusEvents.receiveAsFlow()

    /**
     * Request the map panel to animate to (lat, lng). Safe to call from any
     * thread; the Channel API is thread-safe and buffers if MapPanel isn't
     * currently collecting.
     */
    fun focusOnLocation(latitude: Double, longitude: Double) {
        _focusEvents.trySend(latitude to longitude)
    }

    /**
     * Emit a one-shot UI event (e.g. snackbar message) from any screen.
     * The event is consumed by MainScreen's `LaunchedEffect` collector.
     */
    fun emitEvent(message: String) {
        _events.trySend(message)
    }

    val readings: StateFlow<List<SignalReading>> = repo.observeReadings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val schedules: StateFlow<List<RecordingSchedule>> = repo.observeSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    init {
        // Apply retention sweep silently on startup. The user already
        // accepted the policy by leaving it active; no need to spam them
        // with a snackbar mentioning how many rows got cleaned out.
        val initialRetention = prefs.retentionDays
        _ui.value = _ui.value.copy(retentionDays = initialRetention)
        if (initialRetention > 0) applyRetention(initialRetention, announce = false)

        // Load the persisted theme + dynamic-colour preference so the very
        // first frame is already drawn in the right palette - no flash of
        // light → dark, material-you → static.
        _ui.value = _ui.value.copy(
            themeMode = prefs.themeMode,
            dynamicColorEnabled = prefs.dynamicColorEnabled,
            // First-launch onboarding gate. Defaults to false so a fresh
            // install starts on the onboarding screen; flips to true after
            // the user reaches the final step or taps Skip.
            onboardingCompleted = prefs.onboardingCompleted,
            // Auto-record opt-in. Default false; only flips true if the
            // user has explicitly toggled the Settings switch.
            autoRecordEnabled = prefs.autoRecordEnabled,
            // Battery-vs-accuracy sampling mode. Default Auto.
            samplingMode = prefs.samplingMode,
            // Date and time formats.
            timeFormat = prefs.timeFormat,
            dateFormat = prefs.dateFormat,
            // Persisted default map filters, plus the active filters seeded
            // from them so the very first Map open is already filtered.
            defaultNetworkFilter = prefs.defaultNetworkFilter,
            defaultOperatorFilter = prefs.defaultOperatorFilter,
            coverageFilter = prefs.defaultNetworkFilter,
            operatorFilter = prefs.defaultOperatorFilter,
        )

        viewModelScope.launch(Dispatchers.IO) {
            _ui.value = _ui.value.copy(lastFix = location.lastKnown())
        }
    }

    /**
     * One-shot "reading deleted" events carrying the removed row so the UI can
     * offer an Undo. Kept separate from [events] because these need a richer
     * payload (the full reading) and an action-bearing snackbar, not a plain
     * message. Buffered and consumed exactly once, like [events].
     */
    private val _undoDeleteEvents = Channel<SignalReading>(Channel.BUFFERED)
    val undoDeleteEvents: Flow<SignalReading> = _undoDeleteEvents.receiveAsFlow()

    /**
     * Delete [reading] and surface an undoable event. The full row is carried
     * on [undoDeleteEvents] so [restoreReading] can re-insert it verbatim -
     * including its original id, which is free again once the row is removed.
     */
    fun deleteReading(reading: SignalReading) {
        viewModelScope.launch(Dispatchers.IO) { repo.delete(reading.id) }
        _undoDeleteEvents.trySend(reading)
    }

    /** Re-insert a previously deleted [reading] (Undo of [deleteReading]). */
    fun restoreReading(reading: SignalReading) {
        viewModelScope.launch(Dispatchers.IO) { repo.add(reading) }
    }

    fun deleteAll() {
        viewModelScope.launch(Dispatchers.IO) { repo.deleteAll() }
    }

    fun setSampling(active: Boolean) {
        _ui.value = _ui.value.copy(isSampling = active)
    }

    /**
     * Start recording on demand (e.g. from the Settings recording toggle).
     *
     * Verifies the runtime permissions the foreground sampling service needs
     * before starting; if any are missing we surface a snackbar via [events]
     * and leave [HomeUiState.isSampling] false, so the UI reflects that no
     * recording could start rather than implying one that can't run.
     */
    fun startSampling() {
        val app = getApplication<Application>()
        if (missingSamplingPermissions(app).isNotEmpty()) {
            _events.trySend(app.getString(R.string.auto_record_no_permissions))
            return
        }
        setSampling(active = true)
        SamplingService.start(app)
    }

    /** Stop recording on demand (mirror of [startSampling]). */
    fun stopSampling() {
        setSampling(active = false)
        SamplingService.stop(getApplication<Application>())
    }

    /**
     * The runtime permissions the foreground sampling service needs that are
     * not currently granted. Empty means sampling can start. Shared by the
     * auto-record path and the manual Settings recording toggle so both gate
     * recording on exactly the same set of grants.
     */
    fun missingSamplingPermissions(context: Context = getApplication<Application>()): List<String> {
        val missing = mutableListOf<String>()
        val fine = Manifest.permission.ACCESS_FINE_LOCATION
        if (ContextCompat.checkSelfPermission(context, fine) != PackageManager.PERMISSION_GRANTED) {
            missing += fine
        }
        val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
        if (ContextCompat.checkSelfPermission(context, coarse) != PackageManager.PERMISSION_GRANTED) {
            missing += coarse
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notif = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(context, notif) != PackageManager.PERMISSION_GRANTED) {
                missing += notif
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bg = Manifest.permission.ACCESS_BACKGROUND_LOCATION
            if (ContextCompat.checkSelfPermission(context, bg) != PackageManager.PERMISSION_GRANTED) {
                missing += bg
            }
        }
        return missing
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
     * Toggle inclusion of [operator] in the visible coverage grid.
     *
     * An empty operator filter set shows all operators. When operators
     * are selected, only tiles with readings from those operators are
     * shown on the map.
     */
    fun toggleOperatorFilter(operator: String) {
        _ui.value = _ui.value.let { current ->
            val next = current.operatorFilter.toMutableSet()
            if (!next.add(operator)) next.remove(operator)
            current.copy(operatorFilter = next)
        }
    }

    /**
     * Reset the *active* map filters to the user's persisted defaults. Called
     * whenever the Map tab is opened, so any temporary on-map chip overrides
     * from the previous visit are discarded in favour of the Settings default.
     */
    fun applyDefaultMapFilters() {
        _ui.value = _ui.value.let { current ->
            current.copy(
                coverageFilter = current.defaultNetworkFilter,
                operatorFilter = current.defaultOperatorFilter,
            )
        }
    }

    /**
     * Toggle [type] in the persisted *default* network filter (Settings). The
     * last enabled network cannot be removed - a default of "no networks"
     * would blank the map on every open - so a no-op toggle-off is ignored.
     */
    fun toggleDefaultNetwork(type: NetworkType) {
        _ui.value = _ui.value.let { current ->
            val next = current.defaultNetworkFilter.toMutableSet()
            if (type in next) {
                if (next.size == 1) return@let current // keep at least one
                next.remove(type)
            } else {
                next.add(type)
            }
            prefs.defaultNetworkFilter = next
            current.copy(defaultNetworkFilter = next)
        }
    }

    /**
     * Toggle [operator] in the persisted *default* operator filter (Settings).
     * An empty set means "all operators".
     */
    fun toggleDefaultOperator(operator: String) {
        _ui.value = _ui.value.let { current ->
            val next = current.defaultOperatorFilter.toMutableSet()
            if (!next.add(operator)) next.remove(operator)
            prefs.defaultOperatorFilter = next
            current.copy(defaultOperatorFilter = next)
        }
    }

    /**
     * Update the user's theme override and re-emit it on [ui]. The activity
     * root `SigverageTheme` observes [ui] and swaps colour schemes
     * accordingly. Persisted via the existing [PreferencesStore] so the
     * choice survives app restarts.
     */
    fun setThemeMode(mode: ThemeMode) {
        prefs.themeMode = mode
        _ui.value = _ui.value.copy(themeMode = mode)
    }

    /** Update the user's preferred time format and re-emit. */
    fun setTimeFormat(format: TimeFormat) {
        prefs.timeFormat = format
        _ui.value = _ui.value.copy(timeFormat = format)
    }

    /** Update the user's preferred date format and re-emit. */
    fun setDateFormat(format: DateFormat) {
        prefs.dateFormat = format
        _ui.value = _ui.value.copy(dateFormat = format)
    }

    // ---- Schedule operations ----

    fun saveSchedule(schedule: RecordingSchedule) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = repo.upsertSchedule(schedule)
            val updated = schedule.copy(id = id)
            ScheduleManager.rescheduleOne(getApplication(), updated)
            _events.trySend(getApplication<Application>().getString(R.string.schedule_saved))
        }
    }

    fun deleteSchedule(schedule: RecordingSchedule) {
        viewModelScope.launch(Dispatchers.IO) {
            ScheduleManager.cancelOne(getApplication(), schedule)
            repo.deleteSchedule(schedule.id)
            _events.trySend(getApplication<Application>().getString(R.string.schedule_deleted))
        }
    }

    fun toggleScheduleEnabled(schedule: RecordingSchedule) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = schedule.copy(enabled = !schedule.enabled)
            repo.upsertSchedule(updated)
            ScheduleManager.rescheduleOne(getApplication(), updated)
        }
    }

    /**
     * Toggle the Material You (Android 12+) dynamic palette. On devices
     * older than Android 12 this preference has no effect - the static
     * slate/sky palette is always used - but the flag is still read so a
     * future upgrade "just works".
     */
    fun setDynamicColorEnabled(enabled: Boolean) {
        prefs.dynamicColorEnabled = enabled
        _ui.value = _ui.value.copy(dynamicColorEnabled = enabled)
    }

    /**
     * Mark the first-launch permission-onboarding screen as completed.
     * Called when the user finishes the onboarding flow or taps Skip on
     * any of its steps. Persists to SharedPreferences so the app lands on
     * `MainScreen` instead of the onboarding screen on every subsequent
     * launch.
     */
    fun completeOnboarding() {
        prefs.onboardingCompleted = true
        _ui.value = _ui.value.copy(onboardingCompleted = true)
    }

    /**
     * Update the "auto-record on launch" preference. The actual service
     * start happens inside `MainScreen`'s `LaunchedEffect` so the same
     * path is used whether the user toggled the switch from Settings or
     * simply opened the app with the preference already enabled.
     *
     * Toggling the switch off does NOT stop an in-progress foreground
     * sampling session - "auto-record" only governs what happens on the
     * next app launch. Manual pause stays the explicit way to stop a
     * running session, matching the AppBar Play/Pause button as the
     * single source of truth.
     */
    fun setAutoRecordEnabled(enabled: Boolean) {
        prefs.autoRecordEnabled = enabled
        _ui.value = _ui.value.copy(autoRecordEnabled = enabled)
    }

    /**
     * Update the location sampling mode (battery-vs-accuracy trade-off).
     * Persisted immediately; the running foreground service picks up the new
     * mode the next time it (re)starts the location stream on a still ->
     * moving transition, so an in-progress burst isn't interrupted.
     */
    fun setSamplingMode(mode: SamplingMode) {
        prefs.samplingMode = mode
        _ui.value = _ui.value.copy(samplingMode = mode)
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
            (days.toLong() * 24L * 60L * 60L * 1000L)
        viewModelScope.launch(Dispatchers.IO) {
            val count = repo.deleteOlderThan(cutoff)
            if (announce && count > 0) {
                _events.trySend(
                    getApplication<Application>().resources
                        .getQuantityString(R.plurals.retention_purge_count, count, count)
                )
            }
        }
    }

    /**
     * Write every reading to a CSV file at [destination] (a Uri supplied by
     * the Storage Access Framework `ACTION_CREATE_DOCUMENT` flow). Returns the
     * number of rows written or `-1` on failure. Designed to be `await`ed from
     * a coroutine - internally hops to [Dispatchers.IO].
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

    /**
     * RFC-4180 quoting (quote fields with comma/quote/newline, double inner
     * quotes) plus spreadsheet formula-injection defence: a field beginning
     * with a formula trigger (`=`, `+`, `-`, `@`, tab or CR) is prefixed with a
     * single quote so Excel/Sheets treat it as text rather than executing it.
     * The only free-text field exported is the network operator name, which is
     * normally harmless but is ultimately attacker-influenceable (a rogue base
     * station can advertise an arbitrary operator name).
     */
    private fun csvEscape(s: String): String {
        val guarded = if (s.isNotEmpty() && s.first() in FORMULA_TRIGGERS) "'$s" else s
        val needsQuote = guarded.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = guarded.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }

    private companion object {
        /** Leading characters that make a spreadsheet interpret a cell as a formula. */
        private const val FORMULA_TRIGGERS = "=+-@\t\r"
    }
}
