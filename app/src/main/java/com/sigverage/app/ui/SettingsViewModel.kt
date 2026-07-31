package com.sigverage.app.ui

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sigverage.app.R
import com.sigverage.app.data.CsvManager
import com.sigverage.app.data.PreferencesStore
import com.sigverage.app.data.SignalRepository
import com.sigverage.app.model.DateFormat
import com.sigverage.app.model.NetworkType
import com.sigverage.app.model.RecordingSchedule
import com.sigverage.app.model.SamplingMode
import com.sigverage.app.model.ThemeMode
import com.sigverage.app.model.TimeFormat
import com.sigverage.app.service.ScheduleManager
import com.sigverage.app.ui.theme.NetworkColors
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

/**
 * ViewModel responsible for app settings, data management (import/export),
 * and recording schedules.
 */
/** UI state for settings, data management and onboarding. */
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.Default,
    val dynamicColorEnabled: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val autoRecordEnabled: Boolean = false,
    val samplingMode: SamplingMode = SamplingMode.Default,
    val timeFormat: TimeFormat = TimeFormat.System,
    val dateFormat: DateFormat = DateFormat.System,
    val retentionDays: Int = 0,
    val batteryLowThresholdPct: Int = 15,
    val skipBatteryThresholdWhenCharging: Boolean = true,
    val networkColors: Map<NetworkType, Color> = NetworkColors,
    val defaultNetworkFilter: Set<NetworkType> = NetworkType.entries.toSet(),
    val defaultOperatorFilter: Set<String> = emptySet(),
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesStore(application)
    private val repo = SignalRepository.get(application)
    private val csvManager = CsvManager(application)

    private val _events = Channel<String>(Channel.CONFLATED)
    val events: Flow<String> = _events.receiveAsFlow()

    private val _ui = MutableStateFlow(SettingsUiState())
    val ui: StateFlow<SettingsUiState> = _ui.asStateFlow()

    val schedules: StateFlow<List<RecordingSchedule>> = repo.observeSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        _ui.value = SettingsUiState(
            themeMode = prefs.themeMode,
            dynamicColorEnabled = prefs.dynamicColorEnabled,
            onboardingCompleted = prefs.onboardingCompleted,
            autoRecordEnabled = prefs.autoRecordEnabled,
            samplingMode = prefs.samplingMode,
            timeFormat = prefs.timeFormat,
            dateFormat = prefs.dateFormat,
            retentionDays = prefs.retentionDays,
            batteryLowThresholdPct = prefs.batteryLowThresholdPct,
            skipBatteryThresholdWhenCharging = prefs.skipBatteryThresholdWhenCharging,
            networkColors = resolvedNetworkColors(),
            defaultNetworkFilter = prefs.defaultNetworkFilter,
            defaultOperatorFilter = prefs.defaultOperatorFilter,
        )
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.themeMode = mode
        _ui.value = _ui.value.copy(themeMode = mode)
    }

    fun setTimeFormat(format: TimeFormat) {
        prefs.timeFormat = format
        _ui.value = _ui.value.copy(timeFormat = format)
    }

    fun setDateFormat(format: DateFormat) {
        prefs.dateFormat = format
        _ui.value = _ui.value.copy(dateFormat = format)
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        prefs.dynamicColorEnabled = enabled
        _ui.value = _ui.value.copy(dynamicColorEnabled = enabled)
    }

    private fun resolvedNetworkColors(): Map<NetworkType, Color> {
        val overrides = prefs.networkColorOverrides
        return NetworkType.entries.associateWith { type ->
            overrides[type]?.let { Color(it) } ?: (NetworkColors[type] ?: Color.Gray)
        }
    }

    fun setNetworkColor(type: NetworkType, color: Color) {
        prefs.setNetworkColor(type, color.toArgb())
        _ui.value = _ui.value.copy(networkColors = resolvedNetworkColors())
    }

    fun resetNetworkColor(type: NetworkType) {
        prefs.clearNetworkColor(type)
        _ui.value = _ui.value.copy(networkColors = resolvedNetworkColors())
    }

    fun resetAllNetworkColors() {
        prefs.clearAllNetworkColors()
        _ui.value = _ui.value.copy(networkColors = resolvedNetworkColors())
    }

    fun completeOnboarding() {
        prefs.onboardingCompleted = true
        _ui.value = _ui.value.copy(onboardingCompleted = true)
    }

    fun setAutoRecordEnabled(enabled: Boolean) {
        prefs.autoRecordEnabled = enabled
        _ui.value = _ui.value.copy(autoRecordEnabled = enabled)
    }

    fun setSamplingMode(mode: SamplingMode) {
        prefs.samplingMode = mode
        _ui.value = _ui.value.copy(samplingMode = mode)
    }

    fun setBatteryLowThreshold(pct: Int) {
        prefs.batteryLowThresholdPct = pct
        _ui.value = _ui.value.copy(batteryLowThresholdPct = pct)
    }

    fun setSkipBatteryThresholdWhenCharging(skip: Boolean) {
        prefs.skipBatteryThresholdWhenCharging = skip
        _ui.value = _ui.value.copy(skipBatteryThresholdWhenCharging = skip)
    }

    fun setRetentionDays(days: Int) {
        val normalized = days.coerceAtLeast(0)
        prefs.retentionDays = normalized
        _ui.value = _ui.value.copy(retentionDays = normalized)
        if (normalized > 0) applyRetention(normalized)
    }

    private fun applyRetention(days: Int) {
        val cutoff = System.currentTimeMillis() - (days.toLong() * 24L * 60L * 60L * 1000L)
        viewModelScope.launch(Dispatchers.IO) {
            val count = repo.deleteOlderThan(cutoff)
            if (count > 0) {
                val msg = getApplication<Application>().resources
                    .getQuantityString(R.plurals.retention_purge_count, count, count)
                _events.trySend(msg)
            }
        }
    }

    fun toggleDefaultNetwork(type: NetworkType) {
        val next = _ui.value.defaultNetworkFilter.toMutableSet()
        if (type in next) {
            if (next.size > 1) next.remove(type)
        } else {
            next.add(type)
        }
        prefs.defaultNetworkFilter = next
        _ui.value = _ui.value.copy(defaultNetworkFilter = next)
    }

    fun toggleDefaultOperator(operator: String) {
        val next = _ui.value.defaultOperatorFilter.toMutableSet()
        if (!next.add(operator)) next.remove(operator)
        prefs.defaultOperatorFilter = next
        _ui.value = _ui.value.copy(defaultOperatorFilter = next)
    }

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

    fun deleteAllReadings() {
        viewModelScope.launch(Dispatchers.IO) {
            repo.deleteAll()
        }
    }

    fun emitEvent(message: String) {
        _events.trySend(message)
    }

    suspend fun importCsv(uri: android.net.Uri): Int {
        val readings = csvManager.importCsv(uri)
        if (readings.isEmpty()) return 0
        repo.addAll(readings)
        return readings.size
    }

    suspend fun exportCsv(uri: android.net.Uri): Int {
        val readings = repo.allReadingsOnce()
        return csvManager.exportCsv(uri, readings)
    }
}
