package io.cosgriff.flywaybracket.domain.outing

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

// Bird names, county codes and dates are made up.
class RoundSightingsTest {
    private val zone = ZoneId.of("America/Denver")

    /** A Monday to Sunday. */
    private val round = LocalDate.of(2027, 3, 8)..LocalDate.of(2027, 3, 14)
    private var nextId = 1L

    private fun log(county: String, date: LocalDate, vararg birds: String, at: LocalTime = LocalTime.NOON): OutingLog {
        val id = nextId++
        val start = date.atTime(at).atZone(zone).toInstant()
        return OutingLog(
            Outing(id, "", county, start, start.plusSeconds(1800)),
            birds.map { Sighting(nextId++, id, it, count = 1, seenAt = start) },
        )
    }

    @Test
    fun `the round's sightings give their counties and birds, each once`() {
        val logs = listOf(
            log("US-ZZ-001", LocalDate.of(2027, 3, 8), "Test Warbler", "Sample Sparrow"),
            log("US-ZZ-002", LocalDate.of(2027, 3, 14), "Sample Sparrow", at = LocalTime.of(23, 30)),
            log("US-ZZ-001", LocalDate.of(2027, 3, 10), "Made-up Heron"),
        )

        assertEquals(
            RoundSightings(setOf("US-ZZ-001", "US-ZZ-002"), setOf("Test Warbler", "Sample Sparrow", "Made-up Heron")),
            RoundSightings.of(logs, round, zone),
        )
    }

    @Test
    fun `sightings outside the round, and outings with none in it, add nothing`() {
        val logs = listOf(
            log("US-ZZ-003", LocalDate.of(2027, 3, 7), "Test Warbler", at = LocalTime.of(23, 59)),
            log("US-ZZ-004", LocalDate.of(2027, 3, 15), "Sample Sparrow", at = LocalTime.of(0, 1)),
            log("US-ZZ-005", LocalDate.of(2027, 3, 9)),
        )

        assertEquals(RoundSightings(emptySet(), emptySet()), RoundSightings.of(logs, round, zone))
    }
}
