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
    private var locationJob: Job? = null
    private var transitionsRegistered = false

    /**
     * Coverage tile of the most recent reading we recorded, tracked in memory
     * for the "leave and return" smart-sampling rule. While the device stays
     * inside one cell we record a single reading; once a fix lands in a
     * *different* tile this is updated, so re-entering the original cell later
     * records again (and those readings are averaged together on the map).
     *
     * Deliberately not a database lookup: a persisted cell would be skipped
     * forever, which is why revisiting a location never produced a second
     * reading. Reset when sampling stops so each moving burst starts clean.
     */
    private var lastRecordedTile: TileId? = null

    /**
     * Partial wake lock held during active sampling bursts so the CPU
     * doesn't sleep mid-recording when the screen is off. Released when
     * the device becomes still, the battery drops below
     * [BATTERY_LOW_THRESHOLD_PCT], or the service is destroyed.
     */
    private var wakeLock: PowerManager.WakeLock? = null

    /**
     * Dynamically-registered receiver that monitors battery level during an
     * active sampling burst. If the level drops below [BATTERY_LOW_THRESHOLD_PCT]
     * mid-burst we release the wake lock to preserve the remaining charge.
     * Registered in [startSampling], unregistered in [stopSampling].
     */
    private var batteryReceiver: BroadcastReceiver? = null

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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // CRITICAL: call startForeground within 5 seconds of startForegroundService.
        promoteToForeground()

        // Register for activity transitions (STILL <-> MOVING).
        registerTransitions()

        // Handle transition intent from TransitionReceiver.
        val isMoving = intent?.getBooleanExtra(EXTRA_IS_MOVING, false) ?: false
        if (isMoving) {
            startSampling()
        } else {
            stopSampling()
        }

        return START_STICKY
    }

    /**
     * Called by the system when the last client unbinds and the user removes
     * the task (swipes the app from recents).
     *
     * We restart the service immediately via [startForegroundService] so
     * sampling continues even when the user clears the app from recents —
     * this is expected behaviour for a background service the user
     * deliberately enabled.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        // Restart the service. onStartCommand will call promoteToForeground
        // and registerTransitions, so the service comes back armed and ready
        // for the next activity transition.
        val restart = Intent(applicationContext, SamplingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restart)
        } else {
            startService(restart)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        unregisterTransitions()
        stopSampling()
        unregisterBatteryReceiver()  // safety-net: ensure receiver is unregistered
        releaseWakeLock()  // safety-net: ensure lock is cleared even on unexpected destroy
        cellular.cleanup()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startSampling() {
        if (locationJob?.isActive == true) return

        // Acquire a partial wake lock so the CPU stays on during the sampling
        // burst even when the screen is off — but only if the battery is above
        // the low threshold. We release it in stopSampling().
        acquireWakeLock()

        // Register a battery-level monitor so we can release the wake lock if
        // the device drains below the threshold mid-burst.
        registerBatteryReceiver()

        // Read the user's battery-vs-accuracy mode at start time so a change
        // in Settings takes effect on the next still -> moving transition.
        val mode = prefs.samplingMode
        locationJob = scope.launch {
            // Stream location fixes at the mode's cadence while moving.
            location.stream(mode).collectLatest { fix ->
                // Quality gate: drop coarse fixes that would be binned into the
                // wrong tile. Another fix will arrive shortly while moving.
                if (!fix.isAccurateEnough()) return@collectLatest

                // Smart sampling: while we stay inside one ~50 m cell (zoom 20)
                // record a single reading. Leaving for another cell and coming
                // back records again, so revisits accumulate and get averaged.
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
        // Forget the current cell: becoming still ends this pass, so the next
        // moving burst should record wherever it resumes, even the same cell.
        lastRecordedTile = null
        // Release the wake lock now that the sampling burst is done, and stop
        // listening for battery-level changes.
        unregisterBatteryReceiver()
        releaseWakeLock()
    }

    private fun registerTransitions() {
        // onStartCommand runs on every movement transition; only arm the
        // Activity Recognition request once to avoid needless re-registration.
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
            // ACTIVITY_RECOGNITION not granted - degrade gracefully.
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
                // Best-effort cleanup.
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

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        if (isBatteryLow()) return  // preserve remaining charge
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Sigverage:SamplingWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L) // 10-minute timeout as safety net
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    /**
     * Returns `true` when the device battery is critically low and we should
     * avoid holding a wake lock to preserve the remaining charge.
     *
     * If [PreferencesStore.skipBatteryThresholdWhenCharging] is enabled and
     * the device is plugged in, always returns `false` — battery preservation
     * doesn't matter when on external power.
     *
     * Uses a sticky [Intent.ACTION_BATTERY_CHANGED] broadcast — it is always
     * available without registering a receiver.
     */
    private fun isBatteryLow(): Boolean {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        // If the user opted to skip the threshold while charging and the device
        // is plugged in, pretend the battery is never low.
        if (prefs.skipBatteryThresholdWhenCharging) {
            val status = intent?.getIntExtra("status", BatteryManager.BATTERY_STATUS_UNKNOWN)
                ?: BatteryManager.BATTERY_STATUS_UNKNOWN
            if (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            ) {
                return false
            }
        }

        val level = intent?.getIntExtra("level", -1) ?: -1
        val scale = intent?.getIntExtra("scale", 100) ?: 100
        if (level < 0 || scale <= 0) return false // can't determine — be permissive
        return (level * 100 / scale) < prefs.batteryLowThresholdPct
    }

    /**
     * Registers a dynamic [BroadcastReceiver] for [Intent.ACTION_BATTERY_CHANGED]
     * so we can release the wake lock mid-burst if the device drains below
     * [BATTERY_LOW_THRESHOLD_PCT]. Unregistered by [unregisterBatteryReceiver].
     */
    private fun registerBatteryReceiver() {
        if (batteryReceiver != null) return
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (isBatteryLow() && wakeLock?.isHeld == true) {
                    releaseWakeLock()
                }
            }
        }
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun unregisterBatteryReceiver() {
        batteryReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) { /* already gone */ }
        }
        batteryReceiver = null
    }

    companion object {
        const val NOTIFICATION_ID = 7
        const val EXTRA_IS_MOVING = "extra_is_moving"
        const val TRANSITION_REQUEST_CODE = 42

        /**
         * Called by [TransitionReceiver] when the device transitions
         * between STILL and MOVING states. Forwards the state to the
         * running service via an intent extra.
         */
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
