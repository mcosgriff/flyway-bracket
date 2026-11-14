package io.cosgriff.flywaybracket.ui.league

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.domain.bracket.Season
import io.cosgriff.flywaybracket.domain.league.CloseReason
import io.cosgriff.flywaybracket.domain.league.LeaguePhase
import io.cosgriff.flywaybracket.ui.theme.FlywayTheme
import java.time.LocalDate
import java.time.LocalDateTime

// Made-up leagues, players, codes and figures for previews and screenshot tests: no real person.

/** Today in the previews: Wednesday 17 February 2027. */
internal val previewLeagueToday: LocalDate = LocalDate.of(2027, 2, 17)

private val previewFetched = LocalDateTime.of(2027, 3, 16, 9, 30)

private val previewStandings = listOf(
    StandingRow(rank = 1, name = "Rosa M.", points = 34, rightPicks = 24, you = false),
    StandingRow(rank = 2, name = "Dev P.", points = 29, rightPicks = 22, you = false),
    StandingRow(rank = 3, name = "Wren E.", points = 27, rightPicks = 21, you = true),
    StandingRow(rank = 3, name = "Hana K.", points = 27, rightPicks = 21, you = false),
    StandingRow(rank = 5, name = "Tobias R.", points = 23, rightPicks = 20, you = false),
    StandingRow(rank = 6, name = "Imani W.", points = 21, rightPicks = 19, you = false),
    StandingRow(rank = 7, name = "Luca F.", points = 20, rightPicks = 18, you = false),
)

internal val previewJoin = LeagueTabState.Join(JoinState(code = "TESTWREN"))

internal val previewForming = LeagueTabState.InLeague(
    LeagueView(
        name = "Flyway Friends",
        phase = LeaguePhase.FORMING,
        season = Season.SPRING,
        seasonStart = LocalDate.of(2027, 3, 1),
        seasonEnd = LocalDate.of(2027, 4, 11),
        timeZone = "America/Denver",
        players = 5,
        capacity = 64,
        inviteCode = "TESTWREN",
        standings = listOf("Rosa M.", "Wren E.", "Dev P.", "Hana K.", "Luca F.").map { StandingRow(1, it, 0, 0, you = it == "Wren E.") },
        fetchedAt = LocalDateTime.of(2027, 2, 17, 12, 0),
    ),
)

internal val previewRunning = LeagueTabState.InLeague(
    LeagueView(
        name = "Flyway Friends",
        phase = LeaguePhase.RUNNING,
        season = Season.SPRING,
        seasonStart = LocalDate.of(2027, 3, 1),
        seasonEnd = LocalDate.of(2027, 4, 11),
        timeZone = "America/Denver",
        round = Round.ROUND_OF_32,
        players = 7,
        capacity = 64,
        inviteCode = "TESTWREN",
        standings = previewStandings,
        decidedMatchups = 32,
        fetchedAt = previewFetched,
    ),
)

internal val previewFinished = LeagueTabState.InLeague(
    (previewRunning.league).copy(
        phase = LeaguePhase.FINISHED,
        round = null,
        standings = previewStandings.map { it.copy(points = it.points * 2, rightPicks = it.rightPicks + 20) },
        decidedMatchups = 63,
        fetchedAt = LocalDateTime.of(2027, 4, 13, 8, 0),
    ),
)

internal val previewClosed = LeagueTabState.InLeague(
    LeagueView(
        name = "Early Birds",
        phase = LeaguePhase.CLOSED,
        closedBecause = CloseReason.TOO_FEW_PLAYERS,
        season = Season.SPRING,
        seasonStart = LocalDate.of(2027, 3, 29),
        seasonEnd = LocalDate.of(2027, 5, 9),
        timeZone = "America/Denver",
        players = 5,
        capacity = 64,
        standings = listOf("Sora T.", "Wren E.", "Noor A.", "Ola B.", "Kai D.").map { StandingRow(1, it, 0, 0, you = it == "Wren E.") },
        fetchedAt = LocalDateTime.of(2027, 3, 29, 7, 15),
    ),
)

internal val previewLeave = LeagueTabState.InLeague(previewForming.league.copy(confirmLeave = true))

internal val previewCreate = CreateLeagueState(name = "Flyway Friends", timeZone = "America/Denver", seasonStart = LocalDate.of(2027, 3, 1))

internal val previewCreateInvalid = previewCreate.copy(name = "FF")

internal val previewCreated = previewCreate.copy(created = CreatedLeague(name = "Flyway Friends", inviteCode = "TESTWREN"))

@Preview
@Composable
private fun LeagueRunningPreview() = FlywayTheme(darkTheme = false) {
    LeagueContent(previewRunning, onNavigate = {})
}

@Preview
@Composable
private fun LeagueFormingDarkPreview() = FlywayTheme(darkTheme = true) {
    LeagueContent(previewForming, onNavigate = {})
}

@Preview
@Composable
private fun LeagueJoinPreview() = FlywayTheme(darkTheme = false) {
    LeagueContent(previewJoin, onNavigate = {})
}

@Preview
@Composable
private fun LeagueCreatePreview() = FlywayTheme(darkTheme = false) {
    LeagueCreateContent(previewCreate, previewLeagueToday, onBack = {})
}
