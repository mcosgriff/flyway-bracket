package io.cosgriff.flywaybracket.ui.outing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.ui.components.clockText
import io.cosgriff.flywaybracket.ui.theme.FlywayDimens
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The outing in progress (#26), with the Add sighting sheet over it. Leaving it with Back keeps the outing
 * in progress; Home then offers to continue it.
 *
 * @param onFinished called with the outing's id once it has ended
 * @param onClosed called once it is discarded, or when there is no outing in progress to show
 */
@Composable
fun OutingScreen(
    onBack: () -> Unit,
    onFinished: (Long) -> Unit,
    onClosed: () -> Unit,
    viewModel: OutingViewModel = hiltViewModel(),
    adding: AddSightingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsed.collectAsStateWithLifecycle()
    val addState by adding.state.collectAsStateWithLifecycle()
    val finished by rememberUpdatedState(onFinished)
    val closed by rememberUpdatedState(onClosed)
    LaunchedEffect(viewModel) {
        viewModel.exit.collect { exit ->
            when (exit) {
                is OutingExit.Finished -> finished(exit.outingId)
                OutingExit.Closed -> closed()
            }
        }
    }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    OutingContent(
        state = state,
        elapsed = elapsed,
        onBack = onBack,
        onRename = viewModel::onRename,
        onAddSighting = { sheetOpen = true },
        onOneMore = viewModel::onOneMore,
        onOneFewer = viewModel::onOneFewer,
        onRemove = viewModel::onRemove,
        onFinish = viewModel::onFinish,
        onDiscard = viewModel::onDiscard,
    )
    if (sheetOpen) {
        AddSightingSheet(
            state = addState,
            query = adding.query,
            onQuery = adding::onQuery,
            onPick = adding::onPick,
            onDismiss = {
                adding.onDismiss()
                sheetOpen = false
            },
        )
    }
}

/**
 * @param elapsed the time since the outing started; the screen never reads the clock itself
 * @param onRename called with the new place name once the player saves it
 * @param onOneFewer called for a bird seen more than once; at one, the minus button asks, then calls
 *   [onRemove]
 * @param onDiscard called once the player has confirmed
 */
@Composable
fun OutingContent(
    state: OutingState,
    elapsed: Duration,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onAddSighting: () -> Unit,
    onOneMore: (SightingRow) -> Unit,
    onOneFewer: (SightingRow) -> Unit,
    onRemove: (SightingRow) -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The id of the sighting the player is being asked about.
    var removing by rememberSaveable { mutableStateOf<Long?>(null) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var discarding by rememberSaveable { mutableStateOf(false) }
    val title = stringResource(R.string.outing_title)
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .semantics { paneTitle = title },
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Header(
                state = state,
                elapsed = elapsed,
                onBack = onBack,
                onRename = { renaming = true },
                onDiscard = { discarding = true },
            )
        },
        bottomBar = { Footer(onFinish = onFinish, onAddSighting = onAddSighting) },
    ) { insets ->
        LazyColumn(
            modifier = Modifier.padding(insets),
            contentPadding = PaddingValues(horizontal = FlywayDimens.space5, vertical = FlywayDimens.space4),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2),
        ) {
            item(key = "title") {
                Text(
                    text = stringResource(R.string.outing_sightings_title).toUpperCase(Locale.current),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.semantics { heading() },
                )
            }
            if (state.sightings.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.outing_sightings_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.sightings, key = { it.id }) { sighting ->
                SightingItem(
                    sighting = sighting,
                    onOneMore = { onOneMore(sighting) },
                    onOneFewer = { if (sighting.count > 1) onOneFewer(sighting) else removing = sighting.id },
                )
            }
        }
    }
    state.sightings.firstOrNull { it.id == removing }?.let { sighting ->
        RemoveDialog(
            bird = sighting.bird,
            onConfirm = {
                removing = null
                onRemove(sighting)
            },
            onDismiss = { removing = null },
        )
    }
    if (renaming) {
        RenameDialog(
            place = state.place,
            onSave = { place ->
                renaming = false
                onRename(place)
            },
            onDismiss = { renaming = false },
        )
    }
    if (discarding) {
        DiscardDialog(
            sightings = state.sightings.size,
            onConfirm = {
                discarding = false
                onDiscard()
            },
            onDismiss = { discarding = false },
        )
    }
}

@Composable
private fun Header(state: OutingState, elapsed: Duration, onBack: () -> Unit, onRename: () -> Unit, onDiscard: () -> Unit) {
    val colors = FlywayTheme.colors
    Surface(color = colors.primary, contentColor = colors.onPrimary) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = FlywayDimens.space2, end = FlywayDimens.space2, top = FlywayDimens.space2, bottom = FlywayDimens.space4),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.action_back))
                }
                Text(
                    text = stringResource(R.string.outing_overline).toUpperCase(Locale.current),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onPrimaryMuted,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                MoreMenu(onDiscard)
            }
            Row(
                modifier = Modifier.padding(horizontal = FlywayDimens.space3),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space4),
            ) {
                Text(clockText(elapsed), style = FlywayTheme.text.scoreHero, modifier = Modifier.weight(1f))
                Figure(state.species, pluralStringResource(R.plurals.outing_species_label, state.species))
                Figure(state.birds, pluralStringResource(R.plurals.outing_birds_label, state.birds))
            }
            PlaceButton(state, onRename)
        }
    }
}

/** A figure over its label, read as one phrase: "6 species". */
@Composable
private fun Figure(figure: Int, label: String) {
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(stringResource(R.string.figure, figure), style = FlywayTheme.text.scoreLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = FlywayTheme.colors.onPrimaryMuted)
    }
}

@Composable
private fun MoreMenu(onDiscard: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_more), contentDescription = stringResource(R.string.outing_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = MaterialTheme.colorScheme.surface) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.outing_discard),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    open = false
                    onDiscard()
                },
            )
        }
    }
}

/** The place name and the county. Tapping it changes the place name. */
@Composable
private fun PlaceButton(state: OutingState, onRename: () -> Unit) {
    val colors = FlywayTheme.colors
    val county = state.county
    val text = when {
        state.place.isEmpty() -> stringResource(R.string.outing_place_unnamed)
        county != null -> stringResource(R.string.outing_place_county, state.place, county.name, county.stateAbbreviation)
        else -> state.place
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FlywayDimens.tapMin)
            .clickable(onClickLabel = stringResource(R.string.outing_place_edit), role = Role.Button, onClick = onRename)
            .padding(horizontal = FlywayDimens.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space2),
    ) {
        Icon(painterResource(R.drawable.ic_place), contentDescription = null, modifier = Modifier.size(FlywayDimens.space4), tint = colors.onPrimaryMuted)
        Column(modifier = Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onPrimaryMuted)
            // With no place name the county still shows, on its own line.
            if (state.place.isEmpty() && county != null) {
                Text(
                    text = stringResource(R.string.outing_county, county.name, county.stateAbbreviation),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onPrimaryMuted,
                )
            }
        }
        Icon(painterResource(R.drawable.ic_edit), contentDescription = null, modifier = Modifier.size(FlywayDimens.space4), tint = colors.onPrimaryMuted)
    }
}

@Composable
private fun SightingItem(sighting: SightingRow, onOneMore: () -> Unit, onOneFewer: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface, shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = FlywayDimens.space4, end = FlywayDimens.space2, top = FlywayDimens.space1, bottom = FlywayDimens.space1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space2),
        ) {
            Text(
                text = sighting.seenAt.format(TIME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(min = FlywayDimens.timeColumn),
            )
            Text(sighting.bird, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            StepButton(
                icon = R.drawable.ic_remove,
                label = stringResource(if (sighting.count > 1) R.string.outing_one_fewer else R.string.outing_remove_named, sighting.bird),
                onClick = onOneFewer,
            )
            Text(stringResource(R.string.outing_count, sighting.count), style = FlywayTheme.text.score)
            StepButton(icon = R.drawable.ic_add, label = stringResource(R.string.outing_one_more, sighting.bird), onClick = onOneMore)
        }
    }
}

@Composable
private fun StepButton(icon: Int, label: String, onClick: () -> Unit) {
    OutlinedIconButton(
        onClick = onClick,
        modifier = Modifier.size(FlywayDimens.tapMin),
        colors = IconButtonDefaults.outlinedIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = ButtonDefaults.outlinedButtonBorder(enabled = true),
    ) {
        Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(FlywayDimens.space5))
    }
}

@Composable
private fun Footer(onFinish: () -> Unit, onAddSighting: () -> Unit) {
    val colors = FlywayTheme.colors
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(thickness = FlywayDimens.border, color = colors.border)
            Row(
                modifier = Modifier.padding(start = FlywayDimens.space5, end = FlywayDimens.space5, top = FlywayDimens.space3, bottom = FlywayDimens.space5),
                horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
            ) {
                OutlinedButton(
                    onClick = onFinish,
                    modifier = Modifier.heightIn(min = FlywayDimens.buttonHeight),
                    shape = CircleShape,
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(stringResource(R.string.outing_finish), style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onAddSighting,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = FlywayDimens.buttonHeight),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.action, contentColor = colors.onAction),
                ) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(FlywayDimens.space5))
                    Spacer(Modifier.width(FlywayDimens.space2))
                    Text(stringResource(R.string.outing_add_sighting), style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}

@Composable
private fun RemoveDialog(bird: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.outing_remove_title, bird), style = MaterialTheme.typography.titleLarge) },
        text = { Text(stringResource(R.string.outing_remove_body, bird), style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { DialogButton(R.string.outing_remove, MaterialTheme.colorScheme.error, onConfirm) },
        dismissButton = { DialogButton(R.string.outing_cancel, MaterialTheme.colorScheme.onSurface, onDismiss) },
    )
}

@Composable
private fun DiscardDialog(sightings: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.outing_discard_title), style = MaterialTheme.typography.titleLarge) },
        text = { Text(pluralStringResource(R.plurals.outing_discard_body, sightings, sightings), style = MaterialTheme.typography.bodyLarge) },
        confirmButton = { DialogButton(R.string.outing_discard_confirm, MaterialTheme.colorScheme.error, onConfirm) },
        dismissButton = { DialogButton(R.string.outing_discard_keep, MaterialTheme.colorScheme.onSurface, onDismiss) },
    )
}

@Composable
private fun RenameDialog(place: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(place) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.outing_rename_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.outing_rename_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    shape = MaterialTheme.shapes.small,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FlywayTheme.colors.pick,
                        focusedLabelColor = FlywayTheme.colors.pick,
                        cursorColor = FlywayTheme.colors.pick,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.outing_rename_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { DialogButton(R.string.outing_rename_save, FlywayTheme.colors.pick) { onSave(text) } },
        dismissButton = { DialogButton(R.string.outing_cancel, MaterialTheme.colorScheme.onSurface, onDismiss) },
    )
}

@Composable
private fun DialogButton(label: Int, color: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(contentColor = color)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
    }
}

/** "7:48 AM", in the phone's language. */
private val TIME: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

// Made-up place, county, birds and times for previews and screenshot tests: no real sighting.
internal val previewElapsed: Duration = Duration.ofMinutes(42).plusSeconds(18)
internal val previewOutingEmpty = OutingState(place = "", county = OutingCounty("Lark Hollow", "ZA"))
internal val previewOuting = OutingState(
    place = "Sample Pond",
    county = OutingCounty("Lark Hollow", "ZA"),
    sightings = listOf(
        SightingRow(6, "Sample Vireo", 1, LocalTime.of(7, 48)),
        SightingRow(5, "Yellow-rumped Test Warbler", 6, LocalTime.of(7, 41)),
        SightingRow(4, "Made-up Pelican", 3, LocalTime.of(7, 26)),
        SightingRow(3, "Example Thrush", 2, LocalTime.of(7, 15)),
        SightingRow(2, "Test Teal", 4, LocalTime.of(7, 9)),
    ),
)

@Preview
@Composable
private fun OutingPreview() = FlywayTheme(darkTheme = false) {
    OutingContent(previewOuting, previewElapsed, {}, {}, {}, {}, {}, {}, {}, {})
}

@Preview
@Composable
private fun OutingEmptyDarkPreview() = FlywayTheme(darkTheme = true) {
    OutingContent(previewOutingEmpty, Duration.ofSeconds(12), {}, {}, {}, {}, {}, {}, {}, {})
}
