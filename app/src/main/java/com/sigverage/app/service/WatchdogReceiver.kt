package com.sigverage.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sigverage.app.data.PreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Periodic liveness watchdog that restarts the [SamplingService] if it was
 * killed by the system (OOM on low-RAM devices, OEM background-limit
 * enforcement, etc.) while the user expected recording to be running.
 *
 * The watchdog fires from an [AlarmManager] exact alarm every
 * [WATCHDOG_INTERVAL_MS] (15 minutes). On each tick it checks the persisted
 * [PreferencesStore.recordingShouldBeRunning] flag and, if true, calls
 * [SamplingService.start] to bring the foreground service back.
 *
 * The alarm is registered in [SamplingService.start] and cancelled in
 * [SamplingService.stop], so it only runs while the user explicitly wants
 * recording active.
 *
 * Even on aggressive-OEM devices the 15-minute heartbeat is well within
 * Android's minimum doze window, making this a reliable last-resort restart.
 */
class WatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prefs = PreferencesStore(context)
                if (prefs.recordingShouldBeRunning) {
                    SamplingService.start(context)
                }
                // Re-register the next watchdog tick only if recording should
                // still be running. If the user stopped recording between ticks
                // the alarm was already cancelled via SamplingService.stop().
                if (prefs.recordingShouldBeRunning) {
                    scheduleNext(context)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** 15 minutes in milliseconds — frequent enough to catch kills quickly,
         *  sparse enough to be battery-friendly. */
        const val WATCHDOG_INTERVAL_MS = 15 * 60 * 1000L

        private const val WATCHDOG_REQUEST_CODE = 9001

        /**
         * Register the next watchdog alarm. Safe to call multiple times;
         * each call reschedules the alarm so only one pending intent exists.
         */
        fun scheduleNext(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WatchdogReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                WATCHDOG_REQUEST_CODE,
                intent,
                PendingIntentFlags.updateCurrentImmutable(),
            )
            val triggerAt = System.currentTimeMillis() + WATCHDOG_INTERVAL_MS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        }

        /**
         * Cancel any pending watchdog alarm. Safe to call even if no alarm is
         * currently scheduled.
         */
        fun cancel(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WatchdogReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                WATCHDOG_REQUEST_CODE,
                intent,
                PendingIntentFlags.noCreateImmutable(),
            )
            if (pi != null) am.cancel(pi)
        }
    }
}
