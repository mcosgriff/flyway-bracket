package io.cosgriff.flywaybracket.domain.pack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CountyChartTest {
    private val may = TimeBin.of(5, 2)
    private val january = TimeBin.of(1, 2)

    private val summerWarbler = seasonal("Test Warbler", TimeBin.of(5, 1), TimeBin.of(8, 4), inSeason = 0.30)
    private val yardBird = resident("Test Chickadee", 0.45)
    private val rarity = seasonal("Test Vagrant", TimeBin.of(5, 1), TimeBin.of(5, 4), inSeason = 0.004)
    private val county = chart(summerWarbler, yardBird, rarity)

    @Test
    fun `a bird at or above 3 percent of checklists is expected`() {
        assertTrue(county.isExpected("Test Warbler", may))
        assertTrue(chart(resident("Edge Bird", 0.03)).isExpected("Edge Bird", may))
    }

    @Test
    fun `a bird out of season is not expected`() {
        assertFalse(county.isExpected("Test Warbler", january))
    }

    @Test
    fun `a rarity under the floor is not expected even at its peak`() {
        assertFalse(county.isExpected("Test Vagrant", may))
    }

    @Test
    fun `a bird the chart does not list is not expected`() {
        assertFalse(county.isExpected("Painted Nonesuch", may))
    }

    @Test
    fun `expected birds come most reported first`() {
        assertEquals(listOf("Test Chickadee", "Test Warbler"), county.expected(may).map { it.commonName })
        assertEquals(listOf("Test Chickadee"), county.expected(january).map { it.commonName })
    }

    @Test
    fun `migrants leave out residents and rarities`() {
        assertEquals(listOf("Test Warbler"), county.migrants().map { it.commonName })
    }

    @Test
    fun `a bird that only thins out in winter is still a resident`() {
        // Low season at half the peak: well above the 15 percent a migrant drops to.
        val thinsOut = seasonal("Test Junco", TimeBin.of(4, 1), TimeBin.of(9, 4), inSeason = 0.20, offSeason = 0.40)
        assertTrue(chart(thinsOut).migrants().isEmpty())
    }

    @Test
    fun `one odd week does not make a season`() {
        // A single bin at 6 percent smooths to 2 percent, under the floor.
        val oneWeek = seasonal("Test Flyover", may, may, inSeason = 0.06)
        assertTrue(chart(oneWeek).migrants().isEmpty())
    }

    @Test
    fun `smoothing wraps across the new year`() {
        val winterDuck = SpeciesFrequency("Test Duck", List(TimeBin.COUNT) { if (it == 0 || it == 47) 0.3 else 0.0 })
        assertEquals(0.2, winterDuck.smoothedAt(TimeBin(0)), 1e-9)
        assertEquals(0.2, winterDuck.smoothedAt(TimeBin(47)), 1e-9)
    }
}
