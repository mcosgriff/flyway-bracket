package io.cosgriff.flywaybracket.server.account

import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The `players` and `deleted_players` tables in maps, for tests that need no Postgres. `PostgresPlayerStore`
 * is the real one.
 *
 * @param clock the time a deletion is listed with
 */
class InMemoryPlayerStore(private val clock: Clock = Clock.systemUTC()) : PlayerStore {
    private class Row(val secretHash: String, var profile: StoredProfile)

    private val rows = ConcurrentHashMap<UUID, Row>()
    private val deleted = ConcurrentHashMap<UUID, Instant>()

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

    @Synchronized
    override fun delete(id: UUID): Boolean {
        if (rows.remove(id) == null) return false
        deleted.putIfAbsent(id, clock.instant())
        return true
    }

    override fun deletedButPresent(): List<UUID> = deleted.keys.filter(rows::containsKey).sorted()

    @Synchronized
    override fun forgetDeletedBefore(cutoff: Instant): Int {
        val old = deleted.filterValues { it < cutoff }.keys
        old.forEach(deleted::remove)
        return old.size
    }

    /** When [id] was listed as deleted; null when it is not listed. */
    fun deletedAt(id: UUID): Instant? = deleted[id]

    /** Lists [id] as deleted at [at], as an operator copying the list in after a restore does. */
    fun listAsDeleted(id: UUID, at: Instant) {
        deleted.putIfAbsent(id, at)
    }
}
