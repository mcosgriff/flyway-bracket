package io.cosgriff.flywaybracket.ui.packs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
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
import io.cosgriff.flywaybracket.BuildConfig
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.data.DownloadProgress
import io.cosgriff.flywaybracket.data.FailedDownload
import io.cosgriff.flywaybracket.data.PackDownload
import io.cosgriff.flywaybracket.data.server.ServerProblem
import io.cosgriff.flywaybracket.ui.components.DownloadFailureText
import io.cosgriff.flywaybracket.ui.components.DownloadProgressLine
import io.cosgriff.flywaybracket.ui.components.FlywayBottomBar
import io.cosgriff.flywaybracket.ui.components.MeteredAsk
import io.cosgriff.flywaybracket.ui.components.MeteredDownloadDialog
import io.cosgriff.flywaybracket.ui.components.ServerWaking
import io.cosgriff.flywaybracket.ui.components.TopLevel
import io.cosgriff.flywaybracket.ui.components.failureMessage
import io.cosgriff.flywaybracket.ui.components.fileSizeText
import io.cosgriff.flywaybracket.ui.components.message
import io.cosgriff.flywaybracket.ui.theme.FlywayDimens
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The packs on the phone (#24), and the server's to download (#39). With no account, "Get packs" opens
 * the consent page first ([onGetAccount]). Debug builds also keep the system's file picker, which needs
 * no permission, for testing with a pack built on the developer's machine.
 */
@Composable
fun PacksScreen(onNavigate: (TopLevel) -> Unit, onGetAccount: () -> Unit, viewModel: PacksViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val waking by viewModel.waking.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { file ->
        if (file != null) viewModel.onFileChosen(file)
    }
    PacksContent(
        state = state,
        onNavigate = onNavigate,
        waking = waking,
        onRemove = viewModel::onRemove,
        onDownload = viewModel::onDownload,
        onGetAccount = onGetAccount,
        onRetry = viewModel::onRetry,
        onConfirmMetered = viewModel::onMeteredConfirmed,
        onDismissMetered = viewModel::onMeteredDismissed,
        // No file type is registered for `.fbpack`, so the picker cannot be told to show only packs.
        onAddFile = if (BuildConfig.DEBUG) ({ picker.launch(arrayOf(ANY_FILE)) }) else null,
    )
}

private const val ANY_FILE = "*/*"

/**
 * @param onRemove called with a pack's state code once the player has confirmed
 * @param onDownload called with a state code to download its pack, or the server's newer version of it
 * @param onGetAccount the player wants packs and the phone has no account
 * @param onRetry asks the server for its list again
 * @param onAddFile opens the file picker; null outside debug builds, and the button is not shown
 * @param waking the server is most likely waking (#93): the list being asked for, and a download not yet
 *   started, say so
 */
@Composable
fun PacksContent(
    state: PacksState,
    onNavigate: (TopLevel) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    waking: Boolean = false,
    onDownload: (String) -> Unit = {},
    onGetAccount: () -> Unit = {},
    onRetry: () -> Unit = {},
    onConfirmMetered: () -> Unit = {},
    onDismissMetered: () -> Unit = {},
    onAddFile: (() -> Unit)? = null,
) {
    // The state code of the pack the player is being asked about.
    var asking by rememberSaveable { mutableStateOf<String?>(null) }
    val failure = state.failedDownload
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
            if (state.home != null || state.homePack != null) {
                Section(R.string.packs_home_title) {
                    state.homePack?.let { HomePackCard(it, failure.of(it.stateCode), waking, onUpdate = { onDownload(it.stateCode) }) }
                    state.home?.let { HomeCountyCard(it) }
                }
            }
            if (state.extraPacks.isNotEmpty()) {
                Section(R.string.packs_extra_title) {
                    state.extraPacks.forEach { pack ->
                        ExtraPackRow(
                            pack = pack,
                            failure = failure.of(pack.stateCode),
                            waking = waking,
                            onUpdate = { onDownload(pack.stateCode) },
                            onRemove = { asking = pack.stateCode },
                        )
                    }
                }
            }
            Section(R.string.packs_add) {
                ServerSection(state.server, failure, waking, onDownload, onGetAccount, onRetry)
            }
            if (onAddFile != null) AddFile(state.problem, onAddFile)
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
    state.askMetered?.let { MeteredDownloadDialog(it, onConfirm = onConfirmMetered, onDismiss = onDismissMetered) }
}

/** No byte of the file has come yet: the server may still be waking (#93). */
private val DownloadProgress.notStarted: Boolean get() = receivedBytes == 0L

/** Why the last download of this state installed nothing, or null. */
private fun FailedDownload?.of(stateCode: String): PackDownload? = this?.takeIf { it.stateCode == stateCode }?.outcome

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

/** The server's packs that are not on the phone, or why they are not listed. */
@Composable
private fun ServerSection(
    server: ServerPacks,
    failure: FailedDownload?,
    waking: Boolean,
    onDownload: (String) -> Unit,
    onGetAccount: () -> Unit,
    onRetry: () -> Unit,
) {
    when (server) {
        ServerPacks.NeedsAccount -> NeedsAccountCard(onGetAccount)
        ServerPacks.Loading -> {
            Caption(stringResource(R.string.packs_server_loading))
            if (waking) ServerWaking(style = MaterialTheme.typography.bodySmall)
        }
        is ServerPacks.Failed -> {
            Warning(stringResource(server.problem.message))
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = FlywayDimens.tapMin),
                border = BorderStroke(FlywayDimens.border, FlywayTheme.colors.borderStrong),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text(stringResource(R.string.packs_server_retry), style = MaterialTheme.typography.labelMedium)
            }
        }
        is ServerPacks.Listed -> when {
            server.onServer == 0 -> Caption(stringResource(R.string.packs_server_none))
            server.available.isEmpty() -> Caption(stringResource(R.string.packs_server_all_installed))
            else -> server.available.forEach { pack ->
                ServerPackCard(pack, failure.of(pack.stateCode), waking, onDownload = { onDownload(pack.stateCode) })
            }
        }
    }
}

@Composable
private fun NeedsAccountCard(onGetAccount: () -> Unit) {
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
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
        ) {
            Text(stringResource(R.string.packs_server_needs_account), style = MaterialTheme.typography.bodyMedium)
            PrimaryButton(stringResource(R.string.packs_server_get_account), label = null, onClick = onGetAccount)
        }
    }
}

/** A pack the server has and the phone does not: its Download button, or how far its download has come. */
@Composable
private fun ServerPackCard(pack: ServerPackRow, failure: PackDownload?, waking: Boolean, onDownload: () -> Unit) {
    val colors = FlywayTheme.colors
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FlywayDimens.rowHeight)
                .padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space3),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3)) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) {},
                ) {
                    Text(pack.state, style = MaterialTheme.typography.titleMedium)
                    Caption(stringResource(R.string.packs_server_details, fileSizeText(pack.sizeBytes), day(pack.builtOn)))
                }
                if (pack.progress == null) {
                    PrimaryButton(stringResource(R.string.packs_download), stringResource(R.string.packs_download_named, pack.state), onDownload)
                }
            }
            pack.progress?.let {
                DownloadProgressLine(it, color = colors.pick, trackColor = colors.surfaceMuted, textColor = colors.inkMuted)
                if (waking && it.notStarted) ServerWaking(style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
            }
            failure?.failureMessage?.let { DownloadFailureText(it, color = colors.danger) }
        }
    }
}

/** A filled button in the board's primary green. [label] tells a screen reader which pack it is for. */
@Composable
private fun PrimaryButton(text: String, label: String?, onClick: () -> Unit, container: Color? = null, content: Color? = null) {
    val colors = FlywayTheme.colors
    Button(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = FlywayDimens.tapMin)
            .semantics { if (label != null) contentDescription = label },
        colors = ButtonDefaults.buttonColors(containerColor = container ?: colors.primary, contentColor = content ?: colors.onPrimary),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

/** Debug builds only: the file picker, what it asks for, and why the last file chosen was turned away. */
@Composable
private fun AddFile(problem: PackProblem?, onAddFile: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2)) {
        OutlinedButton(
            onClick = onAddFile,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FlywayDimens.tapMin),
            shape = CircleShape,
            border = BorderStroke(FlywayDimens.border, FlywayTheme.colors.borderStrong),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Text(stringResource(R.string.packs_add_file), style = MaterialTheme.typography.titleMedium)
        }
        Caption(stringResource(R.string.packs_add_hint))
        // Read out when it appears: the file picker has just closed and nothing else has changed.
        if (problem != null) Warning(stringResource(problem.message))
    }
}

@Composable
private fun Warning(text: String) {
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
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space3),
        )
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

/**
 * The pack that holds the home county. Its Remove button is off, and the words beside it say why. When
 * the server has another version, the card offers it.
 */
@Composable
private fun HomePackCard(pack: PackRow, failure: PackDownload?, waking: Boolean, onUpdate: () -> Unit) {
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
                        text = stringResource(R.string.packs_built, day(pack.builtOn), fileSizeText(pack.sizeBytes)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onPrimaryMuted,
                    )
                }
                Text(counties(pack.counties), style = FlywayTheme.text.score)
            }
            pack.update?.let { update ->
                UpdateLine(
                    pack = pack,
                    update = update,
                    failure = failure,
                    waking = waking,
                    onUpdate = onUpdate,
                    textColor = colors.onPrimary,
                    buttonContainer = colors.onPrimary,
                    buttonContent = colors.primary,
                    // White on pale green would barely show: the bar is the action colour on this card.
                    bar = colors.action,
                    track = colors.onPrimaryMuted,
                )
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

/**
 * "Update available" with the size of the server's file, and an Update button; while it downloads, how
 * far it has come. The words say it, not only the colour.
 */
@Composable
private fun UpdateLine(
    pack: PackRow,
    update: PackUpdate,
    failure: PackDownload?,
    waking: Boolean,
    onUpdate: () -> Unit,
    textColor: Color,
    buttonContainer: Color,
    buttonContent: Color,
    bar: Color,
    track: Color,
) {
    val progress = update.progress
    if (progress != null) {
        DownloadProgressLine(progress, color = bar, trackColor = track, textColor = textColor)
        if (waking && progress.notStarted) ServerWaking(style = MaterialTheme.typography.bodySmall, color = textColor)
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3)) {
            Text(
                text = stringResource(R.string.packs_update_available, fileSizeText(update.sizeBytes)),
                style = MaterialTheme.typography.labelMedium,
                color = textColor,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(R.string.packs_update),
                label = stringResource(R.string.packs_update_named, pack.state),
                onClick = onUpdate,
                container = buttonContainer,
                content = buttonContent,
            )
        }
    }
    failure?.failureMessage?.let { DownloadFailureText(it, color = textColor) }
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
private fun ExtraPackRow(pack: PackRow, failure: PackDownload?, waking: Boolean, onUpdate: () -> Unit, onRemove: () -> Unit) {
    val colors = FlywayTheme.colors
    // The ink is named: left to Material, text on the surface colour takes the error role's red.
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FlywayDimens.rowHeight)
                .padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space2),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .semantics(mergeDescendants = true) {},
                ) {
                    Text(pack.state, style = MaterialTheme.typography.titleMedium)
                    Caption(stringResource(R.string.packs_extra_details, counties(pack.counties), day(pack.builtOn), fileSizeText(pack.sizeBytes)))
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
            pack.update?.let { update ->
                UpdateLine(
                    pack = pack,
                    update = update,
                    failure = failure,
                    waking = waking,
                    onUpdate = onUpdate,
                    textColor = colors.pick,
                    buttonContainer = colors.primary,
                    buttonContent = colors.onPrimary,
                    bar = colors.pick,
                    track = colors.surfaceMuted,
                )
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

// Made-up states, counties, sizes and versions for previews and screenshot tests: no real place and no
// eBird data.
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
private val previewServer = ServerPacks.Listed(
    available = listOf(
        ServerPackRow("US-ZC", "Cedar", sizeBytes = 2_310_000, builtOn = LocalDate.of(2027, 4, 2)),
        ServerPackRow("US-ZD", "Dogwood", sizeBytes = 6_200_000, builtOn = LocalDate.of(2027, 3, 28)),
    ),
    onServer = 4,
)
internal val previewNoPacks = PacksState(
    home = HomeCountyLine(county = "Lark Hollow", state = "Alder", species = null),
    server = ServerPacks.NeedsAccount,
)
internal val previewHomePack = PacksState(
    home = HomeCountyLine(county = "Lark Hollow", state = "Alder", species = 214),
    homePack = previewAlder,
    server = ServerPacks.Listed(available = emptyList(), onServer = 1),
)
internal val previewTwoPacks = previewHomePack.copy(extraPacks = listOf(previewBirch), server = previewServer)
internal val previewRejected = previewNoPacks.copy(problem = PackProblem.NOT_A_PACK)
internal val previewServerList = previewNoPacks.copy(
    server = ServerPacks.Listed(
        available = listOf(ServerPackRow("US-ZA", "Alder", sizeBytes = 1_480_000, builtOn = LocalDate.of(2027, 4, 2))) + previewServer.available,
        onServer = 3,
    ),
)
internal val previewDownloading = previewNoPacks.copy(
    server = ServerPacks.Listed(
        available = listOf(
            ServerPackRow(
                "US-ZA",
                "Alder",
                sizeBytes = 1_480_000,
                builtOn = LocalDate.of(2027, 4, 2),
                progress = DownloadProgress(receivedBytes = 621_600, totalBytes = 1_480_000),
            ),
        ) + previewServer.available,
        onServer = 3,
    ),
    failedDownload = FailedDownload("US-ZD", PackDownload.Failed(ServerProblem.NoNetwork)),
)

/** The server's list being asked for; with `waking`, while the server starts (#93). */
internal val previewServerLoading = previewHomePack.copy(server = ServerPacks.Loading)

/** A download asked for, no byte of it come yet; with `waking`, while the server starts (#93). */
internal val previewDownloadStarting = previewNoPacks.copy(
    server = ServerPacks.Listed(
        available = listOf(
            ServerPackRow(
                "US-ZA",
                "Alder",
                sizeBytes = 1_480_000,
                builtOn = LocalDate.of(2027, 4, 2),
                progress = DownloadProgress(receivedBytes = 0, totalBytes = 1_480_000),
            ),
        ),
        onServer = 2,
    ),
)
internal val previewUpdateAvailable = previewTwoPacks.copy(
    homePack = previewAlder.copy(update = PackUpdate(sizeBytes = 1_520_000)),
    extraPacks = listOf(
        previewBirch.copy(
            update = PackUpdate(sizeBytes = 230_000, progress = DownloadProgress(receivedBytes = 172_500, totalBytes = 230_000)),
        ),
    ),
    server = ServerPacks.Listed(available = emptyList(), onServer = 2),
)
internal val previewAskMetered = previewServerList.copy(askMetered = MeteredAsk("US-ZD", "Dogwood", 6_200_000))

@Preview
@Composable
private fun PacksNoPacksPreview() = FlywayTheme(darkTheme = false) {
    PacksContent(previewNoPacks, onNavigate = {}, onRemove = {})
}

@Preview
@Composable
private fun PacksServerListPreview() = FlywayTheme(darkTheme = false) {
    PacksContent(previewServerList, onNavigate = {}, onRemove = {})
}

@Preview
@Composable
private fun PacksDownloadingDarkPreview() = FlywayTheme(darkTheme = true) {
    PacksContent(previewDownloading, onNavigate = {}, onRemove = {})
}

@Preview
@Composable
private fun PacksUpdateAvailablePreview() = FlywayTheme(darkTheme = false) {
    PacksContent(previewUpdateAvailable, onNavigate = {}, onRemove = {})
}

@Preview
@Composable
private fun PacksRejectedPreview() = FlywayTheme(darkTheme = false) {
    PacksContent(previewRejected, onNavigate = {}, onRemove = {}, onAddFile = {})
}
