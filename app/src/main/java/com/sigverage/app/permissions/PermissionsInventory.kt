package com.sigverage.app.permissions

import android.Manifest
import android.annotation.SuppressLint
import com.sigverage.app.R

/**
 * One logical permission item shown in the Settings → Permissions screen.
 *
 * `runtime = true` means the user must grant this via the runtime permission
 * dialog (or via app-settings if they've previously ticked "Don't ask again").
 * `runtime = false` is an install-time permission (always granted, listed
 * here only for transparency).
 *
 * `required = true` means the app cannot function without this permission;
 * missing required permissions show a red banner above the list.
 */
data class PermissionItem(
    val key: String,
    val labelRes: Int,
    val descriptionRes: Int,
    val permission: String,
    val runtime: Boolean,
    val required: Boolean = true,
)

/**
 * Single source of truth for both the manifest declaration and the
 * per-row UI display in the Settings screen. Adding/removing a permission
 * here updates both at once (the manifest is hand-maintained alongside).
 */
@SuppressLint("InlinedApi")
val PERMISSIONS_INVENTORY: List<PermissionItem> = listOf(
    PermissionItem(
        key = "fine_location",
        labelRes = R.string.perm_fine_location_label,
        descriptionRes = R.string.perm_fine_location_desc,
        permission = Manifest.permission.ACCESS_FINE_LOCATION,
        runtime = true,
        required = true,
    ),
    PermissionItem(
        key = "coarse_location",
        labelRes = R.string.perm_coarse_location_label,
        descriptionRes = R.string.perm_coarse_location_desc,
        permission = Manifest.permission.ACCESS_COARSE_LOCATION,
        runtime = true,
        required = true,
    ),
    PermissionItem(
        key = "background_location",
        labelRes = R.string.perm_background_location_label,
        descriptionRes = R.string.perm_background_location_desc,
        permission = Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        runtime = true,
        required = false,
    ),
    PermissionItem(
        key = "post_notifications",
        labelRes = R.string.perm_post_notifications_label,
        descriptionRes = R.string.perm_post_notifications_desc,
        permission = Manifest.permission.POST_NOTIFICATIONS,
        runtime = true,
        required = false,
    ),
    PermissionItem(
        key = "activity_recognition",
        labelRes = R.string.perm_activity_recognition_label,
        descriptionRes = R.string.perm_activity_recognition_desc,
        permission = Manifest.permission.ACTIVITY_RECOGNITION,
        runtime = true,
        required = false,
    ),
    PermissionItem(
        key = "read_phone_state",
        labelRes = R.string.perm_phone_state_label,
        descriptionRes = R.string.perm_phone_state_desc,
        permission = Manifest.permission.READ_PHONE_STATE,
        runtime = false,
        required = false,
    ),
)
