package io.cosgriff.flywaybracket.server.league

import io.cosgriff.flywaybracket.server.account.InMemoryPlayerStore
import io.cosgriff.flywaybracket.server.account.PlayerStore

/** The league rules over stores in memory: these run without Docker. */
class InMemoryLeaguesTest : LeaguesTest() {
    override fun players(): PlayerStore = InMemoryPlayerStore()

    override fun leagueStore(players: PlayerStore): LeagueStore = InMemoryLeagueStore(players)
}
