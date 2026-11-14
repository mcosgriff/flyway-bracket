package io.cosgriff.flywaybracket.api

import io.cosgriff.flywaybracket.domain.bracket.Round
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiPathsTest {
    private val all = listOf(
        ApiPaths.HEALTH,
        ApiPaths.ACCOUNTS,
        ApiPaths.ACCOUNT,
        ApiPaths.LEAGUES,
        ApiPaths.JOIN_LEAGUE,
        ApiPaths.AUTO_JOIN_LEAGUE,
        ApiPaths.SIGHTINGS,
        ApiPaths.sighting("6f1c0d4e-0000-4000-8000-000000000001"),
        ApiPaths.PACKS,
        ApiPaths.pack("US-ZZ"),
        ApiPaths.league("league-1"),
        ApiPaths.membership("league-1"),
        ApiPaths.bracket("league-1"),
        ApiPaths.picks("league-1"),
        ApiPaths.memberPicks("league-1", "member-1"),
        ApiPaths.standings("league-1"),
        ApiPaths.roundResults("league-1", Round.FINAL),
    )

    @Test
    fun `every path starts with v1`() {
        all.forEach { assertTrue(it, it.startsWith("/v1/")) }
    }

    @Test
    fun `no two endpoints share a path`() {
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun `the server's route and the app's call are one definition`() {
        assertEquals("/v1/leagues/{leagueId}/rounds/{round}/results", ApiPaths.roundResults("{leagueId}", "{round}"))
        assertEquals("/v1/leagues/league-1/rounds/ROUND_OF_16/results", ApiPaths.roundResults("league-1", Round.ROUND_OF_16))
        assertEquals("/v1/packs/{state}", ApiPaths.pack("{state}"))
        assertEquals("/v1/sightings/{clientId}", ApiPaths.sighting("{clientId}"))
        assertEquals("/v1/leagues/{leagueId}/members/{memberKey}/picks", ApiPaths.memberPicks("{leagueId}", "{memberKey}"))
    }
}
