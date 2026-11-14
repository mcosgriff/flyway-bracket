package io.cosgriff.flywaybracket.server.account

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** The `players` table in a map, for tests that need no Postgres. `PostgresPlayerStore` is the real one. */
class InMemoryPlayerStore : PlayerStore {
    private class Row(val secretHash: String, var profile: StoredProfile)

    private val rows = ConcurrentHashMap<UUID, Row>()

    override fun create(id: UUID, secretHash: String) {
        check(rows.putIfAbsent(id, Row(secretHash, StoredProfile(null, null))) == null) { "duplicate id" }
    }

    override fun secretHash(id: UUID): String? = rows[id]?.secretHash

    override fun profile(id: UUID): StoredProfile? = rows[id]?.profile

    override fun update(id: UUID, displayName: String?, homeCounty: String?): Boolean {
        val row = rows[id] ?: return false
        row.profile = StoredProfile(displayName ?: row.profile.displayName, homeCounty ?: row.profile.homeCounty)
        return true
    }

    override fun delete(id: UUID): Boolean = rows.remove(id) != null
}
