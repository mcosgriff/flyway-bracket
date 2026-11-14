package io.cosgriff.flywaybracket.domain.league

/** A league table's ranks. */
object Standings {
    /**
     * The rank of each player, in the order of [points]. 1 is first. Players level on points share a rank,
     * and the rank after them skips the places they share: 10, 7, 7, 3 points rank 1, 2, 2, 4.
     */
    fun ranks(points: List<Int>): List<Int> = points.map { mine -> 1 + points.count { it > mine } }
}
