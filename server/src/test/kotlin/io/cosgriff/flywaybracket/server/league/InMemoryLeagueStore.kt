package io.cosgriff.flywaybracket.server.league

import io.cosgriff.flywaybracket.api.MatchupSlot
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.domain.league.LeaguePhase
import io.cosgriff.flywaybracket.domain.league.LeagueStatus
import io.cosgriff.flywaybracket.server.account.PlayerStore
import io.cosgriff.flywaybracket.server.sighting.SightingStore
import io.cosgriff.flywaybracket.server.sighting.StoredSighting
import java.time.LocalDate
import java.util.TreeMap
import java.util.UUID

/**
 * The `leagues`, `league_members`, field, picks, results and round homes tables in maps, for tests that need
 * no Postgres.
 * `PostgresLeagueStore` is the real one. One transaction runs at a time, which stands in for its locks.
 *
 * @param players where a player's display name is read, and whether they exist
 * @param sightingStore where the sightings rounds are decided from are read; none when it is null
 */
class InMemoryLeagueStore(private val players: PlayerStore, private val sightingStore: SightingStore? = null) : LeagueStore {
    private class Member(val playerId: UUID, var left: Boolean = false, val key: UUID = UUID.randomUUID()) {
        val picks = sortedMapOf<MatchupSlot, String>()
        val roundHomes = HashMap<Round, String?>()
    }

    /** In the order they were made, which breaks ties of start as `created_at` does. */
    private val leagues = LinkedHashMap<UUID, StoredLeague>()
    private val members = HashMap<UUID, LinkedHashMap<UUID, Member>>()
    private val fields = HashMap<UUID, StoredField>()
    private val results = HashMap<UUID, TreeMap<MatchupSlot, StoredResult>>()

    /** How many times a round's results have been stored, in any league. */
    @Volatile
    var resultSaves = 0
        private set

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
            players.profile(member.playerId)?.let { StoredMember(member.playerId, it.displayName.orEmpty(), it.homeCounty, member.key) }
        }

        override fun memberByKey(leagueId: UUID, key: UUID): StoredMember? = members(leagueId).firstOrNull { it.key == key }

        override fun picks(leagueId: UUID, playerId: UUID): Map<MatchupSlot, String> =
            existing(leagueId).firstOrNull { it.playerId == playerId }?.picks?.toMap().orEmpty()

        override fun leaguePicks(leagueId: UUID): Map<UUID, Map<MatchupSlot, String>> =
            existing(leagueId).filter { it.picks.isNotEmpty() }.associate { it.playerId to it.picks.toMap() }

        override fun savePicks(leagueId: UUID, playerId: UUID, picks: Map<MatchupSlot, String>) {
            val member = checkNotNull(members[leagueId]?.get(playerId)) { "not a member" }
            member.picks.clear()
            member.picks.putAll(picks)
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

        override fun runningLeagues(): List<UUID> = leagues.values
            .filter { it.status.phase == LeaguePhase.RUNNING }
            .sortedBy { it.status.seasonStart }
            .map { it.id }

        override fun results(leagueId: UUID): List<StoredResult> = results[leagueId]?.values?.toList().orEmpty()

        override fun saveResults(leagueId: UUID, results: List<StoredResult>) {
            checkNotNull(leagues[leagueId])
            val stored = this@InMemoryLeagueStore.results.getOrPut(leagueId, ::TreeMap)
            results.forEach { check(stored.put(it.slot, it) == null) { "decided twice: ${it.slot}" } }
            resultSaves++
        }

        override fun roundHomes(leagueId: UUID, round: Round): Map<UUID, String?> = existing(leagueId)
            .filter { round in it.roundHomes }
            .associate { it.playerId to it.roundHomes[round] }

        override fun saveRoundHomes(leagueId: UUID, round: Round, homes: Map<UUID, String?>) {
            homes.forEach { (playerId, homeCounty) ->
                val member = checkNotNull(members[leagueId]?.get(playerId)) { "not a member" }
                if (round !in member.roundHomes) member.roundHomes[round] = homeCounty
            }
        }

        override fun deleteRoundHomes(leagueId: UUID, round: Round) {
            members[leagueId]?.values?.forEach { it.roundHomes.remove(round) }
        }

        override fun sightings(playerIds: Collection<UUID>, dates: ClosedRange<LocalDate>): Map<UUID, List<StoredSighting>> =
            playerIds
                .associateWith { id -> sightingStore?.of(id).orEmpty().filter { it.date in dates } }
                .filterValues { it.isNotEmpty() }

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
        private fun current(leagueId: UUID): List<Member> = existing(leagueId).filter { !it.left }

        /** Members, left or not, whose account still exists: a member's row and picks go with the account. */
        private fun existing(leagueId: UUID): List<Member> =
            members[leagueId].orEmpty().values.filter { players.profile(it.playerId) != null }

        private fun StoredLeague.withCount() = copy(players = current(id).size)
    }
}
