package io.cosgriff.flywaybracket.server.sighting

import io.cosgriff.flywaybracket.server.account.PlayerStore
import java.util.UUID

/**
 * The `sightings` table in a map, for tests that need no Postgres. `PostgresSightingStore` is the real one.
 *
 * @param players whether a player exists. A deleted player's sightings are gone, as the table's cascade
 *   deletes them.
 */
class InMemorySightingStore(private val players: PlayerStore) : SightingStore {
    private val rows = HashMap<UUID, LinkedHashMap<UUID, StoredSighting>>()

    @Synchronized
    override fun add(playerId: UUID, sightings: List<StoredSighting>): Boolean {
        if (players.profile(playerId) == null) return false
        val own = rows.getOrPut(playerId, ::LinkedHashMap)
        sightings.forEach { own.putIfAbsent(it.clientId, it) }
        return true
    }

    @Synchronized
    override fun delete(playerId: UUID, clientId: UUID): Boolean = live(playerId)?.remove(clientId) != null

    @Synchronized
    override fun of(playerId: UUID): Set<StoredSighting> = live(playerId)?.values?.toSet().orEmpty()

    private fun live(playerId: UUID): MutableMap<UUID, StoredSighting>? {
        if (players.profile(playerId) == null) rows.remove(playerId)
        return rows[playerId]
    }
}
