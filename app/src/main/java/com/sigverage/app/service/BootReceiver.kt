package com.sigverage.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sigverage.app.data.PreferencesStore
import com.sigverage.app.data.SignalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Re-registers all enabled schedule alarms after the device reboots.
 * AlarmManager alarms do not survive reboots, so this receiver
 * listens for [android.content.Intent.ACTION_BOOT_COMPLETED] and
 * re-arms every active schedule.
 *
 * Additionally, if the user had auto-record enabled, it restarts the
 * foreground [SamplingService] and arms the liveness watchdog so
 * sampling resumes automatically after a reboot without the user
 * needing to open the app.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // (1) Re-register schedule alarms that were lost during reboot.
                val repo = SignalRepository.get(context)
                val schedules = repo.getEnabledSchedules()
                ScheduleManager.rescheduleAll(context, schedules)

                // (2) If the user had auto-record enabled, restart the
                //     sampling service and arm the watchdog so recording
                //     resumes without the user opening the app.
                val prefs = PreferencesStore(context)
                if (prefs.autoRecordEnabled) {
                    SamplingService.start(context)
                    WatchdogReceiver.scheduleNext(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
