package io.cosgriff.flywaybracket.server.league

import io.cosgriff.flywaybracket.api.CreateLeagueRequest
import io.cosgriff.flywaybracket.api.LeagueDetail
import io.cosgriff.flywaybracket.api.LeagueState
import io.cosgriff.flywaybracket.api.LeagueSummary
import io.cosgriff.flywaybracket.api.Standing
import io.cosgriff.flywaybracket.domain.league.JoinCheck
import io.cosgriff.flywaybracket.domain.league.LeagueLifecycle
import io.cosgriff.flywaybracket.domain.league.LeagueName
import io.cosgriff.flywaybracket.domain.league.LeagueNameCheck
import io.cosgriff.flywaybracket.domain.league.LeaguePhase
import io.cosgriff.flywaybracket.domain.league.LeagueStatus
import io.cosgriff.flywaybracket.domain.league.SeasonStart
import io.cosgriff.flywaybracket.domain.league.SeasonStartCheck
import io.cosgriff.flywaybracket.domain.league.Standings
import io.cosgriff.flywaybracket.server.account.PlayerLeagues
import io.cosgriff.flywaybracket.server.account.SignedInPlayer
import java.security.SecureRandom
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

/** What became of a request to create or join a league. */
sealed interface LeagueOutcome {
    /** The player is in [league]. */
    data class InLeague(val league: LeagueSummary) : LeagueOutcome

    data class Refused(val reason: LeagueRefusal) : LeagueOutcome
}

/** Why a player was not put in a league. */
enum class LeagueRefusal {
    NAME_INVALID,
    TIME_ZONE_UNKNOWN,
    START_NOT_A_MONDAY,
    START_TOO_SOON,

    /** The other players would have no name to see. */
    NO_DISPLAY_NAME,

    /** The player is in a league that is forming or in season. */
    ALREADY_IN_A_LEAGUE,
    INVITE_CODE_UNKNOWN,
    ALREADY_A_MEMBER,
    JOINING_CLOSED,
    FULL,

    /** The account was deleted after the request was signed. */
    NO_SUCH_PLAYER,
}

/**
 * Leagues (#32): creating, joining, auto-joining, leaving, and what a member sees. The rules are
 * `domain/league`'s; this class applies them to what is stored, under the locks that keep two requests at
 * once from breaking them. Every call blocks on the database; call it off the request threads.
 *
 * A league's dates move it on (its start arrives, its final ends), but nothing runs when they pass. The
 * league is settled instead: brought up to today by [LeagueLifecycle.settle] whenever it is read, and
 * saved, under its lock, before any change to its players. So the count of players settle sees is the
 * count when each date passed.
 *
 * @param clock today, in each league's own time zone
 */
class Leagues(
    private val store: LeagueStore,
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
) : PlayerLeagues {
    /** Creates a league with the player as its first member. */
    fun create(player: SignedInPlayer, request: CreateLeagueRequest): LeagueOutcome {
        if (LeagueName.check(request.name) != LeagueNameCheck.VALID) return refused(LeagueRefusal.NAME_INVALID)
        val zone = zoneOf(request.timeZone) ?: return refused(LeagueRefusal.TIME_ZONE_UNKNOWN)
        when (SeasonStart.check(request.seasonStart, today(zone))) {
            SeasonStartCheck.NOT_A_MONDAY -> return refused(LeagueRefusal.START_NOT_A_MONDAY)
            SeasonStartCheck.TOO_SOON -> return refused(LeagueRefusal.START_TOO_SOON)
            SeasonStartCheck.VALID -> Unit
        }
        return store.transaction {
            val locked = lockPlayer(player.id) ?: return@transaction refused(LeagueRefusal.NO_SUCH_PLAYER)
            refusalToEnter(player.id, locked)?.let { return@transaction refused(it) }
            val league = insertNew(request.name.trim(), request.open, zone, request.seasonStart)
            addMember(league.id, player.id)
            LeagueOutcome.InLeague(summary(league.copy(players = 1)))
        }
    }

    /** Puts the player in the league whose invite code they typed, while it is forming and has room. */
    fun join(player: SignedInPlayer, typedCode: String): LeagueOutcome = store.transaction {
        val locked = lockPlayer(player.id) ?: return@transaction refused(LeagueRefusal.NO_SUCH_PLAYER)
        if (locked.displayName == null) return@transaction refused(LeagueRefusal.NO_DISPLAY_NAME)
        val found = InviteCode.normalize(typedCode)?.let(::leagueByInviteCode)
            ?: return@transaction refused(LeagueRefusal.INVITE_CODE_UNKNOWN)
        if (isMember(found.id, player.id)) return@transaction refused(LeagueRefusal.ALREADY_A_MEMBER)
        refusalToEnter(player.id, locked)?.let { return@transaction refused(it) }
        val league = league(found.id, lock = true)?.let { settleAndSave(it) }
            ?: return@transaction refused(LeagueRefusal.INVITE_CODE_UNKNOWN)
        enter(league, player.id) ?: when (LeagueLifecycle.joinCheck(league.status, league.players)) {
            JoinCheck.FULL -> refused(LeagueRefusal.FULL)
            else -> refused(LeagueRefusal.JOINING_CLOSED)
        }
    }

    /**
     * Puts the player in the open league with the earliest start that has room and has not started. When
     * there is none, opens one that starts on the second Monday from today, in UTC: the request carries no
     * time zone, since ADR 0001's table of what the app sends does not name one.
     */
    fun autoJoin(player: SignedInPlayer): LeagueOutcome = store.transaction {
        val locked = lockPlayer(player.id) ?: return@transaction refused(LeagueRefusal.NO_SUCH_PLAYER)
        refusalToEnter(player.id, locked)?.let { return@transaction refused(it) }
        // One auto-join at a time, so two players who find no room open one league between them, not two.
        lockAutoJoin()
        // A league whose start has arrived may start, move back a week or close: settle each before
        // choosing, so the choice is among today's starts. No zone is more than a day ahead of UTC.
        for (id in formingLeaguesStartingBy(today(ZoneOffset.UTC).plusDays(1))) {
            league(id, lock = true)?.let { settleAndSave(it) }
        }
        for (id in openFormingLeagues()) {
            val league = league(id, lock = true)?.let { settleAndSave(it) } ?: continue
            enter(league, player.id)?.let { return@transaction it }
        }
        val opened = insertNew(newOpenLeagueName(), open = true, AUTO_JOIN_ZONE, SeasonStart.forNewOpenLeague(today(AUTO_JOIN_ZONE)))
        addMember(opened.id, player.id)
        LeagueOutcome.InLeague(summary(opened.copy(players = 1)))
    }

    /** The leagues the player is in and has not left, latest start first. */
    fun mine(player: SignedInPlayer): List<LeagueSummary> = store.transaction {
        leaguesOf(player.id).map { summary(settled(it)) }
    }

    /** The league and its members, or null when there is no such league or the player is not in it. */
    fun detail(player: SignedInPlayer, leagueId: String): LeagueDetail? {
        val id = parseId(leagueId) ?: return null
        return store.transaction {
            val league = league(id)?.let(::settled)?.takeIf { isMember(id, player.id) } ?: return@transaction null
            val members = members(id)
            // No picks are scored yet: every player has 0 points until the server scores them.
            val points = members.map { 0 }
            val ranks = Standings.ranks(points)
            val standings = members.indices
                .map { i -> Standing(members[i].displayName, points[i], ranks[i], you = members[i].playerId == player.id) }
                .sortedWith(compareBy<Standing> { it.rank }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }.thenBy { it.displayName })
            LeagueDetail(
                league = summary(league),
                season = league.status.calendar.season,
                timeZone = league.timeZone.id,
                seasonStart = league.status.seasonStart,
                seasonEnd = league.status.seasonEnd,
                members = standings,
            )
        }
    }

    /**
     * Takes the player out of the league. Before the season starts, or in a league that never started,
     * they are gone as if they had never joined. After it starts they are marked as left: their picks stop
     * scoring and they no longer count toward any matchup.
     *
     * @return false when there is no such league or the player is not in it
     */
    fun leave(player: SignedInPlayer, leagueId: String): Boolean {
        val id = parseId(leagueId) ?: return false
        return store.transaction {
            lockPlayer(player.id) ?: return@transaction false
            val league = league(id, lock = true)?.takeIf { isMember(id, player.id) } ?: return@transaction false
            leaveLocked(league, player.id)
            true
        }
    }

    override fun current(playerId: UUID): LeagueSummary? = store.transaction {
        val leagues = leaguesOf(playerId).map(::settled)
        (leagues.firstOrNull { it.status.isActive } ?: leagues.firstOrNull())?.let(::summary)
    }

    override fun leaveAll(playerId: UUID) {
        store.transaction {
            lockPlayer(playerId) ?: return@transaction
            leaguesOf(playerId).map { it.id }.sorted().forEach { id ->
                league(id, lock = true)?.let { leaveLocked(it, playerId) }
            }
        }
    }

    /** Why the player cannot enter a league now, or null when they can. */
    private fun LeagueTransaction.refusalToEnter(playerId: UUID, player: LockedPlayer): LeagueRefusal? = when {
        player.displayName == null -> LeagueRefusal.NO_DISPLAY_NAME
        // The player's own league is read, not locked: the player's lock keeps them from joining or
        // leaving it meanwhile, and locking it here, then the league they ask for, could deadlock.
        leaguesOf(playerId).any { settled(it).status.isActive } -> LeagueRefusal.ALREADY_IN_A_LEAGUE
        else -> null
    }

    /** Adds the player to a locked, settled league when it can take them; null when it cannot. */
    private fun LeagueTransaction.enter(league: StoredLeague, playerId: UUID): LeagueOutcome? {
        if (LeagueLifecycle.joinCheck(league.status, league.players) != JoinCheck.CAN_JOIN) return null
        addMember(league.id, playerId)
        return LeagueOutcome.InLeague(summary(league.copy(players = league.players + 1)))
    }

    private fun LeagueTransaction.leaveLocked(league: StoredLeague, playerId: UUID) {
        when (settleAndSave(league).status.phase) {
            LeaguePhase.FORMING, LeaguePhase.CLOSED -> removeMember(league.id, playerId)
            LeaguePhase.RUNNING, LeaguePhase.FINISHED -> markLeft(league.id, playerId)
        }
    }

    /** The league brought up to today, and saved when that changed it. Only for a locked league. */
    private fun LeagueTransaction.settleAndSave(league: StoredLeague): StoredLeague {
        val now = settled(league)
        if (now.status != league.status) saveStatus(league.id, now.status)
        return now
    }

    private fun settled(league: StoredLeague): StoredLeague =
        league.copy(status = LeagueLifecycle.settle(league.status, league.players, today(league.timeZone)))

    private fun LeagueTransaction.insertNew(name: String, open: Boolean, zone: ZoneId, seasonStart: LocalDate): StoredLeague {
        repeat(INVITE_CODE_TRIES) {
            val league = StoredLeague(
                id = UUID.randomUUID(),
                name = name,
                open = open,
                timeZone = zone,
                inviteCode = InviteCode.random(random),
                status = LeagueStatus(LeaguePhase.FORMING, seasonStart),
            )
            if (insert(league)) return league
        }
        error("No free invite code in $INVITE_CODE_TRIES tries")
    }

    private fun newOpenLeagueName(): String =
        "$OPEN_LEAGUE_NAME ${String(CharArray(OPEN_LEAGUE_TAG_LENGTH) { InviteCode.ALPHABET[random.nextInt(InviteCode.ALPHABET.length)] })}"

    private fun today(zone: ZoneId): LocalDate = LocalDate.now(clock.withZone(zone))

    private fun summary(league: StoredLeague) = LeagueSummary(
        id = league.id.toString(),
        name = league.name,
        inviteCode = league.inviteCode,
        players = league.players,
        capacity = LeagueLifecycle.MAX_PLAYERS,
        state = when (league.status.phase) {
            LeaguePhase.FORMING -> LeagueState.BEFORE_SEASON
            LeaguePhase.RUNNING -> LeagueState.IN_SEASON
            LeaguePhase.FINISHED -> LeagueState.FINISHED
            LeaguePhase.CLOSED -> LeagueState.CLOSED
        },
    )

    private fun refused(reason: LeagueRefusal) = LeagueOutcome.Refused(reason)

    companion object {
        /** The time zone of a league auto-join opens. */
        val AUTO_JOIN_ZONE: ZoneId = ZoneOffset.UTC

        /**
         * The name of a league auto-join opens, before a tag of random characters. A league's name is the
         * players' data, shown as it is; the brand is the same in every language.
         */
        const val OPEN_LEAGUE_NAME = "Flyway"
        private const val OPEN_LEAGUE_TAG_LENGTH = 4

        /** A clash is one in hundreds of billions per league; three in a row means something else is wrong. */
        private const val INVITE_CODE_TRIES = 3

        private val zoneIds: Set<String> = ZoneId.getAvailableZoneIds()

        /** An IANA zone id the server knows; not an offset such as `+02:00`. */
        private fun zoneOf(text: String): ZoneId? = text.takeIf { it in zoneIds }?.let(ZoneId::of)

        /** Only the form the server writes, so one league has one id text. */
        private fun parseId(text: String): UUID? = try {
            UUID.fromString(text).takeIf { it.toString() == text }
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
