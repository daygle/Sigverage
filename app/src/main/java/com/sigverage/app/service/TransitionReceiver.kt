package com.sigverage.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * Receives Activity Recognition transition events and forwards them to
 * [SamplingService] to start or stop movement-based sampling.
 *
 * When the device transitions from STILL to any active state (walking,
 * running, cycling, vehicle), we tell the service that movement has begun.
 * When it transitions back to STILL, we tell the service to pause.
 */
class TransitionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return

        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val movingActivities = setOf(
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.IN_VEHICLE,
        )

        for (event in result.transitionEvents) {
            if (event.activityType !in movingActivities) continue

            val isMoving = when (event.transitionType) {
                ActivityTransition.ACTIVITY_TRANSITION_ENTER -> true
                ActivityTransition.ACTIVITY_TRANSITION_EXIT -> false
                else -> continue
            }

            // Forward to SamplingService via intent.
            SamplingService.onTransition(context, isMoving)
        }
    }

    companion object {
        fun buildIntent(context: Context): Intent {
            return Intent(context, TransitionReceiver::class.java)
        }
    }
}
