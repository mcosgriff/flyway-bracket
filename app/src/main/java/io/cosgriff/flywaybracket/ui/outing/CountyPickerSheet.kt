package io.cosgriff.flywaybracket.ui.outing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.ui.theme.FlywayDimens
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme
import kotlinx.coroutines.launch

/**
 * The county picker over the Outing screen (#27): the counties of the packs on the phone, by state. Tapping
 * one moves the outing there, then the sheet slides away and calls [onDismiss], as it does when the
 * player closes it without picking.
 *
 * @param currentCode the outing's county now, marked in the list
 * @param onPick called with the picked county's code
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountyPickerSheet(states: List<StateCounties>, currentCode: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        // As on the Add sighting sheet: the handle is a control, so it gets a 48dp touch target.
        dragHandle = {
            Box(Modifier.widthIn(min = FlywayDimens.tapMin), contentAlignment = Alignment.Center) { BottomSheetDefaults.DragHandle() }
        },
    ) {
        CountyPickerContent(
            states = states,
            currentCode = currentCode,
            onPick = { code ->
                onPick(code)
                scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
            },
        )
    }
}

/** The sheet's contents: a heading, then each state's counties in alphabetical order. */
@Composable
fun CountyPickerContent(states: List<StateCounties>, currentCode: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = FlywayDimens.space5, end = FlywayDimens.space5, bottom = FlywayDimens.space6),
        verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2),
    ) {
        item(key = "title") {
            Column(verticalArrangement = Arrangement.spacedBy(FlywayDimens.space1)) {
                Text(
                    text = stringResource(R.string.outing_county_picker_title).toUpperCase(Locale.current),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(if (states.isEmpty()) R.string.outing_county_picker_empty else R.string.outing_county_picker_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        states.forEach { state ->
            item(key = "state ${state.state}") {
                Text(
                    text = state.state.toUpperCase(Locale.current),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = FlywayDimens.space2)
                        .semantics { heading() },
                )
            }
            items(state.counties, key = { it.code }) { county ->
                CountyItem(county, current = county.code == currentCode, onClick = { onPick(county.code) })
            }
        }
    }
}

/** One county to tap. The outing's county now has the pick colour's border and says so in words. */
@Composable
private fun CountyItem(county: CountyChoice, current: Boolean, onClick: () -> Unit) {
    val colors = FlywayTheme.colors
    Surface(
        onClick = onClick,
        color = if (current) colors.pickContainer else colors.surface,
        contentColor = colors.ink,
        shape = MaterialTheme.shapes.medium,
        border = if (current) BorderStroke(FlywayDimens.border, colors.pick) else null,
        modifier = Modifier.semantics { selected = current },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FlywayDimens.buttonHeight)
                .padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
        ) {
            Text(
                text = county.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (current) {
                Text(
                    text = stringResource(R.string.outing_county_picker_current),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.pick,
                )
            }
        }
    }
}

// Made-up states and counties for previews and screenshot tests: the codes name no real place.
internal val previewCountyChoices = listOf(
    StateCounties(
        state = "Alder",
        counties = listOf(
            CountyChoice("US-ZA-001", "Heron"),
            CountyChoice("US-ZA-003", "Lark Hollow"),
            CountyChoice("US-ZA-002", "Sample Bend"),
        ),
    ),
    StateCounties(
        state = "Testland",
        counties = listOf(
            CountyChoice("XX-TS-002", "East Square"),
            CountyChoice("XX-TS-003", "Twin Islands"),
            CountyChoice("XX-TS-001", "West Square"),
        ),
    ),
)
