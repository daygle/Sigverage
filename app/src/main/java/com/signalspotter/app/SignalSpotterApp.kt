package com.signalspotter.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import org.osmdroid.config.Configuration

/**
 * Application entry that runs before any Activity, Service, or Compose root
 * is constructed.
 *
 *  Two things must be initialised here:
 *
 *  1. **osmdroid** — its global `Configuration` must be loaded with a
 *     SharedPreferences and a real `userAgentValue` BEFORE the first MapView is
 *     created, or OpenStreetMap will reject tile requests for using a generic
 *     "Mozilla/5.0" UA. We set a UA that includes the package and the version
 *     so the OSM tile server can reach us if needed.
 *
 *  2. **Notification channel** — `SamplingService` posts to a low-importance
 *     persistent channel on Android 8+; the channel must exist before the
 *     notification is posted.
 */
class SignalSpotterApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // (1) osmdroid must be initialised before any MapView.
        Configuration.getInstance().load(
            this,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )
        Configuration.getInstance().userAgentValue =
            "SignalSpotter/${BuildConfig.VERSION_NAME} (${packageName})"

        // (2) The foreground service channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_SAMPLING,
                getString(R.string.notif_channel_sampling),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_sampling_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_SAMPLING = "signal_spotter_sampling"
    }
}
