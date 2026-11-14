package io.cosgriff.flywaybracket.server.league

import io.cosgriff.flywaybracket.domain.league.LeaguePhase
import io.cosgriff.flywaybracket.domain.league.LeagueStatus
import io.cosgriff.flywaybracket.server.account.PlayerStore
import java.time.LocalDate
import java.util.UUID

/**
 * The `leagues`, `league_members` and field tables in maps, for tests that need no Postgres.
 * `PostgresLeagueStore` is the real one. One transaction runs at a time, which stands in for its locks.
 *
 * @param players where a player's display name is read, and whether they exist
 */
class InMemoryLeagueStore(private val players: PlayerStore) : LeagueStore {
    private class Member(val playerId: UUID, var left: Boolean = false)

    /** In the order they were made, which breaks ties of start as `created_at` does. */
    private val leagues = LinkedHashMap<UUID, StoredLeague>()
    private val members = HashMap<UUID, LinkedHashMap<UUID, Member>>()
    private val fields = HashMap<UUID, StoredField>()

    /** How many times a field has been stored, in any league. */
    @Volatile
    var fieldSaves = 0
        private set

    @Synchronized
    override fun <T> transaction(block: LeagueTransaction.() -> T): T = Transaction().block()

    private inner class Transaction : LeagueTransaction {
        override fun lockPlayer(playerId: UUID): LockedPlayer? = players.profile(playerId)?.let { LockedPlayer(it.displayName) }

        override fun lockAutoJoin() = Unit

        override fun league(id: UUID, lock: Boolean): StoredLeague? = leagues[id]?.withCount()

        override fun leagueByInviteCode(code: String): StoredLeague? = leagues.values.firstOrNull { it.inviteCode == code }?.withCount()

        override fun leaguesOf(playerId: UUID): List<StoredLeague> = leagues.values
            .filter { members[it.id]?.get(playerId)?.left == false }
            .reversed()
            .sortedByDescending { it.status.seasonStart }
            .map { it.withCount() }

        override fun isMember(leagueId: UUID, playerId: UUID): Boolean = members[leagueId]?.get(playerId)?.left == false

        override fun members(leagueId: UUID): List<StoredMember> = current(leagueId).mapNotNull { member ->
            players.profile(member.playerId)?.let { StoredMember(member.playerId, it.displayName.orEmpty(), it.homeCounty) }
        }

        override fun field(leagueId: UUID): StoredField? = fields[leagueId]

        override fun saveField(leagueId: UUID, field: StoredField) {
            checkNotNull(leagues[leagueId])
            fields[leagueId] = field
            fieldSaves++
        }

        override fun openFormingLeagues(): List<UUID> = leagues.values
            .filter { it.open && it.status.phase == LeaguePhase.FORMING }
            .sortedBy { it.status.seasonStart }
            .map { it.id }

        override fun formingLeaguesStartingBy(date: LocalDate): List<UUID> = leagues.values
            .filter { it.status.phase == LeaguePhase.FORMING && it.status.seasonStart <= date }
            .sortedBy { it.status.seasonStart }
            .map { it.id }

        override fun insert(league: StoredLeague): Boolean {
            if (leagues.values.any { it.inviteCode == league.inviteCode }) return false
            leagues[league.id] = league.copy(players = 0)
            members[league.id] = LinkedHashMap()
            return true
        }

        override fun saveStatus(leagueId: UUID, status: LeagueStatus) {
            leagues[leagueId] = checkNotNull(leagues[leagueId]).copy(status = status)
        }

        override fun addMember(leagueId: UUID, playerId: UUID) {
            val inLeague = checkNotNull(members[leagueId])
            check(playerId !in inLeague) { "already a member" }
            inLeague[playerId] = Member(playerId)
        }

        override fun removeMember(leagueId: UUID, playerId: UUID) {
            members[leagueId]?.remove(playerId)
        }

        override fun markLeft(leagueId: UUID, playerId: UUID) {
            members[leagueId]?.get(playerId)?.left = true
        }

        /** Members who have not left and whose account still exists, as the table's cascade keeps it. */
        private fun current(leagueId: UUID): List<Member> =
            members[leagueId].orEmpty().values.filter { !it.left && players.profile(it.playerId) != null }

        private fun StoredLeague.withCount() = copy(players = current(id).size)
    }
}
