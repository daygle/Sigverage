package com.sigverage.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sigverage.app.model.RecordingSchedule
import java.util.Calendar

/**
 * Manages [AlarmManager] alarms for recording schedules.
 *
 * Each enabled schedule produces two alarms per cycle:
 *  - [ACTION_SCHEDULE_START] at the scheduled start time
 *  - [ACTION_SCHEDULE_STOP] at the scheduled end time
 *
 * After each alarm fires, [ScheduleReceiver] re-evaluates all active
 * schedules and re-registers the next pair of alarms, creating a
 * self-sustaining cycle.
 */
object ScheduleManager {

    const val ACTION_SCHEDULE_START = "com.sigverage.app.SCHEDULE_START"
    const val ACTION_SCHEDULE_STOP = "com.sigverage.app.SCHEDULE_STOP"
    const val EXTRA_SCHEDULE_ID = "extra_schedule_id"

    /**
     * Re-register alarms for every enabled schedule. Called on boot,
     * after the database migration, and whenever a schedule is
     * created/updated/deleted.
     */
    fun rescheduleAll(context: Context, schedules: List<RecordingSchedule>) {
        cancelAll(context)
        for (schedule in schedules) {
            if (!schedule.enabled) continue
            registerNextAlarm(context, schedule)
        }
    }

    /**
     * Cancel every pending alarm for the given schedule, then
     * re-register if it is enabled.
     */
    suspend fun rescheduleOne(context: Context, schedule: RecordingSchedule) {
        cancelOne(context, schedule)
        if (schedule.enabled) {
            registerNextAlarm(context, schedule)
        }
    }

    /** Cancel all pending schedule alarms. */
    fun cancelAll(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // We cancel a wide range of request codes to cover all possible alarms.
        for (requestCode in 0 until MAX_SCHEDULES) {
            val intent = Intent(context, ScheduleReceiver::class.java)
            val pi = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pi != null) am.cancel(pi)
        }
    }

    /** Cancel all pending alarms for one schedule. */
    fun cancelOne(context: Context, schedule: RecordingSchedule) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val startCode = scheduleRequestCode(schedule.id, isStart = true)
        val stopCode = scheduleRequestCode(schedule.id, isStart = false)

        val startIntent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ACTION_SCHEDULE_START
            putExtra(EXTRA_SCHEDULE_ID, schedule.id)
        }
        val stopIntent = Intent(context, ScheduleReceiver::class.java).apply {
            action = ACTION_SCHEDULE_STOP
            putExtra(EXTRA_SCHEDULE_ID, schedule.id)
        }
        PendingIntent.getBroadcast(
            context, startCode, startIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )?.let { am.cancel(it) }

        PendingIntent.getBroadcast(
            context, stopCode, stopIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )?.let { am.cancel(it) }
    }

    // ---- internal ----

    /**
     * Find the next occurrence of the schedule's start time and register it.
     * If the schedule is currently active (we are between start and end),
     * register the stop alarm instead.
     */
    private fun registerNextAlarm(context: Context, schedule: RecordingSchedule) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = Calendar.getInstance()

        val startCal = nextOccurrence(schedule, schedule.startHour, schedule.startMinute, now)
        val endCal = nextOccurrence(schedule, schedule.endHour, schedule.endMinute, now)

        val isActive = now in (startCal..endCal)
        val targetCal: Calendar
        val action: String
        val requestCode: Int

        if (isActive) {
            // Currently running - schedule the stop alarm.
            targetCal = endCal
            action = ACTION_SCHEDULE_STOP
            requestCode = scheduleRequestCode(schedule.id, isStart = false)
        } else {
            // Not running - schedule the start alarm.
            targetCal = startCal
            action = ACTION_SCHEDULE_START
            requestCode = scheduleRequestCode(schedule.id, isStart = true)
        }

        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_SCHEDULE_ID, schedule.id)
        }
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetCal.timeInMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetCal.timeInMillis, pi)
            }
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetCal.timeInMillis, pi)
        }
    }

    /**
     * Compute the next [Calendar] at which [hour]:[minute] falls on one
     * of the schedule's [RecordingSchedule.daysOfWeek], starting from [now].
     */
    private fun nextOccurrence(
        schedule: RecordingSchedule,
        hour: Int,
        minute: Int,
        now: Calendar,
    ): Calendar {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Try up to 8 days ahead (covers every day of the week + 1).
        for (offset in 0..7) {
            val candidate = (cal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
            }
            val isoDay = candidate[Calendar.DAY_OF_WEEK].toIsoDay()
            if (isoDay in schedule.daysOfWeek && candidate.after(now)) {
                return candidate
            }
        }

        // Fallback: next day at the requested time.
        return (cal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    /**
     * Stable request code derived from schedule id. Even ids map to start
     * alarms, odd ids map to stop alarms, preventing collisions.
     */
    private fun scheduleRequestCode(scheduleId: Long, isStart: Boolean): Int {
        return (scheduleId.toInt() * 2) + (if (isStart) 0 else 1)
    }

    private const val MAX_SCHEDULES = 200

    /**
     * Convert [Calendar.DAY_OF_WEEK] (1=Sun, 7=Sat) to ISO-8601
     * (1=Mon, 7=Sun).
     */
    private fun Int.toIsoDay(): Int = when (this) {
        Calendar.MONDAY -> 1
        Calendar.TUESDAY -> 2
        Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4
        Calendar.FRIDAY -> 5
        Calendar.SATURDAY -> 6
        Calendar.SUNDAY -> 7
        else -> this
    }
}
