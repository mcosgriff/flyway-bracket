package io.cosgriff.flywaybracket.ui.bracket

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.unit.Dp
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.domain.bracket.DrawPosition
import io.cosgriff.flywaybracket.domain.bracket.Field
import io.cosgriff.flywaybracket.domain.bracket.MatchupView
import io.cosgriff.flywaybracket.domain.bracket.PickStatus
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.domain.bracket.Side
import io.cosgriff.flywaybracket.domain.bracket.Team
import io.cosgriff.flywaybracket.ui.components.nameRes
import io.cosgriff.flywaybracket.ui.theme.FlywayDimens
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme

// The tree of one quarter and the whole field (#42). Both lay out rounds as columns of matchups, each matchup
// centred in an equal share of its column's height, so a matchup sits between the two that feed it. Lines join
// each matchup to the slot its bird takes in the next round. A matchup is one item for a screen reader.

/** How a tree is sized: its first column is wider, and every first-round matchup gets [cell] of height. */
private class TreeSize(val firstColumn: Dp, val column: Dp, val cell: Dp)

/** A round's matchups, top to bottom, as one column of a tree. */
private class TreeColumn(val round: Round, val matchups: List<MatchupView>)

/** One quarter, from its first round to the bird it sends to the semifinals. Scrolls sideways. */
@Composable
internal fun QuarterTree(bracket: BracketState, quarter: String, onOpen: ((DrawPosition) -> Unit)?) {
    val columns = Round.entries.filter { it <= Round.QUARTERFINAL }.map { round ->
        TreeColumn(round, bracket.view.round(round).filter { Field.quarterOf(it.at) == quarter })
    }
    val size = TreeSize(FlywayDimens.treeFirstColumn, FlywayDimens.treeColumn, FlywayDimens.treeCell)
    // The quarterfinal's winner, or the player's pick of it, goes on to a semifinal.
    val last = columns.last().matchups.single()
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = FlywayDimens.space5)) {
        TreeHalf(columns, bracket.liveRound, size, onOpen)
        Joins(listOf(0), 1, size.cell * columns.first().matchups.size, mirrored = false, toSingle = true)
        Column(Modifier.width(size.column)) {
            ColumnLabel(stringResource(Round.SEMIFINAL.nameRes), live = Round.SEMIFINAL == bracket.liveRound)
            Box(Modifier.height(size.cell * columns.first().matchups.size), contentAlignment = Alignment.Center) {
                ChampionSlot(last)
            }
        }
    }
}

/** Every matchup, the top half from the left and the bottom half from the right, meeting at the final. */
@Composable
internal fun WholeField(bracket: BracketState, onOpen: ((DrawPosition) -> Unit)?, modifier: Modifier = Modifier) {
    val size = TreeSize(FlywayDimens.fieldFirstColumn, FlywayDimens.fieldColumn, FlywayDimens.fieldCell)
    fun half(top: Boolean): List<TreeColumn> = Round.entries.filter { it <= Round.SEMIFINAL }.map { round ->
        val matchups = bracket.view.round(round)
        TreeColumn(round, if (top) matchups.take(matchups.size / 2) else matchups.drop(matchups.size / 2))
    }
    val height = size.cell * half(true).first().matchups.size
    Box(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = FlywayDimens.space5, vertical = FlywayDimens.space2),
    ) {
        Row {
            TreeHalf(half(top = true), bracket.liveRound, size, onOpen)
            Joins(listOf(0), 1, height, mirrored = false, toSingle = true)
            Column(Modifier.width(size.column)) {
                ColumnLabel(stringResource(Round.FINAL.nameRes), live = Round.FINAL == bracket.liveRound)
                Box(Modifier.height(height), contentAlignment = Alignment.Center) {
                    MatchupBox(bracket.view.round(Round.FINAL).single(), onOpen)
                }
            }
            Joins(listOf(0), 1, height, mirrored = true, toSingle = true)
            TreeHalf(half(top = false), bracket.liveRound, size, onOpen, mirrored = true)
        }
    }
}

/** Columns of rounds with the lines between them; [mirrored] runs from the right, as the field's lower half. */
@Composable
private fun TreeHalf(columns: List<TreeColumn>, liveRound: Round?, size: TreeSize, onOpen: ((DrawPosition) -> Unit)?, mirrored: Boolean = false) {
    val height = size.cell * columns.first().matchups.size
    val parts = columns.flatMapIndexed { index, column ->
        val part: @Composable () -> Unit = {
            Column(Modifier.width(if (index == 0) size.firstColumn else size.column)) {
                ColumnLabel(stringResource(column.round.nameRes), live = column.round == liveRound)
                Column(Modifier.height(height)) {
                    column.matchups.forEach { matchup ->
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { MatchupBox(matchup, onOpen) }
                    }
                }
            }
        }
        val join: @Composable () -> Unit = { Joins(column.matchups.indices.toList(), column.matchups.size, height, mirrored, toSingle = false) }
        if (index == columns.lastIndex) listOf(part) else listOf(part, join)
    }
    Row { (if (mirrored) parts.asReversed() else parts).forEach { it() } }
}

/** A column's round. The live one is coloured, and the line above the tree says so in words. */
@Composable
private fun ColumnLabel(text: String, live: Boolean) {
    Text(
        text = text.toUpperCase(Locale.current),
        style = MaterialTheme.typography.labelSmall,
        color = if (live) FlywayTheme.colors.live else FlywayTheme.colors.inkMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.height(FlywayDimens.space6),
    )
}

/**
 * The lines from a column of [count] matchups to the next column. With [toSingle] each matchup's line runs
 * straight across, to a single slot or matchup; else each pair joins the two slots of the matchup they feed.
 */
@Composable
private fun Joins(indices: List<Int>, count: Int, height: Dp, mirrored: Boolean, toSingle: Boolean) {
    val line = FlywayTheme.colors.borderStrong
    val label = FlywayDimens.space6
    val slot = FlywayDimens.bracketSlot
    Canvas(Modifier.width(FlywayDimens.bracketJoin).height(label + height)) {
        val stroke = FlywayDimens.border.toPx()
        val cell = height.toPx() / count
        val top = label.toPx()
        fun x(at: Float) = if (mirrored) size.width - at else at
        val path = Path()
        indices.forEach { index ->
            val from = top + cell * (index + 0.5f)
            if (toSingle) {
                path.moveTo(x(0f), from)
                path.lineTo(x(size.width), from)
            } else {
                // The matchup fed sits between this pair; its upper slot takes the upper bird.
                val pair = index / 2
                val centre = top + cell * 2 * (pair + 0.5f)
                val to = if (index % 2 == 0) centre - slot.toPx() / 2 else centre + slot.toPx() / 2
                val mid = size.width / 2
                path.moveTo(x(0f), from)
                path.lineTo(x(mid), from)
                path.lineTo(x(mid), to)
                path.lineTo(x(size.width), to)
            }
        }
        drawPath(path, line, style = Stroke(width = stroke))
    }
}

/** How a slot is drawn. Each look also has its words, in the matchup's description. */
private enum class SlotLook { PLAIN, WON, PICK, OUT, EMPTY }

private fun MatchupView.look(side: Side): SlotLook = when {
    winner != null -> if (side.team == winner) SlotLook.WON else SlotLook.OUT
    side is Side.Known -> SlotLook.PLAIN
    side is Side.YourPick -> if (side.out) SlotLook.OUT else SlotLook.PICK
    else -> SlotLook.EMPTY
}

/** A matchup's two slots, as one item: it opens the matchup after the lock. */
@Composable
private fun MatchupBox(matchup: MatchupView, onOpen: ((DrawPosition) -> Unit)?) {
    val description = matchupDescription(matchup)
    val openLabel = stringResource(R.string.bracket_a11y_open)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onOpen != null) Modifier.clickable(onClickLabel = openLabel) { onOpen(matchup.at) } else Modifier)
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(FlywayDimens.border),
    ) {
        Slot(matchup.upper.team, matchup.look(matchup.upper))
        Slot(matchup.lower.team, matchup.look(matchup.lower))
    }
}

/** The bird that leaves a quarter: the quarterfinal's winner, else the player's pick of it. */
@Composable
private fun ChampionSlot(quarterfinal: MatchupView) {
    val winner = quarterfinal.winner
    val pick = quarterfinal.pick?.takeIf { quarterfinal.pickStatus != PickStatus.OUT }
    val team = winner ?: pick
    val look = when {
        winner != null -> SlotLook.WON
        pick != null -> SlotLook.PICK
        else -> SlotLook.EMPTY
    }
    val description = when {
        winner != null -> stringResource(R.string.bracket_a11y_won, winner.commonName)
        pick != null -> stringResource(R.string.bracket_a11y_pick, pick.commonName)
        else -> stringResource(R.string.bracket_to_come)
    }
    Box(Modifier.clearAndSetSemantics { contentDescription = description }) { Slot(team, look) }
}

@Composable
private fun Slot(team: Team?, look: SlotLook) {
    val colors = FlywayTheme.colors
    val background = when (look) {
        SlotLook.PICK -> colors.pickContainer
        SlotLook.OUT, SlotLook.EMPTY -> Color.Transparent
        SlotLook.PLAIN, SlotLook.WON -> colors.surface
    }
    val ink = when (look) {
        SlotLook.PICK -> colors.pick
        SlotLook.OUT, SlotLook.EMPTY -> colors.inkMuted
        SlotLook.PLAIN, SlotLook.WON -> colors.ink
    }
    val edge = if (look == SlotLook.PICK) colors.pick else colors.border
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(FlywayDimens.bracketSlot)
            .background(background, MaterialTheme.shapes.extraSmall)
            .border(BorderStroke(FlywayDimens.border, edge), MaterialTheme.shapes.extraSmall)
            .padding(horizontal = FlywayDimens.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space1),
    ) {
        Text(
            text = team?.seed?.let { stringResource(R.string.figure, it) } ?: "",
            style = MaterialTheme.typography.labelMedium,
            color = ink,
            modifier = Modifier.width(FlywayDimens.space5),
        )
        Text(
            text = team?.commonName ?: stringResource(R.string.bracket_to_come),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (look == SlotLook.OUT || look == SlotLook.EMPTY) FontWeight.Normal else FontWeight.SemiBold,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** What the slots' looks mean, in words, and that the tree scrolls. */
@Composable
internal fun Legend(modifier: Modifier = Modifier) {
    val colors = FlywayTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FlywayDimens.space1)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space2)) {
            Box(
                Modifier
                    .size(FlywayDimens.swatch)
                    .background(colors.pickContainer, MaterialTheme.shapes.extraSmall)
                    .border(BorderStroke(FlywayDimens.border, colors.pick), MaterialTheme.shapes.extraSmall),
            )
            Text(stringResource(R.string.bracket_your_pick), style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
            Text(stringResource(R.string.bracket_legend_out), style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
        }
        Text(stringResource(R.string.bracket_scroll_hint), style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
    }
}

/** Above the whole field: the round being played and the player's champion. */
@Composable
internal fun FieldSummary(bracket: BracketState) {
    val colors = FlywayTheme.colors
    val final = bracket.view.round(Round.FINAL).single()
    bracket.liveRound?.let { LiveLine(bracket, it) }
    Surface(color = colors.primary, contentColor = colors.onPrimary, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space2)) {
            Text(stringResource(R.string.bracket_champion).toUpperCase(Locale.current), style = MaterialTheme.typography.labelSmall, color = colors.onPrimaryMuted)
            val pick = final.pick
            Text(
                text = when {
                    pick == null -> stringResource(R.string.bracket_a11y_no_pick)
                    final.pickStatus == PickStatus.OUT -> stringResource(R.string.bracket_side_out, pick.commonName)
                    else -> pick.commonName
                },
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}
