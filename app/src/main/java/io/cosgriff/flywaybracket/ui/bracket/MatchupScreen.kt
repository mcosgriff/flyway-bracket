package io.cosgriff.flywaybracket.ui.bracket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.domain.bracket.Decision
import io.cosgriff.flywaybracket.domain.bracket.DrawPosition
import io.cosgriff.flywaybracket.domain.bracket.MatchupRule
import io.cosgriff.flywaybracket.domain.bracket.PickScore
import io.cosgriff.flywaybracket.domain.bracket.PickStatus
import io.cosgriff.flywaybracket.ui.components.nameRes
import io.cosgriff.flywaybracket.ui.components.quarterNameRes
import io.cosgriff.flywaybracket.ui.components.shareText
import io.cosgriff.flywaybracket.ui.components.weekdayText
import io.cosgriff.flywaybracket.ui.theme.FlywayDimens
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme

/** One matchup of the player's bracket (#42), opened from the Bracket tab after picks lock. */
@Composable
fun MatchupScreen(
    leagueId: String,
    at: DrawPosition,
    onBack: () -> Unit,
    viewModel: MatchupViewModel = hiltViewModel<MatchupViewModel, MatchupViewModel.Factory>(
        creationCallback = { factory -> factory.create(leagueId, at) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Blank for the moment it takes to read the bracket.
    state?.let { MatchupContent(it, onBack = onBack) }
}

/**
 * Both birds with how many eligible players logged each, who won and why, the player's pick and points, and
 * whether the player was eligible for and logged each bird. Built from the Matchup board, without its chart
 * of new players per day: the server gives no daily figures.
 */
@Composable
fun MatchupContent(state: MatchupState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.background) { insets ->
        Column(
            modifier = Modifier
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(bottom = FlywayDimens.space4),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
        ) {
            Header(state, onBack)
            Column(
                modifier = Modifier.padding(horizontal = FlywayDimens.space5),
                verticalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
            ) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3)) {
                    BirdCard(state, state.upper, Modifier.weight(1f).fillMaxHeight())
                    BirdCard(state, state.lower, Modifier.weight(1f).fillMaxHeight())
                }
                Section(R.string.matchup_result_title) { ResultText(state) }
                Section(R.string.matchup_pick_title) { PickText(state) }
                Section(R.string.matchup_part_title) { PartText(state) }
            }
        }
    }
}

@Composable
private fun Header(state: MatchupState, onBack: () -> Unit) {
    val colors = FlywayTheme.colors
    Row(
        modifier = Modifier.padding(horizontal = FlywayDimens.space2, vertical = FlywayDimens.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space1),
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.action_back))
        }
        Column {
            val round = stringResource(state.at.round.nameRes)
            Text(
                text = (state.quarter?.let { stringResource(R.string.matchup_overline, round, stringResource(quarterNameRes(it))) } ?: round)
                    .toUpperCase(Locale.current),
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkMuted,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = when {
                    state.result != null -> stringResource(R.string.matchup_decided, weekdayText(state.dates.endInclusive))
                    state.live -> stringResource(R.string.matchup_live, weekdayText(state.dates.endInclusive))
                    else -> stringResource(R.string.matchup_upcoming, weekdayText(state.dates.start))
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (state.live) colors.live else colors.inkMuted,
            )
        }
    }
}

/** One bird: its seed, whether it won or is the pick, and its share of how many eligible players. */
@Composable
private fun BirdCard(state: MatchupState, side: MatchupSide, modifier: Modifier = Modifier) {
    val colors = FlywayTheme.colors
    val team = side.team
    val won = team != null && state.result?.winner == team
    val picked = team != null && state.pick == team
    // The winner stands out once decided; until then the player's pick does.
    val filled = if (state.result != null) won else picked
    val muted = if (filled) colors.onPrimaryMuted else colors.inkMuted
    Surface(
        modifier = modifier,
        color = if (filled) colors.primary else colors.surface,
        contentColor = if (filled) colors.onPrimary else colors.ink,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(FlywayDimens.space4).semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space1),
        ) {
            if (team == null) {
                Text(stringResource(R.string.bracket_to_come), style = MaterialTheme.typography.titleMedium, color = muted)
                return@Column
            }
            var label = stringResource(R.string.matchup_seed, team.seed)
            if (picked) label = stringResource(R.string.matchup_joined, label, stringResource(R.string.matchup_tag_pick))
            if (won) label = stringResource(R.string.matchup_joined, label, stringResource(R.string.matchup_tag_won))
            Text(label, style = MaterialTheme.typography.labelMedium, color = muted)
            Text(team.commonName, style = MaterialTheme.typography.titleMedium)
            val tally = side.tally
            if (tally != null) {
                Text(
                    text = shareText(tally),
                    style = FlywayTheme.text.score,
                    color = when {
                        filled -> colors.onPrimary
                        won -> colors.ink
                        else -> colors.inkMuted
                    },
                )
                Text(
                    text = pluralStringResource(R.plurals.matchup_logged_of, tally.eligible, tally.logged, tally.eligible),
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            } else {
                Text(stringResource(R.string.matchup_no_share), style = MaterialTheme.typography.bodySmall, color = muted)
            }
        }
    }
}

@Composable
private fun Section(title: Int, body: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space3),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2),
        ) {
            Text(
                text = stringResource(title).toUpperCase(Locale.current),
                style = MaterialTheme.typography.labelSmall,
                color = FlywayTheme.colors.inkMuted,
                modifier = Modifier.semantics { heading() },
            )
            body()
        }
    }
}

/** Who won and why, in the game's words: never the server's. */
@Composable
private fun ResultText(state: MatchupState) {
    val result = state.result
    if (result == null) {
        Body(stringResource(R.string.matchup_not_decided))
        return
    }
    val winner = result.winner.commonName
    Body(
        when (result.reason) {
            Decision.Reason.HIGHER_SHARE -> stringResource(R.string.matchup_reason_higher_share, winner)
            Decision.Reason.TIED_BETTER_SEED -> stringResource(R.string.matchup_reason_tied, winner)
            Decision.Reason.TOO_FEW_ELIGIBLE -> pluralStringResource(R.plurals.matchup_reason_too_few, MatchupRule.MIN_ELIGIBLE, MatchupRule.MIN_ELIGIBLE, winner)
        },
    )
    // A side too thin to count can lose on share, or tie at nothing: say so.
    if (result.reason != Decision.Reason.TOO_FEW_ELIGIBLE) {
        listOf(state.upper, state.lower).forEach { side ->
            val tally = side.tally ?: return@forEach
            val team = side.team ?: return@forEach
            if (tally.eligible < MatchupRule.MIN_ELIGIBLE) {
                Body(pluralStringResource(R.plurals.matchup_share_did_not_count, MatchupRule.MIN_ELIGIBLE, MatchupRule.MIN_ELIGIBLE, team.commonName))
            }
        }
    }
}

@Composable
private fun PickText(state: MatchupState) {
    val pick = state.pick
    val result = state.result
    Body(
        when {
            pick == null -> stringResource(R.string.matchup_no_pick)
            result != null && state.pickStatus == PickStatus.RIGHT -> stringResource(R.string.matchup_pick_won, pick.commonName, pointsText(result.score))
            result != null -> stringResource(R.string.matchup_pick_lost, pick.commonName)
            state.pickStatus == PickStatus.OUT -> stringResource(R.string.matchup_pick_out, pick.commonName)
            else -> state.ifPickWins?.let { stringResource(R.string.matchup_pick_if_wins, pick.commonName, pointsText(it)) }
                ?: stringResource(R.string.bracket_a11y_pick, pick.commonName)
        },
    )
}

@Composable
private fun pointsText(score: PickScore): String {
    val points = pluralStringResource(R.plurals.matchup_points, score.roundPoints, score.roundPoints)
    return if (score.upsetBonus > 0) stringResource(R.string.matchup_points_bonus, points, score.upsetBonus) else points
}

/** Whether the player was eligible for and logged each bird, from what the phone holds. */
@Composable
private fun PartText(state: MatchupState) {
    val sides = listOf(state.upper, state.lower).filter { it.part != null && it.team != null }
    if (sides.isEmpty()) {
        Body(stringResource(R.string.matchup_part_not_started))
        return
    }
    sides.forEach { side ->
        val part = side.part ?: return@forEach
        val name = side.team?.commonName ?: return@forEach
        Body(
            when {
                part.counted -> stringResource(R.string.matchup_part_counted, name)
                part.eligible && state.result == null -> stringResource(R.string.matchup_part_eligible_live, name)
                part.eligible -> stringResource(R.string.matchup_part_eligible_done, name)
                part.logged -> stringResource(R.string.matchup_part_not_counted, name)
                else -> stringResource(R.string.matchup_part_not_eligible, name)
            },
        )
    }
    Text(stringResource(R.string.matchup_part_note), style = MaterialTheme.typography.bodySmall, color = FlywayTheme.colors.inkMuted)
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Preview
@Composable
private fun MatchupPreview() {
    FlywayTheme { MatchupContent(previewMatchupHigherShare, onBack = {}) }
}
