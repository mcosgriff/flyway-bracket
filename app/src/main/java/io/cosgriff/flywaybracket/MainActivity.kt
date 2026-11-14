package io.cosgriff.flywaybracket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.cosgriff.flywaybracket.ui.theme.FlywayDimens
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlywayTheme {
                FlywayHomeContent()
            }
        }
    }
}

/** Placeholder home until the Home screen is built from the design canvas. */
@Composable
fun FlywayHomeContent(modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize()) { insets ->
        Column(
            modifier = Modifier
                .padding(insets)
                .padding(horizontal = FlywayDimens.space5, vertical = FlywayDimens.space3),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space1),
        ) {
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Preview
@Composable
private fun FlywayHomeLightPreview() = FlywayTheme(darkTheme = false) { FlywayHomeContent() }

@Preview
@Composable
private fun FlywayHomeDarkPreview() = FlywayTheme(darkTheme = true) { FlywayHomeContent() }
