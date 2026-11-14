package io.cosgriff.flywaybracket.server.league

import io.cosgriff.flywaybracket.server.account.InMemoryPlayerStore
import io.cosgriff.flywaybracket.server.account.PlayerStore
import java.time.Instant
import java.util.UUID

/** The league rules over stores in memory: these run without Docker. */
class InMemoryLeaguesTest : LeaguesTest() {
    override fun players(): PlayerStore = InMemoryPlayerStore()

    override fun leagueStore(players: PlayerStore): LeagueStore = InMemoryLeagueStore(players)

    override fun listAsDeleted(id: UUID, at: Instant) = (playerStore as InMemoryPlayerStore).listAsDeleted(id, at)
}
