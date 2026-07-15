package com.signalspotter.app.ui

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.signalspotter.app.BuildConfig

/**
 * Top-level Settings screen, reachable via the bottom NavigationBar tab.
 *
 * Sections today:
 *  - Permissions — hands off to [PermissionsSection]
 *  - About — app version + privacy blurb
 *
 * Future sections can be added without changing the navigation: drop a
 * new [Section] block, share the same screen.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(vertical = 12.dp)
    ) {
        Section(stringResource(R.string.settings_section_permissions)) {
            PermissionsSection()
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Spacer(Modifier.height(8.dp))

        Section(stringResource(R.string.settings_section_about)) {
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(
                            R.string.about_version_value,
                            BuildConfig.VERSION_NAME
                        )
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.about_blurb),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_android_version_label)) },
                supportingContent = {
                    Text(
                        text = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                },
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}
