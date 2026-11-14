package io.cosgriff.flywaybracket.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.domain.place.CountyEntry
import io.cosgriff.flywaybracket.domain.place.StateEntry
import io.cosgriff.flywaybracket.domain.player.DisplayName
import io.cosgriff.flywaybracket.domain.player.DisplayNameCheck

/**
 * The display name and the home county, as the first run (#23) and Settings (#44) ask for them: the same
 * fields and the same rule, [DisplayName].
 *
 * @param state the state chosen, whose counties [county] is picked from
 */
@Composable
fun ProfileFields(
    name: String,
    states: List<StateEntry>,
    state: StateEntry?,
    county: CountyEntry?,
    onName: (String) -> Unit,
    onState: (StateEntry) -> Unit,
    onCounty: (CountyEntry) -> Unit,
) {
    NameField(name, DisplayName.check(name), onName)
    PickerField(
        label = stringResource(R.string.onboarding_state_label),
        chosen = state?.name.orEmpty(),
        options = states,
        optionName = StateEntry::name,
        enabled = states.isNotEmpty(),
        hint = null,
        onPick = onState,
    )
    PickerField(
        label = stringResource(R.string.onboarding_county_label),
        chosen = county?.name.orEmpty(),
        options = state?.counties.orEmpty(),
        optionName = CountyEntry::name,
        enabled = state != null,
        hint = if (state == null) stringResource(R.string.onboarding_county_needs_state) else null,
        onPick = onCounty,
    )
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
