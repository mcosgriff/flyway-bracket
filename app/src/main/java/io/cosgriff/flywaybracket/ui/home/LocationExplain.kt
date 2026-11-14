package io.cosgriff.flywaybracket.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.ui.theme.FlywayDimens
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme

/**
 * The page before Android's location prompt (#27), shown the first time an outing is started: what the
 * phone's location is used for, that it is approximate, and that it is never kept or sent.
 *
 * @param onContinue shows Android's prompt
 * @param onNotNow starts the outing in the home county without asking
 * @param onBack leaves the page, starting no outing
 */
@Composable
fun LocationExplainContent(onContinue: () -> Unit, onNotNow: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val title = stringResource(R.string.location_title)
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .semantics { paneTitle = title },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(FlywayDimens.space2)) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.action_back))
                }
            }
        },
        bottomBar = { Footer(onContinue = onContinue, onNotNow = onNotNow) },
    ) { insets ->
        Column(
            modifier = Modifier
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FlywayDimens.space5, vertical = FlywayDimens.space2),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space4),
        ) {
            Text(
                text = stringResource(R.string.location_overline).toUpperCase(Locale.current),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = title.toUpperCase(Locale.current),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Point(R.string.location_once_title, R.string.location_once_body)
            Point(R.string.location_approximate_title, R.string.location_approximate_body)
            Point(R.string.location_private_title, R.string.location_private_body)
            Text(
                text = stringResource(R.string.location_declining),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One of the page's points: a short line and the plain words under it, in a card. */
@Composable
private fun Point(title: Int, body: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(FlywayDimens.border, FlywayTheme.colors.border),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space3),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space1),
        ) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Footer(onContinue: () -> Unit, onNotNow: () -> Unit) {
    val colors = FlywayTheme.colors
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(thickness = FlywayDimens.border, color = colors.border)
            Row(
                modifier = Modifier.padding(start = FlywayDimens.space5, end = FlywayDimens.space5, top = FlywayDimens.space3, bottom = FlywayDimens.space5),
                horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
            ) {
                OutlinedButton(
                    onClick = onNotNow,
                    modifier = Modifier.heightIn(min = FlywayDimens.buttonHeight),
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(stringResource(R.string.location_not_now), style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = FlywayDimens.buttonHeight),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.action, contentColor = colors.onAction),
                ) {
                    Text(stringResource(R.string.location_continue), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Preview
@Composable
private fun LocationExplainPreview() = FlywayTheme(darkTheme = false) {
    LocationExplainContent(onContinue = {}, onNotNow = {}, onBack = {})
}

@Preview
@Composable
private fun LocationExplainDarkPreview() = FlywayTheme(darkTheme = true) {
    LocationExplainContent(onContinue = {}, onNotNow = {}, onBack = {})
}
