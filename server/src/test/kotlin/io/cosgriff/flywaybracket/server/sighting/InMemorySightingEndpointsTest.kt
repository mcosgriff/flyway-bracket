package io.cosgriff.flywaybracket.server.sighting

import io.cosgriff.flywaybracket.server.account.InMemoryPlayerStore
import io.cosgriff.flywaybracket.server.account.PlayerStore

/** The sighting endpoints over stores in memory: these run without Docker. */
class InMemorySightingEndpointsTest : SightingEndpointsTest() {
    override fun players(): PlayerStore = InMemoryPlayerStore()

    override fun sightingStore(players: PlayerStore): SightingStore = InMemorySightingStore(players)
}
