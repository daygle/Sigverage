package com.sigverage.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sigverage.app.permissions.PERMISSIONS_INVENTORY
import com.sigverage.app.permissions.PermissionItem

/**
 * Snapshot of permission grant state at a moment in time, recomputed by
 * [takeSnapshot]. Implementation detail of [PermissionsSection] — kept
 * top-level so it's easy to test.
 *
 * `permanentlyDenied` covers both "first request never made" and
 * "user ticked don't ask again" — Android's API can't distinguish them
 * without a remembered per-permission flag, and we lean on the safe side
 * by routing both to app settings.
 */
private data class PermissionsSnapshot(
    val grants: Map<String, Boolean>,
    val permanentlyDenied: Set<String>,
) {
    val allRequiredGranted: Boolean
        get() = PERMISSIONS_INVENTORY.filter { it.required }.all { grants[it.permission] == true }
}

private fun takeSnapshot(context: Context, activity: Activity?): PermissionsSnapshot {
    val grants = PERMISSIONS_INVENTORY.associate { item ->
        item.permission to (ContextCompat.checkSelfPermission(
            context, item.permission
        ) == PackageManager.PERMISSION_GRANTED)
    }
    val permanently = PERMISSIONS_INVENTORY
        .filter { item ->
            item.runtime && grants[item.permission] == false && activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, item.permission)
        }
        .map { it.permission }.toSet()
    return PermissionsSnapshot(grants, permanently)
}

/**
 * Compose list of every [PermissionItem], with grant status, helpful
 * descriptions, and "Grant" / "Open settings" actions. Refreshes its
 * snapshot whenever the host activity hits ON_RESUME so coming back
 * from system app-settings shows fresh state.
 */
@Composable
fun PermissionsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    var snapshot by remember { mutableStateOf(takeSnapshot(context, activity)) }

    // Refresh on every ON_RESUME (covers coming back from system app-settings).
    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                snapshot = takeSnapshot(context, activity)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // One launcher for all runtime permission requests; the callback
    // refreshes the snapshot in-place.
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        snapshot = takeSnapshot(context, activity)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Status banner.
        Banner(
            isAllOk = snapshot.allRequiredGranted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        PERMISSIONS_INVENTORY.forEach { item ->
            HorizontalDivider()
            PermissionRow(
                item = item,
                isGranted = snapshot.grants[item.permission] == true,
                isPermanentlyDenied = item.permission in snapshot.permanentlyDenied,
                onRequest = {
                    launcher.launch(arrayOf(item.permission))
                },
                onOpenSettings = { openAppSettings(context) },
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun Banner(isAllOk: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = if (isAllOk) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                    shape = CircleShape,
                )
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = stringResource(
                if (isAllOk) R.string.perm_banner_all_ok
                else R.string.perm_banner_missing
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isAllOk) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun PermissionRow(
    item: PermissionItem,
    isGranted: Boolean,
    isPermanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    ListItem(
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = when {
                            isGranted -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        },
                        shape = CircleShape,
                    )
            )
        },
        headlineContent = {
            Text(
                text = stringResource(item.labelRes),
                style = MaterialTheme.typography.titleSmall,
            )
        },
        supportingContent = {
            Text(
                text = stringResource(item.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        },
        trailingContent = {
            when {
                isGranted -> Text(
                    text = stringResource(R.string.perm_status_granted),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                )
                !item.runtime -> Text(
                    text = stringResource(R.string.perm_status_install_time),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                isPermanentlyDenied && activity() != null -> TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.perm_action_open_settings))
                }
                else -> TextButton(onClick = onRequest) {
                    Text(stringResource(R.string.perm_action_grant))
                }
            }
        },
    )
}

/** Find the host activity from a Compose Context (no-op if no activity present). */
@Composable
private fun activity(): Activity? = LocalContext.current as? Activity

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

@Suppress("unused")
private val ignored: Color = Color.Unspecified // silences "unused import" warnings if Color is no longer used
