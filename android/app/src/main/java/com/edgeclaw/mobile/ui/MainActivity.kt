package com.edgeclaw.mobile.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.edgeclaw.mobile.core.engine.EdgeClawEngine
import com.edgeclaw.mobile.core.model.EngineConfig
import com.edgeclaw.mobile.ui.navigation.EdgeClawNavHost
import com.edgeclaw.mobile.ui.theme.EdgeClawTheme

/**
 * Main activity — single-activity architecture with Compose navigation.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize engine (singleton)
        try {
            EdgeClawEngine.getInstance()
        } catch (e: IllegalStateException) {
            EdgeClawEngine.create(EngineConfig())
        }

        setContent {
            EdgeClawTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EdgeClawNavHost()
                }
            }
        }
    }
}
