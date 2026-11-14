package io.cosgriff.flywaybracket.ui.packs

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.ui.components.FlywayBottomBar
import io.cosgriff.flywaybracket.ui.components.TopLevel
import io.cosgriff.flywaybracket.ui.theme.FlywayDimens
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The packs on the phone (#24). "Add a pack" opens the system's file picker, which needs no permission;
 * downloading takes its place in #39.
 */
@Composable
fun PacksScreen(onNavigate: (TopLevel) -> Unit, viewModel: PacksViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { file ->
        if (file != null) viewModel.onFileChosen(file)
    }
    PacksContent(
        state = state,
        onNavigate = onNavigate,
        // No file type is registered for `.fbpack`, so the picker cannot be told to show only packs.
        onAddPack = { picker.launch(arrayOf(ANY_FILE)) },
        onRemove = viewModel::onRemove,
    )
}

private const val ANY_FILE = "*/*"

/** @param onRemove called with a pack's state code once the player has confirmed */
@Composable
fun PacksContent(
    state: PacksState,
    onNavigate: (TopLevel) -> Unit,
    onAddPack: () -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The state code of the pack the player is being asked about.
    var asking by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { FlywayBottomBar(selected = TopLevel.Packs, onNavigate = onNavigate) },
    ) { insets ->
        Column(
            modifier = Modifier
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FlywayDimens.space5, vertical = FlywayDimens.space4),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space4),
        ) {
            Header()
            AddPack(state.problem, onAddPack)
            if (state.home != null || state.homePack != null) {
                Section(R.string.packs_home_title) {
                    state.homePack?.let { HomePackCard(it) }
                    state.home?.let { HomeCountyCard(it) }
                }
            }
            if (state.extraPacks.isNotEmpty()) {
                Section(R.string.packs_extra_title) {
                    state.extraPacks.forEach { pack -> ExtraPackRow(pack, onRemove = { asking = pack.stateCode }) }
                }
            }
            // With a home county, its card says that the pack is missing.
            if (state.packs.isEmpty() && state.home == null) Caption(stringResource(R.string.packs_empty))
            Sources(state.packs)
        }
    }
    state.extraPacks.firstOrNull { it.stateCode == asking }?.let { pack ->
        RemoveDialog(
            pack = pack,
            onConfirm = {
                asking = null
                onRemove(pack.stateCode)
            },
            onDismiss = { asking = null },
        )
    }
}

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(FlywayDimens.space1)) {
        Text(
            text = stringResource(R.string.packs_title).toUpperCase(Locale.current),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.packs_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The button, what it asks for, and why the last file chosen was turned away. */
@Composable
private fun AddPack(problem: PackProblem?, onAddPack: () -> Unit) {
    val colors = FlywayTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2)) {
        Button(
            onClick = onAddPack,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FlywayDimens.buttonHeight),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
        ) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(FlywayDimens.space5))
            Spacer(Modifier.width(FlywayDimens.space2))
            Text(stringResource(R.string.packs_add), style = MaterialTheme.typography.titleLarge)
        }
        Caption(stringResource(R.string.packs_add_hint))
        if (problem != null) {
            Surface(
                color = colors.warningContainer,
                contentColor = colors.onWarningContainer,
                shape = MaterialTheme.shapes.medium,
                // Read out when it appears: the file picker has just closed and nothing else has changed.
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
    }
}

private val PackProblem.message: Int
    get() = when (this) {
        PackProblem.NOT_A_PACK -> R.string.packs_problem_not_a_pack
        PackProblem.DAMAGED -> R.string.packs_problem_damaged
        PackProblem.TOO_NEW -> R.string.packs_problem_too_new
        PackProblem.TOO_LARGE -> R.string.packs_problem_too_large
        PackProblem.NOT_READ -> R.string.packs_problem_not_read
        PackProblem.NOT_SAVED -> R.string.packs_problem_not_saved
    }

@Composable
private fun Section(title: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2)) {
        Text(
            text = stringResource(title).toUpperCase(Locale.current),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The pack that holds the home county. Its Remove button is off, and the words beside it say why. */
@Composable
private fun HomePackCard(pack: PackRow) {
    val colors = FlywayTheme.colors
    val whyOff = stringResource(R.string.packs_remove_off)
    val label = pack.removeLabel
    Surface(color = colors.primary, contentColor = colors.onPrimary, shape = MaterialTheme.shapes.large) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space3),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2),
        ) {
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(pack.state, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = stringResource(R.string.packs_built, day(pack.builtOn), size(pack.sizeBytes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onPrimaryMuted,
                    )
                }
                Text(counties(pack.counties), style = FlywayTheme.text.score)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3)) {
                Text(
                    text = whyOff,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onPrimaryMuted,
                    modifier = Modifier.weight(1f),
                )
                // Off, it is grey with text that still reads, as the first run's Continue is.
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .heightIn(min = FlywayDimens.tapMin)
                        .semantics {
                            contentDescription = label
                            stateDescription = whyOff
                        },
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = colors.surfaceMuted,
                        disabledContentColor = colors.inkMuted,
                    ),
                ) {
                    Text(stringResource(R.string.packs_remove), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun HomeCountyCard(home: HomeCountyLine) {
    val colors = FlywayTheme.colors
    Surface(color = colors.pickContainer, contentColor = colors.ink, shape = MaterialTheme.shapes.large) {
        // One phrase for a screen reader: "Your home county, Lark Hollow County, …, 214 species".
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space3)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.packs_home_county_overline).toUpperCase(Locale.current),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.pick,
                )
                Text(stringResource(R.string.packs_home_county, home.county), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (home.species != null) {
                        stringResource(R.string.packs_home_county_species_caption)
                    } else {
                        stringResource(R.string.packs_home_county_no_pack, home.state)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted,
                )
            }
            if (home.species != null) {
                Text(
                    text = pluralStringResource(R.plurals.packs_home_county_species, home.species, home.species),
                    style = FlywayTheme.text.score,
                    color = colors.pick,
                )
            }
        }
    }
}

@Composable
private fun ExtraPackRow(pack: PackRow, onRemove: () -> Unit) {
    // The ink is named: left to Material, text on the surface colour takes the error role's red.
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FlywayDimens.rowHeight)
                .padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
            ) {
                Text(pack.state, style = MaterialTheme.typography.titleMedium)
                Caption(stringResource(R.string.packs_extra_details, counties(pack.counties), day(pack.builtOn), size(pack.sizeBytes)))
            }
            val label = pack.removeLabel
            OutlinedButton(
                onClick = onRemove,
                // "Remove Birch", so two rows' buttons are told apart.
                modifier = Modifier
                    .heightIn(min = FlywayDimens.tapMin)
                    .semantics { contentDescription = label },
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text(stringResource(R.string.packs_remove), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

private val PackRow.removeLabel: String
    @Composable get() = stringResource(R.string.packs_remove_named, state)

/** Asks before a pack is removed, and says what removing it costs. */
@Composable
private fun RemoveDialog(pack: PackRow, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.packs_remove_dialog_title, pack.state), style = MaterialTheme.typography.titleLarge) },
        text = { Text(stringResource(R.string.packs_remove_dialog_body, pack.state), style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(R.string.packs_remove), style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                Text(stringResource(R.string.packs_remove_dialog_cancel), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

/** Where the packs' figures came from, in the words their files give. Packs from one source say so once. */
@Composable
private fun Sources(packs: List<PackRow>) {
    if (packs.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(FlywayDimens.space1)) {
        packs.map { it.source }.distinct().forEach { source -> Caption(stringResource(R.string.packs_source, source)) }
    }
}

@Composable
private fun counties(count: Int): String = pluralStringResource(R.plurals.packs_counties, count, count)

private fun day(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

/** "1.4 MB", in the phone's language. */
@Composable
private fun size(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)

// Made-up states, counties and figures for previews and screenshot tests: no real place and no eBird data.
private val previewAlder = PackRow(
    stateCode = "US-ZA",
    state = "Alder",
    counties = 12,
    builtOn = LocalDate.of(2027, 4, 2),
    sizeBytes = 1_480_000,
    source = "Made-up figures for previews",
)
private val previewBirch = PackRow(
    stateCode = "US-ZB",
    state = "Birch",
    counties = 1,
    builtOn = LocalDate.of(2027, 3, 14),
    sizeBytes = 212_000,
    source = "Made-up figures for previews",
)
internal val previewNoPacks = PacksState(home = HomeCountyLine(county = "Lark Hollow", state = "Alder", species = null))
internal val previewHomePack = PacksState(
    home = HomeCountyLine(county = "Lark Hollow", state = "Alder", species = 214),
    homePack = previewAlder,
)
internal val previewTwoPacks = previewHomePack.copy(extraPacks = listOf(previewBirch))
internal val previewRejected = previewNoPacks.copy(problem = PackProblem.NOT_A_PACK)

@Preview
@Composable
private fun PacksNoPacksPreview() = FlywayTheme(darkTheme = false) {
    PacksContent(previewNoPacks, onNavigate = {}, onAddPack = {}, onRemove = {})
}

@Preview
@Composable
private fun PacksHomePackPreview() = FlywayTheme(darkTheme = false) {
    PacksContent(previewHomePack, onNavigate = {}, onAddPack = {}, onRemove = {})
}

@Preview
@Composable
private fun PacksTwoPacksDarkPreview() = FlywayTheme(darkTheme = true) {
    PacksContent(previewTwoPacks, onNavigate = {}, onAddPack = {}, onRemove = {})
}

@Preview
@Composable
private fun PacksRejectedPreview() = FlywayTheme(darkTheme = false) {
    PacksContent(previewRejected, onNavigate = {}, onAddPack = {}, onRemove = {})
}
