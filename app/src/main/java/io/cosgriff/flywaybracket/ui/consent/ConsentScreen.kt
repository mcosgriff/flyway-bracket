package io.cosgriff.flywaybracket.ui.consent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.data.server.ServerProblem
import io.cosgriff.flywaybracket.ui.components.FlywayBottomBar
import io.cosgriff.flywaybracket.ui.components.PrivacyList
import io.cosgriff.flywaybracket.ui.components.TopLevel
import io.cosgriff.flywaybracket.ui.components.message
import io.cosgriff.flywaybracket.ui.theme.FlywayDimens
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme

/**
 * Stands before League and Bracket (#38): while the phone has no account, the consent page shows in
 * their place; once it has one, [content]. "Not now" goes Home, and nothing is sent (ADR 0001).
 */
@Composable
fun ConsentScreen(
    place: TopLevel,
    onNavigate: (TopLevel) -> Unit,
    viewModel: ConsentViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val hasAccount by viewModel.hasAccount.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (hasAccount) {
        true -> content()
        false -> ConsentContent(
            state = state,
            place = place,
            onNavigate = onNavigate,
            onContinue = viewModel::onContinue,
            onNotNow = { onNavigate(TopLevel.Home) },
        )
        // The store is read in a moment; nothing is drawn until then, so the page doesn't flash.
        null -> Unit
    }
}

/**
 * What is sent, what others see and how to delete, in the words of ADR 0001; then Continue and Not now.
 * Built in the first-run screen's style: the canvas has no board for it.
 */
@Composable
fun ConsentContent(
    state: ConsentState,
    place: TopLevel,
    onNavigate: (TopLevel) -> Unit,
    onContinue: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { FlywayBottomBar(selected = place, onNavigate = onNavigate) },
    ) { insets ->
        Column(
            modifier = Modifier
                .padding(insets)
                .padding(horizontal = FlywayDimens.space5, vertical = FlywayDimens.space4),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FlywayDimens.space4),
            ) {
                Text(
                    text = stringResource(R.string.consent_title).toUpperCase(Locale.current),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.consent_intro), style = MaterialTheme.typography.bodyLarge)
                PrivacyList()
                Text(
                    text = stringResource(R.string.consent_not_now_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.problem?.let { Problem(it) }
            Buttons(state, onContinue, onNotNow)
        }
    }
}

/** Why the last try failed, read out when it appears. The Continue button below says Try again. */
@Composable
private fun Problem(problem: ServerProblem) {
    val colors = FlywayTheme.colors
    Surface(
        color = colors.warningContainer,
        contentColor = colors.onWarningContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(FlywayDimens.border, colors.warningBorder),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = stringResource(problem.message),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space3),
        )
    }
}

@Composable
private fun Buttons(state: ConsentState, onContinue: () -> Unit, onNotNow: () -> Unit) {
    val colors = FlywayTheme.colors
    val label = when {
        state.creating -> R.string.consent_creating
        state.problem != null -> R.string.consent_try_again
        else -> R.string.consent_continue
    }
    Button(
        onClick = onContinue,
        enabled = !state.creating,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FlywayDimens.buttonHeight),
        shape = CircleShape,
        // While the account is asked for it is grey, with its words saying so.
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.action,
            contentColor = colors.onAction,
            disabledContainerColor = colors.surfaceMuted,
            disabledContentColor = colors.inkMuted,
        ),
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.titleLarge)
    }
    OutlinedButton(
        onClick = onNotNow,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FlywayDimens.tapMin),
        shape = CircleShape,
        border = BorderStroke(FlywayDimens.border, colors.borderStrong),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(stringResource(R.string.consent_not_now), style = MaterialTheme.typography.titleMedium)
    }
}

internal val previewAsking = ConsentState()
internal val previewNoNetwork = ConsentState(problem = ServerProblem.NoNetwork)

@Preview
@Composable
private fun ConsentPreview() = FlywayTheme(darkTheme = false) {
    ConsentContent(previewAsking, TopLevel.League, onNavigate = {}, onContinue = {}, onNotNow = {})
}

@Preview
@Composable
private fun ConsentNoNetworkDarkPreview() = FlywayTheme(darkTheme = true) {
    ConsentContent(previewNoNetwork, TopLevel.Bracket, onNavigate = {}, onContinue = {}, onNotNow = {})
}
