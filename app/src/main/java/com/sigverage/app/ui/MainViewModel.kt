package com.sigverage.app.ui

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sigverage.app.R
import com.sigverage.app.cellular.CellularScanner
import com.sigverage.app.data.PreferencesStore
import com.sigverage.app.data.SignalRepository
import com.sigverage.app.location.FixSample
import com.sigverage.app.location.LocationTracker
import com.sigverage.app.model.NetworkType
import com.sigverage.app.model.SignalReading
import com.sigverage.app.service.SamplingService
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
import kotlinx.coroutines.withTimeoutOrNull

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
)

/**
 * Single ViewModel for the home screen. It owns:
 *
 *  - `readings` and `count` flows that mirror Room (unidirectional).
 *  - `ui` flow for transient state (sampling flag, latest fix, etc).
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SignalRepository.get(app)
    private val location = LocationTracker(app)
    private val cellular = CellularScanner(app)
    private val prefs = PreferencesStore(app)

    private val _events = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = _events.receiveAsFlow()

    private val _focusEvents = Channel<Pair<Double, Double>>(Channel.BUFFERED)
    val focusEvents: Flow<Pair<Double, Double>> = _focusEvents.receiveAsFlow()

    fun focusOnLocation(latitude: Double, longitude: Double) {
        _focusEvents.trySend(latitude to longitude)
    }

    fun captureNow() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val fix = location.currentFix()
            if (fix == null) {
                _events.trySend(app.getString(R.string.capture_no_location))
                return@launch
            }
            if (!fix.isAccurateEnough()) {
                _events.trySend(app.getString(R.string.capture_low_accuracy))
                return@launch
            }
            if (ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return@launch
            }
            val reading = cellular.snapshot(
                provider = fix.provider,
                latitude = fix.latitude,
                longitude = fix.longitude,
                accuracyMeters = fix.accuracyMeters,
            )
            repo.add(reading)
            _ui.value = _ui.value.copy(lastFix = fix, latestReading = reading)
            _events.trySend(app.getString(R.string.capture_snackbar, readings.value.size + 1))
        }
    }

    override fun onCleared() {
        cellular.cleanup()
    }

    val readings: StateFlow<List<SignalReading>> = repo.observeReadings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            var fix = location.lastKnown()
            if (fix == null) {
                fix = withTimeoutOrNull(10_000L) { location.currentFix() }
            }
            if (fix != null) {
                _ui.value = _ui.value.copy(lastFix = fix)
            }
        }
    }

    private val _undoDeleteEvents = Channel<SignalReading>(Channel.BUFFERED)
    val undoDeleteEvents: Flow<SignalReading> = _undoDeleteEvents.receiveAsFlow()

    fun deleteReading(reading: SignalReading) {
        viewModelScope.launch(Dispatchers.IO) { repo.delete(reading.id) }
        _undoDeleteEvents.trySend(reading)
    }

    fun restoreReading(reading: SignalReading) {
        viewModelScope.launch(Dispatchers.IO) { repo.add(reading) }
    }

    fun setSampling(active: Boolean) {
        _ui.value = _ui.value.copy(isSampling = active)
    }

    fun startSampling() {
        val app = getApplication<Application>()
        if (missingSamplingPermissions(app).isNotEmpty()) {
            _events.trySend(app.getString(R.string.auto_record_no_permissions))
            return
        }
        setSampling(active = true)
        SamplingService.start(app)
    }

    fun stopSampling() {
        setSampling(active = false)
        SamplingService.stop(getApplication<Application>())
    }

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

    fun toggleCoverageFilter(type: NetworkType) {
        _ui.value = _ui.value.let { current ->
            val next = current.coverageFilter.toMutableSet()
            if (!next.add(type)) next.remove(type)
            current.copy(coverageFilter = next)
        }
    }

    fun toggleOperatorFilter(operator: String) {
        _ui.value = _ui.value.let { current ->
            val next = current.operatorFilter.toMutableSet()
            if (!next.add(operator)) next.remove(operator)
            current.copy(operatorFilter = next)
        }
    }

    fun applyDefaultMapFilters() {
        _ui.value = _ui.value.copy(
            coverageFilter = prefs.defaultNetworkFilter,
            operatorFilter = prefs.defaultOperatorFilter
        )
    }
}
