package io.cosgriff.flywaybracket.ui.league

import android.icu.text.MessageFormat
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.toUpperCase
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.domain.league.CloseReason
import io.cosgriff.flywaybracket.domain.league.InviteCode
import io.cosgriff.flywaybracket.domain.league.LeagueLifecycle
import io.cosgriff.flywaybracket.domain.league.LeaguePhase
import io.cosgriff.flywaybracket.ui.components.FlywayBottomBar
import io.cosgriff.flywaybracket.ui.components.InviteCodeCard
import io.cosgriff.flywaybracket.ui.components.TopLevel
import io.cosgriff.flywaybracket.ui.components.nameRes
import io.cosgriff.flywaybracket.ui.components.shareInvite
import io.cosgriff.flywaybracket.ui.components.weekdayText
import io.cosgriff.flywaybracket.ui.theme.FlywayDimens
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The League tab (#40): the player's league, or the Join view when they have none. Stands behind the consent
 * page, so it is only shown once the phone has an account (#38).
 *
 * @param onCreate opens Create a league
 */
@Composable
fun LeagueScreen(onNavigate: (TopLevel) -> Unit, onCreate: () -> Unit, viewModel: LeagueViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val current = state
    BackHandler(enabled = current is LeagueTabState.Join && current.join.canGoBack, onBack = viewModel::onBackFromJoin)
    LeagueContent(
        state = current,
        onNavigate = onNavigate,
        actions = LeagueActions(
            onRetry = viewModel::refresh,
            onCode = viewModel::onCode,
            onJoin = viewModel::onJoin,
            onFindLeague = viewModel::onFindLeague,
            onCreate = onCreate,
            onBackFromJoin = viewModel::onBackFromJoin,
            onJoinAgain = viewModel::onJoinAgain,
            onShare = {
                val league = (current as? LeagueTabState.InLeague)?.league
                league?.inviteCode?.let { shareInvite(context, league.name, it) }
            },
            onLeave = viewModel::onLeave,
            onConfirmLeave = viewModel::onConfirmLeave,
            onCancelLeave = viewModel::onCancelLeave,
        ),
    )
}

/** What the League tab's controls do. Each defaults to nothing, for previews and screenshot tests. */
class LeagueActions(
    val onRetry: () -> Unit = {},
    val onCode: (String) -> Unit = {},
    val onJoin: () -> Unit = {},
    val onFindLeague: () -> Unit = {},
    val onCreate: () -> Unit = {},
    /** Back from the Join view to the finished or closed league it was opened from. */
    val onBackFromJoin: () -> Unit = {},
    /** Join a league, from a finished or closed one. */
    val onJoinAgain: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onLeave: () -> Unit = {},
    val onConfirmLeave: () -> Unit = {},
    val onCancelLeave: () -> Unit = {},
)

@Composable
fun LeagueContent(
    state: LeagueTabState,
    onNavigate: (TopLevel) -> Unit,
    modifier: Modifier = Modifier,
    actions: LeagueActions = LeagueActions(),
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { FlywayBottomBar(selected = TopLevel.League, onNavigate = onNavigate) },
    ) { insets ->
        Column(
            modifier = Modifier
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FlywayDimens.space5, vertical = FlywayDimens.space4),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
        ) {
            when (state) {
                LeagueTabState.Loading -> {
                    Title(stringResource(R.string.nav_league))
                    Muted(stringResource(R.string.league_loading))
                }
                is LeagueTabState.Failed -> {
                    Title(stringResource(R.string.nav_league))
                    Text(stringResource(state.message), style = MaterialTheme.typography.bodyLarge)
                    PrimaryButton(R.string.league_try_again, onClick = actions.onRetry)
                }
                is LeagueTabState.Join -> JoinView(state.join, actions)
                is LeagueTabState.InLeague -> LeagueBody(state.league, actions)
            }
        }
    }
    val league = (state as? LeagueTabState.InLeague)?.league
    if (league != null && league.confirmLeave) LeaveDialog(league, onConfirm = actions.onConfirmLeave, onCancel = actions.onCancelLeave)
}

@Composable
private fun Title(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.toUpperCase(Locale.current),
        style = MaterialTheme.typography.headlineMedium,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PrimaryButton(@StringRes text: Int, onClick: () -> Unit, enabled: Boolean = true) {
    val colors = FlywayTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = FlywayDimens.tapMin),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            disabledContainerColor = colors.surfaceMuted,
            disabledContentColor = colors.inkMuted,
        ),
    ) {
        Text(stringResource(text), style = MaterialTheme.typography.labelLarge)
    }
}

/** A card on the surface colour: the canvas's sections. */
@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(FlywayDimens.space4),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
            content = content,
        )
    }
}

/** Why the last call failed, read out when it appears; [onRetry] offers Try again when given. */
@Composable
private fun Problem(@StringRes message: Int, onRetry: (() -> Unit)? = null) {
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
        Column(modifier = Modifier.padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space2)) {
            Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = FlywayDimens.space1))
            if (onRetry != null) {
                TextButton(onClick = onRetry, colors = ButtonDefaults.textButtonColors(contentColor = colors.onWarningContainer)) {
                    Text(stringResource(R.string.league_try_again), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// Join a league

@Composable
private fun JoinView(join: JoinState, actions: LeagueActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (join.canGoBack) {
            IconButton(onClick = actions.onBackFromJoin) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.action_back))
            }
        }
        Title(stringResource(R.string.league_join_title))
    }
    Muted(stringResource(R.string.league_join_intro))
    join.problem?.let { Problem(it) }
    CodeCard(join, actions)
    Card {
        Text(stringResource(R.string.league_join_create_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Muted(stringResource(R.string.league_join_create_body))
        PrimaryButton(R.string.league_join_create_button, onClick = actions.onCreate, enabled = join.busy == null)
    }
    Card {
        Text(stringResource(R.string.league_join_solo_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Muted(stringResource(R.string.league_join_solo_body))
        val colors = FlywayTheme.colors
        OutlinedButton(
            onClick = actions.onFindLeague,
            enabled = join.busy == null,
            modifier = Modifier.heightIn(min = FlywayDimens.tapMin),
            border = BorderStroke(FlywayDimens.border, colors.borderStrong),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.ink, disabledContentColor = colors.inkMuted),
        ) {
            Text(
                stringResource(if (join.busy == JoinBusy.FINDING) R.string.league_join_finding else R.string.league_join_find),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** The invite code and Join. Join is on once the code has the shape of one ([InviteCode.normalize]). */
@Composable
private fun CodeCard(join: JoinState, actions: LeagueActions) {
    val colors = FlywayTheme.colors
    val valid = InviteCode.normalize(join.code) != null
    // Said only once enough is typed: a code part-way in is not wrong yet.
    val wrong = !valid && join.code.trim().length >= InviteCode.LENGTH
    Card {
        Text(stringResource(R.string.league_join_code_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Row(horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space2)) {
            OutlinedTextField(
                value = join.code,
                onValueChange = actions.onCode,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.league_join_code_label)) },
                supportingText = { Text(stringResource(if (wrong) R.string.league_join_code_invalid else R.string.league_join_code_hint)) },
                isError = wrong,
                singleLine = true,
                textStyle = FlywayTheme.text.seed,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (valid && join.busy == null) actions.onJoin() }),
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.ground,
                    unfocusedContainerColor = colors.ground,
                    errorContainerColor = colors.ground,
                ),
            )
            Button(
                onClick = actions.onJoin,
                enabled = valid && join.busy == null,
                modifier = Modifier
                    .padding(top = FlywayDimens.space2)
                    .heightIn(min = FlywayDimens.fieldHeight),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.action,
                    contentColor = colors.onAction,
                    disabledContainerColor = colors.surfaceMuted,
                    disabledContentColor = colors.inkMuted,
                ),
            ) {
                Text(
                    stringResource(if (join.busy == JoinBusy.JOINING) R.string.league_join_joining else R.string.league_join_button),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

// The league

@Composable
private fun LeagueBody(league: LeagueView, actions: LeagueActions) {
    Header(league, actions.onLeave)
    league.problem?.let { Problem(it, onRetry = actions.onRetry) }
    when (league.phase) {
        LeaguePhase.FORMING -> {
            Forming(league)
            league.inviteCode?.let { code ->
                InviteCodeCard(code, onShare = actions.onShare)
                Muted(stringResource(R.string.league_invite_hint))
            }
            Table(league, withPoints = false)
        }
        LeaguePhase.RUNNING -> {
            league.you?.let { Hero(league, it) }
            Table(league, withPoints = true)
        }
        LeaguePhase.FINISHED -> {
            league.you?.let { Hero(league, it) }
            Table(league, withPoints = true)
            Card {
                Text(stringResource(R.string.league_finished_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Muted(stringResource(R.string.league_finished_body))
                PrimaryButton(R.string.league_join_again, onClick = actions.onJoinAgain)
            }
        }
        LeaguePhase.CLOSED -> Closed(league.closedBecause, actions.onJoinAgain)
    }
    Text(
        text = stringResource(R.string.league_updated, dateTimeText(league.fetchedAt)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The overline, the name, the state with the players, and the dates; the menu holds Leave league. */
@Composable
private fun Header(league: LeagueView, onLeave: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FlywayDimens.space1)) {
            Text(
                text = (league.season?.let { stringResource(R.string.league_overline, stringResource(it.nameRes)) } ?: stringResource(R.string.league_overline_no_season))
                    .toUpperCase(Locale.current),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Title(league.name)
            Muted(
                stringResource(
                    R.string.league_status,
                    stringResource(league.phase.stateRes),
                    pluralStringResource(R.plurals.league_players_of, league.capacity, league.players, league.capacity),
                ),
            )
            if (league.seasonStart != null && league.seasonEnd != null && league.timeZone != null) {
                Muted(stringResource(R.string.league_dates, weekdayText(league.seasonStart), weekdayText(league.seasonEnd), league.timeZone))
            }
        }
        if (league.canLeave) LeaveMenu(leaving = league.leaving, onLeave = onLeave)
    }
}

@Composable
private fun LeaveMenu(leaving: Boolean, onLeave: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        IconButton(onClick = { open = true }, enabled = !leaving) {
            Icon(painterResource(R.drawable.ic_more), contentDescription = stringResource(R.string.league_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = MaterialTheme.colorScheme.surface) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.league_leave), style = MaterialTheme.typography.labelLarge, color = FlywayTheme.colors.danger) },
                onClick = {
                    open = false
                    onLeave()
                },
            )
        }
    }
}

/** While the league forms: how many have joined of the 8 a bracket needs, and when the season starts. */
@Composable
private fun Forming(league: LeagueView) {
    val colors = FlywayTheme.colors
    val needed = LeagueLifecycle.MIN_PLAYERS
    Surface(color = colors.primary, contentColor = colors.onPrimary, shape = MaterialTheme.shapes.large) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FlywayDimens.space5, vertical = FlywayDimens.space4),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2),
        ) {
            Text(stringResource(R.string.league_forming_overline).toUpperCase(Locale.current), style = MaterialTheme.typography.labelSmall, color = colors.onPrimaryMuted)
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                Text(
                    text = if (league.players < needed) {
                        stringResource(R.string.league_forming_count, league.players, needed)
                    } else {
                        stringResource(R.string.figure, league.players)
                    },
                    style = FlywayTheme.text.scoreHero,
                )
                Text(
                    text = if (league.players < needed) {
                        pluralStringResource(R.plurals.league_forming_needed, needed - league.players, needed - league.players)
                    } else {
                        stringResource(R.string.league_forming_ready)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryMuted,
                    modifier = Modifier.padding(bottom = FlywayDimens.space2),
                )
            }
            league.seasonStart?.let { start ->
                Text(
                    text = pluralStringResource(R.plurals.league_forming_body, needed, weekdayText(start), needed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryMuted,
                )
            }
        }
    }
}

/** The player's rank and points, and the round being played or that the season is over. */
@Composable
private fun Hero(league: LeagueView, you: StandingRow) {
    val colors = FlywayTheme.colors
    Surface(color = colors.primary, contentColor = colors.onPrimary, shape = MaterialTheme.shapes.large) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FlywayDimens.space5, vertical = FlywayDimens.space4)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(if (league.phase == LeaguePhase.FINISHED) R.string.league_final_rank else R.string.league_your_rank).toUpperCase(Locale.current),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onPrimaryMuted,
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space2)) {
                    // "3rd", "3e", "3.º": the phone's language writes the place.
                    Text(MessageFormat.format("{0,ordinal}", you.rank), style = FlywayTheme.text.scoreHero)
                    Text(
                        text = pluralStringResource(R.plurals.league_rank_of, league.standings.size, league.standings.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onPrimaryMuted,
                        modifier = Modifier.padding(bottom = FlywayDimens.space2),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(pluralStringResource(R.plurals.league_points, you.points, you.points), style = FlywayTheme.text.scoreLarge)
                Text(
                    text = league.round?.let { stringResource(R.string.league_round_of, it.ordinal + 1, Round.entries.size) }
                        ?: stringResource(R.string.league_season_over),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onPrimaryMuted,
                )
            }
        }
    }
}

/**
 * The players, best first. With [withPoints], the standings: rank, right picks and points. The player's own
 * row is tinted and says "You".
 */
@Composable
private fun Table(league: LeagueView, withPoints: Boolean) {
    if (league.standings.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2)) {
        Text(
            text = stringResource(if (withPoints) R.string.league_standings_title else R.string.league_players_title).toUpperCase(Locale.current),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
            Column {
                league.standings.forEachIndexed { i, row ->
                    if (i > 0) HorizontalDivider(thickness = FlywayDimens.border, color = FlywayTheme.colors.divider)
                    StandingLine(row, withPoints, league.decidedMatchups)
                }
            }
        }
        if (withPoints) {
            Text(stringResource(R.string.league_standings_key), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StandingLine(row: StandingRow, withPoints: Boolean, decided: Int) {
    val colors = FlywayTheme.colors
    val ink = if (row.you) colors.pick else colors.ink
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (row.you) colors.pickContainer else colors.surface)
            .heightIn(min = FlywayDimens.tapMin)
            .padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space2)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
    ) {
        if (withPoints) {
            Text(
                text = stringResource(R.string.figure, row.rank),
                style = FlywayTheme.text.seed,
                color = colors.inkMuted,
                modifier = Modifier.widthIn(min = FlywayDimens.seedColumn),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(row.name, style = MaterialTheme.typography.titleMedium, color = ink)
            if (row.you) Text(stringResource(R.string.league_you), style = MaterialTheme.typography.labelMedium, color = colors.pick)
        }
        if (withPoints) {
            Text(stringResource(R.string.league_right_picks, row.rightPicks, decided), style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
            Text(
                text = stringResource(R.string.figure, row.points),
                style = FlywayTheme.text.score,
                color = ink,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = FlywayDimens.pointsColumn),
            )
        }
    }
}

/** A league that never started: why, in the app's words, and Join a league. */
@Composable
private fun Closed(reason: CloseReason?, onJoinAgain: () -> Unit) {
    val colors = FlywayTheme.colors
    Surface(
        color = colors.warningContainer,
        contentColor = colors.onWarningContainer,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(FlywayDimens.border, colors.warningBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(FlywayDimens.space4), verticalArrangement = Arrangement.spacedBy(FlywayDimens.space3)) {
            Text(stringResource(R.string.league_closed_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            when (reason) {
                CloseReason.TOO_FEW_PLAYERS -> Text(stringResource(R.string.league_closed_too_few_players), style = MaterialTheme.typography.bodyMedium)
                CloseReason.TOO_FEW_BIRDS -> Text(stringResource(R.string.league_closed_too_few_birds), style = MaterialTheme.typography.bodyMedium)
                // A closed league always has a reason; an older phone's copy might not.
                null -> Unit
            }
            Text(stringResource(R.string.league_closed_body), style = MaterialTheme.typography.bodyMedium)
            PrimaryButton(R.string.league_join_again, onClick = onJoinAgain)
        }
    }
}

/** Asks before leaving, saying what is lost: picks before the season, scoring and the table after it starts. */
@Composable
private fun LeaveDialog(league: LeagueView, onConfirm: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.league_leave_title, league.name), style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                text = stringResource(if (league.phase == LeaguePhase.RUNNING) R.string.league_leave_running else R.string.league_leave_forming),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = FlywayTheme.colors.danger)) {
                Text(stringResource(R.string.league_leave_confirm), style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                Text(stringResource(R.string.league_leave_cancel), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

@get:StringRes
private val LeaguePhase.stateRes: Int
    get() = when (this) {
        LeaguePhase.FORMING -> R.string.league_state_forming
        LeaguePhase.RUNNING -> R.string.league_state_running
        LeaguePhase.FINISHED -> R.string.league_state_finished
        LeaguePhase.CLOSED -> R.string.league_state_closed
    }

/** A date and time, medium and short, in the phone's language: "Feb 17, 2027, 12:00 PM". */
@Composable
private fun dateTimeText(at: LocalDateTime): String =
    at.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(LocalConfiguration.current.locales[0]))
