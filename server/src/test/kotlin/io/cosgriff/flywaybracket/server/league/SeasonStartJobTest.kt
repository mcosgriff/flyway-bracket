package io.cosgriff.flywaybracket.server.league

import io.cosgriff.flywaybracket.api.CreateLeagueRequest
import io.cosgriff.flywaybracket.api.LeagueState
import io.cosgriff.flywaybracket.api.LeagueSummary
import io.cosgriff.flywaybracket.server.account.InMemoryPlayerStore
import io.cosgriff.flywaybracket.server.account.SignedInPlayer
import io.cosgriff.flywaybracket.server.pack.TEST_HOME_COUNTY
import io.cosgriff.flywaybracket.server.pack.testCharts
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** The once-a-minute job, over stores in memory. All names and counties are made up. */
class SeasonStartJobTest {
    private val players = InMemoryPlayerStore()
    private val store = InMemoryLeagueStore(players)

    /** Wednesday 17 February 2027, noon in Denver. */
    private val clock = HandClock(Instant.parse("2027-02-17T19:00:00Z"))
    private val leagues = Leagues(store, testCharts(), clock)

    private fun player(n: Int): SignedInPlayer {
        val id = UUID.randomUUID()
        players.create(id, "made-up-hash")
        players.update(id, "Player $n", TEST_HOME_COUNTY)
        return SignedInPlayer(id)
    }

    /** A league of 8 starting on Monday 1 March 2027, in Denver. */
    private fun leagueOfEight(): Pair<SignedInPlayer, LeagueSummary> {
        val owner = player(1)
        val created = leagues.create(owner, CreateLeagueRequest("Thursday Walkers", false, "America/Denver", LocalDate.of(2027, 3, 1)))
        val league = (created as LeagueOutcome.InLeague).league
        (2..8).forEach { leagues.join(player(it), checkNotNull(league.inviteCode)) }
        return owner to league
    }

    @Test
    fun `a run starts the leagues whose start has arrived in their time zone, and seeds each once`() {
        val (owner, league) = leagueOfEight()
        val job = SeasonStartJob(leagues)

        // 06:59 UTC on 1 March is still 28 February in Denver.
        clock.now = Instant.parse("2027-03-01T06:59:00Z")
        job.runOnce()
        // The field was fixed on Saturday, at this first run since; the season has not started.
        assertEquals(1, store.fieldSaves)
        assertEquals(LeagueState.BEFORE_SEASON, leagues.mine(owner).single().state)

        clock.now = Instant.parse("2027-03-01T07:00:00Z")
        job.runOnce()
        job.runOnce()

        assertEquals(LeagueState.IN_SEASON, leagues.mine(owner).single().state)
        assertEquals(1, store.fieldSaves)
        // No longer due to start; due on every run while in season, for its rounds.
        assertEquals(listOf(UUID.fromString(league.id)), leagues.dueToSettle())
        assertEquals(league.id, leagues.mine(owner).single().id)
    }

    @Test
    fun `once started, the job runs on its own`() {
        val (owner, _) = leagueOfEight()
        clock.now = Instant.parse("2027-03-01T07:00:00Z")

        SeasonStartJob(leagues, period = Duration.ofMillis(10)).use { job ->
            job.start()
            val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
            while (store.fieldSaves == 0 && System.nanoTime() < deadline) Thread.sleep(10)
        }

        // The job seeded the field as it started the season.
        assertEquals(1, store.fieldSaves)
        assertEquals(LeagueState.IN_SEASON, leagues.mine(owner).single().state)
    }
}
