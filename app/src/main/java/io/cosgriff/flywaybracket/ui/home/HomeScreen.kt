package io.cosgriff.flywaybracket.ui.home

import android.icu.text.MessageFormat
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.domain.bracket.Season
import io.cosgriff.flywaybracket.ui.components.FlywayBottomBar
import io.cosgriff.flywaybracket.ui.components.TopLevel
import io.cosgriff.flywaybracket.ui.theme.FlywayDimens
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Home, with the home county from the settings and the season figures and recent outings saved on the
 * phone. No league or tournament exists yet.
 */
@Composable
fun HomeScreen(onNavigate: (TopLevel) -> Unit, onStartOuting: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeContent(state = state, today = LocalDate.now(), onNavigate = onNavigate, onStartOuting = onStartOuting)
}

@Composable
fun HomeContent(
    state: HomeState,
    today: LocalDate,
    onNavigate: (TopLevel) -> Unit,
    onStartOuting: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { FlywayBottomBar(selected = TopLevel.Home, onNavigate = onNavigate) },
    ) { insets ->
        Column(
            modifier = Modifier
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FlywayDimens.space5, vertical = FlywayDimens.space4),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space4),
        ) {
            Header(region = state.region, onRegion = { onNavigate(TopLevel.Packs) })
            if (state.tournament != null) {
                TournamentHero(state.tournament, onViewBracket = { onNavigate(TopLevel.Bracket) })
            } else {
                NoLeagueHero(onFindLeague = { onNavigate(TopLevel.League) })
            }
            StartOutingButton(onStartOuting)
            if (state.league != null) LeagueRow(state.league, onClick = { onNavigate(TopLevel.League) })
            SeasonTiles(state.season)
            RecentOutings(state.recentOutings, today)
        }
    }
}

@Composable
private fun Header(region: HomeRegion?, onRegion: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space2)) {
        Text(
            text = stringResource(R.string.app_name).toUpperCase(Locale.current),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (region != null) {
            OutlinedButton(
                onClick = onRegion,
                modifier = Modifier.heightIn(min = FlywayDimens.tapMin),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text(
                    text = stringResource(R.string.home_region_county, region.county, region.stateAbbreviation),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TournamentHero(tournament: TournamentSummary, onViewBracket: () -> Unit) {
    val colors = FlywayTheme.colors
    Surface(color = colors.primary, contentColor = colors.onPrimary, shape = MaterialTheme.shapes.large) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FlywayDimens.space5, vertical = FlywayDimens.space4),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2),
        ) {
            Text(
                text = stringResource(
                    R.string.home_tournament_overline,
                    stringResource(tournament.season.label),
                    tournament.round.ordinal + 1,
                    Round.entries.size,
                ).toUpperCase(Locale.current),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onPrimaryMuted,
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3)) {
                Text(stringResource(R.string.figure, tournament.points), style = FlywayTheme.text.scoreHero)
                Text(
                    text = pluralStringResource(
                        R.plurals.home_points_caption,
                        tournament.picksDecided,
                        tournament.picksRight,
                        tournament.picksDecided,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryMuted,
                    modifier = Modifier.padding(bottom = FlywayDimens.space2),
                )
            }
            if (tournament.picksNotYetSeen > 0) {
                Text(
                    text = pluralStringResource(R.plurals.home_picks_not_seen, tournament.picksNotYetSeen, tournament.picksNotYetSeen),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryMuted,
                )
            }
            Button(
                onClick = onViewBracket,
                modifier = Modifier.heightIn(min = FlywayDimens.tapMin),
                colors = ButtonDefaults.buttonColors(containerColor = colors.onPrimary, contentColor = colors.primary),
            ) {
                Text(stringResource(R.string.home_view_bracket), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun NoLeagueHero(onFindLeague: () -> Unit) {
    val colors = FlywayTheme.colors
    Surface(color = colors.primary, contentColor = colors.onPrimary, shape = MaterialTheme.shapes.large) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FlywayDimens.space5, vertical = FlywayDimens.space4),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
        ) {
            Text(stringResource(R.string.home_no_league_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.home_no_league_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onPrimaryMuted,
            )
            Button(
                onClick = onFindLeague,
                modifier = Modifier.heightIn(min = FlywayDimens.tapMin),
                colors = ButtonDefaults.buttonColors(containerColor = colors.onPrimary, contentColor = colors.primary),
            ) {
                Text(stringResource(R.string.home_find_league), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun StartOutingButton(onClick: () -> Unit) {
    val colors = FlywayTheme.colors
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FlywayDimens.buttonHeight),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = colors.action, contentColor = colors.onAction),
    ) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null, modifier = Modifier.size(FlywayDimens.space5))
        Spacer(Modifier.width(FlywayDimens.space2))
        Text(stringResource(R.string.home_start_outing), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun LeagueRow(league: LeagueSummary, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FlywayDimens.rowHeight)
                .padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(league.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = pluralStringResource(R.plurals.home_league_caption, league.players, league.players),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // "3rd", "3e", "3.º": the phone's language writes the place.
            Text(MessageFormat.format("{0,ordinal}", league.rank), style = FlywayTheme.text.score, color = FlywayTheme.colors.pick)
        }
    }
}

@Composable
private fun SeasonTiles(season: SeasonStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3)) {
        StatTile(season.species, stringResource(R.string.home_stat_species), Modifier.weight(1f))
        StatTile(season.lifers, stringResource(R.string.home_stat_lifers), Modifier.weight(1f))
        StatTile(season.outings, stringResource(R.string.home_stat_outings), Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(figure: Int, label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
        // One phrase for a screen reader: "47, Species this season".
        Column(
            modifier = Modifier
                .padding(horizontal = FlywayDimens.space3, vertical = FlywayDimens.space3)
                .semantics(mergeDescendants = true) {},
        ) {
            Text(stringResource(R.string.figure, figure), style = FlywayTheme.text.scoreLarge)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecentOutings(outings: List<OutingRow>, today: LocalDate) {
    Column(verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2)) {
        Text(
            text = stringResource(R.string.home_recent_title).toUpperCase(Locale.current),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        if (outings.isEmpty()) {
            Text(
                text = stringResource(R.string.home_recent_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        outings.forEach { outing -> OutingRowItem(outing, today) }
    }
}

@Composable
private fun OutingRowItem(outing: OutingRow, today: LocalDate) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.large) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FlywayDimens.rowHeight)
                .padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space2)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FlywayDimens.space3),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // An outing may have no place name (#25).
                Text(
                    text = outing.place.ifEmpty { stringResource(R.string.home_outing_unnamed) },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.home_outing_when, day(outing.date, today), length(outing.length)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = pluralStringResource(R.plurals.home_outing_species, outing.species, outing.species),
                style = FlywayTheme.text.score,
            )
        }
    }
}

@Composable
private fun day(date: LocalDate, today: LocalDate): String =
    if (date == today) stringResource(R.string.home_outing_today) else date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

@Composable
private fun length(length: Duration): String {
    val hours = length.toHours().toInt()
    val minutes = (length.toMinutes() % 60).toInt()
    return if (hours > 0) stringResource(R.string.duration_hours_minutes, hours, minutes) else stringResource(R.string.duration_minutes, minutes)
}

private val Season.label: Int
    get() = when (this) {
        Season.SPRING -> R.string.season_spring
        Season.FALL -> R.string.season_fall
    }

// Made-up figures for previews: no real person, place data or sighting.
internal val previewToday: LocalDate = LocalDate.of(2027, 4, 18)
internal val previewState = HomeState(
    region = HomeRegion(county = "Jefferson", stateAbbreviation = "CO"),
    tournament = TournamentSummary(Season.SPRING, Round.ROUND_OF_32, points = 27, picksRight = 21, picksDecided = 32, picksNotYetSeen = 4),
    league = LeagueSummary("Flyway Friends", players = 12, rank = 3),
    season = SeasonStats(species = 47, lifers = 3, outings = 12),
    recentOutings = listOf(
        OutingRow(1, "Two Ponds NWR", previewToday, Duration.ofMinutes(72), species = 14),
        OutingRow(2, "Standley Lake", previewToday.minusDays(1), Duration.ofMinutes(48), species = 9),
    ),
)

@Preview
@Composable
private fun HomeLightPreview() = FlywayTheme(darkTheme = false) {
    HomeContent(previewState, previewToday, onNavigate = {}, onStartOuting = {})
}

@Preview
@Composable
private fun HomeDarkPreview() = FlywayTheme(darkTheme = true) {
    HomeContent(previewState, previewToday, onNavigate = {}, onStartOuting = {})
}

@Preview
@Composable
private fun HomeEmptyPreview() = FlywayTheme(darkTheme = false) {
    HomeContent(HomeState(), previewToday, onNavigate = {}, onStartOuting = {})
}
