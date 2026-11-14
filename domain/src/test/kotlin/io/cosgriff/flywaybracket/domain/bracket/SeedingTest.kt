package io.cosgriff.flywaybracket.domain.bracket

import io.cosgriff.flywaybracket.domain.pack.TimeBin
import io.cosgriff.flywaybracket.domain.pack.chart
import io.cosgriff.flywaybracket.domain.pack.seasonal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SeedingTest {
    private val quarters = listOf("Songbirds", "Waterfowl")

    /** Twenty made-up birds per quarter; a higher number is a commoner bird. */
    private fun candidates(perQuarter: Int = 20) = quarters.flatMap { quarter ->
        (1..perQuarter).map { n -> Candidate("$quarter bird ${n.toString().padStart(2, '0')}", quarter, strength = n / 100.0) }
    }

    @Test
    fun `each quarter takes its 16 strongest, commonest first`() {
        val seeded = Seeding.seed(candidates(), quarters) as SeedingResult.Seeded
        assertEquals(32, seeded.teams.size)
        val songbirds = seeded.quarter("Songbirds")
        assertEquals("Songbirds bird 20", songbirds.first().commonName)
        assertEquals(1, songbirds.first().seed)
        assertEquals("Songbirds bird 05", songbirds.last().commonName)
        assertEquals(16, songbirds.last().seed)
    }

    @Test
    fun `equal strength seeds by name, so the same data always seeds the same way`() {
        val tied = (1..16).map { Candidate("Bird ${('P' - it)}", "Songbirds", 0.1) }
        val seeded = Seeding.seed(tied.shuffled(java.util.Random(7)), listOf("Songbirds")) as SeedingResult.Seeded
        assertEquals(tied.map { it.commonName }.sorted(), seeded.quarter("Songbirds").map { it.commonName })
    }

    @Test
    fun `a quarter with fewer than 16 candidates is reported, not padded`() {
        val result = Seeding.seed(candidates().filterNot { it.commonName == "Waterfowl bird 03" }.take(35), quarters)
        assertEquals(SeedingResult.QuarterShort("Waterfowl", 15), result)
    }

    @Test
    fun `the first round pairs seeds that add up to 17`() {
        val seeded = Seeding.seed(candidates(), quarters) as SeedingResult.Seeded
        val games = Seeding.firstRound(seeded.quarter("Songbirds"))
        assertEquals(8, games.size)
        assertEquals(listOf(1 to 16, 8 to 9, 5 to 12, 4 to 13, 6 to 11, 3 to 14, 7 to 10, 2 to 15), games.map { it.upper.seed to it.lower.seed })
    }

    @Test
    fun `a first round needs all 16 seeds`() {
        val seeded = Seeding.seed(candidates(), quarters) as SeedingResult.Seeded
        assertThrows(IllegalArgumentException::class.java) { Seeding.firstRound(seeded.quarter("Songbirds").drop(1)) }
    }

    @Test
    fun `strength is the best in-season frequency, averaged over the members' home counties`() {
        val april = listOf(TimeBin.of(4, 2), TimeBin.of(4, 3))
        val foothills = chart(seasonal("Test Hummingbird", TimeBin.of(4, 1), TimeBin.of(9, 4), inSeason = 0.60))
        val plains = chart(seasonal("Test Hummingbird", TimeBin.of(4, 1), TimeBin.of(9, 4), inSeason = 0.10))
        val elsewhere = chart(seasonal("Other Bird", TimeBin.of(4, 1), TimeBin.of(9, 4), inSeason = 0.50))

        assertEquals(0.60, Seeding.strength("Test Hummingbird", listOf(foothills), april), 1e-9)
        assertEquals(0.35, Seeding.strength("Test Hummingbird", listOf(foothills, plains), april), 1e-9)
        // A member whose county never sees the bird pulls its seed down.
        assertEquals(0.20, Seeding.strength("Test Hummingbird", listOf(foothills, elsewhere, elsewhere), april), 1e-9)
    }

    @Test
    fun `a bird out of season in the tournament's weeks has no strength`() {
        val january = listOf(TimeBin.of(1, 2))
        val county = chart(seasonal("Test Hummingbird", TimeBin.of(4, 1), TimeBin.of(9, 4), inSeason = 0.60))
        assertEquals(0.0, Seeding.strength("Test Hummingbird", listOf(county), january), 0.0)
        assertEquals(0.0, Seeding.strength("Test Hummingbird", emptyList(), january), 0.0)
    }
}
