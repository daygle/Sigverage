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
 * BroadcastReceiver that fires when an alarm set by [ScheduleManager]
 * triggers. It starts or stops the [SamplingService] depending on the
 * action, then re-registers the next alarm for the affected schedule.
 */
class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getLongExtra(ScheduleManager.EXTRA_SCHEDULE_ID, -1)
        if (scheduleId == -1L) return

        when (intent.action) {
            ScheduleManager.ACTION_SCHEDULE_START -> {
                SamplingService.start(context)
            }
            ScheduleManager.ACTION_SCHEDULE_STOP -> {
                SamplingService.stop(context)
            }
        }

        // Re-register the next alarm for this schedule.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = SignalRepository.get(context)
                repo.getScheduleById(scheduleId)?.let { schedule ->
                    ScheduleManager.rescheduleOne(context, schedule)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
