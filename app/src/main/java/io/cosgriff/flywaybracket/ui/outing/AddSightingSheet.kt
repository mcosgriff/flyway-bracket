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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.ui.theme.FlywayDimens
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme
import kotlinx.coroutines.launch

/**
 * The Add sighting sheet over the Outing screen (#26). Tapping a bird logs it, then the sheet slides away
 * and calls [onDismiss], as it does when the player closes it without a bird.
 *
 * @param onPick called with the bird's name
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSightingSheet(
    state: AddSightingState,
    query: String,
    onQuery: (String) -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        // Material's handle is 32dp wide, and it is a control: a screen reader offers "Dismiss" on it.
        // Centred in a 48dp box, it has the touch target every control gets.
        dragHandle = {
            Box(Modifier.widthIn(min = FlywayDimens.tapMin), contentAlignment = Alignment.Center) { BottomSheetDefaults.DragHandle() }
        },
    ) {
        AddSightingContent(
            state = state,
            query = query,
            onQuery = onQuery,
            onPick = { bird ->
                onPick(bird)
                scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
            },
        )
    }
}

/**
 * The sheet's contents: a search field over the birds of the outing's county.
 *
 * @param query what the player has typed; [state] lists the birds that match it
 */
@Composable
fun AddSightingContent(
    state: AddSightingState,
    query: String,
    onQuery: (String) -> Unit,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FlywayDimens.space5),
        verticalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
    ) {
        Text(
            text = stringResource(R.string.add_sighting_title).toUpperCase(Locale.current),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            label = { Text(stringResource(R.string.add_sighting_search_label)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = FlywayTheme.colors.pick,
                focusedLabelColor = FlywayTheme.colors.pick,
                cursorColor = FlywayTheme.colors.pick,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        val expected = state.birds.filter { it.expected }
        val others = state.birds.filterNot { it.expected }
        LazyColumn(
            contentPadding = PaddingValues(bottom = FlywayDimens.space6),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2),
        ) {
            when {
                !state.hasPack -> item(key = "no pack") { Note(stringResource(R.string.add_sighting_no_pack)) }
                state.birds.isEmpty() && query.isNotBlank() ->
                    item(key = "no match") { Note(stringResource(R.string.add_sighting_no_match, query.trim())) }
            }
            if (expected.isNotEmpty()) {
                val county = state.county
                section(
                    key = "expected",
                    title = if (county != null) {
                        { stringResource(R.string.add_sighting_expected_title, county.name, county.stateAbbreviation) }
                    } else {
                        { stringResource(R.string.add_sighting_expected_title_no_county) }
                    },
                    birds = expected,
                    onPick = onPick,
                )
            }
            if (others.isNotEmpty()) {
                section(key = "others", title = { stringResource(R.string.add_sighting_others_title) }, birds = others, onPick = onPick)
            }
        }
    }
}

private fun LazyListScope.section(key: String, title: @Composable () -> String, birds: List<BirdRow>, onPick: (String) -> Unit) {
    item(key = key) {
        Text(
            text = title().toUpperCase(Locale.current),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = FlywayDimens.space2)
                .semantics { heading() },
        )
    }
    items(birds, key = { it.name }) { bird -> BirdItem(bird, onClick = { onPick(bird.name) }) }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * One bird to tap. A bird not expected this week has the warning colours and says so in words; one on
 * the outing already says how many.
 */
@Composable
private fun BirdItem(bird: BirdRow, onClick: () -> Unit) {
    val colors = FlywayTheme.colors
    Surface(
        onClick = onClick,
        color = if (bird.expected) colors.surface else colors.warningContainer,
        contentColor = colors.ink,
        shape = MaterialTheme.shapes.medium,
        border = if (bird.expected) null else BorderStroke(FlywayDimens.border, colors.warningBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FlywayDimens.buttonHeight)
                .padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(bird.name, style = MaterialTheme.typography.titleMedium)
                if (!bird.expected) {
                    Text(
                        text = stringResource(R.string.add_sighting_not_expected),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onWarningContainer,
                    )
                }
            }
            if (bird.logged > 0) {
                Text(
                    text = stringResource(R.string.add_sighting_logged, bird.logged),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (bird.expected) colors.inkMuted else colors.onWarningContainer,
                )
            }
        }
    }
}

// Made-up county and birds for previews and screenshot tests: no real figures.
internal val previewAddSighting = AddSightingState(
    county = OutingCounty("Lark Hollow", "ZA"),
    birds = listOf(
        BirdRow("Sample Vireo", expected = true),
        BirdRow("Yellow-rumped Test Warbler", expected = true, logged = 6),
        BirdRow("Made-up Pelican", expected = true, logged = 3),
        BirdRow("Example Thrush", expected = true, logged = 2),
        BirdRow("Test Teal", expected = true, logged = 4),
        BirdRow("Bobolink Test", expected = false),
        BirdRow("Wilson's Test Warbler", expected = false),
    ),
)

/** The birds that match "warb". */
internal val previewAddSightingSearch = AddSightingState(
    county = previewAddSighting.county,
    birds = listOf(
        BirdRow("Yellow-rumped Test Warbler", expected = true, logged = 6),
        BirdRow("Wilson's Test Warbler", expected = false),
    ),
)
internal val previewAddSightingNoMatch = AddSightingState(county = previewAddSighting.county)
