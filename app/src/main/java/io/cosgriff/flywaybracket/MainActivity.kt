package io.cosgriff.flywaybracket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.cosgriff.flywaybracket.ui.AppViewModel
import io.cosgriff.flywaybracket.ui.FlywayNavHost
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val app: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // The splash stays until the settings say which screen comes first: the first run or Home (#23).
        installSplashScreen().setKeepOnScreenCondition { app.needsOnboarding.value == null }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlywayTheme {
                val needsOnboarding = app.needsOnboarding.collectAsStateWithLifecycle().value
                if (needsOnboarding != null) FlywayNavHost(needsOnboarding)
            }
        }
    }
}
