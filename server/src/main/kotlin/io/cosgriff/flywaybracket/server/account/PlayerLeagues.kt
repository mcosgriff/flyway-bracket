package io.cosgriff.flywaybracket.server.account

import io.cosgriff.flywaybracket.api.LeagueSummary
import java.util.UUID

/** What an account needs of the leagues. Every call blocks; call it off the request threads. */
interface PlayerLeagues {
    /**
     * The league shown with the player's profile: the one forming or in season, else their latest finished
     * or closed one; null when they are in none.
     */
    fun current(playerId: UUID): LeagueSummary?

    /** Takes the player out of every league they are in, as leaving each would. */
    fun leaveAll(playerId: UUID)
}
