package io.cosgriff.flywaybracket.server.league

import io.cosgriff.flywaybracket.server.account.InMemoryPlayerStore
import io.cosgriff.flywaybracket.server.account.PlayerStore
import io.cosgriff.flywaybracket.server.sighting.InMemorySightingStore
import io.cosgriff.flywaybracket.server.sighting.SightingStore

/** Deciding rounds over stores in memory: these run without Docker. */
class InMemoryRoundResultsTest : RoundResultsTest() {
    override fun players(): PlayerStore = InMemoryPlayerStore()

    override fun leagueStore(players: PlayerStore, sightings: SightingStore): LeagueStore = InMemoryLeagueStore(players, sightings)

    override fun sightingStore(players: PlayerStore): SightingStore = InMemorySightingStore(players)
}
