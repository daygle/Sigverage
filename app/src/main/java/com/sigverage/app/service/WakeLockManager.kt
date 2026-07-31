package com.sigverage.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import com.sigverage.app.data.PreferencesStore

/**
 * Manages a partial wake lock to keep the CPU running during sampling bursts.
 * Automatically releases the lock if the battery level drops below the user's
 * configured threshold, unless the device is charging.
 */
class WakeLockManager(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val prefs = PreferencesStore(context)
    private var wakeLock: PowerManager.WakeLock? = null
    private var batteryReceiver: BroadcastReceiver? = null

    /**
     * Acquire a partial wake lock if the battery state allows it.
     */
    fun acquire() {
        if (wakeLock?.isHeld == true) return
        if (isBatteryLow()) return

        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Sigverage:SamplingWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 1000L) // 10-minute safety timeout
        }

        registerBatteryReceiver()
    }

    /**
     * Release the wake lock and stop monitoring battery state.
     */
    fun release() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
        unregisterBatteryReceiver()
    }

    private fun isBatteryLow(): Boolean {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, filter) ?: return false

        if (prefs.skipBatteryThresholdWhenCharging) {
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            if (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            ) {
                return false
            }
        }

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return false

        val percent = (level * 100) / scale
        return percent < prefs.batteryLowThresholdPct
    }

    private fun registerBatteryReceiver() {
        if (batteryReceiver != null) return
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (isBatteryLow()) {
                    release()
                }
            }
        }
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun unregisterBatteryReceiver() {
        batteryReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {
                // Already unregistered
            }
        }
        batteryReceiver = null
    }
}
