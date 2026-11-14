package io.cosgriff.flywaybracket.server.league

import io.cosgriff.flywaybracket.server.account.InMemoryPlayerStore
import io.cosgriff.flywaybracket.server.account.PlayerStore
import io.cosgriff.flywaybracket.server.sighting.InMemorySightingStore
import io.cosgriff.flywaybracket.server.sighting.SightingStore

/** The league endpoints over stores in memory: these run without Docker. */
class InMemoryLeagueEndpointsTest : LeagueEndpointsTest() {
    override fun players(): PlayerStore = InMemoryPlayerStore()

    override fun leagueStore(players: PlayerStore, sightings: SightingStore): LeagueStore = InMemoryLeagueStore(players, sightings)

    override fun sightingStore(players: PlayerStore): SightingStore = InMemorySightingStore(players)
}
