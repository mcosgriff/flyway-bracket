package io.cosgriff.flywaybracket.ui.bracket

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.domain.bracket.Decision
import io.cosgriff.flywaybracket.domain.bracket.DrawPosition
import io.cosgriff.flywaybracket.domain.bracket.Field
import io.cosgriff.flywaybracket.domain.bracket.MatchupView
import io.cosgriff.flywaybracket.domain.bracket.PickScore
import io.cosgriff.flywaybracket.domain.bracket.PickStatus
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.domain.bracket.Side
import io.cosgriff.flywaybracket.domain.bracket.Tally
import io.cosgriff.flywaybracket.domain.bracket.Team
import io.cosgriff.flywaybracket.ui.components.FlywayBottomBar
import io.cosgriff.flywaybracket.ui.components.ServerWaking
import io.cosgriff.flywaybracket.ui.components.TopLevel
import io.cosgriff.flywaybracket.ui.components.nameRes
import io.cosgriff.flywaybracket.ui.components.quarterNameRes
import io.cosgriff.flywaybracket.ui.components.shareText
import io.cosgriff.flywaybracket.ui.components.weekdayText
import io.cosgriff.flywaybracket.ui.theme.FlywayDimens
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The Bracket tab (#42): the player's bracket by round, as one quarter's tree, or as the whole field. Before
 * picks lock a tap on a bird picks it; after, a tap on a matchup opens it ([onOpenMatchup], with the league's
 * id). Which view, round and quarter are shown is kept here, across rotations.
 */
@Composable
fun BracketScreen(
    onNavigate: (TopLevel) -> Unit,
    onOpenMatchup: (leagueId: String, at: DrawPosition) -> Unit,
    viewModel: BracketViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val waking by viewModel.waking.collectAsStateWithLifecycle()
    var layout by rememberSaveable { mutableStateOf(BracketLayout.LIST) }
    var round by rememberSaveable { mutableStateOf<Round?>(null) }
    var quarter by rememberSaveable { mutableStateOf(Field.quarters.first()) }
    BracketContent(
        state = state,
        layout = layout,
        round = round,
        quarter = quarter,
        onNavigate = onNavigate,
        waking = waking,
        actions = BracketActions(
            onLayout = { layout = it },
            onRound = { round = it },
            onQuarter = { quarter = it },
            onPick = viewModel::onPick,
            onConfirmPick = viewModel::onConfirmPick,
            onCancelPick = viewModel::onCancelPick,
            onOpenMatchup = { at -> viewModel.leagueId?.let { onOpenMatchup(it, at) } },
            onDismissNotice = viewModel::onDismissNotice,
            onRetry = viewModel::load,
        ),
    )
}

/** What the Bracket tab's controls do. Each defaults to nothing, for previews and screenshot tests. */
class BracketActions(
    val onLayout: (BracketLayout) -> Unit = {},
    val onRound: (Round) -> Unit = {},
    val onQuarter: (String) -> Unit = {},
    /** A bird was tapped before picks lock: pick it to win that matchup. */
    val onPick: (DrawPosition, Team) -> Unit = { _, _ -> },
    val onConfirmPick: () -> Unit = {},
    val onCancelPick: () -> Unit = {},
    /** A matchup was tapped after picks lock. */
    val onOpenMatchup: (DrawPosition) -> Unit = {},
    val onDismissNotice: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/**
 * @param round the round the list shows; null for the round the bracket opens on ([BracketState.firstRound])
 * @param quarter the quarter the list and the tree show, by its name in a [Team]
 * @param waking the server is most likely waking (#93): while the bracket is read, the tab says so
 */
@Composable
fun BracketContent(
    state: BracketScreenState,
    layout: BracketLayout,
    round: Round?,
    quarter: String,
    onNavigate: (TopLevel) -> Unit,
    modifier: Modifier = Modifier,
    waking: Boolean = false,
    actions: BracketActions = BracketActions(),
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { FlywayBottomBar(selected = TopLevel.Bracket, onNavigate = onNavigate) },
    ) { insets ->
        Column(modifier = Modifier.padding(insets).fillMaxSize()) {
            when (state) {
                BracketScreenState.Loading -> Message(title = R.string.nav_bracket) {
                    Text(stringResource(R.string.bracket_loading), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (waking) ServerWaking()
                }
                is BracketScreenState.NoLeague -> NoLeague(state.forming, onGoToLeague = { onNavigate(TopLevel.League) })
                is BracketScreenState.Failed -> Message(title = R.string.nav_bracket) {
                    Text(stringResource(state.message), style = MaterialTheme.typography.bodyLarge)
                    PrimaryButton(R.string.bracket_try_again, onClick = actions.onRetry)
                }
                is BracketScreenState.Ready -> Ready(state.bracket, layout, round ?: state.bracket.firstRound, quarter, actions)
            }
        }
    }
    val confirm = (state as? BracketScreenState.Ready)?.bracket?.confirm
    if (confirm != null) ChangePickDialog(confirm, onConfirm = actions.onConfirmPick, onCancel = actions.onCancelPick)
}

/** A title over a short message: the states with no bracket to show. */
@Composable
private fun Message(title: Int, body: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = FlywayDimens.space5, vertical = FlywayDimens.space4),
        verticalArrangement = Arrangement.spacedBy(FlywayDimens.space4),
    ) {
        Text(
            text = stringResource(title).toUpperCase(Locale.current),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        body()
    }
}

@Composable
private fun NoLeague(forming: Boolean, onGoToLeague: () -> Unit) {
    Message(title = R.string.nav_bracket) {
        val colors = FlywayTheme.colors
        Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(FlywayDimens.space5),
                verticalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
            ) {
                Text(
                    text = stringResource(if (forming) R.string.bracket_forming_title else R.string.bracket_no_league_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(if (forming) R.string.bracket_forming_body else R.string.bracket_no_league_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.inkMuted,
                )
                PrimaryButton(R.string.bracket_go_to_league, onClick = onGoToLeague)
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: Int, onClick: () -> Unit) {
    val colors = FlywayTheme.colors
    Button(
        onClick = onClick,
        modifier = Modifier.heightIn(min = FlywayDimens.tapMin),
        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
    ) {
        Text(stringResource(text), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Ready(bracket: BracketState, layout: BracketLayout, round: Round, quarter: String, actions: BracketActions) {
    val header: @Composable ColumnScope.() -> Unit = {
        Text(
            text = stringResource(R.string.bracket_title, stringResource(bracket.season.nameRes)).toUpperCase(Locale.current),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        LayoutSwitch(layout, actions.onLayout)
        bracket.notice?.let { Notice(it, actions.onDismissNotice) }
        bracket.problem?.let { Problem(it, actions.onRetry) }
    }
    val gutter = Modifier.padding(horizontal = FlywayDimens.space5)
    when (layout) {
        BracketLayout.LIST -> Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = FlywayDimens.space4),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
        ) {
            Column(gutter, verticalArrangement = Arrangement.spacedBy(FlywayDimens.space3), content = header)
            RoundTabs(bracket, round, actions.onRound)
            if (round <= Round.QUARTERFINAL) QuarterChips(quarter, actions.onQuarter)
            Column(gutter, verticalArrangement = Arrangement.spacedBy(FlywayDimens.space3)) {
                RoundList(bracket, round, quarter, actions)
            }
        }
        BracketLayout.TREE -> Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = FlywayDimens.space4),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
        ) {
            Column(gutter, verticalArrangement = Arrangement.spacedBy(FlywayDimens.space3), content = header)
            QuarterChips(quarter, actions.onQuarter)
            bracket.liveRound?.let { LiveLine(bracket, it, gutter) }
            QuarterTree(bracket, quarter, onOpen = actions.onOpenMatchup.takeIf { bracket.locked })
            Legend(Modifier.padding(horizontal = FlywayDimens.space5))
        }
        BracketLayout.FIELD -> Column(
            modifier = Modifier.padding(top = FlywayDimens.space4),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
        ) {
            Column(gutter, verticalArrangement = Arrangement.spacedBy(FlywayDimens.space3)) {
                header()
                FieldSummary(bracket)
                Legend()
            }
            WholeField(bracket, onOpen = actions.onOpenMatchup.takeIf { bracket.locked }, modifier = Modifier.weight(1f))
        }
    }
}

/** The three views, as a row of choices. */
@Composable
private fun LayoutSwitch(layout: BracketLayout, onLayout: (BracketLayout) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space2)) {
        BracketLayout.entries.forEach { option ->
            val text = when (option) {
                BracketLayout.LIST -> R.string.bracket_layout_list
                BracketLayout.TREE -> R.string.bracket_layout_tree
                BracketLayout.FIELD -> R.string.bracket_layout_field
            }
            Pill(stringResource(text), selected = option == layout, onClick = { onLayout(option) }, role = Role.RadioButton)
        }
    }
}

/** A rounded choice: filled when [selected], else outlined. */
@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit, role: Role, modifier: Modifier = Modifier) {
    val colors = FlywayTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = FlywayDimens.tapMin)
            .semantics {
                this.selected = selected
                this.role = role
            },
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) colors.primary else colors.surface,
        contentColor = if (selected) colors.onPrimary else colors.ink,
        border = if (selected) null else BorderStroke(FlywayDimens.border, colors.border),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = FlywayDimens.space4)) {
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** The six rounds: the one shown filled, decided ones shaded, the live one named so. */
@Composable
private fun RoundTabs(bracket: BracketState, round: Round, onRound: (Round) -> Unit) {
    val colors = FlywayTheme.colors
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = FlywayDimens.space5),
        horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space2),
    ) {
        Round.entries.forEach { option ->
            val selected = option == round
            val decided = bracket.view.isDecided(option)
            val live = option == bracket.liveRound
            val name = stringResource(option.nameRes)
            val label = if (live) stringResource(R.string.bracket_round_live, name) else name
            Surface(
                onClick = { onRound(option) },
                modifier = Modifier
                    .heightIn(min = FlywayDimens.tapMin)
                    .semantics {
                        this.selected = selected
                        this.role = Role.Tab
                    },
                shape = MaterialTheme.shapes.small,
                color = when {
                    selected -> colors.inverse
                    decided -> colors.surfaceMuted
                    else -> Color.Transparent
                },
                contentColor = when {
                    selected -> colors.onInverse
                    live -> colors.live
                    decided -> colors.ink
                    else -> colors.inkMuted
                },
                border = if (selected || decided) null else BorderStroke(FlywayDimens.border, colors.border),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = FlywayDimens.space3)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun QuarterChips(quarter: String, onQuarter: (String) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = FlywayDimens.space5),
        horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space2),
    ) {
        Field.quarters.forEach { option ->
            Pill(stringResource(quarterNameRes(option)), selected = option == quarter, onClick = { onQuarter(option) }, role = Role.Tab)
        }
    }
}

/** Why the picks changed without the player: said once, until OK. */
@Composable
private fun Notice(notice: BracketNotice, onDismiss: () -> Unit) {
    val colors = FlywayTheme.colors
    Surface(
        color = colors.warningContainer,
        contentColor = colors.onWarningContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(FlywayDimens.border, colors.warningBorder),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = FlywayDimens.space4, end = FlywayDimens.space1, top = FlywayDimens.space1, bottom = FlywayDimens.space1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space2),
        ) {
            Text(
                text = stringResource(
                    when (notice) {
                        BracketNotice.LOCKED_BEFORE_SENT -> R.string.bracket_notice_locked
                        BracketNotice.BRACKET_CHANGED -> R.string.bracket_notice_changed
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(vertical = FlywayDimens.space2),
            )
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = colors.onWarningContainer)) {
                Text(stringResource(R.string.bracket_notice_ok), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** The last update or pick failed: why, and a retry. */
@Composable
private fun Problem(message: Int, onRetry: () -> Unit) {
    val colors = FlywayTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space2)) {
        Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, color = colors.inkMuted, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry, colors = ButtonDefaults.textButtonColors(contentColor = colors.pick)) {
            Text(stringResource(R.string.bracket_try_again), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** One round of one quarter (or the semifinals or final), as cards; a decided round with its points. */
@Composable
private fun RoundList(bracket: BracketState, round: Round, quarter: String, actions: BracketActions) {
    val colors = FlywayTheme.colors
    val matchups = bracket.view.round(round).filter { round > Round.QUARTERFINAL || Field.quarterOf(it.at) == quarter }
    val decided = bracket.view.isDecided(round)
    if (!bracket.locked) {
        val lockTime = bracket.locksAt.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
        Text(
            text = stringResource(R.string.bracket_picks_status, bracket.view.picks, bracket.view.matchups.size, lockTime),
            style = MaterialTheme.typography.labelMedium,
            color = colors.live,
        )
        Text(stringResource(R.string.bracket_tap_to_pick), style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
        bracket.previewUntil?.let {
            Text(stringResource(R.string.bracket_preview, weekdayText(it)), style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
        }
    } else if (round == bracket.liveRound) {
        LiveLine(bracket, round)
    }
    if (bracket.picksUnsent) Text(stringResource(R.string.bracket_unsent), style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
    if (decided) {
        RoundDoneBanner(bracket, round)
        matchups.forEach { ResultCard(it, onOpen = actions.onOpenMatchup) }
        Footnote(R.string.bracket_key_done)
    } else {
        matchups.forEach { MatchupCard(it, bracket.locked, actions) }
        Footnote(R.string.bracket_key_list)
    }
}

/** The round being played, and the day it ends. */
@Composable
internal fun LiveLine(bracket: BracketState, round: Round, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.bracket_live, stringResource(round.nameRes), weekdayText(bracket.roundEnds.getValue(round))),
        style = MaterialTheme.typography.labelMedium,
        color = FlywayTheme.colors.live,
        modifier = modifier,
    )
}

@Composable
private fun Footnote(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodySmall, color = FlywayTheme.colors.inkMuted)
}

/** A decided round's header: its points and right picks. */
@Composable
private fun RoundDoneBanner(bracket: BracketState, round: Round) {
    val colors = FlywayTheme.colors
    val score = bracket.view.score(round)
    val total = bracket.view.round(round).size
    Surface(color = colors.primary, contentColor = colors.onPrimary, shape = MaterialTheme.shapes.medium) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space3).semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FlywayDimens.space1)) {
                Text(
                    text = stringResource(R.string.bracket_round_decided, stringResource(round.nameRes)).toUpperCase(Locale.current),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onPrimaryMuted,
                )
                Text(
                    text = pluralStringResource(R.plurals.bracket_round_summary, total, weekdayText(bracket.roundEnds.getValue(round)), score.rightPicks, total),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(pluralStringResource(R.plurals.bracket_points, score.points, score.points), style = FlywayTheme.text.scoreLarge)
        }
    }
}

/** The bird a side holds: the real one, the player's pick to get here, or none yet. */
internal val Side.team: Team?
    get() = when (this) {
        is Side.Known -> team
        is Side.YourPick -> team
        is Side.WinnerOf -> null
    }

/**
 * A matchup of a round not decided yet. Before the lock each bird is a button that picks it, and a screen
 * reader offers the same as actions on the one matchup; after the lock the card opens the matchup.
 */
@Composable
private fun MatchupCard(matchup: MatchupView, locked: Boolean, actions: BracketActions) {
    val colors = FlywayTheme.colors
    val description = matchupDescription(matchup)
    val pickable = listOf(matchup.upper, matchup.lower).mapNotNull { side -> side.team?.takeIf { side !is Side.YourPick || !side.out } }
    val pickLabels = pickable.map { stringResource(R.string.bracket_a11y_pick_action, it.commonName) }
    val openLabel = stringResource(R.string.bracket_a11y_open)
    val bonus = upsetBonus(matchup)
    val semantics = if (locked) {
        Modifier
            .clickable(onClickLabel = openLabel) { actions.onOpenMatchup(matchup.at) }
            .clearAndSetSemantics { contentDescription = description }
    } else {
        Modifier.clearAndSetSemantics {
            contentDescription = description
            customActions = pickable.zip(pickLabels) { bird, label ->
                CustomAccessibilityAction(label) {
                    actions.onPick(matchup.at, bird)
                    true
                }
            }
        }
    }
    Surface(
        color = colors.surface,
        shape = MaterialTheme.shapes.large,
        border = if (bonus != null) BorderStroke(FlywayDimens.emphasisBorder, colors.action) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(semantics) {
            if (bonus != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(colors.action).padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space1),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.bracket_upset_pick).toUpperCase(Locale.current), style = MaterialTheme.typography.labelSmall, color = colors.onAction)
                    Text(stringResource(R.string.bracket_upset_bonus, bonus), style = MaterialTheme.typography.labelSmall, color = colors.onAction)
                }
            }
            SideRow(matchup.upper, matchup, onPick = if (locked) null else actions.onPick)
            HorizontalDivider(color = colors.divider, thickness = FlywayDimens.border)
            SideRow(matchup.lower, matchup, onPick = if (locked) null else actions.onPick)
        }
    }
}

/** The upset bonus the player's pick earns if it wins, when it is the lower seed of two known birds. */
private fun upsetBonus(matchup: MatchupView): Int? {
    val pick = matchup.pick ?: return null
    if (matchup.result != null || matchup.pickStatus != PickStatus.ALIVE) return null
    val other = listOf(matchup.upper, matchup.lower).mapNotNull { it.team }.singleOrNull { it != pick } ?: return null
    return PickScore.of(matchup.at.round, pick.commonName, Decision(pick, other, Decision.Reason.HIGHER_SHARE)).upsetBonus.takeIf { it > 0 }
}

@Composable
private fun SideRow(side: Side, matchup: MatchupView, onPick: ((DrawPosition, Team) -> Unit)?) {
    val colors = FlywayTheme.colors
    val team = side.team
    val out = side is Side.YourPick && side.out
    val canPick = onPick != null && team != null && !out
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (canPick && team != null && onPick != null) Modifier.clickable { onPick(matchup.at, team) } else Modifier)
            .heightIn(min = FlywayDimens.tapMin)
            .padding(horizontal = FlywayDimens.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
    ) {
        Text(
            text = team?.seed?.let { stringResource(R.string.figure, it) } ?: "",
            style = FlywayTheme.text.seed,
            color = colors.inkMuted,
            modifier = Modifier.widthIn(min = FlywayDimens.seedColumn),
        )
        val known = side is Side.Known
        Text(
            text = when {
                team == null -> stringResource(R.string.bracket_to_come)
                out -> stringResource(R.string.bracket_side_out, team.commonName)
                else -> team.commonName
            },
            style = if (known) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            color = if (known) colors.ink else colors.inkMuted,
            modifier = Modifier.weight(1f).padding(vertical = FlywayDimens.space2),
        )
        if (team != null && matchup.pick == team) PickBadge()
    }
}

@Composable
private fun PickBadge(text: Int = R.string.bracket_your_pick) {
    val colors = FlywayTheme.colors
    Surface(
        color = Color.Transparent,
        contentColor = colors.pick,
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(FlywayDimens.border, colors.pick),
    ) {
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = TextStyle.Default.letterSpacing),
            modifier = Modifier.padding(horizontal = FlywayDimens.space2, vertical = FlywayDimens.space1 / 2),
        )
    }
}

/** A decided matchup: both birds with their shares, the winner in bold, and what the pick earned. */
@Composable
private fun ResultCard(matchup: MatchupView, onOpen: (DrawPosition) -> Unit) {
    val colors = FlywayTheme.colors
    val description = matchupDescription(matchup)
    val openLabel = stringResource(R.string.bracket_a11y_open)
    val result = matchup.result ?: return
    Surface(color = colors.surface, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .clickable(onClickLabel = openLabel) { onOpen(matchup.at) }
                .clearAndSetSemantics { contentDescription = description },
        ) {
            Column(Modifier.weight(1f)) {
                ResultRow(matchup.upper, result.upper, matchup)
                HorizontalDivider(color = colors.divider, thickness = FlywayDimens.border)
                ResultRow(matchup.lower, result.lower, matchup)
            }
            val score = result.score
            val missed = score.total == 0
            Column(
                modifier = Modifier
                    .width(FlywayDimens.resultColumn)
                    .fillMaxHeight()
                    .background(if (missed) colors.surfaceMuted else colors.pickContainer),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val tint = if (missed) colors.live else colors.pick
                Text(
                    text = if (missed) stringResource(R.string.figure, 0) else stringResource(R.string.bracket_points_won, score.total),
                    style = FlywayTheme.text.score,
                    color = tint,
                )
                Text(
                    text = stringResource(
                        when {
                            matchup.pick == null -> R.string.bracket_result_no_pick
                            missed -> R.string.bracket_result_missed
                            score.upsetBonus > 0 -> R.string.bracket_result_upset
                            else -> R.string.bracket_result_right
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = tint,
                )
            }
        }
    }
}

@Composable
private fun ResultRow(side: Side, tally: Tally, matchup: MatchupView) {
    val colors = FlywayTheme.colors
    val team = side.team ?: return
    val won = matchup.winner == team
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = FlywayDimens.space6 + FlywayDimens.space4).padding(horizontal = FlywayDimens.space3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space2),
    ) {
        Text(stringResource(R.string.figure, team.seed), style = FlywayTheme.text.seed, color = colors.inkMuted, modifier = Modifier.widthIn(min = FlywayDimens.seedColumn))
        Text(
            text = team.commonName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (won) FontWeight.SemiBold else FontWeight.Normal,
            color = if (won) colors.ink else colors.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (matchup.pick == team) PickBadge(R.string.bracket_you)
        Text(shareText(tally), style = MaterialTheme.typography.labelMedium, color = if (won) colors.ink else colors.inkMuted)
    }
}

/**
 * What a screen reader says for a matchup, as one item: both birds with their seeds (and shares, once
 * decided), the pick, and the result with what it earned.
 */
@Composable
internal fun matchupDescription(matchup: MatchupView): String {
    val result = matchup.result
    @Composable
    fun bird(side: Side, tally: Tally?): String {
        val team = side.team ?: return stringResource(R.string.bracket_to_come)
        return when {
            tally != null -> stringResource(R.string.bracket_a11y_bird_share, team.seed, team.commonName, shareText(tally))
            side is Side.YourPick && side.out -> stringResource(R.string.bracket_side_out, stringResource(R.string.bracket_a11y_bird_picked, team.seed, team.commonName))
            side is Side.YourPick -> stringResource(R.string.bracket_a11y_bird_picked, team.seed, team.commonName)
            else -> stringResource(R.string.bracket_a11y_bird, team.seed, team.commonName)
        }
    }
    val parts = mutableListOf(stringResource(R.string.bracket_a11y_versus, bird(matchup.upper, result?.upper), bird(matchup.lower, result?.lower)))
    parts += matchup.pick?.let { stringResource(R.string.bracket_a11y_pick, it.commonName) } ?: stringResource(R.string.bracket_a11y_no_pick)
    val winner = matchup.winner
    if (winner != null && result != null) {
        parts += stringResource(R.string.bracket_a11y_won, winner.commonName)
        if (matchup.pick != null) parts += pluralStringResource(R.plurals.bracket_a11y_scored, result.score.total, result.score.total)
    }
    return parts.joinToString(" ")
}

/** Asks before a pick that clears later picks, naming each. */
@Composable
private fun ChangePickDialog(change: PickChange, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val colors = FlywayTheme.colors
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.bracket_change_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2)) {
                Text(
                    text = pluralStringResource(R.plurals.bracket_change_body, change.cleared.size, change.cleared.size, change.bird.commonName),
                    style = MaterialTheme.typography.bodyLarge,
                )
                change.cleared.forEach {
                    Text(stringResource(R.string.bracket_change_cleared, stringResource(it.round.nameRes), it.bird), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = colors.pick)) {
                Text(stringResource(R.string.bracket_change_confirm), style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                Text(stringResource(R.string.bracket_change_cancel), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

@Preview
@Composable
private fun BracketPreview() {
    FlywayTheme {
        BracketContent(BracketScreenState.Ready(previewLocked), BracketLayout.LIST, round = null, quarter = Field.quarters.first(), onNavigate = {})
    }
}
