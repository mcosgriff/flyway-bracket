package io.cosgriff.flywaybracket.domain.league

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class LeagueTimeZoneTest {
    @Test
    fun `a zone id is a league's time zone`() {
        assertEquals(ZoneId.of("America/Denver"), LeagueTimeZone.of("America/Denver"))
        assertEquals(ZoneId.of("Europe/Paris"), LeagueTimeZone.of("Europe/Paris"))
    }

    @Test
    fun `an offset, a wrong case or a made-up name is not`() {
        assertNull(LeagueTimeZone.of("+02:00"))
        assertNull(LeagueTimeZone.of("america/denver"))
        assertNull(LeagueTimeZone.of("Mars/Olympus_Mons"))
        assertNull(LeagueTimeZone.of(""))
    }

    @Test
    fun `the zones offered are those accepted, in order`() {
        assertTrue("America/Denver" in LeagueTimeZone.ids)
        assertEquals(LeagueTimeZone.ids.sorted(), LeagueTimeZone.ids)
        LeagueTimeZone.ids.forEach { assertEquals(it, LeagueTimeZone.of(it)?.id) }
    }
}
