package io.cosgriff.flywaybracket.domain.league

import org.junit.Assert.assertEquals
import org.junit.Test

class LeagueNameTest {
    @Test
    fun `3 to 40 characters is a name`() {
        assertEquals(LeagueNameCheck.VALID, LeagueName.check("Owl"))
        assertEquals(LeagueNameCheck.VALID, LeagueName.check("Thursday Walkers"))
        assertEquals(LeagueNameCheck.VALID, LeagueName.check("W".repeat(40)))
    }

    @Test
    fun `spaces at either end do not count`() {
        assertEquals(LeagueNameCheck.TOO_SHORT, LeagueName.check("  Ow  "))
        assertEquals(LeagueNameCheck.VALID, LeagueName.check("  " + "W".repeat(40) + "  "))
    }

    @Test
    fun `empty, too short and too long`() {
        assertEquals(LeagueNameCheck.EMPTY, LeagueName.check(""))
        assertEquals(LeagueNameCheck.EMPTY, LeagueName.check("   "))
        assertEquals(LeagueNameCheck.TOO_SHORT, LeagueName.check("Ow"))
        assertEquals(LeagueNameCheck.TOO_LONG, LeagueName.check("W".repeat(41)))
    }

    @Test
    fun `a character outside the basic plane counts once`() {
        // Three letters of the Deseret alphabet: three characters, six UTF-16 units.
        assertEquals(LeagueNameCheck.VALID, LeagueName.check("𐐀𐐁𐐂"))
        assertEquals(LeagueNameCheck.TOO_SHORT, LeagueName.check("𐐀𐐁"))
    }
}
