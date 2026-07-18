package com.sigverage.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sigverage.app.R
import com.sigverage.app.permissions.PERMISSIONS_INVENTORY
import com.sigverage.app.permissions.PermissionItem

/**
 * Snapshot of permission grant state at a moment in time, recomputed by
 * [takeSnapshot]. Implementation detail of [PermissionsSection] - kept
 * top-level so it's easy to test.
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
 * Modern card-based permissions section. Renders the permission list without
 * its own scroll container - the caller (currently the consolidated
 * Permissions & Access page) is expected to wrap this composable in a single
 * scrollable Column so it shares one scroll with sibling sections. Nesting
 * `verticalScroll` here would be a Compose runtime error.
 */
@Composable
fun PermissionsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val activity = context as? Activity
    @Suppress("DEPRECATION")
    val lifecycleOwner = LocalLifecycleOwner.current

    var snapshot by remember { mutableStateOf(takeSnapshot(context, activity)) }

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

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        snapshot = takeSnapshot(context, activity)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Status banner card
        StatusBanner(allGranted = snapshot.allRequiredGranted)

        // Permission cards
        PERMISSIONS_INVENTORY.forEach { item ->
            PermissionCard(
                item = item,
                isGranted = snapshot.grants[item.permission] == true,
                isPermanentlyDenied = item.permission in snapshot.permanentlyDenied,
                onRequest = { launcher.launch(arrayOf(item.permission)) },
                onOpenSettings = { openAppSettings(context) },
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun StatusBanner(allGranted: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (allGranted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            }
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (allGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (allGranted) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(
                    if (allGranted) R.string.perm_banner_all_ok
                    else R.string.perm_banner_missing
                ),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (allGranted) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun PermissionCard(
    item: PermissionItem,
    isGranted: Boolean,
    isPermanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header row: icon + title + status badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Permission icon
                Icon(
                    imageVector = permissionIcon(item.key),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (isGranted) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                // Title
                Text(
                    text = stringResource(item.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                // Status badge
                PermissionStatusBadge(
                    isGranted = isGranted,
                    isPermanentlyDenied = isPermanentlyDenied,
                    isRuntime = item.runtime,
                )
            }

            Spacer(Modifier.height(6.dp))

            // Description
            Text(
                text = stringResource(item.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp),
            )

            // Action button (only for runtime permissions that aren't granted)
            if (!isGranted && item.runtime) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (isPermanentlyDenied) {
                        OutlinedButton(
                            onClick = onOpenSettings,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(stringResource(R.string.perm_action_open_settings))
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onRequest,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) {
                            Text(stringResource(R.string.perm_action_grant))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusBadge(
    isGranted: Boolean,
    isPermanentlyDenied: Boolean,
    isRuntime: Boolean,
) {
    when {
        isGranted -> {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = stringResource(R.string.perm_status_granted),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        !isRuntime -> {
            Text(
                text = stringResource(R.string.perm_status_install_time),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        isPermanentlyDenied -> {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }
        else -> {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

private fun permissionIcon(key: String): ImageVector = when (key) {
    "fine_location", "coarse_location", "background_location" -> Icons.Default.LocationOn
    "post_notifications" -> Icons.Default.Notifications
    "activity_recognition" -> Icons.Default.Sensors
    "read_phone_state" -> Icons.Default.PhoneAndroid
    else -> Icons.Default.PhoneAndroid
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
