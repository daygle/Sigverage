package com.signalspotter.app

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
import com.signalspotter.app.ui.MainScreen
import com.signalspotter.app.ui.MainViewModel
import com.signalspotter.app.ui.theme.SignalSpotterTheme

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
            SignalSpotterTheme(themeMode = ui.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}
