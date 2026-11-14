package io.cosgriff.flywaybracket.domain.pack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OutlineTest {
    private val square = Outline(listOf(rectangle(0.0, 0.0, 1.0, 1.0)))

    // A "C": a square two degrees wide with a notch cut into its east side, between latitudes 0.5 and 1.5.
    private val concave = Outline(
        listOf(
            listOf(
                LonLat(0.0, 0.0),
                LonLat(2.0, 0.0),
                LonLat(2.0, 0.5),
                LonLat(0.5, 0.5),
                LonLat(0.5, 1.5),
                LonLat(2.0, 1.5),
                LonLat(2.0, 2.0),
                LonLat(0.0, 2.0),
            ),
        ),
    )

    @Test
    fun `a place inside is contained`() {
        assertTrue(square.contains(0.5, 0.5))
        assertTrue(square.contains(0.01, 0.99))
    }

    @Test
    fun `a place outside is not contained`() {
        assertFalse(square.contains(1.5, 0.5))
        assertFalse(square.contains(-0.5, 0.5))
        assertFalse(square.contains(0.5, 1.5))
        assertFalse(square.contains(0.5, -0.5))
    }

    @Test
    fun `a ring that repeats its first corner at the end reads the same`() {
        val ring = rectangle(0.0, 0.0, 1.0, 1.0)
        val closed = Outline(listOf(ring + ring.first()))
        assertTrue(closed.contains(0.5, 0.5))
        assertFalse(closed.contains(1.5, 0.5))
    }

    @Test
    fun `a county in two pieces contains places in either and not between`() {
        val islands = Outline(listOf(rectangle(3.0, 0.0, 4.0, 1.0), rectangle(5.0, 0.0, 6.0, 1.0)))
        assertTrue(islands.contains(3.5, 0.5))
        assertTrue(islands.contains(5.5, 0.5))
        assertFalse(islands.contains(4.5, 0.5))
    }

    @Test
    fun `a place in the notch of a concave outline is outside`() {
        assertFalse(concave.contains(1.5, 1.0))
    }

    @Test
    fun `a place on the far side of the notch is inside`() {
        // From each arm, the line east leaves through one edge; from the spine, through one edge at the notch.
        assertTrue(concave.contains(1.5, 0.25))
        assertTrue(concave.contains(1.5, 1.75))
        assertTrue(concave.contains(0.25, 1.0))
    }

    @Test
    fun `an outline needs a ring of three corners`() {
        assertThrows(IllegalArgumentException::class.java) { Outline(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { Outline(listOf(listOf(LonLat(0.0, 0.0), LonLat(1.0, 1.0)))) }
    }

    @Test
    fun `a pack finds the county a place is in`() {
        val pack = testStatePack()
        assertEquals("XX-TS-001", pack.countyAt(0.5, 0.5)?.code)
        assertEquals("XX-TS-002", pack.countyAt(1.5, 0.5)?.code)
        assertEquals("XX-TS-003", pack.countyAt(3.5, 0.5)?.code)
        assertEquals("XX-TS-003", pack.countyAt(5.5, 0.5)?.code)
    }

    @Test
    fun `a place in no county has none`() {
        val pack = testStatePack()
        assertNull(pack.countyAt(4.5, 0.5))
        assertNull(pack.countyAt(0.5, 7.0))
    }

    @Test
    fun `a pack file is named for its state`() {
        assertEquals("XX-TS.fbpack", testStatePack().fileName)
    }
}
