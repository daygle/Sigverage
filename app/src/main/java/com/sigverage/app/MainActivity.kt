package com.sigorage.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.sigorage.app.ui.MainScreen
import com.sigorage.app.ui.MainViewModel
import com.sigorage.app.ui.OnboardingScreen
import com.sigorage.app.ui.theme.SigorageTheme

/**
 * Single-activity host. Everything UI lives inside this Activity under a
 * Compose root, sharing one ViewModel for state.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // Observe the ViewModel at the activity root so the theme
            // re-resolves whenever the user toggles the override in Settings.
            val ui by viewModel.ui.collectAsState()
            SigorageTheme(
                themeMode = ui.themeMode,
                dynamicColor = ui.dynamicColorEnabled,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // First-launch gate. Fresh installs (or users who have
                    // never reached the final onboarding step) land on
                    // the multi-step permission-onboarding screen;
                    // everyone else goes straight to the main app. The
                    // boolean is owned by `HomeUiState.onboardingCompleted`
                    // and is initialised from `PreferencesStore` in the
                    // ViewModel's `init` block, so this branch is
                    // stable across recompositions.
                    if (ui.onboardingCompleted) {
                        MainScreen(viewModel = viewModel)
                    } else {
                        OnboardingScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
