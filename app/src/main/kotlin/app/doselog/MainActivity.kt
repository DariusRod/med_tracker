package app.doselog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.doselog.ui.DoseLogAppRoot
import app.doselog.ui.theme.DoseLogTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Keep the splash up until settings are read, so the theme never flashes.
        splashScreen.setKeepOnScreenCondition { viewModel.uiState.value is MainUiState.Loading }

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val useDynamicColor = (uiState as? MainUiState.Ready)?.settings?.useDynamicColor ?: false
            DoseLogTheme(dynamicColor = useDynamicColor) {
                DoseLogAppRoot()
            }
        }
    }
}
