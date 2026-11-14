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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.domain.place.CountyEntry
import io.cosgriff.flywaybracket.domain.place.StateEntry
import io.cosgriff.flywaybracket.domain.player.DisplayName
import io.cosgriff.flywaybracket.domain.player.DisplayNameCheck
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
                NameField(state.name, state.nameCheck, onName)
                PickerField(
                    label = stringResource(R.string.onboarding_state_label),
                    chosen = state.state?.name.orEmpty(),
                    options = state.states,
                    optionName = StateEntry::name,
                    enabled = state.states.isNotEmpty(),
                    hint = null,
                    onPick = onState,
                )
                PickerField(
                    label = stringResource(R.string.onboarding_county_label),
                    chosen = state.county?.name.orEmpty(),
                    options = state.state?.counties.orEmpty(),
                    optionName = CountyEntry::name,
                    enabled = state.state != null,
                    hint = if (state.state == null) stringResource(R.string.onboarding_county_needs_state) else null,
                    onPick = onCounty,
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

/** The name, with the rule under it; the rule gives way to the reason when the name breaks it. */
@Composable
private fun NameField(name: String, check: DisplayNameCheck, onName: (String) -> Unit) {
    val message = when (check) {
        DisplayNameCheck.TOO_SHORT ->
            pluralStringResource(R.plurals.onboarding_name_too_short, DisplayName.MIN_LENGTH, DisplayName.MIN_LENGTH)
        DisplayNameCheck.TOO_LONG ->
            pluralStringResource(R.plurals.onboarding_name_too_long, DisplayName.MAX_LENGTH, DisplayName.MAX_LENGTH)
        DisplayNameCheck.EMPTY, DisplayNameCheck.VALID -> pluralStringResource(
            R.plurals.onboarding_name_hint,
            DisplayName.MAX_LENGTH,
            DisplayName.MIN_LENGTH,
            DisplayName.MAX_LENGTH,
        )
    }
    OutlinedTextField(
        value = name,
        onValueChange = onName,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.onboarding_name_label)) },
        supportingText = { Text(message) },
        isError = check == DisplayNameCheck.TOO_SHORT || check == DisplayNameCheck.TOO_LONG,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        shape = MaterialTheme.shapes.medium,
        colors = fieldColors(),
    )
}

/** A field that opens a list to choose one of [options] from. [hint] says why it is off when it is. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> PickerField(
    label: String,
    chosen: String,
    options: List<T>,
    optionName: (T) -> String,
    enabled: Boolean,
    hint: String?,
    onPick: (T) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open && enabled, onExpandedChange = { open = it && enabled }) {
        OutlinedTextField(
            value = chosen,
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled),
            enabled = enabled,
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open && enabled) },
            supportingText = hint?.let { { Text(it) } },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = fieldColors(),
        )
        ExposedDropdownMenu(
            expanded = open && enabled,
            onDismissRequest = { open = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionName(option), style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        onPick(option)
                        open = false
                    },
                )
            }
        }
    }
}

/**
 * The canvas's input: the surface colour inside a border. A field that is off keeps the colours of one
 * that is on, since Material's faded ones are too faint to read; words under it say that it is off.
 */
@Composable
private fun fieldColors(): TextFieldColors {
    val scheme = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = scheme.surface,
        unfocusedContainerColor = scheme.surface,
        disabledContainerColor = scheme.surface,
        errorContainerColor = scheme.surface,
        disabledTextColor = scheme.onSurface,
        disabledLabelColor = scheme.onSurfaceVariant,
        disabledBorderColor = scheme.outline,
        disabledTrailingIconColor = scheme.onSurfaceVariant,
        disabledSupportingTextColor = scheme.onSurfaceVariant,
    )
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
