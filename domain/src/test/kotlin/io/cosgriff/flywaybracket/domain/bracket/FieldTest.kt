package io.cosgriff.flywaybracket.domain.bracket

import io.cosgriff.flywaybracket.domain.pack.CountyChart
import io.cosgriff.flywaybracket.domain.pack.TimeBin
import io.cosgriff.flywaybracket.domain.pack.chart
import io.cosgriff.flywaybracket.domain.pack.resident
import io.cosgriff.flywaybracket.domain.pack.seasonal
import io.cosgriff.flywaybracket.domain.pack.testFieldPack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

// The made-up pack of Fieldland (testFieldPack) and generic names; no figure comes from eBird.
class FieldTest {
    private val pack = testFieldPack()
    private val plenty = pack.counties[0].chart
    private val fewDucks = pack.counties[1].chart
    private val sparse = pack.counties[2].chart

    /** Monday 1 March 2027: a spring season. */
    private val spring = (SeasonCalendar.of(LocalDate.of(2027, 3, 1)) as SeasonCalendarResult.Valid).calendar.allBins()

    private fun seeded(vararg homeCounties: CountyChart): FieldResult.Seeded {
        val result = Field.choose(homeCounties.toList(), spring)
        assertTrue(result.toString(), result is FieldResult.Seeded)
        return result as FieldResult.Seeded
    }

    @Test
    fun `a field is 64 birds, 16 in each group's quarter, the commonest seeded first`() {
        val field = seeded(plenty)

        assertEquals(64, field.teams.size)
        assertEquals(80, field.candidates)
        BirdGroup.entries.forEach { group ->
            val quarter = field.teams.filter { it.quarter == group.name }.sortedBy { it.seed }
            assertEquals((1..16).toList(), quarter.map { it.seed })
            quarter.forEach { assertEquals(it.commonName, group, BirdGroup.of(it.commonName)) }
        }
        val songbirds = field.teams.filter { it.quarter == "SONGBIRDS" }.sortedBy { it.seed }
        assertEquals("Test Warbler 20", songbirds.first().commonName)
        assertEquals("Test Warbler 05", songbirds.last().commonName)
    }

    @Test
    fun `a resident is never a candidate`() {
        val names = Field.candidates(listOf(plenty), spring).map { it.commonName }

        assertFalse("Test Resident Sparrow" in names)
        assertEquals(80, names.size)
    }

    @Test
    fun `a short quarter is filled with the strongest birds left over from any group`() {
        val field = seeded(fewDucks)

        assertEquals(66, field.candidates)
        val waterfowl = field.teams.filter { it.quarter == "WATERFOWL" }.sortedBy { it.seed }
        assertEquals(16, waterfowl.size)
        // All six teal, then the strongest ten of the twelve birds the other quarters did not take (their
        // numbers 1 to 4): every 4, 3 and 2, and the 1 first by name.
        val expected = (1..6).map { "Test Teal 0$it" } +
            listOf("Sandpiper", "Swallow", "Warbler").flatMap { family -> (2..4).map { "Test $family 0$it" } } +
            "Test Sandpiper 01"
        assertEquals(expected.toSet(), waterfowl.map { it.commonName }.toSet())
        assertEquals("Test Teal 06", waterfowl.first().commonName)
        // The other quarters are untouched.
        assertEquals(64, field.teams.map { it.commonName }.toSet().size)
        assertEquals(16, field.teams.count { it.quarter == "SONGBIRDS" && BirdGroup.of(it.commonName) == BirdGroup.SONGBIRDS })
    }

    @Test
    fun `fewer than 64 candidates make no field`() {
        assertEquals(FieldResult.TooFewBirds(40), Field.choose(listOf(sparse), spring))
        assertEquals(FieldResult.TooFewBirds(0), Field.choose(emptyList(), spring))
    }

    @Test
    fun `a bird must be expected in the season's bins to be a candidate`() {
        // 70 migrants that pass through only in September: none is expected in a spring season.
        val september = chart(
            *Array(70) { seasonal("Test Warbler $it", TimeBin.of(9, 1), TimeBin.of(9, 4), inSeason = 0.2) },
        )

        assertEquals(FieldResult.TooFewBirds(0), Field.choose(listOf(september), spring))
    }

    @Test
    fun `a bird is a candidate when it migrates in one member's county and is expected in another's`() {
        // Here it passes through in September only, so it is a migrant but not expected in spring.
        val migrantHere = chart(seasonal("Test Plover", TimeBin.of(9, 1), TimeBin.of(9, 4), inSeason = 0.2))
        // There it stays all year, so it is expected in spring but is no migrant.
        val expectedThere = chart(resident("Test Plover", 0.1), regionCode = "XX-TEST-002")

        assertEquals(listOf("Test Plover"), Field.candidates(listOf(migrantHere, expectedThere), spring).map { it.commonName })
        assertEquals(emptyList<Candidate>(), Field.candidates(listOf(migrantHere), spring))
        assertEquals(emptyList<Candidate>(), Field.candidates(listOf(expectedThere), spring))
    }

    @Test
    fun `strength counts each member, so a county two members share counts twice`() {
        val twoInPlenty = Field.candidates(listOf(plenty, plenty, sparse), spring).associateBy { it.commonName }

        // Test Warbler 20 is on 24% of checklists in Plenty and is not in Sparse's chart.
        assertEquals(0.24 * 2 / 3, twoInPlenty.getValue("Test Warbler 20").strength, 1e-9)
        // Test Warbler 10 is on 14% in both.
        assertEquals(0.14, twoInPlenty.getValue("Test Warbler 10").strength, 1e-9)
    }

    @Test
    fun `the first round is 32 matchups, quarter by quarter from the top`() {
        val field = seeded(plenty)
        val games = Field.firstRound(field.teams)

        assertEquals(32, games.size)
        assertEquals(listOf("SONGBIRDS", "WATERBIRDS", "FLIERS", "WATERFOWL"), games.chunked(8).map { it.first().upper.quarter })
        assertEquals(1 to 16, games[0].upper.seed to games[0].lower.seed)
        assertEquals(2 to 15, games[7].upper.seed to games[7].lower.seed)
        assertEquals("WATERBIRDS", games[8].upper.quarter)
        assertEquals(64, Field.drawOrder(field.teams).map { it.commonName }.toSet().size)
    }

    @Test
    fun `the same field changes no matchup`() {
        val field = seeded(plenty).teams

        assertEquals(emptyList<DrawPosition>(), Field.changedMatchups(field, field))
    }

    @Test
    fun `a bird swapped for another changes its matchup and every one it feeds`() {
        val before = seeded(plenty).teams
        // The waterfowl quarter's 16 seed is drawn in its first matchup, the bracket's 25th.
        val sixteen = before.single { it.quarter == "WATERFOWL" && it.seed == 16 }
        val after = before - sixteen + sixteen.copy(commonName = "Test Teal 99")

        assertEquals(
            listOf(
                DrawPosition(Round.ROUND_OF_64, 24),
                DrawPosition(Round.ROUND_OF_32, 12),
                DrawPosition(Round.ROUND_OF_16, 6),
                DrawPosition(Round.QUARTERFINAL, 3),
                DrawPosition(Round.SEMIFINAL, 1),
                DrawPosition(Round.FINAL, 0),
            ),
            Field.changedMatchups(before, after),
        )
    }

    @Test
    fun `two birds trading seeds change no matchup they stay in together`() {
        val before = seeded(plenty).teams
        // Seeds 1 and 16 of the songbirds meet in the first matchup: trading seeds leaves the pair as it was.
        val one = before.single { it.quarter == "SONGBIRDS" && it.seed == 1 }
        val sixteen = before.single { it.quarter == "SONGBIRDS" && it.seed == 16 }
        val after = before - one - sixteen + one.copy(seed = 16) + sixteen.copy(seed = 1)

        assertEquals(emptyList<DrawPosition>(), Field.changedMatchups(before, after))
    }
}
