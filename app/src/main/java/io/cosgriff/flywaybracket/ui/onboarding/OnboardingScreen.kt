package io.cosgriff.flywaybracket.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.domain.place.CountyEntry
import io.cosgriff.flywaybracket.domain.place.StateEntry
import io.cosgriff.flywaybracket.ui.components.ProfileFields
import io.cosgriff.flywaybracket.ui.theme.FlywayDimens
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme

/**
 * The first run: asks for a display name and a home county, shown while either is missing (#23). It has
 * no back button and nothing behind it, so Back leaves the app.
 */
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    OnboardingContent(
        state = viewModel.state,
        onName = viewModel::onName,
        onState = viewModel::onState,
        onCounty = viewModel::onCounty,
        onContinue = viewModel::onContinue,
    )
}

@Composable
fun OnboardingContent(
    state: OnboardingState,
    onName: (String) -> Unit,
    onState: (StateEntry) -> Unit,
    onCounty: (CountyEntry) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.background) { insets ->
        Column(
            modifier = Modifier
                .padding(insets)
                .consumeWindowInsets(insets)
                // Keeps Continue above the keyboard.
                .imePadding()
                .padding(horizontal = FlywayDimens.space5, vertical = FlywayDimens.space4),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space4),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(FlywayDimens.space4),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_title).toUpperCase(Locale.current),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                ProfileFields(
                    name = state.name,
                    states = state.states,
                    state = state.state,
                    county = state.county,
                    onName = onName,
                    onState = onState,
                    onCounty = onCounty,
                )
                Text(
                    text = stringResource(R.string.onboarding_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onContinue,
                enabled = state.canContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = FlywayDimens.buttonHeight),
                shape = CircleShape,
                // Off, it is grey with text that still reads; the fields say what is missing.
                colors = ButtonDefaults.buttonColors(
                    containerColor = FlywayTheme.colors.action,
                    contentColor = FlywayTheme.colors.onAction,
                    disabledContainerColor = FlywayTheme.colors.surfaceMuted,
                    disabledContentColor = FlywayTheme.colors.inkMuted,
                ),
            ) {
                Text(stringResource(R.string.onboarding_continue), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

// Made-up people and places for previews and screenshot tests: the codes have the real shape and name no
// real state.
private val previewAlder = StateEntry(
    code = "US-ZA",
    name = "Alder",
    counties = listOf(CountyEntry("US-ZA-001", "Heron"), CountyEntry("US-ZA-003", "Lark Hollow")),
)
private val previewBirch = StateEntry(code = "US-ZB", name = "Birch", counties = listOf(CountyEntry("US-ZB-001", "Heron")))
internal val previewEmpty = OnboardingState(states = listOf(previewAlder, previewBirch))
internal val previewNameTooShort = previewEmpty.copy(name = "W", state = previewAlder)
internal val previewFilledIn = previewEmpty.copy(name = "Wren Example", state = previewAlder, county = previewAlder.counties[1])

@Preview
@Composable
private fun OnboardingEmptyPreview() = FlywayTheme(darkTheme = false) {
    OnboardingContent(previewEmpty, onName = {}, onState = {}, onCounty = {}, onContinue = {})
}

@Preview
@Composable
private fun OnboardingNameTooShortPreview() = FlywayTheme(darkTheme = false) {
    OnboardingContent(previewNameTooShort, onName = {}, onState = {}, onCounty = {}, onContinue = {})
}

@Preview
@Composable
private fun OnboardingFilledInDarkPreview() = FlywayTheme(darkTheme = true) {
    OnboardingContent(previewFilledIn, onName = {}, onState = {}, onCounty = {}, onContinue = {})
}
