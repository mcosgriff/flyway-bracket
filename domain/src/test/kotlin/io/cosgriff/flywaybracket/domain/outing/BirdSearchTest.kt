package io.cosgriff.flywaybracket.domain.outing

import io.cosgriff.flywaybracket.domain.pack.TimeBin
import io.cosgriff.flywaybracket.domain.pack.chart
import io.cosgriff.flywaybracket.domain.pack.resident
import io.cosgriff.flywaybracket.domain.pack.seasonal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Bird names and figures are made up.
class BirdSearchTest {
    private val may = TimeBin.of(5, 2)
    private val may1 = TimeBin.of(5, 1)
    private val may4 = TimeBin.of(5, 4)

    private val chart = chart(
        resident("Zebra Test Finch", 0.05),
        seasonal("Yellow-rumped Test Warbler", may1, may4, inSeason = 0.4),
        resident("Common Test Sparrow", 0.6),
        // Under the 3% floor: listed, never expected.
        resident("Rare Test Rail", 0.01),
        resident("Ánhinga Test", 0.0),
        seasonal("Wilson's Test Warbler", TimeBin.of(9, 1), TimeBin.of(9, 4), inSeason = 0.2),
        resident("Bobolink Test", 0.0),
    )

    private fun names(choices: List<BirdChoice>) = choices.map { it.commonName }

    @Test
    fun `expected birds come first, most often reported first, then the rest by name`() {
        assertEquals(
            listOf(
                BirdChoice("Common Test Sparrow", expected = true),
                BirdChoice("Yellow-rumped Test Warbler", expected = true),
                BirdChoice("Zebra Test Finch", expected = true),
                // Accents do not move a name: Ánhinga sorts as Anhinga.
                BirdChoice("Ánhinga Test", expected = false),
                BirdChoice("Bobolink Test", expected = false),
                BirdChoice("Rare Test Rail", expected = false),
                BirdChoice("Wilson's Test Warbler", expected = false),
            ),
            birdChoices(chart, may),
        )
    }

    @Test
    fun `which birds are expected follows the bin`() {
        val september = TimeBin.of(9, 2)
        assertEquals(
            listOf("Common Test Sparrow", "Wilson's Test Warbler", "Zebra Test Finch"),
            names(birdChoices(chart, september).filter { it.expected }),
        )
    }

    @Test
    fun `birds reported equally often are in order of name`() {
        val tied = chart(resident("Beta Test Owl", 0.1), resident("Alpha Test Owl", 0.1))
        assertEquals(listOf("Alpha Test Owl", "Beta Test Owl"), names(birdChoices(tied, may)))
    }

    @Test
    fun `a blank search lists every bird in order`() {
        assertEquals(birdChoices(chart, may), searchBirds(chart, may, ""))
        assertEquals(birdChoices(chart, may), searchBirds(chart, may, "   "))
    }

    @Test
    fun `a search matches the start of any word in the name and keeps the order`() {
        // Expected first, then the rest, as with no search.
        assertEquals(listOf("Yellow-rumped Test Warbler", "Wilson's Test Warbler"), names(searchBirds(chart, may, "warb")))
        assertEquals(listOf("Yellow-rumped Test Warbler"), names(searchBirds(chart, may, "rump")))
        assertEquals(emptyList<String>(), names(searchBirds(chart, may, "arbler")))
    }

    @Test
    fun `a search ignores case, accents and apostrophes`() {
        assertTrue(birdNameMatches("Yellow-rumped Test Warbler", "WARB"))
        assertTrue(birdNameMatches("Ánhinga Test", "anh"))
        assertTrue(birdNameMatches("Anhinga Test", "ÁNH"))
        assertTrue(birdNameMatches("Wilson's Test Warbler", "wilsons"))
        assertTrue(birdNameMatches("Wilson’s Test Warbler", "wilson's"))
    }

    @Test
    fun `every word of a search must start a word of the name, in any order`() {
        assertTrue(birdNameMatches("Yellow-rumped Test Warbler", "warb yel"))
        assertFalse(birdNameMatches("Yellow-rumped Test Warbler", "warb finch"))
    }

    @Test
    fun `a search that matches nothing gives an empty list`() {
        assertEquals(emptyList<BirdChoice>(), searchBirds(chart, may, "pelican"))
    }
}
