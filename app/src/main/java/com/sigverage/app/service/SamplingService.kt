package com.sigverage.app.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.sigverage.app.MainActivity
import com.sigverage.app.R
import com.sigverage.app.SigverageApp
import com.sigverage.app.cellular.CellularScanner
import com.sigverage.app.coverage.CoverageGridOverlay
import com.sigverage.app.coverage.TileId
import com.sigverage.app.coverage.latLngToTile
import com.sigverage.app.data.PreferencesStore
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
 * Foreground service that samples (location + cellular) only when the
 * device detects movement via the Activity Recognition Transition API.
 *
 * The service registers for STILL <-> MOVING transitions. When a
 * [TransitionReceiver] detects movement, it sends an intent with
 * [EXTRA_IS_MOVING] = true, and the service begins streaming location
 * updates. When the device becomes still, the receiver sends false and
 * the location stream is paused, saving battery.
 *
 * A second smart-sampling layer records only one reading per ~50 m
 * coverage cell while the device stays inside it; leaving and returning
 * to a cell records again, so revisits accumulate and are averaged on the
 * map. See [lastRecordedTile].
 *
 * On Android 14 (API 34) a typed foreground service must be promoted
 * within ~5 seconds of `startForegroundService(...)`. We promote on
 * the very first line of `onStartCommand`.
 */
class SamplingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var location: LocationTracker
    private lateinit var cellular: CellularScanner
    private lateinit var repo: SignalRepository
    private lateinit var prefs: PreferencesStore
    private lateinit var wakeLockManager: WakeLockManager
    private var locationJob: Job? = null
    private var transitionsRegistered = false

    private var lastRecordedTile: TileId? = null

    private val transitionPendingIntent: PendingIntent by lazy {
        val intent = TransitionReceiver.buildIntent(this)
        PendingIntent.getBroadcast(
            this,
            TRANSITION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun onCreate() {
        super.onCreate()
        location = LocationTracker(applicationContext)
        cellular = CellularScanner(applicationContext)
        repo = SignalRepository.get(applicationContext)
        prefs = PreferencesStore(applicationContext)
        wakeLockManager = WakeLockManager(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promoteToForeground()
        registerTransitions()

        val isMoving = intent?.getBooleanExtra(EXTRA_IS_MOVING, false) ?: false
        if (isMoving) {
            startSampling()
        } else {
            stopSampling()
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val restart = Intent(applicationContext, SamplingService::class.java)
        startForegroundService(restart)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        unregisterTransitions()
        stopSampling()
        cellular.cleanup()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startSampling() {
        if (locationJob?.isActive == true) return

        wakeLockManager.acquire()

        val mode = prefs.samplingMode
        locationJob = scope.launch {
            location.stream(mode).collectLatest { fix ->
                if (!fix.isAccurateEnough()) return@collectLatest

                val tile = latLngToTile(
                    fix.latitude, fix.longitude,
                    CoverageGridOverlay.DEFAULT_STORAGE_ZOOM
                )
                if (tile == lastRecordedTile) return@collectLatest

                if (ContextCompat.checkSelfPermission(this@SamplingService, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    return@collectLatest
                }

                val reading = cellular.snapshot(
                    provider = fix.provider,
                    latitude = fix.latitude,
                    longitude = fix.longitude,
                    accuracyMeters = fix.accuracyMeters
                )
                repo.add(reading)
                lastRecordedTile = tile
            }
        }
    }

    private fun stopSampling() {
        locationJob?.cancel()
        locationJob = null
        lastRecordedTile = null
        wakeLockManager.release()
    }

    private fun registerTransitions() {
        if (transitionsRegistered) return
        val activities = listOf(
            DetectedActivity.STILL,
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.IN_VEHICLE,
        )
        val transitions = mutableListOf<ActivityTransition>()
        for (activity in activities) {
            transitions += ActivityTransition.Builder()
                .setActivityType(activity)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build()
            transitions += ActivityTransition.Builder()
                .setActivityType(activity)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build()
        }
        val request = ActivityTransitionRequest(transitions)
        try {
            ActivityRecognition.getClient(this).requestActivityTransitionUpdates(
                request, transitionPendingIntent
            )
            transitionsRegistered = true
        } catch (_: SecurityException) {
        }
    }

    private fun unregisterTransitions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            try {
                ActivityRecognition.getClient(this).removeActivityTransitionUpdates(
                    transitionPendingIntent
                )
            } catch (_: Exception) {
            }
        }
        transitionsRegistered = false
    }

    private fun promoteToForeground() {
        val notification = buildNotification()
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
            .setContentText(resources.getQuantityString(R.plurals.notif_sampling_text, 0, 0))
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
        const val EXTRA_IS_MOVING = "extra_is_moving"
        const val TRANSITION_REQUEST_CODE = 42

        fun onTransition(context: Context, isMoving: Boolean) {
            val i = Intent(context, SamplingService::class.java)
                .putExtra(EXTRA_IS_MOVING, isMoving)
            context.startForegroundService(i)
        }

        fun start(context: Context) {
            PreferencesStore(context).recordingShouldBeRunning = true
            WatchdogReceiver.scheduleNext(context)
            val i = Intent(context, SamplingService::class.java)
                .putExtra(EXTRA_IS_MOVING, true)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            PreferencesStore(context).recordingShouldBeRunning = false
            WatchdogReceiver.cancel(context)
            context.stopService(Intent(context, SamplingService::class.java))
        }
    }
}
