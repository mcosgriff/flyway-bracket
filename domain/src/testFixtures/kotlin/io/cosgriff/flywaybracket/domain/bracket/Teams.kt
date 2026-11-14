package io.cosgriff.flywaybracket.domain.bracket

/** A made-up field of 64: `Test <quarter> <seed>`, 16 to a quarter, seeded 1 to 16. No name is real. */
fun testTeams(): List<Team> = Field.quarters.flatMap { quarter ->
    (1..Seeding.PER_QUARTER).map { seed -> Team("Test $quarter ${seed.toString().padStart(2, '0')}", quarter, seed) }
}

/**
 * Plays a whole bracket of [teams], round by round: [winner] picks who advances from each matchup of the two
 * birds that meet there. Returns the winner of each matchup.
 */
fun playBracket(teams: List<Team>, winner: (at: DrawPosition, upper: Team, lower: Team) -> Team): Map<DrawPosition, Team> {
    val drawn = Field.drawOrder(teams)
    val won = LinkedHashMap<DrawPosition, Team>()
    for (round in Round.entries) {
        repeat(round.teams / 2) { position ->
            val at = DrawPosition(round, position)
            val (upper, lower) = if (round == Round.ROUND_OF_64) {
                drawn[2 * position] to drawn[2 * position + 1]
            } else {
                val earlier = Round.entries[round.ordinal - 1]
                won.getValue(DrawPosition(earlier, 2 * position)) to won.getValue(DrawPosition(earlier, 2 * position + 1))
            }
            won[at] = winner(at, upper, lower)
        }
    }
    return won
}

/** The better seed of two; two equal seeds (quarter champions) by name. */
fun favourite(upper: Team, lower: Team): Team = minOf(upper, lower, compareBy({ it.seed }, { it.commonName }))
