package io.cosgriff.flywaybracket.ui.home

import android.Manifest
import android.icu.text.MessageFormat
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.domain.bracket.Season
import io.cosgriff.flywaybracket.ui.components.DownloadFailureText
import io.cosgriff.flywaybracket.ui.components.DownloadProgressLine
import io.cosgriff.flywaybracket.ui.components.FlywayBottomBar
import io.cosgriff.flywaybracket.ui.components.MeteredDownloadDialog
import io.cosgriff.flywaybracket.ui.components.TopLevel
import io.cosgriff.flywaybracket.ui.components.failureMessage
import io.cosgriff.flywaybracket.ui.components.fileSizeText
import io.cosgriff.flywaybracket.ui.components.lengthText
import io.cosgriff.flywaybracket.ui.outing.CountyFallback
import io.cosgriff.flywaybracket.ui.theme.FlywayDimens
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Home, with the home county from the settings and the season figures and recent outings saved on the
 * phone. No league or tournament exists yet.
 *
 * The first time an outing is started, the page that explains the use of the phone's location takes
 * Home's place until the player answers it (#27).
 *
 * @param onOpenOuting called once an outing is in progress, to show it, with why a new one is in the home
 *   county, if it fell back there
 */
@Composable
fun HomeScreen(
    onNavigate: (TopLevel) -> Unit,
    onOpenOuting: (CountyFallback?) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val open by rememberUpdatedState(onOpenOuting)
    LaunchedEffect(viewModel) { viewModel.openOuting.collect { open(it.fallback) } }
    // Whatever the player answers, the outing starts: in the county the phone is in, or the home county.
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.onLocationAnswered() }
    if (state.askLocation) {
        BackHandler(onBack = viewModel::onLocationCancelled)
        LocationExplainContent(
            onContinue = { askPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
            onNotNow = viewModel::onLocationAnswered,
            onBack = viewModel::onLocationCancelled,
        )
        return
    }
    HomeContent(
        state = state,
        today = LocalDate.now(),
        onNavigate = onNavigate,
        onStartOuting = viewModel::onStartOuting,
        onDismissPackMissing = viewModel::onPackMissingDismissed,
        onDownloadPack = viewModel::onDownloadPack,
        onConfirmMetered = viewModel::onMeteredConfirmed,
        onDismissMetered = viewModel::onMeteredDismissed,
        onOpenSettings = onOpenSettings,
    )
}

/**
 * @param onStartOuting the start button: starts an outing, or continues the one in progress
 * @param onDismissPackMissing closes the note that the home county's pack is missing
 * @param onDownloadPack downloads the offered pack of the home county's state
 * @param onConfirmMetered the player said yes to a large download on a metered connection
 * @param onDismissMetered the player said not now to it
 * @param onOpenSettings the gear in the header (#44)
 */
@Composable
fun HomeContent(
    state: HomeState,
    today: LocalDate,
    onNavigate: (TopLevel) -> Unit,
    onStartOuting: () -> Unit,
    modifier: Modifier = Modifier,
    onDismissPackMissing: () -> Unit = {},
    onDownloadPack: () -> Unit = {},
    onConfirmMetered: () -> Unit = {},
    onDismissMetered: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
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
            Header(region = state.region, onRegion = { onNavigate(TopLevel.Packs) }, onSettings = onOpenSettings)
            state.packOffer?.let { PackOfferCard(it, onDownloadPack) }
            if (state.tournament != null) {
                TournamentHero(state.tournament, onViewBracket = { onNavigate(TopLevel.Bracket) })
            } else {
                NoLeagueHero(onFindLeague = { onNavigate(TopLevel.League) })
            }
            StartOutingButton(continuing = state.outingInProgress, finding = state.findingCounty, onClick = onStartOuting)
            if (state.league != null) LeagueRow(state.league, onClick = { onNavigate(TopLevel.League) })
            SeasonTiles(state.season)
            RecentOutings(state.recentOutings, today)
        }
    }
    if (state.packMissing) {
        PackMissingDialog(
            region = state.region,
            onGoToPacks = {
                onDismissPackMissing()
                onNavigate(TopLevel.Packs)
            },
            onDismiss = onDismissPackMissing,
        )
    }
    state.askMetered?.let { MeteredDownloadDialog(it, onConfirm = onConfirmMetered, onDismiss = onDismissMetered) }
}

/**
 * The server has the home county's pack and the phone does not: offers it, with its size. While it
 * downloads, how far it has come; if it failed, why.
 */
@Composable
private fun PackOfferCard(offer: PackOffer, onDownload: () -> Unit) {
    val colors = FlywayTheme.colors
    Surface(color = colors.pickContainer, contentColor = colors.ink, shape = MaterialTheme.shapes.large) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FlywayDimens.space4, vertical = FlywayDimens.space3),
            verticalArrangement = Arrangement.spacedBy(FlywayDimens.space2),
        ) {
            Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Text(stringResource(R.string.home_pack_offer_title, offer.state), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.home_pack_offer_body, fileSizeText(offer.sizeBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkMuted,
                )
            }
            val progress = offer.progress
            if (progress != null) {
                DownloadProgressLine(progress, color = colors.pick, trackColor = colors.surfaceMuted, textColor = colors.inkMuted)
            } else {
                Button(
                    onClick = onDownload,
                    modifier = Modifier.heightIn(min = FlywayDimens.tapMin),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                ) {
                    Text(stringResource(R.string.home_pack_offer_download), style = MaterialTheme.typography.labelLarge)
                }
            }
            offer.failure?.failureMessage?.let { DownloadFailureText(it, color = colors.danger) }
        }
    }
}

/** Start outing was tapped and the home county's pack is not on the phone: says so, and offers Packs. */
@Composable
private fun PackMissingDialog(region: HomeRegion?, onGoToPacks: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.home_pack_missing_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                text = if (region != null) {
                    stringResource(R.string.home_pack_missing_body, region.county, region.stateAbbreviation)
                } else {
                    stringResource(R.string.home_pack_missing_body_no_region)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            TextButton(onClick = onGoToPacks, colors = ButtonDefaults.textButtonColors(contentColor = FlywayTheme.colors.pick)) {
                Text(stringResource(R.string.home_pack_missing_go), style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                Text(stringResource(R.string.home_pack_missing_cancel), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}

/**
 * The title, the county chip and the gear in one row, the title taking what the other two leave. When
 * that is narrower than the title's longest word, the title would break inside it (#76): then the chip
 * goes on a line of its own below, and the title shares the first line with the gear only.
 */
@Composable
private fun Header(region: HomeRegion?, onRegion: () -> Unit, onSettings: () -> Unit) {
    Layout(
        content = { HeaderItems(region, onRegion, onSettings) },
        measurePolicy = { measurables, constraints ->
            // Measured and placed as Row(spacedBy(space2), CenterVertically) with the title weighted, so
            // the header looks as it did when everything fits.
            val gap = FlywayDimens.space2.roundToPx()
            val width = constraints.maxWidth
            val natural = Constraints(maxWidth = width, maxHeight = constraints.maxHeight)
            val title = measurables.first()
            val gear = measurables.last().measure(natural)
            val chip = if (measurables.size == 3) measurables[1].measure(natural) else null
            val chipInRow = chip != null && title.minIntrinsicWidth(constraints.maxHeight) + gap + chip.width + gap + gear.width <= width
            val chipBelow = chip.takeIf { !chipInRow }
            val beside = listOfNotNull(chip.takeIf { chipInRow }, gear)
            val titleWidth = (width - beside.sumOf { it.width + gap }).coerceAtLeast(0)
            val row = listOf(title.measure(natural.copy(minWidth = titleWidth, maxWidth = titleWidth))) + beside
            val rowHeight = row.maxOf { it.height }
            layout(width, if (chipBelow == null) rowHeight else rowHeight + gap + chipBelow.height) {
                var x = 0
                row.forEach {
                    it.placeRelative(x, Alignment.CenterVertically.align(it.height, rowHeight))
                    x += it.width + gap
                }
                chipBelow?.placeRelative(0, rowHeight + gap)
            }
        },
    )
}

@Composable
private fun HeaderItems(region: HomeRegion?, onRegion: () -> Unit, onSettings: () -> Unit) {
    Text(
        text = stringResource(R.string.app_name).toUpperCase(Locale.current),
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.semantics { heading() },
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
    IconButton(onClick = onSettings) {
        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.home_settings))
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

/** @param finding a new outing is waiting for the phone's location; the button says so, and a tap does nothing */
@Composable
private fun StartOutingButton(continuing: Boolean, finding: Boolean, onClick: () -> Unit) {
    val colors = FlywayTheme.colors
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FlywayDimens.buttonHeight),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = colors.action, contentColor = colors.onAction),
    ) {
        Icon(
            painter = painterResource(if (continuing) R.drawable.ic_forward else R.drawable.ic_add),
            contentDescription = null,
            modifier = Modifier.size(FlywayDimens.space5),
        )
        Spacer(Modifier.width(FlywayDimens.space2))
        Text(
            text = stringResource(
                when {
                    finding -> R.string.home_finding_county
                    continuing -> R.string.home_continue_outing
                    else -> R.string.home_start_outing
                },
            ),
            style = MaterialTheme.typography.titleLarge,
        )
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
                    text = stringResource(R.string.home_outing_when, day(outing.date, today), lengthText(outing.length)),
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

/** Just after the first run: the server has the home county's pack (a made-up state) and the phone does not. */
internal val previewPackOffer = HomeState(
    region = HomeRegion(county = "Lark Hollow", stateAbbreviation = "ZA"),
    packOffer = PackOffer(stateCode = "US-ZA", state = "Alder", sizeBytes = 1_480_000),
)

/** A home county with a long name (made up, as is its state), too long to share the title's line (#76). */
internal val previewLongCounty = previewState.copy(region = HomeRegion(county = "Lark Hollow and Fen Reach", stateAbbreviation = "ZA"))

@Preview
@Composable
private fun HomePackOfferPreview() = FlywayTheme(darkTheme = false) {
    HomeContent(previewPackOffer, previewToday, onNavigate = {}, onStartOuting = {})
}

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
