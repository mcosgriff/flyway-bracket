package io.cosgriff.flywaybracket.ui.bracket

import io.cosgriff.flywaybracket.domain.bracket.BracketView
import io.cosgriff.flywaybracket.domain.bracket.DecidedMatchup
import io.cosgriff.flywaybracket.domain.bracket.Decision
import io.cosgriff.flywaybracket.domain.bracket.DrawPosition
import io.cosgriff.flywaybracket.domain.bracket.Field
import io.cosgriff.flywaybracket.domain.bracket.Matchup
import io.cosgriff.flywaybracket.domain.bracket.MatchupRule
import io.cosgriff.flywaybracket.domain.bracket.MatchupView
import io.cosgriff.flywaybracket.domain.bracket.PickScore
import io.cosgriff.flywaybracket.domain.bracket.PickStatus
import io.cosgriff.flywaybracket.domain.bracket.PlayerPart
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.domain.bracket.Season
import io.cosgriff.flywaybracket.domain.bracket.Side
import io.cosgriff.flywaybracket.domain.bracket.Tally
import io.cosgriff.flywaybracket.domain.bracket.Team
import java.time.LocalDate

// Made-up figures for previews and screenshot tests: no real person, and no count or share from eBird. The
// birds are real species, seeded by hand.

private val previewNames = mapOf(
    "SONGBIRDS" to listOf(
        "Northern House Wren", "Yellow Warbler", "Yellow-rumped Warbler", "Common Grackle", "Lesser Goldfinch",
        "Brown-headed Cowbird", "Western Meadowlark", "Chipping Sparrow", "Bullock's Oriole", "Western Tanager",
        "Gray Catbird", "Western Warbling Vireo", "Black-headed Grosbeak", "Yellow-breasted Chat",
        "White-crowned Sparrow", "Lazuli Bunting",
    ),
    "WATERBIRDS" to listOf(
        "Double-crested Cormorant", "Great Blue Heron", "Killdeer", "Ring-billed Gull", "American Coot",
        "Spotted Sandpiper", "American White Pelican", "Snowy Egret", "Western Grebe", "Pied-billed Grebe",
        "Horned Grebe", "American Avocet", "Black-crowned Night-Heron", "Wilson's Snipe", "Greater Yellowlegs",
        "Franklin's Gull",
    ),
    "FLIERS" to listOf(
        "Broad-tailed Hummingbird", "Barn Swallow", "Violet-green Swallow", "Swainson's Hawk", "Tree Swallow",
        "Turkey Vulture", "Cliff Swallow", "Common Nighthawk", "Black-chinned Hummingbird", "White-throated Swift",
        "Northern Rough-winged Swallow", "American Kestrel", "Osprey", "Bank Swallow", "Chimney Swift",
        "Burrowing Owl",
    ),
    "WATERFOWL" to listOf(
        "Gadwall", "Northern Shoveler", "Bufflehead", "Hooded Merganser", "Green-winged Teal", "Lesser Scaup",
        "Blue-winged Teal", "Common Goldeneye", "Cinnamon Teal", "Ring-necked Duck", "American Wigeon", "Redhead",
        "Ruddy Duck", "Cackling Goose", "Greater Scaup", "Red-breasted Merganser",
    ),
)

private val previewTeams: List<Team> = Field.quarters.flatMap { quarter ->
    previewNames.getValue(quarter).mapIndexed { index, name -> Team(name, quarter, seed = index + 1) }
}

private fun team(name: String) = previewTeams.single { it.commonName == name }

/** The birds that win their first matchup against the better seed. */
private val previewUpsets = setOf("Bullock's Oriole", "Western Warbling Vireo", "Pied-billed Grebe", "Northern Rough-winged Swallow", "Cinnamon Teal")

/** A made-up week for [team]: the better the seed, the larger the share, except for the upsets. */
private fun previewTally(team: Team): Tally {
    val eligible = 9 + (team.seed * 5 + team.quarter.length) % 7
    val logged = if (team.commonName in previewUpsets) eligible - 1 else (17 - team.seed) * eligible / 20
    return Tally(eligible, logged)
}

/**
 * The picks: the better seed everywhere, except the vireo picked to upset its first two matchups and the cowbird
 * picked over the 3 seed in the round of 32.
 */
private fun previewPicks(only: (DrawPosition) -> Boolean = { true }): Map<DrawPosition, String> {
    val overrides = setOf("Western Warbling Vireo", "Brown-headed Cowbird")
    val drawn = Field.drawOrder(previewTeams)
    val picked = LinkedHashMap<DrawPosition, Team>()
    for (round in Round.entries) {
        repeat(round.teams / 2) { position ->
            val at = DrawPosition(round, position)
            val (upper, lower) = if (round == Round.ROUND_OF_64) {
                drawn[2 * position] to drawn[2 * position + 1]
            } else {
                val earlier = Round.entries[round.ordinal - 1]
                picked.getValue(DrawPosition(earlier, 2 * position)) to picked.getValue(DrawPosition(earlier, 2 * position + 1))
            }
            val favourite = minOf(upper, lower, compareBy({ it.seed }, { it.commonName }))
            val other = if (favourite == upper) lower else upper
            // Each is picked as an underdog only through the round of 32.
            val overridden = other.commonName in overrides && round <= Round.ROUND_OF_32
            picked[at] = if (overridden) other else favourite
        }
    }
    return picked.filterKeys(only).mapValues { it.value.commonName }
}

/** The first round decided from [previewTally], each pick of [picks] scored as the server scores it. */
private fun previewFirstRound(picks: Map<DrawPosition, String>): Map<DrawPosition, DecidedMatchup> =
    Field.firstRound(previewTeams).withIndex().associate { (position, matchup) ->
        val at = DrawPosition(Round.ROUND_OF_64, position)
        val upper = previewTally(matchup.upper)
        val lower = previewTally(matchup.lower)
        val decision = MatchupRule.decide(matchup, upper, lower)
        val score = picks[at]?.let { PickScore.of(at.round, it, decision) } ?: PickScore.MISSED
        at to DecidedMatchup(decision.winner.commonName, upper, lower, decision.reason, score)
    }

private val previewRoundEnds: Map<Round, LocalDate> = Round.entries.associateWith { LocalDate.of(2027, 3, 7).plusWeeks(it.ordinal.toLong()) }

/** Before the lock, nothing picked yet: a preview field. */
internal val previewBeforeLock = BracketState(
    view = BracketView.of(previewTeams, emptyMap(), emptyMap()),
    season = Season.SPRING,
    locked = false,
    locksAt = LocalDate.of(2027, 3, 1).atStartOfDay(),
    previewUntil = LocalDate.of(2027, 2, 27),
    liveRound = null,
    roundEnds = previewRoundEnds,
)

/** Before the lock: the top two quarters' first round, and the songbirds' round of 32, picked. */
internal val previewPartlyPicked = previewBeforeLock.copy(
    view = BracketView.of(
        previewTeams,
        previewPicks { at -> (at.round == Round.ROUND_OF_64 && at.position < 16) || (at.round == Round.ROUND_OF_32 && at.position < 4) },
        emptyMap(),
    ),
    previewUntil = null,
)

/** A pick in the partly picked bracket that clears two later picks, waiting for a yes. */
internal val previewChangePick = previewPartlyPicked.copy(
    confirm = PickChange(
        at = DrawPosition(Round.ROUND_OF_64, 2),
        bird = team("Lesser Goldfinch"),
        cleared = listOf(ClearedPick(Round.ROUND_OF_32, "Western Warbling Vireo")),
    ),
)

/** Picks locked; the first round decided and the round of 32 being played. */
internal val previewLocked: BracketState = previewPicks().let { picks ->
    previewBeforeLock.copy(
        view = BracketView.of(previewTeams, picks, previewFirstRound(picks)),
        locked = true,
        previewUntil = null,
        liveRound = Round.ROUND_OF_32,
    )
}

/** A round's Monday through its Sunday, in a season that starts on Monday 1 March 2027. */
private fun previewDates(round: Round): ClosedRange<LocalDate> {
    val monday = LocalDate.of(2027, 3, 1).plusWeeks(round.ordinal.toLong())
    return monday..monday.plusDays(6)
}

private fun previewMatchup(
    upper: Team,
    lower: Team,
    upperTally: Tally,
    lowerTally: Tally,
    pick: Team?,
    upperPart: PlayerPart?,
    lowerPart: PlayerPart?,
    at: DrawPosition,
): MatchupState {
    val decision = MatchupRule.decide(Matchup(upper, lower), upperTally, lowerTally)
    val score = pick?.let { PickScore.of(at.round, it.commonName, decision) } ?: PickScore.MISSED
    val status = when (pick) {
        null -> PickStatus.NONE
        decision.winner -> PickStatus.RIGHT
        else -> PickStatus.WRONG
    }
    val view = MatchupView(
        at = at,
        upper = Side.Known(upper),
        lower = Side.Known(lower),
        pick = pick,
        pickStatus = status,
        winner = decision.winner,
        result = DecidedMatchup(decision.winner.commonName, upperTally, lowerTally, decision.reason, score),
    )
    return matchupState(view, Field.quarterOf(at), previewDates(at.round), live = false, bins = emptyList(), player = null)
        .let { it.copy(upper = it.upper.copy(part = upperPart), lower = it.lower.copy(part = lowerPart)) }
}

/** Won on share: the 12 seed the player picked, logged by 6 of 9, over the 4 seed's 6 of 12. */
internal val previewMatchupHigherShare = previewMatchup(
    upper = team("Western Warbling Vireo"),
    lower = team("Common Grackle"),
    upperTally = Tally(eligible = 9, logged = 6),
    lowerTally = Tally(eligible = 12, logged = 6),
    pick = team("Western Warbling Vireo"),
    upperPart = PlayerPart(eligible = true, logged = true),
    lowerPart = PlayerPart(eligible = true, logged = false),
    at = DrawPosition(Round.ROUND_OF_32, 1),
)

/** A tie on share, to the better seed: the player picked the other bird. */
internal val previewMatchupTied = previewMatchup(
    upper = team("Brown-headed Cowbird"),
    lower = team("Yellow-rumped Warbler"),
    upperTally = Tally(eligible = 8, logged = 4),
    lowerTally = Tally(eligible = 10, logged = 5),
    pick = team("Brown-headed Cowbird"),
    upperPart = PlayerPart(eligible = false, logged = true),
    lowerPart = PlayerPart(eligible = true, logged = true),
    at = DrawPosition(Round.ROUND_OF_32, 2),
)

/** Too few eligible players on both sides, so the better seed: no pick. */
internal val previewMatchupTooFew = previewMatchup(
    upper = team("Common Nighthawk"),
    lower = team("Black-chinned Hummingbird"),
    upperTally = Tally(eligible = 2, logged = 1),
    lowerTally = Tally(eligible = 2, logged = 2),
    pick = null,
    upperPart = PlayerPart(eligible = false, logged = false),
    lowerPart = PlayerPart(eligible = false, logged = false),
    at = DrawPosition(Round.ROUND_OF_64, 17),
)
