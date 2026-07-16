package com.sigverage.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.sigverage.app.MainActivity
import com.sigverage.app.R
import com.sigverage.app.SigverageApp
import com.sigverage.app.cellular.CellularScanner
import com.sigverage.app.data.SignalRepository
import com.sigverage.app.location.LocationTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service that periodically samples (location + cellular) and
 * writes each reading to Room.
 *
 * On Android 14 (API 34) a typed foreground service must be promoted within
 * ~5 seconds of `startForegroundService(...)`, otherwise Android will kill the
 * process with a ForegroundServiceDidNotStartInTimeException. We promote on
 * the very first line of `onStartCommand` via `ServiceCompat.startForeground`
 * with `FOREGROUND_SERVICE_TYPE_LOCATION`, which handles back-compat
 * automatically (the type becomes a no-op below API 29).
 */
class SamplingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var location: LocationTracker
    private lateinit var cellular: CellularScanner
    private lateinit var repo: SignalRepository
    private var job: Job? = null

    override fun onCreate() {
        super.onCreate()
        location = LocationTracker(applicationContext)
        cellular = CellularScanner(applicationContext)
        repo = SignalRepository.get(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // CRITICAL: call startForeground within 5 seconds of startForegroundService.
        promoteToForeground()
        if (job?.isActive == true) return START_STICKY

        val intervalMs = intent?.getLongExtra(EXTRA_INTERVAL_MS, DEFAULT_INTERVAL_MS)
            ?: DEFAULT_INTERVAL_MS

        job = scope.launch {
            location.stream(intervalMs).collectLatest { fix ->
                val reading = cellular.snapshot(
                    provider = fix.provider,
                    latitude = fix.latitude,
                    longitude = fix.longitude,
                    accuracyMeters = fix.accuracyMeters
                )
                repo.add(reading)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun promoteToForeground() {
        val notification = buildNotification()
        // ServiceCompat.startForeground handles API differences:
        //   - API 30+ passes the type to Service.startForeground(id, notif, type)
        //   - API < 29 ignores the type bit and behaves like the old overload.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            }
        )
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, SigverageApp.CHANNEL_SAMPLING)
            .setSmallIcon(R.drawable.ic_signal_notification)
            .setContentTitle(getString(R.string.notif_sampling_title))
            .setContentText(getString(R.string.notif_sampling_text, 0))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 7
        const val EXTRA_INTERVAL_MS = "extra_interval_ms"
        const val DEFAULT_INTERVAL_MS = 5_000L

        fun start(context: Context, intervalMs: Long = DEFAULT_INTERVAL_MS) {
            val i = Intent(context, SamplingService::class.java)
                .putExtra(EXTRA_INTERVAL_MS, intervalMs)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SamplingService::class.java))
        }
    }
}
