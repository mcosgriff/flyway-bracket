package io.cosgriff.flywaybracket.ui.league

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.domain.bracket.SeasonCalendar
import io.cosgriff.flywaybracket.domain.bracket.SeasonCalendarResult
import io.cosgriff.flywaybracket.domain.league.LeagueLifecycle
import io.cosgriff.flywaybracket.domain.league.LeagueName
import io.cosgriff.flywaybracket.domain.league.LeagueNameCheck
import io.cosgriff.flywaybracket.domain.league.LeagueTimeZone
import io.cosgriff.flywaybracket.domain.league.SeasonStart
import io.cosgriff.flywaybracket.domain.league.SeasonStartCheck
import io.cosgriff.flywaybracket.ui.components.InviteCodeCard
import io.cosgriff.flywaybracket.ui.components.nameRes
import io.cosgriff.flywaybracket.ui.components.shareInvite
import io.cosgriff.flywaybracket.ui.components.weekdayText
import io.cosgriff.flywaybracket.ui.theme.FlywayDimens
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Create a league (#40), from the League tab's Join view. Once the league is created it shows the invite code
 * with Share invite; Done goes back to the League tab, which then shows the league.
 */
@Composable
fun LeagueCreateScreen(onBack: () -> Unit, viewModel: LeagueCreateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LeagueCreateContent(
        state = state,
        today = viewModel.today(),
        onBack = onBack,
        actions = CreateLeagueActions(
            onName = viewModel::onName,
            onOpen = viewModel::onOpen,
            onSeasonStart = viewModel::onSeasonStart,
            onTimeZone = viewModel::onTimeZone,
            onCreate = viewModel::onCreate,
            onShare = { state.created?.let { created -> created.inviteCode?.let { shareInvite(context, created.name, it) } } },
            onDone = onBack,
        ),
    )
}

/** What Create a league's controls do. Each defaults to nothing, for previews and screenshot tests. */
class CreateLeagueActions(
    val onName: (String) -> Unit = {},
    val onOpen: (Boolean) -> Unit = {},
    val onSeasonStart: (LocalDate) -> Unit = {},
    val onTimeZone: (String) -> Unit = {},
    val onCreate: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onDone: () -> Unit = {},
)

/** @param today today in the league's zone, which the start date is judged from */
@Composable
fun LeagueCreateContent(
    state: CreateLeagueState,
    today: LocalDate,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: CreateLeagueActions = CreateLeagueActions(),
) {
    var pickingStart by rememberSaveable { mutableStateOf(false) }
    var pickingZone by rememberSaveable { mutableStateOf(false) }
    val created = state.created
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopBar(if (created == null) R.string.league_create_title else R.string.league_created_title, onBack) },
        bottomBar = { if (created == null) Footer(state, actions.onCreate) },
    ) { insets ->
        Column(
            modifier = Modifier
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FlywayDimens.space5, vertical = FlywayDimens.space2),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space4),
        ) {
            if (created != null) {
                Created(created, actions)
            } else {
                NameField(state.name, actions.onName)
                Choices(state, onStart = { pickingStart = true }, onZone = { pickingZone = true }, onOpen = actions.onOpen)
                FieldCard()
                state.problem?.let { Problem(it) }
            }
        }
    }
    if (pickingStart && created == null) {
        StartDialog(
            start = state.seasonStart,
            today = today,
            onPick = {
                pickingStart = false
                actions.onSeasonStart(it)
            },
            onCancel = { pickingStart = false },
        )
    }
    if (pickingZone && created == null) {
        ZoneDialog(
            onPick = {
                pickingZone = false
                actions.onTimeZone(it)
            },
            onCancel = { pickingZone = false },
        )
    }
}

@Composable
private fun TopBar(title: Int, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = FlywayDimens.space3, end = FlywayDimens.space3, top = FlywayDimens.space3, bottom = FlywayDimens.space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space1),
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.action_back))
        }
        Text(
            text = stringResource(title).toUpperCase(Locale.current),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** The name, with the rule under it; the rule gives way to the reason when the name breaks it ([LeagueName]). */
@Composable
private fun NameField(name: String, onName: (String) -> Unit) {
    val check = LeagueName.check(name)
    val message = when (check) {
        LeagueNameCheck.TOO_SHORT -> pluralStringResource(R.plurals.league_create_name_too_short, LeagueName.MIN_LENGTH, LeagueName.MIN_LENGTH)
        LeagueNameCheck.TOO_LONG -> pluralStringResource(R.plurals.league_create_name_too_long, LeagueName.MAX_LENGTH, LeagueName.MAX_LENGTH)
        LeagueNameCheck.EMPTY, LeagueNameCheck.VALID ->
            pluralStringResource(R.plurals.league_create_name_hint, LeagueName.MAX_LENGTH, LeagueName.MIN_LENGTH, LeagueName.MAX_LENGTH)
    }
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = name,
        onValueChange = onName,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.league_create_name_label)) },
        supportingText = { Text(message) },
        isError = check == LeagueNameCheck.TOO_SHORT || check == LeagueNameCheck.TOO_LONG,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = scheme.surface,
            unfocusedContainerColor = scheme.surface,
            errorContainerColor = scheme.surface,
        ),
    )
}

/** The season and its start, the time zone, the players, and open to solo players. */
@Composable
private fun Choices(state: CreateLeagueState, onStart: () -> Unit, onZone: () -> Unit, onOpen: (Boolean) -> Unit) {
    val colors = FlywayTheme.colors
    // The start is always a Monday: the view model takes no other.
    val season = (SeasonCalendar.of(state.seasonStart) as? SeasonCalendarResult.Valid)?.calendar?.season
    Surface(color = colors.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space1)) {
            ChoiceRow(
                label = stringResource(R.string.league_create_season),
                value = stringResource(
                    R.string.league_create_season_value,
                    season?.let { stringResource(it.nameRes) }.orEmpty(),
                    weekdayText(state.seasonStart),
                ),
                onClickLabel = stringResource(R.string.league_create_change_start),
                onClick = onStart,
            )
            HorizontalDivider(thickness = FlywayDimens.border, color = colors.divider)
            ChoiceRow(
                label = stringResource(R.string.league_create_time_zone),
                value = zoneName(state.timeZone),
                onClickLabel = stringResource(R.string.league_create_change_time_zone),
                onClick = onZone,
            )
            HorizontalDivider(thickness = FlywayDimens.border, color = colors.divider)
            ChoiceRow(
                label = stringResource(R.string.league_create_players),
                value = pluralStringResource(
                    R.plurals.league_create_players_value,
                    LeagueLifecycle.MAX_PLAYERS,
                    LeagueLifecycle.MIN_PLAYERS,
                    LeagueLifecycle.MAX_PLAYERS,
                ),
            )
            HorizontalDivider(thickness = FlywayDimens.border, color = colors.divider)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = FlywayDimens.buttonHeight)
                    .toggleable(value = state.open, role = Role.Switch, onValueChange = onOpen)
                    .padding(vertical = FlywayDimens.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FlywayDimens.space1)) {
                    Text(stringResource(R.string.league_create_open), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.league_create_open_body), style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
                }
                // The row is the control; the switch only shows its state.
                Switch(
                    checked = state.open,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = colors.primary,
                        checkedThumbColor = colors.onPrimary,
                        uncheckedTrackColor = colors.surfaceMuted,
                        uncheckedThumbColor = colors.inkMuted,
                        uncheckedBorderColor = colors.borderStrong,
                    ),
                )
            }
        }
    }
}

/** A label and its value; tapping it changes the value when there is [onClick]. */
@Composable
private fun ChoiceRow(label: String, value: String, onClickLabel: String? = null, onClick: (() -> Unit)? = null) {
    val tap = if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FlywayDimens.buttonHeight)
            .then(tap)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = if (onClick != null) FlywayTheme.colors.pick else FlywayTheme.colors.ink)
    }
}

/** How the field is built, in the board's words: why friends far apart can share a league. */
@Composable
private fun FieldCard() {
    val colors = FlywayTheme.colors
    Surface(color = colors.pickContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space3),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2),
        ) {
            Text(
                text = stringResource(R.string.league_create_field_title).toUpperCase(Locale.current),
                style = MaterialTheme.typography.labelSmall,
                color = colors.pick,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.league_create_field_body), style = MaterialTheme.typography.bodyMedium, color = colors.ink)
            Text(stringResource(R.string.league_create_field_fair), style = MaterialTheme.typography.bodyMedium, color = colors.ink)
        }
    }
}

@Composable
private fun Problem(message: Int) {
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
            text = stringResource(message),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space3),
        )
    }
}

@Composable
private fun Footer(state: CreateLeagueState, onCreate: () -> Unit) {
    val colors = FlywayTheme.colors
    Column(
        modifier = Modifier.padding(start = FlywayDimens.space5, end = FlywayDimens.space5, top = FlywayDimens.space3, bottom = FlywayDimens.space5),
        verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BigButton(
            text = if (state.creating) R.string.league_create_creating else R.string.league_create_button,
            enabled = state.canCreate,
            onClick = onCreate,
        )
        Text(stringResource(R.string.league_create_next), style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
    }
}

@Composable
private fun BigButton(text: Int, enabled: Boolean, onClick: () -> Unit) {
    val colors = FlywayTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FlywayDimens.buttonHeight),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.action,
            contentColor = colors.onAction,
            disabledContainerColor = colors.surfaceMuted,
            disabledContentColor = colors.inkMuted,
        ),
    ) {
        Text(stringResource(text), style = MaterialTheme.typography.titleLarge)
    }
}

/** The league is made: its code to share, then Done. */
@Composable
private fun Created(created: CreatedLeague, actions: CreateLeagueActions) {
    Text(created.name, style = MaterialTheme.typography.titleLarge)
    created.inviteCode?.let { InviteCodeCard(it, onShare = actions.onShare) }
    Text(stringResource(R.string.league_created_body), style = MaterialTheme.typography.bodyMedium, color = FlywayTheme.colors.inkMuted)
    BigButton(text = R.string.league_created_done, enabled = true, onClick = actions.onDone)
}

/** The start date: only the Mondays [SeasonStart.check] allows can be chosen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartDialog(start: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit, onCancel: () -> Unit) {
    // The picker counts days as midnight UTC.
    val mondays = remember(today) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                SeasonStart.check(Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate(), today) == SeasonStartCheck.VALID

            override fun isSelectableYear(year: Int): Boolean = year >= today.year
        }
    }
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = mondays,
    )
    DatePickerDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(
                onClick = { picker.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onCancel() },
                colors = ButtonDefaults.textButtonColors(contentColor = FlywayTheme.colors.pick),
            ) {
                Text(stringResource(R.string.league_create_ok), style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                Text(stringResource(R.string.league_create_cancel), style = MaterialTheme.typography.labelLarge)
            }
        },
    ) {
        DatePicker(
            state = picker,
            showModeToggle = false,
            title = {
                Column(modifier = Modifier.padding(start = FlywayDimens.space6, end = FlywayDimens.space6, top = FlywayDimens.space4)) {
                    Text(stringResource(R.string.league_create_start_title), style = MaterialTheme.typography.labelSmall)
                    Text(stringResource(R.string.league_create_start_rule), style = MaterialTheme.typography.bodySmall)
                }
            },
        )
    }
}

/** Every zone the server accepts ([LeagueTimeZone.ids]), narrowed by what the player types. */
@Composable
private fun ZoneDialog(onPick: (String) -> Unit, onCancel: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = remember(query) {
        val words = query.trim()
        LeagueTimeZone.ids.filter { words.isEmpty() || it.replace('_', ' ').contains(words, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.league_create_time_zone), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.league_create_zone_search)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (matches.isEmpty()) {
                    Text(stringResource(R.string.league_create_zone_none), style = MaterialTheme.typography.bodyMedium)
                }
                LazyColumn(modifier = Modifier.heightIn(max = FlywayDimens.dialogList)) {
                    items(matches) { id ->
                        Text(
                            text = zoneName(id),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = FlywayDimens.tapMin)
                                .clickable(role = Role.Button) { onPick(id) }
                                .padding(vertical = FlywayDimens.space3),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                Text(stringResource(R.string.league_create_cancel), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

/** A zone id as a person reads it: `America/New York`. */
private fun zoneName(id: String): String = id.replace('_', ' ')
