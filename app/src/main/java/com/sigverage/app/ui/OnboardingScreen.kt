package com.sigverage.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sigverage.app.R

/**
 * First-launch onboarding screen.
 *
 * Renders a five-step carousel that walks the user through granting the
 * runtime permissions the app actually needs to record readings:
 *
 *   1. **Welcome** - explains the value of the app and what data it captures.
 *   2. **Location** - `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION`
 *      requested together (one system dialog).
 *   3. **Notifications** - `POST_NOTIFICATIONS`, only on Android 13+;
 *      skipped on older devices.
 *   4. **Activity Recognition** - `ACTIVITY_RECOGNITION`, only on Android 10+
 *      (API 29, where it became a runtime permission); skipped on older
 *      devices. Powers movement-based sampling, so it's requested up-front
 *      rather than left buried in Settings. It's optional: denying it does
 *      not block recording (the service degrades to continuous sampling), so
 *      a denial here does NOT flip the Done copy to the partial-setup nudge.
 *   5. **Done** - confirmation screen with a button to enter the main app.
 *      When the user has denied location or notifications the Done body is
 *      swapped for a "you can finish from Settings → Permissions" nudge so
 *      they aren't quietly left without recording capability.
 *
 * Background location (`ACCESS_BACKGROUND_LOCATION`) is intentionally NOT
 * part of this flow. Android UX guidance is to ask for foreground
 * permissions first, then send the user to system Settings for the
 * "Allow all the time" deep-link. The existing
 * `MainScreen.missingPermissions(...)` flow at FAB-time handles that step
 * once the user has reached the main app.
 *
 * A persistent **Skip** affordance lives at the top-right corner of every
 * step so the user can leave onboarding immediately. Skipping sets
 * [MainViewModel.onboardingCompleted] to `true`; the user lands on
 * `MainScreen` and can finish granting permissions from Settings
 * → Permissions or by starting sampling from the Map FAB.
 *
 * Step state is held via `rememberSaveable` so a device rotation keeps
 * the user on the same step they were on before. The launchers are
 * hoisted to this composable so we can capture the system dialog's
 * grant result and adjust the Done copy accordingly.
 */
@Composable
@SuppressLint("InlinedApi")
fun OnboardingScreen(viewModel: MainViewModel) {
    var step by rememberSaveable { mutableStateOf(OnboardingStep.Welcome) }
    // True once any permission was denied. Survives rotation. Drives the
    // copy on the Done page so users who skipped or denied aren't left
    // wondering why their first recording fails.
    var anyDenied by rememberSaveable { mutableStateOf(false) }

    val locationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // `grants` is a Map<String, Boolean> with one entry per requested
        // permission. Flag any denial but always advance - the user can
        // re-grant from Settings → Permissions.
        anyDenied = anyDenied || grants.values.any { !it }
        step = step.next()
    }

    val notificationsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        anyDenied = anyDenied || !granted
        step = step.next()
    }

    // Activity recognition is optional - the sampling service degrades to
    // continuous recording when it's missing - so a denial here does not
    // flip `anyDenied`; we simply advance to the Done page either way.
    val activityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        step = step.next()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TextButton(
            onClick = viewModel::completeOnboarding,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        ) {
            Text(stringResource(R.string.onboarding_skip))
        }

        when (step) {
            OnboardingStep.Welcome -> OnboardingPage(
                icon = Icons.Filled.Celebration,
                title = stringResource(R.string.onboarding_welcome_title),
                body = stringResource(R.string.onboarding_welcome_body),
                cta = stringResource(R.string.onboarding_get_started),
                onContinue = { step = step.next() },
            )
            OnboardingStep.Location -> OnboardingPage(
                icon = Icons.Filled.LocationOn,
                title = stringResource(R.string.onboarding_location_title),
                body = stringResource(R.string.onboarding_location_body),
                cta = stringResource(R.string.onboarding_location_grant),
                onContinue = {
                    locationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                },
            )
            OnboardingStep.Notifications -> OnboardingPage(
                icon = Icons.Filled.NotificationsActive,
                title = stringResource(R.string.onboarding_notifications_title),
                body = stringResource(R.string.onboarding_notifications_body),
                cta = stringResource(R.string.onboarding_notifications_grant),
                onContinue = {
                    notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
            )
            OnboardingStep.ActivityRecognition -> OnboardingPage(
                icon = Icons.Filled.Sensors,
                title = stringResource(R.string.onboarding_activity_title),
                body = stringResource(R.string.onboarding_activity_body),
                cta = stringResource(R.string.onboarding_activity_grant),
                onContinue = {
                    activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                },
            )
            OnboardingStep.Done -> OnboardingPage(
                icon = Icons.Filled.LocationOn,
                title = stringResource(R.string.onboarding_done_title),
                body = stringResource(
                    if (anyDenied) R.string.onboarding_done_body_partial
                    else R.string.onboarding_done_body
                ),
                cta = stringResource(R.string.onboarding_open_app),
                onContinue = viewModel::completeOnboarding,
            )
        }
    }
}

/**
 * Linear ordering of the onboarding steps. Held in an enum so the
 * `step.next()` transition is exhaustive and the type system catches
 * regressions if a new step is added.
 *
 * Notifications is skipped entirely on devices older than Android 13
 * (API 33) because the underlying permission is auto-granted there.
 * Activity Recognition is skipped on devices older than Android 10
 * (API 29), where it isn't a runtime permission.
 */
private enum class OnboardingStep {
    Welcome,
    Location,
    Notifications,
    ActivityRecognition,
    Done,
    ;

    fun next(): OnboardingStep = when (this) {
        Welcome -> Location
        Location -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Notifications
        } else {
            afterNotifications()
        }
        Notifications -> afterNotifications()
        ActivityRecognition -> Done
        Done -> Done
    }

    /**
     * The step that follows Notifications: Activity Recognition on Android
     * 10+ (where it's a runtime permission), otherwise straight to Done.
     */
    private fun afterNotifications(): OnboardingStep =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityRecognition
        } else {
            Done
        }
}

@Composable
private fun OnboardingPage(
    icon: ImageVector,
    title: String,
    body: String,
    cta: String,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(112.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(48.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        ) {
            Text(cta, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(24.dp))
    }
}
