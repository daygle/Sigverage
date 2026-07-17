package com.sigverage.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = SignalRepository.get(context)
                val schedules = repo.getEnabledSchedules()
                ScheduleManager.rescheduleAll(context, schedules)
            } finally {
                pending.finish()
            }
        }
    }
}
