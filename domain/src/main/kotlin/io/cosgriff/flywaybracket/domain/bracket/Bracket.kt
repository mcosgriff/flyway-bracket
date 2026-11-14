package io.cosgriff.flywaybracket.domain.bracket

/**
 * The six rounds of a 64-bird bracket, one per week. A right pick is worth [points], doubling each
 * round.
 */
enum class Round(val teams: Int, val points: Int) {
    ROUND_OF_64(64, 1),
    ROUND_OF_32(32, 2),
    ROUND_OF_16(16, 4),
    QUARTERFINAL(8, 8),
    SEMIFINAL(4, 16),
    FINAL(2, 32),
}

/** A bird in the bracket: one of 16 seeds in one of the four quarters. Seed 1 is the favorite. */
data class Team(val commonName: String, val quarter: String, val seed: Int) {
    init {
        require(seed in 1..Seeding.PER_QUARTER) { "Seed $seed is outside 1..${Seeding.PER_QUARTER}" }
    }
}

/** A first-round or later pairing. [upper] is drawn above [lower]; neither is favored by position. */
data class Matchup(val upper: Team, val lower: Team)
