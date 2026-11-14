package io.cosgriff.flywaybracket.server.league

import io.cosgriff.flywaybracket.api.BracketMatchup
import io.cosgriff.flywaybracket.api.BracketResponse
import io.cosgriff.flywaybracket.api.BracketTeam
import io.cosgriff.flywaybracket.api.CreateLeagueRequest
import io.cosgriff.flywaybracket.api.LeagueDetail
import io.cosgriff.flywaybracket.api.LeagueState
import io.cosgriff.flywaybracket.api.LeagueSummary
import io.cosgriff.flywaybracket.api.MatchupSlot
import io.cosgriff.flywaybracket.api.PicksResponse
import io.cosgriff.flywaybracket.api.Standing
import io.cosgriff.flywaybracket.api.SubmitPicksRequest
import io.cosgriff.flywaybracket.domain.bracket.BirdGroup
import io.cosgriff.flywaybracket.domain.bracket.BracketPicks
import io.cosgriff.flywaybracket.domain.bracket.DrawPosition
import io.cosgriff.flywaybracket.domain.bracket.Field
import io.cosgriff.flywaybracket.domain.bracket.FieldResult
import io.cosgriff.flywaybracket.domain.bracket.PicksCheck
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.domain.bracket.SeasonCalendar
import io.cosgriff.flywaybracket.domain.bracket.SeasonCalendarResult
import io.cosgriff.flywaybracket.domain.bracket.Team
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
import io.cosgriff.flywaybracket.server.pack.CountyCharts
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

/** What a member asking for their league's bracket gets. */
sealed interface BracketOutcome {
    data class Ready(val bracket: BracketResponse) : BracketOutcome

    /** No such league, or the player is not in it. */
    data object NotFound : BracketOutcome

    /** The league has no field: too few birds for a preview, or it closed before its season started. */
    data object NotReady : BracketOutcome
}

/** What a member asking for picks, or sending their own, gets (#35). */
sealed interface PicksOutcome {
    data class Shown(val picks: PicksResponse) : PicksOutcome

    /** No such league, or the player is not in it. */
    data object NotFound : PicksOutcome

    /** The league has no member with that key, or the member has left. */
    data object NoSuchMember : PicksOutcome

    /** Another member's picks, asked for before picks lock. */
    data object Hidden : PicksOutcome

    /** The league has no field to pick against: too few birds for a preview, or it closed. */
    data object NotReady : PicksOutcome

    /** The season has started: picks can no longer change. */
    data object Locked : PicksOutcome

    /** The picks are not all or part of a bracket of the field; [slot] is the first pick that cannot stand. */
    data class Refused(val reason: PicksRefusal, val slot: MatchupSlot) : PicksOutcome
}

/** Why a set of picks was refused: [PicksCheck] in `:domain`. */
enum class PicksRefusal {
    /** The bird cannot play in that matchup: not in the field, or drawn elsewhere. */
    NOT_IN_MATCHUP,

    /** The bird is not picked to win the matchup before it on its path. */
    NOT_PICKED_TO_REACH,
}

/**
 * Leagues (#32): creating, joining, auto-joining, leaving, and what a member sees. The rules are
 * `domain/league`'s; this class applies them to what is stored, under the locks that keep two requests at
 * once from breaking them. Every call blocks on the database; call it off the request threads.
 *
 * A league's dates move it on (its field is fixed, its start arrives, its final ends), but nothing has to
 * run when they pass. The league is settled instead: brought up to today by [LeagueLifecycle.settle]
 * whenever it is read, and saved, under its lock, before any change to its players. So the count of
 * players settle sees is the count when each date passed. [SeasonStartJob] also settles, once a minute,
 * every league whose field is due to be fixed or whose start has arrived ([settleDue]), so that both happen
 * on time when no one is looking.
 *
 * **The field** (#34, `Field` in `:domain`). Before it is fixed, [bracket] shows a preview seeded from the
 * members as they are, stored so the next one can be compared with it. The settle that finds the field's
 * day ([io.cosgriff.flywaybracket.domain.bracket.SeasonCalendar.fieldFixedOn]) has come stores it fixed,
 * and the settle that starts the season keeps that one. A league whose field has too few birds is closed
 * when its start arrives. Seeding is part of the settle, in its transaction under the league's lock, so it
 * happens once whoever settles first.
 *
 * **Picks** (#35, `BracketPicks` in `:domain`). A member's picks are all or part of a bracket of the field
 * they see, checked when sent and stored per membership. A new field drops the picks it undoes, in the
 * transaction that stores it. Picks lock when the season starts; only then can members see each other's.
 *
 * @param charts the county charts of the packs the server holds. A member whose home county no pack holds
 *   is left out of seeding.
 * @param clock today, in each league's own time zone
 * @param previewChanged told, inside the transaction that stores a new field, of the matchups whose
 *   birds changed since the field stored before it, once the picks for them are dropped; see
 *   [dropPicksForChangedMatchups]
 */
class Leagues(
    private val store: LeagueStore,
    private val charts: CountyCharts,
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom(),
    private val previewChanged: (leagueId: UUID, changed: List<MatchupSlot>) -> Unit = { _, _ -> },
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
            val league = league(id)?.let { settled(it) }?.takeIf { isMember(id, player.id) } ?: return@transaction null
            val members = members(id)
            // No picks are scored yet: every player has 0 points until the server scores them.
            val points = members.map { 0 }
            val ranks = Standings.ranks(points)
            val standings = members.indices
                .map { i ->
                    Standing(members[i].displayName, points[i], ranks[i], members[i].key.toString(), you = members[i].playerId == player.id)
                }
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

    /**
     * The league's bracket, for a member: before the field is fixed, a preview seeded from the members as
     * they are now (stored, and the picks for any matchup whose birds changed dropped, when it differs from
     * the last); then the fixed field; once the season runs, the bracket and its results so far.
     */
    fun bracket(player: SignedInPlayer, leagueId: String): BracketOutcome = store.transaction {
        val league = memberLeague(player, leagueId) ?: return@transaction BracketOutcome.NotFound
        val (shown, preview) = shownField(league)
        val seeded = shown?.result as? FieldResult.Seeded ?: return@transaction BracketOutcome.NotReady
        BracketOutcome.Ready(bracketOf(seeded.teams, preview))
    }

    /**
     * The player's own picks in the league, at any time. Before the field is fixed the preview is brought up
     * to date first, as [bracket] does, so the picks shown are sound for the bracket the player sees.
     */
    fun picks(player: SignedInPlayer, leagueId: String): PicksOutcome = store.transaction {
        val league = memberLeague(player, leagueId) ?: return@transaction PicksOutcome.NotFound
        shownField(league)
        PicksOutcome.Shown(PicksResponse(picks(league.id, player.id), picksLocked(league)))
    }

    /**
     * A member's picks, named by their [StoredMember.key]: the player's own at any time, another member's only
     * once picks have locked, so that no player can copy another's (ADR 0001).
     */
    fun memberPicks(player: SignedInPlayer, leagueId: String, memberKey: String): PicksOutcome = store.transaction {
        val league = memberLeague(player, leagueId) ?: return@transaction PicksOutcome.NotFound
        val member = parseId(memberKey)?.let { memberByKey(league.id, it) } ?: return@transaction PicksOutcome.NoSuchMember
        if (member.playerId == player.id) {
            shownField(league)
        } else if (!picksLocked(league)) {
            return@transaction PicksOutcome.Hidden
        }
        PicksOutcome.Shown(PicksResponse(picks(league.id, member.playerId), picksLocked(league)))
    }

    /**
     * Replaces the player's picks with [request]'s, until picks lock when the season starts
     * ([SeasonCalendar.picksLockOn], in the league's time zone). Some picks are as good as all of them: a
     * matchup with no pick scores nothing. The picks are checked against the bracket [bracket] shows now
     * ([BracketPicks.check]); none is stored unless all can stand.
     */
    fun submitPicks(player: SignedInPlayer, leagueId: String, request: SubmitPicksRequest): PicksOutcome = store.transaction {
        val league = memberLeague(player, leagueId) ?: return@transaction PicksOutcome.NotFound
        if (league.status.phase == LeaguePhase.CLOSED) return@transaction PicksOutcome.NotReady
        if (picksLocked(league)) return@transaction PicksOutcome.Locked
        val seeded = shownField(league).first?.result as? FieldResult.Seeded ?: return@transaction PicksOutcome.NotReady
        when (val check = BracketPicks.check(seeded.teams, request.picks.mapKeys { (slot, _) -> slot.drawPosition })) {
            PicksCheck.Consistent -> Unit
            is PicksCheck.NotInMatchup -> return@transaction PicksOutcome.Refused(PicksRefusal.NOT_IN_MATCHUP, check.at.slot)
            is PicksCheck.NotPickedToReach -> return@transaction PicksOutcome.Refused(PicksRefusal.NOT_PICKED_TO_REACH, check.at.slot)
        }
        savePicks(league.id, player.id, request.picks)
        PicksOutcome.Shown(PicksResponse(picks(league.id, player.id), locked = false))
    }

    /**
     * The leagues still forming whose field may be due to be fixed, or whose start may have arrived: those
     * that start by three days after today in UTC. No time zone is more than a day ahead of UTC, and the
     * field is fixed two days before the start.
     */
    fun dueToSettle(): List<UUID> = store.transaction {
        formingLeaguesStartingBy(today(ZoneOffset.UTC).plusDays(1 + SeasonCalendar.FIELD_FIXED_DAYS_AHEAD))
    }

    /**
     * Settles one league under its lock and saves it: fixes its field, or starts its season, when the day
     * has come. Settling it again changes nothing.
     */
    fun settle(leagueId: UUID) {
        store.transaction { league(leagueId, lock = true)?.let { settleAndSave(it) } }
    }

    /** Settles every league [dueToSettle], each in a transaction of its own. */
    fun settleDue() {
        dueToSettle().forEach(::settle)
    }

    override fun current(playerId: UUID): LeagueSummary? = store.transaction {
        val leagues = leaguesOf(playerId).map { settled(it) }
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

    /**
     * The league, locked, settled and saved, when the player is in it; null when there is no such league or
     * the player is not in it. Locked, so that a preview or the fixed field is stored once, and the season
     * starts once.
     */
    private fun LeagueTransaction.memberLeague(player: SignedInPlayer, leagueId: String): StoredLeague? {
        val id = parseId(leagueId) ?: return null
        if (!isMember(id, player.id)) return null
        val locked = league(id, lock = true) ?: return null
        // Asked again under the lock: a leave that locked the league first may have taken the player out.
        if (!isMember(id, player.id)) return null
        return settleAndSave(locked)
    }

    /**
     * The field a member sees, and whether it is a preview: before the field is fixed, the preview of the
     * members as they are now (stored, dropping the picks it undoes, when it differs from the last); then the
     * fixed field; none for a closed league. Only for a locked, settled league.
     */
    private fun LeagueTransaction.shownField(league: StoredLeague): Pair<StoredField?, Boolean> = when (league.status.phase) {
        LeaguePhase.FORMING ->
            if (league.status.calendar.isFieldFixed(today(league.timeZone))) field(league.id) to false else refreshPreview(league) to true
        LeaguePhase.RUNNING, LeaguePhase.FINISHED -> field(league.id) to false
        LeaguePhase.CLOSED -> null to false
    }

    /**
     * Whether the league's picks have locked: from midnight at the start of its first Monday
     * ([SeasonCalendar.picksLockOn]) in its time zone, and in a league that never started. Only for a settled
     * league, whose start has moved back when it was postponed.
     */
    private fun picksLocked(league: StoredLeague): Boolean =
        league.status.phase != LeaguePhase.FORMING || today(league.timeZone) >= league.status.calendar.picksLockOn

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

    /**
     * The league brought up to today, and saved when that changed it; its field stored fixed once the field's
     * day or the season's start has come. Only for a locked league.
     */
    private fun LeagueTransaction.settleAndSave(league: StoredLeague): StoredLeague {
        val today = today(league.timeZone)
        val seeded = HashMap<LocalDate, FieldResult>()
        val now = league.copy(
            status = LeagueLifecycle.settle(league.status, league.players, today) { start ->
                seeded.getOrPut(start) { fieldFor(league.id, start) }
            },
        )
        if (now.status != league.status) saveStatus(league.id, now.status)
        val start = now.status.seasonStart
        when {
            // The season has just started: the field it started with is the bracket.
            league.status.phase == LeaguePhase.FORMING && now.status.phase in STARTED -> fixField(league.id, start, seeded[start])
            now.status.phase == LeaguePhase.FORMING && now.status.calendar.isFieldFixed(today) -> fixField(league.id, start, null)
        }
        return now
    }

    /** The league brought up to today, as [settleAndSave] would, but read only: nothing is stored. */
    private fun LeagueTransaction.settled(league: StoredLeague): StoredLeague = league.copy(
        status = LeagueLifecycle.settle(league.status, league.players, today(league.timeZone)) { fieldFor(league.id, it) },
    )

    /**
     * Stores the field of the season starting on [seasonStart] as fixed, unless it already is, so a field is
     * seeded and fixed once.
     *
     * @param seeded that field when the caller has just seeded it
     */
    private fun LeagueTransaction.fixField(leagueId: UUID, seasonStart: LocalDate, seeded: FieldResult?) {
        val stored = field(leagueId)
        if (stored != null && stored.fixed && stored.seasonStart == seasonStart) return
        replaceField(leagueId, stored, StoredField(seasonStart, fixed = true, seeded ?: seed(leagueId, seasonStart)))
    }

    /** The preview of the members as they are now, stored when it differs from the field stored. */
    private fun LeagueTransaction.refreshPreview(league: StoredLeague): StoredField {
        val stored = field(league.id)
        val preview = StoredField(league.status.seasonStart, fixed = false, seed(league.id, league.status.seasonStart))
        if (stored != preview) replaceField(league.id, stored, preview)
        return preview
    }

    private fun LeagueTransaction.replaceField(leagueId: UUID, before: StoredField?, after: StoredField) {
        saveField(leagueId, after)
        val changed = changedSlots(before?.result, after.result)
        if (changed.isNotEmpty()) dropPicksForChangedMatchups(leagueId, changed)
    }

    /**
     * Drops, in this transaction, the picks made against an earlier field for the matchups whose birds
     * changed, each of which named a bird that may no longer play there, and every later pick that depended
     * on one of them ([BracketPicks.withoutChanged]). Every player's in the league.
     */
    private fun LeagueTransaction.dropPicksForChangedMatchups(leagueId: UUID, changed: List<MatchupSlot>) {
        val changedAt = changed.map { it.drawPosition }
        leaguePicks(leagueId).forEach { (playerId, picks) ->
            val kept = BracketPicks.withoutChanged(picks.mapKeys { (slot, _) -> slot.drawPosition }, changedAt)
            if (kept.size != picks.size) savePicks(leagueId, playerId, kept.mapKeys { (at, _) -> at.slot })
        }
        previewChanged(leagueId, changed)
    }

    /** The field of a season starting on [seasonStart]: the fixed one stored for it, else seeded now. */
    private fun LeagueTransaction.fieldFor(leagueId: UUID, seasonStart: LocalDate): FieldResult =
        field(leagueId)?.takeIf { it.fixed && it.seasonStart == seasonStart }?.result ?: seed(leagueId, seasonStart)

    /**
     * A field seeded from the members' home counties as they are now. A member whose home county is not set,
     * or is in no pack the server holds, is left out.
     */
    private fun LeagueTransaction.seed(leagueId: UUID, seasonStart: LocalDate): FieldResult {
        val homeCounties = members(leagueId).mapNotNull { member -> member.homeCounty?.let(charts::chartFor) }
        val calendar = (SeasonCalendar.of(seasonStart) as SeasonCalendarResult.Valid).calendar
        return when (val result = Field.choose(homeCounties, calendar.allBins())) {
            is FieldResult.Seeded -> result.copy(teams = result.teams.inFieldOrder())
            is FieldResult.TooFewBirds -> result
        }
    }

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
        closedBecause = league.status.closedBecause,
    )

    private fun refused(reason: LeagueRefusal) = LeagueOutcome.Refused(reason)

    companion object {
        /** The phases of a league whose season has started. */
        private val STARTED = setOf(LeaguePhase.RUNNING, LeaguePhase.FINISHED)

        /**
         * The matchups whose birds differ between two fields. None when there was no field to pick against,
         * every one when the new field has no birds.
         */
        private fun changedSlots(before: FieldResult?, after: FieldResult): List<MatchupSlot> {
            val was = (before as? FieldResult.Seeded)?.teams ?: return emptyList()
            val now = (after as? FieldResult.Seeded)?.teams ?: return MatchupSlot.all
            return Field.changedMatchups(was, now).map { it.slot }
        }

        private val MatchupSlot.drawPosition: DrawPosition get() = DrawPosition(round, position)

        private val DrawPosition.slot: MatchupSlot get() = MatchupSlot.of(round, position)

        /** The bracket of a field. No round is decided yet (#37), so only the first round has its birds. */
        private fun bracketOf(teams: List<Team>, preview: Boolean): BracketResponse {
            val firstRound = Field.firstRound(teams)
            return BracketResponse(
                teams = teams.inFieldOrder().map { BracketTeam(it.commonName, BirdGroup.valueOf(it.quarter), it.seed) },
                matchups = MatchupSlot.all.map { slot ->
                    if (slot.round == Round.ROUND_OF_64) {
                        val matchup = firstRound[slot.position]
                        BracketMatchup(slot, upper = matchup.upper.commonName, lower = matchup.lower.commonName)
                    } else {
                        BracketMatchup(slot)
                    }
                },
                preview = preview,
            )
        }

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
