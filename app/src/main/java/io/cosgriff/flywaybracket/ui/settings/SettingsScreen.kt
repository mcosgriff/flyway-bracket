package io.cosgriff.flywaybracket.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.data.server.ServerProblem
import io.cosgriff.flywaybracket.domain.place.CountyEntry
import io.cosgriff.flywaybracket.domain.place.StateEntry
import io.cosgriff.flywaybracket.ui.components.BackBar
import io.cosgriff.flywaybracket.ui.components.ListRow
import io.cosgriff.flywaybracket.ui.components.ListSection
import io.cosgriff.flywaybracket.ui.components.PrivacyList
import io.cosgriff.flywaybracket.ui.components.ProfileFields
import io.cosgriff.flywaybracket.ui.components.message
import io.cosgriff.flywaybracket.ui.theme.FlywayDimens
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme

/** Settings (#44), opened from the gear on Home. Links leave for the browser. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenLicences: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    SettingsContent(
        state = viewModel.state,
        actions = SettingsActions(
            onBack = onBack,
            onName = viewModel::onName,
            onState = viewModel::onState,
            onCounty = viewModel::onCounty,
            onSave = viewModel::onSave,
            onDelete = viewModel::onDelete,
            onDeleteContinue = viewModel::onDeleteContinue,
            onDeleteConfirm = viewModel::onDeleteConfirm,
            onDeleteCancel = viewModel::onDeleteCancel,
            onOpenLink = context::openInBrowser,
            onOpenLicences = onOpenLicences,
        ),
    )
}

/** What the player can do on Settings. Each does nothing unless set, for previews and tests. */
class SettingsActions(
    val onBack: () -> Unit = {},
    val onName: (String) -> Unit = {},
    val onState: (StateEntry) -> Unit = {},
    val onCounty: (CountyEntry) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onDeleteContinue: () -> Unit = {},
    val onDeleteConfirm: () -> Unit = {},
    val onDeleteCancel: () -> Unit = {},
    /** Opens an address in the browser. */
    val onOpenLink: (String) -> Unit = {},
    val onOpenLicences: () -> Unit = {},
)

/**
 * Profile, account, privacy, about and licences. There is no board for it: its sections and rows are
 * the Packs board's.
 *
 * @param scroll where the page is scrolled to; a screenshot test starts it at the end
 */
@Composable
fun SettingsContent(
    state: SettingsState,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
    scroll: ScrollState = rememberScrollState(),
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { BackBar(actions.onBack) },
    ) { insets ->
        Column(
            modifier = Modifier
                .padding(insets)
                .consumeWindowInsets(insets)
                .imePadding()
                .verticalScroll(scroll)
                .padding(horizontal = FlywayDimens.space5, vertical = FlywayDimens.space2),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space5),
        ) {
            Text(
                text = stringResource(R.string.settings_title).toUpperCase(Locale.current),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Profile(state, actions)
            Account(state, actions.onDelete)
            ListSection(R.string.settings_privacy_title) {
                PrivacyList()
                ListRow(
                    title = stringResource(R.string.settings_privacy_page),
                    caption = stringResource(R.string.settings_opens_browser),
                    onClick = { actions.onOpenLink(PRIVACY_URL) },
                )
            }
            About(state, actions.onOpenLink)
            ListSection(R.string.settings_licences_title) {
                Caption(stringResource(R.string.app_licence))
                ListRow(
                    title = stringResource(R.string.settings_licences_open),
                    caption = stringResource(R.string.settings_licences_caption),
                    onClick = actions.onOpenLicences,
                )
            }
        }
    }
    when (state.delete) {
        DeleteStep.ASKED -> DeleteDialog(onContinue = actions.onDeleteContinue, onCancel = actions.onDeleteCancel)
        DeleteStep.ASKED_AGAIN -> DeleteAgainDialog(onConfirm = actions.onDeleteConfirm, onCancel = actions.onDeleteCancel)
        DeleteStep.NONE, DeleteStep.DELETING -> Unit
    }
}

@Composable
private fun Profile(state: SettingsState, actions: SettingsActions) {
    val colors = FlywayTheme.colors
    val form = state.profile
    ListSection(R.string.settings_profile_title) {
        Column(verticalArrangement = Arrangement.spacedBy(FlywayDimens.space3)) {
            ProfileFields(
                name = form.name,
                states = form.states,
                state = form.state,
                county = form.county,
                onName = actions.onName,
                onState = actions.onState,
                onCounty = actions.onCounty,
            )
            when (state.hasAccount) {
                true -> Caption(stringResource(R.string.settings_profile_sent))
                false -> Caption(stringResource(R.string.settings_profile_kept))
                null -> Unit
            }
            // Off until something has changed; grey with text that still reads, as the first run's Continue.
            Button(
                onClick = actions.onSave,
                enabled = form.canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = FlywayDimens.tapMin),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary,
                    disabledContainerColor = colors.surfaceMuted,
                    disabledContentColor = colors.inkMuted,
                ),
            ) {
                Text(stringResource(R.string.settings_profile_save), style = MaterialTheme.typography.titleMedium)
            }
            state.profileProblem?.let { Problem(it) }
        }
    }
}

@Composable
private fun Account(state: SettingsState, onDelete: () -> Unit) {
    val hasAccount = state.hasAccount ?: return
    val colors = FlywayTheme.colors
    ListSection(R.string.settings_account_title) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space3),
                verticalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
            ) {
                when {
                    hasAccount -> Text(stringResource(R.string.settings_account_has), style = MaterialTheme.typography.bodyMedium)
                    state.deleted -> Text(
                        text = stringResource(R.string.settings_account_deleted),
                        style = MaterialTheme.typography.bodyMedium,
                        // Read out when it appears: the dialog has just closed.
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    else -> Text(stringResource(R.string.settings_account_none), style = MaterialTheme.typography.bodyMedium)
                }
                if (hasAccount) {
                    val deleting = state.delete == DeleteStep.DELETING
                    // The words say what it does: the red is not the only sign.
                    OutlinedButton(
                        onClick = onDelete,
                        enabled = !deleting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = FlywayDimens.tapMin),
                        shape = CircleShape,
                        border = BorderStroke(FlywayDimens.border, if (deleting) colors.borderStrong else colors.danger),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = colors.danger,
                            disabledContainerColor = colors.surfaceMuted,
                            disabledContentColor = colors.inkMuted,
                        ),
                    ) {
                        Text(
                            text = stringResource(if (deleting) R.string.settings_account_deleting else R.string.settings_account_delete),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
        state.deleteProblem?.let { Problem(it) }
    }
}

@Composable
private fun About(state: SettingsState, onOpenLink: (String) -> Unit) {
    ListSection(R.string.settings_about_title) {
        ListRow(
            title = stringResource(R.string.settings_version),
            trailing = { Text(state.version, style = MaterialTheme.typography.bodyLarge) },
        )
        ListRow(
            title = stringResource(R.string.settings_user_guide),
            caption = stringResource(R.string.settings_opens_browser),
            onClick = { onOpenLink(USER_GUIDE_URL) },
        )
        ListRow(
            title = stringResource(R.string.settings_report),
            caption = stringResource(R.string.settings_report_caption),
            onClick = { onOpenLink(REPORT_URL) },
        )
        // Each pack names where its figures came from; the same source is named once.
        Surface(
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space3)
                    .semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(FlywayDimens.space1),
            ) {
                Text(stringResource(R.string.settings_bird_data), style = MaterialTheme.typography.titleMedium)
                if (state.birdData.isEmpty()) Caption(stringResource(R.string.settings_bird_data_none))
                state.birdData.forEach { Caption(it) }
            }
        }
    }
}

/** Why the last call failed, read out when it appears. */
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
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The first question: exactly what the server deletes, and that the outings on the phone stay. */
@Composable
private fun DeleteDialog(onContinue: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.settings_delete_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FlywayDimens.space3)) {
                Text(stringResource(R.string.settings_delete_server), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.settings_delete_phone), style = MaterialTheme.typography.bodyLarge)
            }
        },
        confirmButton = {
            TextButton(onClick = onContinue, colors = ButtonDefaults.textButtonColors(contentColor = FlywayTheme.colors.danger)) {
                Text(stringResource(R.string.settings_delete_continue), style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                Text(stringResource(R.string.settings_delete_cancel), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

/** The second question: it cannot be undone. Only its yes calls the server. */
@Composable
private fun DeleteAgainDialog(onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.settings_delete_again_title), style = MaterialTheme.typography.titleLarge) },
        text = { Text(stringResource(R.string.settings_delete_again_body), style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = FlywayTheme.colors.danger)) {
                Text(stringResource(R.string.settings_delete_confirm), style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                Text(stringResource(R.string.settings_delete_keep), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

// Made-up people, places and figures for previews and screenshot tests: the codes have the real shape and
// name no real state, and the version and the pack source are marked as samples.
private val previewAlder = StateEntry(
    code = "US-ZA",
    name = "Alder",
    counties = listOf(CountyEntry("US-ZA-001", "Heron"), CountyEntry("US-ZA-003", "Lark Hollow")),
)
internal val previewSettingsNoAccount = SettingsState(
    profile = ProfileForm(
        name = "Wren Example",
        states = listOf(previewAlder),
        state = previewAlder,
        county = previewAlder.counties[1],
        savedName = "Wren Example",
        savedCounty = "US-ZA-003",
    ),
    hasAccount = false,
    version = "0.0.0-sample",
    birdData = listOf("Made-up figures for previews"),
)
internal val previewSettingsAccount = previewSettingsNoAccount.copy(hasAccount = true)
internal val previewSettingsDeleteAsked = previewSettingsAccount.copy(delete = DeleteStep.ASKED)
internal val previewSettingsDeleteAskedAgain = previewSettingsAccount.copy(delete = DeleteStep.ASKED_AGAIN)

@Preview
@Composable
private fun SettingsNoAccountPreview() = FlywayTheme(darkTheme = false) {
    SettingsContent(previewSettingsNoAccount, SettingsActions())
}

@Preview
@Composable
private fun SettingsAccountDarkPreview() = FlywayTheme(darkTheme = true) {
    SettingsContent(previewSettingsAccount, SettingsActions())
}

@Preview
@Composable
private fun SettingsDeletePreview() = FlywayTheme(darkTheme = false) {
    SettingsContent(previewSettingsDeleteAsked, SettingsActions())
}
