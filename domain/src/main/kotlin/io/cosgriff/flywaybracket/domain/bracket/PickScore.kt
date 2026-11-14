package io.cosgriff.flywaybracket.domain.bracket

/** What one pick earned: the round's points, plus the seed gap when the pick was a right upset. */
data class PickScore(val roundPoints: Int, val upsetBonus: Int) {
    val total: Int get() = roundPoints + upsetBonus

    companion object {
        val MISSED = PickScore(0, 0)

        /**
         * A right pick earns the round's points. Calling an upset also earns the gap between the two
         * seeds: a 12 seed over a 5 adds 7. A wrong pick earns nothing.
         */
        fun of(round: Round, pickedCommonName: String, decision: Decision): PickScore {
            if (pickedCommonName != decision.winner.commonName) return MISSED
            val bonus = if (decision.isUpset) decision.winner.seed - decision.loser.seed else 0
            return PickScore(round.points, bonus)
        }
    }
}
