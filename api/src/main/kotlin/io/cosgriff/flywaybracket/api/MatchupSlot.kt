package io.cosgriff.flywaybracket.api

import io.cosgriff.flywaybracket.domain.bracket.Round
import kotlinx.serialization.Serializable

/**
 * Which of a bracket's 63 matchups: one number, 0 to 62, counted by round, then by position from the
 * top of the bracket.
 *
 * | Round | Matchups | Slots |
 * | --- | --- | --- |
 * | `ROUND_OF_64` | 32 | 0–31 |
 * | `ROUND_OF_32` | 16 | 32–47 |
 * | `ROUND_OF_16` | 8 | 48–55 |
 * | `QUARTERFINAL` | 4 | 56–59 |
 * | `SEMIFINAL` | 2 | 60–61 |
 * | `FINAL` | 1 | 62 |
 *
 * [position] counts from 0 at the top of the bracket as it is drawn, within the round. The winners of
 * positions `2p` and `2p + 1` of one round meet at position `p` of the next ([next]); the winner of the
 * even one is drawn above. So slots 0 and 1 feed slot 32, and slots 60 and 61 feed the final, 62.
 *
 * In JSON a slot is its [index], a bare number; as a map key, that number in quotes.
 */
@Serializable
@JvmInline
value class MatchupSlot(val index: Int) : Comparable<MatchupSlot> {
    init {
        require(index in 0 until COUNT) { "Slot $index is outside 0..${COUNT - 1}" }
    }

    val round: Round get() = Round.entries.last { firstIndex(it) <= index }

    /** 0 at the top of the bracket, within [round]. */
    val position: Int get() = index - firstIndex(round)

    /** The matchup this one's winner plays next; null for the final. */
    val next: MatchupSlot?
        get() = Round.entries.getOrNull(round.ordinal + 1)?.let { of(it, position / 2) }

    override fun compareTo(other: MatchupSlot): Int = index.compareTo(other.index)

    companion object {
        /** Matchups in a 64-bird bracket. */
        const val COUNT = 63

        /** Every slot, in order. */
        val all: List<MatchupSlot> get() = List(COUNT, ::MatchupSlot)

        /** How many matchups [round] has: half its teams. */
        fun matchupsIn(round: Round): Int = round.teams / 2

        fun of(round: Round, position: Int): MatchupSlot {
            require(position in 0 until matchupsIn(round)) { "$round has no position $position" }
            return MatchupSlot(firstIndex(round) + position)
        }

        private fun firstIndex(round: Round): Int = Round.entries.take(round.ordinal).sumOf(::matchupsIn)
    }
}
