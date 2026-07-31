package com.sigverage.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.sigverage.app.ui.MainScreen
import com.sigverage.app.ui.MainViewModel
import com.sigverage.app.ui.OnboardingScreen
import com.sigverage.app.ui.SettingsViewModel
import com.sigverage.app.ui.theme.LocalNetworkColors
import com.sigverage.app.ui.theme.SigverageTheme

/**
 * Single-activity host. Everything UI lives inside this Activity under a
 * Compose root, sharing one ViewModel for state.
 */
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settingsUi by settingsViewModel.ui.collectAsState()

            SigverageTheme(
                themeMode = settingsUi.themeMode,
                dynamicColor = settingsUi.dynamicColorEnabled,
            ) {
                CompositionLocalProvider(LocalNetworkColors provides settingsUi.networkColors) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        if (settingsUi.onboardingCompleted) {
                            MainScreen(
                                mainViewModel = mainViewModel,
                                settingsViewModel = settingsViewModel
                            )
                        } else {
                            OnboardingScreen(
                                settingsViewModel = settingsViewModel
                            )
                        }
                    }
                }
            }
        }
    }
}
